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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.relateTextFieldColors
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
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
import com.example.ui.viewmodel.EventsUiState
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

internal object EventsTestTags {
    const val CONTENT_BOTTOM = "events_content_bottom"
    const val MANUAL_DIALOG = "events_manual_dialog"
    const val MANUAL_FORM_BODY = "events_manual_form_body"
    const val MANUAL_YEAR_FIELD = "events_manual_year_field"
    const val MANUAL_WARNING = "events_manual_warning"
    const val MANUAL_SAVE = "events_manual_save"
    const val MANUAL_CANCEL = "events_manual_cancel"
}

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

    EventsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        showManualDialog = showManualDialog,
        onShowManualDialog = { showManualDialog = true },
        onDismissManualDialog = {
            viewModel.clearManualEventDuplicateWarning()
            showManualDialog = false
        },
        onManualInputChanged = viewModel::clearManualEventDuplicateWarning,
        onSaveManualEvent = { existingContactId, newContactName, eventType, label, month, day, year, allowDuplicate ->
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
        onSearchQueryChanged = viewModel::updateSearchQuery,
        onTypeFilterSelected = viewModel::selectTypeFilter,
        onHorizonFilterSelected = viewModel::selectHorizonFilter,
        onRefresh = viewModel::refresh,
        onMergeEvent = { viewModel.resolveEventConflict(it, EventResolutionAction.MERGE_KEEP_SELECTED) },
        onKeepSeparateEvent = { viewModel.resolveEventConflict(it, EventResolutionAction.KEEP_SEPARATE) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventsContent(
    state: EventsUiState,
    snackbarHostState: SnackbarHostState? = null,
    showManualDialog: Boolean = false,
    currentTimeMillis: Long = System.currentTimeMillis(),
    onShowManualDialog: () -> Unit = {},
    onDismissManualDialog: () -> Unit = {},
    onManualInputChanged: () -> Unit = {},
    onSaveManualEvent: (
        existingContactId: String?,
        newContactName: String?,
        eventType: String,
        label: String?,
        month: Int,
        day: Int,
        year: Int?,
        allowDuplicate: Boolean,
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onSearchQueryChanged: (String) -> Unit = {},
    onTypeFilterSelected: (EventTypeFilter) -> Unit = {},
    onHorizonFilterSelected: (EventHorizonFilter) -> Unit = {},
    onRefresh: () -> Unit = {},
    onMergeEvent: (String) -> Unit = {},
    onKeepSeparateEvent: (String) -> Unit = {},
) {
    val resolvedSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }
    if (showManualDialog) {
        ManualEventDialog(
            contacts = state.contacts,
            isSaving = state.isSavingManualEvent,
            duplicateWarning = state.duplicateWarning,
            onDismiss = onDismissManualDialog,
            onInputChanged = onManualInputChanged,
            onSave = { existingContactId, newContactName, eventType, label, month, day, year, allowDuplicate ->
                onSaveManualEvent(
                    existingContactId,
                    newContactName,
                    eventType,
                    label,
                    month,
                    day,
                    year,
                    allowDuplicate,
                )
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        RelateScreen(
            title = stringResource(R.string.nav_events),
            subtitle = stringResource(R.string.events_subtitle),
            action = {
                IconButton(
                    onClick = onShowManualDialog
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.events_add_event),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search)) },
                placeholder = { Text(stringResource(R.string.events_search_placeholder)) },
                singleLine = true,
                colors = relateTextFieldColors(),
            )
            Spacer(modifier = Modifier.height(RelateSpacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
            ) {
                eventTypeFilters.forEach { filter ->
                    FilterChip(
                        label = filter.label(),
                        isSelected = state.selectedTypeFilter == filter,
                        onClick = { onTypeFilterSelected(filter) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
            ) {
                eventHorizonFilters.forEach { filter ->
                    FilterChip(
                        label = filter.label(),
                        isSelected = state.selectedHorizonFilter == filter,
                        onClick = { onHorizonFilterSelected(filter) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(RelateSpacing.md))
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (state.isLoading && state.events.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                        currentTimeMillis = currentTimeMillis,
                        onMergeEvent = onMergeEvent,
                        onKeepSeparateEvent = onKeepSeparateEvent,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = resolvedSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(RelateSpacing.screenHorizontal),
        )
    }
}

@Composable
internal fun EventsList(
    events: List<EventListItem>,
    eventTrust: Map<String, EventTrustState> = buildEventTrustStates(events),
    resolvingEventId: String? = null,
    currentTimeMillis: Long = System.currentTimeMillis(),
    onMergeEvent: (String) -> Unit = {},
    onKeepSeparateEvent: (String) -> Unit = {},
) {
    val resolvedEventTrust = remember(events, eventTrust) {
        if (events.all { eventTrust.containsKey(it.id.value) }) {
            eventTrust
        } else {
            buildEventTrustStates(events)
        }
    }
    // Optimization: remember groupBy result to prevent unnecessary recompositions and
    // Calendar instance allocations on every render pass.
    val groupedEvents = remember(events) {
        events.groupBy {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = it.nextOccurrenceMs
            cal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, Locale.getDefault()) ?: "Other"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
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
                        currentTimeMillis = currentTimeMillis,
                        onMerge = { onMergeEvent(event.id.value) },
                        onKeepSeparate = { onKeepSeparateEvent(event.id.value) },
                    )
                }
            }
        }
        item {
            Spacer(
                modifier = Modifier
                    .height(RelateSpacing.xl)
                    .testTag(EventsTestTags.CONTENT_BOTTOM),
            )
        }
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

    AlertDialog(
        modifier = Modifier.testTag(EventsTestTags.MANUAL_DIALOG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.events_add_event), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            ManualEventDialogBody(
                contacts = contacts,
                useExistingContact = useExistingContact,
                onUseExistingContactChange = { useExistingContact = it },
                selectedContactId = selectedContactId,
                onSelectedContactIdChange = { selectedContactId = it },
                newContactName = newContactName,
                onNewContactNameChange = { newContactName = it },
                eventType = eventType,
                onEventTypeChange = { eventType = it },
                label = label,
                onLabelChange = { label = it },
                monthText = monthText,
                onMonthTextChange = { monthText = it.filter(Char::isDigit).take(2) },
                dayText = dayText,
                onDayTextChange = { dayText = it.filter(Char::isDigit).take(2) },
                yearText = yearText,
                onYearTextChange = { yearText = it.filter(Char::isDigit).take(4) },
                localError = localError,
                duplicateWarning = duplicateWarning,
                onInputChanged = onInputChanged,
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                modifier = Modifier.testTag(EventsTestTags.MANUAL_SAVE),
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
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(EventsTestTags.MANUAL_CANCEL),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManualEventDialogBody(
    contacts: List<ContactPickerItem>,
    useExistingContact: Boolean,
    onUseExistingContactChange: (Boolean) -> Unit,
    selectedContactId: String?,
    onSelectedContactIdChange: (String?) -> Unit,
    newContactName: String,
    onNewContactNameChange: (String) -> Unit,
    eventType: String,
    onEventTypeChange: (String) -> Unit,
    label: String,
    onLabelChange: (String) -> Unit,
    monthText: String,
    onMonthTextChange: (String) -> Unit,
    dayText: String,
    onDayTextChange: (String) -> Unit,
    yearText: String,
    onYearTextChange: (String) -> Unit,
    localError: String?,
    duplicateWarning: ManualEventDuplicateWarning?,
    onInputChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var contactMenuExpanded by remember { mutableStateOf(false) }
    val selectedContactName = contacts.firstOrNull { it.id.value == selectedContactId }?.displayName

    Column(
        modifier = modifier
            .height(RelateSize.dialogContentMaxHeight)
            .verticalScroll(rememberScrollState())
            .testTag(EventsTestTags.MANUAL_FORM_BODY),
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            FilterChip(
                label = stringResource(R.string.events_existing_contact),
                isSelected = useExistingContact,
                onClick = {
                    onInputChanged()
                    onUseExistingContactChange(true)
                    if (selectedContactId == null) {
                        onSelectedContactIdChange(contacts.firstOrNull()?.id?.value)
                    }
                },
            )
            FilterChip(
                label = stringResource(R.string.events_new_contact),
                isSelected = !useExistingContact,
                onClick = {
                    onInputChanged()
                    onUseExistingContactChange(false)
                },
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
                                onSelectedContactIdChange(contact.id.value)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            OutlinedTextField(
                value = newContactName,
                onValueChange = {
                    onInputChanged()
                    onNewContactNameChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.events_new_contact_name)) },
                singleLine = true,
                colors = relateTextFieldColors(),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            eventTypeOptions.forEach { value ->
                FilterChip(
                    label = eventTypeLabel(value),
                    isSelected = eventType == value,
                    onClick = {
                        onInputChanged()
                        onEventTypeChange(value)
                    },
                )
            }
        }

        OutlinedTextField(
            value = label,
            onValueChange = {
                onInputChanged()
                onLabelChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.events_label)) },
            placeholder = { Text(stringResource(R.string.optional)) },
            singleLine = true,
            colors = relateTextFieldColors(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
            OutlinedTextField(
                value = monthText,
                onValueChange = {
                    onInputChanged()
                    onMonthTextChange(it)
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
                    onDayTextChange(it)
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
                    onYearTextChange(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(EventsTestTags.MANUAL_YEAR_FIELD),
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
            Column(
                modifier = Modifier.testTag(EventsTestTags.MANUAL_WARNING),
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
            ) {
                Text(
                    text = stringResource(
                        when (warning.kind) {
                            ManualEventWarningKind.DUPLICATE -> R.string.events_duplicate_title
                            ManualEventWarningKind.DATE_CONFLICT -> R.string.events_conflict_title
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.relateSemanticColors.warning,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = warningMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
    currentTimeMillis: Long,
    onMerge: () -> Unit,
    onKeepSeparate: () -> Unit,
) {
    val daysUntil = event.daysUntil(currentTimeMillis)
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    val sourceLabel = eventSourceLabel(trustState.source).ifBlank { stringResource(R.string.event_source_unknown) }
    val verificationLabel = eventVerificationLabel(trustState)
    val sourceColor = when (EventResolutionPolicy.baseSource(trustState.source).trim().uppercase(Locale.US)) {
        "MANUAL" -> MaterialTheme.colorScheme.primary
        "CONFLICT" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val verificationColor = when (trustState.verification) {
        EventVerificationState.CONFLICT -> MaterialTheme.colorScheme.error
        EventVerificationState.VERIFIED -> MaterialTheme.colorScheme.primary
        EventVerificationState.NEEDS_REVIEW -> MaterialTheme.relateSemanticColors.warning
    }

    RelateGlassCard {
        Row(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(RelateSize.minTouchTarget)
                    .clip(RoundedCornerShape(RelateRadius.control))
                    .background(
                        if (daysUntil <= 14) {
                            MaterialTheme.colorScheme.primary.copy(alpha = RelateAlpha.feedbackContainer)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    eventTypeIcon(event.type.raw),
                    contentDescription = null,
                    tint = if (daysUntil <= 14) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(RelateSize.iconLg),
                )
            }
            Spacer(modifier = Modifier.width(RelateSpacing.md))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
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
                                MaterialTheme.relateSemanticColors.warning
                            },
                        )
                    }
                }
                if (trustState.conflict != EventTrustConflictState.NONE) {
                    Spacer(modifier = Modifier.height(RelateSpacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.days),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .sizeIn(minHeight = RelateSize.chipMinHeight)
            .clip(RoundedCornerShape(RelateRadius.control))
            .background(color.copy(alpha = RelateAlpha.feedbackContainer))
            .padding(horizontal = RelateSpacing.sm, vertical = RelateSpacing.xs),
    )
}
