package com.yashsomani.birthdayautopilot.storage.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2InstrumentationTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun deleteBefore() {
    context.deleteDatabase(TEST_DATABASE)
  }

  @After
  fun deleteAfter() {
    context.deleteDatabase(TEST_DATABASE)
  }

  @Test
  fun migrationPreservesLegacyRowsAndQuarantinesThem() {
    val legacyHelper = FrameworkSQLiteOpenHelperFactory().create(
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(TEST_DATABASE)
        .callback(
          object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
              V1_SCHEMA.forEach(db::execSQL)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
              error("test-fixture-does-not-upgrade")
          },
        )
        .build(),
    )
    legacyHelper.writableDatabase.apply {
      execSQL(
        """
        INSERT INTO app_control(
          singletonId, revision, blockerRevision, accountMode, automationDesired,
          activeInstallationEpoch, lastTrustedServerMillis, lastTrustedElapsedMillis,
          trustedBootCount, resetSafetyState
        ) VALUES(1, 4, 9, 'AUTOMATION_ACTIVE', 1, 7, 1000, 100, 1, 'CLEAR')
        """.trimIndent(),
      )
      execSQL(
        """
        INSERT INTO callback_counter(singletonId, generation, nextPositiveId)
        VALUES(1, 'legacy-generation', 3)
        """.trimIndent(),
      )
      execSQL(
        """
        INSERT INTO contacts(
          localId, sourceFingerprint, displayName, safeGivenName, birthdayMonth, birthdayDay,
          birthdayYear, leapDayPolicy, phoneE164, normalizedDestinationBasis, maskedPhone,
          sourceUpdatedAtMillis, readiness, enrollment, sourceDeleted
        ) VALUES(
          'legacy-contact', 'source-fingerprint', 'Legacy Person', 'Legacy', 7, 12, NULL,
          NULL, '+919999999999', 'destination-fingerprint', '•••• 9999', 1000,
          'READY', 'ENABLED', 0
        )
        """.trimIndent(),
      )
    }
    legacyHelper.close()

    val database = Room.databaseBuilder(context, BirthdayDatabase::class.java, TEST_DATABASE)
      .build()
    try {
        database.openHelper.writableDatabase
        database.query(SimpleSQLiteQuery("SELECT state FROM accounts_v2")).use { cursor ->
          cursor.moveToFirst()
          assertEquals("MIGRATION_REVIEW_REQUIRED", cursor.getString(0))
        }
        database.query(SimpleSQLiteQuery("SELECT state FROM contact_snapshots_v2")).use { cursor ->
          cursor.moveToFirst()
          assertEquals("MIGRATED_REVIEW_REQUIRED", cursor.getString(0))
        }
        database.query(SimpleSQLiteQuery("SELECT state FROM recipient_policies_v2")).use { cursor ->
          cursor.moveToFirst()
          assertEquals("NEEDS_REVIEW", cursor.getString(0))
        }
        database.query(
          SimpleSQLiteQuery(
            "SELECT accountMode, automationDesired, blockerRevision, resetSafetyState, " +
              "initialActivationCompleted FROM app_control",
          ),
        ).use { cursor ->
          cursor.moveToFirst()
          assertEquals("PAUSED_REPAIR", cursor.getString(0))
          assertEquals(0, cursor.getInt(1))
          assertEquals(10, cursor.getLong(2))
          assertEquals("REPAIR_REQUIRED", cursor.getString(3))
          assertEquals(0, cursor.getInt(4))
        }
        database.query(
          SimpleSQLiteQuery("SELECT COUNT(*) FROM contacts WHERE localId = 'legacy-contact'"),
        )
          .use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
          }
    } finally {
      database.close()
    }
  }

  private companion object {
    const val TEST_DATABASE = "migration-1-2-test.db"
    val V1_SCHEMA = listOf(
      """CREATE TABLE IF NOT EXISTS `app_control` (`singletonId` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `blockerRevision` INTEGER NOT NULL, `accountMode` TEXT NOT NULL, `automationDesired` INTEGER NOT NULL, `activeInstallationEpoch` INTEGER, `lastTrustedServerMillis` INTEGER, `lastTrustedElapsedMillis` INTEGER, `trustedBootCount` INTEGER, `resetSafetyState` TEXT NOT NULL, PRIMARY KEY(`singletonId`))""",
      """CREATE TABLE IF NOT EXISTS `contacts` (`localId` TEXT NOT NULL, `sourceFingerprint` TEXT NOT NULL, `displayName` TEXT NOT NULL, `safeGivenName` TEXT, `birthdayMonth` INTEGER, `birthdayDay` INTEGER, `birthdayYear` INTEGER, `leapDayPolicy` TEXT, `phoneE164` TEXT, `normalizedDestinationBasis` TEXT, `maskedPhone` TEXT, `sourceUpdatedAtMillis` INTEGER NOT NULL, `readiness` TEXT NOT NULL, `enrollment` TEXT NOT NULL, `sourceDeleted` INTEGER NOT NULL, PRIMARY KEY(`localId`))""",
      """CREATE UNIQUE INDEX IF NOT EXISTS `index_contacts_sourceFingerprint` ON `contacts` (`sourceFingerprint`)""",
      """CREATE INDEX IF NOT EXISTS `index_contacts_normalizedDestinationBasis` ON `contacts` (`normalizedDestinationBasis`)""",
      """CREATE INDEX IF NOT EXISTS `index_contacts_birthdayMonth_birthdayDay` ON `contacts` (`birthdayMonth`, `birthdayDay`)""",
      """CREATE TABLE IF NOT EXISTS `approvals` (`approvalId` TEXT NOT NULL, `contactId` TEXT NOT NULL, `payloadHash` TEXT NOT NULL, `exactMessage` TEXT NOT NULL, `birthdayRule` TEXT NOT NULL, `windowStartMinutes` INTEGER NOT NULL, `windowEndMinutes` INTEGER NOT NULL, `graceEndMinutes` INTEGER, `simPolicy` TEXT NOT NULL, `segmentCount` INTEGER NOT NULL, `approvedAtMillis` INTEGER NOT NULL, `invalidatedAtMillis` INTEGER, `invalidationReason` TEXT, PRIMARY KEY(`approvalId`), FOREIGN KEY(`contactId`) REFERENCES `contacts`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
      """CREATE UNIQUE INDEX IF NOT EXISTS `index_approvals_contactId` ON `approvals` (`contactId`)""",
      """CREATE TABLE IF NOT EXISTS `occurrences` (`occurrenceId` TEXT NOT NULL, `contactId` TEXT NOT NULL, `localDate` TEXT NOT NULL, `timeZoneId` TEXT NOT NULL, `approvalPayloadHash` TEXT NOT NULL, `idempotencyKey` TEXT NOT NULL, `state` TEXT NOT NULL, `attempt` INTEGER NOT NULL, `armStartBlockerRevision` INTEGER, `serverSubmitNotAfterMillis` INTEGER, `effectiveSubmitNotAfterMillis` INTEGER, `barrierConsumedAtMillis` INTEGER, `submittedAtMillis` INTEGER, `terminalAtMillis` INTEGER, `safeOutcomeCode` TEXT, PRIMARY KEY(`occurrenceId`), FOREIGN KEY(`contactId`) REFERENCES `contacts`(`localId`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
      """CREATE INDEX IF NOT EXISTS `index_occurrences_contactId` ON `occurrences` (`contactId`)""",
      """CREATE INDEX IF NOT EXISTS `index_occurrences_localDate` ON `occurrences` (`localDate`)""",
      """CREATE INDEX IF NOT EXISTS `index_occurrences_state` ON `occurrences` (`state`)""",
      """CREATE UNIQUE INDEX IF NOT EXISTS `index_occurrences_idempotencyKey` ON `occurrences` (`idempotencyKey`)""",
      """CREATE TABLE IF NOT EXISTS `activity` (`activityId` TEXT NOT NULL, `category` TEXT NOT NULL, `safeCode` TEXT NOT NULL, `recordedAtMillis` INTEGER NOT NULL, `relatedOccurrenceId` TEXT, PRIMARY KEY(`activityId`))""",
      """CREATE INDEX IF NOT EXISTS `index_activity_recordedAtMillis` ON `activity` (`recordedAtMillis`)""",
      """CREATE INDEX IF NOT EXISTS `index_activity_category` ON `activity` (`category`)""",
      """CREATE TABLE IF NOT EXISTS `callback_counter` (`singletonId` INTEGER NOT NULL, `generation` TEXT NOT NULL, `nextPositiveId` INTEGER NOT NULL, PRIMARY KEY(`singletonId`))""",
    )
  }
}
