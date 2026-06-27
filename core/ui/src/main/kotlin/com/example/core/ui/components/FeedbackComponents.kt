package com.example.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateError
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.RelateSuccess
import com.example.core.ui.theme.RelateWarning

enum class FeedbackType { SUCCESS, ERROR, WARNING, INFO }

data class FeedbackState(
    val message: String = "",
    val type: FeedbackType = FeedbackType.INFO,
    val visible: Boolean = false,
)

@Composable
fun AdaptiveFeedbackBanner(
    state: FeedbackState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            val (icon, color) = when (state.type) {
                FeedbackType.SUCCESS -> Icons.Filled.CheckCircle to RelateSuccess
                FeedbackType.ERROR -> Icons.Filled.Error to RelateError
                FeedbackType.WARNING -> Icons.Filled.Warning to RelateWarning
                FeedbackType.INFO -> Icons.Filled.Info to RelatePrimary
            }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = RelateSpacing.screenHorizontal, vertical = RelateSpacing.xs)
                .background(
                    color.copy(alpha = RelateAlpha.feedbackContainer),
                    RoundedCornerShape(RelateRadius.control),
                )
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                modifier = Modifier.padding(RelateSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(RelateSize.iconSm),
                )
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                Text(
                    text = state.message,
                    color = color,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun StatusIndicator(
    isActive: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(vertical = RelateSpacing.xxs)
            .semantics(mergeDescendants = true) {
                stateDescription = if (isActive) "Active" else "Inactive"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(RelateSize.statusDot)
                .background(
                    if (isActive) RelateSuccess else RelateOnSurfaceVariant,
                    RoundedCornerShape(RelateRadius.xs),
                ),
        )
        Spacer(modifier = Modifier.width(RelateSize.statusDot))
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = if (isActive) RelateSuccess else RelateOnSurfaceVariant,
        )
    }
}
