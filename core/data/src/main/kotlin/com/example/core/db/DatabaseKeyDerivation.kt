package com.example.core.db

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom

object DatabaseKeyDerivation {
    private const val KEY_LENGTH = 256
    private const val TAG = "DatabaseKeyDerivation"
    private const val PREFS_NAME = "relateai_db_meta_secure"
    private const val PREF_DB_KEY = "db_key_hex"

    @Volatile
    private var warmUpDeferred: Deferred<ByteArray>? = null

    fun deriveKey(context: Context): ByteArray {
        return runBlocking {
            warmUpAsync(context).await()
        }
    }

    private fun deriveKeyInternal(context: Context): ByteArray {
        // One-time migration: delete legacy plaintext prefs file
        val legacyPrefs = context.getSharedPreferences("relateai_db_meta", Context.MODE_PRIVATE)
        if (legacyPrefs.contains("derived_key_hex")) {
            legacyPrefs.edit().clear().apply()
            context.deleteSharedPreferences("relateai_db_meta")
        }

        val prefs = createEncryptedPrefs(context.applicationContext)
        val cachedHex = prefs.getString(PREF_DB_KEY, null)

        if (cachedHex != null) {
            val bytes = decodeStoredKeyHex(cachedHex)
            if (bytes != null) {
                Log.d(TAG, "DB key loaded from EncryptedSharedPreferences")
                return bytes
            }
            prefs.edit().remove(PREF_DB_KEY).apply()
        }

        val derived = computeKeyFromScratch()
        prefs.edit()
            .putString(PREF_DB_KEY, byteArrayToHex(derived))
            .apply()
        Log.d(TAG, "DB key derived from scratch and cached in EncryptedSharedPreferences")
        return derived
    }

    private fun computeKeyFromScratch(): ByteArray {
        val random = SecureRandom()
        val key = ByteArray(KEY_LENGTH / 8)
        random.nextBytes(key)
        return key
    }

    fun deriveKeyString(context: Context): String {
        val keyBytes = deriveKey(context)
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    @Synchronized
    fun warmUpAsync(context: Context): Deferred<ByteArray> {
        return warmUpDeferred ?: kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
            deriveKeyInternal(context.applicationContext)
        }.also { warmUpDeferred = it }
    }

    @Synchronized
    fun clearCachedKey(context: Context? = null) {
        warmUpDeferred = null
        context?.let {
            try {
                val prefs = createEncryptedPrefs(it.applicationContext)
                prefs.edit().clear().apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear DB key preferences on sign-out", e)
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context, "relateai_db_key_v1")
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted prefs for DB key, clearing and retrying", e)
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to clear shared preferences", ex)
            }
            deleteMasterKey()
            context.deleteSharedPreferences(PREFS_NAME)
            try {
                val masterKey = MasterKey.Builder(context, "relateai_db_key_v1")
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (retryEx: Exception) {
                Log.e(TAG, "Retry also failed, failing securely", retryEx)
                throw SecurityException("Failed to initialize EncryptedSharedPreferences for DB key securely", retryEx)
            }
        }
    }

    private fun deleteMasterKey() {
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry("relateai_db_key_v1")
            Log.i(TAG, "Deleted master key 'relateai_db_key_v1' from AndroidKeyStore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete master key from AndroidKeyStore", e)
        }
    }

    private fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    internal fun decodeStoredKeyHex(hex: String): ByteArray? {
        if (hex.length != KEY_LENGTH / 4 || hex.length % 2 != 0) return null
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return hexToByteArray(hex)
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
