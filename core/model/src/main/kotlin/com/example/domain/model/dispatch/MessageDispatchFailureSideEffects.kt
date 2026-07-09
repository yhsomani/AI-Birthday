package com.example.domain.model.dispatch

data class MessageDispatchFailureSideEffects(
    val healthReport: MessageDispatchFailureHealthReport,
)

data class MessageDispatchFailureHealthReport(
    val context: String,
    val errorMessage: String,
)
