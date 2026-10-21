// Package config reads and writes ~/.config/dcc/hosts.yml and resolves the
// effective connection settings. Flags beat environment variables, which beat
// the file, which beats built-in defaults.
package config

import (
	"errors"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
)

const (
	DefaultServerURL      = "http://localhost:8080"
	DefaultAPIVersion     = "v1"
	DefaultConnectSeconds = 10
	DefaultRequestSeconds = 60
)

// Host is one configured DCC server.
type Host struct {
	ServerURL             string `yaml:"server_url"`
	Token                 string `yaml:"token,omitempty"`
	APIVersion            string `yaml:"api_version,omitempty"`
	ConnectTimeoutSeconds int    `yaml:"connect_timeout_seconds,omitempty"`
	RequestTimeoutSeconds int    `yaml:"request_timeout_seconds,omitempty"`
	InsecureSkipTLSVerify bool   `yaml:"insecure_skip_tls_verify,omitempty"`
}

// Config is the on-disk hosts file.
type Config struct {
	Current string           `yaml:"current,omitempty"`
	Hosts   map[string]*Host `yaml:"hosts,omitempty"`

	path string
}

// Path returns the hosts file location:
// $DCC_CONFIG_DIR/hosts.yml, else $XDG_CONFIG_HOME/dcc/hosts.yml, else
// ~/.config/dcc/hosts.yml.
func Path() (string, error) {
	if dir := strings.TrimSpace(os.Getenv("DCC_CONFIG_DIR")); dir != "" {
		return filepath.Join(dir, "hosts.yml"), nil
	}
	if dir := strings.TrimSpace(os.Getenv("XDG_CONFIG_HOME")); dir != "" {
		return filepath.Join(dir, "dcc", "hosts.yml"), nil
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("cannot determine home directory: %w", err)
	}
	return filepath.Join(home, ".config", "dcc", "hosts.yml"), nil
}

// Load reads the default hosts file. A missing file is not an error.
func Load() (Config, error) {
	path, err := Path()
	if err != nil {
		return Config{}, err
	}
	return LoadFrom(path)
}

// LoadFrom reads one hosts file. A missing file yields an empty config.
func LoadFrom(path string) (Config, error) {
	cfg := Config{path: path, Hosts: map[string]*Host{}}
	data, err := os.ReadFile(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return cfg, nil
		}
		return cfg, fmt.Errorf("cannot read %s: %w", path, err)
	}
	if len(data) == 0 {
		return cfg, nil
	}
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return cfg, fmt.Errorf("cannot parse %s: %w", path, err)
	}
	if cfg.Hosts == nil {
		cfg.Hosts = map[string]*Host{}
	}
	return cfg, nil
}

// Save writes the file with 0600 permissions, creating the directory as 0700.
func (c Config) Save() error {
	path := c.path
	if path == "" {
		var err error
		path, err = Path()
		if err != nil {
			return err
		}
	}
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return fmt.Errorf("cannot create %s: %w", dir, err)
	}
	data, err := yaml.Marshal(c)
	if err != nil {
		return fmt.Errorf("cannot encode hosts file: %w", err)
	}
	tmp, err := os.CreateTemp(dir, ".hosts-*.tmp")
	if err != nil {
		return fmt.Errorf("cannot create temporary hosts file: %w", err)
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)
	if err := tmp.Chmod(0o600); err != nil {
		tmp.Close()
		return err
	}
	if _, err := tmp.Write(data); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	if err := os.Rename(tmpName, path); err != nil {
		return fmt.Errorf("cannot replace %s: %w", path, err)
	}
	return nil
}

// Host returns one configured host.
func (c Config) Host(name string) (*Host, bool) {
	h, ok := c.Hosts[name]
	return h, ok && h != nil
}

// SetHost stores a host and makes it current.
func (c *Config) SetHost(name string, host *Host) {
	if c.Hosts == nil {
		c.Hosts = map[string]*Host{}
	}
	c.Hosts[name] = host
	c.Current = name
}

// RemoveHost deletes one host and moves Current when it pointed at that host.
func (c *Config) RemoveHost(name string) bool {
	if c.Hosts == nil {
		return false
	}
	if _, ok := c.Hosts[name]; !ok {
		return false
	}
	delete(c.Hosts, name)
	if c.Current == name {
		c.Current = ""
	}
	return true
}

// HostnameFromURL derives the hosts.yml key from a server URL (host:port).
func HostnameFromURL(raw string) string {
	u, err := url.Parse(raw)
	if err != nil || u.Host == "" {
		return strings.TrimSpace(raw)
	}
	return u.Host
}
