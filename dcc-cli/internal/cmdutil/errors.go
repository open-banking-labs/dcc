// Package cmdutil holds small helpers shared by command packages.
package cmdutil

import (
	"errors"
	"fmt"
)

// ExitError carries the process exit code a failure should map to.
type ExitError struct {
	Code int
	Err  error
}

func (e *ExitError) Error() string {
	if e.Err == nil {
		return ""
	}
	return e.Err.Error()
}

func (e *ExitError) Unwrap() error { return e.Err }

// SilentExit asks the process to exit with code without printing anything.
func SilentExit(code int) error {
	return &ExitError{Code: code}
}

// UsageError reports invalid flags or arguments (exit 2).
func UsageError(format string, args ...any) error {
	return &ExitError{Code: 2, Err: fmt.Errorf(format, args...)}
}

// AuthError reports an authentication or authorization failure (exit 4).
func AuthError(format string, args ...any) error {
	return &ExitError{Code: 4, Err: fmt.Errorf(format, args...)}
}

// Failure wraps a domain/API failure (exit 1).
func Failure(format string, args ...any) error {
	return &ExitError{Code: 1, Err: fmt.Errorf(format, args...)}
}

// ExitCode maps an error to the process exit code (0, 1, 2 or 4).
func ExitCode(err error) int {
	if err == nil {
		return 0
	}
	var exitErr *ExitError
	if errors.As(err, &exitErr) {
		return exitErr.Code
	}
	return 1
}

// FlagErrorf is a convenience for invalid flag values.
func FlagErrorf(format string, args ...any) error {
	return UsageError(format, args...)
}
