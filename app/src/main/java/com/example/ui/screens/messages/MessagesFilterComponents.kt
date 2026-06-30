package com.example.ui.screens.messages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.relateTextFieldColors
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.MessageChannelFilter
import com.example.ui.viewmodel.MessageSort

private val channelFilters = listOf(
    MessageChannelFilter.ALL,
    MessageChannelFilter.SMS,
    MessageChannelFilter.WHATSAPP,
    MessageChannelFilter.EMAIL,
)

private val messageSortOptions = listOf(
    MessageSort.SCHEDULED_ASC,
    MessageSort.SCHEDULED_DESC,
    MessageSort.CONTACT_ASC,
)

@Composable
internal fun MessagesFilterControls(
    searchQuery: String,
    selectedChannelFilter: MessageChannelFilter,
    selectedSort: MessageSort,
    onSearchQueryChange: (String) -> Unit,
    onChannelFilterSelected: (MessageChannelFilter) -> Unit,
    onSortSelected: (MessageSort) -> Unit,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MessagesTestTags.SEARCH_FIELD),
        label = { Text(stringResource(R.string.search)) },
        placeholder = { Text(stringResource(R.string.messages_search_placeholder)) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
        },
        singleLine = true,
        colors = relateTextFieldColors(),
        shape = RoundedCornerShape(RelateRadius.control),
    )
    Spacer(modifier = Modifier.height(RelateSpacing.sm))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        channelFilters.forEach { filter ->
            FilterChip(
                label = filter.label(),
                isSelected = selectedChannelFilter == filter,
                onClick = { onChannelFilterSelected(filter) },
                modifier = Modifier.testTag(MessagesTestTags.CHANNEL_FILTER_PREFIX + filter.name),
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
        messageSortOptions.forEach { sort ->
            FilterChip(
                label = sort.label(),
                isSelected = selectedSort == sort,
                onClick = { onSortSelected(sort) },
                modifier = Modifier.testTag(MessagesTestTags.SORT_PREFIX + sort.name),
            )
        }
    }
}

@Composable
private fun MessageChannelFilter.label(): String = when (this) {
    MessageChannelFilter.ALL -> stringResource(R.string.filter_all)
    MessageChannelFilter.SMS -> stringResource(R.string.channel_sms)
    MessageChannelFilter.WHATSAPP -> stringResource(R.string.channel_whatsapp)
    MessageChannelFilter.EMAIL -> stringResource(R.string.channel_email)
}

@Composable
private fun MessageSort.label(): String = when (this) {
    MessageSort.SCHEDULED_ASC -> stringResource(R.string.message_sort_oldest)
    MessageSort.SCHEDULED_DESC -> stringResource(R.string.message_sort_newest)
    MessageSort.CONTACT_ASC -> stringResource(R.string.message_sort_contact)
}
