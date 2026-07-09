package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.R
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.ui.viewmodel.SettingsUiState

@Composable
internal fun SettingsAiConfigurationSection(
    state: SettingsUiState,
    onGeminiApiKeyChange: (String) -> Unit,
    onSaveGeminiApiKey: () -> Unit,
    onSenderEmailChange: (String) -> Unit,
    onSenderEmailPasswordChange: (String) -> Unit,
    onSaveSenderEmailSettings: () -> Unit,
    onAutomationModeChange: (ApprovalMode) -> Unit,
    onQuietHoursStartChange: (String) -> Unit,
    onQuietHoursEndChange: (String) -> Unit,
    onSaveQuietHours: () -> Unit,
    onChannelBlackoutChange: (MessageChannel, Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    SettingsSection(
        title = stringResource(R.string.settings_ai_configuration_section),
        modifier = Modifier.testTag(SettingsScreenTestTags.AI_CONFIGURATION_SECTION),
    ) {
        SettingsCard {
            Column(
                modifier = Modifier.padding(
                    horizontal = RelateSpacing.cardContent,
                    vertical = RelateSpacing.md,
                ),
            ) {
                Text(
                    text = stringResource(R.string.settings_gemini_api_key),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_gemini_api_key_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                OutlinedTextField(
                    value = state.geminiApiKey,
                    onValueChange = onGeminiApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.settings_gemini_api_key_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onSaveGeminiApiKey()
                    }),
                    colors = settingsTextFieldColors(),
                    shape = RoundedCornerShape(RelateRadius.control),
                )
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSaveGeminiApiKey()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(RelateRadius.control),
                ) {
                    if (state.geminiApiKeySaved) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(RelateSize.iconSm),
                            tint = MaterialTheme.colorScheme.background,
                        )
                        Spacer(modifier = Modifier.width(RelateSpacing.xs))
                        Text(stringResource(R.string.saved), color = MaterialTheme.colorScheme.background)
                    } else {
                        Icon(
                            Icons.Filled.Key,
                            contentDescription = null,
                            modifier = Modifier.size(RelateSize.iconSm),
                            tint = MaterialTheme.colorScheme.background,
                        )
                        Spacer(modifier = Modifier.width(RelateSpacing.xs))
                        Text(
                            stringResource(R.string.settings_save_api_key),
                            color = MaterialTheme.colorScheme.background,
                        )
                    }
                }
            }
            SettingsDivider()
            Column(
                modifier = Modifier.padding(
                    horizontal = RelateSpacing.cardContent,
                    vertical = RelateSpacing.md,
                ),
            ) {
                Text(
                    text = stringResource(R.string.settings_email_sending_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_email_sending_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                OutlinedTextField(
                    value = state.senderEmail,
                    onValueChange = onSenderEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_sender_email)) },
                    singleLine = true,
                    colors = settingsTextFieldColors(),
                    shape = RoundedCornerShape(RelateRadius.control),
                )
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                OutlinedTextField(
                    value = state.senderEmailPassword,
                    onValueChange = onSenderEmailPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_app_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = settingsTextFieldColors(),
                    shape = RoundedCornerShape(RelateRadius.control),
                )
                Text(
                    text = stringResource(R.string.settings_email_app_password_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = RelateSpacing.xs),
                )
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSaveSenderEmailSettings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(RelateRadius.control),
                ) {
                    Text(
                        text = if (state.senderEmailSaved) {
                            stringResource(R.string.saved)
                        } else {
                            stringResource(R.string.settings_save_email_settings)
                        },
                        color = MaterialTheme.colorScheme.background,
                    )
                }
            }
            SettingsAutomationControls(
                state = state,
                onAutomationModeChange = onAutomationModeChange,
                onQuietHoursStartChange = onQuietHoursStartChange,
                onQuietHoursEndChange = onQuietHoursEndChange,
                onSaveQuietHours = onSaveQuietHours,
                onChannelBlackoutChange = onChannelBlackoutChange,
            )
        }
    }
}

@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
)
