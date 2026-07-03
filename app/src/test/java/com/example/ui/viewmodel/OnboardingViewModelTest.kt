package com.example.ui.viewmodel

import com.example.domain.service.PreferencesRepository
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK(relaxed = true)
    private lateinit var preferencesRepository: PreferencesRepository

    @Test
    fun `completeOnboarding marks onboarding complete`() {
        val viewModel = OnboardingViewModel(preferencesRepository)

        viewModel.completeOnboarding()

        verify { preferencesRepository.setOnboardingComplete(true) }
    }

    @Test
    fun `startLocalOnlyMode completes onboarding and enables local mode`() {
        val viewModel = OnboardingViewModel(preferencesRepository)

        viewModel.startLocalOnlyMode()

        verify {
            preferencesRepository.setOnboardingComplete(true)
            preferencesRepository.setLocalOnlyModeEnabled(true)
        }
    }
}
