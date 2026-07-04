package com.example.ui.screens.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.ui.viewmodel.BackupRestoreUiState
import com.example.ui.viewmodel.PasswordStrength

@Composable
internal fun BackupActionsSection(
    uiState: BackupRestoreUiState,
    canExport: Boolean,
    canImport: Boolean,
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < RelateSize.actionGridBreakpoint) {
            Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.md)) {
                BackupExportActionCard(
                    modifier = Modifier.fillMaxWidth(),
                    uiState = uiState,
                    enabled = canExport,
                    onClick = onExportRequested,
                )
                BackupImportActionCard(
                    modifier = Modifier.fillMaxWidth(),
                    uiState = uiState,
                    enabled = canImport,
                    onClick = onImportRequested,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.lg),
            ) {
                BackupExportActionCard(
                    modifier = Modifier.weight(1f),
                    uiState = uiState,
                    enabled = canExport,
                    onClick = onExportRequested,
                )
                BackupImportActionCard(
                    modifier = Modifier.weight(1f),
                    uiState = uiState,
                    enabled = canImport,
                    onClick = onImportRequested,
                )
            }
        }
    }
}

@Composable
private fun BackupExportActionCard(
    uiState: BackupRestoreUiState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackupActionCard(
        modifier = modifier,
        testTag = BackupRestoreTestTags.EXPORT_ACTION,
        progressTag = BackupRestoreTestTags.EXPORT_PROGRESS,
        title = stringResource(R.string.backup_export_title),
        subtitle = stringResource(R.string.backup_export_subtitle),
        contentDescription = stringResource(R.string.backup_export_cd),
        isLoading = uiState.isExporting,
        enabled = enabled,
        icon = Icons.Filled.Backup,
        iconEnabled = uiState.passphrase.isNotEmpty() && uiState.passwordStrength != PasswordStrength.WEAK,
        onClick = onClick,
    )
}

@Composable
private fun BackupImportActionCard(
    uiState: BackupRestoreUiState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackupActionCard(
        modifier = modifier,
        testTag = BackupRestoreTestTags.IMPORT_ACTION,
        progressTag = BackupRestoreTestTags.IMPORT_PROGRESS,
        title = stringResource(R.string.backup_import_title),
        subtitle = stringResource(R.string.backup_import_subtitle),
        contentDescription = stringResource(R.string.backup_restore_cd),
        isLoading = uiState.isImporting,
        enabled = enabled,
        icon = Icons.Filled.Restore,
        iconEnabled = uiState.passphrase.isNotEmpty(),
        onClick = onClick,
    )
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
    icon: ImageVector,
    iconEnabled: Boolean,
    onClick: () -> Unit,
) {
    val semanticColors = MaterialTheme.relateSemanticColors

    Card(
        modifier = modifier.testTag(testTag),
        onClick = onClick,
        shape = RoundedCornerShape(RelateRadius.card),
        colors = CardDefaults.cardColors(containerColor = semanticColors.cardContainer),
        enabled = enabled,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RelateSize.actionCardMinHeight)
                .padding(RelateSpacing.cardContent),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(RelateSize.compactButtonHeight)
                        .testTag(progressTag),
                    strokeWidth = RelateSize.progressStroke,
                )
            } else {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = if (iconEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = RelateAlpha.disabled)
                    },
                    modifier = Modifier.size(RelateSize.compactButtonHeight),
                )
            }
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun BackupStatusCard(
    uiState: BackupRestoreUiState,
    isBusy: Boolean,
    onConfirmImport: () -> Unit,
    onClearStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.hasStatus) return

    val semanticColors = MaterialTheme.relateSemanticColors
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(RelateSpacing.cardContent)
                .testTag(BackupRestoreTestTags.STATUS_CARD),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            when {
                uiState.exportSuccessFileName != null -> {
                    Text(
                        stringResource(R.string.backup_export_success_title),
                        fontWeight = FontWeight.Bold,
                        color = semanticColors.success,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            R.string.backup_export_success_details,
                            uiState.exportSuccessFileName.orEmpty(),
                            uiState.exportSuccessSizeBytes,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.importSuccessCount != null -> {
                    Text(
                        stringResource(R.string.backup_import_success_title),
                        fontWeight = FontWeight.Bold,
                        color = semanticColors.success,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            R.string.backup_import_success_details,
                            uiState.importSuccessCount ?: 0,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.importPreview != null -> {
                    val preview = uiState.importPreview
                    Text(
                        stringResource(R.string.backup_import_preview_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            R.string.backup_import_preview_details,
                            preview.backupVersion,
                            preview.appVersion,
                            preview.totalRecords,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.backup_import_replace_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onConfirmImport,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(RelateRadius.control),
                        modifier = Modifier
                            .heightIn(min = RelateSize.compactButtonHeight)
                            .align(Alignment.End)
                            .testTag(BackupRestoreTestTags.CONFIRM_IMPORT),
                    ) {
                        Text(stringResource(R.string.backup_import_confirm))
                    }
                }
                uiState.errorMessage != null -> {
                    Text(
                        stringResource(R.string.backup_action_failed_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        uiState.errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onClearStatus,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(RelateRadius.control),
                modifier = Modifier
                    .heightIn(min = RelateSize.compactButtonHeight)
                    .align(Alignment.End)
                    .testTag(BackupRestoreTestTags.DISMISS_STATUS),
            ) {
                Text(
                    stringResource(R.string.sync_error_dismiss),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private val BackupRestoreUiState.hasStatus: Boolean
    get() = exportSuccessFileName != null ||
        importPreview != null ||
        importSuccessCount != null ||
        errorMessage != null
