package com.yashsomani.birthdayautopilot.e2e

import android.os.Bundle
import android.view.WindowManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

/** UI-only test activity. No product graph or native product bridge is touched. */
class E2EMainActivity : ReactActivity() {
  override fun getMainComponentName(): String = "BirthdayAutopilotE2E"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
    object : DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled) {
      override fun getLaunchOptions(): Bundle = fixtureProperties(intent.extras)
    }

  override fun onResume() {
    super.onResume()
    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
  }

  override fun onPause() {
    // Match the production recents boundary while keeping active screenshots
    // available for accessibility evidence and failed-flow diagnostics.
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    super.onPause()
  }

  private fun fixtureProperties(extras: Bundle?): Bundle {
    val requestedLanguage = extras?.getString("e2eLanguage")
    val language = if (requestedLanguage == "hi") "hi" else "en"
    val setupComplete = when (val value = extras?.get("e2eSetupComplete")) {
      is Boolean -> value
      is String -> value == "true"
      else -> false
    }
    return Bundle().apply {
      putString("e2eLanguage", language)
      putString("e2ePlatform", "android")
      putString("e2eRuntimeToken", "birthday-e2e-fixture-v1")
      putBoolean("e2eSetupComplete", setupComplete)
    }
  }
}
