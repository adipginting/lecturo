# lecturo — Product & Architecture Plan (platform-neutral)

Purpose of this document: enough detail to generate a **desktop version** of
lecturo without reference to the Android codebase. It describes *what* the app
does and the settled design decisions; platform-specific choices (UI toolkit,
storage, keychain) are left as explicit decision points with a recommended
default.

## 1. What the app is

lecturo is a personal document reader for **PDF and EPUB** files, built around
one idea: while reading, you select interesting text into a **basket** — a
queue of excerpts waiting to be asked about. Tapping a basket item fires it
into its own LLM conversation that knows that excerpt as its frozen context.

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
- **Remove documents**: delete a document's local copy (per-item or
  multi-select) from the app-owned storage. Deletion is local-only — remote
  Calibre/Zotero libraries are never modified. (Remote downloads are full
  local copies; once downloaded, a document is independent of its source.)
- **Done when**: a file picked from disk and a file fetched from a URL both
  appear in the library and open, and a deleted document's file and library
  entry are gone.

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
- Selecting text in any open document offers **"Add to basket"** (custom item
  in the platform's native selection menu).
- Basket item = `{ text, docId, locator, timestamp }`. The locator (PDF page
  / EPUB spine href) keeps every item traceable to its source.
- Basket screen behaves like a cart: list items, remove one, clear all.
  Persistent across restarts.
- Tapping an item opens a **draft conversation** carrying that excerpt as its
  frozen context; nothing is persisted until the first message is sent; backing
  out keeps the item; on first send the conversation is created and the item
  leaves the basket.
- **Done when**: selected text from both a PDF and an EPUB lands in the
  basket, survives an app restart, can be removed/cleared, and tapping an item
  starts a conversation that answers using that excerpt, after which the item
  is gone from the basket.

### F5 — Chatbot
- Chat screen that answers questions using the **frozen excerpt that fired the
  conversation**: each conversation stores the basket item it was fired from
  (`contextText`) and that excerpt is the system-prompt context for every
  message (baskets are tens of items — no retrieval infra needed).
- Pluggable provider layer behind a `ChatProvider` interface:
  - **OpenAI**, **Kimi**, and **OpenRouter** — one shared OpenAI-compatible
    chat-completions client (configurable base URL, key, model).
  - **Anthropic Claude** — native Messages API client.
  - **GitHub Copilot** — listed but stubbed "not yet supported"
    (no public chat API exists).
- Blocking request/response for v1; the interface is shaped so streaming can
  be added later.
- Conversations are persisted and resumable.
- Chat sessions keep their full prompt/message history per session and are
  **deletable**: removing a session deletes the conversation and all its
  messages.
- **Done when**: a conversation fired from a basket excerpt gets an answer
  that demonstrably uses that excerpt, for each provider with a configured key,
  and a deleted session (with its history) no longer appears after restart.

### F6 — Settings
- Provider selection and per-provider API-key entry.
- Server addresses + credentials for Calibre and Zotero.
- **Saved prompts**: CRUD for custom prompt templates; in the draft
  conversation screen they appear as **chips above the composer**; tapping a
  chip appends the prompt's text on a new line in the message draft
  (stackable, editable before sending). Conversations created before this
  change keep their locked system prompt.
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
2. **Selection → basket bridge.** Hook the web view's native text-selection
   menu, read `window.getSelection()` through a JS↔native bridge, push
   `{text, docId, locator}` to native code.
3. **Position tracking.** PDF: hook pdf.js `eventBus.on('pagechanging')`.
   EPUB: intercept navigation and record the root-relative href.
4. **Local model.** Relational store (SQLite-class) with three tables:
   `documents(id, title, fileName, format, lastLocator, updatedAt)`,
   `basket_items(id, docId, text, locator, createdAt)`,
   `conversations/messages` (+ prompt choice per conversation for legacy
   conversations, and a `contextText` frozen-excerpt column for new
   basket-fired conversations).
   Preferences in a simple key-value store; secrets encrypted.
5. **Feature modules.** `reader`, `library`, `sync`, `basket`, `chat`,
   `settings`, `data` — MVVM (or unidirectional-data-flow equivalent):
   screens observe repositories, never touch storage/network directly.
6. **Chat context.** System prompt = the conversation's frozen `contextText`
   excerpt, plus the locked saved prompt on legacy conversations only; no
   whole-basket injection. No embeddings, no vector search — revisit only if
   baskets outgrow context windows.

## 4. Desktop decision points (recommendations)

| Concern | Android (existing) | Desktop recommendation |
|---|---|---|
| UI toolkit | Jetpack Compose | **Compose Multiplatform** (max code reuse from the Android app: ViewModels, repos, parsers are plain Kotlin) — Electron/Tauri also fit the web-view-centric design |
| Web view | Android WebView | CEF (via JCEF) if Compose; the toolkit's native webview otherwise |
| PDF renderer | bundled pdf.js legacy | Same — pdf.js is platform-neutral |
| Local origin | WebViewAssetLoader | Custom scheme handler (`lecturo://`) or loopback server |
| Database | Room | SQLite via SQLDelight (shared Kotlin) |
| Settings | DataStore | Properties/JSON file in app config dir |
| Key encryption | AndroidKeyStore | OS keychain (Windows Credential Manager / macOS Keychain / libsecret) |
| Networking | OkHttp + kotlinx.serialization | Same (both are multiplatform) |
| XML parsing | XmlPullParser | Same (JVM) |

## 5. Boundaries

- No cloud accounts of the app's own; sync is strictly Calibre/Zotero.
- No background sync, no EPUB editing, no annotations beyond the basket.
- No git pushes or credential commits by automated agents without approval.

## 6. Known pitfalls (learned on Android)

- pdf.js in a web view **requires DOM storage enabled**, otherwise the viewer
  half-initializes: toolbar works, pages stay blank.
- Serve documents from the **same origin** as pdf.js or its XHR fetch fails.
- `kxml2` may be unavailable in some build environments; `xpp3:xpp3:1.1.4c`
  is a working `XmlPullParserFactory` for JVM tests.
- Test fixtures: a self-generated multi-page PDF and minimal EPUB live at
  `/tmp/lecturo_test/` on the dev machine (regenerate with
  `make_fixtures.py` there if wiped).
