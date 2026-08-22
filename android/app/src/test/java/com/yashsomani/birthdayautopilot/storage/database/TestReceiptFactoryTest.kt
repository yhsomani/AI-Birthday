package com.yashsomani.birthdayautopilot.storage.database

import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestReceiptFactoryTest {
  @Test
  fun `factory binds exact destination text build installation and policy`() {
    val receipt = TestReceiptFactory.create(
      test = testJob(),
      installation = installation(),
      testReceiptId = "receipt-1",
      smsPolicyVersion = "sms-policy-v1",
      passedAtMillis = 2_000,
    )

    assertEquals("destination-prehash", receipt.destinationBindingHash)
    assertEquals(
      "010c6e6d29a9180d681da7a598e01143844fe6016bba6ee4a2d6a6ed7440ec3d",
      receipt.exactTextHash,
    )
    assertTrue(
      TestReceiptCanonicalHash.matches(
        receipt.bindingHash,
        TestReceiptCanonicalHash.bindingHash(testJob(), installation(), receipt),
      ),
    )
    assertTrue(TestReceiptBindingValidator.matches(testJob(), installation(), receipt))
    val changed = receipt.copy(senderEpoch = 2)
    assertFalse(
      TestReceiptCanonicalHash.matches(
        changed.bindingHash,
        TestReceiptCanonicalHash.bindingHash(testJob(), installation(), changed),
      ),
    )
    assertFalse(TestReceiptBindingValidator.matches(testJob(), installation(), changed))
    assertFalse(
      TestReceiptBindingValidator.matches(
        testJob(),
        installation().copy(callbackGeneration = "different-generation"),
        receipt,
      ),
    )
    assertFalse(
      TestReceiptBindingValidator.matches(
        testJob().copy(terminalAtMillis = 1_999),
        installation(),
        receipt,
      ),
    )
  }

  @Test
  fun `minimum receipt binding survives atomic raw test detail redaction`() {
    val original = testJob()
    val receipt = TestReceiptFactory.create(
      test = original,
      installation = installation(),
      testReceiptId = "receipt-1",
      smsPolicyVersion = "sms-policy-v1",
      passedAtMillis = 2_000,
    )
    val redacted = original.copy(
      normalizedDestination = "",
      maskedDestination = "",
      exactMessage = "",
      foregroundConfirmationNonceHash = "",
      foregroundConfirmedAtMillis = 0,
      revision = original.revision + 1,
    )

    assertEquals(TestJobRetainedDetail.State.REDACTED, TestJobRetainedDetail.classify(redacted))
    assertTrue(TestReceiptBindingValidator.matches(redacted, installation(), receipt))
    assertFalse(
      TestReceiptBindingValidator.matches(
        redacted.copy(retentionUntilMillis = 20_000),
        installation(),
        receipt,
        nowMillis = 19_999,
      ),
    )
    assertFalse(
      TestReceiptBindingValidator.matches(
        redacted.copy(payloadHash = "tampered-payload"),
        installation(),
        receipt,
      ),
    )
  }

  @Test
  fun `partial raw test detail redaction is fail closed`() {
    val original = testJob()
    val receipt = TestReceiptFactory.create(
      test = original,
      installation = installation(),
      testReceiptId = "receipt-1",
      smsPolicyVersion = "sms-policy-v1",
      passedAtMillis = 2_000,
    )
    val partial = original.copy(exactMessage = "")

    assertEquals(TestJobRetainedDetail.State.INVALID, TestJobRetainedDetail.classify(partial))
    assertFalse(TestReceiptBindingValidator.matches(partial, installation(), receipt))
  }

  private fun testJob() = TestJobEntity(
    testJobId = "test-1",
    accountId = "account-1",
    installationId = "installation-1",
    senderEpoch = 1,
    testRequestId = "request-1",
    configHash = "config-hash",
    destinationPrehash = "destination-prehash",
    normalizedDestination = "+919999999999",
    maskedDestination = "•••• 9999",
    exactMessage = "WishWell test",
    payloadHash = "payload-hash",
    simPolicyKind = "EXPLICIT_SUBSCRIPTION",
    resolvedSubscriptionId = 4,
    segmentCount = 1,
    messageEncoding = "GSM_7",
    orderedPartsHash = "parts-hash",
    buildBindingHash = "build-hash",
    appCheckPolicyVersion = "app-check-v1",
    state = TestJobState.PASSED,
    revision = 4,
    foregroundConfirmationNonceHash = "foreground-hash",
    foregroundConfirmedAtMillis = 1_000,
    createdAtMillis = 1_000,
    updatedAtMillis = 2_000,
    terminalAtMillis = 2_000,
    invalidationReason = null,
    retentionUntilMillis = 10_000,
  )

  private fun installation() = InstallationBindingEntity(
    installationId = "installation-1",
    accountId = "account-1",
    localSlot = 1,
    callbackGeneration = "callback-generation-1",
    state = InstallationRecordState.ACTIVE,
    accountMode = AccountMode.TEST_ONLY,
    senderEpoch = 1,
    resetGeneration = 1,
    ownerLeaseUntilMillis = 10_000,
    appVersionCode = 1,
    distributionChannel = "controlled-test",
    signingCertificateSha256 = "certificate-hash",
    lastVerifiedServerMillis = 1_000,
    revision = 0,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_000,
  )
}
