package com.example.feature.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlin.random.Random

data class OnboardingPage(
    val headline: String,
    val description: String
)

private val pages = listOf(
    OnboardingPage("Never Miss a Moment\nThat Matters", "AI-powered birthday wishes, anniversary reminders, and relationship nurturing — all in your voice."),
    OnboardingPage("Your AI Writes\nIn Your Voice", "Train the Style Coach with your past messages, and RelateAI will craft wishes that sound exactly like you."),
    OnboardingPage("Smart Approval\nModes", "Choose to auto-send, smart-approve, or manually review every message. You're always in control."),
    OnboardingPage("Relationship\nHealth Scores", "Track engagement, never let important connections fade. AI surfaces contacts that need your attention."),
    OnboardingPage("Gift Advisor &\nMemory Vault", "Store personal notes, preferences, and gift history. The AI uses this to craft deeply personal messages."),
    OnboardingPage("Privacy First\nAlways", "Your data stays on your device. AES-256 encryption, biometric lock, no cloud storage unless you choose."),
    OnboardingPage("Ready to\nGet Started?", "Import your contacts and let RelateAI handle the rest. You focus on the relationships, we handle the reminders.")
)

/**
 * Onboarding Screen matching Stitch "Welcome to RelateAI" design.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        val nodes = remember {
            List(20) { Offset(Random.nextFloat(), Random.nextFloat()) }
        }

        // Hero illustration: Network of glowing nodes
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .align(Alignment.TopCenter)
        ) {
            val scaledNodes = nodes.map { Offset(it.x * size.width, it.y * size.height) }
            // Draw connections
            for (i in scaledNodes.indices) {
                for (j in i + 1 until scaledNodes.size) {
                    val dist = (scaledNodes[i] - scaledNodes[j]).getDistance()
                    if (dist < 200f) {
                        drawLine(
                            color = NeonViolet.copy(alpha = 0.15f * (1f - dist / 200f)),
                            start = scaledNodes[i],
                            end = scaledNodes[j],
                            strokeWidth = 1f
                        )
                    }
                }
            }
            // Draw nodes
            scaledNodes.forEachIndexed { idx, node ->
                val nodeSize = if (idx % 3 == 0) 8f else 4f
                val nodeColor = if (idx % 2 == 0) NeonViolet else ElectricCyan
                drawCircle(color = nodeColor.copy(alpha = 0.7f), radius = nodeSize, center = node)
                drawCircle(color = nodeColor.copy(alpha = 0.2f), radius = nodeSize * 3, center = node)
            }
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, ObsidianBlack),
                        startY = 200f,
                        endY = 500f
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = pages[currentPage].headline,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = pages[currentPage].description,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Step indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { idx ->
                    Box(
                        modifier = Modifier
                            .size(if (idx == currentPage) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (idx == currentPage) NeonViolet
                                else TextTertiary.copy(alpha = 0.4f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bottom buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSkip) {
                    Text("Skip", color = TextTertiary)
                }

                Button(
                    onClick = {
                        if (currentPage < pages.size - 1) currentPage++ else onComplete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(160.dp).height(48.dp)
                ) {
                    Text(if (currentPage < pages.size - 1) "Next" else "Get Started", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
