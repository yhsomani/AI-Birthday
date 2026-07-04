package com.example.ui.screens.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.FilterChip
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel

@Composable
internal fun ContactPreferencesDialogBody(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    relationshipType: String,
    onRelationshipTypeChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    channel: MessageChannel,
    onChannelChange: (MessageChannel) -> Unit,
    formality: String,
    onFormalityChange: (String) -> Unit,
    style: String,
    onStyleChange: (String) -> Unit,
    automationMode: ApprovalMode,
    onAutomationModeChange: (ApprovalMode) -> Unit,
    sendTime: String,
    onSendTimeChange: (String) -> Unit,
    giftBudget: String,
    onGiftBudgetChange: (String) -> Unit,
    annualBudget: String,
    onAnnualBudgetChange: (String) -> Unit,
    interests: String,
    onInterestsChange: (String) -> Unit,
    sensitiveTopics: String,
    onSensitiveTopicsChange: (String) -> Unit,
    lifePhase: String,
    onLifePhaseChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    skipAutoWish: Boolean,
    onSkipAutoWishChange: (Boolean) -> Unit,
    localError: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(RelateSize.dialogContentMaxHeight)
            .verticalScroll(rememberScrollState())
            .testTag(ContactDetailTestTags.PREFERENCES_FORM_BODY),
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
    ) {
        PreferenceField(R.string.contact_preferences_nickname, nickname, onChange = onNicknameChange)
        PreferenceField(
            labelRes = R.string.contact_preferences_relationship_type,
            value = relationshipType,
            onChange = onRelationshipTypeChange,
        )
        ChoiceRow(
            titleRes = R.string.contact_preferences_language,
            options = listOf(
                "en" to stringResource(R.string.language_english),
                "hi" to stringResource(R.string.language_hindi),
            ),
            selected = language,
            onSelect = onLanguageChange,
        )
        ChoiceRow(
            titleRes = R.string.contact_preferences_channel,
            options = listOf(
                MessageChannel.SMS to stringResource(R.string.channel_sms),
                MessageChannel.WHATSAPP to stringResource(R.string.channel_whatsapp),
                MessageChannel.EMAIL to stringResource(R.string.channel_email),
            ),
            selected = channel,
            onSelect = onChannelChange,
        )
        ChoiceRow(
            titleRes = R.string.contact_preferences_formality,
            options = listOf(
                "CASUAL" to stringResource(R.string.formality_casual),
                "SEMI_FORMAL" to stringResource(R.string.formality_semi_formal),
                "FORMAL" to stringResource(R.string.formality_formal),
            ),
            selected = formality,
            onSelect = onFormalityChange,
        )
        ChoiceRow(
            titleRes = R.string.contact_preferences_style,
            options = listOf(
                "WARM" to stringResource(R.string.style_warm),
                "FUNNY" to stringResource(R.string.style_funny),
                "PROFESSIONAL" to stringResource(R.string.style_professional),
                "EMOTIONAL" to stringResource(R.string.style_emotional),
            ),
            selected = style,
            onSelect = onStyleChange,
        )
        ChoiceRow(
            titleRes = R.string.contact_preferences_automation_mode,
            options = listOf(
                ApprovalMode.DEFAULT to stringResource(R.string.automation_mode_default),
                ApprovalMode.SMART_APPROVE to stringResource(R.string.automation_mode_smart_approve_default),
                ApprovalMode.VIP_APPROVE to stringResource(R.string.automation_mode_vip_approve),
                ApprovalMode.FULLY_AUTO to stringResource(R.string.automation_mode_fully_auto),
                ApprovalMode.ALWAYS_ASK to stringResource(R.string.automation_mode_always_ask),
            ),
            selected = automationMode,
            onSelect = onAutomationModeChange,
        )
        PreferenceField(R.string.contact_preferences_send_time, sendTime, onChange = onSendTimeChange)
        PreferenceField(R.string.contact_preferences_gift_budget, giftBudget, onChange = onGiftBudgetChange)
        PreferenceField(R.string.contact_preferences_annual_budget, annualBudget, onChange = onAnnualBudgetChange)
        PreferenceField(R.string.contact_preferences_interests, interests, onChange = onInterestsChange)
        PreferenceField(
            labelRes = R.string.contact_preferences_sensitive_topics,
            value = sensitiveTopics,
            onChange = onSensitiveTopicsChange,
        )
        PreferenceField(R.string.contact_preferences_life_phase, lifePhase, onChange = onLifePhaseChange)
        PreferenceField(
            labelRes = R.string.contact_preferences_notes,
            value = notes,
            minLines = 2,
            modifier = Modifier.testTag(ContactDetailTestTags.PREFERENCES_NOTES_FIELD),
            onChange = onNotesChange,
        )
        Row(
            modifier = Modifier.testTag(ContactDetailTestTags.PREFERENCES_SKIP_AUTO_WISH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.contact_preferences_skip_auto_wish),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = skipAutoWish, onCheckedChange = onSkipAutoWishChange)
        }
        localError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    titleRes: Int,
    options: List<Pair<MessageChannel, String>>,
    selected: MessageChannel,
    onSelect: (MessageChannel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreferenceChoiceFlow {
            options.forEach { (value, label) ->
                FilterChip(
                    label = label,
                    isSelected = selected == value,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    titleRes: Int,
    options: List<Pair<ApprovalMode, String>>,
    selected: ApprovalMode,
    onSelect: (ApprovalMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreferenceChoiceFlow {
            options.forEach { (value, label) ->
                FilterChip(
                    label = label,
                    isSelected = selected == value,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun PreferenceField(
    labelRes: Int,
    value: String,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        minLines = minLines,
        maxLines = if (minLines > 1) 4 else 1,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoiceRow(
    titleRes: Int,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreferenceChoiceFlow {
            options.forEach { (value, label) ->
                FilterChip(
                    label = label,
                    isSelected = selected.equals(value, ignoreCase = true),
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferenceChoiceFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        content()
    }
}
