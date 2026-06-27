package com.example.domain.model.occasion

import org.junit.Assert.assertEquals
import org.junit.Test

class OccasionTypeTest {
    @Test
    fun `fromRaw normalizes occasion types`() {
        assertEquals(OccasionType.BIRTHDAY, OccasionType.fromRaw(" birthday "))
        assertEquals(OccasionType.WORK_ANNIVERSARY, OccasionType.fromRaw("work_anniversary"))
        assertEquals(OccasionType.FOLLOW_UP, OccasionType.fromRaw("FOLLOW_UP"))
        assertEquals(OccasionType.UNKNOWN, OccasionType.fromRaw("reminder"))
    }
}

