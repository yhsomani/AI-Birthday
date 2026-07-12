package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface BirthdayDao {
  @Query("SELECT * FROM app_control WHERE singletonId = 1")
  suspend fun getControl(): ControlEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertControl(control: ControlEntity): Long

  @Query(
    """
    UPDATE app_control
    SET revision = revision + 1,
        blockerRevision = blockerRevision + 1,
        accountMode = :accountMode,
        automationDesired = :automationDesired
    WHERE singletonId = 1 AND revision = :expectedRevision
    """,
  )
  suspend fun compareAndSetControl(
    expectedRevision: Long,
    accountMode: String,
    automationDesired: Boolean,
  ): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertContacts(contacts: List<ContactEntity>)

  @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE LIMIT :limit OFFSET :offset")
  suspend fun listContacts(limit: Int, offset: Int): List<ContactEntity>

  @Query("SELECT * FROM contacts WHERE localId = :localId")
  suspend fun getContact(localId: String): ContactEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertApproval(approval: ApprovalEntity)

  @Query("UPDATE approvals SET invalidatedAtMillis = :atMillis, invalidationReason = :reason WHERE contactId = :contactId AND invalidatedAtMillis IS NULL")
  suspend fun invalidateApproval(contactId: String, atMillis: Long, reason: String): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertOccurrence(occurrence: OccurrenceEntity)

  @Query("SELECT * FROM occurrences WHERE occurrenceId = :occurrenceId")
  suspend fun getOccurrence(occurrenceId: String): OccurrenceEntity?

  @Query(
    """
    UPDATE occurrences
    SET state = :nextState,
        attempt = :attempt,
        safeOutcomeCode = :safeOutcomeCode,
        terminalAtMillis = :terminalAtMillis
    WHERE occurrenceId = :occurrenceId
      AND state = :expectedState
      AND attempt = :expectedAttempt
    """,
  )
  suspend fun compareAndSetOccurrenceState(
    occurrenceId: String,
    expectedState: String,
    expectedAttempt: Int,
    nextState: String,
    attempt: Int,
    safeOutcomeCode: String?,
    terminalAtMillis: Long?,
  ): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertActivity(activity: ActivityEntity)

  @Query("SELECT * FROM activity ORDER BY recordedAtMillis DESC LIMIT :limit OFFSET :offset")
  suspend fun listActivity(limit: Int, offset: Int): List<ActivityEntity>

  @Query("DELETE FROM activity WHERE recordedAtMillis < :cutoffMillis")
  suspend fun deleteActivityBefore(cutoffMillis: Long): Int

  @Query("SELECT * FROM callback_counter WHERE singletonId = 1")
  suspend fun getCallbackCounter(): CallbackCounterEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertCallbackCounter(counter: CallbackCounterEntity): Long

  @Query(
    """
    UPDATE callback_counter
    SET nextPositiveId = nextPositiveId + 1
    WHERE singletonId = 1
      AND generation = :generation
      AND nextPositiveId = :expectedId
      AND nextPositiveId < 2147483647
    """,
  )
  suspend fun incrementCallbackId(generation: String, expectedId: Int): Int

  @Transaction
  suspend fun allocateCallbackId(generation: String): Int {
    val current = getCallbackCounter()
      ?: throw IllegalStateException("callback-counter-missing")
    check(current.generation == generation) { "callback-generation-mismatch" }
    check(current.nextPositiveId in 1 until Int.MAX_VALUE) { "callback-id-exhausted" }
    check(incrementCallbackId(generation, current.nextPositiveId) == 1) {
      "callback-id-contention"
    }
    return current.nextPositiveId
  }

  @Transaction
  suspend fun initializeIfAbsent(callbackGeneration: String) {
    insertControl(
      ControlEntity(
        revision = 0,
        blockerRevision = 0,
        accountMode = "PAUSED_REPAIR",
        automationDesired = false,
        activeInstallationEpoch = null,
        lastTrustedServerMillis = null,
        lastTrustedElapsedMillis = null,
        trustedBootCount = null,
        resetSafetyState = "CLEAR",
      ),
    )
    insertCallbackCounter(
      CallbackCounterEntity(
        generation = callbackGeneration,
        nextPositiveId = 1,
      ),
    )
  }
}
