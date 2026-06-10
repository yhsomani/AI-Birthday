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
}
