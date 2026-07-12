package com.yashsomani.birthdayautopilot.lifecycle

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.BirthdayOccurrenceRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.ConfigurationReviewEntity
import com.yashsomani.birthdayautopilot.storage.database.ControlEntity

internal data class LifecycleActivityRow(
  val sourceKey: String,
  val sourceType: String,
  val state: String,
  val safeCode: String?,
  val occurredAtMillis: Long,
)

internal data class LifecycleActivityBounds(
  val eventCount: Int,
  val earliestMillis: Long?,
  val latestMillis: Long?,
)

/** Private review material for a real, already-planned, still-unarmed occurrence. */
internal data class LifecycleTodayOccurrenceRow(
  val occurrenceId: String,
  val accountId: String,
  val recipient: String,
  val exactText: String,
  val localDate: String,
  val timeZoneId: String,
  val resolvedWindowStartMillis: Long,
  val resolvedWindowEndMillis: Long,
  val state: String,
  val occurrenceRevision: Long,
  val updatedAtMillis: Long,
)

@Dao
internal interface LifecycleProjectionDao {
  @Query("SELECT * FROM app_control WHERE singletonId = 1")
  suspend fun control(): ControlEntity?

  @Query("SELECT * FROM accounts_v2 WHERE activeSlot = 1 LIMIT 1")
  suspend fun activeAccount(): AccountRecordEntity?

  @Query("SELECT * FROM accounts_v2 WHERE accountId = :accountId LIMIT 1")
  suspend fun account(accountId: String): AccountRecordEntity?

  @Query(
    "UPDATE accounts_v2 SET state = 'RETAINED_SIGNED_OUT', revision = revision + 1, " +
      "updatedAtMillis = :updatedAtMillis WHERE accountId = :accountId AND activeSlot = 1 " +
      "AND state = 'ACTIVE' AND revision < 9223372036854775807",
  )
  suspend fun markAccountRetainedSignedOut(accountId: String, updatedAtMillis: Long): Int

  @Query(
    "UPDATE app_control SET revision = revision + 1, blockerRevision = blockerRevision + 1 " +
      "WHERE singletonId = 1 AND automationDesired = 0 AND accountMode = 'PAUSED_REPAIR' " +
      "AND revision < 9223372036854775807 AND blockerRevision < 9223372036854775807",
  )
  suspend fun bumpRetainedSignOutBoundary(): Int

  @Query("SELECT * FROM configuration_reviews_v4 WHERE reviewId = :reviewId")
  suspend fun review(reviewId: String): ConfigurationReviewEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertReview(review: ConfigurationReviewEntity): Long

  @Query(
    "UPDATE configuration_reviews_v4 SET consumedAtMillis = :atMillis " +
      "WHERE reviewId = :reviewId AND kind = :kind AND consumedAtMillis IS NULL " +
      "AND expiresAtMillis > :atMillis AND controlRevision = :controlRevision " +
      "AND blockerRevision = :blockerRevision",
  )
  suspend fun consumeReview(
    reviewId: String,
    kind: String,
    controlRevision: Long,
    blockerRevision: Long,
    atMillis: Long,
  ): Int

  @Query(
    """
    SELECT occurrence.occurrenceId AS occurrenceId,
      occurrence.accountId AS accountId,
      contact.displayName AS recipient,
      approval.exactMessage AS exactText,
      occurrence.localDate AS localDate,
      occurrence.timeZoneId AS timeZoneId,
      occurrence.resolvedWindowStartMillis AS resolvedWindowStartMillis,
      occurrence.resolvedWindowEndMillis AS resolvedWindowEndMillis,
      occurrence.state AS state,
      occurrence.revision AS occurrenceRevision,
      occurrence.updatedAtMillis AS updatedAtMillis
    FROM birthday_occurrences_v2 occurrence
    JOIN accounts_v2 account ON account.accountId = occurrence.accountId
    JOIN contact_snapshots_v2 contact ON contact.contactId = occurrence.contactId
    JOIN recipient_policies_v2 recipient ON recipient.contactId = contact.contactId
    JOIN approval_snapshots_v2 approval ON approval.approvalId = occurrence.approvalId
    JOIN contact_phones_v2 phone ON phone.phoneId = approval.phoneId
    JOIN automation_policies_v2 policy ON policy.policyId = occurrence.policyId
    WHERE occurrence.occurrenceId = :occurrenceId
      AND account.activeSlot = 1
      AND account.state = 'ACTIVE'
      AND contact.accountId = occurrence.accountId
      AND contact.state = 'ACTIVE'
      AND recipient.state = 'ENABLED'
      AND recipient.approvalId = occurrence.approvalId
      AND approval.accountId = occurrence.accountId
      AND approval.contactId = occurrence.contactId
      AND approval.state = 'ACTIVE'
      AND approval.contentHash = occurrence.payloadHash
      AND approval.contactMaterialRevision = contact.materialRevision
      AND approval.phoneMaterialRevision = phone.materialRevision
      AND phone.state = 'READY'
      AND phone.destinationFingerprint = occurrence.destinationFingerprint
      AND policy.accountId = occurrence.accountId
      AND policy.state = 'ACTIVE'
      AND policy.revision = approval.policyRevision
      AND occurrence.state IN ('PLANNED', 'PREPARED', 'SCHEDULED', 'COORDINATION_BLOCKED')
    LIMIT 1
    """,
  )
  suspend fun todayOccurrence(occurrenceId: String): LifecycleTodayOccurrenceRow?

  @Query("SELECT * FROM birthday_occurrences_v2 WHERE occurrenceId = :occurrenceId")
  suspend fun occurrence(occurrenceId: String): BirthdayOccurrenceRecordEntity?

  @Query(
    """
    SELECT * FROM (
      SELECT 'legacy:' || activityId AS sourceKey, 'legacy' AS sourceType,
        category AS state, safeCode AS safeCode, recordedAtMillis AS occurredAtMillis
      FROM activity WHERE recordedAtMillis >= :visibilityCutoffMillis
      UNION ALL
      SELECT 'occurrence:' || occurrenceId, 'occurrence', state, safeOutcomeCode, updatedAtMillis
      FROM birthday_occurrences_v2 WHERE updatedAtMillis >= :visibilityCutoffMillis
      UNION ALL
      SELECT 'test:' || testJobId, 'test', state, invalidationReason, updatedAtMillis
      FROM test_jobs_v2 WHERE updatedAtMillis >= :visibilityCutoffMillis
      UNION ALL
      SELECT 'outcome:' || purpose || ':' || operationId, 'outcome', visibleOutcome,
        immutableSafetyState, refinedAtMillis
      FROM outcome_projections_v2 WHERE refinedAtMillis >= :visibilityCutoffMillis
    )
    WHERE :hasCursor = 0
      OR occurredAtMillis < :beforeMillis
      OR (occurredAtMillis = :beforeMillis AND sourceKey < :beforeSourceKey)
    ORDER BY occurredAtMillis DESC, sourceKey DESC
    LIMIT :limit
    """,
  )
  suspend fun activityPage(
    visibilityCutoffMillis: Long,
    hasCursor: Boolean,
    beforeMillis: Long,
    beforeSourceKey: String,
    limit: Int,
  ): List<LifecycleActivityRow>

  @Query(
    """
    SELECT COUNT(*) AS eventCount, MIN(occurredAtMillis) AS earliestMillis,
      MAX(occurredAtMillis) AS latestMillis FROM (
      SELECT recordedAtMillis AS occurredAtMillis FROM activity
        WHERE recordedAtMillis >= :visibilityCutoffMillis
      UNION ALL SELECT updatedAtMillis FROM birthday_occurrences_v2
        WHERE updatedAtMillis >= :visibilityCutoffMillis
      UNION ALL SELECT updatedAtMillis FROM test_jobs_v2
        WHERE updatedAtMillis >= :visibilityCutoffMillis
      UNION ALL SELECT refinedAtMillis FROM outcome_projections_v2
        WHERE refinedAtMillis >= :visibilityCutoffMillis
    )
    """,
  )
  suspend fun activityBounds(visibilityCutoffMillis: Long): LifecycleActivityBounds

  @Query("SELECT COUNT(*) FROM contact_snapshots_v2 WHERE accountId = :accountId AND state = 'ACTIVE'")
  suspend fun localContactCount(accountId: String): Int

  @Query(
    """
    SELECT COUNT(*) FROM recipient_policies_v2 policy
    JOIN contact_snapshots_v2 contact ON contact.contactId = policy.contactId
    WHERE contact.accountId = :accountId AND policy.state = 'ENABLED'
    """,
  )
  suspend fun enabledRecipientCount(accountId: String): Int

  @Query("SELECT COUNT(*) FROM approval_snapshots_v2 WHERE accountId = :accountId")
  suspend fun approvalCount(accountId: String): Int

  @Query("SELECT COUNT(*) FROM message_templates_v2 WHERE accountId = :accountId")
  suspend fun templateCount(accountId: String): Int

  @Query(
    "SELECT MAX(COALESCE(lastIncrementalSuccessMillis, lastFullSuccessMillis)) " +
      "FROM contact_sync_state_v2 WHERE accountId = :accountId",
  )
  suspend fun lastContactsSyncMillis(accountId: String): Long?

  @Query(
    "SELECT DISTINCT disclosureVersion FROM consent_receipts_v2 WHERE accountId = :accountId " +
      "ORDER BY disclosureVersion LIMIT 32",
  )
  suspend fun consentVersions(accountId: String): List<String>

  @Query("DELETE FROM activity")
  suspend fun clearLegacyActivity(): Int

  @Query(
    "UPDATE message_templates_v2 SET validationState = 'SUPERSEDED', revision = revision + 1, " +
      "updatedAtMillis = :updatedAtMillis WHERE accountId = :accountId AND source = 'GEMINI' " +
      "AND validationState != 'SUPERSEDED'",
  )
  suspend fun clearGeminiTemplates(accountId: String, updatedAtMillis: Long): Int

  @Query(
    "DELETE FROM delivery_events_v2 WHERE callbackTokenId IN " +
      "(SELECT token.callbackTokenId FROM callback_tokens_v2 token " +
      "JOIN send_attempts_v2 attempt ON attempt.sendAttemptId = token.sendAttemptId " +
      "JOIN coordination_permits_v2 permit ON permit.permitId = attempt.permitId " +
      "WHERE permit.accountId = :accountId)",
  )
  suspend fun deleteDeliveryEvents(accountId: String): Int

  @Query(
    "DELETE FROM callback_tokens_v2 WHERE sendAttemptId IN " +
      "(SELECT attempt.sendAttemptId FROM send_attempts_v2 attempt " +
      "JOIN coordination_permits_v2 permit ON permit.permitId = attempt.permitId " +
      "WHERE permit.accountId = :accountId)",
  )
  suspend fun deleteCallbackTokens(accountId: String): Int

  @Query(
    "DELETE FROM send_attempts_v2 WHERE permitId IN " +
      "(SELECT permitId FROM coordination_permits_v2 WHERE accountId = :accountId)",
  )
  suspend fun deleteSendAttempts(accountId: String): Int

  @Query("DELETE FROM test_receipts_v2 WHERE accountId = :accountId")
  suspend fun deleteTestReceipts(accountId: String): Int

  @Query("DELETE FROM outcome_projections_v2 WHERE accountId = :accountId")
  suspend fun deleteOutcomeProjections(accountId: String): Int

  @Query("DELETE FROM coordination_permits_v2 WHERE accountId = :accountId")
  suspend fun deleteCoordinationPermits(accountId: String): Int

  @Query("DELETE FROM local_destination_guards_v2 WHERE accountId = :accountId")
  suspend fun deleteDestinationGuards(accountId: String): Int

  @Query("DELETE FROM birthday_occurrences_v2 WHERE accountId = :accountId")
  suspend fun deleteBirthdayOccurrences(accountId: String): Int

  @Query("DELETE FROM test_jobs_v2 WHERE accountId = :accountId")
  suspend fun deleteTestJobs(accountId: String): Int

  @Query("DELETE FROM configuration_reviews_v4 WHERE accountId = :accountId")
  suspend fun deleteConfigurationReviews(accountId: String): Int

  @Query("DELETE FROM approval_snapshots_v2 WHERE accountId = :accountId")
  suspend fun deleteApprovalSnapshots(accountId: String): Int

  @Query(
    "DELETE FROM recipient_policies_v2 WHERE contactId IN " +
      "(SELECT contactId FROM contact_snapshots_v2 WHERE accountId = :accountId)",
  )
  suspend fun deleteRecipientPolicies(accountId: String): Int

  @Query(
    "DELETE FROM contact_birthday_choices_v4 WHERE contactId IN " +
      "(SELECT contactId FROM contact_snapshots_v2 WHERE accountId = :accountId)",
  )
  suspend fun deleteBirthdayChoices(accountId: String): Int

  @Query(
    "DELETE FROM contact_phones_v2 WHERE contactId IN " +
      "(SELECT contactId FROM contact_snapshots_v2 WHERE accountId = :accountId)",
  )
  suspend fun deleteContactPhones(accountId: String): Int

  @Query("DELETE FROM contact_snapshots_v2 WHERE accountId = :accountId")
  suspend fun deleteContactSnapshots(accountId: String): Int

  @Query(
    "DELETE FROM people_staging_birthdays_v4 WHERE generationId IN " +
      "(SELECT generationId FROM people_sync_generations_v2 WHERE accountId = :accountId)",
  )
  suspend fun deleteStagingBirthdays(accountId: String): Int

  @Query(
    "DELETE FROM people_staging_phones_v2 WHERE generationId IN " +
      "(SELECT generationId FROM people_sync_generations_v2 WHERE accountId = :accountId)",
  )
  suspend fun deleteStagingPhones(accountId: String): Int

  @Query(
    "DELETE FROM people_staging_contacts_v2 WHERE generationId IN " +
      "(SELECT generationId FROM people_sync_generations_v2 WHERE accountId = :accountId)",
  )
  suspend fun deleteStagingContacts(accountId: String): Int

  @Query("DELETE FROM people_sync_generations_v2 WHERE accountId = :accountId")
  suspend fun deleteSyncGenerations(accountId: String): Int

  @Query("DELETE FROM destination_blocks_v2 WHERE accountId = :accountId")
  suspend fun deleteDestinationBlocks(accountId: String): Int

  @Query("DELETE FROM message_templates_v2 WHERE accountId = :accountId")
  suspend fun deleteMessageTemplates(accountId: String): Int

  @Query("DELETE FROM automation_policies_v2 WHERE accountId = :accountId")
  suspend fun deleteAutomationPolicies(accountId: String): Int

  @Query(
    "UPDATE contact_sync_state_v2 SET activeGeneration = NULL, stagingGeneration = NULL, " +
      "syncToken = NULL, freshness = 'AUTH_ACTION_REQUIRED', lastFullSuccessMillis = NULL, " +
      "lastIncrementalSuccessMillis = NULL, lastAttemptMillis = :updatedAtMillis, " +
      "lastErrorCode = 'CONTACTS_AUTHORIZATION_REQUIRED', revision = revision + 1 " +
      "WHERE accountId = :accountId AND revision < 9223372036854775807",
  )
  suspend fun markContactsDisconnected(accountId: String, updatedAtMillis: Long): Int

  @Query("DELETE FROM occurrences")
  suspend fun deleteLegacyOccurrences(): Int

  @Query("DELETE FROM approvals")
  suspend fun deleteLegacyApprovals(): Int

  @Query("DELETE FROM contacts")
  suspend fun deleteLegacyContacts(): Int
}
