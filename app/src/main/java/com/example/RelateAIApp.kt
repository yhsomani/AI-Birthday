package com.example

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch

@HiltAndroidApp
class RelateAIApp : Application(), androidx.work.Configuration.Provider {

    @javax.inject.Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    @javax.inject.Inject
    lateinit var healthMonitorDiagnosticRecorder: com.example.core.resilience.HealthMonitorDiagnosticRecorder

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        SecurityChecks.checkCertificatePinExpiry()
        com.example.core.automation.notifications.NotificationHelper.createChannels(this)
        if (!isUnderTest()) {
            healthMonitorDiagnosticRecorder.start()
            com.example.core.db.DatabaseKeyDerivation.warmUpAsync(this)
            com.example.core.prefs.SecurePrefs.warmUpAsync(this)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.example.core.automation.scheduler.WorkerScheduler.scheduleAll(this@RelateAIApp)
            }
        }
    }

    private fun isUnderTest(): Boolean {
        if (System.getProperty("robolectric.enabled") == "true") return true
        return listOf(
            "org.robolectric.Robolectric",
            "org.robolectric.RobolectricTestRunner",
        ).any { className ->
            runCatching { Class.forName(className) }.isSuccess
        }
    }

    companion object {
        private const val TAG = "RelateAIApp"
    }
}
