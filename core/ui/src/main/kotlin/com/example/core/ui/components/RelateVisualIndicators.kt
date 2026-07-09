package com.example.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.example.core.ui.theme.RelateFraction
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.relateSemanticColors

@Composable
fun RelateAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = RelateSize.avatar,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifBlank { "?" },
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun HealthIndicatorDot(
    health: Float,
    modifier: Modifier = Modifier,
    size: Dp = RelateSize.indicatorDot,
) {
    val semanticColors = MaterialTheme.relateSemanticColors
    val color = if (health > RelateFraction.healthStrongThreshold) semanticColors.success
    else if (health > RelateFraction.healthAttentionThreshold) semanticColors.warning
    else MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun HealthBar(
    health: Float,
    modifier: Modifier = Modifier,
) {
    val semanticColors = MaterialTheme.relateSemanticColors
    val brush = Brush.horizontalGradient(
        colors = listOf(
            semanticColors.success,
            semanticColors.warning,
            MaterialTheme.colorScheme.error,
        )
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RelateSize.progressTrack)
            .clip(RoundedCornerShape(RelateRadius.xs))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(health.coerceIn(0f, 1f))
                .height(RelateSize.progressTrack)
                .clip(RoundedCornerShape(RelateRadius.xs))
                .background(brush)
        )
    }
}
