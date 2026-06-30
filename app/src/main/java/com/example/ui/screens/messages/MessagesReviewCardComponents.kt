package com.example.ui.screens.messages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.MessageReadiness

@Composable
internal fun MessageFailedStatusIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.Error,
        contentDescription = stringResource(R.string.messages_status_failed),
        tint = MaterialTheme.colorScheme.error,
        modifier = modifier.size(RelateSize.iconLg),
    )
}

@Composable
internal fun MessageApprovedStatusIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = stringResource(R.string.messages_status_approved),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(RelateSize.iconLg),
    )
}

@Composable
internal fun MessageReviewCardBody(
    messageText: String,
    readiness: MessageReadiness,
    readinessTestTag: String,
) {
    Spacer(modifier = Modifier.height(RelateSpacing.sm))
    MessagePreviewText(text = messageText)
    Spacer(modifier = Modifier.height(RelateSpacing.md))
    MessageReadinessBadge(
        readiness = readiness,
        modifier = Modifier.testTag(readinessTestTag),
    )
}
