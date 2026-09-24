// Package prompts owns the reusable prompt templates ("skills").
package prompts

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	domain "lecturo/server/internal/domain/reading"
)

// MaxPrompts caps how many templates one user may keep; the model lives in the domain.
const MaxPrompts = domain.MaxPrompts

// Prompt is one saved template.
type Prompt = domain.Prompt

// ErrNotFound means the prompt does not exist for this user.
var ErrNotFound = domain.ErrPromptNotFound

// ErrCapReached means the user already has MaxPrompts templates.
var ErrCapReached = domain.ErrPromptCap

// Store persists saved prompts.
type Store struct {
	Pool *pgxpool.Pool
}

// Get returns one prompt.
func (s *Store) Get(ctx context.Context, userID, id string) (Prompt, error) {
	var p Prompt
	err := s.Pool.QueryRow(ctx,
		`select id::text, title, body from saved_prompts where user_id = $1 and id = $2`,
		userID, id,
	).Scan(&p.ID, &p.Title, &p.Body)
	if errors.Is(err, pgx.ErrNoRows) {
		return Prompt{}, ErrNotFound
	}
	if err != nil {
		return Prompt{}, fmt.Errorf("prompts: get: %w", err)
	}
	return p, nil
}

// List returns the user's prompts, oldest first (stable order for chips).
func (s *Store) List(ctx context.Context, userID string) ([]Prompt, error) {
	rows, err := s.Pool.Query(ctx, `
		select id::text, title, body from saved_prompts
		where user_id = $1
		order by created_at`, userID)
	if err != nil {
		return nil, fmt.Errorf("prompts: list: %w", err)
	}
	defer rows.Close()

	out := make([]Prompt, 0, MaxPrompts)
	for rows.Next() {
		var p Prompt
		if err := rows.Scan(&p.ID, &p.Title, &p.Body); err != nil {
			return nil, fmt.Errorf("prompts: list scan: %w", err)
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

// Create adds a prompt if the user is under the cap.
func (s *Store) Create(ctx context.Context, userID, title, body string) (Prompt, error) {
	var p Prompt
	err := s.Pool.QueryRow(ctx, `
		insert into saved_prompts (user_id, title, body)
		select $1, $2, $3
		where (select count(*) from saved_prompts where user_id = $1) < $4
		returning id::text, title, body`,
		userID, title, body, MaxPrompts,
	).Scan(&p.ID, &p.Title, &p.Body)
	if errors.Is(err, pgx.ErrNoRows) {
		return Prompt{}, ErrCapReached
	}
	if err != nil {
		return Prompt{}, fmt.Errorf("prompts: create: %w", err)
	}
	return p, nil
}

// Update changes the provided fields.
func (s *Store) Update(ctx context.Context, userID, id string, title, body *string) (Prompt, error) {
	var p Prompt
	err := s.Pool.QueryRow(ctx, `
		update saved_prompts
		set title = coalesce($3, title), body = coalesce($4, body)
		where user_id = $1 and id = $2
		returning id::text, title, body`,
		userID, id, title, body,
	).Scan(&p.ID, &p.Title, &p.Body)
	if errors.Is(err, pgx.ErrNoRows) {
		return Prompt{}, ErrNotFound
	}
	if err != nil {
		return Prompt{}, fmt.Errorf("prompts: update: %w", err)
	}
	return p, nil
}

// Delete removes a prompt.
func (s *Store) Delete(ctx context.Context, userID, id string) error {
	tag, err := s.Pool.Exec(ctx,
		`delete from saved_prompts where user_id = $1 and id = $2`, userID, id)
	if err != nil {
		return fmt.Errorf("prompts: delete: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}
