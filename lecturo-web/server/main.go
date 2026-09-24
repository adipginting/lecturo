// Lecturo Web API.
//
// REST/JSON + SSE, multi-user, Postgres + an S3-compatible BlobStore, all
// shaped by api/openapi.yaml. This entry point wires configuration, the
// database, blob storage and the HTTP server; feature handlers live under
// internal/httpapi.
package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"lecturo/server/internal/infrastructure/blob"
	"lecturo/server/internal/infrastructure/db"
	"lecturo/server/internal/interfaces/httpapi"
	"lecturo/server/internal/infrastructure/secretbox"
	"lecturo/server/internal/infrastructure/webui"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	var pool *pgxpool.Pool
	if url := os.Getenv("DATABASE_URL"); url != "" {
		p, err := db.Open(ctx, url)
		if err != nil {
			log.Fatalf("database: %v", err)
		}
		defer p.Close()
		pool = p

		if err := db.Migrate(ctx, pool); err != nil {
			log.Fatalf("migrate: %v", err)
		}
		log.Print("database ready")
	} else {
		log.Print("DATABASE_URL not set; auth endpoints will return 503")
	}

	var store blob.Store
	blobCfg := blob.ConfigFromEnv()
	if blobCfg.Enabled() {
		b, err := blob.New(ctx, blobCfg)
		if err != nil {
			log.Fatalf("blob: %v", err)
		}
		store = b
		log.Printf("blob store ready (endpoint %s, bucket %s)", blobCfg.Endpoint, blobCfg.Bucket)
	} else {
		log.Print("BLOB_ENDPOINT not set; blob storage disabled")
	}

	// Secrets at rest (provider keys, Calibre/Zotero passwords).
	var box *secretbox.Box
	if raw := os.Getenv("LECTURO_ENCRYPTION_KEY"); raw != "" {
		key, err := secretbox.ParseKey(raw)
		if err != nil {
			log.Fatalf("encryption key: %v", err)
		}
		box, err = secretbox.New(key)
		if err != nil {
			log.Fatalf("encryption key: %v", err)
		}
		log.Print("secret encryption ready")
	} else {
		log.Print("LECTURO_ENCRYPTION_KEY not set; storing API keys will return 503")
	}

	// Cookies are Secure by default; set LECTURO_COOKIE_SECURE=false for plain
	// HTTP development if the browser rejects them.
	secureCookies := os.Getenv("LECTURO_COOKIE_SECURE") != "false"
	api := httpapi.New(pool, store, secureCookies, box)
	api.SPA = webui.Handler() // embedded Angular app (placeholder until `make web`)

	srv := &http.Server{
		Addr:              addr(),
		Handler:           api.Handler(),
		ReadHeaderTimeout: 10 * time.Second,
	}

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = srv.Shutdown(shutdownCtx)
	}()

	log.Printf("lecturo-web server listening on %s", srv.Addr)
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}

func addr() string {
	if v := os.Getenv("LECTURO_ADDR"); v != "" {
		return v
	}
	return ":8787"
}
