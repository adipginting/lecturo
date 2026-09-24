# Lecturo Web

Responsive web client for Lecturo — a PDF/EPUB reader where text selected while
reading becomes a saved excerpt, and each excerpt becomes an LLM conversation
with that passage as its frozen context.

Monorepo:

- `api/` — `openapi.yaml`, the contract shared by the server and the web client.
- `web/` — Angular (standalone components + signals, Angular Material 3, SPA).
- `server/` — Go API (REST/JSON + OpenAPI, SSE streaming, Postgres + blob store).
- `docs/design/` — the responsive design: PNG exports and design tokens, taken
  from the Penpot `Lecturo` file, page `Lecturo Web`.

## Settled decisions

| Concern | Decision |
|---|---|
| Product role | Standalone web client with a server-side library (not a sync companion to the apps) |
| Users | Multi-user accounts, each private, no sharing in v1 |
| Reader | In-browser: pdf.js (PDF) + Readium web (EPUB) |
| Responsive | One adaptive shell — compact <600, medium 600–1023, expanded ≥1024 |
| UI | Angular Material 3 themed from Penpot tokens; signals; no SSR in v1 |
| LLM keys | Server-side proxy; browser never holds a provider key |
| API | REST/JSON + OpenAPI, SSE for streaming |
| Data | Postgres + **S3-compatible BlobStore**: Cloudflare R2 (prod), SeaweedFS (local) |
| Auth | Email + password (Argon2id), httpOnly cookie sessions; OIDC later |
| Calibre/Zotero | Proxied server-side |
| PWA | Installable, app-shell caching, writes online-only |
| i18n | English now, wired for Indonesian later |
| Deployment | Single Go binary (`go:embed` Angular) + Postgres + volume |

## Server architecture (DDD)

The Go server is layered; dependencies point inward only.

```
server/internal/
  domain/          pure model, no SQL/HTTP
    identity/      User, Session, email/password policy
    library/       Document, format detection
    reading/       Excerpt, Prompt (and the twelve cap)
    chat/          Conversation, Message, compaction/roles
    catalog/       remote-library Item/Page
    crop/          screenshot Crop
    settings/      Provider catalogue, credentials, selection
  application/     use cases + ports (repository interfaces)
  infrastructure/  adapters implementing the ports
    auth/ blob/ chat/ db/ images/ library/ llm/ prompts/
    provider/ remotelib/ saved/ secretbox/ servers/ webui/
  interfaces/
    httpapi/       HTTP handlers, middleware, router
```

Every context now has an `application` layer (use cases over consumer-owned
repository ports) and thin handlers: `application/{chat, prompts, documents,
provider, servers, catalog, images, identity, skills}`. The adapters implement
the ports, and `interfaces/httpapi/api.go` is the composition root — the only
place that names an adapter. `docs/adr/` records the decisions that are hard to
reverse; `CONTEXT.md` at the repo root is the ubiquitous language.

## Local development

```sh
docker compose up -d           # Postgres + SeaweedFS (S3 gateway)
cd web && pnpm install && pnpm start        # http://localhost:4200
cd server && DATABASE_URL=... BLOB_ENDPOINT=localhost:8333 go run .
```

See `server/README.md` for the full environment; in production the same
`BlobStore` points at Cloudflare R2.

## Design

The responsive design lives on the Penpot page `Lecturo Web` (boards `W0`–`W7`)
and is exported to `docs/design/`. Tokens are in
[`docs/design/tokens.json`](docs/design/tokens.json) and
[`docs/design/tokens.scss`](docs/design/tokens.scss).

Three breakpoints, one component system:

| | Compact 390 | Medium 834 | Expanded 1440 |
|---|---|---|---|
| Navigation | bottom nav | 80px rail | 248px rail + provider card |
| Library | single list | two-pane list/detail | card grid + FAB |
| Chat | full-screen sheet | right overlay | docked 400px panel |
| Reading | full width | 680px measure | 700px measure |
