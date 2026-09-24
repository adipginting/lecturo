package documents

import (
	"context"
	"errors"
	"io"
	"testing"

	domain "lecturo/server/internal/domain/library"
)

type fakeRepo struct {
	getErr  error
	created domain.Document
	got     CreateInput
}

func (f *fakeRepo) List(context.Context, string, int, string) ([]domain.Document, string, error) {
	return nil, "", nil
}

func (f *fakeRepo) Create(_ context.Context, in CreateInput) (domain.Document, error) {
	f.got = in
	f.created = domain.Document{ID: "doc-1", Title: in.Title}
	return f.created, nil
}

func (f *fakeRepo) Get(context.Context, string, string) (domain.Document, error) {
	if f.getErr != nil {
		return domain.Document{}, f.getErr
	}
	return domain.Document{ID: "doc-1"}, nil
}

func (f *fakeRepo) Open(context.Context, string, string) (io.ReadCloser, string, error) {
	return nil, "", nil
}

func (f *fakeRepo) UpdatePosition(context.Context, string, string, string) error { return nil }

func (f *fakeRepo) Delete(context.Context, string, string) error { return nil }

func TestGetPassesNotFoundThrough(t *testing.T) {
	service := &Service{Repo: &fakeRepo{getErr: domain.ErrNotFound}}

	if _, err := service.Get(context.Background(), "user", "missing"); !errors.Is(err, ErrNotFound) {
		t.Fatalf("want ErrNotFound, got %v", err)
	}
}

func TestCreateStoresWhatItWasGiven(t *testing.T) {
	repo := &fakeRepo{}
	service := &Service{Repo: repo}

	doc, err := service.Create(context.Background(), CreateInput{UserID: "user", Title: "Book", Data: []byte("%PDF-")})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if doc.ID != "doc-1" || repo.got.Title != "Book" || repo.got.UserID != "user" {
		t.Fatalf("unexpected: %+v / %+v", doc, repo.got)
	}
}
