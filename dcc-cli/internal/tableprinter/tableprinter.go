// Package tableprinter renders aligned tables on a TTY and tab-separated rows
// otherwise, following gh's convention that piped output stays parseable.
package tableprinter

import (
	"fmt"
	"io"
	"strings"
	"text/tabwriter"
)

// Table accumulates rows and renders them once.
type Table struct {
	w       io.Writer
	tty     bool
	headers []string
	rows    [][]string
}

// New creates a table writer. tty selects aligned vs TSV output.
func New(w io.Writer, tty bool) *Table {
	return &Table{w: w, tty: tty}
}

// Header sets the column titles. They are only printed on a TTY.
func (t *Table) Header(columns ...string) {
	t.headers = columns
}

// Row appends one row.
func (t *Table) Row(columns ...string) {
	t.rows = append(t.rows, columns)
}

// Len reports the number of data rows.
func (t *Table) Len() int { return len(t.rows) }

// Render writes the table.
func (t *Table) Render() error {
	if !t.tty {
		for _, row := range t.rows {
			if _, err := fmt.Fprintln(t.w, strings.Join(row, "\t")); err != nil {
				return err
			}
		}
		return nil
	}

	tw := tabwriter.NewWriter(t.w, 0, 0, 2, ' ', 0)
	if len(t.headers) > 0 {
		upper := make([]string, len(t.headers))
		for i, header := range t.headers {
			upper[i] = strings.ToUpper(header)
		}
		if _, err := fmt.Fprintln(tw, strings.Join(upper, "\t")); err != nil {
			return err
		}
	}
	for _, row := range t.rows {
		if _, err := fmt.Fprintln(tw, strings.Join(row, "\t")); err != nil {
			return err
		}
	}
	return tw.Flush()
}
