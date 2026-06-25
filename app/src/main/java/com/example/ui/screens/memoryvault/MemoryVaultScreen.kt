package com.example.ui.screens.memoryvault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.example.ui.viewmodel.MemoryVaultUiState
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

internal object MemoryVaultTestTags {
    const val LOADING = "memory_vault_loading"
    const val NOTE_FIELD = "memory_vault_note_field"
    const val CATEGORY_PREFIX = "memory_vault_category_"
    const val ADD_BUTTON = "memory_vault_add_button"
    const val ERROR_CARD = "memory_vault_error_card"
    const val EMPTY_STATE = "memory_vault_empty_state"
    const val NOTE_CARD_PREFIX = "memory_vault_note_"
    const val PIN_BUTTON_PREFIX = "memory_vault_pin_"
    const val DELETE_BUTTON_PREFIX = "memory_vault_delete_"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryVaultScreen(
    contactId: String,
    onBack: () -> Unit,
    viewModel: MemoryVaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var newNoteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MemoryVaultViewModel.CATEGORY_GENERAL) }

    MemoryVaultContent(
        uiState = uiState,
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
        onBack = onBack,
        onTogglePin = viewModel::togglePin,
        onDelete = viewModel::deleteNote,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryVaultContent(
    uiState: MemoryVaultUiState,
    newNoteText: String,
    selectedCategory: String,
    onNoteChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onTogglePin: (MemoryNoteEntity) -> Unit,
    onDelete: (MemoryNoteEntity) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

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
                    .testTag(MemoryVaultTestTags.LOADING)
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
                        onNoteChange = onNoteChange,
                        onCategoryChange = onCategoryChange,
                        onAdd = onAdd,
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
                                .testTag(MemoryVaultTestTags.EMPTY_STATE)
                                .height(150.dp),
                        )
                    }
                } else {
                    items(uiState.notes, key = { it.id }) { note ->
                        MemoryNoteCard(
                            note = note,
                            date = dateFormat.format(Date(note.dateMs)),
                            onTogglePin = { onTogglePin(note) },
                            onDelete = { onDelete(note) },
                            modifier = Modifier.testTag(MemoryVaultTestTags.NOTE_CARD_PREFIX + note.id),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMemoryCard(
    newNoteText: String,
    selectedCategory: String,
    onNoteChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val noteHasOnlyWhitespace = newNoteText.isNotEmpty() && newNoteText.isBlank()

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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MemoryVaultTestTags.NOTE_FIELD),
                minLines = 2,
                maxLines = 4,
                isError = noteHasOnlyWhitespace,
                supportingText = {
                    if (noteHasOnlyWhitespace) {
                        Text(text = stringResource(R.string.memory_vault_error_blank_note))
                    } else {
                        Text(
                            text = stringResource(
                                R.string.memory_vault_note_counter,
                                newNoteText.length,
                                MemoryVaultViewModel.MAX_NOTE_LENGTH,
                            ),
                        )
                    }
                },
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                memoryCategoryOptions.forEach { option ->
                    FilterChip(
                        label = stringResource(option.shortLabelRes),
                        isSelected = selectedCategory == option.value,
                        onClick = { onCategoryChange(option.value) },
                        modifier = Modifier.testTag(MemoryVaultTestTags.CATEGORY_PREFIX + option.value),
                    )
                }
            }

            Button(
                onClick = onAdd,
                enabled = newNoteText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MemoryVaultTestTags.ADD_BUTTON),
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
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MemoryVaultTestTags.ERROR_CARD),
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.testTag(MemoryVaultTestTags.PIN_BUTTON_PREFIX + note.id),
                    ) {
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
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag(MemoryVaultTestTags.DELETE_BUTTON_PREFIX + note.id),
                    ) {
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
