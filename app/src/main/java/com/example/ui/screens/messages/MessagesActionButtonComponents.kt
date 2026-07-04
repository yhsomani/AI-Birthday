package com.example.ui.screens.messages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.example.R
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.model.ApprovalMode
import com.example.ui.viewmodel.MessageActionRoute

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
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(RelateSpacing.xs),
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
        ),
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
