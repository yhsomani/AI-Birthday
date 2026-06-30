package com.example.ui.screens.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing

@Composable
internal fun BulkActionBar(
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
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
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.messages_clear_selection),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
