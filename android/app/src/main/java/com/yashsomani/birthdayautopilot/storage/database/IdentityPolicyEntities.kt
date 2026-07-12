package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yashsomani.birthdayautopilot.core.model.AccountMode

@Entity(
  tableName = "accounts_v2",
  indices = [
    Index(value = ["activeSlot"], unique = true),
    Index(value = ["googleSubjectHash"], unique = true),
    Index(value = ["firebaseUid"], unique = true),
  ],
)
data class AccountRecordEntity(
  @PrimaryKey val accountId: String,
  /** Non-null only for the one locally attachable account. */
  val activeSlot: Int?,
  val googleSubjectHash: String,
  val firebaseUid: String,
  val displayEmail: String?,
  val localeTag: String,
  val state: AccountRecordState,
  val revision: Long,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
) {
  override fun toString(): String =
    "AccountRecordEntity(state=$state, revision=$revision, values=<redacted>)"
}

@Entity(
  tableName = "installation_bindings_v2",
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
    Index(value = ["localSlot"], unique = true),
    Index(value = ["callbackGeneration"], unique = true),
  ],
)
data class InstallationBindingEntity(
  @PrimaryKey val installationId: String,
  val accountId: String,
  /** Always one for a real local installation; nullable only on quarantined migration rows. */
  val localSlot: Int?,
  val callbackGeneration: String,
  val state: InstallationRecordState,
  val accountMode: AccountMode,
  val senderEpoch: Long?,
  val resetGeneration: Long,
  val ownerLeaseUntilMillis: Long?,
  val appVersionCode: Long,
  val distributionChannel: String,
  val signingCertificateSha256: String,
  val lastVerifiedServerMillis: Long?,
  val revision: Long,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
)

@Entity(
  tableName = "consent_receipts_v2",
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
    Index(value = ["accountId", "kind", "sequence"], unique = true),
    Index(value = ["recordedAtMillis"]),
  ],
)
data class ConsentReceiptEntity(
  @PrimaryKey val receiptId: String,
  val accountId: String,
  val kind: ConsentKind,
  val decision: ConsentDecision,
  val disclosureVersion: String,
  val scopeHash: String?,
  val sequence: Long,
  val supersedesReceiptId: String?,
  val recordedAtMillis: Long,
)

@Entity(
  tableName = "automation_policies_v2",
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
    Index(value = ["accountId", "revision"], unique = true),
    Index(value = ["state"]),
  ],
)
data class AutomationPolicyEntity(
  @PrimaryKey val policyId: String,
  val accountId: String,
  val revision: Long,
  val state: PolicyRecordState,
  val timeZoneId: String,
  val windowStartMinute: Int,
  val windowEndMinute: Int,
  val graceEndMinute: Int?,
  val latePolicy: String,
  val dailyCap: Int,
  val simPolicyKind: String,
  val resolvedSubscriptionId: Int,
  val roamingAllowed: Boolean,
  val policyVersion: String,
  val createdAtMillis: Long,
  val invalidatedAtMillis: Long?,
  val invalidationReason: String?,
)

@Entity(
  tableName = "coordination_state_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["activeInstallationId"])],
)
data class CoordinationStateEntity(
  @PrimaryKey val accountId: String,
  val mode: AccountMode,
  val activeInstallationId: String?,
  val senderEpoch: Long?,
  val resetGeneration: Long,
  val continuityGeneration: Long?,
  val ownerLeaseUntilMillis: Long?,
  val nextArmNotBeforeMillis: Long?,
  val latestIssuedSubmitNotAfterMillis: Long?,
  val birthdayAutomationNotBeforeMillis: Long?,
  val transferDrainUntilMillis: Long?,
  val deletionDrainUntilMillis: Long?,
  val lastSuccessfulCoordinationMillis: Long?,
  val lastSafeCode: String?,
  val revision: Long,
  val updatedAtMillis: Long,
)
