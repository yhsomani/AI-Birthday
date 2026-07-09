package com.example.core.automation.workers

import com.example.core.gemini.MessageVariants

internal data class GeneratedWorkerSuggestion(
    val text: String,
    val isFallback: Boolean,
)

internal fun sanitizeGeneratedSuggestion(
    raw: String,
    fallbackText: String,
): GeneratedWorkerSuggestion {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
        return GeneratedWorkerSuggestion(fallbackText, isFallback = true)
    }
    if (trimmed.startsWith("{") && trimmed.contains("\"error\"", ignoreCase = true)) {
        return GeneratedWorkerSuggestion(fallbackText, isFallback = true)
    }
    val text = trimmed
        .removeSurrounding("\"")
        .take(500)
    return if (text.isBlank()) {
        GeneratedWorkerSuggestion(fallbackText, isFallback = true)
    } else {
        GeneratedWorkerSuggestion(text, isFallback = false)
    }
}

internal fun GeneratedWorkerSuggestion.toVariants(): MessageVariants {
    return MessageVariants(text, text, text, text, text, text, "standard", isFallback)
}
