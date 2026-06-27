package com.example.data.repository

import com.example.core.db.dao.GiftHistoryDao
import com.example.core.db.entities.GiftHistoryEntity
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.gift.GiftHistoryRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GiftHistoryRepositoryImplTest {
    private val giftHistoryDao: GiftHistoryDao = mockk(relaxed = true)
    private val repository = GiftHistoryRepositoryImpl(giftHistoryDao)

    @Test
    fun countByContact_delegatesToDao() = runTest {
        coEvery { giftHistoryDao.countByContact("contact_1") } returns 4

        val count = repository.countByContact("contact_1")

        assertEquals(4, count)
        coVerify { giftHistoryDao.countByContact("contact_1") }
    }

    @Test
    fun getRecordsByContact_mapsRoomRowsToPureRecords() = runTest {
        coEvery { giftHistoryDao.getByContact("contact_1") } returns listOf(
            GiftHistoryEntity(
                id = "gift_1",
                contactId = "contact_1",
                giftName = "Travel journal",
                giftCategory = "Books",
                occasionType = "Birthday",
                year = 2026,
                approxCostInr = 1250,
                receivedWell = true,
                notes = "Loved the paper quality",
            )
        )

        val records = repository.getRecordsByContact("contact_1")

        assertEquals(1, records.size)
        assertEquals(GiftHistoryId("gift_1"), records.single().id)
        assertEquals(ContactId("contact_1"), records.single().contactId)
        assertEquals("Travel journal", records.single().giftName)
        assertEquals("Books", records.single().giftCategory)
        assertEquals("Birthday", records.single().occasionType)
        assertEquals(2026, records.single().year)
        assertEquals(1250, records.single().approxCostInr)
        assertEquals(true, records.single().receivedWell)
        assertEquals("Loved the paper quality", records.single().notes)
        coVerify { giftHistoryDao.getByContact("contact_1") }
    }

    @Test
    fun upsertRecord_mapsPureRecordToRoomEntity() = runTest {
        val record = GiftHistoryRecord(
            id = GiftHistoryId("gift_1"),
            contactId = ContactId("contact_1"),
            giftName = "Travel journal",
            giftCategory = "Books",
            occasionType = "Birthday",
            year = 2026,
            approxCostInr = 1250,
            receivedWell = true,
            notes = "Loved the paper quality",
        )

        repository.upsertRecord(record)

        coVerify {
            giftHistoryDao.upsert(
                match {
                    it.id == "gift_1" &&
                        it.contactId == "contact_1" &&
                        it.giftName == "Travel journal" &&
                        it.giftCategory == "Books" &&
                        it.occasionType == "Birthday" &&
                        it.year == 2026 &&
                        it.approxCostInr == 1250 &&
                        it.receivedWell == true &&
                        it.notes == "Loved the paper quality"
                }
            )
        }
    }

    @Test
    fun deleteRecord_deletesByPureId() = runTest {
        repository.deleteRecord(GiftHistoryId("gift_1"))

        coVerify { giftHistoryDao.deleteById("gift_1") }
    }
}
