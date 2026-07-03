package com.example.core.db

import com.example.core.db.entities.ContactEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEntityMappersTest {
    @Test
    fun contactMapsToMessageDispatchRecipient() {
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

    @Test
    fun contactRouteReadinessRequiresUsableEmailSyntax() {
        val valid = ContactEntity(
            id = "valid",
            name = "Asha",
            primaryEmail = "asha@example.com",
            preferredChannel = MessageChannel.EMAIL.raw,
        )
        val invalid = ContactEntity(
            id = "invalid",
            name = "Rohan",
            primaryEmail = "not an email",
            preferredChannel = MessageChannel.EMAIL.raw,
        )

        assertTrue(valid.toDeliveryRouteProfile().hasPrimaryEmail)
        assertTrue(valid.toAutomationReadinessProfile().hasPrimaryEmail)
        assertFalse(invalid.toDeliveryRouteProfile().hasPrimaryEmail)
        assertFalse(invalid.toAutomationReadinessProfile().hasPrimaryEmail)
    }
}
