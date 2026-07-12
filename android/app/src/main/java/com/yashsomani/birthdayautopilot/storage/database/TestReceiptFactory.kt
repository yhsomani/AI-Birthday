package com.yashsomani.birthdayautopilot.storage.database

import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object TestReceiptFactory {
  fun create(
    test: TestJobEntity,
    installation: InstallationBindingEntity,
    testReceiptId: String,
    smsPolicyVersion: String,
    passedAtMillis: Long,
  ): TestReceiptEntity {
    require(testReceiptId.isNotBlank()) { "test-receipt-id-invalid" }
    require(smsPolicyVersion.isNotBlank()) { "sms-policy-version-invalid" }
    require(passedAtMillis > 0) { "passed-time-invalid" }
    require(test.accountId == installation.accountId) { "receipt-account-mismatch" }
    require(test.installationId == installation.installationId) { "receipt-installation-mismatch" }
    require(test.senderEpoch == installation.senderEpoch) { "receipt-epoch-mismatch" }
    val unsigned = TestReceiptEntity(
      testReceiptId = testReceiptId,
      testJobId = test.testJobId,
      accountId = test.accountId,
      bindingHash = "",
      configHash = test.configHash,
      destinationBindingHash = test.destinationPrehash,
      maskedDestination = test.maskedDestination,
      exactTextHash = TestReceiptCanonicalHash.sha256Text(test.exactMessage),
      segmentPlanHash = test.orderedPartsHash,
      resolvedSubscriptionId = test.resolvedSubscriptionId,
      installationId = test.installationId,
      senderEpoch = test.senderEpoch,
      buildBindingHash = test.buildBindingHash,
      distributionChannel = installation.distributionChannel,
      appCheckPolicyVersion = test.appCheckPolicyVersion,
      smsPolicyVersion = smsPolicyVersion,
      state = TestReceiptState.VALID,
      passedAtMillis = passedAtMillis,
      invalidatedAtMillis = null,
      invalidationReason = null,
    )
    return unsigned.copy(
      bindingHash = TestReceiptCanonicalHash.bindingHash(test, installation, unsigned),
    )
  }
}

/** Revalidates a persisted receipt against the exact immutable TestJob and current installation. */
object TestReceiptBindingValidator {
  fun matches(
    test: TestJobEntity,
    installation: InstallationBindingEntity,
    receipt: TestReceiptEntity,
    nowMillis: Long = System.currentTimeMillis().coerceAtLeast(0),
  ): Boolean {
    val retainedDetail = TestJobRetainedDetail.classify(test)
    if (
      nowMillis < 0 ||
      retainedDetail == TestJobRetainedDetail.State.INVALID ||
      (retainedDetail == TestJobRetainedDetail.State.REDACTED &&
        test.retentionUntilMillis > nowMillis) ||
      test.state != TestJobState.PASSED ||
      test.terminalAtMillis != receipt.passedAtMillis ||
      test.invalidationReason != null ||
      receipt.state != TestReceiptState.VALID ||
      receipt.invalidatedAtMillis != null ||
      receipt.invalidationReason != null ||
      receipt.testJobId != test.testJobId ||
      receipt.accountId != test.accountId ||
      test.accountId != installation.accountId ||
      receipt.installationId != installation.installationId ||
      test.installationId != installation.installationId ||
      receipt.senderEpoch != installation.senderEpoch ||
      test.senderEpoch != installation.senderEpoch ||
      receipt.configHash != test.configHash ||
      receipt.destinationBindingHash != test.destinationPrehash ||
      (retainedDetail == TestJobRetainedDetail.State.FULL &&
        receipt.maskedDestination != test.maskedDestination) ||
      (retainedDetail == TestJobRetainedDetail.State.FULL &&
        receipt.exactTextHash != TestReceiptCanonicalHash.sha256Text(test.exactMessage)) ||
      receipt.segmentPlanHash != test.orderedPartsHash ||
      receipt.resolvedSubscriptionId != test.resolvedSubscriptionId ||
      receipt.buildBindingHash != test.buildBindingHash ||
      receipt.distributionChannel != installation.distributionChannel ||
      receipt.appCheckPolicyVersion != test.appCheckPolicyVersion ||
      receipt.smsPolicyVersion.isBlank() ||
      receipt.passedAtMillis <= 0
    ) return false
    return TestReceiptCanonicalHash.matches(
      TestReceiptCanonicalHash.bindingHash(test, installation, receipt),
      receipt.bindingHash,
    )
  }
}

/**
 * After 30 days, the TestReceipt owns the minimum masked destination and exact-text hash. The
 * duplicate raw TEST number/message and foreground nonce in TestJob are atomically blanked. A
 * partially blank row is treated as corrupt and can never satisfy activation readiness.
 */
internal object TestJobRetainedDetail {
  enum class State { FULL, REDACTED, INVALID }

  fun classify(test: TestJobEntity): State {
    val full = test.normalizedDestination.isNotBlank() &&
      test.maskedDestination.isNotBlank() &&
      test.exactMessage.isNotBlank() &&
      test.foregroundConfirmationNonceHash.isNotBlank() &&
      test.foregroundConfirmedAtMillis > 0
    if (full) return State.FULL
    val redacted = test.normalizedDestination.isEmpty() &&
      test.maskedDestination.isEmpty() &&
      test.exactMessage.isEmpty() &&
      test.foregroundConfirmationNonceHash.isEmpty() &&
      test.foregroundConfirmedAtMillis == 0L
    return if (redacted) State.REDACTED else State.INVALID
  }
}

internal object TestReceiptCanonicalHash {
  private const val BINDING_DOMAIN = "BirthdayAutopilot.AndroidTestReceipt.v1"
  private val HEX = "0123456789abcdef".toCharArray()

  fun sha256Text(exactText: String): String = hex(
    MessageDigest.getInstance("SHA-256").digest(exactText.toByteArray(StandardCharsets.UTF_8)),
  )

  fun bindingHash(
    test: TestJobEntity,
    installation: InstallationBindingEntity,
    receipt: TestReceiptEntity,
  ): String = canonicalHash(
    domain = BINDING_DOMAIN,
    fields = listOf(
      "schemaVersion" to "1",
      "testReceiptId" to receipt.testReceiptId,
      "testJobId" to test.testJobId,
      "testRequestId" to test.testRequestId,
      "accountId" to receipt.accountId,
      "configHash" to receipt.configHash,
      "destinationPrehash" to receipt.destinationBindingHash,
      "maskedDestination" to receipt.maskedDestination,
      "exactTextHash" to receipt.exactTextHash,
      "payloadHash" to test.payloadHash,
      "segmentCount" to test.segmentCount.toString(),
      "messageEncoding" to test.messageEncoding,
      "segmentPlanHash" to receipt.segmentPlanHash,
      "resolvedSubscriptionId" to receipt.resolvedSubscriptionId.toString(),
      "installationId" to receipt.installationId,
      "callbackGeneration" to installation.callbackGeneration,
      "senderEpoch" to receipt.senderEpoch.toString(),
      "buildBindingHash" to receipt.buildBindingHash,
      "distributionChannel" to receipt.distributionChannel,
      "signingCertificateSha256" to installation.signingCertificateSha256,
      "appCheckPolicyVersion" to receipt.appCheckPolicyVersion,
      "smsPolicyVersion" to receipt.smsPolicyVersion,
      "passedAtMillis" to receipt.passedAtMillis.toString(),
    ),
  )

  fun matches(expected: String, actual: String): Boolean = MessageDigest.isEqual(
    expected.toByteArray(StandardCharsets.US_ASCII),
    actual.toByteArray(StandardCharsets.US_ASCII),
  )

  private fun canonicalHash(domain: String, fields: List<Pair<String, String>>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    updateLengthPrefixed(digest, domain)
    fields.forEach { (name, value) ->
      updateLengthPrefixed(digest, name)
      updateLengthPrefixed(digest, value)
    }
    return hex(digest.digest())
  }

  private fun updateLengthPrefixed(digest: MessageDigest, value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
    digest.update(bytes)
  }

  private fun hex(bytes: ByteArray): String = CharArray(bytes.size * 2).also { output ->
    bytes.forEachIndexed { index, byte ->
      val value = byte.toInt() and 0xff
      output[index * 2] = HEX[value ushr 4]
      output[index * 2 + 1] = HEX[value and 0x0f]
    }
  }.concatToString()
}
