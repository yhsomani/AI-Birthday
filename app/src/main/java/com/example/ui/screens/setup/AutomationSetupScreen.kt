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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.AiDoctorAction
import com.example.ui.viewmodel.AutomationSetupUiState
import com.example.ui.viewmodel.AutomationSetupViewModel
import com.example.ui.viewmodel.MessageChannelFilter

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
    state: AutomationSetupUiState,
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
            AutomationSetupSummaryBanner(
                summary = state.summary,
                status = state.setupActionReadiness.state,
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

            AutomationSetupSupportCards(
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                whatsAppAutomationConsentGranted = state.whatsAppAutomationConsentGranted,
                onWhatsAppConsentChange = onWhatsAppConsentChange,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenAppSettings = onOpenAppSettings,
                onBack = onBack,
            )

            Spacer(
                modifier = Modifier
                    .height(RelateSpacing.xl)
                    .testTag(AutomationSetupTestTags.CONTENT_BOTTOM),
            )
        }
    }
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
