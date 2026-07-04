package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.BuildConfig
import com.example.R
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.SettingsUiState

@Composable
internal fun SettingsDataSyncSection(
    state: SettingsUiState,
    onDismissLegacyDbNotice: () -> Unit,
    onDismissSecurePrefsRecoveryNotice: () -> Unit,
    onSyncContacts: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToActivityHistory: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_data_sync_section),
        modifier = Modifier.testTag(SettingsScreenTestTags.DATA_SYNC_SECTION),
    ) {
        SettingsCard {
            if (state.showLegacyDbNotice) {
                LegacyDbNotice(onDismiss = onDismissLegacyDbNotice)
                SettingsDivider()
            }
            if (state.showSecurePrefsRecoveryNotice) {
                SecurePrefsRecoveryNotice(onDismiss = onDismissSecurePrefsRecoveryNotice)
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
                onClick = { if (!state.isSyncing) onSyncContacts() },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Storage,
                title = stringResource(R.string.backup_restore_title),
                subtitle = stringResource(
                    R.string.settings_backup_restore_subtitle_with_status,
                    state.lastBackupTimestamp,
                ),
                onClick = onNavigateToBackupRestore,
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.History,
                title = stringResource(R.string.activity_history_title),
                subtitle = stringResource(R.string.settings_activity_history_subtitle),
                onClick = onNavigateToActivityHistory,
            )
        }
    }
}

@Composable
internal fun SettingsAboutSection() {
    SettingsSection(stringResource(R.string.settings_about)) {
        SettingsCard {
            SettingsRow(
                Icons.Filled.Info,
                stringResource(R.string.app_version),
                subtitle = BuildConfig.VERSION_NAME,
            )
        }
    }
}

@Composable
internal fun SettingsSignOutAction(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.sign_out),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(SettingsScreenTestTags.SIGN_OUT_TRIGGER)
            .padding(vertical = RelateSpacing.cardContent),
    )
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(RelateSize.iconSm),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun SecurePrefsRecoveryNotice(onDismiss: () -> Unit) {
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
                    text = stringResource(R.string.settings_secure_prefs_recovery_notice_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Text(
                    text = stringResource(R.string.settings_secure_prefs_recovery_notice_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(text = stringResource(R.string.settings_secure_prefs_recovery_notice_dismiss))
        }
    }
}
