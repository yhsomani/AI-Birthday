package com.example.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
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
import com.example.core.ui.theme.RelateWarning
import com.example.domain.event.EventResolutionPolicy
import com.example.domain.model.contact.ContactPickerItem
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.OccasionType
import com.example.ui.viewmodel.EventHorizonFilter
import com.example.ui.viewmodel.EventResolutionAction
import com.example.ui.viewmodel.EventTrustConflictState
import com.example.ui.viewmodel.EventTrustState
import com.example.ui.viewmodel.EventVerificationState
import com.example.ui.viewmodel.EventTypeFilter
import com.example.ui.viewmodel.EventsViewModel
import com.example.ui.viewmodel.ManualEventDuplicateWarning
import com.example.ui.viewmodel.ManualEventWarningKind
import com.example.ui.viewmodel.buildEventTrustStates
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val eventTypeOptions = listOf(
    OccasionType.BIRTHDAY.raw,
    OccasionType.ANNIVERSARY.raw,
    OccasionType.WORK_ANNIVERSARY.raw,
    OccasionType.CUSTOM.raw,
)

private val eventTypeFilters = listOf(
    EventTypeFilter.ALL,
    EventTypeFilter.BIRTHDAY,
    EventTypeFilter.ANNIVERSARY,
    EventTypeFilter.WORK,
    EventTypeFilter.CUSTOM,
)

private val eventHorizonFilters = listOf(
    EventHorizonFilter.ALL,
    EventHorizonFilter.NEXT_7_DAYS,
    EventHorizonFilter.NEXT_30_DAYS,
    EventHorizonFilter.NEXT_90_DAYS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

    LaunchedEffect(state.saveMessage) {
        if (state.saveMessage != null) {
            showManualDialog = false
        }
    }

    if (showManualDialog) {
        ManualEventDialog(
            contacts = state.contacts,
            isSaving = state.isSavingManualEvent,
            duplicateWarning = state.duplicateWarning,
            onDismiss = {
                viewModel.clearManualEventDuplicateWarning()
                showManualDialog = false
            },
            onInputChanged = viewModel::clearManualEventDuplicateWarning,
            onSave = { existingContactId, newContactName, eventType, label, month, day, year, allowDuplicate ->
                viewModel.saveManualEvent(
                    existingContactId = existingContactId,
                    newContactName = newContactName,
                    eventType = eventType,
                    label = label,
                    month = month,
                    day = day,
                    year = year,
                    allowDuplicate = allowDuplicate,
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
            title = stringResource(R.string.nav_events),
            subtitle = stringResource(R.string.events_subtitle),
            action = {
                IconButton(
                    onClick = {
                        showManualDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.events_add_event),
                        tint = RelatePrimary,
                    )
                }
            },
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search)) },
                placeholder = { Text(stringResource(R.string.events_search_placeholder)) },
                singleLine = true,
                colors = relateTextFieldColors(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                eventTypeFilters.forEach { filter ->
                    FilterChip(
                        label = filter.label(),
                        isSelected = state.selectedTypeFilter == filter,
                        onClick = { viewModel.selectTypeFilter(filter) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                eventHorizonFilters.forEach { filter ->
                    FilterChip(
                        label = filter.label(),
                        isSelected = state.selectedHorizonFilter == filter,
                        onClick = { viewModel.selectHorizonFilter(filter) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
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
                        message = stringResource(R.string.events_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    EventsList(
                        events = state.events,
                        eventTrust = state.eventTrust,
                        resolvingEventId = state.resolvingEventId,
                        onMergeEvent = {
                            viewModel.resolveEventConflict(it, EventResolutionAction.MERGE_KEEP_SELECTED)
                        },
                        onKeepSeparateEvent = {
                            viewModel.resolveEventConflict(it, EventResolutionAction.KEEP_SEPARATE)
                        },
                    )
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
internal fun EventsList(
    events: List<EventListItem>,
    eventTrust: Map<String, EventTrustState> = buildEventTrustStates(events),
    resolvingEventId: String? = null,
    onMergeEvent: (String) -> Unit = {},
    onKeepSeparateEvent: (String) -> Unit = {},
) {
    val resolvedEventTrust = if (events.all { eventTrust.containsKey(it.id.value) }) {
        eventTrust
    } else {
        buildEventTrustStates(events)
    }
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
                item(key = event.id.value) {
                    EventCard(
                        event = event,
                        trustState = resolvedEventTrust.getValue(event.id.value),
                        isResolving = resolvingEventId == event.id.value,
                        onMerge = { onMergeEvent(event.id.value) },
                        onKeepSeparate = { onKeepSeparateEvent(event.id.value) },
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun ManualEventDialog(
    contacts: List<ContactPickerItem>,
    isSaving: Boolean,
    duplicateWarning: ManualEventDuplicateWarning?,
    onDismiss: () -> Unit,
    onInputChanged: () -> Unit,
    onSave: (
        existingContactId: String?,
        newContactName: String?,
        eventType: String,
        label: String?,
        month: Int,
        day: Int,
        year: Int?,
        allowDuplicate: Boolean,
    ) -> Unit,
) {
    var useExistingContact by remember { mutableStateOf(contacts.isNotEmpty()) }
    var selectedContactId by remember { mutableStateOf(contacts.firstOrNull()?.id?.value) }
    var contactMenuExpanded by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf(OccasionType.BIRTHDAY.raw) }
    var label by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val errorMonthDay = stringResource(R.string.events_error_month_day)
    val errorChooseContact = stringResource(R.string.events_error_choose_contact)
    val errorContactName = stringResource(R.string.events_error_contact_name)
    val selectedContactName = contacts.firstOrNull { it.id.value == selectedContactId }?.displayName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.events_add_event), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        label = stringResource(R.string.events_existing_contact),
                        isSelected = useExistingContact,
                        onClick = {
                            onInputChanged()
                            useExistingContact = true
                            selectedContactId = selectedContactId ?: contacts.firstOrNull()?.id?.value
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        label = stringResource(R.string.events_new_contact),
                        isSelected = !useExistingContact,
                        onClick = {
                            onInputChanged()
                            useExistingContact = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (useExistingContact) {
                    Box {
                        OutlinedTextField(
                            value = selectedContactName
                                ?: stringResource(R.string.events_choose_contact),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = contacts.isNotEmpty()) {
                                    contactMenuExpanded = true
                                },
                            label = { Text(stringResource(R.string.events_contact_label)) },
                            enabled = contacts.isNotEmpty(),
                            colors = relateTextFieldColors(),
                        )
                        DropdownMenu(
                            expanded = contactMenuExpanded,
                            onDismissRequest = { contactMenuExpanded = false },
                        ) {
                            contacts.forEach { contact ->
                                DropdownMenuItem(
                                    text = { Text(contact.displayName) },
                                    onClick = {
                                        onInputChanged()
                                        selectedContactId = contact.id.value
                                        contactMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    if (contacts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.events_no_contacts_for_manual),
                            style = MaterialTheme.typography.bodySmall,
                            color = RelateOnSurfaceVariant,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = newContactName,
                        onValueChange = {
                            onInputChanged()
                            newContactName = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.events_new_contact_name)) },
                        singleLine = true,
                        colors = relateTextFieldColors(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        eventTypeOptions.take(2).forEach { value ->
                            FilterChip(
                                label = eventTypeLabel(value),
                                isSelected = eventType == value,
                                onClick = {
                                    onInputChanged()
                                    eventType = value
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        eventTypeOptions.drop(2).forEach { value ->
                            FilterChip(
                                label = eventTypeLabel(value),
                                isSelected = eventType == value,
                                onClick = {
                                    onInputChanged()
                                    eventType = value
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        onInputChanged()
                        label = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.events_label)) },
                    placeholder = { Text(stringResource(R.string.optional)) },
                    singleLine = true,
                    colors = relateTextFieldColors(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = {
                            onInputChanged()
                            monthText = it.filter(Char::isDigit).take(2)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.events_month_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = relateTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = {
                            onInputChanged()
                            dayText = it.filter(Char::isDigit).take(2)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.events_day_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = relateTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = {
                            onInputChanged()
                            yearText = it.filter(Char::isDigit).take(4)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.events_year_label)) },
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

                duplicateWarning?.let { warning ->
                    val warningMessage = when (warning.kind) {
                        ManualEventWarningKind.DUPLICATE -> stringResource(
                            R.string.events_duplicate_message,
                            warning.contactName,
                            eventTypeLabel(warning.eventType),
                            warning.month,
                            warning.dayOfMonth,
                        )
                        ManualEventWarningKind.DATE_CONFLICT -> stringResource(
                            R.string.events_conflict_message,
                            warning.contactName,
                            eventTypeLabel(warning.eventType),
                            warning.month,
                            warning.dayOfMonth,
                            warning.requestedMonth ?: warning.month,
                            warning.requestedDayOfMonth ?: warning.dayOfMonth,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(
                                when (warning.kind) {
                                    ManualEventWarningKind.DUPLICATE -> R.string.events_duplicate_title
                                    ManualEventWarningKind.DATE_CONFLICT -> R.string.events_conflict_title
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = RelateWarning,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = warningMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = RelateOnSurfaceVariant,
                        )
                    }
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
                        localError = errorMonthDay
                    } else if (useExistingContact && existingId == null) {
                        localError = errorChooseContact
                    } else if (!useExistingContact && newName.isNullOrBlank()) {
                        localError = errorContactName
                    } else {
                        onSave(
                            existingId,
                            newName,
                            eventType,
                            label,
                            month,
                            day,
                            year,
                            duplicateWarning != null,
                        )
                    }
                },
            ) {
                Text(
                    when {
                        isSaving -> stringResource(R.string.saving)
                        duplicateWarning != null -> stringResource(R.string.events_duplicate_save_anyway)
                        else -> stringResource(R.string.save)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun EventTypeFilter.label(): String = when (this) {
    EventTypeFilter.ALL -> stringResource(R.string.filter_all)
    EventTypeFilter.BIRTHDAY -> stringResource(R.string.events_filter_birthdays)
    EventTypeFilter.ANNIVERSARY -> stringResource(R.string.events_filter_anniversaries)
    EventTypeFilter.WORK -> stringResource(R.string.events_filter_work)
    EventTypeFilter.CUSTOM -> stringResource(R.string.events_filter_custom)
}

@Composable
private fun EventHorizonFilter.label(): String = when (this) {
    EventHorizonFilter.ALL -> stringResource(R.string.filter_all)
    EventHorizonFilter.NEXT_7_DAYS -> stringResource(R.string.filter_next_7_days)
    EventHorizonFilter.NEXT_30_DAYS -> stringResource(R.string.filter_next_30_days)
    EventHorizonFilter.NEXT_90_DAYS -> stringResource(R.string.filter_next_90_days)
}

@Composable
private fun eventTypeLabel(type: String): String = when (OccasionType.fromRaw(type)) {
    OccasionType.BIRTHDAY -> stringResource(R.string.event_type_birthday)
    OccasionType.ANNIVERSARY -> stringResource(R.string.event_type_anniversary)
    OccasionType.WORK_ANNIVERSARY -> stringResource(R.string.event_type_work_anniversary)
    else -> stringResource(R.string.event_type_custom)
}

private fun eventTypeIcon(type: String): ImageVector = when (OccasionType.fromRaw(type)) {
    OccasionType.BIRTHDAY -> Icons.Filled.Favorite
    OccasionType.ANNIVERSARY,
    OccasionType.WORK_ANNIVERSARY -> Icons.Filled.Star
    else -> Icons.Filled.CalendarMonth
}

@Composable
private fun eventSourceLabel(source: String): String {
    val baseSource = EventResolutionPolicy.baseSource(source)
    return when (baseSource.trim().uppercase(Locale.US)) {
        "CONTACTS" -> stringResource(R.string.event_source_contacts)
        "MANUAL" -> stringResource(R.string.event_source_manual)
        "CALENDAR" -> stringResource(R.string.event_source_calendar)
        "AI_INFERRED" -> stringResource(R.string.event_source_ai_inferred)
        "MERGED" -> stringResource(R.string.event_source_merged)
        "CONFLICT" -> stringResource(R.string.event_source_conflict)
        else -> baseSource.toReadableEventSource()
    }
}

private fun String.toReadableEventSource(): String {
    val words = trim()
        .replace('_', ' ')
        .lowercase(Locale.US)
        .split(' ')
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return ""
    return words.joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EventCard(
    event: EventListItem,
    trustState: EventTrustState,
    isResolving: Boolean,
    onMerge: () -> Unit,
    onKeepSeparate: () -> Unit,
) {
    val daysUntil = event.daysUntil
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    val sourceLabel = eventSourceLabel(trustState.source).ifBlank { stringResource(R.string.event_source_unknown) }
    val verificationLabel = eventVerificationLabel(trustState)
    val sourceColor = when (EventResolutionPolicy.baseSource(trustState.source).trim().uppercase(Locale.US)) {
        "MANUAL" -> RelatePrimary
        "CONFLICT" -> MaterialTheme.colorScheme.error
        else -> RelateOnSurfaceVariant
    }
    val verificationColor = when (trustState.verification) {
        EventVerificationState.CONFLICT -> MaterialTheme.colorScheme.error
        EventVerificationState.VERIFIED -> RelatePrimary
        EventVerificationState.NEEDS_REVIEW -> RelateWarning
    }

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
                    eventTypeIcon(event.type.raw),
                    contentDescription = null,
                    tint = if (daysUntil <= 14) RelatePrimary else RelateOnSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.label ?: eventTypeLabel(event.type.raw),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(
                        R.string.events_card_subtitle,
                        eventTypeLabel(event.type.raw),
                        dateFormat.format(Date(event.nextOccurrenceMs)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EventMetadataChip(
                        text = sourceLabel,
                        color = sourceColor,
                    )
                    EventMetadataChip(
                        text = verificationLabel,
                        color = verificationColor,
                    )
                    eventConflictLabel(trustState.conflict)?.let { conflictLabel ->
                        EventMetadataChip(
                            text = conflictLabel,
                            color = if (trustState.conflict == EventTrustConflictState.DATE_CONFLICT) {
                                MaterialTheme.colorScheme.error
                            } else {
                                RelateWarning
                            },
                        )
                    }
                }
                if (trustState.conflict != EventTrustConflictState.NONE) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            enabled = !isResolving,
                            onClick = onMerge,
                        ) {
                            Text(
                                if (isResolving) {
                                    stringResource(R.string.saving)
                                } else {
                                    stringResource(R.string.event_resolution_merge_here)
                                }
                            )
                        }
                        TextButton(
                            enabled = !isResolving,
                            onClick = onKeepSeparate,
                        ) {
                            Text(stringResource(R.string.event_resolution_keep_separate))
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$daysUntil",
                    style = MaterialTheme.typography.headlineSmall,
                    color = RelatePrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.days),
                    style = MaterialTheme.typography.labelSmall,
                    color = RelateOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun eventVerificationLabel(trustState: EventTrustState): String {
    return when (trustState.verification) {
        EventVerificationState.CONFLICT -> stringResource(R.string.event_verification_conflict)
        EventVerificationState.VERIFIED -> stringResource(R.string.event_verification_verified)
        EventVerificationState.NEEDS_REVIEW -> stringResource(
            R.string.event_verification_needs_review,
            trustState.confidenceScore,
        )
    }
}

@Composable
private fun eventConflictLabel(conflict: EventTrustConflictState): String? {
    return when (conflict) {
        EventTrustConflictState.NONE -> null
        EventTrustConflictState.DUPLICATE -> stringResource(R.string.event_conflict_duplicate)
        EventTrustConflictState.DATE_CONFLICT -> stringResource(R.string.event_conflict_date)
    }
}

@Composable
private fun EventMetadataChip(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .sizeIn(minHeight = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
