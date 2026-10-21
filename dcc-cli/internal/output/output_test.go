package output

import (
	"bytes"
	"io"
	"strings"
	"testing"
)

func TestPrintJSONProjection(t *testing.T) {
	var buf bytes.Buffer
	opts := Options{JSON: "id,content.name"}
	value := map[string]any{
		"id":      1,
		"content": map[string]any{"name": "payment", "extra": true},
		"other":   "hidden",
	}
	if err := opts.Print(&buf, value, func(io.Writer) error {
		t.Fatal("human printer called for --json")
		return nil
	}); err != nil {
		t.Fatalf("Print: %v", err)
	}
	out := buf.String()
	if !strings.Contains(out, `"id": 1`) || !strings.Contains(out, `"name": "payment"`) {
		t.Fatalf("selected fields missing: %s", out)
	}
	if strings.Contains(out, "extra") || strings.Contains(out, "hidden") {
		t.Fatalf("unselected fields leaked: %s", out)
	}
}

func TestPrintJSONUnknownField(t *testing.T) {
	var buf bytes.Buffer
	opts := Options{JSON: "missing"}
	err := opts.Print(&buf, map[string]any{"id": 1}, func(io.Writer) error { return nil })
	if err == nil || !strings.Contains(err.Error(), "unknown field") {
		t.Fatalf("expected unknown-field error, got %v", err)
	}
}

func TestPrintJQ(t *testing.T) {
	var buf bytes.Buffer
	opts := Options{JQ: ".items[] | .code"}
	value := map[string]any{"items": []any{
		map[string]any{"code": "A"},
		map[string]any{"code": "B"},
	}}
	if err := opts.Print(&buf, value, func(io.Writer) error { return nil }); err != nil {
		t.Fatalf("Print: %v", err)
	}
	if got := strings.TrimSpace(buf.String()); got != "\"A\"\n\"B\"" {
		t.Fatalf("jq output = %q", got)
	}
}

func TestPrintTemplate(t *testing.T) {
	var buf bytes.Buffer
	opts := Options{Template: "{{.code}}/{{.id}}"}
	if err := opts.Print(&buf, map[string]any{"code": "STD", "id": float64(7)}, func(io.Writer) error { return nil }); err != nil {
		t.Fatalf("Print: %v", err)
	}
	if got := buf.String(); got != "STD/7" {
		t.Fatalf("template output = %q", got)
	}
}

func TestPrintHumanByDefault(t *testing.T) {
	var buf bytes.Buffer
	if err := (Options{}).Print(&buf, map[string]any{"id": 1}, func(w io.Writer) error {
		_, err := io.WriteString(w, "human output")
		return err
	}); err != nil {
		t.Fatalf("Print: %v", err)
	}
	if got := buf.String(); got != "human output" {
		t.Fatalf("output = %q", got)
	}
}
