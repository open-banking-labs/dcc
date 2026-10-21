package tenantresource

import (
	"fmt"
	"io"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdList returns the list command.
func NewCmdList(f *factory.Factory, kind Kind) *cobra.Command {
	return &cobra.Command{
		Use:   "list",
		Short: "List " + kind.Noun + "s",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			var items []any
			if err := client.Get(cmd.Context(), kind.APIPath, nil, &items); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, items, func(w io.Writer) error {
				return renderTable(w, f.IOStreams.IsStdoutTTY(), kind.ListColumns, items)
			})
		},
	}
}

// NewCmdView returns the view command.
func NewCmdView(f *factory.Factory, kind Kind) *cobra.Command {
	return &cobra.Command{
		Use:   "view <id>",
		Short: "View one " + kind.Noun,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseID(args)
			if err != nil {
				return err
			}
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			var response any
			if err := client.Get(cmd.Context(), fmt.Sprintf("%s/%d", kind.APIPath, id), nil, &response); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, response, func(w io.Writer) error {
				object, ok := response.(map[string]any)
				if !ok {
					return output.WriteJSON(w, response)
				}
				pairs := [][2]string{
					{"id", formatValue(object["id"])},
					{"code", formatValue(object["code"])},
					{"name", formatValue(object["name"])},
				}
				if description := formatValue(object["description"]); description != "" {
					pairs = append(pairs, [2]string{"description", description})
				}
				if sortOrder := formatValue(object["sortOrder"]); sortOrder != "" {
					pairs = append(pairs, [2]string{"sort order", sortOrder})
				}
				pairs = append(pairs, [2]string{"updated", formatValue(object["updatedAt"])})
				return output.KeyValues(w, pairs)
			})
		},
	}
}
