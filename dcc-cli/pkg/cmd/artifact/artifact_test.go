package artifact_test

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/config"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/root"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/iostreams"
)

func runCommand(t *testing.T, server *httptest.Server, args ...string) (string, error) {
	t.Helper()
	var stdout, stderr bytes.Buffer
	streams := &iostreams.IOStreams{
		In:     io.NopCloser(strings.NewReader("")),
		Out:    &stdout,
		ErrOut: &stderr,
	}
	f := &factory.Factory{
		IOStreams:  streams,
		HttpClient: func() (*http.Client, error) { return server.Client(), nil },
		Config: func() (config.Config, error) {
			return config.Config{
				Current: "test",
				Hosts: map[string]*config.Host{
					"test": {ServerURL: server.URL, Token: "tok"},
				},
			}, nil
		},
	}
	cmd := root.NewCmdRoot(f)
	cmd.SetArgs(args)
	cmd.SetOut(&stdout)
	cmd.SetErr(&stderr)
	err := cmd.Execute()
	return stdout.String(), err
}

func TestStandardListUsesPageEndpoint(t *testing.T) {
	var gotPath, gotQuery, gotAuth string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotQuery = r.URL.RawQuery
		gotAuth = r.Header.Get("Authorization")
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"content":[{"id":1,"code":"STD","name":"Payment"}],"totalElements":1}`)
	}))
	defer server.Close()

	out, err := runCommand(t, server, "standard", "list", "--environment-id", "3", "--json=id,code")
	if err != nil {
		t.Fatalf("execute: %v", err)
	}
	if gotPath != "/api/standards/page" {
		t.Fatalf("path = %q", gotPath)
	}
	if !strings.Contains(gotQuery, "environmentId=3") || !strings.Contains(gotQuery, "page=0") || !strings.Contains(gotQuery, "size=20") {
		t.Fatalf("query = %q", gotQuery)
	}
	if gotAuth != "Bearer tok" {
		t.Fatalf("Authorization = %q", gotAuth)
	}
	if !strings.Contains(out, `"id": 1`) || !strings.Contains(out, `"code": "STD"`) {
		t.Fatalf("projected output = %s", out)
	}
	if strings.Contains(out, `"name"`) {
		t.Fatalf("unselected field leaked: %s", out)
	}
}

func TestStandardView(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/standards/7" {
			t.Errorf("path = %q", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"id":7,"code":"STD","status":"DRAFT","currentVersion":"1.0.0","content":{"name":"Payment"}}`)
	}))
	defer server.Close()

	out, err := runCommand(t, server, "standard", "view", "7", "--json")
	if err != nil {
		t.Fatalf("execute: %v", err)
	}
	if !strings.Contains(out, `"code": "STD"`) || !strings.Contains(out, `"name": "Payment"`) {
		t.Fatalf("output = %s", out)
	}
}

func TestStandardHashSendsCommandBody(t *testing.T) {
	var gotPath string
	var gotBody map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"hash":"sha256:abc"}`)
	}))
	defer server.Close()

	out, err := runCommand(t, server, "standard", "hash", "7", "--version", "1.0.0", "--json")
	if err != nil {
		t.Fatalf("execute: %v", err)
	}
	if gotPath != "/cli/v1/hash" {
		t.Fatalf("path = %q", gotPath)
	}
	if gotBody["type"] != "DATA_STANDARD" || gotBody["modelId"] != float64(7) || gotBody["version"] != "1.0.0" {
		t.Fatalf("body = %#v", gotBody)
	}
	if !strings.Contains(out, `"hash": "sha256:abc"`) {
		t.Fatalf("output = %s", out)
	}
}

func TestDriftExitsNonZero(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/cli/v1/drift" {
			t.Errorf("path = %q", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"drifted":[{"artifactType":"TABLE_STRUCTURE","code":"T_ORDER","version":"1.0.0"}]}`)
	}))
	defer server.Close()

	out, err := runCommand(t, server, "drift", "--environment-id", "1", "--application-id", "2")
	if err == nil {
		t.Fatal("expected a non-zero exit for drift")
	}
	var exitErr *cmdutil.ExitError
	if !errors.As(err, &exitErr) || exitErr.Code != 1 {
		t.Fatalf("expected exit 1, got %v", err)
	}
	if !strings.Contains(out, "T_ORDER") {
		t.Fatalf("output = %s", out)
	}
}

func TestAuditVerifyExitsNonZeroWhenBroken(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/cli/v1/audit/verify" {
			t.Errorf("path = %q", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"intact":false,"problems":["entry 3: hash mismatch"]}`)
	}))
	defer server.Close()

	out, err := runCommand(t, server, "audit", "verify", "--entity-type", "DATA_STANDARD", "--entity-id", "7")
	if err == nil {
		t.Fatal("expected a non-zero exit for a broken chain")
	}
	if !strings.Contains(out, "hash chain broken") || !strings.Contains(out, "hash mismatch") {
		t.Fatalf("output = %s", out)
	}
}
