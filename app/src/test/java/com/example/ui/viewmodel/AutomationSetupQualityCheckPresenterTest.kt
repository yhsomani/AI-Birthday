package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.domain.automation.SetupQualityReadinessPolicy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupQualityCheckPresenterTest {

    private lateinit var context: Context
    private lateinit var presenter: AutomationSetupQualityCheckPresenter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        presenter = AutomationSetupQualityCheckPresenter(context)
    }

    @Test
    fun `styleCoach maps empty profile to style coach action`() {
        val check = presenter.styleCoach(
            SetupQualityReadinessPolicy.evaluateStyleCoach(styleSampleCount = 0),
        )

        assertEquals(context.getString(R.string.automation_setup_check_style_coach), check.title)
        assertEquals(ReadinessStatus.ACTION_REQUIRED, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_open_style_coach), check.actionLabel)
        assertEquals(AiDoctorAction.OPEN_STYLE_COACH, check.action)
        assertEquals(ReadinessGroup.QUALITY, check.group)
    }

    @Test
    fun `genericMessages maps missing contacts to sync action`() {
        val check = presenter.genericMessages(
            SetupQualityReadinessPolicy.evaluateGenericMessages(emptyList()),
        )

        assertEquals(context.getString(R.string.automation_setup_check_generic_messages), check.title)
        assertEquals(ReadinessStatus.WARNING, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_sync_contacts), check.actionLabel)
        assertEquals(AiDoctorAction.SYNC_CONTACTS, check.action)
        assertEquals(ReadinessGroup.QUALITY, check.group)
    }
}
