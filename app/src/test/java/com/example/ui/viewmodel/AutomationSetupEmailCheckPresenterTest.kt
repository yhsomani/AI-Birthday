package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.domain.automation.SetupEmailReadinessPolicy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupEmailCheckPresenterTest {

    private lateinit var context: Context
    private lateinit var presenter: AutomationSetupEmailCheckPresenter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        presenter = AutomationSetupEmailCheckPresenter(context)
    }

    @Test
    fun `email maps missing required sender to settings action`() {
        val check = presenter.email(
            SetupEmailReadinessPolicy.evaluate(
                senderEmail = "",
                senderEmailPassword = "",
                emailSelfTestVerified = false,
                emailPreferredContactCount = 2,
            ),
        )

        assertEquals(context.getString(R.string.automation_setup_check_email), check.title)
        assertEquals(ReadinessStatus.ACTION_REQUIRED, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_open_settings), check.actionLabel)
        assertEquals(AiDoctorAction.OPEN_SETTINGS, check.action)
        assertEquals(ReadinessGroup.REQUIRED, check.group)
    }

    @Test
    fun `email maps verified sender to ready check`() {
        val check = presenter.email(
            SetupEmailReadinessPolicy.evaluate(
                senderEmail = "sender@example.com",
                senderEmailPassword = "app-password",
                emailSelfTestVerified = true,
                emailPreferredContactCount = 1,
            ),
        )

        assertEquals(ReadinessStatus.OK, check.status)
        assertEquals(context.getString(R.string.automation_setup_email_ok), check.detail)
        assertEquals(null, check.actionLabel)
        assertEquals(AiDoctorAction.NONE, check.action)
    }
}
