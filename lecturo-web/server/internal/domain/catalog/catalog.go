// Package catalog is the domain model for items offered by a remote library
// (Calibre's OPDS catalogs and Zotero's attachments).
package catalog

import "errors"

// ErrNotConfigured means the source has no address/credentials yet.
var ErrNotConfigured = errors.New("catalog: source is not configured")

// Item is one entry from a remote catalog or search.
type Item struct {
	RemoteID  string
	Title     string
	Format    string // pdf | epub
	Author    string
	URL       string
	IsCatalog bool // a folder/collection to browse into, not a book
}

// Page is one catalog listing.
type Page struct {
	Title      string
	Path       string
	Searchable bool
	Items      []Item
}
