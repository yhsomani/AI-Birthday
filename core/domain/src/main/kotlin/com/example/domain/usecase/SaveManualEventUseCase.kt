package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.contact.toHeader
import com.example.domain.event.EventDatePolicy
import com.example.domain.event.EventIdentityPolicy
import com.example.domain.event.toEventListItem
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.MessageChannel
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
        val contact = when {
            request.existingContactId != null -> {
                contactRepository.getById(request.existingContactId)
                    ?: return Outcome.ContactNotFound
            }
            !request.newContactName.isNullOrBlank() -> ContactEntity(
                id = "manual_${UUID.randomUUID()}",
                name = request.newContactName.trim(),
                contactGroup = "Manual",
                relationshipType = "UNKNOWN",
                preferredChannel = MessageChannel.SMS.raw,
            )
            else -> return Outcome.InvalidInput(InvalidInputReason.MISSING_CONTACT)
        }

        if (!EventDatePolicy.isValidDate(request.dayOfMonth, request.month, request.year)) {
            return Outcome.InvalidInput(InvalidInputReason.INVALID_DATE)
        }

        val existingOccasions = existingOccasions()
        val existingConflict = EventIdentityPolicy.findConflictingActiveOccasion(
            occasions = existingOccasions,
            contactId = contact.id,
            occasionType = eventType.raw,
            month = request.month,
            dayOfMonth = request.dayOfMonth,
            label = normalizedLabel,
        )

        if (!request.allowDuplicate) {
            val existingDuplicate = EventIdentityPolicy.findMatchingActiveOccasion(
                occasions = existingOccasions,
                contactId = contact.id,
                occasionType = eventType.raw,
                month = request.month,
                dayOfMonth = request.dayOfMonth,
                label = normalizedLabel,
            )
            if (existingDuplicate != null) {
                return Outcome.DuplicateFound(
                    contact = contact.toHeader(),
                    existingEvent = existingDuplicate.toEventListItem(),
                )
            }
            if (existingConflict != null) {
                return Outcome.ConflictFound(
                    contact = contact.toHeader(),
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
        val updatedContact = if (shouldUpdateContactEventDate) {
            contact.withEventDate(
                eventType = eventType,
                day = request.dayOfMonth,
                month = request.month,
                year = request.year,
            )
        } else {
            contact
        }
        if (shouldUpdateContactEventDate) {
            contactRepository.upsert(updatedContact)
        }

        val event = Occasion(
            id = OccasionId(
                eventIdFor(
                    contact = updatedContact,
                    eventType = eventType.raw,
                    allowDuplicate = request.allowDuplicate,
                )
            ),
            contactId = ContactId(updatedContact.id),
            type = eventType,
            label = normalizedLabel ?: updatedContact.name,
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
            contact = updatedContact.toHeader(),
            event = event.toEventListItem(),
        )
    }

    private fun ContactEntity.withEventDate(
        eventType: OccasionType,
        day: Int,
        month: Int,
        year: Int?,
    ): ContactEntity = when (eventType) {
        OccasionType.BIRTHDAY -> copy(
            birthdayDay = day,
            birthdayMonth = month,
            birthdayYear = year,
            updatedAt = System.currentTimeMillis(),
        )
        OccasionType.ANNIVERSARY -> copy(
            anniversaryDay = day,
            anniversaryMonth = month,
            anniversaryYear = year,
            updatedAt = System.currentTimeMillis(),
        )
        OccasionType.WORK_ANNIVERSARY -> copy(
            workStartDay = day,
            workStartMonth = month,
            workStartYear = year,
            updatedAt = System.currentTimeMillis(),
        )
        else -> copy(updatedAt = System.currentTimeMillis())
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
        contact: ContactEntity,
        eventType: String,
        allowDuplicate: Boolean,
    ): String {
        if (allowDuplicate) return "manual_${UUID.randomUUID()}"
        return EventIdentityPolicy.canonicalId(contact.id, eventType) ?: "manual_${UUID.randomUUID()}"
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
