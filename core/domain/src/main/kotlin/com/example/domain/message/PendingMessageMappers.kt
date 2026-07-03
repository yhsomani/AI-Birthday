package com.example.domain.message

import com.example.domain.model.dispatch.MessageDispatchDraft
import com.example.domain.model.message.MessageApprovalState
import com.example.domain.model.message.MessageDispatchState
import com.example.domain.model.message.MessageDraft
import com.example.domain.model.message.PendingMessageRecord
import com.example.domain.model.message.PendingMessageListItem
import com.example.domain.model.message.RetryableMessageDraft
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.message.WishPreviewReviewItem
import com.example.domain.model.message.WishPreviewVariants

fun PendingMessageRecord.toMessageDraft(): MessageDraft {
    return MessageDraft(
        id = id,
        contactId = contactId,
        occasionId = occasionId,
        scheduledForMs = scheduledForMs,
        approvalMode = approvalMode,
        status = status,
        channel = channel,
        scheduledYear = scheduledYear,
        qualityScore = qualityScore,
        isUsingFallback = isUsingFallback,
    )
}

fun PendingMessageRecord.toMessageApprovalState(): MessageApprovalState {
    return MessageApprovalState(
        id = id,
        selectedVariantText = selectedVariantText,
        approvalMode = approvalMode,
        status = status,
        editedByUser = editedByUser,
        userEditedText = userEditedText,
    )
}

fun PendingMessageRecord.toRetryableMessageDraft(): RetryableMessageDraft {
    return RetryableMessageDraft(
        id = id,
        contactId = contactId,
        occasionId = occasionId,
        channel = channel,
        status = status,
        scheduledForMs = scheduledForMs,
    )
}

fun PendingMessageRecord.toMessageDispatchDraft(): MessageDispatchDraft {
    return MessageDispatchDraft(
        id = id,
        occasionReference = occasionId,
        preferredChannel = channel,
        messageText = selectedDispatchText(),
    )
}

fun PendingMessageRecord.toMessageDispatchState(): MessageDispatchState {
    return MessageDispatchState(
        draft = toMessageDraft(),
        dispatchDraft = toMessageDispatchDraft(),
    )
}

fun PendingMessageRecord.toWishPreviewDraft(): WishPreviewDraft {
    return WishPreviewDraft(
        id = id,
        contactId = contactId,
        occasionId = occasionId,
        variants = WishPreviewVariants(
            short = shortVariant,
            standard = standardVariant,
            long = longVariant,
            formal = formalVariant,
            funny = funnyVariant,
            emotional = emotionalVariant,
        ),
        selectedVariant = selectedVariant,
        selectedVariantText = selectedVariantText,
        channel = channel,
        scheduledForMs = scheduledForMs,
        approvalMode = approvalMode,
        status = status,
        isUsingFallback = isUsingFallback,
    )
}

fun PendingMessageRecord.toWishPreviewReviewItem(): WishPreviewReviewItem {
    return WishPreviewReviewItem(
        id = id,
        contactId = contactId,
        scheduledForMs = scheduledForMs,
        status = status,
    )
}

fun PendingMessageRecord.toPendingMessageListItem(): PendingMessageListItem {
    return PendingMessageListItem(
        id = id,
        contactId = contactId,
        occasionId = occasionId,
        selectedVariantText = selectedVariantText,
        standardVariant = standardVariant,
        channel = channel,
        scheduledForMs = scheduledForMs,
        approvalMode = approvalMode,
        status = status,
        editedByUser = editedByUser,
        userEditedText = userEditedText,
        qualityScore = qualityScore,
        isUsingFallback = isUsingFallback,
    )
}

fun Iterable<PendingMessageRecord>.toPendingMessageListItems(): List<PendingMessageListItem> {
    return map { it.toPendingMessageListItem() }
}
