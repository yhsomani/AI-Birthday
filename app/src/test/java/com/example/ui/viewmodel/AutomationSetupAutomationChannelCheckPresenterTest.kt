package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.domain.automation.SetupChannelReadinessPolicy
import com.example.domain.model.MessageChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupAutomationChannelCheckPresenterTest {

    private lateinit var context: Context
    private lateinit var presenter: AutomationSetupAutomationChannelCheckPresenter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        presenter = AutomationSetupAutomationChannelCheckPresenter(context)
    }

    @Test
    fun `channelVerification maps missing email evidence to email test action`() {
        val check = presenter.channelVerification(
            SetupChannelReadinessPolicy.evaluateChannelVerification(
                selectedChannels = setOf(MessageChannel.EMAIL),
                successfulChannels = emptySet(),
            ),
        )

        assertEquals(context.getString(R.string.automation_setup_check_channel_verification), check.title)
        assertEquals(ReadinessStatus.WARNING, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_test_email), check.actionLabel)
        assertEquals(AiDoctorAction.TEST_EMAIL, check.action)
        assertTrue(check.detail.contains(context.getString(R.string.channel_email)))
    }

    @Test
    fun `sms maps selected contacts without permission to app settings action`() {
        val check = presenter.sms(
            SetupChannelReadinessPolicy.evaluateSms(
                smsAllowed = false,
                selectedSmsContactCount = 2,
                smsDisabled = false,
            ),
        )

        assertEquals(ReadinessStatus.ACTION_REQUIRED, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_app_settings), check.actionLabel)
        assertEquals(AiDoctorAction.OPEN_APP_SETTINGS, check.action)
        assertEquals(context.getString(R.string.automation_setup_sms_missing_for_contacts, 2), check.detail)
    }

    @Test
    fun `whatsApp maps missing consent to accessibility repair action`() {
        val check = presenter.whatsApp(
            SetupChannelReadinessPolicy.evaluateWhatsApp(
                consentGranted = false,
                accessibilityEnabled = true,
                whatsAppInstalled = true,
                selectedWhatsAppContactCount = 1,
                whatsAppDisabled = false,
            ),
        )

        assertEquals(ReadinessStatus.ACTION_REQUIRED, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_open_accessibility), check.actionLabel)
        assertEquals(AiDoctorAction.OPEN_ACCESSIBILITY_SETTINGS, check.action)
        assertEquals(context.getString(R.string.automation_setup_whatsapp_consent_needed_for_contacts, 1), check.detail)
    }
}
