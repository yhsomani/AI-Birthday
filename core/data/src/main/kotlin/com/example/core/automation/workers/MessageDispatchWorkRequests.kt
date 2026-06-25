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

    fun create(
        pendingMessageId: String,
        eventId: String? = null,
        initialDelayMs: Long = 0L,
    ): OneTimeWorkRequest {
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
            .apply {
                if (initialDelayMs > 0L) {
                    setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                }
            }
            .build()
    }

    fun createForEvent(
        eventId: String,
        initialDelayMs: Long = 0L,
    ): OneTimeWorkRequest {
        val data = Data.Builder()
            .putString(KEY_EVENT_ID, eventId)
            .build()

        return OneTimeWorkRequestBuilder<MessageDispatchWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply {
                if (initialDelayMs > 0L) {
                    setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                }
            }
            .build()
    }
}
