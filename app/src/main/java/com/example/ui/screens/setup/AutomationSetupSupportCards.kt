package com.example.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing

@Composable
internal fun AutomationSetupSupportCards(
    isIgnoringBatteryOptimizations: Boolean,
    whatsAppAutomationConsentGranted: Boolean,
    onWhatsAppConsentChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit,
) {
    SetupCard(
        icon = Icons.AutoMirrored.Filled.Chat,
        title = stringResource(R.string.automation_setup_whatsapp_card_title),
        body = stringResource(R.string.automation_setup_whatsapp_card_body),
        actionText = stringResource(R.string.automation_setup_action_open_accessibility),
        onClick = onOpenAccessibilitySettings,
        consentChecked = whatsAppAutomationConsentGranted,
        consentText = stringResource(R.string.automation_setup_whatsapp_consent_label),
        onConsentChange = onWhatsAppConsentChange,
        modifier = Modifier.testTag(AutomationSetupTestTags.WHATSAPP_CARD),
    )

    SetupCard(
        icon = Icons.Filled.BatterySaver,
        title = stringResource(R.string.automation_setup_battery_card_title),
        body = if (isIgnoringBatteryOptimizations) {
            stringResource(R.string.automation_setup_battery_card_ignored)
        } else {
            stringResource(R.string.automation_setup_battery_card_body)
        },
        actionText = stringResource(R.string.automation_setup_action_open_battery_settings),
        onClick = onOpenBatterySettings,
    )

    SetupCard(
        icon = Icons.Filled.Notifications,
        title = stringResource(R.string.automation_setup_notifications_card_title),
        body = stringResource(R.string.automation_setup_notifications_card_body),
        actionText = stringResource(R.string.automation_setup_action_app_settings),
        onClick = onOpenAppSettings,
    )

    SetupCard(
        icon = Icons.Filled.Security,
        title = stringResource(R.string.automation_setup_approval_card_title),
        body = stringResource(R.string.automation_setup_approval_card_body),
        actionText = stringResource(R.string.automation_setup_action_done),
        onClick = onBack,
        secondary = true,
    )
}

@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    consentChecked: Boolean? = null,
    consentText: String? = null,
    onConsentChange: ((Boolean) -> Unit)? = null,
) {
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(RelateSize.iconLg),
                )
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (consentChecked != null && consentText != null && onConsentChange != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                ) {
                    Checkbox(
                        checked = consentChecked,
                        onCheckedChange = onConsentChange,
                    )
                    Text(
                        text = consentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = RelateSize.primaryButtonHeight),
                shape = RoundedCornerShape(RelateRadius.control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (secondary) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (secondary) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                ),
            ) {
                Text(actionText)
                if (!secondary) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(start = RelateSpacing.sm),
                    )
                }
            }
        }
    }
}
