// Package filedata loads --from-file JSON or YAML request bodies.
package filedata

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"

	"gopkg.in/yaml.v3"
)

// Load reads a JSON or YAML object from path ("-" means stdin).
func Load(path string) (map[string]any, error) {
	var (
		data []byte
		err  error
	)
	if path == "-" {
		data, err = io.ReadAll(os.Stdin)
	} else {
		data, err = os.ReadFile(path)
	}
	if err != nil {
		return nil, fmt.Errorf("cannot read %s: %w", path, err)
	}

	trimmed := bytes.TrimSpace(data)
	if len(trimmed) == 0 {
		return nil, fmt.Errorf("%s is empty", path)
	}

	out := map[string]any{}
	if trimmed[0] == '{' {
		decoder := json.NewDecoder(bytes.NewReader(trimmed))
		decoder.UseNumber()
		if err := decoder.Decode(&out); err != nil {
			return nil, fmt.Errorf("cannot parse %s as JSON: %w", path, err)
		}
		return out, nil
	}
	if err := yaml.Unmarshal(trimmed, &out); err != nil {
		return nil, fmt.Errorf("cannot parse %s as YAML: %w", path, err)
	}
	if out == nil {
		return nil, fmt.Errorf("%s does not contain an object", path)
	}
	return out, nil
}

// RequireString returns a required string field.
func RequireString(body map[string]any, field string) (string, error) {
	value, ok := body[field]
	if !ok || value == nil {
		return "", fmt.Errorf("missing required field %q", field)
	}
	text, ok := value.(string)
	if !ok || text == "" {
		return "", fmt.Errorf("field %q must be a non-empty string", field)
	}
	return text, nil
}
