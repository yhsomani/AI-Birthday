package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "approvals",
  foreignKeys = [
    ForeignKey(
      entity = ContactEntity::class,
      parentColumns = ["localId"],
      childColumns = ["contactId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["contactId"], unique = true)],
)
data class ApprovalEntity(
  @PrimaryKey val approvalId: String,
  val contactId: String,
  val payloadHash: String,
  val exactMessage: String,
  val birthdayRule: String,
  val windowStartMinutes: Int,
  val windowEndMinutes: Int,
  val graceEndMinutes: Int?,
  val simPolicy: String,
  val segmentCount: Int,
  val approvedAtMillis: Long,
  val invalidatedAtMillis: Long?,
  val invalidationReason: String?,
)
