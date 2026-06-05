package com.example.core.automation.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.contacts.ContactMerger
import com.example.core.contacts.DeviceContactsReader
import com.example.core.contacts.GoogleContactsSync
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
            val hasContactsPerm = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            val hasCallLogPerm = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
            val deviceContacts = if (hasContactsPerm || hasCallLogPerm) {
                DeviceContactsReader(applicationContext).readAll()
            } else {
                Log.i(TAG, "Contacts permissions not granted; skipping device contact sync")
                emptyList()
            }

            val gSync = GoogleContactsSync(applicationContext)
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
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
                Log.i(TAG, "User not authenticated; skipping AI classification")
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
