# Lecturo Web server

Go API implementing `../api/openapi.yaml`. REST/JSON + SSE; multi-user; Postgres
for relational data and an S3-compatible `BlobStore` for document bytes.

## Run

```sh
docker compose up -d          # Postgres + SeaweedFS (S3 gateway)

export DATABASE_URL="postgres://postgres:postgres@localhost:55433/lecturo?sslmode=disable"
export BLOB_ENDPOINT="localhost:8333"
export BLOB_ACCESS_KEY="any" BLOB_SECRET_KEY="any" BLOB_REGION="us-east-1"
go run .                      # applies embedded migrations, then serves
```

Environment:

- `DATABASE_URL` — Postgres DSN. If unset, the server runs without a database
  (useful only for `/health`).
- `LECTURO_ADDR` — listen address (default `:8787`).

## Auth

Email + password hashed with **Argon2id** (64 MiB, 1 pass). Sessions are rows in
`sessions` referenced by an opaque token in the httpOnly `lecturo_session`
cookie; only a SHA-256 of the token is stored. State-changing requests must send
`X-CSRF-Token` matching the readable `lecturo_csrf` cookie.

- `POST /api/v1/auth/register`, `POST /api/v1/auth/login` → `User`, sets cookies.
- `POST /api/v1/auth/logout` → 204 (requires CSRF).
- `GET /api/v1/auth/me` → `User` or 401.
- `LECTURO_COOKIE_SECURE` (default `true`; set `false` for plain HTTP in dev).

Integration test (needs a database):

```sh
TEST_DATABASE_URL="postgres://postgres:postgres@localhost:55433/lecturo?sslmode=disable" \
  go test ./internal/httpapi/
```

## Documents

- `GET /api/v1/documents` — newest first; `limit` + opaque `cursor`.
- `POST /api/v1/documents` — multipart `file`. The format is decided by the
  **bytes** (`%PDF-`, or a ZIP whose `mimetype`/container says EPUB), never the
  extension; anything else is refused by name with `415`.
- `POST /api/v1/documents/import-url` — server-side fetch, same sniffing, and it
  follows an HTML download page (meta refresh or a PDF/EPUB link) to the real
  file, at most three hops.
- `GET /api/v1/documents/{id}/file` — streams the bytes from the BlobStore.
- `PUT /api/v1/documents/{id}/position` — stores the opaque locator.
- `DELETE /api/v1/documents/{id}` — removes the row and its blob.

All routes require a session; state-changing ones require CSRF.

## Skills (F8)

A skill is a named prompt with a manifest, installed from a marketplace. The
server stores installed skills and their runs; running is done by the client flow
today.

- `GET /api/v1/skills` — installed skills.
- `POST /api/v1/skills/browse` — `{url}` of a marketplace JSON, returned as the
  catalog (paid skills flagged).
- `POST /api/v1/skills` — install a catalog entry.
- `PATCH /api/v1/skills/{id}` — `{enabled}`.
- `DELETE /api/v1/skills/{id}` — uninstall.
- `GET /api/v1/skills/runs?documentId=` — recent runs, optionally for a document.

## Remote libraries (Calibre, Zotero)

- `GET|PUT /api/v1/servers` — Calibre URL + user/password, Zotero user id +
  API key. Secrets are write-only and encrypted at rest; responses say only
  whether they are set.
- `GET /api/v1/remote/{source}/browse?path=` — one OPDS catalog listing (Calibre)
  or the user's PDF/EPUB attachments (Zotero). A book with two formats is two
  rows; a catalog entry is marked `isCatalog`. Rows already downloaded carry
  `downloaded` + `documentId`.
- `GET /api/v1/remote/{source}/search?q=` — the source's own search (Calibre's
  OpenSearch template, Zotero's `q`).
- `POST /api/v1/remote/{source}/download` — fetches through the server, sniffs
  the payload, stores it and links provenance (`source`, `remoteId`).

Calibre uses Basic auth; Zotero sends `Zotero-API-Key` and drops it when a
`/file` request redirects to signed storage. `LECTURO_ZOTERO_BASE_URL` overrides
the API base (used by tests).

## Conversations and the LLM proxy

- `GET|POST /api/v1/conversations` — list (optionally `?documentId=`) / start one
  from an excerpt. The provider defaults to the user's selected one.
- `GET|PATCH|DELETE /api/v1/conversations/{id}` — detail with all messages,
  rename/switch provider, delete.
- `GET|POST /api/v1/conversations/{id}/messages` — history; `POST` streams the
  reply. With `?stream=false` it returns a JSON `Message`.
- `POST /api/v1/conversations/{id}/compact` — summarises the active exchanges,
  marks the originals `superseded` (kept on screen) and inserts a `summary`.

Streaming replies are `text/event-stream` with named events: `delta`
(`{text}`), `done` (`{message}`), `error` (`{error:{code,message}}`). The client
sends the CSRF header (use `fetch`, not `EventSource`).

The system prompt is the conversation's saved prompt (if any) plus the frozen
excerpt. `internal/llm` has one streaming client per family — OpenAI-compatible
(OpenAI/Kimi/OpenRouter/DeepSeek/Lecturo) and Anthropic's native Messages API —
and the request is made **server-side** with the stored key.

## Providers

- `GET /api/v1/provider` — selected provider, its base URL/model, `hasKey`, and
  the catalog with per-provider `configured` flags. **Keys are never returned.**
- `PUT /api/v1/provider` — `{provider, baseUrl?, model?, apiKey?}`; a field
  omitted is left unchanged, `""` clears it. Marking a provider selects it.
- `GET /api/v1/provider/models` — lists models from the selected provider,
  calling it server-side with the stored key (OpenAI-compatible `/models`, or
  Anthropic's `/v1/models` with `x-api-key`).

Credentials are **per provider** (`provider_credentials`), so switching does not
lose keys; `provider_settings` holds only the selection. Keys are stored
encrypted with AES-256-GCM.

`LECTURO_ENCRYPTION_KEY` is required to store any secret:

```sh
export LECTURO_ENCRYPTION_KEY="$(openssl rand -base64 32)"
```

Without it, saving a key returns `503 encryption_unavailable`; everything else
still works. `LECTURO_RELAY_URL` fills in Lecturo Cloud's base URL.

## Drafts and prompts

A **draft is a conversation with no `sent_at`** (the mobile's model). Asking about
an excerpt or a crop creates a draft; its first reply — and only then — sets
`sent_at` and names it (`ConversationTitle`: 40 chars from the message, 60 typed,
"New conversation"). A failed send leaves the draft to retry. The Saved queue is
gone; its rows became drafts in migration `0004`.

- `GET /api/v1/conversations?status=draft|sent&documentId=` — the two tabs.
- `POST /api/v1/conversations` — start a draft (`excerpt` and/or `imageId`).
- `POST /api/v1/conversations/{id}/messages` — sends; marks the draft sent once
  the reply lands.
- `GET|POST /api/v1/prompts`, `PUT|DELETE /api/v1/prompts/{id}` — templates capped
  at **twelve**.

## Blob storage

Document bytes live behind `internal/blob.Store`, an S3-compatible client:

- **Production: Cloudflare R2** — `BLOB_ENDPOINT="<accountid>.r2.cloudflarestorage.com"`,
  `BLOB_REGION="auto"`, `BLOB_USE_SSL="true"`, `BLOB_VIRTUAL_HOST="true"`.
- **Local: SeaweedFS** — the compose service exposes its S3 gateway on `:8333`;
  any credentials work, path-style addressing (`BLOB_VIRTUAL_HOST` unset).

The bucket is created on start if it does not exist. Round-trip test:

```sh
BLOB_TEST_ENDPOINT=localhost:8333 go test ./internal/blob/
```

## Migrations

Plain SQL under `internal/db/migrations/`, embedded with `go:embed` and applied in
filename order by `db.Migrate`, each in its own transaction, recorded in
`schema_migrations`. Add a new file (e.g. `0002_....sql`) rather than editing an
applied one.

## Test against a throwaway Postgres

```sh
docker run --rm -d --name lecturo-test-pg \
  -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=lecturo \
  -p 55441:5432 postgres:15

DATABASE_URL="postgres://postgres:postgres@localhost:55441/lecturo?sslmode=disable" \
  go run .
```

## Layout

```
main.go                 config, database, HTTP server wiring
internal/db/            pool + embedded migrations
```
