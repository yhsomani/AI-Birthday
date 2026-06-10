package com.example.ui.screens.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.components.RelateStatusBanner
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSuccess
import com.example.core.ui.theme.RelateWarning
import com.example.ui.viewmodel.AiDoctorAction
import com.example.ui.viewmodel.AiDoctorSummary
import com.example.ui.viewmodel.AutomationSetupViewModel
import com.example.ui.viewmodel.ReadinessCheck
import com.example.ui.viewmodel.ReadinessStatus

@Composable
fun AutomationSetupScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenStyleCoach: () -> Unit = {},
    onOpenContacts: () -> Unit = {},
    onOpenActivityHistory: () -> Unit = {},
    viewModel: AutomationSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isIgnoringBattery = remember { context.isIgnoringBatteryOptimizations() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RelateScreen(
        title = stringResource(R.string.automation_setup_title),
        subtitle = stringResource(R.string.automation_setup_subtitle),
        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        navigationContentDescription = stringResource(R.string.back),
        onNavigationClick = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val summaryColors = state.summary.status.statusColors()
            RelateStatusBanner(
                title = state.summary.title,
                message = state.summary.detail,
                icon = state.summary.status.statusIcon(),
                containerColor = summaryColors.container,
                contentColor = summaryColors.content,
            )

            ReadinessDashboard(
                summary = state.summary,
                checks = state.checks,
                isRefreshing = state.isRefreshing,
                isSyncingContacts = state.isSyncingContacts,
                isTestingAi = state.isTestingAi,
                operationMessage = state.operationMessage,
                onRefresh = viewModel::refreshChecks,
                onSyncContacts = viewModel::syncContacts,
                onDryRun = viewModel::runSafeGenerationCheck,
                onTestAi = viewModel::testAiGeneration,
                onAction = { action ->
                    handleAiDoctorAction(
                        action = action,
                        context = context,
                        viewModel = viewModel,
                        onOpenSettings = onOpenSettings,
                        onOpenStyleCoach = onOpenStyleCoach,
                        onOpenContacts = onOpenContacts,
                        onOpenActivityHistory = onOpenActivityHistory,
                    )
                },
            )

            SetupCard(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = stringResource(R.string.automation_setup_whatsapp_card_title),
                body = stringResource(R.string.automation_setup_whatsapp_card_body),
                actionText = stringResource(R.string.automation_setup_action_open_accessibility),
                onClick = { context.safeStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )

            SetupCard(
                icon = Icons.Filled.BatterySaver,
                title = stringResource(R.string.automation_setup_battery_card_title),
                body = if (isIgnoringBattery) {
                    stringResource(R.string.automation_setup_battery_card_ignored)
                } else {
                    stringResource(R.string.automation_setup_battery_card_body)
                },
                actionText = stringResource(R.string.automation_setup_action_open_battery_settings),
                onClick = { context.openBatteryOptimizationSettings() },
            )

            SetupCard(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.automation_setup_notifications_card_title),
                body = stringResource(R.string.automation_setup_notifications_card_body),
                actionText = stringResource(R.string.automation_setup_action_app_settings),
                onClick = { context.openAppSettings() },
            )

            SetupCard(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.automation_setup_approval_card_title),
                body = stringResource(R.string.automation_setup_approval_card_body),
                actionText = stringResource(R.string.automation_setup_action_done),
                onClick = onBack,
                secondary = true,
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ReadinessDashboard(
    summary: AiDoctorSummary,
    checks: List<ReadinessCheck>,
    isRefreshing: Boolean,
    isSyncingContacts: Boolean,
    isTestingAi: Boolean,
    operationMessage: String?,
    onRefresh: () -> Unit,
    onSyncContacts: () -> Unit,
    onDryRun: () -> Unit,
    onTestAi: () -> Unit,
    onAction: (AiDoctorAction) -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = RelatePrimary,
                    )
                }
            }
            Text(
                text = summary.detail,
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )

            checks.forEach { check ->
                ReadinessRow(check = check, onAction = onAction)
            }

            operationMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    enabled = !isRefreshing,
                ) {
                    Text(stringResource(R.string.automation_setup_action_refresh))
                }
                OutlinedButton(
                    onClick = onDryRun,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.automation_setup_action_dry_run))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSyncContacts,
                    modifier = Modifier.weight(1f),
                    enabled = !isSyncingContacts,
                    colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary),
                ) {
                    if (isSyncingContacts) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RelateDarkBackground,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.automation_setup_action_sync_contacts),
                            color = RelateDarkBackground,
                        )
                    }
                }
                Button(
                    onClick = onTestAi,
                    modifier = Modifier.weight(1f),
                    enabled = !isTestingAi,
                    colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary),
                ) {
                    if (isTestingAi) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RelateDarkBackground,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.automation_setup_action_test_ai),
                            color = RelateDarkBackground,
                        )
                    }
                }
            }
        }
    }
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
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
                color = RelateOnSurfaceVariant,
            )
        }
        if (check.actionLabel != null && check.action != AiDoctorAction.NONE) {
            TextButton(
                onClick = { onAction(check.action) },
                modifier = Modifier.padding(top = 0.dp),
            ) {
                Text(check.actionLabel)
            }
        }
    }
}

private data class StatusColors(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
)

@Composable
private fun ReadinessStatus.statusColors(): StatusColors = when (this) {
    ReadinessStatus.OK -> StatusColors(
        container = RelateSuccess.copy(alpha = 0.16f),
        content = RelateSuccess,
    )
    ReadinessStatus.WARNING -> StatusColors(
        container = RelateWarning.copy(alpha = 0.16f),
        content = RelateWarning,
    )
    ReadinessStatus.ACTION_REQUIRED -> StatusColors(
        container = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
        content = MaterialTheme.colorScheme.error,
    )
}

private fun ReadinessStatus.statusIcon(): ImageVector = when (this) {
    ReadinessStatus.OK -> Icons.Filled.CheckCircle
    ReadinessStatus.WARNING -> Icons.Filled.Warning
    ReadinessStatus.ACTION_REQUIRED -> Icons.Filled.Error
}

private fun handleAiDoctorAction(
    action: AiDoctorAction,
    context: Context,
    viewModel: AutomationSetupViewModel,
    onOpenSettings: () -> Unit,
    onOpenStyleCoach: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenActivityHistory: () -> Unit,
) {
    when (action) {
        AiDoctorAction.NONE -> Unit
        AiDoctorAction.REFRESH -> viewModel.refreshChecks()
        AiDoctorAction.TEST_AI -> viewModel.testAiGeneration()
        AiDoctorAction.SYNC_CONTACTS -> viewModel.syncContacts()
        AiDoctorAction.OPEN_SETTINGS -> onOpenSettings()
        AiDoctorAction.OPEN_STYLE_COACH -> onOpenStyleCoach()
        AiDoctorAction.OPEN_CONTACTS -> onOpenContacts()
        AiDoctorAction.OPEN_ACTIVITY_HISTORY -> onOpenActivityHistory()
        AiDoctorAction.OPEN_ACCESSIBILITY_SETTINGS -> context.safeStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        AiDoctorAction.OPEN_BATTERY_SETTINGS -> context.openBatteryOptimizationSettings()
        AiDoctorAction.OPEN_APP_SETTINGS -> context.openAppSettings()
    }
}

@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionText: String,
    onClick: () -> Unit,
    secondary: Boolean = false,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, tint = RelatePrimary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (secondary) MaterialTheme.colorScheme.surfaceVariant else RelatePrimary,
                    contentColor = if (secondary) MaterialTheme.colorScheme.onSurface else RelateDarkBackground,
                ),
            ) {
                Text(actionText)
                if (!secondary) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(packageName) == true
}

private fun Context.openBatteryOptimizationSettings() {
    safeStartActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    })
}

private fun Context.openAppSettings() {
    safeStartActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    )
}

private fun Context.safeStartActivity(intent: Intent, fallback: Intent? = null) {
    try {
        startActivity(intent)
    } catch (_: Exception) {
        if (fallback != null) {
            try {
                startActivity(fallback)
            } catch (_: Exception) {
                // Settings intents can be unavailable on some OEM builds.
            }
        }
    }
}
