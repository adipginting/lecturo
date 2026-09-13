# Lecturo

A reader for PDF and EPUB files that turns what you select into conversations.

You highlight a passage and it goes into **Saved**. Saved is a queue of excerpts
waiting to be asked about. Tap one and it opens a chat whose context is that
passage, frozen — the conversation stays about what you were reading instead of
drifting into the book as a whole.

Package `com.adipginting.lecturo`. Kotlin, Jetpack Compose with Material 3, one
Gradle module, MVVM. minSdk 28, compileSdk 36.1.

## What it does

**Reading.** PDF through `androidx.pdf` — the platform's own `PdfView`, with the
system selection menu. EPUB through the Readium toolkit, rendered by an
`EpubNavigatorFragment` in continuous scroll mode. Both restore the last
position; both keep the page margins to the app's own chrome.

**Selection handles, drawn by hand.** The PDF viewer knows where a text
selection's two drag handles belong — it answers a drag that starts on either
outer corner of a selection, extending it — but it does not paint them, and the
renderer that would is internal to it. So the pickers are drawn over the page in
Compose, at the same two points the viewer is watching, with the drag left
entirely to the viewer underneath: a bar standing outside the selection with a
knob at its foot, the knob being the grip. Outside, because a handle drawn on the
edge sits on the first and last letters and hides them. Their placement is the
one piece of the reader with pure logic worth testing: page units and view pixels
meet there.

**Library.** Import from the file system, download from a URL, or receive a file
from another app. Import means copy into `filesDir/documents/`, so the app owns
its library. Calibre and Zotero sit behind one `RemoteLibrarySource` interface —
Calibre over its OPDS catalog, Zotero over its Web API. Sync is manual, failures
surface per source, and deleting a document is local-only.

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

**Saved.** Selecting text offers **Save** (and **Ask AI**) in the selection menu.
PDF selections are plain text — the platform API returns text and rectangles, not
markup. EPUB selections keep their formatting: Readium runs a snippet of our
JavaScript to hand back the selected HTML, which becomes Markdown. Rows clamp to
three lines, expand on a tap, and open on a double-tap or a long-press.

**Chat.** Every conversation is fired from a saved item and keeps that excerpt as
its frozen context, shown as its first message. The provider is chosen globally
in the reader's top bar and can be changed mid-conversation; messages and context
survive the switch. OpenAI, Kimi, OpenRouter and DeepSeek share one
OpenAI-compatible client; Anthropic has its own Messages client; Copilot is
listed and stubbed, because it has no public chat API. Replies render as Markdown.
Model calls allow 30 seconds of connect, read and write — triple OkHttp's
defaults — because a completion arrives token by token (`modelApiClient`).

**A document's chat.** Conversations are per-excerpt, and a document has a
current one: the conversation fired from it that was engaged with most recently,
resolved from `conversations.docId` by `updatedAt`. The reader's top bar carries
a chat icon — between Saved and the model picker — that opens that conversation
in the panel, tinted while one is in reach and offering the chats list when there
is none. Asking about a new excerpt starts a draft that becomes the document's
next chat on its first send; the previous conversation stays in the list
untouched. A send that never gets a reply is not a chat: the conversation the
draft created is rolled back, the excerpt stays in Saved, and the draft stays in
the composer to retry. A pending draft wins over the stored conversation, but
only for its own document.

Conversations name themselves after their first message, and that name is
editable — from the list row or from the chat's own title. Both routes run
through `ConversationTitle`, which flattens whitespace, caps the length and
refuses to leave a chat nameless; auto-naming never overwrites a name the user
set.

**The panel.** A conversation opened from the reader rides over the page in a
bottom sheet instead of replacing it: the same history and the same composer,
with the book still visible and scrollable above. It is deliberately *not* the
modal sheet the Saved list uses — a scrim would stop the page being read, which
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
vm.addToSaved(text, locator)
```

Nothing downstream knows which format an excerpt came from.

**Packages.** `data/`, `library/`, `reader/`, `sync/`, `saved/`, `chat/`,
`settings/`, `ui/`. Screens observe repositories; nothing touches storage or the
network directly.

**Storage.** Room holds documents, saved items, conversations, messages and
prompts; DataStore holds preferences and encrypted keys. The saved-items table is
still named `basket_items` — renaming it would need a migration for no functional
gain.

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

## Not yet built

- **Provenance.** Nothing records where a document came from, so the app cannot
  tell a fetched book from an import — which is the prerequisite for covering
  only fetched books, and for re-downloading a book whose file is gone.
- **Remote covers.** Calibre's OPDS feed offers a thumbnail link that the parser
  discards; Zotero offers no cover images at all. Remote rows show initials.
- **Cover size.** Covers are cached at the size they arrive — a full-size jacket
  image can be over a megabyte. They should be downscaled before caching.

## What is checked, and what is not

`./gradlew :app:assembleDebug :app:testDebugUnitTest` — 85 tests, green. They
cover the pure logic and the contracts: the HTML-to-Markdown converter and the
Markdown renderer, the OPDS and Zotero parsers, the file-signature checks that
decide what a book is, the fetch that follows a download page (against a local
server), the document downloader, the model catalog, the provider clients, the
selection-handle placement, the active-chat and reader-chat-target rules, and
the cover extraction and fetch rules.

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
