package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.event.EventDatePolicy
import com.example.domain.event.EventIdentityPolicy
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.service.EventReminderSchedulerService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers events (birthday, anniversary, work anniversary) for all contacts
 * and inserts/updates EventEntity rows.
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
        val contacts = contactRepository.getAllSync()
        val existingEvents = runCatching { eventRepository.getAll().first() }.getOrDefault(emptyList())
        var discovered = 0

        contacts.forEach { contact ->
            val events = buildEventsFor(contact, existingEvents)
            events.forEach { event ->
                eventRepository.upsert(event)
                eventReminderSchedulerService.scheduleReminder(event)
                discovered++
            }
        }

        return DiscoveryOutcome(contacts = contacts.size, events = discovered)
    }

    private fun buildEventsFor(
        contact: ContactEntity,
        existingEvents: List<EventEntity>,
    ): List<EventEntity> {
        val events = mutableListOf<EventEntity>()

        contact.birthdayDay?.let { day ->
            contact.birthdayMonth?.let { month ->
                buildContactEvent(
                    contact = contact,
                    existingEvents = existingEvents,
                    type = "BIRTHDAY",
                    day = day,
                    month = month,
                    year = contact.birthdayYear,
                )?.let(events::add)
            }
        }

        contact.anniversaryDay?.let { day ->
            contact.anniversaryMonth?.let { month ->
                buildContactEvent(
                    contact = contact,
                    existingEvents = existingEvents,
                    type = "ANNIVERSARY",
                    day = day,
                    month = month,
                    year = contact.anniversaryYear,
                )?.let(events::add)
            }
        }

        contact.workStartDay?.let { day ->
            contact.workStartMonth?.let { month ->
                buildContactEvent(
                    contact = contact,
                    existingEvents = existingEvents,
                    type = "WORK_ANNIVERSARY",
                    day = day,
                    month = month,
                    year = contact.workStartYear,
                )?.let(events::add)
            }
        }

        return events
    }

    private fun buildContactEvent(
        contact: ContactEntity,
        existingEvents: List<EventEntity>,
        type: String,
        day: Int,
        month: Int,
        year: Int?,
    ): EventEntity? {
        if (!EventDatePolicy.isValidDate(day, month, year)) return null
        val existingMatchingEvent = EventIdentityPolicy.findMatchingActiveEvent(
            events = existingEvents,
            contactId = contact.id,
            eventType = type,
            month = month,
            dayOfMonth = day,
        )
        if (existingMatchingEvent != null && !existingMatchingEvent.source.equals("CONTACTS", ignoreCase = true)) {
            return null
        }
        val nextMs = EventDatePolicy.nextOccurrenceMs(day, month) ?: return null
        return EventEntity(
            id = EventIdentityPolicy.canonicalId(contact.id, type)
                ?: return null,
            contactId = contact.id,
            type = type,
            label = contact.name,
            dayOfMonth = day,
            month = month,
            year = year,
            nextOccurrenceMs = nextMs,
            isActive = true,
            source = "CONTACTS"
        )
    }

    private fun daysUntil(futureMs: Long): Int {
        val diff = futureMs - System.currentTimeMillis()
        return (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    data class DiscoveryOutcome(val contacts: Int, val events: Int)
}
