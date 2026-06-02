package com.example.automation.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.contacts.ContactMerger
import com.example.contacts.DeviceContactsReader
import com.example.contacts.GoogleContactsSync
import com.example.core.db.AppDatabase
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.PromptBuilder
import com.example.core.gemini.RateLimiter
import com.example.core.gemini.ResponseParser
import com.example.core.db.dao.ContactDao
import com.example.core.prefs.SecurePrefs
import com.example.domain.usecase.ClassifyContactUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker

@HiltWorker
class ContactSyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val contactDao: ContactDao,
    private val classifyContactUseCase: ClassifyContactUseCase,
    private val prefs: SecurePrefs
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            if (prefs.getGeminiApiKey().isEmpty()) {
                Log.i(TAG, "Gemini API key not set yet; skipping sync (Result.success no-op)")
                return Result.success()
            }

            val reader = DeviceContactsReader(applicationContext)
            val gSync = GoogleContactsSync(applicationContext)

            val deviceContacts = reader.readAll()
            val googleContacts = gSync.fetchAll()
            val merged = ContactMerger.merge(deviceContacts, googleContacts)

            // 1. First, upsert all merged contacts to ensure they exist in DB with IDs
            merged.forEach { contactDao.upsert(it) }

            // 2. Then, classify contacts that are still UNKNOWN
            merged
                .filter { it.relationshipType == "UNKNOWN" }
                .forEach { contact ->
                    classifyContactUseCase(contact.id)
                }

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "ContactSyncWorker"
    }
}
