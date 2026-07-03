package com.example.core.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
import com.example.domain.repository.ContactRepository
import com.example.domain.usecase.ClassifyContactUseCase
import com.example.domain.usecase.SyncContactsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker

@HiltWorker
class ContactSyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val syncContactsUseCase: SyncContactsUseCase,
    private val contactRepository: ContactRepository,
    private val classifyContactUseCase: ClassifyContactUseCase,
    private val prefs: SecurePrefs
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            StructuredLogger.i(TAG, "Syncing contacts from shared foreground/background pipeline")

            val outcome = syncContactsUseCase(forceRefresh = false)
            StructuredLogger.i(
                TAG,
                "Contact sync completed: google=${outcome.googleCount}, device=${outcome.deviceCount}, " +
                    "inserted=${outcome.inserted}, updated=${outcome.updated}"
            )

            val canClassify = prefs.getGeminiApiKey().isNotBlank() ||
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
            if (!canClassify) {
                StructuredLogger.i(TAG, "No Gemini API key or authenticated user; skipping AI classification")
            } else {
                val unclassifiedContactIds = contactRepository.getUnclassifiedContactIds()
                StructuredLogger.i(TAG, "Classifying ${unclassifiedContactIds.size} unknown contacts")
                unclassifiedContactIds.forEach { contactId ->
                    classifyContactUseCase(contactId.value)
                }
            }

            Result.success()
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "ContactSyncWorker"
    }
}
