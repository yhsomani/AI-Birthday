package com.example.domain.model.message

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptTextFormatterTest {
    @Test
    fun `firstName trims display name and returns first token`() {
        assertEquals("Asha", PromptTextFormatter.firstName("  Asha Mehta  "))
        assertEquals("Asha", PromptTextFormatter.firstName("Asha"))
        assertEquals("", PromptTextFormatter.firstName("   "))
    }

    @Test
    fun `sanitizeNotes redacts emails and phone numbers`() {
        val notes = "Email asha@example.com or call +91 98765 43210 after lunch"

        assertEquals(
            "Email [EMAIL] or call [PHONE] after lunch",
            PromptTextFormatter.sanitizeNotes(notes),
        )
    }
}
