// Package standard implements the `dcc standard` noun group.
package standard

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/artifact"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdStandard returns the data-standard command group.
func NewCmdStandard(f *factory.Factory) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "standard",
		Short: "Work with data standards",
		Long:  "List, inspect, maintain and generate artifacts for data standards.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	artifact.Attach(cmd, f, artifact.Standard)
	return cmd
}
