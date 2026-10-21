package changemessage

import (
	"strings"
	"testing"
)

func TestParseValid(t *testing.T) {
	message, err := Parse("feat(standard): add settlement field\n\nAdds the new field and a validator.\n\nRefs: DCC-12")
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if message.Subject != "feat(standard): add settlement field" {
		t.Fatalf("subject = %q", message.Subject)
	}
	if !strings.Contains(message.Body, "Adds the new field") || !strings.Contains(message.Body, "Refs: DCC-12") {
		t.Fatalf("body = %q", message.Body)
	}
}

func TestParseAcceptsBreakingChange(t *testing.T) {
	if _, err := Parse("feat(api)!: remove the legacy field"); err != nil {
		t.Fatalf("Parse: %v", err)
	}
}

func TestParseRejectsBadSubjects(t *testing.T) {
	cases := []string{
		"",
		"add a field",
		"feature(standard): add a field",
		"feat(standard): ends with a period.",
		"feat(standard): " + strings.Repeat("x", 80),
	}
	for _, input := range cases {
		if _, err := Parse(input); err == nil {
			t.Fatalf("expected an error for %q", input)
		}
	}
}

func TestHeaderValueFlattensAndTruncates(t *testing.T) {
	message, err := Parse("fix(table): handle null\n\nfirst line\nsecond line")
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	header := message.HeaderValue()
	if header != "fix(table): handle null; first line; second line" {
		t.Fatalf("header = %q", header)
	}
	if strings.ContainsAny(header, "\r\n\t") {
		t.Fatalf("header contains a control character: %q", header)
	}

	long := strings.Repeat("x", MaxHeader+50)
	header = Message{Subject: "feat: " + long}.HeaderValue()
	if runes := []rune(header); len(runes) != MaxHeader {
		t.Fatalf("truncated length = %d, want %d", len(runes), MaxHeader)
	}
}
