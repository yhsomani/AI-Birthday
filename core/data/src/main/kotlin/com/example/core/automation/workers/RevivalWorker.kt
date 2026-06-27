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
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.PromptBuilder
import com.example.core.gemini.RateLimiter
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
import com.example.domain.automation.AiAutoSendQualityGate
import com.example.domain.automation.AutoSendChannelSelector
import com.example.domain.automation.ApprovalModeResolver
import com.example.domain.automation.AutomationSchedulePolicy
import com.example.domain.automation.RevivalCadencePolicy
import com.example.domain.contact.toAutomationProfile
import com.example.domain.contact.toDeliveryRouteProfile
import com.example.domain.contact.toRelationshipPromptContext
import com.example.domain.event.toEventEntity
import com.example.domain.message.toMessageDraft
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.UUID

@HiltWorker
class RevivalWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val contactDao: ContactDao,
    private val eventDao: EventDao,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
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
                applicationContext.getString(R.string.notification_setup_ai_title),
                applicationContext.getString(R.string.notification_setup_revival_ai_message),
            )
            return Result.failure()
        }

        return try {
            val prompter = PromptBuilder()
            val now = System.currentTimeMillis()
            val thirtyDaysAgoMs = now - 30L * 24 * 60 * 60 * 1000L

            val contacts = contactDao.getContactsForRevival(thirtyDaysAgoMs)
            StructuredLogger.i(TAG, "Revival scan: ${contacts.size} candidates (healthScore<40, lastRevivalAttemptMs<30d)")

            var createdCount = 0
            contacts.forEach { contact ->
                val scheduledYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
                val revivalEventId = RevivalCadencePolicy.eventId(contact.id)
                val existingRevival = pendingMessageDao.getPendingMessage(contact.id, revivalEventId, scheduledYear)
                val cadenceDecision = RevivalCadencePolicy.evaluate(
                    contact = contact.toAutomationProfile(),
                    existingSameYearRevival = existingRevival?.toMessageDraft(),
                    nowMs = now,
                )
                if (!cadenceDecision.shouldCreate) {
                    StructuredLogger.i(TAG, "Skipping revival for contact ${contact.id}; ${cadenceDecision.reason}", mapOf(
                        "cadenceDays" to cadenceDecision.cadenceDays.toString(),
                    ))
                    return@forEach
                }

                val lastInteraction = contact.lastInteractionDate
                val days = if (lastInteraction != null) {
                    ((now - lastInteraction) / (1000 * 60 * 60 * 24)).toInt()
                } else {
                    180
                }

                RateLimiter.waitIfNeeded()
                val prompt = prompter.buildReconnectPrompt(contact.toRelationshipPromptContext(), days)
                val suggestion = sanitizeSuggestion(gemini.generate(prompt), contact.name)

                val scheduledMs = AutomationSchedulePolicy.nextAllowedSendMs(
                    candidateMs = now + 1000 * 60 * 60,
                    quietHoursStart = prefs.getQuietHoursStart(),
                    quietHoursEnd = prefs.getQuietHoursEnd(),
                    blackoutDatesJson = prefs.getBlackoutDates(),
                    nowMs = now,
                )
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
                val channelSelection = AutoSendChannelSelector.selectRoute(
                    contact = contact.toDeliveryRouteProfile(),
                    routeHistory = sentMessageDao.getDeliveryRouteHistoryByContact(contact.id),
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

                val revivalEvent = revivalEvent(revivalEventId, contact, scheduledMs)
                eventDao.upsert(revivalEvent.toEventEntity())

                val pendingMsg = PendingMessageEntity(
                    id = UUID.randomUUID().toString(),
                    contactId = contact.id,
                    eventId = revivalEventId,
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
                    scheduledYear = scheduledYear,
                    isUsingFallback = suggestion.isFallback,
                )
                pendingMessageDao.insert(pendingMsg)
                createdCount++

                contactDao.updateLastRevivalAttempt(contact.id, now)

                if (ApprovalModeResolver.schedulesAutomaticDispatch(approvalMode)) {
                    DailyScheduler.scheduleExactSend(applicationContext, pendingMsg.id)
                }

                if (ApprovalModeResolver.needsReviewNotification(approvalMode)) {
                    NotificationHelper.showRevivalNotification(
                        context = applicationContext,
                        contactName = contact.name,
                        daysSinceContact = days,
                        suggestionText = suggestion.text,
                        contactId = contact.id
                    )
                }
            }

            StructuredLogger.i(TAG, "Created $createdCount revival suggestions")
            Result.success()
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "RevivalWorker"
    }

    private fun sanitizeSuggestion(raw: String, contactName: String): RevivalSuggestion {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return RevivalSuggestion(fallbackSuggestion(contactName), isFallback = true)
        }
        if (trimmed.startsWith("{") && trimmed.contains("\"error\"", ignoreCase = true)) {
            return RevivalSuggestion(fallbackSuggestion(contactName), isFallback = true)
        }
        val text = trimmed
            .removeSurrounding("\"")
            .take(500)
        return if (text.isBlank()) {
            RevivalSuggestion(fallbackSuggestion(contactName), isFallback = true)
        } else {
            RevivalSuggestion(text, isFallback = false)
        }
    }

    private fun fallbackSuggestion(contactName: String): String {
        val firstName = contactName.trim().substringBefore(' ').ifBlank { "there" }
        return "Hey $firstName, it has been a while. Hope you are doing well. Want to catch up soon?"
    }

    private fun revivalEvent(eventId: String, contact: ContactEntity, occurrenceMs: Long): Occasion {
        val calendar = Calendar.getInstance().apply { timeInMillis = occurrenceMs }
        return Occasion(
            id = OccasionId(eventId),
            contactId = ContactId(contact.id),
            type = OccasionType.REVIVAL,
            label = "Revival",
            date = OccasionDate(
                dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
                month = calendar.get(Calendar.MONTH) + 1,
                year = calendar.get(Calendar.YEAR),
            ),
            nextOccurrenceMs = occurrenceMs,
            isActive = true,
            notifyDaysBefore = 0,
            source = "AI_INFERRED",
            confidenceScore = 100,
            isVerified = true,
        )
    }

    private data class RevivalSuggestion(
        val text: String,
        val isFallback: Boolean,
    )
}
