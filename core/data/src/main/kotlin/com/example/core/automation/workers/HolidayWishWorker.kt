package com.example.core.automation.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.data.R
import com.example.core.db.dao.ContactDao
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.UUID

@HiltWorker
class HolidayWishWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val contactDao: ContactDao,
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
            NotificationHelper.showSetupNotification(
                applicationContext,
                applicationContext.getString(R.string.notification_setup_ai_title),
                applicationContext.getString(R.string.notification_setup_ai_message),
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
                            contact = contact,
                            holidayName = holiday.name,
                            holidayTone = holiday.tone,
                        )
                        val suggestion = sanitizeSuggestion(gemini.generate(prompt), holiday, contact.name)
                        val requestedApprovalMode = ApprovalModeResolver.resolve(
                            relationship = contact.relationshipType,
                            contactOverride = contact.automationMode,
                            globalMode = prefs.getGlobalAutomationMode(),
                        )
                        val qualityDecision = AiAutoSendQualityGate.evaluate(
                            requestedMode = requestedApprovalMode,
                            selectedMessage = suggestion.text,
                            isUsingFallback = suggestion.isFallback,
                        )
                        val approvalMode = qualityDecision.approvalMode
                        val scheduledMs = AutomationSchedulePolicy.messageSendTimeMs(
                            eventOccurrenceMs = holiday.occurrenceMs,
                            customHour = contact.customSendTimeHour,
                            customMinute = contact.customSendTimeMinute,
                            quietHoursStart = prefs.getQuietHoursStart(),
                            quietHoursEnd = prefs.getQuietHoursEnd(),
                            blackoutDatesJson = prefs.getBlackoutDates(),
                            nowMs = now,
                        )
                        val previousMessages = sentMessageDao.getByContact(contact.id)
                        val selectedChannel = AutoSendChannelSelector.select(
                            contact = contact,
                            previousMessages = previousMessages,
                            channelBlackoutJson = prefs.getChannelBlackout(),
                            senderEmail = prefs.getSenderEmail(),
                            senderEmailPassword = prefs.getSenderEmailPassword(),
                        )

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
                            channel = selectedChannel,
                            scheduledForMs = scheduledMs,
                            approvalMode = approvalMode.raw,
                            status = if (approvalMode.raw == "FULLY_AUTO") "APPROVED" else "PENDING",
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
                            NotificationHelper.showApprovalNotification(
                                context = applicationContext,
                                contact = contact,
                                event = holiday.toNotificationEvent(eventId, contact),
                                variants = suggestion.toVariants(),
                                messageId = pending.id,
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

    private fun sanitizeSuggestion(raw: String, holiday: HolidayOccurrence, contactName: String): HolidaySuggestion {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return HolidaySuggestion(fallbackSuggestion(holiday, contactName), isFallback = true)
        }
        if (trimmed.startsWith("{") && trimmed.contains("\"error\"", ignoreCase = true)) {
            return HolidaySuggestion(fallbackSuggestion(holiday, contactName), isFallback = true)
        }
        val text = trimmed
            .removeSurrounding("\"")
            .take(500)
        return if (text.isBlank()) {
            HolidaySuggestion(fallbackSuggestion(holiday, contactName), isFallback = true)
        } else {
            HolidaySuggestion(text, isFallback = false)
        }
    }

    private fun fallbackSuggestion(holiday: HolidayOccurrence, contactName: String): String {
        val firstName = contactName.trim().substringBefore(' ').ifBlank { "there" }
        return "Happy ${holiday.name}, $firstName. Hope the day brings warmth and good moments your way."
    }

    private fun holidayEventId(holiday: HolidayOccurrence, contact: ContactEntity): String {
        return "HOLIDAY_${holiday.id}_${contact.id}_${holiday.year}"
    }

    private fun HolidayOccurrence.toNotificationEvent(eventId: String, contact: ContactEntity): EventEntity {
        val calendar = Calendar.getInstance().apply { timeInMillis = occurrenceMs }
        return EventEntity(
            id = eventId,
            contactId = contact.id,
            type = "HOLIDAY",
            label = name,
            dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
            month = calendar.get(Calendar.MONTH) + 1,
            year = year,
            nextOccurrenceMs = occurrenceMs,
            source = "AI_INFERRED",
        )
    }

    private fun HolidaySuggestion.toVariants(): MessageVariants {
        return MessageVariants(text, text, text, text, text, text, "standard", isFallback)
    }

    private data class HolidaySuggestion(
        val text: String,
        val isFallback: Boolean,
    )

    companion object {
        const val KEY_NOW_MS = "holiday_now_ms"
        const val LOOKAHEAD_DAYS = 7
        const val MAX_CONTACTS_PER_RUN = 50
        private const val TAG = "HolidayWishWorker"
    }
}

private data class FixedHoliday(
    val id: String,
    val name: String,
    val month: Int,
    val dayOfMonth: Int,
    val tone: String,
)

private data class HolidayOccurrence(
    val id: String,
    val name: String,
    val tone: String,
    val year: Int,
    val occurrenceMs: Long,
)

private object FixedHolidayCatalog {
    private val fixedHolidays = listOf(
        FixedHoliday("NEW_YEAR", "New Year", Calendar.JANUARY, 1, "hopeful, warm, fresh-start"),
        FixedHoliday("INDIA_REPUBLIC_DAY", "Republic Day", Calendar.JANUARY, 26, "respectful, proud, inclusive"),
        FixedHoliday("WOMENS_DAY", "International Women's Day", Calendar.MARCH, 8, "respectful, appreciative, empowering"),
        FixedHoliday("INDIA_INDEPENDENCE_DAY", "Independence Day", Calendar.AUGUST, 15, "respectful, proud, inclusive"),
        FixedHoliday("GANDHI_JAYANTI", "Gandhi Jayanti", Calendar.OCTOBER, 2, "peaceful, reflective, respectful"),
        FixedHoliday("CHRISTMAS", "Christmas", Calendar.DECEMBER, 25, "warm, festive, kind"),
    )

    fun upcoming(nowMs: Long, lookaheadDays: Int): List<HolidayOccurrence> {
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val endMs = nowMs + lookaheadDays.coerceAtLeast(0) * 24L * 60L * 60L * 1000L
        val currentYear = now.get(Calendar.YEAR)
        return listOf(currentYear, currentYear + 1)
            .flatMap { year ->
                fixedHolidays.map { holiday ->
                    val occurrenceMs = Calendar.getInstance().apply {
                        clear()
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, holiday.month)
                        set(Calendar.DAY_OF_MONTH, holiday.dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                    }.timeInMillis
                    HolidayOccurrence(
                        id = holiday.id,
                        name = holiday.name,
                        tone = holiday.tone,
                        year = year,
                        occurrenceMs = occurrenceMs,
                    )
                }
            }
            .filter { it.occurrenceMs in nowMs..endMs }
            .sortedBy { it.occurrenceMs }
    }
}
