package com.example.domain.message

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.MessageDispatchDraft
import com.example.domain.model.message.MessageApprovalState
import com.example.domain.model.message.MessageDispatchState
import com.example.domain.model.message.MessageDraft
import com.example.domain.model.message.PendingMessageListItem
import com.example.domain.model.message.RetryableMessageDraft
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.message.WishPreviewReviewItem
import com.example.domain.model.message.WishPreviewVariants

fun PendingMessageEntity.toMessageDraft(): MessageDraft {
    return MessageDraft(
        id = MessageDraftId(id),
        contactId = ContactId(contactId),
        occasionId = OccasionId(eventId),
        scheduledForMs = scheduledForMs,
        approvalMode = ApprovalMode.fromRaw(approvalMode),
        status = MessageStatus.fromRaw(status),
        channel = MessageChannel.fromRaw(channel),
        scheduledYear = scheduledYear,
        qualityScore = qualityScore,
        isUsingFallback = isUsingFallback,
    )
}

fun PendingMessageEntity.toMessageApprovalState(): MessageApprovalState {
    return MessageApprovalState(
        id = MessageDraftId(id),
        selectedVariantText = selectedVariantText,
        approvalMode = ApprovalMode.fromRaw(approvalMode),
        status = MessageStatus.fromRaw(status),
        editedByUser = editedByUser,
        userEditedText = userEditedText,
    )
}

fun PendingMessageEntity.toRetryableMessageDraft(): RetryableMessageDraft {
    return RetryableMessageDraft(
        id = MessageDraftId(id),
        contactId = ContactId(contactId),
        occasionId = OccasionId(eventId),
        channel = MessageChannel.fromRaw(channel),
        status = MessageStatus.fromRaw(status),
        scheduledForMs = scheduledForMs,
    )
}

fun PendingMessageEntity.toMessageDispatchDraft(): MessageDispatchDraft {
    return MessageDispatchDraft(
        id = MessageDraftId(id),
        occasionReference = OccasionId(eventId),
        preferredChannel = MessageChannel.fromRaw(channel),
        messageText = dispatchText(),
    )
}

fun PendingMessageEntity.toMessageDispatchState(): MessageDispatchState {
    return MessageDispatchState(
        draft = toMessageDraft(),
        dispatchDraft = toMessageDispatchDraft(),
    )
}

fun PendingMessageEntity.toWishPreviewDraft(): WishPreviewDraft {
    return WishPreviewDraft(
        id = MessageDraftId(id),
        contactId = ContactId(contactId),
        occasionId = OccasionId(eventId),
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
        channel = MessageChannel.fromRaw(channel),
        scheduledForMs = scheduledForMs,
        approvalMode = ApprovalMode.fromRaw(approvalMode),
        status = MessageStatus.fromRaw(status),
        isUsingFallback = isUsingFallback,
    )
}

fun PendingMessageEntity.toWishPreviewReviewItem(): WishPreviewReviewItem {
    return WishPreviewReviewItem(
        id = MessageDraftId(id),
        contactId = ContactId(contactId),
        scheduledForMs = scheduledForMs,
        status = MessageStatus.fromRaw(status),
    )
}

fun PendingMessageEntity.toPendingMessageListItem(): PendingMessageListItem {
    return PendingMessageListItem(
        id = MessageDraftId(id),
        contactId = ContactId(contactId),
        occasionId = OccasionId(eventId),
        selectedVariantText = selectedVariantText,
        standardVariant = standardVariant,
        channel = MessageChannel.fromRaw(channel),
        scheduledForMs = scheduledForMs,
        approvalMode = ApprovalMode.fromRaw(approvalMode),
        status = MessageStatus.fromRaw(status),
        editedByUser = editedByUser,
        userEditedText = userEditedText,
    )
}

fun Iterable<PendingMessageEntity>.toPendingMessageListItems(): List<PendingMessageListItem> {
    return map { it.toPendingMessageListItem() }
}

private fun PendingMessageEntity.dispatchText(): String {
    return (if (editedByUser) userEditedText else null) ?: selectedVariantText.ifBlank {
        when (selectedVariant) {
            "short" -> shortVariant
            "long" -> longVariant
            "funny" -> funnyVariant
            "formal" -> formalVariant
            else -> standardVariant
        }
    }
}
