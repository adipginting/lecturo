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
reading, you select interesting text and **ask AI** about it. Asking makes a
**draft** — an unsent conversation that knows that excerpt as its frozen context,
waiting beside the conversations it will join until its first reply lands.

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
- A downloaded item is remembered by source, remote id and URL, so the remote list
  marks it with a check and "Downloaded"; the mark opens the local copy instead of
  fetching it again, and a file that has gone is fetched anew.
- Calibre lists **one row per format**: an EPUB and a PDF of the same book are two
  documents, marked independently.
- **Done when**: both servers' catalogs list in the UI and an item from each
  downloads into local storage and opens, then shows as Downloaded; an item that
  only exists as a PDF is not marked by the EPUB's download.

### F4 — Drafts
- Selecting text in any open document offers **"Ask AI"** in the platform's
  selection menu — there is no separate Save.
- Asking makes a **draft**: a conversation whose first message has not been sent,
  keeping the excerpt as its frozen context. A draft is a row (`sentAt` null), so
  it survives restarts and waits in the Drafts tab beside the conversations it
  will join.
- A PDF selection offers **drag handles** at either end, so a selection can be
  adjusted after it is made rather than only while it is being drawn. Dragging
  one moves that end and leaves the other where it is. A selection can cross a
  page boundary; each handle stays on its own page.
- A selection is dismissed with Back, which never leaves the document.
- The excerpt keeps its trace to the source: `{ text, docId, locator }` — a page
  number for PDFs, a position for EPUBs, the crop for a screenshot.
- EPUB selections keep their formatting, converted to Markdown at capture time;
  PDF selections are plain text, because the platform selection API returns no
  markup.
- A draft is sent by its first message: the conversation is named, the reply is
  written, and only **once the reply lands** is `sentAt` set and the draft leaves
  Drafts. A failed send (a timeout, a bad key) leaves the draft unsent to retry,
  not a half-made conversation in the list.
- Drafts and conversations are two tabs of one surface, and a conversation's
  excerpt is shown whole in the chat window — nothing is clamped.
- Reachable **while reading**, as a sheet over the page, so the reading position
  is never lost by going to look at it.
- **Done when**: an Ask AI from a PDF and an EPUB makes a draft that survives a
  restart, a failed send leaves it in Drafts, a successful first reply turns it
  into a conversation, and the whole excerpt is visible in the chat window.

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
- Replies render as Markdown; TeX in a reply is typeset by KaTeX bundled in the
  app, with prose left to the cheap renderer and anything that will not render
  falling back to its source.
- Opened from the reader, a conversation rides over the page in a panel rather
  than replacing it; putting the panel away keeps it, and the reading position,
  for coming back to.
- A document has a **current chat** — the sent conversation fired from it that was
  last engaged with. The reader's top bar carries **one chat icon that opens the
  document's active conversation**, failing that its newest draft, failing that
  the chats surface. **While a conversation is open for the document, a new
  excerpt or screenshot is appended to it** rather than starting another draft
  (see [adr/0003](adr/0003-append-excerpts-to-open-conversation.md)).
- Conversations are persisted, resumable, and deletable with their whole history.
- A draft that gets no reply is **not a conversation**: if the model call fails,
  it stays in Drafts, unsent, to retry.
- Sending past the model's context budget **compacts first** — the older messages
  are summarised and superseded — and goes anyway if that summarising call fails.
- A conversation **names itself** from the first message it is sent, and that name
  is **editable** — from the list row or from the open chat. A typed name is
  trimmed, collapsed to one line and bounded in length; an empty one is refused,
  so a chat is never left nameless. Auto-naming never overwrites a name the user
  set.
- **Done when**: a conversation fired from a draft gets an answer that
  demonstrably uses its excerpt, for each provider with a configured key; a
  deleted session no longer appears after restart; a renamed one comes back
  under its new name, from both entry points; a failed send leaves the draft in
  Drafts; and a document's draft or most recently engaged chat is one tap away in
  the reader's top bar.

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
| Draft capture | `createDraft(text, locator)` — both renderers meet here; nothing downstream knows the format |
| Remote libraries | `RemoteLibrarySource`: `browse() / search() / download()` |
| Chat | `ChatProvider`: `chat(system, messages)` |
| Storage | Room: documents, conversations (a draft is a null-`sentAt` row), messages, prompts |
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
- No background sync, no EPUB editing, no annotations beyond the draft queue.
- Deleting documents and drafts is always local.
- No git pushes or credential commits by automated agents without approval.

## 5. Not yet built

- **Appending an excerpt to the open conversation** — the rule is decided
  ([adr/0003](adr/0003-append-excerpts-to-open-conversation.md)) and the reader's
  chat icon now prefers the active conversation; the Ask AI and screenshot paths
  still create a draft unconditionally.
- **Remote covers** — Calibre covers are fetched now; Zotero's API offers none, so
  its rows show an initial.
- **Book context layers and a PDF text extractor** for skills — see
  [architecture.md](architecture.md).
