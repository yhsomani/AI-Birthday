package com.example.domain.model.dispatch

import com.example.domain.model.common.MessageDraftId

data class MessageDispatchFailureSideEffects(
    val healthReport: MessageDispatchFailureHealthReport,
    val deadLetterCommand: MessageDispatchDeadLetterCommand?,
)

data class MessageDispatchFailureHealthReport(
    val context: String,
    val errorMessage: String,
)

data class MessageDispatchDeadLetterCommand(
    val messageId: MessageDraftId,
    val payload: String,
    val errorMessage: String,
    val errorType: String,
    val retryCount: Int,
)
