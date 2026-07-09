package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.domain.automation.SetupSystemReadinessPolicy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupSystemRecoveryCheckPresenterTest {

    private lateinit var context: Context
    private lateinit var presenter: AutomationSetupSystemRecoveryCheckPresenter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        presenter = AutomationSetupSystemRecoveryCheckPresenter(context)
    }

    @Test
    fun `notifications maps missing permission to app settings action`() {
        val check = presenter.notifications(
            SetupSystemReadinessPolicy.evaluateNotificationPermission(
                notificationsAllowed = false,
            ),
        )

        assertEquals(context.getString(R.string.automation_setup_check_notifications), check.title)
        assertEquals(ReadinessStatus.ACTION_REQUIRED, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_app_settings), check.actionLabel)
        assertEquals(AiDoctorAction.OPEN_APP_SETTINGS, check.action)
    }

    @Test
    fun `dailyAutomation maps missing work to refresh action`() {
        val check = presenter.dailyAutomation(
            SetupSystemReadinessPolicy.evaluateDailyAutomation(dailyScheduled = false),
        )

        assertEquals(context.getString(R.string.automation_setup_check_daily_automation), check.title)
        assertEquals(ReadinessStatus.WARNING, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_refresh), check.actionLabel)
        assertEquals(AiDoctorAction.REFRESH, check.action)
    }

    @Test
    fun `dispatchRecovery maps queued failures to activity action`() {
        val detail = context.getString(R.string.automation_setup_dispatch_recovery_count, 2)

        val check = presenter.dispatchRecovery(
            readiness = SetupSystemReadinessPolicy.evaluateDispatchRecovery(
                persistedRecoveryCount = 2,
            ),
            recoveryDetail = detail,
        )

        assertEquals(context.getString(R.string.automation_setup_check_dispatch_recovery), check.title)
        assertEquals(ReadinessStatus.WARNING, check.status)
        assertEquals(context.getString(R.string.automation_setup_action_view_activity), check.actionLabel)
        assertEquals(AiDoctorAction.OPEN_ACTIVITY_HISTORY, check.action)
        assertEquals(detail, check.detail)
    }
}
