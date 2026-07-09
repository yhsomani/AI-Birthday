package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.example.R
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.SettingsUiState

@Composable
internal fun SettingsAccountSection(state: SettingsUiState) {
    SettingsSection(stringResource(R.string.settings_account_section)) {
        SettingsCard {
            Row(
                modifier = Modifier.padding(RelateSpacing.cardContent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.userPhotoUrl != null) {
                    AsyncImage(
                        model = state.userPhotoUrl,
                        contentDescription = stringResource(R.string.profile_photo),
                        modifier = Modifier
                            .size(RelateSize.minTouchTarget)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(RelateSize.minTouchTarget)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            state.userName.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(RelateSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.userName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        state.userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
    }
}

@Composable
internal fun SettingsPreferencesSection(
    state: SettingsUiState,
    onBirthdayRemindersChange: (Boolean) -> Unit,
    onAiWishGenerationChange: (Boolean) -> Unit,
    onBiometricLockChange: (Boolean) -> Unit,
    onNavigateToStyleCoach: () -> Unit,
    onNavigateToAutomationSetup: () -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_preferences_section)) {
        SettingsCard {
            SettingsToggle(
                title = stringResource(R.string.settings_birthday_reminders),
                icon = Icons.Filled.Notifications,
                checked = state.birthdayReminders,
            ) { onBirthdayRemindersChange(it) }
            SettingsDivider()
            SettingsToggle(
                title = stringResource(R.string.settings_ai_wish_generation),
                icon = Icons.Filled.SmartToy,
                checked = state.aiWishGeneration,
            ) { onAiWishGenerationChange(it) }
            SettingsDivider()
            SettingsToggle(
                title = stringResource(R.string.settings_biometric_lock),
                icon = Icons.Filled.Security,
                checked = state.biometricLockEnabled,
            ) { onBiometricLockChange(it) }
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.settings_ai_style_coach),
                subtitle = stringResource(R.string.settings_ai_style_coach_subtitle),
                onClick = onNavigateToStyleCoach,
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.settings_automation_setup),
                subtitle = stringResource(R.string.settings_automation_setup_subtitle),
                onClick = onNavigateToAutomationSetup,
            )
        }
    }
}
