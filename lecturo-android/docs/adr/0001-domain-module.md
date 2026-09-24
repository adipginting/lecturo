# A pure `:domain` module

Lecturo is one Android module, so nothing stopped UI or Room leaking into the
rules a conversation obeys. We extracted a plain `kotlin-jvm` module, `:domain`,
holding the vocabulary and invariants — `Conversation` (a draft is sent once),
`Excerpt`, `Message`, `ConversationTitle` — with their tests, and made `:app`
depend on it.

The alternative was a `domain/` package inside `:app`, which a reviewer cannot
enforce and the compiler will not. A separate module lets the build enforce the
boundary: domain types cannot reach for `androidx.room` or `Context`. The cost is
a second Gradle module and a mapper at the persistence seam, paid once per
aggregate as it moves out of `data/`.
