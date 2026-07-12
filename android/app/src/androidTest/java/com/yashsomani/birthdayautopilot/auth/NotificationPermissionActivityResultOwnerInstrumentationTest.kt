package com.yashsomani.birthdayautopilot.auth

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPermissionActivityResultOwnerInstrumentationTest {
  @Test
  fun malformedExistingMarkerMakesDirectRequestReturnSettingsWithoutLaunching() {
    ActivityScenario.launch(NotificationPermissionTestActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val marker = File(
          activity.applicationContext.noBackupFilesDir,
          "birthday-notification-permission-v1",
        )
        marker.writeText("malformed\n", Charsets.US_ASCII)
        try {
          assertEquals(
            NotificationPermissionResult.SETTINGS_REQUIRED,
            runBlocking { activity.permissionOwner.request() },
          )
        } finally {
          marker.delete()
        }
      }
    }
  }
}
