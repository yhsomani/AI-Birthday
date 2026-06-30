package com.example.domain.automation

object EmailAddressSyntaxPolicy {
    private val emailPattern = Regex(
        pattern = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        option = RegexOption.IGNORE_CASE,
    )

    fun isUsableAddress(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        if (trimmed.any(Char::isWhitespace)) return false
        return emailPattern.matches(trimmed)
    }

    fun isConfiguredSender(senderEmail: String, senderEmailPassword: String): Boolean {
        return isUsableAddress(senderEmail) && senderEmailPassword.isNotBlank()
    }
}
