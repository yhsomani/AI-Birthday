package com.example.ui.viewmodel

import com.example.R
import com.example.core.db.entities.StyleProfileEntity
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.usecase.StyleAnalysisUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.just
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StyleCoachViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var styleProfileRepository: StyleProfileRepository

    @RelaxedMockK
    private lateinit var styleAnalysisUseCase: StyleAnalysisUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val profileFlow = MutableStateFlow<StyleProfileEntity?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { styleProfileRepository.getProfile() } returns profileFlow
        coEvery { styleProfileRepository.getHistory() } returns emptyList()
        coEvery { styleProfileRepository.getProfileOnce() } returns StyleProfileEntity(sampleCount = 3)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `trainStyle saves pasted samples and reports localized success`() = runTest(testDispatcher) {
        coEvery { styleAnalysisUseCase.analyzeAndSave(any(), "MANUAL_TRAINING") } just Runs
        val viewModel = StyleCoachViewModel(styleProfileRepository, styleAnalysisUseCase)
        advanceUntilIdle()

        viewModel.trainStyle(listOf("Hey, happy birthday!", "Wishing you a great year."))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isTraining)
        assertTrue(viewModel.uiState.value.trainSuccess)
        assertEquals(R.string.style_coach_status_manual_success, viewModel.uiState.value.statusMessageRes)
        assertFalse(viewModel.uiState.value.statusIsError)
        coVerify {
            styleAnalysisUseCase.analyzeAndSave(
                listOf("Hey, happy birthday!", "Wishing you a great year."),
                "MANUAL_TRAINING",
            )
        }
    }

    @Test
    fun `analyzeRecentSentMessages reports localized success when messages were analyzed`() = runTest(testDispatcher) {
        coEvery { styleAnalysisUseCase() } returns true
        val viewModel = StyleCoachViewModel(styleProfileRepository, styleAnalysisUseCase)
        advanceUntilIdle()

        viewModel.analyzeRecentSentMessages()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAutoAnalyzing)
        assertTrue(viewModel.uiState.value.trainSuccess)
        assertEquals(R.string.style_coach_status_auto_success, viewModel.uiState.value.statusMessageRes)
        assertFalse(viewModel.uiState.value.statusIsError)
        coVerify { styleAnalysisUseCase() }
    }

    @Test
    fun `analyzeRecentSentMessages reports localized empty state when no messages are available`() = runTest(testDispatcher) {
        coEvery { styleAnalysisUseCase() } returns false
        val viewModel = StyleCoachViewModel(styleProfileRepository, styleAnalysisUseCase)
        advanceUntilIdle()

        viewModel.analyzeRecentSentMessages()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAutoAnalyzing)
        assertFalse(viewModel.uiState.value.trainSuccess)
        assertEquals(R.string.style_coach_status_auto_empty, viewModel.uiState.value.statusMessageRes)
        assertFalse(viewModel.uiState.value.statusIsError)
    }

    @Test
    fun `trainStyle reports stable localized error when analysis fails`() = runTest(testDispatcher) {
        coEvery { styleAnalysisUseCase.analyzeAndSave(any(), "MANUAL_TRAINING") } throws IllegalStateException("raw failure")
        val viewModel = StyleCoachViewModel(styleProfileRepository, styleAnalysisUseCase)
        advanceUntilIdle()

        viewModel.trainStyle(listOf("sample"))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isTraining)
        assertFalse(viewModel.uiState.value.trainSuccess)
        assertEquals(R.string.style_coach_error_manual_failed, viewModel.uiState.value.statusMessageRes)
        assertTrue(viewModel.uiState.value.statusIsError)
    }
}
