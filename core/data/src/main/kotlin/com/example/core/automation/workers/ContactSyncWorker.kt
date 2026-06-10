package com.example.core.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.contacts.GoogleContactsSync
import com.example.core.db.dao.ContactDao
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
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
            StructuredLogger.i(TAG, "Syncing Google contacts")

            val gSync = GoogleContactsSync(applicationContext)
            val googleContacts = gSync.fetchAll()
            val merged = googleContacts

            // 1. First, map contactGroup to relationshipType before inserting to DB
            val mappedContacts = merged.map { contact ->
                val contactGroup = contact.contactGroup
                if (contact.relationshipType == "UNKNOWN" && contactGroup != null) {
                    val groupLower = contactGroup.lowercase()
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
            StructuredLogger.i(TAG, "Upserted ${mappedContacts.size} contacts")

            // 3. Then, classify contacts that are still UNKNOWN using Gemini
            val canClassify = prefs.getGeminiApiKey().isNotBlank() ||
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
            if (!canClassify) {
                StructuredLogger.i(TAG, "No Gemini API key or authenticated user; skipping AI classification")
            } else {
                val unknownContacts = mappedContacts.filter { it.relationshipType == "UNKNOWN" }
                StructuredLogger.i(TAG, "Classifying ${unknownContacts.size} unknown contacts")
                unknownContacts.forEach { contact ->
                    classifyContactUseCase(contact.id)
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
