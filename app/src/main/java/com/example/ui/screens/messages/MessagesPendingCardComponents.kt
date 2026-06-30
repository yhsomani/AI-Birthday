package com.example.ui.screens.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.MessageReadiness

@Composable
internal fun MessagePendingMetadataRow(
    eventTypeRaw: String,
    channelRaw: String,
    channelTestTag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
    ) {
        MessageEventTypeBadge(eventTypeRaw = eventTypeRaw)
        MessageChannelBadge(
            channelRaw = channelRaw,
            testTag = channelTestTag,
        )
    }
}

@Composable
internal fun MessagePendingCardBody(
    reviewPreviewText: String,
    readiness: MessageReadiness,
    readinessTestTag: String,
) {
    val previewText = remember(reviewPreviewText) {
        if (reviewPreviewText.length > PendingPreviewMaxLength) {
            reviewPreviewText.take(PendingPreviewMaxLength) + "..."
        } else {
            reviewPreviewText
        }
    }

    Spacer(modifier = Modifier.height(RelateSpacing.md))
    MessagePreviewText(text = previewText)
    Spacer(modifier = Modifier.height(RelateSpacing.md))
    MessageReadinessBadge(
        readiness = readiness,
        modifier = Modifier.testTag(readinessTestTag),
    )
}

private const val PendingPreviewMaxLength = 80
