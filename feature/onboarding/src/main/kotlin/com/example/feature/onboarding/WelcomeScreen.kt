package com.example.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.theme.CyberRose
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.Emerald
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WelcomeScreen(navController: NavController) {
    OnboardingWrapper(
        title = "Never lose contact with the ones who matter.",
        subtitle = "Relate AI runs seamlessly on your device to help you analyze relationship health, track important events, and write personalized greetings.",
        currentStep = 1,
        onNext = { navController.navigate("google_signin") },
        nextText = "Get Started"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSlate.copy(alpha = 0.7f))
                    .border(1.dp, GlassEdge, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(80.dp)) {
                    val center = this.center
                    // Draw connections
                    drawLine(
                        color = NeonViolet,
                        start = center,
                        end = center.copy(x = center.x - 26.dp.toPx(), y = center.y - 18.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = NeonViolet,
                        start = center,
                        end = center.copy(x = center.x + 26.dp.toPx(), y = center.y - 18.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = NeonViolet,
                        start = center,
                        end = center.copy(y = center.y + 30.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    
                    // Draw nodes
                    drawCircle(color = NeonViolet, radius = 9.dp.toPx(), center = center)
                    drawCircle(color = Emerald, radius = 6.dp.toPx(), center = center.copy(x = center.x - 26.dp.toPx(), y = center.y - 18.dp.toPx()))
                    drawCircle(color = Emerald, radius = 6.dp.toPx(), center = center.copy(x = center.x + 26.dp.toPx(), y = center.y - 18.dp.toPx()))
                    drawCircle(color = CyberRose, radius = 6.dp.toPx(), center = center.copy(y = center.y + 30.dp.toPx()))
                }
            }

            Text(
                text = "Key Intelligence Capabilities:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            CapabilityOption(
                icon = Icons.Default.Contacts,
                title = "On-Device Directory Graph",
                desc = "Scans offline contacts and networks to build deep relationship health boards."
            )

            CapabilityOption(
                icon = Icons.Default.AutoAwesome,
                title = "Style DNA Analysis",
                desc = "Learns your exact voice from past texts with zero server-side leaks."
            )

            CapabilityOption(
                icon = Icons.Default.BatteryChargingFull,
                title = "Smart Background Deliveries",
                desc = "Automates text drafts while your phone is asleep, saving memory and battery."
            )
        }
    }
}

@Composable
fun CapabilityOption(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSlate.copy(alpha = 0.7f))
            .border(1.dp, GlassEdge, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NeonViolet.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NeonViolet,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
