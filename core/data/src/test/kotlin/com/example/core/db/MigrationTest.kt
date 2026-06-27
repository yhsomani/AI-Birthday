package com.example.core.db

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.name,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun migrate4To14_preservesRepresentativeData() {
        migrateAndAssertPreservesRepresentativeData(4)
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To14_preservesRepresentativeData() {
        migrateAndAssertPreservesRepresentativeData(5)
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To14_preservesRepresentativeData() {
        migrateAndAssertPreservesRepresentativeData(6)
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To14_preservesRepresentativeData() {
        migrateAndAssertPreservesRepresentativeData(9)
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To14_preservesRepresentativeData() {
        migrateAndAssertPreservesRepresentativeData(10)
    }

    @Test
    @Throws(IOException::class)
    fun migrate11To14_preservesRepresentativeData() {
        migrateAndAssertPreservesRepresentativeData(11)
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To15_preservesRepresentativeData() {
        val dbName = "migration-13-to-15"
        var db = helper.createDatabase(dbName, 13)
        insertRepresentativeRows(db, 13)
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName,
            15,
            true,
            AppDatabase.MIGRATION_13_14,
            AppDatabase.MIGRATION_14_15,
        )

        assertRepresentativeRows(db, 13)
        db.query("SELECT COUNT(*) FROM dispatch_attempts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate15To16_addsDiagnosticSnapshotsTable() {
        val dbName = "migration-15-to-16-diagnostic-snapshots"
        var db = helper.createDatabase(dbName, 15)
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName,
            16,
            true,
            AppDatabase.MIGRATION_15_16,
        )

        db.execSQL("""
            INSERT INTO diagnostic_snapshots (
                id, source, status, summary, checksJson, createdAtMs
            ) VALUES (
                'snapshot_1', 'HEALTH_MONITOR', 'WARNING', 'HealthMonitor warning', '{}', 1700000000000
            )
        """.trimIndent())
        db.query("""
            SELECT source, status, summary, checksJson, createdAtMs
            FROM diagnostic_snapshots
            WHERE id = 'snapshot_1'
        """.trimIndent()).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("HEALTH_MONITOR", cursor.getString(0))
            assertEquals("WARNING", cursor.getString(1))
            assertEquals("HealthMonitor warning", cursor.getString(2))
            assertEquals("{}", cursor.getString(3))
            assertEquals(1_700_000_000_000L, cursor.getLong(4))
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13_addsFeedbackAndActivityWorkflowColumns() {
        val dbName = "migration-12-to-13-feedback"
        var db = helper.createDatabase(dbName, 12)
        db.execSQL("""
            INSERT INTO activity_logs (
                id, type, title, detail, contactId, eventId, messageId, createdAtMs
            ) VALUES (
                'log_1', 'AI', 'Feedback', 'Needs warmth', 'contact_1', 'event_1', 'pending_1', 1700000000000
            )
        """.trimIndent())
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName,
            13,
            true,
            AppDatabase.MIGRATION_12_13,
        )

        db.query("SELECT severity, status, metadataJson FROM activity_logs WHERE id = 'log_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("INFO", cursor.getString(0))
            assertEquals("OPEN", cursor.getString(1))
            assertEquals("{}", cursor.getString(2))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'message_feedback'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_splitsSentMessageOccasionFields() {
        val dbName = "migration-13-to-14-sent-message-occasion"
        var db = helper.createDatabase(dbName, 13)
        insertContact(db, 13)
        insertEvent(db, 13)
        db.execSQL("""
            INSERT INTO sent_messages (
                id, contactId, eventType, eventYear, messageText, channel, sentAtMs,
                deliveryStatus, aiGenerated, geminiModel, variantUsed, replyReceived,
                replyAtMs, isContactDeleted
            ) VALUES
                ('sent_event_ref', 'contact_1', 'event_1', 2026, 'Happy birthday', 'SMS', 1700000000000, 'SENT', 1, 'flash', 'standard', 0, NULL, 0),
                ('sent_semantic', 'contact_1', 'ANNIVERSARY', 2026, 'Happy anniversary', 'SMS', 1700000000001, 'SENT', 1, 'flash', 'standard', 0, NULL, 0),
                ('sent_followup', 'contact_1', 'FOLLOWUP_sent_1', 2026, 'How was it?', 'SMS', 1700000000002, 'SENT', 1, 'flash', 'standard', 0, NULL, 0),
                ('sent_revival', 'contact_1', 'REVIVAL_contact_1', 2026, 'Been a while', 'SMS', 1700000000003, 'SENT', 1, 'flash', 'standard', 0, NULL, 0)
        """.trimIndent())
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName,
            14,
            true,
            AppDatabase.MIGRATION_13_14,
        )

        assertSentOccasion(db, "sent_event_ref", "BIRTHDAY", "event_1", "BIRTHDAY", "Birthday")
        assertSentOccasion(db, "sent_semantic", "ANNIVERSARY", null, "ANNIVERSARY", null)
        assertSentOccasion(db, "sent_followup", "FOLLOW_UP", null, "FOLLOW_UP", null)
        assertSentOccasion(db, "sent_revival", "REVIVAL", null, "REVIVAL", null)
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate14To15_addsDispatchAttemptsTable() {
        val dbName = "migration-14-to-15-dispatch-attempts"
        var db = helper.createDatabase(dbName, 14)
        insertContact(db, 14)
        insertEvent(db, 14)
        insertPendingMessage(db, 14)
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName,
            15,
            true,
            AppDatabase.MIGRATION_14_15,
        )
        db.execSQL("PRAGMA foreign_keys=ON")

        db.execSQL("""
            INSERT INTO dispatch_attempts (
                id, messageDraftId, contactId, occasionId, channel,
                eligibilityDecision, requestedAtMs, result, deliveryStatus, createdBy
            ) VALUES (
                'attempt_1', 'pending_1', 'contact_1', 'event_1', 'SMS',
                'SEND_NOW', 1700000000000, 'QUEUED', 'PENDING_DELIVERY', 'WORKER'
            )
        """.trimIndent())

        db.query("""
            SELECT routeRank, retryCount, metadataJson
            FROM dispatch_attempts
            WHERE id = 'attempt_1'
        """.trimIndent()).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("{}", cursor.getString(2))
        }

        db.execSQL("DELETE FROM events WHERE id = 'event_1'")
        db.query("SELECT occasionId FROM dispatch_attempts WHERE id = 'attempt_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNull(cursor.getString(0))
        }

        db.execSQL("DELETE FROM pending_messages WHERE id = 'pending_1'")
        db.query("SELECT COUNT(*) FROM dispatch_attempts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_missingPendingMessageColumns_areBackfilled() {
        val dbName = "migration-10-to-11-missing-columns"
        var db = helper.createDatabase(dbName, 10)

        db.execSQL("ALTER TABLE pending_messages RENAME TO pending_messages_old")
        db.execSQL("""
            CREATE TABLE pending_messages (
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
                FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE CASCADE
            )
        """.trimIndent())

        insertContact(db, 10)
        db.execSQL("""
            INSERT INTO pending_messages (
                id, contactId, eventId, shortVariant, standardVariant, longVariant,
                formalVariant, funnyVariant, emotionalVariant, selectedVariant,
                selectedVariantText, channel, scheduledForMs, approvalMode, status,
                aiModel, generatedAtMs, editedByUser, userEditedText, qualityScore,
                tone, length, includeEmoji
            ) VALUES (
                'pending_1', 'contact_1', 'event_1', 'Short', 'Standard', 'Long',
                'Formal', 'Funny', 'Emotional', 'standard', 'Standard', 'SMS',
                1800000000000, 'SMART_APPROVE', 'PENDING', 'flash', 1700000000000,
                0, NULL, 91, 'WARM', 'STANDARD', 1
            )
        """.trimIndent())
        db.execSQL("DROP TABLE pending_messages_old")
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName,
            11,
            true,
            AppDatabase.MIGRATION_10_11,
        )

        db.query("SELECT scheduledYear, isUsingFallback FROM pending_messages WHERE id = 'pending_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
        }
        db.close()
    }

    private fun migrateAndAssertPreservesRepresentativeData(startVersion: Int) {
        val dbName = "migration-$startVersion-to-14"
        var db = helper.createDatabase(dbName, startVersion)
        insertRepresentativeRows(db, startVersion)
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName,
            14,
            true,
            *migrationsFrom(startVersion),
        )

        assertRepresentativeRows(db, startVersion)
        db.close()
    }

    private fun migrationsFrom(startVersion: Int): Array<Migration> {
        return when (startVersion) {
            4 -> arrayOf(
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
            )
            5 -> arrayOf(
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
            )
            6 -> arrayOf(
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
            )
            9 -> arrayOf(
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
            )
            10 -> arrayOf(
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
            )
            11 -> arrayOf(
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
            )
            13 -> arrayOf(AppDatabase.MIGRATION_13_14)
            else -> error("Unsupported migration start version: $startVersion")
        }
    }

    private fun insertRepresentativeRows(db: SupportSQLiteDatabase, version: Int) {
        insertContact(db, version)
        insertEvent(db, version)
        insertPendingMessage(db, version)
        insertSentMessage(db, version)
        insertMemoryNote(db)
        insertGiftHistory(db)
    }

    private fun insertContact(db: SupportSQLiteDatabase, version: Int) {
        when (version) {
            4 -> db.execSQL("""
                INSERT INTO contacts (
                    id, googleContactId, name, nickname, birthdayDay, birthdayMonth, birthdayYear,
                    anniversaryDay, anniversaryMonth, anniversaryYear, workStartDay, workStartMonth,
                    workStartYear, primaryPhone, secondaryPhone, primaryEmail, company, jobTitle,
                    address, profilePhotoUri, relationshipType, relationshipSubtype, preferredLanguage,
                    preferredChannel, formalityLevel, communicationStyle, healthScore, engagementScore,
                    interactionFrequencyPerMonth, lastInteractionDate, lastWishedDate, consecutiveYearsWished,
                    automationMode, giftBudgetInr, skipAutoWish, customSendTimeHour, customSendTimeMinute,
                    interestsJson, hobbiesJson, sharedHistoryJson, favoritesJson, notesText,
                    typicalMoodWhenContacted, sensitiveTopicsJson, currentLifePhaseJson, createdAt,
                    updatedAt, isArchived
                ) VALUES (
                    'contact_1', 'google_1', 'John Doe', 'Johnny', 15, 6, 1990,
                    10, 12, 2015, 1, 4, 2020, '+10000000000', '+10000000001',
                    'john@example.com', 'Acme', 'Engineer', 'Street 1', 'photo://john',
                    'FRIEND', 'college_friend', 'en', 'SMS', 'CASUAL', 'WARM',
                    72, 64, 2.5, 1600000000000, 1700000000000, 3, 'SMART_APPROVE',
                    500, 0, 9, 30, '["cricket"]', '["music"]', '["college"]',
                    '{"food":"biryani"}', 'Important note', 'NEUTRAL', '[]', '{}',
                    1500000000000, 1700000000000, 0
                )
            """.trimIndent())
            5 -> db.execSQL("""
                INSERT INTO contacts (
                    id, googleContactId, name, nickname, birthdayDay, birthdayMonth, birthdayYear,
                    anniversaryDay, anniversaryMonth, anniversaryYear, workStartDay, workStartMonth,
                    workStartYear, primaryPhone, secondaryPhone, primaryEmail, company, jobTitle,
                    address, profilePhotoUri, contactGroup, relationshipType, relationshipSubtype,
                    preferredLanguage, preferredChannel, formalityLevel, communicationStyle, healthScore,
                    engagementScore, interactionFrequencyPerMonth, lastInteractionDate, lastWishedDate,
                    consecutiveYearsWished, automationMode, giftBudgetInr, skipAutoWish,
                    customSendTimeHour, customSendTimeMinute, interestsJson, hobbiesJson,
                    sharedHistoryJson, favoritesJson, notesText, typicalMoodWhenContacted,
                    sensitiveTopicsJson, currentLifePhaseJson, createdAt, updatedAt, isArchived
                ) VALUES (
                    'contact_1', 'google_1', 'John Doe', 'Johnny', 15, 6, 1990,
                    10, 12, 2015, 1, 4, 2020, '+10000000000', '+10000000001',
                    'john@example.com', 'Acme', 'Engineer', 'Street 1', 'photo://john',
                    'Friends', 'FRIEND', 'college_friend', 'en', 'SMS', 'CASUAL', 'WARM',
                    72, 64, 2.5, 1600000000000, 1700000000000, 3, 'SMART_APPROVE',
                    500, 0, 9, 30, '["cricket"]', '["music"]', '["college"]',
                    '{"food":"biryani"}', 'Important note', 'NEUTRAL', '[]', '{}',
                    1500000000000, 1700000000000, 0
                )
            """.trimIndent())
            6 -> db.execSQL("""
                INSERT INTO contacts (
                    id, googleContactId, name, nickname, birthdayDay, birthdayMonth, birthdayYear,
                    anniversaryDay, anniversaryMonth, anniversaryYear, workStartDay, workStartMonth,
                    workStartYear, primaryPhone, secondaryPhone, primaryEmail, company, jobTitle,
                    address, profilePhotoUri, contactGroup, relationshipType, relationshipSubtype,
                    preferredLanguage, preferredChannel, formalityLevel, communicationStyle, healthScore,
                    engagementScore, interactionFrequencyPerMonth, lastInteractionDate, lastWishedDate,
                    consecutiveYearsWished, automationMode, giftBudgetInr, skipAutoWish,
                    customSendTimeHour, customSendTimeMinute, interestsJson, hobbiesJson,
                    sharedHistoryJson, favoritesJson, relationsJson, notesText, typicalMoodWhenContacted,
                    sensitiveTopicsJson, currentLifePhaseJson, createdAt, updatedAt, isArchived
                ) VALUES (
                    'contact_1', 'google_1', 'John Doe', 'Johnny', 15, 6, 1990,
                    10, 12, 2015, 1, 4, 2020, '+10000000000', '+10000000001',
                    'john@example.com', 'Acme', 'Engineer', 'Street 1', 'photo://john',
                    'Friends', 'FRIEND', 'college_friend', 'en', 'SMS', 'CASUAL', 'WARM',
                    72, 64, 2.5, 1600000000000, 1700000000000, 3, 'SMART_APPROVE',
                    500, 0, 9, 30, '["cricket"]', '["music"]', '["college"]',
                    '{"food":"biryani"}', '[{"person":"Jane","type":"spouse"}]',
                    'Important note', 'NEUTRAL', '[]', '{}', 1500000000000, 1700000000000, 0
                )
            """.trimIndent())
            9 -> db.execSQL("""
                INSERT INTO contacts (
                    id, googleContactId, name, nickname, birthdayDay, birthdayMonth, birthdayYear,
                    anniversaryDay, anniversaryMonth, anniversaryYear, workStartDay, workStartMonth,
                    workStartYear, primaryPhone, secondaryPhone, primaryEmail, company, jobTitle,
                    address, profilePhotoUri, contactGroup, relationshipType, relationshipSubtype,
                    preferredLanguage, preferredChannel, formalityLevel, communicationStyle,
                    classificationConfidence, healthScore, engagementScore, interactionFrequencyPerMonth,
                    lastInteractionDate, lastWishedDate, consecutiveYearsWished, automationMode,
                    giftBudgetInr, skipAutoWish, customSendTimeHour, customSendTimeMinute,
                    interestsJson, hobbiesJson, sharedHistoryJson, favoritesJson, relationsJson,
                    notesText, typicalMoodWhenContacted, sensitiveTopicsJson, currentLifePhaseJson,
                    createdAt, updatedAt, isArchived
                ) VALUES (
                    'contact_1', 'google_1', 'John Doe', 'Johnny', 15, 6, 1990,
                    10, 12, 2015, 1, 4, 2020, '+10000000000', '+10000000001',
                    'john@example.com', 'Acme', 'Engineer', 'Street 1', 'photo://john',
                    'Friends', 'FRIEND', 'college_friend', 'en', 'SMS', 'CASUAL', 'WARM',
                    0.82, 72, 64, 2.5, 1600000000000, 1700000000000, 3, 'SMART_APPROVE',
                    500, 0, 9, 30, '["cricket"]', '["music"]', '["college"]',
                    '{"food":"biryani"}', '[{"person":"Jane","type":"spouse"}]',
                    'Important note', 'NEUTRAL', '[]', '{}', 1500000000000, 1700000000000, 0
                )
            """.trimIndent())
            10, 11, 13, 14 -> db.execSQL("""
                INSERT INTO contacts (
                    id, googleContactId, name, nickname, birthdayDay, birthdayMonth, birthdayYear,
                    anniversaryDay, anniversaryMonth, anniversaryYear, workStartDay, workStartMonth,
                    workStartYear, primaryPhone, secondaryPhone, primaryEmail, company, jobTitle,
                    address, profilePhotoUri, contactGroup, relationshipType, relationshipSubtype,
                    preferredLanguage, preferredChannel, formalityLevel, communicationStyle,
                    classificationConfidence, healthScore, engagementScore, interactionFrequencyPerMonth,
                    lastInteractionDate, lastWishedDate, consecutiveYearsWished, lastRevivalAttemptMs,
                    automationMode, giftBudgetInr, annualBudgetInr, skipAutoWish, customSendTimeHour,
                    customSendTimeMinute, interestsJson, hobbiesJson, sharedHistoryJson, favoritesJson,
                    relationsJson, notesText, typicalMoodWhenContacted, sensitiveTopicsJson,
                    currentLifePhaseJson, createdAt, updatedAt, isArchived, isDeleted
                ) VALUES (
                    'contact_1', 'google_1', 'John Doe', 'Johnny', 15, 6, 1990,
                    10, 12, 2015, 1, 4, 2020, '+10000000000', '+10000000001',
                    'john@example.com', 'Acme', 'Engineer', 'Street 1', 'photo://john',
                    'Friends', 'FRIEND', 'college_friend', 'en', 'SMS', 'CASUAL', 'WARM',
                    0.82, 72, 64, 2.5, 1600000000000, 1700000000000, 3, 0,
                    'SMART_APPROVE', 500, 1200, 0, 9, 30, '["cricket"]', '["music"]',
                    '["college"]', '{"food":"biryani"}', '[{"person":"Jane","type":"spouse"}]',
                    'Important note', 'NEUTRAL', '[]', '{}', 1500000000000, 1700000000000, 0, 0
                )
            """.trimIndent())
        }
    }

    private fun insertEvent(db: SupportSQLiteDatabase, version: Int) {
        val sql = when (version) {
            4, 5, 6 -> """
                INSERT INTO events (
                    id, contactId, type, label, dayOfMonth, month, year, nextOccurrenceMs,
                    daysUntil, isActive, notifyDaysBefore, source, confidenceScore, isVerified
                ) VALUES (
                    'event_1', 'contact_1', 'BIRTHDAY', 'Birthday', 15, 6, 1990,
                    1800000000000, 42, 1, 1, 'CONTACTS', 100, 1
                )
            """.trimIndent()
            9 -> """
                INSERT INTO events (
                    id, contactId, type, label, dayOfMonth, month, year, nextOccurrenceMs,
                    notifyDaysBefore, source, confidenceScore, isVerified
                ) VALUES (
                    'event_1', 'contact_1', 'BIRTHDAY', 'Birthday', 15, 6, 1990,
                    1800000000000, 1, 'CONTACTS', 100, 1
                )
            """.trimIndent()
            10, 11, 13, 14 -> """
                INSERT INTO events (
                    id, contactId, type, label, dayOfMonth, month, year, nextOccurrenceMs,
                    isActive, notifyDaysBefore, source, confidenceScore, isVerified
                ) VALUES (
                    'event_1', 'contact_1', 'BIRTHDAY', 'Birthday', 15, 6, 1990,
                    1800000000000, 1, 1, 'CONTACTS', 100, 1
                )
            """.trimIndent()
            else -> error("Unsupported version: $version")
        }
        db.execSQL(sql)
    }

    private fun insertPendingMessage(db: SupportSQLiteDatabase, version: Int) {
        val sql = if (version in setOf(10, 11, 13, 14)) {
            """
                INSERT INTO pending_messages (
                    id, contactId, eventId, shortVariant, standardVariant, longVariant,
                    formalVariant, funnyVariant, emotionalVariant, selectedVariant,
                    selectedVariantText, channel, scheduledForMs, approvalMode, status,
                    aiModel, generatedAtMs, editedByUser, userEditedText, qualityScore,
                    tone, length, includeEmoji, scheduledYear, isUsingFallback
                ) VALUES (
                    'pending_1', 'contact_1', 'event_1', 'Short', 'Standard', 'Long',
                    'Formal', 'Funny', 'Emotional', 'standard', 'Standard', 'SMS',
                    1800000000000, 'SMART_APPROVE', 'PENDING', 'flash', 1700000000000,
                    0, NULL, 91, 'WARM', 'STANDARD', 1, 2027, 1
                )
            """.trimIndent()
        } else {
            """
                INSERT INTO pending_messages (
                    id, contactId, eventId, shortVariant, standardVariant, longVariant,
                    formalVariant, funnyVariant, emotionalVariant, selectedVariant,
                    selectedVariantText, channel, scheduledForMs, approvalMode, status,
                    aiModel, generatedAtMs, editedByUser, userEditedText, qualityScore,
                    tone, length, includeEmoji
                ) VALUES (
                    'pending_1', 'contact_1', 'event_1', 'Short', 'Standard', 'Long',
                    'Formal', 'Funny', 'Emotional', 'standard', 'Standard', 'SMS',
                    1800000000000, 'SMART_APPROVE', 'PENDING', 'flash', 1700000000000,
                    0, NULL, 91, 'WARM', 'STANDARD', 1
                )
            """.trimIndent()
        }
        db.execSQL(sql)
    }

    private fun insertSentMessage(db: SupportSQLiteDatabase, version: Int) {
        val sql = if (version in setOf(4, 5, 6)) {
            """
                INSERT INTO sent_messages (
                    id, contactId, eventType, eventYear, messageText, channel, sentAtMs,
                    deliveryStatus, aiGenerated, geminiModel, variantUsed, replyAtMs
                ) VALUES (
                    'sent_1', 'contact_1', 'BIRTHDAY', 2026, 'Happy birthday', 'SMS',
                    1700000000000, 'SENT', 1, 'flash', 'standard', NULL
                )
            """.trimIndent()
        } else {
            """
                INSERT INTO sent_messages (
                    id, contactId, eventType, eventYear, messageText, channel, sentAtMs,
                    deliveryStatus, aiGenerated, geminiModel, variantUsed, replyReceived,
                    replyAtMs, isContactDeleted
                ) VALUES (
                    'sent_1', 'contact_1', 'BIRTHDAY', 2026, 'Happy birthday', 'SMS',
                    1700000000000, 'SENT', 1, 'flash', 'standard', 0, NULL, 0
                )
            """.trimIndent()
        }
        db.execSQL(sql)
    }

    private fun insertMemoryNote(db: SupportSQLiteDatabase) {
        db.execSQL("""
            INSERT INTO memory_notes (id, contactId, noteText, category, dateMs, isPinned)
            VALUES ('memory_1', 'contact_1', 'Loves old songs', 'PREFERENCE', 1700000000000, 1)
        """.trimIndent())
    }

    private fun insertGiftHistory(db: SupportSQLiteDatabase) {
        db.execSQL("""
            INSERT INTO gift_history (
                id, contactId, giftName, giftCategory, occasionType, year,
                approxCostInr, receivedWell, notes
            ) VALUES (
                'gift_1', 'contact_1', 'Book', 'Books', 'BIRTHDAY', 2025, 800, 1, 'Liked it'
            )
        """.trimIndent())
    }

    private fun assertRepresentativeRows(db: SupportSQLiteDatabase, sourceVersion: Int) {
        assertCount(db, "contacts")
        assertCount(db, "events")
        assertCount(db, "pending_messages")
        assertCount(db, "sent_messages")
        assertCount(db, "memory_notes")
        assertCount(db, "gift_history")

        db.query("SELECT name, isDeleted, annualBudgetInr, relationsJson FROM contacts WHERE id = 'contact_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("John Doe", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(if (sourceVersion in setOf(10, 11, 13, 14)) 1200 else 0, cursor.getInt(2))
            assertTrue(cursor.getString(3).isNotBlank())
        }

        db.query("SELECT type, isActive FROM events WHERE id = 'event_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("BIRTHDAY", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }

        db.query("SELECT standardVariant, scheduledYear, isUsingFallback FROM pending_messages WHERE id = 'pending_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Standard", cursor.getString(0))
            assertEquals(if (sourceVersion in setOf(10, 11, 13, 14)) 2027 else 0, cursor.getInt(1))
            assertEquals(if (sourceVersion in setOf(10, 11, 13, 14)) 1 else 0, cursor.getInt(2))
        }

        db.query("SELECT messageText, replyReceived, isContactDeleted FROM sent_messages WHERE id = 'sent_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Happy birthday", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }

        db.query("SELECT COUNT(*) FROM activity_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private fun assertCount(db: SupportSQLiteDatabase, table: String) {
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Unexpected row count for $table", 1, cursor.getInt(0))
        }
    }

    private fun assertSentOccasion(
        db: SupportSQLiteDatabase,
        id: String,
        expectedLegacyEventType: String,
        expectedEventId: String?,
        expectedOccasionType: String,
        expectedOccasionLabel: String?,
    ) {
        db.query("SELECT eventType, eventId, occasionType, occasionLabel FROM sent_messages WHERE id = '$id'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expectedLegacyEventType, cursor.getString(0))
            if (expectedEventId == null) {
                assertNull(cursor.getString(1))
            } else {
                assertEquals(expectedEventId, cursor.getString(1))
            }
            assertEquals(expectedOccasionType, cursor.getString(2))
            if (expectedOccasionLabel == null) {
                assertNull(cursor.getString(3))
            } else {
                assertEquals(expectedOccasionLabel, cursor.getString(3))
            }
        }
    }
}
