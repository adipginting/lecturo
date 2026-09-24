package com.adipginting.lecturo.domain.chat

/** Context windows we know, matched by the first pattern a model name contains. */
private val MODEL_WINDOWS = listOf(
    "moonshot-v1-128k" to 128_000,
    "moonshot-v1-32k" to 32_000,
    "moonshot-v1-8k" to 8_000,
    "moonshot" to 8_000,
    "gpt-4.1" to 1_000_000,
    "gpt-4o" to 128_000,
    "o3" to 200_000,
    "o4" to 200_000,
    "claude" to 200_000,
    "deepseek" to 64_000,
    "gemini" to 1_000_000,
)

/** The window of a model we know, or null when we do not. */
fun modelWindow(model: String): Int? {
    val name = model.lowercase()
    return MODEL_WINDOWS.firstOrNull { name.contains(it.first) }?.second
}

private const val RESERVED_OUTPUT = 4_000
private const val SAFETY = 0.85
private const val MIN_BUDGET = 2_000
private const val MAX_BUDGET = 32_000

/** The conservative budget for a model we have never heard of. */
const val DEFAULT_CONTEXT_BUDGET = 8_000

/**
 * Tokens a conversation may carry before compaction: the model's window less
 * room for the reply and a safety margin, between a floor and a hard cap — a
 * larger window is not an invitation to spend it. An unknown model keeps the
 * default and an override wins outright.
 */
fun contextBudget(model: String, override: Int? = null): Int {
    override?.takeIf { it > 0 }?.let { return it }
    val window = modelWindow(model) ?: return DEFAULT_CONTEXT_BUDGET
    val budget = ((window - RESERVED_OUTPUT) * SAFETY).toInt()
    return budget.coerceIn(MIN_BUDGET, MAX_BUDGET)
}
