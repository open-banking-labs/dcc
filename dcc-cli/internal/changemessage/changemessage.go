// Package changemessage validates change notes using GitHub's commit-message
// grammar. Validation happens only in the CLI; the backend columns are unchanged.
package changemessage

import (
	"errors"
	"fmt"
	"regexp"
	"strings"
)

// Message is a parsed, validated change note.
type Message struct {
	Subject string
	Body    string
	Raw     string
}

var subjectPattern = regexp.MustCompile(
	`^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-z0-9][a-z0-9._/-]*\))?(!)?: (\S.*)$`,
)

const (
	// MaxSubject is the recommended maximum subject length.
	MaxSubject = 72
	// MaxHeader is the backend's message column limit.
	MaxHeader = 1024
)

// Parse validates raw and splits it into subject and body.
func Parse(raw string) (Message, error) {
	normalized := strings.ReplaceAll(raw, "\r\n", "\n")
	normalized = strings.TrimSpace(normalized)
	if normalized == "" {
		return Message{}, errors.New("the change note is empty")
	}

	lines := strings.Split(normalized, "\n")
	subject := strings.TrimSpace(lines[0])
	if err := validateSubject(subject); err != nil {
		return Message{}, err
	}

	body := ""
	if len(lines) > 1 {
		body = strings.TrimSpace(strings.Join(lines[1:], "\n"))
	}
	return Message{Subject: subject, Body: body, Raw: normalized}, nil
}

func validateSubject(subject string) error {
	match := subjectPattern.FindStringSubmatch(subject)
	if match == nil {
		return fmt.Errorf(
			"invalid change note %q: first line must be <type>(<scope>): <summary>, "+
				"where type is one of feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert",
			subject,
		)
	}
	summary := match[4]
	if runes := []rune(summary); len(runes) > MaxSubject {
		return fmt.Errorf("change note subject is %d characters, maximum is %d", len(runes), MaxSubject)
	}
	if strings.HasSuffix(summary, ".") {
		return errors.New("change note subject must not end with a period")
	}
	return nil
}

// HeaderValue flattens the message into one HTTP header value. Newlines cannot
// travel in a header, so body clauses are joined with "; " and the result is
// truncated to the backend column width.
func (m Message) HeaderValue() string {
	parts := []string{m.Subject}
	if m.Body != "" {
		for _, line := range strings.Split(m.Body, "\n") {
			if clause := strings.TrimSpace(line); clause != "" {
				parts = append(parts, clause)
			}
		}
	}
	value := strings.Join(parts, "; ")
	value = strings.Map(func(r rune) rune {
		switch r {
		case '\n', '\r', '\t':
			return ' '
		default:
			return r
		}
	}, value)
	runes := []rune(value)
	if len(runes) > MaxHeader {
		value = string(runes[:MaxHeader])
	}
	return value
}
