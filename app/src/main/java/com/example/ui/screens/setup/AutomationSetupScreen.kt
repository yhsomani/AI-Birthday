package com.example.ui.screens.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.components.RelateStatusBanner
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.ui.viewmodel.AiDoctorAction
import com.example.ui.viewmodel.AiDoctorRecommendedFix
import com.example.ui.viewmodel.AiDoctorSummary
import com.example.ui.viewmodel.AutomationSetupViewModel
import com.example.ui.viewmodel.MessageChannelFilter
import com.example.ui.viewmodel.ReadinessGroup
import com.example.ui.viewmodel.ReadinessCheck
import com.example.ui.viewmodel.ReadinessStatus
import com.example.ui.viewmodel.SetupProgressSummary

internal object AutomationSetupTestTags {
    const val DASHBOARD = "automation_setup_dashboard"
    const val WHATSAPP_CARD = "automation_setup_whatsapp_card"
    const val CONTENT_BOTTOM = "automation_setup_content_bottom"
}

@Composable
fun AutomationSetupScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenStyleCoach: () -> Unit = {},
    onOpenContacts: () -> Unit = {},
    onOpenMessages: (MessageChannelFilter?) -> Unit = {},
    onOpenActivityHistory: () -> Unit = {},
    viewModel: AutomationSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isIgnoringBattery = remember { context.isIgnoringBatteryOptimizations() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.syncContacts()
    }
    val syncContacts = {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.syncContacts()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    AutomationSetupContent(
        state = state,
        isIgnoringBatteryOptimizations = isIgnoringBattery,
        onBack = onBack,
        onRefresh = viewModel::refreshChecks,
        onSyncContacts = syncContacts,
        onDryRun = viewModel::runSafeGenerationCheck,
        onTestAi = viewModel::testAiGeneration,
        onTestEmail = viewModel::testEmailSend,
        onWhatsAppConsentChange = viewModel::setWhatsAppAutomationConsent,
        onOpenAccessibilitySettings = {
            context.safeStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onOpenBatterySettings = { context.openBatteryOptimizationSettings() },
        onOpenAppSettings = { context.openAppSettings() },
        onAction = { action ->
            handleAiDoctorAction(
                action = action,
                context = context,
                viewModel = viewModel,
                onOpenSettings = onOpenSettings,
                onOpenStyleCoach = onOpenStyleCoach,
                onOpenContacts = onOpenContacts,
                onOpenMessages = onOpenMessages,
                onOpenActivityHistory = onOpenActivityHistory,
            )
        },
    )
}

@Composable
internal fun AutomationSetupContent(
    state: com.example.ui.viewmodel.AutomationSetupUiState,
    isIgnoringBatteryOptimizations: Boolean,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onSyncContacts: () -> Unit = {},
    onDryRun: () -> Unit = {},
    onTestAi: () -> Unit = {},
    onTestEmail: () -> Unit = {},
    onWhatsAppConsentChange: (Boolean) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onAction: (AiDoctorAction) -> Unit = {},
) {
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
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.lg),
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
                setupProgress = state.setupProgress,
                recommendedFix = state.recommendedFix,
                checks = state.checks,
                isRefreshing = state.isRefreshing,
                isSyncingContacts = state.isSyncingContacts,
                isTestingAi = state.isTestingAi,
                isTestingEmail = state.isTestingEmail,
                operationMessage = state.operationMessage,
                onRefresh = onRefresh,
                onSyncContacts = onSyncContacts,
                onDryRun = onDryRun,
                onTestAi = onTestAi,
                onTestEmail = onTestEmail,
                onAction = onAction,
                modifier = Modifier.testTag(AutomationSetupTestTags.DASHBOARD),
            )

            SetupCard(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = stringResource(R.string.automation_setup_whatsapp_card_title),
                body = stringResource(R.string.automation_setup_whatsapp_card_body),
                actionText = stringResource(R.string.automation_setup_action_open_accessibility),
                onClick = onOpenAccessibilitySettings,
                consentChecked = state.whatsAppAutomationConsentGranted,
                consentText = stringResource(R.string.automation_setup_whatsapp_consent_label),
                onConsentChange = onWhatsAppConsentChange,
                modifier = Modifier.testTag(AutomationSetupTestTags.WHATSAPP_CARD),
            )

            SetupCard(
                icon = Icons.Filled.BatterySaver,
                title = stringResource(R.string.automation_setup_battery_card_title),
                body = if (isIgnoringBatteryOptimizations) {
                    stringResource(R.string.automation_setup_battery_card_ignored)
                } else {
                    stringResource(R.string.automation_setup_battery_card_body)
                },
                actionText = stringResource(R.string.automation_setup_action_open_battery_settings),
                onClick = onOpenBatterySettings,
            )

            SetupCard(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.automation_setup_notifications_card_title),
                body = stringResource(R.string.automation_setup_notifications_card_body),
                actionText = stringResource(R.string.automation_setup_action_app_settings),
                onClick = onOpenAppSettings,
            )

            SetupCard(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.automation_setup_approval_card_title),
                body = stringResource(R.string.automation_setup_approval_card_body),
                actionText = stringResource(R.string.automation_setup_action_done),
                onClick = onBack,
                secondary = true,
            )

            Spacer(
                modifier = Modifier
                    .height(RelateSpacing.xl)
                    .testTag(AutomationSetupTestTags.CONTENT_BOTTOM),
            )
        }
    }
}

@Composable
private fun ReadinessDashboard(
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

@Composable
private fun ReadinessActionPanel(
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

private val readinessGroupOrder = listOf(
    ReadinessGroup.REQUIRED,
    ReadinessGroup.QUALITY,
    ReadinessGroup.RELIABILITY,
    ReadinessGroup.RECOVERY,
)

@Composable
private fun SetupProgressStrip(summary: SetupProgressSummary) {
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
private fun RecommendedFixSection(
    fix: AiDoctorRecommendedFix,
    onAction: (AiDoctorAction) -> Unit,
) {
    val color = fix.status.statusColors().content
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = fix.status.statusColors().container,
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
                tint = color,
                modifier = Modifier.size(RelateSize.iconMd),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.automation_setup_recommended_fix),
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
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
private fun ReadinessGroupSection(
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

private data class StatusColors(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
)

@Composable
private fun ReadinessStatus.statusColors(): StatusColors = when (this) {
    ReadinessStatus.OK -> StatusColors(
        container = MaterialTheme.relateSemanticColors.success.copy(alpha = RelateAlpha.feedbackContainer),
        content = MaterialTheme.relateSemanticColors.success,
    )
    ReadinessStatus.WARNING -> StatusColors(
        container = MaterialTheme.relateSemanticColors.warning.copy(alpha = RelateAlpha.feedbackContainer),
        content = MaterialTheme.relateSemanticColors.warning,
    )
    ReadinessStatus.ACTION_REQUIRED -> StatusColors(
        container = MaterialTheme.colorScheme.error.copy(alpha = RelateAlpha.feedbackContainer),
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
    onOpenMessages: (MessageChannelFilter?) -> Unit,
    onOpenActivityHistory: () -> Unit,
) {
    when (action) {
        AiDoctorAction.NONE -> Unit
        AiDoctorAction.REFRESH -> viewModel.refreshChecks()
        AiDoctorAction.TEST_AI -> viewModel.testAiGeneration()
        AiDoctorAction.TEST_EMAIL -> viewModel.testEmailSend()
        AiDoctorAction.SYNC_CONTACTS -> viewModel.syncContacts()
        AiDoctorAction.OPEN_SETTINGS -> onOpenSettings()
        AiDoctorAction.OPEN_STYLE_COACH -> onOpenStyleCoach()
        AiDoctorAction.OPEN_CONTACTS -> onOpenContacts()
        AiDoctorAction.OPEN_MESSAGES -> onOpenMessages(null)
        AiDoctorAction.OPEN_SMS_MESSAGES -> onOpenMessages(MessageChannelFilter.SMS)
        AiDoctorAction.OPEN_WHATSAPP_MESSAGES -> onOpenMessages(MessageChannelFilter.WHATSAPP)
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
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    consentChecked: Boolean? = null,
    consentText: String? = null,
    onConsentChange: ((Boolean) -> Unit)? = null,
) {
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(RelateSize.iconLg),
                )
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (consentChecked != null && consentText != null && onConsentChange != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                ) {
                    Checkbox(
                        checked = consentChecked,
                        onCheckedChange = onConsentChange,
                    )
                    Text(
                        text = consentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = RelateSize.primaryButtonHeight),
                shape = RoundedCornerShape(RelateRadius.control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (secondary) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (secondary) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                ),
            ) {
                Text(actionText)
                if (!secondary) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(start = RelateSpacing.sm),
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
