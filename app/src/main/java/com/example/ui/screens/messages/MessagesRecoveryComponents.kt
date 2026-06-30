package com.example.ui.screens.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.MessageReadiness
import com.example.ui.viewmodel.MessageChannelFilter
import com.example.ui.viewmodel.PendingMessageItem

@Composable
internal fun ChannelVerificationAssistant(
    channelFilter: MessageChannelFilter,
    modifier: Modifier = Modifier,
) {
    val channelName = when (channelFilter) {
        MessageChannelFilter.SMS -> stringResource(R.string.channel_sms)
        MessageChannelFilter.WHATSAPP -> stringResource(R.string.channel_whatsapp)
        MessageChannelFilter.EMAIL -> stringResource(R.string.channel_email)
        MessageChannelFilter.ALL -> stringResource(R.string.filter_all)
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
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(RelateSize.iconMd),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.messages_verification_title, channelName),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.messages_verification_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.messages_verification_detail, channelName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    tint = MaterialTheme.colorScheme.primary,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.messages_recovery_retry_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
