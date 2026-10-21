// Package api is the only place that knows the wire format. It speaks both the
// resource-oriented /api group and the command-oriented /cli/<version> group.
package api

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

const maxResponseBytes = 32 << 20 // 32 MiB

// Options configures a Client. HTTPClient is injectable for tests.
type Options struct {
	ServerURL  string
	Token      string
	APIVersion string
	HTTPClient *http.Client
}

// Client is a thin JSON-over-HTTP transport.
type Client struct {
	serverURL  string
	apiVersion string
	token      string
	http       *http.Client
}

// New builds a client. The server URL is required; an empty API version means v1.
func New(opts Options) (*Client, error) {
	serverURL := strings.TrimRight(strings.TrimSpace(opts.ServerURL), "/")
	if serverURL == "" {
		return nil, errors.New("server URL is required")
	}
	version := strings.TrimSpace(opts.APIVersion)
	if version == "" {
		version = "v1"
	}
	httpClient := opts.HTTPClient
	if httpClient == nil {
		httpClient = &http.Client{}
	}
	return &Client{
		serverURL:  serverURL,
		apiVersion: version,
		token:      strings.TrimSpace(opts.Token),
		http:       httpClient,
	}, nil
}

// ServerURL is the resolved base URL without a trailing slash.
func (c *Client) ServerURL() string { return c.serverURL }

// APIVersion is the /cli path version.
func (c *Client) APIVersion() string { return c.apiVersion }

// APIPath returns a path under /api.
func (c *Client) APIPath(path string) string {
	return "/api/" + strings.TrimLeft(path, "/")
}

// CommandPath returns a path under /cli/<version>.
func (c *Client) CommandPath(command string) string {
	return "/cli/" + c.apiVersion + "/" + strings.TrimLeft(command, "/")
}

// Request describes one JSON call.
type Request struct {
	Method string
	Path   string
	Query  url.Values
	Body   any
	// ChangeMessage is sent as X-DCC-Change-Reason on maintenance writes.
	ChangeMessage string
	// Out receives the decoded JSON response when non-nil.
	Out any
}

// Do performs the request and decodes the response.
func (c *Client) Do(ctx context.Context, req Request) error {
	method := req.Method
	if method == "" {
		method = http.MethodGet
	}
	endpoint := c.serverURL + req.Path
	if len(req.Query) > 0 {
		endpoint += "?" + req.Query.Encode()
	}

	var body io.Reader
	if req.Body != nil {
		encoded, err := json.Marshal(req.Body)
		if err != nil {
			return fmt.Errorf("cannot encode request body: %w", err)
		}
		body = bytes.NewReader(encoded)
	}

	httpReq, err := http.NewRequestWithContext(ctx, method, endpoint, body)
	if err != nil {
		return fmt.Errorf("cannot build request: %w", err)
	}
	httpReq.Header.Set("Accept", "application/json")
	if req.Body != nil {
		httpReq.Header.Set("Content-Type", "application/json")
	}
	if c.token != "" {
		httpReq.Header.Set("Authorization", "Bearer "+c.token)
	}
	if req.ChangeMessage != "" {
		httpReq.Header.Set("X-DCC-Change-Reason", req.ChangeMessage)
	}

	resp, err := c.http.Do(httpReq)
	if err != nil {
		return fmt.Errorf("cannot reach %s: %w", endpoint, err)
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return fmt.Errorf("cannot read response from %s: %w", endpoint, err)
	}
	if resp.StatusCode >= http.StatusBadRequest {
		return newError(resp.StatusCode, data)
	}
	if req.Out == nil || len(bytes.TrimSpace(data)) == 0 {
		return nil
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.UseNumber()
	if err := decoder.Decode(req.Out); err != nil {
		return fmt.Errorf("invalid response from %s: %w", endpoint, err)
	}
	return nil
}
func (c *Client) Get(ctx context.Context, path string, query url.Values, out any) error {
	return c.Do(ctx, Request{Method: http.MethodGet, Path: c.APIPath(path), Query: query, Out: out})
}

// Post performs a POST under /api.
func (c *Client) Post(ctx context.Context, path string, query url.Values, body, out any) error {
	return c.Do(ctx, Request{Method: http.MethodPost, Path: c.APIPath(path), Query: query, Body: body, Out: out})
}

// Put performs a PUT under /api.
func (c *Client) Put(ctx context.Context, path string, query url.Values, body, out any) error {
	return c.Do(ctx, Request{Method: http.MethodPut, Path: c.APIPath(path), Query: query, Body: body, Out: out})
}

// Delete performs a DELETE under /api.
func (c *Client) Delete(ctx context.Context, path string, query url.Values, out any) error {
	return c.Do(ctx, Request{Method: http.MethodDelete, Path: c.APIPath(path), Query: query, Out: out})
}

// Command performs a POST against /cli/<version>/<command>.
func (c *Client) Command(ctx context.Context, command string, body, out any) error {
	return c.Do(ctx, Request{
		Method: http.MethodPost,
		Path:   c.CommandPath(command),
		Body:   body,
		Out:    out,
	})
}

// Error is a non-2xx response with the backend's error envelope unwrapped.
type Error struct {
	StatusCode int
	Message    string
	Body       []byte
}

func (e *Error) Error() string {
	if e.Message != "" {
		return e.Message
	}
	return fmt.Sprintf("server returned HTTP %d", e.StatusCode)
}

func newError(status int, data []byte) *Error {
	var envelope struct {
		Message string `json:"message"`
		Error   string `json:"error"`
	}
	_ = json.Unmarshal(data, &envelope)
	message := strings.TrimSpace(envelope.Message)
	if message == "" {
		message = strings.TrimSpace(string(data))
	}
	if message == "" {
		message = http.StatusText(status)
	}
	return &Error{StatusCode: status, Message: message, Body: data}
}

// IsAuthError reports whether err is a 401 or 403 API response.
func IsAuthError(err error) bool {
	var apiErr *Error
	return errors.As(err, &apiErr) && (apiErr.StatusCode == http.StatusUnauthorized || apiErr.StatusCode == http.StatusForbidden)
}
