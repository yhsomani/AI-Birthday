package com.example.ui.screens.wish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.RelatePrimaryButton
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.ReviewNextTarget
import com.example.ui.viewmodel.WishPreviewUiState

@Composable
internal fun WishPreviewActionSection(
    state: WishPreviewUiState,
    onRegenerate: () -> Unit,
    onSendTest: () -> Unit,
    onRejectRequested: () -> Unit,
    onApproveRequested: () -> Unit,
    onReviewNext: (ReviewNextTarget) -> Unit,
) {
    Spacer(modifier = Modifier.height(RelateSpacing.lg))
    Button(
        onClick = onRegenerate,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WishPreviewTestTags.REGENERATE_BUTTON),
        enabled = !state.isRegenerating && !state.isApproving && !state.isRejecting && !state.isTestingSend,
        shape = RoundedCornerShape(RelateRadius.control),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        if (state.isRegenerating) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(RelateSize.iconSm),
                strokeWidth = RelateSpacing.xxs,
            )
        } else {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(RelateSize.iconSm),
            )
            Spacer(modifier = Modifier.width(RelateSpacing.sm))
            Text(
                text = stringResource(R.string.wish_preview_regenerate),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }

    Spacer(modifier = Modifier.height(RelateSpacing.sm))
    Button(
        onClick = onSendTest,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WishPreviewTestTags.TEST_SEND_BUTTON),
        enabled = !state.isTestingSend && !state.isRegenerating && !state.isApproving && !state.isRejecting,
        shape = RoundedCornerShape(RelateRadius.control),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        if (state.isTestingSend) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(RelateSize.iconSm),
                strokeWidth = RelateSpacing.xxs,
            )
        } else {
            Text(
                text = stringResource(R.string.wish_preview_send_test),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }

    Spacer(modifier = Modifier.height(RelateSpacing.xl))
    if (state.isApproving) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (!state.approved && !state.rejected) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Button(
                onClick = onRejectRequested,
                modifier = Modifier
                    .weight(1f)
                    .height(RelateSize.primaryButtonHeight)
                    .testTag(WishPreviewTestTags.REJECT_BUTTON),
                shape = RoundedCornerShape(RelateRadius.control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                enabled = !state.isRejecting,
            ) {
                if (state.isRejecting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(RelateSize.iconMd),
                        strokeWidth = RelateSpacing.xxs,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.wish_preview_reject),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            RelatePrimaryButton(
                text = stringResource(R.string.wish_preview_approve_schedule),
                onClick = onApproveRequested,
                modifier = Modifier
                    .weight(1f)
                    .testTag(WishPreviewTestTags.APPROVE_BUTTON),
                enabled = !state.blocksApproval,
            )
        }
    } else if (state.approved) {
        ReviewResultPanel(
            message = stringResource(R.string.wish_preview_approved),
            messageTag = WishPreviewTestTags.APPROVED_MESSAGE,
            state = state,
            onReviewNext = onReviewNext,
        )
    } else if (state.rejected) {
        ReviewResultPanel(
            message = stringResource(R.string.wish_preview_rejected),
            messageTag = WishPreviewTestTags.REJECTED_MESSAGE,
            state = state,
            onReviewNext = onReviewNext,
        )
    }
}
