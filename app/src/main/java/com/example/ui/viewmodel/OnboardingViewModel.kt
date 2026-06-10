package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.core.prefs.SecurePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
) : ViewModel() {
    fun completeOnboarding() {
        securePrefs.setOnboardingComplete(true)
    }
}
