// Package jsonfield projects decoded JSON values onto a comma-separated field
// list, matching gh's --json behaviour.
package jsonfield

import (
	"fmt"
	"sort"
	"strings"
)

// Parse splits "id,code,content.name" into fields, trimming blanks.
func Parse(value string) []string {
	parts := strings.Split(value, ",")
	fields := make([]string, 0, len(parts))
	for _, part := range parts {
		if field := strings.TrimSpace(part); field != "" {
			fields = append(fields, field)
		}
	}
	return fields
}

// Project keeps only the requested fields. Dotted paths build nested objects.
// Arrays are projected element-wise.
func Project(value any, fields []string) (any, error) {
	if len(fields) == 0 {
		return value, nil
	}
	switch typed := value.(type) {
	case []any:
		return projectArray(typed, fields)
	case map[string]any:
		// Spring Page envelope: project each item and keep the page metadata.
		if content, ok := typed["content"].([]any); ok {
			if _, isPage := typed["totalElements"]; isPage {
				projected, err := projectArray(content, fields)
				if err != nil {
					return nil, err
				}
				out := make(map[string]any, len(typed))
				for key, item := range typed {
					out[key] = item
				}
				out["content"] = projected
				return out, nil
			}
		}
		return projectObject(typed, fields)
	default:
		return nil, fmt.Errorf("--json field selection needs an object or array, got %T", value)
	}
}

func projectArray(items []any, fields []string) ([]any, error) {
	out := make([]any, 0, len(items))
	for _, item := range items {
		projected, err := projectObject(item, fields)
		if err != nil {
			return nil, err
		}
		out = append(out, projected)
	}
	return out, nil
}

func projectObject(value any, fields []string) (map[string]any, error) {
	source, ok := value.(map[string]any)
	if !ok {
		return nil, fmt.Errorf("--json field selection needs objects, got %T", value)
	}
	out := map[string]any{}
	for _, field := range fields {
		if err := copyPath(out, source, strings.Split(field, "."), field); err != nil {
			return nil, err
		}
	}
	return out, nil
}

func copyPath(dst, src map[string]any, parts []string, full string) error {
	key := parts[0]
	value, ok := src[key]
	if !ok {
		return fmt.Errorf("unknown field %q; available fields: %s", full, strings.Join(sortedKeys(src), ", "))
	}
	if len(parts) == 1 {
		dst[key] = value
		return nil
	}
	child, ok := value.(map[string]any)
	if !ok {
		return fmt.Errorf("field %q is not an object, cannot select %q", key, full)
	}
	nested, _ := dst[key].(map[string]any)
	if nested == nil {
		nested = map[string]any{}
		dst[key] = nested
	}
	return copyPath(nested, child, parts[1:], full)
}

func sortedKeys(value map[string]any) []string {
	keys := make([]string, 0, len(value))
	for key := range value {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	return keys
}
