// Package skills is the application layer for installed skills: the use cases
// the HTTP handlers call. The model lives in domain/skills; the marketplace and
// the store are ports the infrastructure implements.
package skills

import (
	"context"

	domain "lecturo/server/internal/domain/skills"
)

type (
	// Skill is an installed skill.
	Skill = domain.Skill
	// RemoteSkill is a marketplace entry.
	RemoteSkill = domain.RemoteSkill
	// SkillRun records one run.
	SkillRun = domain.SkillRun
)

// Repository is the persistence port for skills and their runs.
type Repository interface {
	List(ctx context.Context, userID string) ([]Skill, error)
	Install(ctx context.Context, userID string, skill RemoteSkill) error
	Uninstall(ctx context.Context, userID, skillID string) error
	SetEnabled(ctx context.Context, userID, skillID string, enabled bool) error
	AddRun(ctx context.Context, userID string, skill Skill, documentID *string) (string, error)
	CompleteRun(ctx context.Context, userID, runID string, status domain.RunStatus, result, errText string) error
	ListRuns(ctx context.Context, userID string, documentID *string) ([]SkillRun, error)
}

// Catalog fetches a marketplace's skills.
type Catalog interface {
	Fetch(ctx context.Context, marketplaceURL string) ([]RemoteSkill, error)
}

// Service holds the skill use cases.
type Service struct {
	Repo    Repository
	Catalog Catalog
}

// List returns the user's installed skills.
func (s *Service) List(ctx context.Context, userID string) ([]Skill, error) {
	return s.Repo.List(ctx, userID)
}

// Browse fetches the marketplace catalog.
func (s *Service) Browse(ctx context.Context, url string) ([]RemoteSkill, error) {
	return s.Catalog.Fetch(ctx, url)
}

// Install stores a marketplace skill.
func (s *Service) Install(ctx context.Context, userID string, skill RemoteSkill) error {
	return s.Repo.Install(ctx, userID, skill)
}

// Uninstall removes a skill.
func (s *Service) Uninstall(ctx context.Context, userID, skillID string) error {
	return s.Repo.Uninstall(ctx, userID, skillID)
}

// SetEnabled turns a skill on or off.
func (s *Service) SetEnabled(ctx context.Context, userID, skillID string, enabled bool) error {
	return s.Repo.SetEnabled(ctx, userID, skillID, enabled)
}

// Runs lists recent runs, optionally for one document.
func (s *Service) Runs(ctx context.Context, userID string, documentID *string) ([]SkillRun, error) {
	return s.Repo.ListRuns(ctx, userID, documentID)
}
