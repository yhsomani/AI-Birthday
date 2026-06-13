package com.example.ui.screens.memoryvault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.RelateCard
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.MemoryVaultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class MemoryCategoryOption(
    val value: String,
    val shortLabelRes: Int,
    val labelRes: Int,
)

private val memoryCategoryOptions = listOf(
    MemoryCategoryOption("GENERAL", R.string.memory_category_general_short, R.string.memory_category_general),
    MemoryCategoryOption("PREFERENCE", R.string.memory_category_preference_short, R.string.memory_category_preference),
    MemoryCategoryOption("EVENT", R.string.memory_category_event_short, R.string.memory_category_event),
    MemoryCategoryOption("GIFT", R.string.memory_category_gift_short, R.string.memory_category_gift),
    MemoryCategoryOption("MILESTONE", R.string.memory_category_milestone_short, R.string.memory_category_milestone),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryVaultScreen(
    contactId: String,
    onBack: () -> Unit,
    viewModel: MemoryVaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var newNoteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MemoryVaultViewModel.CATEGORY_GENERAL) }
    var noteToDelete by remember { mutableStateOf<MemoryNoteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.contact?.name?.let {
                            stringResource(R.string.memory_vault_title_with_contact, it)
                        } ?: stringResource(R.string.memory_vault_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                ),
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RelateDarkBackground),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = RelatePrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RelateDarkBackground)
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    AddMemoryCard(
                        newNoteText = newNoteText,
                        selectedCategory = selectedCategory,
                        onNoteChange = { nextText ->
                            if (nextText.length <= MemoryVaultViewModel.MAX_NOTE_LENGTH) {
                                newNoteText = nextText
                            }
                        },
                        onCategoryChange = { selectedCategory = it },
                        onAdd = {
                            viewModel.addNote(newNoteText, selectedCategory)
                            newNoteText = ""
                        },
                    )
                }

                uiState.errorMessageRes?.let { errorRes ->
                    item {
                        ErrorCard(message = stringResource(errorRes))
                    }
                }

                item {
                    SectionHeader(title = stringResource(R.string.memory_vault_journal_title))
                }

                if (uiState.notes.isEmpty()) {
                    item {
                        EmptyState(
                            message = stringResource(R.string.memory_vault_empty_message),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        )
                    }
                } else {
                    items(uiState.notes, key = { it.id }) { note ->
                        MemoryNoteCard(
                            note = note,
                            date = dateFormat.format(Date(note.dateMs)),
                            onTogglePin = { viewModel.togglePin(note) },
                            onDelete = { noteToDelete = note },
                        )
                    }
                }
            }
        }
    }

    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text(stringResource(R.string.memory_vault_delete_confirm_title)) },
            text = { Text(stringResource(R.string.memory_vault_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(noteToDelete!!)
                        noteToDelete = null
                    }
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AddMemoryCard(
    newNoteText: String,
    selectedCategory: String,
    onNoteChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.memory_vault_add_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = newNoteText,
                onValueChange = onNoteChange,
                placeholder = { Text(stringResource(R.string.memory_vault_note_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                supportingText = {
                    Text(
                        text = stringResource(
                            R.string.memory_vault_note_counter,
                            newNoteText.length,
                            MemoryVaultViewModel.MAX_NOTE_LENGTH,
                        ),
                    )
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                memoryCategoryOptions.forEach { option ->
                    Box(modifier = Modifier.weight(1f)) {
                        FilterChip(
                            label = stringResource(option.shortLabelRes),
                            isSelected = selectedCategory == option.value,
                            onClick = { onCategoryChange(option.value) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Button(
                onClick = onAdd,
                enabled = newNoteText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary),
            ) {
                Text(stringResource(R.string.memory_vault_add_button))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun MemoryNoteCard(
    note: MemoryNoteEntity,
    date: String,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isPinned) {
                RelateSurfaceVariant.copy(alpha = 0.5f)
            } else {
                RelateCard
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = if (note.isPinned) {
                                stringResource(R.string.memory_vault_unpin_note)
                            } else {
                                stringResource(R.string.memory_vault_pin_note)
                            },
                            tint = if (note.isPinned) RelatePrimary else RelateOnSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.memory_vault_delete_note),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = note.noteText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun memoryCategoryLabel(category: String): String {
    val option = memoryCategoryOptions.firstOrNull { it.value == category }
    return option?.let { stringResource(it.labelRes) } ?: category
}
