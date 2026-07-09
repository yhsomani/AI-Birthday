package com.example.domain.model.message

object PromptTextFormatter {
    private val emailPattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val phonePattern = Regex("\\+?\\d{1,4}?[-.\\s]?\\(?\\d{1,3}?\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}")

    fun firstName(displayName: String): String {
        val trimmed = displayName.trim()
        val spaceIdx = trimmed.indexOf(' ')
        return if (spaceIdx == -1) trimmed else trimmed.substring(0, spaceIdx)
    }

    fun sanitizeNotes(notes: String): String {
        return notes
            .replace(emailPattern, "[EMAIL]")
            .replace(phonePattern, "[PHONE]")
    }
}
