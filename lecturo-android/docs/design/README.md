# Design reference

The app's screens as drawn in Penpot — the reference the UI was aligned against
(Material 3 baseline palette, pill footer controls, tinted letter avatars,
filled send buttons). Exported from the Penpot file whose current page is
`Lecturo Mobile`, one PNG per board:

| File | Board |
| --- | --- |
| `01-library.png` | Library |
| `02-reader-epub.png` | Reader — EPUB |
| `03-reader-selection.png` | Reader — selection menu |
| `04-chat-panel-draft.png` | Reader — chat panel (draft) |
| `04b-chat-panel-keyboard.png` | Reader — chat panel (keyboard) |
| `05-chat-panel-conversation.png` | Reader — chat panel (conversation) |
| `06-chats.png` | Chats — Conversations |
| `06b-chats-drafts.png` | Chats — Drafts *(to re-export)* |
| `08-settings.png` | Settings |

The design changed under the draft model (2026-09): **Save** left the selection
menu, the reader and library carry **one chat icon**, the Chats board grew a
Drafts | Conversations tab row, and the old `07 · Saved` board was deleted. On the
current page the boards are named `06 · Chats — Conversations` and
`06b · Chats — Drafts`; `06-chats.png` and this page's other exports predate the
change, and `06b-chats-drafts.png` still needs exporting (the `07-saved.png` file
is kept only as history).

The boards were originally drawn before a few features landed and have since
been brought up to date through the Penpot MCP plugin (2026-09): the reader
top bar carries the current actions, the conversation header shows the counter
and the compact overflow, and Settings lists Lecturo Cloud with the vision
toggle and the compaction threshold. The Settings board is taller than
800px — it gained a section rather than squeezing.

## Re-exporting

With the Penpot MCP server running (its plugin connected to the file), from the
repository root:

```sh
python3 docs/design/export.py "01 · Library" docs/design/01-library.png
```

`export.py` speaks MCP over HTTP to `http://localhost:4401/mcp`, runs a
`board.export({type: "png", scale: 1})` snippet in the plugin, and decodes the
base64 result straight into the output file. Board names are the Penpot layer
names, spaces and all.
