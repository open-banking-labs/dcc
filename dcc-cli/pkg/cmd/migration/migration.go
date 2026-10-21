// Package migration implements the `dcc migration` noun group.
package migration

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdMigration returns the migration-order command group.
func NewCmdMigration(f *factory.Factory) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "migration",
		Short: "Work with migration orders",
		Long:  "Create, review and execute environment-to-environment migrations.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	cmd.AddCommand(
		NewCmdCreate(f),
		NewCmdList(f),
		NewCmdView(f),
		NewCmdSubmit(f),
		NewCmdApprove(f),
		NewCmdReject(f),
		NewCmdExecute(f),
		NewCmdRollback(f),
	)
	return cmd
}
