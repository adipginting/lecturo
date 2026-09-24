// Package provider holds the LLM provider catalog, per-user credentials
// (encrypted at rest) and model listing. Provider keys never leave the server.
package provider

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	appprovider "lecturo/server/internal/application/provider"
	domain "lecturo/server/internal/domain/settings"
	"lecturo/server/internal/infrastructure/secretbox"
)

// Errors and types come from the domain; this package persists them.
var (
	ErrUnknownProvider       = domain.ErrUnknownProvider
	ErrUnsupported           = domain.ErrUnsupportedProvider
	ErrNoKey                 = domain.ErrNoKey
	ErrEncryptionUnavailable = domain.ErrEncryptionUnavailable
)

// Info, ProviderStatus and Settings are the domain model.
type (
	Info           = domain.Provider
	ProviderStatus = domain.Status
	Settings       = domain.Selection
)

// DefaultProvider is used before a user chooses one.
const DefaultProvider = domain.DefaultProvider

// Catalog returns the provider list, with the relay URL applied to Lecturo Cloud.
func Catalog() []Info {
	relay := strings.TrimRight(os.Getenv("LECTURO_RELAY_URL"), "/")
	out := make([]domain.Provider, len(domain.Catalog))
	copy(out, domain.Catalog)
	for i := range out {
		if out[i].ID == "lecturo" {
			out[i].DefaultBaseURL = relay
		}
	}
	return out
}

// Lookup finds a provider by id.
func Lookup(id string) (Info, bool) { return domain.Lookup(id) }

// Store persists provider credentials.
type Store struct {
	Pool *pgxpool.Pool
	Box  *secretbox.Box
}

// Get resolves the selected provider and its credentials.
func (s *Store) Get(ctx context.Context, userID string) (Settings, error) {
	selected := DefaultProvider
	err := s.Pool.QueryRow(ctx,
		`select provider from provider_settings where user_id = $1`, userID,
	).Scan(&selected)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return Settings{}, fmt.Errorf("provider: get selection: %w", err)
	}

	base, model, hasKey, err := s.credentials(ctx, userID, selected)
	if err != nil {
		return Settings{}, err
	}

	configured, err := s.configuredSet(ctx, userID)
	if err != nil {
		return Settings{}, err
	}

	settings := Settings{Provider: selected, BaseURL: base, Model: model, HasKey: hasKey}
	override, err := s.VisionOverride(ctx, userID, model)
	if err != nil {
		return Settings{}, err
	}
	settings.ModelSupportsImages = domain.VisionWithOverride(model, override)
	settings.Destination = domain.DestinationFor(selected)

	for _, p := range Catalog() {
		settings.Providers = append(settings.Providers, ProviderStatus{
			ID: p.ID, Label: p.Label, SupportsImages: p.SupportsImages,
			Supported: p.Supported, Configured: configured[p.ID],
		})
	}
	return settings, nil
}

// Save applies an update and marks the provider as selected.
func (s *Store) Save(ctx context.Context, userID string, upd appprovider.Update) (Settings, error) {
	info, ok := Lookup(upd.Provider)
	if !ok {
		return Settings{}, ErrUnknownProvider
	}
	if !info.Supported {
		return Settings{}, ErrUnsupported
	}

	var basePtr, modelPtr *string
	err := s.Pool.QueryRow(ctx, `
		select base_url, model from provider_credentials
		where user_id = $1 and provider = $2`, userID, upd.Provider,
	).Scan(&basePtr, &modelPtr)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return Settings{}, fmt.Errorf("provider: get credentials: %w", err)
	}

	if upd.BaseURL != nil {
		basePtr = nilIfEmpty(*upd.BaseURL)
	}
	if upd.Model != nil {
		modelPtr = nilIfEmpty(*upd.Model)
	}

	var sealed []byte
	clearKey := false
	if upd.APIKey != nil {
		if *upd.APIKey == "" {
			clearKey = true
		} else {
			if s.Box == nil {
				return Settings{}, ErrEncryptionUnavailable
			}
			sealed, err = s.Box.Seal([]byte(*upd.APIKey))
			if err != nil {
				return Settings{}, err
			}
		}
	}

	if _, err := s.Pool.Exec(ctx, `
		insert into provider_credentials (user_id, provider, base_url, model, api_key_enc, updated_at)
		values ($1, $2, $3, $4, null, now())
		on conflict (user_id, provider) do update set
			base_url = excluded.base_url,
			model = excluded.model,
			updated_at = now()`,
		userID, upd.Provider, basePtr, modelPtr,
	); err != nil {
		return Settings{}, fmt.Errorf("provider: upsert credentials: %w", err)
	}

	if sealed != nil {
		if _, err := s.Pool.Exec(ctx, `
			update provider_credentials set api_key_enc = $3, updated_at = now()
			where user_id = $1 and provider = $2`,
			userID, upd.Provider, sealed,
		); err != nil {
			return Settings{}, fmt.Errorf("provider: store key: %w", err)
		}
	} else if clearKey {
		if _, err := s.Pool.Exec(ctx, `
			update provider_credentials set api_key_enc = null, updated_at = now()
			where user_id = $1 and provider = $2`,
			userID, upd.Provider,
		); err != nil {
			return Settings{}, fmt.Errorf("provider: clear key: %w", err)
		}
	}

	if upd.VisionOverride != nil {
		resolvedModel := info.DefaultModel
		if modelPtr != nil && *modelPtr != "" {
			resolvedModel = *modelPtr
		}
		if resolvedModel != "" {
			if err := s.SetVisionOverride(ctx, userID, resolvedModel, upd.VisionOverride); err != nil {
				return Settings{}, err
			}
		}
	}

	if _, err := s.Pool.Exec(ctx, `
		insert into provider_settings (user_id, provider, updated_at)
		values ($1, $2, now())
		on conflict (user_id) do update set provider = excluded.provider, updated_at = now()`,
		userID, upd.Provider,
	); err != nil {
		return Settings{}, fmt.Errorf("provider: upsert selection: %w", err)
	}

	return s.Get(ctx, userID)
}

// Credentials returns the decrypted key for a provider, for server-side calls.
func (s *Store) Credentials(ctx context.Context, userID, providerID string) (baseURL, model, apiKey string, err error) {
	base, model, _, err := s.credentials(ctx, userID, providerID)
	if err != nil {
		return "", "", "", err
	}
	info, _ := Lookup(providerID)

	var sealed []byte
	err = s.Pool.QueryRow(ctx, `
		select api_key_enc from provider_credentials
		where user_id = $1 and provider = $2`, userID, providerID).Scan(&sealed)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return "", "", "", fmt.Errorf("provider: key: %w", err)
	}
	if len(sealed) > 0 {
		if s.Box == nil {
			return "", "", "", ErrEncryptionUnavailable
		}
		plain, err := s.Box.Open(sealed)
		if err != nil {
			return "", "", "", err
		}
		apiKey = string(plain)
	}
	if info.RequiresKey && apiKey == "" {
		return "", "", "", ErrNoKey
	}
	return base, model, apiKey, nil
}

func (s *Store) credentials(ctx context.Context, userID, providerID string) (baseURL, model string, hasKey bool, err error) {
	info, ok := Lookup(providerID)
	if !ok {
		return "", "", false, ErrUnknownProvider
	}

	var basePtr, modelPtr *string
	var keyLen int
	err = s.Pool.QueryRow(ctx, `
		select base_url, model, coalesce(length(api_key_enc), 0)
		from provider_credentials
		where user_id = $1 and provider = $2`, userID, providerID,
	).Scan(&basePtr, &modelPtr, &keyLen)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return "", "", false, fmt.Errorf("provider: credentials: %w", err)
	}

	baseURL = info.DefaultBaseURL
	if basePtr != nil && *basePtr != "" {
		baseURL = *basePtr
	}
	if modelPtr != nil {
		model = *modelPtr
	}
	return baseURL, model, keyLen > 0, nil
}

func (s *Store) configuredSet(ctx context.Context, userID string) (map[string]bool, error) {
	rows, err := s.Pool.Query(ctx,
		`select provider from provider_credentials where user_id = $1`, userID)
	if err != nil {
		return nil, fmt.Errorf("provider: configured: %w", err)
	}
	defer rows.Close()

	set := map[string]bool{}
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		set[id] = true
	}
	return set, rows.Err()
}

// Models lists the model ids the provider reports for the given credentials.
func (s *Store) Models(ctx context.Context, providerID, baseURL, apiKey string) ([]string, error) {
	info, ok := Lookup(providerID)
	if !ok {
		return nil, ErrUnknownProvider
	}
	if baseURL == "" {
		baseURL = info.DefaultBaseURL
	}
	if baseURL == "" {
		return nil, ErrUnknownProvider
	}

	endpoint := strings.TrimRight(baseURL, "/")
	if info.Anthropic {
		endpoint += "/v1/models"
	} else {
		endpoint += "/models"
	}

	reqCtx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(reqCtx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}
	if info.Anthropic {
		req.Header.Set("x-api-key", apiKey)
		req.Header.Set("anthropic-version", "2023-06-01")
	} else if apiKey != "" {
		req.Header.Set("Authorization", "Bearer "+apiKey)
	}
	req.Header.Set("Accept", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("provider: models request: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("provider: models: upstream answered %s", resp.Status)
	}

	var payload struct {
		Data []struct {
			ID string `json:"id"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return nil, fmt.Errorf("provider: models decode: %w", err)
	}

	models := make([]string, 0, len(payload.Data))
	for _, m := range payload.Data {
		if m.ID != "" {
			models = append(models, m.ID)
		}
	}
	return models, nil
}

func nilIfEmpty(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}

// VisionOverride returns the stored per-model capability, or nil for the default.
func (s *Store) VisionOverride(ctx context.Context, userID, model string) (*bool, error) {
	if model == "" {
		return nil, nil
	}
	var supports bool
	err := s.Pool.QueryRow(ctx, `
		select supports_images from model_vision_overrides
		where user_id = $1 and model = $2`, userID, model).Scan(&supports)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("provider: vision override: %w", err)
	}
	return &supports, nil
}

// SetVisionOverride stores (or, with nil, clears) a per-model capability.
func (s *Store) SetVisionOverride(ctx context.Context, userID, model string, supports *bool) error {
	if supports == nil {
		_, err := s.Pool.Exec(ctx,
			`delete from model_vision_overrides where user_id = $1 and model = $2`, userID, model)
		return err
	}
	_, err := s.Pool.Exec(ctx, `
		insert into model_vision_overrides (user_id, model, supports_images, updated_at)
		values ($1, $2, $3, now())
		on conflict (user_id, model) do update
		set supports_images = excluded.supports_images, updated_at = now()`,
		userID, model, *supports)
	return err
}
