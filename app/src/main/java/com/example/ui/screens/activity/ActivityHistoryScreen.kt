package com.example.ui.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ActivityLogType
import com.example.domain.model.activity.ActivityLogRecord
import com.example.ui.viewmodel.ActivityHistoryUiState
import com.example.ui.viewmodel.ActivityHistoryViewModel
import com.example.ui.viewmodel.ActivityLogDateFilter
import com.example.ui.viewmodel.ActivityLogStatusFilter
import com.example.ui.viewmodel.ActivityLogTypeFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object ActivityHistoryTestTags {
    const val SEARCH_FIELD = "activity_history_search_field"
    const val LOADING = "activity_history_loading"
    const val EMPTY = "activity_history_empty"
    const val ERROR = "activity_history_error"
    const val TYPE_FILTER_PREFIX = "activity_history_type_filter_"
    const val DATE_FILTER_PREFIX = "activity_history_date_filter_"
    const val STATUS_FILTER_PREFIX = "activity_history_status_filter_"
    const val LOG_CARD_PREFIX = "activity_history_log_"
    const val OPEN_ROUTE_PREFIX = "activity_history_open_route_"
}

@Composable
fun ActivityHistoryScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit = {},
    viewModel: ActivityHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ActivityHistoryContent(
        state = state,
        onBack = onBack,
        onOpenRoute = onOpenRoute,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onTypeFilterSelected = viewModel::selectTypeFilter,
        onDateFilterSelected = viewModel::selectDateFilter,
        onStatusFilterSelected = viewModel::selectStatusFilter,
    )
}

@Composable
internal fun ActivityHistoryContent(
    state: ActivityHistoryUiState,
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTypeFilterSelected: (ActivityLogTypeFilter) -> Unit,
    onDateFilterSelected: (ActivityLogDateFilter) -> Unit,
    onStatusFilterSelected: (ActivityLogStatusFilter) -> Unit,
) {
    RelateScreen(
        title = stringResource(R.string.activity_history_title),
        subtitle = stringResource(R.string.activity_history_subtitle),
        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        navigationContentDescription = stringResource(R.string.back),
        onNavigationClick = onBack,
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ActivityHistoryTestTags.SEARCH_FIELD),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
            },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.clear_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.activity_history_search_placeholder)) },
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        FilterRow(
            filters = ActivityLogTypeFilter.entries,
            selected = state.selectedTypeFilter,
            label = { it.label() },
            tag = { ActivityHistoryTestTags.TYPE_FILTER_PREFIX + it.name },
            onSelected = onTypeFilterSelected,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        FilterRow(
            filters = ActivityLogDateFilter.entries,
            selected = state.selectedDateFilter,
            label = { it.label() },
            tag = { ActivityHistoryTestTags.DATE_FILTER_PREFIX + it.name },
            onSelected = onDateFilterSelected,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.md))
        FilterRow(
            filters = ActivityLogStatusFilter.entries,
            selected = state.selectedStatusFilter,
            label = { it.label() },
            tag = { ActivityHistoryTestTags.STATUS_FILTER_PREFIX + it.name },
            onSelected = onStatusFilterSelected,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.md))

        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(ActivityHistoryTestTags.LOADING),
                )
            }
        } else if (state.errorMessageRes != null) {
            EmptyState(
                message = stringResource(state.errorMessageRes),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ActivityHistoryTestTags.ERROR),
            )
        } else if (state.entries.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.activity_history_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ActivityHistoryTestTags.EMPTY),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                contentPadding = PaddingValues(bottom = RelateSpacing.xl),
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    ActivityLogCard(entry = entry, onOpenRoute = onOpenRoute)
                }
            }
        }
    }
}

@Composable
private fun <T> FilterRow(
    filters: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        filters.forEach { filter ->
            FilterChip(
                label = label(filter),
                isSelected = selected == filter,
                onClick = { onSelected(filter) },
                modifier = Modifier.testTag(tag(filter)),
            )
        }
    }
}

@Composable
private fun ActivityLogCard(
    entry: ActivityLogRecord,
    onOpenRoute: (String) -> Unit,
) {
    val dateFormat = rememberActivityDateFormat()
    RelateGlassCard(
        modifier = Modifier.testTag(ActivityHistoryTestTags.LOG_CARD_PREFIX + entry.id),
    ) {
        Row(
            modifier = Modifier.padding(RelateSpacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Icon(
                imageVector = entry.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
                    Text(
                        text = entry.severity.severityLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = entry.severity.severityColor(),
                    )
                    Text(
                        text = entry.status.statusLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Text(
                    text = dateFormat.format(Date(entry.createdAtMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.actionRoute?.let { route ->
                    Spacer(modifier = Modifier.height(RelateSpacing.sm))
                    Button(
                        onClick = { onOpenRoute(route) },
                        modifier = Modifier.testTag(ActivityHistoryTestTags.OPEN_ROUTE_PREFIX + entry.id),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                    ) {
                        Text(stringResource(R.string.activity_history_open_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLogTypeFilter.label(): String = when (this) {
    ActivityLogTypeFilter.ALL -> stringResource(R.string.filter_all)
    ActivityLogTypeFilter.DISPATCH -> stringResource(R.string.activity_filter_dispatch)
    ActivityLogTypeFilter.AI -> stringResource(R.string.activity_filter_ai)
    ActivityLogTypeFilter.SYNC -> stringResource(R.string.activity_filter_sync)
    ActivityLogTypeFilter.BACKUP -> stringResource(R.string.activity_filter_backup)
    ActivityLogTypeFilter.SETTINGS -> stringResource(R.string.activity_filter_settings)
    ActivityLogTypeFilter.MESSAGE -> stringResource(R.string.activity_filter_message)
    ActivityLogTypeFilter.EVENT -> stringResource(R.string.activity_filter_event)
    ActivityLogTypeFilter.ANALYTICS -> stringResource(R.string.activity_filter_analytics)
}

@Composable
private fun ActivityLogDateFilter.label(): String = when (this) {
    ActivityLogDateFilter.ALL -> stringResource(R.string.filter_all)
    ActivityLogDateFilter.TODAY -> stringResource(R.string.activity_filter_today)
    ActivityLogDateFilter.LAST_7_DAYS -> stringResource(R.string.activity_filter_last_7_days)
    ActivityLogDateFilter.LAST_30_DAYS -> stringResource(R.string.activity_filter_last_30_days)
}

@Composable
private fun ActivityLogStatusFilter.label(): String = when (this) {
    ActivityLogStatusFilter.ALL -> stringResource(R.string.filter_all)
    ActivityLogStatusFilter.OPEN -> stringResource(R.string.activity_filter_open)
    ActivityLogStatusFilter.RESOLVED -> stringResource(R.string.activity_filter_resolved)
}

@Composable
private fun rememberActivityDateFormat(): SimpleDateFormat =
    SimpleDateFormat(stringResource(R.string.activity_history_date_pattern), Locale.getDefault())

private fun String.icon(): ImageVector = when (ActivityLogType.fromRaw(this)) {
    ActivityLogType.DISPATCH -> Icons.Filled.MailOutline
    ActivityLogType.BACKUP -> Icons.Filled.Backup
    ActivityLogType.MESSAGE -> Icons.Filled.MailOutline
    ActivityLogType.EVENT -> Icons.Filled.Event
    ActivityLogType.SYNC -> Icons.Filled.Sync
    ActivityLogType.ANALYTICS -> Icons.Filled.Analytics
    ActivityLogType.SETTINGS -> Icons.Filled.Settings
    ActivityLogType.AI -> Icons.Filled.SmartToy
    ActivityLogType.UNKNOWN -> Icons.Filled.History
}

@Composable
private fun String.severityColor() = when (ActivityLogSeverity.fromRaw(this)) {
    ActivityLogSeverity.ERROR -> MaterialTheme.colorScheme.error
    ActivityLogSeverity.WARNING -> MaterialTheme.relateSemanticColors.warning
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun String.severityLabel(): String = when (ActivityLogSeverity.fromRaw(this)) {
    ActivityLogSeverity.INFO -> stringResource(R.string.activity_severity_info)
    ActivityLogSeverity.WARNING -> stringResource(R.string.activity_severity_warning)
    ActivityLogSeverity.ERROR -> stringResource(R.string.activity_severity_error)
    ActivityLogSeverity.UNKNOWN -> stringResource(R.string.activity_severity_unknown)
}

@Composable
private fun String.statusLabel(): String = when (ActivityLogStatus.fromRaw(this)) {
    ActivityLogStatus.OPEN -> stringResource(R.string.activity_filter_open)
    ActivityLogStatus.RESOLVED -> stringResource(R.string.activity_filter_resolved)
    ActivityLogStatus.UNKNOWN -> stringResource(R.string.activity_status_unknown)
}
