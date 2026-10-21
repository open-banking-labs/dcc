package config

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestSaveLoadRoundTripAndPermissions(t *testing.T) {
	path := filepath.Join(t.TempDir(), "dcc", "hosts.yml")
	cfg, err := LoadFrom(path)
	if err != nil {
		t.Fatalf("LoadFrom: %v", err)
	}
	cfg.SetHost("example.test", &Host{
		ServerURL:             "https://example.test",
		Token:                 "secret",
		APIVersion:            "v2",
		InsecureSkipTLSVerify: true,
	})
	if err := cfg.Save(); err != nil {
		t.Fatalf("Save: %v", err)
	}

	info, err := os.Stat(path)
	if err != nil {
		t.Fatalf("stat: %v", err)
	}
	if got := info.Mode().Perm(); got != 0o600 {
		t.Fatalf("hosts file mode = %o, want 600", got)
	}

	loaded, err := LoadFrom(path)
	if err != nil {
		t.Fatalf("LoadFrom: %v", err)
	}
	if loaded.Current != "example.test" {
		t.Fatalf("Current = %q", loaded.Current)
	}
	host, ok := loaded.Host("example.test")
	if !ok {
		t.Fatal("host not found")
	}
	if host.ServerURL != "https://example.test" || host.Token != "secret" || host.APIVersion != "v2" {
		t.Fatalf("unexpected host: %+v", host)
	}
	if !host.InsecureSkipTLSVerify {
		t.Fatal("insecure flag not persisted")
	}
}

func TestMissingFileIsEmptyConfig(t *testing.T) {
	cfg, err := LoadFrom(filepath.Join(t.TempDir(), "missing.yml"))
	if err != nil {
		t.Fatalf("LoadFrom: %v", err)
	}
	if len(cfg.Hosts) != 0 || cfg.Current != "" {
		t.Fatalf("expected empty config, got %+v", cfg)
	}
}

func TestResolvePrecedence(t *testing.T) {
	clearEnv(t)
	cfg := Config{
		Current: "file-host",
		Hosts: map[string]*Host{
			"file-host": {
				ServerURL:             "https://file.example",
				Token:                 "file-token",
				APIVersion:            "v3",
				ConnectTimeoutSeconds: 7,
				RequestTimeoutSeconds: 9,
			},
		},
	}

	resolved, err := Resolve(cfg, Overrides{})
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	if resolved.ServerURL != "https://file.example" || resolved.Token != "file-token" ||
		resolved.APIVersion != "v3" || resolved.TokenSource != "hosts.yml" {
		t.Fatalf("file values not applied: %+v", resolved)
	}
	if resolved.ConnectTimeout != 7*time.Second || resolved.RequestTimeout != 9*time.Second {
		t.Fatalf("timeouts not applied: %+v", resolved)
	}

	t.Setenv("DCC_TOKEN", "env-token")
	t.Setenv("DCC_SERVER_URL", "https://env.example")
	t.Setenv("DCC_API_VERSION", "v4")
	resolved, err = Resolve(cfg, Overrides{ServerURL: "https://flag.example"})
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	if resolved.ServerURL != "https://flag.example" {
		t.Fatalf("flag server did not win: %s", resolved.ServerURL)
	}
	if resolved.Token != "env-token" || resolved.TokenSource != "env DCC_TOKEN" {
		t.Fatalf("env token did not win: %+v", resolved)
	}
	if resolved.APIVersion != "v4" {
		t.Fatalf("env api version did not win: %s", resolved.APIVersion)
	}
}

func TestResolveHostnameFromServer(t *testing.T) {
	clearEnv(t)
	resolved, err := Resolve(Config{}, Overrides{ServerURL: "http://localhost:8080"})
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	if resolved.Hostname != "localhost:8080" {
		t.Fatalf("hostname = %q", resolved.Hostname)
	}
	if resolved.ServerURL != "http://localhost:8080" || resolved.APIVersion != "v1" {
		t.Fatalf("defaults not applied: %+v", resolved)
	}
}

func TestInvalidTimeoutEnv(t *testing.T) {
	clearEnv(t)
	t.Setenv("DCC_REQUEST_TIMEOUT_SECONDS", "soon")
	if _, err := Resolve(Config{}, Overrides{}); err == nil {
		t.Fatal("expected an error for a non-numeric timeout")
	}
}

func clearEnv(t *testing.T) {
	t.Helper()
	for _, name := range []string{
		"DCC_HOSTNAME", "DCC_SERVER_URL", "DCC_TOKEN", "DCC_API_VERSION",
		"DCC_CONNECT_TIMEOUT_SECONDS", "DCC_REQUEST_TIMEOUT_SECONDS",
	} {
		t.Setenv(name, "")
	}
}
