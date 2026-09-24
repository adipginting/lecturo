package chat

import (
	"strings"
	"testing"
	"unicode/utf8"
)

func TestNormalizeCollapsesWhitespace(t *testing.T) {
	if got := Normalize("  a \n\t b  "); got != "a b" {
		t.Fatalf("got %q", got)
	}
}

func TestBlankInputsAreEmpty(t *testing.T) {
	for _, blank := range []string{"", "   ", "\n\t "} {
		if got := Normalize(blank); got != "" {
			t.Fatalf("Normalize(%q) = %q, want empty", blank, got)
		}
		if got := FromMessage(blank); got != "" {
			t.Fatalf("FromMessage(%q) = %q, want empty", blank, got)
		}
	}
}

func TestLengthsDifferBySource(t *testing.T) {
	long := strings.Repeat("a", 100)

	if got := len(Normalize(long)); got != MaxLength {
		t.Fatalf("typed name length = %d, want %d", got, MaxLength)
	}
	if got := len(FromMessage(long)); got != fromMessageLength {
		t.Fatalf("from-message length = %d, want %d", got, fromMessageLength)
	}
}

func TestACutNameDoesNotEndInASpace(t *testing.T) {
	got := Normalize("hello world this is a fairly long title that gets cut here indeed")
	if strings.HasSuffix(got, " ") {
		t.Fatalf("name ends in a space: %q", got)
	}
}

func TestACutNeverSplitsAMultiByteCharacter(t *testing.T) {
	// 59 ASCII runes, then a 4-byte rune exactly at the bound: a byte-wise cut
	// would leave half of it behind.
	input := strings.Repeat("a", 59) + "😀" + strings.Repeat("b", 5)

	got := Normalize(input)
	if !utf8.ValidString(got) {
		t.Fatalf("cut produced invalid UTF-8: %q", got)
	}
	if utf8.RuneCountInString(got) != MaxLength {
		t.Fatalf("rune count = %d, want %d", utf8.RuneCountInString(got), MaxLength)
	}
	if got != strings.Repeat("a", 59)+"😀" {
		t.Fatalf("got %q", got)
	}
}
