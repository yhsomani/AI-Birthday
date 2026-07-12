package com.yashsomani.birthdayautopilot

import android.app.Application
import androidx.work.Configuration
import com.yashsomani.birthdayautopilot.automation.workers.AutomationScheduler
import com.yashsomani.birthdayautopilot.automation.workers.SchedulerStartupCoordinator
import com.yashsomani.birthdayautopilot.automation.workers.SchedulerStartupStateStore
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.yashsomani.birthdayautopilot.bridge.BirthdayNativePackage

class MainApplication : Application(), ReactApplication, Configuration.Provider {

  private val appGraph by lazy { AppGraph.get(this) }

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
      .setWorkerFactory(appGraph.workerFactory)
      .setMinimumLoggingLevel(android.util.Log.ERROR)
      .build()

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(BirthdayNativePackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    loadReactNative(this)
    SchedulerStartupCoordinator.initialize(SchedulerStartupStateStore(this)) {
      AutomationScheduler.ensureScheduled(this)
      appGraph.startSubscriptionChangeObservation()
    }
    appGraph.configureGeminiOperationalGate()
  }
}
