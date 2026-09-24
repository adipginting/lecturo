// Package skills adapts the skills domain to Postgres and a JSON marketplace.
package skills

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5/pgxpool"

	domain "lecturo/server/internal/domain/skills"
)

// Store persists installed skills and their runs.
type Store struct {
	Pool *pgxpool.Pool
}

const columns = `id::text, skill_id, name, description, author, version, scope, trigger, prompt, price, enabled`

// List returns the user's installed skills, by name.
func (s *Store) List(ctx context.Context, userID string) ([]domain.Skill, error) {
	rows, err := s.Pool.Query(ctx,
		`select `+columns+` from skills where user_id = $1 order by lower(name)`, userID)
	if err != nil {
		return nil, fmt.Errorf("skills: list: %w", err)
	}
	defer rows.Close()

	var out []domain.Skill
	for rows.Next() {
		var k domain.Skill
		if err := rows.Scan(&k.ID, &k.SkillID, &k.Name, &k.Description, &k.Author,
			&k.Version, &k.Scope, &k.Trigger, &k.Prompt, &k.Price, &k.Enabled); err != nil {
			return nil, err
		}
		out = append(out, k)
	}
	return out, rows.Err()
}

// Install stores a marketplace skill, updating one already installed.
func (s *Store) Install(ctx context.Context, userID string, skill domain.RemoteSkill) error {
	_, err := s.Pool.Exec(ctx, `
		insert into skills (user_id, skill_id, name, description, author, version, scope, trigger, prompt, price)
		values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		on conflict (user_id, skill_id) do update set
			name = excluded.name, description = excluded.description, author = excluded.author,
			version = excluded.version, scope = excluded.scope, trigger = excluded.trigger,
			prompt = excluded.prompt, price = excluded.price`,
		userID, skill.SkillID, skill.Name, skill.Description, skill.Author,
		skill.Version, skill.Scope, skill.Trigger, skill.Prompt, skill.Price)
	if err != nil {
		return fmt.Errorf("skills: install: %w", err)
	}
	return nil
}

// Uninstall removes an installed skill.
func (s *Store) Uninstall(ctx context.Context, userID, skillID string) error {
	_, err := s.Pool.Exec(ctx, `delete from skills where user_id = $1 and skill_id = $2`, userID, skillID)
	if err != nil {
		return fmt.Errorf("skills: uninstall: %w", err)
	}
	return nil
}

// SetEnabled toggles a skill.
func (s *Store) SetEnabled(ctx context.Context, userID, skillID string, enabled bool) error {
	_, err := s.Pool.Exec(ctx,
		`update skills set enabled = $3 where user_id = $1 and skill_id = $2`, userID, skillID, enabled)
	if err != nil {
		return fmt.Errorf("skills: enable: %w", err)
	}
	return nil
}

// AddRun records a run and returns its id.
func (s *Store) AddRun(ctx context.Context, userID string, skill domain.Skill, documentID *string) (string, error) {
	var id string
	err := s.Pool.QueryRow(ctx, `
		insert into skill_runs (user_id, skill_id, skill_name, document_id, status)
		values ($1, $2, $3, $4, 'running')
		returning id::text`,
		userID, skill.SkillID, skill.Name, documentID).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("skills: add run: %w", err)
	}
	return id, nil
}

// CompleteRun records a run's outcome.
func (s *Store) CompleteRun(ctx context.Context, userID, runID string, status domain.RunStatus, result, errText string) error {
	_, err := s.Pool.Exec(ctx,
		`update skill_runs set status = $3, result = $4, error = $5 where user_id = $1 and id = $2`,
		userID, runID, string(status), result, errText)
	if err != nil {
		return fmt.Errorf("skills: complete run: %w", err)
	}
	return nil
}

// ListRuns returns recent runs, optionally for one document.
func (s *Store) ListRuns(ctx context.Context, userID string, documentID *string) ([]domain.SkillRun, error) {
	rows, err := s.Pool.Query(ctx, `
		select id::text, skill_id, skill_name, document_id::text, status, result, error, created_at
		from skill_runs
		where user_id = $1 and ($2::uuid is null or document_id = $2)
		order by created_at desc limit 50`, userID, documentID)
	if err != nil {
		return nil, fmt.Errorf("skills: list runs: %w", err)
	}
	defer rows.Close()

	var out []domain.SkillRun
	for rows.Next() {
		var r domain.SkillRun
		if err := rows.Scan(&r.ID, &r.SkillID, &r.SkillName, &r.DocumentID, &r.Status,
			&r.Result, &r.Error, &r.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, rows.Err()
}
