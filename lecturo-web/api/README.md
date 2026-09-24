# Lecturo Web API contract

`openapi.yaml` is the source of truth for the Go backend and the generated
Angular client. Lint it from the repo root:

```sh
npx -y @redocly/cli@latest lint api/openapi.yaml     # config lives in ../redocly.yaml
```

Conventions baked into the contract:

- Base path `/api/v1`; REST/JSON; SSE (`text/event-stream`) for streaming replies.
- `sessionCookie` auth — a server session in an httpOnly cookie; state-changing
  requests also carry a double-submit `X-CSRF-Token`.
- Every resource is scoped to the signed-in user; there is no cross-user access.
- Provider API keys are write-only: sent to `PUT /provider`, never returned.
- `Error` is the single error envelope, with a stable `code` for the client.
- Locators are opaque strings (PDF: page number; EPUB: CFI).

Resources: `documents`, `images`, `prompts`, `conversations`/`messages`, `provider`,
`servers`, `remote` (calibre/zotero). A draft is a conversation with no `sentAt`.
