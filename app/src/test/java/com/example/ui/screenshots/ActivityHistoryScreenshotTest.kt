package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ActivityLogType
import com.example.domain.model.activity.ActivityLogRecord
import com.example.ui.screens.activity.ActivityHistoryContent
import com.example.ui.viewmodel.ActivityHistoryUiState
import com.example.ui.viewmodel.ActivityLogDateFilter
import com.example.ui.viewmodel.ActivityLogStatusFilter
import com.example.ui.viewmodel.ActivityLogTypeFilter
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Category(ScreenshotTests::class)
@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xhdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActivityHistoryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activityHistoryPopulated_compactPhone() {
        setActivityHistoryContent(state = populatedActivityHistoryState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_populated_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun activityHistoryPopulated_typicalPhone() {
        setActivityHistoryContent(state = populatedActivityHistoryState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_populated_typical_phone.png")
    }

    @Test
    fun activityHistoryActionCard_compactPhoneLargeFont() {
        setActivityHistoryContent(
            state = populatedActivityHistoryState().copy(
                entries = listOf(dispatchRecoveryLog()),
                selectedTypeFilter = ActivityLogTypeFilter.DISPATCH,
                selectedDateFilter = ActivityLogDateFilter.LAST_7_DAYS,
                selectedStatusFilter = ActivityLogStatusFilter.OPEN,
                searchQuery = "failed",
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_action_card_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun activityHistoryActionCard_compactPhoneHindiLargeFont() {
        setActivityHistoryContent(
            state = populatedActivityHistoryState().copy(
                entries = listOf(dispatchRecoveryLogHindi()),
                selectedTypeFilter = ActivityLogTypeFilter.DISPATCH,
                selectedDateFilter = ActivityLogDateFilter.LAST_7_DAYS,
                selectedStatusFilter = ActivityLogStatusFilter.OPEN,
                searchQuery = "विफल",
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_action_card_compact_phone_hindi_large_font.png")
    }

    @Test
    fun activityHistoryEmpty_compactPhone() {
        setActivityHistoryContent(state = ActivityHistoryUiState(isLoading = false))

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_empty_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun activityHistoryEmpty_compactPhoneHindiLargeFont() {
        setActivityHistoryContent(
            state = ActivityHistoryUiState(isLoading = false),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_empty_compact_phone_hindi_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun activityHistoryEmpty_typicalPhone() {
        setActivityHistoryContent(state = ActivityHistoryUiState(isLoading = false))

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_empty_typical_phone.png")
    }

    @Test
    fun activityHistoryError_compactPhone() {
        setActivityHistoryContent(
            state = ActivityHistoryUiState(
                isLoading = false,
                errorMessageRes = R.string.activity_history_error_load,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_error_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun activityHistoryError_compactPhoneHindiLargeFont() {
        setActivityHistoryContent(
            state = ActivityHistoryUiState(
                isLoading = false,
                errorMessageRes = R.string.activity_history_error_load,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_error_compact_phone_hindi_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun activityHistoryError_typicalPhone() {
        setActivityHistoryContent(
            state = ActivityHistoryUiState(
                isLoading = false,
                errorMessageRes = R.string.activity_history_error_load,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_error_typical_phone.png")
    }

    @Test
    fun activityHistoryLoading_compactPhone() {
        setActivityHistoryContent(
            state = ActivityHistoryUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_loading_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun activityHistoryLoading_typicalPhone() {
        setActivityHistoryContent(
            state = ActivityHistoryUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/activity_history_loading_typical_phone.png")
    }

    private fun setActivityHistoryContent(
        state: ActivityHistoryUiState,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            ActivityHistoryContent(
                state = state,
                onBack = {},
                onOpenRoute = {},
                onSearchQueryChange = {},
                onTypeFilterSelected = {},
                onDateFilterSelected = {},
                onStatusFilterSelected = {},
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun populatedActivityHistoryState(): ActivityHistoryUiState {
        val entries = listOf(
            dispatchRecoveryLog(),
            backupCompletedLog(),
            analyticsExportLog(),
        )
        return ActivityHistoryUiState(
            allEntries = entries,
            entries = entries,
            isLoading = false,
        )
    }

    private fun dispatchRecoveryLog() = activityLog(
        id = "dispatch_recovery",
        type = ActivityLogType.MESSAGE.raw,
        title = "Failed send needs review",
        detail = "WhatsApp automation could not complete for Priya's birthday message.",
        severity = ActivityLogSeverity.ERROR.raw,
        status = ActivityLogStatus.OPEN.raw,
        actionRoute = "messages/failed",
        createdAtMs = 1_767_688_200_000L,
        metadataJson = """{"decision":"retry"}""",
    )

    private fun dispatchRecoveryLogHindi() = activityLog(
        id = "dispatch_recovery_hi",
        type = ActivityLogType.MESSAGE.raw,
        title = "विफल भेजाई समीक्षा चाहती है",
        detail = "WhatsApp ऑटोमेशन प्रिया के जन्मदिन संदेश के लिए पूरा नहीं हो सका।",
        severity = ActivityLogSeverity.ERROR.raw,
        status = ActivityLogStatus.OPEN.raw,
        actionRoute = "messages/failed",
        createdAtMs = 1_767_688_200_000L,
        metadataJson = """{"decision":"retry"}""",
    )

    private fun backupCompletedLog() = activityLog(
        id = "backup_completed",
        type = ActivityLogType.BACKUP.raw,
        title = "Encrypted backup exported",
        detail = "128 records were written to a protected backup file.",
        severity = ActivityLogSeverity.INFO.raw,
        status = ActivityLogStatus.RESOLVED.raw,
        actionRoute = "settings/backup",
        createdAtMs = 1_767_607_200_000L,
    )

    private fun analyticsExportLog() = activityLog(
        id = "analytics_export",
        type = ActivityLogType.ANALYTICS.raw,
        title = "Analytics CSV generated",
        detail = "Relationship health and delivery reliability report was shared.",
        severity = ActivityLogSeverity.WARNING.raw,
        status = ActivityLogStatus.RESOLVED.raw,
        actionRoute = "analytics",
        createdAtMs = 1_767_520_800_000L,
    )

    private fun activityLog(
        id: String,
        type: String,
        title: String,
        detail: String,
        severity: String,
        status: String,
        actionRoute: String?,
        createdAtMs: Long,
        metadataJson: String = "{}",
    ) = ActivityLogRecord(
        id = id,
        type = type,
        title = title,
        detail = detail,
        severity = severity,
        status = status,
        actionRoute = actionRoute,
        metadataJson = metadataJson,
        createdAtMs = createdAtMs,
    )

    private companion object {
        const val ProgressAnimationFrameMillis = 750L
    }
}
