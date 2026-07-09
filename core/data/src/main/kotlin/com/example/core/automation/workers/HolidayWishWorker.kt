package com.example.core.automation.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.notifications.showApprovalNotification
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.automation.sender.setupNotificationRequest
import com.example.core.automation.sender.showSetupNotification
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
import com.example.core.db.toEventEntity
import com.example.core.db.toDeliveryRouteProfile
import com.example.core.db.toHeader
import com.example.core.db.toRelationshipPromptContext
import com.example.domain.automation.AiAutoSendQualityGate
import com.example.domain.automation.AutoSendChannelSelector
import com.example.domain.automation.ApprovalModeResolver
import com.example.domain.automation.AutomationSchedulePolicy
import com.example.domain.notification.buildApprovalNotificationRequest
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.notification.SetupNotificationReason
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.UUID

@HiltWorker
class HolidayWishWorker @AssistedInject constructor(
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
            StructuredLogger.i(TAG, "AI generation disabled; skipping holiday wish worker")
            return Result.success()
        }

        val apiKey = prefs.getGeminiApiKey()
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (apiKey.isNullOrBlank() && firebaseUser == null) {
            StructuredLogger.w(TAG, "Gemini API key not configured and user not authenticated; skipping holiday worker")
            applicationContext.showSetupNotification(
                setupNotificationRequest(
                    reason = SetupNotificationReason.AI_PROVIDER_MISSING,
                )
            )
            return Result.failure()
        }

        return try {
            val now = inputData.getLong(KEY_NOW_MS, System.currentTimeMillis())
            val holidays = FixedHolidayCatalog.upcoming(now, LOOKAHEAD_DAYS)
            if (holidays.isEmpty()) {
                StructuredLogger.i(TAG, "No fixed-date holidays in the holiday wish lookahead window")
                return Result.success()
            }

            val contacts = contactDao.getAllSync()
                .asSequence()
                .filterNot { it.skipAutoWish }
                .take(MAX_CONTACTS_PER_RUN)
                .toList()
            val prompter = PromptBuilder()
            var createdCount = 0

            for (holiday in holidays) {
                for (contact in contacts) {
                    try {
                        val eventId = holidayEventId(holiday, contact)
                        if (pendingMessageDao.getByEventId(eventId) != null) {
                            continue
                        }

                        RateLimiter.waitIfNeeded()
                        val prompt = prompter.buildHolidayWishPrompt(
                            contact = contact.toRelationshipPromptContext(),
                            holidayName = holiday.name,
                            holidayTone = holiday.tone,
                        )
                        val suggestion = sanitizeGeneratedSuggestion(
                            raw = gemini.generate(prompt),
                            fallbackText = fallbackSuggestion(holiday, contact.name),
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
                        val scheduledMs = AutomationSchedulePolicy.messageSendTimeMs(
                            eventOccurrenceMs = holiday.occurrenceMs,
                            customHour = contact.customSendTimeHour,
                            customMinute = contact.customSendTimeMinute,
                            quietHoursStart = prefs.getQuietHoursStart(),
                            quietHoursEnd = prefs.getQuietHoursEnd(),
                            blackoutDatesJson = prefs.getBlackoutDates(),
                            nowMs = now,
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

                        val holidayEvent = holiday.toOccasion(eventId, contact)
                        eventDao.upsert(holidayEvent.toEventEntity())

                        val pending = PendingMessageEntity(
                            id = UUID.randomUUID().toString(),
                            contactId = contact.id,
                            eventId = eventId,
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
                            scheduledYear = holiday.year,
                            isUsingFallback = suggestion.isFallback,
                        )
                        pendingMessageDao.insert(pending)
                        createdCount++

                        if (ApprovalModeResolver.schedulesAutomaticDispatch(approvalMode)) {
                            DailyScheduler.scheduleExactSend(applicationContext, pending.id)
                        }
                        if (ApprovalModeResolver.needsReviewNotification(approvalMode)) {
                            applicationContext.showApprovalNotification(
                                request = buildApprovalNotificationRequest(contact.toHeader(), holidayEvent, pending.id),
                                variants = suggestion.toVariants(),
                            )
                        }
                    } catch (e: Exception) {
                        StructuredLogger.w(TAG, "Failed to create holiday wish for contact ${contact.id}", e)
                    }
                }
            }

            StructuredLogger.i(TAG, "Created $createdCount holiday AI wish messages")
            Result.success()
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    private fun fallbackSuggestion(holiday: HolidayOccurrence, contactName: String): String {
        val firstName = contactName.trim().substringBefore(' ').ifBlank { "there" }
        return "Happy ${holiday.name}, $firstName. Hope the day brings warmth and good moments your way."
    }

    private fun holidayEventId(holiday: HolidayOccurrence, contact: ContactEntity): String {
        return "HOLIDAY_${holiday.id}_${contact.id}_${holiday.year}"
    }

    private fun HolidayOccurrence.toOccasion(eventId: String, contact: ContactEntity): Occasion {
        val calendar = Calendar.getInstance().apply { timeInMillis = occurrenceMs }
        return Occasion(
            id = OccasionId(eventId),
            contactId = ContactId(contact.id),
            type = OccasionType.HOLIDAY,
            label = name,
            date = OccasionDate(
                dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
                month = calendar.get(Calendar.MONTH) + 1,
                year = year,
            ),
            nextOccurrenceMs = occurrenceMs,
            isActive = true,
            notifyDaysBefore = 1,
            source = "AI_INFERRED",
            confidenceScore = 100,
            isVerified = true,
        )
    }

    companion object {
        const val KEY_NOW_MS = "holiday_now_ms"
        const val LOOKAHEAD_DAYS = 7
        const val MAX_CONTACTS_PER_RUN = 50
        private const val TAG = "HolidayWishWorker"
    }
}
