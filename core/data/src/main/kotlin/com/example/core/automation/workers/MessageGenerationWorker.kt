package com.example.core.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.data.R
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.automation.AutomationSchedulePolicy
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
import com.example.core.db.dao.MemoryNoteDao
import com.example.core.db.dao.GiftHistoryDao
import com.example.core.resilience.StructuredLogger
import com.example.domain.automation.AiAutoSendQualityGate
import com.example.domain.automation.AutoSendChannelSelector
import com.example.domain.automation.ApprovalModeResolver
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
    private val memoryNoteDao: MemoryNoteDao,
    private val giftHistoryDao: GiftHistoryDao,
    private val gemini: GeminiClient,
    private val prefs: SecurePrefs
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!prefs.isAiWishGenerationEnabled()) {
            StructuredLogger.i(TAG, "AI wish generation disabled; skipping worker")
            return Result.success()
        }

        val apiKey = prefs.getGeminiApiKey()
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (apiKey.isNullOrBlank() && firebaseUser == null) {
            StructuredLogger.w(TAG, "Gemini API key not configured and user not authenticated — skipping worker")
            com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                applicationContext,
                applicationContext.getString(R.string.notification_setup_ai_title),
                applicationContext.getString(R.string.notification_setup_ai_message),
            )
            return Result.failure()
        }

        return try {
            val prompter = PromptBuilder()
            // Prepare a week of AI drafts so Smart Approve has a useful review window
            // while exact dispatch still happens at each contact's scheduled send time.
            val lookaheadEndMs = System.currentTimeMillis() + MESSAGE_GENERATION_LOOKAHEAD_MS

            val upcomingEvents = eventDao.getEventsBefore(lookaheadEndMs)
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
                            if (contact.skipAutoWish) {
                                StructuredLogger.i(TAG, "Skipping automatic message generation for contact ${contact.id}; skip-auto-wish is enabled")
                                return@async
                            }

                            val styleProfile = styleProfileDao.get()
                            val previousMessages = sentMessageDao.getByContact(contact.id)
                            val memoryNotes = memoryNoteDao.getByContact(contact.id)
                            val giftHistory = giftHistoryDao.getByContact(contact.id)

                            val contextObj = prompter.buildContactContext(
                                contact = contact,
                                event = event,
                                styleProfile = styleProfile,
                                previousMessages = previousMessages,
                                memoryNotes = memoryNotes,
                                giftHistory = giftHistory,
                            )

                            val messageId = existingPending?.id ?: java.util.UUID.randomUUID().toString()

                            RateLimiter.waitIfNeeded()
                            var prompt = prompter.buildMessageGenerationPrompt(contextObj)
                            var responseString = gemini.generate(prompt)
                            var variants = ResponseParser.parseMessageVariants(
                                responseString,
                                eventType = event.type
                            )

                            // Anti-repetition check
                            var retries = 0
                            while (retries < 2 && isPreviouslyUsed(contact.id, variants.standard)) {
                                RateLimiter.waitIfNeeded()
                                prompt = prompter.buildRegenerationPrompt(variants.standard, contextObj)
                                responseString = gemini.generate(prompt)
                                variants = ResponseParser.parseMessageVariants(
                                    responseString,
                                    eventType = event.type
                                )
                                retries++
                            }
                            if (variants.isUsingFallback) {
                                try {
                                    com.example.core.automation.notifications.NotificationHelper.showSystemAlert(
                                        applicationContext,
                                        applicationContext.getString(R.string.notification_ai_fallback_title),
                                        applicationContext.getString(R.string.notification_ai_fallback_message),
                                    )
                                } catch (e: Exception) {
                                    StructuredLogger.e(TAG, "Failed to show AI fallback alert", e)
                                }
                            }

                            val globalMode = prefs.getGlobalAutomationMode()
                            val requestedApprovalMode = ApprovalModeResolver.resolve(
                                relationship = contact.relationshipType,
                                contactOverride = contact.automationMode,
                                globalMode = globalMode,
                            )
                            val selectedVariantText = variants.get(variants.recommended)
                            val qualityDecision = AiAutoSendQualityGate.evaluate(
                                requestedMode = requestedApprovalMode,
                                selectedMessage = selectedVariantText,
                                isUsingFallback = variants.isUsingFallback,
                            )
                            val approvalMode = qualityDecision.approvalMode
                            val scheduledForMs = AutomationSchedulePolicy.messageSendTimeMs(
                                eventOccurrenceMs = event.nextOccurrenceMs,
                                customHour = contact.customSendTimeHour,
                                customMinute = contact.customSendTimeMinute,
                                quietHoursStart = prefs.getQuietHoursStart(),
                                quietHoursEnd = prefs.getQuietHoursEnd(),
                                blackoutDatesJson = prefs.getBlackoutDates(),
                            )
                            val selectedChannel = AutoSendChannelSelector.select(
                                contact = contact,
                                previousMessages = previousMessages,
                                channelBlackoutJson = prefs.getChannelBlackout(),
                                senderEmail = prefs.getSenderEmail(),
                                senderEmailPassword = prefs.getSenderEmailPassword(),
                            )

                            StructuredLogger.i(TAG, "Generated message for event ${event.id}", mapOf(
                                "contactId" to contact.id,
                                "approvalMode" to approvalMode.raw,
                                "channel" to selectedChannel,
                                "qualityScore" to qualityDecision.qualityScore.toString(),
                                "retries" to retries.toString(),
                            ))

                            pendingMessageDao.insert(PendingMessageEntity(
                                id = messageId,
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
                                channel = selectedChannel,
                                scheduledForMs = scheduledForMs,
                                approvalMode = approvalMode.raw,
                                status = if (approvalMode.raw == "FULLY_AUTO") "APPROVED" else "PENDING",
                                qualityScore = qualityDecision.qualityScore,
                                scheduledYear = scheduledYear,
                                isUsingFallback = variants.isUsingFallback
                            ))

                            if (ApprovalModeResolver.schedulesAutomaticDispatch(approvalMode)) {
                                StructuredLogger.i(TAG, "Scheduling automatic dispatch for event ${event.id}", mapOf(
                                    "approvalMode" to approvalMode.raw,
                                ))
                                DailyScheduler.scheduleExactSend(applicationContext, messageId)
                            }
                            if (ApprovalModeResolver.needsReviewNotification(approvalMode)) {
                                com.example.core.automation.notifications.NotificationHelper.showApprovalNotification(applicationContext, contact, event, variants, messageId)
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
        const val MESSAGE_GENERATION_LOOKAHEAD_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
