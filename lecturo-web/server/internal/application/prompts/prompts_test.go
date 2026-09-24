package prompts

import (
	"context"
	"errors"
	"testing"

	domain "lecturo/server/internal/domain/reading"
)

type fakeRepo struct {
	createErr error
	created   domain.Prompt
}

func (f *fakeRepo) List(context.Context, string) ([]domain.Prompt, error) { return nil, nil }

func (f *fakeRepo) Get(context.Context, string, string) (domain.Prompt, error) {
	return domain.Prompt{}, nil
}

func (f *fakeRepo) Create(_ context.Context, _, title, body string) (domain.Prompt, error) {
	if f.createErr != nil {
		return domain.Prompt{}, f.createErr
	}
	f.created = domain.Prompt{Title: title, Body: body}
	return f.created, nil
}

func (f *fakeRepo) Update(context.Context, string, string, *string, *string) (domain.Prompt, error) {
	return domain.Prompt{}, nil
}

func (f *fakeRepo) Delete(context.Context, string, string) error { return nil }

func TestCreateRefusesAnEmptyPrompt(t *testing.T) {
	service := &Service{Repo: &fakeRepo{}}

	if _, err := service.Create(context.Background(), "user", "   ", "body"); !errors.Is(err, domain.ErrEmpty) {
		t.Fatalf("want ErrEmpty, got %v", err)
	}
}

func TestCreatePassesTheCapThrough(t *testing.T) {
	service := &Service{Repo: &fakeRepo{createErr: domain.ErrPromptCap}}

	if _, err := service.Create(context.Background(), "user", "Title", "Body"); !errors.Is(err, ErrCapReached) {
		t.Fatalf("want ErrCapReached, got %v", err)
	}
}

func TestCreateStoresATrimmedValidPrompt(t *testing.T) {
	repo := &fakeRepo{}
	service := &Service{Repo: repo}

	got, err := service.Create(context.Background(), "user", "Title", "Body")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got.Title != "Title" || got.Body != "Body" {
		t.Fatalf("stored %+v", got)
	}
}
