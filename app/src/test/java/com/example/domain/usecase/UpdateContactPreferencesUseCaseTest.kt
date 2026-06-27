package com.example.domain.usecase

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.repository.ContactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateContactPreferencesUseCaseTest {

    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val useCase = UpdateContactPreferencesUseCase(contactRepository)

    @Test
    fun `invoke persists typed automation mode as raw storage value`() = runTest {
        coEvery { contactRepository.contactExists("c1") } returns true
        coEvery { contactRepository.updatePreferences(any()) } returns true

        val result = useCase(
            UpdateContactPreferencesUseCase.Request(
                contactId = "c1",
                automationMode = ApprovalMode.VIP_APPROVE,
            )
        )

        assertTrue(result is UpdateContactPreferencesUseCase.Outcome.Updated)
        assertEquals(
            ApprovalMode.VIP_APPROVE,
            (result as UpdateContactPreferencesUseCase.Outcome.Updated).preferences.automationMode,
        )
        coVerify {
            contactRepository.updatePreferences(match {
                it.contactId == ContactId("c1") && it.automationMode == ApprovalMode.VIP_APPROVE
            })
        }
    }

    @Test
    fun `invoke persists typed preferred channel as raw storage value`() = runTest {
        coEvery { contactRepository.contactExists("c1") } returns true
        coEvery { contactRepository.updatePreferences(any()) } returns true

        val result = useCase(
            UpdateContactPreferencesUseCase.Request(
                contactId = "c1",
                preferredChannel = MessageChannel.EMAIL,
            )
        )

        assertTrue(result is UpdateContactPreferencesUseCase.Outcome.Updated)
        assertEquals(
            MessageChannel.EMAIL,
            (result as UpdateContactPreferencesUseCase.Outcome.Updated).preferences.preferredChannel,
        )
        coVerify {
            contactRepository.updatePreferences(match {
                it.contactId == ContactId("c1") && it.preferredChannel == MessageChannel.EMAIL
            })
        }
    }

    @Test
    fun `invoke rejects unknown preferred channel before persistence`() = runTest {
        coEvery { contactRepository.contactExists("c1") } returns true

        val result = useCase(
            UpdateContactPreferencesUseCase.Request(
                contactId = "c1",
                preferredChannel = MessageChannel.UNKNOWN,
            )
        )

        assertEquals(
            UpdateContactPreferencesUseCase.Outcome.InvalidInput(
                UpdateContactPreferencesUseCase.InvalidInputReason.UNSUPPORTED_PREFERRED_CHANNEL,
            ),
            result,
        )
        coVerify(exactly = 0) { contactRepository.updatePreferences(any()) }
    }

    @Test
    fun `invoke rejects unknown automation mode before persistence`() = runTest {
        coEvery { contactRepository.contactExists("c1") } returns true

        val result = useCase(
            UpdateContactPreferencesUseCase.Request(
                contactId = "c1",
                automationMode = ApprovalMode.UNKNOWN,
            )
        )

        assertEquals(
            UpdateContactPreferencesUseCase.Outcome.InvalidInput(
                UpdateContactPreferencesUseCase.InvalidInputReason.UNSUPPORTED_AUTOMATION_MODE,
            ),
            result,
        )
        coVerify(exactly = 0) { contactRepository.updatePreferences(any()) }
    }

    @Test
    fun `invoke returns contact not found before validation when contact is missing`() = runTest {
        coEvery { contactRepository.contactExists("missing") } returns false

        val result = useCase(
            UpdateContactPreferencesUseCase.Request(
                contactId = "missing",
                preferredChannel = MessageChannel.UNKNOWN,
            )
        )

        assertEquals(UpdateContactPreferencesUseCase.Outcome.ContactNotFound, result)
        coVerify(exactly = 0) { contactRepository.updatePreferences(any()) }
    }
}
