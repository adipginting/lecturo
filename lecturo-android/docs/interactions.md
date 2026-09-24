# Lecturo — Interaction & Scenario Spec

Every interaction the app can have, written as **Condition** (what must be true,
and the rules that govern it) and **Narration** (what the user sees, what the app
does, and how it ends). Screens are grouped; cross-cutting failures come last.

Status markers: nothing = built; *(planned)* = specified here, not yet built.

## 0. Cast

| Object | Meaning |
|---|---|
| Document | A PDF or EPUB copied into `filesDir/documents`, one row in `documents` |
| Draft | A conversation whose first message has not been sent; was "Saved item" |
| Conversation | A chat, seeded by a frozen excerpt (or screenshot) |
| Message | One user or assistant turn; some are compaction summaries |
| Prompt / skill | A saved prompt; *(planned)* a book-level skill, installable from a catalog |
| Remote item | One entry from Calibre (OPDS) or Zotero (Web API) |
| Background run *(planned)* | One skill applied to one book, run by the app |

## 1. Global rules

**G1 — Back is a stack, innermost first.**
*Condition:* any of these is active.
*Narration:* Back clears a text selection → leaves crop mode → puts the chat panel
away → (with the panel gone) leaves the reader → leaves a screen. A handler that is
not enabled never sees the event: with the panel closed, Back leaves the reader.

**G2 — The library is app-owned.**
*Condition:* import or download.
*Narration:* the file is copied into app storage with a UUID name; the original is
untouched. Deleting a document deletes that copy and its rows — never a remote
library's copy.

**G3 — Persistence.**
*Condition:* anything is created or changed.
*Narration:* documents, conversations (drafts are unsent rows), messages and
prompts live in Room; provider keys, server addresses and selections in DataStore
(keys encrypted with AndroidKeyStore). Positions and excerpts survive restarts,
drafts included.

**G4 — Configuration changes never restart the reader.**
*Condition:* rotation, font scale, theme, locale change while a book is open.
*Narration:* the activity handles them itself; the reader keeps its position and
its panel. On process death the app restores without the navigator and reopens the
book at the stored position.

**G5 — Insets.**
*Condition:* the keyboard or the navigation bar is showing.
*Narration:* the chat composer rides above both; the reader's chapter bar sits
above the navigation bar; nothing interactive sits under the system bar.

**G6 — Network failures are messages, not crashes.**
*Condition:* any remote call fails.
*Narration:* the failure is shown where it happened and the local data is kept;
nothing is half-written.

## 2. Library

### S1 — First launch
*Condition:* no documents.
*Narration:* the Local tab shows "No documents yet. Tap + to import a PDF or
EPUB."; Calibre/Zotero tabs offer to configure.

### S2 — Import from the file system
*Condition:* the user taps the + button.
*Narration:* the system picker opens for `application/pdf` and
`application/epub+zip`. Picking a file shows a spinner while it is read: its first
bytes decide the format. A book is copied in and opened; a web page or anything
else is refused by name ("…is a web page, not a book…"), and the library is
unchanged.

### S3 — Download from a URL
*Condition:* the download icon in the Local tab.
*Narration:* a dialog takes a URL. On confirm, the payload is fetched and sniffed:
a PDF/EPUB is saved and opened; a download page is followed (at most 3 hops, HTTP
headers dropped when the hop crosses hosts) to the real file; anything still not a
book is refused by name.

### S4 — Receive a shared link
*Condition:* another app shares a `text/plain` URL and Lecturo is chosen.
*Narration:* the app opens on the Library and runs the S3 flow with that URL; the
link is consumed once, so returning to the Library does not fetch it again.

### S5 — Open a document
*Condition:* a Local row is tapped, or an import/download just finished.
*Narration:* the reader opens at the stored position. If the file is gone, the
reader says so instead of failing; if its bytes are not a book, it says that.

### S6 — Delete a document
*Condition:* the row's trash icon, or multi-select (long-press a row, then tap) and
the delete action.
*Narration:* a dialog states that the local copy and its drafts go, and that remote
libraries are untouched. Confirm removes the file, the drafts and the document row.

### S7 — Calibre: sync, browse, search
*Condition:* the Calibre tab, with a server URL configured.
*Narration:* Sync lists the root catalog; a catalog row browses into it and Back
steps out; Search uses the server's OPDS search when it declares one, and says so
when it does not. A failing server shows its error inline and leaves the other
tabs alone.

### S8 — Zotero: sync and search
*Condition:* the Zotero tab, with user id and API key configured.
*Narration:* Sync lists the user's PDF/EPUB attachments; Search queries them. A
bad key surfaces the HTTP failure inline.

### S9 — Download a remote item
*Condition:* a downloadable row is tapped.
*Narration:* the file is fetched with the source's headers, saved, opened in the
reader, and linked to the remote item (source, remote id, URL). *(planned)* the row
then reads as downloaded.

### S10 — Open an already-downloaded remote item
*Condition:* the row's remote item has a local document.
*Narration:* the local copy opens with no network. If its file is missing, the
stale row is removed and the item is downloaded again. A second copy is never
written while one exists.

### S11 — Mark a downloaded remote item
*Condition:* a local document was downloaded from this source and remote id.
*Narration:* the row's trailing arrow becomes a check and its meta line says
"Downloaded". The mark is per format: a downloaded EPUB does not mark the PDF
beside it. Deleting the local document unmarks the row.

### S12 — Calibre: two formats, two documents
*Condition:* one OPDS entry offers an EPUB and a PDF.
*Narration:* the catalog lists two rows, "EPUB"/"PDF" in their meta lines, each
downloaded, marked and opened on its own.

## 3. Reader

### S13 — Open a PDF / EPUB
*Condition:* a document is opened.
*Narration:* a spinner, then the page. PDF renders through the platform viewer,
EPUB through Readium in continuous scroll; both resume at the stored position.
Failure to open is a sentence, not a crash.

### S14 — Reading controls
*Condition:* the book is open.
*Narration:* the top bar holds back, title, this document's chat, screenshot and
the model picker. EPUB adds Previous / Contents / Next under the page.

### S15 — EPUB chapter stepping
*Condition:* an EPUB with a table of contents, no panel open.
*Narration:* Previous/Next move one resource in reading order; Contents opens the
TOC, indented, and a tap jumps. The bar hides while the chat panel is open and
returns when it closes.

### S16 — EPUB swipe
*Condition:* the reader is scrolling.
*Narration:* a drag that is mostly vertical scrolls and never turns the chapter.
Only a deliberate sideways swipe — at least a fifth of the width and clearly more
horizontal than vertical — turns it; the direction follows the reading
progression.

### S17 — Select text (PDF/EPUB)
*Condition:* a long press on text.
*Narration:* the platform selection appears with **Ask AI** appended to its menu.
PDF selections are plain text; EPUB selections are converted from HTML to Markdown.
PDF selections also get two drawn pickers at the outer corners.

### S18 — Adjust a PDF selection
*Condition:* a PDF selection exists.
*Narration:* dragging the viewer's handle at either end extends that end; the
pickers follow the page, its scroll and the zoom. A selection can run past a page
boundary: each picker is placed on its own page.

### S19 — The draft persists
*Condition:* a draft exists, from Ask AI (S20) or a screenshot (S22).
*Narration:* the excerpt and its locator (PDF page, EPUB position, saved crop) are
written to the database, so the draft is still in Drafts after the app is killed.
The same excerpt can make more than one draft.

### S20 — Ask AI about a selection
*Condition:* Ask AI in the selection menu.
*Narration:* the chat panel rises over the page with a draft carrying the excerpt;
the chat icon tints. The reading position is untouched.

### S21 — Dismiss a selection with Back
*Condition:* a selection is active.
*Narration:* Back clears the selection. It does not leave the document or close
anything else. With no selection, Back behaves per G1.

### S22 — Screenshot a passage
*Condition:* the screenshot icon; the chosen model can read images.
*Narration:* crop mode covers the page; drag the rectangle, confirm to save the
crop, or cancel to leave without a file. Confirming opens a draft with the image
as its context. If the model cannot read images, a toast says which setting to
change and nothing is captured.

### S23 — Model picker
*Condition:* the model icon.
*Narration:* the configured providers list with their model names; an
unconfigured one is shown dimmed and labelled "(not configured)". Choosing one sets
the global provider new chats use and marks the current conversation for the next
reply; the picker's tint warns while the active provider is unconfigured.

### S24 — Open a document's chat
*Condition:* the chat icon. Its target is the pending draft, else the document's
active conversation (newest by engagement), else the chats list.
*Narration:* the panel opens on that target. Tinted icon = there is a chat in
reach. While a reply is in flight with the panel away, the icon's slot shows a
small spinner.

### S25 — Collapse the panel
*Condition:* the chevron, a drag down, or Back.
*Narration:* the sheet slides away, the book is exactly where it was, and the
chapter bar (EPUB) returns. Nothing is discarded: the draft stays, the
conversation stays, the icon brings either back. A drag that lands on either
collapsed anchor is treated as put away, so the icon never goes dead.

### S26 — Type in the panel
*Condition:* the composer is focused.
*Narration:* the keyboard lifts the whole sheet; the composer stays above it and
above the navigation bar. Back hides the keyboard first, then closes the panel.

### S27 — Send the first message (draft)
*Condition:* a draft with an excerpt or image.
*Narration:* the first message names the conversation and the reply is written; the
draft's excerpt is still its frozen context. Only once a reply lands is the draft
sent — it leaves Drafts and becomes the open conversation. If the model never
answers — a timeout, a bad key — the draft stays in Drafts, unsent, to retry;
nothing half-made is left in the conversation list.

### S28 — Model timeout
*Condition:* a completion times out.
*Narration:* the provider is called again once; if it answers, the flow is a
normal success. If it times out again, the failure surfaces (S27's rollback, or an
error line in an existing conversation).

### S29 — Conversation: send, switch, rename
*Condition:* an open conversation.
*Narration:* sending appends the message and the reply; the provider can be
changed mid-thread, keeping history and context; the title is editable from the
title itself or the list, trimmed to one bounded line, never emptied. A reply's
prose renders as Markdown and its TeX renders through KaTeX, offline; a span that
is not maths — `$5 and $10` — stays prose, and a formula that will not render falls
back to its source.

### S30 — Conversation: compaction
*Condition:* the compact action, or a send that would cross the model's budget.
*Narration:* if there is older material, it is summarised and the originals are
superseded; "Conversation compacted" is shown. If there is not, "Nothing to compact
yet". A send that would overflow compacts by itself first; if that summarising call
fails the message is sent anyway. The token counter reflects the active context.

### S31 — Prompt chips
*Condition:* saved prompts exist.
*Narration:* chips sit above the composer in a draft and in an open conversation;
tapping one sends the prompt together with whatever is already typed.

## 4. Drafts

### S32 — The drafts sheet while reading
*Condition:* the panel's list action.
*Narration:* a modal sheet lists this document's unsent drafts over the page and has
a Chats button; a tap opens a draft in the panel, a swipe removes one.

### S33 — The drafts list
*Condition:* the Drafts tab of the chats surface.
*Narration:* every unsent conversation, newest first, its excerpt shown whole; a tap
opens it, a swipe removes it. Empty state points at selection.

## 5. Chats

### S34 — Conversation list
*Condition:* the Chats screen or the panel's list.
*Narration:* newest first; each row shows title, its excerpt clamped to three
lines, metadata and provider/date. Rename and delete are per row; delete removes
the messages with it. The full screen adds a + that starts a conversation with an
optional saved prompt.

## 6. Settings

### S35 — Providers
*Condition:* the provider section.
*Narration:* one provider is selected and shows base URL, API key and model. The
model list can be fetched from the provider and filtered; Save stores it. Keys are
encrypted at rest and never displayed in clear.

### S36 — Servers
*Condition:* the servers section.
*Narration:* Calibre address, Zotero user id, API key and optional base; Save
stores them (the key encrypted).

### S37 — Saved prompts
*Condition:* the prompts section.
*Narration:* add/edit/delete, capped at twelve, each a title and a body, used as
chips. *(planned)* these become the user's private skills.

## 7. Skills *(planned — book level)*

### S38 — Browse the catalog
*Condition:* a marketplace URL is configured; the user opens Skills.
*Narration:* the catalog JSON lists skills — name, description, author, version,
scope, trigger, price. Installed ones are marked; a failing catalog shows its error
and keeps what is installed.

### S39 — Install / uninstall
*Condition:* a catalog row.
*Narration:* installing stores the skill locally; it is usable offline at once.
Uninstalling removes it and stops its future runs; results it already produced
stay.

### S40 — Apply a skill to a book
*Condition:* a book is open; the skill is installed and enabled.
*Narration:* the skill is applied at **book level** — a chosen skill is attached to
this document. A `message`-scope skill can also be run on demand from the reader; a
`conversation`-scope skill seeds the book's chat. The skill never sees more than
the book it was attached to.

### S41 — A background run
*Condition:* a skill's trigger fires (on demand, or when a draft is made;
scheduled runs later).
*Narration:* the app runs the skill against the configured provider in the
background. A finished run lands in the book's insights list and opens like a
conversation; a failed run is recorded with its error. One run per skill at a time;
timeouts use the normal retry rule; leaving the app does not cancel a run in
flight, but the user can disable a skill.

### S42 — Insights
*Condition:* runs exist for this book.
*Narration:* a per-document list shows each run (skill, time, status) and its
result. Notifications are opt-in per skill; the record is always kept.

### S43 — Paying for it *(planned)*
*Condition:* a skill is priced, or execution is a paid tier; the user has no
entitlement.
*Narration:* the skill is visible but not runnable; installing is offered, and the
flow is refused with the reason. Free skills are unaffected. Enforcement arrives
with the marketplace; the schema carries the entitlement meanwhile.

## 8. Cross-cutting scenarios

### X1 — Provider not configured
*Condition:* sending with no API key.
*Narration:* the message fails with "«Provider» is not configured — add its API
key in Settings"; a draft stays unsent (S27), an existing conversation keeps the
attempt's error line.

### X2 — No network
*Condition:* any remote call with no connectivity.
*Narration:* the call fails with its message where it was requested; the library,
Drafts and chats are unaffected.

### X3 — Missing file
*Condition:* a document's file was removed outside the app.
*Narration:* opening says the file is missing; for a remote-downloaded document,
tapping its remote row drops the stale local row and fetches it again.

### X4 — Deleted document
*Condition:* a document is deleted while its chats exist.
*Narration:* the drafts for it go with the file; conversations stay (they are
history) and still open, though their locator no longer resolves.

### X5 — Process death
*Condition:* Android kills the app.
*Narration:* on relaunch the library shows what persisted; an open book reopens at
its stored position; unsent drafts are still in Drafts, because a draft is a row,
not something held in memory.

## 9. Open decisions

- Two-finger scrolling while a selection is active: built by hand in the EPUB
  reader, still to be confirmed on a device.
- MathML in EPUBs, which KaTeX cannot typeset.
- Scheduled triggers and cloud execution for skills.
- Signing skills once they can do more than hold a prompt.
