# Lecturo Web — design

Exports and tokens for the responsive web UI. The source of truth is the Penpot
file `Lecturo`, page **`Lecturo Web`** (boards `W0`–`W7`); these PNGs are the
checked-in reference the implementation follows.

## Boards

| File | Penpot board |
|---|---|
| `00-foundations.png` | `W0 · Foundations` — palette, type, spacing, grid, states, breakpoints |
| `01-library-{compact,medium,expanded}.png` | `W1 · Library` |
| `02-reader-{compact,medium,expanded}.png` | `W2 · Reader` |
| `03-reader-chat-draft-compact.png` | `W3 · Reader · Chat · draft · Compact` |
| `03-reader-chat-conversation-compact.png` | `W3b · Reader · Chat · conversation · Compact` |
| `03-reader-chat-{medium,expanded}.png` | `W3 · Reader · Chat` |
| `04-saved-{compact,medium,expanded}.png` | `W4 · Saved` |
| `05-chats-{compact,medium,expanded}.png` | `W5 · Chats` |
| `06-settings-{compact,medium,expanded}.png` | `W6 · Settings` |
| `07-components.png` | `W7 · Components` |

## Tokens

- `tokens.json` — the token set (colors, type ramp, spacing, radii, breakpoints,
  rail/panel sizes, reading measure).
- `tokens.scss` — the same as CSS custom properties, ready to import.

Colors, type and the reading font are identical to the mobile and desktop
boards, so the three sets are directly comparable.

## Notes for implementers

- **Wrapped text height is async in Penpot.** When scripting these boards, a
  text's height is not final immediately after `resize`; measure after a short
  delay before placing the next block, or paragraphs overlap.
- **Draw a fill before its text.** A bubble/card rectangle created after its
  label will paint over it.
