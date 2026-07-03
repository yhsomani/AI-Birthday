package com.example.domain.usecase

import com.example.domain.event.EventDatePolicy
import com.example.domain.event.EventIdentityPolicy
import com.example.domain.event.toEventListItem
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import com.example.domain.notification.buildEventReminderScheduleRequest
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.service.EventReminderSchedulerService
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveManualEventUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val eventReminderSchedulerService: EventReminderSchedulerService,
) {
    suspend operator fun invoke(request: Request): Outcome {
        val eventType = request.eventType.normalizedOccasionTypeOrDefault(OccasionType.BIRTHDAY)
            ?: return Outcome.InvalidInput(InvalidInputReason.UNSUPPORTED_EVENT_TYPE)
        val normalizedLabel = request.label?.trim()?.ifBlank { null }
        val isNewContact = request.existingContactId == null
        val contact = when {
            request.existingContactId != null -> {
                contactRepository.getHeader(request.existingContactId)
                    ?: return Outcome.ContactNotFound
            }
            !request.newContactName.isNullOrBlank() -> ContactHeader(
                id = ContactId("manual_${UUID.randomUUID()}"),
                displayName = request.newContactName.trim(),
            )
            else -> return Outcome.InvalidInput(InvalidInputReason.MISSING_CONTACT)
        }

        if (!EventDatePolicy.isValidDate(request.dayOfMonth, request.month, request.year)) {
            return Outcome.InvalidInput(InvalidInputReason.INVALID_DATE)
        }

        val existingOccasions = existingOccasions()
        val existingConflict = EventIdentityPolicy.findConflictingActiveOccasion(
            occasions = existingOccasions,
            contactId = contact.id.value,
            occasionType = eventType.raw,
            month = request.month,
            dayOfMonth = request.dayOfMonth,
            label = normalizedLabel,
        )

        if (!request.allowDuplicate) {
            val existingDuplicate = EventIdentityPolicy.findMatchingActiveOccasion(
                occasions = existingOccasions,
                contactId = contact.id.value,
                occasionType = eventType.raw,
                month = request.month,
                dayOfMonth = request.dayOfMonth,
                label = normalizedLabel,
            )
            if (existingDuplicate != null) {
                return Outcome.DuplicateFound(
                    contact = contact,
                    existingEvent = existingDuplicate.toEventListItem(),
                )
            }
            if (existingConflict != null) {
                return Outcome.ConflictFound(
                    contact = contact,
                    existingEvent = existingConflict.toEventListItem(),
                    requestedMonth = request.month,
                    requestedDayOfMonth = request.dayOfMonth,
                    requestedYear = request.year,
                )
            }
        }

        val nextOccurrenceMs = EventDatePolicy.nextOccurrenceMs(request.dayOfMonth, request.month)
            ?: return Outcome.InvalidInput(InvalidInputReason.INVALID_DATE)
        val shouldUpdateContactEventDate = existingConflict == null
        if (shouldUpdateContactEventDate) {
            val nowMs = System.currentTimeMillis()
            if (isNewContact) {
                contactRepository.createManualContactForEvent(
                    id = contact.id,
                    displayName = contact.displayName,
                    eventType = eventType,
                    day = request.dayOfMonth,
                    month = request.month,
                    year = request.year,
                    createdAt = nowMs,
                )
            } else {
                contactRepository.updateContactEventDate(
                    id = contact.id,
                    eventType = eventType,
                    day = request.dayOfMonth,
                    month = request.month,
                    year = request.year,
                    updatedAt = nowMs,
                )
            }
        }

        val event = Occasion(
            id = OccasionId(
                eventIdFor(
                    contact = contact,
                    eventType = eventType.raw,
                    allowDuplicate = request.allowDuplicate,
                )
            ),
            contactId = contact.id,
            type = eventType,
            label = normalizedLabel ?: contact.displayName,
            date = OccasionDate(
                dayOfMonth = request.dayOfMonth,
                month = request.month,
                year = request.year,
            ),
            nextOccurrenceMs = nextOccurrenceMs,
            isActive = true,
            notifyDaysBefore = request.notifyDaysBefore.coerceIn(0, 30),
            source = "MANUAL",
            confidenceScore = 100,
            isVerified = true,
        )
        eventRepository.upsertOccasion(event)
        eventReminderSchedulerService.scheduleReminder(buildEventReminderScheduleRequest(event))

        return Outcome.Saved(
            contact = contact,
            event = event.toEventListItem(),
        )
    }

    data class Request(
        val existingContactId: String? = null,
        val newContactName: String? = null,
        val eventType: String = OccasionType.BIRTHDAY.raw,
        val label: String? = null,
        val month: Int,
        val dayOfMonth: Int,
        val year: Int? = null,
        val notifyDaysBefore: Int = 1,
        val allowDuplicate: Boolean = false,
    )

    sealed class Outcome {
        data class Saved(val contact: ContactHeader, val event: EventListItem) : Outcome()
        data class InvalidInput(val reason: InvalidInputReason) : Outcome()
        data class DuplicateFound(val contact: ContactHeader, val existingEvent: EventListItem) : Outcome()
        data class ConflictFound(
            val contact: ContactHeader,
            val existingEvent: EventListItem,
            val requestedMonth: Int,
            val requestedDayOfMonth: Int,
            val requestedYear: Int?,
        ) : Outcome()
        data object ContactNotFound : Outcome()
    }

    enum class InvalidInputReason {
        MISSING_CONTACT,
        INVALID_DATE,
        UNSUPPORTED_EVENT_TYPE,
    }

    private suspend fun existingOccasions(): List<Occasion> {
        return runCatching { eventRepository.getOccasions().first() }.getOrDefault(emptyList())
    }

    private fun eventIdFor(
        contact: ContactHeader,
        eventType: String,
        allowDuplicate: Boolean,
    ): String {
        if (allowDuplicate) return "manual_${UUID.randomUUID()}"
        return EventIdentityPolicy.canonicalId(contact.id.value, eventType) ?: "manual_${UUID.randomUUID()}"
    }

    companion object {
        fun isValidDate(day: Int, month: Int, year: Int?): Boolean {
            return EventDatePolicy.isValidDate(day, month, year)
        }

        fun nextOccurrenceMs(day: Int, month: Int, nowMs: Long = System.currentTimeMillis()): Long {
            return EventDatePolicy.nextOccurrenceMs(day, month, nowMs)
                ?: throw IllegalArgumentException("Invalid event date")
        }
    }

    private fun String.normalizedOccasionTypeOrDefault(defaultType: OccasionType): OccasionType? {
        val normalized = trim().uppercase(Locale.US)
        if (normalized.isBlank()) return defaultType
        return OccasionType.fromRaw(normalized).takeUnless { it == OccasionType.UNKNOWN }
    }
}
