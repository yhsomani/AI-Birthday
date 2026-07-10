package com.example.ui.screens.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.occasion.EventListItem
import com.example.ui.viewmodel.EventHorizonFilter
import com.example.ui.viewmodel.EventTrustState
import com.example.ui.viewmodel.EventTypeFilter
import com.example.ui.viewmodel.buildEventTrustStates
import java.util.Locale

internal val eventTypeFilters = listOf(
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

internal val eventHorizonFilters = listOf(
    EventHorizonFilter.ALL,
    EventHorizonFilter.NEXT_7_DAYS,
    EventHorizonFilter.NEXT_30_DAYS,
    EventHorizonFilter.NEXT_90_DAYS,
)

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
    // ⚡ Bolt: Memoized expensive grouping and Calendar allocation to prevent GC churn on recomposition
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
            // ⚡ Bolt: Using built-in items extension instead of manual loop for better LazyColumn performance
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
internal fun EventTypeFilter.label(): String = when (this) {
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
internal fun EventHorizonFilter.label(): String = when (this) {
    EventHorizonFilter.ALL -> stringResource(R.string.filter_all)
    EventHorizonFilter.NEXT_7_DAYS -> stringResource(R.string.filter_next_7_days)
    EventHorizonFilter.NEXT_30_DAYS -> stringResource(R.string.filter_next_30_days)
    EventHorizonFilter.NEXT_90_DAYS -> stringResource(R.string.filter_next_90_days)
}
