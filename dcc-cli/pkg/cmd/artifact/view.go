package artifact

import (
	"fmt"
	"io"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdView returns the noun's view command.
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
				return output.KeyValues(w, viewPairs(kind, object))
			})
		},
	}
}

func viewPairs(kind Kind, object map[string]any) [][2]string {
	pairs := [][2]string{
		{"id", formatValue(object["id"])},
	}
	if kind.Noun == "interface" {
		pairs = append(pairs, [2]string{"interface no", formatValue(object["interfaceNo"])})
	} else {
		pairs = append(pairs, [2]string{"code", formatValue(object["code"])})
		pairs = notEmpty(pairs, "category", formatValue(object["category"]))
	}
	pairs = notEmpty(pairs, "template id", formatValue(object["templateId"]))
	pairs = append(pairs,
		[2]string{"status", formatValue(object["status"])},
		[2]string{"version", formatValue(object["currentVersion"])},
		[2]string{"environment", formatValue(object["environmentId"])},
		[2]string{"application", formatValue(object["applicationId"])},
		[2]string{"updated", formatTime(object["updatedAt"])},
		[2]string{"content", compactJSON(object["content"])},
	)
	return pairs
}
