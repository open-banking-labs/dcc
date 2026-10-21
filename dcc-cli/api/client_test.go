package api

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestCommandSendsAuthBodyAndChangeMessage(t *testing.T) {
	var (
		gotPath   string
		gotAuth   string
		gotReason string
		gotBody   map[string]any
	)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotAuth = r.Header.Get("Authorization")
		gotReason = r.Header.Get("X-DCC-Change-Reason")
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"hash":"abc"}`)
	}))
	defer server.Close()

	client, err := New(Options{
		ServerURL:  server.URL,
		Token:      "tok",
		APIVersion: "v2",
		HTTPClient: server.Client(),
	})
	if err != nil {
		t.Fatalf("New: %v", err)
	}

	var out struct {
		Hash string `json:"hash"`
	}
	err = client.Do(context.Background(), Request{
		Method:        http.MethodPost,
		Path:          client.CommandPath("hash"),
		Body:          map[string]any{"type": "DATA_STANDARD", "modelId": 1},
		ChangeMessage: "feat(standard): add a field",
		Out:           &out,
	})
	if err != nil {
		t.Fatalf("Do: %v", err)
	}
	if gotPath != "/cli/v2/hash" {
		t.Fatalf("path = %q", gotPath)
	}
	if gotAuth != "Bearer tok" {
		t.Fatalf("Authorization = %q", gotAuth)
	}
	if gotReason != "feat(standard): add a field" {
		t.Fatalf("X-DCC-Change-Reason = %q", gotReason)
	}
	if gotBody["type"] != "DATA_STANDARD" {
		t.Fatalf("body = %#v", gotBody)
	}
	if out.Hash != "abc" {
		t.Fatalf("decoded hash = %q", out.Hash)
	}
}

func TestErrorEnvelope(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusForbidden)
		_, _ = io.WriteString(w, `{"status":403,"error":"forbidden","message":"missing artifact.read"}`)
	}))
	defer server.Close()

	client, err := New(Options{ServerURL: server.URL, HTTPClient: server.Client()})
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	err = client.Get(context.Background(), "standards/1", nil, nil)
	if err == nil {
		t.Fatal("expected an error")
	}
	var apiErr *Error
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected *api.Error, got %T", err)
	}
	if apiErr.StatusCode != http.StatusForbidden || apiErr.Message != "missing artifact.read" {
		t.Fatalf("unexpected error: %+v", apiErr)
	}
	if !IsAuthError(err) {
		t.Fatal("403 should count as an auth error")
	}
}

func TestNoContentResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client, err := New(Options{ServerURL: server.URL, HTTPClient: server.Client()})
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	if err := client.Delete(context.Background(), "standards/1", nil, nil); err != nil {
		t.Fatalf("Delete: %v", err)
	}
}

func TestQueryEncoding(t *testing.T) {
	var gotQuery string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotQuery = r.URL.RawQuery
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `[]`)
	}))
	defer server.Close()

	client, err := New(Options{ServerURL: server.URL, HTTPClient: server.Client()})
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	query := map[string][]string{"keyword": {"pay"}, "size": {"5"}}
	var out []any
	if err := client.Get(context.Background(), "standards/page", query, &out); err != nil {
		t.Fatalf("Get: %v", err)
	}
	if gotQuery != "keyword=pay&size=5" {
		t.Fatalf("query = %q", gotQuery)
	}
}
