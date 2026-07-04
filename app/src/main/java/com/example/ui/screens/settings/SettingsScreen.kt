package com.example.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.ui.feedback.asString
import com.example.ui.viewmodel.SettingsUiState
import com.example.ui.viewmodel.SettingsViewModel

internal object SettingsScreenTestTags {
    const val AI_CONFIGURATION_SECTION = "settings_ai_configuration_section"
    const val DATA_SYNC_SECTION = "settings_data_sync_section"
    const val SIGN_OUT_TRIGGER = "settings_sign_out_trigger"
    const val SIGN_OUT_DIALOG = "settings_sign_out_dialog"
    const val SIGN_OUT_CONFIRM = "settings_sign_out_confirm"
}

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit = {},
    onNavigateToStyleCoach: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToAutomationSetup: () -> Unit = {},
    onNavigateToActivityHistory: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
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

    val feedbackText = state.feedbackEvent?.message?.asString()
    LaunchedEffect(state.feedbackEvent?.id, feedbackText) {
        if (feedbackText != null) {
            snackbarHostState.showSnackbar(feedbackText)
            viewModel.clearFeedback()
            viewModel.clearSyncError()
        }
    }

    SettingsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBirthdayRemindersChange = viewModel::toggleBirthdayReminders,
        onAiWishGenerationChange = viewModel::toggleAiWishGeneration,
        onBiometricLockChange = viewModel::toggleBiometricLock,
        onNavigateToStyleCoach = onNavigateToStyleCoach,
        onNavigateToAutomationSetup = onNavigateToAutomationSetup,
        onGeminiApiKeyChange = viewModel::onGeminiApiKeyChange,
        onSaveGeminiApiKey = viewModel::saveGeminiApiKey,
        onSenderEmailChange = viewModel::onSenderEmailChange,
        onSenderEmailPasswordChange = viewModel::onSenderEmailPasswordChange,
        onSaveSenderEmailSettings = viewModel::saveSenderEmailSettings,
        onAutomationModeChange = viewModel::setAutomationMode,
        onQuietHoursStartChange = viewModel::onQuietHoursStartChange,
        onQuietHoursEndChange = viewModel::onQuietHoursEndChange,
        onSaveQuietHours = viewModel::saveQuietHours,
        onChannelBlackoutChange = viewModel::toggleChannelBlackout,
        onDismissLegacyDbNotice = viewModel::dismissLegacyDbNotice,
        onDismissSecurePrefsRecoveryNotice = viewModel::dismissSecurePrefsRecoveryNotice,
        onSyncContacts = syncContacts,
        onNavigateToBackupRestore = onNavigateToBackupRestore,
        onNavigateToActivityHistory = onNavigateToActivityHistory,
        onSignOut = {
            viewModel.signOut()
            onSignOut()
        },
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBirthdayRemindersChange: (Boolean) -> Unit = {},
    onAiWishGenerationChange: (Boolean) -> Unit = {},
    onBiometricLockChange: (Boolean) -> Unit = {},
    onNavigateToStyleCoach: () -> Unit = {},
    onNavigateToAutomationSetup: () -> Unit = {},
    onGeminiApiKeyChange: (String) -> Unit = {},
    onSaveGeminiApiKey: () -> Unit = {},
    onSenderEmailChange: (String) -> Unit = {},
    onSenderEmailPasswordChange: (String) -> Unit = {},
    onSaveSenderEmailSettings: () -> Unit = {},
    onAutomationModeChange: (ApprovalMode) -> Unit = {},
    onQuietHoursStartChange: (String) -> Unit = {},
    onQuietHoursEndChange: (String) -> Unit = {},
    onSaveQuietHours: () -> Unit = {},
    onChannelBlackoutChange: (MessageChannel, Boolean) -> Unit = { _, _ -> },
    onDismissLegacyDbNotice: () -> Unit = {},
    onDismissSecurePrefsRecoveryNotice: () -> Unit = {},
    onSyncContacts: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToActivityHistory: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    var showSignOutDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = RelateSpacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(RelateSize.minTouchTarget))
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(RelateSpacing.xl))

                SettingsAccountSection(state = state)

                Spacer(modifier = Modifier.height(RelateSpacing.xl))
                SettingsPreferencesSection(
                    state = state,
                    onBirthdayRemindersChange = onBirthdayRemindersChange,
                    onAiWishGenerationChange = onAiWishGenerationChange,
                    onBiometricLockChange = onBiometricLockChange,
                    onNavigateToStyleCoach = onNavigateToStyleCoach,
                    onNavigateToAutomationSetup = onNavigateToAutomationSetup,
                )

                Spacer(modifier = Modifier.height(RelateSpacing.xl))
                SettingsAiConfigurationSection(
                    state = state,
                    onGeminiApiKeyChange = onGeminiApiKeyChange,
                    onSaveGeminiApiKey = onSaveGeminiApiKey,
                    onSenderEmailChange = onSenderEmailChange,
                    onSenderEmailPasswordChange = onSenderEmailPasswordChange,
                    onSaveSenderEmailSettings = onSaveSenderEmailSettings,
                    onAutomationModeChange = onAutomationModeChange,
                    onQuietHoursStartChange = onQuietHoursStartChange,
                    onQuietHoursEndChange = onQuietHoursEndChange,
                    onSaveQuietHours = onSaveQuietHours,
                    onChannelBlackoutChange = onChannelBlackoutChange,
                )

                Spacer(modifier = Modifier.height(RelateSpacing.xl))
                SettingsDataSyncSection(
                    state = state,
                    onDismissLegacyDbNotice = onDismissLegacyDbNotice,
                    onDismissSecurePrefsRecoveryNotice = onDismissSecurePrefsRecoveryNotice,
                    onSyncContacts = onSyncContacts,
                    onNavigateToBackupRestore = onNavigateToBackupRestore,
                    onNavigateToActivityHistory = onNavigateToActivityHistory,
                )

                Spacer(modifier = Modifier.height(RelateSpacing.xl))
                SettingsAboutSection()

                Spacer(modifier = Modifier.height(RelateSpacing.xl))
                SettingsSignOutAction(onClick = { showSignOutDialog = true })

                Spacer(modifier = Modifier.height(RelateSpacing.xl))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(RelateSpacing.cardContent),
        )

        if (showSignOutDialog) {
            SignOutConfirmationDialog(
                onDismiss = { showSignOutDialog = false },
                onConfirm = {
                    showSignOutDialog = false
                    onSignOut()
                },
            )
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = RelateSpacing.sm),
    )
    content()
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    RelateGlassCard {
        content()
    }
}

@Composable
internal fun SettingsRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = RelateSpacing.cardContent, vertical = RelateSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(RelateSize.iconMd),
        )
        Spacer(modifier = Modifier.width(RelateSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SettingsToggle(title: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RelateSpacing.cardContent, vertical = RelateSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(RelateSize.iconMd),
        )
        Spacer(modifier = Modifier.width(RelateSpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onBackground,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = RelateAlpha.divider),
        modifier = Modifier.padding(horizontal = RelateSpacing.cardContent),
    )
}
