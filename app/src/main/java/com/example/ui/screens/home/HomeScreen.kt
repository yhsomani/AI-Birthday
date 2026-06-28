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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.StatCard
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.ui.components.SyncErrorCard
import com.example.ui.viewmodel.HomeActionTarget
import com.example.ui.viewmodel.HomeNextAction
import com.example.ui.viewmodel.HomeNextActionKind
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.RelationshipPlannerItem
import com.example.ui.viewmodel.SetupProgressSummary

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
                Spacer(modifier = Modifier.height(RelateSpacing.xl))
                SectionHeader(title = stringResource(R.string.relationship_planner_title))
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
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
            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            SectionHeader(title = stringResource(R.string.home_upcoming_birthdays))
        }

        item {
            RelateGlassCard {
                Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
                    if (state.upcomingBirthdays.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_no_upcoming_birthdays),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.upcomingBirthdays.forEachIndexed { index, birthday ->
                            BirthdayRow(name = birthday.name, date = birthday.date)
                            if (index < state.upcomingBirthdays.lastIndex) {
                                Spacer(modifier = Modifier.height(RelateSpacing.md))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(RelateSpacing.xl))
        }
    }
}

@Composable
private fun HomeStatsGrid(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
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

@Composable
private fun HomeQuickActions(
    onNavigateToAnalytics: () -> Unit,
    onNavigateToActivityHistory: () -> Unit,
    onNavigateToStyleCoach: () -> Unit,
    onNavigateToAutomationSetup: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
) {
    Spacer(modifier = Modifier.height(RelateSpacing.xl))
    SectionHeader(title = stringResource(R.string.dashboard_quick_actions))
    Spacer(modifier = Modifier.height(RelateSpacing.sm))
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
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
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
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

private fun HomeActionTarget.testKey(): String {
    return when (this) {
        HomeActionTarget.AutomationSetup -> "automation_setup"
        HomeActionTarget.BackupRestore -> "backup_restore"
        is HomeActionTarget.ContactDetail -> contactId
        HomeActionTarget.Messages -> "messages"
    }
}

@Composable
private fun NextActionSection(
    primaryAction: HomeNextAction,
    supportingActions: List<HomeNextAction>,
    onActionClick: (HomeActionTarget) -> Unit,
) {
    SectionHeader(title = stringResource(R.string.home_next_action_section))
    Spacer(modifier = Modifier.height(RelateSpacing.sm))
    NextActionCard(
        action = primaryAction,
        onClick = { onActionClick(primaryAction.actionTarget) },
        modifier = Modifier.testTag(HomeScreenTestTags.PRIMARY_ACTION_CARD),
        isPrimary = true,
    )
    if (supportingActions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        Text(
            text = stringResource(R.string.home_supporting_actions_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
            supportingActions.forEach { action ->
                NextActionCard(
                    action = action,
                    onClick = { onActionClick(action.actionTarget) },
                    modifier = Modifier.testTag(
                        HomeScreenTestTags.SUPPORTING_ACTION_PREFIX + action.kind.name.lowercase()
                    ),
                )
            }
        }
    }
}

@Composable
private fun NextActionCard(
    action: HomeNextAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    val icon = when (action.kind) {
        HomeNextActionKind.SYNC_CONTACTS,
        HomeNextActionKind.FIX_CONTACT_SYNC,
        HomeNextActionKind.CONNECT_AI,
        HomeNextActionKind.ENABLE_AI_GENERATION -> Icons.Filled.Settings
        HomeNextActionKind.REVIEW_PENDING -> Icons.Filled.MailOutline
        HomeNextActionKind.CREATE_BACKUP,
        HomeNextActionKind.REFRESH_BACKUP -> Icons.Filled.Storage
        HomeNextActionKind.RECONNECT_CONTACT -> Icons.Filled.Favorite
    }
    val tint = when (action.kind) {
        HomeNextActionKind.SYNC_CONTACTS,
        HomeNextActionKind.FIX_CONTACT_SYNC,
        HomeNextActionKind.CONNECT_AI,
        HomeNextActionKind.ENABLE_AI_GENERATION -> MaterialTheme.colorScheme.error
        HomeNextActionKind.REVIEW_PENDING -> MaterialTheme.colorScheme.primary
        HomeNextActionKind.CREATE_BACKUP,
        HomeNextActionKind.REFRESH_BACKUP -> MaterialTheme.relateSemanticColors.warning
        HomeNextActionKind.RECONNECT_CONTACT -> MaterialTheme.colorScheme.primary
    }
    RelateGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(if (isPrimary) RelateSpacing.cardContent else RelateSpacing.compactCardContent),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(if (isPrimary) RelateSize.iconLg else RelateSize.iconMd),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.titleText(),
                    style = if (isPrimary) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    text = action.detailText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeNextAction.titleText(): String {
    return when (kind) {
        HomeNextActionKind.SYNC_CONTACTS -> stringResource(R.string.home_next_action_sync_contacts_title)
        HomeNextActionKind.FIX_CONTACT_SYNC -> stringResource(R.string.home_next_action_fix_contact_sync_title)
        HomeNextActionKind.CONNECT_AI -> stringResource(R.string.home_next_action_connect_ai_title)
        HomeNextActionKind.ENABLE_AI_GENERATION -> stringResource(R.string.home_next_action_enable_ai_title)
        HomeNextActionKind.REVIEW_PENDING -> stringResource(R.string.home_next_action_review_pending_title)
        HomeNextActionKind.CREATE_BACKUP -> stringResource(R.string.home_backup_never_title)
        HomeNextActionKind.REFRESH_BACKUP -> stringResource(R.string.home_backup_stale_title)
        HomeNextActionKind.RECONNECT_CONTACT -> stringResource(
            R.string.home_next_action_reconnect_title,
            contactName.orEmpty(),
        )
    }
}

@Composable
private fun HomeNextAction.detailText(): String {
    return when (kind) {
        HomeNextActionKind.SYNC_CONTACTS -> stringResource(R.string.home_next_action_sync_contacts_detail)
        HomeNextActionKind.FIX_CONTACT_SYNC -> stringResource(R.string.home_next_action_fix_contact_sync_detail)
        HomeNextActionKind.CONNECT_AI -> stringResource(R.string.home_next_action_connect_ai_detail)
        HomeNextActionKind.ENABLE_AI_GENERATION -> stringResource(R.string.home_next_action_enable_ai_detail)
        HomeNextActionKind.REVIEW_PENDING -> stringResource(
            R.string.home_next_action_review_pending_detail,
            count,
        )
        HomeNextActionKind.CREATE_BACKUP -> stringResource(R.string.home_backup_never_detail)
        HomeNextActionKind.REFRESH_BACKUP -> stringResource(
            R.string.home_backup_stale_detail,
            daysSinceBackup ?: 0L,
        )
        HomeNextActionKind.RECONNECT_CONTACT -> stringResource(
            R.string.home_next_action_reconnect_detail,
            healthScore ?: 0,
        )
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
        summary.warningCount > 0 -> MaterialTheme.relateSemanticColors.warning
        else -> MaterialTheme.relateSemanticColors.success
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
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(RelateSize.iconMd),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RelateSize.progressTrack),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RelateSize.iconMd),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(RelateSize.iconMd),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RelateSize.iconMd),
            )
            Spacer(modifier = Modifier.width(RelateSpacing.sm))
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
                .size(RelateSize.avatar)
                .clip(RoundedCornerShape(RelateRadius.control))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = RelateAlpha.feedbackContainer)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.take(3),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(RelateSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_birthday_on_date, date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
