package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A short-lived, encrypted generation used while a Google People response is being paged.
 *
 * Active contact rows are never reused for staging: their stable primary keys are referenced by
 * approvals and occurrences, and exposing a half-written generation would violate the sync
 * contract. A unique account index also makes overlapping sync attempts fail closed.
 */
@Entity(
  tableName = "people_sync_generations_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["accountId"], unique = true), Index(value = ["startedAtMillis"])],
)
data class PeopleSyncGenerationEntity(
  @PrimaryKey val generationId: String,
  val accountId: String,
  val mode: String,
  val baseActiveGeneration: String?,
  val expectedSyncRevision: Long,
  val parameterFingerprint: String,
  val startedAtMillis: Long,
  val nextPageIndex: Int,
  val stagedContactCount: Int,
) {
  override fun toString(): String =
    "PeopleSyncGenerationEntity(mode=$mode, pages=$nextPageIndex, values=<redacted>)"
}

/** A normalized contact delta. Provider JSON is deliberately never persisted. */
@Entity(
  tableName = "people_staging_contacts_v2",
  foreignKeys = [
    ForeignKey(
      entity = PeopleSyncGenerationEntity::class,
      parentColumns = ["generationId"],
      childColumns = ["generationId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["generationId"]),
    Index(value = ["generationId", "contactId"], unique = true),
    Index(value = ["generationId", "sourceFingerprint"], unique = true),
  ],
)
data class PeopleStagingContactEntity(
  @PrimaryKey val stagingContactId: String,
  val generationId: String,
  val accountId: String,
  val contactId: String,
  /** Encrypted by SQLCipher and never bridged to JavaScript. */
  val peopleResourceName: String,
  val sourceFingerprint: String,
  val displayName: String?,
  val safeGivenName: String?,
  val birthdayMonth: Int?,
  val birthdayDay: Int?,
  val birthdayYear: Int?,
  val leapDayPolicy: String?,
  val deleted: Boolean,
  val selectedPhoneId: String?,
  val selectedBirthdayId: String?,
  val readiness: String,
  /** Comma-separated internal enum names; never rendered or bridged directly. */
  val normalizationIssues: String,
  /** Purpose-separated digest of all approval-sensitive source material. */
  val materialDigest: String,
  val stagedAtMillis: Long,
) {
  override fun toString(): String =
    "PeopleStagingContactEntity(deleted=$deleted, readiness=$readiness, values=<redacted>)"
}

/** A normalized provider birthday retained only until its generation atomically commits. */
@Entity(
  tableName = "people_staging_birthdays_v4",
  foreignKeys = [
    ForeignKey(
      entity = PeopleStagingContactEntity::class,
      parentColumns = ["stagingContactId"],
      childColumns = ["stagingContactId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["stagingContactId"]),
    Index(value = ["generationId"]),
    Index(value = ["generationId", "birthdayId"], unique = true),
    Index(value = ["stagingContactId", "sourceFingerprint"], unique = true),
  ],
)
data class PeopleStagingBirthdayEntity(
  @PrimaryKey val stagingBirthdayId: String,
  val stagingContactId: String,
  val generationId: String,
  val birthdayId: String,
  val contactId: String,
  val sourceFingerprint: String,
  val birthdayYear: Int?,
  val birthdayMonth: Int?,
  val birthdayDay: Int?,
  val selectable: Boolean,
  val issueCode: String?,
  val stagedAtMillis: Long,
) {
  override fun toString(): String =
    "PeopleStagingBirthdayEntity(selectable=$selectable, values=<redacted>)"
}

/** A normalized phone field belonging to a staged contact. */
@Entity(
  tableName = "people_staging_phones_v2",
  foreignKeys = [
    ForeignKey(
      entity = PeopleStagingContactEntity::class,
      parentColumns = ["stagingContactId"],
      childColumns = ["stagingContactId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["stagingContactId"]),
    Index(value = ["generationId"]),
    Index(value = ["generationId", "phoneId"], unique = true),
    Index(value = ["stagingContactId", "sourceFingerprint"], unique = true),
  ],
)
data class PeopleStagingPhoneEntity(
  @PrimaryKey val stagingPhoneId: String,
  val stagingContactId: String,
  val generationId: String,
  val phoneId: String,
  val contactId: String,
  val sourceFingerprint: String,
  /** Encrypted by SQLCipher and never returned by a projection. */
  val rawNumber: String,
  val normalizedE164: String?,
  val destinationFingerprint: String?,
  val maskedDisplay: String,
  val typeLabel: String?,
  val regionCode: String?,
  val isSmsCapableType: Boolean,
  val state: PhoneRecordState,
  val stagedAtMillis: Long,
) {
  override fun toString(): String =
    "PeopleStagingPhoneEntity(state=$state, values=<redacted>)"
}
