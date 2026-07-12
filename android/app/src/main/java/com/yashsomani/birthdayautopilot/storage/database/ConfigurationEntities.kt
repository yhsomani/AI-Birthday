package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A provider-derived birthday choice. Provider identifiers are never exposed; [birthdayId] is a
 * purpose-separated local opaque identifier and the whole database is protected by SQLCipher.
 */
@Entity(
  tableName = "contact_birthday_choices_v4",
  foreignKeys = [
    ForeignKey(
      entity = ContactSnapshotEntity::class,
      parentColumns = ["contactId"],
      childColumns = ["contactId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["contactId"]),
    Index(value = ["contactId", "sourceFingerprint"], unique = true),
    Index(value = ["selectable"]),
  ],
)
data class ContactBirthdayChoiceEntity(
  @PrimaryKey val birthdayId: String,
  val contactId: String,
  val sourceFingerprint: String,
  val birthdayYear: Int?,
  val birthdayMonth: Int?,
  val birthdayDay: Int?,
  val selectable: Boolean,
  val issueCode: String?,
  val materialRevision: Long,
  val updatedAtMillis: Long,
) {
  override fun toString(): String =
    "ContactBirthdayChoiceEntity(selectable=$selectable, revision=$materialRevision, values=<redacted>)"
}

/**
 * An encrypted, bounded, foreground review. The opaque primary key is the only value bridged to
 * JavaScript. Confirmation must atomically compare both revisions, mark the row consumed, and
 * apply the reviewed mutation in the same Room transaction.
 */
@Entity(
  tableName = "configuration_reviews_v4",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["accountId"]),
    Index(value = ["kind"]),
    Index(value = ["expiresAtMillis"]),
    Index(value = ["consumedAtMillis"]),
  ],
)
data class ConfigurationReviewEntity(
  @PrimaryKey val reviewId: String,
  val accountId: String,
  val kind: String,
  /** Strict, bounded JSON containing only the reviewed local material. */
  val payloadJson: String,
  val payloadHash: String,
  val controlRevision: Long,
  val blockerRevision: Long,
  val createdAtMillis: Long,
  val expiresAtMillis: Long,
  val consumedAtMillis: Long?,
) {
  override fun toString(): String =
    "ConfigurationReviewEntity(kind=$kind, revision=$controlRevision, values=<redacted>)"
}
