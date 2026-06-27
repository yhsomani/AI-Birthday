package com.example.domain.contact

import com.example.core.db.entities.ContactEntity
import com.example.domain.model.common.ContactId
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactMappersTest {
    @Test
    fun `contact maps to message dispatch recipient`() {
        val recipient = ContactEntity(
            id = "contact_1",
            name = "Asha",
            primaryPhone = "+15551234567",
            primaryEmail = "asha@example.com",
        ).toMessageDispatchRecipient()

        assertEquals(ContactId("contact_1"), recipient.id)
        assertEquals("Asha", recipient.displayName)
        assertEquals("+15551234567", recipient.primaryPhone)
        assertEquals("asha@example.com", recipient.primaryEmail)
    }
}
