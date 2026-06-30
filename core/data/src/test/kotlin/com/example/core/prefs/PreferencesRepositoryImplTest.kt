package com.example.core.prefs

import com.example.domain.model.ApprovalMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesRepositoryImplTest {
    private val securePrefs: SecurePrefs = mockk(relaxed = true)
    private val repository = PreferencesRepositoryImpl(securePrefs)

    @Test
    fun `getGlobalAutomationMode returns typed SecurePrefs value`() {
        every { securePrefs.getGlobalApprovalMode() } returns ApprovalMode.VIP_APPROVE

        assertEquals(ApprovalMode.VIP_APPROVE, repository.getGlobalAutomationMode())
    }

    @Test
    fun `setGlobalAutomationMode stores typed value through SecurePrefs`() {
        repository.setGlobalAutomationMode(ApprovalMode.ALWAYS_ASK)

        verify { securePrefs.setGlobalApprovalMode(ApprovalMode.ALWAYS_ASK) }
    }

    @Test
    fun `setLastSuccessfulEmailTest stores sender and timestamp through SecurePrefs`() {
        repository.setLastSuccessfulEmailTest("sender@example.com", 1234L)

        verify { securePrefs.setLastSuccessfulEmailTest("sender@example.com", 1234L) }
    }

    @Test
    fun `getLastSuccessfulEmailTest returns SecurePrefs values`() {
        every { securePrefs.getLastSuccessfulEmailTestSender() } returns "sender@example.com"
        every { securePrefs.getLastSuccessfulEmailTestMs() } returns 1234L

        assertEquals("sender@example.com", repository.getLastSuccessfulEmailTestSender())
        assertEquals(1234L, repository.getLastSuccessfulEmailTestMs())
    }
}
