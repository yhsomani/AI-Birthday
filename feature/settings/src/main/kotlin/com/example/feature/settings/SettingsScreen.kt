package com.example.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.core.gemini.GeminiClient
import com.example.core.backup.BackupManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.core.auth.BiometricAuthManager
import com.example.core.prefs.SecurePrefs
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.ElevatedCard
import com.example.ui.theme.RelateAIColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userName: String = "User",
    userEmail: String = "",
    onNavigateStyleCoach: () -> Unit = {},
    onNavigateAnalytics: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { SecurePrefs(context) }
    var saved by remember { mutableStateOf(false) }
    var biometricEnabled by remember { mutableStateOf(prefs.isBiometricLockEnabled()) }
    var globalAutomationMode by remember { mutableStateOf(prefs.getGlobalAutomationMode()) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var restorePassphrase by remember { mutableStateOf("") }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) { restoreUri = uri; restorePassphrase = ""; showRestoreDialog = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, contentDescription = "Search settings", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Profile Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape)
                                .background(RelateAIColors.Primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = RelateAIColors.Primary, modifier = Modifier.size(30.dp))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(RelateAIColors.Primary.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Pro", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = RelateAIColors.Primary)
                                }
                            }
                            Text(text = userEmail.ifEmpty { "Not signed in" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // AI & Automation
            Text("AI & Automation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsNavItem(icon = Icons.Default.VpnKey, title = "Gemini API Key", subtitle = "Configured ✓", onClick = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavItem(icon = Icons.Default.Lock, title = "Default Approval Mode", subtitle = globalAutomationMode.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, onClick = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavItem(icon = Icons.Default.Schedule, title = "Default Send Time", subtitle = "9:00 AM", onClick = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavItem(icon = Icons.Default.AutoAwesome, title = "Style Coach", subtitle = "Train your writing style", onClick = onNavigateStyleCoach)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavItem(icon = Icons.Default.Analytics, title = "Analytics", subtitle = "Relationship health & insights", onClick = onNavigateAnalytics)
                }
            }

            // Notifications
            Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsToggleItem(icon = Icons.Default.Notifications, title = "Push Notifications", checked = true, onCheckedChange = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleItem(icon = Icons.Default.Notifications, title = "Approval Reminders", checked = true, onCheckedChange = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavItem(icon = Icons.Default.DateRange, title = "Event Alerts", subtitle = "Before 3 days", onClick = {})
                }
            }

            // Privacy & Security
            Text("Privacy & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsToggleItem(icon = Icons.Default.Fingerprint, title = "Biometric Lock", checked = biometricEnabled, onCheckedChange = {
                        biometricEnabled = it; prefs.setBiometricLockEnabled(it)
                    })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavItem(icon = Icons.Default.Lock, title = "Data Encryption", subtitle = "AES-256 Active", onClick = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavItem(icon = Icons.Default.CloudUpload, title = "Backup & Restore", subtitle = if (backupStatus != null) backupStatus!! else "Last backup: --", onClick = { showBackupDialog = true })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavItem(icon = Icons.Default.FileDownload, title = "Export Data", onClick = { restoreLauncher.launch(arrayOf("application/json")) })
                }
            }

            // About
            Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsInfoItem(icon = Icons.Default.Info, title = "App Version", value = "v3.3.0")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavItem(icon = Icons.Default.Article, title = "Privacy Policy", onClick = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavItem(icon = Icons.Default.Gavel, title = "Terms of Service", onClick = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSignOut() }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = RelateAIColors.Tertiary, modifier = Modifier.size(22.dp))
                        Text("Sign Out", color = RelateAIColors.Tertiary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Backup Encryption Passphrase") },
            text = {
                OutlinedTextField(value = backupPassphrase, onValueChange = { backupPassphrase = it }, label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            },
            confirmButton = {
                PrimaryButton(text = "Backup", onClick = {
                    if (backupPassphrase.isNotEmpty()) {
                        showBackupDialog = false
                        scope.launch {
                            try { val file = BackupManager.createBackup(context, backupPassphrase); backupStatus = "Last backup: ${file.name}" }
                            catch (e: Exception) { backupStatus = "Backup failed: ${e.message}" }
                        }
                    }
                })
            },
            dismissButton = { SecondaryButton(text = "Cancel", onClick = { showBackupDialog = false }) }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Encryption Passphrase") },
            text = {
                OutlinedTextField(value = restorePassphrase, onValueChange = { restorePassphrase = it }, label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            },
            confirmButton = {
                PrimaryButton(text = "Restore", onClick = {
                    if (restorePassphrase.isNotEmpty()) {
                        showRestoreDialog = false
                        scope.launch {
                            val result = BackupManager.restoreBackup(context, restoreUri!!, restorePassphrase)
                            backupStatus = result.fold(onSuccess = { "Restored $it records" }, onFailure = { "Restore failed: ${it.message}" })
                        }
                    }
                })
            },
            dismissButton = { SecondaryButton(text = "Cancel", onClick = { showRestoreDialog = false }) }
        )
    }
}

@Composable
private fun SettingsNavItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsToggleItem(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsInfoItem(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
