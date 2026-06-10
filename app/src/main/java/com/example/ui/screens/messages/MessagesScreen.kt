package com.example.ui.screens.messages

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.relateTextFieldColors
import com.example.core.ui.theme.*
import com.example.ui.viewmodel.MessageChannelFilter
import com.example.ui.viewmodel.MessageSort
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onNavigateToWish: (String, String) -> Unit = { _, _ -> },
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs = listOf(
        stringResource(R.string.messages_tab_today, state.todayMessages.size),
        stringResource(R.string.messages_tab_pending, state.pendingMessages.size),
        stringResource(R.string.messages_tab_approved, state.approvedMessages.size),
        stringResource(R.string.messages_tab_sent, state.sentMessages.size),
        stringResource(R.string.messages_tab_failed, state.failedMessages.size),
    )

    val pagerState = rememberPagerState(pageCount = { 5 })
    var showRejectDialogForId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    if (showRejectDialogForId != null) {
        AlertDialog(
            onDismissRequest = { showRejectDialogForId = null },
            title = { Text(stringResource(R.string.messages_reject_title), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(R.string.messages_reject_body), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRejectDialogForId?.let { id ->
                            viewModel.rejectMessage(id)
                        }
                        showRejectDialogForId = null
                    }
                ) {
                    Text(stringResource(R.string.reject), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialogForId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.messages_inbox_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = RelateDarkBackground,
            contentColor = RelatePrimary,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    height = 3.dp,
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
                    text = {
                        Text(
                            text = title,
                            color = if (pagerState.currentPage == index) RelatePrimary else RelateOnSurfaceVariant,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search)) },
            placeholder = { Text(stringResource(R.string.messages_search_placeholder)) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
            },
            singleLine = true,
            colors = relateTextFieldColors(),
            shape = RoundedCornerShape(10.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            channelFilters.forEach { filter ->
                FilterChip(
                    label = filter.label(),
                    isSelected = state.selectedChannelFilter == filter,
                    onClick = { viewModel.selectChannelFilter(filter) },
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
            messageSortOptions.forEach { sort ->
                FilterChip(
                    label = sort.label(),
                    isSelected = state.selectedSort == sort,
                    onClick = { viewModel.selectSort(sort) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (state.selectedMessageIds.isNotEmpty()) {
            BulkActionBar(
                selectedCount = state.selectedMessageIds.size,
                showApprove = pagerState.currentPage in listOf(0, 1),
                showRetry = pagerState.currentPage == 4,
                onApprove = viewModel::bulkApproveSelected,
                onRetry = viewModel::bulkRetrySelected,
                onReject = viewModel::bulkRejectSelected,
                onClear = viewModel::clearSelection,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
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
                ) { page ->
                                        when (page) {
                        0 -> PendingMessagesList(
                            messages = state.todayMessages,
                            emptyText = stringResource(R.string.messages_empty_today),
                            onApprove = { viewModel.approveMessage(it) },
                            onReject = { showRejectDialogForId = it },
                            onEdit = onNavigateToWish,
                            approvingMessageId = state.approvingMessageId,
                            selectedMessageIds = state.selectedMessageIds,
                            onToggleSelection = viewModel::toggleSelection,
                        )
                        1 -> PendingMessagesList(
                            messages = state.pendingMessages,
                            emptyText = stringResource(R.string.messages_empty_pending),
                            onApprove = { viewModel.approveMessage(it) },
                            onReject = { showRejectDialogForId = it },
                            onEdit = onNavigateToWish,
                            approvingMessageId = state.approvingMessageId,
                            selectedMessageIds = state.selectedMessageIds,
                            onToggleSelection = viewModel::toggleSelection,
                        )
                        2 -> ApprovedMessagesList(
                            messages = state.approvedMessages,
                            onRevoke = { viewModel.revokeApproval(it) },
                            onReject = { showRejectDialogForId = it },
                            onEdit = onNavigateToWish,
                            revokingMessageId = state.revokingMessageId,
                            selectedMessageIds = state.selectedMessageIds,
                            onToggleSelection = viewModel::toggleSelection,
                        )
                        3 -> SentMessagesList(messages = state.sentMessages)
                        4 -> FailedMessagesList(
                            messages = state.failedMessages,
                            onRetry = { viewModel.retryMessage(it) },
                            retryingMessageId = state.retryingMessageId,
                            selectedMessageIds = state.selectedMessageIds,
                            onToggleSelection = viewModel::toggleSelection,
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
) {
    RelateGlassCard {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary, contentColor = Color.Black),
                ) {
                    Text(stringResource(R.string.approve), fontSize = 12.sp)
                }
            }
            if (showRetry) {
                Button(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary, contentColor = Color.Black),
                ) {
                    Text(stringResource(R.string.retry), fontSize = 12.sp)
                }
            }
            TextButton(onClick = onReject) {
                Text(stringResource(R.string.reject), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
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
) {
    if (messages.isEmpty()) {
        EmptyState(message = emptyText)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.entity.id }) { item ->
                PendingMessageCard(
                    item = item,
                    onApprove = onApprove,
                    onReject = onReject,
                    onEdit = onEdit,
                    isApproving = approvingMessageId == item.entity.id,
                    selected = item.entity.id in selectedMessageIds,
                    onToggleSelection = onToggleSelection,
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
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.entity.id }) { item ->
                SentMessageCard(item = item)
            }
        }
    }
}

@Composable
private fun FailedMessagesList(
    messages: List<PendingMessageItem>,
    onRetry: (String) -> Unit,
    retryingMessageId: String?,
    selectedMessageIds: Set<String>,
    onToggleSelection: (String) -> Unit,
) {
    if (messages.isEmpty()) {
        EmptyState(message = stringResource(R.string.messages_empty_failed))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.entity.id }) { item ->
                FailedMessageCard(
                    item = item,
                    onRetry = onRetry,
                    isRetrying = retryingMessageId == item.entity.id,
                    selected = item.entity.id in selectedMessageIds,
                    onToggleSelection = onToggleSelection,
                )
            }
        }
    }
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
) {
    val message = item.entity
    val eventTypeColor = when (item.eventType) {
        "BIRTHDAY" -> Color(0xFF8B5CF6) // NeonViolet
        "ANNIVERSARY" -> Color(0xFFF43F5E) // CyberRose
        "WORK_ANNIVERSARY" -> Color(0xFF06B6D4) // ElectricCyan
        else -> Color.Gray
    }
    
    val eventIcon = when (item.eventType) {
        "BIRTHDAY" -> Icons.Filled.Cake
        "ANNIVERSARY" -> Icons.Filled.Favorite
        "WORK_ANNIVERSARY" -> Icons.Filled.Work
        else -> Icons.Filled.Info
    }

    val channelIcon = when (message.channel) {
        "WHATSAPP" -> Icons.Filled.Phone
        "EMAIL" -> Icons.Filled.Email
        else -> Icons.Filled.Message
    }

    val previewText = remember(message) {
        val raw = if (message.editedByUser) message.userEditedText ?: message.selectedVariantText else message.selectedVariantText
        if (raw.length > 80) raw.take(80) + "..." else raw
    }

    RelateGlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection(message.id) },
                    colors = CheckboxDefaults.colors(checkedColor = RelatePrimary),
                )
                if (item.contactAvatarUrl != null) {
                    AsyncImage(
                        model = item.contactAvatarUrl,
                        contentDescription = stringResource(R.string.avatar),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.contactName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Event type badge
                        Surface(
                            color = eventTypeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = eventIcon,
                                    contentDescription = null,
                                    tint = eventTypeColor,
                                    modifier = Modifier.size(10.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
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
                            color = RelateSurfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = channelIcon,
                                    contentDescription = null,
                                    tint = RelateOnSurfaceVariant,
                                    modifier = Modifier.size(10.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = message.channel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RelateOnSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                Text(
                    text = dateFormat.format(Date(message.scheduledForMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                    modifier = Modifier.align(Alignment.Top),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = RelateOnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Approval Mode and Smart Approve countdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modeColor = when (message.approvalMode) {
                        "FULLY_AUTO" -> Color(0xFF10B981)
                        "SMART_APPROVE" -> Color(0xFFFBBF24)
                        "VIP_APPROVE" -> Color(0xFFEF4444)
                        else -> Color.Gray
                    }
                    Text(
                        text = message.approvalMode,
                        style = MaterialTheme.typography.labelSmall,
                        color = modeColor,
                        fontWeight = FontWeight.Bold,
                    )
                    
                    if (message.approvalMode == "SMART_APPROVE") {
                        val timeDiff = message.scheduledForMs - System.currentTimeMillis()
                        val minutesLeft = (timeDiff / (1000 * 60)).toInt()
                        if (minutesLeft in 0..30) {
                            Text(
                                text = stringResource(R.string.messages_auto_sends_minutes, minutesLeft),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Red,
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onReject(message.id) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        )
                    ) {
                        Text(stringResource(R.string.reject), fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { onEdit(message.contactId, message.id) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RelatePrimary,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(RelatePrimary.copy(alpha = 0.5f))
                        )
                    ) {
                        Text(stringResource(R.string.edit_contact), fontSize = 11.sp)
                    }

                    Button(
                        onClick = { onApprove(message.id) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RelatePrimary,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isApproving,
                    ) {
                        if (isApproving) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.approve), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SentMessageCard(item: SentMessageItem) {
    val message = item.entity
    RelateGlassCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.contactAvatarUrl != null) {
                AsyncImage(
                    model = item.contactAvatarUrl,
                    contentDescription = stringResource(R.string.avatar),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
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
            Spacer(modifier = Modifier.width(12.dp))
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
                        text = dateFormat.format(Date(message.sentAtMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = message.messageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RelateOnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.messages_channel_format, message.channel),
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant.copy(alpha = 0.8f),
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
) {
    val message = item.entity
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    RelateGlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection(message.id) },
                    colors = CheckboxDefaults.colors(checkedColor = RelatePrimary),
                )
                if (item.contactAvatarUrl != null) {
                    AsyncImage(
                        model = item.contactAvatarUrl,
                        contentDescription = stringResource(R.string.avatar),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.contactName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.messages_scheduled_format, dateFormat.format(Date(message.scheduledForMs))),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = stringResource(R.string.messages_status_failed),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message.selectedVariantText.ifBlank { message.standardVariant },
                style = MaterialTheme.typography.bodyMedium,
                color = RelateOnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { onRetry(message.id) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RelatePrimary,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isRetrying,
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.messages_retry_send), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}



@Composable
private fun ApprovedMessagesList(
    messages: List<PendingMessageItem>,
    onRevoke: (String) -> Unit,
    onReject: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    revokingMessageId: String?,
    selectedMessageIds: Set<String>,
    onToggleSelection: (String) -> Unit,
) {
    if (messages.isEmpty()) {
        EmptyState(message = stringResource(R.string.messages_empty_approved))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.entity.id }) { item ->
                ApprovedMessageCard(
                    item = item,
                    onRevoke = onRevoke,
                    onReject = onReject,
                    onEdit = onEdit,
                    isRevoking = revokingMessageId == item.entity.id,
                    selected = item.entity.id in selectedMessageIds,
                    onToggleSelection = onToggleSelection,
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
) {
    val message = item.entity
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    RelateGlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection(message.id) },
                    colors = CheckboxDefaults.colors(checkedColor = RelatePrimary),
                )
                if (item.contactAvatarUrl != null) {
                    AsyncImage(
                        model = item.contactAvatarUrl,
                        contentDescription = stringResource(R.string.avatar),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.contactName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.messages_scheduled_format, dateFormat.format(Date(message.scheduledForMs))),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.messages_status_approved),
                    tint = RelatePrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message.selectedVariantText.ifBlank { message.standardVariant },
                style = MaterialTheme.typography.bodyMedium,
                color = RelateOnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onReject(message.id) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        )
                    ) {
                        Text(stringResource(R.string.reject), fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { onEdit(message.contactId, message.id) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RelatePrimary,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(RelatePrimary.copy(alpha = 0.5f))
                        )
                    ) {
                        Text(stringResource(R.string.edit_contact), fontSize = 11.sp)
                    }

                    Button(
                        onClick = { onRevoke(message.id) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = RelateOnSurfaceVariant,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isRevoking,
                    ) {
                        if (isRevoking) {
                            CircularProgressIndicator(
                                color = RelateOnSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.messages_revoke), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
