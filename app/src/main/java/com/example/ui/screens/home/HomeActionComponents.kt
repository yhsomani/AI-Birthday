package com.example.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.readiness.RelationshipReadinessState
import com.example.ui.viewmodel.HomeActionTarget
import com.example.ui.viewmodel.HomeNextAction
import com.example.ui.viewmodel.HomeNextActionKind
import com.example.ui.viewmodel.SetupProgressSummary

@Composable
internal fun HomeQuickActions(
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

@Composable
internal fun NextActionSection(
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
                        HomeScreenTestTags.SUPPORTING_ACTION_PREFIX + action.kind.name.lowercase(),
                    ),
                )
            }
        }
    }
}

@Composable
internal fun SetupProgressCard(
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
                    fontWeight = FontWeight.SemiBold,
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
        HomeNextActionKind.RECONNECT_CONTACT -> MaterialTheme.colorScheme.primary
        else -> when (action.actionReadiness.state) {
            RelationshipReadinessState.ACTION_REQUIRED -> MaterialTheme.colorScheme.error
            RelationshipReadinessState.WARNING,
            RelationshipReadinessState.WAITING -> MaterialTheme.relateSemanticColors.warning
            RelationshipReadinessState.NEEDS_REVIEW,
            RelationshipReadinessState.READY,
            RelationshipReadinessState.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        }
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
                    fontWeight = FontWeight.SemiBold,
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
