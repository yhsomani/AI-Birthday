package com.example.domain.event

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionConflictKind
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventResolutionPolicyTest {
    @Test
    fun `conflictStates reports duplicate same-date occasions`() {
        val states = EventResolutionPolicy.conflictStates(
            listOf(
                occasion(id = "a", month = 6, dayOfMonth = 27),
                occasion(id = "b", month = 6, dayOfMonth = 27),
            )
        )

        assertEquals(OccasionConflictKind.DUPLICATE, states["a"])
        assertEquals(OccasionConflictKind.DUPLICATE, states["b"])
    }

    @Test
    fun `conflictStates reports date conflicts`() {
        val states = EventResolutionPolicy.conflictStates(
            listOf(
                occasion(id = "a", month = 6, dayOfMonth = 27),
                occasion(id = "b", month = 7, dayOfMonth = 1),
            )
        )

        assertEquals(OccasionConflictKind.DATE_CONFLICT, states["a"])
        assertEquals(OccasionConflictKind.DATE_CONFLICT, states["b"])
    }

    @Test
    fun `keep separate source removes conflicts`() {
        val states = EventResolutionPolicy.conflictStates(
            listOf(
                occasion(id = "a", source = EventResolutionPolicy.keepSeparateSource("CONTACTS")),
                occasion(id = "b", source = EventResolutionPolicy.keepSeparateSource("MANUAL")),
            )
        )

        assertTrue(states.isEmpty())
    }

    private fun occasion(
        id: String,
        month: Int = 6,
        dayOfMonth: Int = 27,
        source: String = "CONTACTS",
    ): Occasion {
        return Occasion(
            id = OccasionId(id),
            contactId = ContactId("contact_1"),
            type = OccasionType.BIRTHDAY,
            label = null,
            date = OccasionDate(dayOfMonth = dayOfMonth, month = month),
            nextOccurrenceMs = 1_000L,
            isActive = true,
            notifyDaysBefore = 1,
            source = source,
            confidenceScore = 100,
            isVerified = true,
        )
    }
}
