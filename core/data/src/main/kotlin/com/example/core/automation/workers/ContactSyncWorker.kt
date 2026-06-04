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
            val reader = DeviceContactsReader(applicationContext)
            val gSync = GoogleContactsSync(applicationContext)

            val deviceContacts = reader.readAll()
            val googleContacts = gSync.fetchAll()
            val merged = ContactMerger.merge(deviceContacts, googleContacts)

            // 1. First, map contactGroup to relationshipType before inserting to DB
            val mappedContacts = merged.map { contact ->
                if (contact.relationshipType == "UNKNOWN" && contact.contactGroup != null) {
                    val groupLower = contact.contactGroup!!.lowercase()
                    val newRelation = when {
                        groupLower.contains("family") -> "FAMILY"
                        groupLower.contains("coworker") || groupLower.contains("work") || groupLower.contains("colleague") -> "WORK"
                        groupLower.contains("friend") -> "FRIEND"
                        else -> "ACQUAINTANCE"
                    }
                    contact.copy(relationshipType = newRelation)
                } else {
                    contact
                }
            }

            // 2. Upsert all mapped contacts to ensure they exist in DB with IDs
            mappedContacts.forEach { contactDao.upsert(it) }

            // 3. Then, classify contacts that are still UNKNOWN using Gemini
            if (prefs.getGeminiApiKey().isEmpty()) {
                Log.i(TAG, "Gemini API key not set yet; skipping AI classification")
            } else {
                mappedContacts
                    .filter { it.relationshipType == "UNKNOWN" }
                    .forEach { contact ->
                        classifyContactUseCase(contact.id)
                    }
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
