package com.example.core.automation.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.resilience.StructuredLogger
import com.example.domain.usecase.DiscoverEventsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EventDiscoveryWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val discoverEventsUseCase: DiscoverEventsUseCase,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val outcome = discoverEventsUseCase()
            StructuredLogger.i(TAG, "Discovered ${outcome.events} events for ${outcome.contacts} contacts")
            Result.success()
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "EventDiscoveryWorker"
    }
}
