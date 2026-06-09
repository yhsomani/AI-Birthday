package com.example.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.relateTextFieldColors
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.EventsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val eventTypeOptions = listOf(
    "BIRTHDAY" to "Birthday",
    "ANNIVERSARY" to "Anniversary",
    "WORK_ANNIVERSARY" to "Work",
    "CUSTOM" to "Custom",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showManualDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveMessage, state.error) {
        val message = state.saveMessage ?: state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedback()
        }
    }

    if (showManualDialog) {
        ManualEventDialog(
            contacts = state.contacts,
            isSaving = state.isSavingManualEvent,
            onDismiss = { showManualDialog = false },
            onSave = { existingContactId, newContactName, eventType, label, month, day, year ->
                showManualDialog = false
                viewModel.saveManualEvent(
                    existingContactId = existingContactId,
                    newContactName = newContactName,
                    eventType = eventType,
                    label = label,
                    month = month,
                    day = day,
                    year = year,
                )
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground),
    ) {
        RelateScreen(
            title = "Events",
            subtitle = "Birthdays, anniversaries, and manual reminders.",
            action = {
                IconButton(
                    onClick = {
                        showManualDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add event",
                        tint = RelatePrimary,
                    )
                }
            },
        ) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (state.isLoading && state.events.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = RelatePrimary)
                    }
                } else if (state.events.isEmpty()) {
                    EmptyState(
                        message = "No events yet. Add one manually or sync your contacts.",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    EventsList(events = state.events)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun EventsList(events: List<EventEntity>) {
    val groupedEvents = events.groupBy {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = it.nextOccurrenceMs
        cal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, Locale.getDefault()) ?: "Other"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groupedEvents.entries.forEach { (month, monthEvents) ->
            item(key = month) {
                SectionHeader(title = month)
            }
            monthEvents.forEach { event ->
                item(key = event.id) {
                    EventCard(event = event)
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun ManualEventDialog(
    contacts: List<ContactEntity>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        existingContactId: String?,
        newContactName: String?,
        eventType: String,
        label: String?,
        month: Int,
        day: Int,
        year: Int?,
    ) -> Unit,
) {
    var useExistingContact by remember { mutableStateOf(contacts.isNotEmpty()) }
    var selectedContactId by remember { mutableStateOf(contacts.firstOrNull()?.id) }
    var contactMenuExpanded by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("BIRTHDAY") }
    var label by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Event", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        label = "Existing",
                        isSelected = useExistingContact,
                        onClick = {
                            useExistingContact = true
                            selectedContactId = selectedContactId ?: contacts.firstOrNull()?.id
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        label = "New",
                        isSelected = !useExistingContact,
                        onClick = { useExistingContact = false },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (useExistingContact) {
                    Box {
                        OutlinedTextField(
                            value = contacts.firstOrNull { it.id == selectedContactId }?.name
                                ?: "Choose contact",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = contacts.isNotEmpty()) {
                                    contactMenuExpanded = true
                                },
                            label = { Text("Contact") },
                            enabled = contacts.isNotEmpty(),
                            colors = relateTextFieldColors(),
                        )
                        DropdownMenu(
                            expanded = contactMenuExpanded,
                            onDismissRequest = { contactMenuExpanded = false },
                        ) {
                            contacts.forEach { contact ->
                                DropdownMenuItem(
                                    text = { Text(contact.name) },
                                    onClick = {
                                        selectedContactId = contact.id
                                        contactMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    if (contacts.isEmpty()) {
                        Text(
                            text = "No contacts yet. Switch to New to add a local contact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RelateOnSurfaceVariant,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = newContactName,
                        onValueChange = { newContactName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New contact name") },
                        singleLine = true,
                        colors = relateTextFieldColors(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        eventTypeOptions.take(2).forEach { (value, text) ->
                            FilterChip(
                                label = text,
                                isSelected = eventType == value,
                                onClick = { eventType = value },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        eventTypeOptions.drop(2).forEach { (value, text) ->
                            FilterChip(
                                label = text,
                                isSelected = eventType == value,
                                onClick = { eventType = value },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Label") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                    colors = relateTextFieldColors(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = { monthText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Month") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = relateTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { dayText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Day") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = relateTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { yearText = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Year") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = relateTextFieldColors(),
                    )
                }

                localError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val month = monthText.toIntOrNull()
                    val day = dayText.toIntOrNull()
                    val year = yearText.toIntOrNull()
                    val existingId = selectedContactId.takeIf { useExistingContact }
                    val newName = newContactName.takeIf { !useExistingContact }
                    if (month == null || day == null) {
                        localError = "Enter month and day."
                    } else if (useExistingContact && existingId == null) {
                        localError = "Choose a contact."
                    } else if (!useExistingContact && newName.isNullOrBlank()) {
                        localError = "Enter a contact name."
                    } else {
                        onSave(existingId, newName, eventType, label, month, day, year)
                    }
                },
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun eventTypeIcon(type: String): ImageVector = when (type) {
    "BIRTHDAY" -> Icons.Filled.Favorite
    "ANNIVERSARY", "WORK_ANNIVERSARY" -> Icons.Filled.Star
    else -> Icons.Filled.CalendarMonth
}

@Composable
private fun EventCard(event: EventEntity) {
    val daysUntil = event.daysUntil
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    RelateGlassCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (daysUntil <= 14) RelatePrimary.copy(alpha = 0.2f)
                        else RelateSurfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    eventTypeIcon(event.type),
                    contentDescription = null,
                    tint = if (daysUntil <= 14) RelatePrimary else RelateOnSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.label ?: event.type.replace("_", " "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${event.type.replace("_", " ")} • ${event.source} • ${dateFormat.format(Date(event.nextOccurrenceMs))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$daysUntil",
                    style = MaterialTheme.typography.headlineSmall,
                    color = RelatePrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "days",
                    style = MaterialTheme.typography.labelSmall,
                    color = RelateOnSurfaceVariant,
                )
            }
        }
    }
}
