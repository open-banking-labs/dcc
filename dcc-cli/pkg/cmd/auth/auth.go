// Package auth implements `dcc auth`.
package auth

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdAuth returns the authentication command group.
func NewCmdAuth(f *factory.Factory) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "auth",
		Short: "Authenticate dcc against a DCC server",
		Long:  "Manage the token and server URL stored in the dcc hosts configuration.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	cmd.AddCommand(NewCmdLogin(f), NewCmdStatus(f), NewCmdLogout(f))
	return cmd
}
