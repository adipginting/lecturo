---
status: accepted
date: 2026-09-22
---

# Sending is optimistic, messages carry a receipt, and the keyboard sends

The chat added a turn only after the model answered, so sending showed nothing
until the round trip finished, messages had no timestamp, and there was no sign
whether a message had gone or was still in flight. Typing happened in a composer
that stayed full-looking while the request ran.

## Decision

- **Optimistic rendering.** On send, the reader's message is added to the thread
  at once, marked *Sending…*, and the composer is cleared; when the reply lands
  the same row takes its delivered state, and a failed send marks it *Not sent*.
- **A receipt and a time on every message.** The reader's turns show a small
  line beneath them: a clock while in flight, **✓✓ and the time** once delivered,
  *Not sent* on failure. The model's turns show their time. The double check
  means **delivered**, not **read**.
- **The keyboard sends.** The composer's IME send action sends; a newline is the
  IME's own (there is no Shift+Enter on a soft keyboard). The composer is still a
  multiline field, so a pasted multi-line prompt survives.

This is the mobile half of a rule recorded for all three clients (desktop and web
carry their own ADRs); the wording of the receipt is kept the same so the apps
read alike.

## Consequences

- A failed send keeps the passage in Drafts and the message *Not sent*, so the
  two failure surfaces agree.
- The receipt is presentation only, not stored; reopening a chat shows the time
  from `createdAt` with a delivered check on the reader's turns.
- The composer's send affordance and the keyboard's send action must agree, or a
  reader will hit the wrong one; both run the same command.
