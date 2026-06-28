package com.example.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.relateTextFieldColors
import com.example.core.ui.theme.*
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.occasion.OccasionType
import com.example.ui.viewmodel.MessageChannelFilter
import com.example.ui.viewmodel.MessageReadiness
import com.example.ui.viewmodel.MessageSort
import com.example.ui.viewmodel.MessagesUiState
import com.example.ui.viewmodel.MessagesViewModel
import com.example.ui.viewmodel.PendingMessageItem
import com.example.ui.viewmodel.SentMessageItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MessagesContent(
        state = state,
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

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onClearError()
        }
    }

    if (showRejectDialogForId != null) {
        AlertDialog(
            modifier = Modifier.testTag(MessagesTestTags.REJECT_DIALOG),
            onDismissRequest = { showRejectDialogForId = null },
            title = { Text(stringResource(R.string.messages_reject_title), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(R.string.messages_reject_body), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag(MessagesTestTags.REJECT_CONFIRM),
                    onClick = {
                        showRejectDialogForId?.let { id ->
                            onRejectMessage(id)
                        }
                        showRejectDialogForId = null
                    }
                ) {
                    Text(stringResource(R.string.reject), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag(MessagesTestTags.REJECT_CANCEL),
                    onClick = { showRejectDialogForId = null },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
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

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = RelateDarkBackground,
            contentColor = RelatePrimary,
            edgePadding = RelateSpacing.none,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    height = RelateSize.progressStroke,
                    color = RelatePrimary,
                )
            },
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier.testTag(MessagesTestTags.TAB_PREFIX + index),
                    text = {
                        Text(
                            text = title,
                            color = if (pagerState.currentPage == index) RelatePrimary else RelateOnSurfaceVariant,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(RelateSpacing.md))

        OutlinedTextField(
            value = state.searchQuery,
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
                    isSelected = state.selectedChannelFilter == filter,
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
                    isSelected = state.selectedSort == sort,
                    onClick = { onSortSelected(sort) },
                    modifier = Modifier.testTag(MessagesTestTags.SORT_PREFIX + sort.name),
                )
            }
        }
        Spacer(modifier = Modifier.height(RelateSpacing.md))

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

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = RelatePrimary)
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
                            onReject = { showRejectDialogForId = it },
                            onEdit = onNavigateToWish,
                            approvingMessageId = state.approvingMessageId,
                            selectedMessageIds = state.selectedMessageIds,
                            onToggleSelection = onToggleSelection,
                        )
                        1 -> ApprovedMessagesList(
                            messages = state.scheduledMessages,
                            emptyText = stringResource(R.string.messages_empty_scheduled),
                            onRevoke = onRevokeApproval,
                            onReject = { showRejectDialogForId = it },
                            onEdit = onNavigateToWish,
                            revokingMessageId = state.revokingMessageId,
                            selectedMessageIds = state.selectedMessageIds,
                            onToggleSelection = onToggleSelection,
                        )
                        2 -> PendingMessagesList(
                            messages = state.blockedMessages,
                            emptyText = stringResource(R.string.messages_empty_blocked),
                            onApprove = onApproveMessage,
                            onReject = { showRejectDialogForId = it },
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
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun BulkActionBar(
    selectedCount: Int,
    showApprove: Boolean,
    showRetry: Boolean,
    onApprove: () -> Unit,
    onRetry: () -> Unit,
    onReject: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = RelateSpacing.md, vertical = RelateSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.messages_selected_count, selectedCount),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (showApprove) {
                Button(
                    onClick = onApprove,
                    contentPadding = PaddingValues(horizontal = RelateSpacing.md, vertical = RelateSpacing.xs),
                    modifier = Modifier
                        .heightIn(min = RelateSize.compactButtonHeight)
                        .testTag(MessagesTestTags.BULK_APPROVE),
                    shape = RoundedCornerShape(RelateRadius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary, contentColor = RelateOnPrimary),
                ) {
                    Text(stringResource(R.string.approve), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (showRetry) {
                Button(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = RelateSpacing.md, vertical = RelateSpacing.xs),
                    modifier = Modifier
                        .heightIn(min = RelateSize.compactButtonHeight)
                        .testTag(MessagesTestTags.BULK_RETRY),
                    shape = RoundedCornerShape(RelateRadius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary, contentColor = RelateOnPrimary),
                ) {
                    Text(stringResource(R.string.retry), style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(
                modifier = Modifier.testTag(MessagesTestTags.BULK_REJECT),
                onClick = onReject,
            ) {
                Text(
                    stringResource(R.string.reject),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(RelateSize.compactButtonHeight)
                    .testTag(MessagesTestTags.BULK_CLEAR),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.messages_clear_selection), tint = RelateOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PendingMessagesList(
    messages: List<PendingMessageItem>,
    emptyText: String,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    approvingMessageId: String?,
    selectedMessageIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    showApproveAction: Boolean = true,
    cardTagPrefix: String = MessagesTestTags.PENDING_CARD_PREFIX,
) {
    if (messages.isEmpty()) {
        EmptyState(message = emptyText)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = RelateSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md)
        ) {
            items(messages, key = { it.id }) { item ->
                PendingMessageCard(
                    item = item,
                    onApprove = onApprove,
                    onReject = onReject,
                    onEdit = onEdit,
                    isApproving = approvingMessageId == item.id,
                    selected = item.id in selectedMessageIds,
                    onToggleSelection = onToggleSelection,
                    showApproveAction = showApproveAction,
                    modifier = Modifier.testTag(cardTagPrefix + item.id),
                )
            }
        }
    }
}

@Composable
private fun SentMessagesList(messages: List<SentMessageItem>) {
    if (messages.isEmpty()) {
        EmptyState(message = stringResource(R.string.messages_empty_sent))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = RelateSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md)
        ) {
            items(messages, key = { it.id }) { item ->
                SentMessageCard(
                    item = item,
                    modifier = Modifier.testTag(MessagesTestTags.SENT_CARD_PREFIX + item.id),
                )
            }
        }
    }
}

@Composable
private fun FailedMessagesList(
    messages: List<PendingMessageItem>,
    onRetry: (String) -> Unit,
    onOpenAutomationSetup: () -> Unit,
    retryingMessageId: String?,
    selectedMessageIds: Set<String>,
    onToggleSelection: (String) -> Unit,
) {
    if (messages.isEmpty()) {
        EmptyState(message = stringResource(R.string.messages_empty_failed))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = RelateSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md)
        ) {
            items(messages, key = { it.id }) { item ->
                FailedMessageCard(
                    item = item,
                    onRetry = onRetry,
                    isRetrying = retryingMessageId == item.id,
                    selected = item.id in selectedMessageIds,
                    onToggleSelection = onToggleSelection,
                    modifier = Modifier.testTag(MessagesTestTags.FAILED_CARD_PREFIX + item.id),
                )
            }
            item(key = "failed_recovery_assistant") {
                FailedRecoveryAssistant(
                    messages = messages,
                    onOpenAutomationSetup = onOpenAutomationSetup,
                    modifier = Modifier.testTag(MessagesTestTags.FAILED_RECOVERY_ASSISTANT),
                )
            }
        }
    }
}

@Composable
internal fun FailedRecoveryAssistant(
    messages: List<PendingMessageItem>,
    onOpenAutomationSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setupBlockers = messages.count { it.readiness.requiresContactOrChannelFix() }
    val detail = if (setupBlockers > 0) {
        stringResource(R.string.messages_recovery_setup_detail, setupBlockers)
    } else {
        stringResource(R.string.messages_recovery_retry_detail)
    }

    RelateGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    tint = RelatePrimary,
                    modifier = Modifier.size(RelateSize.iconMd),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.messages_recovery_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.messages_recovery_summary, messages.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onOpenAutomationSetup,
                    modifier = Modifier.testTag(MessagesTestTags.FAILED_RECOVERY_OPEN_SETUP),
                ) {
                    Text(stringResource(R.string.messages_recovery_open_setup))
                }
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.messages_recovery_retry_hint),
                style = MaterialTheme.typography.labelSmall,
                color = RelateOnSurfaceVariant,
            )
        }
    }
}

private fun MessageReadiness.requiresContactOrChannelFix(): Boolean = when (this) {
    MessageReadiness.CONTACT_MISSING,
    MessageReadiness.CHANNEL_DISABLED,
    MessageReadiness.MISSING_PHONE,
    MessageReadiness.MISSING_EMAIL,
    MessageReadiness.EMAIL_SETUP_MISSING -> true
    MessageReadiness.READY_FOR_REVIEW,
    MessageReadiness.APPROVED_SCHEDULED,
    MessageReadiness.SENDING_NOW,
    MessageReadiness.FAILED_CHECK_SETUP -> false
}

@Composable
private fun PendingMessageCard(
    item: PendingMessageItem,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    isApproving: Boolean,
    selected: Boolean,
    onToggleSelection: (String) -> Unit,
    showApproveAction: Boolean,
    modifier: Modifier = Modifier,
) {
    val eventType = OccasionType.fromRaw(item.eventType)
    val eventTypeColor = when (eventType) {
        OccasionType.BIRTHDAY -> RelatePrimary
        OccasionType.ANNIVERSARY -> RelateTertiary
        OccasionType.WORK_ANNIVERSARY -> RelateSecondary
        else -> RelateOnSurfaceVariant
    }

    val eventIcon = when (eventType) {
        OccasionType.BIRTHDAY -> Icons.Filled.Cake
        OccasionType.ANNIVERSARY -> Icons.Filled.Favorite
        OccasionType.WORK_ANNIVERSARY -> Icons.Filled.Work
        else -> Icons.Filled.Info
    }

    val messageChannel = item.channel
    val channelIcon = when (messageChannel) {
        MessageChannel.WHATSAPP -> Icons.Filled.Phone
        MessageChannel.EMAIL -> Icons.Filled.Email
        MessageChannel.SMS,
        MessageChannel.UNKNOWN -> Icons.AutoMirrored.Filled.Message
    }
    val channelText = channelLabel(item.channel.raw)

    val previewText = remember(item.reviewPreviewText) {
        val raw = item.reviewPreviewText
        if (raw.length > 80) raw.take(80) + "..." else raw
    }

    RelateGlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection(item.id) },
                    modifier = Modifier.testTag(MessagesTestTags.SELECT_PREFIX + item.id),
                    colors = CheckboxDefaults.colors(checkedColor = RelatePrimary),
                )
                if (item.contactAvatarUrl != null) {
                    AsyncImage(
                        model = item.contactAvatarUrl,
                        contentDescription = stringResource(R.string.avatar),
                        modifier = Modifier
                            .size(RelateSize.avatar)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(RelateSize.avatar)
                            .clip(CircleShape)
                            .background(RelateSurfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.contactName.take(1).uppercase(),
                            color = RelateOnBackground,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(RelateSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.contactName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(RelateSpacing.xxs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
                    ) {
                        // Event type badge
                        Surface(
                            color = eventTypeColor.copy(alpha = RelateAlpha.feedbackContainer),
                            shape = RoundedCornerShape(RelateRadius.sm),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = RelateSpacing.xs, vertical = RelateSpacing.xxs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = eventIcon,
                                    contentDescription = null,
                                    tint = eventTypeColor,
                                    modifier = Modifier.size(RelateSize.iconXs),
                                )
                                Spacer(modifier = Modifier.width(RelateSpacing.xs))
                                Text(
                                    text = item.eventType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = eventTypeColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        // Channel badge
                        Surface(
                            color = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                            shape = RoundedCornerShape(RelateRadius.sm),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = RelateSpacing.xs, vertical = RelateSpacing.xxs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = channelIcon,
                                    contentDescription = null,
                                    tint = RelateOnSurfaceVariant,
                                    modifier = Modifier.size(RelateSize.iconXs),
                                )
                                Spacer(modifier = Modifier.width(RelateSpacing.xs))
                                Text(
                                    text = channelText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RelateOnSurfaceVariant,
                                    modifier = Modifier.testTag(MessagesTestTags.CHANNEL_PREFIX + item.id),
                                )
                            }
                        }
                    }
                }

                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                Text(
                    text = dateFormat.format(Date(item.scheduledForMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                    modifier = Modifier.align(Alignment.Top),
                )
            }

            Spacer(modifier = Modifier.height(RelateSpacing.md))

            Text(
                text = previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = RelateOnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(RelateSpacing.md))
            MessageReadinessBadge(
                readiness = item.readiness,
                modifier = Modifier.testTag(MessagesTestTags.READINESS_PREFIX + item.id),
            )

            Spacer(modifier = Modifier.height(RelateSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Approval Mode and Smart Approve countdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RelateSpacing.xs)
                ) {
                    val approvalMode = item.approvalMode
                    val modeColor = approvalModeColor(approvalMode)
                    Text(
                        text = approvalModeLabel(approvalMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = modeColor,
                        fontWeight = FontWeight.Bold,
                    )

                    if (approvalMode == ApprovalMode.SMART_APPROVE) {
                        val timeDiff = item.scheduledForMs - System.currentTimeMillis()
                        val minutesLeft = (timeDiff / (1000 * 60)).toInt()
                        if (minutesLeft in 0..30) {
                            Text(
                                text = stringResource(R.string.messages_auto_sends_minutes, minutesLeft),
                                style = MaterialTheme.typography.labelSmall,
                                color = RelateError,
                                fontWeight = FontWeight.Bold,
                            )
                        } else if (minutesLeft > 30) {
                            Text(
                                text = stringResource(R.string.messages_hours_left, minutesLeft / 60),
                                style = MaterialTheme.typography.labelSmall,
                                color = RelateOnSurfaceVariant,
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                ) {
                    OutlinedButton(
                        onClick = { onReject(item.id) },
                        contentPadding = PaddingValues(horizontal = RelateSpacing.md, vertical = RelateSpacing.xs),
                        modifier = Modifier
                            .heightIn(min = RelateSize.compactButtonHeight)
                            .testTag(MessagesTestTags.PENDING_REJECT_PREFIX + item.id),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        shape = RoundedCornerShape(RelateRadius.control),
                        border = BorderStroke(
                            width = RelateSize.outlineStroke,
                            brush = SolidColor(MaterialTheme.colorScheme.error.copy(alpha = RelateAlpha.outline)),
                        )
                    ) {
                        Text(stringResource(R.string.reject), style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = { onEdit(item.contactId, item.id) },
                        contentPadding = PaddingValues(horizontal = RelateSpacing.md, vertical = RelateSpacing.xs),
                        modifier = Modifier
                            .heightIn(min = RelateSize.compactButtonHeight)
                            .testTag(MessagesTestTags.PENDING_EDIT_PREFIX + item.id),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RelatePrimary,
                        ),
                        shape = RoundedCornerShape(RelateRadius.control),
                        border = BorderStroke(
                            width = RelateSize.outlineStroke,
                            brush = SolidColor(RelatePrimary.copy(alpha = RelateAlpha.outline)),
                        )
                    ) {
                        Text(stringResource(R.string.edit_contact), style = MaterialTheme.typography.labelSmall)
                    }

                    if (showApproveAction) {
                        Button(
                            onClick = { onApprove(item.id) },
                            contentPadding = PaddingValues(horizontal = RelateSpacing.md, vertical = RelateSpacing.xs),
                            modifier = Modifier
                                .heightIn(min = RelateSize.compactButtonHeight)
                                .testTag(MessagesTestTags.PENDING_APPROVE_PREFIX + item.id),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RelatePrimary,
                                contentColor = RelateOnPrimary,
                            ),
                            shape = RoundedCornerShape(RelateRadius.control),
                            enabled = !isApproving,
                        ) {
                            if (isApproving) {
                                CircularProgressIndicator(
                                    color = RelateOnPrimary,
                                    modifier = Modifier.size(RelateSize.iconSm),
                                    strokeWidth = RelateSpacing.xxs,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.approve),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SentMessageCard(
    item: SentMessageItem,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.contactAvatarUrl != null) {
                AsyncImage(
                    model = item.contactAvatarUrl,
                    contentDescription = stringResource(R.string.avatar),
                    modifier = Modifier
                        .size(RelateSize.avatar)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(RelateSize.avatar)
                        .clip(CircleShape)
                        .background(RelateSurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.contactName.take(1).uppercase(),
                        color = RelateOnBackground,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(modifier = Modifier.width(RelateSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = item.contactName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                    Text(
                        text = dateFormat.format(Date(item.sentAtMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = RelateSuccess,
                        modifier = Modifier.size(RelateSize.iconSm),
                    )
                    Spacer(modifier = Modifier.width(RelateSpacing.xs))
                    Text(
                        text = item.messageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RelateOnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(RelateSpacing.xxs))
                Text(
                    text = stringResource(R.string.messages_channel_format, channelLabel(item.channel.raw)),
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant.copy(alpha = RelateAlpha.subtle),
                    modifier = Modifier.testTag(MessagesTestTags.CHANNEL_PREFIX + item.id),
                )
            }
        }
    }
}

@Composable
private fun FailedMessageCard(
    item: PendingMessageItem,
    onRetry: (String) -> Unit,
    isRetrying: Boolean,
    selected: Boolean,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    RelateGlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection(item.id) },
                    modifier = Modifier.testTag(MessagesTestTags.SELECT_PREFIX + item.id),
                    colors = CheckboxDefaults.colors(checkedColor = RelatePrimary),
                )
                if (item.contactAvatarUrl != null) {
                    AsyncImage(
                        model = item.contactAvatarUrl,
                        contentDescription = stringResource(R.string.avatar),
                        modifier = Modifier
                            .size(RelateSize.avatar)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(RelateSize.avatar)
                            .clip(CircleShape)
                            .background(RelateSurfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.contactName.take(1).uppercase(),
                            color = RelateOnBackground,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(RelateSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.contactName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(RelateSpacing.xxs))
                    Text(
                        text = stringResource(R.string.messages_scheduled_format, dateFormat.format(Date(item.scheduledForMs))),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = stringResource(R.string.messages_status_failed),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(RelateSize.iconLg)
                )
            }
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Text(
                text = item.messageText,
                style = MaterialTheme.typography.bodyMedium,
                color = RelateOnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(RelateSpacing.md))
            MessageReadinessBadge(
                readiness = item.readiness,
                modifier = Modifier.testTag(MessagesTestTags.READINESS_PREFIX + item.id),
            )

            Spacer(modifier = Modifier.height(RelateSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { onRetry(item.id) },
                    contentPadding = PaddingValues(horizontal = RelateSpacing.lg, vertical = RelateSpacing.xs),
                    modifier = Modifier
                        .heightIn(min = RelateSize.compactButtonHeight)
                        .testTag(MessagesTestTags.FAILED_RETRY_PREFIX + item.id),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RelatePrimary,
                        contentColor = RelateOnPrimary,
                    ),
                    shape = RoundedCornerShape(RelateRadius.control),
                    enabled = !isRetrying,
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            color = RelateOnPrimary,
                            modifier = Modifier.size(RelateSize.iconSm),
                            strokeWidth = RelateSpacing.xxs,
                        )
                    } else {
                        Text(
                            stringResource(R.string.messages_retry_send),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun ApprovedMessagesList(
    messages: List<PendingMessageItem>,
    emptyText: String,
    onRevoke: (String) -> Unit,
    onReject: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    revokingMessageId: String?,
    selectedMessageIds: Set<String>,
    onToggleSelection: (String) -> Unit,
) {
    if (messages.isEmpty()) {
        EmptyState(message = emptyText)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = RelateSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md)
        ) {
            items(messages, key = { it.id }) { item ->
                ApprovedMessageCard(
                    item = item,
                    onRevoke = onRevoke,
                    onReject = onReject,
                    onEdit = onEdit,
                    isRevoking = revokingMessageId == item.id,
                    selected = item.id in selectedMessageIds,
                    onToggleSelection = onToggleSelection,
                    modifier = Modifier.testTag(MessagesTestTags.APPROVED_CARD_PREFIX + item.id),
                )
            }
        }
    }
}

@Composable
private fun ApprovedMessageCard(
    item: PendingMessageItem,
    onRevoke: (String) -> Unit,
    onReject: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    isRevoking: Boolean,
    selected: Boolean,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    RelateGlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection(item.id) },
                    modifier = Modifier.testTag(MessagesTestTags.SELECT_PREFIX + item.id),
                    colors = CheckboxDefaults.colors(checkedColor = RelatePrimary),
                )
                if (item.contactAvatarUrl != null) {
                    AsyncImage(
                        model = item.contactAvatarUrl,
                        contentDescription = stringResource(R.string.avatar),
                        modifier = Modifier
                            .size(RelateSize.avatar)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(RelateSize.avatar)
                            .clip(CircleShape)
                            .background(RelateSurfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.contactName.take(1).uppercase(),
                            color = RelateOnBackground,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(RelateSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.contactName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(RelateSpacing.xxs))
                    Text(
                        text = stringResource(R.string.messages_scheduled_format, dateFormat.format(Date(item.scheduledForMs))),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.messages_status_approved),
                    tint = RelatePrimary,
                    modifier = Modifier.size(RelateSize.iconLg)
                )
            }
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Text(
                text = item.messageText,
                style = MaterialTheme.typography.bodyMedium,
                color = RelateOnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(RelateSpacing.md))
            MessageReadinessBadge(
                readiness = item.readiness,
                modifier = Modifier.testTag(MessagesTestTags.READINESS_PREFIX + item.id),
            )

            Spacer(modifier = Modifier.height(RelateSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                ) {
                    OutlinedButton(
                        onClick = { onReject(item.id) },
                        contentPadding = PaddingValues(horizontal = RelateSpacing.md, vertical = RelateSpacing.xs),
                        modifier = Modifier
                            .heightIn(min = RelateSize.compactButtonHeight)
                            .testTag(MessagesTestTags.APPROVED_REJECT_PREFIX + item.id),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        shape = RoundedCornerShape(RelateRadius.control),
                        border = BorderStroke(
                            width = RelateSize.outlineStroke,
                            brush = SolidColor(MaterialTheme.colorScheme.error.copy(alpha = RelateAlpha.outline)),
                        )
                    ) {
                        Text(stringResource(R.string.reject), style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = { onEdit(item.contactId, item.id) },
                        contentPadding = PaddingValues(horizontal = RelateSpacing.md, vertical = RelateSpacing.xs),
                        modifier = Modifier
                            .heightIn(min = RelateSize.compactButtonHeight)
                            .testTag(MessagesTestTags.APPROVED_EDIT_PREFIX + item.id),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RelatePrimary,
                        ),
                        shape = RoundedCornerShape(RelateRadius.control),
                        border = BorderStroke(
                            width = RelateSize.outlineStroke,
                            brush = SolidColor(RelatePrimary.copy(alpha = RelateAlpha.outline)),
                        )
                    ) {
                        Text(stringResource(R.string.edit_contact), style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = { onRevoke(item.id) },
                        contentPadding = PaddingValues(horizontal = RelateSpacing.md, vertical = RelateSpacing.xs),
                        modifier = Modifier
                            .heightIn(min = RelateSize.compactButtonHeight)
                            .testTag(MessagesTestTags.APPROVED_REVOKE_PREFIX + item.id),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = RelateOnSurfaceVariant,
                        ),
                        shape = RoundedCornerShape(RelateRadius.control),
                        enabled = !isRevoking,
                    ) {
                        if (isRevoking) {
                            CircularProgressIndicator(
                                color = RelateOnSurfaceVariant,
                                modifier = Modifier.size(RelateSize.iconSm),
                                strokeWidth = RelateSpacing.xxs,
                            )
                        } else {
                            Text(
                                stringResource(R.string.messages_revoke),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun approvalModeLabel(approvalMode: ApprovalMode): String = when (approvalMode) {
    ApprovalMode.FULLY_AUTO -> stringResource(R.string.automation_mode_fully_auto)
    ApprovalMode.SMART_APPROVE -> stringResource(R.string.automation_mode_smart_approve_default)
    ApprovalMode.VIP_APPROVE -> stringResource(R.string.automation_mode_vip_approve)
    ApprovalMode.ALWAYS_ASK -> stringResource(R.string.automation_mode_always_ask)
    ApprovalMode.DEFAULT,
    ApprovalMode.UNKNOWN -> stringResource(R.string.automation_mode_default)
}

private fun approvalModeColor(approvalMode: ApprovalMode): Color = when (approvalMode) {
    ApprovalMode.FULLY_AUTO -> RelateSuccess
    ApprovalMode.SMART_APPROVE -> RelateWarning
    ApprovalMode.VIP_APPROVE -> RelateError
    ApprovalMode.ALWAYS_ASK -> RelateOnSurfaceVariant
    ApprovalMode.DEFAULT,
    ApprovalMode.UNKNOWN -> RelateOnSurfaceVariant
}

@Composable
private fun MessageReadinessBadge(
    readiness: MessageReadiness,
    modifier: Modifier = Modifier,
) {
    val label = readiness.label()
    val color = when (readiness) {
        MessageReadiness.READY_FOR_REVIEW,
        MessageReadiness.APPROVED_SCHEDULED,
        MessageReadiness.SENDING_NOW -> RelateSuccess
        MessageReadiness.FAILED_CHECK_SETUP -> RelateWarning
        MessageReadiness.CONTACT_MISSING,
        MessageReadiness.CHANNEL_DISABLED,
        MessageReadiness.MISSING_PHONE,
        MessageReadiness.MISSING_EMAIL,
        MessageReadiness.EMAIL_SETUP_MISSING -> MaterialTheme.colorScheme.error
    }
    val icon = when (readiness) {
        MessageReadiness.READY_FOR_REVIEW,
        MessageReadiness.APPROVED_SCHEDULED,
        MessageReadiness.SENDING_NOW -> Icons.Filled.CheckCircle
        MessageReadiness.FAILED_CHECK_SETUP -> Icons.Filled.Warning
        MessageReadiness.CONTACT_MISSING,
        MessageReadiness.CHANNEL_DISABLED,
        MessageReadiness.MISSING_PHONE,
        MessageReadiness.MISSING_EMAIL,
        MessageReadiness.EMAIL_SETUP_MISSING -> Icons.Filled.Error
    }

    Surface(
        modifier = modifier,
        color = color.copy(alpha = RelateAlpha.feedbackContainer),
        shape = RoundedCornerShape(RelateRadius.md),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RelateSpacing.sm, vertical = RelateSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(RelateSize.iconXs),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MessageReadiness.label(): String = when (this) {
    MessageReadiness.READY_FOR_REVIEW -> stringResource(R.string.messages_readiness_ready_review)
    MessageReadiness.APPROVED_SCHEDULED -> stringResource(R.string.messages_readiness_approved_scheduled)
    MessageReadiness.SENDING_NOW -> stringResource(R.string.messages_readiness_sending_now)
    MessageReadiness.CONTACT_MISSING -> stringResource(R.string.messages_readiness_contact_missing)
    MessageReadiness.CHANNEL_DISABLED -> stringResource(R.string.messages_readiness_channel_disabled)
    MessageReadiness.MISSING_PHONE -> stringResource(R.string.messages_readiness_missing_phone)
    MessageReadiness.MISSING_EMAIL -> stringResource(R.string.messages_readiness_missing_email)
    MessageReadiness.EMAIL_SETUP_MISSING -> stringResource(R.string.messages_readiness_email_setup_missing)
    MessageReadiness.FAILED_CHECK_SETUP -> stringResource(R.string.messages_readiness_failed_check_setup)
}

@Composable
private fun MessageChannelFilter.label(): String = when (this) {
    MessageChannelFilter.ALL -> stringResource(R.string.filter_all)
    MessageChannelFilter.SMS -> stringResource(R.string.channel_sms)
    MessageChannelFilter.WHATSAPP -> stringResource(R.string.channel_whatsapp)
    MessageChannelFilter.EMAIL -> stringResource(R.string.channel_email)
}

@Composable
private fun channelLabel(channel: String): String = when (MessageChannel.fromRaw(channel)) {
    MessageChannel.SMS -> stringResource(R.string.channel_sms)
    MessageChannel.WHATSAPP -> stringResource(R.string.channel_whatsapp)
    MessageChannel.EMAIL -> stringResource(R.string.channel_email)
    MessageChannel.UNKNOWN -> channel
}

@Composable
private fun MessageSort.label(): String = when (this) {
    MessageSort.SCHEDULED_ASC -> stringResource(R.string.message_sort_oldest)
    MessageSort.SCHEDULED_DESC -> stringResource(R.string.message_sort_newest)
    MessageSort.CONTACT_ASC -> stringResource(R.string.message_sort_contact)
}
