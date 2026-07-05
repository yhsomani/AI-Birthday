package com.example.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.ui.viewmodel.AiDoctorAction
import com.example.ui.viewmodel.ReadinessCheck
import com.example.ui.viewmodel.ReadinessGroup
import com.example.ui.viewmodel.ReadinessStatus

internal val readinessGroupOrder = listOf(
    ReadinessGroup.REQUIRED,
    ReadinessGroup.QUALITY,
    ReadinessGroup.RELIABILITY,
    ReadinessGroup.RECOVERY,
)

@Composable
internal fun ReadinessGroupSection(
    group: ReadinessGroup,
    checks: List<ReadinessCheck>,
    onAction: (AiDoctorAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
        Text(
            text = group.label(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        checks.forEach { check ->
            ReadinessRow(check = check, onAction = onAction)
        }
    }
}

@Composable
private fun ReadinessGroup.label(): String = when (this) {
    ReadinessGroup.REQUIRED -> stringResource(R.string.automation_setup_group_required)
    ReadinessGroup.QUALITY -> stringResource(R.string.automation_setup_group_quality)
    ReadinessGroup.RELIABILITY -> stringResource(R.string.automation_setup_group_reliability)
    ReadinessGroup.RECOVERY -> stringResource(R.string.automation_setup_group_recovery)
}

@Composable
private fun ReadinessRow(
    check: ReadinessCheck,
    onAction: (AiDoctorAction) -> Unit,
) {
    val icon = check.status.statusIcon()
    val color = check.status.statusColors().content
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(RelateSize.iconMd),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (check.actionLabel != null && check.action != AiDoctorAction.NONE) {
                TextButton(
                    onClick = { onAction(check.action) },
                    modifier = Modifier.heightIn(min = RelateSize.compactButtonHeight),
                ) {
                    Text(check.actionLabel)
                }
            }
        }
    }
}

internal data class ReadinessStatusColors(
    val container: Color,
    val content: Color,
)

@Composable
internal fun ReadinessStatus.statusColors(): ReadinessStatusColors = when (this) {
    ReadinessStatus.OK -> ReadinessStatusColors(
        container = MaterialTheme.relateSemanticColors.success.copy(alpha = RelateAlpha.feedbackContainer),
        content = MaterialTheme.relateSemanticColors.success,
    )
    ReadinessStatus.WARNING -> ReadinessStatusColors(
        container = MaterialTheme.relateSemanticColors.warning.copy(alpha = RelateAlpha.feedbackContainer),
        content = MaterialTheme.relateSemanticColors.warning,
    )
    ReadinessStatus.ACTION_REQUIRED -> ReadinessStatusColors(
        container = MaterialTheme.colorScheme.error.copy(alpha = RelateAlpha.feedbackContainer),
        content = MaterialTheme.colorScheme.error,
    )
}

internal fun ReadinessStatus.statusIcon(): ImageVector = when (this) {
    ReadinessStatus.OK -> Icons.Filled.CheckCircle
    ReadinessStatus.WARNING -> Icons.Filled.Warning
    ReadinessStatus.ACTION_REQUIRED -> Icons.Filled.Error
}
