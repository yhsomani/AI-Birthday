package com.yashsomani.birthdayautopilot.coordination

import com.yashsomani.birthdayautopilot.auth.NativeAccountBinding
import com.yashsomani.birthdayautopilot.people.StablePrivateId
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinationResponseAndClientTest {
  @Test
  fun `registration lease and mode responses are strict and request bound`() {
    val registration = registrationRequest()
    val active = CoordinationResponseParser.registration(
      mapOf("kind" to "REGISTERED_ACTIVE", "fence" to fence(), "installation" to installation()),
      registration,
    ) as RegistrationOutcome.Registered
    assertEquals(RegistrationOutcome.Disposition.REGISTERED_ACTIVE, active.disposition)
    assertEquals(1, active.binding.senderEpoch)

    val wrongInstallation = installation().toMutableMap().apply {
      this["installationId"] = "f".repeat(32)
    }
    assertNull(
      CoordinationResponseParser.registration(
        mapOf("kind" to "REGISTERED_ACTIVE", "fence" to fence(), "installation" to wrongInstallation),
        registration,
      ),
    )
    assertEquals(
      LeaseOutcome.Renewed(601_000),
      CoordinationResponseParser.lease(mapOf("kind" to "RENEWED", "leaseUntilMs" to 601_000L)),
    )
    assertEquals(
      AccountModeOutcome.Changed(ServerAccountMode.PAUSED_REPAIR),
      CoordinationResponseParser.accountMode(
        mapOf("kind" to "CHANGED", "mode" to "PAUSED_REPAIR"),
        CoordinationRequestFactory.pauseForRepair(binding()),
      ),
    )
  }

  @Test
  fun `birthday and test claim responses validate full persisted records`() {
    val birthdayRequest = birthdayClaimRequest()
    val birthdayClaim = claim(purpose = CoordinationPurpose.BIRTHDAY, claimId = CLAIM_ID)
    val birthdayResponse = mapOf(
      "kind" to "CLAIMED",
      "claim" to birthdayClaim,
      "requestRecord" to requestRecord(CoordinationPurpose.BIRTHDAY, CLAIM_ID),
      "occurrenceKeys" to listOf(occurrenceKey()),
      "destinationGuards" to listOf(destinationGuard()),
    )
    val parsed = CoordinationResponseParser.claim(birthdayResponse, birthdayRequest)
      as ClaimOutcome.Accepted
    assertEquals(ClaimOutcome.Disposition.CLAIMED, parsed.disposition)
    assertEquals(CLAIM_ID, parsed.claim.claimId)
    assertEquals(1_000L, parsed.claim.serverObservedAtMillis)

    val malformedGuard = destinationGuard().toMutableMap().apply { this["ownerEpoch"] = 2L }
    assertNull(
      CoordinationResponseParser.claim(
        birthdayResponse + ("destinationGuards" to listOf(malformedGuard)),
        birthdayRequest,
      ),
    )

    val testRequest = testClaimRequest()
    val testParsed = CoordinationResponseParser.claim(
      mapOf("kind" to "REPLAYED", "claim" to claim(CoordinationPurpose.TEST, TEST_ID)),
      testRequest,
    ) as ClaimOutcome.Accepted
    assertEquals(CoordinationPurpose.TEST, testParsed.claim.purpose)
  }

  @Test
  fun `armed response becomes authority only when every request binding matches`() {
    val request = armRequest()
    val armedClaim = claim(
      purpose = CoordinationPurpose.BIRTHDAY,
      claimId = CLAIM_ID,
      state = ServerClaimState.ARMED,
      serverSubmitNotAfter = 61_000L,
    )
    val response = mapOf(
      "kind" to "ARMED",
      "outcome" to armedOutcome(),
      "fence" to fence(
        latestSubmit = 61_000L,
        mode = "AUTOMATION_ACTIVE",
        nextArmNotBefore = 361_000L,
      ),
      "claim" to armedClaim,
      "destinationGuards" to listOf(destinationGuard(state = "ARMED")),
      "occurrenceKeyState" to "ARMED",
      "budget" to budget(),
    )
    val parsed = CoordinationResponseParser.arm(response, request) as ArmDecisionOutcome.Armed
    assertEquals(61_000L, parsed.outcome.serverSubmitNotAfterMillis)
    assertEquals(1_000L, parsed.outcome.resolvedAtMillis)

    val mismatched = armedOutcome().toMutableMap().apply { this["armRequestId"] = OTHER_ARM_ID }
    assertNull(CoordinationResponseParser.arm(response + ("outcome" to mismatched), request))
    assertNull(CoordinationResponseParser.arm(response + ("unexpected" to true), request))
  }

  @Test
  fun `no write is accepted only as an explicit exact immutable outcome`() {
    val request = armRequest()
    val response = noWriteResponse()
    val parsed = CoordinationResponseParser.arm(response, request) as ArmDecisionOutcome.NoWrite
    assertEquals(CoordinationServerReason.EXPIRED, parsed.outcome.reason)

    val status = CoordinationResponseParser.armStatus(
      mapOf("kind" to "REPLAYED", "outcome" to noWriteOutcome()),
      request,
    ) as ArmStatusOutcome.Replayed
    assertTrue(status.outcome is AuthoritativeArmOutcome.NoWrite)
    assertTrue(
      CoordinationResponseParser.armStatus(noWriteResponse(), request) is ArmStatusOutcome.NoWrite,
    )
    assertEquals(ArmStatusOutcome.Unknown, CoordinationResponseParser.armStatus(mapOf("kind" to "UNKNOWN"), request))
    assertNull(CoordinationResponseParser.armStatus(mapOf("kind" to "NO_WRITE"), request))
  }

  @Test
  fun `retry test report and companion status return only bounded sanitized fields`() {
    val retryClaim = claim(
      purpose = CoordinationPurpose.BIRTHDAY,
      claimId = CLAIM_ID,
      state = ServerClaimState.RETRY_CLAIMED,
      attempt = 2,
      retryGeneration = 1,
      serverSubmitNotAfter = 61_000L,
      retryRequestId = RETRY_ID,
      retryProof = RetryProof.ALL_PARTS_RADIO_OFF,
    )
    val retry = CoordinationResponseParser.retry(
      mapOf("kind" to "AUTHORIZED", "claim" to retryClaim),
      retryRequest(),
    ) as RetryOutcome.Authorized
    assertEquals(2, retry.claim.attempt)
    assertNull(
      CoordinationResponseParser.retry(
        mapOf(
          "kind" to "AUTHORIZED",
          "claim" to retryClaim.toMutableMap().apply {
            this["retryRequestId"] = OTHER_ARM_ID
          },
        ),
        retryRequest(),
      ),
    )
    assertEquals(
      CoordinationServerReason.RETRY_REQUEST_MISMATCH,
      (CoordinationResponseParser.retry(
        mapOf("kind" to "REFUSED", "reason" to "RETRY_REQUEST_MISMATCH"),
        retryRequest(),
      ) as RetryOutcome.Refused).reason,
    )

    val terminalTest = claim(
      purpose = CoordinationPurpose.TEST,
      claimId = TEST_ID,
      state = ServerClaimState.TERMINAL,
      testOutcome = TestBarrierOutcome.SENT_ALL_PARTS_IN_WINDOW,
      serverSubmitNotAfter = 61_000L,
    )
    val report = CoordinationResponseParser.testReport(
      mapOf("kind" to "RECORDED", "claim" to terminalTest),
      testReportRequest(),
    ) as TestReportOutcome.Recorded
    assertEquals(TestBarrierOutcome.SENT_ALL_PARTS_IN_WINDOW, report.outcome)

    val companion = CoordinationResponseParser.companionStatus(
      mapOf(
        "composerAllowed" to true,
        "state" to "NO_ANDROID_STATE",
        "serverNowMs" to 1_000L,
        "ledgerGeneration" to LEDGER,
      ),
      companionRequest(),
    )
    assertNotNull(companion)
    assertTrue(companion!!.composerAllowed)
    assertNull(
      CoordinationResponseParser.companionStatus(
        mapOf("composerAllowed" to true, "state" to "MANAGED_BY_ANDROID", "serverNowMs" to 1_000L),
        companionRequest(),
      ),
    )
  }

  @Test
  fun `transfer and deletion responses are strict request-bound lifecycle authority`() {
    val begin = transferRequest(completing = false)
    val transferFence = fence(mode = "TRANSFER_PENDING") + mapOf(
      "transferTargetInstallationId" to TARGET_INSTALLATION,
      "transferDrainUntilMs" to 2_000L,
    )
    val started = CoordinationResponseParser.senderTransfer(
      mapOf("kind" to "STARTED", "fence" to transferFence),
      begin,
      completing = false,
    ) as SenderTransferOutcome.Started
    assertEquals(TARGET_INSTALLATION, started.binding.transferTargetInstallationId)
    assertEquals(2_000L, started.binding.transferDrainUntilMillis)
    assertNull(
      CoordinationResponseParser.senderTransfer(
        mapOf("kind" to "STARTED", "fence" to transferFence),
        begin,
        completing = true,
      ),
    )

    val completedFence = fence(mode = "TEST_ONLY").toMutableMap().apply {
      this["activeInstallationId"] = TARGET_INSTALLATION
      this["senderEpoch"] = 2L
    }
    val old = installation().toMutableMap().apply {
      this["state"] = "REVOKED"
      this["cleanupAtMs"] = 9_000L
    }
    val target = installation().toMutableMap().apply {
      this["installationId"] = TARGET_INSTALLATION
      this["epoch"] = 2L
    }
    val complete = CoordinationResponseParser.senderTransfer(
      mapOf(
        "kind" to "COMPLETED",
        "fence" to completedFence,
        "oldInstallation" to old,
        "targetInstallation" to target,
      ),
      transferRequest(completing = true),
      completing = true,
    ) as SenderTransferOutcome.Completed
    assertEquals(TARGET_INSTALLATION, complete.targetInstallationId)
    assertEquals(2L, complete.binding.senderEpoch)

    val deletionRequest = ready(CoordinationRequestFactory.accountDeletion(DELETION_ID))
    assertEquals(DELETION_REQUEST_KEY, DeletionReceiptKeyPolicy.derive(DELETION_ID))
    val tombstone = mapOf(
      "schemaVersion" to 1,
      "requestKey" to DELETION_REQUEST_KEY,
      "stage" to "DRAINING",
      "drainUntilMs" to 2_000L,
      "createdAtMs" to 1_000L,
      "updatedAtMs" to 1_000L,
    )
    val deletingFence = mapOf(
      "mode" to "DELETING",
      "senderEpoch" to 1L,
      "resetGeneration" to 1L,
      "deletionDrainUntilMs" to 2_000L,
    )
    val deletion = CoordinationResponseParser.accountDeletion(
      mapOf(
        "kind" to "STARTED",
        "receiptId" to DELETION_ID,
        "tombstone" to tombstone,
        "fence" to deletingFence,
      ),
      deletionRequest,
    )
    assertNotNull(deletion)
    assertEquals(2_000L, deletion!!.drainUntilMillis)
    assertEquals(1L, deletion.deletingFence?.senderEpoch)
    assertNull(
      CoordinationResponseParser.accountDeletion(
        mapOf(
          "kind" to "STARTED",
          "receiptId" to DELETION_ID,
          "tombstone" to (tombstone + ("requestKey" to "0".repeat(64))),
          "fence" to deletingFence,
        ),
        deletionRequest,
      ),
    )
    assertNull(
      CoordinationResponseParser.accountDeletion(
        mapOf(
          "kind" to "STARTED",
          "receiptId" to DELETION_ID,
          "tombstone" to (tombstone + ("cleanupAtMs" to 999L)),
          "fence" to deletingFence,
        ),
        deletionRequest,
      ),
    )
    assertNull(
      CoordinationResponseParser.accountDeletion(
        mapOf(
          "kind" to "STARTED",
          "receiptId" to DELETION_ID,
          "tombstone" to tombstone,
          "fence" to (deletingFence + ("unexpected" to true)),
        ),
        deletionRequest,
      ),
    )
    assertNull(
      CoordinationResponseParser.accountDeletion(
        mapOf(
          "kind" to "STARTED",
          "receiptId" to DELETION_ID,
          "tombstone" to tombstone,
          "fence" to (deletingFence + ("deletionDrainUntilMs" to 2_001L)),
        ),
        deletionRequest,
      ),
    )
    assertNull(
      CoordinationResponseParser.accountDeletion(
        mapOf(
          "kind" to "STARTED",
          "receiptId" to OTHER_ARM_ID,
          "tombstone" to tombstone,
          "fence" to deletingFence,
        ),
        deletionRequest,
      ),
    )
    assertNotNull(
      CoordinationResponseParser.accountDeletion(
        mapOf(
          "kind" to "STARTED",
          "receiptId" to DELETION_ID,
          "tombstone" to tombstone,
          "fence" to null,
        ),
        deletionRequest,
      ),
    )
  }

  @Test
  fun `ambiguous transfer and deletion calls make one mutation attempt`() = runTest {
    val transport = FakeTransport(CallableTransportResult.AmbiguousFailure)
    val client = FirebaseCoordinationClient(
      CoordinationPreflight { NativePreflightResult.Ready },
      transport,
    )
    assertTrue(
      client.beginSenderTransfer(transferRequest(completing = false)) is
        CoordinationCallResult.Unavailable,
    )
    assertTrue(
      client.requestAccountDeletion(
        ready(CoordinationRequestFactory.accountDeletion(DELETION_ID)),
      ) is CoordinationCallResult.Unavailable,
    )
    assertEquals(2, transport.calls)
    assertEquals(
      listOf(
        CoordinationEndpointPolicy.BEGIN_SENDER_TRANSFER,
        CoordinationEndpointPolicy.REQUEST_ACCOUNT_DELETION,
      ),
      transport.functionNames,
    )
  }

  @Test
  fun `oversized malformed and bidi controlled responses fail closed`() {
    assertNull(CoordinationResponseParser.lease(mapOf("kind" to "REFUSED\u202E", "reason" to "MODE_BLOCKED")))
    assertNull(
      CoordinationResponseParser.lease(
        mapOf("kind" to "REFUSED", "reason" to "x".repeat(513)),
      ),
    )
    val deep = mutableMapOf<String, Any>("kind" to "UNKNOWN")
    var current: MutableMap<String, Any> = deep
    repeat(12) {
      val next = mutableMapOf<String, Any>()
      current["nested"] = next
      current = next
    }
    assertNull(CoordinationResponseParser.armStatus(deep, armRequest()))
  }

  @Test
  fun `each callable accepts only its own refusal and suppression reason union`() {
    assertNull(
      CoordinationResponseParser.registration(
        mapOf("kind" to "SUPPRESSED", "reason" to "EXPIRED"),
        registrationRequest(),
      ),
    )
    assertTrue(
      CoordinationResponseParser.registration(
        mapOf("kind" to "SUPPRESSED", "reason" to "CHANNEL_UNSUPPORTED"),
        registrationRequest(),
      ) is RegistrationOutcome.Suppressed,
    )
    assertNull(CoordinationResponseParser.lease(mapOf("kind" to "REFUSED", "reason" to "MISSING_CLAIM")))
    assertNull(
      CoordinationResponseParser.accountMode(
        mapOf("kind" to "REFUSED", "reason" to "CONTINUITY_UNAVAILABLE"),
        CoordinationRequestFactory.pauseForRepair(binding()),
      ),
    )
    assertTrue(
      CoordinationResponseParser.claim(
        mapOf("kind" to "REFUSED", "reason" to "OCCURRENCE_RESERVED"),
        birthdayClaimRequest(),
      ) is ClaimOutcome.Refused,
    )
    assertNull(
      CoordinationResponseParser.arm(
        mapOf("kind" to "SUPPRESSED", "reason" to "MODE_BLOCKED"),
        armRequest(),
      ),
    )
    assertNull(
      CoordinationResponseParser.retry(
        mapOf("kind" to "REFUSED", "reason" to "MODE_BLOCKED"),
        retryRequest(),
      ),
    )
    assertTrue(
      CoordinationResponseParser.testReport(
        mapOf("kind" to "REFUSED", "reason" to "ARMED_OUTCOME_REQUIRED"),
        testReportRequest(),
      ) is TestReportOutcome.Refused,
    )
  }

  @Test
  fun `preflight failure performs zero calls and arm transport failure stays ambiguous`() = runTest {
    val transport = FakeTransport(CallableTransportResult.Response(noWriteResponse()))
    val unauthenticated = FirebaseCoordinationClient(
      CoordinationPreflight { NativePreflightResult.NotAuthenticated },
      transport,
    )
    assertEquals(
      CoordinationCallResult.Unavailable(CoordinationUnavailableReason.NOT_AUTHENTICATED),
      unauthenticated.arm(armRequest()),
    )
    assertEquals(0, transport.calls)

    val wrongAccount = FirebaseCoordinationClient(
      CoordinationPreflight { NativePreflightResult.AccountMismatch },
      transport,
    )
    assertEquals(
      CoordinationCallResult.Unavailable(CoordinationUnavailableReason.ACCOUNT_MISMATCH),
      wrongAccount.arm(armRequest()),
    )
    assertEquals(0, transport.calls)

    transport.result = CallableTransportResult.AmbiguousFailure
    val client = FirebaseCoordinationClient(
      CoordinationPreflight { NativePreflightResult.Ready },
      transport,
    )
    val outcome = client.arm(armRequest())
    assertEquals(
      CoordinationCallResult.Unavailable(CoordinationUnavailableReason.AMBIGUOUS_CALL),
      outcome,
    )
    assertFalse(outcome is CoordinationCallResult.Authoritative<*>)
    assertEquals(1, transport.calls)
  }

  @Test
  fun `status reconciliation is one query call and never redispatches arm`() = runTest {
    val transport = FakeTransport(CallableTransportResult.Response(mapOf("kind" to "UNKNOWN")))
    val client = FirebaseCoordinationClient(
      CoordinationPreflight { NativePreflightResult.Ready },
      transport,
    )
    assertEquals(
      CoordinationCallResult.Authoritative(ArmStatusOutcome.Unknown),
      client.getArmStatus(armRequest()),
    )
    assertEquals(1, transport.calls)
    assertEquals(listOf(CoordinationEndpointPolicy.GET_ARM_STATUS), transport.functionNames)
  }

  @Test
  fun `malformed response remains ambiguous after exactly one call`() = runTest {
    val transport = FakeTransport(
      CallableTransportResult.Response(mapOf("kind" to "NO_WRITE")),
    )
    val client = FirebaseCoordinationClient(
      CoordinationPreflight { NativePreflightResult.Ready },
      transport,
    )
    assertEquals(
      CoordinationCallResult.Unavailable(CoordinationUnavailableReason.INVALID_SERVER_RESPONSE),
      client.arm(armRequest()),
    )
    assertEquals(1, transport.calls)
    assertEquals(listOf(CoordinationEndpointPolicy.ARM_ATTEMPT), transport.functionNames)
  }

  @Test
  fun `endpoint policy is exact regional replay protected and bounded`() {
    assertEquals("asia-south1", CoordinationEndpointPolicy.REGION)
    assertTrue(CoordinationEndpointPolicy.LIMITED_USE_APP_CHECK_TOKENS)
    assertEquals(35L, CoordinationEndpointPolicy.CALL_TIMEOUT_SECONDS)
    assertEquals(
      setOf(
        CoordinationEndpointPolicy.REGISTER,
        CoordinationEndpointPolicy.RENEW_LEASE,
        CoordinationEndpointPolicy.CHANGE_MODE,
        CoordinationEndpointPolicy.CLAIM_OCCURRENCE,
        CoordinationEndpointPolicy.CLAIM_TEST,
        CoordinationEndpointPolicy.ARM_ATTEMPT,
        CoordinationEndpointPolicy.GET_ARM_STATUS,
        CoordinationEndpointPolicy.REPORT_TEST,
        CoordinationEndpointPolicy.AUTHORIZE_RETRY,
        CoordinationEndpointPolicy.COMPANION_STATUS,
        CoordinationEndpointPolicy.BEGIN_SENDER_TRANSFER,
        CoordinationEndpointPolicy.COMPLETE_SENDER_TRANSFER,
        CoordinationEndpointPolicy.REQUEST_ACCOUNT_DELETION,
        CoordinationEndpointPolicy.ACCOUNT_DELETION_RECEIPT,
        CoordinationEndpointPolicy.RESET_CONTACT_DERIVED_STATE,
        CoordinationEndpointPolicy.RELEASE_ANDROID_SENDER,
        CoordinationEndpointPolicy.COORDINATION_LIFECYCLE_STATUS,
      ),
      CoordinationEndpointPolicy.allowedFunctionNames,
    )
  }

  @Test
  fun `session ready is native preflight only and does not test backend reachability`() = runTest {
    val transport = FakeTransport(CallableTransportResult.AmbiguousFailure)
    val ready = ConfiguredCoordinationRuntime(
      CoordinationPreflight { NativePreflightResult.Ready },
      transport,
    )
    assertEquals(CoordinationSessionStatus.SESSION_READY, ready.sessionStatus())
    assertNotNull(ready.clientOrNull())
    assertEquals(0, transport.calls)

    val accountMismatch = ConfiguredCoordinationRuntime(
      CoordinationPreflight { NativePreflightResult.AccountMismatch },
      transport,
    )
    assertEquals(CoordinationSessionStatus.ACCOUNT_MISMATCH, accountMismatch.sessionStatus())
    assertEquals(0, transport.calls)

    val unavailable = ConfiguredCoordinationRuntime(
      CoordinationPreflight { throw IllegalStateException("provider-detail-must-not-escape") },
      transport,
    )
    assertEquals(
      CoordinationSessionStatus.NATIVE_PREFLIGHT_UNAVAILABLE,
      unavailable.sessionStatus(),
    )
    assertEquals(
      CoordinationSessionStatus.TIER_CONFIGURATION_MISSING,
      MissingCoordinationRuntime.sessionStatus(),
    )
    assertNull(MissingCoordinationRuntime.clientOrNull())
    assertEquals(
      CoordinationSessionStatus.NATIVE_PREFLIGHT_UNAVAILABLE,
      UnavailableCoordinationRuntime.sessionStatus(),
    )
    assertNull(UnavailableCoordinationRuntime.clientOrNull())
    assertFalse(unavailable.toString().contains("provider-detail"))
  }

  @Test
  fun `encrypted Room account predicate binds Firebase uid account id and Google subject hash`() = runTest {
    val firebaseUid = "firebase-uid-1"
    val googleSubject = "google-subject-1"
    val account = AccountRecordEntity(
      accountId = StablePrivateId.prefixed("a", "FirebaseAccount.v1", firebaseUid),
      activeSlot = 1,
      googleSubjectHash = StablePrivateId.hash("GoogleSubject.v1", googleSubject),
      firebaseUid = firebaseUid,
      displayEmail = null,
      localeTag = "en-IN",
      state = AccountRecordState.ACTIVE,
      revision = 1,
      createdAtMillis = 1,
      updatedAtMillis = 1,
    )
    val binding = NativeAccountBinding(googleSubject, "safe@example.com", firebaseUid)
    assertTrue(ActiveRoomAccountBindingPredicate { account }.matches(binding))
    assertFalse(
      ActiveRoomAccountBindingPredicate { account.copy(firebaseUid = "other-firebase-uid") }
        .matches(binding),
    )
    assertFalse(
      ActiveRoomAccountBindingPredicate {
        account.copy(accountId = StablePrivateId.prefixed("a", "WrongDomain.v1", firebaseUid))
      }.matches(binding),
    )
    assertFalse(
      ActiveRoomAccountBindingPredicate {
        account.copy(googleSubjectHash = StablePrivateId.hash("WrongDomain.v1", googleSubject))
      }.matches(binding),
    )
    assertFalse(
      ActiveRoomAccountBindingPredicate { account.copy(activeSlot = null) }.matches(binding),
    )
    assertFalse(ActiveRoomAccountBindingPredicate { null }.matches(binding))
    assertFalse(ActiveRoomAccountBindingPredicate { account }.toString().contains(firebaseUid))
  }

  private class FakeTransport(
    var result: CallableTransportResult,
  ) : CoordinationCallableTransport {
    var calls: Int = 0
    val functionNames = mutableListOf<String>()

    override suspend fun call(
      functionName: String,
      payload: Map<String, Any>,
    ): CallableTransportResult {
      calls += 1
      functionNames += functionName
      return result
    }
  }

  private fun binding(): CoordinationBinding = ready(
    CoordinationRequestFactory.binding(
      LEDGER,
      INSTALLATION,
      1,
      1,
      100,
      7,
      DistributionChannel.PLAY,
    ),
  )

  private fun registrationRequest(): RegistrationRequest = ready(
    CoordinationRequestFactory.registration(
      LEDGER,
      INSTALLATION,
      100,
      7,
      DistributionChannel.PLAY,
    ),
  )

  private fun birthdayClaimRequest(): BirthdayClaimRequest = ready(
    CoordinationRequestFactory.birthdayClaim(
      binding(),
      CLAIM_ID,
      listOf("ab".repeat(32)),
      listOf("cd".repeat(32)),
    ),
  )

  private fun testClaimRequest(): TestClaimRequest = ready(
    CoordinationRequestFactory.testClaim(binding(), TEST_ID, "12".repeat(32), "34".repeat(32)),
  )

  private fun armRequest(): ArmRequest = ready(
    CoordinationRequestFactory.arm(binding(), CoordinationPurpose.BIRTHDAY, CLAIM_ID, ARM_ID, 1),
  )

  private fun retryRequest(): RetryRequest = ready(
    CoordinationRequestFactory.retry(
      binding(),
      CLAIM_ID,
      RETRY_ID,
      RetryProof.ALL_PARTS_RADIO_OFF,
    ),
  )

  private fun testReportRequest(): TestReportRequest = ready(
    CoordinationRequestFactory.testReport(
      binding(),
      TEST_ID,
      ARM_ID,
      TestReportResult.SENT_ALL_PARTS,
    ),
  )

  private fun companionRequest(): CompanionStatusRequest = ready(
    CoordinationRequestFactory.companionStatus(LEDGER),
  )

  private fun transferRequest(completing: Boolean): SenderTransferRequest = ready(
    if (completing) {
      CoordinationRequestFactory.completeSenderTransfer(binding(), TARGET_INSTALLATION)
    } else {
      CoordinationRequestFactory.beginSenderTransfer(binding(), TARGET_INSTALLATION)
    },
  )

  private fun fence(
    latestSubmit: Long = 1_000L,
    mode: String = "TEST_ONLY",
    nextArmNotBefore: Long = 1_000L,
  ): Map<String, Any> = mapOf(
    "schemaVersion" to 1,
    "mode" to mode,
    "activeInstallationId" to INSTALLATION,
    "senderEpoch" to 1L,
    "ownerLeaseUntilMs" to 601_000L,
    "nextArmNotBeforeMs" to nextArmNotBefore,
    "latestIssuedSubmitNotAfterMs" to latestSubmit,
    "resetGeneration" to 1L,
    "birthdayAutomationNotBeforeMs" to 1_000L,
    "createdAtMs" to 1_000L,
    "updatedAtMs" to 1_000L,
  )

  private fun installation(): Map<String, Any> = mapOf(
    "schemaVersion" to 1,
    "installationId" to INSTALLATION,
    "state" to "ACTIVE",
    "epoch" to 1L,
    "appBuildNumber" to 100,
    "policyVersion" to 7,
    "distributionChannel" to "PLAY",
    "lastSeenAtMs" to 1_000L,
  )

  private fun claim(
    purpose: CoordinationPurpose,
    claimId: String,
    state: ServerClaimState = ServerClaimState.CLAIMED,
    attempt: Int = 1,
    retryGeneration: Int = 0,
    serverSubmitNotAfter: Long? = null,
    testOutcome: TestBarrierOutcome? = null,
    retryRequestId: String? = null,
    retryProof: RetryProof? = null,
  ): Map<String, Any> = linkedMapOf<String, Any>(
    "schemaVersion" to 1,
    "claimId" to claimId,
    "purpose" to purpose.name,
    "claimRequestId" to claimId,
    "ownerInstallationId" to INSTALLATION,
    "ownerEpoch" to 1L,
    "resetGeneration" to 1L,
    "state" to state.name,
    "attempt" to attempt,
    "retryAuthorizationGeneration" to retryGeneration,
    "claimExpiresAtMs" to 600_000L,
    "maxPossibleSubmitNotAfterMs" to 660_000L,
    "occurrenceAliasKeys" to if (purpose == CoordinationPurpose.BIRTHDAY) listOf("v1.occurrence") else emptyList<String>(),
    "destinationAliasKeys" to if (purpose == CoordinationPurpose.BIRTHDAY) listOf("v1.destination") else emptyList<String>(),
    "testMaterialAliasKeys" to if (purpose == CoordinationPurpose.TEST) listOf("v1.test") else emptyList<String>(),
    "createdAtMs" to 1_000L,
    "updatedAtMs" to 1_000L,
    "cleanupAtMs" to 900_000L,
  ).apply {
    serverSubmitNotAfter?.let { this["serverSubmitNotAfterMs"] = it }
    testOutcome?.let { this["testBarrierOutcome"] = it.name }
    retryRequestId?.let { this["retryRequestId"] = it }
    retryProof?.let { this["retryProof"] = it.name }
  }

  private fun requestRecord(purpose: CoordinationPurpose, claimId: String): Map<String, Any> = mapOf(
    "schemaVersion" to 1,
    "requestKey" to claimId,
    "purpose" to purpose.name,
    "linkedClaimId" to claimId,
    "createdAtMs" to 1_000L,
    "cleanupAtMs" to 900_000L,
  )

  private fun occurrenceKey(): Map<String, Any> = mapOf(
    "schemaVersion" to 1,
    "aliasKey" to "v1.occurrence",
    "linkedClaimId" to CLAIM_ID,
    "state" to "RESERVED",
    "createdAtMs" to 1_000L,
    "updatedAtMs" to 1_000L,
    "cleanupAtMs" to 900_000L,
  )

  private fun destinationGuard(state: String = "RESERVED"): Map<String, Any> = mapOf(
    "schemaVersion" to 1,
    "aliasKey" to "v1.destination",
    "linkedClaimId" to CLAIM_ID,
    "ownerEpoch" to 1L,
    "state" to state,
    "createdAtMs" to 1_000L,
    "updatedAtMs" to 1_000L,
    "cleanupAtMs" to 900_000L,
  )

  private fun armedOutcome(): Map<String, Any> = mapOf(
    "schemaVersion" to 1,
    "armRequestId" to ARM_ID,
    "purpose" to "BIRTHDAY",
    "claimId" to CLAIM_ID,
    "ownerInstallationId" to INSTALLATION,
    "ownerEpoch" to 1L,
    "resetGeneration" to 1L,
    "attempt" to 1,
    "kind" to "ARMED",
    "serverSubmitNotAfterMs" to 61_000L,
    "resolvedAtMs" to 1_000L,
    "cleanupAtMs" to 900_000L,
  )

  private fun noWriteOutcome(): Map<String, Any> = mapOf(
    "schemaVersion" to 1,
    "armRequestId" to ARM_ID,
    "purpose" to "BIRTHDAY",
    "claimId" to CLAIM_ID,
    "ownerInstallationId" to INSTALLATION,
    "ownerEpoch" to 1L,
    "resetGeneration" to 1L,
    "attempt" to 1,
    "kind" to "NO_WRITE",
    "reason" to "EXPIRED",
    "resolvedAtMs" to 601_000L,
    "cleanupAtMs" to 900_000L,
  )

  private fun noWriteResponse(): Map<String, Any> = mapOf(
    "kind" to "NO_WRITE",
    "outcome" to noWriteOutcome(),
    "claim" to claim(
      purpose = CoordinationPurpose.BIRTHDAY,
      claimId = CLAIM_ID,
      state = ServerClaimState.EXPIRED_NO_ARM,
    ),
    "destinationGuards" to listOf(destinationGuard(state = "EXPIRED_NO_ARM_RECLAIMABLE")),
    "occurrenceKeyState" to "EXPIRED_NO_ARM_RECLAIMABLE",
  )

  private fun budget(): Map<String, Any> = mapOf(
    "schemaVersion" to 1,
    "purpose" to "BIRTHDAY",
    "entries" to listOf(mapOf("id" to CLAIM_ID, "armedAtMs" to 1_000L)),
    "newestEntryAtMs" to 1_000L,
    "cleanupAtMs" to 900_000L,
  )

  private fun <T> ready(result: RequestBuildResult<T>): T =
    (result as RequestBuildResult.Ready).request

  private companion object {
    const val LEDGER = "ledger-generation-1"
    const val INSTALLATION = "0123456789abcdef0123456789abcdef"
    const val CLAIM_ID = "00000000-0000-4000-8000-000000000001"
    const val TEST_ID = "00000000-0000-4000-8000-000000000002"
    const val ARM_ID = "00000000-0000-4000-8000-000000000003"
    const val OTHER_ARM_ID = "00000000-0000-4000-8000-000000000004"
    const val RETRY_ID = "00000000-0000-4000-8000-000000000005"
    const val TARGET_INSTALLATION = "fedcba9876543210fedcba9876543210"
    const val DELETION_ID = "00000000-0000-4000-8000-000000000006"
    const val DELETION_REQUEST_KEY =
      "bdce35dd213300cfe7da6849c5e96c4cd9990c6ea0da763656e85d3bd8727a7d"
  }
}
