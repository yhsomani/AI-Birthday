package com.example.feature.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.RelateAIColors

@Composable
fun LoginScreen(
    onLoginClick: () -> Unit,
    onGuestClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(RelateAIColors.BackgroundDark, Color(0xFF020408))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section - Logo & Welcome
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    val center = this.center
                    val radius = 20.dp.toPx()
                    // Draw connections
                    drawLine(
                        color = RelateAIColors.Primary,
                        start = center,
                        end = center.copy(x = center.x - 26.dp.toPx(), y = center.y - 18.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = RelateAIColors.Primary,
                        start = center,
                        end = center.copy(x = center.x + 26.dp.toPx(), y = center.y - 18.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = RelateAIColors.Primary,
                        start = center,
                        end = center.copy(y = center.y + 30.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    
                    // Draw nodes
                    drawCircle(color = RelateAIColors.Primary, radius = 9.dp.toPx(), center = center)
                    drawCircle(color = RelateAIColors.Secondary, radius = 6.dp.toPx(), center = center.copy(x = center.x - 26.dp.toPx(), y = center.y - 18.dp.toPx()))
                    drawCircle(color = RelateAIColors.Secondary, radius = 6.dp.toPx(), center = center.copy(x = center.x + 26.dp.toPx(), y = center.y - 18.dp.toPx()))
                    drawCircle(color = RelateAIColors.Tertiary, radius = 6.dp.toPx(), center = center.copy(y = center.y + 30.dp.toPx()))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Welcome to RelateAI",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = RelateAIColors.OnSurfaceDark,
                    letterSpacing = (-0.5).sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Your on-device relationship OS.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = RelateAIColors.OnSurfaceVariantDark,
                    textAlign = TextAlign.Center
                )
            }

            // Middle Section - Login & Biometrics
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                PrimaryButton(
                    text = "Continue with Google",
                    icon = Icons.Default.AccountCircle,
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                SecondaryButton(
                    text = "Proceed as Guest (Offline Mode)",
                    onClick = onGuestClick,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Biometrics indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(vertical = 16.dp, horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Fingerprint",
                        tint = RelateAIColors.Primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Touch sensor to unlock",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RelateAIColors.OnSurfaceVariantDark,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Bottom Section - Privacy disclaimer
            Text(
                text = "RelateAI uses military-grade local encryption to protect your credentials. Your personal data never leaves this device.",
                style = MaterialTheme.typography.bodySmall,
                color = RelateAIColors.OnSurfaceVariantDark.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp),
                lineHeight = 16.sp
            )
        }
    }
}
