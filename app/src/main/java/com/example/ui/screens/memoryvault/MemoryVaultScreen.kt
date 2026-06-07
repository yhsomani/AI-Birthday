package com.example.ui.screens.memoryvault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterChip
import com.example.ui.components.RelateGlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*
import com.example.ui.viewmodel.MemoryVaultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryVaultScreen(
    contactId: String,
    onBack: () -> Unit,
    viewModel: MemoryVaultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var newNoteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("GENERAL") }
    val categories = listOf("GENERAL", "PREFERENCE", "EVENT", "GIFT", "MILESTONE")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory Vault: ${uiState.contact?.name ?: ""}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(RelateDarkBackground),
                contentAlignment = Alignment.Center
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Add Note Card
                item {
                    RelateGlassCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Add new memory or fact",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = newNoteText,
                                onValueChange = { newNoteText = it },
                                placeholder = { Text("e.g. Likes mango lassi; met at college reunion") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )

                            // Category Selector Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categories.forEach { cat ->
                                    val isSel = selectedCategory == cat
                                    Box(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        FilterChip(
                                            label = cat.take(4),
                                            isSelected = isSel,
                                            onClick = { selectedCategory = cat },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    if (newNoteText.isNotBlank()) {
                                        viewModel.addNote(newNoteText, selectedCategory)
                                        newNoteText = ""
                                    }
                                },
                                enabled = newNoteText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary)
                            ) {
                                Text("Add to Vault")
                            }
                        }
                    }
                }

                // Error Card
                if (uiState.error != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = uiState.error ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Notes Header
                item {
                    SectionHeader(title = "Relationship Journal")
                }

                if (uiState.notes.isEmpty()) {
                    item {
                        EmptyState(
                            message = "No memories recorded yet. Add one above to enrich the AI writer!",
                            modifier = Modifier.fillMaxWidth().height(150.dp)
                        )
                    }
                } else {
                    items(uiState.notes) { note ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (note.isPinned) RelateSurfaceVariant.copy(alpha = 0.5f) else RelateCard
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(note.category) }
                                    )
                                    Row {
                                        IconButton(onClick = { viewModel.togglePin(note) }) {
                                            Icon(
                                                Icons.Filled.PushPin,
                                                contentDescription = "Pin note",
                                                tint = if (note.isPinned) RelatePrimary else RelateOnSurfaceVariant.copy(alpha = 0.4f)
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteNote(note) }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete note",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = note.noteText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = dateFormat.format(Date(note.dateMs)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RelateOnSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
