package com.example.ui.viewmodel

import com.example.R
import com.example.domain.message.WishDraftReadiness as DomainWishDraftReadiness
import com.example.domain.message.WishPreviewSendSummary as DomainWishPreviewSendSummary
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipActionReadinessPolicy
import com.example.domain.readiness.RelationshipReadinessAction
import com.example.domain.readiness.RelationshipReadinessState
import com.example.ui.feedback.FeedbackEvent

typealias WishDraftReadiness = DomainWishDraftReadiness
typealias WishPreviewSendSummary = DomainWishPreviewSendSummary

data class AiFeedbackOption(
    val key: String,
    val labelRes: Int,
    val instruction: String,
)

data class WhySignal(
    val labelRes: Int,
    val value: String,
)

data class ReviewNextTarget(
    val contactId: String,
    val messageRef: String,
)

internal val aiFeedbackOptions = listOf(
    AiFeedbackOption(
        key = "too_generic",
        labelRes = R.string.wish_feedback_too_generic,
        instruction = "Make it more personal. Use a specific memory, interest, nickname, or relationship detail from the contact context.",
    ),
    AiFeedbackOption(
        key = "too_formal",
        labelRes = R.string.wish_feedback_too_formal,
        instruction = "Make it more casual and natural, like a real personal message instead of a polished greeting.",
    ),
    AiFeedbackOption(
        key = "wrong_language",
        labelRes = R.string.wish_feedback_wrong_language,
        instruction = "Regenerate in the contact's preferred language and keep the wording culturally natural.",
    ),
    AiFeedbackOption(
        key = "too_long",
        labelRes = R.string.wish_feedback_too_long,
        instruction = "Make the message shorter, tighter, and easier to send without losing warmth.",
    ),
    AiFeedbackOption(
        key = "not_warm",
        labelRes = R.string.wish_feedback_not_warm,
        instruction = "Make it warmer and more emotionally specific without sounding dramatic or artificial.",
    ),
    AiFeedbackOption(
        key = "repetitive",
        labelRes = R.string.wish_feedback_repetitive,
        instruction = "Avoid the current wording and any previous wishes. Use a different structure, reference, and opening line.",
    ),
)

data class WishPreviewUiState(
    val previewDraft: WishPreviewDraft? = null,
    val selectedVariant: String = "standard",
    val editedText: String = "",
    val isLoading: Boolean = true,
    val isApproving: Boolean = false,
    val isRejecting: Boolean = false,
    val isRegenerating: Boolean = false,
    val isTestingSend: Boolean = false,
    val approved: Boolean = false,
    val rejected: Boolean = false,
    val errorMessageRes: Int? = null,
    val testSent: Boolean = false,
    val usedFallback: Boolean = false,
    val qualityMessageRes: Int? = null,
    val qualityMessageArgRes: Int? = null,
    val feedbackOptions: List<AiFeedbackOption> = aiFeedbackOptions,
    val selectedFeedbackKey: String? = null,
    val feedbackMessageRes: Int? = null,
    val feedbackEvent: FeedbackEvent? = null,
    val whySignals: List<WhySignal> = emptyList(),
    val nextReviewTarget: ReviewNextTarget? = null,
    val remainingReviewCount: Int = 0,
    val sendSummary: WishPreviewSendSummary? = null,
    val draftReadiness: WishDraftReadiness = WishDraftReadiness.READY,
) {
    val draftActionReadiness: RelationshipActionReadiness
        get() = draftReadiness.toDraftActionReadiness(previewDraft)

    val sendActionReadiness: RelationshipActionReadiness?
        get() = sendSummary?.toSendActionReadiness(previewDraft)

    val blocksApproval: Boolean
        get() = draftActionReadiness.blocksDraftApproval
}

internal fun WishDraftReadiness.toDraftActionReadiness(
    previewDraft: WishPreviewDraft?,
): RelationshipActionReadiness {
    return RelationshipActionReadinessPolicy.fromWishDraftReadiness(
        readiness = this,
        relatedMessageId = previewDraft?.id?.value,
        relatedContactId = previewDraft?.contactId?.value,
        relatedEventId = previewDraft?.occasionId?.value,
    )
}

private fun WishPreviewSendSummary.toSendActionReadiness(
    previewDraft: WishPreviewDraft?,
): RelationshipActionReadiness {
    return RelationshipActionReadinessPolicy.fromWishPreviewSendSummary(
        summary = this,
        relatedMessageId = previewDraft?.id?.value,
        relatedContactId = previewDraft?.contactId?.value,
        relatedEventId = previewDraft?.occasionId?.value,
    )
}

internal val RelationshipActionReadiness.blocksDraftApproval: Boolean
    get() = state == RelationshipReadinessState.ACTION_REQUIRED &&
        primaryAction == RelationshipReadinessAction.EDIT_DRAFT
