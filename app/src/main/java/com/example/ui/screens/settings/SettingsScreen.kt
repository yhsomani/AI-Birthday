package com.example.ui.screens.settings

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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.components.RelateGlassCard
import com.example.ui.theme.RelateDarkBackground
import com.example.ui.theme.RelateOnBackground
import com.example.ui.theme.RelateOnSurfaceVariant
import com.example.ui.theme.RelatePrimary
import com.example.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit = {},
    onNavigateToStyleCoach: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
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
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection("Account") {
                SettingsCard {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.userPhotoUrl != null) {
                            AsyncImage(
                                model = state.userPhotoUrl,
                                contentDescription = "Profile photo",
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
            SettingsSection("Preferences") {
                SettingsCard {
                    SettingsToggle(
                        title = "Birthday Reminders",
                        icon = Icons.Filled.Notifications,
                        checked = state.birthdayReminders,
                    ) { viewModel.toggleBirthdayReminders(it) }
                    SettingsDivider()
                    SettingsToggle(
                        title = "AI Wish Generation",
                        icon = Icons.Filled.SmartToy,
                        checked = state.aiWishGeneration,
                    ) { viewModel.toggleAiWishGeneration(it) }
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Person,
                        title = "AI Style Coach",
                        subtitle = "Train AI to match your personal writing style",
                        onClick = onNavigateToStyleCoach
                    )
                }
            }

            // ── AI Configuration (Gemini API Key) ──────────────────────────────
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSection("AI Configuration") {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "Gemini API Key",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Required for AI wish generation. Get yours at makersuite.google.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = RelateOnSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.geminiApiKey,
                            onValueChange = viewModel::onGeminiApiKeyChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("AIza...", color = RelateOnSurfaceVariant) },
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
                                Text("Saved", color = RelateDarkBackground)
                            } else {
                                Icon(
                                    Icons.Filled.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = RelateDarkBackground,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save API Key", color = RelateDarkBackground)
                            }
                        }
                    }
                    SettingsDivider()
                    // Automation Mode selector
                    Box {
                        SettingsRow(
                            icon = Icons.Filled.SmartToy,
                            title = "Automation Mode",
                            subtitle = state.automationMode.replace("_", " "),
                            onClick = { showModeMenu = true }
                        )
                        DropdownMenu(
                            expanded = showModeMenu,
                            onDismissRequest = { showModeMenu = false },
                        ) {
                            listOf(
                                "FULLY_AUTO" to "Fully Auto",
                                "SMART_APPROVE" to "Smart Approve (default)",
                                "VIP_APPROVE" to "VIP Approve",
                                "ALWAYS_ASK" to "Always Ask",
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
            SettingsSection("Data & Sync") {
                SettingsCard {
                    val subtitle = if (state.isSyncing) "Syncing..." else "Last synced: ${state.lastSyncTimestamp}"
                    SettingsRow(
                        icon = Icons.Filled.CloudSync,
                        title = "Sync Contacts",
                        subtitle = subtitle,
                        onClick = { if (!state.isSyncing) viewModel.syncContacts() }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Storage,
                        title = "Backup & Restore",
                        subtitle = "Encrypted database import and export",
                        onClick = onNavigateToBackupRestore
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SettingsSection("About") {
                SettingsCard {
                    SettingsRow(Icons.Filled.Info, "App Version", subtitle = "1.0.0")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Sign Out",
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
