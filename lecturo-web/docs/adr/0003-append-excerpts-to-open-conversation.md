---
status: accepted
date: 2026-09-22
---

# Further excerpts append to the open conversation

The web client is per-excerpt: every selection starts a new conversation seeded
by that passage, so reading a chapter produces a conversation per highlight
rather than one conversation about the book.

## Decision

**While a conversation is open for a document, a new excerpt or screenshot is
appended to it** rather than starting a new one:

- `POST /conversations/{id}/messages` takes the new excerpt as `content`, or a
  screenshot as `imageId` (a user turn whose context is the image); the API
  already carries an excerpt per message.
- With no conversation open, a selection still creates a draft; the first reply
  sends it.
- The reader's chat affordance opens the document's open conversation.

This is the web half of a rule recorded for all three clients (desktop and mobile
carry their own ADRs).

## Consequences

- One conversation per reading session per document; the seed excerpt stays
  frozen and later excerpts join as turns.
- A screenshot rides the turn being sent: the model call attaches the appended
  image to the latest user message, and `messages.image_id` keeps it in the
  history. No new schema was needed.
- The client must resolve "the open conversation for this document" before
  deciding whether a selection makes a draft or appends.
- `GET /conversations?documentId=` already answers that lookup.
