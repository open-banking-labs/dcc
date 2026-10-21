// Package table implements the `dcc table` noun group.
package table

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/artifact"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdTable returns the table-structure command group.
func NewCmdTable(f *factory.Factory) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "table",
		Short: "Work with table structures",
		Long:  "List, inspect, maintain and generate DDL for table structures.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	artifact.Attach(cmd, f, artifact.Table)
	return cmd
}
