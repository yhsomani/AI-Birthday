package com.example.data.repository

import com.example.core.db.dao.MemoryNoteCategoryCountRow
import com.example.core.db.dao.MemoryNoteDao
import com.example.core.db.entities.MemoryNoteEntity
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MemoryNoteId
import com.example.domain.model.memory.MemoryNoteRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryNoteRepositoryImplTest {
    private val memoryNoteDao: MemoryNoteDao = mockk(relaxed = true)
    private val repository = MemoryNoteRepositoryImpl(memoryNoteDao)

    @Test
    fun getRecordsByContact_mapsRoomRowsToPureRecords() = runTest {
        coEvery { memoryNoteDao.getByContact("contact_1") } returns listOf(
            MemoryNoteEntity(
                id = "note_1",
                contactId = "contact_1",
                noteText = "Likes mango lassi",
                category = "PREFERENCE",
                dateMs = 1_700_000_000_000L,
                isPinned = true,
            )
        )

        val records = repository.getRecordsByContact("contact_1")

        assertEquals(1, records.size)
        assertEquals(MemoryNoteId("note_1"), records.single().id)
        assertEquals(ContactId("contact_1"), records.single().contactId)
        assertEquals("Likes mango lassi", records.single().noteText)
        assertEquals("PREFERENCE", records.single().category)
        assertEquals(1_700_000_000_000L, records.single().dateMs)
        assertEquals(true, records.single().isPinned)
        coVerify { memoryNoteDao.getByContact("contact_1") }
    }

    @Test
    fun getSummaryForContact_mapsCategoryCountsToPureSummary() = runTest {
        coEvery { memoryNoteDao.getCategoryCountsForContact("contact_1") } returns listOf(
            MemoryNoteCategoryCountRow(category = "GIFT", count = 2L),
            MemoryNoteCategoryCountRow(category = "EVENT", count = 1L),
        )

        val summary = repository.getSummaryForContact("contact_1")

        assertEquals(3, summary.totalCount)
        assertEquals(2, summary.categoryCounts.size)
        assertEquals("GIFT", summary.categoryCounts[0].category)
        assertEquals(2, summary.categoryCounts[0].count)
        assertEquals("EVENT", summary.categoryCounts[1].category)
        assertEquals(1, summary.categoryCounts[1].count)
        coVerify { memoryNoteDao.getCategoryCountsForContact("contact_1") }
    }

    @Test
    fun countByContact_delegatesToDao() = runTest {
        coEvery { memoryNoteDao.countByContact("contact_1") } returns 3

        val count = repository.countByContact("contact_1")

        assertEquals(3, count)
        coVerify { memoryNoteDao.countByContact("contact_1") }
    }

    @Test
    fun upsertRecord_mapsPureRecordToRoomEntity() = runTest {
        val record = MemoryNoteRecord(
            id = MemoryNoteId("note_1"),
            contactId = ContactId("contact_1"),
            noteText = "Likes mango lassi",
            category = "PREFERENCE",
            dateMs = 1_700_000_000_000L,
            isPinned = true,
        )

        repository.upsertRecord(record)

        coVerify {
            memoryNoteDao.upsert(
                match {
                    it.id == "note_1" &&
                        it.contactId == "contact_1" &&
                        it.noteText == "Likes mango lassi" &&
                        it.category == "PREFERENCE" &&
                        it.dateMs == 1_700_000_000_000L &&
                        it.isPinned
                }
            )
        }
    }

    @Test
    fun deleteRecord_deletesByPureId() = runTest {
        repository.deleteRecord(MemoryNoteId("note_1"))

        coVerify { memoryNoteDao.deleteById("note_1") }
    }
}
