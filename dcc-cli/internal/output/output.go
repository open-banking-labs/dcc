// Package output centralises the --json / --jq / --template contract.
package output

import (
	"encoding/json"
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/jq"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/jsonfield"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/tmpl"
)

// Options are the output flags shared by every command.
type Options struct {
	JSON     string
	JQ       string
	Template string
}

// FromCommand reads the persistent output flags.
func FromCommand(cmd *cobra.Command) (Options, error) {
	jsonValue, err := cmd.Flags().GetString("json")
	if err != nil {
		return Options{}, err
	}
	jqExpr, err := cmd.Flags().GetString("jq")
	if err != nil {
		return Options{}, err
	}
	templateText, err := cmd.Flags().GetString("template")
	if err != nil {
		return Options{}, err
	}
	if jqExpr != "" && templateText != "" {
		return Options{}, cmdutil.UsageError("--jq and --template are mutually exclusive")
	}
	return Options{JSON: jsonValue, JQ: jqExpr, Template: templateText}, nil
}

// Machine reports whether the caller asked for machine-readable output.
func (o Options) Machine() bool {
	return o.JSON != "" || o.JQ != "" || o.Template != ""
}

// Print writes human output by default, or the machine representation when one
// of the output flags is set.
func (o Options) Print(w io.Writer, value any, human func(io.Writer) error) error {
	if !o.Machine() {
		return human(w)
	}
	normalized, err := Normalize(value)
	if err != nil {
		return err
	}
	if o.JSON != "" && o.JSON != "true" {
		normalized, err = jsonfield.Project(normalized, jsonfield.Parse(o.JSON))
		if err != nil {
			return cmdutil.UsageError("%s", err)
		}
	}
	if o.JQ != "" {
		return jq.Filter(w, o.JQ, normalized)
	}
	if o.Template != "" {
		return tmpl.Render(w, o.Template, normalized)
	}
	return WriteJSON(w, normalized)
}

// Normalize converts typed values to map[string]any / []any.
func Normalize(value any) (any, error) {
	data, err := json.Marshal(value)
	if err != nil {
		return nil, fmt.Errorf("cannot encode output: %w", err)
	}
	var normalized any
	if err := json.Unmarshal(data, &normalized); err != nil {
		return nil, fmt.Errorf("cannot normalise output: %w", err)
	}
	return normalized, nil
}

// WriteJSON writes indented JSON.
func WriteJSON(w io.Writer, value any) error {
	encoder := json.NewEncoder(w)
	encoder.SetIndent("", "  ")
	encoder.SetEscapeHTML(false)
	return encoder.Encode(value)
}

// KeyValues writes aligned "key: value" pairs.
func KeyValues(w io.Writer, pairs [][2]string) error {
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	for _, pair := range pairs {
		if _, err := fmt.Fprintf(tw, "%s:\t%s\n", pair[0], pair[1]); err != nil {
			return err
		}
	}
	return tw.Flush()
}

// CompactJSON renders a raw JSON value as a single-line string.
func CompactJSON(raw json.RawMessage) string {
	if len(raw) == 0 {
		return ""
	}
	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return string(raw)
	}
	data, err := json.Marshal(value)
	if err != nil {
		return string(raw)
	}
	return string(data)
}
