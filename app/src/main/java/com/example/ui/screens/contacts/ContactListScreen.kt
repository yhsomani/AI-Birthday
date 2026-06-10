package com.example.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.HealthIndicatorDot
import com.example.core.ui.components.ShimmerItem
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.ui.components.SyncErrorCard
import com.example.ui.viewmodel.ContactFilter
import com.example.ui.viewmodel.ContactListViewModel
import com.example.ui.viewmodel.ContactSort

private val filterOptions = listOf(
    ContactFilter.ALL,
    ContactFilter.FAMILY,
    ContactFilter.FRIENDS,
    ContactFilter.WORK,
    ContactFilter.CLOSE_FRIENDS,
    ContactFilter.NEEDS_PERSONALIZATION,
)

private val sortOptions = listOf(
    ContactSort.NAME_ASC,
    ContactSort.HEALTH_DESC,
    ContactSort.HEALTH_ASC,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onContactClick: (String) -> Unit = {},
    viewModel: ContactListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.nav_contacts),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            placeholder = {
                Text(stringResource(R.string.contacts_search_placeholder), color = RelateOnSurfaceVariant)
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search), tint = RelateOnSurfaceVariant)
            },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search), tint = RelateOnSurfaceVariant)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RelatePrimary,
                unfocusedBorderColor = RelateSurfaceVariant,
                focusedContainerColor = RelateSurfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = RelateSurfaceVariant.copy(alpha = 0.3f),
                focusedTextColor = RelateOnBackground,
                unfocusedTextColor = RelateOnBackground,
            ),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filterOptions.forEach { filter ->
                FilterChip(
                    label = filter.label(),
                    isSelected = state.selectedFilter == filter,
                    onClick = { viewModel.selectFilter(filter) },
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
            sortOptions.forEach { sort ->
                FilterChip(
                    label = sort.label(),
                    isSelected = state.selectedSort == sort,
                    onClick = { viewModel.selectSort(sort) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                state.syncError?.let { errorMsg ->
                    SyncErrorCard(
                        message = errorMsg,
                        onRetry = { viewModel.refresh() },
                        onDismiss = { viewModel.dismissSyncError() },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }

                if (state.isLoading && state.contacts.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(10) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                ShimmerItem(modifier = Modifier.size(48.dp).clip(CircleShape))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    ShimmerItem(modifier = Modifier.fillMaxWidth(0.5f).height(16.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ShimmerItem(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp))
                                }
                            }
                        }
                    }
                } else if (state.contacts.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(message = stringResource(R.string.contacts_no_contacts_found))
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.contacts, key = { it.id }) { contact ->
                            ContactRow(
                                contact = contact,
                                onClick = { onContactClick(contact.id) },
                            )
                        }
                    }
                }
            }
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
}

@Composable
private fun ContactSort.label(): String = when (this) {
    ContactSort.NAME_ASC -> stringResource(R.string.contact_sort_name)
    ContactSort.HEALTH_DESC -> stringResource(R.string.contact_sort_health_high)
    ContactSort.HEALTH_ASC -> stringResource(R.string.contact_sort_health_low)
}

@Composable
private fun ContactRow(
    contact: ContactEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(RelateSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contact.name.take(1),
                color = RelateOnBackground,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                HealthIndicatorDot(health = contact.healthScore / 100f)
            }
            val group = contact.contactGroup ?: contact.relationshipType
            Text(
                text = group,
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
        }
    }
}
