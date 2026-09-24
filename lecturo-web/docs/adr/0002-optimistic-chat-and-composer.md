---
status: accepted
date: 2026-09-22
---

# Sending is optimistic, messages carry a receipt, and Enter sends

The web client added the user's turn only once the server had stored it, and the
assistant's turn as the SSE stream arrived. The reader's message therefore did
not appear until the request had been accepted, messages had no timestamp, and
Enter inserted a newline.

## Decision

- **Optimistic rendering.** On send, the reader's message is added to the thread
  at once, marked *Sending…*, and the composer cleared. The bubble is reconciled
  with the stored message when the server responds; a failed send marks it
  *Not sent*.
- **A receipt and a time on every message.** The reader's turns show a clock
  while in flight, **✓✓ and the time** once delivered, *Not sent* on failure;
  the model's turns show their time. Because the reply streams, *delivered* is
  the moment the stream is accepted, not when it finishes. The double check means
  **delivered**, not **read**.
- **Enter sends, Shift+Enter is a newline.** The composer is a multiline field;
  Enter without a modifier sends, Shift+Enter inserts a newline.

This is the web half of a rule recorded for all three clients (desktop and mobile
carry their own ADRs); the receipt wording is kept the same.

## Consequences

- Reconciliation must match the optimistic bubble to the server's message by a
  client id, since the server assigns the real id; a mismatch would duplicate the
  turn. The API carries the client id for this.
- The receipt is presentation only, not stored; reopening a chat shows the time
  with a delivered check on the reader's turns.
- Streaming and the optimistic bubble are independent: the reader's row is
  *Sent* as soon as the request is accepted, while the assistant's row fills in.
