// Package prompts is the application layer for saved prompt templates: the use
// cases the HTTP handlers call. The rules (validation, the twelve cap) live in
// domain/reading; persistence is a port the infrastructure implements.
package prompts

import (
	"context"

	domain "lecturo/server/internal/domain/reading"
)

// Domain errors, re-exported so callers need not reach into the domain.
var (
	ErrNotFound   = domain.ErrPromptNotFound
	ErrCapReached = domain.ErrPromptCap
)

// Repository is the persistence port the prompt use cases depend on. It is
// defined here, by the consumer, and implemented by infrastructure/prompts.
type Repository interface {
	List(ctx context.Context, userID string) ([]domain.Prompt, error)
	Get(ctx context.Context, userID, id string) (domain.Prompt, error)
	Create(ctx context.Context, userID, title, body string) (domain.Prompt, error)
	Update(ctx context.Context, userID, id string, title, body *string) (domain.Prompt, error)
	Delete(ctx context.Context, userID, id string) error
}

// Service holds the prompt use cases.
type Service struct {
	Repo Repository
}

// List returns the user's prompts, oldest first.
func (s *Service) List(ctx context.Context, userID string) ([]domain.Prompt, error) {
	return s.Repo.List(ctx, userID)
}

// Get returns one stored prompt.
func (s *Service) Get(ctx context.Context, userID, id string) (domain.Prompt, error) {
	return s.Repo.Get(ctx, userID, id)
}

// Create validates a new prompt and stores it, refusing past the cap.
func (s *Service) Create(ctx context.Context, userID, title, body string) (domain.Prompt, error) {
	draft, err := domain.NewPrompt(title, body)
	if err != nil {
		return domain.Prompt{}, err
	}
	return s.Repo.Create(ctx, userID, draft.Title, draft.Body)
}

// Update applies the given fields, leaving omitted ones as they were.
func (s *Service) Update(ctx context.Context, userID, id string, title, body *string) (domain.Prompt, error) {
	return s.Repo.Update(ctx, userID, id, title, body)
}

// Delete removes a prompt.
func (s *Service) Delete(ctx context.Context, userID, id string) error {
	return s.Repo.Delete(ctx, userID, id)
}
