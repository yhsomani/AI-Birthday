package com.example.core.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.db.dao.*
import com.example.core.db.entities.*
import net.sqlcipher.database.SupportFactory
import java.io.File

@Database(
    entities = [
        ContactEntity::class,
        EventEntity::class,
        PendingMessageEntity::class,
        SentMessageEntity::class,
        StyleProfileEntity::class,
        MemoryNoteEntity::class,
        GiftHistoryEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun eventDao(): EventDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun sentMessageDao(): SentMessageDao
    abstract fun styleProfileDao(): StyleProfileDao
    abstract fun memoryNoteDao(): MemoryNoteDao
    abstract fun giftHistoryDao(): GiftHistoryDao
    // abstract fun moodLogDao(): MoodLogDao

    companion object {
        private const val TAG = "AppDatabase"
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN customSendTimeHour INTEGER")
                db.execSQL("ALTER TABLE contacts ADD COLUMN customSendTimeMinute INTEGER")

                db.execSQL("ALTER TABLE events ADD COLUMN source TEXT NOT NULL DEFAULT 'CONTACTS'")
                db.execSQL("ALTER TABLE events ADD COLUMN confidenceScore INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE events ADD COLUMN isVerified INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE pending_messages ADD COLUMN qualityScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pending_messages ADD COLUMN tone TEXT NOT NULL DEFAULT 'WARM'")
                db.execSQL("ALTER TABLE pending_messages ADD COLUMN length TEXT NOT NULL DEFAULT 'STANDARD'")
                db.execSQL("ALTER TABLE pending_messages ADD COLUMN includeEmoji INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS mood_logs")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN contactGroup TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN relationsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_nextOccurrenceMs ON events(nextOccurrenceMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_messages_scheduledForMs ON pending_messages(scheduledForMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_sent_messages_contactId_sentAtMs ON sent_messages(contactId, sentAtMs DESC)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create MoodLog table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mood_logs (
                        id TEXT PRIMARY KEY NOT NULL,
                        contactId TEXT NOT NULL,
                        mood TEXT NOT NULL,
                        note TEXT,
                        timestamp INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                        source TEXT NOT NULL DEFAULT 'MANUAL',
                        FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Update Contacts
                db.execSQL("ALTER TABLE contacts ADD COLUMN classificationConfidence REAL NOT NULL DEFAULT 0.0")

                // Update SentMessages
                db.execSQL("ALTER TABLE sent_messages ADD COLUMN replyReceived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop dead schema bloat
                db.execSQL("DROP TABLE IF EXISTS mood_logs")

                // Add missing indices
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_contactId ON events(contactId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_messages_contactId ON pending_messages(contactId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_memory_notes_contactId ON memory_notes(contactId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_gift_history_contactId ON gift_history(contactId)")

                // Note: Adding ON DELETE CASCADE to existing tables requires table recreation in SQLite.
                // For this migration, we rely on Repository-level cleanup or skip full recreation to avoid risk.
            }
        }

        private fun isDatabaseUnencrypted(context: Context): Boolean {
            val dbFile = context.getDatabasePath("relateai.db")
            if (!dbFile.exists() || dbFile.length() < 16) return false
            return try {
                dbFile.inputStream().use { stream ->
                    val header = ByteArray(16)
                    val read = stream.read(header)
                    if (read != 16) return false
                    val expected = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
                    header.contentEquals(expected)
                }
            } catch (e: Exception) {
                false
            }
        }

        private fun deleteUnencryptedDb(context: Context) {
            val dbFile = context.getDatabasePath("relateai.db")
            if (dbFile.exists()) {
                Log.w(TAG, "Deleting existing unencrypted DB for SQLCipher migration")
                dbFile.delete()
                // Also delete WAL and SHM files if they exist
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                if (isDatabaseUnencrypted(context)) {
                    deleteUnencryptedDb(context)
                }

                val passphrase = DatabaseKeyDerivation.deriveKey(context)
                val factory = SupportFactory(passphrase, null, false)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "relateai.db"
                )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
