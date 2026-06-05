package com.example.feature.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Splash Screen matching Stitch "RelateAI Immersive Splash Screen" design.
 * - Obsidian black background with ambient particles
 * - Glowing neon violet heart logo with radial glow
 * - Shimmer text "RelateAI" with tagline
 * - Animated loading progress bar
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_anim")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    // Progress bar animation
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(2500),
        label = "progress"
    )

    LaunchedEffect(Unit) {
        progress = 1f
        delay(3000)
        onSplashComplete()
    }

    // Particle data
    val particles = remember {
        List(50) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 3f + 1f,
                alpha = Random.nextFloat() * 0.4f + 0.1f,
                speed = Random.nextFloat() * 0.0005f + 0.0001f
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Ambient particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { p ->
                drawCircle(
                    color = TextPrimary.copy(alpha = p.alpha),
                    radius = p.size,
                    center = Offset(p.x * size.width, p.y * size.height)
                )
            }
        }

        // Centered content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo with radial glow
            Box(contentAlignment = Alignment.Center) {
                // Radial glow
                Canvas(modifier = Modifier.size(200.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                NeonViolet.copy(alpha = glowPulse * 0.3f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2
                    )
                }
                // Heart logo
                Canvas(modifier = Modifier.size(120.dp)) {
                    drawNeonHeart(this, NeonViolet, glowPulse)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App name with gradient
            Text(
                text = "RelateAI",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                ),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Your Relationship Intelligence",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Bottom progress bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 64.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(GlassEdge)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    NeonViolet.copy(alpha = 0.6f),
                                    NeonViolet
                                )
                            )
                        )
                )
            }
        }
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
    val speed: Float
)

private fun drawNeonHeart(scope: DrawScope, color: Color, glow: Float) {
    with(scope) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.85f)
            cubicTo(w * 0.15f, h * 0.55f, w * 0.0f, h * 0.3f, w * 0.25f, h * 0.15f)
            cubicTo(w * 0.35f, h * 0.08f, w * 0.45f, h * 0.12f, w * 0.5f, h * 0.25f)
            cubicTo(w * 0.55f, h * 0.12f, w * 0.65f, h * 0.08f, w * 0.75f, h * 0.15f)
            cubicTo(w * 1.0f, h * 0.3f, w * 0.85f, h * 0.55f, w * 0.5f, h * 0.85f)
            close()
        }
        drawPath(path, color.copy(alpha = glow))
    }
}
