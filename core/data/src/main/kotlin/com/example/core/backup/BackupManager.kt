package com.example.core.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.core.db.AppDatabase
import com.example.core.db.entities.*
import com.example.core.prefs.SecurePrefs
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {
    private const val TAG = "BackupManager"

    data class BackupData(
        val version: Int = 1,
        val timestampMs: Long = System.currentTimeMillis(),
        val contacts: List<ContactEntity> = emptyList(),
        val events: List<EventEntity> = emptyList(),
        val pendingMessages: List<PendingMessageEntity> = emptyList(),
        val sentMessages: List<SentMessageEntity> = emptyList(),
        val styleProfile: StyleProfileEntity? = null,
        val memoryNotes: List<MemoryNoteEntity> = emptyList(),
        val giftHistory: List<GiftHistoryEntity> = emptyList()
    )

    suspend fun createBackup(context: Context, passphrase: String): File = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(BackupData::class.java).indent("  ")

        val backup = BackupData(
            contacts = db.contactDao().getAllSync(),
            events = db.eventDao().getAllSync(), // Fixed: was emptyList()
            styleProfile = db.styleProfileDao().get(), // Fixed: was null
            memoryNotes = db.memoryNoteDao().getAllSync(), // Fixed: was emptyList()
            giftHistory = db.giftHistoryDao().getAllSync() // Fixed: was emptyList()
        )

        val json = adapter.toJson(backup)
        val encryptedJson = BackupEncryption.encrypt(json, passphrase)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(context.filesDir, "relateai_backup_$timestamp.enc")
        backupFile.writeText(encryptedJson)
        Log.i(TAG, "Encrypted backup created: ${backupFile.name} (${backupFile.length()} bytes)")
        backupFile
    }

    suspend fun restoreBackup(context: Context, uri: Uri, passphrase: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val encryptedJson = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@withContext Result.failure(Exception("Could not read backup file"))
            
            val json = BackupEncryption.decrypt(encryptedJson, passphrase)

            val moshi = Moshi.Builder().build()
            val adapter = moshi.adapter(BackupData::class.java)
            val backup = adapter.fromJson(json) ?: return@withContext Result.failure(Exception("Invalid backup format"))

            val db = AppDatabase.getInstance(context)
            var count = 0

            backup.contacts.forEach { db.contactDao().upsert(it); count++ }
            backup.events.forEach { db.eventDao().upsert(it); count++ }
            backup.styleProfile?.let { db.styleProfileDao().upsert(it); count++ }
            backup.memoryNotes.forEach { db.memoryNoteDao().upsert(it); count++ }
            backup.giftHistory.forEach { db.giftHistoryDao().upsert(it); count++ }

            Log.i(TAG, "Restored $count records from encrypted backup")
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: likely incorrect passphrase", e)
            Result.failure(e)
        }
    }
}
