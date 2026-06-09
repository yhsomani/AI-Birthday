package com.example.core.automation.workers

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit

object MessageDispatchWorkRequests {
    const val KEY_PENDING_MESSAGE_ID = "pending_message_id"
    const val KEY_EVENT_ID = "event_id"

    private val constraints = Constraints.Builder()
        .setRequiresStorageNotLow(true)
        .build()

    fun create(pendingMessageId: String, eventId: String? = null): OneTimeWorkRequest {
        val data = Data.Builder()
            .putString(KEY_PENDING_MESSAGE_ID, pendingMessageId)
            .apply {
                if (!eventId.isNullOrBlank()) {
                    putString(KEY_EVENT_ID, eventId)
                }
            }
            .build()

        return OneTimeWorkRequestBuilder<MessageDispatchWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
    }

    fun createForEvent(eventId: String): OneTimeWorkRequest {
        val data = Data.Builder()
            .putString(KEY_EVENT_ID, eventId)
            .build()

        return OneTimeWorkRequestBuilder<MessageDispatchWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
    }
}
