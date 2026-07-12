package com.yashsomani.birthdayautopilot

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.swmansion.rnscreens.fragment.restoration.RNScreensFragmentFactory
import com.yashsomani.birthdayautopilot.attention.AndroidAttentionNotifier
import com.yashsomani.birthdayautopilot.auth.AuthorizationResolutionActivityResultOwner
import com.yashsomani.birthdayautopilot.auth.ForegroundActivityRegistry
import com.yashsomani.birthdayautopilot.auth.NotificationPermissionActivityResultOwner
import com.yashsomani.birthdayautopilot.auth.TelephonyPermissionActivityResultOwner
import com.yashsomani.birthdayautopilot.automation.workers.AutomationScheduler

class MainActivity : ReactActivity() {
  private val contactsResolutionOwner = AuthorizationResolutionActivityResultOwner(this)
  private val telephonyPermissionOwner = TelephonyPermissionActivityResultOwner(this)
  private val notificationPermissionOwner = NotificationPermissionActivityResultOwner(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    supportFragmentManager.fragmentFactory = RNScreensFragmentFactory()
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      setRecentsScreenshotEnabled(false)
    }
    AndroidAttentionNotifier(applicationContext).acceptIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    AndroidAttentionNotifier(applicationContext).acceptIntent(intent)
  }

  override fun onResume() {
    super.onResume()
    // Active screenshots remain available for accessibility/support. The flag
    // is applied only while leaving the foreground so older Android releases
    // cannot expose contact/message content in the system recents preview.
    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    // Retry listener registration after a Settings permission change or transient platform error.
    AppGraph.get(applicationContext).also { graph ->
      graph.startSubscriptionChangeObservation()
      graph.refreshGeminiOperationalGate()
    }
    ForegroundActivityRegistry.onResumed(
      this,
      contactsResolutionOwner,
      telephonyPermissionOwner,
      notificationPermissionOwner,
    )
    AutomationScheduler.enqueueImmediateLocal(applicationContext, "FOREGROUND")
  }

  override fun onPause() {
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    ForegroundActivityRegistry.onPaused(this)
    super.onPause()
  }

  override fun onDestroy() {
    ForegroundActivityRegistry.onPaused(this)
    contactsResolutionOwner.onDestroy()
    telephonyPermissionOwner.onDestroy()
    notificationPermissionOwner.onDestroy()
    super.onDestroy()
  }

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "BirthdayAutopilot"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}
