package com.example.ui.screens.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.viewmodel.MessagesUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessagesPagerContent(
    state: MessagesUiState,
    pagerState: PagerState,
    onRefresh: () -> Unit,
    onNavigateToWish: (String, String) -> Unit,
    onNavigateToAutomationSetup: () -> Unit,
    onApproveMessage: (String) -> Unit,
    onRejectRequested: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onRevokeApproval: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (page) {
                    0 -> PendingMessagesList(
                        messages = state.needsReviewMessages,
                        emptyText = stringResource(R.string.messages_empty_needs_review),
                        onApprove = onApproveMessage,
                        onReject = onRejectRequested,
                        onEdit = onNavigateToWish,
                        approvingMessageId = state.approvingMessageId,
                        selectedMessageIds = state.selectedMessageIds,
                        onToggleSelection = onToggleSelection,
                    )
                    1 -> ApprovedMessagesList(
                        messages = state.scheduledMessages,
                        emptyText = stringResource(R.string.messages_empty_scheduled),
                        onRevoke = onRevokeApproval,
                        onReject = onRejectRequested,
                        onEdit = onNavigateToWish,
                        revokingMessageId = state.revokingMessageId,
                        selectedMessageIds = state.selectedMessageIds,
                        onToggleSelection = onToggleSelection,
                    )
                    2 -> PendingMessagesList(
                        messages = state.blockedMessages,
                        emptyText = stringResource(R.string.messages_empty_blocked),
                        onApprove = onApproveMessage,
                        onReject = onRejectRequested,
                        onEdit = onNavigateToWish,
                        approvingMessageId = state.approvingMessageId,
                        selectedMessageIds = state.selectedMessageIds,
                        onToggleSelection = onToggleSelection,
                        showApproveAction = false,
                        cardTagPrefix = MessagesTestTags.BLOCKED_CARD_PREFIX,
                    )
                    3 -> SentMessagesList(messages = state.sentMessages)
                    4 -> FailedMessagesList(
                        messages = state.failedMessages,
                        onRetry = onRetryMessage,
                        onOpenAutomationSetup = onNavigateToAutomationSetup,
                        retryingMessageId = state.retryingMessageId,
                        selectedMessageIds = state.selectedMessageIds,
                        onToggleSelection = onToggleSelection,
                    )
                }
            }
        }
    }
}
