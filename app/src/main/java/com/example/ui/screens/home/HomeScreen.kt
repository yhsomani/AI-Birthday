package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.StatCard
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSuccess
import com.example.core.ui.theme.RelateWarning
import com.example.ui.components.SyncErrorCard
import com.example.ui.viewmodel.HomeActionTarget
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.RelationshipPlannerItem
import com.example.ui.viewmodel.SetupProgressSummary

internal object HomeScreenTestTags {
    const val SYNC_ERROR_CARD = "home_sync_error_card"
    const val READINESS_BANNER = "home_readiness_banner"
    const val SETUP_PROGRESS_CARD = "home_setup_progress_card"
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(horizontal = 16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(48.dp))
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
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = stringResource(R.string.home_greeting, state.userName),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = RelateOnSurfaceVariant,
                    )
                }
            }
        }

        state.syncError?.let { errorMsg ->
            item {
                Spacer(modifier = Modifier.height(16.dp))
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

        val readinessTitle = state.readinessTitle
        val readinessDetail = state.readinessDetail
        if (readinessTitle != null && readinessDetail != null) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                ReadinessBanner(
                    title = readinessTitle,
                    detail = readinessDetail,
                    onClick = {
                        state.readinessAction?.let(navigateToAction) ?: onNavigateToAutomationSetup()
                    },
                    modifier = Modifier.testTag(HomeScreenTestTags.READINESS_BANNER),
                )
            }
        }

        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = RelatePrimary)
                }
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                if (state.setupProgress.totalSteps > 0) {
                    SetupProgressCard(
                        summary = state.setupProgress,
                        onClick = onNavigateToAutomationSetup,
                        modifier = Modifier.testTag(HomeScreenTestTags.SETUP_PROGRESS_CARD),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        label = stringResource(R.string.home_stat_wishes_sent),
                        value = "${state.sentCount}",
                        icon = Icons.Filled.Star,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.home_stat_upcoming),
                        value = "${state.upcomingEventsCount}",
                        icon = Icons.Filled.CalendarMonth,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        label = stringResource(R.string.dashboard_contacts),
                        value = "${state.contactCount}",
                        icon = Icons.Filled.People,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.messages_pending),
                        value = "${state.pendingCount}",
                        icon = Icons.Filled.MailOutline,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.home_stat_score),
                        value = "${state.healthScore}",
                        icon = Icons.Filled.Favorite,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = stringResource(R.string.dashboard_quick_actions))
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickActionTile(
                        label = stringResource(R.string.analytics),
                        icon = Icons.Filled.Analytics,
                        onClick = onNavigateToAnalytics,
                        modifier = Modifier.weight(1f),
                        testTag = HomeScreenTestTags.QUICK_ACTION_ANALYTICS,
                    )
                    QuickActionTile(
                        label = stringResource(R.string.activity_history_title),
                        icon = Icons.Filled.History,
                        onClick = onNavigateToActivityHistory,
                        modifier = Modifier.weight(1f),
                        testTag = HomeScreenTestTags.QUICK_ACTION_ACTIVITY_HISTORY,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickActionTile(
                        label = stringResource(R.string.settings_ai_style_coach),
                        icon = Icons.Filled.SmartToy,
                        onClick = onNavigateToStyleCoach,
                        modifier = Modifier.weight(1f),
                        testTag = HomeScreenTestTags.QUICK_ACTION_STYLE_COACH,
                    )
                    QuickActionTile(
                        label = stringResource(R.string.settings_automation_setup),
                        icon = Icons.Filled.Settings,
                        onClick = onNavigateToAutomationSetup,
                        modifier = Modifier.weight(1f),
                        testTag = HomeScreenTestTags.QUICK_ACTION_AUTOMATION_SETUP,
                    )
                }
                QuickActionTile(
                    label = stringResource(R.string.backup_restore_title),
                    icon = Icons.Filled.Storage,
                    onClick = onNavigateToBackupRestore,
                    testTag = HomeScreenTestTags.QUICK_ACTION_BACKUP_RESTORE,
                )
            }
        }

        if (state.plannerItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = stringResource(R.string.relationship_planner_title))
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.plannerItems.forEach { item ->
                        PlannerItemCard(
                            item = item,
                            onClick = { navigateToAction(item.actionTarget) },
                            modifier = Modifier.testTag(
                                HomeScreenTestTags.PLANNER_ITEM_PREFIX + item.actionTarget.testKey()
                            ),
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = stringResource(R.string.home_upcoming_birthdays))
        }

        item {
            RelateGlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.upcomingBirthdays.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_no_upcoming_birthdays),
                            style = MaterialTheme.typography.bodyMedium,
                            color = RelateOnSurfaceVariant,
                        )
                    } else {
                        state.upcomingBirthdays.forEachIndexed { index, birthday ->
                            BirthdayRow(name = birthday.name, date = birthday.date)
                            if (index < state.upcomingBirthdays.lastIndex) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun HomeActionTarget.testKey(): String {
    return when (this) {
        HomeActionTarget.AutomationSetup -> "automation_setup"
        HomeActionTarget.BackupRestore -> "backup_restore"
        is HomeActionTarget.ContactDetail -> contactId
        HomeActionTarget.Messages -> "messages"
    }
}

@Composable
private fun SetupProgressCard(
    summary: SetupProgressSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = when {
        summary.actionRequiredCount > 0 -> MaterialTheme.colorScheme.error
        summary.warningCount > 0 -> RelateWarning
        else -> RelateSuccess
    }
    val statusIcon = when {
        summary.actionRequiredCount > 0 -> Icons.Filled.Error
        summary.warningCount > 0 -> Icons.Filled.Warning
        else -> Icons.Filled.CheckCircle
    }
    val detail = when {
        summary.actionRequiredCount > 0 -> stringResource(
            R.string.setup_progress_blockers,
            summary.actionRequiredCount,
        )
        summary.warningCount > 0 -> stringResource(
            R.string.setup_progress_warnings,
            summary.warningCount,
        )
        else -> stringResource(R.string.setup_progress_ready)
    }

    RelateGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.setup_progress_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.setup_progress_count,
                        summary.completedSteps,
                        summary.totalSteps,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                )
            }
            LinearProgressIndicator(
                progress = { summary.progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlannerItemCard(
    item: RelationshipPlannerItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = RelatePrimary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReadinessBanner(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    RelateGlassCard(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RelatePrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun BirthdayRow(
    name: String,
    date: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RelatePrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.take(3),
                style = MaterialTheme.typography.labelMedium,
                color = RelatePrimary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_birthday_on_date, date),
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
        }
    }
}
