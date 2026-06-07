package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthManager
import com.example.core.db.DatabaseKeyDerivation
import com.example.core.prefs.SecurePrefs
import com.example.domain.repository.ContactRepository
import com.example.domain.usecase.SyncContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "User",
    val userEmail: String = "",
    val userPhotoUrl: String? = null,
    val birthdayReminders: Boolean = true,
    val aiWishGeneration: Boolean = true,
    val contactSyncEnabled: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: String = "Never",
    // AI configuration
    val geminiApiKey: String = "",
    val geminiApiKeySaved: Boolean = false,
    val automationMode: String = "SMART_APPROVE",
    val syncError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val syncContactsUseCase: SyncContactsUseCase,
    private val contactRepository: ContactRepository,
    private val authManager: AuthManager,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load persisted settings
        _uiState.value = _uiState.value.copy(
            geminiApiKey = securePrefs.getGeminiApiKey(),
            automationMode = securePrefs.getGlobalAutomationMode(),
        )
        viewModelScope.launch {
            authManager.userProfile.collect { profile ->
                _uiState.value = _uiState.value.copy(
                    userName = profile.displayName,
                    userEmail = profile.email,
                    userPhotoUrl = profile.photoUrl,
                )
            }
        }
    }

    fun toggleBirthdayReminders(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(birthdayReminders = enabled)
    }

    fun toggleAiWishGeneration(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(aiWishGeneration = enabled)
    }

    fun onGeminiApiKeyChange(key: String) {
        _uiState.value = _uiState.value.copy(geminiApiKey = key, geminiApiKeySaved = false)
    }

    fun saveGeminiApiKey() {
        val key = _uiState.value.geminiApiKey.trim()
        securePrefs.setGeminiApiKey(key)
        _uiState.value = _uiState.value.copy(geminiApiKeySaved = true)
    }

    fun setAutomationMode(mode: String) {
        securePrefs.setGlobalAutomationMode(mode)
        _uiState.value = _uiState.value.copy(automationMode = mode)
    }

    fun syncContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncError = null)
            try {
                syncContactsUseCase(forceRefresh = true)
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    lastSyncTimestamp = "Just now",
                    syncError = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncError = e.message ?: "Failed to sync contacts"
                )
            }
        }
    }

    fun clearSyncError() {
        _uiState.value = _uiState.value.copy(syncError = null)
    }

    fun signOut(context: Context) {
        authManager.signOut()
        DatabaseKeyDerivation.clearCachedKey(context)
        securePrefs.clearAll()
        try {
            context.deleteDatabase("relateai.db")
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "Failed to delete database file", e)
        }
    }
}
