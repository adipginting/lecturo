# Lecturo Desktop — Interaction & Scenario Spec

> **Superseded in part (2026-09-21).** Scenarios that describe **saving** a
> passage, the Saved screen, or a draft that is only persisted once a message is
> sent no longer describe the app: asking about a passage now makes a *draft*,
> which is an unsent conversation from the moment it is captured, and there is no
> Save. See [CONTEXT.md](../CONTEXT.md) and
> [ADR 0002](adr/0002-drafts-are-unsent-conversations.md). Affected: S17, S19,
> S20, S24, S27, S32, S33 and whatever they are cited by.

Desktop/computer adaptation of the Android interaction spec (`lecturo-interactions.md`
in the mobile repo). Same scenario IDs; every input is translated to mouse + keyboard
and every platform object to its desktop equivalent. Where the desktop port already
implements a scenario it is marked **[built]**; otherwise it is **(planned)**.

- Pointer: **click** = primary (select/open), **right-click** = context menu,
  **double-click** = open, **drag** = select text / adjust handles / drop files.
- Keyboard: **Esc** = Back (see G1), **Alt+Left** = Back, **arrows / PageUp /
  PageDown / Space** = scroll, **Ctrl+C / Ctrl+A** = copy / select all,
  **Ctrl+F** = find (planned), **Enter** = confirm.
- Window: resizable, minimum 1040×640; the shell is a **nav rail** (Library,
  Saved, Chats, Settings) plus a **docked right panel** for chat.

## 0. Cast

| Object | Meaning |
|---|---|
| Document | A PDF or EPUB copied into `~/.local/share/lecturo/documents`, one row in `documents` |
| Saved item | An excerpt + locator + document, waiting to be asked about |
| Conversation | A chat, seeded by a frozen excerpt (or screenshot) |
| Message | One user or assistant turn; some are compaction summaries |
| Prompt / skill | A saved prompt; *(planned)* a book-level skill, installable from a catalog |
| Remote item | One entry from Calibre (OPDS) or Zotero (Web API) |
| Background run *(planned)* | One skill applied to one book, run by the app |

## 1. Global rules

**G1 — Back is a stack, innermost first.** **[built]**
*Condition:* any of these is active.
*Narration:* **Esc** (or **Alt+Left**) clears a text selection → leaves crop mode →
closes the chat panel → (panel already closed) leaves the reader → leaves the
Settings/Chats screen. A handler that is not enabled never sees the event: with
the panel closed, Esc leaves the reader. Window close is not part of the stack.

**G2 — The library is app-owned.** **[built]**
*Condition:* import or download.
*Narration:* the file is copied into app storage with a UUID name; the original is
untouched. Deleting a document deletes that copy and its rows — never a remote
library's copy.

**G3 — Persistence.** **[built]**
*Condition:* anything is created or changed.
*Narration:* documents, saved items, conversations, messages and prompts live in
SQLite (`~/.local/share/lecturo/lecturo.db`); provider keys, server addresses and
selections in `~/.config/lecturo/settings.json`, with secrets in the OS keychain
(libsecret) or an AES-encrypted file. Positions and excerpts survive restarts.

**G4 — Window changes never restart the reader.** **[built]**
*Condition:* resize, maximise, theme change or DPI/font-scale change while a book
is open.
*Narration:* the window reflows; the reader keeps its position and its panel. The
stored position is reopened on relaunch.

**G5 — Layout.** **[built]**
*Condition:* a small window, or the chat panel open.
*Narration:* nothing interactive is clipped: the chat composer stays at the bottom
of the panel, the reader's chapter bar sits above the window edge, and the reading
column reflows to the available width (it does not assume phone insets).

**G6 — Network failures are messages, not crashes.** **[built]**
*Condition:* any remote call fails.
*Narration:* the failure is shown where it happened and the local data is kept;
nothing is half-written.

## 2. Library

### S1 — First launch **[built]**
*Condition:* no documents.
*Narration:* the Local tab shows "No documents yet. Press + to import a PDF or
EPUB, or paste a URL above to download one."; Calibre/Zotero tabs offer to
configure.

### S2 — Import from the file system **[built]**
*Condition:* the **+** button, or files dropped on the window.
*Narration:* the system picker opens for `application/pdf` and
`application/epub+zip` (multi-select). Each file's first bytes decide the format;
a book is copied in and opened; a web page or anything else is refused by name
("…is not a book — only PDF and EPUB are supported."), and the library is
unchanged.

### S3 — Download from a URL **[built]**
*Condition:* the URL field in the Local tab; **Enter** or **Download**.
*Narration:* the payload is fetched and sniffed: a PDF/EPUB is saved and opened;
a download page is followed (at most 3 hops, HTTP headers dropped when the hop
crosses hosts) to the real file; anything still not a book is
refused by name.

### S4 — Receive a link from another app **[built]**
*Condition:* the OS opens a URL or a document with Lecturo — the command line
carries a `http(s)` link or a PDF/EPUB path.
*Narration:* the app opens on the Library and runs the S3 flow for a link, or the
S2 flow for a file; the input is consumed once. Drag-and-drop is unchanged. (The
desktop equivalent of the mobile share intent; the OS file associations
themselves are packaging, not app code.)

### S5 — Open a document **[built]**
*Condition:* a Local row is clicked, or an import/download just finished.
*Narration:* the reader opens at the stored position. If the file is gone, the
reader says so instead of failing; if its bytes are not a book, it says that.

### S6 — Delete a document **[built]**
*Condition:* the row's trash icon, or multi-select (Ctrl-click rows) and Delete.
*Narration:* a dialog states that the local copy and its saved excerpts go, and
that remote libraries are untouched. Confirm removes the file, the
saved rows and the document row. Today the trash removes immediately.

### S7 — Calibre: sync, browse, search *(planned)*
*Condition:* the Calibre tab, with a server URL configured.
*Narration:* **Sync** lists the root catalog; a catalog row browses into it and
Back/Esc steps out; **Search** uses the server's OPDS search when it declares one,
and says so when it does not. A failing server shows its error inline and leaves
the other tabs alone.

### S8 — Zotero: sync and search *(planned)*
*Condition:* the Zotero tab, with user id and API key configured.
*Narration:* Sync lists the user's PDF/EPUB attachments; Search queries them. A
bad key surfaces the HTTP failure inline.

### S9 — Download a remote item *(planned)*
*Condition:* a downloadable row is clicked.
*Narration:* the file is fetched with the source's headers, saved, opened in the
reader, and linked to the remote item (source, remote id, URL). The row then reads
as downloaded.

### S10 — Open an already-downloaded remote item *(planned)*
*Condition:* the row's remote item has a local document.
*Narration:* the local copy opens with no network. If its file is missing, the
stale row is removed and the item is downloaded again. A second copy is never
written while one exists.

### S11 — Mark a downloaded remote item *(planned)*
*Condition:* a local document was downloaded from this source and remote id.
*Narration:* the row's trailing arrow becomes a check and its meta line says
"Downloaded". The mark is per format: a downloaded EPUB does not mark the PDF
beside it. Deleting the local document unmarks the row.

### S12 — Calibre: two formats, two documents *(planned)*
*Condition:* one OPDS entry offers an EPUB and a PDF.
*Narration:* the catalog lists two rows, "EPUB"/"PDF" in their meta lines, each
downloaded, marked and opened on its own.

## 3. Reader

### S13 — Open a PDF / EPUB **[built]**
*Condition:* a document is opened.
*Narration:* a spinner, then the page. **EPUB renders natively** in a scrolling
readable column (Source Serif 4); **PDF renders through WebKitGTK's own viewer**.
Both resume at the stored position. Failure to open is a sentence, not a crash.

### S14 — Reading controls **[built]**
*Condition:* the book is open.
*Narration:* the top bar holds back, title, **Save selection**, **Ask AI**, the
**model picker** and the chat toggle. EPUB adds Previous / Contents / Next under
the page. Scroll with the wheel, arrows, PageUp/PageDown or Space.

### S15 — EPUB chapter stepping **[built]**
*Condition:* an EPUB with more than one resource.
*Narration:* Previous/Next move one resource in reading order; **Contents** is a
dropdown that jumps to a resource. The bar stays available while the panel is
open (desktop has the width).

### S16 — EPUB paging gesture *(not applicable)*
*Condition:* —
*Narration:* No swipe on desktop. Chapters turn with Previous/Next, the Contents
dropdown, or PageUp/PageDown at the end of a chapter.

### S17 — Select text (PDF/EPUB) **[built]**
*Condition:* drag with the mouse over text (double-click selects a word,
triple-click a paragraph).
*Narration:* the platform selection appears. **EPUB** selections are native; the
top bar's **Save** and **Ask AI** act on them and the reader offers a right-click **Save** / **Ask AI** menu (plus Ctrl+S /
Ctrl+Enter). **PDF** text is rendered natively too, so selection is the same.

### S18 — Adjust a PDF selection *(planned)*
*Condition:* a PDF selection exists.
*Narration:* dragging the viewer's handle at either end extends that end; drawn
pickers follow the page, its scroll and the zoom. A selection can run past a page
boundary: each picker is placed on its own page.

### S19 — Save a selection **[built]**
*Condition:* **Save** in the top bar (or, planned, the context menu).
*Narration:* the text and its locator (PDF page, EPUB chapter) go to Saved; a
status line confirms; the selection clears. The same excerpt can be saved twice.

### S20 — Ask AI about a selection **[built]**
*Condition:* **Ask AI** in the top bar (or, planned, the context menu).
*Narration:* the docked chat panel opens with a draft carrying the excerpt; the
reading position is untouched.

### S21 — Dismiss a selection with Back **[built]**
*Condition:* a selection is active.
*Narration:* **Esc** clears the selection. It does not leave the document or close
anything else. With no selection, Esc behaves per G1.

### S22 — Screenshot a passage **[built, partial]**
*Condition:* the screenshot action; the chosen model can read images.
*Narration:* the camera captures the visible reading area and opens a draft with
the image as its context; the image is sent to the provider on the first message.
Scanned PDFs (the WebKit surface) cannot be captured yet, and drag-to-crop is not
built — the whole visible page is captured.

### S23 — Model picker **[built]**
*Condition:* the model control in the reader top bar.
*Narration:* the configured providers list with their display names; choosing one
sets the global provider new chats use and marks the current conversation for the
next reply. The reader shows the active provider by name.

### S24 — Open a document's chat **[built]**
*Condition:* the chat toggle. Its target is the pending draft, else the document's
active conversation (newest by engagement), else the chats list.
*Narration:* the panel shows that target. While a reply is in flight with the panel
closed, the counter/status shows activity.

### S25 — Collapse the panel **[built]**
*Condition:* the panel's close button, or **Esc**.
*Narration:* the panel slides away, the book is exactly where it was, and the
chapter bar (EPUB) is unaffected. Nothing is discarded: the draft stays, the
conversation stays, the toggle brings either back.

### S26 — Type in the panel **[built]**
*Condition:* the composer is focused.
*Narration:* text wraps in the composer; **Ctrl+Enter** (or the send button)
sends. Esc puts the panel away (and returns focus to the reader).

### S27 — Send the first message (draft) **[built]**
*Condition:* a draft with an excerpt or image.
*Narration:* the conversation is created with the excerpt as its frozen context,
the first message names it, and the reply is written. On success the saved item
leaves Saved and the draft becomes the open conversation. If the model never
answers — a timeout, a bad key — the just-created conversation is rolled back: the
excerpt stays in Saved and the draft stays to retry.

### S28 — Model timeout *(planned)*
*Condition:* a completion times out.
*Narration:* the failure surfaces where it happened — a draft leaves nothing
behind but the draft itself, an existing conversation an error line — and the
reader can retry by sending again. There is **no automatic retry yet** (the
status doc lists it as planned; the interaction were written before the code
caught up).

### S29 — Conversation: send, switch, rename **[built]**
*Condition:* an open conversation.
*Narration:* sending appends the message and the reply; the provider can be
changed mid-thread, keeping history and context; the title is editable from the
title itself or the list, trimmed to one bounded line, never emptied.

### S30 — Conversation: compaction **[built]**
*Condition:* the compact action.
*Narration:* if there is older material, it is summarised and the originals are
superseded; "Conversation compacted" is shown. If there is not, "Nothing to
compact yet". The token counter reflects the active context.

### S31 — Prompt chips **[built]**
*Condition:* saved prompts exist.
*Narration:* chips sit above the composer in a draft and in an open conversation;
clicking one sends the prompt together with whatever is already typed.

## 4. Saved

### S32 — Saved while reading **[built, as a screen]**
*Condition:* the bookmark icon in the reader.
*Narration:* Saved opens with the reading position preserved (a docked side panel
is the desktop equivalent of the mobile sheet). Rows can be removed; opening one
shows its draft in the panel.

### S33 — The Saved screen **[built]**
*Condition:* **Saved** in the nav rail.
*Narration:* the list behaves like a cart: rows clamp to three lines, trash
removes one, **Clear all** empties it, and clicking a row opens a draft. Empty
state points at selection.

## 5. Chats

### S34 — Conversation list **[built]**
*Condition:* the Chats screen.
*Narration:* newest first; each row shows title, its excerpt clamped, metadata and
provider/date. Rename and delete are per row; delete removes the messages with it.
A **+** starts a conversation from a saved excerpt.

## 6. Settings

### S35 — Providers **[built]**
*Condition:* the provider section.
*Narration:* one provider is selected and shows base URL, API key and model. The
model list can be fetched from the provider; **Save provider** stores it. Keys are
encrypted at rest and never displayed in clear. Lecturo Cloud needs no key (the
install id is the bearer).

### S36 — Servers **[built]**
*Condition:* the servers section.
*Narration:* Calibre address + credentials, Zotero user id + API key; **Save
servers** stores them (keys encrypted).

### S37 — Saved prompts **[built]**
*Condition:* the prompts section.
*Narration:* add / edit / delete, **capped at twelve**, each a title and a body,
used as chips.

## 7. Skills *[built]*

### S38 — Browse the catalog **[built]**
*Condition:* a marketplace URL is configured; the user opens Skills.
*Narration:* the catalog JSON lists skills — name, description, author, version,
scope, trigger, price. Installed ones are marked; a failing catalog shows its
error and keeps what is installed.

### S39 — Install / uninstall **[built]**
*Condition:* a catalog row.
*Narration:* installing stores the skill locally; it is usable offline at once.
Uninstalling removes it and stops its future runs; results it already produced
stay.

### S40 — Apply a skill to a book **[built]**
*Condition:* a book is open; the skill is installed and enabled.
*Narration:* the skill is attached to this document. A `message`-scope skill can
run on demand from the reader; a `conversation`-scope skill seeds the book's chat.
The skill never sees more than the book it was attached to.

### S41 — A background run **[built]**
*Condition:* a skill's trigger fires (on demand, or when text is saved; scheduled
runs later).
*Narration:* the app runs the skill against the configured provider in the
background. A finished run lands in the book's insights list and opens like a
conversation; a failed run is recorded with its error. One run per skill at a
time; timeouts use the normal retry rule; closing the window does not cancel a run
in flight, but the user can disable a skill.

### S42 — Insights **[built]**
*Condition:* runs exist for this book.
*Narration:* a per-document list shows each run (skill, time, status) and its
result. Notifications are opt-in per skill; the record is always kept.

### S43 — Paying for it **[built, partial]**
*Condition:* a skill is priced, or execution is a paid tier; the user has no
entitlement.
*Narration:* the skill is visible but not runnable; installing is offered, and the
flow is refused with the reason. Free skills are unaffected.

## 8. Cross-cutting scenarios

### X1 — Provider not configured **[built]**
*Condition:* sending with no API key.
*Narration:* the message fails with "No API key configured for «Provider»"; a draft
rolls back (S27), an existing conversation keeps the attempt's error line.

### X2 — No network **[built]**
*Condition:* any remote call with no connectivity.
*Narration:* the call fails with its message where it was requested; the library,
Saved and chats are unaffected.

### X3 — Missing file **[built]**
*Condition:* a document's file was removed outside the app.
*Narration:* opening says "Could not open «title»: the document's file is missing";
for a remote-downloaded document, clicking its remote row fetches it again
*(planned)*.

### X4 — Deleted document **[built]**
*Condition:* a document is deleted while its chats exist.
*Narration:* the saved excerpts for it go with the file; conversations stay (they
are history) and still open, though their locator no longer resolves.

### X5 — Process death **[built]**
*Condition:* the app is killed.
*Narration:* on relaunch the library shows what persisted; an open book reopens at
its stored position; the panel's in-memory draft is gone, unless it was already
sent.

## 9. Platform mapping (Android → desktop)

| Android | Desktop |
|---|---|
| Tap / long-press / double-tap | Click / right-click / double-click |
| Swipe to turn chapter | Previous / Next or PageUp/PageDown |
| System Back button | **Esc** / Alt+Left / toolbar back (G1) |
| Bottom sheet over the page | Docked right panel |
| Share intent | Drag-and-drop, "Open with", or paste URL |
| Room | SQLite (`lecturo.db`) |
| DataStore | `settings.json` |
| AndroidKeyStore (AES/GCM) | libsecret, or AES-256-GCM file fallback |
| WebView + Readium | Native Avalonia (EPUB) + WebKitGTK viewer (PDF) |
| Floating selection action mode | OS context menu + reader top-bar Save / Ask AI |
| Rotation / font scale / locale | Window resize / system theme / DPI |
| Insets, IME padding | Window layout; composer pinned to panel bottom |
| Device screenshot + crop | Region capture of the rendered page |

## 10. Open decisions (desktop)

- Wheel-scrolling while a selection is active in the PDF viewer.
- Whether Saved opens as a docked side panel or keeps replacing the main content.
- A shortcut set (Ctrl+F find, Ctrl+S save selection, Ctrl+Enter send).
- Scheduled triggers and cloud execution for skills.
- Signing skills once they can do more than hold a prompt.
