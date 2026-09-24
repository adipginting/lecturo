# Lecturo

A personal document reader for **PDF and EPUB** on Android, built around one
idea: while reading, you select interesting text and **ask AI** about it. Asking
makes a **draft** — an unsent conversation that knows that excerpt as its frozen
context, waiting beside the conversations it will join until its first reply
lands.

## Features

- **Reading** — smooth continuous scrolling for PDF (`androidx.pdf`, the platform
  viewer) and EPUB (Readium), with last-read position restored on reopen. The
  EPUB chapter controls (Previous, Contents, Next) sit in a footer below the
  page, never over it.
- **Sources** — import from local storage, download from a URL, or receive
  files via share intent. Imports are copied into app-owned storage, and a
  download is judged by its payload: a URL that answers with a web page instead
  of the book is followed to the real file, or refused.
- **Remote libraries** — browse, search, and download from **Calibre** (OPDS)
  and **Zotero** (Web API); manual sync only, per-source error handling. A
  download is remembered, so the remote list marks what is already local and opens
  it instead of fetching again.
- **Drafts** — "Ask AI" in the text-selection menu makes an unsent conversation
  carrying the excerpt: it survives restarts, sits in a Drafts tab beside the
  chats, and only leaves it once the first reply lands. EPUB excerpts keep their
  formatting as Markdown, and a PDF selection carries drag handles at either end
  so it can be adjusted before it is asked about.
- **Screenshots** — crop a rectangle out of the page (PDF or EPUB) and ask
  about that: the image becomes the conversation's frozen context, sent to
  vision-capable models only. The reader dims and explains itself rather than
  offering the crop for a model that can't read pictures.
- **Covers** — an EPUB's own cover image, or a PDF's first page, cached per
  document.
- **Chatbot** — each conversation is fired from a draft or a screenshot that
  becomes its frozen context, is named after the first message sent, and can be
  renamed from the list or from the chat itself. The reader's top bar keeps one
  chat icon, which opens the document's newest draft, else its current
  conversation, else the chats list. A draft that gets no answer stays in Drafts,
  unsent, rather than leaving a half-made chat behind. A context counter (live
  estimate, calibrated by the provider's reported usage) knows each model's
  window, and sending past it compacts first — folding the active history into one
  summary written by the conversation's current model. Replies render as Markdown,
  with TeX typeset by a bundled KaTeX. Pluggable providers: OpenAI, Kimi,
  OpenRouter, DeepSeek (OpenAI-compatible), Anthropic Claude (native API),
  **Lecturo Cloud** (the bundled relay, for testers without keys); GitHub Copilot
  stubbed.
- **Settings** — provider keys and server credentials, saved prompt templates
  (usable as composer chips, capped at twelve), the compaction threshold, a switch
  for formula typesetting and per-model vision overrides, API keys AES-encrypted
  with AndroidKeyStore.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · single Gradle module, MVVM ·
Room + DataStore · `androidx.pdf` and the Readium toolkit for rendering ·
OkHttp + kotlinx.serialization · XmlPullParser (OPDS/OPF).

Package: `com.adipginting.lecturo` · minSdk 28 · targetSdk 36.

## Build

Requires the Android SDK (a `local.properties` with `sdk.dir`, or the
`ANDROID_HOME` environment variable).

```sh
./gradlew :app:assembleDebug       # build debug APK
./gradlew :app:testDebugUnitTest   # 138 unit tests, green
cd ../lecturo-relay && ../gradlew test   # the relay's 10 tests, green
```

## Documentation

- [docs/product-plan.md](docs/product-plan.md) — feature list with acceptance
  criteria and a platform-neutral architecture description.
- [docs/architecture.md](docs/architecture.md) — settled design decisions,
  serving model, and lessons learned.
- [docs/interactions.md](docs/interactions.md) — every interaction and scenario,
  as conditions and narrations.
- [docs/design/](docs/design/) — the app's screens as drawn in Penpot, the
  reference the UI's styling follows, with the script that re-exports them.
- [lecturo-relay/README.md](../lecturo-relay/README.md) — the Lecturo Cloud server: what it does,
  how to run and deploy it, and why it stays deliberately dumb.

## License

[MIT](LICENSE)
