package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.domain.model.MessageChannel
import com.example.domain.readiness.RelationshipReadinessAction
import com.example.domain.readiness.RelationshipReadinessReason
import com.example.domain.readiness.RelationshipReadinessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupReadinessPresenterTest {

    private lateinit var context: Context
    private lateinit var presenter: AutomationSetupReadinessPresenter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        presenter = AutomationSetupReadinessPresenter(context)
    }

    @Test
    fun `summarize returns required summary for blockers`() {
        val summary = presenter.summarize(
            listOf(
                ReadinessCheck(
                    title = context.getString(R.string.automation_setup_check_gemini),
                    detail = context.getString(R.string.automation_setup_gemini_auth_missing),
                    status = ReadinessStatus.ACTION_REQUIRED,
                ),
            ),
        )

        assertEquals(ReadinessStatus.ACTION_REQUIRED, summary.status)
        assertEquals(context.getString(R.string.automation_setup_summary_blockers, 1), summary.title)
        assertTrue(summary.detail.contains(context.getString(R.string.automation_setup_check_gemini)))
    }

    @Test
    fun `recommendedFix ranks required setup blockers before earlier quality fixes`() {
        val recommendedFix = presenter.recommendedFix(
            listOf(
                ReadinessCheck(
                    title = "Style Coach",
                    detail = "No writing samples",
                    status = ReadinessStatus.ACTION_REQUIRED,
                    actionLabel = "Open Style Coach",
                    action = AiDoctorAction.OPEN_STYLE_COACH,
                    group = ReadinessGroup.QUALITY,
                ),
                ReadinessCheck(
                    title = MessageChannel.SMS.raw,
                    detail = "SMS permission is missing",
                    status = ReadinessStatus.ACTION_REQUIRED,
                    actionLabel = "App Settings",
                    action = AiDoctorAction.OPEN_APP_SETTINGS,
                    group = ReadinessGroup.REQUIRED,
                ),
            ),
        )

        assertEquals(MessageChannel.SMS.raw, recommendedFix?.title)
        assertEquals(AiDoctorAction.OPEN_APP_SETTINGS, recommendedFix?.action)
        assertEquals(ReadinessGroup.REQUIRED, recommendedFix?.group)
        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, recommendedFix?.actionReadiness?.state)
        assertEquals(RelationshipReadinessReason.SETUP_ACTION_REQUIRED, recommendedFix?.actionReadiness?.primaryReason)
        assertEquals(RelationshipReadinessAction.OPEN_SETUP, recommendedFix?.actionReadiness?.primaryAction)
    }

    @Test
    fun `setupProgress counts ok warnings and blockers`() {
        val progress = presenter.setupProgress(
            listOf(
                ReadinessCheck(
                    title = "Ready",
                    detail = "Ready",
                    status = ReadinessStatus.OK,
                ),
                ReadinessCheck(
                    title = "Warning",
                    detail = "Warning",
                    status = ReadinessStatus.WARNING,
                ),
                ReadinessCheck(
                    title = "Blocker",
                    detail = "Blocker",
                    status = ReadinessStatus.ACTION_REQUIRED,
                ),
            ),
        )

        assertEquals(1, progress.completedSteps)
        assertEquals(3, progress.totalSteps)
        assertEquals(1, progress.warningCount)
        assertEquals(1, progress.actionRequiredCount)
    }
}
