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
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.occasion.OccasionType
import com.example.ui.viewmodel.MessageReadiness

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
        OccasionType.WORK_ANNIVERSARY -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val eventIcon = when (eventType) {
        OccasionType.BIRTHDAY -> Icons.Filled.Cake
        OccasionType.ANNIVERSARY -> Icons.Filled.Favorite
        OccasionType.WORK_ANNIVERSARY -> Icons.Filled.Work
        else -> Icons.Filled.Info
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
                text = eventTypeRaw,
                style = MaterialTheme.typography.labelSmall,
                color = eventTypeColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
    readiness: MessageReadiness,
    modifier: Modifier = Modifier,
) {
    val label = readiness.label()
    val color = when (readiness) {
        MessageReadiness.READY_FOR_REVIEW,
        MessageReadiness.APPROVED_SCHEDULED,
        MessageReadiness.SENDING_NOW -> MaterialTheme.relateSemanticColors.success
        MessageReadiness.FAILED_CHECK_SETUP -> MaterialTheme.relateSemanticColors.warning
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
private fun channelLabel(channelRaw: String): String = when (MessageChannel.fromRaw(channelRaw)) {
    MessageChannel.SMS -> stringResource(R.string.channel_sms)
    MessageChannel.WHATSAPP -> stringResource(R.string.channel_whatsapp)
    MessageChannel.EMAIL -> stringResource(R.string.channel_email)
    MessageChannel.UNKNOWN -> channelRaw
}
