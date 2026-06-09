package com.example.core.resilience

object SensitiveLogRedactor {
    private val emailPattern = Regex(
        pattern = "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
        option = RegexOption.IGNORE_CASE,
    )
    private val bearerTokenPattern = Regex(
        pattern = "(?i)Bearer\\s+[A-Za-z0-9._~+\\-/]+=*",
    )
    private val sensitiveQueryParamPattern = Regex(
        pattern = "(?i)([?&](?:access_token|token|syncToken|pageToken|key)=)[^&\\s]+",
    )
    private val peopleApiConnectionsUrlPattern = Regex(
        pattern = "https://people\\.googleapis\\.com/v1/people/me/connections\\?[^\\s]+",
    )

    fun redact(message: String): String {
        return message
            .replace(peopleApiConnectionsUrlPattern) {
                "https://people.googleapis.com/v1/people/me/connections?[REDACTED_QUERY]"
            }
            .replace(bearerTokenPattern, "Bearer [REDACTED]")
            .replace(sensitiveQueryParamPattern) { match ->
                "${match.groupValues[1]}[REDACTED]"
            }
            .replace(emailPattern, "[REDACTED_EMAIL]")
    }

    fun googleContactsHttpErrorSummary(statusCode: Int): String {
        val reason = when (statusCode) {
            400 -> "Google Contacts sync token or request parameters were rejected"
            401 -> "Google account authorization expired or was rejected"
            403 -> "Google Contacts access is disabled or permission was denied"
            in 500..599 -> "Google Contacts service is temporarily unavailable"
            else -> "Google Contacts request failed"
        }
        return "HTTP $statusCode: $reason"
    }
}
