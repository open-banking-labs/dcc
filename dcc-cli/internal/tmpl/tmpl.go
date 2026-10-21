// Package tmpl renders decoded JSON with a Go text/template.
package tmpl

import (
	"fmt"
	"io"
	"text/template"
)

// Render executes text against data. Missing map keys are an error so typos in
// --template fail loudly instead of printing "<no value>".
func Render(w io.Writer, text string, data any) error {
	t, err := template.New("output").Option("missingkey=error").Parse(text)
	if err != nil {
		return fmt.Errorf("invalid --template: %w", err)
	}
	if err := t.Execute(w, data); err != nil {
		return fmt.Errorf("cannot render --template: %w", err)
	}
	return nil
}
