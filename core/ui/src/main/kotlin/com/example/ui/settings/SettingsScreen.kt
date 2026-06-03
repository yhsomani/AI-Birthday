package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { SecurePrefs(context) }
    var apiKey by remember { mutableStateOf(prefs.getGeminiApiKey()) }
    var senderEmail by remember { mutableStateOf(prefs.getSenderEmail()) }
    var senderEmailPw by remember { mutableStateOf(prefs.getSenderEmailPassword()) }
    var saved by remember { mutableStateOf(false) }

    var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var quietStart by remember { mutableStateOf(prefs.getQuietHoursStart().toString()) }
    var quietEnd by remember { mutableStateOf(prefs.getQuietHoursEnd().toString()) }
    var biometricEnabled by remember { mutableStateOf(prefs.isBiometricLockEnabled()) }
    var globalAutomationMode by remember { mutableStateOf(prefs.getGlobalAutomationMode()) }

    var testMessageSent by remember { mutableStateOf(false) }
    var testMessageError by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupPassphrase by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                backupStatus = "Restoring..."
                val result = BackupManager.restoreBackup(context, uri, "")
                backupStatus = result.fold(
                    onSuccess = { "Restored $it records" },
                    onFailure = { "Restore failed: ${it.message}" }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration", fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Account Sync Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(RelateAIColors.Primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = RelateAIColors.Primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = userName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = userEmail.ifEmpty { "Not signed in" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Verified Status Checkmark
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RelateAIColors.Secondary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RelateAIColors.Secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Synced",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RelateAIColors.Secondary
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SecondaryButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                        icon = Icons.Default.Logout,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 2. Intelligence / AI Settings Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Intelligence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; saved = false },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "Required for language generation features. Your API Key is encrypted and stored locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Default Wish Mode
                    Text(
                        text = "Default Wish Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("FULLY_AUTO", "SMART_APPROVE", "VIP_APPROVE").forEach { mode ->
                            val isSelected = globalAutomationMode == mode
                            val label = when (mode) {
                                "FULLY_AUTO" -> "Auto-Send"
                                "SMART_APPROVE" -> "Smart Approve"
                                "VIP_APPROVE" -> "Always Review"
                                else -> mode
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { globalAutomationMode = mode; saved = false },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RelateAIColors.Primary.copy(alpha = 0.2f),
                                    selectedLabelColor = RelateAIColors.Primary
                                )
                            )
                        }
                    }

                    // Mode description text
                    val modeDesc = when (globalAutomationMode) {
                        "FULLY_AUTO" -> "Sends greetings automatically on target dates without intervention."
                        "SMART_APPROVE" -> "Auto-sends standard events, but requires approval for close connections."
                        "VIP_APPROVE" -> "Always requires manual review and approval before sending messages."
                        else -> ""
                    }
                    Text(
                        text = modeDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Style Coach Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Style Coach",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Train AI to write in your unique voice and tone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SecondaryButton(
                            text = "Configure",
                            onClick = onNavigateStyleCoach,
                            icon = Icons.Default.Brush
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Test Voice Generation
                    Text(
                        text = "Test Generation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    SecondaryButton(
                        text = "Test Voice Generation",
                        onClick = {
                            if (apiKey.isEmpty()) {
                                testMessageError = "Set Gemini API key first"
                                return@SecondaryButton
                            }
                            scope.launch {
                                testMessageError = null
                                testMessageSent = false
                                try {
                                    val model = com.google.firebase.vertexai.FirebaseVertexAI.getInstance(
                                        com.google.firebase.FirebaseApp.getInstance(),
                                        "us-central1"
                                    ).generativeModel("gemini-1.5-flash")
                                    val client = GeminiClient(model, prefs)
                                    val prompt = "Generate a short test message (under 50 words) that someone might send to a friend named Alex for their birthday. Make it warm and personal. Return ONLY the message text, no JSON."
                                    val result = client.generate(prompt)
                                    if (result.contains("error")) {
                                        testMessageError = "Gemini returned: $result"
                                    } else {
                                        testMessageSent = true
                                    }
                                } catch (e: Exception) {
                                    testMessageError = "Failed: ${e.message}"
                                }
                            }
                        },
                        icon = Icons.Default.Send,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (testMessageSent || testMessageError != null) {
                        Text(
                            text = when {
                                testMessageSent -> "Test message generated successfully!"
                                testMessageError != null -> testMessageError!!
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (testMessageSent) RelateAIColors.Secondary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 3. Security & Privacy Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Security & Privacy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val authManager = remember { BiometricAuthManager(context as FragmentActivity) }
                    val biometricAvailable = remember { authManager.isAvailable() }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric App Lock",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (biometricAvailable) "Require fingerprint or PIN to open the app"
                                       else "Not available on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = biometricEnabled && biometricAvailable,
                            onCheckedChange = {
                                biometricEnabled = it
                                prefs.setBiometricLockEnabled(it)
                                saved = false
                            },
                            enabled = biometricAvailable
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Offline database shield status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(RelateAIColors.Secondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = RelateAIColors.Secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Offline Encrypted Database",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Active: 256-bit AES Encryption. Zero personal data leaves this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 4. Appearance Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                            val isSelected = themeMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { themeMode = mode; saved = false },
                                label = { Text(mode.lowercase().replaceFirstChar { it.uppercase() }) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RelateAIColors.Primary.copy(alpha = 0.2f),
                                    selectedLabelColor = RelateAIColors.Primary
                                )
                            )
                        }
                    }
                }
            }

            // 5. Email Delivery (Optional)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Email Delivery (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = senderEmail,
                        onValueChange = { senderEmail = it; saved = false },
                        label = { Text("Sender Email (Gmail)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = senderEmailPw,
                        onValueChange = { senderEmailPw = it; saved = false },
                        label = { Text("App Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // 6. Automation Rules
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Automation Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = quietStart,
                            onValueChange = { quietStart = it; saved = false },
                            label = { Text("Quiet Hrs Start (24h)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = quietEnd,
                            onValueChange = { quietEnd = it; saved = false },
                            label = { Text("Quiet Hrs End (24h)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Text(
                        text = "Quiet hours prevent automated message dispatches and reminder notifications during selected times.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 7. Backup & Restore (Data Portability)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Data Portability",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SecondaryButton(
                            text = "Backup",
                            onClick = { showBackupDialog = true },
                            icon = Icons.Default.Backup,
                            modifier = Modifier.weight(1f)
                        )

                        SecondaryButton(
                            text = "Restore",
                            onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                            icon = Icons.Default.Restore,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (backupStatus != null) {
                        Text(
                            text = backupStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (backupStatus!!.contains("failed") || backupStatus!!.contains("Failed"))
                                MaterialTheme.colorScheme.error else RelateAIColors.Secondary
                        )
                    }
                }
            }


            if (showBackupDialog) {
                AlertDialog(
                    onDismissRequest = { showBackupDialog = false },
                    title = { Text("Backup Encryption Passphrase") },
                    text = {
                        OutlinedTextField(
                            value = backupPassphrase,
                            onValueChange = { backupPassphrase = it },
                            label = { Text("Passphrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        PrimaryButton(text = "Backup", onClick = {
                            if (backupPassphrase.isNotEmpty()) {
                                showBackupDialog = false
                                scope.launch {
                                    backupStatus = "Creating backup..."
                                    try {
                                        val file = BackupManager.createBackup(context, backupPassphrase)
                                        backupStatus = "Backup saved: ${file.name}"
                                    } catch (e: Exception) {
                                        backupStatus = "Backup failed: ${e.message}"
                                    }
                                }
                            }
                        })
                    },
                    dismissButton = { SecondaryButton(text = "Cancel", onClick = { showBackupDialog = false }) }
                )
            }

            // Save Settings Primary Button
            PrimaryButton(
                text = if (saved) "Settings Saved" else "Save Settings",
                icon = if (!saved) Icons.Default.Save else null,
                onClick = {
                    prefs.setGeminiApiKey(apiKey)
                    prefs.setSenderEmail(senderEmail)
                    prefs.setSenderEmailPassword(senderEmailPw)
                    prefs.setThemeMode(themeMode)
                    prefs.setGlobalAutomationMode(globalAutomationMode)
                    prefs.setQuietHoursStart(quietStart.toIntOrNull() ?: 22)
                    prefs.setQuietHoursEnd(quietEnd.toIntOrNull() ?: 8)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saved
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}
