package artifact

import (
	"fmt"
	"io"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdExport returns the noun's export command.
func NewCmdExport(f *factory.Factory, kind Kind) *cobra.Command {
	var version, format string
	cmd := &cobra.Command{
		Use:   "export <id>",
		Short: "Export generated files for one " + kind.Noun,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseID(args)
			if err != nil {
				return err
			}
			if format != kind.ExportFormat {
				return cmdutil.UsageError("%s export supports --format %s", kind.Noun, kind.ExportFormat)
			}
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			var response api.ExportResponse
			if err := client.Command(cmd.Context(), "export/"+format, modelBody(kind, id, version), &response); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, response, func(w io.Writer) error {
				for _, file := range response.Files {
					if _, err := fmt.Fprintf(w, "--- %s\n", file.Path); err != nil {
						return err
					}
					if _, err := fmt.Fprintln(w, file.Content); err != nil {
						return err
					}
				}
				_, err := fmt.Fprintf(w, "%d file(s)\n", len(response.Files))
				return err
			})
		},
	}
	cmd.Flags().StringVar(&version, "version", "", "Version")
	cmd.Flags().StringVar(&format, "format", kind.ExportFormat, "Export format ("+kind.ExportFormat+")")
	_ = cmd.MarkFlagRequired("version")
	return cmd
}
