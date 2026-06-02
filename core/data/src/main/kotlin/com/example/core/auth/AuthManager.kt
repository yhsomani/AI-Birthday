package com.example.core.auth

import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor() {
    private val auth = FirebaseAuth.getInstance()

    fun signInWithGoogle(account: GoogleSignInAccount, onComplete: (Boolean) -> Unit) {
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

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentUser() = auth.currentUser

    fun getUserDisplayName(): String = auth.currentUser?.displayName ?: "User"

    fun getUserEmail(): String = auth.currentUser?.email ?: ""
}
