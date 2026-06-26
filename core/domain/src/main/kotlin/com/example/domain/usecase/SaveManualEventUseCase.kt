package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.event.EventDatePolicy
import com.example.domain.event.EventIdentityPolicy
import com.example.domain.model.EventType
import com.example.domain.model.MessageChannel
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
        val normalizedType = request.eventType.normalizedEventTypeOrDefault(EventType.BIRTHDAY)
        val eventType = EventType.fromRaw(normalizedType)
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

        val existingEvents = existingEvents()
        val existingConflict = EventIdentityPolicy.findConflictingActiveEvent(
            events = existingEvents,
            contactId = contact.id,
            eventType = normalizedType,
            month = request.month,
            dayOfMonth = request.dayOfMonth,
            label = normalizedLabel,
        )

        if (!request.allowDuplicate) {
            val existingDuplicate = EventIdentityPolicy.findMatchingActiveEvent(
                events = existingEvents,
                contactId = contact.id,
                eventType = normalizedType,
                month = request.month,
                dayOfMonth = request.dayOfMonth,
                label = normalizedLabel,
            )
            if (existingDuplicate != null) {
                return Outcome.DuplicateFound(contact = contact, existingEvent = existingDuplicate)
            }
            if (existingConflict != null) {
                return Outcome.ConflictFound(
                    contact = contact,
                    existingEvent = existingConflict,
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

        val event = EventEntity(
            id = eventIdFor(
                contact = updatedContact,
                eventType = normalizedType,
                allowDuplicate = request.allowDuplicate,
            ),
            contactId = updatedContact.id,
            type = normalizedType,
            label = normalizedLabel ?: updatedContact.name,
            dayOfMonth = request.dayOfMonth,
            month = request.month,
            year = request.year,
            nextOccurrenceMs = nextOccurrenceMs,
            notifyDaysBefore = request.notifyDaysBefore.coerceIn(0, 30),
            source = "MANUAL",
            confidenceScore = 100,
            isVerified = true,
        )
        eventRepository.upsert(event)
        eventReminderSchedulerService.scheduleReminder(event)

        return Outcome.Saved(contact = updatedContact, event = event)
    }

    private fun ContactEntity.withEventDate(
        eventType: EventType,
        day: Int,
        month: Int,
        year: Int?,
    ): ContactEntity = when (eventType) {
        EventType.BIRTHDAY -> copy(
            birthdayDay = day,
            birthdayMonth = month,
            birthdayYear = year,
            updatedAt = System.currentTimeMillis(),
        )
        EventType.ANNIVERSARY -> copy(
            anniversaryDay = day,
            anniversaryMonth = month,
            anniversaryYear = year,
            updatedAt = System.currentTimeMillis(),
        )
        EventType.WORK_ANNIVERSARY -> copy(
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
        val eventType: String = EventType.BIRTHDAY.raw,
        val label: String? = null,
        val month: Int,
        val dayOfMonth: Int,
        val year: Int? = null,
        val notifyDaysBefore: Int = 1,
        val allowDuplicate: Boolean = false,
    )

    sealed class Outcome {
        data class Saved(val contact: ContactEntity, val event: EventEntity) : Outcome()
        data class InvalidInput(val reason: InvalidInputReason) : Outcome()
        data class DuplicateFound(val contact: ContactEntity, val existingEvent: EventEntity) : Outcome()
        data class ConflictFound(
            val contact: ContactEntity,
            val existingEvent: EventEntity,
            val requestedMonth: Int,
            val requestedDayOfMonth: Int,
            val requestedYear: Int?,
        ) : Outcome()
        data object ContactNotFound : Outcome()
    }

    enum class InvalidInputReason {
        MISSING_CONTACT,
        INVALID_DATE,
    }

    private suspend fun existingEvents(): List<EventEntity> {
        return runCatching { eventRepository.getAll().first() }.getOrDefault(emptyList())
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

    private fun String.normalizedEventTypeOrDefault(defaultType: EventType): String {
        val normalized = trim().uppercase(Locale.US)
        if (normalized.isBlank()) return defaultType.raw
        return EventType.fromRaw(normalized).takeUnless { it == EventType.UNKNOWN }?.raw ?: normalized
    }
}
