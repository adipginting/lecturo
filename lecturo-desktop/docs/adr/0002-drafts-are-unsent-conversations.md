---
status: accepted
---

# A draft is an unsent conversation, and there is no Save

Asking about a passage creates a **draft**: a conversation row whose `sent_at` is
null. The reply is what sends it, names it and moves it out of Drafts; a reply
that never arrives leaves it a draft to retry. The saved queue is gone — its rows
became drafts in migration 7 — and the selection pill offers **Ask AI** with no
separate Save. This follows the mobile app, which made the same move first (its
Room migration 10 → 11), so the two share one model rather than one porting a
shape the other has already left behind.

## Considered options

- **A saved queue that becomes a conversation on first send** (what the desktop
  did until now): the queue is a concept of its own, and because nothing was
  persisted until a reply landed, a send that timed out had to be *rolled back* —
  delete the conversation, return the excerpt to the queue, restore the composer.
  Every failure path had to undo work that existed only because of the ordering.
- **A draft as a row from the moment it is captured** (chosen): the failure path
  disappears instead of being handled. A draft survives restarts for free, the
  same passage can make more than one draft, and Drafts is a query
  (`sent_at IS NULL`) rather than a table.

## Consequences

- **Nothing is saved, only asked.** Collecting a passage without asking about it
  means making a draft and leaving it — one keystroke, but it is a
  conversation-shaped row rather than a bookmark.
- **A draft costs nothing until it is sent.** It has no messages, so the context
  counter and compaction ignore it.
- **The two lists sort differently.** Sent conversations are ordered by
  `updated_at`; drafts by `created_at`.
- **Migration 7 changes the schema, not the data.** `basket_items` rows are copied
  into `conversations` as drafts before the table is dropped, and every
  conversation that already existed was sent, so it takes its last update as
  `sent_at`. Mobile's own migration omits that last step, which leaves its
  pre-existing conversations reading as drafts; the desktop test suite pins the
  correct behaviour.
