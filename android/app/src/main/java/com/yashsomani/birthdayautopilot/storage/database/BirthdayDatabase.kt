package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yashsomani.birthdayautopilot.automation.orchestration.AutomationOrchestrationDao
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleProjectionDao

@Database(
  entities = [
    ControlEntity::class,
    ContactEntity::class,
    ApprovalEntity::class,
    OccurrenceEntity::class,
    ActivityEntity::class,
    CallbackCounterEntity::class,
    AccountRecordEntity::class,
    InstallationBindingEntity::class,
    ConsentReceiptEntity::class,
    AutomationPolicyEntity::class,
    CoordinationStateEntity::class,
    ContactSyncStateEntity::class,
    ContactSnapshotEntity::class,
    ContactPhoneEntity::class,
    RecipientPolicyEntity::class,
    DestinationBlockEntity::class,
    MessageTemplateEntity::class,
    ApprovalSnapshotEntity::class,
    BirthdayOccurrenceRecordEntity::class,
    LocalDestinationGuardEntity::class,
    TestJobEntity::class,
    CoordinationPermitEntity::class,
    SendAttemptEntity::class,
    CallbackTokenEntity::class,
    DeliveryEventEntity::class,
    OutcomeProjectionEntity::class,
    TestReceiptEntity::class,
    ResetSafetyEntity::class,
    ResetBlockedDateEntity::class,
    ClockTrustEntity::class,
    ReadinessStateEntity::class,
    PeopleSyncGenerationEntity::class,
    PeopleStagingContactEntity::class,
    PeopleStagingPhoneEntity::class,
    PeopleStagingBirthdayEntity::class,
    ContactBirthdayChoiceEntity::class,
    ConfigurationReviewEntity::class,
  ],
  version = 4,
  autoMigrations = [
    AutoMigration(from = 1, to = 2, spec = Migration1To2Spec::class),
    AutoMigration(from = 2, to = 3),
    AutoMigration(from = 3, to = 4),
  ],
  exportSchema = true,
)
@TypeConverters(SafetyLedgerConverters::class)
abstract class BirthdayDatabase : RoomDatabase() {
  abstract fun birthdayDao(): BirthdayDao
  abstract fun safetyLedgerDao(): SafetyLedgerDao
  abstract fun peopleSyncDao(): PeopleSyncDao
  abstract fun automationOrchestrationDao(): AutomationOrchestrationDao
  abstract fun configurationDao(): ConfigurationDao
  abstract fun smsOutcomeDao(): SmsOutcomeDao
  internal abstract fun lifecycleProjectionDao(): LifecycleProjectionDao

  companion object {
    const val DATABASE_NAME = "birthday-autopilot.db"
  }
}
