package artifact_test

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
)

func TestCreateSendsChangeNoteAndBody(t *testing.T) {
	var (
		gotMethod string
		gotPath   string
		gotQuery  string
		gotReason string
		gotBody   map[string]any
	)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotMethod = r.Method
		gotPath = r.URL.Path
		gotQuery = r.URL.RawQuery
		gotReason = r.Header.Get("X-DCC-Change-Reason")
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"id":11,"code":"STD_PAY","status":"DRAFT","currentVersion":"1.0.0"}`)
	}))
	defer server.Close()

	bodyFile := filepath.Join(t.TempDir(), "standard.json")
	if err := os.WriteFile(bodyFile, []byte(`{"code":"STD_PAY","content":{"name":"Payment"}}`), 0o600); err != nil {
		t.Fatal(err)
	}

	out, err := runCommand(t, server,
		"standard", "create",
		"--environment-id", "1",
		"--application-id", "2",
		"--from-file", bodyFile,
		"-m", "feat(standard): add payment standard",
		"--json",
	)
	if err != nil {
		t.Fatalf("execute: %v", err)
	}
	if gotMethod != http.MethodPost || gotPath != "/api/standards" {
		t.Fatalf("request = %s %s", gotMethod, gotPath)
	}
	if !strings.Contains(gotQuery, "environmentId=1") || !strings.Contains(gotQuery, "applicationId=2") {
		t.Fatalf("query = %q", gotQuery)
	}
	if gotReason != "feat(standard): add payment standard" {
		t.Fatalf("X-DCC-Change-Reason = %q", gotReason)
	}
	if gotBody["code"] != "STD_PAY" {
		t.Fatalf("body = %#v", gotBody)
	}
	if !strings.Contains(out, `"id": 11`) {
		t.Fatalf("output = %s", out)
	}
}

func TestWriteRequiresCommitMessage(t *testing.T) {
	calls := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
	}))
	defer server.Close()

	bodyFile := filepath.Join(t.TempDir(), "standard.json")
	if err := os.WriteFile(bodyFile, []byte(`{"content":{"name":"Payment"}}`), 0o600); err != nil {
		t.Fatal(err)
	}

	_, err := runCommand(t, server,
		"standard", "edit", "7", "--from-file", bodyFile)
	if err == nil {
		t.Fatal("expected an error without a change note")
	}
	var exitErr *cmdutil.ExitError
	if !errors.As(err, &exitErr) || exitErr.Code != 2 {
		t.Fatalf("expected exit 2, got %v", err)
	}
	if calls != 0 {
		t.Fatal("no request should be sent without a valid note")
	}
}

func TestInvalidCommitMessageRejected(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("no request expected")
	}))
	defer server.Close()

	bodyFile := filepath.Join(t.TempDir(), "standard.json")
	if err := os.WriteFile(bodyFile, []byte(`{"content":{"name":"Payment"}}`), 0o600); err != nil {
		t.Fatal(err)
	}

	_, err := runCommand(t, server,
		"standard", "edit", "7", "--from-file", bodyFile, "-m", "just a note")
	if err == nil || !strings.Contains(err.Error(), "invalid change note") {
		t.Fatalf("expected a commit-message error, got %v", err)
	}
}

func TestDeleteRequiresYesOutsideTTY(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("no request expected without --yes")
	}))
	defer server.Close()

	_, err := runCommand(t, server,
		"standard", "delete", "7", "-m", "chore(standard): remove stale standard")
	if err == nil || !strings.Contains(err.Error(), "pass --yes") {
		t.Fatalf("expected a confirmation error, got %v", err)
	}
}

func TestDeleteWithYesSendsNote(t *testing.T) {
	var gotMethod, gotPath, gotReason string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotMethod = r.Method
		gotPath = r.URL.Path
		gotReason = r.Header.Get("X-DCC-Change-Reason")
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	out, err := runCommand(t, server,
		"standard", "delete", "7", "--yes", "-m", "chore(standard): remove stale standard")
	if err != nil {
		t.Fatalf("execute: %v", err)
	}
	if gotMethod != http.MethodDelete || gotPath != "/api/standards/7" {
		t.Fatalf("request = %s %s", gotMethod, gotPath)
	}
	if gotReason != "chore(standard): remove stale standard" {
		t.Fatalf("X-DCC-Change-Reason = %q", gotReason)
	}
	if !strings.Contains(out, "deleted standard #7") {
		t.Fatalf("output = %s", out)
	}
}

func TestStatusSendsTargetAndNote(t *testing.T) {
	var gotQuery, gotReason string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotQuery = r.URL.RawQuery
		gotReason = r.Header.Get("X-DCC-Change-Reason")
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"id":7,"code":"STD","status":"PUBLISHED"}`)
	}))
	defer server.Close()

	_, err := runCommand(t, server,
		"standard", "status", "7", "--target", "PUBLISHED", "-m", "docs(standard): publish standard")
	if err != nil {
		t.Fatalf("execute: %v", err)
	}
	if gotQuery != "target=PUBLISHED" {
		t.Fatalf("query = %q", gotQuery)
	}
	if gotReason != "docs(standard): publish standard" {
		t.Fatalf("X-DCC-Change-Reason = %q", gotReason)
	}
}
