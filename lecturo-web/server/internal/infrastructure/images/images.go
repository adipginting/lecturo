// Package images stores screenshot crops: the bytes live in the BlobStore and a
// row records who owns them and what they are.
package images

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	domain "lecturo/server/internal/domain/crop"
	"lecturo/server/internal/infrastructure/blob"
)

// Image is a stored crop; the model lives in the domain.
type Image = domain.Crop

// ErrNotFound means the image does not exist for this user.
var ErrNotFound = domain.ErrNotFound

// ErrNotAnImage means the payload is not a supported image.
var ErrNotAnImage = domain.ErrNotACrop

// Store persists images.
type Store struct {
	Pool *pgxpool.Pool
	Blob blob.Store
}

// Detect reports the content type of a supported image, or "".
func Detect(data []byte) string { return domain.Detect(data) }

// Create sniffs the payload, stores it and inserts the row.
func (s *Store) Create(ctx context.Context, userID string, data []byte) (Image, error) {
	contentType := Detect(data)
	if contentType == "" {
		return Image{}, ErrNotAnImage
	}

	id := uuid.NewString()
	ext := extension(contentType)
	key := fmt.Sprintf("images/%s/%s.%s", userID, id, ext)

	if err := s.Blob.Put(ctx, key, bytes.NewReader(data), int64(len(data)), contentType); err != nil {
		return Image{}, err
	}

	var img Image
	err := s.Pool.QueryRow(ctx, `
		insert into images (id, user_id, content_type, byte_size, blob_key)
		values ($1, $2, $3, $4, $5)
		returning id::text, content_type, byte_size, created_at`,
		id, userID, contentType, len(data), key,
	).Scan(&img.ID, &img.ContentType, &img.ByteSize, &img.CreatedAt)
	if err != nil {
		_ = s.Blob.Delete(ctx, key)
		return Image{}, fmt.Errorf("images: insert: %w", err)
	}
	return img, nil
}

// Open streams an image's bytes.
func (s *Store) Open(ctx context.Context, userID, id string) (io.ReadCloser, string, error) {
	var key, contentType string
	err := s.Pool.QueryRow(ctx, `
		select blob_key, content_type from images
		where user_id = $1 and id = $2`, userID, id).Scan(&key, &contentType)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, "", ErrNotFound
	}
	if err != nil {
		return nil, "", fmt.Errorf("images: open: %w", err)
	}

	rc, err := s.Blob.Get(ctx, key)
	if err != nil {
		return nil, "", err
	}
	return rc, contentType, nil
}

// Load reads an image fully, for building a vision request payload.
func (s *Store) Load(ctx context.Context, userID, id string) ([]byte, string, error) {
	rc, contentType, err := s.Open(ctx, userID, id)
	if err != nil {
		return nil, "", err
	}
	defer rc.Close()

	data, err := io.ReadAll(io.LimitReader(rc, 20<<20))
	if err != nil {
		return nil, "", err
	}
	return data, contentType, nil
}

func extension(contentType string) string {
	switch contentType {
	case "image/png":
		return "png"
	case "image/jpeg":
		return "jpg"
	case "image/gif":
		return "gif"
	case "image/webp":
		return "webp"
	default:
		return "bin"
	}
}
