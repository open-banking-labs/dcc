package auth

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/config"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/iostreams"
)

func TestRunLoginPersistsHost(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/me" {
			t.Errorf("path = %q", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer tok" {
			t.Errorf("Authorization = %q", got)
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(api.Me{Subject: "alice", Tenant: "acme"})
	}))
	defer server.Close()

	path := filepath.Join(t.TempDir(), "hosts.yml")
	cfg, err := config.LoadFrom(path)
	if err != nil {
		t.Fatalf("LoadFrom: %v", err)
	}
	var out bytes.Buffer
	opts := &LoginOptions{
		IO: &iostreams.IOStreams{
			In:     io.NopCloser(strings.NewReader("")),
			Out:    &out,
			ErrOut: &out,
		},
		Config:     func() (config.Config, error) { return cfg, nil },
		HttpClient: func() (*http.Client, error) { return server.Client(), nil },
		ServerURL:  server.URL,
		Token:      "tok",
	}
	if err := RunLogin(opts); err != nil {
		t.Fatalf("RunLogin: %v", err)
	}

	loaded, err := config.LoadFrom(path)
	if err != nil {
		t.Fatalf("LoadFrom: %v", err)
	}
	hostname := config.HostnameFromURL(server.URL)
	host, ok := loaded.Host(hostname)
	if !ok {
		t.Fatalf("host %q not stored: %+v", hostname, loaded.Hosts)
	}
	if host.Token != "tok" || host.ServerURL != server.URL {
		t.Fatalf("unexpected host: %+v", host)
	}
	if loaded.Current != hostname {
		t.Fatalf("Current = %q", loaded.Current)
	}
	if !strings.Contains(out.String(), "Logged in to") || !strings.Contains(out.String(), "alice") {
		t.Fatalf("output = %q", out.String())
	}
}

func TestRunLoginRejectsBadToken(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = io.WriteString(w, `{"message":"invalid token"}`)
	}))
	defer server.Close()

	path := filepath.Join(t.TempDir(), "hosts.yml")
	cfg, _ := config.LoadFrom(path)
	opts := &LoginOptions{
		IO:         &iostreams.IOStreams{In: io.NopCloser(strings.NewReader("")), Out: &bytes.Buffer{}, ErrOut: &bytes.Buffer{}},
		Config:     func() (config.Config, error) { return cfg, nil },
		HttpClient: func() (*http.Client, error) { return server.Client(), nil },
		ServerURL:  server.URL,
		Token:      "bad",
	}
	err := RunLogin(opts)
	if err == nil || !strings.Contains(err.Error(), "invalid token") {
		t.Fatalf("expected token error, got %v", err)
	}
	if _, statErr := config.LoadFrom(path); statErr != nil {
		t.Fatalf("config should remain readable: %v", statErr)
	}
}

func TestRunLoginRequiresToken(t *testing.T) {
	t.Setenv("DCC_TOKEN", "")
	opts := &LoginOptions{
		IO:     &iostreams.IOStreams{In: io.NopCloser(strings.NewReader("")), Out: &bytes.Buffer{}, ErrOut: &bytes.Buffer{}},
		Config: func() (config.Config, error) { return config.Config{}, nil },
	}
	err := RunLogin(opts)
	if err == nil || !strings.Contains(err.Error(), "token is required") {
		t.Fatalf("expected usage error, got %v", err)
	}
}
