package com.yashsomani.birthdayautopilot.automation.sms

import androidx.room.withTransaction
import com.yashsomani.birthdayautopilot.automation.state.BirthdayJobState
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.BirthdayOccurrenceRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.DeliveryEventEntity
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import com.yashsomani.birthdayautopilot.storage.database.OutcomeProjectionEntity
import com.yashsomani.birthdayautopilot.storage.database.SendAttemptEntity
import com.yashsomani.birthdayautopilot.storage.database.SendAttemptState
import com.yashsomani.birthdayautopilot.storage.database.TestReceiptFactory
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class SmsOutcomeProcessingResult(
  val sendAttemptId: String,
  val callbackInserted: Boolean,
  val nextLocalWakeAtMillis: Long?,
  val needsNetworkFollowUp: Boolean,
  val safeCode: String,
  val attentionSafeCode: String? = null,
) {
  override fun toString(): String =
    "SmsOutcomeProcessingResult(code=$safeCode,inserted=$callbackInserted," +
      "hasWake=${nextLocalWakeAtMillis != null},network=$needsNetworkFollowUp)"
}

/**
 * Owns callback reduction and watchdog state transitions. All mutations are one Room transaction;
 * this class performs no network I/O and never loads a destination, name, or message for Birthday
 * jobs. Test receipt creation uses the already-persisted immutable TestJob only inside Room.
 */
internal class SmsOutcomeProcessor(
  private val database: BirthdayDatabase,
  private val wallClockMillis: () -> Long = System::currentTimeMillis,
) {
  private val outcomes get() = database.smsOutcomeDao()
  private val ledger get() = database.safetyLedgerDao()

  suspend fun recordCallbackAndReduce(
    sendAttemptId: String,
    event: DeliveryEventEntity,
    observedAtMillis: Long,
  ): SmsOutcomeProcessingResult = database.withTransaction {
    if (observedAtMillis != event.receivedAtMillis) {
      return@withTransaction result(sendAttemptId, false, null, false, "CALLBACK_TIME_INVALID")
    }
    val attempt = outcomes.attempt(sendAttemptId)
      ?: return@withTransaction result(sendAttemptId, false, null, false, "ATTEMPT_MISSING")
    val inserted = ledger.recordDeliveryEvent(event)
    if (!inserted) {
      return@withTransaction processAttemptLocked(attempt, observedAtMillis, false)
    }
    processAttemptLocked(attempt, observedAtMillis, true)
  }

  suspend fun processAttempt(
    sendAttemptId: String,
    observedAtMillis: Long,
  ): SmsOutcomeProcessingResult = database.withTransaction {
    val attempt = outcomes.attempt(sendAttemptId)
      ?: return@withTransaction result(sendAttemptId, false, null, false, "ATTEMPT_MISSING")
    processAttemptLocked(attempt, observedAtMillis, false)
  }

  suspend fun processDue(
    observedAtMillis: Long,
    limit: Int = MAX_RECONSTRUCTION_BATCH,
  ): List<SmsOutcomeProcessingResult> {
    require(limit in 1..MAX_RECONSTRUCTION_BATCH) { "outcome-batch-invalid" }
    val ids = database.withTransaction { outcomes.dueAttemptIds(observedAtMillis, limit) }
    return ids.map { processAttempt(it, observedAtMillis) }
  }

  suspend fun reconstruct(
    observedAtMillis: Long,
    limit: Int = MAX_RECONSTRUCTION_BATCH,
  ): List<SmsOutcomeProcessingResult> {
    require(limit in 1..MAX_RECONSTRUCTION_BATCH) { "outcome-batch-invalid" }
    val ids = database.withTransaction {
      outcomes.reconstructableAttemptIds(observedAtMillis, limit)
    }
    return ids.map { processAttempt(it, observedAtMillis) }
  }

  private suspend fun processAttemptLocked(
    originalAttempt: SendAttemptEntity,
    observedAtMillis: Long,
    callbackInserted: Boolean,
  ): SmsOutcomeProcessingResult {
    require(observedAtMillis >= 0) { "outcome-observation-time-invalid" }
    var attempt = outcomes.attempt(originalAttempt.sendAttemptId) ?: originalAttempt
    val permit = outcomes.permit(attempt.permitId)
      ?: return result(attempt.sendAttemptId, callbackInserted, null, false, "PERMIT_MISSING")
    if (
      permit.operationId != attempt.operationId ||
      permit.purpose != attempt.purpose ||
      permit.attemptNumber != attempt.attemptNumber ||
      permit.installationId != attempt.installationId ||
      permit.payloadHash != attempt.payloadHash
    ) return result(attempt.sendAttemptId, callbackInserted, null, false, "ATTEMPT_BINDING_INVALID")

    val evidence = outcomes.callbackEvidence(attempt.sendAttemptId).mapNotNull { row ->
      val evidenceClass = row.evidenceClass ?: return@mapNotNull null
      val receivedAtMillis = row.receivedAtMillis ?: return@mapNotNull null
      SmsPartEvidence(row.partIndex, row.kind, evidenceClass, receivedAtMillis)
    }
    var decision = SmsOutcomeReducer.reduce(
      expectedPartCount = attempt.expectedPartCount,
      sentDeadlineMillis = attempt.sentWatchdogAtMillis,
      deliveryDeadlineMillis = attempt.deliveryWatchdogAtMillis,
      observedAtMillis = observedAtMillis,
      evidence = evidence,
    )

    when (attempt.purpose) {
      OperationPurpose.BIRTHDAY -> processBirthday(attempt, decision, observedAtMillis)
      OperationPurpose.TEST -> processTest(attempt, decision, observedAtMillis)
    }

    attempt = outcomes.attempt(attempt.sendAttemptId) ?: attempt
    val derivedDeliveryDeadline = attempt.deliveryWatchdogAtMillis ?: decision
      .latestSuccessfulSentAtMillis
      ?.let { safeAdd(it, DELIVERY_WATCHDOG_MILLIS) }
    if (
      derivedDeliveryDeadline != null &&
      attempt.deliveryWatchdogAtMillis == null &&
      attempt.state in setOf(SendAttemptState.SENT_FROM_DEVICE, SendAttemptState.TERMINAL)
    ) {
      outcomes.setDeliveryWatchdogIfAbsent(
        attempt.sendAttemptId,
        attempt.revision,
        derivedDeliveryDeadline,
      )
      attempt = outcomes.attempt(attempt.sendAttemptId) ?: attempt
    }

    decision = SmsOutcomeReducer.reduce(
      expectedPartCount = attempt.expectedPartCount,
      sentDeadlineMillis = attempt.sentWatchdogAtMillis,
      deliveryDeadlineMillis = attempt.deliveryWatchdogAtMillis ?: derivedDeliveryDeadline,
      observedAtMillis = observedAtMillis,
      evidence = evidence,
    )
    if (attempt.purpose == OperationPurpose.BIRTHDAY) {
      processBirthdayDelivery(attempt, decision, observedAtMillis)
      attempt = outcomes.attempt(attempt.sendAttemptId) ?: attempt
    }

    val immutableState = immutableState(attempt)
    val candidate = visibleOutcome(attempt, decision, observedAtMillis)
    val existing = outcomes.projection(attempt.purpose, attempt.operationId)
    val visible = SmsOutcomeProjectionPolicy.refine(existing?.visibleOutcome, candidate)
    val completeness = evidenceCompleteness(decision)
    if (
      existing == null ||
      existing.immutableSafetyState != immutableState ||
      existing.visibleOutcome != visible ||
      existing.evidenceCompleteness != completeness ||
      existing.sentEvidenceDeadlineMillis != attempt.sentWatchdogAtMillis ||
      existing.deliveryEvidenceDeadlineMillis != attempt.deliveryWatchdogAtMillis
    ) {
      outcomes.putProjection(
        OutcomeProjectionEntity(
          purpose = attempt.purpose,
          operationId = attempt.operationId,
          accountId = permit.accountId,
          immutableSafetyState = immutableState,
          visibleOutcome = visible,
          evidenceCompleteness = completeness,
          sentEvidenceDeadlineMillis = attempt.sentWatchdogAtMillis,
          deliveryEvidenceDeadlineMillis = attempt.deliveryWatchdogAtMillis,
          refinedAtMillis = observedAtMillis,
          revision = (existing?.revision ?: -1L) + 1L,
        ),
      )
    }

    val nextWake = when {
      attempt.state in SENT_PENDING_STATES && observedAtMillis < attempt.sentWatchdogAtMillis ->
        attempt.sentWatchdogAtMillis
      attempt.state == SendAttemptState.SENT_FROM_DEVICE &&
        attempt.deliveryWatchdogAtMillis != null &&
        observedAtMillis < attempt.deliveryWatchdogAtMillis -> attempt.deliveryWatchdogAtMillis
      else -> null
    }
    val network = when (attempt.purpose) {
      OperationPurpose.BIRTHDAY ->
        attempt.state == SendAttemptState.RETRYABLE_ZERO &&
          outcomes.birthday(attempt.operationId)?.state == BirthdayJobState.RETRYABLE_ZERO
      OperationPurpose.TEST ->
        outcomes.test(attempt.operationId)?.state in TEST_REPORTABLE_STATES &&
          attempt.safeOutcomeCode?.startsWith(TEST_REPORT_SETTLED_PREFIX) != true
    }
    return result(
      attempt.sendAttemptId,
      callbackInserted,
      nextWake,
      network,
      "OUTCOME_REDUCED",
      SmsOutcomeAttentionPolicy.safeCode(visible),
    )
  }

  private suspend fun processBirthday(
    attempt: SendAttemptEntity,
    decision: SmsOutcomeDecision,
    observedAtMillis: Long,
  ) {
    val occurrence = outcomes.birthday(attempt.operationId)
      ?: throw IllegalStateException("birthday-operation-missing")
    check(
      occurrence.attemptNumber == attempt.attemptNumber &&
        occurrence.payloadHash == attempt.payloadHash,
    ) { "birthday-attempt-binding-invalid" }
    if (
      attempt.state != SendAttemptState.SUBMITTED ||
      occurrence.state != BirthdayJobState.SUBMITTED
    ) {
      return
    }

    when (decision.timelySent) {
      SentEvidenceDecision.ALL_SENT -> {
        val deliveryDeadline = decision.latestSuccessfulSentAtMillis
          ?.let { safeAdd(it, DELIVERY_WATCHDOG_MILLIS) }
          ?: throw IllegalStateException("sent-evidence-time-missing")
        transitionBirthday(
          attempt,
          occurrence,
          SendAttemptState.SENT_FROM_DEVICE,
          BirthdayJobState.SENT_FROM_DEVICE,
          deliveryDeadline,
          null,
          null,
          observedAtMillis,
        )
      }
      SentEvidenceDecision.ALL_RADIO_OFF,
      SentEvidenceDecision.ALL_NO_SERVICE,
      -> {
        val proof = if (decision.timelySent == SentEvidenceDecision.ALL_RADIO_OFF) {
          "ALL_PARTS_RADIO_OFF"
        } else {
          "ALL_PARTS_NO_SERVICE"
        }
        if (
          attempt.attemptNumber == 1 &&
          occurrence.attemptNumber == 1 &&
          observedAtMillis < occurrence.resolvedWindowEndMillis
        ) {
          transitionBirthday(
            attempt,
            occurrence,
            SendAttemptState.RETRYABLE_ZERO,
            BirthdayJobState.RETRYABLE_ZERO,
            null,
            observedAtMillis,
            proof,
            observedAtMillis,
          )
        } else {
          transitionBirthday(
            attempt,
            occurrence,
            SendAttemptState.TERMINAL,
            BirthdayJobState.RETRY_EXHAUSTED,
            null,
            observedAtMillis,
            "RETRY_EXHAUSTED_$proof",
            observedAtMillis,
          )
        }
      }
      SentEvidenceDecision.COMPLETE_FAILURE -> transitionBirthday(
        attempt,
        occurrence,
        SendAttemptState.PERMANENT_FAILURE,
        BirthdayJobState.PERMANENT_FAILURE,
        null,
        observedAtMillis,
        "SENT_CALLBACK_FAILURE",
        observedAtMillis,
      )
      SentEvidenceDecision.PARTIAL_UNKNOWN -> if (observedAtMillis >= attempt.sentWatchdogAtMillis) {
        transitionBirthday(
          attempt,
          occurrence,
          SendAttemptState.PARTIAL_UNKNOWN,
          BirthdayJobState.PARTIAL_DELIVERY_UNKNOWN,
          null,
          observedAtMillis,
          "SENT_CALLBACK_PARTIAL_UNKNOWN",
          observedAtMillis,
        )
      }
      SentEvidenceDecision.SUBMISSION_UNKNOWN -> if (
        observedAtMillis >= attempt.sentWatchdogAtMillis
      ) {
        transitionBirthday(
          attempt,
          occurrence,
          SendAttemptState.UNKNOWN,
          BirthdayJobState.SUBMISSION_UNKNOWN,
          null,
          observedAtMillis,
          "SENT_CALLBACK_MISSING",
          observedAtMillis,
        )
      }
      SentEvidenceDecision.WAITING -> Unit
    }
  }

  private suspend fun processBirthdayDelivery(
    attempt: SendAttemptEntity,
    decision: SmsOutcomeDecision,
    observedAtMillis: Long,
  ) {
    if (attempt.state != SendAttemptState.SENT_FROM_DEVICE) return
    val occurrence = outcomes.birthday(attempt.operationId)
      ?: throw IllegalStateException("birthday-operation-missing")
    if (occurrence.state != BirthdayJobState.SENT_FROM_DEVICE) return
    val next = when (decision.delivery) {
      DeliveryEvidenceDecision.DELIVERED -> BirthdayJobState.DELIVERED
      DeliveryEvidenceDecision.DELIVERY_FAILED -> BirthdayJobState.DELIVERY_FAILED
      DeliveryEvidenceDecision.PARTIAL_DELIVERY -> BirthdayJobState.PARTIAL_DELIVERY
      DeliveryEvidenceDecision.PARTIAL_DELIVERY_UNKNOWN ->
        BirthdayJobState.PARTIAL_DELIVERY_UNKNOWN
      DeliveryEvidenceDecision.DELIVERY_UNKNOWN -> BirthdayJobState.DELIVERY_UNKNOWN
      DeliveryEvidenceDecision.NOT_APPLICABLE,
      DeliveryEvidenceDecision.WAITING,
      -> return
    }
    transitionBirthday(
      attempt,
      occurrence,
      SendAttemptState.TERMINAL,
      next,
      attempt.deliveryWatchdogAtMillis,
      observedAtMillis,
      next.name,
      observedAtMillis,
    )
  }

  private suspend fun processTest(
    attempt: SendAttemptEntity,
    decision: SmsOutcomeDecision,
    observedAtMillis: Long,
  ) {
    val test = outcomes.test(attempt.operationId)
      ?: throw IllegalStateException("test-operation-missing")
    check(
      test.installationId == attempt.installationId &&
        test.senderEpoch > 0 &&
        test.payloadHash == attempt.payloadHash,
    ) { "test-attempt-binding-invalid" }
    if (attempt.state !in SENT_PENDING_STATES || test.state != TestJobState.SUBMITTED) return

    val transactionNowMillis = wallClockMillis()
    if (
      decision.timelySent == SentEvidenceDecision.ALL_SENT &&
      observedAtMillis < attempt.sentWatchdogAtMillis &&
      transactionNowMillis < attempt.sentWatchdogAtMillis
    ) {
      val installation = outcomes.installation(test.installationId)
        ?: throw IllegalStateException("test-installation-missing")
      val receiptId = UUID.nameUUIDFromBytes(
        "BirthdayAutopilot.TestReceipt.v1|${attempt.sendAttemptId}"
          .toByteArray(StandardCharsets.US_ASCII),
      ).toString()
      val receipt = TestReceiptFactory.create(
        test = test,
        installation = installation,
        testReceiptId = receiptId,
        smsPolicyVersion = AndroidSmsPolicyBinding.VERSION,
        passedAtMillis = observedAtMillis,
      )
      check(
        ledger.mintPassingTestReceipt(
          test.testJobId,
          test.revision,
          receipt,
          observedAtMillis,
        ),
      ) { "test-receipt-mint-rejected" }
      if (wallClockMillis() >= attempt.sentWatchdogAtMillis) {
        // Throwing rolls the entire callback/evidence/receipt transaction back. This conservative
        // edge sacrifices the TEST rather than letting a deadline-crossing commit enable sending.
        throw TestReceiptDeadlineCrossedException()
      }
      return
    }

    val transition = when (decision.timelySent) {
      SentEvidenceDecision.ALL_RADIO_OFF,
      SentEvidenceDecision.ALL_NO_SERVICE,
      SentEvidenceDecision.COMPLETE_FAILURE,
      -> SendAttemptState.PERMANENT_FAILURE to TestJobState.FAILED
      SentEvidenceDecision.PARTIAL_UNKNOWN -> if (
        observedAtMillis >= attempt.sentWatchdogAtMillis
      ) SendAttemptState.PARTIAL_UNKNOWN to TestJobState.PARTIAL_UNKNOWN else null
      SentEvidenceDecision.SUBMISSION_UNKNOWN -> if (
        observedAtMillis >= attempt.sentWatchdogAtMillis
      ) SendAttemptState.UNKNOWN to TestJobState.UNKNOWN else null
      SentEvidenceDecision.ALL_SENT -> if (
        observedAtMillis >= attempt.sentWatchdogAtMillis ||
        transactionNowMillis >= attempt.sentWatchdogAtMillis
      ) SendAttemptState.UNKNOWN to TestJobState.UNKNOWN else null
      SentEvidenceDecision.WAITING -> null
    } ?: return
    check(
      outcomes.casAttemptOutcome(
        attempt.sendAttemptId,
        attempt.state,
        attempt.revision,
        transition.first,
        null,
        observedAtMillis,
        "TEST_SENT_EVIDENCE_${decision.timelySent.name}",
      ) == 1,
    ) { "test-attempt-outcome-cas-lost" }
    check(
      outcomes.casTestOutcome(
        test.testJobId,
        test.state,
        test.revision,
        transition.second,
        observedAtMillis,
        observedAtMillis,
        "TEST_SENT_EVIDENCE_${decision.timelySent.name}",
      ) == 1,
    ) { "test-outcome-cas-lost" }
  }

  private class TestReceiptDeadlineCrossedException : IllegalStateException(
    "test-receipt-deadline-crossed",
  )

  private suspend fun transitionBirthday(
    attempt: SendAttemptEntity,
    occurrence: BirthdayOccurrenceRecordEntity,
    nextAttemptState: SendAttemptState,
    nextBirthdayState: BirthdayJobState,
    deliveryWatchdogAtMillis: Long?,
    terminalAtMillis: Long?,
    safeOutcomeCode: String?,
    observedAtMillis: Long,
  ) {
    check(
      outcomes.casAttemptOutcome(
        attempt.sendAttemptId,
        attempt.state,
        attempt.revision,
        nextAttemptState,
        deliveryWatchdogAtMillis,
        terminalAtMillis,
        safeOutcomeCode,
      ) == 1,
    ) { "birthday-attempt-outcome-cas-lost" }
    check(
      outcomes.casBirthdayOutcome(
        occurrence.occurrenceId,
        occurrence.state,
        occurrence.attemptNumber,
        occurrence.revision,
        nextBirthdayState,
        observedAtMillis,
        terminalAtMillis,
        safeOutcomeCode,
      ) == 1,
    ) { "birthday-outcome-cas-lost" }
  }

  private suspend fun immutableState(attempt: SendAttemptEntity): String = when (attempt.purpose) {
    OperationPurpose.BIRTHDAY -> outcomes.birthday(attempt.operationId)?.state?.name
      ?: "BIRTHDAY_STATE_UNAVAILABLE"
    OperationPurpose.TEST -> outcomes.test(attempt.operationId)?.state?.name
      ?: "TEST_STATE_UNAVAILABLE"
  }

  private suspend fun visibleOutcome(
    attempt: SendAttemptEntity,
    decision: SmsOutcomeDecision,
    observedAtMillis: Long,
  ): String {
    val immutable = immutableState(attempt)
    val sentLate = immutable !in setOf(
      BirthdayJobState.SENT_FROM_DEVICE.name,
      BirthdayJobState.DELIVERED.name,
      BirthdayJobState.DELIVERY_FAILED.name,
      BirthdayJobState.PARTIAL_DELIVERY.name,
      TestJobState.PASSED.name,
    )
    val deliveryLate = sentLate ||
      immutable in setOf(
        BirthdayJobState.PARTIAL_DELIVERY_UNKNOWN.name,
        BirthdayJobState.DELIVERY_UNKNOWN.name,
      ) ||
      (attempt.deliveryWatchdogAtMillis?.let { observedAtMillis >= it } == true)
    when (decision.delivery) {
      DeliveryEvidenceDecision.DELIVERED -> return if (deliveryLate) {
        SmsVisibleOutcome.DELIVERED_LATE
      } else {
        SmsVisibleOutcome.DELIVERED
      }
      DeliveryEvidenceDecision.DELIVERY_FAILED -> return if (deliveryLate) {
        SmsVisibleOutcome.DELIVERY_FAILED_LATE
      } else {
        SmsVisibleOutcome.DELIVERY_FAILED
      }
      DeliveryEvidenceDecision.PARTIAL_DELIVERY -> return if (deliveryLate) {
        SmsVisibleOutcome.PARTIAL_DELIVERY_LATE
      } else {
        SmsVisibleOutcome.PARTIAL_DELIVERY
      }
      DeliveryEvidenceDecision.PARTIAL_DELIVERY_UNKNOWN ->
        return SmsVisibleOutcome.PARTIAL_DELIVERY_UNKNOWN
      DeliveryEvidenceDecision.DELIVERY_UNKNOWN -> return SmsVisibleOutcome.DELIVERY_UNKNOWN
      DeliveryEvidenceDecision.NOT_APPLICABLE,
      DeliveryEvidenceDecision.WAITING,
      -> Unit
    }
    if (attempt.purpose == OperationPurpose.TEST && immutable == TestJobState.PASSED.name) {
      return SmsVisibleOutcome.TEST_PASSED
    }
    return when (decision.visibleSent) {
      SentEvidenceDecision.ALL_SENT -> if (sentLate) {
        SmsVisibleOutcome.SENT_EVIDENCE_LATE
      } else if (attempt.attemptNumber == 2) {
        SmsVisibleOutcome.RETRY_SENT_FROM_DEVICE
      } else {
        SmsVisibleOutcome.SENT_FROM_DEVICE
      }
      SentEvidenceDecision.ALL_RADIO_OFF -> if (attempt.attemptNumber == 2) {
        SmsVisibleOutcome.RETRY_EXHAUSTED
      } else if (attempt.state == SendAttemptState.RETRYABLE_ZERO) {
        SmsVisibleOutcome.ZERO_ACCEPTED_RADIO_OFF
      } else {
        SmsVisibleOutcome.ZERO_ACCEPTED_LATE
      }
      SentEvidenceDecision.ALL_NO_SERVICE -> if (attempt.attemptNumber == 2) {
        SmsVisibleOutcome.RETRY_EXHAUSTED
      } else if (attempt.state == SendAttemptState.RETRYABLE_ZERO) {
        SmsVisibleOutcome.ZERO_ACCEPTED_NO_SERVICE
      } else {
        SmsVisibleOutcome.ZERO_ACCEPTED_LATE
      }
      SentEvidenceDecision.COMPLETE_FAILURE -> if (attempt.purpose == OperationPurpose.TEST) {
        SmsVisibleOutcome.TEST_FAILED
      } else {
        SmsVisibleOutcome.PERMANENT_FAILURE
      }
      SentEvidenceDecision.PARTIAL_UNKNOWN -> SmsVisibleOutcome.PARTIAL_UNKNOWN
      SentEvidenceDecision.SUBMISSION_UNKNOWN -> SmsVisibleOutcome.SUBMISSION_UNKNOWN
      SentEvidenceDecision.WAITING -> when (immutable) {
        BirthdayJobState.RETRY_EXHAUSTED.name -> SmsVisibleOutcome.RETRY_EXHAUSTED
        BirthdayJobState.PERMANENT_FAILURE.name -> SmsVisibleOutcome.PERMANENT_FAILURE
        TestJobState.FAILED.name,
        TestJobState.PARTIAL_UNKNOWN.name,
        TestJobState.UNKNOWN.name,
        -> SmsVisibleOutcome.TEST_FAILED
        else -> if (attempt.attemptNumber == 2) {
          SmsVisibleOutcome.RETRY_SUBMITTED
        } else {
          SmsVisibleOutcome.SUBMITTED
        }
      }
    }
  }

  private fun evidenceCompleteness(decision: SmsOutcomeDecision): String = when {
    decision.delivery != DeliveryEvidenceDecision.NOT_APPLICABLE ->
      "DELIVERY_${decision.deliveryCompleteness.name}"
    else -> "SENT_${decision.sentCompleteness.name}"
  }

  private fun result(
    attemptId: String,
    inserted: Boolean,
    wake: Long?,
    network: Boolean,
    code: String,
    attentionSafeCode: String? = null,
  ) = SmsOutcomeProcessingResult(attemptId, inserted, wake, network, code, attentionSafeCode)

  private fun safeAdd(value: Long, delta: Long): Long? = try {
    Math.addExact(value, delta)
  } catch (_: ArithmeticException) {
    null
  }

  private companion object {
    const val DELIVERY_WATCHDOG_MILLIS = 72L * 60L * 60L * 1_000L
    const val MAX_RECONSTRUCTION_BATCH = 64
    val SENT_PENDING_STATES = setOf(
      SendAttemptState.BARRIER_CONSUMED,
      SendAttemptState.API_CALL_STARTED,
      SendAttemptState.SUBMITTED,
    )
    val TEST_REPORTABLE_STATES = setOf(
      TestJobState.PASSED,
      TestJobState.FAILED,
      TestJobState.PARTIAL_UNKNOWN,
      TestJobState.UNKNOWN,
      TestJobState.CLEANUP_CANCELLED,
    )
    const val TEST_REPORT_SETTLED_PREFIX = "TEST_REPORT_SETTLED_"
  }
}
