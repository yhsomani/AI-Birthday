package com.example.data.repository

import com.example.core.db.dao.StyleProfileDao
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.db.entities.StyleProfileHistoryEntity
import com.example.domain.model.style.StyleProfileHistoryRecord
import com.example.domain.model.style.StyleProfileRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StyleProfileRepositoryImplTest {
    private val styleProfileDao: StyleProfileDao = mockk(relaxed = true)
    private val repository = StyleProfileRepositoryImpl(styleProfileDao)

    @Test
    fun getProfile_mapsRoomRowToPureRecord() = runTest {
        every { styleProfileDao.getFlow() } returns MutableStateFlow(
            StyleProfileEntity(
                sampleMessagesJson = """["hello"]""",
                usesEmoji = false,
                avgMessageLength = 42,
                commonPhrasesJson = """["happy birthday"]""",
                commonGreetingsJson = """["Hey"]""",
                formalityLevel = "FORMAL",
                preferredLanguage = "hi",
                emojiSetJson = """["smile"]""",
                avoidPhrasesJson = """["sir"]""",
                toneDescriptors = """["warm"]""",
                sampleCount = 4,
                updatedAtMs = 1_700_000_000_000L,
            )
        )

        val profile = repository.getProfile().first()

        assertEquals("""["hello"]""", profile?.sampleMessagesJson)
        assertEquals(false, profile?.usesEmoji)
        assertEquals(42, profile?.avgMessageLength)
        assertEquals("FORMAL", profile?.formalityLevel)
        assertEquals("hi", profile?.preferredLanguage)
        assertEquals(4, profile?.sampleCount)
    }

    @Test
    fun upsertWithHistory_mapsPureRecordsToRoomEntities() = runTest {
        val profile = StyleProfileRecord(
            sampleMessagesJson = """["hello"]""",
            usesEmoji = true,
            avgMessageLength = 64,
            commonPhrasesJson = """["warm wishes"]""",
            commonGreetingsJson = """["Hi"]""",
            formalityLevel = "CASUAL",
            preferredLanguage = "en",
            emojiSetJson = """["party"]""",
            avoidPhrasesJson = """[]""",
            toneDescriptors = """["friendly"]""",
            sampleCount = 5,
            updatedAtMs = 1_700_000_000_000L,
        )
        val history = StyleProfileHistoryRecord(
            id = 7,
            profileJson = """{"formalityLevel":"CASUAL"}""",
            savedAtMs = 1_700_000_000_001L,
            source = "AUTO_ANALYSIS",
        )

        repository.upsertWithHistory(profile, history)

        coVerify {
            styleProfileDao.upsertWithHistory(
                match {
                    it.sampleMessagesJson == """["hello"]""" &&
                        it.usesEmoji &&
                        it.avgMessageLength == 64 &&
                        it.formalityLevel == "CASUAL" &&
                        it.sampleCount == 5
                },
                match {
                    it.id == 7 &&
                        it.profileJson == """{"formalityLevel":"CASUAL"}""" &&
                        it.savedAtMs == 1_700_000_000_001L &&
                        it.source == "AUTO_ANALYSIS"
                },
            )
        }
    }

    @Test
    fun getHistory_mapsRoomRowsToPureRecords() = runTest {
        coEvery { styleProfileDao.getHistory() } returns listOf(
            StyleProfileHistoryEntity(
                id = 7,
                profileJson = """{"preferredLanguage":"hi"}""",
                savedAtMs = 1_700_000_000_001L,
                source = "MANUAL_TRAINING",
            )
        )

        val history = repository.getHistory()

        assertEquals(1, history.size)
        assertEquals(7, history.single().id)
        assertEquals("""{"preferredLanguage":"hi"}""", history.single().profileJson)
        assertEquals("MANUAL_TRAINING", history.single().source)
    }
}
