// Package library is the domain model for the app-owned document library.
package library

import (
	"errors"
	"time"
)

// Supported formats.
const (
	FormatPDF  = "pdf"
	FormatEPUB = "epub"
)

// Document is one book in the library. Bytes live in the blob store at BlobKey;
// only the location is modelled here.
type Document struct {
	ID        string
	Title     string
	Format    string
	Locator   *string
	ByteSize  *int64
	BlobKey   string
	Source    *string
	RemoteID  *string
	CreatedAt time.Time
	UpdatedAt time.Time
}

// MaxUploadBytes caps an imported payload.
const MaxUploadBytes = 256 << 20

// Domain errors.
var (
	ErrNotFound = errors.New("library: not found")
	ErrNotABook = errors.New("library: not a PDF or EPUB")
)
