package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppResourceContractTest {

    @Test
    fun appIdentityResources_matchCheckedInProductContract() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("RelateAI", context.getString(R.string.app_name))
        assertEquals("com.aistudio.relateai.qxtjrk", BuildConfig.APPLICATION_ID)
        assertEquals("RelateAI", context.applicationInfo.loadLabel(context.packageManager).toString())
    }

    @Test
    fun generatedGoogleServicesResources_arePresentForDebugAuth() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val defaultWebClientId = context.getString(R.string.default_web_client_id)

        assertTrue(
            "default_web_client_id must be generated from google-services.json for Google Sign-In",
            defaultWebClientId.isNotBlank() &&
                defaultWebClientId != "default_web_client_id" &&
                defaultWebClientId.endsWith(".apps.googleusercontent.com"),
        )
    }
}
