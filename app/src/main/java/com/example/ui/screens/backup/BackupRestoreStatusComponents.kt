package com.example.ui.screens.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.ui.viewmodel.BackupRestoreUiState

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
