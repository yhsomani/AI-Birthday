package com.example.core.db

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object DatabaseKeyDerivation {
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val TAG = "DatabaseKeyDerivation"
    private const val PREFS_NAME = "relateai_db_meta"
    private const val PREF_DB_KEY = "db_key_v2_b64"
    private const val PREF_SCHEMA_VERSION = "db_key_schema_version"
    private const val CURRENT_SCHEMA_VERSION = 2

    @Volatile
    private var cachedKey: ByteArray? = null

    @Synchronized
    fun deriveKey(context: Context): ByteArray {
        cachedKey?.let { return it }

        val prefs: SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val schemaVersion = prefs.getInt(PREF_SCHEMA_VERSION, 0)
        val cachedB64 = if (schemaVersion == CURRENT_SCHEMA_VERSION) prefs.getString(PREF_DB_KEY, null) else null

        if (cachedB64 != null) {
            val bytes = Base64.decode(cachedB64, Base64.NO_WRAP)
            if (bytes.size == KEY_LENGTH / 8) {
                cachedKey = bytes
                Log.d(TAG, "DB key loaded from cache (fast path)")
                return bytes
            }
        }

        val bytes = computeKeyFromScratch(context)
        cachedKey = bytes

        prefs.edit()
            .putString(PREF_DB_KEY, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .putInt(PREF_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .apply()
        Log.d(TAG, "DB key derived from scratch and cached")
        return bytes
    }

    private fun computeKeyFromScratch(context: Context): ByteArray {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: java.util.UUID.randomUUID().toString() // Fallback if unavailable, will trigger destructive migration on next run but won't crash

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

    fun warmUpAsync(context: Context) {
        Thread({
            try {
                deriveKey(context.applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Background DB-key warmup failed (will retry on first DB access)", e)
            }
        }, "db-key-warmup").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }.start()
    }
    @Synchronized
    fun clearCachedKey(context: Context? = null) {
        cachedKey = null
        context?.let {
            try {
                val prefs = it.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear DB key preferences on sign-out", e)
            }
        }
    }
}
