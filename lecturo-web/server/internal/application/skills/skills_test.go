package skills

import (
	"context"
	"testing"

	domain "lecturo/server/internal/domain/skills"
)

type fakeRepo struct {
	installed []Skill
	saved     bool
}

func (f *fakeRepo) List(context.Context, string) ([]Skill, error) { return f.installed, nil }

func (f *fakeRepo) Install(context.Context, string, RemoteSkill) error {
	f.saved = true
	return nil
}

func (f *fakeRepo) Uninstall(context.Context, string, string) error { return nil }

func (f *fakeRepo) SetEnabled(context.Context, string, string, bool) error { return nil }

func (f *fakeRepo) AddRun(context.Context, string, Skill, *string) (string, error) {
	return "run-1", nil
}

func (f *fakeRepo) CompleteRun(context.Context, string, string, domain.RunStatus, string, string) error {
	return nil
}

func (f *fakeRepo) ListRuns(context.Context, string, *string) ([]SkillRun, error) { return nil, nil }

type fakeCatalog struct{ skills []RemoteSkill }

func (f fakeCatalog) Fetch(context.Context, string) ([]RemoteSkill, error) { return f.skills, nil }

func TestListDelegatesToTheRepository(t *testing.T) {
	service := &Service{Repo: &fakeRepo{installed: []Skill{{SkillID: "a"}}}, Catalog: fakeCatalog{}}

	got, err := service.List(context.Background(), "user")
	if err != nil || len(got) != 1 || got[0].SkillID != "a" {
		t.Fatalf("got %+v (%v)", got, err)
	}
}

func TestBrowseUsesTheCatalog(t *testing.T) {
	service := &Service{Repo: &fakeRepo{}, Catalog: fakeCatalog{skills: []RemoteSkill{{SkillID: "x"}}}}

	got, err := service.Browse(context.Background(), "https://example.test/skills.json")
	if err != nil || len(got) != 1 || got[0].SkillID != "x" {
		t.Fatalf("got %+v (%v)", got, err)
	}
}

func TestInstallDelegatesToTheRepository(t *testing.T) {
	repo := &fakeRepo{}
	service := &Service{Repo: repo, Catalog: fakeCatalog{}}

	if err := service.Install(context.Background(), "user", RemoteSkill{SkillID: "s"}); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !repo.saved {
		t.Fatalf("install should reach the repository")
	}
}
