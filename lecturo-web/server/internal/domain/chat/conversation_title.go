package chat

import "strings"

// ConversationTitle is the one place a conversation's name is decided.
type ConversationTitle struct{}

// Untitled names a conversation that has not named itself yet.
const Untitled = "New conversation"

// MaxLength bounds a name the user typed.
const MaxLength = 60

// fromMessageLength bounds a name derived from the first message; shorter,
// because a sentence is not a title.
const fromMessageLength = 40

// Normalize cleans a user-typed name; "" means nothing usable was given.
func Normalize(input string) string { return shorten(input, MaxLength) }

// FromMessage derives a name from a conversation's first message.
func FromMessage(message string) string { return shorten(message, fromMessageLength) }

// shorten collapses whitespace, trims, bounds the length (by rune, so a
// multi-byte character is never split) and reports "" for blank input.
func shorten(input string, maxLength int) string {
	collapsed := strings.Join(strings.Fields(input), " ")
	runes := []rune(collapsed)
	if len(runes) > maxLength {
		runes = runes[:maxLength]
	}
	return strings.TrimRight(string(runes), " ")
}
