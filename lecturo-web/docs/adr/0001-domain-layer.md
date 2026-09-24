# A pure `internal/domain` layer

The server started package-per-concern, so nothing stopped SQL or `net/http`
leaking into the rules a conversation obeys. We extracted `internal/domain`
holding the vocabulary and invariants — `Conversation` (a draft is sent once,
named from its first message, never left nameless), `Excerpt`, `Message`,
`ConversationTitle` — with their tests, and made `infrastructure` and
`interfaces` depend on it, never the reverse (mirrors the mobile's
`docs/adr/0001-domain-module.md`).

The alternative was domain-shaped packages scattered beside the stores, which a
reviewer cannot enforce. Unlike Kotlin, Go cannot make a package un-importable
from another, so the boundary is held by review plus a dependency check rather
than by the compiler — the reason to keep the rule written down.

**In progress.** The domain model and the `infrastructure`/`interfaces` split are
in place and adopted; the `application` layer (use cases over repository ports)
and the thin handlers that call it are still to come.
