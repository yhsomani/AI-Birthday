package com.example.ui.screens.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.HealthIndicatorDot
import com.example.core.ui.components.ShimmerItem
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateFraction
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.contact.ContactListItem
import com.example.ui.components.SyncErrorCard
import com.example.ui.viewmodel.ContactFilter
import com.example.ui.viewmodel.ContactListUiState
import com.example.ui.viewmodel.ContactListViewModel
import com.example.ui.viewmodel.ContactQualityState
import com.example.ui.viewmodel.ContactQualityStatus
import com.example.ui.viewmodel.ContactSort

internal object ContactListTestTags {
    const val SEARCH_FIELD = "contact_list_search_field"
    const val SYNC_ERROR_CARD = "contact_list_sync_error_card"
    const val FILTER_PREFIX = "contact_list_filter_"
    const val SORT_PREFIX = "contact_list_sort_"
    const val ROW_PREFIX = "contact_list_row_"
    const val QUALITY_PREFIX = "contact_list_quality_"
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onContactClick: (String) -> Unit = {},
    viewModel: ContactListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refresh()
    }
    val syncContacts = {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.refresh()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    ContactListContent(
        state = state,
        onContactClick = onContactClick,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onClearSearch = { viewModel.updateSearchQuery("") },
        onFilterSelected = viewModel::selectFilter,
        onSortSelected = viewModel::selectSort,
        onRefresh = syncContacts,
        onDismissSyncError = { viewModel.dismissSyncError() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactListContent(
    state: ContactListUiState,
    onContactClick: (String) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onFilterSelected: (ContactFilter) -> Unit = {},
    onSortSelected: (ContactSort) -> Unit = {},
    onRefresh: () -> Unit = {},
    onDismissSyncError: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = RelateSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(RelateSize.minTouchTarget))
        Text(
            text = stringResource(R.string.nav_contacts),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.lg))
        OutlinedTextField(
            value = state.searchQuery,
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
                if (state.searchQuery.isNotEmpty()) {
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
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            ),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            filterOptions.forEach { filter ->
                FilterChip(
                    label = filter.label(),
                    isSelected = state.selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    modifier = Modifier.testTag(ContactListTestTags.FILTER_PREFIX + filter.name),
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
            sortOptions.forEach { sort ->
                FilterChip(
                    label = sort.label(),
                    isSelected = state.selectedSort == sort,
                    onClick = { onSortSelected(sort) },
                    modifier = Modifier.testTag(ContactListTestTags.SORT_PREFIX + sort.name),
                )
            }
        }
        Spacer(modifier = Modifier.height(RelateSpacing.sm))

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                state.syncError?.let { errorMsg ->
                    SyncErrorCard(
                        message = errorMsg,
                        onRetry = onRefresh,
                        onDismiss = onDismissSyncError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = RelateSpacing.md)
                            .testTag(ContactListTestTags.SYNC_ERROR_CARD),
                    )
                }

                if (state.isLoading && state.contacts.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(RelateSpacing.lg),
                        contentPadding = PaddingValues(vertical = RelateSpacing.md)
                    ) {
                        items(10) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                ShimmerItem(modifier = Modifier.size(RelateSize.minTouchTarget).clip(CircleShape))
                                Spacer(modifier = Modifier.width(RelateSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    ShimmerItem(modifier = Modifier.fillMaxWidth(RelateFraction.skeletonTitle).height(RelateSpacing.lg))
                                    Spacer(modifier = Modifier.height(RelateSpacing.sm))
                                    ShimmerItem(modifier = Modifier.fillMaxWidth(RelateFraction.skeletonSubtitle).height(RelateSpacing.md))
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
                        items(state.contacts, key = { it.id.value }) { contact ->
                            ContactRow(
                                contact = contact,
                                quality = state.contactQuality[contact.id.value],
                                onClick = { onContactClick(contact.id.value) },
                                modifier = Modifier.testTag(ContactListTestTags.ROW_PREFIX + contact.id.value),
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

@Composable
private fun ContactRow(
    contact: ContactListItem,
    quality: ContactQualityState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = RelateSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(RelateSize.minTouchTarget)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contact.displayName.take(1),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.width(RelateSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                HealthIndicatorDot(health = contact.healthScore / 100f)
            }
            val group = contact.contactGroup ?: contact.relationshipType
            Text(
                text = group,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            quality?.let {
                Text(
                    text = it.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = it.labelColor(),
                    modifier = Modifier.testTag(ContactListTestTags.QUALITY_PREFIX + contact.id.value),
                )
            }
        }
    }
}

@Composable
private fun ContactQualityState.label(): String = when (status) {
    ContactQualityStatus.READY -> stringResource(R.string.contact_quality_ready)
    ContactQualityStatus.MISSING_EVENT -> stringResource(R.string.contact_quality_missing_event)
    ContactQualityStatus.MISSING_CHANNEL -> stringResource(R.string.contact_quality_missing_channel)
    ContactQualityStatus.MISSING_CONTEXT -> stringResource(R.string.contact_quality_missing_context)
}

@Composable
private fun ContactQualityState.labelColor() = when (status) {
    ContactQualityStatus.READY -> MaterialTheme.colorScheme.onSurfaceVariant
    ContactQualityStatus.MISSING_EVENT,
    ContactQualityStatus.MISSING_CHANNEL,
    ContactQualityStatus.MISSING_CONTEXT -> MaterialTheme.colorScheme.error
}
