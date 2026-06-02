package com.example.feature.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.StandardCard

@Composable
fun WhatsAppSetupScreen(navController: NavController) {
    val context = LocalContext.current
    OnboardingWrapper(
        title = "Accessibility Assistant",
        subtitle = "To trigger automatic text dispatches inside WhatsApp, Android requires an active Accessibility service. This acts as an automated send trigger.",
        currentStep = 6,
        onNext = {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {}
            navController.navigate("battery_opt")
        },
        nextText = "Configure Accessibility Bridge",
        onBack = { navController.popBackStack() },
        onSkip = { navController.navigate("battery_opt") },
        skipText = "Skip Bridge"
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
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
            }

            StandardCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "🛡️ Accessibility Disclosure",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        "What it does: RelateAI uses this service to automatically send messages in WhatsApp. It interacts with the message input and send button.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                        Text(
                            "What it does NOT do: It does NOT read your private chats, contacts, or any other app's data. It only interacts with WhatsApp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    Text(
                        "How to disable: Go to Android Settings → Accessibility → Relate AI Bridge → Off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Text(
                        "💡 Quick Configuration Guide",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "1. Click the button below to open Settings.\n2. Tap 'Installed Apps' / 'Downloaded Services'.\n3. Select 'Relate AI Bridge' and toggle it ON.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
