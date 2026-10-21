package cmdutil

import (
	"context"
	"crypto/tls"
	"errors"
	"net"
	"net/http"
	"time"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/config"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// LoadConfig returns the factory's config loader or the default hosts file.
func LoadConfig(f *factory.Factory) (config.Config, error) {
	if f != nil && f.Config != nil {
		return f.Config()
	}
	return config.Load()
}

// ResolveConfig applies the global flags, environment and hosts.yml.
func ResolveConfig(cmd *cobra.Command, f *factory.Factory) (config.Resolved, error) {
	cfg, err := LoadConfig(f)
	if err != nil {
		return config.Resolved{}, err
	}
	hostname, _ := cmd.Flags().GetString("hostname")
	serverURL, _ := cmd.Flags().GetString("server")
	apiVersion, _ := cmd.Flags().GetString("api-version")
	return config.Resolve(cfg, config.Overrides{
		Hostname:   hostname,
		ServerURL:  serverURL,
		APIVersion: apiVersion,
	})
}

// NewClient builds the API client for the resolved configuration.
func NewClient(cmd *cobra.Command, f *factory.Factory) (*api.Client, config.Resolved, error) {
	resolved, err := ResolveConfig(cmd, f)
	if err != nil {
		return nil, config.Resolved{}, err
	}

	httpClient := DefaultHTTPClient(resolved)
	if f != nil && f.HttpClient != nil {
		injected, err := f.HttpClient()
		if err != nil {
			return nil, resolved, err
		}
		if injected != nil {
			httpClient = injected
		}
	}

	client, err := api.New(api.Options{
		ServerURL:  resolved.ServerURL,
		Token:      resolved.Token,
		APIVersion: resolved.APIVersion,
		HTTPClient: httpClient,
	})
	if err != nil {
		return nil, resolved, err
	}
	return client, resolved, nil
}

// NewClientFor builds a client from explicit values, used by auth login before
// anything is persisted.
func NewClientFor(serverURL, token, apiVersion string, insecure bool, httpClient *http.Client) (*api.Client, error) {
	if httpClient == nil {
		resolved := config.Resolved{
			ServerURL:             serverURL,
			ConnectTimeout:        10 * time.Second,
			RequestTimeout:        60 * time.Second,
			InsecureSkipTLSVerify: insecure,
		}
		httpClient = DefaultHTTPClient(resolved)
	}
	return api.New(api.Options{
		ServerURL:  serverURL,
		Token:      token,
		APIVersion: apiVersion,
		HTTPClient: httpClient,
	})
}

// DefaultHTTPClient applies the resolved connect/request timeouts and the
// opt-in TLS skip. InsecureSkipVerify is only set when the user explicitly
// stored that choice for the host.
func DefaultHTTPClient(resolved config.Resolved) *http.Client {
	connectTimeout := resolved.ConnectTimeout
	if connectTimeout <= 0 {
		connectTimeout = 10 * time.Second
	}
	requestTimeout := resolved.RequestTimeout
	if requestTimeout <= 0 {
		requestTimeout = 60 * time.Second
	}
	dialer := &net.Dialer{Timeout: connectTimeout}
	transport := &http.Transport{
		Proxy:       http.ProxyFromEnvironment,
		DialContext: dialer.DialContext,
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: resolved.InsecureSkipTLSVerify, // #nosec G402 -- explicit per-host opt-in
			MinVersion:         tls.VersionTLS12,
		},
	}
	return &http.Client{Timeout: requestTimeout, Transport: transport}
}

// WrapError maps API failures to the CLI's exit-code convention.
func WrapError(err error) error {
	if err == nil {
		return nil
	}
	var apiErr *api.Error
	if errors.As(err, &apiErr) {
		if api.IsAuthError(err) {
			return AuthError("%s", apiErr.Message)
		}
		return Failure("%s", apiErr.Message)
	}
	return err
}

// Context is a small helper so commands do not repeat context.Background().
func Context() context.Context { return context.Background() }
