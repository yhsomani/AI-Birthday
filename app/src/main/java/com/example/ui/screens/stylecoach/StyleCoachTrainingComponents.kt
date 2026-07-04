package com.example.ui.screens.stylecoach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors

@Composable
internal fun StyleTrainingCard(
    samplesText: String,
    onSamplesChange: (String) -> Unit,
    isTraining: Boolean,
    isAutoAnalyzing: Boolean,
    statusMessageRes: Int?,
    statusIsError: Boolean,
    onManualAnalyze: () -> Unit,
    onAutoAnalyze: () -> Unit,
) {
    val busy = isTraining || isAutoAnalyzing
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RelateRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
        ),
    ) {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(RelateSize.iconLg),
                )
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                Text(
                    text = stringResource(R.string.style_coach_train_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = stringResource(R.string.style_coach_train_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = samplesText,
                onValueChange = onSamplesChange,
                label = { Text(stringResource(R.string.style_coach_samples_label)) },
                placeholder = { Text(stringResource(R.string.style_coach_samples_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StyleCoachTestTags.SAMPLE_FIELD),
                minLines = 4,
                maxLines = 8,
            )

            statusMessageRes?.let { messageRes ->
                StatusMessage(
                    message = stringResource(messageRes),
                    isError = statusIsError,
                )
            }

            Button(
                onClick = onManualAnalyze,
                enabled = !busy && samplesText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StyleCoachTestTags.MANUAL_ANALYZE_BUTTON),
                shape = RoundedCornerShape(RelateRadius.control),
            ) {
                if (isTraining) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(RelateSize.iconLg)
                            .testTag(StyleCoachTestTags.MANUAL_PROGRESS),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.style_coach_manual_analyze))
                }
            }

            OutlinedButton(
                onClick = onAutoAnalyze,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StyleCoachTestTags.AUTO_ANALYZE_BUTTON),
                shape = RoundedCornerShape(RelateRadius.control),
            ) {
                if (isAutoAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(RelateSize.iconSm)
                            .testTag(StyleCoachTestTags.AUTO_PROGRESS),
                        strokeWidth = RelateSize.progressStroke,
                    )
                } else {
                    Text(stringResource(R.string.style_coach_auto_analyze))
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(
    message: String,
    isError: Boolean,
) {
    val color = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.relateSemanticColors.success
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StyleCoachTestTags.STATUS_MESSAGE),
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.Info else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = color,
        )
        Text(
            text = message,
            color = color,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
