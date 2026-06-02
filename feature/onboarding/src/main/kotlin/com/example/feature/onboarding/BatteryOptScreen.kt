package com.example.feature.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.StandardCard

@Composable
fun BatteryOptScreen(navController: NavController) {
    val context = LocalContext.current
    OnboardingWrapper(
        title = "Sustained Background Reminders",
        subtitle = "To execute daily automated analyses and deliver drafts during system sleep states, please grant a battery optimization exemption.",
        currentStep = 7,
        onNext = {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            try {
                context.startActivity(intent)
            } catch (e: Exception) { }
            navController.navigate("writing_style")
        },
        nextText = "Grant Exemption",
        onBack = { navController.popBackStack() },
        onSkip = { navController.navigate("writing_style") },
        skipText = "Keep System Limits"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
            }

            StandardCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "📈 Background Reliability",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Without this exemption, Android may occasionally suspend routine background schedulers, causing draft reminds or event updates to arrive hours late.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
