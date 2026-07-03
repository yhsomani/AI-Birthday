package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.domain.automation.SetupAccountProviderReadinessPolicy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupAccountProviderCheckPresenterTest {

    private lateinit var context: Context
    private lateinit var presenter: AutomationSetupAccountProviderCheckPresenter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        presenter = AutomationSetupAccountProviderCheckPresenter(context)
    }

    @Test
    fun `googleContacts maps missing access to sync action`() {
        val check = presenter.googleContacts(
            SetupAccountProviderReadinessPolicy.evaluateGoogleContacts(
                hasGoogleContactsAccess = false,
            ),
        )

        assertEquals(context.getString(R.string.automation_setup_check_google_contacts), check.title)
        assertEquals(ReadinessStatus.ACTION_REQUIRED, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_sync_contacts), check.actionLabel)
        assertEquals(AiDoctorAction.SYNC_CONTACTS, check.action)
        assertEquals(ReadinessGroup.REQUIRED, check.group)
    }

    @Test
    fun `geminiAccess maps configured key to test action`() {
        val check = presenter.geminiAccess(
            SetupAccountProviderReadinessPolicy.evaluateGeminiAccess(
                hasGeminiApiKey = true,
                hasFirebaseAuth = false,
            ),
        )

        assertEquals(context.getString(R.string.automation_setup_check_gemini), check.title)
        assertEquals(ReadinessStatus.OK, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_test_ai), check.actionLabel)
        assertEquals(AiDoctorAction.TEST_AI, check.action)
        assertEquals(ReadinessGroup.REQUIRED, check.group)
    }
}
