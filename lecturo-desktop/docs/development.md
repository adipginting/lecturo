# Development

## Prerequisites

- .NET 10 SDK.
- Linux: WebKitGTK 2.52 (only for the rare PDF-viewer fallback; reading is native).
- Windows: the WebView2 runtime (ships with Windows 10/11; used for the rare PDF
  fallback and to typeset KaTeX formulas).
- Fonts are embedded in `Assets/Fonts/` (Plus Jakarta Sans, Source Serif 4) and
  copied to the output; nothing to install.

## Build & run

```sh
dotnet build
dotnet run
```

`dotnet build` at the root builds the app, which references the domain project
beside it. The tests are their own project:

```sh
dotnet test Tests        # 25 tests: the domain rules and the schema migrations
```

Three projects, no solution file, so each command names what it builds:
`Lecturo.csproj` (app, root), `Domain/Lecturo.Domain.csproj` (the model — no
dependencies), `Tests/Lecturo.Tests.csproj` (xunit).

## Windows

The app is .NET/Avalonia, so Windows is a publish, not a port:

```sh
dotnet publish Lecturo.csproj -c Release -r win-x64 --self-contained false \
  -o publish/win-x64
```

Platform differences are behind `OperatingSystem` checks, not `#if`s:

- **Secrets** — the libsecret keychain is Linux-only P/Invoke; on Windows the
  AES-encrypted file store is used (under `%APPDATA%\lecturo`), and the settings
  screen surfaces its warning.
- **Paths** — `AppPaths` uses `%APPDATA%`/`%LOCALAPPDATA%` on Windows and the XDG
  dirs on Linux.
- **Reading** — EPUB/text-PDFs render natively on both; only the rare PDF with
  neither text nor extractable images (and KaTeX maths) uses the WebView, which
  is WebKitGTK on Linux and WebView2 on Windows.

The database and library live under the XDG dirs:

```
~/.config/lecturo/settings.json      non-secret settings
~/.config/lecturo/secrets.enc        secret fallback when libsecret is unavailable
~/.local/share/lecturo/lecturo.db    SQLite (schema v8)
~/.local/share/lecturo/documents/    app-owned document copies
~/.local/share/lecturo/covers/       cached covers
~/.local/share/lecturo/chat_images/  screenshot contexts
~/.local/share/lecturo/cache/        unpacked EPUBs
```

## Developer aids

Two environment variables are read at startup:

- **`LECTURO_START`** — open a screen directly (screenshots / smoke tests). Cases:
  `drafts`, `chats`, `settings`, `reader`, `reader-chat`, `draft-chat`,
  `conversation`, `calibre`, `calibre-download`, `zotero`, `zotero-download`,
  `skills`. `reader-chat` opens the document's draft or its active conversation,
  which is what the reader's chat icon does.
- **`LECTURO_ZOTERO_KEY`** — seeds the `zotero-api-key` secret, so the Zotero
  path can be exercised without the UI.

```sh
LECTURO_START=reader dotnet run
LECTURO_ZOTERO_KEY=test LECTURO_START=zotero dotnet run
```

A **URL or document path as a command-line argument** is the "open with" route
(S4): `dotnet run -- "https://example.org/book.epub"` downloads it, and
`dotnet run -- "/path/to/book.pdf"` imports it.

## Local test servers

Mock servers live in `docs/dev/`; put a `.epub`/`.pdf` in `docs/dev/files/`.

```sh
mkdir -p docs/dev/files
cp /path/to/sample.epub docs/dev/files/sample.epub
cp /path/to/multipage.pdf docs/dev/files/multipage.pdf

python3 docs/dev/opds_server.py     # Calibre OPDS on :8799
python3 docs/dev/zotero_server.py   # Zotero API on :8801
```

- **Calibre** — set Settings → Servers → Calibre URL to `http://127.0.0.1:8799`,
  then Sync the Calibre tab. The feed exposes one book as **both** an EPUB and a
  PDF (two rows) plus a catalog link.
- **Zotero** — set Zotero user ID (e.g. `12345`) and Zotero API base to
  `http://127.0.0.1:8801`, store any non-empty API key, then Sync. The mock
  returns 403 without a key and lists a PDF + EPUB with it.
- **Skills** — set Settings → Skills → Marketplace URL to a `file://`-served or
  HTTP copy of `docs/dev/skills.json`, then Browse and Install.

## Fixtures

Historical fixtures are a self-generated multi-page PDF and a minimal EPUB
(`/tmp/docai_test/` on the dev machine). Any PDF/EPUB works; a scanned
(image-only) PDF exercises the `pdf-images` path — `magick cover.png scan.pdf`
makes one.

## Design sync

`docs/penpot/` rebuilds the desktop boards in Penpot over the MCP server:

```sh
python3 docs/penpot/create_desktop_boards.py
```

`desktop_boards.js` is the builder; `step_cleanup.js` / `step_a.js` / `step_b.js`
split it under the plugin's 30s `execute_code` limit. Penpot uses **absolute**
child coordinates — offset every child by the board origin — and the file only
ships Plus Jakarta Sans **Regular**, so the mock uses Inter Tight for weights.

## Conventions

- **No DI container** — `App.axaml.cs` is the composition root.
- **Rules live in `Domain/`, not in view models.** If a rule is true whatever
  screen is asking — how a conversation is named, when it is a draft, what an
  excerpt is — it belongs in the domain project, and it gets a test there.
- **A draft is a conversation with no `sent_at`** — see
  [adr/0002](adr/0002-drafts-are-unsent-conversations.md). Nothing is created at
  send time, so nothing needs rolling back.
- **Maths is KaTeX, bundled.** `Assets/katex/` (MIT, version pinned in its
  README) is copied to the output and then into `~/.local/share/lecturo/maths/`,
  where `Services/MathsHtml` writes one page per distinct reply for the WebView
  to load. Replacing KaTeX means replacing that directory wholesale.
- **ViewLocator** — `FooViewModel` → `Lecturo.Views.FooView` (auto data template).
- **CommunityToolkit.Mvvm** partial properties:
  `[ObservableProperty] public partial string X { get; set; }`.
- **SQLite timestamps** — `DateTime.UtcNow.ToString("O")`, parsed with
  `RoundtripKind`.
- **Progress** is recorded in `progress.md`, not git commits.

## Pitfalls (learned here)

- **Avalonia 12**: `TextBox.Watermark` → `PlaceholderText`; `Clipboard.SetTextAsync`
  is gone — use `DataTransfer` + `DataTransferItem.CreateText` and
  `IClipboard.SetDataAsync`; `ItemsControl` has no `HorizontalContentAlignment`.
- **Native WebView input** under Wayland/XWayland is unreliable (no wheel/keys;
  the offscreen adapter spams Gdk warnings). This is why EPUB/PDF render
  **natively** and the WebView is a last-resort fallback.
- **Card stretch**: put list content in a `Grid ColumnDefinitions="*"` and set the
  ScrollViewer `HorizontalScrollBarVisibility="Disabled"`, or cards size to
  content instead of filling the measure.
- **Fonts for the WebView** must exist on disk — hence the
  `None Include="Assets\Fonts\*.ttf" CopyToOutputDirectory` item.
- **Penpot** export of large boards can exceed the 30s tool timeout; the HTTP
  runner (`base64` via `execute_code`) avoids it.
