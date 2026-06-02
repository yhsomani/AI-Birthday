package com.example.automation.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
            Log.i(TAG, "Starting style analysis from sent messages...")
            styleAnalysisUseCase()
            Log.i(TAG, "Style analysis completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Style analysis failed", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "StyleAnalysisWorker"
    }
}
