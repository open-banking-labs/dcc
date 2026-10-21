package auth

import (
	"fmt"
	"io"
	"strings"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

type statusView struct {
	Hostname      string   `json:"hostname"`
	ServerURL     string   `json:"serverUrl"`
	APIVersion    string   `json:"apiVersion"`
	TokenSource   string   `json:"tokenSource"`
	Authenticated bool     `json:"authenticated"`
	Subject       string   `json:"subject,omitempty"`
	Tenant        string   `json:"tenant,omitempty"`
	Permissions   []string `json:"permissions,omitempty"`
	Error         string   `json:"error,omitempty"`
}

// NewCmdStatus returns the status command.
func NewCmdStatus(f *factory.Factory) *cobra.Command {
	return &cobra.Command{
		Use:   "status",
		Short: "Show the authentication status of a host",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			resolved, err := cmdutil.ResolveConfig(cmd, f)
			if err != nil {
				return cmdutil.Failure("%s", err)
			}

			view := statusView{
				Hostname:    resolved.Hostname,
				ServerURL:   resolved.ServerURL,
				APIVersion:  resolved.APIVersion,
				TokenSource: resolved.TokenSource,
			}
			if resolved.Token == "" {
				view.Error = "not logged in"
			} else {
				client, _, err := cmdutil.NewClient(cmd, f)
				if err != nil {
					return err
				}
				var me api.Me
				if err := client.Get(cmd.Context(), "me", nil, &me); err != nil {
					view.Error = err.Error()
				} else {
					view.Authenticated = true
					view.Subject = me.Subject
					view.Tenant = me.Tenant
					view.Permissions = me.Permissions
				}
			}

			if err := printStatus(out, f, view); err != nil {
				return err
			}
			if !view.Authenticated {
				return cmdutil.SilentExit(1)
			}
			return nil
		},
	}
}

func printStatus(out output.Options, f *factory.Factory, view statusView) error {
	return out.Print(f.IOStreams.Out, view, func(w io.Writer) error {
		pairs := [][2]string{
			{"host", view.Hostname},
			{"server", view.ServerURL},
			{"api version", view.APIVersion},
			{"token", view.TokenSource},
			{"authenticated", fmt.Sprintf("%t", view.Authenticated)},
		}
		if view.Subject != "" {
			pairs = append(pairs, [2]string{"user", view.Subject})
		}
		if view.Tenant != "" {
			pairs = append(pairs, [2]string{"tenant", view.Tenant})
		}
		if len(view.Permissions) > 0 {
			pairs = append(pairs, [2]string{"permissions", strings.Join(view.Permissions, ", ")})
		}
		if view.Error != "" {
			pairs = append(pairs, [2]string{"error", view.Error})
		}
		return output.KeyValues(w, pairs)
	})
}
