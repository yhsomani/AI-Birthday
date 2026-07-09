package com.example.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object AppDatabaseRecentMigrations {
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
}
