# Lecturo — Product Requirements Document

| | |
|---|---|
| **Version** | 1.1 (unified) |
| **Date** | 2026-09-22 |
| **Status** | Draft for review |
| **Source of truth** | **Lecturo Mobile** (`lecturo-android`) — features, vocabulary and behaviour are defined there first; `lecturo-web` and `lecturo-desktop` follow and specialize |
| **Scope** | One product, three clients: Mobile (Android), Web (Angular + Go server), Desktop (.NET/Avalonia) |
| **Owner** | Lecturo maintainers |
| **License** | MIT |

> This is the unified PRD. It is written **from the mobile product plan**
> (`lecturo-android/docs/product-plan.md`), which is the reference client. The
> Web and Desktop sections describe where a platform must, or deliberately
> chooses to, diverge. When this document and the mobile docs disagree, the
> mobile docs win and this document has a bug.

---

## 1. Summary

Lecturo is a personal document reader for **PDF and EPUB** with a single idea at
its centre: **while reading, you select a passage and ask a language model about
it.** Selecting text does not highlight or bookmark — it creates a **draft**, an
unsent conversation that carries that passage as its **frozen context**. When the
first reply lands, the draft becomes a **conversation**.

Every other feature exists to serve that loop: get documents in, read them, turn
passages into conversations, and keep those conversations inside a model's
context budget.

The same product ships on three surfaces:

- **Mobile** — a local, single-user Android app; the reference implementation.
- **Web** — a responsive browser client backed by a Go server, with multi-user
  accounts and a server-side library.
- **Desktop** — a native Linux-first application; a single-user, local port of
  the mobile model.

## 2. Problem and opportunity

Reading and asking are separated today. A reader who wants to understand a
passage leaves the book, opens a chat app, copies the text, loses the context,
and pastes it back without any trace of where it came from. The result is a
conversation that drifts away from the page it was about.

Lecturo collapses that distance. One gesture — Ask AI on a selection — captures
the passage, its document and its position, and opens a conversation whose
context is *only* that passage, frozen for the life of the conversation. The
conversation stays about the line you chose instead of drifting into the book as
a whole.

It is private by default: documents are copied into app-owned storage, the
reader keeps their own provider keys (or uses the bundled keyless destination),
and nothing is shared or synced unless the reader asks for it.

## 3. Target users

| Persona | Need | Served by |
|---|---|---|
| **The studying reader** | Understand a dense passage (a paper, a textbook, a contract) without leaving the page | Ask AI, drafts, Markdown/TeX replies |
| **The researcher** | Keep a PDF/EPUB library and ask about specific findings, with Zotero as the source | Remote libraries (Zotero/Calibre), skills |
| **The casual reader** | Ask what a sentence means, or what a chapter was about, on a phone | Mobile reader, keyless Lecturo Cloud |
| **The desktop reader** | Read with the chat docked beside the page, never losing the reading position | Desktop panel |
| **The signed-in web user** | Reach the same idea from a browser, with the library on a server | Web accounts + server-side library |

Primary platforms by persona: Mobile for casual and studying readers, Desktop
for the researcher at a workstation, Web for reaching a library from any browser.

## 4. Domain vocabulary (canonical)

These terms are identical across all three clients. Code, tests, UI strings and
docs use these words and no synonyms. This vocabulary is shared verbatim from the
clients' `CONTEXT.md` files and is a hard requirement, not a glossary of
convenience.

| Term | Meaning | Avoid |
|---|---|---|
| **Document** | A PDF or EPUB in the library, owned as a copied file. | Book, file, item |
| **Library** | The documents the app owns, plus the remote sources it can fetch more from. | Collection, shelf |
| **Source** | Where a document came from — a local file, a URL, or a remote library (Calibre, Zotero). | Origin, provider (for documents) |
| **Excerpt** | The passage, screenshot or crop a conversation is about. | Selection, context, passage |
| **Draft** | A conversation whose first message has not been sent; a conversation with no `sentAt`. | Saved item, pending chat |
| **Conversation** | A chat fired from an excerpt, existing once a draft has been sent. | Thread, session, chat (entity) |
| **Message** | One turn in a conversation, from the user or the model. | |
| **Locator** | Where in a document an excerpt sits — a 1-based PDF page, or an EPUB position kept opaque. | Position, bookmark, offset |
| **Frozen** | An excerpt, once captured, is never edited or compacted for the life of the conversation. | Locked, immutable |
| **Compact** | Fold older active messages into one summary so the next call fits the model's budget. | Summarize, truncate, prune |
| **Provider** | The chat backend a conversation is sent to — OpenAI, Anthropic, and so on. | Model, backend, service |
| **Destination** | Where a model call is routed — the user's own key or the Lecturo relay/cloud. | Host, endpoint, gateway |
| **Provenance** | The record that a document was downloaded, and from which remote item. | Source (for a download's origin) |
| **Skill** | A named prompt with a manifest, run against a document or a conversation to produce insights. | Prompt |
| **Insight** | The result of one skill run, attached to the document it was run against. | Output, result |

Platform-specific terms (desktop *Panel*, *Render mode*; web *Window class*,
*Session*) are defined in §9, not here.

## 5. Product model

The loop is the product. Everything below is a requirement on some part of it.

1. **Read** a Document (PDF or EPUB), with the last position restored.
2. **Select** a passage (or crop a screenshot). The selection menu offers
   **Ask AI** — there is no separate "Save".
3. **Draft**: Ask AI creates a Draft — a conversation with no `sentAt`, holding
   the Excerpt as its frozen context. It survives restarts and waits beside the
   conversations it will join.
4. **Send**: the first message fires the conversation. Only **once the reply
   lands** is `sentAt` set and the Draft leaves the Drafts tab. A failed send
   leaves it unsent, to retry — never a half-made conversation.
5. **Converse**: every message in the conversation is answered against the
   frozen Excerpt plus the active history. When the next call would exceed the
   model's context budget, the app **compacts first**.
6. **Keep**: conversations are persisted, resumable, renameable and deletable
   with their whole history.

The invariant that makes this portable (and the line a port must not cross):
**whatever renders the document, capture must end in `(excerpt, locator)`.**

## 6. Goals

- **G1 — The loop works everywhere.** A reader on any client can go from page to
  conversation in one gesture, and every client behaves the same way.
- **G2 — The excerpt stays frozen.** A conversation is about a passage, not a
  book. Context never silently widens, and compaction never touches the Excerpt.
- **G3 — The reader owns their data.** Documents are copied into app storage;
  keys are encrypted at rest; deletion is local and explicit.
- **G4 — No key required to start.** Lecturo Cloud / the relay answers without a
  provider key for testers and first-run users.
- **G5 — One vocabulary, three clients.** A term means the same thing everywhere.
- **G6 — Long conversations stay affordable.** Context is counted and compacted;
  budgets are floored and capped so a million-token window is not a bill.

## 7. Non-goals and boundaries

- No cloud accounts of the product's own; sync is strictly Calibre/Zotero.
  (Web accounts exist to authenticate the *web client*, not to sync the apps.)
- No background sync; sync is manual.
- No EPUB editing, and no annotations beyond the draft queue.
- No retrieval/embeddings in v1; context is built in layers when it comes.
- Deleting documents, drafts and conversations is always local.
- No automated agent may commit secrets or push without approval.
- No cross-user sharing in the Web v1.

## 8. Functional requirements

Each requirement states behaviour and **acceptance criteria** ("Done when").
Priorities: **P0** = the loop cannot ship without it on a platform; **P1** =
important; **P2** = desirable. Unless marked otherwise, a requirement is P0 on
Mobile and P0 wherever the feature exists on the other clients.

### F1 — Reading (P0)

- Open and render PDF with smooth continuous scrolling.
- Open and render EPUB in continuous scroll mode, following the publication's
  own reading order.
- Restore the last-read position per document on reopen.
- Keep the reading position when the chat surface opens (panel/sheet), so
  looking at a conversation never loses the page.
- **Done when**: a multi-page PDF and a multi-chapter EPUB both open, scroll,
  and resume where they were left after closing and reopening.

### F2 — Sources (P0)

- **Import = copy**: a document picked from the local file system is copied into
  app-owned storage; the app owns its library.
- Download a document from a web URL (in-app URL field).
- Decide a download by its **payload, not its URL**: `%PDF-`, or a ZIP container
  that says EPUB, or it is refused by name and never enters the library.
- **Follow download gateways**: a page served in place of the file (e.g. Standard
  Ebooks) is followed to the real file, at most three hops, dropping credentials
  when a hop crosses hosts. A file picked from disk gets the same byte check
  before it is copied in.
- Accept documents sent from other apps (share intent on mobile, drag-and-drop /
  "open with" on desktop, upload on web).
- **Remove documents**, per-item or multi-select. Deletion is local-only; remote
  Calibre/Zotero libraries are never modified.
- **Done when**: a file picked from disk and a file fetched from a URL both
  appear in the library and open; a URL that answers with a web page says so
  instead of adding a broken book; a deleted document's file and library entry
  are both gone.

### F3 — Remote libraries (Calibre, Zotero) (P1)

- Two remote sources behind one interface: `browse() / search() / download()`
  returning library items.
  - **Calibre** — its Content Server speaks **OPDS** (Atom/XML catalog).
  - **Zotero** — the **Web API** (`https://api.zotero.org`, REST/JSON, per-user
    API key).
- Manual sync only (a sync button); no background sync.
- A failing source surfaces its error inline and never blocks the others; local
  data is always kept.
- Library UI: one screen, three tabs — Local / Calibre / Zotero.
- **Provenance**: a downloaded item is remembered by source, remote id and URL,
  so the remote list marks it with a check and "Downloaded"; the mark opens the
  local copy instead of fetching again, and a file that has gone is fetched anew.
- Calibre lists **one row per format**: an EPUB and a PDF of the same book are two
  documents, marked independently.
- **Done when**: both servers' catalogs list in the UI and an item from each
  downloads into local storage and opens, then shows as Downloaded; an item that
  only exists as a PDF is not marked by the EPUB's download.

### F4 — Drafts and excerpts (P0)

- Selecting text in any open document offers **"Ask AI"** in the platform's
  selection menu — there is no separate Save.
- Asking makes a **Draft**: a conversation whose first message has not been sent,
  keeping the Excerpt as frozen context. A Draft is a row (`sentAt` null), so it
  survives restarts and waits in the Drafts tab beside the conversations it will
  join.
- The Excerpt keeps its trace to the source: `{ text, docId, locator }` — a page
  number for PDF, a position for EPUB, the crop/image for a screenshot.
- **Selections are adjustable where the platform allows it**: a PDF selection on
  mobile offers **drag handles** at either end; a selection can cross a page
  boundary and each handle stays on its own page. (Desktop selects natively; see
  §9.4.)
- EPUB selections keep their formatting, converted to Markdown at capture time;
  PDF selections are plain text where the platform's selection API returns no
  markup.
- **Screenshots**: crop a rectangle out of the page (PDF or EPUB) and ask about
  that. The image becomes the conversation's frozen context and is sent to
  vision-capable models only. The reader dims and explains itself rather than
  offering the crop for a model that cannot read pictures.
- A Draft is sent by its first message: the conversation is named, the reply is
  written, and only **once the reply lands** is `sentAt` set and the Draft leaves
  Drafts. A failed send (timeout, bad key) leaves the Draft unsent to retry.
- Drafts and conversations are two tabs of one surface, and a conversation's
  Excerpt is shown whole in the chat window — nothing is clamped.
- Reachable **while reading**, as a sheet over the page (mobile) or a docked panel
  (desktop), so the reading position is never lost.
- **While a conversation is open for the document, a new Ask AI selection or
  screenshot is appended to it** rather than making another Draft (F5); with
  nothing open, the selection starts a Draft as usual.
- **Done when**: an Ask AI from a PDF and an EPUB makes a Draft that survives a
  restart; a failed send leaves it in Drafts; a successful first reply turns it
  into a conversation; the whole Excerpt is visible in the chat window; and, with
  a conversation open, a further selection or screenshot lands in that
  conversation rather than starting a new one.

### F5 — Chat (P0)

- A conversation is seeded by the Excerpt it was fired from, and that Excerpt is
  the system-prompt context. **While a conversation is already open for a
  document, a new Excerpt or screenshot is appended to it** — as a message —
  rather than opening a new Draft, so a reading session on one book grows one
  conversation instead of a Draft per selection. With no conversation open,
  asking about an Excerpt starts a fresh Draft as before. *(Added after the first
  release; applies to every client.)*
- Pluggable providers behind a `ChatProvider` interface:
  - **OpenAI**, **Kimi**, **OpenRouter**, **DeepSeek** — one shared
    OpenAI-compatible chat-completions client (configurable base URL, key,
    model).
  - **Anthropic Claude** — native Messages API client.
  - **Lecturo Cloud / relay** — the keyless destination (§F9).
  - **GitHub Copilot** — listed but stubbed; no public chat API exists.
- The active provider is chosen in the reader's top bar and can be **changed
  mid-conversation**; history and frozen context stay put.
- Replies render as Markdown; TeX in a reply is typeset by bundled KaTeX, with
  prose left to the cheap renderer and anything that will not render falling
  back to its source. A Settings toggle turns typesetting off. (MathML in an EPUB
  is not typeset — open question, §13.)
- Opened from the reader, a conversation rides over the page in a panel/sheet
  rather than replacing it; putting it away keeps it, and the reading position.
- A document has a **current chat** — the sent conversation fired from it that
  was last engaged with. The reader's top bar carries **one chat icon that opens
  the document's open conversation** (where further excerpts and screenshots are
  appended), failing that its newest Draft, failing that its active
  conversation, failing that the chats surface.
- Conversations are persisted, resumable, and deletable with their whole history.
- A Draft that gets no reply is **not** a conversation: if the model call fails it
  stays in Drafts, unsent, to retry.
- **Context budget and compaction**: sending past the model's context budget
  **compacts first** — older active messages are summarised and superseded — and
  goes anyway if the summarising call fails. The budget is the model's window
  less room for the reply and a safety margin, floored (never so low a short chat
  compacts itself) and hard-capped (a million-token window is a bill); the number
  the reader typed in Settings wins outright. The Excerpt is never compacted.
- A conversation **names itself** from its first message, and that name is
  **editable** — from the list row or from the open chat. A typed name is
  trimmed, collapsed to one line and bounded; an empty one is refused, so a chat
  is never left nameless. Auto-naming never overwrites a user-set name.
- **Sending is optimistic**: the reader's message appears in the conversation the
  moment it is sent — marked *Sending…* — before any reply, and takes its
  delivered state when the reply lands. A send that fails marks it *Not sent*;
  in a Draft it stays unsent to retry. The composer is emptied at once. *(This
  was added after the first release and applies to every client.)*
- **Every message is timestamped, and the reader's carry a receipt**: a clock
  while in flight, a **double check and the time** once the reply has landed, and
  *Not sent* on failure — the WhatsApp/Telegram idiom. The receipt means
  *delivered*, not *read*: the model does not report that, so the UI never claims
  it. The assistant's messages carry their time.
- **The composer sends**: **Enter** sends, **Shift+Enter** inserts a newline
  (desktop and web, where the composer is a real text area). On mobile the
  keyboard's own send action sends and a newline is the IME's.
- **Done when**: a conversation fired from a Draft gets an answer that
  demonstrably uses its Excerpt, for each provider with a configured key; a
  deleted conversation no longer appears after restart; a renamed one comes back
  under its new name from both entry points; a failed send leaves the Draft in
  Drafts; a document's Draft or most recently engaged chat is one tap away in
  the reader's top bar; and sending shows the message at once, with a timestamp
  and a delivered receipt, before the reply.

### F6 — Settings (P1)

- Provider selection, and each provider's base URL, API key and model.
- Server addresses and credentials for Calibre and Zotero.
- Vision overrides per model, and the compaction threshold override.
- **Saved prompts**: CRUD for custom prompt templates, capped at **twelve**.
  They appear as chips above the composer in the Draft screen and in an open
  conversation; tapping one sends it together with whatever is already typed.
- API keys are **encrypted at rest** — AndroidKeyStore on mobile, the OS
  keychain on desktop, AES-256-GCM server-side on web — never stored in plain
  text, never committed.
- **Done when**: keys and servers survive restart, a saved prompt visibly changes
  chatbot behaviour, and no secret appears in the repo.

### F7 — Library covers (P2)

- Each document shows a cover: an EPUB's own cover image, or a PDF's first page.
- Covers are cached, so they are not recomputed on every launch. A document
  without a determinable cover shows its initial.
- **Done when**: a shelf of mixed formats shows real covers for the books that
  have them and initials for those that don't, without stalling the list.

### F8 — Skills and insights (P1, desktop ahead of mobile)

- A **Skill** is a named prompt with a manifest, installed from a marketplace and
  run against a document (or a conversation) to produce insights. Skills are
  versioned and installed/removed by the reader.
- A run reads the document's own text where an extractor exists (mobile: EPUB
  spine flattened to Markdown; PDF has no extractor yet). Runs are manual —
  nothing runs until the reader asks.
- Budget: a skill run spends the same context budget as everything else.
- **Mobile:** a finished run produces an **Insight**, listed against the
  document. Book context (structure, chapter summaries, a book summary) is the
  planned substrate; today a long book is sent whole.
- **Desktop (divergence):** a finished run becomes a **Conversation**, recorded
  in `CONTEXT.md` so the two vocabularies meet later instead of drifting.
- **Done when**: a skill can be browsed, installed, enabled, run against a
  document, and its result is reachable from that document; a paid skill without
  an entitlement is refused rather than run.

### F9 — Lecturo Cloud (P0 for keyless first-run)

- Testers and first-run readers should not have to obtain a provider API key.
- The destination is a small server that holds one upstream key and meters what
  each install spends: an OpenAI-shaped pass-through that replaces the request's
  model with its own, counts the tokens the upstream reports, and answers `429`
  in OpenAI's error shape when a device's free day is spent.
- The app sees it as one more provider whose "key" is the install's anonymous
  device id, generated once and stored; a future account or paid plan hangs off
  it. The server is deliberately dumb: per-device per-day counters, no message
  logging, no product logic in the app.
- **Done when**: a fresh install with no key can send a message and get a reply;
  spending the free day produces the provider's own quota message in the UI.

## 9. Platform strategy

### 9.1 Source of truth and specialization

- **Mobile is the reference.** Features, acceptance criteria and vocabulary are
  defined there first. A behaviour that exists on mobile and is missing elsewhere
  is a gap, not a difference.
- **Web** and **Desktop** may specialize only where the platform forces it or the
  product deliberately chooses it. Every specialization must be recorded — in
  `CONTEXT.md` for vocabulary, in an ADR for decisions that are hard to reverse —
  so the three clients do not drift silently.
- When a client specializes a *feature*, this PRD's acceptance criteria are the
  baseline; the client adds criteria rather than replacing them.

### 9.2 Capability matrix

Legend: **Yes** = built/required parity · **Specialized** = deliberate
difference · **Gap** = not yet at parity.

| Capability | Mobile (reference) | Web | Desktop |
|---|---|---|---|
| Reader engine | `androidx.pdf` + Readium Kotlin (native) | pdf.js + Readium web (in-browser) | Native EPUB/PDF render modes; WebKitGTK fallback |
| Library ownership | Local, app-owned copy | Server-side (Postgres + S3 BlobStore) | Local, app-owned copy |
| Accounts | None (single user) | Multi-user, private, no sharing | None (single user) |
| Auth | n/a | Email+password (Argon2id), httpOnly session | n/a |
| Drafts model | Conversation with null `sentAt` | Same | Same |
| Screenshots | `PixelCopy` crop of the rendered surface | Crop in browser | Drag-to-crop the rendered surface |
| Selection UI | Platform menu + drawn PDF handles | Native selection | Floating pill · native selection (PDF handles N/A) |
| Chat surface | Non-modal bottom sheet | Sheet (compact) / overlay (medium) / docked 400px (expanded) | Docked Panel, never modal |
| Providers | OpenAI/Kimi/OpenRouter/DeepSeek, Anthropic, Lecturo relay | Same, proxied server-side | Same, incl. Lecturo Cloud by device id |
| Key storage | AndroidKeyStore | AES-256-GCM server-side, never returned | OS keychain (libsecret) |
| Skills outcome | Insight list per document | Skills surface planned | Skill run → Conversation (divergence) |
| Providers keys | On device | Never in the browser | On device |

### 9.3 Mobile — the reference

- Kotlin, Jetpack Compose (Material 3), single Gradle module, MVVM; `:domain`
  holds pure vocabulary/invariants with no Android or Room dependency, exposing
  ports the `data/` adapters implement.
- Storage: Room (documents, conversations, messages, prompts) + DataStore
  (preferences, encrypted keys). A Draft is a conversation with a null `sentAt`.
- Positions are **opaque strings**: a 1-based PDF page, or Readium's serialized
  `Locator` JSON. Nothing else reads the column, so either engine can be
  replaced without a migration.
- Two renderers meet at one call, `createDraft(text, locator)`; nothing
  downstream knows an excerpt's format.
- The reader's panel is deliberately **not modal** — a scrim would stop the page
  being read. Back dismisses a selection before it does anything else.
- `lecturo-relay/` is the shared Lecturo Cloud server.

### 9.4 Desktop — specialized

- .NET 10 + Avalonia 12 (MVVM, CommunityToolkit.Mvvm), cross-platform: Linux is
  the primary target and Windows is a `dotnet publish -r win-x64` (secret store,
  paths and WebView differ per platform); `Domain` has no dependencies and its
  own tests. UI text is **Plus Jakarta Sans**, the reading surface **Source Serif 4**.
- Exact-portable pieces: the `(text, locator)` seam, the Draft/Conversation
  model, the provider interface, the compaction rules, the library/sources/
  remote-library behaviour.
- Native specializations: three render modes (`Epub`, `PdfText`, `PdfImages`,
  and `Pdf` via WebKitGTK as a last resort); a **Panel** docked beside the page
  rather than a sheet over it; a JSON settings file in `~/.config/lecturo`;
  Microsoft.Data.Sqlite with hand-written migrations; a floating pill
  (Copy · Select all · Ask AI) over native text selection.
- **PDF drawn selection handles are N/A** — native text selection replaces them.
- **Skills produce conversations, not insights** — recorded divergence from
  mobile, to be reconciled.
- The desktop implements only the **Expanded** web breakpoint.

### 9.5 Web — specialized

- Angular (standalone components + signals, Angular Material 3, SPA) + a Go API
  (REST/JSON + OpenAPI, SSE streaming, Postgres + S3-compatible BlobStore).
- **Standalone web client with a server-side library**, not a sync companion to
  the apps. Multi-user accounts, each private, no sharing in v1.
- The browser **never holds a provider key**: all model calls are proxied
  server-side with the stored key. This is a security requirement, not a
  preference.
- One adaptive shell, three **window classes**: Compact (<600 — bottom nav, chat
  sheet), Medium (600–1023 — nav rail, chat overlay), Expanded (≥1024 — wide
  rail, chat docked 400px). Reading measure centres at 680–700px on wide screens.
- Installable **PWA**, app-shell caching, writes online-only.
- i18n: English now, wired for Indonesian later.
- Deployment: a single Go binary (`go:embed` the Angular build) + Postgres +
  a volume.
- Calibre/Zotero are proxied server-side; provider keys are per-provider and
  write-only over the API.

### 9.6 Shared, non-negotiable invariants

These must hold on every client or the product has forked:

1. Capture ends in `(excerpt, locator)`; no downstream code knows the format.
2. A Draft is a conversation with no `sentAt`; `sentAt` is set only after the
   first reply lands.
3. The Excerpt is frozen: never edited, never compacted, always shown whole.
4. The API key never appears in plain text at rest or in the repo.
5. Every resource is scoped to its owner; there is no cross-user access.
6. The vocabulary in §4 is used verbatim in code, UI and docs.

### 9.7 Architecture — domain-driven design

All three clients are converging on the same layering: a **pure domain model**
(the vocabulary and invariants of §4), an **application** layer of use cases over
consumer-owned **ports**, **infrastructure** adapters that implement those ports,
and thin **interfaces** (transport) on the outside. The load-bearing rule is
**dependency direction**: the domain depends on nothing; the application layer
depends on its own ports; infrastructure and interfaces point inward.

The layering is a requirement, not a description — a client whose rules leak into
view models or SQL has forked from the others.

| Layer | Responsibility | Must not |
|---|---|---|
| **Domain** | Vocabulary, entities and invariants (Conversation, Excerpt, Message, ConversationTitle, budget) | Import a framework, database, or transport |
| **Application** | Use cases over ports; orchestration | Reach for infrastructure directly |
| **Infrastructure** | SQL, HTTP clients, blob stores, key stores implementing the ports | Own business rules |
| **Interfaces** | HTTP handlers, Compose screens, views/view models — thin | Contain rules; call the database directly |

**Current state (audited 2026-09-22).** The model is extracted and genuinely pure
on every client; the application/port layer is uneven. Web is the reference for
ports and adapters; Mobile is the reference for the domain model. Desktop has
since adopted ports and an application layer (ADR 0003).

- **Mobile** — `:domain` is a plain `kotlin-jvm` module with no Android or Room
  imports; ports (`Conversations`, `Prompts`, `Documents`, `Skills`) are
  implemented by Room adapters under `data/`; `AppContainer` is the composition
  root. *Gap:* no application layer (ViewModels carry use-case logic) and one
  ViewModel imports `LecturoDatabase` directly.
- **Web** — `application/chat` defines a consumer-owned `Repository` port and a
  `Service`; `infrastructure/chat` implements it; `application` and `domain`
  import no infrastructure. *Gap:* only `chat` has migrated — documents,
  provider, prompts, servers, remote and images still go handler → store.
- **Desktop** — `Domain` references nothing; `Application` holds the use cases
  and ports (`IConversationRepository`, `IChatClient`, `IDocumentRepository`,
  `ISettingsStore`, …); `Infrastructure` implements them; `App.axaml.cs` is the
  only place that names an adapter, and an architecture test fails if a ViewModel
  or the application layer reaches into infrastructure. *Remaining:*
  `ReaderService`, `CoverService` and `MathsHtml` are rendering helpers still in
  the app project.

**Convergence requirements.**

1. Every persistence or I/O boundary is a **port owned by its consumer** (the
   application layer), implemented in infrastructure — never the reverse.
2. Domain and application import no framework, database, or transport.
3. Transport stays thin: handlers and view models call application services.
4. Adapters map rows to domain types at the seam; no storage type escapes them.
5. Each client records its migration state (ADR or status doc) so the three do
   not drift silently.

## 10. Non-functional requirements

| Area | Requirement |
|---|---|
| **Privacy** | Documents and keys stay with the reader; server-side keys are never returned; nothing is logged that contains message content. |
| **Security** | Keys encrypted at rest (keystore/keychain/AES-256-GCM); web sessions are httpOnly + CSRF-protected; passwords hashed with Argon2id. |
| **Offline** | Mobile and Desktop are fully usable offline except model calls and remote libraries. Web is installable; writes are online-only in v1. |
| **Performance** | Library lists must not stall on covers; reads/opens must feel immediate; model calls allow a long timeout (30s connect/read/write on mobile) because replies stream token by token. |
| **Resilience** | A failing remote source surfaces inline and never blocks the others; a failed send or compaction never loses a turn or a Draft. |
| **Portability** | A new renderer must be replaceable without a data migration — positions are opaque. |
| **Accessibility** | Material 3 components and the platform's own selection menus; focus/hover/disabled states specified in the design system. |
| **i18n** | English first; Indonesian wired where the client supports it. |
| **Testing** | Pure logic (conversation rules, compaction budget, parsers, capture geometry, file-signature checks) is unit-tested on every client. Rendering and gestures are driven on-device and reviewed. |

## 11. Success metrics (proposed)

No analytics ship with v1 for privacy reasons; these are the signals the team
will watch from testing and review, not instrumented counters.

- **Activation**: a fresh install can reach a first reply without obtaining a
  provider key (via Lecturo Cloud).
- **Core loop**: time and taps from opening a document to a Draft, and from a
  Draft to its first reply.
- **Retention of the idea**: share of sessions that create a Draft while reading.
- **Reliability**: failed sends that leave a Draft retryable vs. conversations
  that never happened; compaction that loses a turn = zero.
- **Portability**: a bug fixed on mobile is mirrored (or recorded as a
  specialization) on web and desktop.

## 12. Release criteria and current status

A client is release-ready when it satisfies F1–F7 (and F5's compaction),
preserves §9.6, and has no secret in the repo.

| Area | Mobile | Web | Desktop |
|---|---|---|---|
| Reading | Built | In progress (pdf.js + Readium web) | Built (native modes) |
| Sources / remote libraries | Built | Built server-side; HTML download-page following still to come | Built |
| Drafts / Chat / Providers | Built | Built (SSE streaming) | Built |
| Compaction + budget | Built | Built | Built |
| Settings / key storage | Built | Built (server AES) | Built (keychain) |
| Covers | Built (Local; remote initials) | Planned | Built |
| Skills | Installed/run; book context partial | Planned | Built (runs → conversations) |
| Lecturo Cloud / relay | Built (`lecturo-relay/`) | Relay URL wired | Built (device-id cloud) |
| Accounts | n/a | Built | n/a |
| Architecture (DDD, §9.7) | Domain pure + ports; no application layer; 1 Room leak | Domain pure; ports + application for `chat` only | Domain / Application / Infrastructure over ports; VMs take services; architecture test (ADR 0003) |

Known gaps to close (see each client's `docs/status.md` or `docs/architecture.md`
for the authoritative list): desktop Esc back-stack and model-timeout retry;
desktop "open with" / shared-link intake; web HTML download-page following and
live Zotero verification; mobile book-context layers and retrieval; and the
layering gaps in §9.7.

## 13. Open questions

| # | Question | Owner | Notes |
|---|---|---|---|
| Q1 | Skills on mobile produce **Insights**, on desktop **Conversations** — which becomes the shared model? | Product | Recorded as an explicit divergence today. |
| Q2 | MathML in EPUBs: leave the source, or bundle a MathML engine? | Mobile | KaTeX typesets TeX only. |
| Q3 | When does retrieval arrive, and does it run on-device first? | Architecture | Preference: on-device embeddings, relay fallback. |
| Q4 | Do the web accounts and the mobile device identity ever unify into one account? | Product | The relay's device id is "the thing a future account hangs off". |
| Q5 | Should the desktop implement Medium/Compact layouts, or stay Expanded-only? | Design | Desktop is Expanded-only today. |
| Q6 | i18n rollout (Indonesian) — which client first, and does it gate web release? | Product | Web is wired; mobile not specified. |
| Q7 | Finish the DDD migration — when does each non-chat context move behind a port, and which client's layering is the template? | Architecture | Web is the ports reference; mobile the domain reference (§9.7). |

## 14. Appendix — document map

| Document | Role |
|---|---|
| `lecturo-android/docs/product-plan.md` | **Reference** feature list and acceptance criteria |
| `lecturo-android/docs/architecture.md` | Mobile design decisions and lessons |
| `lecturo-android/docs/interactions.md` | Every interaction, as conditions and narrations |
| `lecturo-desktop/docs/plan.md`, `docs/status.md`, `docs/interactions.md` | Desktop plan, coverage matrix, desktop-adapted interactions |
| `lecturo-web/README.md`, `api/openapi.yaml`, `server/README.md` | Web/client decisions and the API contract |
| `*/CONTEXT.md` | Canonical vocabulary, per client (shared + platform-specific) |
| `*/docs/adr/` | Decisions that are hard to reverse |

When the code and this PRD disagree, the mobile docs and the clients'
`CONTEXT.md` are authoritative.
