package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	domain "lecturo/server/internal/domain/identity"
)

const (
	// SessionCookie holds the opaque session token (httpOnly).
	SessionCookie = "lecturo_session"
	// CSRFCookie is readable by the app so it can echo the value in a header.
	CSRFCookie = "lecturo_csrf"

	// SessionTTL is how long a session stays valid.
	SessionTTL = 30 * 24 * time.Hour
)

// ErrNoSession means the token is unknown or expired.
var ErrNoSession = domain.ErrNoSession

// SessionStore persists sessions in Postgres. Only a hash of the cookie token
// is stored, so a database leak does not yield usable cookies.
type SessionStore struct {
	Pool *pgxpool.Pool
}

// Session is the resolved server-side session; the model lives in the domain.
type Session = domain.Session

// Create starts a session for the user and returns the cookie token and CSRF token.
func (st *SessionStore) Create(ctx context.Context, userID string) (token, csrf string, expires time.Time, err error) {
	token, err = randomToken()
	if err != nil {
		return "", "", time.Time{}, err
	}
	csrf, err = randomToken()
	if err != nil {
		return "", "", time.Time{}, err
	}
	expires = time.Now().Add(SessionTTL)

	_, err = st.Pool.Exec(ctx, `
		insert into sessions (token_hash, user_id, csrf_token, expires_at)
		values ($1, $2, $3, $4)`,
		hashToken(token), userID, csrf, expires)
	if err != nil {
		return "", "", time.Time{}, fmt.Errorf("auth: create session: %w", err)
	}

	// Opportunistic cleanup of this user's expired sessions.
	_, _ = st.Pool.Exec(ctx, `delete from sessions where user_id = $1 and expires_at < now()`, userID)

	return token, csrf, expires, nil
}

// Get resolves a session token, deleting it if expired.
func (st *SessionStore) Get(ctx context.Context, token string) (Session, error) {
	var s Session
	err := st.Pool.QueryRow(ctx, `
		select user_id::text, csrf_token, expires_at
		from sessions where token_hash = $1`, hashToken(token),
	).Scan(&s.UserID, &s.CSRFToken, &s.ExpiresAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return Session{}, ErrNoSession
	}
	if err != nil {
		return Session{}, fmt.Errorf("auth: get session: %w", err)
	}

	if time.Now().After(s.ExpiresAt) {
		_, _ = st.Pool.Exec(ctx, `delete from sessions where token_hash = $1`, hashToken(token))
		return Session{}, ErrNoSession
	}
	return s, nil
}

// Delete ends a session.
func (st *SessionStore) Delete(ctx context.Context, token string) error {
	_, err := st.Pool.Exec(ctx, `delete from sessions where token_hash = $1`, hashToken(token))
	return err
}

func randomToken() (string, error) {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("auth: random: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(buf), nil
}

func hashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}
