# Lecturo

A reader that turns the passages you select into conversations with a language
model. The language below is the project's canonical vocabulary; code, tests, UI
strings and docs use these words and no synonyms. It is shared with the mobile
and desktop clients — a term means the same thing everywhere.

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

## Web-only terms

**Window class**:
The width band the shell adapts to, named after Material 3's classes:
**Compact** (under 600px — bottom navigation, chat as a sheet), **Medium**
(600–1023px — a rail, chat as an overlay), **Expanded** (1024px and up — a wide
rail, chat docked beside the page).

**Session**:
The server-side record of a signed-in browser; the cookie carries an opaque
token, the database a hash of it. A session is a web transport concern, not part
of the domain.
_Avoid_: Login, token, account
