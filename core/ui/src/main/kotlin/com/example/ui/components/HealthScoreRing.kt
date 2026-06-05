package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet

/**
 * Animated circular health score ring matching the Stitch analytics design.
 * Cyan gradient ring with score number centered inside.
 */
@Composable
fun HealthScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    strokeWidth: Dp = 8.dp,
    trackColor: Color = Color.White.copy(alpha = 0.06f),
    scoreColor: Color = ElectricCyan,
    animationDuration: Int = 1500
) {
    var animationPlayed by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animationPlayed,
        animationSpec = tween(durationMillis = animationDuration),
        label = "health_score_anim"
    )

    LaunchedEffect(score) {
        animationPlayed = score / 100f
    }

    val progressColor = when {
        score >= 70 -> ElectricCyan
        score >= 40 -> Color(0xFFFBBF24) // Amber for medium
        else -> Color(0xFFF43F5E) // Cyber Rose for low
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val diameter = this.size.minDimension
            val stroke = strokeWidth.toPx()
            val arcSize = Size(diameter - stroke, diameter - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Animated progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        progressColor.copy(alpha = 0.4f),
                        progressColor,
                        progressColor.copy(alpha = 0.8f)
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        // Score text
        Text(
            text = "$score",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = (size.value * 0.35f).sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White
        )
    }
}
