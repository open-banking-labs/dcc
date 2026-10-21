// Package drift implements `dcc drift`.
package drift

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

// NewCmdDrift returns the drift-check command.
func NewCmdDrift(f *factory.Factory) *cobra.Command {
	var environmentID, applicationID int64
	cmd := &cobra.Command{
		Use:   "drift",
		Short: "Check whether stored snapshots match their recorded hashes",
		Long: `Check every stored snapshot in an environment/application scope.

Exits 1 when any snapshot drifted, so it can gate a pipeline.`,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			body := map[string]any{
				"environmentId": environmentID,
				"applicationId": applicationID,
			}
			var response api.DriftResponse
			if err := client.Command(cmd.Context(), "drift", body, &response); err != nil {
				return cmdutil.WrapError(err)
			}
			if err := out.Print(f.IOStreams.Out, response, func(w io.Writer) error {
				if len(response.Drifted) == 0 {
					_, err := fmt.Fprintln(w, "no drift detected")
					return err
				}
				table := tableprinter.New(w, f.IOStreams.IsStdoutTTY())
				table.Header("Artifact", "Code", "Version")
				for _, finding := range response.Drifted {
					table.Row(finding.ArtifactType, finding.Code, finding.Version)
				}
				return table.Render()
			}); err != nil {
				return err
			}
			if len(response.Drifted) > 0 {
				return cmdutil.SilentExit(1)
			}
			return nil
		},
	}
	cmd.Flags().Int64Var(&environmentID, "environment-id", 0, "Environment id")
	cmd.Flags().Int64Var(&applicationID, "application-id", 0, "Application id")
	_ = cmd.MarkFlagRequired("environment-id")
	_ = cmd.MarkFlagRequired("application-id")
	return cmd
}
