// Package blob stores document bytes in an S3-compatible object store:
// Cloudflare R2 in production, SeaweedFS locally. The same code path serves
// both; only configuration differs.
package blob

import (
	"context"
	"io"
	"os"
)

// Store is the seam between the API and wherever bytes live.
type Store interface {
	Put(ctx context.Context, key string, r io.Reader, size int64, contentType string) error
	Get(ctx context.Context, key string) (io.ReadCloser, error)
	Delete(ctx context.Context, key string) error
}

// Config describes an S3-compatible endpoint.
type Config struct {
	Endpoint  string
	AccessKey string
	SecretKey string
	Bucket    string
	Region    string
	UseSSL    bool
	// VirtualHost selects virtual-hosted-style addressing (R2) over path-style
	// (SeaweedFS on localhost).
	VirtualHost bool
}

// ConfigFromEnv reads the BLOB_* environment variables.
func ConfigFromEnv() Config {
	return Config{
		Endpoint:    os.Getenv("BLOB_ENDPOINT"),
		AccessKey:   os.Getenv("BLOB_ACCESS_KEY"),
		SecretKey:   os.Getenv("BLOB_SECRET_KEY"),
		Bucket:      envOr("BLOB_BUCKET", "lecturo"),
		Region:      envOr("BLOB_REGION", "auto"),
		UseSSL:      os.Getenv("BLOB_USE_SSL") == "true",
		VirtualHost: os.Getenv("BLOB_VIRTUAL_HOST") == "true",
	}
}

// Enabled reports whether a blob endpoint is configured.
func (c Config) Enabled() bool { return c.Endpoint != "" }

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
