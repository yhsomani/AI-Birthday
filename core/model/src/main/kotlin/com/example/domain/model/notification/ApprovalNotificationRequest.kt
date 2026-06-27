package com.example.domain.model.notification

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

data class ApprovalNotificationRequest(
    val contactId: ContactId,
    val contactDisplayName: String,
    val eventId: OccasionId,
    val messageId: MessageDraftId,
)

data class EventReminderNotificationRequest(
    val contactId: ContactId,
    val contactDisplayName: String,
    val eventId: OccasionId,
    val eventType: String,
)
