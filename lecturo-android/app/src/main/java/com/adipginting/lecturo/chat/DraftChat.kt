package com.adipginting.lecturo.chat

/**
 * What a draft's composer shows: the excerpt it was fired from, and where in
 * the document it sits. Excerpts can be long, so this is built from the stored
 * conversation rather than passed as navigation arguments.
 */
data class DraftChatArgs(
    val text: String,
    val docTitle: String?,
    val locator: String?,
    /** The document this excerpt came from; a draft is that document's chat. */
    val docId: String,
    /** Screenshot captured as the draft's context, as a ChatImages file path. */
    val imagePath: String? = null,
)
