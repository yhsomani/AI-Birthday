package com.example.domain.automation

import com.example.domain.model.ApprovalMode

object AiAutoSendQualityGate {
    private const val FULLY_AUTO_MIN_SCORE = 70

    private val genericPhrases = listOf(
        "wishing you a very happy birthday",
        "hope you have a wonderful day",
        "many happy returns",
        "wishing you all the best",
        "have a great day",
        "hope you're doing great",
        "hope you are doing great",
        "it has been a while",
    )

    fun evaluate(
        requestedMode: ApprovalMode,
        selectedMessage: String,
        isUsingFallback: Boolean,
    ): Decision {
        val trimmed = selectedMessage.trim()
        val reasons = mutableListOf<String>()
        var score = 100

        if (trimmed.isBlank()) {
            score = minOf(score, 20)
            reasons += "blank_message"
        }

        if (isUsingFallback) {
            score = minOf(score, 35)
            reasons += "ai_fallback"
        }

        if (trimmed.length in 1..24) {
            score = minOf(score, 60)
            reasons += "too_short"
        }

        if (containsGenericPhrase(trimmed)) {
            score = minOf(score, 55)
            reasons += "generic_phrase"
        }

        val finalMode = if (requestedMode.schedulesAutomaticDispatch() && score < FULLY_AUTO_MIN_SCORE) {
            ApprovalMode.ALWAYS_ASK
        } else {
            requestedMode
        }

        return Decision(
            approvalMode = finalMode,
            qualityScore = score,
            downgradeReason = reasons.joinToString(",").takeIf { finalMode != requestedMode },
        )
    }

    private fun containsGenericPhrase(message: String): Boolean {
        val normalized = message.lowercase()
        return genericPhrases.any { it in normalized }
    }

    data class Decision(
        val approvalMode: ApprovalMode,
        val qualityScore: Int,
        val downgradeReason: String?,
    )

    private fun ApprovalMode.schedulesAutomaticDispatch(): Boolean {
        return this == ApprovalMode.FULLY_AUTO || this == ApprovalMode.SMART_APPROVE
    }
}
