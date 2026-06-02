package com.example.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.core.prefs.SecurePrefs

@Composable
fun AutomationPrefsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { SecurePrefs(context) }
    var selectedOption by remember { mutableStateOf(prefs.getGlobalAutomationMode()) }

    OnboardingWrapper(
        title = "Choose Dispatch Rigor",
        subtitle = "Determine your default automation execution policy. Rest assured, this configuration can be toggled individually for any connection.",
        currentStep = 9,
        onNext = {
            prefs.setGlobalAutomationMode(selectedOption)
            navController.navigate("import_progress")
        },
        nextText = "Save Default Mode",
        onBack = { navController.popBackStack() },
        onSkip = { navController.navigate("import_progress") },
        skipText = "Skip Preferences"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AutomationChoiceCard(
                title = "Strict Manual Verification",
                desc = "Recommended. Relate AI holds all drafted texts in draft logs and triggers a notification for review before dispatching.",
                selected = selectedOption == "ALWAYS_ASK",
                onClick = { selectedOption = "ALWAYS_ASK" }
            )

            AutomationChoiceCard(
                title = "Smart Hold Approval",
                desc = "Notifies when draft suggestions are calculated. Spends if not cancelled within 2 hours.",
                selected = selectedOption == "SMART_APPROVE",
                onClick = { selectedOption = "SMART_APPROVE" }
            )

            AutomationChoiceCard(
                title = "Full Background Autonomous",
                desc = "Quietly schedules and executes drafted greetings on milestone events entirely hands-free.",
                selected = selectedOption == "FULLY_AUTO",
                onClick = { selectedOption = "FULLY_AUTO" }
            )
        }
    }
}

@Composable
fun AutomationChoiceCard(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
