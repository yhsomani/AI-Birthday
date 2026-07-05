package com.example.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.AiDoctorAction
import com.example.ui.viewmodel.AiDoctorRecommendedFix
import com.example.ui.viewmodel.ReadinessStatus
import com.example.ui.viewmodel.SetupProgressSummary

@Composable
internal fun ReadinessActionPanel(
    isRefreshing: Boolean,
    isSyncingContacts: Boolean,
    isTestingAi: Boolean,
    isTestingEmail: Boolean,
    onRefresh: () -> Unit,
    onDryRun: () -> Unit,
    onSyncContacts: () -> Unit,
    onTestAi: () -> Unit,
    onTestEmail: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = RelateSize.compactButtonHeight),
                enabled = !isRefreshing,
            ) {
                Text(stringResource(R.string.automation_setup_action_refresh))
            }
            OutlinedButton(
                onClick = onDryRun,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = RelateSize.compactButtonHeight),
            ) {
                Text(stringResource(R.string.automation_setup_action_dry_run))
            }
        }
        PrimaryReadinessButton(
            text = stringResource(R.string.automation_setup_action_sync_contacts),
            loading = isSyncingContacts,
            enabled = !isSyncingContacts,
            onClick = onSyncContacts,
        )
        PrimaryReadinessButton(
            text = stringResource(R.string.automation_setup_action_test_ai),
            loading = isTestingAi,
            enabled = !isTestingAi,
            onClick = onTestAi,
        )
        PrimaryReadinessButton(
            text = stringResource(R.string.automation_setup_action_test_email),
            loading = isTestingEmail,
            enabled = !isTestingEmail,
            onClick = onTestEmail,
        )
    }
}

@Composable
internal fun SetupProgressStrip(summary: SetupProgressSummary) {
    if (summary.totalSteps == 0) return

    val status = when {
        summary.actionRequiredCount > 0 -> ReadinessStatus.ACTION_REQUIRED
        summary.warningCount > 0 -> ReadinessStatus.WARNING
        else -> ReadinessStatus.OK
    }
    val color = status.statusColors().content
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

    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.setup_progress_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.setup_progress_count,
                    summary.completedSteps,
                    summary.totalSteps,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
        LinearProgressIndicator(
            progress = { summary.progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(RelateSize.progressTrack),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun RecommendedFixSection(
    fix: AiDoctorRecommendedFix,
    onAction: (AiDoctorAction) -> Unit,
) {
    val colors = fix.status.statusColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.container,
                shape = RoundedCornerShape(RelateRadius.card),
            )
            .padding(RelateSpacing.md),
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            Icon(
                imageVector = fix.status.statusIcon(),
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(RelateSize.iconMd),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.automation_setup_recommended_fix),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.content,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = fix.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = fix.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = { onAction(fix.action) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RelateSize.primaryButtonHeight),
            shape = RoundedCornerShape(RelateRadius.control),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(
                text = fix.actionLabel,
                color = MaterialTheme.colorScheme.background,
            )
        }
    }
}

@Composable
private fun PrimaryReadinessButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RelateSize.primaryButtonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(RelateRadius.control),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(RelateSize.iconSm),
                color = MaterialTheme.colorScheme.background,
                strokeWidth = RelateSpacing.xxs,
            )
        } else {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.background,
            )
        }
    }
}
