package com.example.domain.model.dispatch

import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

data class MessageDispatchRequest(
    val messageId: MessageDraftId,
    val contactId: ContactId,
    val occasionReference: OccasionId,
    val preferredChannel: MessageChannel,
    val messageText: String,
    val contactDisplayName: String,
    val primaryPhone: String?,
    val primaryEmail: String?,
    val dispatchAttemptId: DispatchAttemptId? = null,
)
