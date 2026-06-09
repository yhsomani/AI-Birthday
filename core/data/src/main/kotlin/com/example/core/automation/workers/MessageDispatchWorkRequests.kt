package com.example.core.automation.workers

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit

object MessageDispatchWorkRequests {
    const val KEY_EVENT_ID = "event_id"

    private val constraints = Constraints.Builder()
        .setRequiresStorageNotLow(true)
        .build()

    fun create(eventId: String): OneTimeWorkRequest {
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
