package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextTertiary

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            SurfaceElevated,
            TextTertiary.copy(alpha = 0.15f),
            SurfaceElevated
        ),
        start = Offset(translateX, 0f),
        end = Offset(translateX + 200f, 0f)
    )
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(shimmerBrush))
}

@Composable
fun ShimmerCircle(size: Dp = 48.dp) {
    ShimmerBox(modifier = Modifier.size(size).clip(CircleShape))
}

@Composable
fun ShimmerTextLine(width: Dp = 200.dp, height: Dp = 16.dp) {
    ShimmerBox(modifier = Modifier.width(width).height(height))
}

@Composable
fun ShimmerCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceElevated.copy(alpha = 0.3f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerCircle(size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerTextLine(width = 120.dp)
                ShimmerTextLine(width = 80.dp, height = 12.dp)
            }
        }
        ShimmerTextLine(width = 260.dp, height = 14.dp)
        ShimmerTextLine(width = 180.dp, height = 14.dp)
    }
}
