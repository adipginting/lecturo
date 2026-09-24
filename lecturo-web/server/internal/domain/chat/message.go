package chat

import "time"

// Roles a message can have. A summary is a Message like any other, marked by
// IsSummary; it is not a third role.
const (
	RoleUser      = "user"
	RoleAssistant = "assistant"
)

// Message is one turn in a conversation.
type Message struct {
	ID             string
	ConversationID string
	Role           string
	Content        string
	ImageID        *string
	IsSummary      bool
	Superseded     bool
	CreatedAt      time.Time
}

// IsActive reports whether the message is sent on the next call.
func (m Message) IsActive() bool { return !m.Superseded }
