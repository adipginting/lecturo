// Package reading is the domain model for Saved excerpts and Prompts.
package reading

import (
	"errors"
	"strings"
	"time"
)

// Excerpt is one saved selection, with its document's title for display.
type Excerpt struct {
	ID            string
	DocumentID    string
	DocumentTitle string
	Text          string
	Locator       string
	CreatedAt     time.Time
}

// Prompt is a reusable instruction, capped per user.
type Prompt struct {
	ID    string
	Title string
	Body  string
}

// MaxPrompts caps how many Prompts one user may keep.
const MaxPrompts = 12

// Domain errors.
var (
	ErrExcerptNotFound = errors.New("reading: excerpt not found")
	ErrPromptNotFound  = errors.New("reading: prompt not found")
	ErrPromptCap       = errors.New("reading: prompt cap reached")
	ErrEmpty           = errors.New("reading: value is empty")
)

// NewExcerpt validates and constructs an Excerpt.
func NewExcerpt(documentID, text, locator string) (Excerpt, error) {
	if strings.TrimSpace(documentID) == "" || strings.TrimSpace(text) == "" {
		return Excerpt{}, ErrEmpty
	}
	return Excerpt{DocumentID: documentID, Text: text, Locator: locator}, nil
}

// NewPrompt validates and constructs a Prompt.
func NewPrompt(title, body string) (Prompt, error) {
	if strings.TrimSpace(title) == "" || strings.TrimSpace(body) == "" {
		return Prompt{}, ErrEmpty
	}
	return Prompt{Title: title, Body: body}, nil
}

// Rename returns a copy of the excerpt's prompt with the given non-empty fields.
func (p Prompt) With(title, body *string) Prompt {
	if title != nil && strings.TrimSpace(*title) != "" {
		p.Title = *title
	}
	if body != nil && strings.TrimSpace(*body) != "" {
		p.Body = *body
	}
	return p
}
