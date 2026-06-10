package com.example.ui.screens.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.runtime.LaunchedEffect
import com.example.BuildConfig
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit = {},
    onNavigateToStyleCoach: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToAutomationSetup: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showModeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.syncError) {
        state.syncError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearSyncError()
        }
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
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
            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(stringResource(R.string.settings_account_section)) {
                SettingsCard {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.userPhotoUrl != null) {
                            AsyncImage(
                                model = state.userPhotoUrl,
                                contentDescription = stringResource(R.string.profile_photo),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
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
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.userName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(state.userEmail, style = MaterialTheme.typography.bodySmall, color = RelateOnSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_preferences_section)) {
                SettingsCard {
                    SettingsToggle(
                        title = stringResource(R.string.settings_birthday_reminders),
                        icon = Icons.Filled.Notifications,
                        checked = state.birthdayReminders,
                    ) { viewModel.toggleBirthdayReminders(it) }
                    SettingsDivider()
                    SettingsToggle(
                        title = stringResource(R.string.settings_ai_wish_generation),
                        icon = Icons.Filled.SmartToy,
                        checked = state.aiWishGeneration,
                    ) { viewModel.toggleAiWishGeneration(it) }
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

            // ── AI Configuration (Gemini API Key) ──────────────────────────────
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_ai_configuration_section)) {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.geminiApiKey,
                            onValueChange = viewModel::onGeminiApiKeyChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.settings_gemini_api_key_placeholder), color = RelateOnSurfaceVariant) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                viewModel.saveGeminiApiKey()
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RelatePrimary,
                                unfocusedBorderColor = RelateSurfaceVariant,
                                focusedContainerColor = RelateSurfaceVariant.copy(alpha = 0.2f),
                                unfocusedContainerColor = RelateSurfaceVariant.copy(alpha = 0.2f),
                                focusedTextColor = RelateOnBackground,
                                unfocusedTextColor = RelateOnBackground,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.saveGeminiApiKey()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            if (state.geminiApiKeySaved) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = RelateDarkBackground,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.saved), color = RelateDarkBackground)
                            } else {
                                Icon(
                                    Icons.Filled.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = RelateDarkBackground,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.settings_save_api_key), color = RelateDarkBackground)
                            }
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
                                "FULLY_AUTO" to stringResource(R.string.automation_mode_fully_auto),
                                "SMART_APPROVE" to stringResource(R.string.automation_mode_smart_approve_default),
                                "VIP_APPROVE" to stringResource(R.string.automation_mode_vip_approve),
                                "ALWAYS_ASK" to stringResource(R.string.automation_mode_always_ask),
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setAutomationMode(mode)
                                        showModeMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_data_sync_section)) {
                SettingsCard {
                    if (state.showLegacyDbNotice) {
                        LegacyDbNotice(onDismiss = viewModel::dismissLegacyDbNotice)
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
                        onClick = { if (!state.isSyncing) viewModel.syncContacts() }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Storage,
                        title = stringResource(R.string.backup_restore_title),
                        subtitle = stringResource(R.string.settings_backup_restore_subtitle),
                        onClick = onNavigateToBackupRestore
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_about)) {
                SettingsCard {
                    SettingsRow(Icons.Filled.Info, stringResource(R.string.app_version), subtitle = BuildConfig.VERSION_NAME)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.sign_out),
                color = Color(0xFFEF4444),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        viewModel.signOut(context)
                        onSignOut()
                    })
                    .padding(vertical = 16.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun String.automationModeLabel(): String {
    return when (this) {
        "FULLY_AUTO" -> stringResource(R.string.automation_mode_fully_auto)
        "SMART_APPROVE" -> stringResource(R.string.automation_mode_smart_approve_default)
        "VIP_APPROVE" -> stringResource(R.string.automation_mode_vip_approve)
        "ALWAYS_ASK" -> stringResource(R.string.automation_mode_always_ask)
        else -> replace("_", " ")
    }
}

@Composable
private fun LegacyDbNotice(onDismiss: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_legacy_db_notice_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(4.dp))
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
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = RelatePrimary,
        modifier = Modifier.padding(bottom = 8.dp),
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = RelateOnSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = RelateOnSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
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
        color = RelateOnSurfaceVariant.copy(alpha = 0.12f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
