package com.yashsomani.birthdayautopilot.coordination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinationRequestFactoryTest {
  @Test
  fun `binding and registration mirror the strict backend schema`() {
    val registration = ready(
      CoordinationRequestFactory.registration(
        LEDGER,
        INSTALLATION,
        100,
        7,
        DistributionChannel.PLAY,
      ),
    )
    assertEquals(
      setOf(
        "contractVersion",
        "ledgerGeneration",
        "installationId",
        "appBuildNumber",
        "policyVersion",
        "distributionChannel",
      ),
      registration.callablePayload().keys,
    )
    assertEquals(1, registration.callablePayload()["contractVersion"])
    assertEquals("PLAY", registration.callablePayload()["distributionChannel"])

    val lease = CoordinationRequestFactory.lease(binding(), CoordinationPurpose.BIRTHDAY)
    assertEquals(
      setOf(
        "contractVersion",
        "ledgerGeneration",
        "installationId",
        "senderEpoch",
        "resetGeneration",
        "appBuildNumber",
        "policyVersion",
        "distributionChannel",
        "purpose",
      ),
      lease.callablePayload().keys,
    )
  }

  @Test
  fun `birthday and test claim payloads remain content minimized and purpose separated`() {
    val birthday = ready(
      CoordinationRequestFactory.birthdayClaim(
        binding(),
        CLAIM_ID,
        listOf("ab".repeat(32)),
        listOf("cd".repeat(32)),
      ),
    )
    assertEquals(
      setOf(
        "contractVersion",
        "ledgerGeneration",
        "installationId",
        "senderEpoch",
        "resetGeneration",
        "appBuildNumber",
        "policyVersion",
        "distributionChannel",
        "purpose",
        "claimRequestId",
        "recipientPrehashAliases",
        "destinationPrehashAliases",
      ),
      birthday.callablePayload().keys,
    )
    assertFalse(birthday.callablePayload().containsKey("phone"))
    assertFalse(birthday.callablePayload().containsKey("message"))
    assertFalse(birthday.callablePayload().containsKey("birthday"))

    val test = ready(
      CoordinationRequestFactory.testClaim(
        binding(),
        TEST_ID,
        "12".repeat(32),
        "34".repeat(32),
      ),
    )
    assertEquals("TEST", test.callablePayload()["purpose"])
    assertFalse(test.callablePayload().containsKey("recipientPrehashAliases"))
  }

  @Test
  fun `mode arm retry report and companion builders have exact discriminated fields`() {
    val pause = CoordinationRequestFactory.pauseForRepair(binding())
    assertEquals("PAUSE_FOR_REPAIR", pause.callablePayload()["action"])
    assertFalse(pause.callablePayload().containsKey("testClaimId"))

    val activate = ready(
      CoordinationRequestFactory.activateAutomation(
        binding(),
        TEST_ID,
        "ef".repeat(32),
        1,
      ),
    )
    assertEquals("ACTIVATE_AUTOMATION", activate.callablePayload()["action"])
    assertEquals(1, activate.callablePayload()["readinessContractVersion"])

    val arm = ready(CoordinationRequestFactory.arm(binding(), CoordinationPurpose.BIRTHDAY, CLAIM_ID, ARM_ID, 1))
    assertEquals("BIRTHDAY", arm.callablePayload()["purpose"])
    assertEquals(1, arm.callablePayload()["attempt"])

    val retry = ready(
      CoordinationRequestFactory.retry(
        binding(),
        CLAIM_ID,
        RETRY_ID,
        RetryProof.ALL_PARTS_RADIO_OFF,
      ),
    )
    assertEquals("ALL_PARTS_RADIO_OFF", retry.callablePayload()["proof"])
    assertEquals(RETRY_ID, retry.callablePayload()["retryRequestId"])

    val report = ready(
      CoordinationRequestFactory.testReport(
        binding(),
        TEST_ID,
        ARM_ID,
        TestReportResult.SENT_ALL_PARTS,
      ),
    )
    assertEquals("SENT_ALL_PARTS", report.callablePayload()["result"])

    val companion = ready(CoordinationRequestFactory.companionStatus(LEDGER))
    assertEquals(setOf("contractVersion", "ledgerGeneration"), companion.callablePayload().keys)

    val beginTransfer = ready(
      CoordinationRequestFactory.beginSenderTransfer(binding(), TARGET_INSTALLATION),
    )
    assertEquals(CoordinationEndpointPolicy.BEGIN_SENDER_TRANSFER, beginTransfer.functionName)
    assertEquals(TARGET_INSTALLATION, beginTransfer.callablePayload()["targetInstallationId"])
    assertFalse(beginTransfer.callablePayload().containsKey("requestId"))

    val completeTransfer = ready(
      CoordinationRequestFactory.completeSenderTransfer(binding(), TARGET_INSTALLATION),
    )
    assertEquals(CoordinationEndpointPolicy.COMPLETE_SENDER_TRANSFER, completeTransfer.functionName)

    val deletion = ready(CoordinationRequestFactory.accountDeletion(DELETION_ID))
    assertEquals(
      mapOf("contractVersion" to 1, "requestId" to DELETION_ID),
      deletion.callablePayload(),
    )
  }

  @Test
  fun `invalid IDs bounds hashes and versions fail before transport`() {
    assertEquals(
      RequestInvalidReason.INSTALLATION_ID,
      (CoordinationRequestFactory.registration(
        LEDGER,
        INSTALLATION.uppercase(),
        100,
        7,
        DistributionChannel.PLAY,
      ) as RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.INSTALLATION_ID,
      (CoordinationRequestFactory.beginSenderTransfer(
        binding(),
        INSTALLATION,
      ) as RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.REQUEST_ID,
      (CoordinationRequestFactory.accountDeletion("not-a-uuid") as RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.REQUEST_ID,
      (CoordinationRequestFactory.accountDeletion(
        "00000000-0000-5000-8000-000000000001",
      ) as RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.REQUEST_ID,
      (CoordinationRequestFactory.accountDeletionReceipt(
        "00000000-0000-5000-8000-000000000001",
      ) as RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.SENDER_EPOCH,
      (CoordinationRequestFactory.binding(
        LEDGER,
        INSTALLATION,
        MAX_SAFE_JSON_INTEGER + 1,
        1,
        100,
        7,
        DistributionChannel.PLAY,
      ) as RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.PREHASH_ALIASES,
      (CoordinationRequestFactory.birthdayClaim(
        binding(),
        CLAIM_ID,
        emptyList(),
        listOf("cd".repeat(32)),
      ) as RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.ATTEMPT,
      (CoordinationRequestFactory.arm(
        binding(),
        CoordinationPurpose.BIRTHDAY,
        CLAIM_ID,
        ARM_ID,
        3,
      ) as RequestBuildResult.Invalid).reason,
    )
    assertEquals(
      RequestInvalidReason.REQUEST_ID,
      (CoordinationRequestFactory.retry(
        binding(),
        CLAIM_ID,
        "not-a-uuid",
        RetryProof.ALL_PARTS_NO_SERVICE,
      ) as RequestBuildResult.Invalid).reason,
    )
  }

  @Test
  fun `request and binding string forms never reveal identifiers or hashes`() {
    val request = ready(
      CoordinationRequestFactory.birthdayClaim(
        binding(),
        CLAIM_ID,
        listOf("ab".repeat(32)),
        listOf("cd".repeat(32)),
      ),
    )
    val rendered = request.toString() + binding().toString()
    assertFalse(rendered.contains(CLAIM_ID))
    assertFalse(rendered.contains(INSTALLATION))
    assertFalse(rendered.contains("ab".repeat(32)))
    assertTrue(rendered.contains("redacted"))
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

  private fun <T> ready(result: RequestBuildResult<T>): T =
    (result as RequestBuildResult.Ready).request

  private companion object {
    const val LEDGER = "ledger-generation-1"
    const val INSTALLATION = "0123456789abcdef0123456789abcdef"
    const val CLAIM_ID = "00000000-0000-4000-8000-000000000001"
    const val TEST_ID = "00000000-0000-4000-8000-000000000002"
    const val ARM_ID = "00000000-0000-4000-8000-000000000003"
    const val RETRY_ID = "00000000-0000-4000-8000-000000000004"
    const val TARGET_INSTALLATION = "fedcba9876543210fedcba9876543210"
    const val DELETION_ID = "00000000-0000-4000-8000-000000000006"
  }
}
