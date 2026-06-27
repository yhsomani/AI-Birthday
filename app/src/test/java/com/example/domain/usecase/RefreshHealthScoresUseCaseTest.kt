package com.example.domain.usecase

import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactHealthProfile
import com.example.domain.repository.ContactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshHealthScoresUseCaseTest {

    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val useCase = RefreshHealthScoresUseCase(contactRepository)

    @Test
    fun `invoke with no contacts scans and updates nothing`() = runTest {
        coEvery { contactRepository.getHealthProfiles() } returns emptyList()

        val outcome = useCase()

        assertEquals(0, outcome.scanned)
        assertEquals(0, outcome.updated)
        coVerify(exactly = 0) { contactRepository.updateHealthScore(any(), any()) }
    }

    @Test
    fun `invoke computes baseline health score of 50 for quiet contact`() = runTest {
        val contact = ContactHealthProfile(
            id = ContactId("c1"),
            currentHealthScore = 80,
            interactionFrequencyPerMonth = 0f,
            lastInteractionAtMs = null,
            consecutiveYearsWished = 0,
            lastWishedAtMs = System.currentTimeMillis(),
        )
        coEvery { contactRepository.getHealthProfiles() } returns listOf(contact)

        val outcome = useCase()

        assertEquals(1, outcome.scanned)
        assertEquals(1, outcome.updated)
        coVerify { contactRepository.updateHealthScore("c1", 50) }
    }

    @Test
    fun `invoke computes high score for active contact with bonuses`() = runTest {
        val now = System.currentTimeMillis()
        val contact = ContactHealthProfile(
            id = ContactId("c1"),
            currentHealthScore = 50,
            interactionFrequencyPerMonth = 5f,
            lastInteractionAtMs = now - 5 * 24 * 3600 * 1000L,
            consecutiveYearsWished = 3,
            lastWishedAtMs = now,
        )
        coEvery { contactRepository.getHealthProfiles() } returns listOf(contact)

        val outcome = useCase()

        assertEquals(1, outcome.scanned)
        assertEquals(1, outcome.updated)
        coVerify { contactRepository.updateHealthScore("c1", 100) }
    }

    @Test
    fun `invoke penalizes quiet contact with no history`() = runTest {
        val now = System.currentTimeMillis()
        val contact = ContactHealthProfile(
            id = ContactId("c1"),
            currentHealthScore = 50,
            interactionFrequencyPerMonth = 0f,
            lastInteractionAtMs = now - 200 * 24 * 3600 * 1000L,
            consecutiveYearsWished = 0,
            lastWishedAtMs = null,
        )
        coEvery { contactRepository.getHealthProfiles() } returns listOf(contact)

        val outcome = useCase()

        assertEquals(1, outcome.scanned)
        assertEquals(1, outcome.updated)
        coVerify { contactRepository.updateHealthScore("c1", 30) }
    }
}
