package com.example.ui.viewmodel

import com.example.core.auth.AuthManager
import com.example.core.auth.UserProfile
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { mockPreferencesRepository.getLastSyncError() } returns null
        every { mockPreferencesRepository.getGeminiApiKey() } returns "gemini-key"
        every { mockPreferencesRepository.isAiWishGenerationEnabled() } returns true
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
        val viewModel = HomeViewModel(mockUseCase, mockAuthManager, mockContactRepository, mockEventRepository, mockSyncContactsUseCase, mockPreferencesRepository)
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
        assertEquals(HomeActionTarget.Messages, viewModel.uiState.value.plannerItems.first().actionTarget)
        assertEquals(HomeActionTarget.Messages, viewModel.uiState.value.readinessAction)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadMetrics handles exception gracefully`() = runTest(testDispatcher) {
        coEvery { mockUseCase() } throws RuntimeException("Simulated failure")
        coEvery { mockEventRepository.getUpcoming(30) } returns emptyList()
        val viewModel = HomeViewModel(mockUseCase, mockAuthManager, mockContactRepository, mockEventRepository, mockSyncContactsUseCase, mockPreferencesRepository)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(0, viewModel.uiState.value.healthScore)
    }
}
