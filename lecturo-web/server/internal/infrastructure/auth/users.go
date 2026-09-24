package auth

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"

	domain "lecturo/server/internal/domain/identity"
)

// PasswordHasher adapts the password functions to the application port.
type PasswordHasher struct{}

func (PasswordHasher) Hash(password string) (string, error) { return HashPassword(password) }

func (PasswordHasher) Verify(encoded, password string) (bool, error) {
	return VerifyPassword(encoded, password)
}

// UserStore persists accounts.
type UserStore struct {
	Pool *pgxpool.Pool
}

// Create inserts a user, mapping a duplicate email to the domain error.
func (s *UserStore) Create(ctx context.Context, email, passwordHash string) (domain.User, error) {
	var user domain.User
	err := s.Pool.QueryRow(ctx,
		`insert into users (email, password_hash) values ($1, $2)
		 returning id::text, email, created_at`,
		email, passwordHash,
	).Scan(&user.ID, &user.Email, &user.CreatedAt)
	if err != nil {
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == "23505" {
			return domain.User{}, domain.ErrEmailTaken
		}
		return domain.User{}, err
	}
	return user, nil
}

// FindByEmail returns the user and their stored password hash.
func (s *UserStore) FindByEmail(ctx context.Context, email string) (domain.User, string, error) {
	var user domain.User
	var hash string
	err := s.Pool.QueryRow(ctx,
		`select id::text, email, password_hash, created_at
		 from users where lower(email) = $1`, email,
	).Scan(&user.ID, &user.Email, &hash, &user.CreatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.User{}, "", domain.ErrInvalidCredentials
	}
	if err != nil {
		return domain.User{}, "", err
	}
	return user, hash, nil
}

// FindByID returns the account for a session's owner.
func (s *UserStore) FindByID(ctx context.Context, id string) (domain.User, error) {
	var user domain.User
	err := s.Pool.QueryRow(ctx,
		`select id::text, email, created_at from users where id = $1`, id,
	).Scan(&user.ID, &user.Email, &user.CreatedAt)
	if err != nil {
		return domain.User{}, err
	}
	return user, nil
}
