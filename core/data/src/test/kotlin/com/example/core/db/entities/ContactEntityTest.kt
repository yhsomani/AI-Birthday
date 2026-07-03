package com.example.core.db.entities

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactEntityTest {
    @Test
    fun `default automation mode uses ApprovalMode raw value`() {
        val contact = ContactEntity(id = "contact_1", name = "Asha")

        assertEquals(ApprovalMode.DEFAULT.raw, contact.automationMode)
    }

    @Test
    fun `default preferred channel uses MessageChannel raw value`() {
        val contact = ContactEntity(id = "contact_1", name = "Asha")

        assertEquals(MessageChannel.SMS.raw, contact.preferredChannel)
    }
}
