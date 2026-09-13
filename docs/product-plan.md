# Lecturo — Product & Feature Plan

What the app does, and what "done" means for each feature. Kept portable on
purpose: the features and their acceptance criteria shouldn't depend on which
platform implements them.

The rendering stack as it stands is **Android-specific** — see §3 — so this
document describes the behaviour and the seams, not the widgets. The living
architecture record, including the current constraints, is
[architecture.md](architecture.md).

## 1. What the app is

A personal document reader for **PDF and EPUB**, built around one idea: while
reading, you select interesting text into **Saved** — a queue of excerpts
waiting to be asked about. Tapping a saved item fires it into its own LLM
conversation that knows that excerpt as its frozen context.

## 2. Features with acceptance criteria

### F1 — Reading
- Open and render PDF files with smooth continuous scrolling.
- Open and render EPUB files in continuous scroll mode, following the
  publication's own reading order.
- Restore the last-read position per document on reopen.
- **Done when**: a multi-page PDF and a multi-chapter EPUB both open, scroll, and
  resume where they were left after closing and reopening.

### F2 — Sources
- Import documents from the local file system. **Import = copy** into the app's
  own storage; the app owns its library.
- Download a document from a web URL (in-app URL field).
- A download is judged by its payload, not by its URL. Download gateways — pages
  served in place of the file, as Standard Ebooks serves — are followed to the
  real file; whatever is still not a PDF or an EPUB is refused by name and never
  enters the library. A file picked from disk gets the same check before it is
  copied in.
- Accept documents sent from other apps.
- **Remove documents**: delete the local copy, per-item or multi-select.
  Deletion is local-only — remote Calibre/Zotero libraries are never modified.
- **Done when**: a file picked from disk and a file fetched from a URL both
  appear in the library and open, a URL that answers with a web page says so
  instead of adding a broken book, and a deleted document's file and library
  entry are both gone.

### F3 — Remote library sync
- Two remote sources behind one interface:
  `browse() / search() / download()` returning library items.
  - **Calibre**: its Content Server speaks **OPDS** (Atom/XML catalog).
  - **Zotero**: the **Web API** (`https://api.zotero.org`, REST/JSON, per-user
    API key).
- Manual sync only (a sync button); no background sync.
- A failing source surfaces its error inline and never blocks the others; local
  data is always kept.
- Library UI: one screen, three tabs — Local / Calibre / Zotero.
- **Done when**: both servers' catalogs list in the UI and an item from each
  downloads into local storage and opens.

### F4 — Saved
- Selecting text in any open document offers **"Save"** in the platform's
  selection menu.
- A PDF selection offers **drag handles** at either end, so a selection can be
  adjusted after it is made rather than only while it is being drawn. Dragging
  one moves that end and leaves the other where it is.
- A saved item is `{ text, docId, locator, timestamp }`. The locator keeps every
  item traceable to its source: a page number for PDFs, a position for EPUBs.
- EPUB selections keep their formatting, converted to Markdown at capture time;
  PDF selections are plain text, because the platform selection API returns no
  markup.
- The list behaves like a cart: list items, remove one, clear all. Persistent
  across restarts. Rows clamp to three lines, expand on a tap, and open on a
  double-tap.
- Tapping an item opens a **draft conversation** carrying that excerpt as its
  frozen context; nothing is persisted until the first message is sent; backing
  out keeps the item; on first send the conversation is created, and the item
  leaves the list only once the model's reply lands — a failed send (a timeout,
  say) leaves the item and no conversation behind.
- Reachable **while reading**, as a sheet over the page, so the reading position
  is never lost by going to look at it.
- **Done when**: selected text from both a PDF and an EPUB lands in the list, a
  PDF selection can be adjusted with its handles before being saved, the list
  survives an app restart and can be removed and cleared, and tapping an item
  starts a conversation that answers using that excerpt, after which the item is
  gone.

### F5 — Chatbot
- Each conversation stores the excerpt it was fired from, and that excerpt is the
  system-prompt context for every message in it. Conversations are per-excerpt,
  not per-document.
- Pluggable providers behind a `ChatProvider` interface:
  - **OpenAI**, **Kimi**, **OpenRouter**, **DeepSeek** — one shared
    OpenAI-compatible chat-completions client, configurable base URL, key, model.
  - **Anthropic Claude** — native Messages API client.
  - **GitHub Copilot** — listed but stubbed; no public chat API exists.
- The active provider is chosen in the reader's top bar and can be **changed
  mid-conversation**; the history and the frozen context stay put.
- Replies render as Markdown.
- Opened from the reader, a conversation rides over the page in a panel rather
  than replacing it; putting the panel away keeps it, and the reading position,
  for coming back to.
- A document has a **current chat** — the conversation fired from it that was
  last engaged with — and the reader's top bar opens it in that panel, tinted
  while one exists and offering the chats list when there is none. Asking about
  a new excerpt starts the next chat; the previous one stays in the list.
- Conversations are persisted, resumable, and deletable with their whole history.
- A draft that gets no reply is **not a conversation**: if the model call fails,
  the attempt is rolled back, the excerpt stays in Saved, and the draft stays in
  the composer to retry.
- A conversation **names itself** from the first message it is sent, and that name
  is **editable** — from the list row or from the open chat. A typed name is
  trimmed, collapsed to one line and bounded in length; an empty one is refused,
  so a chat is never left nameless. Auto-naming never overwrites a name the user
  set.
- **Done when**: a conversation fired from a saved excerpt gets an answer that
  demonstrably uses that excerpt, for each provider with a configured key; a
  deleted session no longer appears after restart; a renamed one comes back
  under its new name, from both entry points; and a document's most recently
  engaged chat is one tap away in the reader's top bar.

### F6 — Settings
- Provider selection, and each provider's base URL, API key and model.
- Server addresses and credentials for Calibre and Zotero.
- **Saved prompts**: CRUD for custom prompt templates, capped at twelve. They
  appear as chips above the composer in the draft screen and in an open
  conversation; tapping one sends it, together with whatever is already typed.
- API keys are encrypted at rest — Android keystore here; another platform would
  use its own keychain — never stored in plain text, never committed.
- **Done when**: keys and servers survive restart, a saved prompt visibly changes
  chatbot behaviour, and no secret appears in the repo.

### F7 — Library covers
- Each document shows a cover: an EPUB's own cover image, or a PDF's first page.
- Covers are cached, so they are not recomputed on every launch.
- A document without a determinable cover shows its initial instead.
- **Done when**: a shelf of mixed formats shows real covers for the books that
  have them, and initials for those that don't, without stalling the list.

## 3. The seams that make it portable

The Android implementation splits rendering from everything else. Anything below
the renderers is plain Kotlin and could move:

| Seam | Contract |
|---|---|
| Saved capture | `addToSaved(text, locator)` — both renderers meet here; nothing downstream knows the format |
| Remote libraries | `RemoteLibrarySource`: `browse() / search() / download()` |
| Chat | `ChatProvider`: `chat(system, messages)` |
| Storage | Room: documents, saved items, conversations, messages, prompts |
| Positions | opaque strings — a page number, or Readium's serialized `Locator` |

What is **not** portable, and would be re-decided on a desktop port:

- **Both renderers.** PDF uses `androidx.pdf`, which is the Android platform's
  own renderer; EPUB uses the Readium Kotlin toolkit, which is Android too.
  Neither exists on desktop.
- **The selection menus.** Both are the Android floating action mode, and both
  feed the seam above them.
- **The reader's panel**, which is a Material bottom sheet arranged not to be
  modal, wrapping chat bodies the full screens also use. The wrapping is portable;
  the sheet is not.
- **The PDF pickers**, which are drawn against `androidx.pdf`'s own touch targets.
  A port would have neither those targets nor the same page geometry to draw them
  against, so they would be rebuilt rather than reused.
- **Key storage**, **Room**, and **DataStore** — each has a platform equivalent.
- **The cover pipeline**, which leans on `PdfRenderer` for PDFs.

The invariant a port must preserve is the first row of that table: whatever
renders the documents, capture must end in `(text, locator)`.

## 4. Boundaries

- No cloud accounts of the app's own; sync is strictly Calibre/Zotero.
- No background sync, no EPUB editing, no annotations beyond the saved queue.
- Deleting documents and saved items is always local.
- No git pushes or credential commits by automated agents without approval.

## 5. Not yet built

- **Provenance** — which source a document came from. Needed before covers can be
  limited to fetched books, and before a book whose file is gone could be
  re-downloaded.
- **Remote covers** — Calibre's OPDS thumbnail link is discarded today; Zotero
  offers none.
