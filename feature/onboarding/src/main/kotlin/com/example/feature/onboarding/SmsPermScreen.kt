package com.example.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.components.StandardCard

@Composable
fun SmsPermScreen(navController: NavController) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        navController.navigate("whatsapp_setup")
    }

    OnboardingWrapper(
        title = "Permit Draft Despatch",
        subtitle = "Automate outbound messaging directly over SMS when reminders fire.",
        currentStep = 5,
        onNext = {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.SEND_SMS
            ))
        },
        nextText = "Grant SMS Transmission",
        onBack = { navController.popBackStack() },
        onSkip = { navController.navigate("whatsapp_setup") },
        skipText = "Do Later"
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
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
            }

            StandardCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "🛡️ Active Protection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "We never send any messages automatically unless explicitly authorized. Every automated schedule offers notification checks for safety.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
