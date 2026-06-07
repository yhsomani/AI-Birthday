package com.example.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: AppDatabase
    private lateinit var contactDao: ContactDao
    private lateinit var eventDao: EventDao
    private lateinit var pendingMessageDao: PendingMessageDao
    private lateinit var sentMessageDao: SentMessageDao

    @Before
    fun createDb() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        contactDao = db.contactDao()
        eventDao = db.eventDao()
        pendingMessageDao = db.pendingMessageDao()
        sentMessageDao = db.sentMessageDao()

        contactDao.upsert(ContactEntity(id = "c1", name = "Alice"))
        eventDao.upsert(EventEntity(
            id = "e1",
            contactId = "c1",
            type = "BIRTHDAY",
            dayOfMonth = 1,
            month = 1,
            nextOccurrenceMs = System.currentTimeMillis()
        ))
        eventDao.upsert(EventEntity(
            id = "e2",
            contactId = "c1",
            type = "ANNIVERSARY",
            dayOfMonth = 2,
            month = 1,
            nextOccurrenceMs = System.currentTimeMillis()
        ))
    }

    @After
    fun closeDb() { db.close() }

    private fun testContact(id: String = "c1", name: String = "Alice") = ContactEntity(id = id, name = name)
    private fun testEvent(id: String = "e1", contactId: String = "c1") = EventEntity(
        id = id,
        contactId = contactId,
        type = "BIRTHDAY",
        dayOfMonth = 1,
        month = 1,
        nextOccurrenceMs = System.currentTimeMillis()
    )
    private fun testPending(id: String = "p1", eventId: String = "e1") = PendingMessageEntity(
        id = id,
        contactId = "c1",
        eventId = eventId,
        shortVariant = "Hi",
        standardVariant = "Hello",
        longVariant = "Dear friend, hope you have a great day!",
        formalVariant = "Respected Sir/Madam",
        funnyVariant = "LOL happy birthday!",
        emotionalVariant = "You mean so much to me",
        channel = "SMS",
        scheduledForMs = System.currentTimeMillis(),
        approvalMode = "SMART_APPROVE"
    )
    private fun testSent(id: String = "s1", contactId: String = "c1") = SentMessageEntity(
        id = id,
        contactId = contactId,
        eventType = "BIRTHDAY",
        eventYear = 2025,
        messageText = "Happy Birthday!",
        channel = "SMS",
        sentAtMs = System.currentTimeMillis(),
        deliveryStatus = "SENT"
    )

    @Test
    fun contactDao_insertAndGetAll() = runTest {
        contactDao.upsert(testContact("c1", "Alice"))
        contactDao.upsert(testContact("c2", "Bob"))
        val contacts = contactDao.getAll().first()
        assertEquals(2, contacts.size)
        assertTrue(contacts.any { it.name == "Alice" })
        assertTrue(contacts.any { it.name == "Bob" })
    }

    @Test
    fun contactDao_getById() = runTest {
        contactDao.upsert(testContact("c1", "Alice"))
        val result = contactDao.getById("c1")
        assertNotNull(result)
        assertEquals("Alice", result!!.name)
    }

    @Test
    fun contactDao_delete() = runTest {
        contactDao.upsert(testContact("c1", "Alice"))
        contactDao.delete(testContact("c1", "Alice"))
        val contacts = contactDao.getAll().first()
        assertTrue(contacts.isEmpty())
    }

    @Test
    fun contactDao_countAll() = runTest {
        contactDao.upsert(testContact("c1"))
        contactDao.upsert(testContact("c2"))
        val count = contactDao.countAll().first()
        assertEquals(2, count)
    }

    @Test
    fun contactDao_updateHealthScore() = runTest {
        contactDao.upsert(testContact("c1"))
        contactDao.updateHealthScore("c1", 85)
        val contact = contactDao.getById("c1")
        assertEquals(85, contact!!.healthScore)
    }

    @Test
    fun eventDao_insertAndGetAll() = runTest {
        eventDao.upsert(testEvent("e1", "c1"))
        eventDao.upsert(testEvent("e2", "c1"))
        val events = eventDao.getAll().first()
        assertEquals(2, events.size)
    }

    @Test
    fun eventDao_delete() = runTest {
        eventDao.upsert(testEvent("e1"))
        eventDao.delete(testEvent("e1"))
        val events = eventDao.getAll().first()
        assertTrue(events.none { it.id == "e1" })
    }

    @Test
    fun pendingMessageDao_insertAndCount() = runTest {
        pendingMessageDao.insert(testPending("p1", "e1"))
        pendingMessageDao.insert(testPending("p2", "e2"))
        val count = pendingMessageDao.countPending().first()
        assertEquals(2, count)
    }

    @Test
    fun pendingMessageDao_updateStatus() = runTest {
        pendingMessageDao.insert(testPending("p1"))
        pendingMessageDao.updateStatus("p1", "APPROVED")
        val pending = pendingMessageDao.getAll().first()
        assertEquals("APPROVED", pending.first().status)
    }

    @Test
    fun sentMessageDao_insertAndCount() = runTest {
        sentMessageDao.insert(testSent("s1"))
        sentMessageDao.insert(testSent("s2"))
        val count = sentMessageDao.countAll().first()
        assertEquals(2, count)
    }
}
