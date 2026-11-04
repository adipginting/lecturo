# Architecture

## Stack

| Concern | Choice |
|---|---|
| Runtime | .NET 10 (`net10.0`) |
| UI | Avalonia 12.1.1 (FluentTheme, light) |
| MVVM | CommunityToolkit.Mvvm 8.4.2 (partial `[ObservableProperty]`, `[RelayCommand]`) |
| Storage | Microsoft.Data.Sqlite 10.0.11, hand-written SQL, single-baseline schema |
| WebView | Avalonia.Controls.WebView 12.1.0 (`NativeWebView`) — **fallback only** |
| Markdown | Markdig 1.3.2 (render), ReverseMarkdown 6.2.1 (EPUB XHTML → Markdown) |
| PDF | PdfPig 0.1.16 (text extraction + embedded-image extraction) |

Five projects, all `net10.0`:

| Project | What it holds |
|---|---|
| `Lecturo.csproj` (repo root) | The app: views, view models, the design system, and the rendering helpers (`ReaderService`, `CoverService`, `MathsHtml`) |
| `Application/Lecturo.Application.csproj` | Use cases and **ports** (repository/fetcher/chat interfaces); references `Domain` only |
| `Infrastructure/Lecturo.Infrastructure.csproj` | Adapters implementing the ports — SQLite repositories, `FileStore`, the HTTP fetcher and skill catalog, `ChatClient`, the remote-library sources, the settings and secret stores |
| `Domain/Lecturo.Domain.csproj` | The model and its rules — `Conversation`, `Excerpt`, `Message`, `ConversationTitle` (in `Domain.Chat`), `Document`, `RenderMode`, `Prompt`, `Skill`, `Provenance` |
| `Tests/Lecturo.Tests.csproj` | xunit tests over the domain, the use cases (fakes, no DB), the schema, and the layering |

The domain references nothing. It does not know what SQLite is, what HTTP is, or
that Avalonia exists, so the rules in it cannot be bent by a view that finds them
inconvenient. Namespace `Lecturo.Domain` for the model, `Lecturo.*` for the app.
See [adr/0003](adr/0003-application-layer-over-ports.md) for the application
layer and why view models no longer take repositories.

## Composition root

`App.axaml.cs` builds everything by hand (no DI container): `AppPaths` →
`Database` → repositories → `SettingsService` → `SecretStore.Create()` →
services → view models → `MainWindow`. It also applies two dev aids (below).

`Program.cs` is the standard Avalonia bootstrap (`UsePlatformDetect`,
`WithInterFont`, `LogToTrace`).

## Layers

```
Views/ + ViewModels/        screens observe view models
Application/                use cases + ports (references Domain only)
Services/                   rendering helpers still in the app (ReaderService, CoverService, MathsHtml)
Infrastructure/             adapters: SQLite repositories, HTTP, settings, secrets (implements Application ports)
Domain/                     the model and its rules, in its own project
Tests/                      xunit: the domain, the use cases (fakes), the schema, the layering
Styles/ + Controls/         design system and reusable controls
```

Screens never touch storage or the network directly; they go through view models,
which call application services. Dependency direction is enforced two ways: by
the project references (`Application → Domain`; `Infrastructure → Application,
Domain`; the app → both) and by `Tests/ArchitectureTests`, which fails if a view
model or the application layer references `Lecturo.Infrastructure`. A view model
is where a screen's state lives; a *rule* is not: if it is true regardless of
which screen is asking, it belongs in `Domain/`; if it is a use case, it belongs
in `Application/`.

## Data model (SQLite, `~/.local/share/lecturo/lecturo.db`)

The schema is a single baseline: `PRAGMA user_version` is **1**, and
`Database.ApplyMigration1` creates every table directly. The app is pre-release,
so the migration history was reset to zero rather than accumulated. The tables
are `documents`, `prompts`, `conversations`, `messages`, `document_sources`,
`skills` and `skill_runs`, and every generated primary key is a UUIDv7 string
(SQLite cannot mint one, so the repositories generate it on insert). See
[database-schema.svg](database-schema.svg) for the shape.

Repositories (in `Infrastructure/`): `DocumentRepository`, `ChatRepository`,
`PromptRepository` (prompts are their own aggregate), `DocumentSourceRepository`
(the `Provenance` records), `SkillRepository`. `ChatRepository` carries both
halves of a conversation's life — `ListDrafts()` is `sent_at IS NULL`,
`ListConversations()` is `sent_at IS NOT NULL` — because a draft and a
conversation are one row in two states.

Settings (`~/.config/lecturo/settings.json`, `AppSettings`): active provider,
per-provider base URL + model, anonymous `DeviceId`, `ContextThreshold`,
`VisionOverrides`, Calibre address/username, Zotero user id + base URL, skills
`MarketplaceUrl`. **Secrets** live in the OS keychain (libsecret) or an
AES-256-GCM file fallback — never in `settings.json`.

## Reader (`Services/ReaderService.cs`)

`ReaderService.Load(doc)` resolves a document to one of four **render modes**
(distinct from the document's `DocumentFormat`, which is only pdf or epub):

| Render mode | When | How it renders |
|---|---|---|
| `Epub` | EPUB | Unzip → OPF spine → each spine item's body → **Markdown** (ReverseMarkdown) |
| `PdfText` | PDF with a text layer (≥200 letters) | PdfPig `ContentOrderTextExtractor`, one chapter per page |
| `PdfImages` | PDF with no text layer but embedded page images | PdfPig `IPdfImage.TryGetPng`, one chapter (page) per image |
| `Pdf` | PDF with neither | WebKitGTK's own viewer (rare fallback) |

A chapter is `ReaderChapter(Title, Markdown, ImagePng?)`. `ReaderViewModel`
renders Markdown via `MarkdownPresenter` (selectable) or the page image, inside a
native `ScrollViewer` — so scrolling and (for text) selection are reliable, and
screenshots can crop the surface. Empty leading spine items are skipped.
Why the surface is native rather than a WebView is recorded in
[adr/0001](adr/0001-native-rendering-over-webview.md).

**Selection → draft** happens on the native surface: a floating pill
(`Copy · Select all · Ask AI`) appears on a selection; `Ctrl+Enter` and a
right-click menu do the same. **Screenshots** enter a crop mode
(drag a rectangle → Capture) that renders the surface to a PNG with
`RenderTargetBitmap`, crops with `DrawingContext.DrawImage`, and opens an image
draft.

## Chat (`Services/ChatService.cs`, `ViewModels/ChatPanelViewModel.cs`)

- One `CompleteAsync(providerId, systemPrompt, history, imagePath?)` for all
  providers. OpenAI/Kimi/OpenRouter/DeepSeek share the OpenAI chat-completions
  shape; **Lecturo Cloud** is the same client but authenticates with the install
  `DeviceId` as a bearer and no key; Anthropic uses the native Messages API.
- An optional context image is attached to the first user message (OpenAI
  `image_url` data URI / Anthropic base64 image).
- A conversation is per-excerpt (or per-screenshot), named from the first message,
  renameable — the naming rules live in `Domain/ConversationTitle`. **A draft is a
  conversation with no `sent_at`** ([ADR 0002](adr/0002-drafts-are-unsent-conversations.md)):
  asking creates the draft row, the first reply sends it, and a reply that never
  arrives leaves it a draft to retry rather than rolling anything back.
  `ActiveHistory` sends the summary + non-superseded turns.
- **The context counter** (`Domain/ContextCounter`) shows what the next call would
  carry. Before a provider has reported usage it estimates the whole payload at
  four characters to a token; after a reply it starts from the prompt tokens the
  provider actually counted (`CompleteAsync` returns them, stored as
  `last_prompt_tokens` / `last_call_at`) and adds only what has arrived since.
- **The budget** (`Domain/ModelWindow`) is the model's window less room for the
  reply and a safety margin, floored and capped; an unknown model keeps 8 000 and
  a threshold the reader typed wins outright. Crossing it compacts.
- **Maths.** `Domain/MathSplit` divides a reply into prose and formulas — `$$…$$`
  and `\[…\]` display, `\(…\)` inline, and `$…$` only when it is one line with no
  space inside and carries a maths-only character, which keeps `$5 and $10` as
  money. A message with a formula is rendered by `Controls/MathsView`: a WebView
  over `Services/MathsHtml`, which renders the prose with Markdig, hands the TeX
  over in `data-tex` attributes, and lets the bundled **KaTeX 0.16.22**
  (`Assets/katex/`, copied into the data directory on first use) typeset it and
  report the height it needs. A message without maths never pays for a WebView.
  A Settings toggle turns the whole thing off.

## Remote libraries (`Services/RemoteLibrary.cs`)

`IRemoteLibrarySource` — `Browse / Search / Download`:

- **`CalibreSource`** — OPDS (Atom/XML): acquisition links, `subsection`
  catalogs, OpenSearch `{searchTerms}`, HTTP Basic auth.
- **`ZoteroSource`** — Web API attachments (`/users/{id}/items?itemType=attachment`),
  configurable base URL, `Zotero-API-Version` + bearer key, `q=` search.

Downloads create a `document_sources` row; the remote list marks downloaded items
and reopens the local copy without network.

## Skills (`Services/SkillService.cs`)

A JSON marketplace (`{skills:[…]}` or a bare array) is browsed and installed into
`skills`; `RunAsync` applies one skill to one document — it creates a *sent*
conversation (`CreateSentConversation`: a run does its own asking, so its result
is not a draft) whose system prompt is the skill instruction and whose context is
the running chapter, calls the provider, and records a `skill_runs` row
(`Running` → `Done`/`Failed`). Priced skills install but refuse to run.

## Covers (`Services/CoverService.cs`)

Extracts an EPUB cover (the OPF `cover` meta, `properties="cover-image"`, or a
`cover` image item) and caches it under `~/.local/share/lecturo/covers/<id>`;
PDF covers fall back to the tinted initial tile.
