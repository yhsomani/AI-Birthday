package com.example.core.resilience

object SensitiveLogRedactor {
    private const val REDACTED_MESSAGE_BODY = "[REDACTED_MESSAGE_BODY]"

    private val messageBodyKeys = setOf(
        "messagetext",
        "messagebody",
        "drafttext",
        "selectedvarianttext",
        "recommendedvarianttext",
        "recommendedtext",
        "shortvariant",
        "standardvariant",
        "longvariant",
        "formalvariant",
        "funnyvariant",
        "emotionalvariant",
        "usereditedtext",
        "payload",
    )
    private val emailPattern = Regex(
        pattern = "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
        option = RegexOption.IGNORE_CASE,
    )
    private val bearerTokenPattern = Regex(
        pattern = "(?i)Bearer\\s+[A-Za-z0-9._~+\\-/]+=*",
    )
    private val apiKeyPattern = Regex(
        pattern = "AIza[A-Za-z0-9_-]{20,}",
    )
    private val secretAssignmentPattern = Regex(
        pattern = "(?i)\\b(password|passphrase|api[_ -]?key|token|access_token|refresh_token)=([^,\\s]+)",
    )
    private val phonePattern = Regex(
        pattern = "(?<!\\d)\\+?\\d[\\d\\s().-]{7,}\\d(?!\\d)",
    )
    private val sensitiveQueryParamPattern = Regex(
        pattern = "(?i)([?&](?:access_token|token|syncToken|pageToken|key)=)[^&\\s]+",
    )
    private val peopleApiConnectionsUrlPattern = Regex(
        pattern = "https://people\\.googleapis\\.com/v1/people/me/connections\\?[^\\s]+",
    )
    private val messageBodyAssignmentPattern = Regex(
        pattern = "(?i)([\"']?(?:messageText|messageBody|draftText|selectedVariantText|recommendedVariantText|recommendedText|shortVariant|standardVariant|longVariant|formalVariant|funnyVariant|emotionalVariant|userEditedText|payload)[\"']?\\s*[:=]\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|[^,\\s}\\]]+)",
    )

    fun redact(message: String): String {
        return message
            .replace(peopleApiConnectionsUrlPattern) {
                "https://people.googleapis.com/v1/people/me/connections?[REDACTED_QUERY]"
            }
            .replace(bearerTokenPattern, "Bearer [REDACTED]")
            .replace(apiKeyPattern, "[REDACTED_API_KEY]")
            .replace(secretAssignmentPattern) { match ->
                "${match.groupValues[1]}=[REDACTED]"
            }
            .replace(messageBodyAssignmentPattern) { match ->
                "${match.groupValues[1]}$REDACTED_MESSAGE_BODY"
            }
            .replace(sensitiveQueryParamPattern) { match ->
                "${match.groupValues[1]}[REDACTED]"
            }
            .replace(phonePattern, "[REDACTED_PHONE]")
            .replace(emailPattern, "[REDACTED_EMAIL]")
    }

    fun redactValue(key: String, value: String): String {
        return if (key.isMessageBodyKey()) {
            REDACTED_MESSAGE_BODY
        } else {
            redact(value)
        }
    }

    private fun String.isMessageBodyKey(): Boolean {
        val normalized = filter { it.isLetterOrDigit() }.lowercase()
        return normalized in messageBodyKeys
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
