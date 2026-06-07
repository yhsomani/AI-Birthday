package com.example.core.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.resilience.StructuredLogger
import com.example.domain.usecase.StyleAnalysisUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker

@HiltWorker
class StyleAnalysisWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val styleAnalysisUseCase: StyleAnalysisUseCase
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            StructuredLogger.i(TAG, "Starting style analysis from sent messages")
            styleAnalysisUseCase()
            StructuredLogger.i(TAG, "Style analysis completed successfully")
            Result.success()
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Style analysis failed", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "StyleAnalysisWorker"
    }
}
