# lecturo — Architecture & Status

An Android document reader: PDF/EPUB in a unified WebView shell, with a
text-selection "basket" and a multi-provider chatbot for learning from what
you collected.

Package: `com.adipginting.lecturo` · Kotlin, Jetpack Compose (Material 3),
single Gradle module, MVVM.

## Feature plan (goal order)

| # | Feature | Status |
|---|---------|--------|
| 1 | Reading: PDF (pdf.js) + EPUB, scrolling, position restore | Done, verified on emulator |
| 2 | Sources: storage import (SAF), web download (URL field + share target) | Done, verified on emulator |
| 3 | Library sync: Calibre (OPDS) + Zotero (Web API), `RemoteLibrarySource` | Done, verified on emulator |
| 4 | Basket: selection → JS bridge → Room; cart-style screen; tap-to-fire queue | Done, verified on emulator |
| 5 | Chatbot: OpenAI/Kimi (OpenAI-compatible), Claude (native), Copilot stub | Done, verified on emulator (mock LLM endpoints) |
| 6 | Settings: providers, servers, saved prompts; Keystore-encrypted keys | Done, verified on emulator |
| 7 | Ask AI from selection: opens draft chat with selection as context; global model chooser in reader top bar | Done (aligned to draft flow); emulator re-check pending |
| 8 | Basket queue → draft chat: tap row → draft screen (context banner, prompt chips), persist on first send, frozen `contextText` (DB v5) | Implemented, build green; emulator UX check pending |

## Settled architecture

From a grilling session; each decision numbered as asked.

- **Q1b — Unified WebView rendering.** PDF via bundled pdf.js 6.2.108 legacy
  dist (`app/src/main/assets/pdfjs/`, Mozilla stock `viewer.html`). EPUB via
  unzip + OPF parse, served with `WebViewAssetLoader`. One DOM-based
  text-selection pipeline for the basket instead of two.
- **Q2a — Single module**, feature packages: `data/`, `library/`, `reader/`,
  `sync/`, `basket/`, `chat/`, `settings/`.
- **Q3a — App-private storage.** Import = copy into `filesDir/documents/`;
  EPUBs unpack to `filesDir/epub/`.
- **Q4a — Room + DataStore.** Documents, basket, conversations in Room;
  settings in DataStore.
- **Q5a — `RemoteLibrarySource` interface** (`browse/search/download`) with
  Calibre-OPDS and Zotero implementations.
- **Q6a — OkHttp + kotlinx.serialization (JSON) + XmlPullParser (OPDS/OPF).**
- **Q7b — `ChatProvider` interface.** Shared OpenAI-compatible client (OpenAI,
  Kimi), native Anthropic Messages client, Copilot stubbed (no public API).
- **Q8a** Selection: WebView native handles + action-mode items "Add to
  basket" and "Ask AI", `@JavascriptInterface` bridge. "Ask AI" also adds
  the excerpt to the basket, then opens a dialog to pick a saved prompt or
  a one-off custom prompt (stored on the conversation's `customPrompt`
  column, DB v4), creates the conversation, and navigates to it
  (superseded by Q24a). The provider is NOT chosen per prompt: the reader
  top bar carries a model chooser button (current provider + model label)
  opening a picker that sets the global `ChatSettings.selectedProvider`;
  new conversations are stamped with it and stay locked.
- **Q9a** EPUB: manual unzip + OPF parse (no EPUB library), zip-slip guarded.
- **Q10a** Stock pdf.js viewer; app chrome in Compose around the WebView.
- **Q11b** Basket item = `{text, docId, locator, timestamp}`.
- **Q12a** Chat context: inject all basket items into the system prompt
  (superseded by Q22a).
- **Q13c** Saved prompt chosen per conversation at start; **Q15a** locked
  thereafter. **Q16a** Conversations persisted. (The locked prompt now
  applies to legacy conversations only; new basket-fired conversations use
  composer chips instead — see Q23a.)
- **Q14b** Blocking chat responses first; interface streaming-ready.
- **Q17a** Manual sync only. Sync errors surface per-source, never block.
- **Q18c** Web download via in-app URL field **and** share-target intent.
- **Q19b** API keys AES-encrypted (AndroidKeyStore), ciphertext in DataStore.
- **Q20** Last-read position persisted (PDF page / EPUB spine href), restored
  on reopen.
- **Q21a — Basket is a queue of pending asks.** Tapping a basket row opens a
  **draft conversation** screen (`chat/new`); the excerpt travels via the
  in-memory `DraftChat` holder (long texts stay out of nav routes). Nothing
  persists until the first send; backing out keeps the item in the basket. On
  first send: conversation created (global provider, `promptId`/`customPrompt`
  null), user message + reply stored, source basket item deleted, draft route
  popped to `chat/{id}`.
- **Q22a — Frozen per-conversation context** (supersedes Q12a):
  `conversations.contextText` (DB v5, `Migration(4,5)`) holds the fired
  excerpt; `buildSystemPrompt` = locked prompt (legacy conversations) +
  `contextText`. Whole-basket injection removed.
- **Q23a — Saved prompts are composer chips.** In the draft screen they render
  as a horizontally scrollable chip row above the composer; tapping appends
  the prompt body on a new line (stackable, editable before send). Nothing is
  locked as system prompt for new conversations.
- **Q24a — Ask AI aligned to the draft flow** (supersedes the dialog part of
  Q8a): no dialog, excerpt not added to the basket; opens the same draft
  screen.
- **Q25a — Cache icon.** Library top-bar basket glyph → hand-written
  `Icons.Filled.Cache` (`ui/theme/Cache.kt`, stacked ellipses; no
  icons-extended dependency). Feature stays named "Basket".

## Serving model

All reader content is same-origin under
`https://appassets.androidplatform.net/` via `WebViewAssetLoader`:

- `/assets/pdfjs/...` → bundled pdf.js (AssetsPathHandler)
- `/doc/<file>` → `filesDir/documents/` (InternalStoragePathHandler)
- `/epub/<id>/...` → unpacked EPUBs (InternalStoragePathHandler)

PDF opens as `viewer.html?file=<encoded /doc/ URL>#page=N`. EPUB opens at the
root-relative spine href (restored locator or first spine item).

## Position tracking

- PDF: injected JS hooks pdf.js `eventBus.on('pagechanging')` →
  `Lecturo.onPdfPage(n)` → Room `lastLocator`.
- EPUB: `shouldOverrideUrlLoading` extracts the root-relative href → Room.

## Lessons / build notes

- **AGP 9 built-in Kotlin vs KSP**: KSP registers generated sources via
  `kotlin.sourceSets`, which AGP 9 forbids. Escape hatch in
  `gradle.properties`: `android.disallowKotlinSourceSets=false`.
- **material3 no longer bundles icons**: `material-icons-core` added
  explicitly.
- **kxml2 unavailable in this environment's Maven Central**: JVM tests use
  `xpp3:xpp3:1.1.4c` as the `XmlPullParserFactory` implementation instead
  (app runtime uses the framework's own factory; xpp3 is test-only).
- **pdf.js needs DOM storage**: WebView disables it by default and the
  viewer's init dies on `localStorage.getItem`, leaving pages blank.
  `settings.domStorageEnabled = true` is required.
- **Small_Phone AVD repointed** from the missing API 35 image to the
  installed API 36 image (`~/.android/avd/Small_Phone.avd/config.ini`).
- **pdf.js gray pages**: the viewer's own initial `update()` can run before
  the WebView propagates real layout dimensions, finds zero visible pages,
  and paints nothing; DOM storage must also be on (`domStorageEnabled`).
  Fix in `PDF_PAGE_HOOK` (`ReaderScreen.kt`): on `pagesloaded`, call
  `pdfViewer.update()` twice (100 ms and 1000 ms) — the second pass covers
  pages adjacent to a programmatically restored `#page=N` position.

## Verification so far

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` green; OPF/container
  parser tests in `app/src/test/.../EpubBookParserTest.kt`.
- On emulator: imported `multipage.pdf` + `sample.epub` via SAF picker;
  pdf.js viewer paints pages on open and on scroll; page-tracking and
  EPUB link-navigation locators persist; reopening restores the last page
  (PDF) / spine href (EPUB). Temporary `PDF_DIAGNOSTIC` probe removed.
- Web download verified against a host-side `python3 -m http.server 8777`
  (emulator reaches it as `10.0.2.2`): in-app URL dialog and share target
  (cold start and warm via `onNewIntent`) both download and open the doc.
- `android:usesCleartextTraffic="true"` is required — Calibre and other
  LAN servers are plain HTTP; without it downloads fail with
  "CLEARTEXT communication not permitted".
- `MainActivity` is `launchMode="singleTask"` so share intents reuse the
  running instance via `onNewIntent` instead of stacking new activities.
- Sync (feature 3) verified against `/tmp/lecturo_test/fixture_server.py`
  (port 8777), which serves a Calibre-shaped OPDS feed (`/opds` →
  `/opds_all` sub-catalog, OpenSearch descriptor at `/opds_search.xml`) and
  a Zotero-shaped Web API (`/zotero/users/12345/...`). On the emulator the
  sources are configured as Calibre `http://10.0.2.2:8777` and Zotero
  user `12345`, any key, API base `http://10.0.2.2:8777/zotero`. Verified:
  catalog listing, OPDS sub-catalog browsing, OpenSearch search, and
  download → open for PDF (Calibre) and EPUB (Zotero).
- **OPDS search templates must be substituted before URL resolution**:
  `{searchTerms}` is illegal in `java.net.URI`, so resolving the raw
  template silently falls back to the unresolved relative path and OkHttp
  rejects it ("no scheme"). `CalibreSource.search` substitutes first.
- **WebView selection menu**: `BasketWebView` (in `ReaderScreen.kt`) overrides
  both `startActionMode` overloads and wraps the callback to append an
  "Add to basket" item (lands in the overflow). The selection must be read
  via `evaluateJavascript` *before* `mode.finish()` — finishing collapses
  the selection. The snippet returns `{text, page}`; `page` comes from the
  nearest `[data-page-number]` ancestor (pdf.js pages only).
- **Initial `loadUrl` does not fire `shouldOverrideUrlLoading`**: the EPUB
  locator must be seeded from the loaded href in `ReaderViewModel`
  (`buildEpubUrl`), likewise PDF page 1, or the first basket item gets no
  locator.
- **API keys at rest**: `settings/KeyStoreCrypto.kt` (AES/GCM in
  AndroidKeyStore) encrypts keys into DataStore; `decryptOrNull` returns null
  for legacy plaintext, and `SyncSettings.zoteroApiKey` re-encrypts such
  values in place on read (verified: sync.preferences_pb holds only Base64
  ciphertext). Chat keys never shipped plaintext.
- **Chatbot verification**: `fixture_server.py` also mocks the LLM endpoints —
  `POST /v1/chat/completions` (OpenAI shape) and `POST /anthropic/v1/messages`
  (Anthropic shape) echo the system prompt's first line and basket excerpts,
  so the emulator run proves the saved prompt + basket injection. Emulator
  config: OpenAI base `http://10.0.2.2:8777/v1`, Anthropic base
  `http://10.0.2.2:8777/anthropic`, any key/model.
- **All six features are Done.** No resume checklist remains; the fixture
  setup below supports regression checks.
- **Ask AI (feature 7) verification**: selection menu → "Ask AI" → dialog
  lists saved prompts + "Custom prompt"; with saved prompt "Summarizer" the
  mock echoed system prompt "Summarize the basket excerpts concisely." with
  the selected excerpt in the basket; with custom prompt "Explain like five"
  the mock echoed it likewise. Model chooser in the reader top bar lists
  OpenAI/Kimi/Anthropic with configured model names; unconfigured providers
  are disabled.
- **Basket queue → draft chat (feature 8)**: implemented;
  `./gradlew :app:assembleDebug :app:testDebugUnitTest` green. Emulator UX
  verification pending.
- **Package vs applicationId mismatch**: the rename set
  namespace/applicationId to `com.adipginting.lecturo` while sources stayed
  in `com.example.lecturo` — the manifest merged `.MainActivity` against the
  applicationId and the app crashed on launch with
  `ClassNotFoundException: com.adipginting.lecturo.MainActivity`, even though
  `assembleDebug` was green and `adb install` reported Success. Fixed by
  moving all sources to `com.adipginting.lecturo`. Note: the installed
  package is `com.adipginting.lecturo`; older `com.example.lecturo` /
  `com.example.myapplication` installs on the emulator are stale leftovers.

## Fixture / emulator setup

- Test fixtures regenerate: `python3 /tmp/lecturo_test/make_fixtures.py`
  (multipage.pdf, sample.epub; pushed to `/sdcard/Download/`)
- Fixture server: `cd /tmp/lecturo_test && python3 fixture_server.py`
  (port 8777; OPDS + Zotero shapes + raw files + mock chat endpoints)
- Emulator: `~/Android/Sdk/emulator/emulator -avd Small_Phone -no-snapshot-save`
- If Gradle's Kotlin/AAPT2 daemons fail to spawn ("Failed to exec spawn
  helper"), `./gradlew --stop` and kill stale `KotlinCompileDaemon`
  processes, then rebuild.

