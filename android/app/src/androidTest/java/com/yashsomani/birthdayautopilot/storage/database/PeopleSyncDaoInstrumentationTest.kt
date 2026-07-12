package com.yashsomani.birthdayautopilot.storage.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.people.PeopleBirthday
import com.yashsomani.birthdayautopilot.people.PeopleContactDelta
import com.yashsomani.birthdayautopilot.people.PeopleName
import com.yashsomani.birthdayautopilot.people.PeoplePhone
import com.yashsomani.birthdayautopilot.people.PeopleRequestFactory
import com.yashsomani.birthdayautopilot.people.PeopleSyncCompletion
import com.yashsomani.birthdayautopilot.people.PeopleSyncMode
import com.yashsomani.birthdayautopilot.people.PeopleWallClock
import com.yashsomani.birthdayautopilot.people.RoomPeopleSyncStagingStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeopleSyncDaoInstrumentationTest {
  private lateinit var database: BirthdayDatabase
  private lateinit var dao: PeopleSyncDao
  private var now = 10_000L
  private val fingerprint = PeopleRequestFactory(1_000).parameterFingerprint

  @Before
  fun setUp() = runBlocking {
    val context: Context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    database.birthdayDao().initializeIfAbsent("callback-generation")
    dao = database.peopleSyncDao()
    assertEquals(
      IdentityAttachDecision.ATTACHED,
      dao.attachIdentity(
        AccountRecordEntity(
          accountId = ACCOUNT_ID,
          activeSlot = 1,
          googleSubjectHash = "2".repeat(64),
          firebaseUid = "firebase-uid",
          displayEmail = "person@example.test",
          localeTag = "en-IN",
          state = AccountRecordState.ACTIVE,
          revision = 0,
          createdAtMillis = now,
          updatedAtMillis = now,
        ),
        fingerprint,
      ),
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun fullAndIncrementalGenerationsAreAtomicAndTombstonesRemainRetained() = runBlocking {
    val full = store().begin(PeopleSyncMode.Full)
    assertNotNull(full)
    assertTrue(full!!.stagePage(0, listOf(activeDelta())))
    assertEquals(0, dao.activeContactCount(ACCOUNT_ID))
    assertTrue(full.commit(completion("full-token", changedPeople = 1)))
    assertEquals(1, dao.activeContactCount(ACCOUNT_ID))
    val visible = dao.contactPage(ACCOUNT_ID, "all", "%", 50, 0).single()
    assertEquals(ContactSnapshotState.ACTIVE, visible.state)
    assertEquals("Ada Lovelace", visible.displayName)
    seedPassingTestReceipt()
    assertEquals(1, database.configurationDao().validTestReceipts(ACCOUNT_ID).size)

    val incremental = store().begin(PeopleSyncMode.Incremental("full-token", fingerprint))
    assertNotNull(incremental)
    assertTrue(incremental!!.stagePage(0, listOf(deletedDelta())))
    // A staged tombstone cannot affect the active generation before commit.
    assertEquals(1, dao.activeContactCount(ACCOUNT_ID))
    assertTrue(incremental.commit(completion("incremental-token", changedPeople = 1)))
    assertEquals(0, dao.activeContactCount(ACCOUNT_ID))
    assertEquals(
      ContactSnapshotState.DELETED,
      dao.contactsBySourceFingerprints(ACCOUNT_ID, listOf(visible.sourceFingerprint)).single().state,
    )
    assertTrue(dao.contactPage(ACCOUNT_ID, "all", "%", 50, 0).isEmpty())
    assertTrue(database.configurationDao().validTestReceipts(ACCOUNT_ID).isEmpty())
    database.openHelper.readableDatabase.query(
      "SELECT state, invalidationReason FROM test_receipts_v2 WHERE testReceiptId = 'receipt-1'",
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(TestReceiptState.INVALIDATED.name, cursor.getString(0))
      assertEquals("CONTACT_SYNC_MATERIAL_CHANGED", cursor.getString(1))
    }
  }

  @Test
  fun rollbackDeletesOnlyStagingAndRetainsPriorGeneration() = runBlocking {
    val full = store().begin(PeopleSyncMode.Full)!!
    assertTrue(full.stagePage(0, listOf(activeDelta())))
    assertTrue(full.commit(completion("full-token", changedPeople = 1)))

    val incremental = store().begin(PeopleSyncMode.Incremental("full-token", fingerprint))!!
    assertTrue(incremental.stagePage(0, listOf(deletedDelta())))
    incremental.rollback()

    assertEquals(1, dao.activeContactCount(ACCOUNT_ID))
    assertEquals(ContactSnapshotState.ACTIVE, dao.contactPage(ACCOUNT_ID, "all", "%", 50, 0).single().state)
    assertTrue(dao.contactSyncState(ACCOUNT_ID)?.stagingGeneration == null)
  }

  @Test
  fun postWriteCommitRejectionRollsBackEveryActiveTableMutation() = runBlocking {
    val full = store().begin(PeopleSyncMode.Full)!!
    assertTrue(full.stagePage(0, listOf(activeDelta())))
    database.openHelper.writableDatabase.execSQL("DELETE FROM app_control WHERE singletonId = 1")

    val failure = runCatching {
      full.commit(completion("must-not-commit", changedPeople = 1))
    }.exceptionOrNull()

    assertNotNull(failure)
    assertEquals(0, dao.activeContactCount(ACCOUNT_ID))
    assertTrue(dao.contactPage(ACCOUNT_ID, "all", "%", 50, 0).isEmpty())
    // The failed transaction retains its staging generation for a later bounded rollback/reclaim.
    assertNotNull(dao.contactSyncState(ACCOUNT_ID)?.stagingGeneration)
  }

  private fun store() = RoomPeopleSyncStagingStore(
    dao = dao,
    accountId = ACCOUNT_ID,
    accountLocaleTag = "en-IN",
    parameterFingerprint = fingerprint,
    clock = PeopleWallClock { now++ },
  )

  private fun completion(token: String, changedPeople: Int) = PeopleSyncCompletion(
    nextSyncToken = token,
    parameterFingerprint = fingerprint,
    changedPeople = changedPeople,
    pages = 1,
  )

  private fun activeDelta() = PeopleContactDelta(
    resourceName = "people/abc",
    contactSourceId = "contacts/abc",
    deleted = false,
    names = listOf(PeopleName("Ada Lovelace", "Ada")),
    birthdays = listOf(PeopleBirthday(1815, 12, 12)),
    phoneNumbers = listOf(PeoplePhone("+919876543210", "mobile")),
  )

  private fun deletedDelta() = PeopleContactDelta(
    resourceName = "people/abc",
    contactSourceId = "contacts/abc",
    deleted = true,
    names = emptyList(),
    birthdays = emptyList(),
    phoneNumbers = emptyList(),
  )

  private suspend fun seedPassingTestReceipt() {
    val installation = InstallationBindingEntity(
      installationId = "installation-1",
      accountId = ACCOUNT_ID,
      localSlot = 1,
      callbackGeneration = "callback-generation",
      state = InstallationRecordState.ACTIVE,
      accountMode = AccountMode.TEST_ONLY,
      senderEpoch = 1,
      resetGeneration = 1,
      ownerLeaseUntilMillis = now + 60_000,
      appVersionCode = 1,
      distributionChannel = "RESTRICTED_LAB",
      signingCertificateSha256 = "a".repeat(64),
      lastVerifiedServerMillis = now,
      revision = 0,
      createdAtMillis = now,
      updatedAtMillis = now,
    )
    val test = TestJobEntity(
      testJobId = "test-1",
      accountId = ACCOUNT_ID,
      installationId = installation.installationId,
      senderEpoch = 1,
      testRequestId = "request-1",
      configHash = "b".repeat(64),
      destinationPrehash = "c".repeat(64),
      normalizedDestination = "+919999999999",
      maskedDestination = "•••• 9999",
      exactMessage = "Birthday Autopilot test",
      payloadHash = "d".repeat(64),
      simPolicyKind = "SYSTEM_DEFAULT",
      resolvedSubscriptionId = 1,
      segmentCount = 1,
      messageEncoding = "GSM_7",
      orderedPartsHash = "e".repeat(64),
      buildBindingHash = "f".repeat(64),
      appCheckPolicyVersion = "app-check-v1",
      state = TestJobState.PASSED,
      revision = 1,
      foregroundConfirmationNonceHash = "0".repeat(64),
      foregroundConfirmedAtMillis = now,
      createdAtMillis = now,
      updatedAtMillis = now,
      terminalAtMillis = now,
      invalidationReason = null,
      retentionUntilMillis = now + 60_000,
    )
    database.safetyLedgerDao().insertInstallation(installation)
    database.safetyLedgerDao().insertTestJob(test)
    val receipt = TestReceiptFactory.create(
      test = test,
      installation = installation,
      testReceiptId = "receipt-1",
      smsPolicyVersion = "android-sms-policy-v1",
      passedAtMillis = now,
    )
    database.openHelper.writableDatabase.execSQL(
      """
      INSERT INTO test_receipts_v2 (
        testReceiptId, testJobId, accountId, bindingHash, configHash,
        destinationBindingHash, maskedDestination, exactTextHash, segmentPlanHash,
        resolvedSubscriptionId, installationId, senderEpoch, buildBindingHash,
        distributionChannel, appCheckPolicyVersion, smsPolicyVersion, state,
        passedAtMillis, invalidatedAtMillis, invalidationReason
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """.trimIndent(),
      arrayOf<Any?>(
        receipt.testReceiptId,
        receipt.testJobId,
        receipt.accountId,
        receipt.bindingHash,
        receipt.configHash,
        receipt.destinationBindingHash,
        receipt.maskedDestination,
        receipt.exactTextHash,
        receipt.segmentPlanHash,
        receipt.resolvedSubscriptionId,
        receipt.installationId,
        receipt.senderEpoch,
        receipt.buildBindingHash,
        receipt.distributionChannel,
        receipt.appCheckPolicyVersion,
        receipt.smsPolicyVersion,
        receipt.state.name,
        receipt.passedAtMillis,
        receipt.invalidatedAtMillis,
        receipt.invalidationReason,
      ),
    )
  }

  private companion object {
    val ACCOUNT_ID = "a_${"1".repeat(64)}"
  }
}
