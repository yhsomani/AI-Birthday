package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.core.auth.AuthManager
import com.example.domain.service.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class SplashDestination {
    HOME,        // Already signed in → skip all intro screens
    AUTH,        // Seen onboarding, not signed in → show sign-in
    ONBOARDING   // Fresh install → show onboarding first
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    fun resolveDestination(): SplashDestination {
        if (authManager.isSignedIn()) return SplashDestination.HOME
        return if (preferencesRepository.isOnboardingComplete()) SplashDestination.AUTH
        else SplashDestination.ONBOARDING
    }
}
