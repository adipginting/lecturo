// Package chat persists conversations and messages. The model — and the rules
// about drafts, sending and naming — lives in domain/chat.
package chat

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	appchat "lecturo/server/internal/application/chat"
	domain "lecturo/server/internal/domain/chat"
)

// Aliases so callers keep reading naturally.
type (
	Conversation = domain.Conversation
	Message      = domain.Message
	Excerpt      = domain.Excerpt
)

// ErrNotFound means the conversation does not exist for this user.
var ErrNotFound = appchat.ErrNotFound

// Store persists conversations.
type Store struct {
	Pool *pgxpool.Pool
}

const conversationColumns = `
	id::text, title, provider, excerpt, context_image_id::text,
	document_id::text, doc_title, locator, prompt_id::text,
	sent_at, created_at, updated_at`

func scanConversation(row rowScanner) (Conversation, error) {
	var (
		c               Conversation
		excerptText     *string
		contextImageID  *string
		documentID      *string
		documentTitle   *string
		documentLocator *string
		promptID        *string
	)
	err := row.Scan(&c.ID, &c.Title, &c.Provider, &excerptText, &contextImageID,
		&documentID, &documentTitle, &documentLocator, &promptID,
		&c.SentAt, &c.CreatedAt, &c.UpdatedAt)
	if err != nil {
		return Conversation{}, err
	}

	if excerptText != nil && *excerptText != "" {
		c.Excerpt.Text = excerptText
	}
	c.Excerpt.ImageID = contextImageID
	c.DocumentID = documentID
	c.DocumentTitle = documentTitle
	c.DocumentLocator = documentLocator
	c.PromptID = promptID
	return c, nil
}

type rowScanner interface{ Scan(dest ...any) error }

// List returns conversations newest first, optionally only drafts or only sent.
func (s *Store) List(ctx context.Context, userID, status string, documentID *string) ([]Conversation, error) {
	rows, err := s.Pool.Query(ctx, `
		select `+conversationColumns+`
		from conversations
		where user_id = $1
		  and ($2::uuid is null or document_id = $2)
		  and ($3::text is null
		       or ($3 = 'draft' and sent_at is null)
		       or ($3 = 'sent' and sent_at is not null))
		order by updated_at desc`,
		userID, documentID, nullIfEmpty(status))
	if err != nil {
		return nil, fmt.Errorf("chat: list: %w", err)
	}
	defer rows.Close()

	out := make([]Conversation, 0, 16)
	for rows.Next() {
		c, err := scanConversation(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, c)
	}
	return out, rows.Err()
}

// CreateDraft starts an unsent conversation from an excerpt.
func (s *Store) CreateDraft(ctx context.Context, in appchat.DraftInput) (Conversation, error) {
	text := ""
	if in.Excerpt.Text != nil {
		text = *in.Excerpt.Text
	}

	c, err := scanConversation(s.Pool.QueryRow(ctx, `
		insert into conversations
		  (user_id, document_id, title, excerpt, locator, context_image_id,
		   provider, doc_title, sent_at, created_at, updated_at)
		values ($1, $2, $3, $4, $5, $6, $7, $8, null, now(), now())
		returning `+conversationColumns,
		in.UserID, in.DocumentID, domain.Untitled, text, in.DocumentLocator,
		in.Excerpt.ImageID, in.Provider, in.DocumentTitle))
	if err != nil {
		return Conversation{}, fmt.Errorf("chat: create draft: %w", err)
	}
	return c, nil
}

// Get returns a conversation and all its messages (oldest first).
func (s *Store) Get(ctx context.Context, userID, id string) (Conversation, []Message, error) {
	c, err := scanConversation(s.Pool.QueryRow(ctx, `
		select `+conversationColumns+`
		from conversations where user_id = $1 and id = $2`, userID, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return Conversation{}, nil, ErrNotFound
	}
	if err != nil {
		return Conversation{}, nil, fmt.Errorf("chat: get: %w", err)
	}

	messages, err := s.Messages(ctx, userID, id)
	if err != nil {
		return Conversation{}, nil, err
	}
	c.Messages = messages
	return c, messages, nil
}

// Messages returns every message in a conversation, oldest first.
func (s *Store) Messages(ctx context.Context, userID, conversationID string) ([]Message, error) {
	rows, err := s.Pool.Query(ctx, `
		select id::text, conversation_id::text, role, content, image_id::text,
		       is_summary, superseded, created_at
		from messages
		where user_id = $1 and conversation_id = $2
		order by created_at`, userID, conversationID)
	if err != nil {
		return nil, fmt.Errorf("chat: messages: %w", err)
	}
	defer rows.Close()

	out := make([]Message, 0, 16)
	for rows.Next() {
		var m Message
		if err := rows.Scan(&m.ID, &m.ConversationID, &m.Role, &m.Content,
			&m.ImageID, &m.IsSummary, &m.Superseded, &m.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, m)
	}
	return out, rows.Err()
}

// ActiveMessages returns the messages that would be sent on the next call.
func (s *Store) ActiveMessages(ctx context.Context, userID, conversationID string) ([]Message, error) {
	all, err := s.Messages(ctx, userID, conversationID)
	if err != nil {
		return nil, err
	}
	active := make([]Message, 0, len(all))
	for _, m := range all {
		if m.IsActive() {
			active = append(active, m)
		}
	}
	return active, nil
}

// Rename applies the domain's naming rules; a blank name changes nothing.
func (s *Store) Rename(ctx context.Context, userID, id, name string) (Conversation, error) {
	current, err := scanConversation(s.Pool.QueryRow(ctx, `
		select `+conversationColumns+`
		from conversations where user_id = $1 and id = $2`, userID, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return Conversation{}, ErrNotFound
	}
	if err != nil {
		return Conversation{}, fmt.Errorf("chat: rename: %w", err)
	}

	renamed := current.Rename(name)
	tag, err := s.Pool.Exec(ctx, `
		update conversations set title = $3, updated_at = now()
		where user_id = $1 and id = $2`, userID, id, renamed.Title)
	if err != nil {
		return Conversation{}, fmt.Errorf("chat: rename: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return Conversation{}, ErrNotFound
	}
	renamed.UpdatedAt = time.Now()
	return renamed, nil
}

// SwitchProvider changes the provider and/or model.
func (s *Store) SwitchProvider(ctx context.Context, userID, id string, provider, model *string) (Conversation, error) {
	c, err := scanConversation(s.Pool.QueryRow(ctx, `
		update conversations
		set provider = coalesce($3, provider),
		    model = coalesce($4, model),
		    updated_at = now()
		where user_id = $1 and id = $2
		returning `+conversationColumns,
		userID, id, provider, model))
	if errors.Is(err, pgx.ErrNoRows) {
		return Conversation{}, ErrNotFound
	}
	if err != nil {
		return Conversation{}, fmt.Errorf("chat: provider: %w", err)
	}
	return c, nil
}

// MarkSent names a draft from its first message and marks it sent. SentAt is
// only ever set once the reply has landed. A conversation already sent is
// returned unchanged.
func (s *Store) MarkSent(ctx context.Context, userID, id, firstMessage string) (Conversation, error) {
	c, _, err := s.Get(ctx, userID, id)
	if err != nil {
		return Conversation{}, err
	}
	if !c.IsDraft() {
		return c, nil
	}

	sent := c.Send(firstMessage, time.Now())
	if _, err := s.Pool.Exec(ctx, `
		update conversations set title = $3, sent_at = $4, updated_at = now()
		where user_id = $1 and id = $2`, userID, id, sent.Title, sent.SentAt); err != nil {
		return Conversation{}, fmt.Errorf("chat: mark sent: %w", err)
	}
	sent.UpdatedAt = time.Now()
	return sent, nil
}

// Delete removes a conversation and its messages.
func (s *Store) Delete(ctx context.Context, userID, id string) error {
	tag, err := s.Pool.Exec(ctx,
		`delete from conversations where user_id = $1 and id = $2`, userID, id)
	if err != nil {
		return fmt.Errorf("chat: delete: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

// AppendMessage adds a turn.
func (s *Store) AppendMessage(ctx context.Context, userID, conversationID, role, content string) (Message, error) {
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return Message{}, fmt.Errorf("chat: append begin: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()

	var m Message
	err = tx.QueryRow(ctx, `
		insert into messages (user_id, conversation_id, role, content)
		values ($1, $2, $3, $4)
		returning id::text, conversation_id::text, role, content, image_id::text,
		          is_summary, superseded, created_at`,
		userID, conversationID, role, content,
	).Scan(&m.ID, &m.ConversationID, &m.Role, &m.Content, &m.ImageID,
		&m.IsSummary, &m.Superseded, &m.CreatedAt)
	if err != nil {
		return Message{}, fmt.Errorf("chat: append: %w", err)
	}

	if _, err := tx.Exec(ctx, `
		update conversations set updated_at = now()
		where user_id = $1 and id = $2`, userID, conversationID); err != nil {
		return Message{}, fmt.Errorf("chat: touch: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return Message{}, fmt.Errorf("chat: append commit: %w", err)
	}
	return m, nil
}

// AppendImageMessage adds a user turn whose context is a screenshot.
func (s *Store) AppendImageMessage(ctx context.Context, userID, conversationID, content, imageID string) (Message, error) {
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return Message{}, fmt.Errorf("chat: append image begin: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()

	var m Message
	err = tx.QueryRow(ctx, `
		insert into messages (user_id, conversation_id, role, content, image_id)
		values ($1, $2, 'user', $3, $4)
		returning id::text, conversation_id::text, role, content, image_id::text,
		          is_summary, superseded, created_at`,
		userID, conversationID, content, imageID,
	).Scan(&m.ID, &m.ConversationID, &m.Role, &m.Content, &m.ImageID,
		&m.IsSummary, &m.Superseded, &m.CreatedAt)
	if err != nil {
		return Message{}, fmt.Errorf("chat: append image: %w", err)
	}

	if _, err := tx.Exec(ctx, `
		update conversations set updated_at = now()
		where user_id = $1 and id = $2`, userID, conversationID); err != nil {
		return Message{}, fmt.Errorf("chat: append image touch: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return Message{}, fmt.Errorf("chat: append image commit: %w", err)
	}
	return m, nil
}

// Summarise marks the active turns superseded and inserts a summary Message.
func (s *Store) Summarise(ctx context.Context, userID, conversationID, summary string) (Message, error) {
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return Message{}, fmt.Errorf("chat: summarise begin: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()

	if _, err := tx.Exec(ctx, `
		update messages set superseded = true
		where user_id = $1 and conversation_id = $2
		  and not superseded and not is_summary`,
		userID, conversationID); err != nil {
		return Message{}, fmt.Errorf("chat: supersede: %w", err)
	}

	var m Message
	err = tx.QueryRow(ctx, `
		insert into messages (user_id, conversation_id, role, content, is_summary)
		values ($1, $2, 'assistant', $3, true)
		returning id::text, conversation_id::text, role, content, image_id::text,
		          is_summary, superseded, created_at`,
		userID, conversationID, summary,
	).Scan(&m.ID, &m.ConversationID, &m.Role, &m.Content, &m.ImageID,
		&m.IsSummary, &m.Superseded, &m.CreatedAt)
	if err != nil {
		return Message{}, fmt.Errorf("chat: summary insert: %w", err)
	}

	if _, err := tx.Exec(ctx,
		`update conversations set updated_at = now() where user_id = $1 and id = $2`,
		userID, conversationID); err != nil {
		return Message{}, fmt.Errorf("chat: summary touch: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return Message{}, fmt.Errorf("chat: summarise commit: %w", err)
	}
	return m, nil
}

func nullIfEmpty(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
