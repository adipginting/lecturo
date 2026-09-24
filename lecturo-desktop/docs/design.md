# Design

The desktop UI follows the **Lecturo Web · Expanded** boards in Penpot (page
`Lecturo Web`, 1440×900), with `W0 · Foundations` as the token/type reference.

## Penpot pages

| Page | Use |
|---|---|
| `Lecturo Web` | **Source of truth** — Foundations + Compact/Medium/Expanded of each screen |
| `Lecturo Mobile` | The 360×800 Android screens (Settings, Chats → Drafts/Conversations) |
| `Lecturo Desktop` | The first-pass `D1–D6` desktop boards (superseded by the Web page) |

`docs/penpot/` contains scripts that rebuild the desktop boards in Penpot
(`desktop_boards.js` + a split runner + `create_desktop_boards.py`).

## Breakpoints (W0 Foundations)

| | Compact 390×844 | Medium 834×1112 | **Expanded 1440×900** |
|---|---|---|---|
| Chrome | top app bar + bottom nav | nav rail (icon + label) | **248px nav rail + provider card** |
| Library | single list | two-pane (list + detail) | **tabbed, card grid** |
| Chat | full-screen sheet | right-side overlay | **docked 400px panel** |
| Reading measure | full width | ~680px | **centred 680–720px** |

The desktop app implements the **Expanded** column only.

## Colour tokens (`Styles/Tokens.axaml`)

| Token | Hex | Token | Hex |
|---|---|---|---|
| surface | `#FFFBFE` | secondaryContainer | `#E8DEF8` |
| surfaceContainerLow | `#F7F2FA` | onSecondaryContainer | `#1D192B` |
| surfaceContainer | `#F3EDF7` | onSurface | `#1C1B1F` |
| surfaceHigh | `#ECE6F0` | onSurfaceVariant | `#49454F` |
| primary | `#6650A4` | outline | `#79747E` |
| primaryContainer | `#EADDFF` | outlineVariant | `#CAC4D0` |
| onPrimaryContainer | `#21005D` | selection | `#D0BCFF` |

Cover tints cycle `#FFD8E4`, `#E8DEF8`, `#E6E0E9`, `#EADDFF` (`CoverPalette`).

## Typography

The Penpot boards are set in **Plus Jakarta Sans** (the `W0 · Foundations`
specimens' annotations still say "Inter Tight", but the text layers are Plus
Jakarta Sans), with **Source Serif 4** for the reading surface. Fonts are
embedded under `Assets/Fonts/` (regular/medium/semibold/bold).

Measured from the boards (W0 Foundations + the W1/W6 Expanded text layers):

| Role | Size / weight | Family |
|---|---|---|
| Display / screen title | 24 / 600 | Plus Jakarta Sans |
| Brand ("Lecturo") | 22 / 600 | Plus Jakarta Sans |
| Title / section | 20 / 500–600 | Plus Jakarta Sans |
| Card title | 16 / 600 | Plus Jakarta Sans |
| Body | 14 / 400 | Plus Jakarta Sans |
| Button | 14 / 600 | Plus Jakarta Sans |
| Tab / nav label (selected) | 14 / 600 | Plus Jakarta Sans |
| Field / input | 13 / 400 | Plus Jakarta Sans |
| Label / meta | 12 / 400 | Plus Jakarta Sans |
| Provider label ("CHAT PROVIDER") | 10 / 500 | Plus Jakarta Sans |
| Chapter title | 26 / 700 | Source Serif 4 |
| Reading body | 19 / 400 | Source Serif 4 |

Cards are `surfaceContainer` (#F3EDF7) at radius **16**; the selected nav pill is
`secondaryContainer` at radius 22 in a 12px-padded rail.

Spacing scale: 4 · 8 · 12 · 16 · 24 · 32 · 48 · 64.

## Components

Reusable classes live in `Styles/Controls.axaml` (`screenTitle`, `cardTitle`,
`excerpt`, `meta`, `pill`, `chip`, `fab`, `icon`, `text`, `plain`, `card`,
`providers`, `bubble`, `tab`, `fieldInput`, …). Icons are Material Symbols path
data in `Styles/Icons.axaml`. `FieldBox` renders an outlined Material field;
`MarkdownPresenter` renders Markdown as selectable blocks.

## Screen mapping

| Penpot board | View |
|---|---|
| `W1 · Library · Expanded` | `Views/LibraryView.axaml` — tabs Local/Calibre/Zotero, two-column card grid, FAB |
| `W2 · Reader · Expanded` | `Views/ReaderView.axaml` — top bar, native surface, chapter bar |
| `W3 · Reader · Chat · Expanded` | reader + docked `Views/ChatPanelView.axaml` |
| `W4 · Saved · Expanded` | *(retired 2026-09-21)* — the saved queue became drafts, and `Views/SavedView.axaml` was deleted; drafts live in `Views/ChatsView.axaml` |
| `W5 · Chats · Expanded` (mobile `06b`) | `Views/ChatsView.axaml` — Drafts \| Conversations |
| `W6 · Settings · Expanded` | `Views/SettingsView.axaml` — providers, vision toggle, compaction, servers, prompts, skills |

Shell: `Views/MainWindow.axaml` (248px rail + content + 400px chat panel).

## Interaction states

W0 specifies Default / Hover / Focus ring / Disabled for controls; hover overlays
and a focus style are applied to buttons, nav rows and list items. Selection →
Saved uses a floating pill over native text: **Copy · Select all · Save · Ask AI**
(the reader top bar keeps Save/Ask AI as fallbacks; Compact would use the OS menu).

## Deviations from the boards

- The `W0 · Foundations` annotations name **Inter Tight**, but the boards
  themselves are set in **Plus Jakarta Sans**; the app follows the boards.
- The reader keeps **camera** (screenshot) and **skills** icons the Expanded
  boards omit, for feature parity; the design shows only Saved + Chat.
- Screenshots capture the visible surface (drag-to-crop), not a device capture.
