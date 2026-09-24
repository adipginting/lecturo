# Lecturo

A reader that turns the passages you select into conversations with a language
model. The language below is the project's canonical vocabulary — shared with the
mobile app, so the same idea has the same name on both — and code, tests, UI
strings and docs use these words and no synonyms. Desktop-only vocabulary is
marked at the end.

## Language

**Document**:
A PDF or EPUB in the library, owned as a copied file.
_Avoid_: Book, file, item

**Library**:
The documents the app owns, plus the remote sources it can fetch more from.
_Avoid_: Collection, shelf

**Source**:
Where a document came from — a local file, a URL, or a remote library (Calibre, Zotero).
_Avoid_: Origin, provider (for documents)

**Excerpt**:
The passage, screenshot or crop a conversation is about.
_Avoid_: Selection, context, passage

**Draft**:
A conversation whose first message has not been sent; a conversation with no `sentAt`.
_Avoid_: Saved item, basket item, pending chat

**Conversation**:
A chat fired from an excerpt, existing once a draft has been sent.
_Avoid_: Thread, session, chat (as a noun for the entity)

**Message**:
One turn in a conversation, from the user or the model.

**Locator**:
Where in a document an excerpt sits — a 1-based PDF page, or an EPUB position kept opaque.
_Avoid_: Position, bookmark, offset

**Frozen**:
An excerpt, once captured, is never edited or compacted for the life of the conversation.
_Avoid_: Locked, immutable

**Compact**:
Fold older active messages into one summary so the next call fits the model's budget.
_Avoid_: Summarize, truncate, prune

**Context budget**:
How many tokens a conversation may carry before it compacts: the model's window
less room for the reply and a safety margin, capped hard — a million-token window
is an invitation to a bill — and never so low that a short chat compacts itself.
A number the reader typed wins outright.
_Avoid_: Limit, max tokens, threshold (that is the reader's override)

**Provider**:
The chat backend a conversation is sent to — OpenAI, Anthropic, and so on.
_Avoid_: Model, backend, service

**Destination**:
Where a model call is routed — the user's own key or the `lecturo` relay.
_Avoid_: Host, endpoint, gateway

**Provenance**:
The record that a document was downloaded, and from which remote item.
_Avoid_: Source (for a download's origin — reserved for the library source)

**Skill**:
A named prompt with a manifest, run against a document or a conversation to produce insights.
_Avoid_: Prompt (a prompt is a user-authored chip; a skill is installed and versioned)

**Insight**:
The result of one skill run, attached to the document it was run against.
_Avoid_: Output, result

## Desktop differences

The vocabulary above is the shared one. These are the places the desktop app
genuinely differs rather than merely lagging:

**Panel**:
The chat surface beside the page rather than over it — docked, never modal, so the
reading position is never lost by looking at a conversation. The phone's
equivalent is a bottom sheet.
_Avoid_: Sheet, drawer, sidebar

**Render mode**:
How the reading surface shows a document, which is not its format: `Epub`,
`PdfText`, `PdfImages`, or — last resort, when a PDF has neither text nor
extractable images — `Pdf`, where WebKitGTK's own viewer takes over and
selection and screenshots are out of reach.
_Avoid_: Format (a PDF has one format and three render modes)

**Lecturo Cloud**:
This port's `Destination`: the provider that answers without a key, identifying
the installation by its device ID. Mobile reaches the same idea through the
`relay` server.
_Avoid_: Relay, proxy, hosted API

**Skill run**:
One application of an installed skill to a document. On the desktop a finished
run becomes a *conversation* rather than an Insight — a divergence from mobile,
where insights have a list of their own and skills are still unbuilt. Recorded
here so the two vocabularies meet later instead of drifting apart silently.
_Avoid_: Insight (desktop), Job, Task

**Summary message**:
The desktop stores a compaction summary as a message whose role is `Summary` — a
third role rather than a flag on an assistant turn, because it is the one that
replaces others. Providers are told it is an assistant turn.
_Avoid_: Recap, digest
