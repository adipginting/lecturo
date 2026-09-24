# lecturo — Product & Architecture Plan (platform-neutral)

> **Superseded in part (2026-09-21).** This plan describes the **saved queue**:
> selecting text puts a passage in a basket, and tapping it fires a conversation.
> The product no longer works that way — asking about a passage makes a draft,
> and a draft is a conversation whose first message has not been sent. Read
> [CONTEXT.md](../CONTEXT.md) for the vocabulary and
> [ADR 0002](adr/0002-drafts-are-unsent-conversations.md) for the decision; the
> sections here about the basket, Save, and "nothing is persisted until the first
> message" are history. Everything else — reading, sources, remote libraries,
> skills, covers — still stands.

Purpose of this document: enough detail to generate a **desktop version** of
lecturo without reference to the Android codebase. It describes *what* the app
does and the settled design decisions; platform-specific choices (UI toolkit,
storage, keychain) are left as explicit decision points with a recommended
default.

## 1. What the app is

lecturo is a personal document reader for **PDF and EPUB** files, built around
one idea: while reading, you select interesting text into a **basket** (like
an e-commerce cart), and later **learn from the basket's contents by chatting
with an LLM** about them.

## 2. Feature list with acceptance criteria

### F1 — Reading
- Open and render PDF files with smooth continuous scrolling.
- Open and render EPUB files (spine order, working internal links).
- Restore last-read position per document on reopen
  (PDF: page number; EPUB: spine item + anchor).
- **Done when**: a multi-page PDF and a multi-chapter EPUB both open, scroll,
  follow links, and resume where left off after closing and reopening.

### F2 — Sources
- Import documents from the local file system. **Import = copy** into the
  app's own storage; the app owns its library.
- Download a document from a web URL (in-app URL field).
- Accept documents sent from other apps (on desktop: drag-and-drop and/or
  "open with" file association).
- **Done when**: a file picked from disk and a file fetched from a URL both
  appear in the library and open.

### F3 — Remote library sync
- Two remote sources behind one interface:
  `browse() / search() / download()` returning library items.
  - **Calibre**: its Content Server speaks **OPDS** (Atom/XML catalog).
  - **Zotero**: the **Web API** (`https://api.zotero.org`, REST/JSON,
    per-user API key).
- Manual sync only (a sync button / pull-to-refresh); no background sync.
- A failing source surfaces its error inline and never blocks the others;
  local data is always kept.
- Library UI: one screen, three tabs — Local / Calibre / Zotero.
- **Done when**: both servers' catalogs list in the UI and an item from each
  downloads into local storage and opens.

### F4 — Basket
- Selecting text in any open document offers **"Add to basket"** (on desktop:
  an in-page JS bubble next to the selection; see Section 4).
- Basket item = `{ text, docId, locator, timestamp }`. The locator (PDF page
  / EPUB spine href) keeps every item traceable to its source.
- Basket screen behaves like a cart: list items, remove one, clear all.
  Persistent across restarts.
- **Done when**: selected text from both a PDF and an EPUB lands in the
  basket, survives an app restart, and can be removed/cleared.

### F5 — Chatbot
- Chat screen that answers questions about the **current basket contents**:
  all basket items are injected into the system prompt (baskets are tens of
  items — no retrieval infra needed).
- Pluggable provider layer behind a `ChatProvider` interface:
  - **OpenAI** and **Kimi** — one shared OpenAI-compatible chat-completions
    client (configurable base URL, key, model).
  - **Anthropic Claude** — native Messages API client.
  - **GitHub Copilot** — listed but stubbed "not yet supported"
    (no public chat API exists).
- Blocking request/response for v1; the interface is shaped so streaming can
  be added later.
- Conversations are persisted and resumable.
- **Done when**: a question about basket contents gets an answer that
  demonstrably uses them, for each provider with a configured key.

### F6 — Settings
- Provider selection and per-provider API-key entry.
- Server addresses + credentials for Calibre and Zotero.
- **Saved prompts**: CRUD for custom prompt templates; one is **chosen per
  conversation at chat start** and locked for that conversation (it becomes
  the system prompt, ahead of the injected basket).
- API keys are encrypted at rest using the OS keychain/keystore, never stored
  in plain text, never committed to version control.
- **Done when**: keys and servers survive restart, a saved prompt visibly
  changes chatbot behavior, and no secret appears in the repo.

## 3. Architecture (the decisions that matter)

1. **Unified HTML rendering.** One embedded web view renders everything:
   - PDF → **pdf.js** (bundle Mozilla's stock `viewer.html` with the app;
     pass the file as a query parameter).
   - EPUB → unzip, parse the OPF manifest (`container.xml` → OPF → spine),
     serve the spine's XHTML.
   - Serve app files and bundled assets from **one local origin** (custom
     scheme handler / local asset server) so pdf.js can XHR the file and the
     same-origin selection pipeline works everywhere.
   - Why: the basket needs identical text selection in both formats; one
     DOM-based pipeline beats two native ones. Web content needs **DOM
     storage enabled** — pdf.js dies silently without `localStorage`.
2. **Selection → basket bridge.** Inject JS that watches
   `window.getSelection()`, show an in-page "Add to basket" bubble, and push
   `{text, docId, locator}` to native code through the JS↔C# bridge.
3. **Position tracking.** PDF: hook pdf.js `eventBus.on('pagechanging')`.
   EPUB: intercept navigation and record the root-relative href.
4. **Local model.** Relational store (SQLite-class) with three tables:
   `documents(id, title, fileName, format, lastLocator, updatedAt)`,
   `basket_items(id, docId, text, locator, createdAt)`,
   `conversations/messages` (+ prompt choice per conversation).
   Preferences in a simple key-value store; secrets encrypted.
5. **Feature modules.** `reader`, `library`, `sync`, `basket`, `chat`,
   `settings`, `data` — MVVM (or unidirectional-data-flow equivalent):
   screens observe repositories, never touch storage/network directly.
6. **Chat context.** System prompt = saved prompt (if chosen) + serialized
   basket items. No embeddings, no vector search — revisit only if baskets
   outgrow context windows.

## 4. Desktop decisions (settled for lecturo-desktop)

Settled stack: **.NET 10 + Avalonia 12 (MVVM, CommunityToolkit.Mvvm),
Linux-first** with platform-specific pieces behind small interfaces.
Greenfield C# — this document is the complete spec; no code is ported from
the Android app.

| Concern | Android (existing) | Desktop (settled) |
|---|---|---|
| UI toolkit | Jetpack Compose | **Avalonia 12.1 + CommunityToolkit.Mvvm**; single-window shell with view-switching (no ReactiveUI/routing frameworks) |
| Web view | Android WebView | **WebView-Avalonia wrapping WebKitGTK** (2.52 confirmed on dev machine) |
| PDF renderer | bundled pdf.js legacy | Same — latest stable pdf.js **legacy** build, vendored under `Assets/pdfjs/`, version pinned |
| Local origin | WebViewAssetLoader | **`lecturo://` custom scheme handler** (timeboxed spike; fallback: loopback Kestrel server) |
| Database | Room | **Microsoft.Data.Sqlite**, hand-written SQL + migrations (no EF Core) |
| Settings | DataStore | JSON file in `~/.config/lecturo` |
| Key encryption | AndroidKeyStore | **libsecret via P/Invoke** (present as WebKitGTK dependency); AES-encrypted-file fallback with a settings warning |
| Networking | OkHttp + kotlinx.serialization | Hand-rolled **HttpClient + System.Text.Json** (OpenAI-compatible + Anthropic Messages; no vendor SDKs) |
| XML parsing | XmlPullParser | System.Xml.Linq (OPDS Atom, OPF, container.xml) |

Selection→basket UX: **in-page JS bubble** on non-empty
`window.getSelection()` (not the platform's native selection menu — WebKitGTK
context menus are not practically customizable from managed code). The bubble
posts `{text, docId, locator}` across the JS↔C# bridge.

## 5. Boundaries

- No cloud accounts of the app's own; sync is strictly Calibre/Zotero.
- No background sync, no EPUB editing, no annotations beyond the basket.
- No git pushes or credential commits by automated agents without approval.
- Deferred until a real server demands them: Calibre HTTP **Digest** auth
  (Basic supported), Zotero **group libraries** (user library only).

## 6. Known pitfalls (learned on Android)

- pdf.js in a web view **requires DOM storage enabled**, otherwise the viewer
  half-initializes: toolbar works, pages stay blank.
- Serve documents from the **same origin** as pdf.js or its XHR fetch fails.
- `kxml2` may be unavailable in some build environments; `xpp3:xpp3:1.1.4c`
  is a working `XmlPullParserFactory` for JVM tests.
- Test fixtures: a self-generated multi-page PDF and minimal EPUB live at
  `/tmp/lecturo_test/` on the dev machine (regenerate with
  `make_fixtures.py` there if wiped).
