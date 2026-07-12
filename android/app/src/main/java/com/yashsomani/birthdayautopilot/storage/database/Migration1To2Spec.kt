package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 was a denormalized foundation schema with no account/install ownership or send permit.
 * Its rows are retained and copied into explicitly quarantined v2 projections. Nothing migrated
 * can arm or send until foreground identity, sync, approval, reset, clock, and TEST setup rebuild
 * current records. The original v1 tables remain intact for a later audited retirement migration.
 */
class Migration1To2Spec : AutoMigrationSpec {
  override fun onPostMigrate(connection: SQLiteConnection) {
    connection.execSQL(
      """
      INSERT OR IGNORE INTO accounts_v2(
        accountId, activeSlot, googleSubjectHash, firebaseUid, displayEmail, localeTag,
        state, revision, createdAtMillis, updatedAtMillis
      )
      SELECT '$MIGRATED_ACCOUNT_ID', NULL, '$MIGRATED_SUBJECT_HASH', '$MIGRATED_FIREBASE_UID',
        NULL, 'und', 'MIGRATION_REVIEW_REQUIRED', 0, 0, 0
      WHERE EXISTS(SELECT 1 FROM contacts)
         OR EXISTS(SELECT 1 FROM approvals)
         OR EXISTS(SELECT 1 FROM occurrences)
      """.trimIndent(),
    )
    connection.execSQL(
      """
      INSERT OR IGNORE INTO contact_sync_state_v2(
        accountId, activeGeneration, stagingGeneration, syncToken, parametersHash, freshness,
        lastFullSuccessMillis, lastIncrementalSuccessMillis, lastAttemptMillis, lastErrorCode,
        revision
      )
      SELECT '$MIGRATED_ACCOUNT_ID', '$MIGRATED_GENERATION', NULL, NULL, '', 'SAFETY_PAUSED',
        NULL, NULL, NULL, 'MIGRATION_REVIEW_REQUIRED', 0
      WHERE EXISTS(SELECT 1 FROM accounts_v2 WHERE accountId = '$MIGRATED_ACCOUNT_ID')
      """.trimIndent(),
    )
    connection.execSQL(
      """
      INSERT OR IGNORE INTO contact_snapshots_v2(
        contactId, accountId, peopleResourceName, sourceFingerprint, sourceEtag, displayName,
        safeGivenName, birthdayMonth, birthdayDay, birthdayYear, leapDayPolicy, state,
        syncGeneration, materialRevision, sourceUpdatedAtMillis, syncedAtMillis, deletedAtMillis
      )
      SELECT localId, '$MIGRATED_ACCOUNT_ID', 'legacy:' || sourceFingerprint, sourceFingerprint,
        NULL, displayName, safeGivenName, birthdayMonth, birthdayDay, birthdayYear, leapDayPolicy,
        'MIGRATED_REVIEW_REQUIRED', '$MIGRATED_GENERATION', 0, sourceUpdatedAtMillis,
        sourceUpdatedAtMillis, CASE WHEN sourceDeleted = 1 THEN sourceUpdatedAtMillis ELSE NULL END
      FROM contacts
      """.trimIndent(),
    )
    connection.execSQL(
      """
      INSERT OR IGNORE INTO contact_phones_v2(
        phoneId, contactId, sourceFingerprint, rawNumber, normalizedE164,
        destinationFingerprint, maskedDisplay, typeLabel, regionCode, isSmsCapableType,
        state, materialRevision, updatedAtMillis
      )
      SELECT 'legacy-phone:' || localId, localId, 'legacy-phone:' || localId,
        COALESCE(phoneE164, ''), phoneE164, normalizedDestinationBasis,
        COALESCE(maskedPhone, ''), NULL, NULL, 0, 'INVALID', 0, sourceUpdatedAtMillis
      FROM contacts
      """.trimIndent(),
    )
    connection.execSQL(
      """
      INSERT OR IGNORE INTO recipient_policies_v2(
        contactId, chosenPhoneId, state, explicitEnrollmentEventId, blockReason, approvalId,
        revision, enabledAtMillis, updatedAtMillis
      )
      SELECT localId, 'legacy-phone:' || localId, 'NEEDS_REVIEW', NULL,
        'MIGRATION_REVIEW_REQUIRED', NULL, 0, NULL, sourceUpdatedAtMillis
      FROM contacts
      """.trimIndent(),
    )
    connection.execSQL(
      """
      INSERT OR IGNORE INTO automation_policies_v2(
        policyId, accountId, revision, state, timeZoneId, windowStartMinute, windowEndMinute,
        graceEndMinute, latePolicy, dailyCap, simPolicyKind, resolvedSubscriptionId,
        roamingAllowed, policyVersion, createdAtMillis, invalidatedAtMillis, invalidationReason
      )
      SELECT '$MIGRATED_POLICY_ID', '$MIGRATED_ACCOUNT_ID', 0, 'INVALIDATED', 'UTC', 0, 30,
        NULL, 'SAME_DAY_WINDOW_ONLY', 1, 'MIGRATED_UNKNOWN', -1, 0, 'legacy-v1', 0, 0,
        'MIGRATION_REVIEW_REQUIRED'
      WHERE EXISTS(SELECT 1 FROM accounts_v2 WHERE accountId = '$MIGRATED_ACCOUNT_ID')
      """.trimIndent(),
    )
    connection.execSQL(
      """
      INSERT OR IGNORE INTO approval_snapshots_v2(
        approvalId, accountId, contactId, phoneId, schemaVersion, contactMaterialRevision,
        phoneMaterialRevision, policyId, policyRevision, normalizedPhoneE164,
        destinationFingerprint, maskedPhoneDisplay, exactMessage, sourceTemplateId,
        sourceTemplateVersion, placeholderMode, birthdayMonth, birthdayDay, leapDayPolicy,
        windowStartMinute, windowEndMinute, graceEndMinute, latePolicy, simPolicyKind,
        resolvedSubscriptionId, segmentCount, messageEncoding, orderedPartsHash,
        carrierCostDisclosureVersion, consentDisclosureVersion, contentHash, state,
        approvedAtMillis, invalidatedAtMillis, invalidationReason
      )
      SELECT a.approvalId, '$MIGRATED_ACCOUNT_ID', a.contactId, 'legacy-phone:' || a.contactId,
        1, 0, 0, '$MIGRATED_POLICY_ID', 0, COALESCE(c.phoneE164, ''),
        COALESCE(c.normalizedDestinationBasis, ''), COALESCE(c.maskedPhone, ''), a.exactMessage,
        NULL, 'legacy-v1', 'MIGRATED_UNKNOWN', COALESCE(c.birthdayMonth, 1),
        COALESCE(c.birthdayDay, 1), c.leapDayPolicy, a.windowStartMinutes, a.windowEndMinutes,
        a.graceEndMinutes, 'MIGRATED_UNKNOWN', a.simPolicy, -1, a.segmentCount,
        'MIGRATED_UNKNOWN', '', 'legacy-v1', 'legacy-v1',
        'legacy:' || a.approvalId || ':' || a.payloadHash, 'MIGRATED_REVIEW_REQUIRED',
        a.approvedAtMillis, COALESCE(a.invalidatedAtMillis, a.approvedAtMillis),
        COALESCE(a.invalidationReason, 'MIGRATION_REVIEW_REQUIRED')
      FROM approvals a
      JOIN contacts c ON c.localId = a.contactId
      """.trimIndent(),
    )
    connection.execSQL(
      """
      INSERT OR IGNORE INTO birthday_occurrences_v2(
        occurrenceId, accountId, contactId, approvalId, policyId, localDate, timeZoneId,
        resolvedWindowStartMillis, resolvedWindowEndMillis, idempotencyKey,
        destinationFingerprint, channel, payloadHash, state, attemptNumber, revision,
        claimedBlockerRevision, createdAtMillis, updatedAtMillis, terminalAtMillis,
        retentionUntilMillis, safeOutcomeCode
      )
      SELECT o.occurrenceId, '$MIGRATED_ACCOUNT_ID', o.contactId, a.approvalId,
        '$MIGRATED_POLICY_ID', o.localDate, o.timeZoneId, 0, 1, o.idempotencyKey,
        COALESCE(c.normalizedDestinationBasis, ''), 'SMS',
        'legacy:' || a.approvalId || ':' || a.payloadHash, 'CANCELLED', o.attempt, 0,
        NULL, 0, COALESCE(o.terminalAtMillis, 0), o.terminalAtMillis,
        $MAX_RETENTION_MILLIS, 'MIGRATION_REVIEW_REQUIRED:' || o.state
      FROM occurrences o
      JOIN approvals a ON a.contactId = o.contactId
      JOIN contacts c ON c.localId = o.contactId
      """.trimIndent(),
    )
    connection.execSQL(
      """
      INSERT OR IGNORE INTO local_destination_guards_v2(
        guardId, accountId, occurrenceId, destinationFingerprint, localDate, channel,
        armedOrLater, createdAtMillis, armedAtMillis, retentionUntilMillis
      )
      SELECT 'legacy-guard:' || occurrenceId, accountId, occurrenceId, destinationFingerprint,
        localDate, channel, 1, createdAtMillis, updatedAtMillis, retentionUntilMillis
      FROM birthday_occurrences_v2
      WHERE accountId = '$MIGRATED_ACCOUNT_ID' AND destinationFingerprint <> ''
      """.trimIndent(),
    )
    connection.execSQL(
      """
      INSERT OR IGNORE INTO reset_safety_v2(
        resetSafetyId, accountId, resetGeneration, resetAtMillis, resetLocalDate,
        resetTimeZoneId, birthdayAutomationNotBeforeMillis, status, overflowBlocked,
        revision, updatedAtMillis
      )
      SELECT '$MIGRATED_RESET_ID', '$MIGRATED_ACCOUNT_ID', 1, 0, '1970-01-01', 'UTC',
        $MAX_RETENTION_MILLIS, 'REPAIR_REQUIRED', 0, 0, 0
      WHERE EXISTS(SELECT 1 FROM accounts_v2 WHERE accountId = '$MIGRATED_ACCOUNT_ID')
      """.trimIndent(),
    )
    connection.execSQL(
      """
      INSERT OR IGNORE INTO clock_trust_v2(
        accountId, status, greatestTrustedServerMillis, lastDeviceWallMillis,
        lastElapsedRealtimeMillis, trustedBootCount, lastVerificationMillis,
        observedDriftMillis, revision
      )
      SELECT '$MIGRATED_ACCOUNT_ID', 'UNVERIFIED', NULL, NULL, NULL, NULL, NULL, NULL, 0
      WHERE EXISTS(SELECT 1 FROM accounts_v2 WHERE accountId = '$MIGRATED_ACCOUNT_ID')
      """.trimIndent(),
    )
    connection.execSQL(
      """
      UPDATE app_control
      SET revision = revision + 1,
          blockerRevision = blockerRevision + 1,
          accountMode = 'PAUSED_REPAIR',
          automationDesired = 0,
          activeInstallationEpoch = NULL,
          resetSafetyState = 'REPAIR_REQUIRED'
      WHERE singletonId = 1
        AND (EXISTS(SELECT 1 FROM contacts)
          OR EXISTS(SELECT 1 FROM approvals)
          OR EXISTS(SELECT 1 FROM occurrences))
      """.trimIndent(),
    )
  }

  private companion object {
    const val MIGRATED_ACCOUNT_ID = "__migrated_unbound_v1__"
    const val MIGRATED_SUBJECT_HASH = "__migrated_subject_unbound_v1__"
    const val MIGRATED_FIREBASE_UID = "__migrated_firebase_unbound_v1__"
    const val MIGRATED_GENERATION = "__migrated_generation_v1__"
    const val MIGRATED_POLICY_ID = "__migrated_policy_v1__"
    const val MIGRATED_RESET_ID = "__migrated_reset_v1__"
    const val MAX_RETENTION_MILLIS = Long.MAX_VALUE
  }
}
