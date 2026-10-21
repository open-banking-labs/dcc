// Package version implements `dcc version`.
package version

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/build"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdVersion returns the version command.
func NewCmdVersion(f *factory.Factory) *cobra.Command {
	return &cobra.Command{
		Use:   "version",
		Short: "Print version information",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			_, err := fmt.Fprintf(f.IOStreams.Out, "dcc version %s\n", build.String())
			return err
		},
	}
}
