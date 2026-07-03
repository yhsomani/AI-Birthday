package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.domain.service.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    fun completeOnboarding() {
        preferencesRepository.setOnboardingComplete(true)
    }

    fun startLocalOnlyMode() {
        preferencesRepository.setOnboardingComplete(true)
        preferencesRepository.setLocalOnlyModeEnabled(true)
    }
}
