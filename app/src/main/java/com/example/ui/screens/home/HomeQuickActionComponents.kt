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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing

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
