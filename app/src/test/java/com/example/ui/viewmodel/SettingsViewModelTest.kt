package com.example.ui.viewmodel

import com.example.R
import com.example.core.auth.AuthManager
import com.example.core.auth.UserProfile
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.repository.ContactRepository
import com.example.domain.usecase.SyncContactsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var syncContactsUseCase: SyncContactsUseCase

    @RelaxedMockK
    private lateinit var contactRepository: ContactRepository

    @RelaxedMockK
    private lateinit var authManager: AuthManager

    @RelaxedMockK
    private lateinit var securePrefs: SecurePrefs

    private val testDispatcher = StandardTestDispatcher()
    private val userProfileFlow = MutableStateFlow(UserProfile())
    private val context = io.mockk.mockk<android.content.Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { authManager.userProfile } returns userProfileFlow
        every { securePrefs.isBirthdayRemindersEnabled() } returns true
        every { securePrefs.isAiWishGenerationEnabled() } returns true
        every { securePrefs.getGeminiApiKey() } returns ""
        every { securePrefs.getSenderEmail() } returns ""
        every { securePrefs.getSenderEmailPassword() } returns ""
        every { securePrefs.getGlobalApprovalMode() } returns ApprovalMode.SMART_APPROVE
        every { securePrefs.getQuietHoursStart() } returns 22
        every { securePrefs.getQuietHoursEnd() } returns 8
        every { securePrefs.getChannelBlackout() } returns "[]"
        every { securePrefs.isBiometricLockEnabled() } returns false
        every { securePrefs.wasLegacyUnencryptedDbQuarantined() } returns false
        every { securePrefs.getLastBackupMs() } returns 0L
        every { context.getString(R.string.settings_last_sync_never) } returns "Never"
        every { context.getString(R.string.settings_last_sync_just_now) } returns "Just now"
        every { context.getString(R.string.settings_last_backup_today) } returns "Today"
        every { context.getString(R.string.settings_last_backup_yesterday) } returns "Yesterday"
        every { context.getString(R.string.settings_sync_contacts_failed) } returns "Contact sync failed."
        every { context.getString(R.string.settings_sync_contacts_device_permission_missing) } returns "Contacts permission missing."
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleBirthdayReminders updates state`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        viewModel.toggleBirthdayReminders(false)
        assertFalse(viewModel.uiState.value.birthdayReminders)
        verify { securePrefs.setBirthdayRemindersEnabled(false) }

        viewModel.toggleBirthdayReminders(true)
        assertTrue(viewModel.uiState.value.birthdayReminders)
        verify { securePrefs.setBirthdayRemindersEnabled(true) }
    }

    @Test
    fun `toggleAiWishGeneration updates state`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        viewModel.toggleAiWishGeneration(false)
        assertFalse(viewModel.uiState.value.aiWishGeneration)
        verify { securePrefs.setAiWishGenerationEnabled(false) }
    }

    @Test
    fun `init reads typed global automation mode from secure prefs`() = runTest(testDispatcher) {
        every { securePrefs.getGlobalApprovalMode() } returns ApprovalMode.VIP_APPROVE

        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        assertEquals(ApprovalMode.VIP_APPROVE, viewModel.uiState.value.automationMode)
    }

    @Test
    fun `init uses secure prefs fallback global automation mode`() = runTest(testDispatcher) {
        every { securePrefs.getGlobalApprovalMode() } returns ApprovalMode.SMART_APPROVE

        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        assertEquals(ApprovalMode.SMART_APPROVE, viewModel.uiState.value.automationMode)
    }

    @Test
    fun `setAutomationMode stores typed value and updates typed state`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        viewModel.setAutomationMode(ApprovalMode.ALWAYS_ASK)

        assertEquals(ApprovalMode.ALWAYS_ASK, viewModel.uiState.value.automationMode)
        verify { securePrefs.setGlobalApprovalMode(ApprovalMode.ALWAYS_ASK) }
    }

    @Test
    fun `init maps channel blackout storage to typed settings state`() = runTest(testDispatcher) {
        every { securePrefs.getChannelBlackout() } returns
            """["${MessageChannel.SMS.raw.lowercase()}","LEGACY_CHANNEL","${MessageChannel.EMAIL.raw}"]"""

        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        assertTrue(viewModel.uiState.value.channelBlackoutSms)
        assertFalse(viewModel.uiState.value.channelBlackoutWhatsApp)
        assertTrue(viewModel.uiState.value.channelBlackoutEmail)
    }

    @Test
    fun `toggleChannelBlackout stores typed channel raw values`() = runTest(testDispatcher) {
        every { securePrefs.getChannelBlackout() } returns
            """["${MessageChannel.SMS.raw.lowercase()}","LEGACY_CHANNEL","${MessageChannel.EMAIL.raw}"]"""
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        viewModel.toggleChannelBlackout(MessageChannel.WHATSAPP, true)

        assertTrue(viewModel.uiState.value.channelBlackoutSms)
        assertTrue(viewModel.uiState.value.channelBlackoutWhatsApp)
        assertTrue(viewModel.uiState.value.channelBlackoutEmail)
        verify {
            securePrefs.setChannelBlackout(
                """["${MessageChannel.EMAIL.raw}","${MessageChannel.SMS.raw}","${MessageChannel.WHATSAPP.raw}"]"""
            )
        }
    }

    @Test
    fun `toggleChannelBlackout ignores unknown channel`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        viewModel.toggleChannelBlackout(MessageChannel.UNKNOWN, true)

        assertFalse(viewModel.uiState.value.channelBlackoutSms)
        assertFalse(viewModel.uiState.value.channelBlackoutWhatsApp)
        assertFalse(viewModel.uiState.value.channelBlackoutEmail)
        verify(exactly = 0) { securePrefs.setChannelBlackout(any()) }
    }

    @Test
    fun `syncContacts success flips isSyncing off and updates lastSyncTimestamp`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        viewModel.syncContacts()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSyncing)
        assertEquals("Just now", viewModel.uiState.value.lastSyncTimestamp)
    }

    @Test
    fun `syncContacts exposes device permission outcome without generic failure`() = runTest(testDispatcher) {
        coEvery { syncContactsUseCase(forceRefresh = true) } returns SyncContactsUseCase.SyncOutcome(
            googleCount = 1,
            deviceCount = 0,
            inserted = 1,
            updated = 0,
            deviceContactsPermissionDenied = true,
        )
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        viewModel.syncContacts()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSyncing)
        assertEquals("Just now", viewModel.uiState.value.lastSyncTimestamp)
        assertEquals("Contacts permission missing.", viewModel.uiState.value.syncError)
        assertEquals("Contacts permission missing.", (viewModel.uiState.value.feedbackEvent?.message as com.example.ui.feedback.UiText.Dynamic).value)
    }

    @Test
    fun `init shows no backup freshness when backup has never run`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        assertEquals("Never", viewModel.uiState.value.lastBackupTimestamp)
    }

    @Test
    fun `init shows today for fresh backup timestamp`() = runTest(testDispatcher) {
        every { securePrefs.getLastBackupMs() } returns System.currentTimeMillis()
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        assertEquals("Today", viewModel.uiState.value.lastBackupTimestamp)
    }

    @Test
    fun `dismissLegacyDbNotice clears persisted notice flag`() = runTest(testDispatcher) {
        every { securePrefs.wasLegacyUnencryptedDbQuarantined() } returns true
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        assertTrue(viewModel.uiState.value.showLegacyDbNotice)

        viewModel.dismissLegacyDbNotice()

        assertFalse(viewModel.uiState.value.showLegacyDbNotice)
        verify { securePrefs.setLegacyUnencryptedDbQuarantined(false) }
    }
}
