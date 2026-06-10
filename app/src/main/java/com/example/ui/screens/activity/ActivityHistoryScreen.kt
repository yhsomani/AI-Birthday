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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.R
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.ui.viewmodel.ActivityHistoryViewModel
import com.example.ui.viewmodel.ActivityLogDateFilter
import com.example.ui.viewmodel.ActivityLogTypeFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityHistoryScreen(
    onBack: () -> Unit,
    viewModel: ActivityHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    RelateScreen(
        title = stringResource(R.string.activity_history_title),
        subtitle = stringResource(R.string.activity_history_subtitle),
        navigationIcon = Icons.Filled.ArrowBack,
        navigationContentDescription = stringResource(R.string.back),
        onNavigationClick = onBack,
    ) {
        FilterRow(
            filters = ActivityLogTypeFilter.entries,
            selected = state.selectedTypeFilter,
            label = { it.label() },
            onSelected = viewModel::selectTypeFilter,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilterRow(
            filters = ActivityLogDateFilter.entries,
            selected = state.selectedDateFilter,
            label = { it.label() },
            onSelected = viewModel::selectDateFilter,
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = RelatePrimary)
            }
        } else if (state.entries.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.activity_history_empty),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    ActivityLogCard(entry = entry)
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
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { filter ->
            FilterChip(
                label = label(filter),
                isSelected = selected == filter,
                onClick = { onSelected(filter) },
            )
        }
    }
}

@Composable
private fun ActivityLogCard(entry: ActivityLogEntity) {
    val dateFormat = rememberActivityDateFormat()
    RelateGlassCard {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = entry.type.icon(),
                contentDescription = null,
                tint = RelatePrimary,
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
                    color = RelateOnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(entry.createdAtMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = RelateOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityLogTypeFilter.label(): String = when (this) {
    ActivityLogTypeFilter.ALL -> stringResource(R.string.filter_all)
    ActivityLogTypeFilter.MESSAGE -> stringResource(R.string.activity_filter_message)
    ActivityLogTypeFilter.EVENT -> stringResource(R.string.activity_filter_event)
    ActivityLogTypeFilter.SYNC -> stringResource(R.string.activity_filter_sync)
    ActivityLogTypeFilter.ANALYTICS -> stringResource(R.string.activity_filter_analytics)
    ActivityLogTypeFilter.SETTINGS -> stringResource(R.string.activity_filter_settings)
}

@Composable
private fun ActivityLogDateFilter.label(): String = when (this) {
    ActivityLogDateFilter.ALL -> stringResource(R.string.filter_all)
    ActivityLogDateFilter.TODAY -> stringResource(R.string.activity_filter_today)
    ActivityLogDateFilter.LAST_7_DAYS -> stringResource(R.string.activity_filter_last_7_days)
    ActivityLogDateFilter.LAST_30_DAYS -> stringResource(R.string.activity_filter_last_30_days)
}

@Composable
private fun rememberActivityDateFormat(): SimpleDateFormat =
    SimpleDateFormat(stringResource(R.string.activity_history_date_pattern), Locale.getDefault())

private fun String.icon(): ImageVector = when (uppercase(Locale.US)) {
    "MESSAGE" -> Icons.Filled.MailOutline
    "EVENT" -> Icons.Filled.Event
    "SYNC" -> Icons.Filled.Sync
    "ANALYTICS" -> Icons.Filled.Analytics
    "SETTINGS" -> Icons.Filled.Settings
    else -> Icons.Filled.History
}
