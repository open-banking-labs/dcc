package auth

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/config"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/iostreams"
)

// LoginOptions is the injectable surface of `dcc auth login`.
type LoginOptions struct {
	IO         *iostreams.IOStreams
	Config     func() (config.Config, error)
	HttpClient func() (*http.Client, error)

	Hostname              string
	ServerURL             string
	Token                 string
	TokenStdin            bool
	InsecureSkipTLSVerify bool
}

// NewCmdLogin returns the login command.
func NewCmdLogin(f *factory.Factory) *cobra.Command {
	opts := &LoginOptions{IO: f.IOStreams, Config: f.Config, HttpClient: f.HttpClient}
	cmd := &cobra.Command{
		Use:   "login",
		Short: "Store a token for a DCC server",
		Long: `Store a bearer token in the dcc hosts file after verifying it against /api/me.

Prefer --token-stdin or DCC_TOKEN in CI so the token never appears in the
process list.`,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			opts.Hostname, _ = cmd.Flags().GetString("hostname")
			opts.ServerURL, _ = cmd.Flags().GetString("server")
			return RunLogin(opts)
		},
	}
	cmd.Flags().StringVar(&opts.Token, "token", "", "Bearer token (prefer --token-stdin)")
	cmd.Flags().BoolVar(&opts.TokenStdin, "token-stdin", false, "Read the bearer token from stdin")
	cmd.Flags().BoolVar(&opts.InsecureSkipTLSVerify, "insecure-skip-tls-verify", false,
		"Skip TLS certificate verification for this host")
	return cmd
}

// RunLogin performs the login flow.
func RunLogin(opts *LoginOptions) error {
	token, err := opts.resolveToken()
	if err != nil {
		return err
	}

	serverURL := firstNonEmpty(opts.ServerURL, os.Getenv("DCC_SERVER_URL"), config.DefaultServerURL)
	hostname := firstNonEmpty(opts.Hostname, config.HostnameFromURL(serverURL))

	cfg, err := opts.loadConfig()
	if err != nil {
		return cmdutil.Failure("%s", err)
	}

	apiVersion := strings.TrimSpace(os.Getenv("DCC_API_VERSION"))
	if apiVersion == "" {
		if existing, ok := cfg.Host(hostname); ok {
			apiVersion = existing.APIVersion
		}
	}
	if apiVersion == "" {
		apiVersion = config.DefaultAPIVersion
	}

	var httpClient *http.Client
	if opts.HttpClient != nil {
		httpClient, err = opts.HttpClient()
		if err != nil {
			return cmdutil.Failure("%s", err)
		}
	}

	client, err := cmdutil.NewClientFor(serverURL, token, apiVersion, opts.InsecureSkipTLSVerify, httpClient)
	if err != nil {
		return cmdutil.Failure("%s", err)
	}

	var me api.Me
	if err := client.Get(cmdutil.Context(), "me", nil, &me); err != nil {
		return cmdutil.WrapError(err)
	}

	cfg.SetHost(hostname, &config.Host{
		ServerURL:             serverURL,
		Token:                 token,
		APIVersion:            apiVersion,
		InsecureSkipTLSVerify: opts.InsecureSkipTLSVerify,
	})
	if err := cfg.Save(); err != nil {
		return cmdutil.Failure("%s", err)
	}

	fmt.Fprintf(opts.IO.Out, "Logged in to %s as %s (tenant: %s)\n",
		serverURL, display(me.Subject), display(me.Tenant))
	return nil
}

func (o *LoginOptions) resolveToken() (string, error) {
	if token := strings.TrimSpace(o.Token); token != "" {
		return token, nil
	}
	if o.TokenStdin {
		token, err := cmdutil.ReadAll(o.IO.In)
		if err != nil {
			return "", cmdutil.Failure("cannot read token from stdin: %s", err)
		}
		if token == "" {
			return "", cmdutil.UsageError("no token received on stdin")
		}
		return token, nil
	}
	if token := strings.TrimSpace(os.Getenv("DCC_TOKEN")); token != "" {
		return token, nil
	}
	if o.IO.IsStdinTTY() {
		fmt.Fprint(o.IO.ErrOut, "Token: ")
		token, err := cmdutil.ReadLine(o.IO.In)
		if err != nil && err != io.EOF {
			return "", cmdutil.Failure("cannot read token: %s", err)
		}
		if token != "" {
			return token, nil
		}
	}
	return "", cmdutil.UsageError("a token is required: use --token, --token-stdin, or DCC_TOKEN")
}

func (o *LoginOptions) loadConfig() (config.Config, error) {
	if o.Config != nil {
		return o.Config()
	}
	return config.Load()
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if trimmed := strings.TrimSpace(value); trimmed != "" {
			return trimmed
		}
	}
	return ""
}

func display(value string) string {
	if strings.TrimSpace(value) == "" {
		return "(unknown)"
	}
	return value
}
