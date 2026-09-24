package provider

import (
	"context"
	"errors"
	"testing"

	domain "lecturo/server/internal/domain/settings"
)

type fakeRepo struct{ getErr error }

func (f *fakeRepo) Get(context.Context, string) (domain.Selection, error) {
	if f.getErr != nil {
		return domain.Selection{}, f.getErr
	}
	return domain.Selection{Provider: "deepseek"}, nil
}

func (f *fakeRepo) Save(context.Context, string, Update) (domain.Selection, error) {
	return domain.Selection{}, nil
}

func (f *fakeRepo) Credentials(context.Context, string, string) (string, string, string, error) {
	return "", "", "", nil
}

func (f *fakeRepo) Models(context.Context, string, string, string) ([]string, error) {
	return nil, nil
}

func (f *fakeRepo) VisionOverride(context.Context, string, string) (*bool, error) {
	return nil, nil
}

func TestGetDelegatesToTheRepository(t *testing.T) {
	service := &Service{Repo: &fakeRepo{}}

	got, err := service.Get(context.Background(), "user")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got.Provider != "deepseek" {
		t.Fatalf("got %+v", got)
	}
}

func TestGetPassesAnErrorThrough(t *testing.T) {
	service := &Service{Repo: &fakeRepo{getErr: ErrNoKey}}

	if _, err := service.Get(context.Background(), "user"); !errors.Is(err, ErrNoKey) {
		t.Fatalf("want ErrNoKey, got %v", err)
	}
}

func TestLookupFindsTheDefaultProvider(t *testing.T) {
	if _, ok := Lookup(DefaultProvider); !ok {
		t.Fatalf("the default provider should be in the catalogue")
	}
}
