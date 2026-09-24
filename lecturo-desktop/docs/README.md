# Lecturo — Desktop

Documentation index for the **Lecturo** desktop application: a personal PDF/EPUB
reader where asking about a selected passage makes a **draft** — an unsent
conversation whose frozen context is that excerpt — and the first reply turns it
into a conversation.

This is the desktop port of the Android app
([`adipginting/lecturo`](https://github.com/adipginting/lecturo)). The UI follows
the **Lecturo Web · Expanded** boards in the Penpot file.

## Quick start

```sh
dotnet build
dotnet run
```

Requires the .NET 10 SDK. On Linux, EPUB/PDF reading is native; the embedded
WebView (used only as a rare PDF fallback) needs WebKitGTK 2.52.

## Documents

| File | Contents |
|---|---|
| [`architecture.md`](architecture.md) | Stack, layers, data model, service flows |
| [`design.md`](design.md) | Design system, Penpot mapping, typography, deviations |
| [`development.md`](development.md) | Build/run, dev aids, fixtures, local test servers |
| [`status.md`](status.md) | Feature + scenario coverage matrix, known limitations |
| [`plan.md`](plan.md) | Product & architecture plan (spec) |
| [`interactions.md`](interactions.md) | Every interaction as Condition/Narration, desktop-adapted |
| [`progress.md`](progress.md) | Dated running log of implementation sessions |
| [`penpot/`](penpot/) | Scripts that build the desktop boards in Penpot |

## What it does

- **Library** — import local PDF/EPUB (or drag-drop), download from a URL
  (following download gateways), browse Calibre (OPDS) and Zotero (Web API), with
  cached EPUB covers and a two-column card grid.
- **Reader** — EPUB and PDF render **natively** (scroll, selection); scanned PDFs
  render as extracted page images; a floating selection pill offers
  Copy · Select all · Ask AI; a crop screenshot makes a draft carrying the image.
  Chapter Previous/Contents/Next for paginated content.
- **Drafts and Chats** — asking about a passage makes a draft (an unsent
  conversation); the Chats surface has Drafts and Conversations; streaming-free
  completions from OpenAI/Kimi/OpenRouter/DeepSeek, Anthropic, or **Lecturo
  Cloud**; Markdown replies, live context counter, compaction, image context.
- **Settings** — providers + keys, Calibre/Zotero, prompts, skills
  marketplace, vision support, compaction threshold.

## Repository layout

```
Domain/          the model and its rules; no dependencies at all (Conversation, Excerpt, Document, …)
Application/     use cases + ports (references Domain only)
Infrastructure/  adapters: SQLite repositories, HTTP, settings, secrets
Services/        rendering helpers still in the app (ReaderService, CoverService, MathsHtml)
Tests/           xunit over the domain, the use cases (fakes), the migrations, the layering
ViewModels/      MVVM view models (CommunityToolkit.Mvvm)
Views/           Avalonia XAML screens + code-behind
Controls/        FieldBox, MarkdownPresenter
Styles/          Tokens, Icons, Controls (design system)
Assets/Fonts     Plus Jakarta Sans (UI) + Source Serif 4 (reading)
docs/            this documentation
```

The vocabulary these names must match lives in [`CONTEXT.md`](../CONTEXT.md) at
the repo root. Decisions that are hard to reverse live in [`docs/adr/`](adr/).
