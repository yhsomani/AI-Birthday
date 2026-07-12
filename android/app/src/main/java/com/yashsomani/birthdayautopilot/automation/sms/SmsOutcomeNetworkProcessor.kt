package com.yashsomani.birthdayautopilot.automation.sms

import androidx.room.withTransaction
import com.yashsomani.birthdayautopilot.automation.state.BirthdayJobState
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.coordination.CoordinationPurpose
import com.yashsomani.birthdayautopilot.coordination.RetryOutcome
import com.yashsomani.birthdayautopilot.coordination.RetryProof
import com.yashsomani.birthdayautopilot.coordination.ServerClaim
import com.yashsomani.birthdayautopilot.coordination.ServerClaimState
import com.yashsomani.birthdayautopilot.coordination.TestReportOutcome
import com.yashsomani.birthdayautopilot.coordination.TestReportResult
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitEntity
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitState
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import com.yashsomani.birthdayautopilot.storage.database.SendAttemptState
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class SmsOutcomeNetworkResult(
  val safeCode: String,
  val retryRecommended: Boolean,
  val localStateChanged: Boolean,
) {
  override fun toString(): String =
    "SmsOutcomeNetworkResult(code=$safeCode,retry=$retryRecommended,changed=$localStateChanged)"
}

/** Executes only content-free retry/report callables. No receiver or SubmissionGate calls this. */
internal class SmsOutcomeNetworkProcessor(
  private val database: BirthdayDatabase,
  private val coordination: SmsOutcomeCoordinationPort,
) {
  private val dao get() = database.smsOutcomeDao()

  suspend fun process(
    sendAttemptId: String,
    wallNowMillis: Long,
    elapsedRealtimeMillis: Long,
    bootCount: Int?,
  ): SmsOutcomeNetworkResult {
    val snapshot = database.withTransaction {
      val attempt = dao.attempt(sendAttemptId) ?: return@withTransaction null
      val permit = dao.permit(attempt.permitId) ?: return@withTransaction null
      attempt to permit
    } ?: return result("OUTCOME_NETWORK_BINDING_MISSING", false, false)
    return when (snapshot.first.purpose) {
      OperationPurpose.BIRTHDAY -> authorizeRetry(
        sendAttemptId,
        snapshot.second,
        wallNowMillis,
        elapsedRealtimeMillis,
        bootCount,
      )
      OperationPurpose.TEST -> reportTest(sendAttemptId, snapshot.second)
    }
  }

  private suspend fun authorizeRetry(
    sendAttemptId: String,
    originalPermit: CoordinationPermitEntity,
    wallNowMillis: Long,
    elapsedRealtimeMillis: Long,
    bootCount: Int?,
  ): SmsOutcomeNetworkResult {
    val windowExpired = database.withTransaction {
      val attempt = dao.attempt(sendAttemptId) ?: return@withTransaction false
      val occurrence = dao.birthday(attempt.operationId) ?: return@withTransaction false
      attempt.state == SendAttemptState.RETRYABLE_ZERO &&
        attempt.attemptNumber == 1 &&
        occurrence.state == BirthdayJobState.RETRYABLE_ZERO &&
        occurrence.attemptNumber == 1 &&
        occurrence.resolvedWindowEndMillis <= wallNowMillis
    }
    if (windowExpired) {
      val changed = closeRetry(
        sendAttemptId,
        wallNowMillis,
        "RETRY_WINDOW_CLOSED",
        missed = true,
      )
      return result("RETRY_WINDOW_CLOSED", false, changed)
    }
    val prepared = database.withTransaction {
      val attempt = dao.attempt(sendAttemptId) ?: return@withTransaction null
      val occurrence = dao.birthday(attempt.operationId) ?: return@withTransaction null
      val permit = dao.permit(attempt.permitId) ?: return@withTransaction null
      val replay = dao.permitForAttempt(OperationPurpose.BIRTHDAY, attempt.operationId, 2)
      if (
        replay != null &&
        replay.state == CoordinationPermitState.CLOUD_CLAIMED &&
        replay.attemptNumber == 2 &&
        occurrence.state == BirthdayJobState.RETRY_CLAIMED &&
        occurrence.attemptNumber == 2
      ) return@withTransaction RetryPreparation.AlreadyCommitted
      if (
        attempt.state != SendAttemptState.RETRYABLE_ZERO ||
        attempt.attemptNumber != 1 ||
        occurrence.state != BirthdayJobState.RETRYABLE_ZERO ||
        occurrence.attemptNumber != 1 ||
        occurrence.resolvedWindowEndMillis <= wallNowMillis ||
        permit != originalPermit
      ) return@withTransaction null
      val proof = when (attempt.safeOutcomeCode) {
        "ALL_PARTS_RADIO_OFF" -> RetryProof.ALL_PARTS_RADIO_OFF
        "ALL_PARTS_NO_SERVICE" -> RetryProof.ALL_PARTS_NO_SERVICE
        else -> return@withTransaction null
      }
      RetryPreparation.Ready(attempt.revision, occurrence.revision, occurrence.resolvedWindowEndMillis, proof)
    }
    if (prepared == RetryPreparation.AlreadyCommitted) {
      return result("RETRY_AUTHORIZATION_ALREADY_COMMITTED", false, false)
    }
    val ready = prepared as? RetryPreparation.Ready
      ?: return result("RETRY_NOT_ELIGIBLE", false, false)
    val boot = bootCount?.takeIf { it >= 0 }
      ?: return result("RETRY_BOOT_ANCHOR_UNAVAILABLE", true, false)
    if (elapsedRealtimeMillis < 0) return result("RETRY_ELAPSED_TIME_INVALID", true, false)

    return when (val call = coordination.authorizeRetry(originalPermit, ready.proof)) {
      is SmsOutcomeCoordinationCall.Unavailable -> result(call.safeCode, true, false)
      is SmsOutcomeCoordinationCall.Authoritative -> when (val outcome = call.value) {
        is RetryOutcome.Refused -> {
          val changed = closeRetry(
            sendAttemptId,
            wallNowMillis,
            "RETRY_REFUSED_${outcome.reason.name}",
            missed = wallNowMillis >= ready.windowEndMillis,
          )
          result("RETRY_REFUSED_${outcome.reason.name}", false, changed)
        }
        is RetryOutcome.Authorized -> {
          if (!SmsRetryAuthorizationPolicy.isValid(
              outcome.claim,
              originalPermit,
              SmsRetryRequestIdentity.forPermit(originalPermit.permitId),
              ready.proof,
              wallNowMillis,
            )
          ) {
            val changed = closeRetry(
              sendAttemptId,
              wallNowMillis,
              "RETRY_AUTHORIZATION_BINDING_INVALID",
              missed = false,
            )
            return result("RETRY_AUTHORIZATION_BINDING_INVALID", false, changed)
          }
          val committed = commitRetryAuthorization(
            sendAttemptId = sendAttemptId,
            originalPermit = originalPermit,
            claim = outcome.claim,
            expectedAttemptRevision = ready.attemptRevision,
            expectedOccurrenceRevision = ready.occurrenceRevision,
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            bootCount = boot,
            wallNowMillis = wallNowMillis,
          )
          result(
            if (committed) "RETRY_AUTHORIZATION_COMMITTED" else "RETRY_AUTHORIZATION_STALE",
            false,
            committed,
          )
        }
      }
    }
  }

  private suspend fun commitRetryAuthorization(
    sendAttemptId: String,
    originalPermit: CoordinationPermitEntity,
    claim: ServerClaim,
    expectedAttemptRevision: Long,
    expectedOccurrenceRevision: Long,
    elapsedRealtimeMillis: Long,
    bootCount: Int,
    wallNowMillis: Long,
  ): Boolean = database.withTransaction {
    val attempt = dao.attempt(sendAttemptId) ?: return@withTransaction false
    val occurrence = dao.birthday(attempt.operationId) ?: return@withTransaction false
    val existing = dao.permitForAttempt(OperationPurpose.BIRTHDAY, attempt.operationId, 2)
    if (existing != null) {
      return@withTransaction existing.state == CoordinationPermitState.CLOUD_CLAIMED &&
        existing.opaqueClaimId == claim.claimId &&
        occurrence.state == BirthdayJobState.RETRY_CLAIMED &&
        occurrence.attemptNumber == 2
    }
    if (
      attempt.state != SendAttemptState.RETRYABLE_ZERO ||
      attempt.attemptNumber != 1 ||
      attempt.revision != expectedAttemptRevision ||
      occurrence.state != BirthdayJobState.RETRYABLE_ZERO ||
      occurrence.attemptNumber != 1 ||
      occurrence.revision != expectedOccurrenceRevision ||
      occurrence.resolvedWindowEndMillis <= claim.serverObservedAtMillis ||
      occurrence.resolvedWindowEndMillis <= wallNowMillis
    ) return@withTransaction false

    val permitId = deterministicUuid("RetryPermit.v1", originalPermit.permitId)
    val requestId = SmsRetryRequestIdentity.forPermit(originalPermit.permitId)
    val retryPermit = CoordinationPermitEntity(
      permitId = permitId,
      accountId = originalPermit.accountId,
      installationId = originalPermit.installationId,
      senderEpoch = originalPermit.senderEpoch,
      resetGeneration = originalPermit.resetGeneration,
      purpose = OperationPurpose.BIRTHDAY,
      operationId = originalPermit.operationId,
      attemptNumber = 2,
      payloadHash = originalPermit.payloadHash,
      opaqueClaimId = claim.claimId,
      opaqueDestinationGuardId = originalPermit.opaqueDestinationGuardId,
      claimRequestId = requestId,
      armRequestId = null,
      state = CoordinationPermitState.CLOUD_CLAIMED,
      armDispatched = false,
      armStartBlockerRevision = null,
      claimExpiresAtMillis = claim.claimExpiresAtMillis,
      maxPossibleSubmitNotAfterMillis = claim.maxPossibleSubmitNotAfterMillis,
      unresolvedArmCutoffMillis = claim.maxPossibleSubmitNotAfterMillis,
      trustedServerNowMillis = claim.serverObservedAtMillis,
      requestStartElapsedMillis = elapsedRealtimeMillis,
      bootCount = bootCount,
      serverSubmitNotAfterMillis = null,
      effectiveSubmitNotAfterMillis = null,
      noWriteReason = null,
      revision = 0,
      createdAtMillis = wallNowMillis,
      updatedAtMillis = wallNowMillis,
      barrierConsumedAtMillis = null,
      retentionUntilMillis = originalPermit.retentionUntilMillis,
    )
    check(
      dao.casBirthdayRetryAuthorized(
        occurrence.occurrenceId,
        occurrence.revision,
        claim.serverObservedAtMillis,
        wallNowMillis,
      ) == 1,
    ) { "retry-occurrence-cas-lost" }
    check(
      dao.casAttemptOutcome(
        attempt.sendAttemptId,
        SendAttemptState.RETRYABLE_ZERO,
        attempt.revision,
        SendAttemptState.TERMINAL,
        attempt.deliveryWatchdogAtMillis,
        wallNowMillis,
        "RETRY_AUTHORIZED_ATTEMPT_2_${attempt.safeOutcomeCode ?: "PROOF_RETAINED"}",
      ) == 1,
    ) { "retry-prior-attempt-close-cas-lost" }
    dao.insertRetryPermit(retryPermit)
    true
  }

  private suspend fun closeRetry(
    sendAttemptId: String,
    observedAtMillis: Long,
    safeCode: String,
    missed: Boolean,
  ): Boolean = database.withTransaction {
    val attempt = dao.attempt(sendAttemptId) ?: return@withTransaction false
    val occurrence = dao.birthday(attempt.operationId) ?: return@withTransaction false
    if (
      attempt.state != SendAttemptState.RETRYABLE_ZERO ||
      attempt.attemptNumber != 1 ||
      occurrence.state != BirthdayJobState.RETRYABLE_ZERO ||
      occurrence.attemptNumber != 1
    ) return@withTransaction false
    check(
      dao.casAttemptOutcome(
        attempt.sendAttemptId,
        attempt.state,
        attempt.revision,
        SendAttemptState.TERMINAL,
        attempt.deliveryWatchdogAtMillis,
        observedAtMillis,
        safeCode,
      ) == 1,
    ) { "retry-attempt-close-cas-lost" }
    check(
      dao.casBirthdayOutcome(
        occurrence.occurrenceId,
        occurrence.state,
        occurrence.attemptNumber,
        occurrence.revision,
        if (missed) BirthdayJobState.MISSED else BirthdayJobState.RETRY_EXHAUSTED,
        observedAtMillis,
        observedAtMillis,
        safeCode,
      ) == 1,
    ) { "retry-occurrence-close-cas-lost" }
    true
  }

  private suspend fun reportTest(
    sendAttemptId: String,
    permit: CoordinationPermitEntity,
  ): SmsOutcomeNetworkResult {
    val report = database.withTransaction {
      val attempt = dao.attempt(sendAttemptId) ?: return@withTransaction null
      val test = dao.test(attempt.operationId) ?: return@withTransaction null
      when (test.state) {
        TestJobState.PASSED -> TestReportResult.SENT_ALL_PARTS
        TestJobState.CLEANUP_CANCELLED -> TestReportResult.CLEANUP_CANCELLED
        TestJobState.FAILED -> if (
          attempt.safeOutcomeCode in setOf(
            "TEST_SENT_EVIDENCE_ALL_RADIO_OFF",
            "TEST_SENT_EVIDENCE_ALL_NO_SERVICE",
          )
        ) TestReportResult.FAILED_ZERO_ACCEPTED else TestReportResult.FAILED_OR_UNKNOWN
        TestJobState.PARTIAL_UNKNOWN,
        TestJobState.UNKNOWN,
        TestJobState.PERMANENT_FAILURE,
        -> TestReportResult.FAILED_OR_UNKNOWN
        else -> null
      }
    } ?: return result("TEST_OUTCOME_NOT_REPORTABLE", false, false)
    return when (val call = coordination.reportTest(permit, report)) {
      is SmsOutcomeCoordinationCall.Unavailable -> result(call.safeCode, true, false)
      is SmsOutcomeCoordinationCall.Authoritative -> when (val outcome = call.value) {
        is TestReportOutcome.Recorded -> settledReport(sendAttemptId, "RECORDED")
        is TestReportOutcome.Replayed -> settledReport(sendAttemptId, "REPLAYED")
        is TestReportOutcome.Refused -> settledReport(
          sendAttemptId,
          "REFUSED_${outcome.reason.name}",
        )
        is TestReportOutcome.Suppressed -> settledReport(
          sendAttemptId,
          "SUPPRESSED_${outcome.reason.name}",
        )
      }
    }
  }

  private suspend fun settledReport(
    sendAttemptId: String,
    disposition: String,
  ): SmsOutcomeNetworkResult {
    val changed = database.withTransaction {
      val attempt = dao.attempt(sendAttemptId) ?: return@withTransaction false
      if (attempt.safeOutcomeCode?.startsWith(TEST_REPORT_SETTLED_PREFIX) == true) {
        return@withTransaction false
      }
      dao.markTestReportSettled(
        sendAttemptId,
        attempt.revision,
        "$TEST_REPORT_SETTLED_PREFIX$disposition",
      ) == 1
    }
    return result("TEST_REPORT_$disposition", false, changed)
  }

  private fun deterministicUuid(domain: String, value: String): String = UUID.nameUUIDFromBytes(
    "$domain|$value".toByteArray(StandardCharsets.US_ASCII),
  ).toString()

  private fun result(code: String, retry: Boolean, changed: Boolean) =
    SmsOutcomeNetworkResult(code, retry, changed)

  private sealed interface RetryPreparation {
    data object AlreadyCommitted : RetryPreparation
    data class Ready(
      val attemptRevision: Long,
      val occurrenceRevision: Long,
      val windowEndMillis: Long,
      val proof: RetryProof,
    ) : RetryPreparation
  }

  private companion object {
    const val TEST_REPORT_SETTLED_PREFIX = "TEST_REPORT_SETTLED_"
  }
}

internal object SmsRetryAuthorizationPolicy {
  fun isValid(
    claim: ServerClaim,
    original: CoordinationPermitEntity,
    retryRequestId: String,
    proof: RetryProof,
    wallNowMillis: Long,
  ): Boolean {
    if (wallNowMillis < 0) return false
    val latestAcceptedServerNow = try {
      Math.addExact(wallNowMillis, SERVER_CLOCK_TOLERANCE_MILLIS)
    } catch (_: ArithmeticException) {
      Long.MAX_VALUE
    }
    return claim.purpose == CoordinationPurpose.BIRTHDAY &&
      claim.ownerInstallationId == original.installationId &&
      claim.ownerEpoch == original.senderEpoch &&
      claim.resetGeneration == original.resetGeneration &&
      claim.claimId == original.opaqueClaimId &&
      claim.retryRequestId == retryRequestId &&
      claim.retryProof == proof &&
      claim.state == ServerClaimState.RETRY_CLAIMED &&
      claim.attempt == 2 &&
      original.serverSubmitNotAfterMillis != null &&
      claim.serverSubmitNotAfterMillis == original.serverSubmitNotAfterMillis &&
      claim.testBarrierOutcome == null &&
      claim.serverObservedAtMillis > 0 &&
      claim.serverObservedAtMillis <= latestAcceptedServerNow &&
      claim.claimExpiresAtMillis > claim.serverObservedAtMillis &&
      claim.maxPossibleSubmitNotAfterMillis > claim.claimExpiresAtMillis
  }

  private const val SERVER_CLOCK_TOLERANCE_MILLIS = 5L * 60L * 1_000L
}
