package com.example.automation.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.automation.notifications.NotificationHelper
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.PromptBuilder
import com.example.core.gemini.RateLimiter
import com.example.core.prefs.SecurePrefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID

@HiltWorker
class RevivalWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val contactDao: ContactDao,
    private val pendingMessageDao: PendingMessageDao,
    private val gemini: GeminiClient,
    private val prefs: SecurePrefs
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            if (prefs.getGeminiApiKey().isEmpty()) {
                Log.i(TAG, "Gemini API key not set yet; skipping revival scan (Result.success no-op)")
                return Result.success()
            }

            val contacts = contactDao.getAllSync()
            val prompter = PromptBuilder()

            val now = System.currentTimeMillis()
            var revivedCount = 0

            contacts.forEach { contact ->
                val lastInteraction = contact.lastInteractionDate
                if (lastInteraction != null) {
                    val days = ((now - lastInteraction) / (1000 * 60 * 60 * 24)).toInt()
                    if (days > 90 && (contact.relationshipType == "FRIEND" || contact.relationshipType == "FAMILY")) {
                        RateLimiter.waitIfNeeded()
                        val prompt = prompter.buildReconnectPrompt(contact, days)
                        val suggestionResponse = gemini.generate(prompt)

                        val scheduledMs = now + 1000 * 60 * 60 + (revivedCount * 60000L)

                        val pendingMsg = PendingMessageEntity(
                            id = UUID.randomUUID().toString(),
                            contactId = contact.id,
                            eventId = "REVIVAL_${contact.id}",
                            shortVariant = suggestionResponse,
                            standardVariant = suggestionResponse,
                            longVariant = suggestionResponse,
                            formalVariant = suggestionResponse,
                            funnyVariant = suggestionResponse,
                            emotionalVariant = suggestionResponse,
                            selectedVariant = "standard",
                            selectedVariantText = suggestionResponse,
                            channel = contact.preferredChannel,
                            scheduledForMs = scheduledMs,
                            approvalMode = "VIP_APPROVE",
                            status = "PENDING"
                        )
                        pendingMessageDao.insert(pendingMsg)
                        NotificationHelper.showRevivalNotification(
                            context = applicationContext,
                            contactName = contact.name,
                            daysSinceContact = days,
                            suggestionText = suggestionResponse,
                            contactId = contact.id
                        )
                        revivedCount++
                    }
                }
            }

            Log.i(TAG, "Created $revivedCount revival suggestions")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "RevivalWorker"
    }
}
