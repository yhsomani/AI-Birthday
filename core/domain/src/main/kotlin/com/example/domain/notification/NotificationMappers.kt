package com.example.domain.notification

import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.notification.ApprovalNotificationRequest
import com.example.domain.model.notification.EventReminderNotificationRequest
import com.example.domain.model.notification.EventReminderScheduleRequest
import com.example.domain.model.occasion.Occasion

fun buildApprovalNotificationRequest(
    contact: ContactHeader,
    event: Occasion,
    messageId: String,
): ApprovalNotificationRequest {
    return ApprovalNotificationRequest(
        contactId = contact.id,
        contactDisplayName = contact.displayName,
        eventId = event.id,
        messageId = MessageDraftId(messageId),
    )
}

fun buildEventReminderNotificationRequest(
    contact: ContactHeader,
    event: Occasion,
): EventReminderNotificationRequest {
    return EventReminderNotificationRequest(
        contactId = contact.id,
        contactDisplayName = contact.displayName,
        eventId = event.id,
        eventType = event.type.raw,
    )
}

fun buildEventReminderScheduleRequest(occasion: Occasion): EventReminderScheduleRequest {
    return EventReminderScheduleRequest(
        eventId = occasion.id,
        contactId = occasion.contactId,
        nextOccurrenceMs = occasion.nextOccurrenceMs,
        notifyDaysBefore = occasion.notifyDaysBefore,
        isActive = occasion.isActive,
    )
}
