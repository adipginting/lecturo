# Lecturo

A reader for PDF and EPUB files that turns what you select into conversations.

You highlight a passage and it goes into a **draft**. A draft is an unsent
conversation — a row with no `sentAt` yet — waiting to be asked about. Tap one and
it opens a chat whose context is that passage, frozen — the conversation stays
about what you were reading instead of drifting into the book as a whole.

Package `com.adipginting.lecturo`. Kotlin, Jetpack Compose with Material 3, one
Gradle module, MVVM. minSdk 28, compileSdk 36.1.

## What it does

**Reading.** PDF through `androidx.pdf` — the platform's own `PdfView`, with the
system selection menu. EPUB through the Readium toolkit, rendered by an
`EpubNavigatorFragment` in continuous scroll mode. Both restore the last
position; both keep the page margins to the app's own chrome. The EPUB chapter
controls (Previous, Contents, Next) sit in a solid footer *below* the page, not
over it — an earlier version floated them transparently over the text, where
they could cover the words being read.

**Selection handles, drawn by hand.** The PDF viewer knows where a text
selection's two drag handles belong — it answers a drag that starts on either
outer corner of a selection, extending it — but it does not paint them, and the
renderer that would is internal to it. So the pickers are drawn over the page in
Compose, at the same two points the viewer is watching, with the drag left
entirely to the viewer underneath: a bar standing outside the selection with a
knob at its foot, the knob being the grip. Outside, because a handle drawn on the
edge sits on the first and last letters and hides them. Their placement is the
one piece of the reader with pure logic worth testing: page units and view pixels
meet there. A selection can run past a page boundary, so each end carries the page
it belongs to and is placed against that page's frame; an end whose page has
scrolled away is simply not drawn, rather than dragging the other with it. Back
dismisses a selection before it does anything else — before crop mode, before the
panel, long before leaving the book.

**Scrolling and swiping.** The EPUB reader scrolls continuously; a sideways swipe
turns the chapter only when it is deliberate — a fifth of the width, clearly more
horizontal than vertical — and never while text is selected. With a selection up,
Chromium stops the page moving at all, so a two-finger drag is turned into a scroll
of the navigator's web view by hand.

**Library.** Import from the file system, download from a URL, or receive a file
from another app. Import means copy into `filesDir/documents/`, so the app owns
its library. Calibre and Zotero sit behind one `RemoteLibrarySource` interface —
Calibre over its OPDS catalog, Zotero over its Web API. Sync is manual, failures
surface per source, and deleting a document is local-only. A download remembers
where it came from — source, the remote item's id, the URL — so the remote list
can mark what is already local with a check and "Downloaded", and a tap opens the
local copy instead of fetching it again; if the file has gone, the stale row is
dropped and the item is fetched anew. Calibre lists one row per format, because an
EPUB and a PDF are two documents and a mark on one must not speak for the other.

**A book is what its bytes say it is.** A book URL does not always answer with
the book. Standard Ebooks answers with a "Your Download Has Started!" page whose
meta refresh carries the same URL plus `?source=download`, and an earlier version
of this app saved that page under an EPUB name — leaving a library row that
showed `AssetRetriever$RetrieveError$Reading@f918e3f` where its book should have
been. Now the first bytes decide: `%PDF-`, or a ZIP container, or it is not a
book. A download page is followed to the file it stands in for, at most three
hops, with credentials dropped when a hop crosses hosts. A payload that is still
not a book is refused by name, and a file picked from disk is checked the same
way before it is copied in.

**Drafts.** Selecting text offers **Ask AI** — the whole action. PDF selections
are plain text; EPUB selections keep their formatting, Readium handing back the
selected HTML to become Markdown. Asking makes a **draft**: a conversation whose
first message has not been sent, a row with a null `sentAt` that survives restarts
and waits beside the conversations it will join. A screenshot makes a draft too.
Nothing is "saved" any more — the old saved queue is where drafts came from, and
its rows became drafts when the table changed. The first reply flips the draft,
`sentAt` being set only after the model answers, so a timeout leaves the excerpt
in Drafts rather than in a conversation that never happened. Drafts and
conversations are two tabs of one surface, and a conversation's excerpt is shown
whole in the chat window, unclamped.

**Chat.** Every conversation is fired from a draft and keeps that excerpt as
its frozen context, shown as its first message. The provider is chosen globally
in the reader's top bar and can be changed mid-conversation; messages and context
survive the switch. OpenAI, Kimi, OpenRouter and DeepSeek share one
OpenAI-compatible client; Anthropic has its own Messages client; Copilot is
listed and stubbed, because it has no public chat API. Replies render as Markdown.
Model calls allow 30 seconds of connect, read and write — triple OkHttp's
defaults — because a completion arrives token by token (`modelApiClient`).

**Mathematics.** The system prompt asks every provider for TeX — `\( … \)` inline,
`\[ … \]` display — so formulas arrive delimited rather than guessed at. The app
splits a reply into prose and maths itself, conservatively: an inline `$…$` counts
only when it is one line with no space inside and carries a maths-only character,
which keeps `$5 and $10` as money. A message with maths is rendered by KaTeX,
bundled in the APK so it works offline; prose keeps the cheap Compose renderer,
and a formula that will not render falls back to its source. A Settings switch
turns the whole thing off.

**Context, counted and compacted.** The chat's top bar carries a small counter
estimating how much would be sent on the next call: the excerpt plus the active
messages plus whatever is typed, at four characters to a token, calibrated by the
`usage` both provider clients now parse from each reply — after a call, the
counter starts from the prompt tokens the provider actually counted and adds only
what is new. The budget comes from the model: a bundled table of known windows,
less room for the reply and a safety margin, floored and capped, with the Settings
number as an override for a model we do not know. When a turn would cross it the
app compacts on its own, asking the conversation's current model to summarize the
active messages into one summary message; the originals are marked `superseded`
and stay on screen behind a divider, but only the summary and later messages are
sent (`ApiHistory`). A failed compaction is swallowed — the message goes anyway,
because losing a turn to save tokens is a bad trade. The excerpt is never
compacted: frozen means frozen.

**A document's chat.** Conversations are per-excerpt, and a document has a current
one: the sent conversation fired from it that was engaged with most recently,
resolved from `conversations.docId` by `updatedAt`. The reader's top bar carries
one chat icon, tinted while there is anything here, and a camera icon opens crop
mode. The icon opens the document's newest **draft** in the panel, failing that its
active conversation, failing that the chats surface. Asking about a new excerpt
makes another draft; the previous conversation stays in the list untouched. A draft
is only a conversation once a reply lands, so a send that times out leaves it in
Drafts rather than in the chat list.

Conversations name themselves after their first message, and that name is
editable — from the list row or from the chat's own title. Both routes run
through `ConversationTitle`, which flattens whitespace, caps the length and
refuses to leave a chat nameless; auto-naming never overwrites a name the user
set.

**The panel.** A conversation opened from the reader rides over the page in a
bottom sheet instead of replacing it: the same history and the same composer,
with the book still visible and scrollable above. It is deliberately *not* the
modal sheet the drafts list uses — a scrim would stop the page being read, which
is the point of the panel. Collapsing puts it away and keeps nothing itself: the
way back is the document's chat icon, which re-derives the draft or the current
conversation from the same sources the panel opened from. Being where the typing
happens, the panel is the one surface that must clear the phone: its content is
padded by `WindowInsets.safeDrawing`'s bottom, and the scaffold itself takes
`imePadding`, so the composer rides above the navigation bar and above the
keyboard.

**Prompts.** Saved prompts are CRUD-able in Settings, capped at twelve. They
appear as chips above the composer in both the draft screen and an open
conversation, and a tap sends: whatever is already typed plus the prompt's text.

**Screenshots.** A conversation can start from a picture of the page instead of
an excerpt. The reader's camera icon dims the page, stands a rectangle with grab
handles over it — the geometry is pure and tested (`CropRect`), the drawing is
`CaptureOverlay` — and copies the confirmed rectangle out of the window with
`PixelCopy`, because the page is the platform's own rendering surface and only
the compositor has its pixels. The crop is saved downscaled into
`filesDir/chat_images/` and opens a draft with it attached; nothing sends
itself. Such a conversation's frozen context is the image
(`conversations.contextImagePath`), attached to the first user message of every
payload. The whole feature is gated on the selected model reading images at all
(`VisionCapability`: defaults by model family, a per-model override in
Settings): the icon dims and explains itself rather than failing at send time.

**Lecturo Cloud.** Testers should not have to obtain an API key. `lecturo-relay/` —
a small server at the repository root, its own Gradle project beside the clients —
holds one upstream key
and meters what each install spends: an OpenAI-shaped pass-through that replaces
the request's model with its own, counts the tokens the upstream reports, and
answers `429` in OpenAI's error shape (so the app shows the message as-is) when
a device's free day is spent. The app sees it as one more provider (`lecturo`)
whose "key" is the install's anonymous device id — generated once, stored in
DataStore, and the thing a future account or paid plan hangs off. The server is
deliberately dumb: counters per device per day in one JSON file, no message
logging, no business logic in the app (the single `free` plan lives in
`lecturo-relay/src/main/kotlin/lecturo/relay/Quota.kt`).

**Settings.** Provider selection, per-provider keys and models, Calibre and
Zotero addresses, saved prompts. API keys are AES/GCM-encrypted through the
Android keystore; the ciphertext lives in DataStore alongside everything else.

**Covers.** The library shows a cover per document: an EPUB's cover image read
from its OPF, or a PDF's first page rendered small. They are cached as files in
`filesDir/covers/`, one per document, so they survive restarts. Books whose cover
can't be determined show their initial. Remote rows still show initials — the
cover work for Calibre and Zotero is described under *Not yet built*.

## How it is put together

**Two renderers, one seam.** PDF and EPUB have nothing in common at the rendering
layer, and that is deliberate: the platform's viewer gives hardware rendering and
a supported selection menu, Readium gives EPUB features we would otherwise keep
rebuilding. They meet at one call:

```kotlin
vm.createDraft(text, locator)
```

Nothing downstream knows which format an excerpt came from.

**Packages.** `:domain` holds the vocabulary and invariants as pure Kotlin, with no
Android or Room dependency — the compiler enforces that boundary (see
`docs/adr/0001-domain-module.md`). It exposes **ports** — `Conversations`,
`Prompts`, `Documents`, `Skills` — returning domain types only. The app's
`data/` implements them as Room adapters that map rows to domain objects at the
seam; no Compose screen or ViewModel imports a Room type. `AppContainer` is the
composition root that wires the adapters to the ports. The other app packages are
`library/`, `reader/`, `sync/`, `chat/`, `settings/`, `ui/`.

**Storage.** Room holds documents, conversations, messages and prompts — a draft
is a conversation with a null `sentAt`; DataStore holds preferences and encrypted
keys. The old saved-items table (`basket_items`) became drafts in migration
10 → 11 and was dropped in 12 → 13: every saved excerpt is an unsent conversation,
and no table carries the name "saved".

**Positions are opaque strings.** PDF stores a 1-based page number; EPUB stores
Readium's serialized `Locator` JSON. Nothing else reads that column, so either
engine could be replaced without a migration. A position saved as a spine href —
the format an earlier reader wrote — is matched against the publication's reading
order and converted on open.

**Icons.** The three glyphs `material-icons-core` doesn't ship — bookmark, chat
bubble, memory chip — are vendored Material Symbols path data in
`ui/theme/Icons.kt`, attributed there. The dependency that carries every icon
isn't worth three.

**Constraints worth remembering.** PDF text selection needs the S SDK extension
at level 13 or better: the library renders below that, but the selection menu
does not appear, so PDFs degrade to reading-only on older devices. Three of
Readium's transitive Compose and Fragment dependencies are pinned in
`app/build.gradle.kts` because they demand compileSdk 37, which this SDK doesn't
have installed.

Readium also decides how the app handles the Android lifecycle. `MainActivity`
declares `android:configChanges` for orientation, theme, font scale and locale,
because an activity recreated while a book is open would restore a navigator
fragment that Android cannot instantiate and Readium cannot accept — the app
crashed on rotation. Process death takes the opposite route: a restored activity
installs Readium's dummy factory so the restore can finish, then drops the
restored fragments before they resume, leaving the reader to open its book again
from the position kept in the library.

## Context

A chat in Lecturo is about a passage, not a book. That is the whole idea, and
most of the design follows from taking it seriously.

When you save a passage and ask about it, the conversation's system prompt is
that passage plus whatever standing instruction you picked, and nothing else.
The history is the messages that compaction has not put away. That is a small
context on purpose. Hand a model the whole book and it stops answering about the
line you chose and starts answering about the book — a different product, and a
worse one.

A passage cannot answer every question, though. "What is this book about" and
"how does this chapter follow the last" want the book, and no excerpt will do.
So a book's context is built in layers instead of pasted. Structure first: title,
author, table of contents, where you are. Then summaries — one per chapter, and a
book summary made out of them, cached per book. Then the chapter in front of you.
Finding one line, "where does it say X", is retrieval, and it comes later,
because the first three layers answer most of what a reader asks.

The building of those layers is work for skills. A skill is a prompt with a name,
applied to a book rather than a line, run in the background on the provider you
configured. A book-level skill is handed the book summary, the table of contents,
the chapter in view and, when it came from one, the passage. The run records what
it saw, so a bad answer can be diagnosed instead of guessed at. Nothing is read
that you have not opened, and nothing runs until you ask.

Conversations get long and models have windows, so the app compacts. Before a
turn that would cross the model's budget, the older messages are summarised and
superseded: the summary stays in the thread, the originals stay visible and are
never sent again. A summary keeps the passage it came from and anything you said
mattered. It runs on the same provider, and if it fails your message goes anyway
— losing a turn to save tokens is a bad trade.

The budget comes from the model. We ship a table of known windows, let you
override a model we do not know, and compute the budget as the window minus room
for the reply and a safety margin: never under eight thousand tokens, never over
thirty-two thousand, because a window of a million is an invitation to a bill.
One budget drives compaction, the chapter a skill may take, and the book context
alike. Three constants would drift.

Change the model mid-conversation and the context is derived again for the new
one: a model that cannot see images is not sent images, and the chat says where
its context goes — your key or the Lecturo relay — because a reader should not
have to guess.

Retrieval will come. When it does, embeddings run on the device by preference, so
a book you own does not leave your phone to become searchable, with the relay as
the fallback for phones that cannot run a model locally. It will spend the same
budget as everything else.

Two rules earn tests and have them: how a system prompt and a history are
assembled, and how a book's context is assembled from its layers. Both are pure
functions. The model call is only a contract.

## Not yet built

- **Running skills, and book context.** Skills can be installed, listed, removed
  and *run*: from the reader, a book's applied skills run on demand and their
  results land as insights. A run reads the book's own text, which today means
  EPUB only — the spine's XHTML, flattened to Markdown (`library/BookText.kt`),
  giving a book-level skill something real to read. A PDF has no extractor yet.
  Book context as proper layers — structure, chapter summaries, a book summary —
  is still unbuilt, so a long book is sent whole; retrieval follows it.
- **MathML in EPUBs.** KaTeX typesets TeX, not MathML: an EPUB that ships `<math>`
  renders its source. Open decision: leave the source, or bundle a MathML engine.
- **Zotero covers.** Calibre covers are fetched now; Zotero's API offers none, so
  those rows show an initials placeholder.
- **Verification gaps.** Two-finger scrolling while a selection is up is built but
  unconfirmed on a device (adb can't inject multi-touch). Downloaded-marking needs
  a configured Calibre/Zotero server to be seen.
- **Design.** The Penpot Skills board exists on Mobile only; Desktop and Web have
  no Skills board yet.

## What is checked, and what is not

`./gradlew :app:assembleDebug :app:testDebugUnitTest :domain:test` — 156 app
tests and 40 in `:domain`, green. They
cover the pure logic and the contracts: the HTML-to-Markdown converter and the
Markdown renderer, the maths splitter and the markdown-to-HTML the maths view is
handed, the OPDS and Zotero parsers and Calibre's one-row-per-format rule, the
file-signature checks that decide what a book is, the fetch that follows a download
page (against a local server), the document downloader, the model catalog, the
provider clients including their token-usage parsing and vision-capable image
payloads, the context counter, the model-window budget and the compaction history
rules, the vision-capability matrix, the crop rectangle's geometry, the image-decode
subsample, the selection-handle placement including a selection across pages, the
active-chat, latest-draft and reader-chat-target rules, the EPUB chapter-swipe
threshold, the downloaded-remote-item matcher, and the cover extraction and fetch
rules. The conversation aggregate in `:domain` pins the life of a chat — a draft
is sent once, named from its first message, never left nameless — and its PDF
page rule decides where a book opens when the stored position is unreadable. The
relay is checked the same way —
`cd ../lecturo-relay && ../gradlew test`, 10 tests over quota rules, the store's
durability, and config parsing — and has been driven end-to-end against a fake
upstream: quota denial, model override, per-device counters.

Chat has also been driven against a real provider rather than a mock: the device
holds a DeepSeek conversation, and no endpoint override is configured.

Deliberately not tested, because they need a rendered pixel or a real engine:
Readium rendering, `PdfView`'s own selection and touch behaviour, Compose layout,
sheet and drag gestures. Those get driven on the emulator and looked at.

Every bug that reached a device in this project's recent history was in that
second category or in an untested seam — a cover with literal quotes around it, a
library row showing raw JSON, a gesture that never fired. That is the argument for
pushing the boundary outward whenever a piece of it becomes cheap to test.

## Running it locally

The emulator is `~/Android/Sdk/emulator/emulator -avd Small_Phone -no-snapshot-save`,
started detached — `nohup … &` — because one launched from a shell dies with the
shell that started it. It reaches the host as `10.0.2.2`, and a cold boot takes
about a minute: `adb shell getprop sys.boot_completed` returns 1 when it is ready.
To exercise a download, serve a fixture from the host — `python3 -m http.server 8777`
in any directory holding a PDF or EPUB — and paste `http://10.0.2.2:8777/<file>`
into the app's download field. Standard Ebooks is the useful live case, because it
hands out exactly the download page that the fetch logic exists to follow.

If Gradle's Kotlin or AAPT2 daemons fail to spawn with "Failed to exec spawn
helper", stop them — `./gradlew --stop`, and kill stale `KotlinCompileDaemon`
processes — then build again.
