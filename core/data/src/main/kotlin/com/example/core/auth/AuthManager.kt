package com.example.core.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.db.AppDatabase
import com.example.core.db.DatabaseKeyDerivation
import com.example.core.prefs.SecurePrefs
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class UserProfile(
    val displayName: String = "User",
    val email: String = "",
    val photoUrl: String? = null,
)

enum class SignInFailure {
    DEVELOPER_CONFIGURATION,
    NETWORK,
    FIREBASE_AUTH,
    UNKNOWN,
}

data class SignInResult(
    val success: Boolean,
    val failure: SignInFailure? = null,
)

@Singleton
open class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val securePrefs: SecurePrefs
) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private companion object { private const val TAG = "AuthManager" }

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    init {
        updateProfileFromFirebaseUser()
    }

    private fun updateProfileFromFirebaseUser() {
        val user = auth.currentUser
        _userProfile.value = UserProfile(
            displayName = user?.displayName ?: "User",
            email = user?.email ?: "",
            photoUrl = user?.photoUrl?.toString(),
        )
    }

    open fun signInWithGoogle(data: Intent?, onComplete: (SignInResult) -> Unit) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = try {
                task.getResult(ApiException::class.java)
            } catch (e: ApiException) {
                Log.e(TAG, "Google Sign-In failed: statusCode=${e.statusCode}", e)
                onComplete(SignInResult(success = false, failure = e.toSignInFailure()))
                return
            }
            _userProfile.value = UserProfile(
                displayName = account.displayName ?: "User",
                email = account.email ?: "",
                photoUrl = account.photoUrl?.toString(),
            )
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Firebase Sign-In successful")
                        updateProfileFromFirebaseUser()
                        onComplete(SignInResult(success = true))
                    } else {
                        Log.e(TAG, "Firebase Sign-In failed", task.exception)
                        onComplete(SignInResult(success = false, failure = SignInFailure.FIREBASE_AUTH))
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onComplete(SignInResult(success = false, failure = SignInFailure.UNKNOWN))
        }
    }

    private fun ApiException.toSignInFailure(): SignInFailure {
        return when (statusCode) {
            7 -> SignInFailure.NETWORK
            10 -> SignInFailure.DEVELOPER_CONFIGURATION
            else -> SignInFailure.UNKNOWN
        }
    }

    open fun signOut() {
        Log.i("AuthManager", "Initiating secure sign-out sequence")
        // The calling ViewModel handles navigation to AuthScreen after
        // signOut() completes, clearing the back stack (handled in SettingsScreen.kt).

        try {
            // Step 1: Stop all workers before DB access
            androidx.work.WorkManager.getInstance(context).cancelAllWork()

            // Step 2: Clear pending notifications
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancelAll()

            // Step 3: Wipe all 7 Room tables
            try {
                database.clearAllTables()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear tables before close", e)
            }

            // Close and reset database instance to release files and prevent reuse of stale connection
            AppDatabase.closeAndResetInstance()

            // Step 4: Clear all secrets (OAuth token, API key, syncToken)
            securePrefs.clearAll()
            DatabaseKeyDerivation.clearCachedKey(context)

            // Step 5: Delete database files from disk
            val dbFile = context.getDatabasePath("relateai.db")
            listOf(dbFile, File("${dbFile.path}-wal"), File("${dbFile.path}-shm"))
                .filter { it.exists() }
                .forEach { file ->
                    val deleted = file.delete()
                    Log.d(TAG, "Delete ${file.name}: success=$deleted")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Sign-out data wipe failed — continuing with auth sign-out anyway", e)
        }

        // Steps 6–7 always execute regardless of errors in steps 1–5
        try {
            // Step 6: Firebase sign-out
            auth.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Auth sign-out failed", e)
        }

        try {
            // Step 7: Revoke Google OAuth token server-side (revokeAccess, not just signOut)
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).build()
            val googleSignInClient = GoogleSignIn.getClient(context, gso)
            googleSignInClient.revokeAccess()
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In client revoke failed", e)
        }

        _userProfile.value = UserProfile()
    }

    open fun isSignedIn(): Boolean = auth.currentUser != null

    open fun getCurrentUser() = auth.currentUser

    open fun getUserDisplayName(): String = _userProfile.value.displayName

    open fun getUserEmail(): String = _userProfile.value.email
}
