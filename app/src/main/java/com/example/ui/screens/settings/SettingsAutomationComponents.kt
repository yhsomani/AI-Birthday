package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.R
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.ui.viewmodel.SettingsUiState

@Composable
internal fun SettingsAutomationControls(
    state: SettingsUiState,
    onAutomationModeChange: (ApprovalMode) -> Unit,
    onQuietHoursStartChange: (String) -> Unit,
    onQuietHoursEndChange: (String) -> Unit,
    onSaveQuietHours: () -> Unit,
    onChannelBlackoutChange: (MessageChannel, Boolean) -> Unit,
) {
    SettingsDivider()
    AutomationModePicker(
        selectedMode = state.automationMode,
        onAutomationModeChange = onAutomationModeChange,
    )
    SettingsDivider()
    QuietHoursEditor(
        start = state.quietHoursStart,
        end = state.quietHoursEnd,
        onStartChange = onQuietHoursStartChange,
        onEndChange = onQuietHoursEndChange,
        onSave = onSaveQuietHours,
    )
    SettingsDivider()
    ChannelBlackoutEditor(
        smsDisabled = state.channelBlackoutSms,
        whatsAppDisabled = state.channelBlackoutWhatsApp,
        emailDisabled = state.channelBlackoutEmail,
        onSmsChange = { onChannelBlackoutChange(MessageChannel.SMS, it) },
        onWhatsAppChange = { onChannelBlackoutChange(MessageChannel.WHATSAPP, it) },
        onEmailChange = { onChannelBlackoutChange(MessageChannel.EMAIL, it) },
    )
}

@Composable
private fun AutomationModePicker(
    selectedMode: ApprovalMode,
    onAutomationModeChange: (ApprovalMode) -> Unit,
) {
    var showModeMenu by remember { mutableStateOf(false) }

    Box {
        SettingsRow(
            icon = Icons.Filled.SmartToy,
            title = stringResource(R.string.settings_automation_mode),
            subtitle = selectedMode.automationModeLabel(),
            onClick = { showModeMenu = true },
        )
        DropdownMenu(
            expanded = showModeMenu,
            onDismissRequest = { showModeMenu = false },
        ) {
            listOf(
                ApprovalMode.FULLY_AUTO to stringResource(R.string.automation_mode_fully_auto),
                ApprovalMode.SMART_APPROVE to stringResource(R.string.automation_mode_smart_approve_default),
                ApprovalMode.VIP_APPROVE to stringResource(R.string.automation_mode_vip_approve),
                ApprovalMode.ALWAYS_ASK to stringResource(R.string.automation_mode_always_ask),
            ).forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onAutomationModeChange(mode)
                        showModeMenu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun QuietHoursEditor(
    start: String,
    end: String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = RelateSpacing.cardContent, vertical = RelateSpacing.md)) {
        Text(
            text = stringResource(R.string.settings_quiet_hours_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_quiet_hours_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
            OutlinedTextField(
                value = start,
                onValueChange = onStartChange,
                label = { Text(stringResource(R.string.settings_quiet_hrs_start)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(RelateRadius.control),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = end,
                onValueChange = onEndChange,
                label = { Text(stringResource(R.string.settings_quiet_hrs_end)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(RelateRadius.control),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        TextButton(onClick = onSave, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.settings_save_quiet_hours))
        }
    }
}

@Composable
private fun ChannelBlackoutEditor(
    smsDisabled: Boolean,
    whatsAppDisabled: Boolean,
    emailDisabled: Boolean,
    onSmsChange: (Boolean) -> Unit,
    onWhatsAppChange: (Boolean) -> Unit,
    onEmailChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = RelateSpacing.cardContent, vertical = RelateSpacing.md)) {
        Text(
            text = stringResource(R.string.settings_channel_blackout_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_channel_blackout_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChannelBlackoutRow(stringResource(R.string.channel_sms), smsDisabled, onSmsChange)
        ChannelBlackoutRow(stringResource(R.string.channel_whatsapp), whatsAppDisabled, onWhatsAppChange)
        ChannelBlackoutRow(stringResource(R.string.channel_email), emailDisabled, onEmailChange)
    }
}

@Composable
private fun ChannelBlackoutRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = RelateSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onBackground,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun ApprovalMode.automationModeLabel(): String {
    return when (this) {
        ApprovalMode.FULLY_AUTO -> stringResource(R.string.automation_mode_fully_auto)
        ApprovalMode.SMART_APPROVE -> stringResource(R.string.automation_mode_smart_approve_default)
        ApprovalMode.VIP_APPROVE -> stringResource(R.string.automation_mode_vip_approve)
        ApprovalMode.ALWAYS_ASK -> stringResource(R.string.automation_mode_always_ask)
        ApprovalMode.DEFAULT,
        ApprovalMode.UNKNOWN -> stringResource(R.string.automation_mode_default)
    }
}
