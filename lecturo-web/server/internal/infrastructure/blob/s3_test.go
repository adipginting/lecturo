package blob

import (
	"bytes"
	"context"
	"io"
	"os"
	"testing"
	"time"
)

// Round-trips through a real S3-compatible endpoint. Opt in with:
//
//	BLOB_TEST_ENDPOINT=localhost:8333 go test ./internal/blob/
//
// SeaweedFS locally, R2 (or any S3) in CI if you wire credentials.
func TestS3RoundTrip(t *testing.T) {
	endpoint := os.Getenv("BLOB_TEST_ENDPOINT")
	if endpoint == "" {
		t.Skip("BLOB_TEST_ENDPOINT not set")
	}

	cfg := Config{
		Endpoint:  endpoint,
		AccessKey: envOr("BLOB_TEST_ACCESS_KEY", "any"),
		SecretKey: envOr("BLOB_TEST_SECRET_KEY", "any"),
		Bucket:    envOr("BLOB_TEST_BUCKET", "lecturo-test"),
		Region:    envOr("BLOB_TEST_REGION", "us-east-1"),
		UseSSL:    os.Getenv("BLOB_TEST_USE_SSL") == "true",
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	store, err := New(ctx, cfg)
	if err != nil {
		t.Fatalf("New: %v", err)
	}

	key := "test/round-trip.txt"
	want := []byte("lecturo blob round-trip")

	if err := store.Put(ctx, key, bytes.NewReader(want), int64(len(want)), "text/plain"); err != nil {
		t.Fatalf("Put: %v", err)
	}

	rc, err := store.Get(ctx, key)
	if err != nil {
		t.Fatalf("Get: %v", err)
	}
	got, err := io.ReadAll(rc)
	_ = rc.Close()
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if !bytes.Equal(got, want) {
		t.Fatalf("payload mismatch: got %q want %q", got, want)
	}

	if err := store.Delete(ctx, key); err != nil {
		t.Fatalf("Delete: %v", err)
	}
	if _, err := store.Get(ctx, key); err == nil {
		t.Fatalf("expected not-found after delete")
	}
}
