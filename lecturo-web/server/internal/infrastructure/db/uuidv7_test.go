package db_test

import (
	"context"
	"fmt"
	"os"
	"strings"
	"testing"

	"github.com/google/uuid"

	"lecturo/server/internal/infrastructure/db"
)

// Postgres 18's built-in uuidv7() is the default for every generated key; an
// insert that does not name an id must come back time-ordered and v7.
func TestUUIDv7GeneratorAndDefaults(t *testing.T) {
	dbURL := os.Getenv("TEST_DATABASE_URL")
	if dbURL == "" {
		t.Skip("TEST_DATABASE_URL not set")
	}

	ctx := context.Background()
	pool, err := db.Open(ctx, dbURL)
	if err != nil {
		t.Fatalf("db.Open: %v", err)
	}
	t.Cleanup(pool.Close)
	if err := db.Migrate(ctx, pool); err != nil {
		t.Fatalf("db.Migrate: %v", err)
	}

	var id string
	if err := pool.QueryRow(ctx, `select uuidv7()::text`).Scan(&id); err != nil {
		t.Fatalf("uuidv7(): %v", err)
	}
	assertV7(t, id)

	var def string
	if err := pool.QueryRow(ctx, `
		select column_default from information_schema.columns
		where table_name = 'conversations' and column_name = 'id'`).Scan(&def); err != nil {
		t.Fatalf("read conversations.id default: %v", err)
	}
	if !strings.Contains(def, "uuidv7") {
		t.Fatalf("conversations.id default is %q, want uuidv7", def)
	}

	// An insert that does not name the id is handed a v7 by the default.
	email := fmt.Sprintf("v7-%s@example.com", uuid.NewString())
	var userID string
	if err := pool.QueryRow(ctx,
		`insert into users (email, password_hash) values ($1, $2) returning id::text`,
		email, "x",
	).Scan(&userID); err != nil {
		t.Fatalf("insert user: %v", err)
	}
	assertV7(t, userID)
}

func assertV7(t *testing.T, id string) {
	t.Helper()
	if len(id) != 36 || id[14] != '7' {
		t.Fatalf("not a v7 uuid: %q", id)
	}
	if v := id[19]; v != '8' && v != '9' && v != 'a' && v != 'b' {
		t.Fatalf("variant is not RFC 9562: %q", id)
	}
}
