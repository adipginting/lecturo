package chat

import "time"

// Conversation is a chat fired from an excerpt. Until its first message is
// sent it is a Draft — a row with no SentAt, waiting beside the conversations
// it will join.
type Conversation struct {
	ID       string
	Title    string
	Provider string
	Excerpt  Excerpt
	// The excerpt's trace, copied so it survives the document being deleted.
	DocumentID      *string
	DocumentTitle   *string
	DocumentLocator *string
	PromptID        *string
	Messages        []Message
	SentAt          *time.Time
	CreatedAt       time.Time
	UpdatedAt       time.Time
}

// IsDraft reports whether the conversation has not been sent.
func (c Conversation) IsDraft() bool { return c.SentAt == nil }

// Send names a draft from its first message and marks it sent. A conversation
// that is already sent is returned unchanged: a draft is sent once.
// SentAt is set by the caller only once the reply has landed.
func (c Conversation) Send(firstMessage string, at time.Time) Conversation {
	if !c.IsDraft() {
		return c
	}
	title := FromMessage(firstMessage)
	if title == "" {
		title = Untitled
	}
	sentAt := at
	c.Title = title
	c.SentAt = &sentAt
	return c
}

// Rename sets a user-typed name. A blank name leaves the old title untouched,
// so a conversation is never left nameless.
func (c Conversation) Rename(name string) Conversation {
	if normalized := Normalize(name); normalized != "" {
		c.Title = normalized
	}
	return c
}

// NewDraft constructs an unsent conversation from an excerpt.
func NewDraft(id, provider string, excerpt Excerpt, documentID *string) Conversation {
	return Conversation{
		ID:         id,
		Title:      Untitled,
		Provider:   provider,
		Excerpt:    excerpt,
		DocumentID: documentID,
	}
}
