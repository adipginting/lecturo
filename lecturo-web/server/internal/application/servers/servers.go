// Package servers is the application layer for Calibre/Zotero connection
// settings: the use cases the HTTP handlers call. The model lives in
// domain/settings; persistence and secret encryption are a port the
// infrastructure implements.
package servers

import (
	"context"

	domain "lecturo/server/internal/domain/settings"
)

// ErrEncryptionUnavailable means no encryption key is configured.
var ErrEncryptionUnavailable = domain.ErrEncryptionUnavailable

// Settings and Credentials are the domain model.
type (
	Settings    = domain.Servers
	Credentials = domain.ServerCredentials
)

// Update carries the writable fields. nil leaves a field unchanged; a pointer
// to "" clears it.
type Update struct {
	CalibreURL      *string
	CalibreUser     *string
	CalibrePassword *string
	ZoteroUserID    *string
	ZoteroAPIKey    *string
}

// Repository is the persistence port the server-settings use cases depend on.
type Repository interface {
	Get(ctx context.Context, userID string) (Settings, error)
	Save(ctx context.Context, userID string, upd Update) (Settings, error)
	Credentials(ctx context.Context, userID string) (Credentials, error)
}

// Service holds the server-settings use cases.
type Service struct {
	Repo Repository
}

// Get returns the settings, hiding secrets.
func (s *Service) Get(ctx context.Context, userID string) (Settings, error) {
	return s.Repo.Get(ctx, userID)
}

// Save applies the given fields.
func (s *Service) Save(ctx context.Context, userID string, upd Update) (Settings, error) {
	return s.Repo.Save(ctx, userID, upd)
}

// Credentials returns the decrypted values for server-side calls.
func (s *Service) Credentials(ctx context.Context, userID string) (Credentials, error) {
	return s.Repo.Credentials(ctx, userID)
}
