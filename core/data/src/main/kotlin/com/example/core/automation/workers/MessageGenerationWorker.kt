package com.example.core.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.PromptBuilder
import com.example.core.gemini.RateLimiter
import com.example.core.gemini.ResponseParser
import com.example.core.prefs.SecurePrefs
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.dao.StyleProfileDao
import com.example.core.resilience.StructuredLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker
import java.util.UUID
import java.util.Calendar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@HiltWorker
class MessageGenerationWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val contactDao: ContactDao,
    private val eventDao: EventDao,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val styleProfileDao: StyleProfileDao,
    private val gemini: GeminiClient,
    private val prefs: SecurePrefs
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val apiKey = prefs.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            StructuredLogger.w(TAG, "Gemini API key not configured — skipping worker")
            com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                applicationContext,
                "RelateAI Setup Needed",
                "RelateAI needs your Gemini API key to generate messages. Tap to configure."
            )
            return Result.failure()
        }

        return try {
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
                StructuredLogger.i(TAG, "User not authenticated; skipping message generation")
                return Result.success()
            }

            val prompter = PromptBuilder()
            val tomorrow = System.currentTimeMillis() + 24 * 60 * 60 * 1000L

            val upcomingEvents = eventDao.getEventsBefore(tomorrow)
            StructuredLogger.i(TAG, "Found ${upcomingEvents.size} upcoming events for generation")

            coroutineScope {
                val deferredList = upcomingEvents.map { event ->
                    async {
                        try {
                            val cal = Calendar.getInstance().apply { timeInMillis = event.nextOccurrenceMs }
                            val scheduledYear = cal.get(Calendar.YEAR)

                            val existingPending = pendingMessageDao.getPendingMessage(event.contactId, event.id, scheduledYear)
                            if (existingPending != null) {
                                if (existingPending.status == "PENDING") {
                                    StructuredLogger.i(TAG, "Pending message already queued for contact ${event.contactId} event ${event.id} year $scheduledYear; skipping")
                                    return@async
                                } else if (existingPending.status == "FAILED") {
                                    StructuredLogger.i(TAG, "Regenerating previously failed message for contact ${event.contactId} event ${event.id} year $scheduledYear")
                                } else {
                                    StructuredLogger.i(TAG, "Message already processed with status ${existingPending.status} for contact ${event.contactId} event ${event.id} year $scheduledYear; skipping")
                                    return@async
                                }
                            }

                            val contact = contactDao.getById(event.contactId) ?: return@async
                            val styleProfile = styleProfileDao.get()
                            val previousMessages = sentMessageDao.getByContact(contact.id)

                            val contextObj = prompter.buildContactContext(contact, event, styleProfile, previousMessages)

                            RateLimiter.waitIfNeeded()
                            var prompt = prompter.buildMessageGenerationPrompt(contextObj)
                            var responseString = gemini.generate(prompt)
                            var variants = ResponseParser.parseMessageVariants(responseString)

                            // Anti-repetition check
                            var retries = 0
                            while (retries < 2 && isPreviouslyUsed(contact.id, variants.standard)) {
                                RateLimiter.waitIfNeeded()
                                prompt = prompter.buildRegenerationPrompt(variants.standard, contextObj)
                                responseString = gemini.generate(prompt)
                                variants = ResponseParser.parseMessageVariants(responseString)
                                retries++
                            }

                            val globalMode = prefs.getGlobalAutomationMode()
                            val approvalMode = determineApprovalMode(contact.relationshipType, contact.automationMode, globalMode)

                            val selectedVariantText = variants.get(variants.recommended)

                            StructuredLogger.i(TAG, "Generated message for event ${event.id}", mapOf(
                                "contactId" to contact.id,
                                "approvalMode" to approvalMode,
                                "retries" to retries.toString(),
                            ))

                            pendingMessageDao.insert(PendingMessageEntity(
                                id = existingPending?.id ?: UUID.randomUUID().toString(),
                                contactId = contact.id,
                                eventId = event.id,
                                shortVariant = variants.short,
                                standardVariant = variants.standard,
                                longVariant = variants.long,
                                formalVariant = variants.formal,
                                funnyVariant = variants.funny,
                                emotionalVariant = variants.emotional,
                                selectedVariant = variants.recommended,
                                selectedVariantText = selectedVariantText,
                                channel = contact.preferredChannel,
                                scheduledForMs = event.nextOccurrenceMs,
                                approvalMode = approvalMode,
                                status = if (approvalMode == "FULLY_AUTO") "APPROVED" else "PENDING",
                                scheduledYear = scheduledYear
                            ))

                            if (approvalMode == "FULLY_AUTO") {
                                StructuredLogger.i(TAG, "Auto-approving message for event ${event.id}")
                                DailyScheduler.scheduleExactSend(applicationContext, event.id)
                            } else {
                                com.example.core.automation.notifications.NotificationHelper.showApprovalNotification(applicationContext, contact, event, variants)
                            }
                        } catch (e: Exception) {
                            StructuredLogger.w(TAG, "Failed to generate message for event ${event.id}", e)
                        }
                    }
                }
                deferredList.awaitAll()
            }

            Result.success()
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    private fun determineApprovalMode(relationship: String, contactOverride: String, globalMode: String): String {
        if (contactOverride != "DEFAULT" && contactOverride.isNotEmpty()) return contactOverride
        return when (relationship) {
            "FAMILY", "BEST_FRIEND" -> "VIP_APPROVE"
            "CLOSE_FRIEND", "RELATIVE" -> if(globalMode == "ALWAYS_ASK") "ALWAYS_ASK" else "SMART_APPROVE"
            else -> globalMode
        }
    }

    private suspend fun isPreviouslyUsed(contactId: String, newMessage: String): Boolean {
        val previous = sentMessageDao.getByContact(contactId)
        val newWords = newMessage.lowercase().split(" ").toSet()
        return previous.any { sent ->
            val oldWords = sent.messageText.lowercase().split(" ").toSet()
            val intersection = newWords.intersect(oldWords).size
            val union = newWords.union(oldWords).size
            if (union == 0) false else (intersection.toFloat() / union) > 0.65f
        }
    }

    private companion object {
        const val TAG = "MessageGenerationWorker"
    }
}
