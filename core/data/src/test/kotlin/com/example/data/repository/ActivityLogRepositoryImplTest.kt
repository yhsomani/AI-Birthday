package com.example.data.repository

import com.example.core.db.dao.ActivityLogDao
import com.example.core.db.entities.ActivityLogEntity
import com.example.domain.model.activity.ActivityLogRecord
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogRepositoryImplTest {
    private val dao: ActivityLogDao = mockk(relaxed = true)
    private val repository = ActivityLogRepositoryImpl(dao)

    @Test
    fun `getByType delegates to dao`() = runTest {
        val entries = listOf(
            ActivityLogEntity(
                id = "a1",
                type = "MESSAGE",
                title = "Message approved",
                detail = "A message was approved.",
                createdAtMs = 100L,
            )
        )
        every { dao.getByType("MESSAGE", 5) } returns MutableStateFlow(entries)

        assertEquals("a1", repository.getByType("MESSAGE", 5).first().single().id)
    }

    @Test
    fun `record and prune delegate to dao`() = runTest {
        val entry = ActivityLogRecord(
            id = "a1",
            type = "SYNC",
            title = "Contacts synced",
            detail = "Contacts were refreshed.",
        )

        repository.record(entry)
        repository.deleteOlderThan(123L)

        coVerify {
            dao.insert(match {
                it.id == "a1" &&
                    it.type == "SYNC" &&
                    it.title == "Contacts synced" &&
                    it.detail == "Contacts were refreshed."
            })
            dao.deleteOlderThan(123L)
        }
    }
}
