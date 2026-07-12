package com.yashsomani.birthdayautopilot.auth

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Debug-only host for exercising the exact direct-request boundary on pre-API-33 emulators. */
class NotificationPermissionTestActivity : ComponentActivity() {
  internal lateinit var permissionOwner: NotificationPermissionActivityResultOwner

  override fun onCreate(savedInstanceState: Bundle?) {
    permissionOwner = NotificationPermissionActivityResultOwner(
      activity = this,
      permissionRequired = { true },
      permissionGranted = { false },
    )
    super.onCreate(savedInstanceState)
  }

  override fun onDestroy() {
    permissionOwner.onDestroy()
    super.onDestroy()
  }
}
