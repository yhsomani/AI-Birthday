package com.example.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.ContactFilter
import com.example.ui.viewmodel.ContactListUiState
import com.example.ui.viewmodel.ContactSort

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
        ContactSearchField(
            searchQuery = state.searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onClearSearch = onClearSearch,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.md))
        ContactFilterRow(
            selectedFilter = state.selectedFilter,
            onFilterSelected = onFilterSelected,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        ContactSortRow(
            selectedSort = state.selectedSort,
            onSortSelected = onSortSelected,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            ContactListRefreshContent(
                state = state,
                onRefresh = onRefresh,
                onDismissSyncError = onDismissSyncError,
                onContactClick = onContactClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
