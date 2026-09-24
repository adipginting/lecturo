---
status: accepted
date: 2026-09-22
---

# Sending is optimistic, messages carry a receipt, and Enter sends

The chat panel only showed a message after the whole round trip finished: the
view model called the provider and only then added both the user's turn and the
answer. So the composer stayed full-looking and the chat stayed empty while a
request was in flight, with no timestamp anywhere and no sign whether a message
had actually gone. The reader sent with **Ctrl+Enter**; **Enter** inserted a
newline.

## Decision

- **Optimistic rendering.** On send, the reader's message is added to the thread
  at once, marked *Sending…*, and the composer is cleared. When the reply lands
  the same bubble takes its delivered state; a failed send marks it *Not sent*.
- **A receipt and a time on every message.** The reader's turns show a small
  line under them: a clock while in flight, **✓✓ and the time** once delivered,
  *Not sent* on failure. Assistant turns show their time. The double check means
  **delivered**, not **read** — the model never reports being read, so the UI
  does not claim it.
- **Enter sends, Shift+Enter is a newline.** The composer is a real multiline
  field; Enter without a modifier sends, Shift+Enter inserts a newline.

`ChatBubble` became an observable class (with a `Delivery` enum) so a bubble can
change state in place rather than being replaced, and the send flow adds the
pending bubble before awaiting `ConversationService.SendAsync`.

## Considered options

- **Add the message only when the reply arrives** (what it did): the UI lies
  about time — a message typed thirty seconds ago appears only now — and the
  reader cannot tell "thinking" from "nothing happened".
- **A separate "sending" spinner row**: a second place to look; the bubble that
  will exist anyway is the honest place for the state.
- **A read receipt**: dishonest. The provider reports token usage, not reading.

## Consequences

- A send that fails keeps the reader's text in the thread with *Not sent*, and a
  Draft stays a draft — the two failure surfaces agree.
- The receipt is part of the presentation only; it is not stored, so reopening a
  chat shows the time (from `created_at`) with a delivered check for the
  reader's turns.
- The same rule is recorded for mobile and web (their ADRs) so the three clients
  read alike; on mobile the "Enter" line is the software keyboard's send action,
  and there is no Shift+Enter.
