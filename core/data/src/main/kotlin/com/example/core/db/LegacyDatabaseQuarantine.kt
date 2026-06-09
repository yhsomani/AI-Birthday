package com.example.core.db

import android.content.Context
import java.io.File

data class LegacyDatabaseQuarantineResult(
    val quarantined: Boolean,
    val directory: File? = null,
)

object LegacyDatabaseQuarantine {
    private const val DB_NAME = "relateai.db"
    private const val QUARANTINE_DIR = "legacy-unencrypted-db"
    private val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun quarantineIfPlaintext(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
    ): LegacyDatabaseQuarantineResult {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!hasPlaintextSqliteHeader(dbFile)) {
            return LegacyDatabaseQuarantineResult(quarantined = false)
        }

        val destination = nextQuarantineDirectory(context, nowMs)
        destination.mkdirs()

        listOf(
            dbFile,
            File("${dbFile.path}-wal"),
            File("${dbFile.path}-shm"),
        ).filter { it.exists() }
            .forEach { file ->
                movePreservingBytes(file, File(destination, file.name))
            }

        return LegacyDatabaseQuarantineResult(quarantined = true, directory = destination)
    }

    fun hasPlaintextSqliteHeader(dbFile: File): Boolean {
        if (!dbFile.exists() || dbFile.length() < sqliteHeader.size) return false
        return try {
            dbFile.inputStream().use { stream ->
                val header = ByteArray(sqliteHeader.size)
                stream.read(header) == sqliteHeader.size && header.contentEquals(sqliteHeader)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun nextQuarantineDirectory(context: Context, nowMs: Long): File {
        val baseDir = File(context.noBackupFilesDir, QUARANTINE_DIR)
        var candidate = File(baseDir, nowMs.toString())
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(baseDir, "${nowMs}_$suffix")
            suffix += 1
        }
        return candidate
    }

    private fun movePreservingBytes(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        if (source.renameTo(destination)) return

        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (!source.delete()) {
            throw IllegalStateException("Failed to delete legacy database file after quarantine: ${source.name}")
        }
    }
}
