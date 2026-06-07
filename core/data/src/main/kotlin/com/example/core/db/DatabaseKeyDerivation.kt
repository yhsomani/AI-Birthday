package com.example.core.db

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object DatabaseKeyDerivation {
    private const val ITERATIONS = 65536
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
        val prefs = createEncryptedPrefs(context.applicationContext)
        val cachedHex = prefs.getString(PREF_DB_KEY, null)

        if (cachedHex != null) {
            val bytes = hexToByteArray(cachedHex)
            if (bytes.size == KEY_LENGTH / 8) {
                Log.d(TAG, "DB key loaded from EncryptedSharedPreferences")
                return bytes
            }
        }

        val derived = computeKeyFromScratch(context)
        prefs.edit()
            .putString(PREF_DB_KEY, byteArrayToHex(derived))
            .apply()
        Log.d(TAG, "DB key derived from scratch and cached in EncryptedSharedPreferences")
        return derived
    }

    private fun computeKeyFromScratch(context: Context): ByteArray {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: java.util.UUID.randomUUID().toString()

        val appSignatureHash = getAppCertificateHash(context)
        val keyMaterial = "$androidId:$appSignatureHash:relateai_v2"

        val salt = androidId.take(16).toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(
            keyMaterial.toCharArray(),
            salt,
            ITERATIONS,
            KEY_LENGTH
        )

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secret = factory.generateSecret(spec)
        return secret.encoded
    }

    private fun getAppCertificateHash(context: Context): String {
        return try {
            val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }

            signatures?.firstOrNull()?.let { signature ->
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(signature.toByteArray())
                Base64.encodeToString(digest, Base64.NO_WRAP)
            } ?: "fallback"
        } catch (e: Exception) {
            "fallback"
        }
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
                Log.e(TAG, "Retry also failed, falling back to unencrypted preferences", retryEx)
                try {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to clear shared preferences during fallback", ex)
                }
                deleteMasterKey()
                context.deleteSharedPreferences(PREFS_NAME)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
