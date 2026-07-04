package com.example.ui.screens.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.R
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.RelateElevation
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.BackupRestoreUiState
import com.example.ui.viewmodel.BackupRestoreViewModel
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
    const val CONFIRM_IMPORT = "backup_restore_confirm_import"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
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
            exportLauncher.launch("relateai_backup.enc")
        },
        onImportRequested = {
            importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        },
        onConfirmImport = viewModel::confirmImportBackup,
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
    onConfirmImport: () -> Unit,
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
                title = {
                    Text(
                        stringResource(R.string.backup_restore_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
                        RelateElevation.appBar,
                    ),
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .testTag(BackupRestoreTestTags.SCREEN)
                .verticalScroll(rememberScrollState())
                .padding(RelateSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.xl),
        ) {
            BackupSecurityWarningCard()

            SectionHeader(title = stringResource(R.string.backup_encryption_key_section))

            BackupPassphraseCard(
                passphrase = uiState.passphrase,
                passwordStrength = uiState.passwordStrength,
                passwordVisible = passwordVisible,
                onPassphraseChange = onPassphraseChange,
                onTogglePasswordVisibility = onTogglePasswordVisibility,
            )

            SectionHeader(title = stringResource(R.string.backup_actions_section))

            BackupActionsSection(
                uiState = uiState,
                canExport = canExport,
                canImport = canImport,
                onExportRequested = onExportRequested,
                onImportRequested = onImportRequested,
            )

            BackupStatusCard(
                uiState = uiState,
                isBusy = isBusy,
                onConfirmImport = onConfirmImport,
                onClearStatus = onClearStatus,
            )

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
        }
    }
}
