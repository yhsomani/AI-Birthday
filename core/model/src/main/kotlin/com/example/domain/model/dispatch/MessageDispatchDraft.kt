package com.example.domain.model.dispatch

import com.example.domain.model.MessageChannel
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

data class MessageDispatchDraft(
    val id: MessageDraftId,
    val occasionReference: OccasionId,
    val preferredChannel: MessageChannel,
    val messageText: String,
)
