package com.yashsomani.birthdayautopilot.automation.sms

import com.yashsomani.birthdayautopilot.storage.database.ArmedAttemptPermit
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import com.yashsomani.birthdayautopilot.storage.database.SafetyLedgerDao

/** Private send material loaded from the encrypted ledger; never bridge or log this value. */
internal sealed interface LocalSendPayload {
  val purpose: OperationPurpose
  val operationId: String
  val payloadHash: String
  val destinationE164: String
  val exactText: String
  val orderedPartsHash: String
  val messageEncoding: String
  val expectedPartCount: Int
  val subscriptionId: Int
  val callbackGeneration: String
  val roamingAllowed: Boolean
  val foregroundConfirmationNonceHash: String?
  val foregroundConfirmedAtMillis: Long?
}

private data class LedgerLocalSendPayload(
  override val purpose: OperationPurpose,
  override val operationId: String,
  override val payloadHash: String,
  override val destinationE164: String,
  override val exactText: String,
  override val orderedPartsHash: String,
  override val messageEncoding: String,
  override val expectedPartCount: Int,
  override val subscriptionId: Int,
  override val callbackGeneration: String,
  override val roamingAllowed: Boolean,
  override val foregroundConfirmationNonceHash: String?,
  override val foregroundConfirmedAtMillis: Long?,
) : LocalSendPayload {
  override fun toString(): String = "LocalSendPayload(<redacted>)"
}

internal object LocalSendPayloadLoader {
  suspend fun load(
    permit: ArmedAttemptPermit,
    ledger: SafetyLedgerDao,
  ): LocalSendPayload? {
    val attempt = ledger.getSendAttempt(permit.sendAttemptId) ?: return null
    val installation = ledger.getInstallation(permit.installationId) ?: return null
    if (
      attempt.permitId != permit.permitId ||
      attempt.purpose != permit.purpose ||
      attempt.operationId != permit.operationId ||
      attempt.attemptNumber != permit.attemptNumber ||
      attempt.payloadHash != permit.payloadHash ||
      attempt.installationId != permit.installationId ||
      attempt.callbackGeneration != installation.callbackGeneration
    ) return null

    return when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = ledger.getBirthdayOccurrence(permit.operationId) ?: return null
        val approval = ledger.getApproval(occurrence.approvalId) ?: return null
        val policy = ledger.getAutomationPolicy(occurrence.policyId) ?: return null
        // A policy row is re-read at the barrier by SafetyLedgerDao. The immutable approval owns
        // the exact approved SIM, segmentation plan, destination, and final message text.
        if (
          occurrence.payloadHash != permit.payloadHash ||
          approval.contentHash != permit.payloadHash ||
          approval.destinationFingerprint != occurrence.destinationFingerprint ||
          approval.policyId != policy.policyId ||
          approval.policyRevision != policy.revision ||
          approval.segmentCount != attempt.expectedPartCount ||
          approval.resolvedSubscriptionId != attempt.resolvedSubscriptionId
        ) return null
        LedgerLocalSendPayload(
          purpose = permit.purpose,
          operationId = permit.operationId,
          payloadHash = permit.payloadHash,
          destinationE164 = approval.normalizedPhoneE164,
          exactText = approval.exactMessage,
          orderedPartsHash = approval.orderedPartsHash,
          messageEncoding = approval.messageEncoding,
          expectedPartCount = approval.segmentCount,
          subscriptionId = approval.resolvedSubscriptionId,
          callbackGeneration = attempt.callbackGeneration,
          roamingAllowed = policy.roamingAllowed,
          foregroundConfirmationNonceHash = null,
          foregroundConfirmedAtMillis = null,
        )
      }
      OperationPurpose.TEST -> {
        val test = ledger.getTestJob(permit.operationId) ?: return null
        if (
          test.payloadHash != permit.payloadHash ||
          test.segmentCount != attempt.expectedPartCount ||
          test.resolvedSubscriptionId != attempt.resolvedSubscriptionId
        ) return null
        LedgerLocalSendPayload(
          purpose = permit.purpose,
          operationId = permit.operationId,
          payloadHash = permit.payloadHash,
          destinationE164 = test.normalizedDestination,
          exactText = test.exactMessage,
          orderedPartsHash = test.orderedPartsHash,
          messageEncoding = test.messageEncoding,
          expectedPartCount = test.segmentCount,
          subscriptionId = test.resolvedSubscriptionId,
          callbackGeneration = attempt.callbackGeneration,
          roamingAllowed = false,
          foregroundConfirmationNonceHash = test.foregroundConfirmationNonceHash,
          foregroundConfirmedAtMillis = test.foregroundConfirmedAtMillis,
        )
      }
    }
  }
}
