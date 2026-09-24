# Lecturo — Desktop

A personal PDF/EPUB reader for the desktop: select text while reading into
**Saved**, then ask an LLM about an excerpt in its own conversation. Desktop port
of [`adipginting/lecturo`](https://github.com/adipginting/lecturo), styled after
the **Lecturo Web · Expanded** boards in Penpot.

Built with .NET 10 + Avalonia 12 (MVVM, `CommunityToolkit.Mvvm`), with the model
and its rules in a `Domain` project that has no dependencies and its own tests.
UI text is **Plus Jakarta Sans**, the reading surface **Source Serif 4**.

```sh
dotnet build
dotnet run
dotnet test Tests
```

Windows is a publish, not a port: `dotnet publish -c Release -r win-x64` (see
[`docs/development.md`](docs/development.md)).

## Docs

Start at [`docs/README.md`](docs/README.md):

- [`CONTEXT.md`](CONTEXT.md) — the vocabulary the code is named after
- [`docs/architecture.md`](docs/architecture.md) — stack, layers, data model, flows
- [`docs/adr/`](docs/adr/) — decisions that are hard to reverse: native rendering,
  and drafts as unsent conversations
- [`docs/design.md`](docs/design.md) — design system and Penpot mapping
- [`docs/development.md`](docs/development.md) — build/run, dev aids, test servers
- [`docs/status.md`](docs/status.md) — coverage matrix and limitations
- [`docs/plan.md`](docs/plan.md) — product/architecture plan
- [`docs/interactions.md`](docs/interactions.md) — every interaction, desktop-adapted
- [`docs/progress.md`](docs/progress.md) — dated implementation log
- [`docs/penpot/`](docs/penpot/) — scripts that build the desktop boards in Penpot

## Highlights

- **Reading** — EPUB and PDF render natively (reliable scroll + selection); text
  PDFs as Markdown, scans as extracted page images; a floating selection pill
  (Copy · Select all · Ask AI) and drag-to-crop screenshots.
- **Library** — import (files or drag-drop), URL download that follows gateways,
  Calibre (OPDS) and Zotero (Web API) browsing, cached covers.
- **Drafts and chats** — asking about a passage makes a draft: an unsent
  conversation that survives restarts and becomes a conversation on its first
  reply, with OpenAI / Kimi / OpenRouter / DeepSeek, Anthropic, or Lecturo Cloud
  answering, Markdown replies, image context, and compaction.
- **Skills** — install document-level skills from a marketplace and run them
  against a document.

## License

MIT.
