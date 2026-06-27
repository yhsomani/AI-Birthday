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
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object DatabaseKeyDerivation {
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val TAG = "DatabaseKeyDerivation"
    private const val PREFS_NAME = "relateai_db_meta_secure"
    private const val PREF_DB_KEY = "db_key_hex"
    private const val PREF_DB_KEY_SOURCE = "db_key_source"
    private const val PREF_DB_KEY_VERSION = "db_key_version"
    private const val CURRENT_KEY_VERSION = 3
    internal const val KEY_SOURCE_RANDOM = "random_keystore_wrapped"
    internal const val KEY_SOURCE_LEGACY_IDENTIFIER = "legacy_identifier_recovery"
    private const val DB_NAME = "relateai.db"

    @Volatile
    private var warmUpDeferred: Deferred<ByteArray>? = null

    internal enum class MissingCachedKeyStrategy {
        GENERATE_RANDOM,
        RECOVER_LEGACY_IDENTIFIER_KEY,
    }

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
            val cachedSource = prefs.getString(PREF_DB_KEY_SOURCE, null)
            val bytes = decodeStoredKeyHex(cachedHex)
            if (bytes != null) {
                Log.d(TAG, "DB key loaded from EncryptedSharedPreferences")
                return sqlCipherPassphraseBytes(bytes, cachedSource)
            }
            prefs.edit()
                .remove(PREF_DB_KEY)
                .remove(PREF_DB_KEY_SOURCE)
                .remove(PREF_DB_KEY_VERSION)
                .apply()
        }

        val strategy = missingCachedKeyStrategy(hasExistingDatabaseFiles(context))
        val derived = when (strategy) {
            MissingCachedKeyStrategy.GENERATE_RANDOM -> {
                Log.i(TAG, "Generating new random DB key for fresh encrypted database")
                generateRandomKey()
            }
            MissingCachedKeyStrategy.RECOVER_LEGACY_IDENTIFIER_KEY -> {
                Log.w(TAG, "Recovering legacy identifier-derived DB key for existing database")
                computeLegacyIdentifierKey(context)
            }
        }
        prefs.edit()
            .putString(PREF_DB_KEY, byteArrayToHex(derived))
            .putString(PREF_DB_KEY_SOURCE, strategy.toStoredSource())
            .putInt(PREF_DB_KEY_VERSION, CURRENT_KEY_VERSION)
            .apply()
        Log.d(TAG, "DB key cached in EncryptedSharedPreferences")
        return sqlCipherPassphraseBytes(derived, strategy.toStoredSource())
    }

    private fun computeLegacyIdentifierKey(context: Context): ByteArray {
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

    internal fun missingCachedKeyStrategy(hasExistingDatabaseFiles: Boolean): MissingCachedKeyStrategy {
        return if (hasExistingDatabaseFiles) {
            MissingCachedKeyStrategy.RECOVER_LEGACY_IDENTIFIER_KEY
        } else {
            MissingCachedKeyStrategy.GENERATE_RANDOM
        }
    }

    private fun MissingCachedKeyStrategy.toStoredSource(): String {
        return when (this) {
            MissingCachedKeyStrategy.GENERATE_RANDOM -> KEY_SOURCE_RANDOM
            MissingCachedKeyStrategy.RECOVER_LEGACY_IDENTIFIER_KEY -> KEY_SOURCE_LEGACY_IDENTIFIER
        }
    }

    internal fun generateRandomKey(
        fillRandom: (ByteArray) -> Unit = SecureRandom()::nextBytes,
    ): ByteArray {
        return ByteArray(KEY_LENGTH / 8).also(fillRandom)
    }

    internal fun sqlCipherPassphraseBytes(storedKeyBytes: ByteArray, storedSource: String?): ByteArray {
        return when (storedSource) {
            KEY_SOURCE_RANDOM -> rawSqlCipherKeyBytes(storedKeyBytes)
            else -> storedKeyBytes
        }
    }

    internal fun rawSqlCipherKeyBytes(keyBytes: ByteArray): ByteArray {
        require(keyBytes.size == KEY_LENGTH / 8) { "Raw SQLCipher key material must be 256 bits" }
        return "x'${byteArrayToHex(keyBytes)}'".toByteArray(Charsets.US_ASCII)
    }

    private fun hasExistingDatabaseFiles(context: Context): Boolean {
        val dbFile = context.getDatabasePath(DB_NAME)
        return listOf(
            dbFile,
            File("${dbFile.path}-wal"),
            File("${dbFile.path}-shm"),
        ).any { it.exists() }
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
