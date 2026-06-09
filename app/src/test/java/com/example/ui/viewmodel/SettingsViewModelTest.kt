package com.example.ui.viewmodel

import com.example.core.auth.AuthManager
import com.example.core.auth.UserProfile
import com.example.core.prefs.SecurePrefs
import com.example.domain.repository.ContactRepository
import com.example.domain.usecase.SyncContactsUseCase
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
        every { securePrefs.getGlobalAutomationMode() } returns "SMART_APPROVE"
        every { securePrefs.wasLegacyUnencryptedDbQuarantined() } returns false
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
    fun `syncContacts success flips isSyncing off and updates lastSyncTimestamp`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(context, syncContactsUseCase, contactRepository, authManager, securePrefs)

        viewModel.syncContacts()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSyncing)
        assertEquals("Just now", viewModel.uiState.value.lastSyncTimestamp)
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
