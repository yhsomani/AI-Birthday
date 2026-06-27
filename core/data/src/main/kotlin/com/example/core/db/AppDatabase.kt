package com.example.core.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.db.dao.ActivityLogDao
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DiagnosticSnapshotDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.GiftHistoryDao
import com.example.core.db.dao.MemoryNoteDao
import com.example.core.db.dao.MessageFeedbackDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.dao.StyleProfileDao
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.DiagnosticSnapshotEntity
import com.example.core.db.entities.DispatchAttemptEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.db.entities.StyleProfileHistoryEntity
import com.example.core.prefs.SecurePrefs
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        ContactEntity::class,
        EventEntity::class,
        PendingMessageEntity::class,
        SentMessageEntity::class,
        StyleProfileEntity::class,
        MemoryNoteEntity::class,
        GiftHistoryEntity::class,
        StyleProfileHistoryEntity::class,
        ActivityLogEntity::class,
        MessageFeedbackEntity::class,
        DispatchAttemptEntity::class,
        DiagnosticSnapshotEntity::class,
    ],
    version = 16,
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
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun messageFeedbackDao(): MessageFeedbackDao
    abstract fun dispatchAttemptDao(): DispatchAttemptDao
    abstract fun diagnosticSnapshotDao(): DiagnosticSnapshotDao
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
                // contacts table: add lastRevivalAttemptMs, isDeleted, and annualBudgetInr
                db.execSQL("ALTER TABLE contacts ADD COLUMN lastRevivalAttemptMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN annualBudgetInr INTEGER NOT NULL DEFAULT 0")

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

                // Recreate pending_messages table with scheduledYear and isUsingFallback columns, and cascade foreign key
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
                        isUsingFallback INTEGER NOT NULL DEFAULT 0,
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

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Helper to check if a column exists in a table
                fun columnExists(tableName: String, columnName: String): Boolean {
                    return try {
                        db.query("SELECT `$columnName` FROM `$tableName` LIMIT 0").use {
                            true
                        }
                    } catch (e: Exception) {
                        false
                    }
                }

                // Helper to check if a table exists
                fun tableExists(tableName: String): Boolean {
                    db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'").use { cursor ->
                        return cursor.count > 0
                    }
                }

                // 1. Hardening contacts columns
                if (!columnExists("contacts", "lastRevivalAttemptMs")) {
                    db.execSQL("ALTER TABLE contacts ADD COLUMN lastRevivalAttemptMs INTEGER NOT NULL DEFAULT 0")
                }
                if (!columnExists("contacts", "isDeleted")) {
                    db.execSQL("ALTER TABLE contacts ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                }
                if (!columnExists("contacts", "annualBudgetInr")) {
                    db.execSQL("ALTER TABLE contacts ADD COLUMN annualBudgetInr INTEGER NOT NULL DEFAULT 0")
                }

                // 2. Hardening events columns
                if (!columnExists("events", "isActive")) {
                    db.execSQL("ALTER TABLE events ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
                }

                // 3. Hardening pending_messages columns
                if (!columnExists("pending_messages", "scheduledYear")) {
                    db.execSQL("ALTER TABLE pending_messages ADD COLUMN scheduledYear INTEGER NOT NULL DEFAULT 0")
                }
                if (!columnExists("pending_messages", "isUsingFallback")) {
                    db.execSQL("ALTER TABLE pending_messages ADD COLUMN isUsingFallback INTEGER NOT NULL DEFAULT 0")
                }

                // 4. Hardening sent_messages columns
                if (!columnExists("sent_messages", "isContactDeleted")) {
                    db.execSQL("ALTER TABLE sent_messages ADD COLUMN isContactDeleted INTEGER NOT NULL DEFAULT 0")
                }

                // 5. Hardening style_profile_history table
                if (!tableExists("style_profile_history")) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS style_profile_history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            profileJson TEXT NOT NULL,
                            savedAtMs INTEGER NOT NULL,
                            source TEXT NOT NULL DEFAULT 'MANUAL_TRAINING'
                        )
                    """.trimIndent())
                }

                // 6. Recreate pending_messages table with UNIQUE(contactId, eventId, scheduledYear) constraint
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
                        isUsingFallback INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE CASCADE,
                        UNIQUE(contactId, eventId, scheduledYear) ON CONFLICT REPLACE
                    )
                """.trimIndent())

                // Insert into new table
                db.execSQL("""
                    INSERT INTO pending_messages_new (id, contactId, eventId, shortVariant, standardVariant, longVariant, formalVariant, funnyVariant, emotionalVariant, selectedVariant, selectedVariantText, channel, scheduledForMs, approvalMode, status, aiModel, generatedAtMs, editedByUser, userEditedText, qualityScore, tone, length, includeEmoji, scheduledYear, isUsingFallback)
                    SELECT id, contactId, eventId, shortVariant, standardVariant, longVariant, formalVariant, funnyVariant, emotionalVariant, selectedVariant, selectedVariantText, channel, scheduledForMs, approvalMode, status, aiModel, generatedAtMs, editedByUser, userEditedText, qualityScore, tone, length, includeEmoji, scheduledYear, isUsingFallback FROM pending_messages
                """.trimIndent())

                db.execSQL("DROP TABLE pending_messages")
                db.execSQL("ALTER TABLE pending_messages_new RENAME TO pending_messages")

                // Recreate indices since dropping table drops indices
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_messages_scheduledForMs ON pending_messages(scheduledForMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_messages_contactId ON pending_messages(contactId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_messages_contactId_eventId_scheduledYear ON pending_messages(contactId, eventId, scheduledYear)")

                // Also recreate other indices that might be missing in some v10 databases
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_revival ON contacts(healthScore ASC, lastRevivalAttemptMs ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_active ON contacts(isDeleted ASC, healthScore ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_nextOccurrenceMs ON events(nextOccurrenceMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_contactId ON events(contactId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_active ON events(isActive ASC, nextOccurrenceMs ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_sent_messages_contactId_sentAtMs ON sent_messages(contactId ASC, sentAtMs DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_memory_notes_contactId ON memory_notes(contactId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_gift_history_contactId ON gift_history(contactId)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS activity_logs (
                        id TEXT PRIMARY KEY NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        contactId TEXT,
                        eventId TEXT,
                        messageId TEXT,
                        createdAtMs INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_activity_logs_createdAtMs ON activity_logs(createdAtMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_activity_logs_type ON activity_logs(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_activity_logs_contactId ON activity_logs(contactId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_activity_logs_eventId ON activity_logs(eventId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_activity_logs_messageId ON activity_logs(messageId)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity_logs ADD COLUMN severity TEXT NOT NULL DEFAULT 'INFO'")
                db.execSQL("ALTER TABLE activity_logs ADD COLUMN status TEXT NOT NULL DEFAULT 'OPEN'")
                db.execSQL("ALTER TABLE activity_logs ADD COLUMN actionRoute TEXT")
                db.execSQL("ALTER TABLE activity_logs ADD COLUMN metadataJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_activity_logs_status ON activity_logs(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_activity_logs_severity ON activity_logs(severity)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS message_feedback (
                        id TEXT PRIMARY KEY NOT NULL,
                        pendingMessageId TEXT NOT NULL,
                        contactId TEXT NOT NULL,
                        eventId TEXT NOT NULL,
                        reasonKey TEXT NOT NULL,
                        instruction TEXT NOT NULL,
                        draftText TEXT NOT NULL,
                        appliedToRegeneration INTEGER NOT NULL DEFAULT 0,
                        createdAtMs INTEGER NOT NULL,
                        FOREIGN KEY(pendingMessageId) REFERENCES pending_messages(id) ON DELETE CASCADE,
                        FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_feedback_pendingMessageId ON message_feedback(pendingMessageId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_feedback_contactId ON message_feedback(contactId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_feedback_createdAtMs ON message_feedback(createdAtMs)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sent_messages ADD COLUMN eventId TEXT")
                db.execSQL("ALTER TABLE sent_messages ADD COLUMN occasionType TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE sent_messages ADD COLUMN occasionLabel TEXT")

                db.execSQL("""
                    UPDATE sent_messages
                    SET eventId = eventType
                    WHERE EXISTS (
                        SELECT 1 FROM events
                        WHERE events.id = sent_messages.eventType
                    )
                """.trimIndent())

                db.execSQL("""
                    UPDATE sent_messages
                    SET occasionType = COALESCE(
                        (
                            SELECT events.type FROM events
                            WHERE events.id = sent_messages.eventType
                            LIMIT 1
                        ),
                        CASE
                            WHEN UPPER(eventType) IN (
                                'BIRTHDAY',
                                'ANNIVERSARY',
                                'WORK_ANNIVERSARY',
                                'GRADUATION',
                                'HOLIDAY',
                                'REVIVAL',
                                'FOLLOW_UP',
                                'CUSTOM'
                            ) THEN UPPER(eventType)
                            WHEN UPPER(eventType) LIKE 'FOLLOWUP_%' THEN 'FOLLOW_UP'
                            WHEN UPPER(eventType) LIKE 'FOLLOW_UP_%' THEN 'FOLLOW_UP'
                            WHEN UPPER(eventType) LIKE 'HOLIDAY_%' THEN 'HOLIDAY'
                            WHEN UPPER(eventType) LIKE 'REVIVAL_%' THEN 'REVIVAL'
                            ELSE 'UNKNOWN'
                        END
                    )
                """.trimIndent())

                db.execSQL("""
                    UPDATE sent_messages
                    SET occasionLabel = (
                        SELECT events.label FROM events
                        WHERE events.id = sent_messages.eventId
                        LIMIT 1
                    )
                    WHERE eventId IS NOT NULL
                """.trimIndent())

                db.execSQL("UPDATE sent_messages SET eventType = occasionType")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_sent_messages_eventId ON sent_messages(eventId)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_sent_messages_contactId_occasionType_sentAtMs " +
                        "ON sent_messages(contactId ASC, occasionType ASC, sentAtMs DESC)"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS dispatch_attempts (
                        id TEXT NOT NULL,
                        messageDraftId TEXT NOT NULL,
                        contactId TEXT,
                        occasionId TEXT,
                        channel TEXT NOT NULL,
                        routeRank INTEGER NOT NULL DEFAULT 0,
                        eligibilityDecision TEXT NOT NULL,
                        blockOrDeferReason TEXT,
                        requestedAtMs INTEGER NOT NULL,
                        attemptedAtMs INTEGER,
                        resolvedAtMs INTEGER,
                        result TEXT NOT NULL,
                        deliveryStatus TEXT NOT NULL,
                        providerMessageId TEXT,
                        errorType TEXT,
                        errorCode TEXT,
                        redactedErrorMessage TEXT,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        nextRetryAtMs INTEGER,
                        deadLetteredAtMs INTEGER,
                        createdBy TEXT NOT NULL,
                        metadataJson TEXT NOT NULL DEFAULT '{}',
                        PRIMARY KEY(id),
                        FOREIGN KEY(messageDraftId) REFERENCES pending_messages(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(contactId) REFERENCES contacts(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(occasionId) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_dispatch_attempts_messageDraftId_requestedAtMs
                    ON dispatch_attempts(messageDraftId, requestedAtMs)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_dispatch_attempts_result_nextRetryAtMs
                    ON dispatch_attempts(result, nextRetryAtMs)
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_dispatch_attempts_deadLetteredAtMs " +
                        "ON dispatch_attempts(deadLetteredAtMs)"
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_dispatch_attempts_contactId_requestedAtMs
                    ON dispatch_attempts(contactId, requestedAtMs)
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_dispatch_attempts_occasionId " +
                        "ON dispatch_attempts(occasionId)"
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS diagnostic_snapshots (
                        id TEXT NOT NULL,
                        source TEXT NOT NULL DEFAULT 'AI_DOCTOR',
                        status TEXT NOT NULL DEFAULT 'OK',
                        summary TEXT NOT NULL,
                        checksJson TEXT NOT NULL DEFAULT '{}',
                        createdAtMs INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_diagnostic_snapshots_source_createdAtMs
                    ON diagnostic_snapshots(source, createdAtMs)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_diagnostic_snapshots_createdAtMs
                    ON diagnostic_snapshots(createdAtMs)
                    """.trimIndent()
                )
            }
        }

        fun closeAndResetInstance() {
            synchronized(this) {
                INSTANCE?.let { db ->
                    try {
                        db.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing database", e)
                    }
                }
                INSTANCE = null
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val quarantineResult = LegacyDatabaseQuarantine.quarantineIfPlaintext(context.applicationContext)
                if (quarantineResult.quarantined) {
                    Log.w(TAG, "Quarantined legacy unencrypted DB at ${quarantineResult.directory?.absolutePath}")
                    try {
                        SecurePrefs(context.applicationContext).setLegacyUnencryptedDbQuarantined(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to persist legacy DB quarantine notice", e)
                    }
                }

                val passphrase = DatabaseKeyDerivation.deriveKey(context)
                val factory = SupportFactory(passphrase, null, false)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "relateai.db"
                )
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
