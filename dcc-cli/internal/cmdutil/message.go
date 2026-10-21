package cmdutil

import (
	"fmt"
	"os"
	"strings"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/changemessage"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/editor"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/iostreams"
)

// MessageOptions carries the repeated -m/--message flags and --message-file.
type MessageOptions struct {
	Messages    []string
	MessageFile string
}

// AddMessageFlags registers the change-note flags on a write command.
func AddMessageFlags(cmd *cobra.Command, opts *MessageOptions) {
	cmd.Flags().StringArrayVarP(&opts.Messages, "message", "m", nil,
		"Change note in commit-message format (repeatable)")
	cmd.Flags().StringVarP(&opts.MessageFile, "message-file", "F", "",
		"Read the change note from a file (\"-\" = stdin)")
}

// Resolve reads, validates and returns the change note.
func (o *MessageOptions) Resolve(io *iostreams.IOStreams) (changemessage.Message, error) {
	if len(o.Messages) > 0 && o.MessageFile != "" {
		return changemessage.Message{}, UsageError("use either --message or --message-file, not both")
	}

	var raw string
	switch {
	case len(o.Messages) > 0:
		raw = strings.Join(o.Messages, "\n\n")
	case o.MessageFile != "":
		var err error
		raw, err = readMessageFile(o.MessageFile)
		if err != nil {
			return changemessage.Message{}, Failure("%s", err)
		}
	case io.IsStdinTTY():
		var err error
		raw, err = editor.Edit()
		if err != nil {
			return changemessage.Message{}, Failure("%s", err)
		}
	default:
		return changemessage.Message{}, UsageError(
			"a change note is required: pass -m/--message or --message-file")
	}

	message, err := changemessage.Parse(raw)
	if err != nil {
		return changemessage.Message{}, UsageError("%s", err)
	}
	return message, nil
}

func readMessageFile(path string) (string, error) {
	if path == "-" {
		return ReadAll(os.Stdin)
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("cannot read %s: %w", path, err)
	}
	return strings.TrimSpace(string(data)), nil
}
