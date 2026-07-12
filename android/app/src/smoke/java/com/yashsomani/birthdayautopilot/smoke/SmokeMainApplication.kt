@file:Suppress("DEPRECATION") // Matches React Native's default-preference API.

package com.yashsomani.birthdayautopilot.smoke

import android.app.Application
import android.preference.PreferenceManager
import androidx.core.content.edit
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.yashsomani.birthdayautopilot.BuildConfig

/** Production JS host with a synthetic, fail-closed native projection module. */
class SmokeMainApplication : Application(), ReactApplication {
  override val reactHost: ReactHost by lazy {
    checkSmokeIdentity()
    getDefaultReactHost(
      context = applicationContext,
      packageList = PackageList(this).packages.apply {
        add(SmokeBirthdayNativePackage())
      },
      jsMainModulePath = "index",
      useDevSupport = true,
    )
  }

  override fun onCreate() {
    super.onCreate()
    checkSmokeIdentity()
    PreferenceManager.getDefaultSharedPreferences(this).edit {
      putString("debug_http_host", LOOPBACK_METRO_HOST)
    }
    loadReactNative(this)
  }

  private fun checkSmokeIdentity() {
    check(BuildConfig.DEBUG) { "The production-path smoke host must remain a debug build." }
    check(BuildConfig.APP_ENV == "production-path-smoke") { "Invalid smoke environment." }
    check(BuildConfig.APPLICATION_ID == SMOKE_APPLICATION_ID) { "Invalid smoke application ID." }
    check(!BuildConfig.RESTRICTED_SMS_CAPABLE) { "Smoke builds can never be SMS capable." }
  }

  private companion object {
    const val SMOKE_APPLICATION_ID = "com.yashsomani.birthdayautopilot.smoke"
    const val LOOPBACK_METRO_HOST = "localhost:8081"
  }
}
