package com.example.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.relateTextFieldColors
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.occasion.EventListItem
import com.example.ui.viewmodel.EventHorizonFilter
import com.example.ui.viewmodel.EventResolutionAction
import com.example.ui.viewmodel.EventTrustState
import com.example.ui.viewmodel.EventTypeFilter
import com.example.ui.viewmodel.EventsUiState
import com.example.ui.viewmodel.EventsViewModel
import com.example.ui.viewmodel.buildEventTrustStates
import kotlinx.coroutines.launch
import java.util.Locale

private val eventTypeFilters = listOf(
    EventTypeFilter.ALL,
    EventTypeFilter.BIRTHDAY,
    EventTypeFilter.ANNIVERSARY,
    EventTypeFilter.WORK,
    EventTypeFilter.GRADUATION,
    EventTypeFilter.HOLIDAY,
    EventTypeFilter.REVIVAL,
    EventTypeFilter.FOLLOW_UP,
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
    const val MANUAL_CONTACT_FIELD = "events_manual_contact_field"
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
    val resolvedEventTrust = if (events.all { eventTrust.containsKey(it.id.value) }) {
        eventTrust
    } else {
        buildEventTrustStates(events)
    }
    val groupedEvents = remember(events) {
        val cal = java.util.Calendar.getInstance()
        events.groupBy {
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
            items(monthEvents, key = { it.id.value }) { event ->
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
private fun EventTypeFilter.label(): String = when (this) {
    EventTypeFilter.ALL -> stringResource(R.string.filter_all)
    EventTypeFilter.BIRTHDAY -> stringResource(R.string.events_filter_birthdays)
    EventTypeFilter.ANNIVERSARY -> stringResource(R.string.events_filter_anniversaries)
    EventTypeFilter.WORK -> stringResource(R.string.events_filter_work)
    EventTypeFilter.GRADUATION -> stringResource(R.string.events_filter_graduation)
    EventTypeFilter.HOLIDAY -> stringResource(R.string.events_filter_holidays)
    EventTypeFilter.REVIVAL -> stringResource(R.string.events_filter_revivals)
    EventTypeFilter.FOLLOW_UP -> stringResource(R.string.events_filter_follow_ups)
    EventTypeFilter.CUSTOM -> stringResource(R.string.events_filter_custom)
}

@Composable
private fun EventHorizonFilter.label(): String = when (this) {
    EventHorizonFilter.ALL -> stringResource(R.string.filter_all)
    EventHorizonFilter.NEXT_7_DAYS -> stringResource(R.string.filter_next_7_days)
    EventHorizonFilter.NEXT_30_DAYS -> stringResource(R.string.filter_next_30_days)
    EventHorizonFilter.NEXT_90_DAYS -> stringResource(R.string.filter_next_90_days)
}
