package com.example.domain.usecase

import com.example.domain.event.EventDatePolicy
import com.example.domain.event.EventIdentityPolicy
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactEventDiscoveryProfile
import com.example.domain.notification.buildEventReminderScheduleRequest
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.service.EventReminderSchedulerService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers events (birthday, anniversary, work anniversary) from pure contact
 * discovery profiles and persists pure Occasion values through the event repository.
 * - Idempotent: each event has a stable id derived from contactId + type
 * - Computes nextOccurrenceMs (the next time this event will occur)
 * - Sets notifyDaysBefore (default 1)
 */
@Singleton
class DiscoverEventsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val eventReminderSchedulerService: EventReminderSchedulerService,
) {
    suspend operator fun invoke(): DiscoveryOutcome {
        val contacts = contactRepository.getEventDiscoveryProfiles()
        val existingOccasions = runCatching { eventRepository.getOccasions().first() }.getOrDefault(emptyList())
        var discovered = 0

        contacts.forEach { contact ->
            val occasions = buildOccasionsFor(contact, existingOccasions)
            occasions.forEach { occasion ->
                eventRepository.upsertOccasion(occasion)
                eventReminderSchedulerService.scheduleReminder(buildEventReminderScheduleRequest(occasion))
                discovered++
            }
        }

        return DiscoveryOutcome(contacts = contacts.size, events = discovered)
    }

    private suspend fun buildOccasionsFor(
        contact: ContactEventDiscoveryProfile,
        existingOccasions: List<Occasion>,
    ): List<Occasion> {
        val occasions = mutableListOf<Occasion>()

        buildContactOccasionOrDeactivate(
            contact = contact,
            existingOccasions = existingOccasions,
            type = OccasionType.BIRTHDAY,
            day = contact.birthdayDay,
            month = contact.birthdayMonth,
            year = contact.birthdayYear,
        )?.let(occasions::add)

        buildContactOccasionOrDeactivate(
            contact = contact,
            existingOccasions = existingOccasions,
            type = OccasionType.ANNIVERSARY,
            day = contact.anniversaryDay,
            month = contact.anniversaryMonth,
            year = contact.anniversaryYear,
        )?.let(occasions::add)

        buildContactOccasionOrDeactivate(
            contact = contact,
            existingOccasions = existingOccasions,
            type = OccasionType.WORK_ANNIVERSARY,
            day = contact.workStartDay,
            month = contact.workStartMonth,
            year = contact.workStartYear,
        )?.let(occasions::add)

        return occasions
    }

    private suspend fun buildContactOccasionOrDeactivate(
        contact: ContactEventDiscoveryProfile,
        existingOccasions: List<Occasion>,
        type: OccasionType,
        day: Int?,
        month: Int?,
        year: Int?,
    ): Occasion? {
        if (day == null || month == null) {
            eventRepository.deactivateContactDerivedOccasion(contact.id, type)
            return null
        }

        return buildContactOccasion(
            contact = contact,
            existingOccasions = existingOccasions,
            type = type,
            day = day,
            month = month,
            year = year,
        )
    }

    private fun buildContactOccasion(
        contact: ContactEventDiscoveryProfile,
        existingOccasions: List<Occasion>,
        type: OccasionType,
        day: Int,
        month: Int,
        year: Int?,
    ): Occasion? {
        if (!EventDatePolicy.isValidDate(day, month, year)) return null
        val existingMatchingEvent = EventIdentityPolicy.findMatchingActiveOccasion(
            occasions = existingOccasions,
            contactId = contact.id.value,
            occasionType = type.raw,
            month = month,
            dayOfMonth = day,
        )
        if (existingMatchingEvent != null && !existingMatchingEvent.source.equals("CONTACTS", ignoreCase = true)) {
            return null
        }
        val nextMs = EventDatePolicy.nextOccurrenceMs(day, month) ?: return null
        return Occasion(
            id = OccasionId(EventIdentityPolicy.canonicalId(contact.id.value, type.raw) ?: return null),
            contactId = contact.id,
            type = type,
            label = contact.displayName,
            date = OccasionDate(dayOfMonth = day, month = month, year = year),
            nextOccurrenceMs = nextMs,
            isActive = true,
            notifyDaysBefore = 1,
            source = "CONTACTS",
            confidenceScore = 100,
            isVerified = true,
        )
    }

    data class DiscoveryOutcome(val contacts: Int, val events: Int)
}
