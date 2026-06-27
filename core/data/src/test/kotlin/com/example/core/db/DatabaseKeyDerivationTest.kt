package com.example.core.db

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatabaseKeyDerivationTest {

    @Test
    fun decodeStoredKeyHex_acceptsValidSha256LengthHex() {
        val hex = (0 until 32).joinToString("") { "%02x".format(it) }

        val decoded = DatabaseKeyDerivation.decodeStoredKeyHex(hex)

        assertEquals(32, decoded?.size)
        assertArrayEquals(ByteArray(32) { it.toByte() }, decoded)
    }

    @Test
    fun decodeStoredKeyHex_rejectsMalformedCachedValues() {
        assertNull(DatabaseKeyDerivation.decodeStoredKeyHex("abc"))
        assertNull(DatabaseKeyDerivation.decodeStoredKeyHex("00ff"))
        assertNull(DatabaseKeyDerivation.decodeStoredKeyHex("z".repeat(64)))
    }

    @Test
    fun missingCachedKeyStrategy_generatesRandomKeyForFreshInstall() {
        assertEquals(
            DatabaseKeyDerivation.MissingCachedKeyStrategy.GENERATE_RANDOM,
            DatabaseKeyDerivation.missingCachedKeyStrategy(hasExistingDatabaseFiles = false),
        )
    }

    @Test
    fun missingCachedKeyStrategy_recoversLegacyKeyWhenDatabaseFilesExist() {
        assertEquals(
            DatabaseKeyDerivation.MissingCachedKeyStrategy.RECOVER_LEGACY_IDENTIFIER_KEY,
            DatabaseKeyDerivation.missingCachedKeyStrategy(hasExistingDatabaseFiles = true),
        )
    }

    @Test
    fun generateRandomKey_returnsThirtyTwoBytesFromRandomSource() {
        val key = DatabaseKeyDerivation.generateRandomKey { bytes ->
            bytes.indices.forEach { index -> bytes[index] = index.toByte() }
        }

        assertEquals(32, key.size)
        assertArrayEquals(ByteArray(32) { it.toByte() }, key)
    }

    @Test
    fun sqlCipherPassphraseBytes_formatsRandomKeysAsRawKeyLiteral() {
        val storedKey = ByteArray(32) { it.toByte() }

        val passphrase = DatabaseKeyDerivation.sqlCipherPassphraseBytes(
            storedKey,
            DatabaseKeyDerivation.KEY_SOURCE_RANDOM,
        )

        assertEquals(
            "x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'",
            passphrase.toString(Charsets.US_ASCII),
        )
    }

    @Test
    fun sqlCipherPassphraseBytes_keepsLegacyKeysAsOriginalPassphraseBytes() {
        val storedKey = ByteArray(32) { (it + 1).toByte() }

        assertArrayEquals(
            storedKey,
            DatabaseKeyDerivation.sqlCipherPassphraseBytes(
                storedKey,
                DatabaseKeyDerivation.KEY_SOURCE_LEGACY_IDENTIFIER,
            ),
        )
        assertArrayEquals(
            storedKey,
            DatabaseKeyDerivation.sqlCipherPassphraseBytes(storedKey, storedSource = null),
        )
    }
}
