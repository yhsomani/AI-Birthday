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
        GiftHistoryEntity::class,
        StyleProfileHistoryEntity::class
    ],
    version = 10,
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

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // contacts table: add lastRevivalAttemptMs and isDeleted
                db.execSQL("ALTER TABLE contacts ADD COLUMN lastRevivalAttemptMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

                // Recreate events table with cascade foreign key and isActive column
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS events_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        contactId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        label TEXT,
                        dayOfMonth INTEGER NOT NULL,
                        month INTEGER NOT NULL,
                        year INTEGER,
                        nextOccurrenceMs INTEGER NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        notifyDaysBefore INTEGER NOT NULL DEFAULT 1,
                        source TEXT NOT NULL DEFAULT 'CONTACTS',
                        confidenceScore INTEGER NOT NULL DEFAULT 100,
                        isVerified INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO events_new (id, contactId, type, label, dayOfMonth, month, year, nextOccurrenceMs, notifyDaysBefore, source, confidenceScore, isVerified)
                    SELECT id, contactId, type, label, dayOfMonth, month, year, nextOccurrenceMs, notifyDaysBefore, source, confidenceScore, isVerified FROM events
                """.trimIndent())
                db.execSQL("DROP TABLE events")
                db.execSQL("ALTER TABLE events_new RENAME TO events")

                // Recreate pending_messages table with scheduledYear column, unique index, and cascade foreign key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_messages_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        contactId TEXT NOT NULL,
                        eventId TEXT NOT NULL,
                        shortVariant TEXT NOT NULL,
                        standardVariant TEXT NOT NULL,
                        longVariant TEXT NOT NULL,
                        formalVariant TEXT NOT NULL,
                        funnyVariant TEXT NOT NULL,
                        emotionalVariant TEXT NOT NULL,
                        selectedVariant TEXT NOT NULL DEFAULT 'standard',
                        selectedVariantText TEXT NOT NULL DEFAULT '',
                        channel TEXT NOT NULL,
                        scheduledForMs INTEGER NOT NULL,
                        approvalMode TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        aiModel TEXT NOT NULL DEFAULT 'flash',
                        generatedAtMs INTEGER NOT NULL,
                        editedByUser INTEGER NOT NULL,
                        userEditedText TEXT,
                        qualityScore INTEGER NOT NULL DEFAULT 0,
                        tone TEXT NOT NULL DEFAULT 'WARM',
                        length TEXT NOT NULL DEFAULT 'STANDARD',
                        includeEmoji INTEGER NOT NULL DEFAULT 1,
                        scheduledYear INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO pending_messages_new (id, contactId, eventId, shortVariant, standardVariant, longVariant, formalVariant, funnyVariant, emotionalVariant, selectedVariant, selectedVariantText, channel, scheduledForMs, approvalMode, status, aiModel, generatedAtMs, editedByUser, userEditedText, qualityScore, tone, length, includeEmoji)
                    SELECT id, contactId, eventId, shortVariant, standardVariant, longVariant, formalVariant, funnyVariant, emotionalVariant, selectedVariant, selectedVariantText, channel, scheduledForMs, approvalMode, status, aiModel, generatedAtMs, editedByUser, userEditedText, qualityScore, tone, length, includeEmoji FROM pending_messages
                """.trimIndent())
                db.execSQL("DROP TABLE pending_messages")
                db.execSQL("ALTER TABLE pending_messages_new RENAME TO pending_messages")

                // Recreate sent_messages table with set-null foreign key and isContactDeleted column
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sent_messages_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        contactId TEXT,
                        eventType TEXT NOT NULL,
                        eventYear INTEGER NOT NULL,
                        messageText TEXT NOT NULL,
                        channel TEXT NOT NULL,
                        sentAtMs INTEGER NOT NULL,
                        deliveryStatus TEXT NOT NULL,
                        aiGenerated INTEGER NOT NULL DEFAULT 1,
                        geminiModel TEXT NOT NULL DEFAULT 'flash',
                        variantUsed TEXT NOT NULL DEFAULT 'standard',
                        replyReceived INTEGER NOT NULL DEFAULT 0,
                        replyAtMs INTEGER,
                        isContactDeleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO sent_messages_new (id, contactId, eventType, eventYear, messageText, channel, sentAtMs, deliveryStatus, aiGenerated, geminiModel, variantUsed, replyReceived, replyAtMs)
                    SELECT id, contactId, eventType, eventYear, messageText, channel, sentAtMs, deliveryStatus, aiGenerated, geminiModel, variantUsed, replyReceived, replyAtMs FROM sent_messages
                """.trimIndent())
                db.execSQL("DROP TABLE sent_messages")
                db.execSQL("ALTER TABLE sent_messages_new RENAME TO sent_messages")

                // Recreate memory_notes table with cascade foreign key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS memory_notes_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        contactId TEXT NOT NULL,
                        noteText TEXT NOT NULL,
                        category TEXT NOT NULL DEFAULT 'GENERAL',
                        dateMs INTEGER NOT NULL,
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO memory_notes_new (id, contactId, noteText, category, dateMs, isPinned)
                    SELECT id, contactId, noteText, category, dateMs, isPinned FROM memory_notes
                """.trimIndent())
                db.execSQL("DROP TABLE memory_notes")
                db.execSQL("ALTER TABLE memory_notes_new RENAME TO memory_notes")

                // Recreate gift_history table with cascade foreign key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS gift_history_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        contactId TEXT NOT NULL,
                        giftName TEXT NOT NULL,
                        giftCategory TEXT NOT NULL,
                        occasionType TEXT NOT NULL,
                        year INTEGER NOT NULL,
                        approxCostInr INTEGER NOT NULL,
                        receivedWell INTEGER,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO gift_history_new (id, contactId, giftName, giftCategory, occasionType, year, approxCostInr, receivedWell, notes)
                    SELECT id, contactId, giftName, giftCategory, occasionType, year, approxCostInr, receivedWell, notes FROM gift_history
                """.trimIndent())
                db.execSQL("DROP TABLE gift_history")
                db.execSQL("ALTER TABLE gift_history_new RENAME TO gift_history")

                // Create style_profile_history table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS style_profile_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileJson TEXT NOT NULL,
                        savedAtMs INTEGER NOT NULL,
                        source TEXT NOT NULL DEFAULT 'MANUAL_TRAINING'
                    )
                """.trimIndent())

                // Create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_revival ON contacts(healthScore ASC, lastRevivalAttemptMs ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_active ON contacts(isDeleted ASC, healthScore ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_nextOccurrenceMs ON events(nextOccurrenceMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_contactId ON events(contactId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_active ON events(isActive ASC, nextOccurrenceMs ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_messages_scheduledForMs ON pending_messages(scheduledForMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_messages_contactId ON pending_messages(contactId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_messages_contactId_eventId_scheduledYear ON pending_messages(contactId, eventId, scheduledYear)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_sent_messages_contactId_sentAtMs ON sent_messages(contactId ASC, sentAtMs DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_memory_notes_contactId ON memory_notes(contactId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_gift_history_contactId ON gift_history(contactId)")
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
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
