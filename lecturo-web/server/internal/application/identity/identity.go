// Package identity is the application layer for accounts and sessions: the use
// cases the HTTP handlers call. The model and rules live in domain/identity;
// persistence and password hashing are ports the infrastructure implements.
package identity

import (
	"context"
	"time"

	domain "lecturo/server/internal/domain/identity"
)

type (
	// User is an account.
	User = domain.User
	// Session is a signed-in browser session.
	Session = domain.Session
)

// Errors, re-exported so callers need not reach into the domain.
var (
	ErrInvalidEmail       = domain.ErrInvalidEmail
	ErrWeakPassword       = domain.ErrWeakPassword
	ErrEmailTaken         = domain.ErrEmailTaken
	ErrInvalidCredentials = domain.ErrInvalidCredentials
	ErrNoSession          = domain.ErrNoSession
)

// UserRepository stores accounts.
type UserRepository interface {
	Create(ctx context.Context, email, passwordHash string) (User, error)
	// FindByEmail returns the user and their password hash.
	FindByEmail(ctx context.Context, email string) (User, string, error)
	// FindByID returns the user for a session's owner.
	FindByID(ctx context.Context, id string) (User, error)
}

// SessionRepository stores sessions. Only a hash of the token is kept.
type SessionRepository interface {
	Create(ctx context.Context, userID string) (token, csrf string, expires time.Time, err error)
	Get(ctx context.Context, token string) (Session, error)
	Delete(ctx context.Context, token string) error
}

// PasswordHasher hashes and verifies passwords.
type PasswordHasher interface {
	Hash(password string) (string, error)
	Verify(encoded, password string) (bool, error)
}

// Service holds the identity use cases.
type Service struct {
	Users    UserRepository
	Sessions SessionRepository
	Hasher   PasswordHasher
}

// Register validates, stores and signs in a new account.
func (s *Service) Register(ctx context.Context, rawEmail, password string) (User, string, string, time.Time, error) {
	email, err := domain.NormalizeEmail(rawEmail)
	if err != nil {
		return User{}, "", "", time.Time{}, err
	}
	if err := domain.ValidatePassword(password); err != nil {
		return User{}, "", "", time.Time{}, err
	}
	hash, err := s.Hasher.Hash(password)
	if err != nil {
		return User{}, "", "", time.Time{}, err
	}
	user, err := s.Users.Create(ctx, email, hash)
	if err != nil {
		return User{}, "", "", time.Time{}, err
	}
	token, csrf, expires, err := s.Sessions.Create(ctx, user.ID)
	if err != nil {
		return User{}, "", "", time.Time{}, err
	}
	return user, token, csrf, expires, nil
}

// Login verifies credentials and signs in. A wrong email and a wrong password
// are the same error, so the response does not reveal which.
func (s *Service) Login(ctx context.Context, rawEmail, password string) (User, string, string, time.Time, error) {
	email, err := domain.NormalizeEmail(rawEmail)
	if err != nil {
		return User{}, "", "", time.Time{}, ErrInvalidCredentials
	}
	user, hash, err := s.Users.FindByEmail(ctx, email)
	if err != nil {
		return User{}, "", "", time.Time{}, ErrInvalidCredentials
	}
	ok, err := s.Hasher.Verify(hash, password)
	if err != nil || !ok {
		return User{}, "", "", time.Time{}, ErrInvalidCredentials
	}
	token, csrf, expires, err := s.Sessions.Create(ctx, user.ID)
	if err != nil {
		return User{}, "", "", time.Time{}, err
	}
	return user, token, csrf, expires, nil
}

// Logout deletes a session.
func (s *Service) Logout(ctx context.Context, token string) error {
	return s.Sessions.Delete(ctx, token)
}

// Session resolves a token to its session.
func (s *Service) Session(ctx context.Context, token string) (Session, error) {
	return s.Sessions.Get(ctx, token)
}

// User loads the account a session belongs to.
func (s *Service) User(ctx context.Context, id string) (User, error) {
	return s.Users.FindByID(ctx, id)
}
