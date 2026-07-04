package com.example.ui.screens.contacts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.usecase.UpdateContactPreferencesUseCase

@Composable
internal fun ContactPreferencesDialog(
    contact: ContactDetailProfile,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (UpdateContactPreferencesUseCase.Request) -> Unit,
) {
    var nickname by remember(contact.id.value) { mutableStateOf(contact.nickname.orEmpty()) }
    var relationshipType by remember(contact.id.value) { mutableStateOf(contact.relationshipType) }
    var language by remember(contact.id.value) { mutableStateOf(contact.preferredLanguage) }
    var channel by remember(contact.id.value) { mutableStateOf(contact.preferredChannel.toSupportedContactMessageChannel()) }
    var formality by remember(contact.id.value) { mutableStateOf(contact.formalityLevel) }
    var style by remember(contact.id.value) { mutableStateOf(contact.communicationStyle) }
    var automationMode by remember(contact.id.value) { mutableStateOf(contact.automationMode.toSupportedContactApprovalMode()) }
    var sendTime by remember(contact.id.value) {
        mutableStateOf(
            if (contact.customSendTimeHour != null && contact.customSendTimeMinute != null) {
                "%02d:%02d".format(contact.customSendTimeHour, contact.customSendTimeMinute)
            } else {
                ""
            }
        )
    }
    var giftBudget by remember(contact.id.value) { mutableStateOf(contact.giftBudgetInr.toString()) }
    var annualBudget by remember(contact.id.value) { mutableStateOf(contact.annualBudgetInr.toString()) }
    var skipAutoWish by remember(contact.id.value) { mutableStateOf(contact.skipAutoWish) }
    var interests by remember(contact.id.value) { mutableStateOf(contact.interestsJson.toCsvList()) }
    var sensitiveTopics by remember(contact.id.value) { mutableStateOf(contact.sensitiveTopicsJson.toCsvList()) }
    var lifePhase by remember(contact.id.value) { mutableStateOf(contact.currentLifePhaseJson.lifePhaseLabel()) }
    var notes by remember(contact.id.value) { mutableStateOf(contact.notesText) }
    var localError by remember(contact.id.value) { mutableStateOf<String?>(null) }
    val invalidSendTime = stringResource(R.string.contact_preferences_invalid_send_time)

    AlertDialog(
        modifier = Modifier.testTag(ContactDetailTestTags.PREFERENCES_DIALOG),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.contact_preferences_title),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            ContactPreferencesDialogBody(
                nickname = nickname,
                onNicknameChange = { nickname = it },
                relationshipType = relationshipType,
                onRelationshipTypeChange = { relationshipType = it },
                language = language,
                onLanguageChange = { language = it },
                channel = channel,
                onChannelChange = { channel = it },
                formality = formality,
                onFormalityChange = { formality = it },
                style = style,
                onStyleChange = { style = it },
                automationMode = automationMode,
                onAutomationModeChange = { automationMode = it },
                sendTime = sendTime,
                onSendTimeChange = { sendTime = it },
                giftBudget = giftBudget,
                onGiftBudgetChange = { giftBudget = it.filter(Char::isDigit) },
                annualBudget = annualBudget,
                onAnnualBudgetChange = { annualBudget = it.filter(Char::isDigit) },
                interests = interests,
                onInterestsChange = { interests = it },
                sensitiveTopics = sensitiveTopics,
                onSensitiveTopicsChange = { sensitiveTopics = it },
                lifePhase = lifePhase,
                onLifePhaseChange = { lifePhase = it },
                notes = notes,
                onNotesChange = { notes = it },
                skipAutoWish = skipAutoWish,
                onSkipAutoWishChange = { skipAutoWish = it },
                localError = localError,
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                modifier = Modifier.testTag(ContactDetailTestTags.PREFERENCES_SAVE),
                onClick = {
                    val parsedTime = sendTime.parseSendTime()
                    if (sendTime.isNotBlank() && parsedTime == null) {
                        localError = invalidSendTime
                        return@TextButton
                    }
                    localError = null
                    onSave(
                        contact.toPreferenceRequest().copy(
                            nickname = nickname,
                            relationshipType = relationshipType,
                            preferredLanguage = language,
                            preferredChannel = channel,
                            formalityLevel = formality,
                            communicationStyle = style,
                            automationMode = automationMode,
                            customSendTimeHour = parsedTime?.first,
                            customSendTimeMinute = parsedTime?.second,
                            giftBudgetInr = giftBudget.toIntOrNull() ?: contact.giftBudgetInr,
                            annualBudgetInr = annualBudget.toIntOrNull() ?: contact.annualBudgetInr,
                            skipAutoWish = skipAutoWish,
                            interests = interests,
                            sensitiveTopics = sensitiveTopics,
                            currentLifePhase = lifePhase,
                            notes = notes,
                        )
                    )
                },
            ) {
                Text(if (isSaving) stringResource(R.string.saving) else stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(ContactDetailTestTags.PREFERENCES_CANCEL),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

internal fun ContactDetailProfile.toPreferenceRequest(): UpdateContactPreferencesUseCase.Request =
    UpdateContactPreferencesUseCase.Request(
        contactId = id.value,
        nickname = nickname.orEmpty(),
        relationshipType = relationshipType,
        preferredLanguage = preferredLanguage,
        preferredChannel = preferredChannel.toSupportedContactMessageChannel(),
        formalityLevel = formalityLevel,
        communicationStyle = communicationStyle,
        automationMode = automationMode.toSupportedContactApprovalMode(),
        customSendTimeHour = customSendTimeHour,
        customSendTimeMinute = customSendTimeMinute,
        giftBudgetInr = giftBudgetInr,
        annualBudgetInr = annualBudgetInr,
        skipAutoWish = skipAutoWish,
        interests = interestsJson.toCsvList(),
        sensitiveTopics = sensitiveTopicsJson.toCsvList(),
        currentLifePhase = currentLifePhaseJson.lifePhaseLabel(),
        notes = notesText,
    )

private fun String.parseSendTime(): Pair<Int, Int>? {
    val parts = trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return hour to minute
}

private fun ApprovalMode.toSupportedContactApprovalMode(): ApprovalMode {
    return takeIf { it != ApprovalMode.UNKNOWN } ?: ApprovalMode.DEFAULT
}

private fun MessageChannel.toSupportedContactMessageChannel(): MessageChannel {
    return takeIf { it != MessageChannel.UNKNOWN } ?: MessageChannel.SMS
}

private fun String.toCsvList(): String {
    return try {
        val array = org.json.JSONArray(this)
        List(array.length()) { array.getString(it) }.joinToString(", ")
    } catch (_: Exception) {
        ""
    }
}

private fun String.lifePhaseLabel(): String {
    return try {
        org.json.JSONObject(this).optString("phase")
    } catch (_: Exception) {
        ""
    }
}
