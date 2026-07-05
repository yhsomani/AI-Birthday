package com.example.ui.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ActivityLogType
import com.example.domain.model.activity.ActivityLogRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ActivityLogCard(
    entry: ActivityLogRecord,
    onOpenRoute: (String) -> Unit,
) {
    val dateFormat = rememberActivityDateFormat()
    RelateGlassCard(
        modifier = Modifier.testTag(ActivityHistoryTestTags.LOG_CARD_PREFIX + entry.id),
    ) {
        Row(
            modifier = Modifier.padding(RelateSpacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Icon(
                imageVector = entry.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
                    Text(
                        text = entry.severity.severityLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = entry.severity.severityColor(),
                    )
                    Text(
                        text = entry.status.statusLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Text(
                    text = dateFormat.format(Date(entry.createdAtMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.actionRoute?.let { route ->
                    Spacer(modifier = Modifier.height(RelateSpacing.sm))
                    Button(
                        onClick = { onOpenRoute(route) },
                        modifier = Modifier.testTag(ActivityHistoryTestTags.OPEN_ROUTE_PREFIX + entry.id),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(R.string.activity_history_open_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberActivityDateFormat(): SimpleDateFormat =
    SimpleDateFormat(stringResource(R.string.activity_history_date_pattern), Locale.getDefault())

private fun String.icon(): ImageVector = when (ActivityLogType.fromRaw(this)) {
    ActivityLogType.DISPATCH -> Icons.Filled.MailOutline
    ActivityLogType.BACKUP -> Icons.Filled.Backup
    ActivityLogType.MESSAGE -> Icons.Filled.MailOutline
    ActivityLogType.EVENT -> Icons.Filled.Event
    ActivityLogType.SYNC -> Icons.Filled.Sync
    ActivityLogType.ANALYTICS -> Icons.Filled.Analytics
    ActivityLogType.SETTINGS -> Icons.Filled.Settings
    ActivityLogType.AI -> Icons.Filled.SmartToy
    ActivityLogType.UNKNOWN -> Icons.Filled.History
}

@Composable
private fun String.severityColor() = when (ActivityLogSeverity.fromRaw(this)) {
    ActivityLogSeverity.ERROR -> MaterialTheme.colorScheme.error
    ActivityLogSeverity.WARNING -> MaterialTheme.relateSemanticColors.warning
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun String.severityLabel(): String = when (ActivityLogSeverity.fromRaw(this)) {
    ActivityLogSeverity.INFO -> stringResource(R.string.activity_severity_info)
    ActivityLogSeverity.WARNING -> stringResource(R.string.activity_severity_warning)
    ActivityLogSeverity.ERROR -> stringResource(R.string.activity_severity_error)
    ActivityLogSeverity.UNKNOWN -> stringResource(R.string.activity_severity_unknown)
}

@Composable
private fun String.statusLabel(): String = when (ActivityLogStatus.fromRaw(this)) {
    ActivityLogStatus.OPEN -> stringResource(R.string.activity_filter_open)
    ActivityLogStatus.RESOLVED -> stringResource(R.string.activity_filter_resolved)
    ActivityLogStatus.UNKNOWN -> stringResource(R.string.activity_status_unknown)
}
