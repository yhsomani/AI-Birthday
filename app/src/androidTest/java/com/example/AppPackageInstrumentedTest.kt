package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPackageInstrumentedTest {

    @Test
    fun installedPackage_matchesCheckedInApplicationIdAndLabel() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
        assertEquals(
            appContext.getString(R.string.app_name),
            appContext.applicationInfo.loadLabel(appContext.packageManager).toString(),
        )
    }
}
