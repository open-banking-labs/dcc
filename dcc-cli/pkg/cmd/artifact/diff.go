package artifact

import (
	"fmt"
	"io"
	"net/url"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/tableprinter"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdDiff returns the noun's diff command.
func NewCmdDiff(f *factory.Factory, kind Kind) *cobra.Command {
	var from, to string
	cmd := &cobra.Command{
		Use:   "diff <id>",
		Short: "Compare two versions of one " + kind.Noun,
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

			var diff api.DiffView
			path := fmt.Sprintf("%s/%d/diff", kind.APIPath, id)
			query := url.Values{"from": {from}, "to": {to}}
			if err := client.Get(cmd.Context(), path, query, &diff); err != nil {
				return cmdutil.WrapError(err)
			}

			return out.Print(f.IOStreams.Out, diff, func(w io.Writer) error {
				fmt.Fprintf(w, "from %s to %s (%s)\n", diff.FromVersion, diff.ToVersion, diff.ChangeType)
				if len(diff.Changes) == 0 {
					fmt.Fprintln(w, "no changes")
					return nil
				}
				table := tableprinter.New(w, f.IOStreams.IsStdoutTTY())
				table.Header("Field", "Before", "After")
				for _, change := range diff.Changes {
					table.Row(change.Field, change.Before, change.After)
				}
				return table.Render()
			})
		},
	}
	cmd.Flags().StringVar(&from, "from", "", "Source version")
	cmd.Flags().StringVar(&to, "to", "", "Target version")
	_ = cmd.MarkFlagRequired("from")
	_ = cmd.MarkFlagRequired("to")
	return cmd
}
