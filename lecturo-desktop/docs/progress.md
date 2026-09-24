# lecturo-desktop — Progress Log

Running log of implementation sessions. Read this first when picking up work.
Companion to `plan.md` (the spec); both files are gitignored local docs.

## 2026-08-23 — Foundation (done)

**State: builds clean (`dotnet build`). Nothing user-facing yet.**

Done:
- NuGet deps added: `Microsoft.Data.Sqlite` 10.0.11, `WebView.Avalonia.Desktop` 11.0.0.1
  (pulls in WebkitGtkSharp for the later reader spike).
- `Models/`: `Document`, `BasketItem`, `SavedPrompt`, `Conversation`, `ChatMessage` (plain records).
- `Data/`:
  - `Database` — opens/creates `~/.local/share/lecturo/lecturo.db`, WAL + foreign keys on,
    migrations gated by `PRAGMA user_version` (currently at version 1:
    `documents`, `basket_items`, `saved_prompts`, `conversations`, `messages`).
  - `DocumentRepository`, `BasketRepository` (list/add/remove/clear, joins doc title),
    `ChatRepository` (prompts CRUD + conversations + messages). Sync, hand-written SQL.
- `Services/`:
  - `AppPaths` — XDG dirs (`~/.config/lecturo`, `~/.local/share/lecturo` + `documents/`).
  - `SettingsService` — `AppSettings` JSON at `~/.config/lecturo/settings.json`
    (provider choice, base URLs, models, Calibre/Zotero addresses; NO secrets).
  - `ISecretStore` + `SecretStore.Create()` factory: tries `LibSecretStore`
    (P/Invoke `libsecret-1.so.0`, org.freedesktop.Secret simple API, probes with a
    test write/read/delete), falls back to `EncryptedFileSecretStore` (AES-256-GCM
    file, 0600 key) which sets `Warning` for the settings screen to surface.
  - Secret key names: `openai-api-key`, `kimi-api-key`, `anthropic-api-key`,
    `calibre-password`, `zotero-api-key`.
- `Lecturo.csproj`: enabled `ImplicitUsings` (new files rely on it).

Known non-issues:
- 2× CA1416 warnings on `File.SetUnixFileMode` — fine, Linux-first by design.

## 2026-08-23 (cont.) — App shell (done)

**State: builds clean; app launches, creates DB (all 5 tables, user_version=1),
runs without crashing.**

- `MainViewModel`: `CurrentView` + `NavigateToCommand("Library"|"Basket"|"Chat"|"Settings")`.
- `MainWindow.axaml`: 160px nav column + `ContentControl` bound to `CurrentView`
  (ViewLocator resolves `FooViewModel` → `Views/FooView` automatically).
- Views + VMs: `LibraryView(Model)` (lists documents from repo, Refresh button),
  `BasketView(Model)` (list/remove/clear, working against repo),
  `ChatView(Model)` (placeholder + stored conversation count),
  `SettingsView(Model)` (provider combo, URLs/models, Calibre/Zotero fields,
  masked key inputs — keys only written to `ISecretStore` when non-blank,
  surfaces `ISecretStore.Warning`).
- `App.axaml.cs` composition root: EnsureDirectories → Database → repos →
  SettingsService → SecretStore.Create() → MainViewModel.
- Design-time `Design.DataContext` removed from MainWindow (MainViewModel now
  has ctor deps).

## 2026-08-28 — F2 Sources (local import done)

**State: builds clean; import pipeline verified with /tmp/lecturo_test fixtures
(multipage.pdf + sample.epub imported, copied to DocumentsDir, listed in repo;
basket add/remove round-trip OK). The two fixture docs are now in the real library.**

- `Services/LibraryService.cs`: `ImportFromFile` (validate ext → copy to
  DocumentsDir as `<guid>.<ext>` → repo row) and `ImportFromUrlAsync`
  (HttpClient; format from Content-Type → URL extension → magic bytes
  `%PDF` / ZIP `PK`; title from Content-Disposition or URL).
- `LibraryViewModel`: ImportCommand (StorageProvider file picker, multi-select),
  DownloadCommand (URL field), `ImportPathsAsync` (shared entry), IsBusy +
  StatusMessage.
- `LibraryView`: import/download UI + **drag-and-drop**. NOTE: Avalonia 12
  replaced `DragEventArgs.Data` (IDataObject) with `DataTransfer` (IDataTransfer);
  files come from `item.TryGetRaw(DataFormat.File) as IStorageItem`.
- TextBox `Watermark` is obsolete in Avalonia 12 — use `PlaceholderText`.
- URL download path written but NOT yet tested against a real server.
- "Open document" still pending the reader spike (next).

## Next up (in order)

1. **Reader spike (riskiest, timeboxed)** — WebView-Avalonia + vendored pdf.js legacy
   + `lecturo://` custom scheme; test with `/tmp/lecturo_test/multipage.pdf` and
   `sample.epub` (regenerate via `make_fixtures.py` there if wiped).
   Watch for: pdf.js needs DOM storage enabled; serve docs from the SAME origin as pdf.js.
2. **F3 sync** — Calibre (OPDS) + Zotero (Web API) behind one browse/search/download
   interface; Library screen gets Local/Calibre/Zotero tabs.
3. F4 basket bridge (in-page JS selection bubble → C#), F5 chat providers, F6 settings UI.

## 2026-09-15 — Lecture UI pass (design-matched desktop UI)

**State: builds clean; every screen verified on screen against the Penpot
`Lecturo UI` boards (which are identical to the Android repo's
`docs/design/*.png` reference).**

- **Design system** (`Styles/Tokens.axaml`, `Styles/Icons.axaml`,
  `Styles/Controls.axaml`): Material 3 light palette sampled straight from
  Penpot (`#FFFBFE` surface, `#6650A4` primary, `#EADDFF`/`#E8DEF8` containers,
  `#1C1B1F`/`#49454F` text, `#CAC4D0` outlines). Fonts embedded under
  `Assets/Fonts/` — **Plus Jakarta Sans** for UI (per user request; replaces the
  mock's Inter Tight) and **Source Serif 4** for the reading surface. Reusable
  classes: `screenTitle`, `cardTitle`, `excerpt`, `meta`, `pill`, `chip`, `fab`,
  `icon`, `text`, `card`, `providers`, `bubble`.
- **Shell** (`MainWindow.axaml`): 248px nav rail (Library / Saved / Chats /
  Settings) + content host + a **docked 400px chat panel** on the right — the
  desktop answer to the mobile bottom sheet. A dev aid reads `LECTURO_START`
  (library|saved|chats|settings|reader|reader-chat|saved-chat) to open a screen
  directly for screenshots.
- **Screens**: Library (Local/Calibre/Zotero tabs, tinted-initial covers,
  import FAB, URL download), Saved (excerpt cards, clear all, tap → draft),
  Chats (cards with excerpt + rename/delete), Settings (provider radios, fields,
  servers, saved prompts CRUD), Reader (back/title/Save-selection/Chat top bar,
  WebView reading surface, Previous/Contents dropdown/Next footer — a footer,
  never floating), Chat panel (excerpt context board, prompt chips, composer,
  send).
- **Reader** uses the official **`Avalonia.Controls.WebView` 12.1.0**
  (`NativeWebView`) — replaced `WebView.Avalonia.Desktop` 11, which only targets
  Avalonia 11 and would not load under 12.1.1. EPUB is unzipped, OPF spine
  parsed, chapters rendered as our own HTML (fonts injected from disk);
  PDF is handed to WebKitGTK's built-in viewer with `#page=N` for resume.
- **Chat**: per-excerpt conversations (migration 2 adds `document_id`, `excerpt`,
  `locator`, `provider`, `updated_at`); draft → first reply persists the
  conversation and removes the saved item, a failed send rolls it back. One
  `ChatService` covers OpenAI/Kimi/OpenRouter/DeepSeek + **Lecturo Cloud**
  (bearer = install device id) and Anthropic's Messages API. A live context
  counter estimates the next call at 4 chars/token and turns red past the
  threshold.
- **Penpot extraction** stayed the source of truth for geometry, radii, colors,
  and type scale; icons are the same Material Symbols path data the Android app
  vendors.

Known gaps (deliberate, next): in-page selection bubble (currently a
top-bar Save-selection button that reads `getSelection()` via `InvokeScript`);
Markdown rendering for replies; screenshots/crop; Calibre/Zotero browse;
real EPUB/PDF cover images (initial tiles for now).

### 2026-09-15 (cont.) — selection menu + Penpot desktop boards

- **In-page selection menu** (design screen 03): `ReaderService.BuildPage` now
  injects a floating pill (Copy / Select all / Save / Ask AI) that appears over a
  non-empty selection. The GTK WebView adapter injects `invokeCSharpAction(data)`
  into every page and raises `WebMessageReceived`; `ReaderView` parses
  `{action,text}` and calls `ReaderViewModel.SaveSelection` / `AskAboutSelection`.
  "Ask AI" saves the excerpt and opens it as a draft in the docked panel. The
  top-bar Save-selection button remains as a fallback (and for PDF, whose
  WebKit viewer isn't our DOM). Verified: JS parses (`node --check`), the menu
  renders (standalone screenshot), and the app opens the reader cleanly.
- **Penpot desktop boards**: `docs/penpot/desktop_boards.js` (with
  `step_cleanup.js` / `step_a.js` / `step_b.js` split under the plugin's 30s
  `execute_code` limit, and `create_desktop_boards.py` to run them over the MCP
  server) creates six 1440×900 boards in the `Lecturo UI` page: `D1 · Library`,
  `D2 · Reader`, `D3 · Reader · Chat`, `D4 · Saved`, `D5 · Chats`,
  `D6 · Settings`. Penpot uses absolute child coordinates — offset every child by
  the board origin or the layers land off-board. The file only ships Plus Jakarta
  Sans Regular, so the mock uses Inter Tight; the app keeps Plus Jakarta Sans.

- **Naming**: the app is renamed DocAi → Lecturo everywhere (`Lecturo.csproj`,
  `Lecturo.*` namespaces, `avares://Lecturo/...`, XDG dirs `~/.config/lecturo` +
  `~/.local/share/lecturo/lecturo.db`, docs `lecturo-plan.md` /
  `lecturo-progress.md`). Old data at `~/.local/share/docai` is not used any more.

### 2026-09-15 (cont.) — Markdown replies + reader scrolling

- **Markdown replies** (F5): `Controls/MarkdownPresenter.cs` (Markdig 1.3.2)
  renders assistant replies (and user text) as Avalonia content — paragraphs,
  bold/italic, inline code, underlined links, headings, ordered/unordered lists,
  block quotes, fenced code. The chat bubble now hosts it.
- **Reader scrolling fix**: the native GTK WebView child does not reliably
  receive wheel/keyboard input under a Wayland/XWayland session, so the page
  would not scroll. `ReaderView` now handles `EnvironmentRequested` and sets
  `GtkWebViewEnvironmentRequestedEventArgs.ExperimentalOffscreen = true`; the
  offscreen compositor adapter implements `PointerWheelInput`, which
  `NativeWebView.OnPointerWheelChanged` forwards, so scrolling works through
  Avalonia (and overlay/layering is cleaner).
- **Robustness**: `ReaderViewModel.Load` now catches a missing/unreadable file
  and shows a message instead of crashing the app (`ReaderService.Load` throws
  `FileNotFoundException`).
- Added a `LECTURO_START=conversation` dev start (opens the first conversation)
  alongside the existing screen names.

### 2026-09-15 (cont.) — EPUB rendered natively (scroll + selection)

Both GTK WebView adapters proved unreliable for input under Wayland/XWayland:
the native child did not receive wheel/keys, and the offscreen adapter spammed
`Gdk-WARNING: Event with type 11 not holding a GdkDevice` on every motion and
still did not scroll. So EPUB no longer uses the WebView at all:

- `ReaderService` converts each EPUB spine item's body HTML to **Markdown**
  (ReverseMarkdown); `img`/`script`/`style`/`link` are stripped, and empty
  leading spine items (cover/title pages) are skipped so a book opens on prose.
  `ReaderChapter` is now `(Title, Markdown)`.
- `ReaderView` renders EPUB with a native `ScrollViewer` + `MarkdownPresenter`
  (Source Serif 4, 19px, 680px measure). `MarkdownPresenter` now uses
  `SelectableTextBlock`, so text is selectable; the top-bar bookmark / send
  buttons read `MarkdownPresenter.GetSelectedText()` for **Save** / **Ask AI**.
- PDF still uses the WebView (WebKit's own viewer); the experimental offscreen
  adapter was reverted. The in-page selection-menu JS is gone with `BuildPage`
  (native selection replaces it for EPUB).
- Also restored `Program.cs`, which a stray reflection snippet had overwritten.

### 2026-09-15 (cont.) — desktop interaction spec + spec-aligned fixes

- Wrote `lecturo-interactions.md`: the mobile interaction spec translated to
  desktop/computer use (same scenario IDs; mouse + keyboard inputs; nav rail +
  docked panel; a platform-mapping table; per-scenario **[built]** / *(planned)*
  status for this port).
- Spec-aligned changes: Library empty state (S1), clearer "not a book" refusals
  (S2/S3), saved-prompt cap of twelve (S37), and a **chat model picker in the
  reader top bar** (S23) wired to the chat panel's provider.

### 2026-09-15 (cont.) — PDF renders natively too

- PDF text extraction (PdfPig 0.1.16,
  `ContentOrderTextExtractor`) gives one chapter per page. A PDF with a usable
  text layer becomes `pdf-text` and renders in the same native surface as EPUB:
  scroll, **selectable text**, page picker and Previous/Next, locator = page
  number. A PDF with no text layer (a scan) falls back to WebKit's viewer.
- `ReaderContent.Format` is now `epub` | `pdf-text` | `pdf`; `ReaderViewModel`
  gained `UsesWebView` and `HasChapters`, and the native surface / WebView and
  the chapter bar are chosen from those (not from `IsPdf`).
- Trade-off: text PDFs read as text (no page images/layout); that is the cost of
  reliable selection, which is the app's point.

### 2026-09-16 — Calibre + Zotero (S7–S12)

- `Services/RemoteLibrary.cs`: `IRemoteLibrarySource` (`Browse/Search/Download`)
  with **`CalibreSource`** (OPDS Atom; Basic auth; resolves relative hrefs;
  acquisition + `subsection` catalog links; OpenSearch `{searchTerms}`) and
  **`ZoteroSource`** (Web API attachments; `Zotero-API-Key` / bearer). One book
  with an EPUB and a PDF yields **two rows** (S12).
- Provenance: migration 3 adds `document_sources(document_id, source, remote_id,
  url, title)` + `DocumentSourceRepository`; a downloaded row is marked (S11) and
  opens its local copy without network (S10).
- Library: the Calibre/Zotero tabs gain a toolbar (Back / search box / Search /
  Sync), a status line, and a remote list with catalog chevrons and a
  downloaded/bookmark marker. `LibraryService.ImportDownloaded` sniffs the
  payload before storing.
- **Verified** against a local OPDS test server (`/tmp/opencode/opds_server.py`):
  browse listed the feed (two formats as two rows), and downloading a book saved,
  linked and opened it in the reader. Zotero is implemented but needs a live
  account to exercise.
- Dev start hooks added: `LECTURO_START=calibre|zotero|calibre-download`.

### 2026-09-17 — finishing pass (S3, S6, S17, S22, S30, covers)

- **Download gateways (S3)**: `ImportFromUrlAsync` follows HTML download pages
  (meta refresh or a `.pdf`/`.epub` link) up to three hops before judging the
  payload by its magic bytes.
- **Delete confirmation (S6)**: a modal in the Library; multi-select via a
  **Select** toggle (checkboxes) with **Delete selected**.
- **Selection actions (S17)**: right-click **Save** / **Ask AI** on the native
  reader, plus **Ctrl+S** / **Ctrl+Enter** shortcuts.
- **Screenshots (S22)**: the reader's camera captures the visible reading area to
  `chat_images/<id>.png` (`RenderTargetBitmap`), opening an image draft. Chat
  gained a context image: migration 5 adds `conversations.context_image_path`;
  `ChatService` sends it as an OpenAI `image_url` data URI or an Anthropic base64
  image on the first user message; the panel shows it in the context board.
  Scanned PDFs (WebKit surface) can't be captured yet.
- **Compaction (S30)**: migration 4 adds `messages.superseded`; the panel's
  overflow menu summarises the active turns with the current model into a
  `summary` message and marks the originals superseded (shown dimmed); only the
  summary and later turns are sent.
- **Covers (F7)**: `CoverService` extracts the EPUB cover named by the OPF (or
  `properties="cover-image"`), caches it under `covers/<id>`, and the cards show
  it with the initial tile as fallback. PDF first-page covers are not rendered.

### 2026-09-17 (cont.) — Skills (S38–S43)

- Schema v6: `skills`, `skill_runs`, and `conversations.system_prompt`.
- `SkillService`: **Browse** a marketplace JSON (`{skills:[…]}` or a bare array),
  **Install / Uninstall / Enable**, and **RunAsync** — one skill against one book.
  A run creates a conversation whose system prompt is the skill instruction and
  whose context is the running chapter, calls the provider, records a
  `skill_runs` row (`running`→`done`/`error`), and returns the conversation.
- **S43**: a priced skill installs but refuses to run ("is a paid skill; it is
  not runnable yet").
- UI: Settings gains a **Skills** section (marketplace URL, Browse, catalog with
  Install, installed list with enable/disable and Remove). The reader's skills
  button lists installed skills to run against the current book and the book's
  **insights** (recent runs with status/time).
- Verified end-to-end with a local catalog: browse listed three skills and
  installed the first (`summarize`, scope message, free, enabled). Running needs
  a configured provider key.

### 2026-09-19 — desktop rewrite against the Penpot "Lecturo Web" design

The Penpot file gained a **Lecturo Web** page: `W0 · Foundations` plus
Compact/Medium/**Expanded** variants of every screen at 1440×900. The desktop
shell was rewritten to the **Expanded** layouts:

- **Library**: cards are now a **two-column grid** (WrapPanel, 530px cards); the
  window opens at 1440×900.
- **Reader**: top bar reduced to the design's controls (Saved bookmark + chat,
  plus the camera and skills affordances kept for feature parity — the design
  shows only the two). The chapter bar **hides while the chat panel is open**.
- **Selection pill**: a floating pill over a native selection — **Copy ·
  Select all · Save · Ask AI** — per W0 Foundations; right-click Save/Ask AI and
  Ctrl+S / Ctrl+Enter remain as fallbacks.
- **Chats**: **Drafts | Conversations** tabs (W5 / mobile 06b). Drafts are the
  saved excerpts (excerpt + source + a "Draft" badge); Conversations are the
  recorded chats.
- **Settings**: added **"Current model supports images"** (per-provider vision
  override, with the default hint) and **"Compact chats above (tokens)"** (the
  context threshold); provider order now matches the design (OpenAI … Anthropic,
  Lecturo Cloud, Copilot).
- **Chat panel** header uses the design's back arrow and stacks the token counter
  over the provider.
- Stretch fix: list ScrollViewers disable horizontal overflow and content sits in
  a star Grid so cards fill the measure.

Deviations worth flagging: UI text stays **Plus Jakarta Sans** (the user's earlier
request) though the Foundations sheet lists Inter Tight; the reader keeps camera
and skills icons the Expanded boards omit; the drag-to-crop screenshot and
scanned-PDF capture are still not built.

### 2026-09-20 — crop screenshots + native scanned PDFs

- **Drag-to-crop screenshots (S22)**: the reader's camera enters a crop mode — a
  dimmed overlay with a dragged rectangle and Capture / Cancel. Capture renders
  the visible surface (`RenderTargetBitmap`), crops to the rectangle with a second
  bitmap (`DrawingContext.DrawImage`), saves a PNG and opens an image draft.
- **Scanned PDFs render natively**: when a PDF has no usable text layer, PdfPig
  extracts each page's largest embedded image (`IPdfImage.TryGetPng`) and the
  reader shows it as a native, scrollable page (`pdf-images`). The WebView is now
  used only if a PDF has neither text nor extractable images; scans are croppable
  and scroll reliably like text.
- `ReaderChapter` carries an optional `ImagePng`; `ReaderViewModel` gained
  `PageImage` / `IsImagePage`; the reading surface toggles the Markdown presenter
  and the page image.

### 2026-09-20 (cont.) — Zotero verified against a mock

- Added a configurable **Zotero API base** (`AppSettings.ZoteroBaseUrl`, the
  spec's "optional base"; the Settings → Servers section has the field) so the
  source can be pointed at a self-hosted or test server, not just
  `api.zotero.org`.
- Verified the whole Zotero path against a local mock Web API (an anonymous
  `/users/{id}/items` that 403s without a key): **Sync** listed the two
  attachments (a PDF and an EPUB), and the download opened the book in the
  reader. A dev aid `LECTURO_ZOTERO_KEY` seeds the key for that.
- Only a real account remains unexercised; the request/parse/download shape is
  covered. Test servers and their settings were removed afterwards.

### 2026-09-21 — vocabulary aligned with the glossary (no behaviour change)

Two documents came first, then the code followed them:

- **`CONTEXT.md`** (repo root) — the glossary: Document, Format, Excerpt, Saved,
  Draft, Provider, Conversation, Prompt, skill run, and ~20 more, each with the
  words to avoid. It is a glossary only; no implementation details.
- **`docs/adr/0001-native-rendering-over-webview.md`** — why the reading surface
  is native and what that costs (text PDFs reflowed, WebKit fallback
  unselectable/uncapturable).

Renames, all compile-checked:

- `BasketItem` → **`Excerpt`**, `BasketRepository` → **`ExcerptRepository`**,
  `_basket` → `_excerpts`; `SavedPrompt` → **`Prompt`**;
  `Conversation.SavedPromptId` → `PromptId`.
- **Tables keep their historical names** (`basket_items`, `saved_prompts`) —
  a rename would need a migration for no behavioural gain, so the DB stays at
  **v6**. The repository carries a note saying so.
- Settings copy "Saved prompts" → "Prompts" (the glossary gives `Saved` to the
  excerpt list alone).

Value types, replacing strings that had scattered literals:

- **`DocumentFormat`** (`Pdf` | `Epub`) replaces `Document.Format`'s string and
  its six re-derivation sites (cover, library, remote libraries, reader, library
  card, reader top bar). Stored as `pdf`/`epub` exactly as before.
- **`RenderMode`** (`Epub` | `PdfText` | `PdfImages` | `Pdf`) replaces
  `ReaderContent.Format` — the word "format" was doing two jobs; a PDF has three
  render modes but one format.
- **`RunStatus`** (`Running` | `Done` | `Failed`) replaces the run-status
  strings. Failures now store `failed`; rows written earlier hold `error` and
  still read as Failed.
- `RemoteItem.Format` is now `DocumentFormat?`: a Calibre catalog entry carries
  no format rather than an empty string.
- Skill `scope`/`trigger` are no longer shown in a card's meta line. They are
  still stored as marketplace pass-through — nothing in the app reads them, and
  the sample catalog contradicts the behaviour (`scope: "message"` on a skill
  that runs against a whole document), so they are not in the glossary either.

**Verified:** `dotnet build` is green (3 warnings, all in files this change did
not touch: `Bitmap.Save` obsolete, two CA1416). The existing database at
`~/.local/share/lecturo/lecturo.db` is v6 and holds only `pdf`/`epub` formats
and `done` run statuses, so no data migration is needed.

**Still to come** — the part that is not vocabulary: pulling the conversation
rules (draft → conversation, rollback on a failed send, compaction) out of
`ChatPanelViewModel` into something that owns them, one repository per
aggregate root, and naming the bounded contexts.

### 2026-09-21 (cont.) — the draft model, ported from mobile

The mobile app (`../lecturo`) changed its model first: **there is no Save any
more — asking about a passage makes a draft, and a draft is a conversation whose
first message has not been sent** (its Room migration 10 → 11). The desktop had
the old shape: a saved queue (`basket_items`), a Saved screen, a selection pill
with Save, and a conversation created at send time with a rollback when the model
never answered. This entry is that port.

**Vocabulary first.** `CONTEXT.md` was rewritten to the shared vocabulary —
Document, Library, Source, Excerpt, Draft, Conversation, Message, Locator,
Frozen, Compact, Provider, Destination, Provenance, Skill, Insight — with a
marked *desktop differences* section for the docked Panel, Render mode, Lecturo
Cloud, skill runs (which desktop records as conversations rather than Insights)
and the Summary message role. `docs/adr/0002-drafts-are-unsent-conversations.md`
records the decision and what it costs.

**Three projects now, not one.**

- `Domain/Lecturo.Domain.csproj` — every model type and its rules:
  `Conversation` (`IsDraft`, `Send`, `Rename`, `Draft`), `Excerpt` (text *or*
  image — the two columns a conversation used to carry separately are now one
  value), `Message` + `MessageRole`, `ConversationTitle` (the sieve: 40 from a
  first message, 60 typed, whitespace collapsed, nothing cut through an emoji),
  `Document`, `DocumentFormat`, `RenderMode`, `RemoteItem`, `Provenance`,
  `Prompt`, `Skill`, `RunStatus`. It references nothing — no SQLite, no HTTP, no
  Avalonia.
- `Tests/Lecturo.Tests.csproj` — xunit, 25 tests: the naming rules, the
  conversation lifecycle, the stored values (including that a run recorded as
  `error` before this change still reads as `Failed`), and migration 7.
- `Lecturo.csproj` — the app, which excludes `Domain/` and `Tests/` from its
  compile globs and references the domain project.

**Schema v7.** `conversations.sent_at` added; `basket_items` rows copied into
`conversations` as drafts (`title = 'New conversation'`, `sent_at NULL`) and the
table dropped; `saved_prompts` → `prompts`; `conversations.saved_prompt_id` →
`prompt_id`. Existing conversations were all sent — under the old model a row
survived only if a reply landed — so each takes its last update as `sent_at`.
Worth flagging to the mobile side: **their migration omits that step**, so every
conversation that existed before their 10 → 11 reads as a draft. The desktop test
pins the correct behaviour.

**What left the app.** `ExcerptRepository`, the `basket_items` table, the Saved
screen and its nav item, `ReaderViewModel.SaveSelection`, `AskAboutExcerpt` and
the rollback branch in the chat panel. A failed send now simply leaves the draft
unsent, because nothing was created at send time to undo.

**What changed on screen.** The selection pill is Copy · Select all · Ask AI
(Save and `Ctrl+S` retired; `Ctrl+Enter` and the right-click menu still ask). The
reader's top bar carries one chat icon — tinted while the document has a draft or
a conversation — whose target is the newest draft, else the most recently
engaged sent conversation, else the chats list. Chats → Drafts lists unsent
conversations newest first with per-row discard and no clear-all; the Saved
screen is gone. Skill runs create *sent* conversations with no locator, since a
run is about the document rather than a position in it.

**Verified.** `dotnet build` green; `dotnet test Tests` 25/25; migration 7 tested
against a database seeded at v6 with a saved item, a conversation and a prompt.
The live database (`~/.local/share/lecturo/lecturo.db`) is still v6 and will
migrate on the next launch — it was backed up to `/tmp/lecturo-pre-v7-backup.db`
first, and it holds no saved items to move.

**Deliberately not ported** from mobile, because they are their own projects:
KaTeX maths, the model-window budget table, and the usage-calibrated token
counter (desktop still estimates at 4 characters per token against a single
editable threshold).

### 2026-09-21 (cont.) — the context budget and the usage-calibrated counter

The last two things the desktop was missing from mobile's chat, both ported:

- **`Domain/ModelWindow`** — the table of known context windows, the budget as
  `(window - 4000) * 0.85` clamped to 2 000–32 000, 8 000 for a model we do not
  know, and a reader's own number winning outright. Mobile's `contextBudget` and
  its `ModelWindowTest` cases, ported with their assertions.
- **`Domain/ContextCounter`** — the counter now starts from the prompt tokens a
  provider actually reported and adds only what arrived since, falling back to
  estimating the payload at four characters per token until a reply reports
  usage. `ChatService.CompleteAsync` returns a `Completion` (text + token counts,
  parsed from OpenAI's `usage.prompt_tokens` and Anthropic's `usage.input_tokens`),
  schema **v8** stores them as `last_prompt_tokens` / `last_call_at`, and
  compaction clears them because the summary replaced the payload they described.
  The counter reads `~4.2k / ~32k`.
- Settings' "Compact chats above" is now what mobile made it: a *hand-set* limit.
  Left at 8 000 it means "ask the model"; the helper text says so.

**Also fixed in the mobile repo** (`../lecturo`, needs no desktop change): its
`MIGRATION_10_11` left every conversation that already existed with a null
`sentAt`, so chats answered weeks earlier were listed under Drafts. Added
`MIGRATION_11_12` (version 11 → 12) which marks every conversation that has
messages as sent, dated by its last update; a draft with no messages stays a
draft. `./gradlew :app:compileDebugKotlin` is green with the change.

**Verified:** build green, `dotnet test Tests` 36/36, migration 8 exercised on a
copy of the real database, and the app launched into a document's chat with no
exception.

**Maths, ported the same way mobile does it.**

- `Domain/MathSplit` — the splitter, with mobile's rules and its nine test cases:
  `$$…$$` and `\[…\]` are display, `\(…\)` is inline, and `$…$` counts only on
  one line with no space just inside and a maths-only character within, so
  `$5 and $10` stays money. Backtick code is never maths.
- `Services/MathsHtml` — the page the view is handed: prose rendered by Markdig
  (the desktop already had it) with each formula left as TeX in a `data-tex`
  attribute, plus the bundled KaTeX and the height-reporting script.
- `Controls/MathsView` — one WebView, only for messages that actually carry a
  formula, sized by the height KaTeX reports back over the JS bridge
  (`invokeCSharpAction` → `WebMessageReceived`). A reply of plain prose never
  pays for it.
- **KaTeX 0.16.22** is copied from the mobile repo's own bundle
  (`app/src/main/assets/katex/`) into `Assets/katex/` — no download, and both
  apps typeset with the same build. Its MIT licence and provenance are recorded
  in `Assets/katex/README.md`.
- A Settings toggle, "Typeset formulas", turns it off; off, the TeX shows as
  written.

Six tests cover the page builder too — prose escaping, the formula element,
money staying money, the assets surviving the build, and TeX quotes that cannot
break out of the attribute.

**Looked at, not just compiled.** A page built by `MathsHtml` for a sample reply
was rendered by headless Chrome against these assets and inspected: the inline
formula, the display integral, the Greek letter and the inline fraction all
typeset with the KaTeX fonts, and `$5 and $10` stayed prose. The page lives at
`/tmp/katex-preview/index.html` for as long as `/tmp` lasts.

### 2026-09-22 — the application layer, over ports (DDD sweep)

The last "Still to come" from the vocabulary pass, done: the chat rules left the
view model, and the desktop got the layering the web already had.

**Three projects become five.** `Application/Lecturo.Application.csproj`
(use cases + ports, references `Domain` only) and
`Infrastructure/Lecturo.Infrastructure.csproj` (adapters, references both). The
app references both; only `App.axaml.cs` names an adapter. `Lecturo.csproj` now
excludes `Application/**` and `Infrastructure/**` from its compile glob as well.

**Ports, consumer-owned.** `IConversationRepository`, `IPromptRepository`,
`IChatClient`; `IDocumentRepository`, `IFileStore`, `IDocumentFetcher`;
`IRemoteLibrarySource`, `IProvenanceRepository`; `ISkillRepository`,
`ISkillCatalog`; `ISettingsStore`, `ISecretStore`. Adapters implement them:
SQLite repositories, `FileStore`, `HttpDocumentFetcher`, `HttpSkillCatalog`,
`ChatClient` (was `ChatService`), `CalibreSource`/`ZoteroSource`,
`JsonSettingsStore`, the libsecret/AES secret stores.

**Use cases.** `Application/Chat/ConversationService` owns send, name, draft →
conversation, compaction and the system prompt; `Application/Library/
LibraryService` owns import/download/delete; `CatalogService` owns the remote
download and provenance; `Skills` `SkillService`; `Settings` the ports.
`ChatPanelViewModel` lost ~90 lines and the `BuildSystemPrompt`/`ActiveHistory`
helpers.

**Tests: 36 → 75.** New fakes-only suites (`ConversationServiceTests`,
`LibraryServiceTests`, `CatalogServiceTests`, `SkillServiceTests`) cover the
flows that had no seam before — the send/name/fail/compact life of a
conversation, payload-sniffed imports, remote provenance, paid-skill refusal —
with no database and no network. `ArchitectureTests` fails if a view model or the
application layer references Infrastructure.

**Trap worth remembering.** `Lecturo.Application` shadows `Avalonia.Application`
inside the `Lecturo` namespace, so `Application.Current` binds to the wrong
symbol; write `Avalonia.Application.Current` (`MainViewModel`, `LibraryViewModel`
were fixed).

**Green at every step** — one vertical slice at a time (Chat, Library, Catalog,
Skills, Settings), `dotnet build` and `dotnet test Tests` run after each.
Recorded in `docs/adr/0003-application-layer-over-ports.md`.

**Still in the app, on purpose:** `ReaderService`, `CoverService` and
`MathsHtml` are rendering helpers, not persistence; they keep their home until
the reading surface changes engines.

### 2026-09-22 (cont.) — Windows build, and the UI font made real

**Windows is a publish, not a port.** `dotnet publish -c Release -r win-x64`
produces `Lecturo.exe` with the Win32 Avalonia natives. The Linux-only pieces are
now behind `OperatingSystem` checks (not `#if`s):

- `SecretStore.Create` only tries the libsecret P/Invoke on Linux; everywhere
  else the AES-encrypted file store is used, and `EncryptedFileSecretStore` no
  longer calls `File.SetUnixFileMode` on Windows (a `RestrictToUser` guard).
- `AppPaths` uses `%APPDATA%` / `%LOCALAPPDATA%` on Windows, XDG on Linux.
- The rare PDF fallback and KaTeX maths use the platform WebView (WebKitGTK /
  WebView2); EPUB and text-PDFs are native on both.

A clean `-t:Rebuild` also caught a latent XAML binding the incremental Debug
build had cached: `SettingsView.axaml` still pointed `svc:ProviderInfo` at
`Lecturo.Services` after `ProviderInfo` moved to `Lecturo.Application.Settings`.

**Plus Jakarta Sans is now the real default.** The styles already set `UiFont`,
but `Program.cs` called `.WithInterFont()`, which made Avalonia's default Inter —
so anything unstyled (and every popup) rendered in Inter. Replaced it with a
`FontManagerOptions.DefaultFamilyName` of `avares://Lecturo/Assets/Fonts#Plus
Jakarta Sans`, and a `Window` style for the same. The reading surface keeps
Source Serif 4.

## Decisions & conventions

- No DI container: `App.axaml.cs` is the manual composition root.
- ViewLocator convention: `FooViewModel` → `Lecturo.Views.FooView` (auto data template).
- CommunityToolkit.Mvvm new-style partial properties: `[ObservableProperty] public partial string X { get; set; }`.
- Timestamps in SQLite: `DateTime.UtcNow.ToString("O")`, parse with `RoundtripKind`.
- Progress saved to THIS file, not git commits, per user request (2026-08-23).
  (Commit eb1f67b contains the foundation; .gitignore now covers bin/ obj/.)
