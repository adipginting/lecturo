// Package provider is the application layer for chat provider settings: the use
// cases the HTTP handlers call. The catalogue and vision rules live in
// domain/settings; persistence and credential encryption are a port the
// infrastructure implements.
package provider

import (
	"context"

	domain "lecturo/server/internal/domain/settings"
)

// Errors and constants, re-exported so callers need not reach into the domain.
var (
	ErrUnknownProvider       = domain.ErrUnknownProvider
	ErrUnsupported           = domain.ErrUnsupportedProvider
	ErrNoKey                 = domain.ErrNoKey
	ErrEncryptionUnavailable = domain.ErrEncryptionUnavailable
)

// DefaultProvider is the provider a user starts on.
const DefaultProvider = domain.DefaultProvider

type (
	// Settings is the resolved provider configuration for a user.
	Settings = domain.Selection
	// Status is the client-facing view of one provider.
	Status = domain.Status
	// ProviderStatus is the same view, under the name the handlers use.
	ProviderStatus = domain.Status
)

// Lookup finds a provider in the catalogue.
func Lookup(id string) (domain.Provider, bool) { return domain.Lookup(id) }

// Update describes the fields to change on the selected provider.
type Update struct {
	Provider       string
	BaseURL        *string
	Model          *string
	APIKey         *string
	VisionOverride *bool
}

// Repository is the persistence port the provider use cases depend on. It is
// defined here, by the consumer, and implemented by infrastructure/provider.
type Repository interface {
	Get(ctx context.Context, userID string) (Settings, error)
	Save(ctx context.Context, userID string, upd Update) (Settings, error)
	Credentials(ctx context.Context, userID, providerID string) (baseURL, model, apiKey string, err error)
	Models(ctx context.Context, providerID, baseURL, apiKey string) ([]string, error)
	VisionOverride(ctx context.Context, userID, model string) (*bool, error)
}

// Service holds the provider use cases.
type Service struct {
	Repo Repository
}

// Get returns the user's selected provider and catalogue.
func (s *Service) Get(ctx context.Context, userID string) (Settings, error) {
	return s.Repo.Get(ctx, userID)
}

// Save applies a selection change (and any key/model/vision fields).
func (s *Service) Save(ctx context.Context, userID string, upd Update) (Settings, error) {
	return s.Repo.Save(ctx, userID, upd)
}

// Credentials returns the values needed to call the selected provider.
func (s *Service) Credentials(ctx context.Context, userID, providerID string) (string, string, string, error) {
	return s.Repo.Credentials(ctx, userID, providerID)
}

// Models lists the models the provider offers, with the given credentials.
func (s *Service) Models(ctx context.Context, providerID, baseURL, apiKey string) ([]string, error) {
	return s.Repo.Models(ctx, providerID, baseURL, apiKey)
}

// VisionOverride returns a model's stored image-capability override, if any.
func (s *Service) VisionOverride(ctx context.Context, userID, model string) (*bool, error) {
	return s.Repo.VisionOverride(ctx, userID, model)
}
