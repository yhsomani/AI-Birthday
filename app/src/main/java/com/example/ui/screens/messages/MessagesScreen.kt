package com.example.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.*
import com.example.ui.viewmodel.MessagesViewModel
import com.example.ui.viewmodel.PendingMessageItem
import com.example.ui.viewmodel.SentMessageItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onNavigateToWish: (String, String) -> Unit = { _, _ -> },
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val tabs = listOf(
        "Today (${state.todayMessages.size})",
        "Pending (${state.pendingMessages.size})",
        "Approved (${state.approvedMessages.size})",
        "Sent (${state.sentMessages.size})",
        "Failed (${state.failedMessages.size})"
    )

    val pagerState = rememberPagerState(pageCount = { 5 })
    var showRejectDialogForId by remember { mutableStateOf<String?>(null) }

    if (showRejectDialogForId != null) {
        AlertDialog(
            onDismissRequest = { showRejectDialogForId = null },
            title = { Text("Reject Message", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Are you sure you want to reject this message? It will not be sent.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRejectDialogForId?.let { id ->
                            viewModel.rejectMessage(id)
                        }
                        showRejectDialogForId = null
                    }
                ) {
                    Text("Reject", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialogForId = null }) {
                    Text("Cancel")
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
            text = "Messages Inbox",
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
                            emptyText = "No messages scheduled for today",
                            onApprove = { viewModel.approveMessage(it) },
                            onReject = { showRejectDialogForId = it },
                            onEdit = onNavigateToWish,
                            approvingMessageId = state.approvingMessageId,
                        )
                        1 -> PendingMessagesList(
                            messages = state.pendingMessages,
                            emptyText = "No upcoming messages",
                            onApprove = { viewModel.approveMessage(it) },
                            onReject = { showRejectDialogForId = it },
                            onEdit = onNavigateToWish,
                            approvingMessageId = state.approvingMessageId,
                        )
                        2 -> ApprovedMessagesList(
                            messages = state.approvedMessages,
                            onRevoke = { viewModel.revokeApproval(it) },
                            onReject = { showRejectDialogForId = it },
                            onEdit = onNavigateToWish,
                            revokingMessageId = state.revokingMessageId,
                        )
                        3 -> SentMessagesList(messages = state.sentMessages)
                        4 -> FailedMessagesList(
                            messages = state.failedMessages,
                            onRetry = { viewModel.retryMessage(it) },
                            retryingMessageId = state.retryingMessageId,
                        )
                    }
                }
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
                )
            }
        }
    }
}

@Composable
private fun SentMessagesList(messages: List<SentMessageItem>) {
    if (messages.isEmpty()) {
        EmptyState(message = "No sent messages yet")
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
) {
    if (messages.isEmpty()) {
        EmptyState(message = "No failed messages")
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
                if (item.contactAvatarUrl != null) {
                    AsyncImage(
                        model = item.contactAvatarUrl,
                        contentDescription = "Avatar",
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
                                text = "Auto-sends in $minutesLeft mins!",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                            )
                        } else if (minutesLeft > 30) {
                            Text(
                                text = "${minutesLeft / 60}h left",
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
                        Text("Reject", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { onEdit(message.contactId, message.eventId) },
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
                        Text("Edit", fontSize = 11.sp)
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
                            Text("Approve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    contentDescription = "Avatar",
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
                    text = "Channel: ${message.channel}",
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
) {
    val message = item.entity
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    RelateGlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.contactAvatarUrl != null) {
                    AsyncImage(
                        model = item.contactAvatarUrl,
                        contentDescription = "Avatar",
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
                        text = "Scheduled: " + dateFormat.format(Date(message.scheduledForMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "Failed",
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
                        Text("Retry Send", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
) {
    if (messages.isEmpty()) {
        EmptyState(message = "No approved messages")
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
) {
    val message = item.entity
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    RelateGlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.contactAvatarUrl != null) {
                    AsyncImage(
                        model = item.contactAvatarUrl,
                        contentDescription = "Avatar",
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
                        text = "Scheduled: " + dateFormat.format(Date(message.scheduledForMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Approved",
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
                        Text("Reject", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { onEdit(message.contactId, message.eventId) },
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
                        Text("Edit", fontSize = 11.sp)
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
                            Text("Revoke", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
