package com.adipginting.lecturo.domain.shared

/** A document's identity in the library. */
@JvmInline
value class DocumentId(val value: String)

/** A chat backend's identity, as persisted on a conversation. */
@JvmInline
value class ProviderId(val value: String)
