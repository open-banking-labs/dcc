package artifact

import (
	"fmt"
	"io"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/tableprinter"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdHistory returns the noun's history command.
func NewCmdHistory(f *factory.Factory, kind Kind) *cobra.Command {
	return &cobra.Command{
		Use:   "history <id>",
		Short: "Show the version history of one " + kind.Noun,
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

			var versions []api.VersionView
			path := fmt.Sprintf("%s/%d/history", kind.APIPath, id)
			if err := client.Get(cmd.Context(), path, nil, &versions); err != nil {
				return cmdutil.WrapError(err)
			}

			return out.Print(f.IOStreams.Out, versions, func(w io.Writer) error {
				table := tableprinter.New(w, f.IOStreams.IsStdoutTTY())
				table.Header("Version", "Change", "Author", "Created", "Message")
				for _, version := range versions {
					table.Row(
						version.Version,
						version.ChangeType,
						version.Author,
						version.CreatedAt.UTC().Format("2006-01-02 15:04:05"),
						version.Message,
					)
				}
				return table.Render()
			})
		},
	}
}
