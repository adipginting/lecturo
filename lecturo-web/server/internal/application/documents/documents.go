// Package documents is the application layer for the document library: the use
// cases the HTTP handlers call. The model lives in domain/library; persistence
// and blob storage are a port the infrastructure implements.
package documents

import (
	"context"
	"io"

	domain "lecturo/server/internal/domain/library"
)

// Errors, re-exported so callers need not reach into the domain.
var (
	ErrNotFound = domain.ErrNotFound
	ErrNotABook = domain.ErrNotABook
)

// CreateInput describes a document to store: its bytes and where it came from.
type CreateInput struct {
	UserID   string
	Title    string
	Data     []byte
	Source   *string
	RemoteID *string
}

// Repository is the persistence port the document use cases depend on. It is
// defined here, by the consumer, and implemented by infrastructure/library.
type Repository interface {
	List(ctx context.Context, userID string, limit int, cursor string) ([]domain.Document, string, error)
	Create(ctx context.Context, in CreateInput) (domain.Document, error)
	Get(ctx context.Context, userID, id string) (domain.Document, error)
	Open(ctx context.Context, userID, id string) (io.ReadCloser, string, error)
	UpdatePosition(ctx context.Context, userID, id, locator string) error
	Delete(ctx context.Context, userID, id string) error
}

// Service holds the document use cases.
type Service struct {
	Repo Repository
}

// List returns a page of the user's documents.
func (s *Service) List(ctx context.Context, userID string, limit int, cursor string) ([]domain.Document, string, error) {
	return s.Repo.List(ctx, userID, limit, cursor)
}

// Create sniffs the payload and stores it, refusing anything not a book.
func (s *Service) Create(ctx context.Context, in CreateInput) (domain.Document, error) {
	return s.Repo.Create(ctx, in)
}

// Get loads one document.
func (s *Service) Get(ctx context.Context, userID, id string) (domain.Document, error) {
	return s.Repo.Get(ctx, userID, id)
}

// Open streams a document's stored bytes.
func (s *Service) Open(ctx context.Context, userID, id string) (io.ReadCloser, string, error) {
	return s.Repo.Open(ctx, userID, id)
}

// UpdatePosition stores the reader's opaque locator.
func (s *Service) UpdatePosition(ctx context.Context, userID, id, locator string) error {
	return s.Repo.UpdatePosition(ctx, userID, id, locator)
}

// Delete removes a document row and its bytes.
func (s *Service) Delete(ctx context.Context, userID, id string) error {
	return s.Repo.Delete(ctx, userID, id)
}
