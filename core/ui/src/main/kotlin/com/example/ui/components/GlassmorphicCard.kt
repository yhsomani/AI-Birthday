package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet

/**
 * Glassmorphic card component matching the Stitch design system.
 * Dark surface with subtle glass border effect and optional glow.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = DarkSlate.copy(alpha = 0.7f),
    borderColor: Color = GlassEdge,
    cornerRadius: Dp = 16.dp,
    contentPadding: Dp = 16.dp,
    hasGlow: Boolean = false,
    glowColor: Color = NeonViolet.copy(alpha = 0.15f),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Glassmorphic card with a colored left accent border.
 * Used for message cards, notifications, etc.
 */
@Composable
fun AccentGlassmorphicCard(
    modifier: Modifier = Modifier,
    accentColor: Color = NeonViolet,
    backgroundColor: Color = DarkSlate.copy(alpha = 0.7f),
    borderColor: Color = GlassEdge,
    cornerRadius: Dp = 16.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.3f), Color.Transparent),
                        startX = 0f,
                        endX = 80f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp) // Extra padding for accent bar
                .padding(contentPadding),
            content = content
        )
    }
}
