package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupCapabilityProbeTest {

    private lateinit var context: Context
    private lateinit var probe: AutomationSetupCapabilityProbe

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        probe = AutomationSetupCapabilityProbe(context)
    }

    @Test
    fun `hasGoogleContactsAccess trusts cached OAuth token`() {
        assertTrue(probe.hasGoogleContactsAccess(hasCachedGoogleOAuthToken = true))
    }

    @Test
    fun `isWhatsAppAutomationServiceEnabled matches configured accessibility service`() {
        val service = "${context.packageName}/com.example.core.accessibility.WhatsAppAccessibilityService"

        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "other.package/.OtherService",
        )
        assertFalse(probe.isWhatsAppAutomationServiceEnabled())

        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            service,
        )
        assertTrue(probe.isWhatsAppAutomationServiceEnabled())
    }
}
