package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "occurrences",
  foreignKeys = [
    ForeignKey(
      entity = ContactEntity::class,
      parentColumns = ["localId"],
      childColumns = ["contactId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["contactId"]),
    Index(value = ["localDate"]),
    Index(value = ["state"]),
    Index(value = ["idempotencyKey"], unique = true),
  ],
)
data class OccurrenceEntity(
  @PrimaryKey val occurrenceId: String,
  val contactId: String,
  val localDate: String,
  val timeZoneId: String,
  val approvalPayloadHash: String,
  val idempotencyKey: String,
  val state: String,
  val attempt: Int,
  val armStartBlockerRevision: Long?,
  val serverSubmitNotAfterMillis: Long?,
  val effectiveSubmitNotAfterMillis: Long?,
  val barrierConsumedAtMillis: Long?,
  val submittedAtMillis: Long?,
  val terminalAtMillis: Long?,
  val safeOutcomeCode: String?,
)
