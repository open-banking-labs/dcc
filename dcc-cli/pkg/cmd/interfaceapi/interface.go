// Package interfaceapi implements the `dcc interface` noun group. The package
// is named interfaceapi because "interface" is a Go keyword; the command name
// users see is still "interface".
package interfaceapi

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/artifact"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdInterface returns the interface-definition command group.
func NewCmdInterface(f *factory.Factory) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "interface",
		Short: "Work with interface definitions",
		Long:  "List, inspect, maintain and generate DTOs for interface definitions.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	artifact.Attach(cmd, f, artifact.Interface)
	return cmd
}
