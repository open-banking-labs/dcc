package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Overrides are the command-line flags that affect resolution.
type Overrides struct {
	Hostname   string
	ServerURL  string
	APIVersion string
}

// Resolved is the effective connection configuration for one invocation.
type Resolved struct {
	Hostname              string
	ServerURL             string
	Token                 string
	TokenSource           string
	APIVersion            string
	ConnectTimeout        time.Duration
	RequestTimeout        time.Duration
	InsecureSkipTLSVerify bool
}

// Resolve applies flags > environment > hosts.yml > defaults.
func Resolve(cfg Config, ov Overrides) (Resolved, error) {
	hostname := firstNonEmpty(ov.Hostname, os.Getenv("DCC_HOSTNAME"), cfg.Current)
	host, hasHost := cfg.Host(hostname)

	serverURL := firstNonEmpty(ov.ServerURL, os.Getenv("DCC_SERVER_URL"))
	if serverURL == "" && hasHost {
		serverURL = host.ServerURL
	}
	if serverURL == "" {
		serverURL = DefaultServerURL
	}
	if hostname == "" {
		hostname = HostnameFromURL(serverURL)
	}

	apiVersion := firstNonEmpty(ov.APIVersion, os.Getenv("DCC_API_VERSION"))
	if apiVersion == "" && hasHost {
		apiVersion = host.APIVersion
	}
	if apiVersion == "" {
		apiVersion = DefaultAPIVersion
	}

	token := strings.TrimSpace(os.Getenv("DCC_TOKEN"))
	tokenSource := "none"
	if token != "" {
		tokenSource = "env DCC_TOKEN"
	} else if hasHost && strings.TrimSpace(host.Token) != "" {
		token = strings.TrimSpace(host.Token)
		tokenSource = "hosts.yml"
	}

	connectSeconds := DefaultConnectSeconds
	if hasHost && host.ConnectTimeoutSeconds > 0 {
		connectSeconds = host.ConnectTimeoutSeconds
	}
	connectSeconds, err := envSeconds("DCC_CONNECT_TIMEOUT_SECONDS", connectSeconds)
	if err != nil {
		return Resolved{}, err
	}

	requestSeconds := DefaultRequestSeconds
	if hasHost && host.RequestTimeoutSeconds > 0 {
		requestSeconds = host.RequestTimeoutSeconds
	}
	requestSeconds, err = envSeconds("DCC_REQUEST_TIMEOUT_SECONDS", requestSeconds)
	if err != nil {
		return Resolved{}, err
	}

	return Resolved{
		Hostname:              hostname,
		ServerURL:             serverURL,
		Token:                 token,
		TokenSource:           tokenSource,
		APIVersion:            apiVersion,
		ConnectTimeout:        time.Duration(connectSeconds) * time.Second,
		RequestTimeout:        time.Duration(requestSeconds) * time.Second,
		InsecureSkipTLSVerify: hasHost && host.InsecureSkipTLSVerify,
	}, nil
}

func envSeconds(name string, fallback int) (int, error) {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback, nil
	}
	seconds, err := strconv.Atoi(value)
	if err != nil || seconds <= 0 {
		return 0, fmt.Errorf("invalid value for %s: %s", name, value)
	}
	return seconds, nil
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}
