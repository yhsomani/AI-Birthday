@file:Suppress("DEPRECATION") // Matches React Native's default-preference API.

package com.yashsomani.birthdayautopilot.e2e

import android.app.Application
import android.preference.PreferenceManager
import androidx.core.content.edit
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.yashsomani.birthdayautopilot.BuildConfig

/**
 * Isolated React host for synthetic device UI tests.
 *
 * It intentionally loads only autolinked React Native UI packages. Product
 * services, identity clients, schedulers, receivers, and gateways are absent.
 */
class E2EMainApplication : Application(), ReactApplication {
  override val reactHost: ReactHost by lazy {
    checkFixtureIdentity()
    getDefaultReactHost(
      context = applicationContext,
      packageList = PackageList(this).packages,
      jsMainModulePath = "e2e/index",
      useDevSupport = true,
    )
  }

  override fun onCreate() {
    super.onCreate()
    checkFixtureIdentity()
    configureLoopbackMetro()
    loadReactNative(this)
  }

  private fun configureLoopbackMetro() {
    // React Native otherwise chooses the emulator gateway (10.0.2.2). The
    // fixture network policy intentionally authorizes only adb-reversed
    // localhost, so set the isolated app's standard dev-server preference
    // before its ReactHost is created.
    PreferenceManager.getDefaultSharedPreferences(this).edit {
      putString("debug_http_host", LOOPBACK_METRO_HOST)
    }
  }

  private fun checkFixtureIdentity() {
    check(BuildConfig.DEBUG) { "The fixture host must remain a debug build." }
    check(BuildConfig.APP_ENV == "e2e-fixture") { "Invalid fixture environment." }
    check(BuildConfig.APPLICATION_ID == E2E_APPLICATION_ID) { "Invalid fixture application ID." }
    check(!BuildConfig.RESTRICTED_SMS_CAPABLE) { "Fixture builds can never be SMS capable." }
  }

  private companion object {
    const val E2E_APPLICATION_ID = "com.yashsomani.birthdayautopilot.e2e"
    const val LOOPBACK_METRO_HOST = "localhost:8081"
  }
}
