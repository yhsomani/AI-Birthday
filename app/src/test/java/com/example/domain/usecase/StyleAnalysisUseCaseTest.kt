package com.example.domain.usecase

import com.example.core.db.entities.SentMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.style.StyleProfileRecord
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StyleAnalysisUseCaseTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val styleProfileRepository: StyleProfileRepository = mockk(relaxed = true)
    private val useCase = StyleAnalysisUseCase(messageRepository, styleProfileRepository)

    @Test
    fun `invoke with no messages does not perform analysis`() = runTest {
        coEvery { messageRepository.getRecentForStyleAnalysis(any(), 100) } returns emptyList()

        val analyzed = useCase()

        assertEquals(false, analyzed)
        coVerify(exactly = 0) { styleProfileRepository.upsertWithHistory(any(), any()) }
    }

    @Test
    fun `invoke with recent messages performs analysis`() = runTest {
        coEvery { messageRepository.getRecentForStyleAnalysis(any(), 100) } returns listOf(
            SentMessageEntity(
                id = "sent_1",
                contactId = "contact_1",
                eventType = "BIRTHDAY",
                eventYear = 2026,
                messageText = "Dear John, wishing you a very happy birthday. Warm regards.",
                channel = MessageChannel.SMS.raw,
                sentAtMs = 1L,
                deliveryStatus = "SENT",
            ),
        )
        coEvery { styleProfileRepository.getProfileOnce() } returns StyleProfileRecord()

        val analyzed = useCase()

        assertEquals(true, analyzed)
        coVerify { styleProfileRepository.upsertWithHistory(any(), match { it.source == "AUTO_ANALYSIS" }) }
    }

    @Test
    fun `analyzeAndSave with messages computes metrics correctly and saves profile`() = runTest {
        val texts = listOf(
            "Dear John, Wishing you a very happy birthday! Warm regards.",
            "Dear Sarah, Heartiest congratulations on your anniversary. Warm regards.",
            "Dear Amit, Happy work anniversary! Warm regards.",
            "Dear Priya, Wishing you the best on your special day. Warm regards.",
            "Dear Ravi, Congratulations on your achievement. Warm regards."
        )
        // Greetings start with "Dear" -> formal openers
        // Average length is around 50-60 characters
        // No devanagari characters -> "en"
        // No emojis -> usesEmoji = false

        coEvery { styleProfileRepository.getProfileOnce() } returns StyleProfileRecord()

        useCase.analyzeAndSave(texts, "TEST_SOURCE")

        coVerify {
            styleProfileRepository.upsertWithHistory(
                match { profile ->
                    profile.formalityLevel == "FORMAL" &&
                    profile.preferredLanguage == "en" &&
                    !profile.usesEmoji &&
                    profile.avgMessageLength > 0
                },
                match { history ->
                    history.source == "TEST_SOURCE"
                }
            )
        }
    }

    @Test
    fun `analyzeAndSave detects Hindi devanagari characters`() = runTest {
        val texts = listOf(
            "जन्मदिन की शुभकामनाएं!",
            "बधाई हो!",
            "बहुत बहुत बधाई!",
            "शुभकामनाएं!",
            "बधाई!"
        )
        coEvery { styleProfileRepository.getProfileOnce() } returns StyleProfileRecord()

        useCase.analyzeAndSave(texts, "TEST_SOURCE")

        coVerify {
            styleProfileRepository.upsertWithHistory(
                match { profile ->
                    profile.preferredLanguage == "hi"
                },
                any()
            )
        }
    }
}
