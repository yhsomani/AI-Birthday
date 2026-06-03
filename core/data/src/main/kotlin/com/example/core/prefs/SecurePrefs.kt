package com.example.core.prefs

import android.content.Context
import kotlinx.coroutines.launch
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {
    private val prefs: SharedPreferences = getSharedInstance(context)

    companion object {
        @Volatile
        private var instance: SharedPreferences? = null
        private val lock = Any()

        private fun getSharedInstance(context: Context): SharedPreferences {
            return instance ?: synchronized(lock) {
                instance ?: createEncryptedPrefs(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun warmUpAsync(context: Context) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                getSharedInstance(context)
            }
        }

        private fun deleteMasterKey() {
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry("_androidx_security_master_key_")
                Log.i("SecurePrefs", "Deleted master key '_androidx_security_master_key_' from AndroidKeyStore")
            } catch (e: Exception) {
                Log.e("SecurePrefs", "Failed to delete master key from AndroidKeyStore", e)
            }
        }

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "relateai_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e("SecurePrefs", "Failed to create encrypted prefs, clearing and retrying", e)
                try {
                    context.getSharedPreferences("relateai_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
                } catch (ex: Exception) {
                    Log.e("SecurePrefs", "Failed to clear shared preferences", ex)
                }
                deleteMasterKey()
                context.deleteSharedPreferences("relateai_secure_prefs")
                try {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context,
                        "relateai_secure_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (retryEx: Exception) {
                    Log.e("SecurePrefs", "Retry also failed, falling back to unencrypted preferences", retryEx)
                    try {
                        context.getSharedPreferences("relateai_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
                    } catch (ex: Exception) {
                        Log.e("SecurePrefs", "Failed to clear shared preferences during fallback", ex)
                    }
                    deleteMasterKey()
                    context.deleteSharedPreferences("relateai_secure_prefs")
                    context.getSharedPreferences("relateai_secure_prefs", Context.MODE_PRIVATE)
                }
            }
        }
    }

    fun setGoogleOAuthToken(token: String) = prefs.edit().putString("oauth_token", token).apply()
    fun getGoogleOAuthToken(): String = prefs.getString("oauth_token", "") ?: ""

    fun setGeminiApiKey(key: String) = prefs.edit().putString("gemini_key", key).apply()
    fun getGeminiApiKey(): String = prefs.getString("gemini_key", "") ?: ""

    fun setSenderEmail(email: String) = prefs.edit().putString("sender_email", email).apply()
    fun getSenderEmail(): String = prefs.getString("sender_email", "") ?: ""

    fun setSenderEmailPassword(pw: String) = prefs.edit().putString("sender_email_pw", pw).apply()
    fun getSenderEmailPassword(): String = prefs.getString("sender_email_pw", "") ?: ""

    fun setGlobalAutomationMode(mode: String) = prefs.edit().putString("global_automation_mode", mode).apply()
    fun getGlobalAutomationMode(): String = prefs.getString("global_automation_mode", "SMART_APPROVE") ?: "SMART_APPROVE"

    fun setThemeMode(mode: String) = prefs.edit().putString("theme_mode", mode).apply()
    fun getThemeMode(): String = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"

    fun setBlackoutDates(datesJson: String) = prefs.edit().putString("blackout_dates", datesJson).apply()
    fun getBlackoutDates(): String = prefs.getString("blackout_dates", "[]") ?: "[]"

    fun setQuietHoursStart(hour: Int) = prefs.edit().putInt("quiet_hours_start", hour).apply()
    fun getQuietHoursStart(): Int = prefs.getInt("quiet_hours_start", 22)

    fun setQuietHoursEnd(hour: Int) = prefs.edit().putInt("quiet_hours_end", hour).apply()
    fun getQuietHoursEnd(): Int = prefs.getInt("quiet_hours_end", 8)

    fun setChannelBlackout(channelsJson: String) = prefs.edit().putString("channel_blackout", channelsJson).apply()
    fun getChannelBlackout(): String = prefs.getString("channel_blackout", "[]") ?: "[]"

    fun setBiometricLockEnabled(enabled: Boolean) = prefs.edit().putBoolean("biometric_lock", enabled).apply()
    fun isBiometricLockEnabled(): Boolean = prefs.getBoolean("biometric_lock", false)

    fun isSecureStorageAvailable(): Boolean {
        return try {
            prefs is EncryptedSharedPreferences
        } catch (e: Exception) {
            false
        }
    }

    fun setSyncToken(token: String) = prefs.edit().putString("sync_token", token).apply()
    fun getSyncToken(): String = prefs.getString("sync_token", "") ?: ""

    fun setOnboardingComplete(complete: Boolean) = prefs.edit().putBoolean("onboarding_complete", complete).apply()
    fun isOnboardingComplete(): Boolean = prefs.getBoolean("onboarding_complete", false)

    fun setGuestMode(enabled: Boolean) = prefs.edit().putBoolean("guest_mode", enabled).apply()
    fun isGuestMode(): Boolean = prefs.getBoolean("guest_mode", false)

    fun clearAll() = prefs.edit().clear().apply()
}
