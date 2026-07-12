package com.yashsomani.birthdayautopilot.coordination

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinationLifecycleTransportTest {
  @Test
  fun `reset release and lifecycle requests have exact strict wire fields`() {
    val reset = ready(CoordinationRequestFactory.resetContactDerivedState(REQUEST_ID))
    assertEquals(
      mapOf("contractVersion" to 1, "requestId" to REQUEST_ID),
      reset.callablePayload(),
    )
    assertEquals(CoordinationEndpointPolicy.RESET_CONTACT_DERIVED_STATE, reset.functionName)

    val release = ready(
      CoordinationRequestFactory.releaseAndroidSender(
        REQUEST_ID,
        INSTALLATION_ID,
        4,
        3,
      ),
    )
    assertEquals(
      setOf(
        "contractVersion",
        "requestId",
        "installationId",
        "senderEpoch",
        "resetGeneration",
      ),
      release.callablePayload().keys,
    )
    assertEquals(4L, release.callablePayload()["senderEpoch"])
    assertEquals(3L, release.callablePayload()["resetGeneration"])

    val status = CoordinationRequestFactory.coordinationLifecycleStatus()
    assertEquals(mapOf("contractVersion" to 1), status.callablePayload())
    assertEquals(CoordinationEndpointPolicy.COORDINATION_LIFECYCLE_STATUS, status.functionName)

    assertEquals(
      RequestInvalidReason.REQUEST_ID,
      (CoordinationRequestFactory.resetContactDerivedState("not-a-uuid") as
        RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.INSTALLATION_ID,
      (CoordinationRequestFactory.releaseAndroidSender(
        REQUEST_ID,
        INSTALLATION_ID.uppercase(),
        4,
        3,
      ) as RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.SENDER_EPOCH,
      (CoordinationRequestFactory.releaseAndroidSender(
        REQUEST_ID,
        INSTALLATION_ID,
        0,
        3,
      ) as RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.RESET_GENERATION,
      (CoordinationRequestFactory.releaseAndroidSender(
        REQUEST_ID,
        INSTALLATION_ID,
        4,
        MAX_SAFE_JSON_INTEGER + 1,
      ) as RequestBuildResult.Invalid).reason,
    )
  }

  @Test
  fun `reset progress and completion require exact discriminated field combinations`() {
    val draining = CoordinationResponseParser.contactDerivedReset(resetDraining())
      as CoordinationOperationOutcome.InProgress
    assertEquals(CoordinationOperationStage.RESET_DRAINING, draining.progress.stage)
    assertEquals(5L, draining.progress.senderEpochAfter)
    assertEquals(60_000L, draining.progress.drainUntilMillis)

    val purging = resetDraining().toMutableMap().apply {
      this["stage"] = "RESET_PURGING"
      remove("drainUntilMs")
    }
    assertTrue(
      CoordinationResponseParser.contactDerivedReset(purging) is
        CoordinationOperationOutcome.InProgress,
    )
    assertNull(
      CoordinationResponseParser.contactDerivedReset(purging + ("drainUntilMs" to 60_000L)),
    )

    val noAndroid = mapOf(
      "kind" to "IN_PROGRESS",
      "operation" to "CONTACT_DERIVED_RESET",
      "stage" to "RESET_PURGING",
      "androidStateExisted" to false,
    )
    assertTrue(
      CoordinationResponseParser.contactDerivedReset(noAndroid) is
        CoordinationOperationOutcome.InProgress,
    )
    assertNull(
      CoordinationResponseParser.contactDerivedReset(noAndroid + ("senderEpochAfter" to 5L)),
    )

    val completed = CoordinationResponseParser.contactDerivedReset(resetCompleted())
      as CoordinationOperationOutcome.Completed
    val reset = completed.completion as CoordinationCompletion.ContactDerivedReset
    assertTrue(reset.androidStateExisted)
    assertEquals(6L, reset.resetGenerationAfter)

    val noAndroidCompletion = mapOf(
      "kind" to "COMPLETED",
      "operation" to "CONTACT_DERIVED_RESET",
      "androidStateExisted" to false,
      "contactDerivedStateErased" to true,
      "firebaseAuthPreserved" to true,
      "completedAtMs" to 60_001L,
    )
    assertTrue(
      CoordinationResponseParser.contactDerivedReset(noAndroidCompletion) is
        CoordinationOperationOutcome.Completed,
    )
    assertNull(
      CoordinationResponseParser.contactDerivedReset(
        noAndroidCompletion + ("resetGenerationAfter" to 6L),
      ),
    )
    assertNull(
      CoordinationResponseParser.contactDerivedReset(
        resetCompleted() + ("contactDerivedStateErased" to false),
      ),
    )
    assertNull(
      CoordinationResponseParser.contactDerivedReset(resetCompleted() + ("unexpected" to true)),
    )
  }

  @Test
  fun `sender release accepts only release draining purging completion and refusal union`() {
    val draining = CoordinationResponseParser.senderRelease(releaseDraining())
      as CoordinationOperationOutcome.InProgress
    assertEquals(CoordinationOperationKind.SENDER_RELEASE, draining.progress.operation)
    assertEquals(5L, draining.progress.senderEpochAfter)

    val purging = releaseDraining().toMutableMap().apply {
      this["stage"] = "RELEASE_PURGING"
      remove("drainUntilMs")
    }
    assertTrue(
      CoordinationResponseParser.senderRelease(purging) is
        CoordinationOperationOutcome.InProgress,
    )
    assertNull(CoordinationResponseParser.senderRelease(purging + ("drainUntilMs" to 60_000L)))
    assertNull(
      CoordinationResponseParser.senderRelease(
        releaseDraining() + ("birthdayAutomationNotBeforeMs" to 86_400_000L),
      ),
    )

    val completion = CoordinationResponseParser.senderRelease(releaseCompleted())
      as CoordinationOperationOutcome.Completed
    assertTrue(completion.completion is CoordinationCompletion.SenderRelease)
    assertNull(
      CoordinationResponseParser.senderRelease(
        releaseCompleted() + ("androidStateExisted" to false),
      ),
    )
    assertNull(CoordinationResponseParser.contactDerivedReset(releaseCompleted()))

    OPERATION_REFUSALS.forEach { reason ->
      val outcome = CoordinationResponseParser.senderRelease(
        mapOf("kind" to "REFUSED", "reason" to reason),
      )
      assertTrue(reason, outcome is CoordinationOperationOutcome.Refused)
    }
    assertNull(
      CoordinationResponseParser.senderRelease(
        mapOf("kind" to "REFUSED", "reason" to "MODE_BLOCKED"),
      ),
    )
  }

  @Test
  fun `lifecycle status is strict content-free and fail closed across every branch`() {
    val operation = CoordinationResponseParser.coordinationLifecycleStatus(
      resetDraining() + mapOf(
        "kind" to "OPERATION_IN_PROGRESS",
        "serverNowMs" to 1_000L,
      ),
    ) as CoordinationLifecycleStatusOutcome.OperationInProgress
    assertEquals(1_000L, operation.serverNowMillis)
    assertEquals(CoordinationOperationStage.RESET_DRAINING, operation.progress.stage)

    val deletion = CoordinationResponseParser.coordinationLifecycleStatus(
      mapOf(
        "kind" to "ACCOUNT_DELETION_IN_PROGRESS",
        "serverNowMs" to 1_000L,
        "stage" to "AUTH_DELETION_PENDING",
        "drainUntilMs" to 60_000L,
      ),
    ) as CoordinationLifecycleStatusOutcome.AccountDeletionInProgress
    assertEquals(DeletionStage.AUTH_DELETION_PENDING, deletion.stage)

    val android = CoordinationResponseParser.coordinationLifecycleStatus(androidState())
      as CoordinationLifecycleStatusOutcome.AndroidState
    assertEquals(ServerAccountMode.PAUSED_REPAIR, android.state.mode)
    assertTrue(android.latestCompletion is CoordinationCompletion.ContactDerivedReset)

    val transfer = androidState().toMutableMap().apply {
      this["mode"] = "TRANSFER_PENDING"
      this["transferTargetInstallationId"] = TARGET_INSTALLATION_ID
      this["transferDrainUntilMs"] = 60_000L
    }
    val transferStatus = CoordinationResponseParser.coordinationLifecycleStatus(transfer)
      as CoordinationLifecycleStatusOutcome.AndroidState
    assertEquals(TARGET_INSTALLATION_ID, transferStatus.state.transferTargetInstallationId)
    assertNull(
      CoordinationResponseParser.coordinationLifecycleStatus(
        transfer.toMutableMap().apply { remove("transferDrainUntilMs") },
      ),
    )
    assertNull(
      CoordinationResponseParser.coordinationLifecycleStatus(
        transfer + ("transferTargetInstallationId" to INSTALLATION_ID),
      ),
    )
    assertNull(
      CoordinationResponseParser.coordinationLifecycleStatus(
        androidState() + ("transferTargetInstallationId" to TARGET_INSTALLATION_ID),
      ),
    )

    val noAndroid = CoordinationResponseParser.coordinationLifecycleStatus(
      mapOf(
        "kind" to "NO_ANDROID_STATE",
        "serverNowMs" to 60_001L,
        "latestCompletion" to releaseCompleted(),
      ),
    ) as CoordinationLifecycleStatusOutcome.NoAndroidState
    assertTrue(noAndroid.latestCompletion is CoordinationCompletion.SenderRelease)

    assertTrue(
      CoordinationResponseParser.coordinationLifecycleStatus(
        mapOf("kind" to "SAFETY_STATUS_UNAVAILABLE", "serverNowMs" to 1_000L),
      ) is CoordinationLifecycleStatusOutcome.SafetyStatusUnavailable,
    )
    assertNull(
      CoordinationResponseParser.coordinationLifecycleStatus(
        androidState() + ("mode" to "DELETING"),
      ),
    )
    assertNull(
      CoordinationResponseParser.coordinationLifecycleStatus(
        mapOf(
          "kind" to "NO_ANDROID_STATE",
          "serverNowMs" to 1_000L,
          "latestCompletion" to null,
        ),
      ),
    )
    assertFalse(android.toString().contains(INSTALLATION_ID))
  }

  @Test
  fun `new client endpoints use one exact authenticated app-check transport dispatch`() = runTest {
    val transport = RecordingTransport(CallableTransportResult.Response(resetCompleted()))
    val client = FirebaseCoordinationClient(
      CoordinationPreflight { NativePreflightResult.Ready },
      transport,
    )
    assertTrue(
      client.resetContactDerivedState(
        ready(CoordinationRequestFactory.resetContactDerivedState(REQUEST_ID)),
      ) is CoordinationCallResult.Authoritative,
    )

    transport.result = CallableTransportResult.Response(releaseCompleted())
    assertTrue(
      client.releaseAndroidSender(
        ready(
          CoordinationRequestFactory.releaseAndroidSender(
            REQUEST_ID,
            INSTALLATION_ID,
            4,
            3,
          ),
        ),
      ) is CoordinationCallResult.Authoritative,
    )

    transport.result = CallableTransportResult.Response(
      mapOf("kind" to "SAFETY_STATUS_UNAVAILABLE", "serverNowMs" to 1_000L),
    )
    assertTrue(
      client.coordinationLifecycleStatus(
        CoordinationRequestFactory.coordinationLifecycleStatus(),
      ) is CoordinationCallResult.Authoritative,
    )
    assertEquals(
      listOf(
        CoordinationEndpointPolicy.RESET_CONTACT_DERIVED_STATE,
        CoordinationEndpointPolicy.RELEASE_ANDROID_SENDER,
        CoordinationEndpointPolicy.COORDINATION_LIFECYCLE_STATUS,
      ),
      transport.functionNames,
    )
    assertEquals(3, transport.payloads.size)
    assertEquals(mapOf("contractVersion" to 1), transport.payloads.last())
  }

  @Test
  fun `ambiguous destructive lifecycle calls remain nonterminal after one dispatch each`() = runTest {
    val transport = RecordingTransport(CallableTransportResult.AmbiguousFailure)
    val client = FirebaseCoordinationClient(
      CoordinationPreflight { NativePreflightResult.Ready },
      transport,
    )
    val reset = client.resetContactDerivedState(
      ready(CoordinationRequestFactory.resetContactDerivedState(REQUEST_ID)),
    )
    val release = client.releaseAndroidSender(
      ready(
        CoordinationRequestFactory.releaseAndroidSender(
          REQUEST_ID,
          INSTALLATION_ID,
          4,
          3,
        ),
      ),
    )
    val status = client.coordinationLifecycleStatus(
      CoordinationRequestFactory.coordinationLifecycleStatus(),
    )
    assertEquals(
      CoordinationCallResult.Unavailable(CoordinationUnavailableReason.AMBIGUOUS_CALL),
      reset,
    )
    assertEquals(
      CoordinationCallResult.Unavailable(CoordinationUnavailableReason.AMBIGUOUS_CALL),
      release,
    )
    assertEquals(
      CoordinationCallResult.Unavailable(CoordinationUnavailableReason.AMBIGUOUS_CALL),
      status,
    )
    assertEquals(3, transport.functionNames.size)
  }

  private class RecordingTransport(
    var result: CallableTransportResult,
  ) : CoordinationCallableTransport {
    val functionNames = mutableListOf<String>()
    val payloads = mutableListOf<Map<String, Any>>()

    override suspend fun call(
      functionName: String,
      payload: Map<String, Any>,
    ): CallableTransportResult {
      functionNames += functionName
      payloads += payload
      return result
    }
  }

  private fun resetDraining(): Map<String, Any> = mapOf(
    "kind" to "IN_PROGRESS",
    "operation" to "CONTACT_DERIVED_RESET",
    "stage" to "RESET_DRAINING",
    "androidStateExisted" to true,
    "senderEpochAfter" to 5L,
    "resetGenerationAfter" to 6L,
    "birthdayAutomationNotBeforeMs" to 86_400_000L,
    "drainUntilMs" to 60_000L,
  )

  private fun resetCompleted(): Map<String, Any> = mapOf(
    "kind" to "COMPLETED",
    "operation" to "CONTACT_DERIVED_RESET",
    "androidStateExisted" to true,
    "senderEpochAfter" to 5L,
    "resetGenerationAfter" to 6L,
    "birthdayAutomationNotBeforeMs" to 86_400_000L,
    "contactDerivedStateErased" to true,
    "firebaseAuthPreserved" to true,
    "completedAtMs" to 60_001L,
  )

  private fun releaseDraining(): Map<String, Any> = mapOf(
    "kind" to "IN_PROGRESS",
    "operation" to "SENDER_RELEASE",
    "stage" to "RELEASE_DRAINING",
    "androidStateExisted" to true,
    "senderEpochAfter" to 5L,
    "resetGenerationAfter" to 3L,
    "drainUntilMs" to 60_000L,
  )

  private fun releaseCompleted(): Map<String, Any> = mapOf(
    "kind" to "COMPLETED",
    "operation" to "SENDER_RELEASE",
    "androidStateExisted" to true,
    "senderEpochAfter" to 5L,
    "resetGenerationAfter" to 3L,
    "androidSenderStateErased" to true,
    "firebaseAuthPreserved" to true,
    "completedAtMs" to 60_001L,
  )

  private fun androidState(): Map<String, Any> = mapOf(
    "kind" to "ANDROID_STATE",
    "serverNowMs" to 60_001L,
    "mode" to "PAUSED_REPAIR",
    "activeInstallationId" to INSTALLATION_ID,
    "senderEpoch" to 5L,
    "resetGeneration" to 6L,
    "ownerLeaseUntilMs" to 1_000L,
    "latestIssuedSubmitNotAfterMs" to 60_000L,
    "birthdayAutomationNotBeforeMs" to 86_400_000L,
    "latestCompletion" to resetCompleted(),
  )

  private fun <T> ready(result: RequestBuildResult<T>): T =
    (result as RequestBuildResult.Ready).request

  private companion object {
    const val REQUEST_ID = "00000000-0000-4000-8000-000000000007"
    const val INSTALLATION_ID = "0123456789abcdef0123456789abcdef"
    const val TARGET_INSTALLATION_ID = "fedcba9876543210fedcba9876543210"
    val OPERATION_REFUSALS = listOf(
      "DELETION_SUPPRESSED",
      "COORDINATION_OPERATION_IN_PROGRESS",
      "REQUEST_MISMATCH",
      "RESET_SUPPRESSED",
      "CONTINUITY_UNAVAILABLE",
      "GENERATION_EXHAUSTED",
    )
  }
}
