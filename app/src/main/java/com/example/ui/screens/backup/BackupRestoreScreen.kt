package com.example.ui.screens.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.*
import com.example.ui.viewmodel.BackupRestoreViewModel
import com.example.ui.viewmodel.BackupRestoreUiState
import com.example.ui.viewmodel.PasswordStrength

object BackupRestoreTestTags {
    const val SCREEN = "backup_restore_screen"
    const val PASSPHRASE_FIELD = "backup_restore_passphrase_field"
    const val VISIBILITY_TOGGLE = "backup_restore_visibility_toggle"
    const val STRENGTH_INDICATOR = "backup_restore_strength_indicator"
    const val EXPORT_ACTION = "backup_restore_export_action"
    const val IMPORT_ACTION = "backup_restore_import_action"
    const val EXPORT_PROGRESS = "backup_restore_export_progress"
    const val IMPORT_PROGRESS = "backup_restore_import_progress"
    const val STATUS_CARD = "backup_restore_status_card"
    const val DISMISS_STATUS = "backup_restore_dismiss_status"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(uri)
        }
    }

    BackupRestoreContent(
        uiState = uiState,
        passwordVisible = passwordVisible,
        onPassphraseChange = viewModel::updatePassphrase,
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        onExportRequested = {
            val defaultFilename = "relateai_backup.enc"
            exportLauncher.launch(defaultFilename)
        },
        onImportRequested = {
            importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        },
        onClearStatus = viewModel::clearStatus,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreContent(
    uiState: BackupRestoreUiState,
    passwordVisible: Boolean,
    onPassphraseChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onClearStatus: () -> Unit,
    onBack: () -> Unit,
) {
    val isBusy = uiState.isExporting || uiState.isImporting
    val canExport = uiState.passphrase.isNotEmpty() &&
        uiState.passwordStrength != PasswordStrength.WEAK &&
        !isBusy
    val canImport = uiState.passphrase.isNotEmpty() && !isBusy

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(RelateDarkBackground)
                .testTag(BackupRestoreTestTags.SCREEN)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Security Warning Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = stringResource(R.string.backup_security_warning_cd),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.backup_security_note_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.backup_security_note_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.backup_encryption_key_section))

            RelateGlassCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.backup_passphrase_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = uiState.passphrase,
                        onValueChange = onPassphraseChange,
                        label = { Text(stringResource(R.string.backup_passphrase_label)) },
                        placeholder = { Text(stringResource(R.string.backup_passphrase_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(BackupRestoreTestTags.PASSPHRASE_FIELD),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(
                                onClick = onTogglePasswordVisibility,
                                modifier = Modifier.testTag(BackupRestoreTestTags.VISIBILITY_TOGGLE),
                            ) {
                                Icon(
                                    image,
                                    contentDescription = stringResource(R.string.backup_toggle_password_visibility)
                                )
                            }
                        }
                    )

                    // Password Strength Indicator
                    if (uiState.passphrase.isNotEmpty()) {
                        val color = when (uiState.passwordStrength) {
                            PasswordStrength.WEAK -> Color(0xFFEF4444)
                            PasswordStrength.FAIR -> Color(0xFFF59E0B)
                            PasswordStrength.STRONG -> Color(0xFF3B82F6)
                            PasswordStrength.VERY_STRONG -> Color(0xFF10B981)
                        }
                        val progress = when (uiState.passwordStrength) {
                            PasswordStrength.WEAK -> 0.25f
                            PasswordStrength.FAIR -> 0.5f
                            PasswordStrength.STRONG -> 0.75f
                            PasswordStrength.VERY_STRONG -> 1.0f
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.backup_password_strength_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(uiState.passwordStrength.labelRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(BackupRestoreTestTags.STRENGTH_INDICATOR),
                                color = color,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.backup_actions_section))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth < 520.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BackupActionCard(
                            modifier = Modifier.fillMaxWidth(),
                            testTag = BackupRestoreTestTags.EXPORT_ACTION,
                            progressTag = BackupRestoreTestTags.EXPORT_PROGRESS,
                            title = stringResource(R.string.backup_export_title),
                            subtitle = stringResource(R.string.backup_export_subtitle),
                            contentDescription = stringResource(R.string.backup_export_cd),
                            isLoading = uiState.isExporting,
                            enabled = canExport,
                            icon = Icons.Filled.Backup,
                            iconEnabled = uiState.passphrase.isNotEmpty() && uiState.passwordStrength != PasswordStrength.WEAK,
                            onClick = onExportRequested,
                        )
                        BackupActionCard(
                            modifier = Modifier.fillMaxWidth(),
                            testTag = BackupRestoreTestTags.IMPORT_ACTION,
                            progressTag = BackupRestoreTestTags.IMPORT_PROGRESS,
                            title = stringResource(R.string.backup_import_title),
                            subtitle = stringResource(R.string.backup_import_subtitle),
                            contentDescription = stringResource(R.string.backup_restore_cd),
                            isLoading = uiState.isImporting,
                            enabled = canImport,
                            icon = Icons.Filled.Restore,
                            iconEnabled = uiState.passphrase.isNotEmpty(),
                            onClick = onImportRequested,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BackupActionCard(
                            modifier = Modifier.weight(1f),
                            testTag = BackupRestoreTestTags.EXPORT_ACTION,
                            progressTag = BackupRestoreTestTags.EXPORT_PROGRESS,
                            title = stringResource(R.string.backup_export_title),
                            subtitle = stringResource(R.string.backup_export_subtitle),
                            contentDescription = stringResource(R.string.backup_export_cd),
                            isLoading = uiState.isExporting,
                            enabled = canExport,
                            icon = Icons.Filled.Backup,
                            iconEnabled = uiState.passphrase.isNotEmpty() && uiState.passwordStrength != PasswordStrength.WEAK,
                            onClick = onExportRequested,
                        )
                        BackupActionCard(
                            modifier = Modifier.weight(1f),
                            testTag = BackupRestoreTestTags.IMPORT_ACTION,
                            progressTag = BackupRestoreTestTags.IMPORT_PROGRESS,
                            title = stringResource(R.string.backup_import_title),
                            subtitle = stringResource(R.string.backup_import_subtitle),
                            contentDescription = stringResource(R.string.backup_restore_cd),
                            isLoading = uiState.isImporting,
                            enabled = canImport,
                            icon = Icons.Filled.Restore,
                            iconEnabled = uiState.passphrase.isNotEmpty(),
                            onClick = onImportRequested,
                        )
                    }
                }
            }

            // Status Card
            if (uiState.exportSuccessFileName != null || uiState.importSuccessCount != null || uiState.errorMessage != null) {
                RelateGlassCard {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .testTag(BackupRestoreTestTags.STATUS_CARD),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when {
                            uiState.exportSuccessFileName != null -> {
                                Text(
                                    stringResource(R.string.backup_export_success_title),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(
                                        R.string.backup_export_success_details,
                                        uiState.exportSuccessFileName.orEmpty(),
                                        uiState.exportSuccessSizeBytes,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            uiState.importSuccessCount != null -> {
                                Text(
                                    stringResource(R.string.backup_import_success_title),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(
                                        R.string.backup_import_success_details,
                                        uiState.importSuccessCount ?: 0,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            uiState.errorMessage != null -> {
                                Text(
                                    stringResource(R.string.backup_action_failed_title),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    uiState.errorMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onClearStatus,
                            colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag(BackupRestoreTestTags.DISMISS_STATUS)
                        ) {
                            Text(stringResource(R.string.sync_error_dismiss), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BackupActionCard(
    modifier: Modifier,
    testTag: String,
    progressTag: String,
    title: String,
    subtitle: String,
    contentDescription: String,
    isLoading: Boolean,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconEnabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.testTag(testTag),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = RelateCard),
        enabled = enabled,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 148.dp)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(36.dp)
                        .testTag(progressTag),
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = if (iconEnabled) RelatePrimary else RelateOnSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private val PasswordStrength.labelRes: Int
    get() = when (this) {
        PasswordStrength.WEAK -> R.string.backup_password_strength_weak
        PasswordStrength.FAIR -> R.string.backup_password_strength_fair
        PasswordStrength.STRONG -> R.string.backup_password_strength_strong
        PasswordStrength.VERY_STRONG -> R.string.backup_password_strength_very_strong
    }
