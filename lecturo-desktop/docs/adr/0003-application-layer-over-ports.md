---
status: accepted
date: 2026-09-22
---

# An application layer over ports, with infrastructure behind it

`ViewModels/ChatPanelViewModel.cs` was 471 lines and owned the life of a
conversation — a draft is born, sent once by its first reply, named from its
first message, compacted when it outgrows the model — while also holding UI
state and talking to `ChatRepository` (SQLite) and `ChatService` (the provider
client). No test covered that flow; the domain had its rules tested and the
migrations were covered, but the part where the rules meet persistence and the
network was only ever exercised by hand.

The port that everything hung on was a concrete class, so there was no seam to
put a fake behind. More generally, every view model took concrete repositories,
and nothing but convention stopped a screen from reaching into SQL.

## Decision

Add an application layer and put the adapters behind it.

- **`Application/`** is a project that references `Domain` only. It holds the
  use cases and the **ports** they depend on — `IConversationRepository`,
  `IPromptRepository`, `IChatClient`, `IDocumentRepository`, `IFileStore`,
  `IDocumentFetcher`, `IRemoteLibrarySource`, `IProvenanceRepository`,
  `ISkillRepository`, `ISkillCatalog`, `ISettingsStore`, `ISecretStore`. Ports
  are declared by their consumer, not the adapter.
- **`Infrastructure/`** is a project that references `Application` and `Domain`.
  It holds the adapters: SQLite repositories, `FileStore`, the HTTP fetcher and
  skill catalog, `ChatClient`, the remote-library sources, the JSON settings
  store and the keychain-backed secret stores.
- **`Lecturo.csproj`** (the app) references both, and only `App.axaml.cs` — the
  composition root — names an adapter. View models depend on application
  services and interfaces.
- The rules moved out of `ChatPanelViewModel` into `Application/Chat/
  ConversationService`, which is now covered by tests that use fakes and no
  database or network.

The work was done as **vertical slices**, one bounded context at a time
(Chat, Library, Catalog, Skills, Settings), the build green and the suite passing
at every step, rather than a single rename across all contexts.

## Considered options

- **Keep a `Services/` namespace in the app and enforce layering by review**:
  what the project had, and what the untested chat flow came from. Nothing
  enforces it, so it drifts.
- **Put ports in `Domain/`** (the mobile app's shape): kept the domain free but
  gave it persistence-shaped interfaces to carry. The web's `application/chat`
  puts them in the application layer, and §9.7 of the PRD names the application
  layer as the consumer; the desktop follows that.
- **Application-owned ports with separate projects** (chosen): the compiler
  enforces the direction, and the fakes fall out of the ports for free.

## Consequences

- **Three projects become five** (`Domain`, `Application`, `Infrastructure`, the
  app, `Tests`). `Tests` references the three libraries, not the app.
- **A namespace trap**: `Lecturo.Application` shadows `Avalonia.Application`
  inside the `Lecturo` namespace, so `Application.Current` silently binds to the
  wrong thing. It must be written `Avalonia.Application.Current` — done in
  `MainViewModel` and `LibraryViewModel`.
- **Rendering stays in the app.** `ReaderService`, `CoverService` and
  `MathsHtml` are presentation helpers, not persistence; they remain in
  `Services/`. A later pass can give them ports if the rendering surface moves
  engines again.
- **`Services/ChatService` was renamed `ChatClient`** and moved to
  Infrastructure: it is a wire-format client, not an application service.
- An **architecture test** fails if a view model or the application layer
  references `Lecturo.Infrastructure`, so the rule cannot quietly rot.
