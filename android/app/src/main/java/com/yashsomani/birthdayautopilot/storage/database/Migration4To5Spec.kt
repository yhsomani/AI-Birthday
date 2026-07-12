package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Backfills only the one v4 state that durably proves initial activation already completed. */
class Migration4To5Spec : AutoMigrationSpec {
  override fun onPostMigrate(connection: SQLiteConnection) {
    connection.execSQL(
      """
      UPDATE app_control
      SET initialActivationCompleted = 1
      WHERE singletonId = 1
        AND accountMode = 'AUTOMATION_ACTIVE'
        AND automationDesired = 1
      """.trimIndent(),
    )
  }
}
