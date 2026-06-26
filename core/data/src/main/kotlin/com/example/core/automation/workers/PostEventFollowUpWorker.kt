package com.example.core.automation.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.data.R
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.MessageVariants
import com.example.core.gemini.PromptBuilder
import com.example.core.gemini.RateLimiter
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
import com.example.domain.automation.AiAutoSendQualityGate
import com.example.domain.automation.AutoSendChannelSelector
import com.example.domain.automation.ApprovalModeResolver
import com.example.domain.automation.AutomationSchedulePolicy
import com.example.domain.model.ApprovalMode
import com.example.domain.model.EventType
import com.example.domain.model.MessageStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.UUID

@HiltWorker
class PostEventFollowUpWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val contactDao: ContactDao,
    private val eventDao: EventDao,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val gemini: GeminiClient,
    private val prefs: SecurePrefs,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!prefs.isAiWishGenerationEnabled()) {
            StructuredLogger.i(TAG, "AI generation disabled; skipping post-event follow-up worker")
            return Result.success()
        }

        val apiKey = prefs.getGeminiApiKey()
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (apiKey.isNullOrBlank() && firebaseUser == null) {
            StructuredLogger.w(TAG, "Gemini API key not configured and user not authenticated; skipping follow-up worker")
            NotificationHelper.showSetupNotification(
                applicationContext,
                applicationContext.getString(R.string.notification_setup_ai_title),
                applicationContext.getString(R.string.notification_setup_ai_message),
            )
            return Result.failure()
        }

        return try {
            val now = System.currentTimeMillis()
            val candidates = sentMessageDao.getPostEventFollowUpCandidates(
                windowStartMs = now - FOLLOW_UP_WINDOW_END_MS,
                windowEndMs = now - FOLLOW_UP_WINDOW_START_MS,
                limit = FOLLOW_UP_CANDIDATE_LIMIT,
            )
            val prompter = PromptBuilder()
            var createdCount = 0

            candidates.forEach { sent ->
                try {
                    val contactId = sent.contactId ?: return@forEach
                    val followUpEventId = followUpEventId(sent.id)
                    if (pendingMessageDao.getByEventId(followUpEventId) != null) {
                        return@forEach
                    }

                    val contact = contactDao.getById(contactId) ?: return@forEach
                    if (contact.skipAutoWish) {
                        StructuredLogger.i(TAG, "Skipping follow-up for contact ${contact.id}; skip-auto-wish is enabled")
                        return@forEach
                    }

                    val originalEvent = eventDao.getById(sent.eventType)
                    RateLimiter.waitIfNeeded()
                    val prompt = prompter.buildPostEventFollowUpPrompt(
                        contact = contact,
                        originalMessage = sent.messageText,
                        eventType = originalEvent?.type,
                        eventLabel = originalEvent?.label,
                    )
                    val suggestion = sanitizeSuggestion(gemini.generate(prompt), contact.name)
                    val requestedApprovalMode = ApprovalModeResolver.resolve(
                        relationship = contact.relationshipType,
                        contactOverride = ApprovalMode.fromRaw(contact.automationMode),
                        globalMode = prefs.getGlobalApprovalMode(),
                    )
                    val qualityDecision = AiAutoSendQualityGate.evaluate(
                        requestedMode = requestedApprovalMode,
                        selectedMessage = suggestion.text,
                        isUsingFallback = suggestion.isFallback,
                    )
                    val scheduledMs = AutomationSchedulePolicy.nextAllowedSendMs(
                        candidateMs = now + FOLLOW_UP_SEND_DELAY_MS,
                        quietHoursStart = prefs.getQuietHoursStart(),
                        quietHoursEnd = prefs.getQuietHoursEnd(),
                        blackoutDatesJson = prefs.getBlackoutDates(),
                        nowMs = now,
                    )
                    val previousMessages = sentMessageDao.getByContact(contact.id)
                    val channelSelection = AutoSendChannelSelector.selectRoute(
                        contact = contact,
                        previousMessages = previousMessages,
                        channelBlackoutJson = prefs.getChannelBlackout(),
                        senderEmail = prefs.getSenderEmail(),
                        senderEmailPassword = prefs.getSenderEmailPassword(),
                    )
                    val approvalMode = if (channelSelection.hasAvailableRoute) {
                        qualityDecision.approvalMode
                    } else {
                        ApprovalMode.ALWAYS_ASK
                    }

                    val status = if (approvalMode == ApprovalMode.FULLY_AUTO) {
                        MessageStatus.APPROVED
                    } else {
                        MessageStatus.PENDING
                    }

                    val pending = PendingMessageEntity(
                        id = UUID.randomUUID().toString(),
                        contactId = contact.id,
                        eventId = followUpEventId,
                        shortVariant = suggestion.text,
                        standardVariant = suggestion.text,
                        longVariant = suggestion.text,
                        formalVariant = suggestion.text,
                        funnyVariant = suggestion.text,
                        emotionalVariant = suggestion.text,
                        selectedVariant = "standard",
                        selectedVariantText = suggestion.text,
                        channel = channelSelection.channel.raw,
                        scheduledForMs = scheduledMs,
                        approvalMode = approvalMode.raw,
                        status = status.raw,
                        qualityScore = qualityDecision.qualityScore,
                        scheduledYear = Calendar.getInstance().get(Calendar.YEAR),
                        isUsingFallback = suggestion.isFallback,
                    )
                    pendingMessageDao.insert(pending)
                    createdCount++

                    if (ApprovalModeResolver.schedulesAutomaticDispatch(approvalMode)) {
                        DailyScheduler.scheduleExactSend(applicationContext, pending.id)
                    }
                    if (ApprovalModeResolver.needsReviewNotification(approvalMode)) {
                        NotificationHelper.showApprovalNotification(
                            context = applicationContext,
                            contact = contact,
                            event = notificationEvent(followUpEventId, contact, now),
                            variants = suggestion.toVariants(),
                            messageId = pending.id,
                        )
                    }
                } catch (e: Exception) {
                    StructuredLogger.w(TAG, "Failed to create post-event follow-up for sent message ${sent.id}", e)
                }
            }

            StructuredLogger.i(TAG, "Created $createdCount post-event follow-up messages")
            Result.success()
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    private fun sanitizeSuggestion(raw: String, contactName: String): FollowUpSuggestion {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return FollowUpSuggestion(fallbackSuggestion(contactName), isFallback = true)
        }
        if (trimmed.startsWith("{") && trimmed.contains("\"error\"", ignoreCase = true)) {
            return FollowUpSuggestion(fallbackSuggestion(contactName), isFallback = true)
        }
        val text = trimmed
            .removeSurrounding("\"")
            .take(500)
        return if (text.isBlank()) {
            FollowUpSuggestion(fallbackSuggestion(contactName), isFallback = true)
        } else {
            FollowUpSuggestion(text, isFallback = false)
        }
    }

    private fun fallbackSuggestion(contactName: String): String {
        val firstName = contactName.trim().substringBefore(' ').ifBlank { "there" }
        return "Hey $firstName, hope your day went nicely. How did you celebrate?"
    }

    private fun followUpEventId(sentMessageId: String): String = "FOLLOWUP_$sentMessageId"

    private fun notificationEvent(eventId: String, contact: ContactEntity, nowMs: Long): EventEntity {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        return EventEntity(
            id = eventId,
            contactId = contact.id,
            type = EventType.FOLLOW_UP.raw,
            label = "Follow-up",
            dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
            month = calendar.get(Calendar.MONTH) + 1,
            year = calendar.get(Calendar.YEAR),
            nextOccurrenceMs = nowMs,
            source = "AI_INFERRED",
        )
    }

    private fun FollowUpSuggestion.toVariants(): MessageVariants {
        return MessageVariants(text, text, text, text, text, text, "standard", isFallback)
    }

    private data class FollowUpSuggestion(
        val text: String,
        val isFallback: Boolean,
    )

    private companion object {
        const val TAG = "PostEventFollowUpWorker"
        const val FOLLOW_UP_WINDOW_START_MS = 24L * 60 * 60 * 1000L
        const val FOLLOW_UP_WINDOW_END_MS = 72L * 60 * 60 * 1000L
        const val FOLLOW_UP_SEND_DELAY_MS = 60L * 60 * 1000L
        const val FOLLOW_UP_CANDIDATE_LIMIT = 25
    }
}
