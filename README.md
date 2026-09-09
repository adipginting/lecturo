# lecturo

A personal document reader for **PDF and EPUB** on Android, built around one
idea: while reading, you select interesting text into a **basket** — a queue
of excerpts waiting to be asked about. Tapping a basket item fires it into its
own LLM conversation that knows that excerpt as its frozen context.

## Features

- **Reading** — smooth continuous scrolling for PDF (bundled pdf.js) and EPUB,
  with last-read position restored on reopen.
- **Sources** — import from local storage, download from a URL, or receive
  files via share intent. Imports are copied into app-owned storage.
- **Remote libraries** — browse, search, and download from **Calibre** (OPDS)
  and **Zotero** (Web API); manual sync only, per-source error handling.
- **Basket** — "Add to basket" in the text-selection menu collects excerpts
  with their source locators; cart-style list, persistent across restarts.
- **Chatbot** — each conversation is fired from a basket excerpt that becomes
  its frozen context. Pluggable providers: OpenAI, Kimi, OpenRouter
  (OpenAI-compatible), Anthropic Claude (native API); GitHub Copilot stubbed.
- **Settings** — provider keys and server credentials, saved prompt templates
  (usable as composer chips), API keys AES-encrypted with AndroidKeyStore.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · single Gradle module, MVVM ·
Room + DataStore · WebView with pdf.js and `WebViewAssetLoader` for a unified
same-origin rendering pipeline · OkHttp + kotlinx.serialization ·
XmlPullParser (OPDS/OPF).

Package: `com.adipginting.lecturo` · minSdk 24 · targetSdk 36.

## Build

Requires the Android SDK (a `local.properties` with `sdk.dir`, or the
`ANDROID_HOME` environment variable).

```sh
./gradlew :app:assembleDebug       # build debug APK
./gradlew :app:testDebugUnitTest   # run unit tests
```

## Documentation

- [docs/product-plan.md](docs/product-plan.md) — feature list with acceptance
  criteria and a platform-neutral architecture description.
- [docs/architecture.md](docs/architecture.md) — settled design decisions,
  serving model, and lessons learned.

## License

[MIT](LICENSE)
