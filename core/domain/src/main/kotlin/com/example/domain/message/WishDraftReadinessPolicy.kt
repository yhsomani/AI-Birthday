package com.example.domain.message

enum class WishDraftReadiness(
    val blocksApproval: Boolean,
) {
    READY(blocksApproval = false),
    EDITED_READY(blocksApproval = false),
    TOO_SHORT(blocksApproval = true),
    BLANK(blocksApproval = true),
}

object WishDraftReadinessPolicy {
    const val MIN_REVIEWED_DRAFT_LENGTH = 12

    fun evaluate(
        draftText: String,
        sourceText: String,
    ): WishDraftReadiness {
        val trimmed = draftText.trim()
        return when {
            trimmed.isBlank() -> WishDraftReadiness.BLANK
            trimmed.length < MIN_REVIEWED_DRAFT_LENGTH -> WishDraftReadiness.TOO_SHORT
            draftText != sourceText -> WishDraftReadiness.EDITED_READY
            else -> WishDraftReadiness.READY
        }
    }
}
