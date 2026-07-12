package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "app_control")
data class ControlEntity(
  @PrimaryKey val singletonId: Int = SINGLETON_ID,
  val revision: Long,
  val blockerRevision: Long,
  val accountMode: String,
  val automationDesired: Boolean,
  @ColumnInfo(defaultValue = "0") val initialActivationCompleted: Boolean,
  val activeInstallationEpoch: Long?,
  val lastTrustedServerMillis: Long?,
  val lastTrustedElapsedMillis: Long?,
  val trustedBootCount: Int?,
  val resetSafetyState: String,
) {
  companion object {
    const val SINGLETON_ID = 1
  }
}
