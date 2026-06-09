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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.components.RelateStatusBanner
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSuccess
import com.example.core.ui.theme.RelateWarning
import com.example.ui.viewmodel.AutomationSetupViewModel
import com.example.ui.viewmodel.ReadinessCheck
import com.example.ui.viewmodel.ReadinessStatus

@Composable
fun AutomationSetupScreen(
    onBack: () -> Unit,
    viewModel: AutomationSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isAccessibilityEnabled = remember { context.isWhatsAppAutomationServiceEnabled() }
    val isIgnoringBattery = remember { context.isIgnoringBatteryOptimizations() }
    val state by viewModel.uiState.collectAsState()

    RelateScreen(
        title = "Automation Setup",
        subtitle = "Prepare WhatsApp automation, reminders, and reliable background sends.",
        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        navigationContentDescription = "Back",
        onNavigationClick = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RelateStatusBanner(
                title = if (isAccessibilityEnabled) "WhatsApp automation is enabled" else "WhatsApp automation needs setup",
                message = if (isAccessibilityEnabled) {
                    "RelateAI can open WhatsApp and send already-approved messages."
                } else {
                    "Enable the RelateAI accessibility service only if you want automatic WhatsApp sends."
                },
                icon = if (isAccessibilityEnabled) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.Chat,
                containerColor = if (isAccessibilityEnabled) RelateSuccess.copy(alpha = 0.16f) else RelateWarning.copy(alpha = 0.16f),
                contentColor = if (isAccessibilityEnabled) RelateSuccess else RelateWarning,
            )

            ReadinessDashboard(
                checks = state.checks,
                isRefreshing = state.isRefreshing,
                isSyncingContacts = state.isSyncingContacts,
                isTestingAi = state.isTestingAi,
                operationMessage = state.operationMessage,
                onRefresh = viewModel::refreshChecks,
                onSyncContacts = viewModel::syncContacts,
                onDryRun = viewModel::runSafeGenerationCheck,
                onTestAi = viewModel::testAiGeneration,
            )

            SetupCard(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "Enable WhatsApp automation",
                body = "Open Accessibility settings and enable RelateAI - Auto WhatsApp. The service only looks for WhatsApp compose and send controls.",
                actionText = "Open Accessibility",
                onClick = { context.safeStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )

            SetupCard(
                icon = Icons.Filled.BatterySaver,
                title = "Allow background reliability",
                body = if (isIgnoringBattery) {
                    "Battery optimization is already ignored for RelateAI."
                } else {
                    "Review OS battery settings if scheduled workers are delayed on this device."
                },
                actionText = "Open Battery Settings",
                onClick = { context.openBatteryOptimizationSettings() },
            )

            SetupCard(
                icon = Icons.Filled.Notifications,
                title = "Review notification access",
                body = "Notifications are used for reminders and approval prompts before messages are sent.",
                actionText = "App Settings",
                onClick = { context.openAppSettings() },
            )

            SetupCard(
                icon = Icons.Filled.Security,
                title = "Automation stays approval-first",
                body = "Use SMART_APPROVE, VIP_APPROVE, or ALWAYS_ASK in Settings when you want review before scheduling or sending.",
                actionText = "Done",
                onClick = onBack,
                secondary = true,
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ReadinessDashboard(
    checks: List<ReadinessCheck>,
    isRefreshing: Boolean,
    isSyncingContacts: Boolean,
    isTestingAi: Boolean,
    operationMessage: String?,
    onRefresh: () -> Unit,
    onSyncContacts: () -> Unit,
    onDryRun: () -> Unit,
    onTestAi: () -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Automation Readiness",
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

            checks.forEach { check ->
                ReadinessRow(check)
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
                    Text("Refresh")
                }
                OutlinedButton(
                    onClick = onDryRun,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Dry Run")
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
                        Text("Sync Contacts", color = RelateDarkBackground)
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
                        Text("Test AI", color = RelateDarkBackground)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessRow(check: ReadinessCheck) {
    val (icon, color) = when (check.status) {
        ReadinessStatus.OK -> Icons.Filled.CheckCircle to RelateSuccess
        ReadinessStatus.WARNING -> Icons.Filled.Warning to RelateWarning
        ReadinessStatus.ACTION_REQUIRED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
    }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Column {
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

private fun Context.isWhatsAppAutomationServiceEnabled(): Boolean {
    val expectedService = "$packageName/com.example.core.accessibility.WhatsAppAccessibilityService"
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabledServices.split(':').any {
        it.equals(expectedService, ignoreCase = true)
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
