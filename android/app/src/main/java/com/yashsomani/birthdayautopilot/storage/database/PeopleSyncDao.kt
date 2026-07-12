package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

enum class IdentityAttachDecision {
  ATTACHED,
  ACCOUNT_CONFLICT,
  STORAGE_REJECTED,
}

data class PeopleCommitResult(
  val materialChanges: Int,
  val invalidatedApprovals: Int,
)

/**
 * Owns account attachment, People staging, and contact projections. SMS/claim/arm state is
 * intentionally outside this DAO so a contact import cannot accidentally cross the send barrier.
 */
@Dao
abstract class PeopleSyncDao {
  @Query("SELECT * FROM accounts_v2 WHERE activeSlot = 1 LIMIT 1")
  abstract suspend fun activeAccount(): AccountRecordEntity?

  @Query("SELECT * FROM contact_sync_state_v2 WHERE accountId = :accountId")
  abstract suspend fun contactSyncState(accountId: String): ContactSyncStateEntity?

  @Query("SELECT * FROM people_sync_generations_v2 WHERE generationId = :generationId")
  abstract suspend fun generation(generationId: String): PeopleSyncGenerationEntity?

  @Query(
    "SELECT * FROM contact_snapshots_v2 WHERE accountId = :accountId " +
      "AND sourceFingerprint IN (:fingerprints)",
  )
  abstract suspend fun contactsBySourceFingerprints(
    accountId: String,
    fingerprints: List<String>,
  ): List<ContactSnapshotEntity>

  @Query("SELECT * FROM contact_phones_v2 WHERE contactId IN (:contactIds)")
  abstract suspend fun phonesForContacts(contactIds: List<String>): List<ContactPhoneEntity>

  @Query("SELECT * FROM recipient_policies_v2 WHERE contactId IN (:contactIds)")
  abstract suspend fun policiesForContacts(contactIds: List<String>): List<RecipientPolicyEntity>

  @Query("SELECT * FROM recipient_policies_v2 WHERE contactId = :contactId")
  abstract suspend fun recipientPolicy(contactId: String): RecipientPolicyEntity?

  @Query("SELECT * FROM contact_phones_v2 WHERE contactId = :contactId ORDER BY phoneId")
  abstract suspend fun contactPhones(contactId: String): List<ContactPhoneEntity>

  @Query("SELECT * FROM approval_snapshots_v2 WHERE approvalId = :approvalId")
  abstract suspend fun approval(approvalId: String): ApprovalSnapshotEntity?

  @Query(
    """
    SELECT * FROM contact_snapshots_v2 c
    WHERE c.accountId = :accountId
      AND (c.state != 'DELETED' OR EXISTS(
        SELECT 1 FROM recipient_policies_v2 visible_policy
        WHERE visible_policy.contactId = c.contactId AND visible_policy.state != 'OFF'
      ))
      AND (:searchPattern = '%' OR c.displayName LIKE :searchPattern ESCAPE '\')
      AND (
        :filter = 'all'
        OR (:filter = 'enabled' AND EXISTS(
          SELECT 1 FROM recipient_policies_v2 p
          WHERE p.contactId = c.contactId AND p.state = 'ENABLED'
        ))
        OR (:filter = 'excluded' AND EXISTS(
          SELECT 1 FROM recipient_policies_v2 p
          WHERE p.contactId = c.contactId AND p.state = 'EXCLUDED'
        ))
        OR (:filter = 'ready' AND c.state = 'ACTIVE'
          AND c.birthdayMonth IS NOT NULL AND c.birthdayDay IS NOT NULL
          AND EXISTS(SELECT 1 FROM contact_phones_v2 ph
            WHERE ph.contactId = c.contactId AND ph.state = 'READY'))
        OR (:filter = 'needs-attention' AND (
          c.state != 'ACTIVE' OR c.birthdayMonth IS NULL OR c.birthdayDay IS NULL
          OR NOT EXISTS(SELECT 1 FROM contact_phones_v2 ph
            WHERE ph.contactId = c.contactId AND ph.state = 'READY')
        ))
      )
    ORDER BY c.displayName COLLATE NOCASE, c.contactId
    LIMIT :limit OFFSET :offset
    """,
  )
  abstract suspend fun contactPage(
    accountId: String,
    filter: String,
    searchPattern: String,
    limit: Int,
    offset: Int,
  ): List<ContactSnapshotEntity>

  @Query(
    """
    SELECT COUNT(*) FROM contact_snapshots_v2 c
    WHERE c.accountId = :accountId
      AND (c.state != 'DELETED' OR EXISTS(
        SELECT 1 FROM recipient_policies_v2 visible_policy
        WHERE visible_policy.contactId = c.contactId AND visible_policy.state != 'OFF'
      ))
      AND (:searchPattern = '%' OR c.displayName LIKE :searchPattern ESCAPE '\')
      AND (
        :filter = 'all'
        OR (:filter = 'enabled' AND EXISTS(
          SELECT 1 FROM recipient_policies_v2 p
          WHERE p.contactId = c.contactId AND p.state = 'ENABLED'
        ))
        OR (:filter = 'excluded' AND EXISTS(
          SELECT 1 FROM recipient_policies_v2 p
          WHERE p.contactId = c.contactId AND p.state = 'EXCLUDED'
        ))
        OR (:filter = 'ready' AND c.state = 'ACTIVE'
          AND c.birthdayMonth IS NOT NULL AND c.birthdayDay IS NOT NULL
          AND EXISTS(SELECT 1 FROM contact_phones_v2 ph
            WHERE ph.contactId = c.contactId AND ph.state = 'READY'))
        OR (:filter = 'needs-attention' AND (
          c.state != 'ACTIVE' OR c.birthdayMonth IS NULL OR c.birthdayDay IS NULL
          OR NOT EXISTS(SELECT 1 FROM contact_phones_v2 ph
            WHERE ph.contactId = c.contactId AND ph.state = 'READY')
        ))
      )
    """,
  )
  abstract suspend fun contactCount(
    accountId: String,
    filter: String,
    searchPattern: String,
  ): Int

  @Query(
    "SELECT COUNT(*) FROM contact_snapshots_v2 WHERE accountId = :accountId AND state = 'ACTIVE'",
  )
  abstract suspend fun activeContactCount(accountId: String): Int

  @Query("SELECT * FROM app_control WHERE singletonId = 1")
  protected abstract suspend fun controlRow(): ControlEntity?

  @Query("SELECT * FROM accounts_v2 WHERE firebaseUid = :firebaseUid LIMIT 1")
  protected abstract suspend fun accountByFirebaseUid(firebaseUid: String): AccountRecordEntity?

  @Upsert
  protected abstract suspend fun upsertAccountRow(account: AccountRecordEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertSyncStateRow(state: ContactSyncStateEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertGenerationRow(generation: PeopleSyncGenerationEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertStagingContactRows(contacts: List<PeopleStagingContactEntity>)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertStagingPhoneRows(phones: List<PeopleStagingPhoneEntity>)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertStagingBirthdayRows(
    birthdays: List<PeopleStagingBirthdayEntity>,
  )

  @Upsert
  protected abstract suspend fun upsertContactRow(contact: ContactSnapshotEntity)

  @Upsert
  protected abstract suspend fun upsertPhoneRow(phone: ContactPhoneEntity)

  @Upsert
  protected abstract suspend fun upsertBirthdayChoiceRow(choice: ContactBirthdayChoiceEntity)

  @Upsert
  protected abstract suspend fun upsertRecipientPolicyRow(policy: RecipientPolicyEntity)

  @Query("DELETE FROM people_sync_generations_v2 WHERE generationId = :generationId")
  protected abstract suspend fun deleteGenerationRow(generationId: String): Int

  @Query("DELETE FROM people_sync_generations_v2 WHERE startedAtMillis <= :cutoffMillis")
  protected abstract suspend fun deleteExpiredGenerationRows(cutoffMillis: Long): Int

  @Query("DELETE FROM people_sync_generations_v2 WHERE startedAtMillis > :currentWallMillis")
  protected abstract suspend fun deleteFutureGenerationRows(currentWallMillis: Long): Int

  @Query(
    """
    UPDATE contact_sync_state_v2
    SET stagingGeneration = NULL,
        lastErrorCode = 'STALE_STAGING_RECLAIMED',
        revision = revision + 1
    WHERE stagingGeneration IS NOT NULL
      AND revision < 9223372036854775807
      AND NOT EXISTS(
        SELECT 1 FROM people_sync_generations_v2 g
        WHERE g.generationId = contact_sync_state_v2.stagingGeneration
      )
    """,
  )
  protected abstract suspend fun clearDanglingStagingRows(): Int

  @Query(
    """
    UPDATE contact_sync_state_v2
    SET stagingGeneration = :generationId,
        lastAttemptMillis = :startedAtMillis,
        lastErrorCode = NULL,
        revision = revision + 1
    WHERE accountId = :accountId
      AND revision = :expectedRevision
      AND revision < 9223372036854775807
      AND stagingGeneration IS NULL
    """,
  )
  protected abstract suspend fun startStagingCas(
    accountId: String,
    expectedRevision: Long,
    generationId: String,
    startedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE people_sync_generations_v2
    SET nextPageIndex = nextPageIndex + 1,
        stagedContactCount = stagedContactCount + :contactCount
    WHERE generationId = :generationId
      AND nextPageIndex = :expectedPageIndex
      AND stagedContactCount <= :maximumPreviousCount
    """,
  )
  protected abstract suspend fun advanceGenerationCas(
    generationId: String,
    expectedPageIndex: Int,
    contactCount: Int,
    maximumPreviousCount: Int,
  ): Int

  @Query("SELECT COUNT(*) FROM people_staging_contacts_v2 WHERE generationId = :generationId")
  protected abstract suspend fun stagedContactCount(generationId: String): Int

  @Query(
    """
    SELECT * FROM people_staging_contacts_v2
    WHERE generationId = :generationId
    ORDER BY stagingContactId
    LIMIT :limit OFFSET :offset
    """,
  )
  protected abstract suspend fun stagedContactPage(
    generationId: String,
    limit: Int,
    offset: Int,
  ): List<PeopleStagingContactEntity>

  @Query("SELECT * FROM people_staging_phones_v2 WHERE stagingContactId IN (:stagingContactIds)")
  protected abstract suspend fun stagedPhonesForContacts(
    stagingContactIds: List<String>,
  ): List<PeopleStagingPhoneEntity>

  @Query("SELECT * FROM people_staging_birthdays_v4 WHERE stagingContactId IN (:stagingContactIds)")
  protected abstract suspend fun stagedBirthdaysForContacts(
    stagingContactIds: List<String>,
  ): List<PeopleStagingBirthdayEntity>

  @Query(
    "SELECT * FROM contact_snapshots_v2 WHERE accountId = :accountId " +
      "AND sourceFingerprint = :sourceFingerprint LIMIT 1",
  )
  protected abstract suspend fun activeContactBySource(
    accountId: String,
    sourceFingerprint: String,
  ): ContactSnapshotEntity?

  @Query(
    "SELECT * FROM contact_snapshots_v2 WHERE accountId = :accountId " +
      "AND peopleResourceName = :resourceName LIMIT 1",
  )
  protected abstract suspend fun activeContactByResourceName(
    accountId: String,
    resourceName: String,
  ): ContactSnapshotEntity?

  @Query("SELECT * FROM contact_phones_v2 WHERE contactId = :contactId")
  protected abstract suspend fun activePhones(contactId: String): List<ContactPhoneEntity>

  @Query("SELECT * FROM contact_birthday_choices_v4 WHERE contactId = :contactId")
  protected abstract suspend fun activeBirthdays(
    contactId: String,
  ): List<ContactBirthdayChoiceEntity>

  @Query("DELETE FROM contact_birthday_choices_v4 WHERE contactId = :contactId")
  protected abstract suspend fun deleteAllBirthdayChoices(contactId: String): Int

  @Query(
    "DELETE FROM contact_birthday_choices_v4 WHERE contactId = :contactId " +
      "AND birthdayId NOT IN (:retainedIds)",
  )
  protected abstract suspend fun deleteMissingBirthdayChoices(
    contactId: String,
    retainedIds: List<String>,
  ): Int

  @Query("SELECT * FROM recipient_policies_v2 WHERE contactId = :contactId")
  protected abstract suspend fun activePolicy(contactId: String): RecipientPolicyEntity?

  @Query(
    """
    SELECT * FROM contact_snapshots_v2 c
    WHERE c.accountId = :accountId
      AND c.state != 'DELETED'
      AND NOT EXISTS(
        SELECT 1 FROM people_staging_contacts_v2 s
        WHERE s.generationId = :generationId
          AND s.sourceFingerprint = c.sourceFingerprint
      )
    ORDER BY c.contactId
    LIMIT :limit
    """,
  )
  protected abstract suspend fun fullSyncOmissions(
    accountId: String,
    generationId: String,
    limit: Int,
  ): List<ContactSnapshotEntity>

  @Query(
    """
    UPDATE approval_snapshots_v2
    SET state = 'INVALIDATED',
        invalidatedAtMillis = COALESCE(invalidatedAtMillis, :atMillis),
        invalidationReason = COALESCE(invalidationReason, :reason)
    WHERE contactId = :contactId AND state = 'ACTIVE'
    """,
  )
  protected abstract suspend fun invalidateActiveApprovals(
    contactId: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Query(
    """
    UPDATE contact_sync_state_v2
    SET activeGeneration = :generationId,
        stagingGeneration = NULL,
        syncToken = :syncToken,
        parametersHash = :parametersHash,
        freshness = 'FRESH',
        lastFullSuccessMillis = CASE WHEN :mode = 'FULL' THEN :completedAtMillis
          ELSE lastFullSuccessMillis END,
        lastIncrementalSuccessMillis = CASE WHEN :mode = 'INCREMENTAL' THEN :completedAtMillis
          ELSE lastIncrementalSuccessMillis END,
        lastAttemptMillis = :completedAtMillis,
        lastErrorCode = NULL,
        revision = revision + 1
    WHERE accountId = :accountId
      AND revision = :expectedRevision
      AND revision < 9223372036854775807
      AND stagingGeneration = :generationId
    """,
  )
  protected abstract suspend fun commitSyncStateCas(
    accountId: String,
    expectedRevision: Long,
    generationId: String,
    syncToken: String,
    parametersHash: String,
    mode: String,
    completedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE contact_sync_state_v2
    SET stagingGeneration = NULL,
        lastErrorCode = 'SYNC_ABORTED',
        revision = revision + 1
    WHERE accountId = :accountId
      AND revision = :expectedRevision
      AND revision < 9223372036854775807
      AND stagingGeneration = :generationId
    """,
  )
  protected abstract suspend fun rollbackSyncStateCas(
    accountId: String,
    expectedRevision: Long,
    generationId: String,
  ): Int

  @Query(
    """
    UPDATE contact_sync_state_v2
    SET freshness = :freshness,
        lastErrorCode = :safeErrorCode,
        revision = revision + 1
    WHERE accountId = :accountId
      AND revision = :expectedRevision
      AND revision < 9223372036854775807
      AND stagingGeneration IS NULL
    """,
  )
  protected abstract suspend fun recordSyncFailureCas(
    accountId: String,
    expectedRevision: Long,
    freshness: SyncFreshness,
    safeErrorCode: String,
  ): Int

  @Query(
    """
    UPDATE app_control SET revision = revision + 1
    WHERE singletonId = 1 AND revision = :expectedRevision
      AND revision < 9223372036854775807
    """,
  )
  protected abstract suspend fun bumpControlRevisionCas(expectedRevision: Long): Int

  @Query(
    """
    UPDATE app_control
    SET revision = revision + 1, blockerRevision = blockerRevision + 1
    WHERE singletonId = 1 AND revision = :expectedRevision
      AND revision < 9223372036854775807
      AND blockerRevision < 9223372036854775807
    """,
  )
  protected abstract suspend fun bumpControlBlockerCas(expectedRevision: Long): Int

  @Query(
    """
    UPDATE test_receipts_v2
    SET state = 'INVALIDATED',
        invalidatedAtMillis = COALESCE(invalidatedAtMillis, :atMillis),
        invalidationReason = COALESCE(invalidationReason, :reason)
    WHERE accountId = :accountId AND state = 'VALID'
    """,
  )
  protected abstract suspend fun invalidatePassingTestReceipts(
    accountId: String,
    atMillis: Long,
    reason: String,
  ): Int

  @Transaction
  open suspend fun attachIdentity(
    candidate: AccountRecordEntity,
    parameterFingerprint: String,
  ): IdentityAttachDecision {
    if (candidate.activeSlot != 1 || candidate.state != AccountRecordState.ACTIVE) {
      return IdentityAttachDecision.STORAGE_REJECTED
    }
    val control = controlRow() ?: return IdentityAttachDecision.STORAGE_REJECTED
    val active = activeAccount()
    if (
      active != null &&
      (active.state !in setOf(AccountRecordState.ACTIVE, AccountRecordState.RETAINED_SIGNED_OUT) ||
        active.firebaseUid != candidate.firebaseUid ||
        active.googleSubjectHash != candidate.googleSubjectHash ||
        active.accountId != candidate.accountId)
    ) {
      return IdentityAttachDecision.ACCOUNT_CONFLICT
    }
    val existing = accountByFirebaseUid(candidate.firebaseUid)
    if (
      existing != null &&
      (existing.state !in setOf(AccountRecordState.ACTIVE, AccountRecordState.RETAINED_SIGNED_OUT) ||
        existing.googleSubjectHash != candidate.googleSubjectHash ||
        existing.accountId != candidate.accountId)
    ) {
      return IdentityAttachDecision.ACCOUNT_CONFLICT
    }
    val nextRevision = existing?.revision?.incrementExactOrNull()
      ?: if (existing == null) 0L else return IdentityAttachDecision.STORAGE_REJECTED
    upsertAccountRow(
      candidate.copy(
        revision = nextRevision,
        createdAtMillis = existing?.createdAtMillis ?: candidate.createdAtMillis,
      ),
    )
    if (contactSyncState(candidate.accountId) == null) {
      insertSyncStateRow(
        ContactSyncStateEntity(
          accountId = candidate.accountId,
          activeGeneration = null,
          stagingGeneration = null,
          syncToken = null,
          parametersHash = parameterFingerprint,
          freshness = SyncFreshness.NEVER_SYNCED,
          lastFullSuccessMillis = null,
          lastIncrementalSuccessMillis = null,
          lastAttemptMillis = null,
          lastErrorCode = null,
          revision = 0,
        ),
      )
    }
    check(bumpControlBlockerCas(control.revision) == 1) { "identity-control-cas-lost" }
    return IdentityAttachDecision.ATTACHED
  }

  @Transaction
  open suspend fun beginGeneration(
    generation: PeopleSyncGenerationEntity,
    requestedSyncToken: String?,
    staleCutoffMillis: Long,
  ): Boolean {
    if (generation.startedAtMillis <= staleCutoffMillis) return false
    deleteExpiredGenerationRows(staleCutoffMillis)
    // A wall-clock rollback must not extend retention indefinitely. The displaced generation is
    // discarded; its coordinator then loses CAS and cannot publish partial data.
    deleteFutureGenerationRows(generation.startedAtMillis)
    val reclaimed = clearDanglingStagingRows()
    val control = controlRow() ?: return false
    if (reclaimed > 0) {
      if (bumpControlRevisionCas(control.revision) != 1) return false
    }
    val refreshedControl = controlRow() ?: return false
    val account = activeAccount() ?: return false
    if (
      account.accountId != generation.accountId ||
      account.state != AccountRecordState.ACTIVE ||
      account.activeSlot != 1
    ) return false
    val state = contactSyncState(generation.accountId) ?: return false
    if (state.stagingGeneration != null || generation.nextPageIndex != 0 || generation.stagedContactCount != 0) {
      return false
    }
    when (generation.mode) {
      MODE_FULL -> Unit
      MODE_INCREMENTAL -> if (
        state.activeGeneration == null ||
        generation.baseActiveGeneration != state.activeGeneration ||
        state.syncToken != requestedSyncToken ||
        requestedSyncToken.isNullOrBlank() ||
        state.parametersHash != generation.parameterFingerprint
      ) return false
      else -> return false
    }
    val expectedSyncRevision = state.revision.incrementExactOrNull() ?: return false
    val persistedGeneration = generation.copy(expectedSyncRevision = expectedSyncRevision)
    if (
      startStagingCas(
        state.accountId,
        state.revision,
        generation.generationId,
        generation.startedAtMillis,
      ) != 1
    ) return false
    insertGenerationRow(persistedGeneration)
    check(bumpControlRevisionCas(refreshedControl.revision) == 1) { "sync-begin-control-cas-lost" }
    return true
  }

  @Transaction
  open suspend fun stagePreparedPage(
    generationId: String,
    pageIndex: Int,
    contacts: List<PeopleStagingContactEntity>,
    phones: List<PeopleStagingPhoneEntity>,
    birthdays: List<PeopleStagingBirthdayEntity>,
  ): Boolean {
    if (pageIndex < 0 || contacts.size > MAX_PAGE_CONTACTS) return false
    val generation = generation(generationId) ?: return false
    val state = contactSyncState(generation.accountId) ?: return false
    if (
      state.stagingGeneration != generationId ||
      state.revision != generation.expectedSyncRevision ||
      generation.nextPageIndex != pageIndex ||
      contacts.any { it.generationId != generationId || it.accountId != generation.accountId } ||
      contacts.map(PeopleStagingContactEntity::stagingContactId).toSet().size != contacts.size ||
      contacts.map(PeopleStagingContactEntity::sourceFingerprint).toSet().size != contacts.size
    ) return false
    val contactKeys = contacts.mapTo(hashSetOf(), PeopleStagingContactEntity::stagingContactId)
    if (
      phones.any { it.generationId != generationId || it.stagingContactId !in contactKeys } ||
      phones.map(PeopleStagingPhoneEntity::stagingPhoneId).toSet().size != phones.size ||
      phones.map(PeopleStagingPhoneEntity::phoneId).toSet().size != phones.size
    ) return false
    if (
      birthdays.any {
        it.generationId != generationId || it.stagingContactId !in contactKeys
      } ||
      birthdays.map(PeopleStagingBirthdayEntity::stagingBirthdayId).toSet().size !=
      birthdays.size ||
      birthdays.map(PeopleStagingBirthdayEntity::birthdayId).toSet().size != birthdays.size
    ) return false
    if (contacts.isNotEmpty()) insertStagingContactRows(contacts)
    if (phones.isNotEmpty()) insertStagingPhoneRows(phones)
    if (birthdays.isNotEmpty()) insertStagingBirthdayRows(birthdays)
    val maximumPrevious = Int.MAX_VALUE - contacts.size
    check(
      advanceGenerationCas(
        generationId,
        pageIndex,
        contacts.size,
        maximumPrevious,
      ) == 1,
    ) { "sync-page-cas-lost" }
    return true
  }

  @Transaction
  open suspend fun rollbackGeneration(generationId: String): Boolean {
    val generation = generation(generationId) ?: return true
    val state = contactSyncState(generation.accountId) ?: return false
    if (
      state.stagingGeneration != generationId ||
      state.revision != generation.expectedSyncRevision
    ) return false
    val control = controlRow() ?: return false
    check(
      rollbackSyncStateCas(
        generation.accountId,
        generation.expectedSyncRevision,
        generationId,
      ) == 1,
    ) { "sync-rollback-state-cas-lost" }
    check(deleteGenerationRow(generationId) == 1) { "sync-rollback-generation-missing" }
    check(bumpControlRevisionCas(control.revision) == 1) { "sync-rollback-control-cas-lost" }
    return true
  }

  @Transaction
  open suspend fun commitGeneration(
    generationId: String,
    nextSyncToken: String,
    parameterFingerprint: String,
    changedPeople: Int,
    pages: Int,
    completedAtMillis: Long,
  ): PeopleCommitResult? {
    if (
      nextSyncToken.isBlank() || nextSyncToken.length > MAX_SYNC_TOKEN_LENGTH ||
      nextSyncToken.any { it.isISOControl() || it.isWhitespace() } ||
      !SHA256.matches(parameterFingerprint) ||
      changedPeople < 0 || pages <= 0 || completedAtMillis < 0
    ) return null
    val generation = generation(generationId) ?: return null
    val state = contactSyncState(generation.accountId) ?: return null
    if (
      state.stagingGeneration != generationId ||
      state.revision != generation.expectedSyncRevision ||
      generation.parameterFingerprint != parameterFingerprint ||
      generation.nextPageIndex != pages ||
      generation.stagedContactCount != changedPeople ||
      stagedContactCount(generationId) != changedPeople
    ) return null
    var offset = 0
    var materialChanges = 0
    var invalidatedApprovals = 0
    while (true) {
      val staged = stagedContactPage(generationId, COMMIT_CHUNK_SIZE, offset)
      if (staged.isEmpty()) break
      val phones = stagedPhonesForContacts(staged.map(PeopleStagingContactEntity::stagingContactId))
        .groupBy(PeopleStagingPhoneEntity::stagingContactId)
      val birthdays = stagedBirthdaysForContacts(
        staged.map(PeopleStagingContactEntity::stagingContactId),
      ).groupBy(PeopleStagingBirthdayEntity::stagingContactId)
      for (contact in staged) {
        val result = applyStagedContact(
          contact,
          phones[contact.stagingContactId].orEmpty(),
          birthdays[contact.stagingContactId].orEmpty(),
          generationId,
          completedAtMillis,
        ) ?: throw PeopleCommitRejectedException()
        materialChanges += result.materialChanges
        invalidatedApprovals += result.invalidatedApprovals
      }
      offset += staged.size
    }
    if (generation.mode == MODE_FULL) {
      while (true) {
        val omitted = fullSyncOmissions(generation.accountId, generationId, COMMIT_CHUNK_SIZE)
        if (omitted.isEmpty()) break
        omitted.forEach { existing ->
          val result = tombstoneExisting(existing, generationId, completedAtMillis)
          materialChanges += result.materialChanges
          invalidatedApprovals += result.invalidatedApprovals
        }
      }
    }
    val control = controlRow() ?: throw PeopleCommitRejectedException()
    check(
      commitSyncStateCas(
        generation.accountId,
        generation.expectedSyncRevision,
        generationId,
        nextSyncToken,
        parameterFingerprint,
        generation.mode,
        completedAtMillis,
      ) == 1,
    ) { "sync-commit-state-cas-lost" }
    check(deleteGenerationRow(generationId) == 1) { "sync-commit-generation-missing" }
    if (materialChanges > 0) {
      // Test receipts are configuration-bound activation evidence. A provider-side contact,
      // phone, or birthday change can invalidate an approved recipient without passing through
      // the foreground configuration controller, so revoke the evidence in this same commit.
      invalidatePassingTestReceipts(
        generation.accountId,
        completedAtMillis,
        CONTACT_SYNC_MATERIAL_CHANGED,
      )
      check(bumpControlBlockerCas(control.revision) == 1) { "sync-commit-blocker-cas-lost" }
    } else {
      check(bumpControlRevisionCas(control.revision) == 1) { "sync-commit-control-cas-lost" }
    }
    return PeopleCommitResult(materialChanges, invalidatedApprovals)
  }

  @Transaction
  open suspend fun recordSyncFailure(
    accountId: String,
    safeErrorCode: String,
    authorizationRequired: Boolean,
  ): Boolean {
    if (!SAFE_ERROR_CODE.matches(safeErrorCode)) return false
    val state = contactSyncState(accountId) ?: return false
    if (state.stagingGeneration != null) return false
    val freshness = when {
      authorizationRequired -> SyncFreshness.AUTH_ACTION_REQUIRED
      state.activeGeneration != null -> SyncFreshness.STALE_WARNING
      else -> SyncFreshness.NEVER_SYNCED
    }
    val control = controlRow() ?: return false
    if (
      recordSyncFailureCas(accountId, state.revision, freshness, safeErrorCode) != 1
    ) return false
    check(bumpControlRevisionCas(control.revision) == 1) { "sync-failure-control-cas-lost" }
    return true
  }

  private suspend fun applyStagedContact(
    staged: PeopleStagingContactEntity,
    stagedPhones: List<PeopleStagingPhoneEntity>,
    stagedBirthdays: List<PeopleStagingBirthdayEntity>,
    generationId: String,
    atMillis: Long,
  ): PeopleCommitResult? {
    if (staged.generationId != generationId) return null
    val existing = activeContactBySource(staged.accountId, staged.sourceFingerprint)
    if (existing != null && existing.contactId != staged.contactId) return null
    val resourceCollision = activeContactByResourceName(staged.accountId, staged.peopleResourceName)
    if (resourceCollision != null && resourceCollision.contactId != staged.contactId) return null
    if (staged.deleted) {
      return existing?.let { tombstoneExisting(it, generationId, atMillis) }
        ?: PeopleCommitResult(0, 0)
    }
    val displayName = staged.displayName ?: SAFE_UNKNOWN_CONTACT_LABEL
    val materialChanged = existing == null ||
      existing.state != ContactSnapshotState.ACTIVE ||
      existing.sourceEtag != digestMarker(staged.materialDigest)
    val materialRevision = when {
      existing == null -> 1L
      materialChanged -> existing.materialRevision.incrementExactOrNull() ?: return null
      else -> existing.materialRevision
    }
    val previousPhones = existing?.let { activePhones(it.contactId) }.orEmpty()
    val previousBirthdays = existing?.let { activeBirthdays(it.contactId) }.orEmpty()
    val incomingPhoneIds = stagedPhones.mapTo(hashSetOf(), PeopleStagingPhoneEntity::phoneId)
    // The parent must exist before a new generation's phone rows can satisfy their foreign key.
    upsertContactRow(
      ContactSnapshotEntity(
        contactId = staged.contactId,
        accountId = staged.accountId,
        peopleResourceName = staged.peopleResourceName,
        sourceFingerprint = staged.sourceFingerprint,
        sourceEtag = digestMarker(staged.materialDigest),
        displayName = displayName,
        safeGivenName = staged.safeGivenName,
        birthdayMonth = staged.birthdayMonth,
        birthdayDay = staged.birthdayDay,
        birthdayYear = staged.birthdayYear,
        leapDayPolicy = staged.leapDayPolicy,
        state = ContactSnapshotState.ACTIVE,
        syncGeneration = generationId,
        materialRevision = materialRevision,
        sourceUpdatedAtMillis = atMillis,
        syncedAtMillis = atMillis,
        deletedAtMillis = null,
      ),
    )
    stagedPhones.forEach { incoming ->
      if (
        incoming.contactId != staged.contactId ||
        incoming.generationId != generationId
      ) return null
      val previous = previousPhones.singleOrNull { it.phoneId == incoming.phoneId }
      val phoneChanged = previous == null || !samePhoneMaterial(previous, incoming)
      val phoneRevision = when {
        previous == null -> 1L
        phoneChanged -> previous.materialRevision.incrementExactOrNull() ?: return null
        else -> previous.materialRevision
      }
      upsertPhoneRow(
        ContactPhoneEntity(
          phoneId = incoming.phoneId,
          contactId = staged.contactId,
          sourceFingerprint = incoming.sourceFingerprint,
          rawNumber = incoming.rawNumber,
          normalizedE164 = incoming.normalizedE164,
          destinationFingerprint = incoming.destinationFingerprint,
          maskedDisplay = incoming.maskedDisplay,
          typeLabel = incoming.typeLabel,
          regionCode = incoming.regionCode,
          isSmsCapableType = incoming.isSmsCapableType,
          state = incoming.state,
          materialRevision = phoneRevision,
          updatedAtMillis = atMillis,
        ),
      )
    }
    previousPhones.filter { it.phoneId !in incomingPhoneIds && it.state != PhoneRecordState.DELETED }
      .forEach { previous ->
        val revision = previous.materialRevision.incrementExactOrNull() ?: return null
        upsertPhoneRow(
          previous.copy(
            state = PhoneRecordState.DELETED,
            materialRevision = revision,
            updatedAtMillis = atMillis,
          ),
        )
      }
    val incomingBirthdayIds = stagedBirthdays.mapTo(hashSetOf(), PeopleStagingBirthdayEntity::birthdayId)
    stagedBirthdays.forEach { incoming ->
      if (
        incoming.contactId != staged.contactId ||
        incoming.generationId != generationId
      ) return null
      val previous = previousBirthdays.singleOrNull { it.birthdayId == incoming.birthdayId }
      val changed = previous == null ||
        previous.sourceFingerprint != incoming.sourceFingerprint ||
        previous.birthdayYear != incoming.birthdayYear ||
        previous.birthdayMonth != incoming.birthdayMonth ||
        previous.birthdayDay != incoming.birthdayDay ||
        previous.selectable != incoming.selectable ||
        previous.issueCode != incoming.issueCode
      val revision = when {
        previous == null -> 1L
        changed -> previous.materialRevision.incrementExactOrNull() ?: return null
        else -> previous.materialRevision
      }
      upsertBirthdayChoiceRow(
        ContactBirthdayChoiceEntity(
          birthdayId = incoming.birthdayId,
          contactId = incoming.contactId,
          sourceFingerprint = incoming.sourceFingerprint,
          birthdayYear = incoming.birthdayYear,
          birthdayMonth = incoming.birthdayMonth,
          birthdayDay = incoming.birthdayDay,
          selectable = incoming.selectable,
          issueCode = incoming.issueCode,
          materialRevision = revision,
          updatedAtMillis = atMillis,
        ),
      )
    }
    if (incomingBirthdayIds.isEmpty()) {
      deleteAllBirthdayChoices(staged.contactId)
    } else {
      deleteMissingBirthdayChoices(staged.contactId, incomingBirthdayIds.toList())
    }
    val previousPolicy = activePolicy(staged.contactId)
    if (previousPolicy == null) {
      upsertRecipientPolicyRow(
        RecipientPolicyEntity(
          contactId = staged.contactId,
          chosenPhoneId = staged.selectedPhoneId,
          state = RecipientEnrollmentState.OFF,
          explicitEnrollmentEventId = null,
          blockReason = null,
          approvalId = null,
          revision = 0,
          enabledAtMillis = null,
          updatedAtMillis = atMillis,
          chosenBirthdayId = staged.selectedBirthdayId,
        ),
      )
    } else if (materialChanged) {
      val nextRevision = previousPolicy.revision.incrementExactOrNull() ?: return null
      upsertRecipientPolicyRow(
        previousPolicy.copy(
          chosenPhoneId = staged.selectedPhoneId,
          chosenBirthdayId = staged.selectedBirthdayId,
          state = when (previousPolicy.state) {
            RecipientEnrollmentState.OFF -> RecipientEnrollmentState.OFF
            RecipientEnrollmentState.EXCLUDED -> RecipientEnrollmentState.EXCLUDED
            RecipientEnrollmentState.ENABLED,
            RecipientEnrollmentState.PAUSED,
            RecipientEnrollmentState.BLOCKED,
            RecipientEnrollmentState.NEEDS_REVIEW,
            -> RecipientEnrollmentState.NEEDS_REVIEW
          },
          blockReason = if (previousPolicy.state == RecipientEnrollmentState.OFF) {
            null
          } else {
            CONTACT_SYNC_MATERIAL_CHANGED
          },
          approvalId = null,
          revision = nextRevision,
          enabledAtMillis = null,
          updatedAtMillis = atMillis,
        ),
      )
    }
    val invalidated = if (materialChanged) {
      invalidateActiveApprovals(
        staged.contactId,
        atMillis,
        invalidationReason(existing, staged, previousPhones, stagedPhones),
      )
    } else {
      0
    }
    return PeopleCommitResult(if (materialChanged) 1 else 0, invalidated)
  }

  private suspend fun tombstoneExisting(
    existing: ContactSnapshotEntity,
    generationId: String,
    atMillis: Long,
  ): PeopleCommitResult {
    if (existing.state == ContactSnapshotState.DELETED) {
      upsertContactRow(existing.copy(syncGeneration = generationId, syncedAtMillis = atMillis))
      return PeopleCommitResult(0, 0)
    }
    val materialRevision = existing.materialRevision.incrementExactOrNull()
      ?: error("contact-material-revision-exhausted")
    activePhones(existing.contactId)
      .filter { it.state != PhoneRecordState.DELETED }
      .forEach { phone ->
        val revision = phone.materialRevision.incrementExactOrNull()
          ?: error("phone-material-revision-exhausted")
        upsertPhoneRow(
          phone.copy(
            state = PhoneRecordState.DELETED,
            materialRevision = revision,
            updatedAtMillis = atMillis,
          ),
        )
      }
    deleteAllBirthdayChoices(existing.contactId)
    upsertContactRow(
      existing.copy(
        state = ContactSnapshotState.DELETED,
        syncGeneration = generationId,
        materialRevision = materialRevision,
        syncedAtMillis = atMillis,
        deletedAtMillis = atMillis,
      ),
    )
    activePolicy(existing.contactId)?.let { policy ->
      val revision = policy.revision.incrementExactOrNull()
        ?: error("recipient-policy-revision-exhausted")
      upsertRecipientPolicyRow(
        policy.copy(
          state = when (policy.state) {
            RecipientEnrollmentState.OFF -> RecipientEnrollmentState.OFF
            RecipientEnrollmentState.EXCLUDED -> RecipientEnrollmentState.EXCLUDED
            RecipientEnrollmentState.ENABLED,
            RecipientEnrollmentState.PAUSED,
            RecipientEnrollmentState.BLOCKED,
            RecipientEnrollmentState.NEEDS_REVIEW,
            -> RecipientEnrollmentState.BLOCKED
          },
          blockReason = CONTACT_SOURCE_DELETED,
          approvalId = null,
          revision = revision,
          enabledAtMillis = null,
          updatedAtMillis = atMillis,
        ),
      )
    }
    val invalidated = invalidateActiveApprovals(
      existing.contactId,
      atMillis,
      CONTACT_SOURCE_DELETED,
    )
    return PeopleCommitResult(1, invalidated)
  }

  private fun samePhoneMaterial(
    previous: ContactPhoneEntity,
    incoming: PeopleStagingPhoneEntity,
  ): Boolean =
    previous.sourceFingerprint == incoming.sourceFingerprint &&
      previous.rawNumber == incoming.rawNumber &&
      previous.normalizedE164 == incoming.normalizedE164 &&
      previous.destinationFingerprint == incoming.destinationFingerprint &&
      previous.maskedDisplay == incoming.maskedDisplay &&
      previous.typeLabel == incoming.typeLabel &&
      previous.regionCode == incoming.regionCode &&
      previous.isSmsCapableType == incoming.isSmsCapableType &&
      previous.state == incoming.state

  private fun invalidationReason(
    previous: ContactSnapshotEntity?,
    current: PeopleStagingContactEntity,
    previousPhones: List<ContactPhoneEntity>,
    currentPhones: List<PeopleStagingPhoneEntity>,
  ): String {
    if (previous == null) return CONTACT_SYNC_MATERIAL_CHANGED
    val reasons = buildList {
      if (
        previous.peopleResourceName != current.peopleResourceName ||
        previous.displayName != (current.displayName ?: SAFE_UNKNOWN_CONTACT_LABEL) ||
        previous.safeGivenName != current.safeGivenName
      ) add("NAME_CHANGED")
      if (
        previous.birthdayMonth != current.birthdayMonth ||
        previous.birthdayDay != current.birthdayDay ||
        previous.birthdayYear != current.birthdayYear ||
        previous.leapDayPolicy != current.leapDayPolicy
      ) add("BIRTHDAY_CHANGED")
      val previousMaterial = previousPhones.map(::phoneComparisonKey).sorted()
      val currentMaterial = currentPhones.map(::phoneComparisonKey).sorted()
      if (previousMaterial != currentMaterial) add("PHONE_CHANGED")
    }
    return reasons.ifEmpty { listOf(CONTACT_SYNC_MATERIAL_CHANGED) }.joinToString("+")
  }

  private fun phoneComparisonKey(phone: ContactPhoneEntity): String = listOf(
    phone.sourceFingerprint,
    phone.normalizedE164.orEmpty(),
    phone.destinationFingerprint.orEmpty(),
    phone.state.name,
  ).joinToString("|")

  private fun phoneComparisonKey(phone: PeopleStagingPhoneEntity): String = listOf(
    phone.sourceFingerprint,
    phone.normalizedE164.orEmpty(),
    phone.destinationFingerprint.orEmpty(),
    phone.state.name,
  ).joinToString("|")

  private fun digestMarker(digest: String): String = "local-sha256:$digest"

  private fun Long.incrementExactOrNull(): Long? = if (this == Long.MAX_VALUE) null else this + 1

  private class PeopleCommitRejectedException : RuntimeException() {
    override val message: String? = null
  }

  private companion object {
    const val MODE_FULL = "FULL"
    const val MODE_INCREMENTAL = "INCREMENTAL"
    const val MAX_PAGE_CONTACTS = 1_000
    const val COMMIT_CHUNK_SIZE = 250
    const val MAX_SYNC_TOKEN_LENGTH = 8_192
    const val SAFE_UNKNOWN_CONTACT_LABEL = "Contact"
    const val CONTACT_SYNC_MATERIAL_CHANGED = "CONTACT_SYNC_MATERIAL_CHANGED"
    const val CONTACT_SOURCE_DELETED = "CONTACT_SOURCE_DELETED"
    val SHA256 = Regex("^[0-9a-f]{64}$")
    val SAFE_ERROR_CODE = Regex("^[A-Z][A-Z0-9_]{2,63}$")
  }
}
