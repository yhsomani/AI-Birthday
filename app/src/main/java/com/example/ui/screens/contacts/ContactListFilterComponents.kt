package com.example.ui.screens.contacts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.FilterChip
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.ContactFilter
import com.example.ui.viewmodel.ContactSort

private val filterOptions = listOf(
    ContactFilter.ALL,
    ContactFilter.FAMILY,
    ContactFilter.FRIENDS,
    ContactFilter.WORK,
    ContactFilter.CLOSE_FRIENDS,
    ContactFilter.NEEDS_PERSONALIZATION,
    ContactFilter.MISSING_RELATIONSHIP,
    ContactFilter.MISSING_CHANNEL,
    ContactFilter.LOW_HEALTH,
    ContactFilter.VIP,
)

private val sortOptions = listOf(
    ContactSort.NAME_ASC,
    ContactSort.HEALTH_DESC,
    ContactSort.HEALTH_ASC,
)

@Composable
internal fun ContactSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        label = {
            Text(stringResource(R.string.contacts_search_placeholder))
        },
        placeholder = {
            Text(
                stringResource(R.string.contacts_search_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = stringResource(R.string.search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = onClearSearch) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ContactListTestTags.SEARCH_FIELD),
        shape = RoundedCornerShape(RelateRadius.control),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = RelateAlpha.fieldContainer,
            ),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = RelateAlpha.fieldContainer,
            ),
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        ),
        singleLine = true,
    )
}

@Composable
internal fun ContactFilterRow(
    selectedFilter: ContactFilter,
    onFilterSelected: (ContactFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        filterOptions.forEach { filter ->
            FilterChip(
                label = filter.label(),
                isSelected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                modifier = Modifier.testTag(ContactListTestTags.FILTER_PREFIX + filter.name),
            )
        }
    }
}

@Composable
internal fun ContactSortRow(
    selectedSort: ContactSort,
    onSortSelected: (ContactSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        sortOptions.forEach { sort ->
            FilterChip(
                label = sort.label(),
                isSelected = selectedSort == sort,
                onClick = { onSortSelected(sort) },
                modifier = Modifier.testTag(ContactListTestTags.SORT_PREFIX + sort.name),
            )
        }
    }
}

@Composable
private fun ContactFilter.label(): String = when (this) {
    ContactFilter.ALL -> stringResource(R.string.filter_all)
    ContactFilter.FAMILY -> stringResource(R.string.contact_filter_family)
    ContactFilter.FRIENDS -> stringResource(R.string.contact_filter_friends)
    ContactFilter.WORK -> stringResource(R.string.contact_filter_work)
    ContactFilter.CLOSE_FRIENDS -> stringResource(R.string.contact_filter_close_friends)
    ContactFilter.NEEDS_PERSONALIZATION -> stringResource(R.string.contact_filter_needs_personalization)
    ContactFilter.MISSING_RELATIONSHIP -> stringResource(R.string.contact_filter_missing_relationship)
    ContactFilter.MISSING_CHANNEL -> stringResource(R.string.contact_filter_missing_channel)
    ContactFilter.LOW_HEALTH -> stringResource(R.string.contact_filter_low_health)
    ContactFilter.VIP -> stringResource(R.string.contact_filter_vip)
}

@Composable
private fun ContactSort.label(): String = when (this) {
    ContactSort.NAME_ASC -> stringResource(R.string.contact_sort_name)
    ContactSort.HEALTH_DESC -> stringResource(R.string.contact_sort_health_high)
    ContactSort.HEALTH_ASC -> stringResource(R.string.contact_sort_health_low)
}
