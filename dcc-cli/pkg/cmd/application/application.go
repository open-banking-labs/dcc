// Package application implements the `dcc application` noun group.
package application

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/tenantresource"
)

// NewCmdApplication returns the application command group.
func NewCmdApplication(f *factory.Factory) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "application",
		Short: "Work with applications",
		Long:  "List, inspect and maintain tenant applications.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	tenantresource.Attach(cmd, f, tenantresource.Application)
	return cmd
}
