package com.example.ui.screens.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.BackupFreshnessPrompt
import com.example.ui.viewmodel.HomeActionTarget
import com.example.ui.viewmodel.HomeNextAction
import com.example.ui.viewmodel.HomeNextActionKind
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.RelationshipPlannerItem
import com.example.ui.viewmodel.SetupProgressSummary
import com.example.ui.viewmodel.UpcomingBirthday
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HomeScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun dashboardCardsAndLinks_dispatchExpectedCallbacks() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(
                syncError = "Google sync failed",
                primaryAction = HomeNextAction(
                    kind = HomeNextActionKind.FIX_CONTACT_SYNC,
                    actionTarget = HomeActionTarget.AutomationSetup,
                ),
            ),
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.settings))
            .assertIsDisplayed()
            .performClick()

        clickTaggedCard(HomeScreenTestTags.PRIMARY_ACTION_CARD)
        clickTaggedCard(HomeScreenTestTags.QUICK_ACTION_ANALYTICS)
        clickTaggedCard(HomeScreenTestTags.QUICK_ACTION_ACTIVITY_HISTORY)
        clickTaggedCard(HomeScreenTestTags.QUICK_ACTION_STYLE_COACH)
        clickTaggedCard(HomeScreenTestTags.QUICK_ACTION_AUTOMATION_SETUP)
        clickTaggedCard(HomeScreenTestTags.QUICK_ACTION_BACKUP_RESTORE)
        clickTaggedCard(HomeScreenTestTags.PLANNER_ITEM_PREFIX + "contact-1")

        assertEquals(
            listOf(
                "settings",
                "automation",
                "analytics",
                "activity",
                "style",
                "automation",
                "backup",
                "contact:contact-1",
            ),
            actions,
        )
    }

    @Test
    fun syncErrorControls_dispatchRetryAndDismiss() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(syncError = "Unable to sync contacts."),
        )

        composeRule.onNodeWithTag(HomeScreenTestTags.SYNC_ERROR_CARD)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sync_error_retry))
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.sync_error_dismiss))
            .performClick()

        assertEquals(listOf("retry", "dismiss"), actions)
    }

    @Test
    fun pendingApprovalEntryPoints_openMessagesDirectly() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(
                primaryAction = HomeNextAction(
                    kind = HomeNextActionKind.REVIEW_PENDING,
                    actionTarget = HomeActionTarget.Messages,
                    count = 2,
                ),
                plannerItems = listOf(
                    RelationshipPlannerItem(
                        title = "Review pending wishes",
                        detail = "2 approval(s) are waiting before send time.",
                        actionTarget = HomeActionTarget.Messages,
                    ),
                ),
            ),
        )

        clickTaggedCard(HomeScreenTestTags.PRIMARY_ACTION_CARD)
        clickTaggedCard(HomeScreenTestTags.PLANNER_ITEM_PREFIX + "messages")

        assertEquals(listOf("messages", "messages"), actions)
    }

    @Test
    fun primarySetupAction_opensAutomationDoctor() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(
                primaryAction = HomeNextAction(
                    kind = HomeNextActionKind.CONNECT_AI,
                    actionTarget = HomeActionTarget.AutomationSetup,
                ),
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.home_next_action_connect_ai_title))
            .assertIsDisplayed()
        clickTaggedCard(HomeScreenTestTags.PRIMARY_ACTION_CARD)

        assertEquals(listOf("automation"), actions)
    }

    @Test
    fun setupProgressCard_opensAutomationDoctor() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(
                setupProgress = SetupProgressSummary(
                    completedSteps = 2,
                    totalSteps = 3,
                    warningCount = 1,
                ),
            ),
        )

        clickTaggedCard(HomeScreenTestTags.SETUP_PROGRESS_CARD)

        assertEquals(listOf("automation"), actions)
    }

    @Test
    fun staleBackupPrompt_opensBackupRestore() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(
                primaryAction = HomeNextAction(
                    kind = HomeNextActionKind.REFRESH_BACKUP,
                    actionTarget = HomeActionTarget.BackupRestore,
                    daysSinceBackup = 45,
                ),
            ),
        )

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(HomeScreenTestTags.PRIMARY_ACTION_CARD))
        composeRule.onNodeWithText(context.getString(R.string.home_backup_stale_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_backup_stale_detail, 45L))
            .assertIsDisplayed()
        clickTaggedCard(HomeScreenTestTags.PRIMARY_ACTION_CARD)

        assertEquals(listOf("backup"), actions)
    }

    @Test
    fun lowHealthRelationshipAction_opensContactDetail() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(
                primaryAction = HomeNextAction(
                    kind = HomeNextActionKind.RECONNECT_CONTACT,
                    actionTarget = HomeActionTarget.ContactDetail("contact-1"),
                    contactName = "Asha",
                    healthScore = 32,
                ),
            ),
        )

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(HomeScreenTestTags.PRIMARY_ACTION_CARD))
        composeRule.onNodeWithText(context.getString(R.string.home_next_action_reconnect_title, "Asha"))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_next_action_reconnect_detail, 32))
            .assertIsDisplayed()
        clickTaggedCard(HomeScreenTestTags.PRIMARY_ACTION_CARD)

        assertEquals(listOf("contact:contact-1"), actions)
    }

    @Test
    fun supportingActions_routeThroughTypedTargets() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(
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
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.home_next_action_section))
            .assertIsDisplayed()
        clickTaggedCard(HomeScreenTestTags.SUPPORTING_ACTION_PREFIX + "refresh_backup")
        clickTaggedCard(HomeScreenTestTags.SUPPORTING_ACTION_PREFIX + "connect_ai")

        assertEquals(listOf("backup", "automation"), actions)
    }

    @Test
    fun plannerItems_routeThroughTypedTargets() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(
                plannerItems = listOf(
                    RelationshipPlannerItem(
                        title = "Open backup",
                        detail = "Recent backup is missing.",
                        actionTarget = HomeActionTarget.BackupRestore,
                    ),
                    RelationshipPlannerItem(
                        title = "Fix setup",
                        detail = "Automation setup needs attention.",
                        actionTarget = HomeActionTarget.AutomationSetup,
                    ),
                    RelationshipPlannerItem(
                        title = "Review contact",
                        detail = "Personalization is incomplete.",
                        actionTarget = HomeActionTarget.ContactDetail("contact-2"),
                    ),
                ),
            ),
        )

        clickTaggedCard(HomeScreenTestTags.PLANNER_ITEM_PREFIX + "backup_restore")
        clickTaggedCard(HomeScreenTestTags.PLANNER_ITEM_PREFIX + "automation_setup")
        clickTaggedCard(HomeScreenTestTags.PLANNER_ITEM_PREFIX + "contact-2")

        assertEquals(listOf("backup", "automation", "contact:contact-2"), actions)
    }

    private fun setHomeContent(
        actions: MutableList<String>,
        state: HomeUiState,
    ) {
        composeRule.setContent {
            RelateAITheme {
                HomeContent(
                    state = state,
                    onNavigateToContact = { actions += "contact:$it" },
                    onNavigateToSettings = { actions += "settings" },
                    onNavigateToAnalytics = { actions += "analytics" },
                    onNavigateToActivityHistory = { actions += "activity" },
                    onNavigateToStyleCoach = { actions += "style" },
                    onNavigateToBackupRestore = { actions += "backup" },
                    onNavigateToAutomationSetup = { actions += "automation" },
                    onNavigateToMessages = { actions += "messages" },
                    onRetrySync = { actions += "retry" },
                    onDismissSyncError = { actions += "dismiss" },
                )
            }
        }
    }

    private fun clickTaggedCard(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }

    private fun populatedHomeState(
        syncError: String? = null,
        readinessTitle: String? = null,
        readinessDetail: String? = null,
        readinessAction: HomeActionTarget? = null,
        setupProgress: SetupProgressSummary = SetupProgressSummary(
            completedSteps = 2,
            totalSteps = 3,
            warningCount = 1,
        ),
        backupPrompt: BackupFreshnessPrompt? = null,
        primaryAction: HomeNextAction? = null,
        supportingActions: List<HomeNextAction> = emptyList(),
        plannerItems: List<RelationshipPlannerItem> = listOf(
            RelationshipPlannerItem(
                title = "Reconnect with Asha",
                detail = "Relationship health is 42.",
                actionTarget = HomeActionTarget.ContactDetail("contact-1"),
            ),
        ),
    ) = HomeUiState(
        userName = "Yash",
        healthScore = 82,
        pendingCount = 2,
        upcomingEventsCount = 4,
        contactCount = 12,
        sentCount = 8,
        upcomingBirthdays = listOf(UpcomingBirthday(name = "Asha", date = "Jun 15")),
        isLoading = false,
        syncError = syncError,
        readinessTitle = readinessTitle,
        readinessDetail = readinessDetail,
        readinessAction = readinessAction,
        setupProgress = setupProgress,
        backupPrompt = backupPrompt,
        primaryAction = primaryAction,
        supportingActions = supportingActions,
        plannerItems = plannerItems,
    )
}
