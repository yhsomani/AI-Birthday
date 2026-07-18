package com.yashsomani.birthdayautopilot.automation.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.yashsomani.birthdayautopilot.storage.database.ArmDispatchResult
import com.yashsomani.birthdayautopilot.storage.database.AuthoritativeArmedEvidence
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.CallbackKind
import com.yashsomani.birthdayautopilot.storage.database.ClockTrustEntity
import com.yashsomani.birthdayautopilot.storage.database.ClockTrustStatus
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitEntity
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitState
import com.yashsomani.birthdayautopilot.storage.database.CoordinationStateEntity
import com.yashsomani.birthdayautopilot.storage.database.DeliveryEvidenceClass
import com.yashsomani.birthdayautopilot.storage.database.FinalExternalGateSnapshot
import com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import com.yashsomani.birthdayautopilot.storage.database.PermitIssueResult
import com.yashsomani.birthdayautopilot.storage.database.TestJobEntity
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsCallbackRuntimeInstrumentationTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val databaseNames = mutableSetOf<String>()

  @After
  fun tearDown() {
    databaseNames.forEach(context::deleteDatabase)
  }

  @Test
  fun registeredPrivateReceiversCompleteMalformedPendingIntentBroadcasts() {
    listOf(
      SmsSentCallbackReceiver::class.java,
      SmsDeliveryCallbackReceiver::class.java,
    ).forEachIndexed { index, receiverClass ->
      val receiver = context.packageManager.getReceiverInfo(
        ComponentName(context, receiverClass),
        0,
      )
      assertTrue(receiver.enabled)
      assertFalse(receiver.exported)
      val finished = CountDownLatch(1)
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        9_000 + index,
        Intent(context, receiverClass).setAction("malformed.callback"),
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      pendingIntent.send(
        context,
        0,
        null,
        { _, _, _, _, _ -> finished.countDown() },
        Handler(Looper.getMainLooper()),
      )

      assertTrue("receiver did not complete", finished.await(5, TimeUnit.SECONDS))
      pendingIntent.cancel()
    }
  }

  @Test
  fun exactIntentRoutesWhileStaleSpoofedAndStructurallyMalformedIntentsDoNot() = runBlocking {
    val exact = prepare(partCount = 1, expiresAtMillis = RETENTION_UNTIL)
    val sent = exact.identity(0, CallbackKind.SENT)
    val exactObservation = capture(sent.intent, CallbackKind.SENT)

    val inserted = SmsCallbackRoomRouter(exact.database) { OBSERVED_AT }
      .route(exactObservation)

    assertNotNull(inserted)
    assertTrue(checkNotNull(inserted).callbackInserted)
    assertEquals(
      1,
      exact.database.smsOutcomeDao().callbackEvidence(SEND_ATTEMPT_ID)
        .count { it.evidenceClass != null },
    )

    val spoofedData = Intent(sent.intent).setData(Uri.parse("${sent.token.dataUri}-spoofed"))
    val spoofedObservation = capture(spoofedData, CallbackKind.SENT)
    assertNull(SmsCallbackRoomRouter(exact.database) { OBSERVED_AT + 1 }.route(spoofedObservation))
    assertNull(
      SmsCallbackIntentCapture.captureSafely(
        Intent(sent.intent).setAction("com.yashsomani.birthdayautopilot.callback.delivery.spoof"),
        Activity.RESULT_OK,
        CallbackKind.SENT,
      ),
    )
    assertNull(
      SmsCallbackIntentCapture.captureSafely(
        Intent(),
        Activity.RESULT_OK,
        CallbackKind.SENT,
      ),
    )
    assertNull(
      SmsCallbackIntentCapture.captureSafely(
        Intent("com.yashsomani.birthdayautopilot.callback.sent.${"x".repeat(220)}")
          .setData(Uri.parse(sent.token.dataUri)),
        Activity.RESULT_OK,
        CallbackKind.SENT,
      ),
    )

    val stale = prepare(partCount = 1, expiresAtMillis = OBSERVED_AT)
    val staleObservation = capture(stale.identity(0, CallbackKind.SENT).intent, CallbackKind.SENT)
    assertNull(SmsCallbackRoomRouter(stale.database) { OBSERVED_AT }.route(staleObservation))
    assertEquals(
      0,
      stale.database.smsOutcomeDao().callbackEvidence(SEND_ATTEMPT_ID)
        .count { it.evidenceClass != null },
    )

    exact.database.close()
    stale.database.close()
  }

  @Test
  fun malformedDeliveryPayloadIsDurablyClassifiedUnknownWithoutRetainingRawPdu() = runBlocking {
    val prepared = prepare(partCount = 1, expiresAtMillis = RETENTION_UNTIL)
    val delivery = prepared.identity(0, CallbackKind.DELIVERY)
    val oversizedPdu = ByteArray(4 * 1024 + 1) { 0x7f }
    val intent = Intent(delivery.intent)
      .putExtra("pdu", oversizedPdu)
      .putExtra("format", "3gpp")

    val observation = capture(intent, CallbackKind.DELIVERY)
    assertNull(observation.pdu)
    assertTrue(oversizedPdu.all { it == 0.toByte() })
    val result = SmsCallbackRoomRouter(prepared.database) { OBSERVED_AT }.route(observation)

    assertNotNull(result)
    assertTrue(checkNotNull(result).callbackInserted)
    val evidence = prepared.database.smsOutcomeDao().callbackEvidence(SEND_ATTEMPT_ID)
      .single { it.kind == CallbackKind.DELIVERY }
    assertEquals(DeliveryEvidenceClass.DELIVERY_UNKNOWN, evidence.evidenceClass)
    prepared.database.close()
  }

  @Test
  fun multipartCallbacksAreIndependentAndDuplicateCallbackIsIdempotent() = runBlocking {
    val prepared = prepare(partCount = 2, expiresAtMillis = RETENTION_UNTIL)
    val router = SmsCallbackRoomRouter(prepared.database) { OBSERVED_AT }
    val first = capture(prepared.identity(0, CallbackKind.SENT).intent, CallbackKind.SENT)
    val second = capture(prepared.identity(1, CallbackKind.SENT).intent, CallbackKind.SENT)

    assertTrue(checkNotNull(router.route(first)).callbackInserted)
    assertTrue(checkNotNull(router.route(second)).callbackInserted)
    assertFalse(checkNotNull(router.route(first)).callbackInserted)

    val sentEvidence = prepared.database.smsOutcomeDao().callbackEvidence(SEND_ATTEMPT_ID)
      .filter { it.kind == CallbackKind.SENT && it.evidenceClass != null }
    assertEquals(2, sentEvidence.size)
    assertTrue(sentEvidence.all { it.evidenceClass == DeliveryEvidenceClass.SENT_SUCCESS })
    assertEquals(TestJobState.PASSED, prepared.database.safetyLedgerDao().getTestJob(TEST_JOB_ID)?.state)
    prepared.database.close()
  }

  @Test
  fun callbackRoutingSurvivesDatabaseAndRouterRecreation() = runBlocking {
    val name = newDatabaseName()
    val prepared = prepare(
      partCount = 1,
      expiresAtMillis = RETENTION_UNTIL,
      databaseName = name,
    )
    val sent = prepared.identity(0, CallbackKind.SENT)
    val observation = capture(sent.intent, CallbackKind.SENT)
    prepared.database.close()

    val reopened = openDatabase(name)
    val result = SmsCallbackRoomRouter(reopened) { OBSERVED_AT }.route(observation)

    assertNotNull(result)
    assertTrue(checkNotNull(result).callbackInserted)
    assertEquals(
      DeliveryEvidenceClass.SENT_SUCCESS,
      reopened.smsOutcomeDao().callbackEvidence(SEND_ATTEMPT_ID)
        .single { it.kind == CallbackKind.SENT }.evidenceClass,
    )
    reopened.close()
  }

  @Test
  fun callbackRoutesRemainAsciiAndValidUnderTurkishDeviceLocale() = runBlocking {
    val originalLocale = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"))
      val prepared = prepare(partCount = 1, expiresAtMillis = RETENTION_UNTIL)
      val delivery = prepared.identity(0, CallbackKind.DELIVERY)

      assertTrue(delivery.token.action.contains(".delivery."))
      assertTrue(delivery.token.dataUri.contains("/test/"))
      assertNotNull(
        SmsCallbackIntentCapture.captureSafely(
          delivery.intent,
          Activity.RESULT_OK,
          CallbackKind.DELIVERY,
        ),
      )
      prepared.database.close()
    } finally {
      Locale.setDefault(originalLocale)
    }
  }

  private fun capture(intent: Intent, kind: CallbackKind): SmsCallbackObservation =
    checkNotNull(SmsCallbackIntentCapture.captureSafely(intent, Activity.RESULT_OK, kind))

  private suspend fun prepare(
    partCount: Int,
    expiresAtMillis: Long,
    databaseName: String = newDatabaseName(),
  ): PreparedCallbackRuntime {
    val database = openDatabase(databaseName)
    val dao = database.safetyLedgerDao()
    database.birthdayDao().initializeIfAbsent(CALLBACK_GENERATION)
    dao.insertAccount(account())
    dao.insertInstallation(installation())
    dao.putCoordinationState(coordination())
    dao.insertClockTrust(clockTrust())
    dao.insertTestJob(testJob(partCount))
    assertTrue(dao.recordCloudClaim(cloudClaim()))
    assertTrue(
      dao.beginArmDispatch(
        permitId = PERMIT_ID,
        expectedPermitRevision = 0,
        armRequestId = ARM_REQUEST_ID,
        trustedNowMillis = 1_500,
      ) is ArmDispatchResult.Committed,
    )
    val issued = dao.consumeAuthoritativeArmedEvidence(
      permitId = PERMIT_ID,
      expectedPermitRevision = 1,
      evidence = AuthoritativeArmedEvidence(
        armRequestId = ARM_REQUEST_ID,
        serverNowMillis = 2_000,
        serverSubmitNotAfterMillis = 3_000,
      ),
      external = externalGate(),
      deviceWallMillis = 2_000,
      currentElapsedRealtimeMillis = 1_001,
      currentBootCount = 7,
      sendAttemptId = SEND_ATTEMPT_ID,
      callbackGeneration = CALLBACK_GENERATION,
      sentWatchdogAtMillis = 902_000,
      retentionUntilMillis = RETENTION_UNTIL,
    ) as PermitIssueResult.Issued
    val payload = checkNotNull(LocalSendPayloadLoader.load(issued.permit, dao))
    val identities = buildList(partCount * CallbackKind.entries.size) {
      repeat(partCount) { partIndex ->
        CallbackKind.entries.forEach { kind ->
          add(
            CallbackIdentityFactory.create(
              context = context,
              permit = issued.permit,
              payload = payload,
              kind = kind,
              partIndex = partIndex,
              requestCode = 100 + partIndex * 2 + kind.ordinal,
              createdAtMillis = 2_001,
              expiresAtMillis = expiresAtMillis,
            ),
          )
        }
      }
    }
    dao.registerCallbackTokens(issued.permit, identities.map(CallbackIdentity::token))
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
    return PreparedCallbackRuntime(database, identities)
  }

  private fun openDatabase(name: String): BirthdayDatabase = Room.databaseBuilder(
    context,
    BirthdayDatabase::class.java,
    name,
  ).build()

  private fun newDatabaseName(): String =
    "sms-callback-runtime-${UUID.randomUUID()}.db".also(databaseNames::add)

  private fun account() = AccountRecordEntity(
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
    ownerLeaseUntilMillis = 10_000,
    appVersionCode = 1,
    distributionChannel = "test",
    signingCertificateSha256 = "certificate",
    lastVerifiedServerMillis = 1_000,
    revision = 0,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_000,
  )

  private fun coordination() = CoordinationStateEntity(
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
  )

  private fun clockTrust() = ClockTrustEntity(
    accountId = ACCOUNT_ID,
    status = ClockTrustStatus.TRUSTED,
    greatestTrustedServerMillis = 1_000,
    lastDeviceWallMillis = 1_000,
    lastElapsedRealtimeMillis = 100,
    trustedBootCount = 7,
    lastVerificationMillis = 1_000,
    observedDriftMillis = 0,
    revision = 0,
  )

  private fun testJob(partCount: Int) = TestJobEntity(
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
    segmentCount = partCount,
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
    retentionUntilMillis = RETENTION_UNTIL,
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
    retentionUntilMillis = RETENTION_UNTIL,
  )

  private fun externalGate() = FinalExternalGateSnapshot(
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

  private data class PreparedCallbackRuntime(
    val database: BirthdayDatabase,
    val identities: List<CallbackIdentity>,
  ) {
    fun identity(partIndex: Int, kind: CallbackKind): CallbackIdentity =
      identities.single { it.token.partIndex == partIndex && it.token.kind == kind }
  }

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
    const val OBSERVED_AT = 2_200L
    const val RETENTION_UNTIL = 2_594_000_000L
  }
}
