package com.example.ui.screens.memoryvault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.model.memory.MemoryNoteRecord

@Composable
internal fun MemoryVaultErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MemoryVaultTestTags.ERROR_CARD),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(RelateSpacing.cardContent),
        )
    }
}

@Composable
internal fun MemoryNoteCard(
    note: MemoryNoteRecord,
    date: String,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isPinned) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.outline)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(memoryCategoryLabel(note.category)) },
                )
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag(MemoryVaultTestTags.EDIT_BUTTON_PREFIX + note.id.value),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.memory_vault_edit_note),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = RelateAlpha.subtle),
                        )
                    }
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.testTag(MemoryVaultTestTags.PIN_BUTTON_PREFIX + note.id.value),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = if (note.isPinned) {
                                stringResource(R.string.memory_vault_unpin_note)
                            } else {
                                stringResource(R.string.memory_vault_pin_note)
                            },
                            tint = if (note.isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = RelateAlpha.disabled)
                            },
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag(MemoryVaultTestTags.DELETE_BUTTON_PREFIX + note.id.value),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.memory_vault_delete_note),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = RelateAlpha.subtle),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Text(
                text = note.noteText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(RelateSpacing.md))
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
