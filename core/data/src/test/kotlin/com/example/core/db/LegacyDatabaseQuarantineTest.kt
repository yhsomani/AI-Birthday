package com.example.core.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34])
class LegacyDatabaseQuarantineTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanFiles()
    }

    @After
    fun tearDown() {
        cleanFiles()
    }

    @Test
    fun plaintextDatabaseIsMovedToNoBackupQuarantine() {
        val dbFile = context.getDatabasePath("relateai.db")
        val walFile = File("${dbFile.path}-wal")
        val shmFile = File("${dbFile.path}-shm")
        dbFile.parentFile?.mkdirs()

        val dbBytes = "SQLite format 3\u0000legacy-db".toByteArray(Charsets.US_ASCII)
        val walBytes = "legacy-wal".toByteArray(Charsets.US_ASCII)
        val shmBytes = "legacy-shm".toByteArray(Charsets.US_ASCII)
        dbFile.writeBytes(dbBytes)
        walFile.writeBytes(walBytes)
        shmFile.writeBytes(shmBytes)

        val result = LegacyDatabaseQuarantine.quarantineIfPlaintext(context, nowMs = 123456789L)

        assertTrue(result.quarantined)
        assertNotNull(result.directory)
        assertFalse(dbFile.exists())
        assertFalse(walFile.exists())
        assertFalse(shmFile.exists())
        assertArrayEquals(dbBytes, File(result.directory, "relateai.db").readBytes())
        assertArrayEquals(walBytes, File(result.directory, "relateai.db-wal").readBytes())
        assertArrayEquals(shmBytes, File(result.directory, "relateai.db-shm").readBytes())
    }

    @Test
    fun encryptedDatabaseIsNotQuarantined() {
        val dbFile = context.getDatabasePath("relateai.db")
        val encryptedBytes = ByteArray(32) { index -> (index + 1).toByte() }
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(encryptedBytes)

        val result = LegacyDatabaseQuarantine.quarantineIfPlaintext(context, nowMs = 123456789L)

        assertFalse(result.quarantined)
        assertTrue(dbFile.exists())
        assertArrayEquals(encryptedBytes, dbFile.readBytes())
    }

    private fun cleanFiles() {
        val dbFile = context.getDatabasePath("relateai.db")
        listOf(dbFile, File("${dbFile.path}-wal"), File("${dbFile.path}-shm")).forEach {
            if (it.exists()) it.delete()
        }
        File(context.noBackupFilesDir, "legacy-unencrypted-db").deleteRecursively()
    }
}
