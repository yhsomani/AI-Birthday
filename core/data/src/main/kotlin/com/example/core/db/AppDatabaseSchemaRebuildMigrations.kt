package com.example.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object AppDatabaseSchemaRebuildMigrations {
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
}
