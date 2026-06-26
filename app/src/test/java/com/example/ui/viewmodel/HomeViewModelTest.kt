package com.example.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.auth.AuthManager
import com.example.core.auth.UserProfile
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.GetDashboardMetricsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var mockUseCase: GetDashboardMetricsUseCase

    @RelaxedMockK
    private lateinit var mockAuthManager: AuthManager

    @RelaxedMockK
    private lateinit var mockEventRepository: EventRepository

    @RelaxedMockK
    private lateinit var mockContactRepository: ContactRepository

    @RelaxedMockK
    private lateinit var mockSyncContactsUseCase: com.example.domain.usecase.SyncContactsUseCase

    @RelaxedMockK
    private lateinit var mockPreferencesRepository: com.example.domain.service.PreferencesRepository

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { mockPreferencesRepository.getLastSyncError() } returns null
        every { mockPreferencesRepository.getGeminiApiKey() } returns "gemini-key"
        every { mockPreferencesRepository.isAiWishGenerationEnabled() } returns true
        every { mockPreferencesRepository.getLastBackupMs() } returns System.currentTimeMillis()
        coEvery { mockContactRepository.getBottomByHealthScore(3) } returns emptyList()
        every { mockAuthManager.userProfile } returns MutableStateFlow(
            UserProfile(displayName = "TestUser", email = "test@example.com")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `loadMetrics emits dashboard metrics`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } returns GetDashboardMetricsUseCase.DashboardMetrics(
            healthScore = 75,
            pendingCount = 3,
            upcomingEventsCount = 5,
            contactCount = 10,
            sentCount = 2,
        )
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals("TestUser", viewModel.uiState.value.userName)
        assertEquals("test@example.com", viewModel.uiState.value.userEmail)
        assertEquals(75, viewModel.uiState.value.healthScore)
        assertEquals(3, viewModel.uiState.value.pendingCount)
        assertEquals(5, viewModel.uiState.value.upcomingEventsCount)
        assertEquals(10, viewModel.uiState.value.contactCount)
        assertEquals(2, viewModel.uiState.value.sentCount)
        assertEquals(true, viewModel.uiState.value.upcomingBirthdays.isEmpty())
        assertEquals(2, viewModel.uiState.value.setupProgress.completedSteps)
        assertEquals(3, viewModel.uiState.value.setupProgress.totalSteps)
        assertEquals(0, viewModel.uiState.value.setupProgress.actionRequiredCount)
        assertEquals(1, viewModel.uiState.value.setupProgress.warningCount)
        assertEquals(HomeActionTarget.Messages, viewModel.uiState.value.readinessAction)
        assertEquals(HomeNextActionKind.REVIEW_PENDING, viewModel.uiState.value.primaryAction?.kind)
        assertEquals(HomeActionTarget.Messages, viewModel.uiState.value.primaryAction?.actionTarget)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadMetrics handles exception gracefully`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } throws RuntimeException("Simulated failure")
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(0, viewModel.uiState.value.healthScore)
    }

    @Test
    fun `never backed up surfaces backup prompt on Home`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } returns dashboardMetrics()
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        every { mockPreferencesRepository.getLastBackupMs() } returns 0L

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(BackupFreshnessStatus.NEVER_BACKED_UP, viewModel.uiState.value.backupPrompt?.status)
        assertEquals(HomeNextActionKind.CREATE_BACKUP, viewModel.uiState.value.primaryAction?.kind)
        assertEquals(HomeActionTarget.BackupRestore, viewModel.uiState.value.primaryAction?.actionTarget)
    }

    @Test
    fun `stale backup surfaces backup prompt on Home`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } returns dashboardMetrics()
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        every { mockPreferencesRepository.getLastBackupMs() } returns System.currentTimeMillis() - 31L * DAY_MS

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(BackupFreshnessStatus.STALE, viewModel.uiState.value.backupPrompt?.status)
        assertEquals(31L, viewModel.uiState.value.backupPrompt?.daysSinceBackup)
        assertEquals(HomeNextActionKind.REFRESH_BACKUP, viewModel.uiState.value.primaryAction?.kind)
        assertEquals(31L, viewModel.uiState.value.primaryAction?.daysSinceBackup)
    }

    @Test
    fun `recent backup does not surface backup prompt on Home`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } returns dashboardMetrics()
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        every { mockPreferencesRepository.getLastBackupMs() } returns System.currentTimeMillis() - 2L * DAY_MS

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.backupPrompt)
        assertNull(viewModel.uiState.value.primaryAction)
    }

    @Test
    fun `pending reviews rank above stale backup on Home`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } returns dashboardMetrics(pendingCount = 2)
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        every { mockPreferencesRepository.getLastBackupMs() } returns System.currentTimeMillis() - 31L * DAY_MS

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(HomeNextActionKind.REVIEW_PENDING, viewModel.uiState.value.primaryAction?.kind)
        assertEquals(HomeNextActionKind.REFRESH_BACKUP, viewModel.uiState.value.supportingActions.firstOrNull()?.kind)
    }

    @Test
    fun `contact sync error becomes top setup blocker on Home`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } returns dashboardMetrics()
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        every { mockPreferencesRepository.getLastSyncError() } returns "Sync failed"

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(HomeNextActionKind.FIX_CONTACT_SYNC, viewModel.uiState.value.primaryAction?.kind)
        assertEquals(HomeActionTarget.AutomationSetup, viewModel.uiState.value.primaryAction?.actionTarget)
    }

    @Test
    fun `missing ai access becomes setup action on Home`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } returns dashboardMetrics()
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        every { mockPreferencesRepository.getGeminiApiKey() } returns ""

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(HomeNextActionKind.CONNECT_AI, viewModel.uiState.value.primaryAction?.kind)
        assertEquals(HomeActionTarget.AutomationSetup, viewModel.uiState.value.primaryAction?.actionTarget)
    }

    @Test
    fun `disabled ai generation becomes setup action on Home`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } returns dashboardMetrics()
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        every { mockPreferencesRepository.isAiWishGenerationEnabled() } returns false

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(HomeNextActionKind.ENABLE_AI_GENERATION, viewModel.uiState.value.primaryAction?.kind)
        assertEquals(HomeActionTarget.AutomationSetup, viewModel.uiState.value.primaryAction?.actionTarget)
    }

    @Test
    fun `low health contact becomes relationship action when operational work is clear`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } returns dashboardMetrics()
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        coEvery { mockContactRepository.getBottomByHealthScore(3) } returns listOf(
            ContactEntity(id = "c_low", name = "Asha", healthScore = 32),
            ContactEntity(id = "c_next", name = "Ravi", healthScore = 45),
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(HomeNextActionKind.RECONNECT_CONTACT, viewModel.uiState.value.primaryAction?.kind)
        assertEquals(HomeActionTarget.ContactDetail("c_low"), viewModel.uiState.value.primaryAction?.actionTarget)
        assertEquals("Asha", viewModel.uiState.value.primaryAction?.contactName)
        assertEquals(32, viewModel.uiState.value.primaryAction?.healthScore)
        assertEquals(HomeActionTarget.ContactDetail("c_next"), viewModel.uiState.value.plannerItems.first().actionTarget)
    }

    @Test
    fun `backup risk ranks above low health relationship action`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } returns dashboardMetrics()
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        every { mockPreferencesRepository.getLastBackupMs() } returns System.currentTimeMillis() - 31L * DAY_MS
        coEvery { mockContactRepository.getBottomByHealthScore(3) } returns listOf(
            ContactEntity(id = "c_low", name = "Asha", healthScore = 32),
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(HomeNextActionKind.REFRESH_BACKUP, viewModel.uiState.value.primaryAction?.kind)
        assertEquals(HomeNextActionKind.RECONNECT_CONTACT, viewModel.uiState.value.supportingActions.firstOrNull()?.kind)
    }

    private fun dashboardMetrics(
        pendingCount: Int = 0,
        contactCount: Int = 10,
    ) = GetDashboardMetricsUseCase.DashboardMetrics(
        healthScore = 75,
        pendingCount = pendingCount,
        upcomingEventsCount = 0,
        contactCount = contactCount,
        sentCount = 2,
    )

    private fun newViewModel(): HomeViewModel {
        return HomeViewModel(
            appContext = context,
            getDashboardMetricsUseCase = mockUseCase,
            authManager = mockAuthManager,
            contactRepository = mockContactRepository,
            eventRepository = mockEventRepository,
            syncContactsUseCase = mockSyncContactsUseCase,
            preferencesRepository = mockPreferencesRepository,
        )
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000L
    }
}
