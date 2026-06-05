package com.example

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch

@HiltAndroidApp
class RelateAIApp : Application(), androidx.work.Configuration.Provider {

    @javax.inject.Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        SecurityChecks.checkCertificatePinExpiry()
        com.example.core.automation.notifications.NotificationHelper.createChannels(this)
        if (!isUnderTest()) {
            com.example.core.db.DatabaseKeyDerivation.warmUpAsync(this)
            com.example.core.prefs.SecurePrefs.warmUpAsync(this)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.example.core.automation.scheduler.WorkerScheduler.scheduleAll(this@RelateAIApp)
            }
        }
    }

    private fun isUnderTest(): Boolean {
        return try {
            Class.forName("org.robolectric.Robolectric") != null
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    companion object {
        private const val TAG = "RelateAIApp"
    }
}
