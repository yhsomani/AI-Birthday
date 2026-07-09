package com.example.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object AppDatabaseMigrations {
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

    val MIGRATION_9_10 = AppDatabaseSchemaRebuildMigrations.MIGRATION_9_10

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

    val MIGRATION_11_12 = AppDatabaseRecentMigrations.MIGRATION_11_12
    val MIGRATION_12_13 = AppDatabaseRecentMigrations.MIGRATION_12_13
    val MIGRATION_13_14 = AppDatabaseRecentMigrations.MIGRATION_13_14
    val MIGRATION_14_15 = AppDatabaseRecentMigrations.MIGRATION_14_15
    val MIGRATION_15_16 = AppDatabaseRecentMigrations.MIGRATION_15_16

    val ALL: Array<Migration> = arrayOf(
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
}
