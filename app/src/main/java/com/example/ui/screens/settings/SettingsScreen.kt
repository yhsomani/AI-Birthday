package com.example.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.runtime.LaunchedEffect
import com.example.BuildConfig
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.RelateSurfaceVariant
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
    onSyncContacts: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToActivityHistory: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    var showModeMenu by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RelateDarkBackground)
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

                SettingsSection(stringResource(R.string.settings_account_section)) {
                    SettingsCard {
                        Row(
                            modifier = Modifier.padding(RelateSpacing.cardContent),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.userPhotoUrl != null) {
                                AsyncImage(
                                    model = state.userPhotoUrl,
                                    contentDescription = stringResource(R.string.profile_photo),
                                    modifier = Modifier
                                        .size(RelateSize.minTouchTarget)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(RelateSize.minTouchTarget)
                                        .clip(CircleShape)
                                        .background(RelateSurfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        state.userName.take(1).uppercase(),
                                        color = RelateOnBackground,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(RelateSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(state.userName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(state.userEmail, style = MaterialTheme.typography.bodySmall, color = RelateOnSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(RelateSpacing.sm))
                }

                Spacer(modifier = Modifier.height(RelateSpacing.xl))
                SettingsSection(stringResource(R.string.settings_preferences_section)) {
                    SettingsCard {
                        SettingsToggle(
                            title = stringResource(R.string.settings_birthday_reminders),
                            icon = Icons.Filled.Notifications,
                            checked = state.birthdayReminders,
                        ) { onBirthdayRemindersChange(it) }
                        SettingsDivider()
                        SettingsToggle(
                            title = stringResource(R.string.settings_ai_wish_generation),
                            icon = Icons.Filled.SmartToy,
                            checked = state.aiWishGeneration,
                        ) { onAiWishGenerationChange(it) }
                        SettingsDivider()
                        SettingsToggle(
                            title = stringResource(R.string.settings_biometric_lock),
                            icon = Icons.Filled.Security,
                            checked = state.biometricLockEnabled,
                        ) { onBiometricLockChange(it) }
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Filled.Person,
                            title = stringResource(R.string.settings_ai_style_coach),
                            subtitle = stringResource(R.string.settings_ai_style_coach_subtitle),
                            onClick = onNavigateToStyleCoach
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Filled.Security,
                            title = stringResource(R.string.settings_automation_setup),
                            subtitle = stringResource(R.string.settings_automation_setup_subtitle),
                            onClick = onNavigateToAutomationSetup
                        )
                    }
                }

                // AI configuration and send readiness
            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            SettingsSection(
                title = stringResource(R.string.settings_ai_configuration_section),
                modifier = Modifier.testTag(SettingsScreenTestTags.AI_CONFIGURATION_SECTION),
            ) {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = RelateSpacing.cardContent, vertical = RelateSpacing.md)) {
                        Text(
                            text = stringResource(R.string.settings_gemini_api_key),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_gemini_api_key_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = RelateOnSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(RelateSpacing.sm))
                        OutlinedTextField(
                            value = state.geminiApiKey,
                            onValueChange = onGeminiApiKeyChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.settings_gemini_api_key_placeholder), color = RelateOnSurfaceVariant) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                onSaveGeminiApiKey()
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RelatePrimary,
                                unfocusedBorderColor = RelateSurfaceVariant,
                                focusedContainerColor = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                                unfocusedContainerColor = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                                focusedTextColor = RelateOnBackground,
                                unfocusedTextColor = RelateOnBackground,
                            ),
                            shape = RoundedCornerShape(RelateRadius.control),
                        )
                        Spacer(modifier = Modifier.height(RelateSpacing.sm))
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSaveGeminiApiKey()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary),
                            shape = RoundedCornerShape(RelateRadius.control),
                        ) {
                            if (state.geminiApiKeySaved) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(RelateSize.iconSm),
                                    tint = RelateDarkBackground,
                                )
                                Spacer(modifier = Modifier.width(RelateSpacing.xs))
                                Text(stringResource(R.string.saved), color = RelateDarkBackground)
                            } else {
                                Icon(
                                    Icons.Filled.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(RelateSize.iconSm),
                                    tint = RelateDarkBackground,
                                )
                                Spacer(modifier = Modifier.width(RelateSpacing.xs))
                                Text(stringResource(R.string.settings_save_api_key), color = RelateDarkBackground)
                            }
                        }
                    }
                    SettingsDivider()
                    Column(modifier = Modifier.padding(horizontal = RelateSpacing.cardContent, vertical = RelateSpacing.md)) {
                        Text(
                            text = stringResource(R.string.settings_email_sending_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_email_sending_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = RelateOnSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(RelateSpacing.sm))
                        OutlinedTextField(
                            value = state.senderEmail,
                            onValueChange = onSenderEmailChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_sender_email)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RelatePrimary,
                                unfocusedBorderColor = RelateSurfaceVariant,
                                focusedContainerColor = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                                unfocusedContainerColor = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                                focusedTextColor = RelateOnBackground,
                                unfocusedTextColor = RelateOnBackground,
                            ),
                            shape = RoundedCornerShape(RelateRadius.control),
                        )
                        Spacer(modifier = Modifier.height(RelateSpacing.sm))
                        OutlinedTextField(
                            value = state.senderEmailPassword,
                            onValueChange = onSenderEmailPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_app_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RelatePrimary,
                                unfocusedBorderColor = RelateSurfaceVariant,
                                focusedContainerColor = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                                unfocusedContainerColor = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                                focusedTextColor = RelateOnBackground,
                                unfocusedTextColor = RelateOnBackground,
                            ),
                            shape = RoundedCornerShape(RelateRadius.control),
                        )
                        Spacer(modifier = Modifier.height(RelateSpacing.sm))
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSaveSenderEmailSettings()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary),
                            shape = RoundedCornerShape(RelateRadius.control),
                        ) {
                            Text(
                                text = if (state.senderEmailSaved) {
                                    stringResource(R.string.saved)
                                } else {
                                    stringResource(R.string.settings_save_email_settings)
                                },
                                color = RelateDarkBackground,
                            )
                        }
                    }
                    SettingsDivider()
                    // Automation Mode selector
                    Box {
                        SettingsRow(
                            icon = Icons.Filled.SmartToy,
                            title = stringResource(R.string.settings_automation_mode),
                            subtitle = state.automationMode.automationModeLabel(),
                            onClick = { showModeMenu = true }
                        )
                        DropdownMenu(
                            expanded = showModeMenu,
                            onDismissRequest = { showModeMenu = false },
                        ) {
                            listOf(
                                ApprovalMode.FULLY_AUTO to stringResource(R.string.automation_mode_fully_auto),
                                ApprovalMode.SMART_APPROVE to stringResource(R.string.automation_mode_smart_approve_default),
                                ApprovalMode.VIP_APPROVE to stringResource(R.string.automation_mode_vip_approve),
                                ApprovalMode.ALWAYS_ASK to stringResource(R.string.automation_mode_always_ask),
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        onAutomationModeChange(mode)
                                        showModeMenu = false
                                    }
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    QuietHoursEditor(
                        start = state.quietHoursStart,
                        end = state.quietHoursEnd,
                        onStartChange = onQuietHoursStartChange,
                        onEndChange = onQuietHoursEndChange,
                        onSave = onSaveQuietHours,
                    )
                    SettingsDivider()
                    ChannelBlackoutEditor(
                        smsDisabled = state.channelBlackoutSms,
                        whatsAppDisabled = state.channelBlackoutWhatsApp,
                        emailDisabled = state.channelBlackoutEmail,
                        onSmsChange = { onChannelBlackoutChange(MessageChannel.SMS, it) },
                        onWhatsAppChange = { onChannelBlackoutChange(MessageChannel.WHATSAPP, it) },
                        onEmailChange = { onChannelBlackoutChange(MessageChannel.EMAIL, it) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            SettingsSection(
                title = stringResource(R.string.settings_data_sync_section),
                modifier = Modifier.testTag(SettingsScreenTestTags.DATA_SYNC_SECTION),
            ) {
                SettingsCard {
                    if (state.showLegacyDbNotice) {
                        LegacyDbNotice(onDismiss = onDismissLegacyDbNotice)
                        SettingsDivider()
                    }
                    val subtitle = if (state.isSyncing) {
                        stringResource(R.string.settings_syncing)
                    } else {
                        stringResource(R.string.settings_last_synced_format, state.lastSyncTimestamp)
                    }
                    SettingsRow(
                        icon = Icons.Filled.CloudSync,
                        title = stringResource(R.string.settings_sync_contacts),
                        subtitle = subtitle,
                        onClick = { if (!state.isSyncing) onSyncContacts() }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Storage,
                        title = stringResource(R.string.backup_restore_title),
                        subtitle = stringResource(
                            R.string.settings_backup_restore_subtitle_with_status,
                            state.lastBackupTimestamp,
                        ),
                        onClick = onNavigateToBackupRestore
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.History,
                        title = stringResource(R.string.activity_history_title),
                        subtitle = stringResource(R.string.settings_activity_history_subtitle),
                        onClick = onNavigateToActivityHistory
                    )
                }
            }

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            SettingsSection(stringResource(R.string.settings_about)) {
                SettingsCard {
                    SettingsRow(Icons.Filled.Info, stringResource(R.string.app_version), subtitle = BuildConfig.VERSION_NAME)
                }
            }

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            Text(
                text = stringResource(R.string.sign_out),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        showSignOutDialog = true
                    })
                    .testTag(SettingsScreenTestTags.SIGN_OUT_TRIGGER)
                    .padding(vertical = RelateSpacing.cardContent),
            )

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
internal fun SignOutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(SettingsScreenTestTags.SIGN_OUT_DIALOG),
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.settings_sign_out_title),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_sign_out_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RelateOnSurfaceVariant,
                )
                SignOutChecklistItem(text = stringResource(R.string.settings_sign_out_check_local_data))
                SignOutChecklistItem(text = stringResource(R.string.settings_sign_out_check_preferences))
                SignOutChecklistItem(text = stringResource(R.string.settings_sign_out_check_external))
                SignOutChecklistItem(text = stringResource(R.string.settings_sign_out_check_backup))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(SettingsScreenTestTags.SIGN_OUT_CONFIRM),
            ) {
                Text(
                    text = stringResource(R.string.settings_sign_out_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_sign_out_cancel))
            }
        },
    )
}

@Composable
private fun SignOutChecklistItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = RelatePrimary,
            modifier = Modifier.size(RelateSize.iconSm),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = RelateOnSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuietHoursEditor(
    start: String,
    end: String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = RelateSpacing.cardContent, vertical = RelateSpacing.md)) {
        Text(
            text = stringResource(R.string.settings_quiet_hours_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_quiet_hours_help),
            style = MaterialTheme.typography.bodySmall,
            color = RelateOnSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
            OutlinedTextField(
                value = start,
                onValueChange = onStartChange,
                label = { Text(stringResource(R.string.settings_quiet_hrs_start)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = end,
                onValueChange = onEndChange,
                label = { Text(stringResource(R.string.settings_quiet_hrs_end)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        TextButton(onClick = onSave, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.settings_save_quiet_hours))
        }
    }
}

@Composable
private fun ChannelBlackoutEditor(
    smsDisabled: Boolean,
    whatsAppDisabled: Boolean,
    emailDisabled: Boolean,
    onSmsChange: (Boolean) -> Unit,
    onWhatsAppChange: (Boolean) -> Unit,
    onEmailChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = RelateSpacing.cardContent, vertical = RelateSpacing.md)) {
        Text(
            text = stringResource(R.string.settings_channel_blackout_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_channel_blackout_help),
            style = MaterialTheme.typography.bodySmall,
            color = RelateOnSurfaceVariant,
        )
        ChannelBlackoutRow(stringResource(R.string.channel_sms), smsDisabled, onSmsChange)
        ChannelBlackoutRow(stringResource(R.string.channel_whatsapp), whatsAppDisabled, onWhatsAppChange)
        ChannelBlackoutRow(stringResource(R.string.channel_email), emailDisabled, onEmailChange)
    }
}

@Composable
private fun ChannelBlackoutRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = RelateSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RelateOnBackground,
                checkedTrackColor = RelatePrimary,
                uncheckedThumbColor = RelateOnSurfaceVariant,
                uncheckedTrackColor = RelateSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun ApprovalMode.automationModeLabel(): String {
    return when (this) {
        ApprovalMode.FULLY_AUTO -> stringResource(R.string.automation_mode_fully_auto)
        ApprovalMode.SMART_APPROVE -> stringResource(R.string.automation_mode_smart_approve_default)
        ApprovalMode.VIP_APPROVE -> stringResource(R.string.automation_mode_vip_approve)
        ApprovalMode.ALWAYS_ASK -> stringResource(R.string.automation_mode_always_ask)
        ApprovalMode.DEFAULT,
        ApprovalMode.UNKNOWN -> stringResource(R.string.automation_mode_default)
    }
}

@Composable
private fun LegacyDbNotice(onDismiss: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = RelateSpacing.cardContent, vertical = RelateSpacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(RelateSize.iconMd),
            )
            Spacer(modifier = Modifier.width(RelateSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_legacy_db_notice_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Text(
                    text = stringResource(R.string.settings_legacy_db_notice_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(text = stringResource(R.string.settings_legacy_db_notice_dismiss))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = RelatePrimary,
        modifier = modifier.padding(bottom = RelateSpacing.sm),
    )
    content()
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    RelateGlassCard {
        content()
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit = {}) {
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
            tint = RelateOnSurfaceVariant,
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
                    color = RelateOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsToggle(title: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RelateSpacing.cardContent, vertical = RelateSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = RelateOnSurfaceVariant,
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
                checkedThumbColor = RelateOnBackground,
                checkedTrackColor = RelatePrimary,
                uncheckedThumbColor = RelateOnSurfaceVariant,
                uncheckedTrackColor = RelateSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = RelateOnSurfaceVariant.copy(alpha = RelateAlpha.divider),
        modifier = Modifier.padding(horizontal = RelateSpacing.cardContent),
    )
}
