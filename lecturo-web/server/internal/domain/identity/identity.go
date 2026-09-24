// Package identity is the domain model for accounts and sessions.
package identity

import (
	"errors"
	"net/mail"
	"strings"
	"time"
)

// User is an account.
type User struct {
	ID        string
	Email     string
	CreatedAt time.Time
}

// Session is a signed-in browser session. Only a hash of the token is stored;
// the token itself lives in the cookie.
type Session struct {
	UserID    string
	CSRFToken string
	ExpiresAt time.Time
}

// Domain errors.
var (
	ErrInvalidEmail       = errors.New("identity: invalid email")
	ErrWeakPassword       = errors.New("identity: password too short")
	ErrEmailTaken         = errors.New("identity: email already registered")
	ErrInvalidCredentials = errors.New("identity: wrong email or password")
	ErrNoSession          = errors.New("identity: no session")
)

// MinPasswordLength is the shortest accepted password.
const MinPasswordLength = 12

// NormalizeEmail lower-cases and validates an address.
func NormalizeEmail(raw string) (string, error) {
	email := strings.ToLower(strings.TrimSpace(raw))
	if email == "" {
		return "", ErrInvalidEmail
	}
	addr, err := mail.ParseAddress(email)
	if err != nil || addr.Address != email || !strings.Contains(email, "@") {
		return "", ErrInvalidEmail
	}
	return email, nil
}

// ValidatePassword enforces the password policy.
func ValidatePassword(password string) error {
	if len(password) < MinPasswordLength {
		return ErrWeakPassword
	}
	return nil
}
