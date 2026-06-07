package com.example.core.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34])
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.name,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate6To11() {
        // Create earliest database with version 6
        var db = helper.createDatabase(TEST_DB, 6)

        // Insert some test data at version 6
        db.execSQL("INSERT INTO contacts (id, name, relationshipType, preferredLanguage, preferredChannel, formalityLevel, communicationStyle, healthScore, engagementScore, interactionFrequencyPerMonth, consecutiveYearsWished, automationMode, giftBudgetInr, skipAutoWish, interestsJson, hobbiesJson, sharedHistoryJson, favoritesJson, relationsJson, notesText, typicalMoodWhenContacted, sensitiveTopicsJson, currentLifePhaseJson, createdAt, updatedAt, isArchived) " +
                "VALUES ('contact_1', 'John Doe', 'FRIEND', 'en', 'SMS', 'CASUAL', 'WARM', 50, 50, 0.0, 0, 'DEFAULT', 500, 0, '[]', '[]', '[]', '{}', '[]', '', 'NEUTRAL', '[]', '{}', 0, 0, 0)")

        db.execSQL("INSERT INTO events (id, contactId, type, label, dayOfMonth, month, year, nextOccurrenceMs, daysUntil, isActive, notifyDaysBefore, source, confidenceScore, isVerified) " +
                "VALUES ('event_1', 'contact_1', 'BIRTHDAY', 'John Doe', 15, 6, 1990, 0, 0, 1, 1, 'CONTACTS', 100, 1)")

        db.close()

        // Open database at version 11 and run all migrations
        db = helper.runMigrationsAndValidate(TEST_DB, 11, true, 
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11
        )

        // Verify that the data survived and new columns are initialized
        val cursor = db.query("SELECT * FROM contacts WHERE id = 'contact_1'")
        assert(cursor.moveToFirst())
        
        // Verify new columns added in MIGRATION_9_10/MIGRATION_10_11: lastRevivalAttemptMs, isDeleted, annualBudgetInr
        val idxLastRevival = cursor.getColumnIndex("lastRevivalAttemptMs")
        val idxIsDeleted = cursor.getColumnIndex("isDeleted")
        val idxAnnualBudget = cursor.getColumnIndex("annualBudgetInr")
        assert(idxLastRevival != -1)
        assert(idxIsDeleted != -1)
        assert(idxAnnualBudget != -1)
        assert(cursor.getLong(idxLastRevival) == 0L)
        assert(cursor.getInt(idxIsDeleted) == 0)
        assert(cursor.getInt(idxAnnualBudget) == 0)
        
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To10() {
        var db = helper.createDatabase(TEST_DB, 9)
        // Insert test data at version 9
        db.execSQL("INSERT INTO contacts (id, name, relationshipType, preferredLanguage, preferredChannel, formalityLevel, communicationStyle, healthScore, engagementScore, interactionFrequencyPerMonth, consecutiveYearsWished, automationMode, giftBudgetInr, skipAutoWish, interestsJson, hobbiesJson, sharedHistoryJson, favoritesJson, relationsJson, notesText, typicalMoodWhenContacted, sensitiveTopicsJson, currentLifePhaseJson, createdAt, updatedAt, isArchived, classificationConfidence) " +
                "VALUES ('contact_1', 'John Doe', 'FRIEND', 'en', 'SMS', 'CASUAL', 'WARM', 50, 50, 0.0, 0, 'DEFAULT', 500, 0, '[]', '[]', '[]', '{}', '[]', '', 'NEUTRAL', '[]', '{}', 0, 0, 0, 0.0)")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, AppDatabase.MIGRATION_9_10)

        val cursor = db.query("SELECT * FROM contacts WHERE id = 'contact_1'")
        assert(cursor.moveToFirst())
        assert(cursor.getLong(cursor.getColumnIndexOrThrow("lastRevivalAttemptMs")) == 0L)
        assert(cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")) == 0)
        assert(cursor.getInt(cursor.getColumnIndexOrThrow("annualBudgetInr")) == 0)
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11() {
        var db = helper.createDatabase(TEST_DB, 10)
        // Insert test data at version 10
        db.execSQL("INSERT INTO contacts (id, name, relationshipType, preferredLanguage, preferredChannel, formalityLevel, communicationStyle, healthScore, engagementScore, interactionFrequencyPerMonth, consecutiveYearsWished, lastRevivalAttemptMs, automationMode, giftBudgetInr, annualBudgetInr, skipAutoWish, interestsJson, hobbiesJson, sharedHistoryJson, favoritesJson, relationsJson, notesText, typicalMoodWhenContacted, sensitiveTopicsJson, currentLifePhaseJson, createdAt, updatedAt, isArchived, isDeleted, classificationConfidence) " +
                "VALUES ('contact_1', 'John Doe', 'FRIEND', 'en', 'SMS', 'CASUAL', 'WARM', 50, 50, 0.0, 0, 0, 'DEFAULT', 500, 0, 0, '[]', '[]', '[]', '{}', '[]', '', 'NEUTRAL', '[]', '{}', 0, 0, 0, 0, 0.0)")
        
        db.execSQL("INSERT INTO pending_messages (id, contactId, eventId, shortVariant, standardVariant, longVariant, formalVariant, funnyVariant, emotionalVariant, selectedVariant, selectedVariantText, channel, scheduledForMs, approvalMode, status, aiModel, generatedAtMs, editedByUser, userEditedText, qualityScore, tone, length, includeEmoji, scheduledYear, isUsingFallback) " +
                "VALUES ('msg_1', 'contact_1', 'event_1', '', 'hello', '', '', '', '', 'standard', 'hello', 'SMS', 0, 'DEFAULT', 'PENDING', 'flash', 0, 0, NULL, 0, 'WARM', 'STANDARD', 1, 0, 0)")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 11, true, AppDatabase.MIGRATION_10_11)

        val cursor = db.query("SELECT * FROM pending_messages WHERE id = 'msg_1'")
        assert(cursor.moveToFirst())
        assert(cursor.getString(cursor.getColumnIndexOrThrow("standardVariant")) == "hello")
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_missingColumns() {
        var db = helper.createDatabase(TEST_DB, 10)
        
        // Recreate pending_messages without isUsingFallback and scheduledYear to simulate missing columns
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
        
        // Insert a dummy contact first because of foreign key constraint
        db.execSQL("INSERT INTO contacts (id, name, relationshipType, preferredLanguage, preferredChannel, formalityLevel, communicationStyle, healthScore, engagementScore, interactionFrequencyPerMonth, consecutiveYearsWished, lastRevivalAttemptMs, automationMode, giftBudgetInr, annualBudgetInr, skipAutoWish, interestsJson, hobbiesJson, sharedHistoryJson, favoritesJson, relationsJson, notesText, typicalMoodWhenContacted, sensitiveTopicsJson, currentLifePhaseJson, createdAt, updatedAt, isArchived, isDeleted, classificationConfidence) " +
                "VALUES ('contact_1', 'John Doe', 'FRIEND', 'en', 'SMS', 'CASUAL', 'WARM', 50, 50, 0.0, 0, 0, 'DEFAULT', 500, 0, 0, '[]', '[]', '[]', '{}', '[]', '', 'NEUTRAL', '[]', '{}', 0, 0, 0, 0, 0.0)")

        db.execSQL("INSERT INTO pending_messages (id, contactId, eventId, shortVariant, standardVariant, longVariant, formalVariant, funnyVariant, emotionalVariant, selectedVariant, selectedVariantText, channel, scheduledForMs, approvalMode, status, aiModel, generatedAtMs, editedByUser, userEditedText, qualityScore, tone, length, includeEmoji) " +
                "VALUES ('msg_1', 'contact_1', 'event_1', '', 'hello', '', '', '', '', 'standard', 'hello', 'SMS', 0, 'DEFAULT', 'PENDING', 'flash', 0, 0, NULL, 0, 'WARM', 'STANDARD', 1)")
        db.execSQL("DROP TABLE pending_messages_old")
        db.close()

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 11, true, AppDatabase.MIGRATION_10_11)

        val cursor = db.query("SELECT * FROM pending_messages")
        // Verify columns are there
        assert(cursor.getColumnIndex("isUsingFallback") >= 0)
        assert(cursor.getColumnIndex("scheduledYear") >= 0)
        cursor.close()
    }
}

