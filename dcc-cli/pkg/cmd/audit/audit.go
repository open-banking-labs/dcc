// Package audit implements the `dcc audit` noun group.
package audit

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdAudit returns the audit-trail command group.
func NewCmdAudit(f *factory.Factory) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "audit",
		Short: "Inspect the append-only audit trail",
		Long:  "Read history, search entries and verify the hash chain.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	cmd.AddCommand(NewCmdHistory(f), NewCmdSearch(f), NewCmdVerify(f))
	return cmd
}
