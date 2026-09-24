// Package chat is the application layer for conversations: the use cases the
// HTTP handlers call. The rules live in domain/chat; persistence is a port the
// infrastructure implements.
package chat

import (
	"context"
	"errors"

	domain "lecturo/server/internal/domain/chat"
)

// ErrNotFound means the conversation does not exist for this user.
var ErrNotFound = errors.New("chat: not found")

// DraftInput describes a draft to start.
type DraftInput struct {
	UserID          string
	Provider        string
	Excerpt         domain.Excerpt
	DocumentID      *string
	DocumentTitle   *string
	DocumentLocator *string
	PromptID        *string
}

// Repository is the persistence port the chat use cases depend on. It is
// defined here, by the consumer, and implemented by infrastructure/chat.
type Repository interface {
	List(ctx context.Context, userID, status string, documentID *string) ([]domain.Conversation, error)
	CreateDraft(ctx context.Context, in DraftInput) (domain.Conversation, error)
	Get(ctx context.Context, userID, id string) (domain.Conversation, []domain.Message, error)
	Messages(ctx context.Context, userID, id string) ([]domain.Message, error)
	ActiveMessages(ctx context.Context, userID, id string) ([]domain.Message, error)
	Rename(ctx context.Context, userID, id, name string) (domain.Conversation, error)
	SwitchProvider(ctx context.Context, userID, id string, provider, model *string) (domain.Conversation, error)
	Delete(ctx context.Context, userID, id string) error
	MarkSent(ctx context.Context, userID, id, firstMessage string) (domain.Conversation, error)
	AppendMessage(ctx context.Context, userID, id, role, content string) (domain.Message, error)
	AppendImageMessage(ctx context.Context, userID, id, content, imageID string) (domain.Message, error)
	Summarise(ctx context.Context, userID, id, summary string) (domain.Message, error)
}

// Service holds the chat use cases. For now it delegates to the repository;
// the seam exists so use-case logic (auto-compaction, the context budget)
// lands here rather than in the HTTP handlers.
type Service struct {
	Repo Repository
}

// List returns drafts, sent conversations, or both.
func (s *Service) List(ctx context.Context, userID, status string, documentID *string) ([]domain.Conversation, error) {
	return s.Repo.List(ctx, userID, status, documentID)
}

// StartDraft creates an unsent conversation from an excerpt or a crop.
func (s *Service) StartDraft(ctx context.Context, in DraftInput) (domain.Conversation, error) {
	return s.Repo.CreateDraft(ctx, in)
}

// Get returns a conversation with its messages.
func (s *Service) Get(ctx context.Context, userID, id string) (domain.Conversation, []domain.Message, error) {
	return s.Repo.Get(ctx, userID, id)
}

// Messages returns every message in a conversation.
func (s *Service) Messages(ctx context.Context, userID, id string) ([]domain.Message, error) {
	return s.Repo.Messages(ctx, userID, id)
}

// ActiveMessages returns the messages sent on the next call.
func (s *Service) ActiveMessages(ctx context.Context, userID, id string) ([]domain.Message, error) {
	return s.Repo.ActiveMessages(ctx, userID, id)
}

// Rename applies the domain's naming rules.
func (s *Service) Rename(ctx context.Context, userID, id, name string) (domain.Conversation, error) {
	return s.Repo.Rename(ctx, userID, id, name)
}

// SwitchProvider changes the provider and/or model.
func (s *Service) SwitchProvider(ctx context.Context, userID, id string, provider, model *string) (domain.Conversation, error) {
	return s.Repo.SwitchProvider(ctx, userID, id, provider, model)
}

// Delete removes a conversation and its messages.
func (s *Service) Delete(ctx context.Context, userID, id string) error {
	return s.Repo.Delete(ctx, userID, id)
}

// MarkSent names a draft from its first message and marks it sent, once the
// reply has landed.
func (s *Service) MarkSent(ctx context.Context, userID, id, firstMessage string) (domain.Conversation, error) {
	return s.Repo.MarkSent(ctx, userID, id, firstMessage)
}

// AppendMessage adds a turn.
func (s *Service) AppendMessage(ctx context.Context, userID, id, role, content string) (domain.Message, error) {
	return s.Repo.AppendMessage(ctx, userID, id, role, content)
}

// AppendImageMessage adds a user turn that carries a screenshot, so a crop made
// while a conversation is open joins it rather than starting a draft.
func (s *Service) AppendImageMessage(ctx context.Context, userID, id, content, imageID string) (domain.Message, error) {
	return s.Repo.AppendImageMessage(ctx, userID, id, content, imageID)
}

// Compact folds the active turns into a summary.
func (s *Service) Compact(ctx context.Context, userID, id, summary string) (domain.Message, error) {
	return s.Repo.Summarise(ctx, userID, id, summary)
}
