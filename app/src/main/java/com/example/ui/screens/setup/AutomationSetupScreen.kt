package com.example.ui.screens.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.components.RelateStatusBanner
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSuccess
import com.example.core.ui.theme.RelateWarning

@Composable
fun AutomationSetupScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isAccessibilityEnabled = remember { context.isWhatsAppAutomationServiceEnabled() }
    val isIgnoringBattery = remember { context.isIgnoringBatteryOptimizations() }

    RelateScreen(
        title = "Automation Setup",
        subtitle = "Prepare WhatsApp automation, reminders, and reliable background sends.",
        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        navigationContentDescription = "Back",
        onNavigationClick = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RelateStatusBanner(
                title = if (isAccessibilityEnabled) "WhatsApp automation is enabled" else "WhatsApp automation needs setup",
                message = if (isAccessibilityEnabled) {
                    "RelateAI can open WhatsApp and send already-approved messages."
                } else {
                    "Enable the RelateAI accessibility service only if you want automatic WhatsApp sends."
                },
                icon = if (isAccessibilityEnabled) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.Chat,
                containerColor = if (isAccessibilityEnabled) RelateSuccess.copy(alpha = 0.16f) else RelateWarning.copy(alpha = 0.16f),
                contentColor = if (isAccessibilityEnabled) RelateSuccess else RelateWarning,
            )

            SetupCard(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "Enable WhatsApp automation",
                body = "Open Accessibility settings and enable RelateAI - Auto WhatsApp. The service only looks for WhatsApp compose and send controls.",
                actionText = "Open Accessibility",
                onClick = { context.safeStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )

            SetupCard(
                icon = Icons.Filled.BatterySaver,
                title = "Allow background reliability",
                body = if (isIgnoringBattery) {
                    "Battery optimization is already ignored for RelateAI."
                } else {
                    "Allow RelateAI to keep scheduled workers reliable around birthdays and reminders."
                },
                actionText = "Battery Settings",
                onClick = { context.openBatteryOptimizationSettings() },
            )

            SetupCard(
                icon = Icons.Filled.Notifications,
                title = "Review notification access",
                body = "Notifications are used for reminders and approval prompts before messages are sent.",
                actionText = "App Settings",
                onClick = { context.openAppSettings() },
            )

            SetupCard(
                icon = Icons.Filled.Security,
                title = "Automation stays approval-first",
                body = "Use SMART_APPROVE, VIP_APPROVE, or ALWAYS_ASK in Settings when you want review before scheduling or sending.",
                actionText = "Done",
                onClick = onBack,
                secondary = true,
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionText: String,
    onClick: () -> Unit,
    secondary: Boolean = false,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, tint = RelatePrimary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (secondary) MaterialTheme.colorScheme.surfaceVariant else RelatePrimary,
                    contentColor = if (secondary) MaterialTheme.colorScheme.onSurface else RelateDarkBackground,
                ),
            ) {
                Text(actionText)
                if (!secondary) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private fun Context.isWhatsAppAutomationServiceEnabled(): Boolean {
    val expectedService = "$packageName/com.example.core.accessibility.WhatsAppAccessibilityService"
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabledServices.split(':').any {
        it.equals(expectedService, ignoreCase = true)
    }
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(packageName) == true
}

private fun Context.openBatteryOptimizationSettings() {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$packageName")
    }
    safeStartActivity(intent, fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

private fun Context.openAppSettings() {
    safeStartActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    )
}

private fun Context.safeStartActivity(intent: Intent, fallback: Intent? = null) {
    try {
        startActivity(intent)
    } catch (_: Exception) {
        if (fallback != null) {
            try {
                startActivity(fallback)
            } catch (_: Exception) {
                // Settings intents can be unavailable on some OEM builds.
            }
        }
    }
}
