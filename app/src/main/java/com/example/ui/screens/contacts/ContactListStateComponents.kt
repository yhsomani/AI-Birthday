package com.example.ui.screens.contacts

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.ShimmerItem
import com.example.core.ui.theme.RelateFraction
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.components.SyncErrorCard
import com.example.ui.viewmodel.ContactListUiState

@Composable
internal fun ContactListRefreshContent(
    state: ContactListUiState,
    onRefresh: () -> Unit,
    onDismissSyncError: () -> Unit,
    onContactClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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

        when {
            state.isLoading && state.contacts.isEmpty() ->
                ContactListLoadingRows(modifier = Modifier.weight(1f))
            state.contacts.isEmpty() ->
                ContactListEmptyState(modifier = Modifier.weight(1f))
            else ->
                ContactRows(
                    state = state,
                    onContactClick = onContactClick,
                    modifier = Modifier.weight(1f),
                )
        }
    }
}

@Composable
private fun ContactListLoadingRows(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.lg),
        contentPadding = PaddingValues(vertical = RelateSpacing.md),
    ) {
        items(10) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerItem(
                    modifier = Modifier
                        .size(RelateSize.minTouchTarget)
                        .clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(RelateSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerItem(
                        modifier = Modifier
                            .fillMaxWidth(RelateFraction.skeletonTitle)
                            .height(RelateSpacing.lg),
                    )
                    Spacer(modifier = Modifier.height(RelateSpacing.sm))
                    ShimmerItem(
                        modifier = Modifier
                            .fillMaxWidth(RelateFraction.skeletonSubtitle)
                            .height(RelateSpacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactListEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(message = stringResource(R.string.contacts_no_contacts_found))
    }
}

@Composable
private fun ContactRows(
    state: ContactListUiState,
    onContactClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
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
