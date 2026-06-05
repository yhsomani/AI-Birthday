package com.example.contacts

import com.example.core.db.entities.ContactEntity
import com.example.core.contacts.ContactMerger
import org.junit.Test
import org.junit.Assert.*

class ContactMergerTest {

    @Test
    fun `merge with device only returns device contacts`() {
        val device = listOf(
            ContactEntity(id = "1", name = "Alice", primaryPhone = "111"),
            ContactEntity(id = "2", name = "Bob", primaryPhone = "222")
        )
        val google = emptyList<ContactEntity>()
        val result = ContactMerger.merge(device, google)
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Alice" })
        assertTrue(result.any { it.name == "Bob" })
    }

    @Test
    fun `merge with google only returns google contacts`() {
        val device = emptyList<ContactEntity>()
        val google = listOf(
            ContactEntity(id = "g1", name = "Charlie", googleContactId = "g1"),
            ContactEntity(id = "g2", name = "Diana", googleContactId = "g2")
        )
        val result = ContactMerger.merge(device, google)
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Charlie" })
        assertTrue(result.any { it.name == "Diana" })
    }

    @Test
    fun `merge with same name in both sources fills missing fields`() {
        val device = listOf(
            ContactEntity(id = "d1", name = "Alice", primaryPhone = "111")
        )
        val google = listOf(
            ContactEntity(id = "g1", name = "Alice", primaryEmail = "alice@example.com", googleContactId = "g1")
        )
        val result = ContactMerger.merge(device, google)
        assertEquals(1, result.size)
        val alice = result.first()
        assertEquals("111", alice.primaryPhone)
        assertEquals("alice@example.com", alice.primaryEmail)
        assertEquals("g1", alice.googleContactId)
    }

    @Test
    fun `merge with different names returns all contacts`() {
        val device = listOf(
            ContactEntity(id = "d1", name = "Alice", primaryPhone = "111")
        )
        val google = listOf(
            ContactEntity(id = "g1", name = "Bob", primaryEmail = "bob@example.com")
        )
        val result = ContactMerger.merge(device, google)
        assertEquals(2, result.size)
    }

    @Test
    fun `merge does not override existing phone with google null phone`() {
        val device = listOf(
            ContactEntity(id = "d1", name = "Alice", primaryPhone = "111")
        )
        val google = listOf(
            ContactEntity(id = "g1", name = "Alice", googleContactId = "g1")
        )
        val result = ContactMerger.merge(device, google)
        assertEquals("111", result.first().primaryPhone)
    }

    @Test
    fun `merge does not override existing email with google null email`() {
        val device = listOf(
            ContactEntity(id = "d1", name = "Alice", primaryEmail = "alice@device.com")
        )
        val google = listOf(
            ContactEntity(id = "g1", name = "Alice", primaryPhone = "999", googleContactId = "g1")
        )
        val result = ContactMerger.merge(device, google)
        assertEquals("alice@device.com", result.first().primaryEmail)
    }

    @Test
    fun `merge with empty lists returns empty`() {
        val result = ContactMerger.merge(emptyList(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `merge case sensitivity uses exact name match`() {
        val device = listOf(
            ContactEntity(id = "d1", name = "Alice")
        )
        val google = listOf(
            ContactEntity(id = "g1", name = "alice", primaryEmail = "alice@example.com")
        )
        val result = ContactMerger.merge(device, google)
        assertEquals(2, result.size)
    }
}
