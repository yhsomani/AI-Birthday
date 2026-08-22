package com.yashsomani.birthdayautopilot.storage.database

import android.content.ContentValues
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.automation.workers.AndroidDataRetention
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDataRetentionInstrumentationTest {
  private lateinit var database: BirthdayDatabase
  private lateinit var ledger: SafetyLedgerDao
  private lateinit var installation: InstallationBindingEntity

  @Before
  fun setUp() = runBlocking {
    database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      BirthdayDatabase::class.java,
    ).build()
    database.birthdayDao().initializeIfAbsent(CALLBACK_GENERATION)
    ledger = database.safetyLedgerDao()
    ledger.insertAccount(account())
    installation = installation()
    ledger.insertInstallation(installation)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun expiredDetailIsMinimizedWithoutWeakeningReceiptOrBirthdaySafety() = runBlocking {
    database.birthdayDao().insertActivity(activity("expired", ACTIVITY_CUTOFF - 1))
    database.birthdayDao().insertActivity(activity("boundary", ACTIVITY_CUTOFF))

    val passing = testJob("passing", TestJobState.PASSED, RETENTION_EXPIRED, PASSED_AT)
    ledger.insertTestJob(passing)
    val passingPermit = testPermit("passing-permit", passing, RETENTION_EXPIRED)
    insertPermit(passingPermit)
    val passingAttempt = testAttempt("passing-attempt", passingPermit, RETENTION_EXPIRED)
    insertAttempt(passingAttempt)
    val passingToken = callbackToken("passing-token", passingAttempt, CallbackTokenState.RETIRED)
    insertCallback(passingToken)
    insertEvent(deliveryEvent("passing-event", passingToken))
    val passingReceipt = TestReceiptFactory.create(
      test = passing,
      installation = installation,
      testReceiptId = "passing-receipt",
      smsPolicyVersion = "sms-policy-v1",
      passedAtMillis = PASSED_AT,
    )
    insertReceipt(passingReceipt)

    val failed = testJob("failed", TestJobState.FAILED, RETENTION_EXPIRED, PASSED_AT)
    ledger.insertTestJob(failed)
    val failedPermit = testPermit("failed-permit", failed, RETENTION_EXPIRED)
    insertPermit(failedPermit)
    val failedAttempt = testAttempt("failed-attempt", failedPermit, RETENTION_EXPIRED)
    insertAttempt(failedAttempt)
    val failedToken = callbackToken("failed-token", failedAttempt, CallbackTokenState.EXPIRED)
    insertCallback(failedToken)
    insertEvent(deliveryEvent("failed-event", failedToken))
    insertProjection("failed")

    val future = testJob("future", TestJobState.FAILED, RETENTION_FUTURE, PASSED_AT)
    ledger.insertTestJob(future)
    val malformedActive = testJob(
      "malformed-active",
      TestJobState.PREPARED,
      RETENTION_EXPIRED,
      PASSED_AT,
    ).copy(terminalAtMillis = null, invalidationReason = null)
    ledger.insertTestJob(malformedActive)

    val birthdayPermit = birthdayPermit()
    insertPermit(birthdayPermit)
    val birthdayAttempt = birthdayAttempt(birthdayPermit)
    insertAttempt(birthdayAttempt)
    val birthdayToken = callbackToken(
      "birthday-token",
      birthdayAttempt,
      CallbackTokenState.RETIRED,
    )
    insertCallback(birthdayToken)
    insertEvent(deliveryEvent("birthday-event", birthdayToken))

    val result = AndroidDataRetention(database).prune(NOW, maxBatches = 4, batchSize = 16)

    assertTrue(result.deletedRows >= 12)
    assertEquals(2, result.redactedTestJobs)
    assertEquals(listOf("boundary"), database.birthdayDao().listActivity(10, 0).map { it.activityId })

    val retainedPassing = ledger.getTestJob("passing")
    assertNotNull(retainedPassing)
    assertEquals(TestJobRetainedDetail.State.REDACTED, TestJobRetainedDetail.classify(retainedPassing!!))
    assertTrue(TestReceiptBindingValidator.matches(retainedPassing, installation, passingReceipt))
    assertNotNull(ledger.getCoordinationPermit("passing-permit"))
    assertNull(ledger.getSendAttempt("passing-attempt"))
    assertEquals(0, count("callback_tokens_v2", "callbackTokenId", "passing-token"))
    assertEquals(0, count("delivery_events_v2", "eventId", "passing-event"))
    assertEquals(1, count("test_receipts_v2", "testReceiptId", "passing-receipt"))

    assertNull(ledger.getTestJob("failed"))
    assertNull(ledger.getCoordinationPermit("failed-permit"))
    assertNull(ledger.getSendAttempt("failed-attempt"))
    assertNull(ledger.getOutcomeProjection(OperationPurpose.TEST, "failed"))

    val retainedFuture = ledger.getTestJob("future")
    assertNotNull(retainedFuture)
    assertEquals(TestJobRetainedDetail.State.FULL, TestJobRetainedDetail.classify(retainedFuture!!))
    assertEquals(
      TestJobRetainedDetail.State.FULL,
      TestJobRetainedDetail.classify(checkNotNull(ledger.getTestJob("malformed-active"))),
    )

    // The generic 30-day callback window is gone, while the 400-day Birthday proof remains.
    assertEquals(0, count("callback_tokens_v2", "callbackTokenId", "birthday-token"))
    assertEquals(0, count("delivery_events_v2", "eventId", "birthday-event"))
    assertNotNull(ledger.getSendAttempt("birthday-attempt"))
    assertNotNull(ledger.getCoordinationPermit("birthday-permit"))
  }

  @Test
  fun oneBatchIsBoundedAndReportsContinuation() = runBlocking {
    repeat(3) { index ->
      database.birthdayDao().insertActivity(activity("old-$index", ACTIVITY_CUTOFF - index - 1))
    }

    val first = database.retentionDao().pruneBatch(NOW, ACTIVITY_CUTOFF, 2)
    assertEquals(2, first.deletedActivityRows)
    assertTrue(first.moreWork)
    assertEquals(1, database.birthdayDao().listActivity(10, 0).size)

    val completed = AndroidDataRetention(database).prune(NOW, maxBatches = 2, batchSize = 2)
    assertEquals(1, completed.deletedRows)
    assertTrue(!completed.moreWork)
    assertTrue(database.birthdayDao().listActivity(10, 0).isEmpty())
  }

  @Test
  fun liveCallbackIdentityIsNeverDeletedByTheDatabasePruner() = runBlocking {
    val permit = birthdayPermit()
    insertPermit(permit)
    val attempt = birthdayAttempt(permit)
    insertAttempt(attempt)
    val live = callbackToken("live-token", attempt, CallbackTokenState.EXPECTED)
    insertCallback(live)

    AndroidDataRetention(database).prune(NOW, maxBatches = 1, batchSize = 16)

    assertEquals(1, count("callback_tokens_v2", "callbackTokenId", "live-token"))
    assertNotNull(ledger.getSendAttempt("birthday-attempt"))
  }

  @Test
  fun trustedServerHighWaterSurvivesDeviceWallRollback() = runBlocking {
    ledger.insertClockTrust(
      ClockTrustEntity(
        accountId = ACCOUNT_ID,
        status = ClockTrustStatus.TRUSTED,
        greatestTrustedServerMillis = NOW,
        lastDeviceWallMillis = NOW - DAY,
        lastElapsedRealtimeMillis = 1_000,
        trustedBootCount = 7,
        lastVerificationMillis = NOW,
        observedDriftMillis = -DAY,
        revision = 0,
      ),
    )

    assertEquals(NOW, database.retentionDao().greatestTrustedServerMillis())
  }

  private fun account() = AccountRecordEntity(
    accountId = ACCOUNT_ID,
    activeSlot = 1,
    googleSubjectHash = "subject-hash",
    firebaseUid = "firebase-uid",
    displayEmail = null,
    localeTag = "en-IN",
    state = AccountRecordState.ACTIVE,
    revision = 0,
    createdAtMillis = PASSED_AT,
    updatedAtMillis = PASSED_AT,
  )

  private fun installation() = InstallationBindingEntity(
    installationId = INSTALLATION_ID,
    accountId = ACCOUNT_ID,
    localSlot = 1,
    callbackGeneration = CALLBACK_GENERATION,
    state = InstallationRecordState.ACTIVE,
    accountMode = AccountMode.TEST_ONLY,
    senderEpoch = 1,
    resetGeneration = 1,
    ownerLeaseUntilMillis = RETENTION_FUTURE,
    appVersionCode = 1,
    distributionChannel = "controlled-test",
    signingCertificateSha256 = "certificate-hash",
    lastVerifiedServerMillis = PASSED_AT,
    revision = 0,
    createdAtMillis = PASSED_AT,
    updatedAtMillis = PASSED_AT,
  )

  private fun activity(id: String, at: Long) = ActivityEntity(
    activityId = id,
    category = "test",
    safeCode = "CONTENT_FREE",
    recordedAtMillis = at,
    relatedOccurrenceId = null,
  )

  private fun testJob(
    id: String,
    state: TestJobState,
    retentionUntil: Long,
    terminalAt: Long,
  ) = TestJobEntity(
    testJobId = id,
    accountId = ACCOUNT_ID,
    installationId = INSTALLATION_ID,
    senderEpoch = 1,
    testRequestId = "$id-request",
    configHash = "$id-config",
    destinationPrehash = "$id-destination-prehash",
    normalizedDestination = "+919999999999",
    maskedDestination = "•••• 9999",
    exactMessage = "WishWell test",
    payloadHash = "$id-payload",
    simPolicyKind = "EXPLICIT_SUBSCRIPTION",
    resolvedSubscriptionId = 4,
    segmentCount = 1,
    messageEncoding = "GSM_7",
    orderedPartsHash = "$id-parts",
    buildBindingHash = "build-hash",
    appCheckPolicyVersion = "app-check-v1",
    state = state,
    revision = 3,
    foregroundConfirmationNonceHash = "$id-foreground-nonce",
    foregroundConfirmedAtMillis = PASSED_AT,
    createdAtMillis = PASSED_AT,
    updatedAtMillis = terminalAt,
    terminalAtMillis = terminalAt,
    invalidationReason = if (state == TestJobState.FAILED) "TEST_FAILED" else null,
    retentionUntilMillis = retentionUntil,
  )

  private fun testPermit(
    id: String,
    test: TestJobEntity,
    retentionUntil: Long,
  ) = CoordinationPermitEntity(
    permitId = id,
    accountId = ACCOUNT_ID,
    installationId = INSTALLATION_ID,
    senderEpoch = 1,
    resetGeneration = 1,
    purpose = OperationPurpose.TEST,
    operationId = test.testJobId,
    attemptNumber = 1,
    payloadHash = test.payloadHash,
    opaqueClaimId = "$id-claim",
    opaqueDestinationGuardId = null,
    claimRequestId = "$id-claim-request",
    armRequestId = "$id-arm-request",
    state = CoordinationPermitState.BARRIER_CONSUMED,
    armDispatched = true,
    armStartBlockerRevision = 1,
    claimExpiresAtMillis = PASSED_AT + 1_000,
    maxPossibleSubmitNotAfterMillis = PASSED_AT + 2_000,
    unresolvedArmCutoffMillis = PASSED_AT + 2_000,
    trustedServerNowMillis = PASSED_AT,
    requestStartElapsedMillis = 100,
    bootCount = 7,
    serverSubmitNotAfterMillis = PASSED_AT + 1_500,
    effectiveSubmitNotAfterMillis = PASSED_AT + 1_500,
    noWriteReason = null,
    revision = 2,
    createdAtMillis = PASSED_AT,
    updatedAtMillis = PASSED_AT,
    barrierConsumedAtMillis = PASSED_AT,
    retentionUntilMillis = retentionUntil,
  )

  private fun birthdayPermit() = testPermit(
    "birthday-permit",
    testJob("birthday-operation", TestJobState.FAILED, BIRTHDAY_RETENTION, PASSED_AT),
    BIRTHDAY_RETENTION,
  ).copy(
    purpose = OperationPurpose.BIRTHDAY,
    operationId = "birthday-operation",
    opaqueDestinationGuardId = "birthday-guard",
  )

  private fun testAttempt(
    id: String,
    permit: CoordinationPermitEntity,
    retentionUntil: Long,
  ) = SendAttemptEntity(
    sendAttemptId = id,
    permitId = permit.permitId,
    installationId = INSTALLATION_ID,
    callbackGeneration = CALLBACK_GENERATION,
    purpose = permit.purpose,
    operationId = permit.operationId,
    attemptNumber = permit.attemptNumber,
    payloadHash = permit.payloadHash,
    resolvedSubscriptionId = 4,
    expectedPartCount = 1,
    state = SendAttemptState.SENT_FROM_DEVICE,
    apiBoundaryStartedAtMillis = PASSED_AT,
    submittedAtMillis = PASSED_AT,
    sentWatchdogAtMillis = PASSED_AT + 1_000,
    deliveryWatchdogAtMillis = PASSED_AT + 2_000,
    terminalAtMillis = PASSED_AT,
    safeOutcomeCode = "SENT_FROM_DEVICE",
    revision = 4,
    retentionUntilMillis = retentionUntil,
  )

  private fun birthdayAttempt(permit: CoordinationPermitEntity) =
    testAttempt("birthday-attempt", permit, BIRTHDAY_RETENTION)

  private fun callbackToken(
    id: String,
    attempt: SendAttemptEntity,
    state: CallbackTokenState,
  ) = CallbackTokenEntity(
    callbackTokenId = id,
    sendAttemptId = attempt.sendAttemptId,
    installationId = INSTALLATION_ID,
    callbackGeneration = CALLBACK_GENERATION,
    attemptNumber = attempt.attemptNumber,
    partIndex = 0,
    kind = CallbackKind.SENT,
    callbackRequestCode = id.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1),
    action = "com.yashsomani.birthdayautopilot.callback.sent.$id",
    dataUri = "birthday-autopilot://callback/$id",
    mutableForPlatformFillIn = false,
    state = state,
    createdAtMillis = PASSED_AT,
    observedAtMillis = PASSED_AT,
    retiredAtMillis = PASSED_AT,
    expiresAtMillis = RETENTION_EXPIRED,
  )

  private fun deliveryEvent(id: String, token: CallbackTokenEntity) = DeliveryEventEntity(
    eventId = id,
    callbackTokenId = token.callbackTokenId,
    evidenceKey = "$id-evidence",
    evidenceClass = DeliveryEvidenceClass.SENT_SUCCESS,
    androidResultCode = -1,
    modemStatus = null,
    receivedAtMillis = PASSED_AT,
  )

  private fun insertPermit(entity: CoordinationPermitEntity) = insert(
    "coordination_permits_v2",
    valuesOf(
      "permitId" to entity.permitId,
      "accountId" to entity.accountId,
      "installationId" to entity.installationId,
      "senderEpoch" to entity.senderEpoch,
      "resetGeneration" to entity.resetGeneration,
      "purpose" to entity.purpose.name,
      "operationId" to entity.operationId,
      "attemptNumber" to entity.attemptNumber,
      "payloadHash" to entity.payloadHash,
      "opaqueClaimId" to entity.opaqueClaimId,
      "opaqueDestinationGuardId" to entity.opaqueDestinationGuardId,
      "claimRequestId" to entity.claimRequestId,
      "armRequestId" to entity.armRequestId,
      "state" to entity.state.name,
      "armDispatched" to entity.armDispatched,
      "armStartBlockerRevision" to entity.armStartBlockerRevision,
      "claimExpiresAtMillis" to entity.claimExpiresAtMillis,
      "maxPossibleSubmitNotAfterMillis" to entity.maxPossibleSubmitNotAfterMillis,
      "unresolvedArmCutoffMillis" to entity.unresolvedArmCutoffMillis,
      "trustedServerNowMillis" to entity.trustedServerNowMillis,
      "requestStartElapsedMillis" to entity.requestStartElapsedMillis,
      "bootCount" to entity.bootCount,
      "serverSubmitNotAfterMillis" to entity.serverSubmitNotAfterMillis,
      "effectiveSubmitNotAfterMillis" to entity.effectiveSubmitNotAfterMillis,
      "noWriteReason" to entity.noWriteReason,
      "revision" to entity.revision,
      "createdAtMillis" to entity.createdAtMillis,
      "updatedAtMillis" to entity.updatedAtMillis,
      "barrierConsumedAtMillis" to entity.barrierConsumedAtMillis,
      "retentionUntilMillis" to entity.retentionUntilMillis,
    ),
  )

  private fun insertAttempt(entity: SendAttemptEntity) = insert(
    "send_attempts_v2",
    valuesOf(
      "sendAttemptId" to entity.sendAttemptId,
      "permitId" to entity.permitId,
      "installationId" to entity.installationId,
      "callbackGeneration" to entity.callbackGeneration,
      "purpose" to entity.purpose.name,
      "operationId" to entity.operationId,
      "attemptNumber" to entity.attemptNumber,
      "payloadHash" to entity.payloadHash,
      "resolvedSubscriptionId" to entity.resolvedSubscriptionId,
      "expectedPartCount" to entity.expectedPartCount,
      "state" to entity.state.name,
      "apiBoundaryStartedAtMillis" to entity.apiBoundaryStartedAtMillis,
      "submittedAtMillis" to entity.submittedAtMillis,
      "sentWatchdogAtMillis" to entity.sentWatchdogAtMillis,
      "deliveryWatchdogAtMillis" to entity.deliveryWatchdogAtMillis,
      "terminalAtMillis" to entity.terminalAtMillis,
      "safeOutcomeCode" to entity.safeOutcomeCode,
      "revision" to entity.revision,
      "retentionUntilMillis" to entity.retentionUntilMillis,
    ),
  )

  private fun insertCallback(entity: CallbackTokenEntity) = insert(
    "callback_tokens_v2",
    valuesOf(
      "callbackTokenId" to entity.callbackTokenId,
      "sendAttemptId" to entity.sendAttemptId,
      "installationId" to entity.installationId,
      "callbackGeneration" to entity.callbackGeneration,
      "attemptNumber" to entity.attemptNumber,
      "partIndex" to entity.partIndex,
      "kind" to entity.kind.name,
      "callbackRequestCode" to entity.callbackRequestCode,
      "action" to entity.action,
      "dataUri" to entity.dataUri,
      "mutableForPlatformFillIn" to entity.mutableForPlatformFillIn,
      "state" to entity.state.name,
      "createdAtMillis" to entity.createdAtMillis,
      "observedAtMillis" to entity.observedAtMillis,
      "retiredAtMillis" to entity.retiredAtMillis,
      "expiresAtMillis" to entity.expiresAtMillis,
    ),
  )

  private fun insertEvent(entity: DeliveryEventEntity) = insert(
    "delivery_events_v2",
    valuesOf(
      "eventId" to entity.eventId,
      "callbackTokenId" to entity.callbackTokenId,
      "evidenceKey" to entity.evidenceKey,
      "evidenceClass" to entity.evidenceClass.name,
      "androidResultCode" to entity.androidResultCode,
      "modemStatus" to entity.modemStatus,
      "receivedAtMillis" to entity.receivedAtMillis,
    ),
  )

  private fun insertReceipt(entity: TestReceiptEntity) = insert(
    "test_receipts_v2",
    valuesOf(
      "testReceiptId" to entity.testReceiptId,
      "testJobId" to entity.testJobId,
      "accountId" to entity.accountId,
      "bindingHash" to entity.bindingHash,
      "configHash" to entity.configHash,
      "destinationBindingHash" to entity.destinationBindingHash,
      "maskedDestination" to entity.maskedDestination,
      "exactTextHash" to entity.exactTextHash,
      "segmentPlanHash" to entity.segmentPlanHash,
      "resolvedSubscriptionId" to entity.resolvedSubscriptionId,
      "installationId" to entity.installationId,
      "senderEpoch" to entity.senderEpoch,
      "buildBindingHash" to entity.buildBindingHash,
      "distributionChannel" to entity.distributionChannel,
      "appCheckPolicyVersion" to entity.appCheckPolicyVersion,
      "smsPolicyVersion" to entity.smsPolicyVersion,
      "state" to entity.state.name,
      "passedAtMillis" to entity.passedAtMillis,
      "invalidatedAtMillis" to entity.invalidatedAtMillis,
      "invalidationReason" to entity.invalidationReason,
    ),
  )

  private fun insertProjection(operationId: String) = insert(
    "outcome_projections_v2",
    valuesOf(
      "purpose" to OperationPurpose.TEST.name,
      "operationId" to operationId,
      "accountId" to ACCOUNT_ID,
      "immutableSafetyState" to "FAILED",
      "visibleOutcome" to "FAILED",
      "evidenceCompleteness" to "COMPLETE",
      "sentEvidenceDeadlineMillis" to RETENTION_EXPIRED,
      "deliveryEvidenceDeadlineMillis" to null,
      "refinedAtMillis" to PASSED_AT,
      "revision" to 1,
    ),
  )

  private fun valuesOf(vararg entries: Pair<String, Any?>): ContentValues = ContentValues().apply {
    entries.forEach { (key, value) ->
      when (value) {
        null -> putNull(key)
        is String -> put(key, value)
        is Int -> put(key, value)
        is Long -> put(key, value)
        is Boolean -> put(key, value)
        else -> error("unsupported-content-value-${value::class.java.name}")
      }
    }
  }

  private fun insert(table: String, values: ContentValues) {
    val rowId = database.openHelper.writableDatabase.insert(table, 0, values)
    check(rowId != -1L) { "insert-failed-$table" }
  }

  private fun count(table: String, column: String, value: String): Int =
    database.openHelper.readableDatabase.query(
      "SELECT COUNT(*) FROM $table WHERE $column = ?",
      arrayOf(value),
    ).use { cursor ->
      check(cursor.moveToFirst())
      cursor.getInt(0)
    }

  private companion object {
    const val DAY = 86_400_000L
    const val NOW = 40L * DAY
    const val ACTIVITY_CUTOFF = NOW - AndroidDataRetention.ACTIVITY_RETENTION_MILLIS
    const val RETENTION_EXPIRED = NOW - 1
    const val RETENTION_FUTURE = NOW + DAY
    const val BIRTHDAY_RETENTION = NOW + 400L * DAY
    const val PASSED_AT = DAY
    const val ACCOUNT_ID = "account-1"
    const val INSTALLATION_ID = "installation-1"
    const val CALLBACK_GENERATION = "callback-generation-1"
  }
}
