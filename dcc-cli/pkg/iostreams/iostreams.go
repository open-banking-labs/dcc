// Package iostreams bundles the three standard streams plus terminal facts that
// commands use to choose between human and machine output. It mirrors the
// equivalent package in cli/cli so command code never touches os.Stdout directly.
package iostreams

import (
	"io"
	"os"
)

// IOStreams is the injectable stream bundle shared by every command.
type IOStreams struct {
	In     io.ReadCloser
	Out    io.Writer
	ErrOut io.Writer

	colorEnabled bool
	stdinTTY     bool
	stdoutTTY    bool
}

// System builds the streams for the real process.
func System() *IOStreams {
	return &IOStreams{
		In:           os.Stdin,
		Out:          os.Stdout,
		ErrOut:       os.Stderr,
		colorEnabled: os.Getenv("NO_COLOR") == "" && isTerminal(os.Stdout),
		stdinTTY:     isTerminal(os.Stdin),
		stdoutTTY:    isTerminal(os.Stdout),
	}
}

// SetColorEnabled forces colour on or off, overriding TTY detection.
func (s *IOStreams) SetColorEnabled(enabled bool) { s.colorEnabled = enabled }

// ColorEnabled reports whether ANSI colour may be written to Out.
func (s *IOStreams) ColorEnabled() bool { return s.colorEnabled }

// IsStdinTTY reports whether stdin is an interactive terminal.
func (s *IOStreams) IsStdinTTY() bool { return s.stdinTTY }

// IsStdoutTTY reports whether stdout is an interactive terminal.
func (s *IOStreams) IsStdoutTTY() bool { return s.stdoutTTY }

func isTerminal(f *os.File) bool {
	info, err := f.Stat()
	if err != nil {
		return false
	}
	return info.Mode()&os.ModeCharDevice != 0
}
