package library

import (
	"bytes"
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	appdocuments "lecturo/server/internal/application/documents"
	domain "lecturo/server/internal/domain/library"
	"lecturo/server/internal/infrastructure/blob"
)

// Document is the domain model; this package persists it.
type Document = domain.Document

// ErrNotFound means the document does not exist for this user.
var ErrNotFound = domain.ErrNotFound

// ErrNotABook means the payload is neither a PDF nor an EPUB.
var ErrNotABook = domain.ErrNotABook

// Store persists documents in Postgres and their bytes in the BlobStore.
type Store struct {
	Pool *pgxpool.Pool
	Blob blob.Store
}

// Create sniffs the payload, stores the bytes and inserts the row.
func (s *Store) Create(ctx context.Context, in appdocuments.CreateInput) (Document, error) {
	format, ok := Detect(in.Data)
	if !ok {
		return Document{}, ErrNotABook
	}

	id := uuid.Must(uuid.NewV7()).String()
	key := fmt.Sprintf("documents/%s/%s.%s", in.UserID, id, format)

	if err := s.Blob.Put(ctx, key, bytes.NewReader(in.Data), int64(len(in.Data)), contentType(format)); err != nil {
		return Document{}, err
	}

	size := int64(len(in.Data))
	doc, err := scanDocument(s.Pool.QueryRow(ctx, `
		insert into documents (id, user_id, title, format, byte_size, blob_key, source, remote_id)
		values ($1, $2, $3, $4, $5, $6, $7, $8)
		returning `+documentColumns,
		id, in.UserID, in.Title, format, size, key, in.Source, in.RemoteID,
	))
	if err != nil {
		// Don't leave an orphaned blob behind.
		_ = s.Blob.Delete(ctx, key)
		return Document{}, fmt.Errorf("library: insert: %w", err)
	}
	return doc, nil
}

// List returns documents newest-first with an optional opaque cursor.
func (s *Store) List(ctx context.Context, userID string, limit int, cursor string) ([]Document, string, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}

	var cursorTime *time.Time
	var cursorID *string
	if cursor != "" {
		ts, id, err := decodeCursor(cursor)
		if err != nil {
			return nil, "", err
		}
		cursorTime, cursorID = &ts, &id
	}

	rows, err := s.Pool.Query(ctx, `
		select `+documentColumns+`
		from documents
		where user_id = $1
		  and ($2::timestamptz is null or (updated_at, id) < ($2, $3))
		order by updated_at desc, id desc
		limit $4`,
		userID, cursorTime, cursorID, limit+1)
	if err != nil {
		return nil, "", fmt.Errorf("library: list: %w", err)
	}
	defer rows.Close()

	docs := make([]Document, 0, limit)
	for rows.Next() {
		doc, err := scanDocument(rows)
		if err != nil {
			return nil, "", err
		}
		docs = append(docs, doc)
	}
	if err := rows.Err(); err != nil {
		return nil, "", fmt.Errorf("library: list: %w", err)
	}

	next := ""
	if len(docs) > limit {
		docs = docs[:limit]
		last := docs[len(docs)-1]
		next = encodeCursor(last.UpdatedAt, last.ID)
	}
	return docs, next, nil
}

// Get returns one document owned by the user.
func (s *Store) Get(ctx context.Context, userID, id string) (Document, error) {
	doc, err := scanDocument(s.Pool.QueryRow(ctx, `
		select `+documentColumns+`
		from documents where user_id = $1 and id = $2`, userID, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return Document{}, ErrNotFound
	}
	return doc, err
}

// UpdatePosition stores the opaque last-read locator.
func (s *Store) UpdatePosition(ctx context.Context, userID, id, locator string) error {
	tag, err := s.Pool.Exec(ctx, `
		update documents set locator = $3, updated_at = now()
		where user_id = $1 and id = $2`, userID, id, locator)
	if err != nil {
		return fmt.Errorf("library: position: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

// Open streams a document's bytes.
func (s *Store) Open(ctx context.Context, userID, id string) (io.ReadCloser, string, error) {
	var key, format string
	err := s.Pool.QueryRow(ctx, `
		select blob_key, format from documents
		where user_id = $1 and id = $2`, userID, id).Scan(&key, &format)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, "", ErrNotFound
	}
	if err != nil {
		return nil, "", fmt.Errorf("library: open: %w", err)
	}

	rc, err := s.Blob.Get(ctx, key)
	if err != nil {
		return nil, "", err
	}
	return rc, contentType(format), nil
}

// Delete removes the row and its blob.
func (s *Store) Delete(ctx context.Context, userID, id string) error {
	var key string
	err := s.Pool.QueryRow(ctx, `
		delete from documents where user_id = $1 and id = $2
		returning blob_key`, userID, id).Scan(&key)
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	if err != nil {
		return fmt.Errorf("library: delete: %w", err)
	}
	// Best effort: the row is gone either way.
	_ = s.Blob.Delete(ctx, key)
	return nil
}

// DownloadedIDs maps each remote id that already has a local document to that
// document's id, for a source. It backs the catalog's "Downloaded" marker.
func (s *Store) DownloadedIDs(ctx context.Context, userID, source string, remoteIDs []string) (map[string]string, error) {
	found := map[string]string{}
	if len(remoteIDs) == 0 {
		return found, nil
	}

	rows, err := s.Pool.Query(ctx, `
		select id::text, remote_id from documents
		where user_id = $1 and source = $2 and remote_id = any($3)`,
		userID, source, remoteIDs)
	if err != nil {
		return nil, fmt.Errorf("library: downloaded ids: %w", err)
	}
	defer rows.Close()

	for rows.Next() {
		var id, remoteID string
		if err := rows.Scan(&id, &remoteID); err != nil {
			return nil, err
		}
		found[remoteID] = id
	}
	return found, rows.Err()
}

const documentColumns = `
	id::text, title, format, locator, byte_size, source, remote_id, created_at, updated_at`

type rowScanner interface {
	Scan(dest ...any) error
}

func scanDocument(row rowScanner) (Document, error) {
	var d Document
	err := row.Scan(
		&d.ID, &d.Title, &d.Format, &d.Locator, &d.ByteSize,
		&d.Source, &d.RemoteID, &d.CreatedAt, &d.UpdatedAt,
	)
	if err != nil {
		return Document{}, err
	}
	return d, nil
}

func encodeCursor(updatedAt time.Time, id string) string {
	raw := updatedAt.UTC().Format(time.RFC3339Nano) + "|" + id
	return base64.RawURLEncoding.EncodeToString([]byte(raw))
}

func decodeCursor(cursor string) (time.Time, string, error) {
	raw, err := base64.RawURLEncoding.DecodeString(cursor)
	if err != nil {
		return time.Time{}, "", fmt.Errorf("library: bad cursor")
	}
	parts := strings.SplitN(string(raw), "|", 2)
	if len(parts) != 2 {
		return time.Time{}, "", fmt.Errorf("library: bad cursor")
	}
	ts, err := time.Parse(time.RFC3339Nano, parts[0])
	if err != nil {
		return time.Time{}, "", fmt.Errorf("library: bad cursor")
	}
	return ts, parts[1], nil
}
