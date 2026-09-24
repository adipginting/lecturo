# Status

Build is green, 75 tests pass. The layers are now `Domain` / `Application` /
`Infrastructure` over ports, with view models taking application services rather
than repositories ([ADR 0003](adr/0003-application-layer-over-ports.md)). Schema
is at v8, and a draft is an unsent conversation
([ADR 0002](adr/0002-drafts-are-unsent-conversations.md)) — the saved queue is
gone, so the scenarios below that describe saving a passage now describe asking
about it. This is the coverage map against
[`interactions.md`](interactions.md) (the desktop-adapted
interaction spec). Legend: **Built** · **Partial** · **N/A** · **Planned**.

## Global rules

| | Status | Notes |
|---|---|---|
| G1 Back stack (Esc / Alt+Left) | Built | selection/crop → panel → reader → library |
| G2 App-owned library | Built | copy-in on import; delete removes the local copy only |
| G3 Persistence | Built | SQLite + `settings.json` + keychain |
| G4 Window changes keep the reader | Built | resize/theme/DPI reflow; position stored |
| G5 Layout | Built | rail + content + docked panel; no system insets on desktop |
| G6 Failures are messages | Built | every remote call surfaces inline |

## Library

| | Status | Notes |
|---|---|---|
| S1 First launch | Built | empty state on the Local tab |
| S2 Import from disk | Built | picker + drag-and-drop; payload sniffed |
| S3 Download from URL | Built | follows download pages ≤3 hops, then sniffs bytes |
| S4 Receive a link | Built | "open with" / command-line URL runs the S3 flow; a file path runs S2; drag-and-drop. File associations are packaging |
| S5 Open a document | Built | missing file shows a message |
| S6 Delete | Built | confirm dialog + multi-select delete |
| S7 Calibre sync/browse/search | Built | verified against a local OPDS mock |
| S8 Zotero sync/search | Built | verified against a local Web API mock |
| S9 Download a remote item | Built | saved, linked, opened |
| S10 Open a downloaded remote item | Built | local copy, no network |
| S11 Mark downloaded | Built | `document_sources` provenance, per format |
| S12 Two formats, two rows | Built | Calibre EPUB + PDF |

## Reader

| | Status | Notes |
|---|---|---|
| S13 Open PDF/EPUB | Built | native EPUB, text-PDF, image-PDF; WebView only as a rare fallback |
| S14 Reading controls | Built | back, title, chat, camera (+ skills kept beyond the boards) |
| S15 Chapter stepping | Built | Previous / Contents dropdown / Next |
| S16 Swipe paging | **N/A** | desktop has no swipe; uses buttons/keys |
| S17 Select text | Built | floating pill Copy · Select all · Ask AI; right-click and Ctrl+Enter do the same |
| S18 PDF selection pickers | **N/A** | native text selection replaces the drawn PDF handles |
| S19 A draft persists | Built | the draft is a row from the moment it is captured; locator = page/chapter |
| S20 Ask AI about a selection | Built | opens a draft in the panel |
| S21 Dismiss a selection with Back | Built | Esc clears the pill/crop before anything else |
| S22 Screenshot a passage | Built | drag-to-crop the visible surface → image draft → vision provider |
| S23 Model picker | Built | reader top bar + panel |
| S24 Open a document's chat | Built | the icon's target is the newest draft, else the active chat, else the chats list; tinted when there is one in reach |
| S25 Collapse the panel | Built | state preserved |
| S26 Type in the panel | Built | composer pinned to the panel bottom |
| S27 Send the first message | Built | the reply marks it sent and names it; a failed send leaves it a draft to retry |
| S28 Model timeout (retry once) | **Planned** | single attempt with an error line |
| S29 Send / switch provider / rename | Built | |
| S30 Compaction | Built | summary + superseded originals; the budget comes from the model, and a hand-set threshold in Settings overrides it |
| S31 Prompt chips | Built | |
| Formulas (TeX) | Built | replies carrying maths are typeset by the bundled KaTeX 0.16.22 (a WebView per maths message, prose stays native); a Settings toggle turns it off; MathML in an EPUB is not typeset |

## Drafts / Chats / Settings

| | Status | Notes |
|---|---|---|
| S32 Drafts while reading | Built | the chat icon opens the document's draft in the docked panel |
| S33 The drafts list | Built | Chats → Drafts, newest first, per-row discard; no clear-all |
| S34 Conversation list | Built | Chats → Conversations with rename/delete |
| S35 Providers | Built | base URL / key / model; fetch model list; Lecturo Cloud keyless |
| S36 Servers | Built | Calibre + Zotero (incl. configurable Zotero base) |
| S37 Prompts | Built | add/edit/delete, capped at twelve |
| S38–S42 Skills | Built | catalog browse, install/enable, document-level runs that become conversations (see CONTEXT.md on the Insight divergence) |
| S43 Paying for a skill | **Partial** | paid skills install but refuse to run; no entitlement flow |

## Cross-cutting

| | Status |
|---|---|
| X1 Provider not configured | Built |
| X2 No network | Built |
| X3 Missing file | Built |
| X4 Deleted document | Built |
| X5 Process death / restart | Built |
| X6 Layering: application ports + infrastructure adapters | Built |

## Known limitations / not built

- **Zotero** is verified against a mock only; a live account is unexercised.
- **Esc back-stack** (G1 / S21) and **model-timeout retry** (S28) are not wired.
- **"Open with" / shared-link intake** (S4).
- **PDF drawn selection pickers** (S18) are N/A because text PDFs select natively.
- **Screenshots** crop the rendered surface; there is no device-screenshot path.
- **UI font** deviates from the Foundations sheet by decision (Plus Jakarta Sans).
- **Maths** typesets TeX, not MathML: an EPUB that ships `<math>` renders its
  source, as on mobile.
- ~~**No automated tests**~~ — 25 now: the domain rules (`Conversation`,
  `ConversationTitle`, the stored values) and the v7 migration, which is checked
  against a v6 database seeded with a saved item, a conversation and a prompt.
