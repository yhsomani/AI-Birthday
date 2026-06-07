package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthManager
import com.example.core.auth.UserProfile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val error: String? = null,
    val userProfile: UserProfile = UserProfile(),
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = AuthUiState(
            isSignedIn = authManager.isSignedIn(),
            userProfile = authManager.userProfile.value,
        )
    }

    fun getSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.example.R.string.default_web_client_id))
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/contacts.readonly"))
            .build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        return googleSignInClient.signInIntent
    }

    fun handleResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            _uiState.value = _uiState.value.copy(error = "Sign in cancelled")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            authManager.signInWithGoogle(data) { success ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSignedIn = success,
                    error = if (success) null else "Sign in failed. Please try again.",
                    userProfile = authManager.userProfile.value,
                )
            }
        }
    }

    fun bypassSignIn() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            authManager.bypassSignIn { success ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSignedIn = success,
                    error = null,
                    userProfile = authManager.userProfile.value,
                )
            }
        }
    }
}
