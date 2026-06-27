package com.example.domain.event

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventIdentityPolicyTest {
    @Test
    fun `findMatchingActiveOccasion matches active same type and date`() {
        val occasion = occasion(type = OccasionType.BIRTHDAY, month = 6, dayOfMonth = 27)

        val match = EventIdentityPolicy.findMatchingActiveOccasion(
            occasions = listOf(occasion),
            contactId = "contact_1",
            occasionType = OccasionType.BIRTHDAY.raw,
            month = 6,
            dayOfMonth = 27,
        )

        assertEquals(occasion, match)
    }

    @Test
    fun `findConflictingActiveOccasion matches same type with different date`() {
        val occasion = occasion(type = OccasionType.ANNIVERSARY, month = 6, dayOfMonth = 27)

        val match = EventIdentityPolicy.findConflictingActiveOccasion(
            occasions = listOf(occasion),
            contactId = "contact_1",
            occasionType = OccasionType.ANNIVERSARY.raw,
            month = 7,
            dayOfMonth = 1,
        )

        assertEquals(occasion, match)
    }

    @Test
    fun `custom occasion duplicate requires compatible label`() {
        val occasion = occasion(type = OccasionType.CUSTOM, label = "Graduation", month = 6, dayOfMonth = 27)

        val mismatch = EventIdentityPolicy.findMatchingActiveOccasion(
            occasions = listOf(occasion),
            contactId = "contact_1",
            occasionType = OccasionType.CUSTOM.raw,
            month = 6,
            dayOfMonth = 27,
            label = "Housewarming",
        )

        assertNull(mismatch)
    }

    private fun occasion(
        type: OccasionType,
        month: Int,
        dayOfMonth: Int,
        label: String? = null,
    ): Occasion {
        return Occasion(
            id = OccasionId("occasion_1"),
            contactId = ContactId("contact_1"),
            type = type,
            label = label,
            date = OccasionDate(dayOfMonth = dayOfMonth, month = month),
            nextOccurrenceMs = 1_000L,
            isActive = true,
            notifyDaysBefore = 1,
            source = "CONTACTS",
            confidenceScore = 100,
            isVerified = true,
        )
    }
}
