package cmdutil

import (
	"bufio"
	"io"
	"strings"
)

// ReadLine reads one trimmed line from r.
func ReadLine(r io.Reader) (string, error) {
	scanner := bufio.NewScanner(r)
	if scanner.Scan() {
		return strings.TrimSpace(scanner.Text()), nil
	}
	if err := scanner.Err(); err != nil {
		return "", err
	}
	return "", io.EOF
}

// ReadAll reads and trims all input, used by --token-stdin and --message-file -.
func ReadAll(r io.Reader) (string, error) {
	data, err := io.ReadAll(r)
	if err != nil {
		return "", err
	}
	return strings.TrimSpace(string(data)), nil
}
