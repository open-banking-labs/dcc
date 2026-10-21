// Package environment implements the `dcc environment` noun group.
package environment

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/tenantresource"
)

// NewCmdEnvironment returns the environment command group.
func NewCmdEnvironment(f *factory.Factory) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "environment",
		Short: "Work with environments",
		Long:  "List, inspect and maintain tenant environments.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	tenantresource.Attach(cmd, f, tenantresource.Environment)
	return cmd
}
