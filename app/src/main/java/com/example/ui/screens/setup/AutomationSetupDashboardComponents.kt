package com.example.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateStatusBanner
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.readiness.RelationshipReadinessState
import com.example.ui.viewmodel.AiDoctorAction
import com.example.ui.viewmodel.AiDoctorRecommendedFix
import com.example.ui.viewmodel.AiDoctorSummary
import com.example.ui.viewmodel.ReadinessCheck
import com.example.ui.viewmodel.SetupProgressSummary

@Composable
internal fun AutomationSetupSummaryBanner(
    summary: AiDoctorSummary,
    status: RelationshipReadinessState,
) {
    val summaryColors = status.statusColors()
    RelateStatusBanner(
        title = summary.title,
        message = summary.detail,
        icon = status.statusIcon(),
        containerColor = summaryColors.container,
        contentColor = summaryColors.content,
    )
}

@Composable
internal fun ReadinessDashboard(
    summary: AiDoctorSummary,
    setupProgress: SetupProgressSummary,
    recommendedFix: AiDoctorRecommendedFix?,
    checks: List<ReadinessCheck>,
    isRefreshing: Boolean,
    isSyncingContacts: Boolean,
    isTestingAi: Boolean,
    isTestingEmail: Boolean,
    operationMessage: String?,
    onRefresh: () -> Unit,
    onSyncContacts: () -> Unit,
    onDryRun: () -> Unit,
    onTestAi: () -> Unit,
    onTestEmail: () -> Unit,
    onAction: (AiDoctorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.automation_setup_diagnostic_checks),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(RelateSize.iconSm),
                        strokeWidth = RelateSpacing.xxs,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = summary.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SetupProgressStrip(summary = setupProgress)

            recommendedFix?.let { fix ->
                RecommendedFixSection(
                    fix = fix,
                    onAction = onAction,
                )
            }

            readinessGroupOrder.forEach { group ->
                val groupChecks = checks.filter { it.group == group }
                if (groupChecks.isNotEmpty()) {
                    ReadinessGroupSection(
                        group = group,
                        checks = groupChecks,
                        onAction = onAction,
                    )
                }
            }

            operationMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ReadinessActionPanel(
                isRefreshing = isRefreshing,
                isSyncingContacts = isSyncingContacts,
                isTestingAi = isTestingAi,
                isTestingEmail = isTestingEmail,
                onRefresh = onRefresh,
                onDryRun = onDryRun,
                onSyncContacts = onSyncContacts,
                onTestAi = onTestAi,
                onTestEmail = onTestEmail,
            )
        }
    }
}

private data class StatusColors(
    val container: Color,
    val content: Color,
)

@Composable
private fun RelationshipReadinessState.statusColors(): StatusColors = when (this) {
    RelationshipReadinessState.READY,
    RelationshipReadinessState.NEEDS_REVIEW,
    RelationshipReadinessState.IN_PROGRESS -> StatusColors(
        container = MaterialTheme.relateSemanticColors.success.copy(alpha = RelateAlpha.feedbackContainer),
        content = MaterialTheme.relateSemanticColors.success,
    )
    RelationshipReadinessState.WARNING,
    RelationshipReadinessState.WAITING -> StatusColors(
        container = MaterialTheme.relateSemanticColors.warning.copy(alpha = RelateAlpha.feedbackContainer),
        content = MaterialTheme.relateSemanticColors.warning,
    )
    RelationshipReadinessState.ACTION_REQUIRED -> StatusColors(
        container = MaterialTheme.colorScheme.error.copy(alpha = RelateAlpha.feedbackContainer),
        content = MaterialTheme.colorScheme.error,
    )
}

private fun RelationshipReadinessState.statusIcon(): ImageVector = when (this) {
    RelationshipReadinessState.READY,
    RelationshipReadinessState.NEEDS_REVIEW,
    RelationshipReadinessState.IN_PROGRESS -> Icons.Filled.CheckCircle
    RelationshipReadinessState.WARNING,
    RelationshipReadinessState.WAITING -> Icons.Filled.Warning
    RelationshipReadinessState.ACTION_REQUIRED -> Icons.Filled.Error
}
