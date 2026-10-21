// Package confirmation implements the interactive gate for destructive commands.
package confirmation

import (
	"errors"
	"fmt"
	"io"
	"strings"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/iostreams"
)

// Confirm asks for a yes/no answer unless yes is true or stdin is not a TTY.
func Confirm(streams *iostreams.IOStreams, prompt string, yes bool) error {
	if yes {
		return nil
	}
	if !streams.IsStdinTTY() {
		return cmdutil.UsageError("%s: refusing to continue without a terminal; pass --yes", prompt)
	}
	fmt.Fprintf(streams.ErrOut, "%s [y/N]: ", prompt)
	answer, err := cmdutil.ReadLine(streams.In)
	if err != nil && !errors.Is(err, io.EOF) {
		return cmdutil.Failure("cannot read confirmation: %s", err)
	}
	switch strings.ToLower(strings.TrimSpace(answer)) {
	case "y", "yes":
		return nil
	default:
		return cmdutil.Failure("aborted")
	}
}
