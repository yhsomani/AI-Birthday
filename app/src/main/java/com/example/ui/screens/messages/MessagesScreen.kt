package com.example.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.MessageChannelFilter
import com.example.ui.viewmodel.MessageSort
import com.example.ui.viewmodel.MessagesUiState
import com.example.ui.viewmodel.MessagesViewModel
import kotlinx.coroutines.launch

internal object MessagesTestTags {
    const val SEARCH_FIELD = "messages_search_field"
    const val BULK_BAR = "messages_bulk_bar"
    const val BULK_APPROVE = "messages_bulk_approve"
    const val BULK_RETRY = "messages_bulk_retry"
    const val BULK_REJECT = "messages_bulk_reject"
    const val BULK_CLEAR = "messages_bulk_clear"
    const val REJECT_DIALOG = "messages_reject_dialog"
    const val REJECT_CONFIRM = "messages_reject_confirm"
    const val REJECT_CANCEL = "messages_reject_cancel"
    const val TAB_PREFIX = "messages_tab_"
    const val CHANNEL_FILTER_PREFIX = "messages_channel_filter_"
    const val SORT_PREFIX = "messages_sort_"
    const val PENDING_CARD_PREFIX = "messages_pending_card_"
    const val BLOCKED_CARD_PREFIX = "messages_blocked_card_"
    const val APPROVED_CARD_PREFIX = "messages_approved_card_"
    const val FAILED_CARD_PREFIX = "messages_failed_card_"
    const val FAILED_RECOVERY_ASSISTANT = "messages_failed_recovery_assistant"
    const val FAILED_RECOVERY_OPEN_SETUP = "messages_failed_recovery_open_setup"
    const val VERIFICATION_ASSISTANT = "messages_verification_assistant"
    const val READINESS_PREFIX = "messages_readiness_"
    const val CHANNEL_PREFIX = "messages_channel_"
    const val SENT_CARD_PREFIX = "messages_sent_card_"
    const val SELECT_PREFIX = "messages_select_"
    const val PENDING_APPROVE_PREFIX = "messages_pending_approve_"
    const val PENDING_REJECT_PREFIX = "messages_pending_reject_"
    const val PENDING_EDIT_PREFIX = "messages_pending_edit_"
    const val APPROVED_REVOKE_PREFIX = "messages_approved_revoke_"
    const val APPROVED_REJECT_PREFIX = "messages_approved_reject_"
    const val APPROVED_EDIT_PREFIX = "messages_approved_edit_"
    const val FAILED_RETRY_PREFIX = "messages_failed_retry_"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onNavigateToWish: (String, String) -> Unit = { _, _ -> },
    onNavigateToAutomationSetup: () -> Unit = {},
    initialChannelFilter: MessageChannelFilter? = null,
    verificationChannelFilter: MessageChannelFilter? = null,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialChannelFilter) {
        initialChannelFilter?.let(viewModel::selectChannelFilter)
    }

    MessagesContent(
        state = state,
        verificationChannelFilter = verificationChannelFilter,
        onNavigateToWish = onNavigateToWish,
        onNavigateToAutomationSetup = onNavigateToAutomationSetup,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onChannelFilterSelected = viewModel::selectChannelFilter,
        onSortSelected = viewModel::selectSort,
        onRefresh = viewModel::refresh,
        onApproveMessage = viewModel::approveMessage,
        onRejectMessage = viewModel::rejectMessage,
        onRetryMessage = viewModel::retryMessage,
        onRevokeApproval = viewModel::revokeApproval,
        onBulkApproveSelected = viewModel::bulkApproveSelected,
        onBulkRetrySelected = viewModel::bulkRetrySelected,
        onBulkRejectSelected = viewModel::bulkRejectSelected,
        onClearSelection = viewModel::clearSelection,
        onToggleSelection = viewModel::toggleSelection,
        onClearError = viewModel::clearError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessagesContent(
    state: MessagesUiState,
    initialPage: Int = 0,
    verificationChannelFilter: MessageChannelFilter? = null,
    onNavigateToWish: (String, String) -> Unit = { _, _ -> },
    onNavigateToAutomationSetup: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onChannelFilterSelected: (MessageChannelFilter) -> Unit = {},
    onSortSelected: (MessageSort) -> Unit = {},
    onRefresh: () -> Unit = {},
    onApproveMessage: (String) -> Unit = {},
    onRejectMessage: (String) -> Unit = {},
    onRetryMessage: (String) -> Unit = {},
    onRevokeApproval: (String) -> Unit = {},
    onBulkApproveSelected: () -> Unit = {},
    onBulkRetrySelected: () -> Unit = {},
    onBulkRejectSelected: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onClearError: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs = listOf(
        stringResource(R.string.messages_tab_needs_review, state.needsReviewMessages.size),
        stringResource(R.string.messages_tab_scheduled, state.scheduledMessages.size),
        stringResource(R.string.messages_tab_blocked, state.blockedMessages.size),
        stringResource(R.string.messages_tab_sent, state.sentMessages.size),
        stringResource(R.string.messages_tab_failed, state.failedMessages.size),
    )

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 5 })
    var showRejectDialogForId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(
        verificationChannelFilter,
        state.isLoading,
        state.selectedChannelFilter,
        state.needsReviewMessages.size,
        state.failedMessages.size,
        state.scheduledMessages.size,
        state.blockedMessages.size,
    ) {
        if (
            verificationChannelFilter != null &&
            !state.isLoading &&
            state.selectedChannelFilter == verificationChannelFilter
        ) {
            val targetPage = when {
                state.needsReviewMessages.isNotEmpty() -> 0
                state.failedMessages.isNotEmpty() -> 4
                state.scheduledMessages.isNotEmpty() -> 1
                state.blockedMessages.isNotEmpty() -> 2
                else -> 0
            }
            if (pagerState.currentPage != targetPage) {
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onClearError()
        }
    }

    showRejectDialogForId?.let { messageId ->
        MessagesRejectDialog(
            onConfirm = {
                onRejectMessage(messageId)
                showRejectDialogForId = null
            },
            onDismiss = { showRejectDialogForId = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = RelateSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(RelateSpacing.screenTop))
        Text(
            text = stringResource(R.string.messages_inbox_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.lg))

        MessagesTabRow(
            tabTitles = tabs,
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
        )

        Spacer(modifier = Modifier.height(RelateSpacing.md))

        MessagesFilterControls(
            searchQuery = state.searchQuery,
            selectedChannelFilter = state.selectedChannelFilter,
            selectedSort = state.selectedSort,
            onSearchQueryChange = onSearchQueryChange,
            onChannelFilterSelected = onChannelFilterSelected,
            onSortSelected = onSortSelected,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.md))

        if (verificationChannelFilter != null) {
            ChannelVerificationAssistant(
                channelFilter = verificationChannelFilter,
                modifier = Modifier.testTag(MessagesTestTags.VERIFICATION_ASSISTANT),
            )
            Spacer(modifier = Modifier.height(RelateSpacing.md))
        }

        if (state.selectedMessageIds.isNotEmpty()) {
            BulkActionBar(
                selectedCount = state.selectedMessageIds.size,
                showApprove = pagerState.currentPage == 0,
                showRetry = pagerState.currentPage == 4,
                onApprove = onBulkApproveSelected,
                onRetry = onBulkRetrySelected,
                onReject = onBulkRejectSelected,
                onClear = onClearSelection,
                modifier = Modifier.testTag(MessagesTestTags.BULK_BAR),
            )
            Spacer(modifier = Modifier.height(RelateSpacing.md))
        }

        MessagesPagerContent(
            state = state,
            pagerState = pagerState,
            onRefresh = onRefresh,
            onNavigateToWish = onNavigateToWish,
            onNavigateToAutomationSetup = onNavigateToAutomationSetup,
            onApproveMessage = onApproveMessage,
            onRejectRequested = { showRejectDialogForId = it },
            onRetryMessage = onRetryMessage,
            onRevokeApproval = onRevokeApproval,
            onToggleSelection = onToggleSelection,
            modifier = Modifier.weight(1f),
        )
        SnackbarHost(hostState = snackbarHostState)
    }
}
