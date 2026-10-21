// Package editor opens $GIT_EDITOR/$VISUAL/$EDITOR for change notes.
package editor

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
)

const template = `# Write a commit-message-formatted change note.
#
#   <type>(<scope>): <summary>
#
#   <optional body>
#
# type: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert
# Lines starting with '#' are removed.
`

// Edit opens the user's editor with a template and returns the edited text.
func Edit() (string, error) {
	file, err := os.CreateTemp("", "dcc-message-*.txt")
	if err != nil {
		return "", fmt.Errorf("cannot create message file: %w", err)
	}
	defer os.Remove(file.Name())
	if _, err := file.WriteString(template); err != nil {
		file.Close()
		return "", err
	}
	if err := file.Close(); err != nil {
		return "", err
	}

	command := firstNonEmpty(os.Getenv("GIT_EDITOR"), os.Getenv("VISUAL"), os.Getenv("EDITOR"), "vi")
	// The editor value may contain arguments, so run it through sh with the
	// file passed as $1 (quoted by the shell, not concatenated).
	process := exec.Command("sh", "-c", command+` "$1"`, "sh", file.Name())
	process.Stdin = os.Stdin
	process.Stdout = os.Stdout
	process.Stderr = os.Stderr
	if err := process.Run(); err != nil {
		return "", fmt.Errorf("editor %q failed: %w", command, err)
	}

	data, err := os.ReadFile(file.Name())
	if err != nil {
		return "", fmt.Errorf("cannot read message file: %w", err)
	}
	return stripComments(string(data)), nil
}

func stripComments(text string) string {
	lines := strings.Split(strings.ReplaceAll(text, "\r\n", "\n"), "\n")
	kept := make([]string, 0, len(lines))
	for _, line := range lines {
		if strings.HasPrefix(strings.TrimSpace(line), "#") {
			continue
		}
		kept = append(kept, line)
	}
	return strings.TrimSpace(strings.Join(kept, "\n"))
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return strings.TrimSpace(value)
		}
	}
	return ""
}
