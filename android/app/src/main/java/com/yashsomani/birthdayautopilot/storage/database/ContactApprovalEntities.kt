package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "contact_sync_state_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["activeGeneration"], unique = true)],
)
data class ContactSyncStateEntity(
  @PrimaryKey val accountId: String,
  val activeGeneration: String?,
  val stagingGeneration: String?,
  val syncToken: String?,
  val parametersHash: String,
  val freshness: SyncFreshness,
  val lastFullSuccessMillis: Long?,
  val lastIncrementalSuccessMillis: Long?,
  val lastAttemptMillis: Long?,
  val lastErrorCode: String?,
  val revision: Long,
) {
  override fun toString(): String =
    "ContactSyncStateEntity(freshness=$freshness, revision=$revision, values=<redacted>)"
}

@Entity(
  tableName = "contact_snapshots_v2",
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
    Index(value = ["accountId", "sourceFingerprint"], unique = true),
    Index(value = ["syncGeneration"]),
    Index(value = ["birthdayMonth", "birthdayDay"]),
    Index(value = ["state"]),
  ],
)
data class ContactSnapshotEntity(
  @PrimaryKey val contactId: String,
  val accountId: String,
  /** Encrypted at rest by SQLCipher; never used as a server key without purpose-separated hashing. */
  val peopleResourceName: String,
  val sourceFingerprint: String,
  val sourceEtag: String?,
  val displayName: String,
  val safeGivenName: String?,
  val birthdayMonth: Int?,
  val birthdayDay: Int?,
  val birthdayYear: Int?,
  val leapDayPolicy: String?,
  val state: ContactSnapshotState,
  val syncGeneration: String,
  val materialRevision: Long,
  val sourceUpdatedAtMillis: Long,
  val syncedAtMillis: Long,
  val deletedAtMillis: Long?,
) {
  override fun toString(): String =
    "ContactSnapshotEntity(state=$state, revision=$materialRevision, values=<redacted>)"
}

@Entity(
  tableName = "contact_phones_v2",
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
    Index(value = ["destinationFingerprint"]),
    Index(value = ["state"]),
  ],
)
data class ContactPhoneEntity(
  @PrimaryKey val phoneId: String,
  val contactId: String,
  val sourceFingerprint: String,
  val rawNumber: String,
  val normalizedE164: String?,
  val destinationFingerprint: String?,
  val maskedDisplay: String,
  val typeLabel: String?,
  val regionCode: String?,
  val isSmsCapableType: Boolean,
  val state: PhoneRecordState,
  val materialRevision: Long,
  val updatedAtMillis: Long,
) {
  override fun toString(): String =
    "ContactPhoneEntity(state=$state, revision=$materialRevision, values=<redacted>)"
}

@Entity(
  tableName = "recipient_policies_v2",
  foreignKeys = [
    ForeignKey(
      entity = ContactSnapshotEntity::class,
      parentColumns = ["contactId"],
      childColumns = ["contactId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["chosenPhoneId"]),
    Index(value = ["state"]),
  ],
)
data class RecipientPolicyEntity(
  @PrimaryKey val contactId: String,
  val chosenPhoneId: String?,
  val state: RecipientEnrollmentState,
  val explicitEnrollmentEventId: String?,
  val blockReason: String?,
  val approvalId: String?,
  val revision: Long,
  val enabledAtMillis: Long?,
  val updatedAtMillis: Long,
  /** Opaque local provider-choice identifier; the actual date remains on ContactSnapshot. */
  val chosenBirthdayId: String? = null,
)

@Entity(
  tableName = "destination_blocks_v2",
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
    Index(value = ["accountId", "destinationFingerprint"], unique = true),
  ],
)
data class DestinationBlockEntity(
  @PrimaryKey val blockId: String,
  val accountId: String,
  val destinationFingerprint: String,
  val reason: String,
  val active: Boolean,
  val revision: Long,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
)

@Entity(
  tableName = "message_templates_v2",
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
    Index(value = ["accountId", "contentHash", "revision"], unique = true),
    Index(value = ["validationState"]),
  ],
)
data class MessageTemplateEntity(
  @PrimaryKey val templateId: String,
  val accountId: String,
  val source: TemplateSource,
  val exactTemplateText: String,
  val languageTag: String,
  val tone: String,
  val placeholderMode: String,
  val templateVersion: String,
  val promptPolicyVersion: String?,
  val validatorVersion: String,
  val modelIdentifier: String?,
  val contentHash: String,
  val validationState: TemplateValidationState,
  val revision: Long,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
  @androidx.room.ColumnInfo(defaultValue = "2")
  val requestedSegmentCap: Int = 2,
)

@Entity(
  tableName = "approval_snapshots_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = ContactSnapshotEntity::class,
      parentColumns = ["contactId"],
      childColumns = ["contactId"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = ContactPhoneEntity::class,
      parentColumns = ["phoneId"],
      childColumns = ["phoneId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["accountId"]),
    Index(value = ["contactId"]),
    Index(value = ["phoneId"]),
    Index(value = ["contentHash"], unique = true),
    Index(value = ["state"]),
  ],
)
data class ApprovalSnapshotEntity(
  @PrimaryKey val approvalId: String,
  val accountId: String,
  val contactId: String,
  val phoneId: String,
  val schemaVersion: Int,
  val contactMaterialRevision: Long,
  val phoneMaterialRevision: Long,
  val policyId: String,
  val policyRevision: Long,
  val normalizedPhoneE164: String,
  val destinationFingerprint: String,
  val maskedPhoneDisplay: String,
  val exactMessage: String,
  val sourceTemplateId: String?,
  val sourceTemplateVersion: String,
  val placeholderMode: String,
  val birthdayMonth: Int,
  val birthdayDay: Int,
  val leapDayPolicy: String?,
  val windowStartMinute: Int,
  val windowEndMinute: Int,
  val graceEndMinute: Int?,
  val latePolicy: String,
  val simPolicyKind: String,
  val resolvedSubscriptionId: Int,
  val segmentCount: Int,
  val messageEncoding: String,
  val orderedPartsHash: String,
  val carrierCostDisclosureVersion: String,
  val consentDisclosureVersion: String,
  val contentHash: String,
  val state: ApprovalRecordState,
  val approvedAtMillis: Long,
  val invalidatedAtMillis: Long?,
  val invalidationReason: String?,
)
