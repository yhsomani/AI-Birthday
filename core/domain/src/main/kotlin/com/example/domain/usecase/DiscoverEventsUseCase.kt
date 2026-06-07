package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import java.util.Calendar
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
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(): DiscoveryOutcome {
        val contacts = contactRepository.getAllSync()
        var discovered = 0

        contacts.forEach { contact ->
            val events = buildEventsFor(contact)
            events.forEach { event ->
                eventRepository.upsert(event)
                discovered++
            }
        }

        return DiscoveryOutcome(contacts = contacts.size, events = discovered)
    }

    private fun buildEventsFor(contact: ContactEntity): List<EventEntity> {
        val events = mutableListOf<EventEntity>()

        contact.birthdayDay?.let { day ->
            contact.birthdayMonth?.let { month ->
                val nextMs = nextOccurrenceMs(day, month)
                events.add(
                    EventEntity(
                        id = "${contact.id}_birthday",
                        contactId = contact.id,
                        type = "BIRTHDAY",
                        label = contact.name,
                        dayOfMonth = day,
                        month = month,
                        year = contact.birthdayYear,
                        nextOccurrenceMs = nextMs,
                        isActive = true,
                        source = "CONTACTS"
                    )
                )
            }
        }

        contact.anniversaryDay?.let { day ->
            contact.anniversaryMonth?.let { month ->
                val nextMs = nextOccurrenceMs(day, month)
                events.add(
                    EventEntity(
                        id = "${contact.id}_anniversary",
                        contactId = contact.id,
                        type = "ANNIVERSARY",
                        label = contact.name,
                        dayOfMonth = day,
                        month = month,
                        year = contact.anniversaryYear,
                        nextOccurrenceMs = nextMs,
                        isActive = true,
                        source = "CONTACTS"
                    )
                )
            }
        }

        contact.workStartDay?.let { day ->
            contact.workStartMonth?.let { month ->
                val nextMs = nextOccurrenceMs(day, month)
                events.add(
                    EventEntity(
                        id = "${contact.id}_work_anniversary",
                        contactId = contact.id,
                        type = "WORK_ANNIVERSARY",
                        label = contact.name,
                        dayOfMonth = day,
                        month = month,
                        year = contact.workStartYear,
                        nextOccurrenceMs = nextMs,
                        isActive = true,
                        source = "CONTACTS"
                    )
                )
            }
        }

        return events
    }

    private fun nextOccurrenceMs(day: Int, month: Int): Long {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        cal.set(currentYear, month - 1, day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis < System.currentTimeMillis()) {
            cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun daysUntil(futureMs: Long): Int {
        val diff = futureMs - System.currentTimeMillis()
        return (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    data class DiscoveryOutcome(val contacts: Int, val events: Int)
}
