package com.example.ui.screens.memoryvault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.RelateElevation
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.ui.viewmodel.MemoryVaultUiState
import com.example.ui.viewmodel.MemoryVaultViewModel
import java.text.DateFormat
import java.util.Date

internal object MemoryVaultTestTags {
    const val LOADING = "memory_vault_loading"
    const val NOTE_FIELD = "memory_vault_note_field"
    const val SEARCH_FIELD = "memory_vault_search_field"
    const val SEARCH_CLEAR = "memory_vault_search_clear"
    const val PROMPT_PREFIX = "memory_vault_prompt_"
    const val CATEGORY_PREFIX = "memory_vault_category_"
    const val ADD_BUTTON = "memory_vault_add_button"
    const val ERROR_CARD = "memory_vault_error_card"
    const val EMPTY_STATE = "memory_vault_empty_state"
    const val SEARCH_EMPTY_STATE = "memory_vault_search_empty_state"
    const val JOURNAL_HEADER = "memory_vault_journal_header"
    const val NOTE_CARD_PREFIX = "memory_vault_note_"
    const val EDIT_BUTTON_PREFIX = "memory_vault_edit_"
    const val EDIT_FIELD_PREFIX = "memory_vault_edit_field_"
    const val EDIT_CATEGORY_PREFIX = "memory_vault_edit_category_"
    const val EDIT_SAVE_PREFIX = "memory_vault_edit_save_"
    const val EDIT_CANCEL_PREFIX = "memory_vault_edit_cancel_"
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
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var editNoteText by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf(MemoryVaultViewModel.CATEGORY_GENERAL) }

    MemoryVaultContent(
        uiState = uiState,
        newNoteText = newNoteText,
        selectedCategory = selectedCategory,
        editingNoteId = editingNoteId,
        editNoteText = editNoteText,
        editCategory = editCategory,
        onNoteChange = { nextText ->
            if (nextText.length <= MemoryVaultViewModel.MAX_NOTE_LENGTH) {
                newNoteText = nextText
            }
        },
        onPromptSelected = { promptText, category ->
            newNoteText = promptText
            selectedCategory = category
        },
        onCategoryChange = { selectedCategory = it },
        onAdd = {
            viewModel.addNote(newNoteText, selectedCategory)
            newNoteText = ""
        },
        onBack = onBack,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onEditStart = { note ->
            editingNoteId = note.id.value
            editNoteText = note.noteText
            editCategory = note.category.takeIf { it in MemoryVaultViewModel.ALLOWED_CATEGORIES }
                ?: MemoryVaultViewModel.CATEGORY_GENERAL
        },
        onEditTextChange = { nextText ->
            if (nextText.length <= MemoryVaultViewModel.MAX_NOTE_LENGTH) {
                editNoteText = nextText
            }
        },
        onEditCategoryChange = { editCategory = it },
        onEditCancel = {
            editingNoteId = null
            editNoteText = ""
            editCategory = MemoryVaultViewModel.CATEGORY_GENERAL
        },
        onEditSave = { note ->
            viewModel.updateNote(note, editNoteText, editCategory)
            editingNoteId = null
            editNoteText = ""
            editCategory = MemoryVaultViewModel.CATEGORY_GENERAL
        },
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
    editingNoteId: String?,
    editNoteText: String,
    editCategory: String,
    onNoteChange: (String) -> Unit,
    onPromptSelected: (String, String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onEditStart: (MemoryNoteRecord) -> Unit,
    onEditTextChange: (String) -> Unit,
    onEditCategoryChange: (String) -> Unit,
    onEditCancel: () -> Unit,
    onEditSave: (MemoryNoteRecord) -> Unit,
    onTogglePin: (MemoryNoteRecord) -> Unit,
    onDelete: (MemoryNoteRecord) -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.contact?.displayName?.let {
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
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(RelateElevation.appBar),
                ),
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(MemoryVaultTestTags.LOADING)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(RelateSpacing.screenHorizontal),
                contentPadding = PaddingValues(bottom = RelateSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.lg),
            ) {
                item {
                    AddMemoryCard(
                        newNoteText = newNoteText,
                        selectedCategory = selectedCategory,
                        onNoteChange = onNoteChange,
                        onPromptSelected = onPromptSelected,
                        onCategoryChange = onCategoryChange,
                        onAdd = onAdd,
                    )
                }

                uiState.errorMessageRes?.let { errorRes ->
                    item {
                        MemoryVaultErrorCard(message = stringResource(errorRes))
                    }
                }

                item {
                    SectionHeader(
                        title = stringResource(R.string.memory_vault_journal_title),
                        modifier = Modifier.testTag(MemoryVaultTestTags.JOURNAL_HEADER),
                    )
                }

                if (uiState.notes.isEmpty()) {
                    item {
                        EmptyState(
                            message = stringResource(R.string.memory_vault_empty_message),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(MemoryVaultTestTags.EMPTY_STATE)
                                .height(RelateSize.actionCardMinHeight),
                        )
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            label = { Text(stringResource(R.string.memory_vault_search_label)) },
                            placeholder = { Text(stringResource(R.string.memory_vault_search_placeholder)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchQueryChange("") },
                                        modifier = Modifier.testTag(MemoryVaultTestTags.SEARCH_CLEAR),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.clear_search),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(MemoryVaultTestTags.SEARCH_FIELD),
                            singleLine = true,
                        )
                    }

                    if (uiState.visibleNotes.isEmpty()) {
                        item {
                            EmptyState(
                                message = stringResource(R.string.memory_vault_no_search_results),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(MemoryVaultTestTags.SEARCH_EMPTY_STATE)
                                    .height(RelateSize.actionCardMinHeight),
                            )
                        }
                    }

                    items(uiState.visibleNotes, key = { it.id.value }) { note ->
                        if (editingNoteId == note.id.value) {
                            EditMemoryNoteCard(
                                note = note,
                                editNoteText = editNoteText,
                                editCategory = editCategory,
                                onEditTextChange = onEditTextChange,
                                onEditCategoryChange = onEditCategoryChange,
                                onSave = { onEditSave(note) },
                                onCancel = onEditCancel,
                                modifier = Modifier.testTag(MemoryVaultTestTags.NOTE_CARD_PREFIX + note.id.value),
                            )
                        } else {
                            MemoryNoteCard(
                                note = note,
                                date = dateFormat.format(Date(note.dateMs)),
                                onEdit = { onEditStart(note) },
                                onTogglePin = { onTogglePin(note) },
                                onDelete = { onDelete(note) },
                                modifier = Modifier.testTag(MemoryVaultTestTags.NOTE_CARD_PREFIX + note.id.value),
                            )
                        }
                    }
                }
            }
        }
    }
}
