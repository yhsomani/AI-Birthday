package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yashsomani.birthdayautopilot.automation.state.BirthdayJobState
import com.yashsomani.birthdayautopilot.automation.state.TestJobState

data class SmsCallbackEvidenceRow(
  val partIndex: Int,
  val kind: CallbackKind,
  val evidenceClass: DeliveryEvidenceClass?,
  val receivedAtMillis: Long?,
)

/** Narrow callback/outcome DAO. It never selects contact, destination, or message content. */
@Dao
abstract class SmsOutcomeDao {
  @Query("SELECT * FROM send_attempts_v2 WHERE sendAttemptId = :sendAttemptId")
  abstract suspend fun attempt(sendAttemptId: String): SendAttemptEntity?

  @Query("SELECT * FROM coordination_permits_v2 WHERE permitId = :permitId")
  abstract suspend fun permit(permitId: String): CoordinationPermitEntity?

  @Query(
    "SELECT * FROM coordination_permits_v2 " +
      "WHERE purpose = :purpose AND operationId = :operationId AND attemptNumber = :attemptNumber",
  )
  abstract suspend fun permitForAttempt(
    purpose: OperationPurpose,
    operationId: String,
    attemptNumber: Int,
  ): CoordinationPermitEntity?

  @Query("SELECT * FROM birthday_occurrences_v2 WHERE occurrenceId = :operationId")
  abstract suspend fun birthday(operationId: String): BirthdayOccurrenceRecordEntity?

  @Query("SELECT * FROM test_jobs_v2 WHERE testJobId = :operationId")
  abstract suspend fun test(operationId: String): TestJobEntity?

  @Query("SELECT * FROM test_receipts_v2 WHERE testJobId = :testJobId")
  abstract suspend fun testReceipt(testJobId: String): TestReceiptEntity?

  @Query("SELECT * FROM installation_bindings_v2 WHERE installationId = :installationId")
  abstract suspend fun installation(installationId: String): InstallationBindingEntity?

  @Query(
    "SELECT * FROM outcome_projections_v2 WHERE purpose = :purpose AND operationId = :operationId",
  )
  abstract suspend fun projection(
    purpose: OperationPurpose,
    operationId: String,
  ): OutcomeProjectionEntity?

  @Query(
    """
    SELECT token.partIndex AS partIndex,
      token.kind AS kind,
      event.evidenceClass AS evidenceClass,
      event.receivedAtMillis AS receivedAtMillis
    FROM callback_tokens_v2 token
    LEFT JOIN delivery_events_v2 event ON event.callbackTokenId = token.callbackTokenId
    WHERE token.sendAttemptId = :sendAttemptId
    ORDER BY token.kind, token.partIndex, event.receivedAtMillis, event.eventId
    """,
  )
  abstract suspend fun callbackEvidence(sendAttemptId: String): List<SmsCallbackEvidenceRow>

  @Query(
    """
    SELECT sendAttemptId FROM send_attempts_v2
    WHERE retentionUntilMillis > :nowMillis
      AND (
        state IN (
          'BARRIER_CONSUMED', 'API_CALL_STARTED', 'SUBMITTED', 'SENT_FROM_DEVICE',
          'RETRYABLE_ZERO'
        )
        OR (
          purpose = 'TEST'
          AND state IN ('PARTIAL_UNKNOWN', 'UNKNOWN', 'PERMANENT_FAILURE', 'TERMINAL')
          AND (safeOutcomeCode IS NULL OR safeOutcomeCode NOT LIKE 'TEST_REPORT_SETTLED_%')
        )
      )
    ORDER BY
      CASE
        WHEN state IN ('BARRIER_CONSUMED', 'API_CALL_STARTED', 'SUBMITTED', 'SENT_FROM_DEVICE')
          THEN 0
        WHEN state = 'RETRYABLE_ZERO' THEN 1
        ELSE 2
      END,
      CASE
        WHEN state = 'SENT_FROM_DEVICE' THEN deliveryWatchdogAtMillis
        ELSE sentWatchdogAtMillis
      END,
      sendAttemptId
    LIMIT :limit
    """,
  )
  abstract suspend fun reconstructableAttemptIds(nowMillis: Long, limit: Int): List<String>

  @Query(
    """
    SELECT sendAttemptId FROM send_attempts_v2
    WHERE retentionUntilMillis > :nowMillis
      AND (
        (state IN ('BARRIER_CONSUMED', 'API_CALL_STARTED', 'SUBMITTED')
          AND sentWatchdogAtMillis <= :nowMillis)
        OR (state = 'SENT_FROM_DEVICE'
          AND deliveryWatchdogAtMillis IS NOT NULL
          AND deliveryWatchdogAtMillis <= :nowMillis)
      )
    ORDER BY COALESCE(deliveryWatchdogAtMillis, sentWatchdogAtMillis), sendAttemptId
    LIMIT :limit
    """,
  )
  abstract suspend fun dueAttemptIds(nowMillis: Long, limit: Int): List<String>

  @Query(
    """
    SELECT * FROM callback_tokens_v2
    WHERE installationId = :installationId
      AND callbackGeneration = :callbackGeneration
      AND state IN ('EXPECTED', 'OBSERVED')
    ORDER BY callbackRequestCode
    """,
  )
  abstract suspend fun liveTokensForGeneration(
    installationId: String,
    callbackGeneration: String,
  ): List<CallbackTokenEntity>

  @Query(
    """
    SELECT * FROM callback_tokens_v2
    WHERE state IN ('EXPECTED', 'OBSERVED')
      AND expiresAtMillis <= :expiredAtMillis
    ORDER BY expiresAtMillis, callbackRequestCode
    LIMIT :limit
    """,
  )
  abstract suspend fun dueLiveTokens(
    expiredAtMillis: Long,
    limit: Int,
  ): List<CallbackTokenEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun putProjection(projection: OutcomeProjectionEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertRetryPermit(permit: CoordinationPermitEntity)

  @Query(
    """
    UPDATE send_attempts_v2
    SET state = :nextState,
        deliveryWatchdogAtMillis = :deliveryWatchdogAtMillis,
        terminalAtMillis = :terminalAtMillis,
        safeOutcomeCode = :safeOutcomeCode,
        revision = revision + 1
    WHERE sendAttemptId = :sendAttemptId
      AND state = :expectedState
      AND revision = :expectedRevision
    """,
  )
  abstract suspend fun casAttemptOutcome(
    sendAttemptId: String,
    expectedState: SendAttemptState,
    expectedRevision: Long,
    nextState: SendAttemptState,
    deliveryWatchdogAtMillis: Long?,
    terminalAtMillis: Long?,
    safeOutcomeCode: String?,
  ): Int

  @Query(
    """
    UPDATE send_attempts_v2
    SET deliveryWatchdogAtMillis = :deliveryWatchdogAtMillis,
        revision = revision + 1
    WHERE sendAttemptId = :sendAttemptId
      AND revision = :expectedRevision
      AND deliveryWatchdogAtMillis IS NULL
    """,
  )
  abstract suspend fun setDeliveryWatchdogIfAbsent(
    sendAttemptId: String,
    expectedRevision: Long,
    deliveryWatchdogAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE send_attempts_v2
    SET safeOutcomeCode = :settledCode,
        revision = revision + 1
    WHERE sendAttemptId = :sendAttemptId
      AND revision = :expectedRevision
      AND purpose = 'TEST'
      AND state IN ('SENT_FROM_DEVICE', 'PARTIAL_UNKNOWN', 'UNKNOWN', 'PERMANENT_FAILURE', 'TERMINAL')
      AND (safeOutcomeCode IS NULL OR safeOutcomeCode NOT LIKE 'TEST_REPORT_SETTLED_%')
    """,
  )
  abstract suspend fun markTestReportSettled(
    sendAttemptId: String,
    expectedRevision: Long,
    settledCode: String,
  ): Int

  @Query(
    """
    UPDATE birthday_occurrences_v2
    SET state = :nextState,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis,
        terminalAtMillis = :terminalAtMillis,
        safeOutcomeCode = :safeOutcomeCode
    WHERE occurrenceId = :occurrenceId
      AND state = :expectedState
      AND attemptNumber = :expectedAttempt
      AND revision = :expectedRevision
    """,
  )
  abstract suspend fun casBirthdayOutcome(
    occurrenceId: String,
    expectedState: BirthdayJobState,
    expectedAttempt: Int,
    expectedRevision: Long,
    nextState: BirthdayJobState,
    updatedAtMillis: Long,
    terminalAtMillis: Long?,
    safeOutcomeCode: String?,
  ): Int

  @Query(
    """
    UPDATE birthday_occurrences_v2
    SET state = 'RETRY_CLAIMED',
        attemptNumber = 2,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis,
        terminalAtMillis = NULL,
        safeOutcomeCode = NULL
    WHERE occurrenceId = :occurrenceId
      AND state = 'RETRYABLE_ZERO'
      AND attemptNumber = 1
      AND revision = :expectedRevision
      AND resolvedWindowEndMillis > :trustedServerNowMillis
      AND NOT EXISTS (
        SELECT 1 FROM coordination_permits_v2 permit
        WHERE permit.purpose = 'BIRTHDAY'
          AND permit.operationId = :occurrenceId
          AND permit.attemptNumber = 2
      )
    """,
  )
  abstract suspend fun casBirthdayRetryAuthorized(
    occurrenceId: String,
    expectedRevision: Long,
    trustedServerNowMillis: Long,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE test_jobs_v2
    SET state = :nextState,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis,
        terminalAtMillis = :terminalAtMillis,
        invalidationReason = :safeOutcomeCode
    WHERE testJobId = :testJobId
      AND state = :expectedState
      AND revision = :expectedRevision
    """,
  )
  abstract suspend fun casTestOutcome(
    testJobId: String,
    expectedState: TestJobState,
    expectedRevision: Long,
    nextState: TestJobState,
    updatedAtMillis: Long,
    terminalAtMillis: Long?,
    safeOutcomeCode: String?,
  ): Int

  @Query(
    """
    UPDATE callback_tokens_v2
    SET state = 'RETIRED', retiredAtMillis = :retiredAtMillis
    WHERE installationId = :installationId
      AND callbackGeneration = :callbackGeneration
      AND state IN ('EXPECTED', 'OBSERVED')
    """,
  )
  abstract suspend fun retireGeneration(
    installationId: String,
    callbackGeneration: String,
    retiredAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE callback_tokens_v2
    SET state = 'EXPIRED', retiredAtMillis = COALESCE(retiredAtMillis, :expiredAtMillis)
    WHERE callbackTokenId IN (:tokenIds)
      AND state IN ('EXPECTED', 'OBSERVED')
      AND expiresAtMillis <= :expiredAtMillis
    """,
  )
  abstract suspend fun expireTokens(
    tokenIds: List<String>,
    expiredAtMillis: Long,
  ): Int
}
