package artifact

import (
	"encoding/json"
	"fmt"
	"io"
	"strconv"
	"time"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/tableprinter"
)

func article(kind Kind) string {
	if kind.Article != "" {
		return kind.Article
	}
	return "a"
}

func parseID(args []string) (int64, error) {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil || id <= 0 {
		return 0, cmdutil.UsageError("invalid id %q: expected a positive integer", args[0])
	}
	return id, nil
}

func formatValue(value any) string {
	switch typed := value.(type) {
	case nil:
		return ""
	case string:
		return typed
	case json.Number:
		return typed.String()
	case float64:
		return strconv.FormatFloat(typed, 'f', -1, 64)
	case bool:
		return strconv.FormatBool(typed)
	default:
		encoded, err := json.Marshal(typed)
		if err != nil {
			return fmt.Sprint(typed)
		}
		return string(encoded)
	}
}

func renderTable(w io.Writer, tty bool, columns []Column, items []any) error {
	table := tableprinter.New(w, tty)
	headers := make([]string, len(columns))
	for i, column := range columns {
		headers[i] = column.Header
	}
	table.Header(headers...)
	for _, item := range items {
		object, _ := item.(map[string]any)
		row := make([]string, len(columns))
		for i, column := range columns {
			row[i] = formatValue(object[column.Key])
		}
		table.Row(row...)
	}
	return table.Render()
}

func extractItems(response any, page bool) []any {
	if page {
		object, ok := response.(map[string]any)
		if !ok {
			return nil
		}
		content, _ := object["content"].([]any)
		return content
	}
	items, _ := response.([]any)
	return items
}

func compactJSON(value any) string {
	if value == nil {
		return ""
	}
	encoded, err := json.Marshal(value)
	if err != nil {
		return fmt.Sprint(value)
	}
	return string(encoded)
}

func formatTime(value any) string {
	text, ok := value.(string)
	if !ok {
		return formatValue(value)
	}
	parsed, err := time.Parse(time.RFC3339Nano, text)
	if err != nil {
		return text
	}
	return parsed.UTC().Format(time.RFC3339)
}

func notEmpty(pairs [][2]string, label, value string) [][2]string {
	if value == "" {
		return pairs
	}
	return append(pairs, [2]string{label, value})
}
