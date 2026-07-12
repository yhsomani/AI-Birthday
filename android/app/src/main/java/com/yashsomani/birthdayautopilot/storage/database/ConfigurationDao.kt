package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.messages.MessageTemplateValidator

data class EnabledContactPlanRow(
  val contactId: String,
  val displayName: String,
  val birthdayMonth: Int,
  val birthdayDay: Int,
  val leapDayPolicy: String?,
  val maskedDisplay: String,
  val destinationFingerprint: String,
  val exactMessage: String,
)

data class ConfiguredBirthdayRow(
  val contactId: String,
  val birthdayMonth: Int,
  val birthdayDay: Int,
  val leapDayPolicy: String?,
)

/**
 * Configuration-only data access. No method in this DAO can create a claim, Arm, permit, send
 * attempt, callback token, or SMS call.
 */
@Dao
abstract class ConfigurationDao {
  @Query("SELECT * FROM app_control WHERE singletonId = 1")
  abstract suspend fun control(): ControlEntity?

  @Query("SELECT * FROM accounts_v2 WHERE activeSlot = 1 AND state = 'ACTIVE' LIMIT 1")
  abstract suspend fun activeAccount(): AccountRecordEntity?

  @Query("SELECT * FROM contact_sync_state_v2 WHERE accountId = :accountId")
  abstract suspend fun syncState(accountId: String): ContactSyncStateEntity?

  @Query("SELECT * FROM contact_snapshots_v2 WHERE contactId = :contactId")
  abstract suspend fun contact(contactId: String): ContactSnapshotEntity?

  @Query("SELECT * FROM contact_phones_v2 WHERE contactId = :contactId ORDER BY phoneId")
  abstract suspend fun phones(contactId: String): List<ContactPhoneEntity>

  @Query(
    "SELECT * FROM contact_birthday_choices_v4 WHERE contactId = :contactId " +
      "ORDER BY birthdayMonth, birthdayDay, birthdayYear, birthdayId",
  )
  abstract suspend fun birthdays(contactId: String): List<ContactBirthdayChoiceEntity>

  @Query("SELECT * FROM recipient_policies_v2 WHERE contactId = :contactId")
  abstract suspend fun recipientPolicy(contactId: String): RecipientPolicyEntity?

  @Query("SELECT * FROM approval_snapshots_v2 WHERE approvalId = :approvalId")
  abstract suspend fun approval(approvalId: String): ApprovalSnapshotEntity?

  @Query(
    "SELECT * FROM message_templates_v2 WHERE accountId = :accountId " +
      "AND validationState = 'VALID' AND validatorVersion = :validatorVersion " +
      "ORDER BY revision DESC LIMIT 1",
  )
  protected abstract suspend fun activeTemplateForValidator(
    accountId: String,
    validatorVersion: String,
  ): MessageTemplateEntity?

  suspend fun activeTemplate(accountId: String): MessageTemplateEntity? = activeTemplateForValidator(
    accountId,
    MessageTemplateValidator.VALIDATOR_VERSION,
  )

  @Query(
    "SELECT * FROM automation_policies_v2 WHERE accountId = :accountId " +
      "AND state = 'ACTIVE' ORDER BY revision DESC LIMIT 1",
  )
  abstract suspend fun activeAutomationPolicy(accountId: String): AutomationPolicyEntity?

  @Query("SELECT COALESCE(MAX(revision), 0) FROM message_templates_v2 WHERE accountId = :accountId")
  abstract suspend fun latestTemplateRevision(accountId: String): Long

  @Query("SELECT COALESCE(MAX(revision), 0) FROM automation_policies_v2 WHERE accountId = :accountId")
  abstract suspend fun latestPolicyRevision(accountId: String): Long

  @Query(
    "SELECT * FROM contact_snapshots_v2 WHERE accountId = :accountId " +
      "AND contactId IN (:contactIds)",
  )
  abstract suspend fun contacts(accountId: String, contactIds: List<String>): List<ContactSnapshotEntity>

  @Query(
    "SELECT * FROM recipient_policies_v2 WHERE contactId IN (:contactIds)",
  )
  abstract suspend fun recipientPolicies(contactIds: List<String>): List<RecipientPolicyEntity>

  @Query(
    "SELECT * FROM contact_phones_v2 WHERE contactId IN (:contactIds) ORDER BY phoneId",
  )
  abstract suspend fun phonesForContacts(contactIds: List<String>): List<ContactPhoneEntity>

  @Query(
    "SELECT * FROM contact_birthday_choices_v4 WHERE contactId IN (:contactIds) " +
      "ORDER BY birthdayMonth, birthdayDay, birthdayYear, birthdayId",
  )
  abstract suspend fun birthdaysForContacts(
    contactIds: List<String>,
  ): List<ContactBirthdayChoiceEntity>

  @Query(
    "SELECT COUNT(*) FROM recipient_policies_v2 p " +
      "JOIN contact_snapshots_v2 c ON c.contactId = p.contactId " +
      "WHERE c.accountId = :accountId AND p.state = 'ENABLED'",
  )
  abstract suspend fun enabledRecipientCount(accountId: String): Int

  @Query(
    "SELECT COUNT(*) FROM recipient_policies_v2 p " +
      "JOIN contact_snapshots_v2 c ON c.contactId = p.contactId " +
      "WHERE c.accountId = :accountId AND p.state NOT IN ('OFF', 'EXCLUDED')",
  )
  abstract suspend fun configuredRecipientCount(accountId: String): Int

  @Query(
    "SELECT c.* FROM contact_snapshots_v2 c " +
      "JOIN recipient_policies_v2 p ON p.contactId = c.contactId " +
      "WHERE c.accountId = :accountId AND c.state = 'ACTIVE' " +
      "AND p.state NOT IN ('OFF', 'EXCLUDED') " +
      "ORDER BY c.displayName COLLATE NOCASE, c.contactId LIMIT :limit",
  )
  abstract suspend fun configuredPreviewContacts(
    accountId: String,
    limit: Int,
  ): List<ContactSnapshotEntity>

  @Query(
    "SELECT c.* FROM contact_snapshots_v2 c WHERE c.accountId = :accountId " +
      "AND c.state = 'ACTIVE' ORDER BY c.displayName COLLATE NOCASE, c.contactId LIMIT :limit",
  )
  abstract suspend fun fallbackPreviewContacts(
    accountId: String,
    limit: Int,
  ): List<ContactSnapshotEntity>

  @Query(
    """
    SELECT c.contactId AS contactId,
           c.birthdayMonth AS birthdayMonth,
           c.birthdayDay AS birthdayDay,
           c.leapDayPolicy AS leapDayPolicy
    FROM contact_snapshots_v2 c
    JOIN recipient_policies_v2 p ON p.contactId = c.contactId
    WHERE c.accountId = :accountId
      AND c.state = 'ACTIVE'
      AND p.state IN ('ENABLED', 'BLOCKED', 'NEEDS_REVIEW')
      AND c.birthdayMonth IS NOT NULL
      AND c.birthdayDay IS NOT NULL
    ORDER BY c.contactId
    LIMIT :limit
    """,
  )
  abstract suspend fun configuredBirthdayRows(
    accountId: String,
    limit: Int,
  ): List<ConfiguredBirthdayRow>

  @Query(
    """
    SELECT c.contactId AS contactId,
           c.birthdayMonth AS birthdayMonth,
           c.birthdayDay AS birthdayDay,
           c.leapDayPolicy AS leapDayPolicy
    FROM contact_snapshots_v2 c
    JOIN recipient_policies_v2 p ON p.contactId = c.contactId
    WHERE c.accountId = :accountId
      AND c.state = 'ACTIVE'
      AND p.state IN ('ENABLED', 'BLOCKED', 'NEEDS_REVIEW')
      AND c.birthdayMonth IS NOT NULL
      AND c.birthdayDay IS NOT NULL
    ORDER BY c.contactId
    """,
  )
  abstract suspend fun configuredBirthdayRowsForCapacity(
    accountId: String,
  ): List<ConfiguredBirthdayRow>

  @Query(
    "SELECT COUNT(*) FROM recipient_policies_v2 p " +
      "JOIN contact_snapshots_v2 c ON c.contactId = p.contactId " +
      "WHERE c.accountId = :accountId AND p.state IN ('PAUSED', 'BLOCKED', 'NEEDS_REVIEW')",
  )
  abstract suspend fun attentionRecipientCount(accountId: String): Int

  @Query(
    """
    SELECT COUNT(*) FROM contact_snapshots_v2 c
    LEFT JOIN recipient_policies_v2 p ON p.contactId = c.contactId
    WHERE c.accountId = :accountId
      AND c.state = 'ACTIVE'
      AND (
        c.birthdayMonth IS NULL OR c.birthdayDay IS NULL
        OR (:requiresName = 1 AND c.safeGivenName IS NULL)
        OR NOT EXISTS(
          SELECT 1 FROM contact_phones_v2 ready_phone
          WHERE ready_phone.contactId = c.contactId AND ready_phone.state = 'READY'
        )
        OR (
          p.chosenPhoneId IS NULL AND 1 < (
            SELECT COUNT(*) FROM contact_phones_v2 candidate_phone
            WHERE candidate_phone.contactId = c.contactId AND candidate_phone.state = 'READY'
          )
        )
        OR p.state IN ('PAUSED', 'BLOCKED', 'NEEDS_REVIEW')
      )
    """,
  )
  abstract suspend fun needsAttentionContactCount(
    accountId: String,
    requiresName: Boolean,
  ): Int

  @Query(
    "SELECT COUNT(*) FROM contact_snapshots_v2 c WHERE c.accountId = :accountId " +
      "AND c.state != 'ACTIVE'",
  )
  abstract suspend fun unavailableContactCount(accountId: String): Int

  @Query(
    """
    SELECT c.contactId AS contactId,
           c.displayName AS displayName,
           c.birthdayMonth AS birthdayMonth,
           c.birthdayDay AS birthdayDay,
           c.leapDayPolicy AS leapDayPolicy,
           ph.maskedDisplay AS maskedDisplay,
           ph.destinationFingerprint AS destinationFingerprint,
           a.exactMessage AS exactMessage
    FROM contact_snapshots_v2 c
    JOIN recipient_policies_v2 p ON p.contactId = c.contactId
    JOIN contact_phones_v2 ph ON ph.phoneId = p.chosenPhoneId
    JOIN approval_snapshots_v2 a ON a.approvalId = p.approvalId
    JOIN automation_policies_v2 current_policy ON current_policy.policyId = a.policyId
    JOIN message_templates_v2 current_template ON current_template.templateId = a.sourceTemplateId
    WHERE c.accountId = :accountId
      AND c.state = 'ACTIVE'
      AND p.state = 'ENABLED'
      AND ph.state = 'READY'
      AND ph.destinationFingerprint IS NOT NULL
      AND c.birthdayMonth IS NOT NULL
      AND c.birthdayDay IS NOT NULL
      AND a.state = 'ACTIVE'
      AND a.contactMaterialRevision = c.materialRevision
      AND a.phoneMaterialRevision = ph.materialRevision
      AND a.birthdayMonth = c.birthdayMonth
      AND a.birthdayDay = c.birthdayDay
      AND COALESCE(a.leapDayPolicy, '') = COALESCE(c.leapDayPolicy, '')
      AND current_policy.accountId = c.accountId
      AND current_policy.state = 'ACTIVE'
      AND current_policy.revision = a.policyRevision
      AND current_template.accountId = c.accountId
      AND current_template.validationState = 'VALID'
      AND current_template.templateVersion = a.sourceTemplateVersion
      AND NOT EXISTS(
        SELECT 1 FROM destination_blocks_v2 destination_block
        WHERE destination_block.accountId = c.accountId
          AND destination_block.destinationFingerprint = ph.destinationFingerprint
          AND destination_block.active = 1
      )
    ORDER BY c.contactId
    LIMIT :limit
    """,
  )
  abstract suspend fun enabledPlanRows(
    accountId: String,
    limit: Int,
  ): List<EnabledContactPlanRow>

  @Query(
    "SELECT COUNT(*) FROM recipient_policies_v2 other " +
      "JOIN contact_phones_v2 other_phone ON other_phone.phoneId = other.chosenPhoneId " +
      "JOIN contact_snapshots_v2 other_contact ON other_contact.contactId = other.contactId " +
      "WHERE other_contact.accountId = :accountId AND other.contactId != :contactId " +
      "AND other.state = 'ENABLED' AND other_phone.destinationFingerprint = :fingerprint",
  )
  abstract suspend fun enabledDuplicateDestinationCount(
    accountId: String,
    contactId: String,
    fingerprint: String,
  ): Int

  @Query(
    "SELECT * FROM birthday_occurrences_v2 WHERE contactId = :contactId " +
      "ORDER BY updatedAtMillis DESC LIMIT 1",
  )
  abstract suspend fun latestOccurrence(contactId: String): BirthdayOccurrenceRecordEntity?

  @Query(
    "SELECT occurrenceId FROM birthday_occurrences_v2 WHERE contactId = :contactId " +
      "AND localDate = :localDate AND (state IN " +
      "('PLANNED', 'PREPARED', 'SCHEDULED', 'COORDINATION_BLOCKED') " +
      "OR (state = 'MISSED' AND safeOutcomeCode = 'WINDOW_CLOSED')) " +
      "ORDER BY updatedAtMillis DESC, occurrenceId LIMIT 1",
  )
  abstract suspend fun reviewableOccurrenceId(contactId: String, localDate: String): String?

  @Query("SELECT * FROM coordination_state_v2 WHERE accountId = :accountId")
  abstract suspend fun coordinationState(accountId: String): CoordinationStateEntity?

  @Query("SELECT * FROM clock_trust_v2 WHERE accountId = :accountId")
  abstract suspend fun clockTrust(accountId: String): ClockTrustEntity?

  @Query("SELECT * FROM reset_safety_v2 WHERE accountId = :accountId")
  abstract suspend fun resetSafety(accountId: String): ResetSafetyEntity?

  @Query(
    "SELECT * FROM installation_bindings_v2 WHERE accountId = :accountId " +
      "AND localSlot = 1 AND state = 'ACTIVE' LIMIT 1",
  )
  abstract suspend fun activeInstallation(accountId: String): InstallationBindingEntity?

  @Query(
    "SELECT * FROM test_receipts_v2 WHERE accountId = :accountId " +
      "AND state = 'VALID' AND invalidatedAtMillis IS NULL ORDER BY passedAtMillis DESC",
  )
  abstract suspend fun validTestReceipts(accountId: String): List<TestReceiptEntity>

  @Query("SELECT * FROM test_jobs_v2 WHERE testJobId = :testJobId")
  abstract suspend fun testJob(testJobId: String): TestJobEntity?

  @Query(
    "SELECT * FROM test_jobs_v2 WHERE accountId = :accountId " +
      "ORDER BY updatedAtMillis DESC, testJobId DESC LIMIT 1",
  )
  abstract suspend fun latestTestJob(accountId: String): TestJobEntity?

  @Query(
    """
    SELECT permit.* FROM coordination_permits_v2 permit
    JOIN test_receipts_v2 receipt ON receipt.testJobId = permit.operationId
    WHERE receipt.testJobId = :testJobId
      AND receipt.state = 'VALID'
      AND receipt.invalidatedAtMillis IS NULL
      AND permit.purpose = 'TEST'
      AND permit.attemptNumber = 1
    ORDER BY permit.updatedAtMillis DESC
    LIMIT 1
    """,
  )
  abstract suspend fun testPermit(testJobId: String): CoordinationPermitEntity?

  @Query(
    "SELECT * FROM approval_snapshots_v2 WHERE accountId = :accountId " +
      "AND state = 'ACTIVE' ORDER BY approvedAtMillis DESC LIMIT 1",
  )
  abstract suspend fun latestActiveApproval(accountId: String): ApprovalSnapshotEntity?

  @Query(
    "SELECT COUNT(*) FROM destination_blocks_v2 " +
      "WHERE accountId = :accountId AND destinationFingerprint = :fingerprint AND active = 1",
  )
  abstract suspend fun activeDestinationBlockCount(accountId: String, fingerprint: String): Int

  @Query(
    "SELECT * FROM destination_blocks_v2 WHERE accountId = :accountId " +
      "AND destinationFingerprint = :fingerprint LIMIT 1",
  )
  abstract suspend fun destinationBlock(
    accountId: String,
    fingerprint: String,
  ): DestinationBlockEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertDestinationBlock(block: DestinationBlockEntity)

  @Update
  abstract suspend fun updateDestinationBlock(block: DestinationBlockEntity): Int

  @Query(
    "UPDATE approval_snapshots_v2 SET state = 'REVOKED', " +
      "invalidatedAtMillis = COALESCE(invalidatedAtMillis, :atMillis), " +
      "invalidationReason = COALESCE(invalidationReason, :reason) " +
      "WHERE accountId = :accountId AND destinationFingerprint = :fingerprint " +
      "AND state = 'ACTIVE'",
  )
  abstract suspend fun revokeApprovalsForDestination(
    accountId: String,
    fingerprint: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    "UPDATE recipient_policies_v2 SET state = 'NEEDS_REVIEW', approvalId = NULL, " +
      "blockReason = :reason, revision = revision + 1, enabledAtMillis = NULL, " +
      "updatedAtMillis = :atMillis WHERE state = 'ENABLED' AND chosenPhoneId IN (" +
      "SELECT phone.phoneId FROM contact_phones_v2 phone " +
      "JOIN contact_snapshots_v2 contact ON contact.contactId = phone.contactId " +
      "WHERE contact.accountId = :accountId AND phone.destinationFingerprint = :fingerprint" +
      ") AND revision < 9223372036854775807",
  )
  abstract suspend fun markEnabledRecipientsForDestinationReview(
    accountId: String,
    fingerprint: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    "UPDATE recipient_policies_v2 SET blockReason = 'APPROVAL_REQUIRED', " +
      "revision = revision + 1, updatedAtMillis = :atMillis " +
      "WHERE state = 'NEEDS_REVIEW' AND blockReason = :blockedReason " +
      "AND chosenPhoneId IN (SELECT phone.phoneId FROM contact_phones_v2 phone " +
      "JOIN contact_snapshots_v2 contact ON contact.contactId = phone.contactId " +
      "WHERE contact.accountId = :accountId AND phone.destinationFingerprint = :fingerprint" +
      ") AND revision < 9223372036854775807",
  )
  abstract suspend fun clearDestinationBlockReasonForReview(
    accountId: String,
    fingerprint: String,
    atMillis: Long,
    blockedReason: String,
  ): Int

  @Query(
    "UPDATE birthday_occurrences_v2 SET state = 'CANCELLED', revision = revision + 1, " +
      "updatedAtMillis = :atMillis, terminalAtMillis = COALESCE(terminalAtMillis, :atMillis), " +
      "safeOutcomeCode = COALESCE(safeOutcomeCode, :reason) " +
      "WHERE accountId = :accountId AND destinationFingerprint = :fingerprint " +
      "AND state IN ('PLANNED', 'PREPARED', 'SCHEDULED', 'COORDINATION_BLOCKED') " +
      "AND revision < 9223372036854775807",
  )
  abstract suspend fun cancelUnclaimedOccurrencesForDestination(
    accountId: String,
    fingerprint: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    """
    SELECT COUNT(*) FROM recipient_policies_v2 p
    JOIN contact_snapshots_v2 c ON c.contactId = p.contactId
    LEFT JOIN contact_phones_v2 ph ON ph.phoneId = p.chosenPhoneId
    LEFT JOIN approval_snapshots_v2 a ON a.approvalId = p.approvalId
    WHERE c.accountId = :accountId
      AND p.state IN ('ENABLED', 'BLOCKED', 'NEEDS_REVIEW')
      AND (
        p.state != 'ENABLED' OR c.state != 'ACTIVE' OR ph.phoneId IS NULL
        OR ph.state != 'READY' OR a.approvalId IS NULL OR a.state != 'ACTIVE'
        OR a.contactMaterialRevision != c.materialRevision
        OR a.phoneMaterialRevision != ph.materialRevision
        OR a.birthdayMonth != c.birthdayMonth OR a.birthdayDay != c.birthdayDay
        OR COALESCE(a.leapDayPolicy, '') != COALESCE(c.leapDayPolicy, '')
        OR NOT EXISTS(
          SELECT 1 FROM automation_policies_v2 current_policy
          WHERE current_policy.accountId = c.accountId
            AND current_policy.state = 'ACTIVE'
            AND current_policy.policyId = a.policyId
            AND current_policy.revision = a.policyRevision
        )
        OR NOT EXISTS(
          SELECT 1 FROM message_templates_v2 current_template
          WHERE current_template.accountId = c.accountId
            AND current_template.validationState = 'VALID'
            AND current_template.templateId = a.sourceTemplateId
            AND current_template.templateVersion = a.sourceTemplateVersion
        )
      )
    """,
  )
  abstract suspend fun unreadyConfiguredRecipientCount(accountId: String): Int

  @Query("SELECT * FROM configuration_reviews_v4 WHERE reviewId = :reviewId")
  abstract suspend fun review(reviewId: String): ConfigurationReviewEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertReview(review: ConfigurationReviewEntity): Long

  @Query(
    "DELETE FROM configuration_reviews_v4 WHERE expiresAtMillis <= :nowMillis " +
      "OR (consumedAtMillis IS NOT NULL AND consumedAtMillis <= :consumedCutoffMillis)",
  )
  abstract suspend fun deleteObsoleteReviews(nowMillis: Long, consumedCutoffMillis: Long): Int

  @Query(
    "UPDATE configuration_reviews_v4 SET consumedAtMillis = :atMillis " +
      "WHERE reviewId = :reviewId AND kind = :kind AND consumedAtMillis IS NULL " +
      "AND expiresAtMillis > :atMillis AND controlRevision = :controlRevision " +
      "AND blockerRevision = :blockerRevision",
  )
  abstract suspend fun markReviewConsumed(
    reviewId: String,
    kind: String,
    controlRevision: Long,
    blockerRevision: Long,
    atMillis: Long,
  ): Int

  @Update
  abstract suspend fun updateContact(contact: ContactSnapshotEntity): Int

  @Update
  abstract suspend fun updateRecipientPolicy(policy: RecipientPolicyEntity): Int

  @Query(
    "UPDATE approval_snapshots_v2 SET state = 'INVALIDATED', " +
      "invalidatedAtMillis = COALESCE(invalidatedAtMillis, :atMillis), " +
      "invalidationReason = COALESCE(invalidationReason, :reason) " +
      "WHERE contactId = :contactId AND state = 'ACTIVE'",
  )
  abstract suspend fun invalidateApprovals(
    contactId: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    "UPDATE approval_snapshots_v2 SET state = 'INVALIDATED', " +
      "invalidatedAtMillis = COALESCE(invalidatedAtMillis, :atMillis), " +
      "invalidationReason = COALESCE(invalidationReason, :reason) " +
      "WHERE accountId = :accountId AND state = 'ACTIVE'",
  )
  abstract suspend fun invalidateAllApprovals(
    accountId: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    "SELECT COUNT(*) FROM approval_snapshots_v2 WHERE accountId = :accountId " +
      "AND state = 'ACTIVE' AND simPolicyKind = 'SYSTEM_DEFAULT'",
  )
  abstract suspend fun activeSystemDefaultApprovalCount(accountId: String): Int

  @Query(
    "UPDATE recipient_policies_v2 SET state = 'NEEDS_REVIEW', approvalId = NULL, " +
      "blockReason = :reason, revision = revision + 1, enabledAtMillis = NULL, " +
      "updatedAtMillis = :atMillis WHERE approvalId IN (" +
      "SELECT approvalId FROM approval_snapshots_v2 WHERE accountId = :accountId " +
      "AND state = 'ACTIVE' AND simPolicyKind = 'SYSTEM_DEFAULT'" +
      ") AND state NOT IN ('OFF', 'EXCLUDED') AND revision < 9223372036854775807",
  )
  abstract suspend fun markSystemDefaultRecipientsForReview(
    accountId: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    "UPDATE approval_snapshots_v2 SET state = 'INVALIDATED', " +
      "invalidatedAtMillis = COALESCE(invalidatedAtMillis, :atMillis), " +
      "invalidationReason = COALESCE(invalidationReason, :reason) " +
      "WHERE accountId = :accountId AND state = 'ACTIVE' " +
      "AND simPolicyKind = 'SYSTEM_DEFAULT'",
  )
  abstract suspend fun invalidateSystemDefaultApprovals(
    accountId: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    "UPDATE approval_snapshots_v2 SET state = 'REVOKED', " +
      "invalidatedAtMillis = COALESCE(invalidatedAtMillis, :atMillis), " +
      "invalidationReason = COALESCE(invalidationReason, :reason) " +
      "WHERE contactId = :contactId AND state = 'ACTIVE'",
  )
  abstract suspend fun revokeApprovals(contactId: String, atMillis: Long, reason: String): Int

  @Query(
    "UPDATE test_receipts_v2 SET state = 'INVALIDATED', " +
      "invalidatedAtMillis = COALESCE(invalidatedAtMillis, :atMillis), " +
      "invalidationReason = COALESCE(invalidationReason, :reason) " +
      "WHERE accountId = :accountId AND state = 'VALID'",
  )
  abstract suspend fun invalidateTestReceipts(
    accountId: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    "SELECT COUNT(*) FROM test_receipts_v2 r INNER JOIN test_jobs_v2 j " +
      "ON j.testJobId = r.testJobId WHERE r.accountId = :accountId " +
      "AND r.state = 'VALID' AND j.simPolicyKind = 'SYSTEM_DEFAULT'",
  )
  abstract suspend fun validSystemDefaultTestReceiptCount(accountId: String): Int

  @Query(
    "UPDATE test_receipts_v2 SET state = 'INVALIDATED', " +
      "invalidatedAtMillis = COALESCE(invalidatedAtMillis, :atMillis), " +
      "invalidationReason = COALESCE(invalidationReason, :reason) " +
      "WHERE accountId = :accountId AND state = 'VALID' AND testJobId IN (" +
      "SELECT testJobId FROM test_jobs_v2 WHERE accountId = :accountId " +
      "AND simPolicyKind = 'SYSTEM_DEFAULT'" +
      ")",
  )
  abstract suspend fun invalidateSystemDefaultTestReceipts(
    accountId: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    "UPDATE recipient_policies_v2 SET state = 'NEEDS_REVIEW', approvalId = NULL, " +
      "blockReason = :reason, revision = revision + 1, enabledAtMillis = NULL, " +
      "updatedAtMillis = :atMillis WHERE contactId IN (" +
      "SELECT c.contactId FROM contact_snapshots_v2 c WHERE c.accountId = :accountId" +
      ") AND state NOT IN ('OFF', 'EXCLUDED') AND revision < 9223372036854775807",
  )
  abstract suspend fun markConfiguredRecipientsForReview(
    accountId: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    "UPDATE message_templates_v2 SET validationState = 'SUPERSEDED' " +
      "WHERE accountId = :accountId AND validationState = 'VALID'",
  )
  abstract suspend fun supersedeTemplates(accountId: String): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertTemplate(template: MessageTemplateEntity)

  @Query(
    "UPDATE automation_policies_v2 SET state = 'SUPERSEDED', " +
      "invalidatedAtMillis = COALESCE(invalidatedAtMillis, :atMillis), " +
      "invalidationReason = COALESCE(invalidationReason, :reason) " +
      "WHERE accountId = :accountId AND state = 'ACTIVE'",
  )
  abstract suspend fun supersedePolicies(accountId: String, atMillis: Long, reason: String): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertAutomationPolicy(policy: AutomationPolicyEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertApproval(approval: ApprovalSnapshotEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertConsentReceipt(receipt: ConsentReceiptEntity)

  @Query(
    "SELECT COALESCE(MAX(sequence), 0) FROM consent_receipts_v2 " +
      "WHERE accountId = :accountId AND kind = :kind",
  )
  abstract suspend fun latestConsentSequence(accountId: String, kind: ConsentKind): Long

  @Query(
    "SELECT * FROM consent_receipts_v2 WHERE accountId = :accountId AND kind = :kind " +
      "ORDER BY sequence DESC LIMIT 1",
  )
  abstract suspend fun latestConsentReceipt(
    accountId: String,
    kind: ConsentKind,
  ): ConsentReceiptEntity?

  @Query(
    "UPDATE app_control SET revision = revision + 1, blockerRevision = blockerRevision + 1 " +
      "WHERE singletonId = 1 AND revision = :expectedRevision " +
      "AND blockerRevision = :expectedBlockerRevision " +
      "AND revision < 9223372036854775807 AND blockerRevision < 9223372036854775807",
  )
  abstract suspend fun bumpControlBlocker(
    expectedRevision: Long,
    expectedBlockerRevision: Long,
  ): Int

  @Query(
    "UPDATE app_control SET revision = revision + 1, blockerRevision = blockerRevision + 1, " +
      "automationDesired = :desired, accountMode = :mode " +
      "WHERE singletonId = 1 AND revision = :expectedRevision " +
      "AND blockerRevision = :expectedBlockerRevision " +
      "AND revision < 9223372036854775807 AND blockerRevision < 9223372036854775807",
  )
  abstract suspend fun updateAutomationControl(
    expectedRevision: Long,
    expectedBlockerRevision: Long,
    desired: Boolean,
    mode: AccountMode,
  ): Int

  @Query(
    "UPDATE app_control SET revision = revision + 1, blockerRevision = blockerRevision + 1, " +
      "automationDesired = 1, accountMode = 'AUTOMATION_ACTIVE', " +
      "initialActivationCompleted = 1 " +
      "WHERE singletonId = 1 AND revision = :expectedRevision " +
      "AND blockerRevision = :expectedBlockerRevision " +
      "AND revision < 9223372036854775807 AND blockerRevision < 9223372036854775807",
  )
  abstract suspend fun markAutomationActivated(
    expectedRevision: Long,
    expectedBlockerRevision: Long,
  ): Int

  @Query(
    "UPDATE installation_bindings_v2 SET accountMode = :mode, " +
      "ownerLeaseUntilMillis = :ownerLeaseUntilMillis, revision = revision + 1, " +
      "updatedAtMillis = :updatedAtMillis WHERE installationId = :installationId " +
      "AND localSlot = 1 AND state = 'ACTIVE' AND senderEpoch = :senderEpoch",
  )
  abstract suspend fun updateInstallationMode(
    installationId: String,
    senderEpoch: Long,
    mode: AccountMode,
    ownerLeaseUntilMillis: Long?,
    updatedAtMillis: Long,
  ): Int

  @Query(
    "UPDATE coordination_state_v2 SET mode = :mode, ownerLeaseUntilMillis = :ownerLeaseUntilMillis, " +
      "lastSafeCode = :safeCode, revision = revision + 1, updatedAtMillis = :updatedAtMillis " +
      "WHERE accountId = :accountId AND activeInstallationId = :installationId " +
      "AND senderEpoch = :senderEpoch",
  )
  abstract suspend fun updateCoordinationMode(
    accountId: String,
    installationId: String,
    senderEpoch: Long,
    mode: AccountMode,
    ownerLeaseUntilMillis: Long?,
    safeCode: String?,
    updatedAtMillis: Long,
  ): Int
}
