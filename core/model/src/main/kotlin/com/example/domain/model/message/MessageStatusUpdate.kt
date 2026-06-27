package com.example.domain.model.message

import com.example.domain.model.MessageStatus
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

data class MessageStatusUpdate(
    val id: MessageDraftId,
    val status: MessageStatus,
)

data class MessageStatusUpdateByOccasion(
    val occasionId: OccasionId,
    val status: MessageStatus,
)
