// Package jq applies a jq expression to decoded JSON and writes one encoded
// result per line, like `gh ... --jq`.
package jq

import (
	"encoding/json"
	"fmt"
	"io"

	"github.com/itchyny/gojq"
)

// Filter runs expr over input and writes each result as compact JSON.
func Filter(w io.Writer, expr string, input any) error {
	query, err := gojq.Parse(expr)
	if err != nil {
		return fmt.Errorf("invalid --jq expression: %w", err)
	}
	iter := query.Run(input)
	encoder := json.NewEncoder(w)
	encoder.SetEscapeHTML(false)
	for {
		value, ok := iter.Next()
		if !ok {
			break
		}
		if err, ok := value.(error); ok {
			return fmt.Errorf("--jq: %w", err)
		}
		if err := encoder.Encode(value); err != nil {
			return err
		}
	}
	return nil
}
