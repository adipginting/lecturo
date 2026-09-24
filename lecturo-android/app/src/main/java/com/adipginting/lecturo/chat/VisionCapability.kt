package com.adipginting.lecturo.chat

/**
 * Known vision-capable model families, matched by model-id prefix. Defaults
 * only — the user's per-model override always wins. Anything unknown defaults
 * to not vision-capable: sending an image to a text-only model fails at the
 * API anyway, so the safer error is the local refusal.
 */
private val OPENAI_VISION_PREFIXES = listOf("gpt-4o", "gpt-4.1", "gpt-5")

private val ANTHROPIC_VISION_PREFIXES =
    listOf("claude-3", "claude-sonnet-4", "claude-opus-4")

private val KIMI_VISION_PREFIXES = listOf("kimi-k2")

/** Key under which a per-model override is stored: "openai/gpt-4o-mini". */
fun visionOverrideKey(providerId: String, modelId: String): String = "$providerId/$modelId"

/** Kimi's vision Moonshot ids embed the word: moonshot-v1-8k-vision-preview. */
private fun isKimiVisionId(id: String): Boolean =
    KIMI_VISION_PREFIXES.any(id::startsWith) ||
        (id.startsWith("moonshot-v1-") && "vision" in id)

/** The built-in verdict for a model, before user overrides. */
fun defaultSupportsVision(providerId: String, modelId: String): Boolean {
    // Whatever model the relay runs behind it is chosen to be vision-capable;
    // which one it is is the server's business, not this list's.
    if (providerId == "lecturo") return true
    val id = modelId.trim().lowercase()
    if (id.isEmpty()) return false
    return when (providerId) {
        "openai" -> OPENAI_VISION_PREFIXES.any(id::startsWith)
        "anthropic" -> ANTHROPIC_VISION_PREFIXES.any(id::startsWith)
        "kimi" -> isKimiVisionId(id)
        // OpenRouter ids embed the origin family: "openai/gpt-4o", ...
        "openrouter" -> id.substringAfter('/').let { routed ->
            OPENAI_VISION_PREFIXES.any(routed::startsWith) ||
                ANTHROPIC_VISION_PREFIXES.any(routed::startsWith) ||
                isKimiVisionId(routed)
        }
        else -> false
    }
}

/** Whether [modelId] on [providerId] accepts image input; overrides win. */
fun supportsVision(
    providerId: String,
    modelId: String,
    overrides: Map<String, Boolean> = emptyMap(),
): Boolean =
    overrides[visionOverrideKey(providerId, modelId)]
        ?: defaultSupportsVision(providerId, modelId)
