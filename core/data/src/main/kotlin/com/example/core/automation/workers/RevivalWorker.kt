package com.example.core.automation.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.PromptBuilder
import com.example.core.gemini.RateLimiter
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
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
        if (!prefs.isAiWishGenerationEnabled()) {
            StructuredLogger.i(TAG, "AI generation disabled; skipping revival worker")
            return Result.success()
        }

        val apiKey = prefs.getGeminiApiKey()
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (apiKey.isNullOrBlank() && firebaseUser == null) {
            StructuredLogger.w(TAG, "Gemini API key not configured and user not authenticated — skipping worker")
            com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                applicationContext,
                "RelateAI Setup Needed",
                "RelateAI needs your Gemini API key or a signed-in Google account to generate revival messages."
            )
            return Result.failure()
        }

        return try {
            val prompter = PromptBuilder()
            val now = System.currentTimeMillis()
            val thirtyDaysAgoMs = now - 30L * 24 * 60 * 60 * 1000L

            val contacts = contactDao.getContactsForRevival(thirtyDaysAgoMs)
            StructuredLogger.i(TAG, "Revival scan: ${contacts.size} candidates (healthScore<40, lastRevivalAttemptMs<30d)")

            contacts.forEach { contact ->
                val lastInteraction = contact.lastInteractionDate
                val days = if (lastInteraction != null) {
                    ((now - lastInteraction) / (1000 * 60 * 60 * 24)).toInt()
                } else {
                    180
                }

                RateLimiter.waitIfNeeded()
                val prompt = prompter.buildReconnectPrompt(contact, days)
                val suggestionResponse = sanitizeSuggestion(gemini.generate(prompt), contact.name)

                val scheduledMs = now + 1000 * 60 * 60

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

                contactDao.updateLastRevivalAttempt(contact.id, now)

                NotificationHelper.showRevivalNotification(
                    context = applicationContext,
                    contactName = contact.name,
                    daysSinceContact = days,
                    suggestionText = suggestionResponse,
                    contactId = contact.id
                )
            }

            StructuredLogger.i(TAG, "Created ${contacts.size} revival suggestions")
            Result.success()
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "RevivalWorker"
    }

    private fun sanitizeSuggestion(raw: String, contactName: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return fallbackSuggestion(contactName)
        }
        if (trimmed.startsWith("{") && trimmed.contains("\"error\"", ignoreCase = true)) {
            return fallbackSuggestion(contactName)
        }
        return trimmed
            .removeSurrounding("\"")
            .take(500)
            .ifBlank { fallbackSuggestion(contactName) }
    }

    private fun fallbackSuggestion(contactName: String): String {
        val firstName = contactName.trim().substringBefore(' ').ifBlank { "there" }
        return "Hey $firstName, it has been a while. Hope you are doing well. Want to catch up soon?"
    }
}
