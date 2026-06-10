package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.service.EventReminderSchedulerService
import java.util.Calendar
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
        val normalizedType = request.eventType.trim().uppercase(Locale.US).ifBlank { "BIRTHDAY" }
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
                preferredChannel = "SMS",
            )
            else -> return Outcome.InvalidInput("Choose a contact or enter a new contact name.")
        }

        if (!isValidDate(request.dayOfMonth, request.month, request.year)) {
            return Outcome.InvalidInput("Enter a valid date.")
        }

        val nextOccurrenceMs = nextOccurrenceMs(request.dayOfMonth, request.month)
        val updatedContact = contact.withEventDate(
            eventType = normalizedType,
            day = request.dayOfMonth,
            month = request.month,
            year = request.year,
        )
        contactRepository.upsert(updatedContact)

        val event = EventEntity(
            id = "manual_${UUID.randomUUID()}",
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
        eventType: String,
        day: Int,
        month: Int,
        year: Int?,
    ): ContactEntity = when (eventType) {
        "BIRTHDAY" -> copy(
            birthdayDay = day,
            birthdayMonth = month,
            birthdayYear = year,
            updatedAt = System.currentTimeMillis(),
        )
        "ANNIVERSARY" -> copy(
            anniversaryDay = day,
            anniversaryMonth = month,
            anniversaryYear = year,
            updatedAt = System.currentTimeMillis(),
        )
        "WORK_ANNIVERSARY" -> copy(
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
        val eventType: String = "BIRTHDAY",
        val label: String? = null,
        val month: Int,
        val dayOfMonth: Int,
        val year: Int? = null,
        val notifyDaysBefore: Int = 1,
    )

    sealed class Outcome {
        data class Saved(val contact: ContactEntity, val event: EventEntity) : Outcome()
        data class InvalidInput(val message: String) : Outcome()
        data object ContactNotFound : Outcome()
    }

    companion object {
        fun isValidDate(day: Int, month: Int, year: Int?): Boolean {
            if (month !in 1..12 || day !in 1..31) return false
            val validationYear = year ?: 2024
            return try {
                Calendar.getInstance().apply {
                    isLenient = false
                    set(Calendar.YEAR, validationYear)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    timeInMillis
                }
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }

        fun nextOccurrenceMs(day: Int, month: Int, nowMs: Long = System.currentTimeMillis()): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            var candidateYear = calendar.get(Calendar.YEAR)
            while (true) {
                val candidate = tryCreateDate(candidateYear, month, day)
                if (candidate != null && candidate.timeInMillis >= calendar.timeInMillis) {
                    return candidate.timeInMillis
                }
                candidateYear++
            }
        }

        private fun tryCreateDate(year: Int, month: Int, day: Int): Calendar? {
            return try {
                Calendar.getInstance().apply {
                    isLenient = false
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    timeInMillis
                }
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
