package com.example.ui.screens.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateFraction
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.ui.viewmodel.PasswordStrength

@Composable
internal fun BackupSecurityWarningCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RelateRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(
                alpha = RelateAlpha.feedbackContainer,
            ),
        ),
    ) {
        Row(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = stringResource(R.string.backup_security_warning_cd),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(RelateSize.iconLg),
            )
            Spacer(modifier = Modifier.width(RelateSpacing.md))
            Column {
                Text(
                    stringResource(R.string.backup_security_note_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Text(
                    stringResource(R.string.backup_security_note_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun BackupPassphraseCard(
    passphrase: String,
    passwordStrength: PasswordStrength,
    passwordVisible: Boolean,
    onPassphraseChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Text(
                stringResource(R.string.backup_passphrase_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = passphrase,
                onValueChange = onPassphraseChange,
                label = { Text(stringResource(R.string.backup_passphrase_label)) },
                placeholder = { Text(stringResource(R.string.backup_passphrase_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BackupRestoreTestTags.PASSPHRASE_FIELD),
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) {
                        Icons.Filled.Visibility
                    } else {
                        Icons.Filled.VisibilityOff
                    }
                    IconButton(
                        onClick = onTogglePasswordVisibility,
                        modifier = Modifier.testTag(BackupRestoreTestTags.VISIBILITY_TOGGLE),
                    ) {
                        Icon(
                            image,
                            contentDescription = stringResource(R.string.backup_toggle_password_visibility),
                        )
                    }
                },
            )

            if (passphrase.isNotEmpty()) {
                BackupPasswordStrengthIndicator(passwordStrength = passwordStrength)
            }
        }
    }
}

@Composable
private fun BackupPasswordStrengthIndicator(
    passwordStrength: PasswordStrength,
    modifier: Modifier = Modifier,
) {
    val semanticColors = MaterialTheme.relateSemanticColors
    val color = when (passwordStrength) {
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrength.FAIR -> semanticColors.warning
        PasswordStrength.STRONG -> MaterialTheme.colorScheme.secondary
        PasswordStrength.VERY_STRONG -> semanticColors.success
    }
    val progress = when (passwordStrength) {
        PasswordStrength.WEAK -> RelateFraction.strengthWeak
        PasswordStrength.FAIR -> RelateFraction.strengthFair
        PasswordStrength.STRONG -> RelateFraction.strengthStrong
        PasswordStrength.VERY_STRONG -> RelateFraction.strengthFull
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.backup_password_strength_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(passwordStrength.labelRes),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(RelateSize.progressTrack)
                .testTag(BackupRestoreTestTags.STRENGTH_INDICATOR),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

private val PasswordStrength.labelRes: Int
    get() = when (this) {
        PasswordStrength.WEAK -> R.string.backup_password_strength_weak
        PasswordStrength.FAIR -> R.string.backup_password_strength_fair
        PasswordStrength.STRONG -> R.string.backup_password_strength_strong
        PasswordStrength.VERY_STRONG -> R.string.backup_password_strength_very_strong
    }
