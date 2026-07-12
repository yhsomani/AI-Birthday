package com.yashsomani.birthdayautopilot.smoke

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.swmansion.rnscreens.fragment.restoration.RNScreensFragmentFactory

/** Real production React tree without product lifecycle, permission, or messaging hooks. */
class SmokeMainActivity : ReactActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    supportFragmentManager.fragmentFactory = RNScreensFragmentFactory()
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      setRecentsScreenshotEnabled(false)
    }
  }

  override fun getMainComponentName(): String = "BirthdayAutopilot"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
    DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  override fun onResume() {
    super.onResume()
    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
  }

  override fun onPause() {
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    super.onPause()
  }
}
