package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

internal data class RetentionBatchResult(
  val deletedActivityRows: Int,
  val deletedDeliveryEvents: Int,
  val deletedCallbackTokens: Int,
  val redactedTestJobs: Int,
  val deletedTestAttempts: Int,
  val deletedTestPermits: Int,
  val deletedTestReceipts: Int,
  val deletedTestProjections: Int,
  val deletedTestJobs: Int,
  val moreWork: Boolean,
)

/**
 * Privacy-retention DAO. Every destructive query is deliberately narrow:
 *
 * - callback evidence is removed only after platform identities were retired/expired;
 * - TEST graphs are removed only after their terminal 30-day boundary;
 * - a valid TestReceipt keeps its minimum TestJob/permit binding; and
 * - Birthday occurrences, destination guards, permits, and attempts are never selected here.
 */
@Dao
internal abstract class RetentionDao {
  @Query("SELECT MAX(greatestTrustedServerMillis) FROM clock_trust_v2")
  abstract suspend fun greatestTrustedServerMillis(): Long?

  @Query(
    "SELECT activityId FROM activity WHERE recordedAtMillis < :cutoffMillis " +
      "ORDER BY recordedAtMillis, activityId LIMIT :limit",
  )
  protected abstract suspend fun expiredActivityIds(
    cutoffMillis: Long,
    limit: Int,
  ): List<String>

  @Query(
    "DELETE FROM activity WHERE activityId IN (:activityIds) " +
      "AND recordedAtMillis < :cutoffMillis",
  )
  protected abstract suspend fun deleteExpiredActivityRows(
    activityIds: List<String>,
    cutoffMillis: Long,
  ): Int

  @Query(
    """
    SELECT event.eventId FROM delivery_events_v2 event
    JOIN callback_tokens_v2 token ON token.callbackTokenId = event.callbackTokenId
    WHERE token.state IN ('RETIRED', 'EXPIRED')
      AND token.expiresAtMillis <= :nowMillis
    ORDER BY token.expiresAtMillis, event.receivedAtMillis, event.eventId
    LIMIT :limit
    """,
  )
  protected abstract suspend fun expiredDeliveryEventIds(
    nowMillis: Long,
    limit: Int,
  ): List<String>

  @Query("DELETE FROM delivery_events_v2 WHERE eventId IN (:eventIds)")
  protected abstract suspend fun deleteDeliveryEvents(eventIds: List<String>): Int

  @Query(
    """
    SELECT token.callbackTokenId FROM callback_tokens_v2 token
    WHERE token.state IN ('RETIRED', 'EXPIRED')
      AND token.expiresAtMillis <= :nowMillis
      AND NOT EXISTS (
        SELECT 1 FROM delivery_events_v2 event
        WHERE event.callbackTokenId = token.callbackTokenId
      )
    ORDER BY token.expiresAtMillis, token.callbackTokenId
    LIMIT :limit
    """,
  )
  protected abstract suspend fun expiredCallbackTokenIds(
    nowMillis: Long,
    limit: Int,
  ): List<String>

  @Query(
    "DELETE FROM callback_tokens_v2 WHERE callbackTokenId IN (:callbackTokenIds) " +
      "AND state IN ('RETIRED', 'EXPIRED') AND expiresAtMillis <= :nowMillis",
  )
  protected abstract suspend fun deleteExpiredCallbackTokens(
    callbackTokenIds: List<String>,
    nowMillis: Long,
  ): Int

  @Query(
    """
    SELECT testJobId FROM test_jobs_v2
    WHERE retentionUntilMillis <= :nowMillis
      AND state IN (
        'COORDINATION_UNKNOWN', 'ARMED_SUPPRESSED', 'PASSED', 'FAILED',
        'PARTIAL_UNKNOWN', 'UNKNOWN', 'PERMANENT_FAILURE', 'CLEANUP_CANCELLED',
        'RECEIPT_INVALIDATED'
      )
      AND (
        normalizedDestination != ''
        OR maskedDestination != ''
        OR exactMessage != ''
        OR foregroundConfirmationNonceHash != ''
        OR foregroundConfirmedAtMillis != 0
      )
    ORDER BY retentionUntilMillis, testJobId
    LIMIT :limit
    """,
  )
  protected abstract suspend fun testJobsWithExpiredSensitiveDetail(
    nowMillis: Long,
    limit: Int,
  ): List<String>

  @Query(
    """
    UPDATE test_jobs_v2
    SET normalizedDestination = '',
        maskedDestination = '',
        exactMessage = '',
        foregroundConfirmationNonceHash = '',
        foregroundConfirmedAtMillis = 0,
        revision = CASE
          WHEN revision < 9223372036854775807 THEN revision + 1
          ELSE revision
        END
    WHERE testJobId IN (:testJobIds)
      AND retentionUntilMillis <= :nowMillis
    """,
  )
  protected abstract suspend fun redactExpiredTestJobDetail(
    testJobIds: List<String>,
    nowMillis: Long,
  ): Int

  @Query(
    """
    SELECT testJobId FROM test_jobs_v2
    WHERE retentionUntilMillis <= :nowMillis
      AND state IN (
        'COORDINATION_UNKNOWN', 'ARMED_SUPPRESSED', 'PASSED', 'FAILED',
        'PARTIAL_UNKNOWN', 'UNKNOWN', 'PERMANENT_FAILURE', 'CLEANUP_CANCELLED',
        'RECEIPT_INVALIDATED'
      )
      AND EXISTS (
        SELECT 1 FROM coordination_permits_v2 permit
        JOIN send_attempts_v2 attempt ON attempt.permitId = permit.permitId
        WHERE permit.purpose = 'TEST' AND permit.operationId = test_jobs_v2.testJobId
      )
    ORDER BY retentionUntilMillis, testJobId
    LIMIT :limit
    """,
  )
  protected abstract suspend fun expiredTerminalTestGraphIds(
    nowMillis: Long,
    limit: Int,
  ): List<String>

  @Query(
    """
    SELECT job.testJobId FROM test_jobs_v2 job
    WHERE job.retentionUntilMillis <= :nowMillis
      AND job.state IN (
        'COORDINATION_UNKNOWN', 'ARMED_SUPPRESSED', 'PASSED', 'FAILED',
        'PARTIAL_UNKNOWN', 'UNKNOWN', 'PERMANENT_FAILURE', 'CLEANUP_CANCELLED',
        'RECEIPT_INVALIDATED'
      )
      AND NOT EXISTS (
        SELECT 1 FROM test_receipts_v2 receipt
        WHERE receipt.testJobId = job.testJobId
          AND receipt.state = 'VALID'
          AND receipt.invalidatedAtMillis IS NULL
          AND receipt.invalidationReason IS NULL
      )
    ORDER BY job.retentionUntilMillis, job.testJobId
    LIMIT :limit
    """,
  )
  protected abstract suspend fun obsoleteTerminalTestJobIds(
    nowMillis: Long,
    limit: Int,
  ): List<String>

  @Query(
    """
    DELETE FROM send_attempts_v2
    WHERE sendAttemptId IN (
      SELECT attempt.sendAttemptId
      FROM send_attempts_v2 attempt
      JOIN coordination_permits_v2 permit ON permit.permitId = attempt.permitId
      WHERE permit.purpose = 'TEST'
        AND permit.operationId IN (:testJobIds)
        AND attempt.retentionUntilMillis <= :nowMillis
        AND NOT EXISTS (
          SELECT 1 FROM callback_tokens_v2 token
          WHERE token.sendAttemptId = attempt.sendAttemptId
        )
    )
    """,
  )
  protected abstract suspend fun deleteObsoleteTestAttempts(
    testJobIds: List<String>,
    nowMillis: Long,
  ): Int

  @Query(
    """
    DELETE FROM coordination_permits_v2
    WHERE purpose = 'TEST'
      AND operationId IN (:testJobIds)
      AND retentionUntilMillis <= :nowMillis
      AND NOT EXISTS (
        SELECT 1 FROM send_attempts_v2 attempt
        WHERE attempt.permitId = coordination_permits_v2.permitId
      )
      AND NOT EXISTS (
        SELECT 1 FROM test_receipts_v2 receipt
        WHERE receipt.testJobId = coordination_permits_v2.operationId
          AND receipt.state = 'VALID'
          AND receipt.invalidatedAtMillis IS NULL
          AND receipt.invalidationReason IS NULL
      )
    """,
  )
  protected abstract suspend fun deleteObsoleteTestPermits(
    testJobIds: List<String>,
    nowMillis: Long,
  ): Int

  @Query(
    """
    DELETE FROM test_receipts_v2
    WHERE testJobId IN (:testJobIds)
      AND NOT (
        state = 'VALID'
        AND invalidatedAtMillis IS NULL
        AND invalidationReason IS NULL
      )
    """,
  )
  protected abstract suspend fun deleteObsoleteTestReceipts(testJobIds: List<String>): Int

  @Query(
    "DELETE FROM outcome_projections_v2 " +
      "WHERE purpose = 'TEST' AND operationId IN (:testJobIds)",
  )
  protected abstract suspend fun deleteObsoleteTestProjections(testJobIds: List<String>): Int

  @Query(
    """
    DELETE FROM test_jobs_v2
    WHERE testJobId IN (:testJobIds)
      AND retentionUntilMillis <= :nowMillis
      AND state IN (
        'COORDINATION_UNKNOWN', 'ARMED_SUPPRESSED', 'PASSED', 'FAILED',
        'PARTIAL_UNKNOWN', 'UNKNOWN', 'PERMANENT_FAILURE', 'CLEANUP_CANCELLED',
        'RECEIPT_INVALIDATED'
      )
      AND NOT EXISTS (
        SELECT 1 FROM coordination_permits_v2 permit
        WHERE permit.purpose = 'TEST' AND permit.operationId = test_jobs_v2.testJobId
      )
      AND NOT EXISTS (
        SELECT 1 FROM test_receipts_v2 receipt
        WHERE receipt.testJobId = test_jobs_v2.testJobId
          AND receipt.state = 'VALID'
          AND receipt.invalidatedAtMillis IS NULL
          AND receipt.invalidationReason IS NULL
      )
    """,
  )
  protected abstract suspend fun deleteObsoleteTestJobs(
    testJobIds: List<String>,
    nowMillis: Long,
  ): Int

  @Transaction
  open suspend fun pruneBatch(
    nowMillis: Long,
    activityCutoffMillis: Long,
    limit: Int,
  ): RetentionBatchResult {
    require(nowMillis >= 0) { "retention-now-invalid" }
    require(activityCutoffMillis in 0..nowMillis) { "retention-cutoff-invalid" }
    require(limit in 1..MAX_BATCH_SIZE) { "retention-limit-invalid" }

    val activityIds = expiredActivityIds(activityCutoffMillis, limit)
    val deletedActivity = activityIds.takeIf(List<String>::isNotEmpty)?.let {
      deleteExpiredActivityRows(it, activityCutoffMillis)
    } ?: 0

    val eventIds = expiredDeliveryEventIds(nowMillis, limit)
    val deletedEvents = eventIds.takeIf(List<String>::isNotEmpty)?.let {
      deleteDeliveryEvents(it)
    } ?: 0
    val callbackIds = expiredCallbackTokenIds(nowMillis, limit)
    val deletedCallbacks = callbackIds.takeIf(List<String>::isNotEmpty)?.let {
      deleteExpiredCallbackTokens(it, nowMillis)
    } ?: 0

    val redactionIds = testJobsWithExpiredSensitiveDetail(nowMillis, limit)
    val redactedTests = redactionIds.takeIf(List<String>::isNotEmpty)?.let {
      redactExpiredTestJobDetail(it, nowMillis)
    } ?: 0

    val expiredGraphIds = expiredTerminalTestGraphIds(nowMillis, limit)
    val deletedAttempts = expiredGraphIds.takeIf(List<String>::isNotEmpty)?.let {
      deleteObsoleteTestAttempts(it, nowMillis)
    } ?: 0
    val obsoleteTestIds = obsoleteTerminalTestJobIds(nowMillis, limit)
    val deletedPermits = obsoleteTestIds.takeIf(List<String>::isNotEmpty)?.let {
      deleteObsoleteTestPermits(it, nowMillis)
    } ?: 0
    val deletedReceipts = obsoleteTestIds.takeIf(List<String>::isNotEmpty)?.let {
      deleteObsoleteTestReceipts(it)
    } ?: 0
    val deletedProjections = obsoleteTestIds.takeIf(List<String>::isNotEmpty)?.let {
      deleteObsoleteTestProjections(it)
    } ?: 0
    val deletedTests = obsoleteTestIds.takeIf(List<String>::isNotEmpty)?.let {
      deleteObsoleteTestJobs(it, nowMillis)
    } ?: 0

    return RetentionBatchResult(
      deletedActivityRows = deletedActivity,
      deletedDeliveryEvents = deletedEvents,
      deletedCallbackTokens = deletedCallbacks,
      redactedTestJobs = redactedTests,
      deletedTestAttempts = deletedAttempts,
      deletedTestPermits = deletedPermits,
      deletedTestReceipts = deletedReceipts,
      deletedTestProjections = deletedProjections,
      deletedTestJobs = deletedTests,
      moreWork = activityIds.size == limit ||
        eventIds.size == limit ||
        callbackIds.size == limit ||
        redactionIds.size == limit ||
        expiredGraphIds.size == limit ||
        obsoleteTestIds.size == limit ||
        deletedTests < obsoleteTestIds.size,
    )
  }

  private companion object {
    const val MAX_BATCH_SIZE = 512
  }
}
