package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
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
        coEvery { contactRepository.getAllSync() } returns emptyList()

        val outcome = useCase()

        assertEquals(0, outcome.scanned)
        assertEquals(0, outcome.updated)
        coVerify(exactly = 0) { contactRepository.updateHealthScore(any(), any()) }
    }

    @Test
    fun `invoke computes baseline health score of 50 for quiet contact`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "Jane",
            healthScore = 80, // Needs update to 50
            interactionFrequencyPerMonth = 0f,
            lastInteractionDate = null,
            consecutiveYearsWished = 0,
            lastWishedDate = System.currentTimeMillis() // Prevents penalty
        )
        coEvery { contactRepository.getAllSync() } returns listOf(contact)

        val outcome = useCase()

        assertEquals(1, outcome.scanned)
        assertEquals(1, outcome.updated)
        coVerify { contactRepository.updateHealthScore("c1", 50) }
    }

    @Test
    fun `invoke computes high score for active contact with bonuses`() = runTest {
        val now = System.currentTimeMillis()
        val contact = ContactEntity(
            id = "c1",
            name = "Active",
            healthScore = 50, // Needs update
            interactionFrequencyPerMonth = 5f, // +40 max
            lastInteractionDate = now - 5 * 24 * 3600 * 1000L, // < 7 days: +20 and +10
            consecutiveYearsWished = 3, // +15
            lastWishedDate = now
        )
        // Expected score: 50 + 40 (freq) + 20 (<30 days) + 10 (<7 days) + 15 (years) = 135 -> capped at 100
        coEvery { contactRepository.getAllSync() } returns listOf(contact)

        val outcome = useCase()

        assertEquals(1, outcome.scanned)
        assertEquals(1, outcome.updated)
        coVerify { contactRepository.updateHealthScore("c1", 100) }
    }

    @Test
    fun `invoke penalizes quiet contact with no history`() = runTest {
        val now = System.currentTimeMillis()
        val contact = ContactEntity(
            id = "c1",
            name = "Penalized",
            healthScore = 50, // Needs update
            interactionFrequencyPerMonth = 0f,
            lastInteractionDate = now - 200 * 24 * 3600 * 1000L, // > 180 days
            consecutiveYearsWished = 0,
            lastWishedDate = null // Triggers penalty -20
        )
        // Expected score: 50 + 0 (freq) + 0 (recent) + 0 (years) - 20 (penalty) = 30
        coEvery { contactRepository.getAllSync() } returns listOf(contact)

        val outcome = useCase()

        assertEquals(1, outcome.scanned)
        assertEquals(1, outcome.updated)
        coVerify { contactRepository.updateHealthScore("c1", 30) }
    }
}
