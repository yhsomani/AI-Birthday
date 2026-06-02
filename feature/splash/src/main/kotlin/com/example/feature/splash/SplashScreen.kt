package com.example.feature.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.core.auth.BiometricAuthManager
import com.example.core.prefs.SecurePrefs
import com.example.ui.theme.RelateAIColors
import kotlinx.coroutines.delay

private tailrec fun android.content.Context.findActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val authManager = remember(activity) { activity?.let { BiometricAuthManager(it) } }
    var authAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(2500) // Slightly longer to appreciate the splash animation
        val prefs = SecurePrefs(context)
        if (prefs.isBiometricLockEnabled() && authManager != null && authManager.isAvailable() && !authAttempted) {
            authAttempted = true
            authManager.authenticate(
                onSuccess = { onSplashFinished() },
                onError = { _, _ -> onSplashFinished() },
                onFailed = { onSplashFinished() }
            )
        } else {
            onSplashFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(RelateAIColors.BackgroundDark, Color(0xFF020408))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight()
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "logo_scale"
            )
            val alphaPulse by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "logo_alpha"
            )

            Canvas(modifier = Modifier.size(120.dp)) {
                val center = this.center
                val radius = 30.dp.toPx()
                // Draw connection lines
                drawLine(
                    color = RelateAIColors.Primary.copy(alpha = alphaPulse),
                    start = center,
                    end = center.copy(x = center.x - 40.dp.toPx(), y = center.y - 25.dp.toPx()),
                    strokeWidth = 3.dp.toPx()
                )
                drawLine(
                    color = RelateAIColors.Primary.copy(alpha = alphaPulse),
                    start = center,
                    end = center.copy(x = center.x + 40.dp.toPx(), y = center.y - 25.dp.toPx()),
                    strokeWidth = 3.dp.toPx()
                )
                drawLine(
                    color = RelateAIColors.Primary.copy(alpha = alphaPulse),
                    start = center,
                    end = center.copy(y = center.y + 45.dp.toPx()),
                    strokeWidth = 3.dp.toPx()
                )
                
                // Draw outer pulsing glow circle
                drawCircle(
                    color = RelateAIColors.Primary.copy(alpha = 0.12f * pulseScale),
                    radius = radius * pulseScale * 1.6f,
                    center = center
                )
                // Draw center node (Primary color)
                drawCircle(
                    color = RelateAIColors.Primary,
                    radius = 14.dp.toPx(),
                    center = center
                )
                // Draw surrounding relationship nodes
                drawCircle(
                    color = RelateAIColors.Secondary,
                    radius = 9.dp.toPx(),
                    center = center.copy(x = center.x - 40.dp.toPx(), y = center.y - 25.dp.toPx())
                )
                drawCircle(
                    color = RelateAIColors.Secondary,
                    radius = 9.dp.toPx(),
                    center = center.copy(x = center.x + 40.dp.toPx(), y = center.y - 25.dp.toPx())
                )
                drawCircle(
                    color = RelateAIColors.Tertiary,
                    radius = 9.dp.toPx(),
                    center = center.copy(y = center.y + 45.dp.toPx())
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            
            Text(
                text = "RelateAI",
                style = MaterialTheme.typography.headlineLarge,
                color = RelateAIColors.OnSurfaceDark,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Your relationships, nurtured offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = RelateAIColors.OnSurfaceVariantDark,
                fontWeight = FontWeight.Normal
            )
        }

        // Sleek, minimal loading line at the bottom
        LinearProgressIndicator(
            color = RelateAIColors.Primary,
            trackColor = RelateAIColors.OutlineDark.copy(alpha = 0.3f),
            modifier = Modifier
                .width(140.dp)
                .height(2.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        )
    }
}
