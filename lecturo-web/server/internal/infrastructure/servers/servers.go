// Package servers stores Calibre and Zotero connection details. Passwords and
// API keys are encrypted at rest and only decrypted for server-side calls.
package servers

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	appservers "lecturo/server/internal/application/servers"
	domain "lecturo/server/internal/domain/settings"
	"lecturo/server/internal/infrastructure/secretbox"
)

// ErrEncryptionUnavailable means no encryption key is configured.
var ErrEncryptionUnavailable = domain.ErrEncryptionUnavailable

// Settings and Credentials are the domain model.
type (
	Settings    = domain.Servers
	Credentials = domain.ServerCredentials
)

// Store persists server settings.
type Store struct {
	Pool *pgxpool.Pool
	Box  *secretbox.Box
}

// Get returns the settings, hiding secrets.
func (s *Store) Get(ctx context.Context, userID string) (Settings, error) {
	var out Settings
	var calibreURL, calibreUser, zoteroUser *string
	var hasCalibrePass, hasZoteroKey bool

	err := s.Pool.QueryRow(ctx, `
		select calibre_url, calibre_user,
		       (calibre_password_enc is not null) as has_calibre_password,
		       zotero_user_id,
		       (zotero_api_key_enc is not null) as has_zotero_key
		from server_settings where user_id = $1`, userID,
	).Scan(&calibreURL, &calibreUser, &hasCalibrePass, &zoteroUser, &hasZoteroKey)
	if errors.Is(err, pgx.ErrNoRows) {
		return Settings{}, nil
	}
	if err != nil {
		return Settings{}, fmt.Errorf("servers: get: %w", err)
	}

	if calibreURL != nil {
		out.CalibreURL = *calibreURL
	}
	if calibreUser != nil {
		out.CalibreUser = *calibreUser
	}
	if zoteroUser != nil {
		out.ZoteroUserID = *zoteroUser
	}
	out.HasCalibrePassword = hasCalibrePass
	out.HasZoteroKey = hasZoteroKey
	return out, nil
}

// Save applies an update and returns the resulting settings.
func (s *Store) Save(ctx context.Context, userID string, upd appservers.Update) (Settings, error) {
	var calibreURL, calibreUser, zoteroUser *string
	var calibrePassEnc, zoteroKeyEnc []byte

	err := s.Pool.QueryRow(ctx, `
		select calibre_url, calibre_user, calibre_password_enc, zotero_user_id, zotero_api_key_enc
		from server_settings where user_id = $1`, userID,
	).Scan(&calibreURL, &calibreUser, &calibrePassEnc, &zoteroUser, &zoteroKeyEnc)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return Settings{}, fmt.Errorf("servers: load: %w", err)
	}

	if upd.CalibreURL != nil {
		calibreURL = nilIfEmpty(*upd.CalibreURL)
	}
	if upd.CalibreUser != nil {
		calibreUser = nilIfEmpty(*upd.CalibreUser)
	}
	if upd.ZoteroUserID != nil {
		zoteroUser = nilIfEmpty(*upd.ZoteroUserID)
	}

	if upd.CalibrePassword != nil {
		if *upd.CalibrePassword == "" {
			calibrePassEnc = nil
		} else {
			sealed, err := s.seal(*upd.CalibrePassword)
			if err != nil {
				return Settings{}, err
			}
			calibrePassEnc = sealed
		}
	}
	if upd.ZoteroAPIKey != nil {
		if *upd.ZoteroAPIKey == "" {
			zoteroKeyEnc = nil
		} else {
			sealed, err := s.seal(*upd.ZoteroAPIKey)
			if err != nil {
				return Settings{}, err
			}
			zoteroKeyEnc = sealed
		}
	}

	if _, err := s.Pool.Exec(ctx, `
		insert into server_settings
		  (user_id, calibre_url, calibre_user, calibre_password_enc, zotero_user_id, zotero_api_key_enc, updated_at)
		values ($1, $2, $3, $4, $5, $6, now())
		on conflict (user_id) do update set
			calibre_url = excluded.calibre_url,
			calibre_user = excluded.calibre_user,
			calibre_password_enc = excluded.calibre_password_enc,
			zotero_user_id = excluded.zotero_user_id,
			zotero_api_key_enc = excluded.zotero_api_key_enc,
			updated_at = now()`,
		userID, calibreURL, calibreUser, calibrePassEnc, zoteroUser, zoteroKeyEnc,
	); err != nil {
		return Settings{}, fmt.Errorf("servers: save: %w", err)
	}

	return s.Get(ctx, userID)
}

// Credentials decrypts the stored secrets for a proxied call.
func (s *Store) Credentials(ctx context.Context, userID string) (Credentials, error) {
	var out Credentials
	var calibreURL, calibreUser, zoteroUser *string
	var calibrePassEnc, zoteroKeyEnc []byte

	err := s.Pool.QueryRow(ctx, `
		select calibre_url, calibre_user, calibre_password_enc, zotero_user_id, zotero_api_key_enc
		from server_settings where user_id = $1`, userID,
	).Scan(&calibreURL, &calibreUser, &calibrePassEnc, &zoteroUser, &zoteroKeyEnc)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return Credentials{}, fmt.Errorf("servers: credentials: %w", err)
	}

	if calibreURL != nil {
		out.CalibreURL = *calibreURL
	}
	if calibreUser != nil {
		out.CalibreUser = *calibreUser
	}
	if zoteroUser != nil {
		out.ZoteroUserID = *zoteroUser
	}
	if len(calibrePassEnc) > 0 {
		plain, err := s.open(calibrePassEnc)
		if err != nil {
			return Credentials{}, err
		}
		out.CalibrePassword = plain
	}
	if len(zoteroKeyEnc) > 0 {
		plain, err := s.open(zoteroKeyEnc)
		if err != nil {
			return Credentials{}, err
		}
		out.ZoteroAPIKey = plain
	}
	return out, nil
}

func (s *Store) seal(plaintext string) ([]byte, error) {
	if s.Box == nil {
		return nil, ErrEncryptionUnavailable
	}
	return s.Box.Seal([]byte(plaintext))
}

func (s *Store) open(sealed []byte) (string, error) {
	if s.Box == nil {
		return "", ErrEncryptionUnavailable
	}
	plain, err := s.Box.Open(sealed)
	if err != nil {
		return "", err
	}
	return string(plain), nil
}

func nilIfEmpty(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
