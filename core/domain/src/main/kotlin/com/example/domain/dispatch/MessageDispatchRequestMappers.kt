package com.example.domain.dispatch

import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.dispatch.MessageDispatchDraft
import com.example.domain.model.dispatch.MessageDispatchRecipient
import com.example.domain.model.dispatch.MessageDispatchRequest

fun buildMessageDispatchRequest(
    message: MessageDispatchDraft,
    recipient: MessageDispatchRecipient,
    dispatchAttemptId: String? = null,
): MessageDispatchRequest {
    return MessageDispatchRequest(
        messageId = message.id,
        contactId = recipient.id,
        occasionReference = message.occasionReference,
        preferredChannel = message.preferredChannel,
        messageText = message.messageText,
        contactDisplayName = recipient.displayName,
        primaryPhone = recipient.primaryPhone,
        primaryEmail = recipient.primaryEmail,
        dispatchAttemptId = dispatchAttemptId?.let(::DispatchAttemptId),
    )
}
