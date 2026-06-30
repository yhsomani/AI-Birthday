package com.example.domain.usecase

import com.example.domain.service.PreferencesRepository
import com.example.domain.service.TestSendService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TestSendUseCaseTest {

    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
    private val testSendService: TestSendService = mockk(relaxed = true)
    private val useCase = TestSendUseCase(preferencesRepository, testSendService)

    @Test
    fun `blank message returns validation outcome without sending`() = runTest {
        val outcome = useCase("   ")

        assertEquals(TestSendUseCase.Outcome.BlankMessage, outcome)
        coVerify(exactly = 0) { testSendService.sendEmailToSelf(any()) }
        verify(exactly = 0) { preferencesRepository.setLastSuccessfulEmailTest(any(), any()) }
    }

    @Test
    fun `missing email setup returns validation outcome without sending`() = runTest {
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"

        val outcome = useCase("Happy birthday")

        assertEquals(TestSendUseCase.Outcome.MissingEmailSetup, outcome)
        coVerify(exactly = 0) { testSendService.sendEmailToSelf(any()) }
        verify(exactly = 0) { preferencesRepository.setLastSuccessfulEmailTest(any(), any()) }
    }

    @Test
    fun `configured email sends trimmed message`() = runTest {
        every { preferencesRepository.getSenderEmail() } returns "sender@gmail.com"
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"

        val outcome = useCase("  Happy birthday  ")

        assertEquals(TestSendUseCase.Outcome.Sent, outcome)
        coVerify { testSendService.sendEmailToSelf("Happy birthday") }
        verify { preferencesRepository.setLastSuccessfulEmailTest("sender@gmail.com", any()) }
    }

    @Test
    fun `send exception returns failure outcome`() = runTest {
        every { preferencesRepository.getSenderEmail() } returns "sender@gmail.com"
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
        coEvery { testSendService.sendEmailToSelf(any()) } throws RuntimeException("smtp failed")

        val outcome = useCase("Happy birthday")

        assertEquals(TestSendUseCase.Outcome.SendFailed, outcome)
        verify(exactly = 0) { preferencesRepository.setLastSuccessfulEmailTest(any(), any()) }
    }
}
