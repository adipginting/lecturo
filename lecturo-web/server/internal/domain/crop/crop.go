// Package crop is the domain model for screenshot crops used as a
// conversation's frozen context.
package crop

import (
	"bytes"
	"errors"
	"time"
)

// Crop is one stored image.
type Crop struct {
	ID          string
	ContentType string
	ByteSize    int64
	CreatedAt   time.Time
}

// Domain errors.
var (
	ErrNotFound = errors.New("crop: not found")
	ErrNotACrop = errors.New("crop: not a supported image")
)

// MaxBytes caps an uploaded crop.
const MaxBytes = 12 << 20

// Detect reports the content type of a supported image, or "".
func Detect(data []byte) string {
	switch {
	case len(data) >= 8 && bytes.Equal(data[:8], []byte{0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a}):
		return "image/png"
	case len(data) >= 3 && data[0] == 0xff && data[1] == 0xd8 && data[2] == 0xff:
		return "image/jpeg"
	case len(data) >= 6 && (bytes.HasPrefix(data, []byte("GIF87a")) || bytes.HasPrefix(data, []byte("GIF89a"))):
		return "image/gif"
	case len(data) >= 12 && bytes.HasPrefix(data, []byte("RIFF")) && bytes.Equal(data[8:12], []byte("WEBP")):
		return "image/webp"
	default:
		return ""
	}
}
