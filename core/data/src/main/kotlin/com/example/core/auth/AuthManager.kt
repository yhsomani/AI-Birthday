package com.example.core.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.db.AppDatabase
import com.example.core.db.DatabaseKeyDerivation
import com.example.core.prefs.SecurePrefs
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
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

@Singleton
open class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val securePrefs: SecurePrefs
) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var isMocked = false

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    init {
        updateProfileFromFirebaseUser()
    }

    private fun updateProfileFromFirebaseUser() {
        if (securePrefs.isGuestMode()) {
            _userProfile.value = UserProfile(
                displayName = "Developer User",
                email = "dev@example.com",
                photoUrl = null
            )
            return
        }
        val user = auth.currentUser
        _userProfile.value = UserProfile(
            displayName = user?.displayName ?: "User",
            email = user?.email ?: "",
            photoUrl = user?.photoUrl?.toString(),
        )
    }

    open fun signInWithGoogle(data: Intent?, onComplete: (Boolean) -> Unit) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.result
            _userProfile.value = UserProfile(
                displayName = account.displayName ?: "User",
                email = account.email ?: "",
                photoUrl = account.photoUrl?.toString(),
            )
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("AuthManager", "Firebase Sign-In successful")
                        securePrefs.setGuestMode(false)
                        updateProfileFromFirebaseUser()
                    } else {
                        Log.e("AuthManager", "Firebase Sign-In failed: ${task.exception?.message}")
                    }
                    onComplete(task.isSuccessful)
                }
        } catch (e: Exception) {
            Log.e("AuthManager", "Google Sign-In failed: ${e.message}")
            onComplete(false)
        }
    }

    open fun bypassSignIn(onComplete: (Boolean) -> Unit) {
        isMocked = true
        securePrefs.setGuestMode(true)
        _userProfile.value = UserProfile(
            displayName = "Developer User",
            email = "dev@example.com",
            photoUrl = null
        )
        onComplete(true)
    }

    open fun signOut() {
        Log.i("AuthManager", "Initiating secure sign-out sequence")

        try {
            // Step 1: database.clearAllTables()
            database.clearAllTables()
        } catch (e: Exception) {
            Log.e("AuthManager", "Wipe database tables failed", e)
        }

        try {
            // Step 2: securePrefs.clearAll()
            securePrefs.clearAll()
            DatabaseKeyDerivation.clearCachedKey(context)
        } catch (e: Exception) {
            Log.e("AuthManager", "Clear secure preferences failed", e)
        }

        try {
            // Step 3: Delete database files from disk
            val dbFile = context.getDatabasePath("relateai.db")
            if (dbFile.exists()) {
                dbFile.delete()
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Deleting database files failed", e)
        }

        try {
            // Step 4: WorkManager.cancelAllWork()
            androidx.work.WorkManager.getInstance(context).cancelAllWork()
        } catch (e: Exception) {
            Log.e("AuthManager", "Cancel WorkManager tasks failed", e)
        }

        try {
            // Step 5: NotificationManager.cancelAll()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancelAll()
        } catch (e: Exception) {
            Log.e("AuthManager", "Clear notifications failed", e)
        }

        try {
            // Step 6: firebaseAuth.signOut()
            auth.signOut()
        } catch (e: Exception) {
            Log.e("AuthManager", "Firebase Auth sign-out failed", e)
        }

        try {
            // Step 7: googleSignInClient.signOut()
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).build()
            val googleSignInClient = GoogleSignIn.getClient(context, gso)
            googleSignInClient.signOut()
        } catch (e: Exception) {
            Log.e("AuthManager", "Google Sign-In client sign-out failed", e)
        }

        isMocked = false
        _userProfile.value = UserProfile()
    }

    open fun isSignedIn(): Boolean = isMocked || securePrefs.isGuestMode() || auth.currentUser != null

    open fun getCurrentUser() = auth.currentUser

    open fun getUserDisplayName(): String = _userProfile.value.displayName

    open fun getUserEmail(): String = _userProfile.value.email
}
