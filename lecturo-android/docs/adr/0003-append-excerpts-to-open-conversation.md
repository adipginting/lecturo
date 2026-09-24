---
status: accepted
date: 2026-09-22
---

# Further excerpts append to the open conversation

Asking about a passage creates a **draft**, and a draft becomes the
conversation for that passage. Each new selection therefore made a *new* draft,
so a reader working through a chapter ended up with one conversation per
highlight — a scattering of tiny chats about the same book instead of one
conversation that follows the reading.

## Decision

**While a conversation is open for a document, a new excerpt or screenshot is
appended to it** rather than starting another draft:

- Ask AI on a selection, or asking about a screenshot, adds the excerpt to the
  open conversation as a message (the screenshot as an image message).
- With no conversation open, the behaviour is unchanged: the selection makes a
  draft, and the first reply sends it.
- The reader's chat icon opens the document's **open conversation**; further
  selections land there.

This is the mobile half of a rule recorded for all three clients (desktop and web
carry their own ADRs).

## Consequences

- A reading session on one book grows **one** conversation. The excerpt the chat
  was fired from stays its frozen seed; later excerpts join as turns, so the
  context is the seed plus what was appended — and compaction treats them like
  any other history.
- The Drafts list no longer fills with one unsent draft per highlight.
- "New chat" remains the way to deliberately start over from a single excerpt.
- The rule needs a well-defined notion of "the open conversation for this
  document", which is the same *current chat* lookup the reader's icon already
  uses.
