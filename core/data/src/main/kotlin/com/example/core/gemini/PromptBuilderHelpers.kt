package com.example.core.gemini

import org.json.JSONArray

internal fun promptFirstName(fullName: String): String {
    val trimmed = fullName.trim()
    val spaceIdx = trimmed.indexOf(' ')
    return if (spaceIdx == -1) trimmed else trimmed.substring(0, spaceIdx)
}

internal fun sanitizePromptNotes(notes: String): String {
    var sanitized = notes
    sanitized = sanitized.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[EMAIL]")
    sanitized = sanitized.replace(
        Regex("\\+?\\d{1,4}?[-.\\s]?\\(?\\d{1,3}?\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}"),
        "[PHONE]",
    )
    return sanitized
}

internal fun parsePromptStringList(json: String): List<String> {
    return try {
        val array = JSONArray(json)
        List(array.length()) { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}
