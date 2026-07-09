package com.example.ui.viewmodel

import com.example.R
import com.example.domain.message.WishDraftReadinessPolicy
import com.example.domain.message.WishPreviewSendSummaryPolicy
import com.example.domain.model.MessageStatus
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.contact.ContactWishContext
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.message.WishPreviewReviewItem
import com.example.domain.model.occasion.OccasionType

internal data class WishPreviewLiveData(
    val draft: WishPreviewDraft?,
    val contact: ContactWishContext? = null,
    val memoryCount: Int = 0,
    val giftCount: Int = 0,
    val previousWishes: Int = 0,
    val eventType: OccasionType? = null,
    val routeContact: ContactMessageContext? = null,
    val channelBlackoutJson: String? = null,
    val blackoutDatesJson: String? = null,
    val quietHoursStart: Int? = null,
    val quietHoursEnd: Int? = null,
    val senderEmail: String? = null,
    val senderEmailPassword: String? = null,
    val deviceReadiness: WishPreviewDeviceReadinessSnapshot? = null,
    val reviewQueue: List<WishPreviewReviewItem> = emptyList(),
)

internal data class WishPreviewContextData(
    val contact: ContactWishContext?,
    val memoryCount: Int,
    val giftCount: Int,
    val previousWishes: Int,
    val eventType: OccasionType?,
)

internal fun WishPreviewLiveData.toUiState(current: WishPreviewUiState): WishPreviewUiState {
    val draft = draft ?: return current.copy(
        previewDraft = null,
        isLoading = false,
        errorMessageRes = R.string.wish_preview_error_message_not_found,
    )
    val reviewQueueState = buildReviewQueueState(draft, reviewQueue)
    val preserveEditorState = !current.isLoading && current.previewDraft == draft
    val selectedVariant = if (preserveEditorState) current.selectedVariant else draft.selectedVariant
    val editedText = if (preserveEditorState) current.editedText else draft.selectedVariantText
    val qualityMessageRes = current.qualityMessageRes
        ?: if (draft.isUsingFallback) R.string.wish_preview_quality_template_used else null
    return current.copy(
        previewDraft = draft,
        selectedVariant = selectedVariant,
        editedText = editedText,
        isLoading = false,
        errorMessageRes = null,
        usedFallback = draft.isUsingFallback,
        whySignals = buildWhySignals(
            draft = draft,
            contact = contact,
            memoryCount = memoryCount,
            giftCount = giftCount,
            previousWishes = previousWishes,
        ),
        sendSummary = buildSendSummary(
            draft = draft,
            eventType = eventType,
            routeContact = routeContact,
            channelBlackoutJson = channelBlackoutJson,
            blackoutDatesJson = blackoutDatesJson,
            quietHoursStart = quietHoursStart,
            quietHoursEnd = quietHoursEnd,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
            deviceReadiness = deviceReadiness,
        ),
        draftReadiness = draft.evaluateDraftReadiness(editedText, selectedVariant),
        nextReviewTarget = reviewQueueState.nextTarget,
        remainingReviewCount = reviewQueueState.remainingReviewCount,
        qualityMessageRes = qualityMessageRes,
    )
}

internal fun WishPreviewDraft.evaluateDraftReadiness(
    draft: String,
    variant: String,
): WishDraftReadiness {
    return WishDraftReadinessPolicy.evaluate(
        draftText = draft,
        sourceText = variantText(variant),
    )
}

internal fun WishDraftReadiness.errorMessageRes(): Int {
    return when (this) {
        WishDraftReadiness.TOO_SHORT -> R.string.wish_preview_readiness_short
        WishDraftReadiness.BLANK -> R.string.wish_preview_readiness_blank
        WishDraftReadiness.READY,
        WishDraftReadiness.EDITED_READY -> R.string.wish_preview_readiness_ready
    }
}

private fun buildReviewQueueState(
    current: WishPreviewDraft,
    reviewQueue: List<WishPreviewReviewItem>,
): ReviewQueueState {
    val reviewableMessages = reviewQueue
        .filter { it.status == MessageStatus.PENDING }
        .sortedWith(compareBy<WishPreviewReviewItem> { it.scheduledForMs }.thenBy { it.id.value })
    val remainingReviewCount = reviewableMessages.count { it.id != current.id }
    val currentIndex = reviewableMessages.indexOfFirst { it.id == current.id }
    val nextMessage = when {
        remainingReviewCount == 0 -> null
        currentIndex == -1 -> reviewableMessages.firstOrNull { it.id != current.id }
        else -> reviewableMessages
            .drop(currentIndex + 1)
            .firstOrNull { it.id != current.id }
            ?: reviewableMessages.firstOrNull { it.id != current.id }
    }
    return ReviewQueueState(
        nextTarget = nextMessage?.let {
            ReviewNextTarget(contactId = it.contactId.value, messageRef = it.id.value)
        },
        remainingReviewCount = remainingReviewCount,
    )
}

private fun buildWhySignals(
    draft: WishPreviewDraft,
    contact: ContactWishContext?,
    memoryCount: Int,
    giftCount: Int,
    previousWishes: Int,
): List<WhySignal> {
    return listOf(
        WhySignal(R.string.wish_why_relationship, contact?.relationshipType ?: "UNKNOWN"),
        WhySignal(R.string.wish_why_language, contact?.preferredLanguage ?: "en"),
        WhySignal(R.string.wish_why_channel, draft.channel.raw),
        WhySignal(R.string.wish_why_tone, draft.selectedVariant),
        WhySignal(R.string.wish_why_memories, memoryCount.toString()),
        WhySignal(R.string.wish_why_gifts, giftCount.toString()),
        WhySignal(R.string.wish_why_previous, previousWishes.toString()),
    )
}

private fun buildSendSummary(
    draft: WishPreviewDraft,
    eventType: OccasionType?,
    routeContact: ContactMessageContext?,
    channelBlackoutJson: String?,
    blackoutDatesJson: String?,
    quietHoursStart: Int?,
    quietHoursEnd: Int?,
    senderEmail: String?,
    senderEmailPassword: String?,
    deviceReadiness: WishPreviewDeviceReadinessSnapshot?,
): WishPreviewSendSummary {
    return WishPreviewSendSummaryPolicy.build(
        draft = draft,
        eventType = eventType,
        quietHoursStart = quietHoursStart,
        quietHoursEnd = quietHoursEnd,
        blackoutDatesJson = blackoutDatesJson,
        routeContact = routeContact,
        channelBlackoutJson = channelBlackoutJson,
        senderEmail = senderEmail,
        senderEmailPassword = senderEmailPassword,
        preferredChannel = routeContact?.preferredChannel,
        smsAllowed = deviceReadiness?.smsAllowed,
        whatsAppConsentGranted = deviceReadiness?.whatsAppConsentGranted,
        whatsAppAccessibilityEnabled = deviceReadiness?.whatsAppAccessibilityEnabled,
        whatsAppInstalled = deviceReadiness?.whatsAppInstalled,
    )
}

private data class ReviewQueueState(
    val nextTarget: ReviewNextTarget? = null,
    val remainingReviewCount: Int = 0,
)
