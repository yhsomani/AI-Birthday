package com.example.domain.dispatch

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.MessageDispatchRequest

fun buildMessageDispatchRequest(
    message: PendingMessageEntity,
    contact: ContactEntity,
    dispatchAttemptId: String? = null,
): MessageDispatchRequest {
    return MessageDispatchRequest(
        messageId = MessageDraftId(message.id),
        contactId = ContactId(contact.id),
        occasionReference = OccasionId(message.eventId),
        preferredChannel = MessageChannel.fromRaw(message.channel),
        messageText = message.dispatchText(),
        contactDisplayName = contact.name,
        primaryPhone = contact.primaryPhone,
        primaryEmail = contact.primaryEmail,
        dispatchAttemptId = dispatchAttemptId?.let(::DispatchAttemptId),
    )
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
