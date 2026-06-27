package com.example.domain.model.message

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId

data class PendingMessageListItem(
    val id: MessageDraftId,
    val contactId: ContactId,
    val occasionId: OccasionId,
    val selectedVariantText: String,
    val standardVariant: String,
    val channel: MessageChannel,
    val scheduledForMs: Long,
    val approvalMode: ApprovalMode,
    val status: MessageStatus,
    val editedByUser: Boolean,
    val userEditedText: String?,
)

data class SentMessageListItem(
    val id: SentMessageId,
    val contactId: ContactId?,
    val occasionType: String,
    val messageText: String,
    val channel: MessageChannel,
    val sentAtMs: Long,
    val deliveryStatus: MessageDeliveryStatus,
)
