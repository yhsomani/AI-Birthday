package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.components.SyncErrorCard
import com.example.ui.viewmodel.HomeActionTarget
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.HomeViewModel

internal object HomeScreenTestTags {
    const val SYNC_ERROR_CARD = "home_sync_error_card"
    const val SETUP_PROGRESS_CARD = "home_setup_progress_card"
    const val PRIMARY_ACTION_CARD = "home_primary_action_card"
    const val SUPPORTING_ACTION_PREFIX = "home_supporting_action_"
    const val QUICK_ACTION_ANALYTICS = "home_quick_action_analytics"
    const val QUICK_ACTION_ACTIVITY_HISTORY = "home_quick_action_activity_history"
    const val QUICK_ACTION_STYLE_COACH = "home_quick_action_style_coach"
    const val QUICK_ACTION_AUTOMATION_SETUP = "home_quick_action_automation_setup"
    const val QUICK_ACTION_BACKUP_RESTORE = "home_quick_action_backup_restore"
    const val PLANNER_ITEM_PREFIX = "home_planner_item_"
}

@Composable
fun HomeScreen(
    onNavigateToContact: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToActivityHistory: () -> Unit = {},
    onNavigateToStyleCoach: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToAutomationSetup: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onNavigateToContact = onNavigateToContact,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToAnalytics = onNavigateToAnalytics,
        onNavigateToActivityHistory = onNavigateToActivityHistory,
        onNavigateToStyleCoach = onNavigateToStyleCoach,
        onNavigateToBackupRestore = onNavigateToBackupRestore,
        onNavigateToAutomationSetup = onNavigateToAutomationSetup,
        onNavigateToMessages = onNavigateToMessages,
        onRetrySync = { viewModel.loadMetrics() },
        onDismissSyncError = { viewModel.dismissSyncError() },
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    onNavigateToContact: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToActivityHistory: () -> Unit = {},
    onNavigateToStyleCoach: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToAutomationSetup: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onRetrySync: () -> Unit = {},
    onDismissSyncError: () -> Unit = {},
) {
    val navigateToAction: (HomeActionTarget) -> Unit = { target ->
        when (target) {
            HomeActionTarget.AutomationSetup -> onNavigateToAutomationSetup()
            HomeActionTarget.BackupRestore -> onNavigateToBackupRestore()
            is HomeActionTarget.ContactDetail -> onNavigateToContact(target.contactId)
            HomeActionTarget.Messages -> onNavigateToMessages()
        }
    }
    val displayUserName = state.userName.ifBlank { stringResource(R.string.home_default_user) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = RelateSpacing.screenHorizontal),
    ) {
        item {
            Spacer(modifier = Modifier.height(RelateSpacing.screenTop))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.userPhotoUrl != null) {
                        AsyncImage(
                            model = state.userPhotoUrl,
                            contentDescription = stringResource(R.string.profile_photo),
                            modifier = Modifier
                                .size(RelateSize.avatar)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.width(RelateSpacing.md))
                    }
                    Text(
                        text = stringResource(R.string.home_greeting, displayUserName),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.syncError?.let { errorMsg ->
            item {
                Spacer(modifier = Modifier.height(RelateSpacing.lg))
                SyncErrorCard(
                    message = errorMsg,
                    onRetry = onRetrySync,
                    onDismiss = onDismissSyncError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HomeScreenTestTags.SYNC_ERROR_CARD),
                )
            }
        }

        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RelateSize.loadingPanelHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(RelateSpacing.xl))
                state.primaryAction?.let { action ->
                    NextActionSection(
                        primaryAction = action,
                        supportingActions = state.supportingActions,
                        onActionClick = navigateToAction,
                    )
                    Spacer(modifier = Modifier.height(RelateSpacing.lg))
                }
                if (state.setupProgress.totalSteps > 0) {
                    SetupProgressCard(
                        summary = state.setupProgress,
                        onClick = onNavigateToAutomationSetup,
                        modifier = Modifier.testTag(HomeScreenTestTags.SETUP_PROGRESS_CARD),
                    )
                    Spacer(modifier = Modifier.height(RelateSpacing.lg))
                }
                HomeStatsGrid(state = state)
            }
        }

        item {
            HomeQuickActions(
                onNavigateToAnalytics = onNavigateToAnalytics,
                onNavigateToActivityHistory = onNavigateToActivityHistory,
                onNavigateToStyleCoach = onNavigateToStyleCoach,
                onNavigateToAutomationSetup = onNavigateToAutomationSetup,
                onNavigateToBackupRestore = onNavigateToBackupRestore,
            )
        }

        if (state.plannerItems.isNotEmpty()) {
            item {
                HomePlannerSection(
                    plannerItems = state.plannerItems,
                    onActionClick = navigateToAction,
                )
            }
        }

        item {
            HomeBirthdaysSection(upcomingBirthdays = state.upcomingBirthdays)
        }
    }
}
