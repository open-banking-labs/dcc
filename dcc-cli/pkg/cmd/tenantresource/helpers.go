package tenantresource

import (
	"encoding/json"
	"fmt"
	"io"
	"strconv"

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

func responseID(response any) string {
	object, ok := response.(map[string]any)
	if !ok {
		return ""
	}
	return formatValue(object["id"])
}
