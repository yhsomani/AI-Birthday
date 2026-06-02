package com.example.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {
    fun signInWithGoogle(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount, onResult: (Boolean) -> Unit) {
        authManager.signInWithGoogle(account) { success ->
            onResult(success)
        }
    }
}
