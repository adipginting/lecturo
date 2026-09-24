// Package remotelib browses and downloads from Calibre (OPDS) and Zotero
// (Web API) behind one interface. Credentials are supplied per call from the
// server-side settings store; they never reach the browser.
package remotelib

import (
	"errors"

	appcatalog "lecturo/server/internal/application/catalog"
	domain "lecturo/server/internal/domain/catalog"
)

// Item and Page are the domain model; this package fetches them.
type (
	Item = domain.Item
	Page = domain.Page
)

// Errors.
var (
	ErrNotConfigured = domain.ErrNotConfigured
	ErrUpstream      = errors.New("remotelib: upstream failed")
)

// For returns the source for "calibre" or "zotero".
func For(name string) (appcatalog.Source, bool) {
	switch name {
	case "calibre":
		return calibreSource{}, true
	case "zotero":
		return zoteroSource{}, true
	default:
		return nil, false
	}
}
