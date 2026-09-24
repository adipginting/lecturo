package com.adipginting.lecturo.util

import com.adipginting.lecturo.domain.chat.Message
import kotlin.math.ceil

/** Rough token count for English prose: four characters to a token. */
fun estimateTokens(chars: Int): Int = ceil(chars / 4.0).toInt()

/**
 * Tokens the next API call will carry. With the usage of the last reply known,
 * that payload plus an estimate of what arrived since (and of the draft being
 * typed) is exact at the base; without it the whole context is estimated.
 */
fun contextTokens(
    lastPromptTokens: Int?,
    lastCallAt: Long?,
    systemChars: Int,
    activeMessages: List<Message>,
    draftChars: Int,
): Int =
    if (lastPromptTokens != null && lastCallAt != null) {
        val newChars = activeMessages.filter { it.createdAt > lastCallAt }
            .sumOf { it.text.length }
        lastPromptTokens + estimateTokens(newChars + draftChars)
    } else {
        estimateTokens(systemChars + activeMessages.sumOf { it.text.length } + draftChars)
    }

/** "~800", "~4.2k", "~12k". */
fun formatTokenCount(tokens: Int): String = when {
    tokens < 1_000 -> "~$tokens"
    tokens < 10_000 -> "~%.1fk".format(tokens / 1_000.0)
    else -> "~${tokens / 1_000}k"
}

/** A threshold field's text as a usable threshold; zero and junk are refused. */
fun thresholdOrNull(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }
