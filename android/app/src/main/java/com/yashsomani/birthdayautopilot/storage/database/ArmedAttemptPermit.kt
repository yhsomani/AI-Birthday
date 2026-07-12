package com.yashsomani.birthdayautopilot.storage.database

/**
 * Kotlin-only, non-serializable capability accepted by the future sole SmsGateway entry point.
 * No public constructor exists: Room can mint it only after the local submission barrier commits.
 */
sealed interface ArmedAttemptPermit {
  val permitId: String
  val sendAttemptId: String
  val purpose: OperationPurpose
  val operationId: String
  val attemptNumber: Int
  val installationId: String
  val senderEpoch: Long
  val payloadHash: String
  val foregroundConfirmationNonceHash: String?
  val armStartBlockerRevision: Long
  val serverSubmitNotAfterMillis: Long
  val effectiveSubmitNotAfterMillis: Long
  val deadlineElapsedRealtimeMillis: Long
  val bootCount: Int
}

private class RoomArmedAttemptPermit(
  override val permitId: String,
  override val sendAttemptId: String,
  override val purpose: OperationPurpose,
  override val operationId: String,
  override val attemptNumber: Int,
  override val installationId: String,
  override val senderEpoch: Long,
  override val payloadHash: String,
  override val foregroundConfirmationNonceHash: String?,
  override val armStartBlockerRevision: Long,
  override val serverSubmitNotAfterMillis: Long,
  override val effectiveSubmitNotAfterMillis: Long,
  override val deadlineElapsedRealtimeMillis: Long,
  override val bootCount: Int,
) : ArmedAttemptPermit {
  override fun toString(): String = "ArmedAttemptPermit(<redacted>)"
}

internal object ArmedAttemptPermitIssuer {
  fun issue(
    permit: CoordinationPermitEntity,
    sendAttemptId: String,
    foregroundConfirmationNonceHash: String? = null,
  ): ArmedAttemptPermit {
    check(permit.state == CoordinationPermitState.BARRIER_CONSUMED) { "barrier-not-consumed" }
    val blockerRevision = checkNotNull(permit.armStartBlockerRevision) { "blocker-revision-missing" }
    val serverDeadline = checkNotNull(permit.serverSubmitNotAfterMillis) { "server-deadline-missing" }
    val effectiveDeadline = checkNotNull(permit.effectiveSubmitNotAfterMillis) {
      "effective-deadline-missing"
    }
    val serverRemaining = try {
      Math.subtractExact(effectiveDeadline, permit.trustedServerNowMillis)
    } catch (_: ArithmeticException) {
      throw IllegalStateException("server-deadline-overflow")
    }
    check(serverRemaining > 0) { "permit-expired" }
    check(permit.requestStartElapsedMillis >= 0) { "elapsed-anchor-invalid" }
    check(
      (permit.purpose == OperationPurpose.TEST) ==
        !foregroundConfirmationNonceHash.isNullOrBlank(),
    ) { "foreground-confirmation-binding-invalid" }
    val elapsedDeadline = try {
      Math.addExact(permit.requestStartElapsedMillis, serverRemaining)
    } catch (_: ArithmeticException) {
      throw IllegalStateException("elapsed-deadline-overflow")
    }
    return RoomArmedAttemptPermit(
      permitId = permit.permitId,
      sendAttemptId = sendAttemptId,
      purpose = permit.purpose,
      operationId = permit.operationId,
      attemptNumber = permit.attemptNumber,
      installationId = permit.installationId,
      senderEpoch = permit.senderEpoch,
      payloadHash = permit.payloadHash,
      foregroundConfirmationNonceHash = foregroundConfirmationNonceHash,
      armStartBlockerRevision = blockerRevision,
      serverSubmitNotAfterMillis = serverDeadline,
      effectiveSubmitNotAfterMillis = effectiveDeadline,
      deadlineElapsedRealtimeMillis = elapsedDeadline,
      bootCount = permit.bootCount,
    )
  }
}

data class FinalExternalGateSnapshot(
  val distributionEligible: Boolean,
  val accountSessionValid: Boolean,
  val contactsAuthorizationValid: Boolean,
  val networkValidated: Boolean,
  val backgroundAllowed: Boolean,
  val smsPermissionGranted: Boolean,
  val simReady: Boolean,
  val currentSubscriptionId: Int,
  val payloadHash: String,
  val orderedPartsHash: String,
  val foregroundConfirmationValid: Boolean,
  val foregroundConfirmationNonceHash: String?,
  val observedAtElapsedRealtimeMillis: Long,
  val bootCount: Int,
)

data class AuthoritativeArmedEvidence(
  val armRequestId: String,
  val serverNowMillis: Long,
  val serverSubmitNotAfterMillis: Long,
)

sealed interface PermitIssueResult {
  data class Issued(val permit: ArmedAttemptPermit) : PermitIssueResult
  data class Suppressed(val reason: String) : PermitIssueResult
}

sealed interface ArmDispatchResult {
  data class Committed(val blockerRevision: Long) : ArmDispatchResult
  data class Rejected(val reason: String) : ArmDispatchResult
}
