package auth

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/config"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdLogout returns the logout command.
func NewCmdLogout(f *factory.Factory) *cobra.Command {
	var all bool
	cmd := &cobra.Command{
		Use:   "logout",
		Short: "Remove stored credentials",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			cfg, err := cmdutil.LoadConfig(f)
			if err != nil {
				return cmdutil.Failure("%s", err)
			}

			if all {
				removed := len(cfg.Hosts)
				cfg.Hosts = map[string]*config.Host{}
				cfg.Current = ""
				if err := cfg.Save(); err != nil {
					return cmdutil.Failure("%s", err)
				}
				fmt.Fprintf(f.IOStreams.Out, "Logged out of %d host(s)\n", removed)
				return nil
			}

			hostname, _ := cmd.Flags().GetString("hostname")
			if hostname == "" {
				hostname = cfg.Current
			}
			if hostname == "" {
				return cmdutil.UsageError("no host configured; pass --hostname")
			}
			if !cfg.RemoveHost(hostname) {
				return cmdutil.Failure("not logged in to %s", hostname)
			}
			if err := cfg.Save(); err != nil {
				return cmdutil.Failure("%s", err)
			}
			fmt.Fprintf(f.IOStreams.Out, "Logged out of %s\n", hostname)
			return nil
		},
	}
	cmd.Flags().BoolVar(&all, "all", false, "Remove every configured host")
	return cmd
}
