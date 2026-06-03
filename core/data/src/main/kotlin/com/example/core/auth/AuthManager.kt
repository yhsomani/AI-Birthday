package com.example.core.auth

import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AuthManager @Inject constructor() {
    // Lazy initialization for testing
    private val auth by lazy { FirebaseAuth.getInstance() }

    open fun signInWithGoogle(account: GoogleSignInAccount, onComplete: (Boolean) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("AuthManager", "Firebase Sign-In successful")
                } else {
                    Log.e("AuthManager", "Firebase Sign-In failed: ${task.exception?.message}")
                }
                onComplete(task.isSuccessful)
            }
    }

    open fun signOut() {
        auth.signOut()
    }

    open fun getCurrentUser() = auth.currentUser

    open fun getUserDisplayName(): String = auth.currentUser?.displayName ?: "User"

    open fun getUserEmail(): String = auth.currentUser?.email ?: ""
}
