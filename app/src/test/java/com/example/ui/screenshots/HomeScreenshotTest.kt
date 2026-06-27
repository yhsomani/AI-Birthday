package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.home.HomeContent
import com.example.ui.screens.home.HomeScreenTestTags
import com.example.ui.viewmodel.HomeActionTarget
import com.example.ui.viewmodel.HomeNextAction
import com.example.ui.viewmodel.HomeNextActionKind
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.RelationshipPlannerItem
import com.example.ui.viewmodel.SetupProgressSummary
import com.example.ui.viewmodel.UpcomingBirthday
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
class HomeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homePopulated_compactPhone() {
        setHomeContent(state = populatedHomeState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/home_populated_compact_phone.png")
    }

    @Test
    fun homePopulated_compactPhoneLargeFont() {
        setHomeContent(
            state = populatedHomeState(),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/home_populated_compact_phone_large_font.png")
    }

    @Test
    fun homeActions_compactPhoneLargeFont() {
        setHomeContent(
            state = populatedHomeState(),
            fontScale = LargeFontScale,
        )

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(HomeScreenTestTags.QUICK_ACTION_ANALYTICS))
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/home_actions_compact_phone_large_font.png")
    }

    @Test
    fun homeLoading_compactPhone() {
        setHomeContent(
            state = loadingHomeState(),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/home_loading_compact_phone.png")
    }

    private fun setHomeContent(
        state: HomeUiState,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            HomeContent(state = state)
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun populatedHomeState(): HomeUiState {
        return HomeUiState(
            userName = "Yash",
            healthScore = 82,
            pendingCount = 2,
            upcomingEventsCount = 4,
            contactCount = 12,
            sentCount = 8,
            upcomingBirthdays = listOf(
                UpcomingBirthday(name = "Asha", date = "Jun 15"),
                UpcomingBirthday(name = "Mira", date = "Jun 22"),
            ),
            isLoading = false,
            setupProgress = SetupProgressSummary(
                completedSteps = 2,
                totalSteps = 3,
                warningCount = 1,
            ),
            primaryAction = HomeNextAction(
                kind = HomeNextActionKind.REVIEW_PENDING,
                actionTarget = HomeActionTarget.Messages,
                count = 2,
            ),
            supportingActions = listOf(
                HomeNextAction(
                    kind = HomeNextActionKind.REFRESH_BACKUP,
                    actionTarget = HomeActionTarget.BackupRestore,
                    daysSinceBackup = 35,
                ),
                HomeNextAction(
                    kind = HomeNextActionKind.CONNECT_AI,
                    actionTarget = HomeActionTarget.AutomationSetup,
                ),
            ),
            plannerItems = listOf(
                RelationshipPlannerItem(
                    title = "Reconnect with Asha",
                    detail = "Relationship health is 42.",
                    actionTarget = HomeActionTarget.ContactDetail("contact-1"),
                ),
                RelationshipPlannerItem(
                    title = "Review Mira's birthday",
                    detail = "Birthday reminder is in 7 days.",
                    actionTarget = HomeActionTarget.ContactDetail("contact-2"),
                ),
            ),
        )
    }

    private fun loadingHomeState(): HomeUiState {
        return HomeUiState(
            userName = "Yash",
            isLoading = true,
        )
    }

    private companion object {
        const val ProgressAnimationFrameMillis = 750L
    }
}
