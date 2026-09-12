# Lecturo

A personal document reader for **PDF and EPUB** on Android, built around one
idea: while reading, you select interesting text into **Saved** — a queue
of excerpts waiting to be asked about. Tapping a saved item fires it into its
own LLM conversation that knows that excerpt as its frozen context.

## Features

- **Reading** — smooth continuous scrolling for PDF (`androidx.pdf`, the platform
  viewer) and EPUB (Readium), with last-read position restored on reopen.
- **Sources** — import from local storage, download from a URL, or receive
  files via share intent. Imports are copied into app-owned storage, and a
  download is judged by its payload: a URL that answers with a web page instead
  of the book is followed to the real file, or refused.
- **Remote libraries** — browse, search, and download from **Calibre** (OPDS)
  and **Zotero** (Web API); manual sync only, per-source error handling.
- **Saved** — "Save" in the text-selection menu collects excerpts
  with their source locators; cart-style list, persistent across restarts.
  EPUB excerpts keep their formatting as Markdown, and a PDF selection carries
  drag handles at either end so it can be adjusted before it is saved.
- **Covers** — an EPUB's own cover image, or a PDF's first page, cached per
  document.
- **Chatbot** — each conversation is fired from a saved excerpt that becomes
  its frozen context, is named after the first message sent, and can be renamed
  from the list or from the chat itself. Pluggable providers: OpenAI, Kimi,
  OpenRouter, DeepSeek (OpenAI-compatible), Anthropic Claude (native API);
  GitHub Copilot stubbed.
- **Settings** — provider keys and server credentials, saved prompt templates
  (usable as composer chips, capped at twelve), API keys AES-encrypted with
  AndroidKeyStore.

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
./gradlew :app:testDebugUnitTest   # 65 unit tests, green
```

## Documentation

- [docs/product-plan.md](docs/product-plan.md) — feature list with acceptance
  criteria and a platform-neutral architecture description.
- [docs/architecture.md](docs/architecture.md) — settled design decisions,
  serving model, and lessons learned.

## License

[MIT](LICENSE)
