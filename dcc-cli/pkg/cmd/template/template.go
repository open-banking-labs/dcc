// Package template implements the `dcc template` noun group.
package template

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/artifact"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdTemplate returns the interface-template command group.
func NewCmdTemplate(f *factory.Factory) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "template",
		Short: "Work with interface templates",
		Long:  "List, inspect and maintain interface templates.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	artifact.Attach(cmd, f, artifact.Template)
	return cmd
}
