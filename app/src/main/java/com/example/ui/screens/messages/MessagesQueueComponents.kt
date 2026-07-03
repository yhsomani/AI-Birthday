package com.example.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipReadinessReason
import com.example.domain.readiness.RelationshipReadinessState
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.occasion.OccasionType
import com.example.ui.viewmodel.MessageActionRoute

@Composable
internal fun <T> MessageQueueList(
    queueItems: List<T>,
    emptyText: String,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    trailingContent: LazyListScope.() -> Unit = {},
    itemContent: @Composable (T) -> Unit,
) {
    if (queueItems.isEmpty()) {
        EmptyState(message = emptyText)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = RelateSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            items(queueItems, key = key) { item ->
                itemContent(item)
            }
            trailingContent()
        }
    }
}

@Composable
internal fun MessageSelectionCheckbox(
    selected: Boolean,
    onToggleSelection: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Checkbox(
        checked = selected,
        onCheckedChange = { onToggleSelection() },
        modifier = modifier.testTag(testTag),
        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
    )
}

@Composable
internal fun MessageContactAvatar(
    contactName: String,
    contactAvatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    if (contactAvatarUrl != null) {
        AsyncImage(
            model = contactAvatarUrl,
            contentDescription = stringResource(R.string.avatar),
            modifier = modifier
                .size(RelateSize.avatar)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .size(RelateSize.avatar)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contactName.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun MessageEventTypeBadge(
    eventTypeRaw: String,
    modifier: Modifier = Modifier,
) {
    val eventType = OccasionType.fromRaw(eventTypeRaw)
    val eventTypeColor = when (eventType) {
        OccasionType.BIRTHDAY -> MaterialTheme.colorScheme.primary
        OccasionType.ANNIVERSARY -> MaterialTheme.colorScheme.tertiary
        OccasionType.WORK_ANNIVERSARY,
        OccasionType.GRADUATION -> MaterialTheme.colorScheme.secondary
        OccasionType.HOLIDAY,
        OccasionType.FOLLOW_UP -> MaterialTheme.colorScheme.tertiary
        OccasionType.REVIVAL -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val eventIcon = when (eventType) {
        OccasionType.BIRTHDAY -> Icons.Filled.Cake
        OccasionType.ANNIVERSARY -> Icons.Filled.Favorite
        OccasionType.WORK_ANNIVERSARY -> Icons.Filled.Work
        OccasionType.GRADUATION,
        OccasionType.HOLIDAY,
        OccasionType.REVIVAL,
        OccasionType.FOLLOW_UP,
        OccasionType.CUSTOM -> Icons.Filled.CalendarMonth
        OccasionType.UNKNOWN -> Icons.Filled.Info
    }

    Surface(
        modifier = modifier,
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
                text = eventTypeLabel(eventType),
                style = MaterialTheme.typography.labelSmall,
                color = eventTypeColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun eventTypeLabel(eventType: OccasionType): String {
    return when (eventType) {
        OccasionType.BIRTHDAY -> stringResource(R.string.event_type_birthday)
        OccasionType.ANNIVERSARY -> stringResource(R.string.event_type_anniversary)
        OccasionType.WORK_ANNIVERSARY -> stringResource(R.string.event_type_work_anniversary)
        OccasionType.GRADUATION -> stringResource(R.string.event_type_graduation)
        OccasionType.HOLIDAY -> stringResource(R.string.event_type_holiday)
        OccasionType.REVIVAL -> stringResource(R.string.event_type_revival)
        OccasionType.FOLLOW_UP -> stringResource(R.string.event_type_follow_up)
        OccasionType.CUSTOM -> stringResource(R.string.event_type_custom)
        OccasionType.UNKNOWN -> OccasionType.UNKNOWN.raw
    }
}

@Composable
internal fun MessageChannelBadge(
    channelRaw: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val messageChannel = MessageChannel.fromRaw(channelRaw)
    val channelIcon = when (messageChannel) {
        MessageChannel.WHATSAPP -> Icons.Filled.Phone
        MessageChannel.EMAIL -> Icons.Filled.Email
        MessageChannel.SMS,
        MessageChannel.UNKNOWN -> Icons.AutoMirrored.Filled.Message
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
        shape = RoundedCornerShape(RelateRadius.sm),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RelateSpacing.xs, vertical = RelateSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = channelIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(RelateSize.iconXs),
            )
            Spacer(modifier = Modifier.width(RelateSpacing.xs))
            Text(
                text = channelLabel(channelRaw),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(testTag),
            )
        }
    }
}

@Composable
internal fun MessageSentChannelLabel(
    channelRaw: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.messages_channel_format, channelLabel(channelRaw)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = RelateAlpha.subtle),
        modifier = modifier,
    )
}

@Composable
internal fun MessageRejectActionButton(
    onClick: () -> Unit,
    testTag: String,
) {
    MessageOutlinedActionButton(
        label = stringResource(R.string.reject),
        contentColor = MaterialTheme.colorScheme.error,
        onClick = onClick,
        testTag = testTag,
    )
}

@Composable
internal fun MessageEditActionButton(
    onClick: () -> Unit,
    testTag: String,
) {
    MessageOutlinedActionButton(
        label = stringResource(R.string.edit_contact),
        contentColor = MaterialTheme.colorScheme.primary,
        onClick = onClick,
        testTag = testTag,
    )
}

@Composable
internal fun MessagePrimaryActionButton(
    actionRoute: MessageActionRoute,
    onClick: () -> Unit,
    testTag: String,
) {
    val label = when (actionRoute) {
        MessageActionRoute.CONTACT,
        MessageActionRoute.WISH -> stringResource(R.string.edit_contact)
        MessageActionRoute.AUTOMATION_SETUP -> stringResource(R.string.messages_recovery_open_setup)
        MessageActionRoute.NONE -> stringResource(R.string.edit_contact)
    }
    MessageOutlinedActionButton(
        label = label,
        contentColor = MaterialTheme.colorScheme.primary,
        onClick = onClick,
        testTag = testTag,
    )
}

@Composable
internal fun MessageApproveActionButton(
    isApproving: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    MessageProgressActionButton(
        label = stringResource(R.string.approve),
        inProgress = isApproving,
        onClick = onClick,
        testTag = testTag,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
internal fun MessageRetryActionButton(
    isRetrying: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    MessageProgressActionButton(
        label = stringResource(R.string.messages_retry_send),
        inProgress = isRetrying,
        onClick = onClick,
        testTag = testTag,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        horizontalPadding = RelateSpacing.lg,
    )
}

@Composable
internal fun MessageRevokeActionButton(
    isRevoking: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    MessageProgressActionButton(
        label = stringResource(R.string.messages_revoke),
        inProgress = isRevoking,
        onClick = onClick,
        testTag = testTag,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun MessageApprovalModeStatus(
    approvalMode: ApprovalMode,
    scheduledForMs: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
    ) {
        val modeColor = approvalModeColor(approvalMode)
        Text(
            text = approvalModeLabel(approvalMode),
            style = MaterialTheme.typography.labelSmall,
            color = modeColor,
            fontWeight = FontWeight.Bold,
        )

        if (approvalMode == ApprovalMode.SMART_APPROVE) {
            val timeDiff = scheduledForMs - System.currentTimeMillis()
            val minutesLeft = (timeDiff / (1000 * 60)).toInt()
            if (minutesLeft in 0..30) {
                Text(
                    text = stringResource(R.string.messages_auto_sends_minutes, minutesLeft),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            } else if (minutesLeft > 30) {
                Text(
                    text = stringResource(R.string.messages_hours_left, minutesLeft / 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun MessageReadinessBadge(
    readiness: RelationshipActionReadiness,
    modifier: Modifier = Modifier,
) {
    val label = readiness.label()
    val color = when (readiness.state) {
        RelationshipReadinessState.READY,
        RelationshipReadinessState.NEEDS_REVIEW,
        RelationshipReadinessState.IN_PROGRESS -> MaterialTheme.relateSemanticColors.success
        RelationshipReadinessState.WAITING,
        RelationshipReadinessState.WARNING -> MaterialTheme.relateSemanticColors.warning
        RelationshipReadinessState.ACTION_REQUIRED -> MaterialTheme.colorScheme.error
    }
    val icon = when (readiness.state) {
        RelationshipReadinessState.READY,
        RelationshipReadinessState.NEEDS_REVIEW,
        RelationshipReadinessState.IN_PROGRESS -> Icons.Filled.CheckCircle
        RelationshipReadinessState.WAITING,
        RelationshipReadinessState.WARNING -> Icons.Filled.Warning
        RelationshipReadinessState.ACTION_REQUIRED -> Icons.Filled.Error
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
internal fun MessageDraftSourceBadge(
    isUsingFallback: Boolean,
    qualityScore: Int,
    modifier: Modifier = Modifier,
) {
    val normalizedScore = qualityScore.coerceIn(0, 100)
    val isLowQuality = normalizedScore in 1..69
    val color = if (isUsingFallback || isLowQuality) {
        MaterialTheme.relateSemanticColors.warning
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = if (isUsingFallback || isLowQuality) Icons.Filled.Warning else Icons.Filled.Info
    val label = when {
        isUsingFallback -> stringResource(R.string.messages_draft_source_fallback)
        normalizedScore > 0 -> stringResource(R.string.messages_draft_source_ai_score, normalizedScore)
        else -> stringResource(R.string.messages_draft_source_ai)
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
private fun MessageOutlinedActionButton(
    label: String,
    contentColor: Color,
    onClick: () -> Unit,
    testTag: String,
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = RelateSpacing.md, vertical = RelateSpacing.xs),
        modifier = Modifier
            .heightIn(min = RelateSize.compactButtonHeight)
            .testTag(testTag),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
        ),
        shape = RoundedCornerShape(RelateRadius.control),
        border = BorderStroke(
            width = RelateSize.outlineStroke,
            brush = SolidColor(contentColor.copy(alpha = RelateAlpha.outline)),
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MessageProgressActionButton(
    label: String,
    inProgress: Boolean,
    onClick: () -> Unit,
    testTag: String,
    containerColor: Color,
    contentColor: Color,
    horizontalPadding: Dp = RelateSpacing.md,
) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = RelateSpacing.xs),
        modifier = Modifier
            .heightIn(min = RelateSize.compactButtonHeight)
            .testTag(testTag),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = RoundedCornerShape(RelateRadius.control),
        enabled = !inProgress,
    ) {
        if (inProgress) {
            CircularProgressIndicator(
                color = contentColor,
                modifier = Modifier.size(RelateSize.iconSm),
                strokeWidth = RelateSpacing.xxs,
            )
        } else {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
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

@Composable
private fun approvalModeColor(approvalMode: ApprovalMode): Color = when (approvalMode) {
    ApprovalMode.FULLY_AUTO -> MaterialTheme.relateSemanticColors.success
    ApprovalMode.SMART_APPROVE -> MaterialTheme.relateSemanticColors.warning
    ApprovalMode.VIP_APPROVE -> MaterialTheme.colorScheme.error
    ApprovalMode.ALWAYS_ASK -> MaterialTheme.colorScheme.onSurfaceVariant
    ApprovalMode.DEFAULT,
    ApprovalMode.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun RelationshipActionReadiness.label(): String = when (primaryReason) {
    RelationshipReadinessReason.MESSAGE_NEEDS_REVIEW -> stringResource(R.string.messages_readiness_ready_review)
    RelationshipReadinessReason.APPROVED_READY,
    RelationshipReadinessReason.READY -> stringResource(R.string.messages_readiness_approved_scheduled)
    RelationshipReadinessReason.WAITING_FOR_SCHEDULE ->
        stringResource(R.string.messages_readiness_waiting_schedule)
    RelationshipReadinessReason.WAITING_FOR_ALLOWED_WINDOW ->
        stringResource(R.string.messages_readiness_paused_window)
    RelationshipReadinessReason.SENDING -> stringResource(R.string.messages_readiness_sending_now)
    RelationshipReadinessReason.CONTACT_MISSING -> stringResource(R.string.messages_readiness_contact_missing)
    RelationshipReadinessReason.CHANNEL_DISABLED -> stringResource(R.string.messages_readiness_channel_disabled)
    RelationshipReadinessReason.MISSING_PHONE -> stringResource(R.string.messages_readiness_missing_phone)
    RelationshipReadinessReason.MISSING_EMAIL -> stringResource(R.string.messages_readiness_missing_email)
    RelationshipReadinessReason.EMAIL_SETUP_MISSING -> stringResource(R.string.messages_readiness_email_setup_missing)
    RelationshipReadinessReason.FAILED_CHECK_SETUP,
    RelationshipReadinessReason.SETUP_ACTION_REQUIRED,
    RelationshipReadinessReason.SETUP_WARNING -> stringResource(R.string.messages_readiness_failed_check_setup)
    RelationshipReadinessReason.DRAFT_READY,
    RelationshipReadinessReason.DRAFT_EDITED_READY,
    RelationshipReadinessReason.DRAFT_TOO_SHORT,
    RelationshipReadinessReason.DRAFT_BLANK,
    RelationshipReadinessReason.CONTACT_SYNC_FAILED,
    RelationshipReadinessReason.CONTACTS_MISSING,
    RelationshipReadinessReason.AI_ACCESS_MISSING,
    RelationshipReadinessReason.AI_GENERATION_DISABLED,
    RelationshipReadinessReason.PENDING_MESSAGES,
    RelationshipReadinessReason.BACKUP_MISSING,
    RelationshipReadinessReason.BACKUP_STALE,
    RelationshipReadinessReason.RELATIONSHIP_HEALTH_LOW -> stringResource(R.string.messages_readiness_ready_review)
}

@Composable
private fun channelLabel(channelRaw: String): String = when (MessageChannel.fromRaw(channelRaw)) {
    MessageChannel.SMS -> stringResource(R.string.channel_sms)
    MessageChannel.WHATSAPP -> stringResource(R.string.channel_whatsapp)
    MessageChannel.EMAIL -> stringResource(R.string.channel_email)
    MessageChannel.UNKNOWN -> channelRaw
}
