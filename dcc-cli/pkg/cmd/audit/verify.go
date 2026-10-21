package audit

import (
	"fmt"
	"io"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdVerify returns `dcc audit verify`.
func NewCmdVerify(f *factory.Factory) *cobra.Command {
	var entityType string
	var entityID int64
	cmd := &cobra.Command{
		Use:   "verify",
		Short: "Recompute one row's hash chain",
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

			body := map[string]any{"entityType": entityType, "entityId": entityID}
			var result api.VerifyResult
			if err := client.Command(cmd.Context(), "audit/verify", body, &result); err != nil {
				return cmdutil.WrapError(err)
			}
			if err := out.Print(f.IOStreams.Out, result, func(w io.Writer) error {
				status := "hash chain broken"
				if result.Intact {
					status = "hash chain intact"
				}
				if _, err := fmt.Fprintln(w, status); err != nil {
					return err
				}
				for _, problem := range result.Problems {
					if _, err := fmt.Fprintf(w, "- %s\n", problem); err != nil {
						return err
					}
				}
				return nil
			}); err != nil {
				return err
			}
			if !result.Intact {
				return cmdutil.SilentExit(1)
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&entityType, "entity-type", "", "Entity type, e.g. DATA_STANDARD")
	cmd.Flags().Int64Var(&entityID, "entity-id", 0, "Entity id")
	_ = cmd.MarkFlagRequired("entity-type")
	_ = cmd.MarkFlagRequired("entity-id")
	return cmd
}
