package com.example.core.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SecurePrefs(context: Context) {
    private val authPrefs: SharedPreferences by lazy { getSharedAuthInstance(context) }
    private val configPrefs: SharedPreferences by lazy { getSharedConfigInstance(context) }

    companion object {
        @Volatile
        private var authInstance: SharedPreferences? = null
        @Volatile
        private var configInstance: SharedPreferences? = null
        private val lockAuth = Any()
        private val lockConfig = Any()

        private fun getSharedAuthInstance(context: Context): SharedPreferences {
            return authInstance ?: synchronized(lockAuth) {
                authInstance ?: createEncryptedPrefs(context.applicationContext, "relateai_auth_prefs", "relateai_auth_key").also {
                    authInstance = it
                }
            }
        }

        private fun getSharedConfigInstance(context: Context): SharedPreferences {
            return configInstance ?: synchronized(lockConfig) {
                configInstance ?: createEncryptedPrefs(context.applicationContext, "relateai_config_prefs", "relateai_config_key").also {
                    configInstance = it
                }
            }
        }

        fun warmUpAsync(context: Context) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                getSharedAuthInstance(context)
                getSharedConfigInstance(context)
            }
        }

        private fun deleteMasterKey(keyAlias: String) {
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(keyAlias)
                Log.i("SecurePrefs", "Deleted master key '$keyAlias' from AndroidKeyStore")
            } catch (e: Exception) {
                Log.e("SecurePrefs", "Failed to delete master key '$keyAlias' from AndroidKeyStore", e)
            }
        }

        private fun createEncryptedPrefs(context: Context, fileName: String, keyAlias: String): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context, keyAlias)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e("SecurePrefs", "Failed to create encrypted prefs for $fileName, clearing and retrying", e)
                try {
                    context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().commit()
                } catch (ex: Exception) {
                    Log.e("SecurePrefs", "Failed to clear shared preferences", ex)
                }
                deleteMasterKey(keyAlias)
                context.deleteSharedPreferences(fileName)
                try {
                    val masterKey = MasterKey.Builder(context, keyAlias)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context,
                        fileName,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (retryEx: Exception) {
                    Log.e("SecurePrefs", "Retry also failed for $fileName, failing securely", retryEx)
                    throw SecurityException("Failed to initialize EncryptedSharedPreferences securely", retryEx)
                }
            }
        }
    }

    fun setGoogleOAuthToken(token: String) = authPrefs.edit().putString("oauth_token", token).apply()
    fun getGoogleOAuthToken(): String = authPrefs.getString("oauth_token", "") ?: ""

    fun setGeminiApiKey(key: String) = configPrefs.edit().putString("gemini_key", key).apply()
    fun getGeminiApiKey(): String = configPrefs.getString("gemini_key", "") ?: ""

    fun setSenderEmail(email: String) = configPrefs.edit().putString("sender_email", email).apply()
    fun getSenderEmail(): String = configPrefs.getString("sender_email", "") ?: ""

    fun setSenderEmailPassword(pw: String) = configPrefs.edit().putString("sender_email_pw", pw).apply()
    fun getSenderEmailPassword(): String = configPrefs.getString("sender_email_pw", "") ?: ""

    fun setGlobalAutomationMode(mode: String) = configPrefs.edit().putString("global_automation_mode", mode).apply()
    fun getGlobalAutomationMode(): String = configPrefs.getString("global_automation_mode", "SMART_APPROVE") ?: "SMART_APPROVE"

    fun setThemeMode(mode: String) = configPrefs.edit().putString("theme_mode", mode).apply()
    fun getThemeMode(): String = configPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"

    fun setBlackoutDates(datesJson: String) = configPrefs.edit().putString("blackout_dates", datesJson).apply()
    fun getBlackoutDates(): String = configPrefs.getString("blackout_dates", "[]") ?: "[]"

    fun setQuietHoursStart(hour: Int) = configPrefs.edit().putInt("quiet_hours_start", hour).apply()
    fun getQuietHoursStart(): Int = configPrefs.getInt("quiet_hours_start", 22)

    fun setQuietHoursEnd(hour: Int) = configPrefs.edit().putInt("quiet_hours_end", hour).apply()
    fun getQuietHoursEnd(): Int = configPrefs.getInt("quiet_hours_end", 8)

    fun setChannelBlackout(channelsJson: String) = configPrefs.edit().putString("channel_blackout", channelsJson).apply()
    fun getChannelBlackout(): String = configPrefs.getString("channel_blackout", "[]") ?: "[]"

    fun setBiometricLockEnabled(enabled: Boolean) = configPrefs.edit().putBoolean("biometric_lock", enabled).apply()
    fun isBiometricLockEnabled(): Boolean = configPrefs.getBoolean("biometric_lock", false)

    fun isSecureStorageAvailable(): Boolean {
        return try {
            authPrefs is EncryptedSharedPreferences && configPrefs is EncryptedSharedPreferences
        } catch (e: Exception) {
            false
        }
    }

    fun setSyncToken(token: String) = authPrefs.edit().putString("sync_token", token).apply()
    fun getSyncToken(): String = authPrefs.getString("sync_token", "") ?: ""

    fun setOnboardingComplete(complete: Boolean) = configPrefs.edit().putBoolean("onboarding_complete", complete).apply()
    fun isOnboardingComplete(): Boolean = configPrefs.getBoolean("onboarding_complete", false)

    fun setGuestMode(enabled: Boolean) = configPrefs.edit().putBoolean("guest_mode", enabled).apply()
    fun isGuestMode(): Boolean = configPrefs.getBoolean("guest_mode", false)

    fun setFirebaseUid(uid: String) = authPrefs.edit().putString("firebase_uid", uid).apply()
    fun getFirebaseUid(): String = authPrefs.getString("firebase_uid", "") ?: ""

    fun setLastBackupMs(ms: Long) = configPrefs.edit().putLong("last_backup_ms", ms).apply()
    fun getLastBackupMs(): Long = configPrefs.getLong("last_backup_ms", 0L)

    fun setLastSyncError(error: String?) = configPrefs.edit().putString("last_sync_error", error).apply()
    fun getLastSyncError(): String? = configPrefs.getString("last_sync_error", null)

    fun clearAll() {
        authPrefs.edit().clear().apply()
        configPrefs.edit().clear().apply()
    }
}
