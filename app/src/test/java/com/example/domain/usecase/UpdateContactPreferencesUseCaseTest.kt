package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
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
        coEvery { contactRepository.getById("c1") } returns ContactEntity(id = "c1", name = "Alice")

        val result = useCase(
            UpdateContactPreferencesUseCase.Request(
                contactId = "c1",
                automationMode = ApprovalMode.VIP_APPROVE,
            )
        )

        assertTrue(result is UpdateContactPreferencesUseCase.Outcome.Updated)
        assertEquals(
            ApprovalMode.VIP_APPROVE.raw,
            (result as UpdateContactPreferencesUseCase.Outcome.Updated).contact.automationMode,
        )
        coVerify {
            contactRepository.update(match { it.id == "c1" && it.automationMode == ApprovalMode.VIP_APPROVE.raw })
        }
    }

    @Test
    fun `invoke persists typed preferred channel as raw storage value`() = runTest {
        coEvery { contactRepository.getById("c1") } returns ContactEntity(id = "c1", name = "Alice")

        val result = useCase(
            UpdateContactPreferencesUseCase.Request(
                contactId = "c1",
                preferredChannel = MessageChannel.EMAIL,
            )
        )

        assertTrue(result is UpdateContactPreferencesUseCase.Outcome.Updated)
        assertEquals(
            MessageChannel.EMAIL.raw,
            (result as UpdateContactPreferencesUseCase.Outcome.Updated).contact.preferredChannel,
        )
        coVerify {
            contactRepository.update(match { it.id == "c1" && it.preferredChannel == MessageChannel.EMAIL.raw })
        }
    }

    @Test
    fun `invoke rejects unknown preferred channel before persistence`() = runTest {
        coEvery { contactRepository.getById("c1") } returns ContactEntity(id = "c1", name = "Alice")

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
        coVerify(exactly = 0) { contactRepository.update(any()) }
    }

    @Test
    fun `invoke rejects unknown automation mode before persistence`() = runTest {
        coEvery { contactRepository.getById("c1") } returns ContactEntity(id = "c1", name = "Alice")

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
        coVerify(exactly = 0) { contactRepository.update(any()) }
    }
}
