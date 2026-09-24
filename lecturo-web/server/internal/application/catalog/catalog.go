// Package catalog is the application layer for remote libraries (Calibre OPDS,
// Zotero Web API): the use cases the HTTP handlers call. The item/page model
// lives in domain/catalog; the sources, credentials and provenance are ports.
package catalog

import (
	"context"
	"errors"
	"strings"

	appdocuments "lecturo/server/internal/application/documents"
	appservers "lecturo/server/internal/application/servers"
	domain "lecturo/server/internal/domain/catalog"
	domainlib "lecturo/server/internal/domain/library"
)

type (
	// Item and Page are the domain model.
	Item = domain.Item
	Page = domain.Page
	// Credentials are the decrypted server settings the sources call with.
	Credentials = appservers.Credentials
)

// ErrNotConfigured means the source has no address/credentials yet.
var ErrNotConfigured = domain.ErrNotConfigured

// ErrUnknownSource means the source name is not "calibre" or "zotero".
var ErrUnknownSource = errors.New("catalog: unknown source")

// Source is one remote library.
type Source interface {
	Browse(ctx context.Context, creds Credentials, path string) (Page, error)
	Search(ctx context.Context, creds Credentials, query string) (Page, error)
	Fetch(ctx context.Context, creds Credentials, url string) ([]byte, string, error)
}

// Provenance reports which remote ids already have a local document.
type Provenance interface {
	DownloadedIDs(ctx context.Context, userID, source string, remoteIDs []string) (map[string]string, error)
}

// PageResult is a listing plus which of its items are already downloaded.
type PageResult struct {
	Page       Page
	Downloaded map[string]string
}

// Service holds the catalog use cases.
type Service struct {
	Calibre    Source
	Zotero     Source
	Servers    *appservers.Service
	Documents  *appdocuments.Service
	Provenance Provenance
}

// For returns the source for a name.
func (s *Service) For(name string) (Source, bool) {
	switch strings.ToLower(name) {
	case "calibre":
		return s.Calibre, s.Calibre != nil
	case "zotero":
		return s.Zotero, s.Zotero != nil
	default:
		return nil, false
	}
}

// Browse lists a source's catalog at the given path.
func (s *Service) Browse(ctx context.Context, userID, source, path string) (PageResult, error) {
	return s.page(ctx, userID, source, func(src Source, creds Credentials) (Page, error) {
		return src.Browse(ctx, creds, path)
	})
}

// Search asks a source's own search.
func (s *Service) Search(ctx context.Context, userID, source, query string) (PageResult, error) {
	return s.page(ctx, userID, source, func(src Source, creds Credentials) (Page, error) {
		return src.Search(ctx, creds, query)
	})
}

// Download fetches an item and stores it in the local library, with provenance.
func (s *Service) Download(ctx context.Context, userID, source, remoteID, url, title string) (domainlib.Document, error) {
	src, ok := s.For(source)
	if !ok {
		return domainlib.Document{}, ErrUnknownSource
	}
	creds, err := s.Servers.Credentials(ctx, userID)
	if err != nil {
		return domainlib.Document{}, err
	}
	data, _, err := src.Fetch(ctx, creds, url)
	if err != nil {
		return domainlib.Document{}, err
	}
	if strings.TrimSpace(title) == "" {
		title = "Untitled"
	}
	name := strings.ToLower(source)
	id := remoteID
	return s.Documents.Create(ctx, appdocuments.CreateInput{
		UserID:   userID,
		Title:    title,
		Data:     data,
		Source:   &name,
		RemoteID: &id,
	})
}

func (s *Service) page(ctx context.Context, userID, source string, load func(Source, Credentials) (Page, error)) (PageResult, error) {
	src, ok := s.For(source)
	if !ok {
		return PageResult{}, ErrUnknownSource
	}
	creds, err := s.Servers.Credentials(ctx, userID)
	if err != nil {
		return PageResult{}, err
	}
	page, err := load(src, creds)
	if err != nil {
		return PageResult{}, err
	}

	downloaded := map[string]string{}
	if s.Provenance != nil && len(page.Items) > 0 {
		ids := make([]string, 0, len(page.Items))
		for _, item := range page.Items {
			ids = append(ids, item.RemoteID)
		}
		if found, err := s.Provenance.DownloadedIDs(ctx, userID, strings.ToLower(source), ids); err == nil {
			downloaded = found
		}
	}
	return PageResult{Page: page, Downloaded: downloaded}, nil
}
