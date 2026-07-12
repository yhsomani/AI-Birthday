package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.automation.sms.SmsOutcomeProcessor
import com.yashsomani.birthdayautopilot.automation.sms.SmsCallbackCleanup
import com.yashsomani.birthdayautopilot.automation.sms.SmsCallbackCleanupResult
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyLedgerDaoInstrumentationTest {
  private lateinit var database: BirthdayDatabase
  private lateinit var dao: SafetyLedgerDao

  @Before
  fun setUp() = runBlocking {
    database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      BirthdayDatabase::class.java,
    ).build()
    database.birthdayDao().initializeIfAbsent(CALLBACK_GENERATION)
    dao = database.safetyLedgerDao()
    dao.insertAccount(
      AccountRecordEntity(
        accountId = ACCOUNT_ID,
        activeSlot = 1,
        googleSubjectHash = "subject-hash",
        firebaseUid = "firebase-uid",
        displayEmail = null,
        localeTag = "en-IN",
        state = AccountRecordState.ACTIVE,
        revision = 0,
        createdAtMillis = 1_000,
        updatedAtMillis = 1_000,
      ),
    )
    dao.insertInstallation(
      InstallationBindingEntity(
        installationId = INSTALLATION_ID,
        accountId = ACCOUNT_ID,
        localSlot = 1,
        callbackGeneration = CALLBACK_GENERATION,
        state = InstallationRecordState.ACTIVE,
        accountMode = AccountMode.TEST_ONLY,
        senderEpoch = 1,
        resetGeneration = 1,
        ownerLeaseUntilMillis = 10_000,
        appVersionCode = 1,
        distributionChannel = "test",
        signingCertificateSha256 = "certificate",
        lastVerifiedServerMillis = 1_000,
        revision = 0,
        createdAtMillis = 1_000,
        updatedAtMillis = 1_000,
      ),
    )
    dao.putCoordinationState(
      CoordinationStateEntity(
        accountId = ACCOUNT_ID,
        mode = AccountMode.TEST_ONLY,
        activeInstallationId = INSTALLATION_ID,
        senderEpoch = 1,
        resetGeneration = 1,
        continuityGeneration = 1,
        ownerLeaseUntilMillis = 10_000,
        nextArmNotBeforeMillis = null,
        latestIssuedSubmitNotAfterMillis = null,
        birthdayAutomationNotBeforeMillis = null,
        transferDrainUntilMillis = null,
        deletionDrainUntilMillis = null,
        lastSuccessfulCoordinationMillis = 1_000,
        lastSafeCode = null,
        revision = 0,
        updatedAtMillis = 1_000,
      ),
    )
    dao.insertClockTrust(
      ClockTrustEntity(
        accountId = ACCOUNT_ID,
        status = ClockTrustStatus.TRUSTED,
        greatestTrustedServerMillis = 1_000,
        lastDeviceWallMillis = 1_000,
        lastElapsedRealtimeMillis = 100,
        trustedBootCount = 7,
        lastVerificationMillis = 1_000,
        observedDriftMillis = 0,
        revision = 0,
      ),
    )
    dao.insertTestJob(testJob())
    assertTrue(dao.recordCloudClaim(cloudClaim()))
    val arm = dao.beginArmDispatch(
      permitId = PERMIT_ID,
      expectedPermitRevision = 0,
      armRequestId = ARM_REQUEST_ID,
      trustedNowMillis = 1_500,
    )
    assertTrue(arm is ArmDispatchResult.Committed)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun concurrentArmedResolversConsumeExactlyOneBarrierAndMintOneCapability() = runBlocking {
    val evidence = AuthoritativeArmedEvidence(
      armRequestId = ARM_REQUEST_ID,
      serverNowMillis = 2_000,
      serverSubmitNotAfterMillis = 3_000,
    )
    val external = FinalExternalGateSnapshot(
      distributionEligible = true,
      accountSessionValid = true,
      contactsAuthorizationValid = true,
      networkValidated = true,
      backgroundAllowed = false,
      smsPermissionGranted = true,
      simReady = true,
      currentSubscriptionId = 4,
      payloadHash = PAYLOAD_HASH,
      orderedPartsHash = PARTS_HASH,
      foregroundConfirmationValid = true,
      foregroundConfirmationNonceHash = "foreground-hash",
      observedAtElapsedRealtimeMillis = 1_000,
      bootCount = 7,
    )
    val results = coroutineScope {
      (1..2).map {
        async(Dispatchers.Default) {
          dao.consumeAuthoritativeArmedEvidence(
            permitId = PERMIT_ID,
            expectedPermitRevision = 1,
            evidence = evidence,
            external = external,
            deviceWallMillis = 2_000,
            currentElapsedRealtimeMillis = 1_001,
            currentBootCount = 7,
            sendAttemptId = SEND_ATTEMPT_ID,
            callbackGeneration = CALLBACK_GENERATION,
            sentWatchdogAtMillis = 902_000,
            retentionUntilMillis = 2_594_000_000,
          )
        }
      }.awaitAll()
    }

    assertEquals(1, results.count { it is PermitIssueResult.Issued })
    assertEquals(1, results.count { it is PermitIssueResult.Suppressed })
    assertEquals(1, dao.countSendAttempts(OperationPurpose.TEST, TEST_JOB_ID))
    assertEquals(
      CoordinationPermitState.BARRIER_CONSUMED,
      dao.getCoordinationPermit(PERMIT_ID)?.state,
    )
    val capability = (results.single { it is PermitIssueResult.Issued } as PermitIssueResult.Issued)
      .permit
    assertFalse(
      dao.commitApiBoundary(
        permit = capability,
        expectedAttemptRevision = 0,
        currentElapsedRealtimeMillis = 1_500,
        currentBootCount = 7,
        apiBoundaryWallMillis = 2_050,
        payloadHash = PAYLOAD_HASH,
        subscriptionId = 4,
      ),
    )
    dao.registerCallbackTokens(
      capability,
      listOf(
        callbackToken("sent-token", CallbackKind.SENT, 1, false),
        callbackToken("delivery-token", CallbackKind.DELIVERY, 2, true),
      ),
    )
    assertTrue(
      dao.commitApiBoundary(
        permit = capability,
        expectedAttemptRevision = 0,
        currentElapsedRealtimeMillis = 1_500,
        currentBootCount = 7,
        apiBoundaryWallMillis = 2_050,
        payloadHash = PAYLOAD_HASH,
        subscriptionId = 4,
      ),
    )
    assertEquals(902_050L, dao.getSendAttempt(SEND_ATTEMPT_ID)?.sentWatchdogAtMillis)
    assertEquals(2_050L, dao.getSendAttempt(SEND_ATTEMPT_ID)?.apiBoundaryStartedAtMillis)
    assertFalse(
      dao.commitApiBoundary(
        permit = capability,
        expectedAttemptRevision = 0,
        currentElapsedRealtimeMillis = 1_500,
        currentBootCount = 7,
        apiBoundaryWallMillis = 2_050,
        payloadHash = PAYLOAD_HASH,
        subscriptionId = 4,
      ),
    )
    assertTrue(
      dao.markSmsManagerAccepted(
        permit = capability,
        expectedAttemptRevision = 1,
        submittedAtMillis = 2_100,
      ),
    )
    assertFalse(
      dao.markSmsManagerAccepted(
        permit = capability,
        expectedAttemptRevision = 1,
        submittedAtMillis = 2_101,
      ),
    )

    val sentToken = checkNotNull(
      dao.findLiveCallbackToken(
        action = "com.yashsomani.birthdayautopilot.callback.sent",
        dataUri = "birthday-autopilot://callback/sent-token",
        kind = CallbackKind.SENT,
        observedAtMillis = 2_200,
      ),
    )
    assertFalse(
      dao.recordDeliveryEvent(
        DeliveryEventEntity(
          eventId = "af887a2c-dc01-4697-b05b-eb9ebcdfa8fe",
          callbackTokenId = sentToken.callbackTokenId,
          evidenceKey =
            "ec345c27e711fa53631f47b792f1202505ffecdf03f047414df5b3b85ca0d158",
          evidenceClass = DeliveryEvidenceClass.SENT_SUCCESS,
          androidResultCode = 1,
          modemStatus = null,
          receivedAtMillis = 2_199,
        ),
      ),
    )
    val processed = SmsOutcomeProcessor(database) { 2_200 }.recordCallbackAndReduce(
      sendAttemptId = SEND_ATTEMPT_ID,
      event =
        DeliveryEventEntity(
          eventId = "17d38dfe-953d-4e4a-a19a-fd3b5f86688e",
          callbackTokenId = sentToken.callbackTokenId,
          evidenceKey = CALLBACK_EVIDENCE_KEY,
          evidenceClass = DeliveryEvidenceClass.SENT_SUCCESS,
          androidResultCode = -1,
          modemStatus = null,
          receivedAtMillis = 2_200,
        ),
      observedAtMillis = 2_200,
    )
    assertTrue(processed.callbackInserted)
    assertEquals(TestJobState.PASSED, dao.getTestJob(TEST_JOB_ID)?.state)
    assertEquals(
      SendAttemptState.SENT_FROM_DEVICE,
      dao.getSendAttempt(SEND_ATTEMPT_ID)?.state,
    )
    assertTrue(database.smsOutcomeDao().testReceipt(TEST_JOB_ID) != null)
    assertEquals(
      "PASSED",
      dao.getOutcomeProjection(OperationPurpose.TEST, TEST_JOB_ID)?.immutableSafetyState,
    )
    assertFalse(
      dao.recordDeliveryEvent(
        DeliveryEventEntity(
          eventId = "1a134bdb-e48f-48fb-896f-c80212604121",
          callbackTokenId = sentToken.callbackTokenId,
          evidenceKey = CALLBACK_EVIDENCE_KEY,
          evidenceClass = DeliveryEvidenceClass.SENT_SUCCESS,
          androidResultCode = -1,
          modemStatus = null,
          receivedAtMillis = 2_201,
        ),
      ),
    )
  }

  @Test
  fun activeAttemptIsNotStarvedByMoreThanOnePageOfOldTerminalReports() = runBlocking {
    val sql = database.openHelper.writableDatabase
    repeat(80) { index ->
      rawAttempt(sql, "old-$index", SendAttemptState.TERMINAL, index.toLong() + 1L)
      rawAttempt(
        sql,
        "committed-retry-$index",
        SendAttemptState.TERMINAL,
        index.toLong() + 1L,
        OperationPurpose.BIRTHDAY,
      )
    }
    rawAttempt(sql, "active", SendAttemptState.SUBMITTED, 10_000L)

    assertEquals(
      listOf("attempt-active"),
      database.smsOutcomeDao().reconstructableAttemptIds(nowMillis = 20_000L, limit = 1),
    )
  }

  @Test
  fun lateTestSentEvidenceRefinesProjectionButNeverMintsReceipt() = runBlocking {
    val evidence = AuthoritativeArmedEvidence(
      armRequestId = ARM_REQUEST_ID,
      serverNowMillis = 2_000,
      serverSubmitNotAfterMillis = 3_000,
    )
    val external = FinalExternalGateSnapshot(
      distributionEligible = true,
      accountSessionValid = true,
      contactsAuthorizationValid = true,
      networkValidated = true,
      backgroundAllowed = false,
      smsPermissionGranted = true,
      simReady = true,
      currentSubscriptionId = 4,
      payloadHash = PAYLOAD_HASH,
      orderedPartsHash = PARTS_HASH,
      foregroundConfirmationValid = true,
      foregroundConfirmationNonceHash = "foreground-hash",
      observedAtElapsedRealtimeMillis = 1_000,
      bootCount = 7,
    )
    val issued = dao.consumeAuthoritativeArmedEvidence(
      permitId = PERMIT_ID,
      expectedPermitRevision = 1,
      evidence = evidence,
      external = external,
      deviceWallMillis = 2_000,
      currentElapsedRealtimeMillis = 1_001,
      currentBootCount = 7,
      sendAttemptId = SEND_ATTEMPT_ID,
      callbackGeneration = CALLBACK_GENERATION,
      sentWatchdogAtMillis = 2_500,
      retentionUntilMillis = 2_594_000_000,
    ) as PermitIssueResult.Issued
    dao.registerCallbackTokens(
      issued.permit,
      listOf(
        callbackToken("sent-token", CallbackKind.SENT, 1, false),
        callbackToken("delivery-token", CallbackKind.DELIVERY, 2, true),
      ),
    )
    assertTrue(
      dao.commitApiBoundary(
        permit = issued.permit,
        expectedAttemptRevision = 0,
        currentElapsedRealtimeMillis = 1_500,
        currentBootCount = 7,
        apiBoundaryWallMillis = 2_050,
        payloadHash = PAYLOAD_HASH,
        subscriptionId = 4,
      ),
    )
    assertTrue(dao.markSmsManagerAccepted(issued.permit, 1, 2_100))
    val watchdog = checkNotNull(dao.getSendAttempt(SEND_ATTEMPT_ID)).sentWatchdogAtMillis
    var processorNow = watchdog
    val processor = SmsOutcomeProcessor(database) { processorNow }
    processor.processAttempt(SEND_ATTEMPT_ID, watchdog)
    assertEquals(TestJobState.UNKNOWN, dao.getTestJob(TEST_JOB_ID)?.state)
    assertTrue(database.smsOutcomeDao().testReceipt(TEST_JOB_ID) == null)

    val lateAt = watchdog + 1
    processorNow = lateAt
    val late = processor.recordCallbackAndReduce(
      SEND_ATTEMPT_ID,
      DeliveryEventEntity(
        eventId = "fc378d8d-7250-4520-a369-d6b1f61858d2",
        callbackTokenId = "sent-token",
        evidenceKey =
          "17f10c9108d743d3c2e1fdcf0d63dcd737d2a9e88527fb26de899060672caff7",
        evidenceClass = DeliveryEvidenceClass.SENT_SUCCESS,
        androidResultCode = -1,
        modemStatus = null,
        receivedAtMillis = lateAt,
      ),
      lateAt,
    )
    assertTrue(late.callbackInserted)
    assertEquals(TestJobState.UNKNOWN, dao.getTestJob(TEST_JOB_ID)?.state)
    assertTrue(database.smsOutcomeDao().testReceipt(TEST_JOB_ID) == null)
    val projection = checkNotNull(dao.getOutcomeProjection(OperationPurpose.TEST, TEST_JOB_ID))
    assertEquals("UNKNOWN", projection.immutableSafetyState)
    assertEquals("SENT_EVIDENCE_LATE", projection.visibleOutcome)
    val cleanup = SmsCallbackCleanup(
      ApplicationProvider.getApplicationContext(),
      database,
    ).cancelAndRetireGeneration(INSTALLATION_ID, CALLBACK_GENERATION, lateAt + 1)
    assertEquals(SmsCallbackCleanupResult.Completed(2), cleanup)
    assertTrue(
      database.smsOutcomeDao()
        .liveTokensForGeneration(INSTALLATION_ID, CALLBACK_GENERATION)
        .isEmpty(),
    )
  }

  private fun rawAttempt(
    sql: androidx.sqlite.db.SupportSQLiteDatabase,
    suffix: String,
    state: SendAttemptState,
    watchdogAtMillis: Long,
    purpose: OperationPurpose = OperationPurpose.TEST,
  ) {
    val permitId = "permit-$suffix"
    val operationId = "operation-$suffix"
    sql.execSQL(
      """
      INSERT INTO coordination_permits_v2 (
        permitId, accountId, installationId, senderEpoch, resetGeneration, purpose,
        operationId, attemptNumber, payloadHash, opaqueClaimId, opaqueDestinationGuardId,
        claimRequestId, armRequestId, state, armDispatched, armStartBlockerRevision,
        claimExpiresAtMillis, maxPossibleSubmitNotAfterMillis, unresolvedArmCutoffMillis,
        trustedServerNowMillis, requestStartElapsedMillis, bootCount,
        serverSubmitNotAfterMillis, effectiveSubmitNotAfterMillis, noWriteReason, revision,
        createdAtMillis, updatedAtMillis, barrierConsumedAtMillis, retentionUntilMillis
      ) VALUES (?, ?, ?, 1, 1, ?, ?, 1, ?, ?, NULL, ?, NULL, 'BARRIER_CONSUMED',
        1, 0, 30000, 31000, 31000, 1000, 100, 7, 2000, 2000, NULL, 1, 1000, 1000,
        1500, 30000)
      """.trimIndent(),
      arrayOf<Any?>(
        permitId,
        ACCOUNT_ID,
        INSTALLATION_ID,
        purpose.name,
        operationId,
        PAYLOAD_HASH,
        "claim-$suffix",
        "request-$suffix",
      ),
    )
    sql.execSQL(
      """
      INSERT INTO send_attempts_v2 (
        sendAttemptId, permitId, installationId, callbackGeneration, purpose, operationId,
        attemptNumber, payloadHash, resolvedSubscriptionId, expectedPartCount, state,
        apiBoundaryStartedAtMillis, submittedAtMillis, sentWatchdogAtMillis,
        deliveryWatchdogAtMillis, terminalAtMillis, safeOutcomeCode, revision,
        retentionUntilMillis
      ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, 4, 1, ?, 1500, 1600, ?, NULL, ?, ?, 0, 30000)
      """.trimIndent(),
      arrayOf<Any?>(
        "attempt-$suffix",
        permitId,
        INSTALLATION_ID,
        CALLBACK_GENERATION,
        purpose.name,
        operationId,
        PAYLOAD_HASH,
        state.name,
        watchdogAtMillis,
        if (state == SendAttemptState.TERMINAL) 2_000L else null,
        if (state == SendAttemptState.TERMINAL) {
          if (purpose == OperationPurpose.TEST) {
            "NEEDS_TEST_REPORT"
          } else {
            "RETRY_AUTHORIZED_ATTEMPT_2_PROOF_RETAINED"
          }
        } else {
          null
        },
      ),
    )
  }

  private fun callbackToken(
    id: String,
    kind: CallbackKind,
    requestCode: Int,
    mutable: Boolean,
  ) = CallbackTokenEntity(
    callbackTokenId = id,
    sendAttemptId = SEND_ATTEMPT_ID,
    installationId = INSTALLATION_ID,
    callbackGeneration = CALLBACK_GENERATION,
    attemptNumber = 1,
    partIndex = 0,
    kind = kind,
    callbackRequestCode = requestCode,
    action = "com.yashsomani.birthdayautopilot.callback.${kind.name.lowercase()}",
    dataUri = "birthday-autopilot://callback/$id",
    mutableForPlatformFillIn = mutable,
    state = CallbackTokenState.EXPECTED,
    createdAtMillis = 2_001,
    observedAtMillis = null,
    retiredAtMillis = null,
    expiresAtMillis = 2_594_000_000,
  )

  private fun testJob() = TestJobEntity(
    testJobId = TEST_JOB_ID,
    accountId = ACCOUNT_ID,
    installationId = INSTALLATION_ID,
    senderEpoch = 1,
    testRequestId = "test-request",
    configHash = "config-hash",
    destinationPrehash = "destination-prehash",
    normalizedDestination = "+919999999999",
    maskedDestination = "•••• 9999",
    exactMessage = "Birthday Autopilot test",
    payloadHash = PAYLOAD_HASH,
    simPolicyKind = "EXPLICIT_SUBSCRIPTION",
    resolvedSubscriptionId = 4,
    segmentCount = 1,
    messageEncoding = "GSM_7",
    orderedPartsHash = PARTS_HASH,
    buildBindingHash = "build-hash",
    appCheckPolicyVersion = "app-check-v1",
    state = TestJobState.PREPARED,
    revision = 0,
    foregroundConfirmationNonceHash = "foreground-hash",
    foregroundConfirmedAtMillis = 1_000,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_000,
    terminalAtMillis = null,
    invalidationReason = null,
    retentionUntilMillis = 2_594_000_000,
  )

  private fun cloudClaim() = CoordinationPermitEntity(
    permitId = PERMIT_ID,
    accountId = ACCOUNT_ID,
    installationId = INSTALLATION_ID,
    senderEpoch = 1,
    resetGeneration = 1,
    purpose = OperationPurpose.TEST,
    operationId = TEST_JOB_ID,
    attemptNumber = 1,
    payloadHash = PAYLOAD_HASH,
    opaqueClaimId = "opaque-test-claim",
    opaqueDestinationGuardId = null,
    claimRequestId = "claim-request",
    armRequestId = null,
    state = CoordinationPermitState.CLOUD_CLAIMED,
    armDispatched = false,
    armStartBlockerRevision = null,
    claimExpiresAtMillis = 5_000,
    maxPossibleSubmitNotAfterMillis = 6_000,
    unresolvedArmCutoffMillis = 6_000,
    trustedServerNowMillis = 1_000,
    requestStartElapsedMillis = 100,
    bootCount = 7,
    serverSubmitNotAfterMillis = null,
    effectiveSubmitNotAfterMillis = null,
    noWriteReason = null,
    revision = 0,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_000,
    barrierConsumedAtMillis = null,
    retentionUntilMillis = 2_594_000_000,
  )

  private companion object {
    const val ACCOUNT_ID = "account-1"
    const val INSTALLATION_ID = "installation-1"
    const val CALLBACK_GENERATION = "callback-generation-1"
    const val TEST_JOB_ID = "test-job-1"
    const val PERMIT_ID = "permit-1"
    const val SEND_ATTEMPT_ID = "send-attempt-1"
    const val ARM_REQUEST_ID = "4f013554-82c7-4d50-88e1-0257c16ba484"
    const val PAYLOAD_HASH = "payload-hash"
    const val PARTS_HASH = "parts-hash"
    const val CALLBACK_EVIDENCE_KEY =
      "7c7d1f460aaf4a69c9e59c340c7be23cb3de835b3314da4d0eadcaac2c30bbdf"
  }
}
