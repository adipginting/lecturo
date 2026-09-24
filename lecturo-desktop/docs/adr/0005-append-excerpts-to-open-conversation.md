---
status: accepted
date: 2026-09-22
---

# Further excerpts append to the open conversation

Asking about a passage makes a draft, and the first reply turns it into the
conversation for that passage. Each new selection on the same book therefore made
another draft, so a reader working through a document accumulated one chat per
highlight instead of one conversation that follows the reading.

## Decision

**While a conversation is open for a document, a new excerpt or screenshot is
appended to it** rather than starting another draft:

- Ask AI on a selection adds the excerpt to the open conversation as a message;
  a screenshot is appended the same way, as an image message.
- With no conversation open, the behaviour is unchanged: the selection makes a
  draft, and the first reply sends it.
- The reader's chat icon opens the document's open conversation; further
  selections land there.

This is the desktop half of a rule recorded for all three clients (mobile and web
carry their own ADRs).

## Consequences

- A reading session on one book grows **one** conversation; the seed excerpt is
  frozen and later excerpts join as turns.
- `ReaderViewModel.AskAboutSelection` / `AskAboutImage` no longer always create a
  draft: they first resolve the document's open conversation (the same lookup the
  reader's chat icon uses) and append when there is one.
- "New chat" remains the way to start over from a single excerpt.
