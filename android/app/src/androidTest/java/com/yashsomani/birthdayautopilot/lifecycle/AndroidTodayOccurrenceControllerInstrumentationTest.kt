package com.yashsomani.birthdayautopilot.lifecycle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.automation.state.BirthdayJobState
import com.yashsomani.birthdayautopilot.configuration.ConfigurationOutcome
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.messages.UserControlledSmsComposer
import com.yashsomani.birthdayautopilot.messages.UserControlledSmsComposerDraft
import com.yashsomani.birthdayautopilot.messages.UserControlledSmsComposerOpenResult
import com.yashsomani.birthdayautopilot.messages.MessageTemplateValidator
import com.yashsomani.birthdayautopilot.people.RoomContactsConsentRecorder
import com.yashsomani.birthdayautopilot.planning.BirthdayCapacityPolicy
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.yashsomani.birthdayautopilot.storage.database.ApprovalRecordState
import com.yashsomani.birthdayautopilot.storage.database.ApprovalSnapshotEntity
import com.yashsomani.birthdayautopilot.storage.database.AutomationPolicyEntity
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.BirthdayOccurrenceRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactPhoneEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotState
import com.yashsomani.birthdayautopilot.storage.database.ContactSyncStateEntity
import com.yashsomani.birthdayautopilot.storage.database.CoordinationStateEntity
import com.yashsomani.birthdayautopilot.storage.database.ClockTrustEntity
import com.yashsomani.birthdayautopilot.storage.database.ClockTrustStatus
import com.yashsomani.birthdayautopilot.storage.database.ConsentDecision
import com.yashsomani.birthdayautopilot.storage.database.ConsentKind
import com.yashsomani.birthdayautopilot.storage.database.ConsentReceiptEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.LocalDestinationGuardEntity
import com.yashsomani.birthdayautopilot.storage.database.MessageTemplateEntity
import com.yashsomani.birthdayautopilot.storage.database.PhoneRecordState
import com.yashsomani.birthdayautopilot.storage.database.PolicyRecordState
import com.yashsomani.birthdayautopilot.storage.database.RecipientEnrollmentState
import com.yashsomani.birthdayautopilot.storage.database.RecipientPolicyEntity
import com.yashsomani.birthdayautopilot.storage.database.ResetSafetyEntity
import com.yashsomani.birthdayautopilot.storage.database.ResetSafetyStatus
import com.yashsomani.birthdayautopilot.storage.database.SyncFreshness
import com.yashsomani.birthdayautopilot.storage.database.TemplateSource
import com.yashsomani.birthdayautopilot.storage.database.TemplateValidationState
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTodayOccurrenceControllerInstrumentationTest {
  private lateinit var context: Context
  private lateinit var database: BirthdayDatabase

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    lifecycleFiles().forEach(File::delete)
    database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    database.birthdayDao().initializeIfAbsent("callback-generation")
    seedReviewedOccurrence(ResetSafetyStatus.BLOCKED)
  }

  @After
  fun tearDown() {
    database.close()
    lifecycleFiles().forEach(File::delete)
  }

  @Test
  fun resetBlockedReviewRetiresOccurrenceBeforeOpeningExactPrivateDraft() = runBlocking {
    val composer = RecordingComposer(canOpen = true, opens = true)
    val controller = controller(composer)
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()

    assertEquals("open-system-composer", review.getString("choice"))
    assertEquals("start-next-year", review.getString("alternativeChoice"))
    assertEquals("•••• 3210", review.getString("maskedDestination"))
    assertFalse(review.toString().contains(CANONICAL_RECIPIENT))
    assertTrue(composer.openedDrafts.isEmpty())

    val confirmed = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "open-system-composer")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    )

    assertTrue(confirmed is ConfigurationOutcome.Success)
    assertEquals(
      UserControlledSmsComposerDraft(CANONICAL_RECIPIENT, EXACT_BODY),
      composer.openedDrafts.single(),
    )
    val retired = requireNotNull(database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID))
    assertEquals(BirthdayJobState.CANCELLED, retired.state)
    assertEquals("USER_OPENED_SYSTEM_COMPOSER", retired.safeOutcomeCode)
    assertTrue(retired.terminalAtMillis != null)
    assertTrue(
      controller.confirmTodayOccurrence(
        JSONObject()
          .put("handle", review.getString("handle"))
          .put("choice", "open-system-composer")
          .put("expectedRevision", expectedRevision.toString()),
        expectedRevision,
      ) is ConfigurationOutcome.Problem,
    )
    assertEquals(1, composer.openedDrafts.size)
  }

  @Test
  fun unavailableForegroundComposerDoesNotConsumeReviewOrRetireOccurrence() = runBlocking {
    val composer = RecordingComposer(canOpen = false, opens = false)
    val controller = controller(composer)
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()

    val result = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "open-system-composer")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    )

    assertTrue(result is ConfigurationOutcome.Problem)
    assertEquals(
      BirthdayJobState.SCHEDULED,
      database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID)?.state,
    )
    assertNull(database.lifecycleProjectionDao().review(review.getString("handle"))?.consumedAtMillis)
    assertTrue(composer.openedDrafts.isEmpty())
  }

  @Test
  fun durableArmedEvidenceForCivilDateFeedsFailClosedDailyCapPreflight() = runBlocking {
    val ledger = database.safetyLedgerDao()
    assertEquals(0, ledger.armedBirthdayGuardCount(ACCOUNT_ID, LOCAL_DATE, "SMS"))
    database.openHelper.writableDatabase.execSQL(
      "UPDATE local_destination_guards_v2 SET armedOrLater = 1, armedAtMillis = ? WHERE occurrenceId = ?",
      arrayOf(NOW_MILLIS, OCCURRENCE_ID),
    )

    val consumed = ledger.armedBirthdayGuardCount(ACCOUNT_ID, LOCAL_DATE, "SMS")
    assertEquals(1, consumed)
    assertFalse(
      BirthdayCapacityPolicy.allowsArm(
        armedOnCivilDate = consumed,
        dailyCap = 1,
        occurrenceAlreadyArmed = false,
      ),
    )
    assertTrue(
      BirthdayCapacityPolicy.allowsArm(
        armedOnCivilDate = consumed,
        dailyCap = 1,
        occurrenceAlreadyArmed = true,
      ),
    )
  }

  @Test
  fun knownLaunchFailureRestoresOnlyTheExactUnarmedOccurrenceForFreshReview() = runBlocking {
    val composer = RecordingComposer(canOpen = true, opens = false)
    val controller = controller(composer)
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()

    val result = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "open-system-composer")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ) as ConfigurationOutcome.Problem

    assertEquals("system-composer-unavailable", result.payload.getString("code"))
    val restored = requireNotNull(database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID))
    assertEquals(BirthdayJobState.SCHEDULED, restored.state)
    assertNull(restored.safeOutcomeCode)
    assertNull(restored.terminalAtMillis)
    assertTrue(
      database.lifecycleProjectionDao().review(review.getString("handle"))?.consumedAtMillis != null,
    )
    assertEquals(1, composer.openAttempts)
    assertTrue(composer.openedDrafts.isEmpty())
    assertTrue(
      controller.prepareTodayOccurrence(
        JSONObject()
          .put("occurrenceId", OCCURRENCE_ID)
          .put("expectedRevision", controlRevision().toString()),
        controlRevision(),
      ) is ConfigurationOutcome.Success,
    )
  }

  @Test
  fun unknownLaunchOutcomeNeverRestoresAndKeepsDuplicateBarrierRetired() = runBlocking {
    val composer = RecordingComposer(
      canOpen = true,
      opens = false,
      forcedOpenResult = UserControlledSmsComposerOpenResult.UNKNOWN,
    )
    val controller = controller(composer)
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()

    val result = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "open-system-composer")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ) as ConfigurationOutcome.Problem

    assertEquals("system-composer-outcome-unknown", result.payload.getString("code"))
    val retired = requireNotNull(database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID))
    assertEquals(BirthdayJobState.CANCELLED, retired.state)
    assertEquals("USER_OPENED_SYSTEM_COMPOSER", retired.safeOutcomeCode)
    assertNull(database.configurationDao().reviewableOccurrenceId(CONTACT_ID, LOCAL_DATE))
    assertEquals(1, composer.openAttempts)
  }

  @Test
  fun blockerRevisionChangeInvalidatesReviewBeforeComposerPreflight() = runBlocking {
    val composer = RecordingComposer(canOpen = true, opens = true)
    val controller = controller(composer)
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()
    database.openHelper.writableDatabase.execSQL(
      "UPDATE app_control SET blockerRevision = blockerRevision + 1 WHERE singletonId = 1",
    )

    val result = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "open-system-composer")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    )

    assertTrue(result is ConfigurationOutcome.Problem)
    assertTrue(composer.openedDrafts.isEmpty())
    assertEquals(
      BirthdayJobState.SCHEDULED,
      database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID)?.state,
    )
  }

  @Test
  fun exactAccountSessionChangeInvalidatesReviewBeforeComposerPreflight() = runBlocking {
    var sessionMatches = true
    val composer = RecordingComposer(canOpen = true, opens = true)
    val controller = controller(composer) { sessionMatches }
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()
    sessionMatches = false

    val result = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "open-system-composer")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    )

    assertTrue(result is ConfigurationOutcome.Problem)
    assertTrue(composer.openedDrafts.isEmpty())
    assertEquals(
      BirthdayJobState.SCHEDULED,
      database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID)?.state,
    )
  }

  @Test
  fun clearResetSafetyKeepsTheNormalProtectedPathAndNeverTouchesComposer() = runBlocking {
    markResetSafetyClear()
    val composer = RecordingComposer(canOpen = true, opens = true)
    val controller = controller(composer)
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()

    assertEquals("send-through-normal-path", review.getString("choice"))
    assertEquals("start-next-year", review.getString("alternativeChoice"))
    val result = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "send-through-normal-path")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    )

    assertTrue(result is ConfigurationOutcome.Success)
    assertEquals(
      BirthdayJobState.SCHEDULED,
      database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID)?.state,
    )
    assertTrue(composer.openedDrafts.isEmpty())
  }

  @Test
  fun nextYearRemainsAnExplicitAlternativeToTheNormalProtectedPath() = runBlocking {
    markResetSafetyClear()
    val composer = RecordingComposer(canOpen = true, opens = true)
    val controller = controller(composer)
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()

    val result = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "start-next-year")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    )

    assertTrue(result is ConfigurationOutcome.Success)
    val retired = requireNotNull(database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID))
    assertEquals(BirthdayJobState.CANCELLED, retired.state)
    assertEquals("USER_CHOSE_NEXT_YEAR", retired.safeOutcomeCode)
    assertTrue(composer.openedDrafts.isEmpty())
  }

  @Test
  fun closedWindowOffersTheComposerWithoutCreatingAnUnattendedException() = runBlocking {
    markResetSafetyClear()
    database.openHelper.writableDatabase.execSQL(
      "UPDATE birthday_occurrences_v2 SET resolvedWindowStartMillis = ?, " +
        "resolvedWindowEndMillis = ? WHERE occurrenceId = ?",
      arrayOf<Any>(NOW_MILLIS - 2 * 60 * 60 * 1_000, NOW_MILLIS, OCCURRENCE_ID),
    )
    val composer = RecordingComposer(canOpen = true, opens = true)
    val controller = controller(composer)
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()

    assertEquals("open-system-composer", review.getString("choice"))
    val result = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "open-system-composer")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    )

    assertTrue(result is ConfigurationOutcome.Success)
    assertEquals(1, composer.openedDrafts.size)
    assertEquals(
      BirthdayJobState.CANCELLED,
      database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID)?.state,
    )
  }

  @Test
  fun alreadyMissedOccurrenceIsReviewableOnceAndKeepsItsTerminalState() = runBlocking {
    markResetSafetyClear()
    database.openHelper.writableDatabase.execSQL(
      """
      UPDATE birthday_occurrences_v2
      SET state = 'MISSED', resolvedWindowStartMillis = ?, resolvedWindowEndMillis = ?,
        terminalAtMillis = ?,
        safeOutcomeCode = 'WINDOW_CLOSED', revision = revision + 1
      WHERE occurrenceId = ?
      """.trimIndent(),
      arrayOf<Any>(
        NOW_MILLIS - 2 * 60 * 60 * 1_000,
        NOW_MILLIS,
        NOW_MILLIS,
        OCCURRENCE_ID,
      ),
    )
    assertEquals(
      OCCURRENCE_ID,
      database.configurationDao().reviewableOccurrenceId(CONTACT_ID, LOCAL_DATE),
    )
    val composer = RecordingComposer(canOpen = true, opens = true)
    val controller = controller(composer)
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()

    assertEquals("open-system-composer", review.getString("choice"))
    val result = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "open-system-composer")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    )

    assertTrue(result is ConfigurationOutcome.Success)
    val missed = requireNotNull(database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID))
    assertEquals(BirthdayJobState.MISSED, missed.state)
    assertEquals("USER_OPENED_SYSTEM_COMPOSER", missed.safeOutcomeCode)
    assertNull(database.configurationDao().reviewableOccurrenceId(CONTACT_ID, LOCAL_DATE))
    assertEquals(1, composer.openedDrafts.size)
  }

  @Test
  fun standbyInstallationCannotPrepareAUserControlledTodayAction() = runBlocking {
    database.openHelper.writableDatabase.execSQL(
      "UPDATE app_control SET accountMode = 'STANDBY', activeInstallationEpoch = NULL " +
        "WHERE singletonId = 1",
    )
    database.openHelper.writableDatabase.execSQL(
      "UPDATE installation_bindings_v2 SET state = 'STANDBY', accountMode = 'STANDBY', " +
        "senderEpoch = NULL WHERE installationId = ?",
      arrayOf(INSTALLATION_ID),
    )
    val composer = RecordingComposer(canOpen = true, opens = true)
    val expectedRevision = controlRevision()

    val result = controller(composer).prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ) as ConfigurationOutcome.Problem

    assertEquals("active-sender-other-device", result.payload.getString("code"))
    assertTrue(composer.openedDrafts.isEmpty())
    assertEquals(
      BirthdayJobState.SCHEDULED,
      database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID)?.state,
    )
  }

  @Test
  fun senderEpochChangeAfterReviewBlocksRetirementAndComposerLaunch() = runBlocking {
    val composer = RecordingComposer(canOpen = true, opens = true)
    val controller = controller(composer)
    val expectedRevision = controlRevision()
    val review = controller.prepareTodayOccurrence(
      JSONObject()
        .put("occurrenceId", OCCURRENCE_ID)
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ).successPayload()
    database.openHelper.writableDatabase.execSQL(
      "UPDATE coordination_state_v2 SET senderEpoch = senderEpoch + 1 WHERE accountId = ?",
      arrayOf(ACCOUNT_ID),
    )

    val result = controller.confirmTodayOccurrence(
      JSONObject()
        .put("handle", review.getString("handle"))
        .put("choice", "open-system-composer")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    ) as ConfigurationOutcome.Problem

    assertEquals("active-sender-other-device", result.payload.getString("code"))
    assertTrue(composer.openedDrafts.isEmpty())
    assertEquals(
      BirthdayJobState.SCHEDULED,
      database.safetyLedgerDao().getBirthdayOccurrence(OCCURRENCE_ID)?.state,
    )
    assertNull(database.lifecycleProjectionDao().review(review.getString("handle"))?.consumedAtMillis)
  }

  @Test
  fun exactContactsConsentReceiptUnlocksTheRealBirthdayClaimPreflight() = runBlocking {
    val ledger = database.safetyLedgerDao()
    database.openHelper.writableDatabase.execSQL(
      "UPDATE app_control SET accountMode = 'AUTOMATION_ACTIVE', automationDesired = 1, " +
        "activeInstallationEpoch = 1 WHERE singletonId = 1",
    )
    database.openHelper.writableDatabase.execSQL(
      "UPDATE installation_bindings_v2 SET accountMode = 'AUTOMATION_ACTIVE', " +
        "ownerLeaseUntilMillis = ? WHERE installationId = ?",
      arrayOf<Any>(NOW_MILLIS + 60_000, INSTALLATION_ID),
    )
    database.openHelper.writableDatabase.execSQL(
      "UPDATE coordination_state_v2 SET mode = 'AUTOMATION_ACTIVE', " +
        "ownerLeaseUntilMillis = ? WHERE accountId = ?",
      arrayOf<Any>(NOW_MILLIS + 60_000, ACCOUNT_ID),
    )
    database.openHelper.writableDatabase.execSQL(
      "UPDATE reset_safety_v2 SET status = 'CLEAR', overflowBlocked = 0, " +
        "birthdayAutomationNotBeforeMillis = ? WHERE accountId = ?",
      arrayOf<Any>(NOW_MILLIS - 1, ACCOUNT_ID),
    )
    ledger.putContactSyncState(
      ContactSyncStateEntity(
        accountId = ACCOUNT_ID,
        activeGeneration = "generation",
        stagingGeneration = null,
        syncToken = "opaque-sync-token",
        parametersHash = "parameters-hash",
        freshness = SyncFreshness.FRESH,
        lastFullSuccessMillis = NOW_MILLIS - 1_000,
        lastIncrementalSuccessMillis = null,
        lastAttemptMillis = NOW_MILLIS - 1_000,
        lastErrorCode = null,
        revision = 0,
      ),
    )
    ledger.insertClockTrust(
      ClockTrustEntity(
        accountId = ACCOUNT_ID,
        status = ClockTrustStatus.TRUSTED,
        greatestTrustedServerMillis = NOW_MILLIS - 1,
        lastDeviceWallMillis = NOW_MILLIS,
        lastElapsedRealtimeMillis = 1_000,
        trustedBootCount = 1,
        lastVerificationMillis = NOW_MILLIS - 1,
        observedDriftMillis = 1,
        revision = 0,
      ),
    )
    ledger.insertConsentReceipt(
      ConsentReceiptEntity(
        receiptId = "sms-standing-receipt",
        accountId = ACCOUNT_ID,
        kind = ConsentKind.SMS_STANDING_APPROVAL,
        decision = ConsentDecision.GRANTED,
        disclosureVersion = "consent-v1",
        scopeHash = null,
        sequence = 1,
        supersedesReceiptId = null,
        recordedAtMillis = NOW_MILLIS - 1_000,
      ),
    )
    val scheduled = requireNotNull(ledger.getBirthdayOccurrence(OCCURRENCE_ID))

    assertFalse(
      ledger.claimBirthdayOccurrence(OCCURRENCE_ID, scheduled.revision, NOW_MILLIS),
    )
    assertTrue(
      RoomContactsConsentRecorder(
        database,
        Clock.fixed(Instant.ofEpochMilli(NOW_MILLIS), java.time.ZoneOffset.UTC),
      ).recordGranted(ACCOUNT_ID, disclosureAcknowledged = true),
    )
    assertTrue(
      ledger.claimBirthdayOccurrence(OCCURRENCE_ID, scheduled.revision, NOW_MILLIS),
    )
    assertEquals(
      BirthdayJobState.CLAIMED,
      ledger.getBirthdayOccurrence(OCCURRENCE_ID)?.state,
    )
  }

  private fun controller(
    composer: UserControlledSmsComposer,
    accountSessionMatches: () -> Boolean = { true },
  ) = AndroidLifecycleController(
    context = context,
    database = database,
    wallClockMillis = { NOW_MILLIS },
    accountSessionMatches = { accountSessionMatches() },
    userControlledSmsComposer = composer,
  )

  private fun markResetSafetyClear() {
    database.openHelper.writableDatabase.execSQL(
      "UPDATE reset_safety_v2 SET status = 'CLEAR', overflowBlocked = 0 WHERE accountId = ?",
      arrayOf(ACCOUNT_ID),
    )
  }

  private suspend fun seedReviewedOccurrence(resetStatus: ResetSafetyStatus) {
    val ledger = database.safetyLedgerDao()
    ledger.insertAccount(
      AccountRecordEntity(
        accountId = ACCOUNT_ID,
        activeSlot = 1,
        googleSubjectHash = "subject-hash",
        firebaseUid = "firebase-uid",
        displayEmail = null,
        localeTag = "en-IN",
        state = AccountRecordState.ACTIVE,
        revision = 0,
        createdAtMillis = NOW_MILLIS - 10_000,
        updatedAtMillis = NOW_MILLIS - 10_000,
      ),
    )
    database.openHelper.writableDatabase.execSQL(
      "UPDATE app_control SET activeInstallationEpoch = 1 WHERE singletonId = 1",
    )
    ledger.insertInstallation(
      InstallationBindingEntity(
        installationId = INSTALLATION_ID,
        accountId = ACCOUNT_ID,
        localSlot = 1,
        callbackGeneration = "callback-generation",
        state = InstallationRecordState.ACTIVE,
        accountMode = AccountMode.PAUSED_REPAIR,
        senderEpoch = 1,
        resetGeneration = 1,
        ownerLeaseUntilMillis = NOW_MILLIS + 60_000,
        appVersionCode = 1,
        distributionChannel = "test",
        signingCertificateSha256 = "certificate",
        lastVerifiedServerMillis = NOW_MILLIS,
        revision = 0,
        createdAtMillis = NOW_MILLIS - 10_000,
        updatedAtMillis = NOW_MILLIS - 10_000,
      ),
    )
    ledger.putCoordinationState(
      CoordinationStateEntity(
        accountId = ACCOUNT_ID,
        mode = AccountMode.PAUSED_REPAIR,
        activeInstallationId = INSTALLATION_ID,
        senderEpoch = 1,
        resetGeneration = 1,
        continuityGeneration = 1,
        ownerLeaseUntilMillis = NOW_MILLIS + 60_000,
        nextArmNotBeforeMillis = null,
        latestIssuedSubmitNotAfterMillis = null,
        birthdayAutomationNotBeforeMillis = null,
        transferDrainUntilMillis = null,
        deletionDrainUntilMillis = null,
        lastSuccessfulCoordinationMillis = NOW_MILLIS,
        lastSafeCode = null,
        revision = 0,
        updatedAtMillis = NOW_MILLIS,
      ),
    )
    ledger.insertAutomationPolicy(
      AutomationPolicyEntity(
        policyId = POLICY_ID,
        accountId = ACCOUNT_ID,
        revision = 1,
        state = PolicyRecordState.ACTIVE,
        timeZoneId = ZONE_ID,
        windowStartMinute = 540,
        windowEndMinute = 660,
        graceEndMinute = null,
        latePolicy = "STRICT_END",
        dailyCap = 10,
        simPolicyKind = "FIXED",
        resolvedSubscriptionId = 1,
        roamingAllowed = false,
        policyVersion = "policy-v1",
        createdAtMillis = NOW_MILLIS - 10_000,
        invalidatedAtMillis = null,
        invalidationReason = null,
      ),
    )
    ledger.insertContactSnapshot(
      ContactSnapshotEntity(
        contactId = CONTACT_ID,
        accountId = ACCOUNT_ID,
        peopleResourceName = "people/private",
        sourceFingerprint = "contact-source",
        sourceEtag = null,
        displayName = "Ada Lovelace",
        safeGivenName = "Ada",
        birthdayMonth = 7,
        birthdayDay = 12,
        birthdayYear = null,
        leapDayPolicy = null,
        state = ContactSnapshotState.ACTIVE,
        syncGeneration = "generation",
        materialRevision = 1,
        sourceUpdatedAtMillis = NOW_MILLIS - 10_000,
        syncedAtMillis = NOW_MILLIS - 10_000,
        deletedAtMillis = null,
      ),
    )
    ledger.insertContactPhone(
      ContactPhoneEntity(
        phoneId = PHONE_ID,
        contactId = CONTACT_ID,
        sourceFingerprint = "phone-source",
        rawNumber = CANONICAL_RECIPIENT,
        normalizedE164 = CANONICAL_RECIPIENT,
        destinationFingerprint = DESTINATION_FINGERPRINT,
        maskedDisplay = "•••• 3210",
        typeLabel = "mobile",
        regionCode = "IN",
        isSmsCapableType = true,
        state = PhoneRecordState.READY,
        materialRevision = 1,
        updatedAtMillis = NOW_MILLIS - 10_000,
      ),
    )
    // The real claim preflight requires the approval's current, validator-bound template lineage.
    ledger.insertMessageTemplate(
      MessageTemplateEntity(
        templateId = TEMPLATE_ID,
        accountId = ACCOUNT_ID,
        source = TemplateSource.USER,
        exactTemplateText = "Happy birthday, {firstName}!",
        languageTag = "en",
        tone = "warm",
        placeholderMode = "PERSONALIZED_FIRST_NAME",
        templateVersion = "template-v1",
        promptPolicyVersion = null,
        validatorVersion = MessageTemplateValidator.VALIDATOR_VERSION,
        modelIdentifier = null,
        contentHash = "template-content-hash",
        validationState = TemplateValidationState.VALID,
        revision = 1,
        createdAtMillis = NOW_MILLIS - 10_000,
        updatedAtMillis = NOW_MILLIS - 10_000,
      ),
    )
    ledger.insertApprovalSnapshot(
      ApprovalSnapshotEntity(
        approvalId = APPROVAL_ID,
        accountId = ACCOUNT_ID,
        contactId = CONTACT_ID,
        phoneId = PHONE_ID,
        schemaVersion = 1,
        contactMaterialRevision = 1,
        phoneMaterialRevision = 1,
        policyId = POLICY_ID,
        policyRevision = 1,
        normalizedPhoneE164 = CANONICAL_RECIPIENT,
        destinationFingerprint = DESTINATION_FINGERPRINT,
        maskedPhoneDisplay = "•••• 3210",
        exactMessage = EXACT_BODY,
        sourceTemplateId = TEMPLATE_ID,
        sourceTemplateVersion = "template-v1",
        placeholderMode = "GIVEN_NAME",
        birthdayMonth = 7,
        birthdayDay = 12,
        leapDayPolicy = null,
        windowStartMinute = 540,
        windowEndMinute = 660,
        graceEndMinute = null,
        latePolicy = "STRICT_END",
        simPolicyKind = "FIXED",
        resolvedSubscriptionId = 1,
        segmentCount = 1,
        messageEncoding = "GSM_7",
        orderedPartsHash = "parts-hash",
        carrierCostDisclosureVersion = "carrier-v1",
        consentDisclosureVersion = "consent-v1",
        contentHash = PAYLOAD_HASH,
        state = ApprovalRecordState.ACTIVE,
        approvedAtMillis = NOW_MILLIS - 10_000,
        invalidatedAtMillis = null,
        invalidationReason = null,
      ),
    )
    ledger.insertRecipientPolicy(
      RecipientPolicyEntity(
        contactId = CONTACT_ID,
        chosenPhoneId = PHONE_ID,
        state = RecipientEnrollmentState.ENABLED,
        explicitEnrollmentEventId = "enrollment-event",
        blockReason = null,
        approvalId = APPROVAL_ID,
        revision = 1,
        enabledAtMillis = NOW_MILLIS - 10_000,
        updatedAtMillis = NOW_MILLIS - 10_000,
      ),
    )
    ledger.insertResetSafety(
      ResetSafetyEntity(
        resetSafetyId = RESET_ID,
        accountId = ACCOUNT_ID,
        resetGeneration = 1,
        resetAtMillis = NOW_MILLIS - 10_000,
        resetLocalDate = LOCAL_DATE,
        resetTimeZoneId = ZONE_ID,
        birthdayAutomationNotBeforeMillis = NOW_MILLIS + 86_400_000,
        status = resetStatus,
        overflowBlocked = false,
        revision = 0,
        updatedAtMillis = NOW_MILLIS - 10_000,
      ),
    )
    ledger.createPlannedBirthdayOccurrence(
      BirthdayOccurrenceRecordEntity(
        occurrenceId = OCCURRENCE_ID,
        accountId = ACCOUNT_ID,
        contactId = CONTACT_ID,
        approvalId = APPROVAL_ID,
        policyId = POLICY_ID,
        localDate = LOCAL_DATE,
        timeZoneId = ZONE_ID,
        // Exact 09:00-11:00 Asia/Kolkata binding required by the production claim preflight.
        resolvedWindowStartMillis = NOW_MILLIS,
        resolvedWindowEndMillis = NOW_MILLIS + 2 * 60 * 60 * 1_000,
        idempotencyKey = "idempotency-key",
        destinationFingerprint = DESTINATION_FINGERPRINT,
        channel = "SMS",
        payloadHash = PAYLOAD_HASH,
        state = BirthdayJobState.PLANNED,
        attemptNumber = 0,
        revision = 0,
        claimedBlockerRevision = null,
        createdAtMillis = NOW_MILLIS - 10_000,
        updatedAtMillis = NOW_MILLIS - 10_000,
        terminalAtMillis = null,
        retentionUntilMillis = NOW_MILLIS + 86_400_000,
        safeOutcomeCode = null,
      ),
      LocalDestinationGuardEntity(
        guardId = "guard-id",
        accountId = ACCOUNT_ID,
        occurrenceId = OCCURRENCE_ID,
        destinationFingerprint = DESTINATION_FINGERPRINT,
        localDate = LOCAL_DATE,
        channel = "SMS",
        armedOrLater = false,
        createdAtMillis = NOW_MILLIS - 10_000,
        armedAtMillis = null,
        retentionUntilMillis = NOW_MILLIS + 86_400_000,
      ),
    )
    assertTrue(database.automationOrchestrationDao().scheduleNewOccurrence(OCCURRENCE_ID, NOW_MILLIS))
  }

  private suspend fun controlRevision(): Long =
    requireNotNull(database.lifecycleProjectionDao().control()).revision

  private fun lifecycleFiles(): List<File> {
    val base = File(context.noBackupFilesDir, "birthday-lifecycle-state-v1")
    return listOf(base, File(base.path + ".bak"), File(base.path + ".new"))
  }

  private fun ConfigurationOutcome.successPayload(): JSONObject =
    (this as ConfigurationOutcome.Success).payload

  private class RecordingComposer(
    private val canOpen: Boolean,
    private val opens: Boolean,
    private val forcedOpenResult: UserControlledSmsComposerOpenResult? = null,
  ) : UserControlledSmsComposer {
    val openedDrafts = mutableListOf<UserControlledSmsComposerDraft>()
    var openAttempts = 0

    override fun canOpen(draft: UserControlledSmsComposerDraft): Boolean = canOpen

    override fun open(
      draft: UserControlledSmsComposerDraft,
    ): UserControlledSmsComposerOpenResult {
      openAttempts += 1
      val result = forcedOpenResult ?: if (opens) {
        UserControlledSmsComposerOpenResult.OPENED
      } else {
        UserControlledSmsComposerOpenResult.KNOWN_FAILURE
      }
      if (result == UserControlledSmsComposerOpenResult.OPENED) openedDrafts += draft
      return result
    }
  }

  private companion object {
    const val NOW_MILLIS = 1_783_827_000_000L // 2026-07-12T09:00:00+05:30
    const val LOCAL_DATE = "2026-07-12"
    const val ZONE_ID = "Asia/Kolkata"
    const val ACCOUNT_ID = "account-id"
    const val CONTACT_ID = "contact-id"
    const val PHONE_ID = "phone-id"
    const val POLICY_ID = "policy-id"
    const val APPROVAL_ID = "approval-id"
    const val TEMPLATE_ID = "template-id"
    const val RESET_ID = "reset-id"
    val INSTALLATION_ID = "b".repeat(32)
    const val DESTINATION_FINGERPRINT = "destination-fingerprint"
    const val PAYLOAD_HASH = "payload-hash"
    const val CANONICAL_RECIPIENT = "+919876543210"
    const val EXACT_BODY = "Happy birthday, Ada!"
    val OCCURRENCE_ID = "occ_${"a".repeat(64)}"
  }
}
