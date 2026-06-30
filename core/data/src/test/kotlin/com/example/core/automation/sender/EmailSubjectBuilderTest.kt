package com.example.core.automation.sender

import org.junit.Assert.assertEquals
import org.junit.Test

class EmailSubjectBuilderTest {
    @Test
    fun `build returns birthday subject`() {
        assertEquals(
            "Happy birthday, Asha!",
            EmailSubjectBuilder.build("Asha", "BIRTHDAY")
        )
    }

    @Test
    fun `build returns anniversary subject`() {
        assertEquals(
            "Happy anniversary, Neel!",
            EmailSubjectBuilder.build("Neel", "ANNIVERSARY")
        )
    }

    @Test
    fun `build returns work anniversary subject`() {
        assertEquals(
            "Congratulations on your work anniversary, Priya!",
            EmailSubjectBuilder.build("Priya", "WORK_ANNIVERSARY")
        )
    }

    @Test
    fun `build uses custom label when present`() {
        assertEquals(
            "Graduation day for Kabir",
            EmailSubjectBuilder.build("Kabir", "CUSTOM", "Graduation day")
        )
    }

    @Test
    fun `build returns explicit subjects for generated relationship event types`() {
        assertEquals(
            "Diwali wishes for Riya",
            EmailSubjectBuilder.build("Riya", "HOLIDAY", "Diwali")
        )
        assertEquals(
            "Checking in, Sam",
            EmailSubjectBuilder.build("Sam", "REVIVAL")
        )
        assertEquals(
            "Following up, Dev",
            EmailSubjectBuilder.build("Dev", "FOLLOW_UP")
        )
    }

    @Test
    fun `build falls back to neutral subject for unknown event`() {
        assertEquals(
            "A note for Tara",
            EmailSubjectBuilder.build("Tara", "UNKNOWN")
        )
    }
}
