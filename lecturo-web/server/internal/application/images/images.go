// Package images is the application layer for screenshot crops: the use cases
// the HTTP handlers call. The model lives in domain/crop; blob storage is a port
// the infrastructure implements.
package images

import (
	"context"
	"io"

	domain "lecturo/server/internal/domain/crop"
)

// Errors, re-exported so callers need not reach into the domain.
var (
	ErrNotFound   = domain.ErrNotFound
	ErrNotAnImage = domain.ErrNotACrop
)

// Image is one stored crop.
type Image = domain.Crop

// Repository is the persistence port the image use cases depend on.
type Repository interface {
	Create(ctx context.Context, userID string, data []byte) (Image, error)
	Open(ctx context.Context, userID, id string) (io.ReadCloser, string, error)
	Load(ctx context.Context, userID, id string) ([]byte, string, error)
}

// Service holds the image use cases.
type Service struct {
	Repo Repository
}

// Create sniffs and stores a crop.
func (s *Service) Create(ctx context.Context, userID string, data []byte) (Image, error) {
	return s.Repo.Create(ctx, userID, data)
}

// Open streams a stored crop.
func (s *Service) Open(ctx context.Context, userID, id string) (io.ReadCloser, string, error) {
	return s.Repo.Open(ctx, userID, id)
}

// Load reads a stored crop's bytes and media type.
func (s *Service) Load(ctx context.Context, userID, id string) ([]byte, string, error) {
	return s.Repo.Load(ctx, userID, id)
}
