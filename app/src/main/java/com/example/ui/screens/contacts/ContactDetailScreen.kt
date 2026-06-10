package com.example.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.HealthIndicatorDot
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelatePrimaryButton
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.FilterChip
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.core.db.entities.ContactEntity
import com.example.domain.usecase.UpdateContactPreferencesUseCase
import com.example.ui.viewmodel.ContactDetailViewModel

@Composable
fun ContactDetailScreen(
    contactId: String,
    onBack: () -> Unit = {},
    onNavigateToWish: (String) -> Unit = {},
    onNavigateToMemoryVault: (String) -> Unit = {},
    onNavigateToGiftAdvisor: (String) -> Unit = {},
    onNavigateToChatHistory: (String) -> Unit = {},
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPreferencesEditor by remember { mutableStateOf(false) }

    LaunchedEffect(state.generationResult) {
        state.generationResult?.let { pendingId ->
            onNavigateToWish(pendingId)
            viewModel.clearGenerationResult()
        }
    }

    LaunchedEffect(state.preferenceMessageRes) {
        if (
            showPreferencesEditor &&
            state.preferenceMessageRes == R.string.contact_detail_preferences_saved
        ) {
            showPreferencesEditor = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = RelateOnBackground,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = RelatePrimary)
            }
        } else {
            val contact = state.contact

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                val displayName = contact?.name ?: contactId
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(RelateSurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayName.take(1),
                        color = RelateOnBackground,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                contact?.let {
                    HealthIndicatorDot(health = it.healthScore / 100f, size = 14)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val group = contact?.contactGroup ?: contact?.relationshipType ?: ""
                Text(
                    text = group,
                    style = MaterialTheme.typography.bodyLarge,
                    color = RelateOnSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { onNavigateToMemoryVault(contactId) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)
                    ) {
                        Text(
                            text = stringResource(R.string.contact_detail_memory_vault),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Button(
                        onClick = { onNavigateToGiftAdvisor(contactId) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)
                    ) {
                        Text(
                            text = stringResource(R.string.contact_detail_gift_advisor),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onNavigateToChatHistory(contactId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.contact_detail_chat_history),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showPreferencesEditor = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.contact_detail_edit_preferences),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                contact?.let {
                    QuickPersonalizationCard(
                        contact = it,
                        onAddMemory = { onNavigateToMemoryVault(contactId) },
                        onAddGift = { onNavigateToGiftAdvisor(contactId) },
                        onMarkVip = {
                            viewModel.savePreferences(it.toPreferenceRequest().copy(automationMode = "VIP_APPROVE"))
                        },
                        onSetWhatsApp = {
                            viewModel.savePreferences(it.toPreferenceRequest().copy(preferredChannel = "WHATSAPP"))
                        },
                        onSetSms = {
                            viewModel.savePreferences(it.toPreferenceRequest().copy(preferredChannel = "SMS"))
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PersonalizationQualityCard(contact = it)
                }

                state.preferenceMessageRes?.let { messageRes ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelatePrimary,
                    )
                }
                state.preferenceError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = stringResource(R.string.contact_detail_contact_info))
                RelateGlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        contact?.primaryPhone?.let {
                            InfoRow(Icons.Filled.Phone, it)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        contact?.primaryEmail?.let {
                            InfoRow(Icons.Filled.Email, it)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        val birthdayMonth = contact?.birthdayMonth
                        val birthdayDay = contact?.birthdayDay
                        val birthday = if (birthdayMonth != null && birthdayDay != null) {
                            stringResource(R.string.contact_detail_birthday_date_format, birthdayMonth, birthdayDay)
                        } else {
                            stringResource(R.string.contact_detail_unknown)
                        }
                        InfoRow(
                            Icons.Filled.CalendarMonth,
                            stringResource(R.string.contact_detail_birthday_format, birthday),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = stringResource(R.string.contact_detail_next_birthday))
                RelateGlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = RelatePrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val daysText = state.upcomingBirthdayDaysLeft?.let {
                                stringResource(R.string.contact_detail_days_left_format, it)
                            } ?: stringResource(R.string.contact_detail_no_upcoming_event)
                            Text(
                                text = daysText,
                                style = MaterialTheme.typography.titleMedium,
                                color = RelatePrimary,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (state.isGenerating) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = RelatePrimary)
                            }
                        } else {
                            RelatePrimaryButton(
                                text = stringResource(R.string.contact_detail_generate_ai_wish),
                                onClick = { viewModel.generateWish() },
                            )
                        }
                        state.generationErrorRes?.let { errorRes ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(errorRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = RelateOnSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    val editorContact = state.contact
    if (showPreferencesEditor && editorContact != null) {
        ContactPreferencesDialog(
            contact = editorContact,
            isSaving = state.isSavingPreferences,
            onDismiss = { showPreferencesEditor = false },
            onSave = { request ->
                viewModel.savePreferences(request)
            },
        )
    }
}

@Composable
private fun PersonalizationQualityCard(contact: ContactEntity) {
    val checklist = listOf(
        R.string.personalization_quality_nickname to !contact.nickname.isNullOrBlank(),
        R.string.personalization_quality_interests to contact.interestsJson.hasJsonArrayContent(),
        R.string.personalization_quality_memory_notes to contact.notesText.isNotBlank(),
        R.string.personalization_quality_channel to contact.preferredChannel in setOf("SMS", "WHATSAPP", "EMAIL"),
    )
    val complete = checklist.count { it.second }
    val score = (complete * 100) / checklist.size

    RelateGlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.personalization_quality_title, score),
                style = MaterialTheme.typography.titleSmall,
                color = RelatePrimary,
                fontWeight = FontWeight.SemiBold,
            )
            checklist.forEach { (labelRes, isComplete) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isComplete) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (isComplete) RelatePrimary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isComplete) MaterialTheme.colorScheme.onSurface else RelateOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPersonalizationCard(
    contact: ContactEntity,
    onAddMemory: () -> Unit,
    onAddGift: () -> Unit,
    onMarkVip: () -> Unit,
    onSetWhatsApp: () -> Unit,
    onSetSms: () -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.contact_detail_quick_enrichment),
                style = MaterialTheme.typography.titleSmall,
                color = RelatePrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onAddMemory, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)) {
                    Text(stringResource(R.string.contact_detail_add_memory), color = MaterialTheme.colorScheme.onSurface)
                }
                Button(onClick = onAddGift, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)) {
                    Text(stringResource(R.string.contact_detail_add_gift), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onMarkVip, modifier = Modifier.weight(1f), enabled = contact.automationMode != "VIP_APPROVE", colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)) {
                    Text(stringResource(R.string.contact_detail_mark_vip), color = MaterialTheme.colorScheme.onSurface)
                }
                Button(onClick = onSetWhatsApp, modifier = Modifier.weight(1f), enabled = contact.preferredChannel != "WHATSAPP", colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)) {
                    Text(stringResource(R.string.contact_detail_set_whatsapp), color = MaterialTheme.colorScheme.onSurface)
                }
                Button(onClick = onSetSms, modifier = Modifier.weight(1f), enabled = contact.preferredChannel != "SMS", colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)) {
                    Text(stringResource(R.string.contact_detail_set_sms), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun ContactPreferencesDialog(
    contact: ContactEntity,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (UpdateContactPreferencesUseCase.Request) -> Unit,
) {
    var nickname by remember(contact.id) { mutableStateOf(contact.nickname.orEmpty()) }
    var relationshipType by remember(contact.id) { mutableStateOf(contact.relationshipType) }
    var language by remember(contact.id) { mutableStateOf(contact.preferredLanguage) }
    var channel by remember(contact.id) { mutableStateOf(contact.preferredChannel) }
    var formality by remember(contact.id) { mutableStateOf(contact.formalityLevel) }
    var style by remember(contact.id) { mutableStateOf(contact.communicationStyle) }
    var automationMode by remember(contact.id) { mutableStateOf(contact.automationMode) }
    var sendTime by remember(contact.id) {
        mutableStateOf(
            if (contact.customSendTimeHour != null && contact.customSendTimeMinute != null) {
                "%02d:%02d".format(contact.customSendTimeHour, contact.customSendTimeMinute)
            } else {
                ""
            }
        )
    }
    var giftBudget by remember(contact.id) { mutableStateOf(contact.giftBudgetInr.toString()) }
    var annualBudget by remember(contact.id) { mutableStateOf(contact.annualBudgetInr.toString()) }
    var skipAutoWish by remember(contact.id) { mutableStateOf(contact.skipAutoWish) }
    var interests by remember(contact.id) { mutableStateOf(contact.interestsJson.toCsvList()) }
    var sensitiveTopics by remember(contact.id) { mutableStateOf(contact.sensitiveTopicsJson.toCsvList()) }
    var lifePhase by remember(contact.id) { mutableStateOf(contact.currentLifePhaseJson.lifePhaseLabel()) }
    var notes by remember(contact.id) { mutableStateOf(contact.notesText) }
    var localError by remember(contact.id) { mutableStateOf<String?>(null) }
    val invalidSendTime = stringResource(R.string.contact_preferences_invalid_send_time)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contact_preferences_title)) },
        text = {
            Column(
                modifier = Modifier
                    .height(460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PreferenceField(R.string.contact_preferences_nickname, nickname) { nickname = it }
                PreferenceField(R.string.contact_preferences_relationship_type, relationshipType) { relationshipType = it }
                ChoiceRow(
                    titleRes = R.string.contact_preferences_language,
                    options = listOf(
                        "en" to stringResource(R.string.language_english),
                        "hi" to stringResource(R.string.language_hindi),
                    ),
                    selected = language,
                    onSelect = { language = it },
                )
                ChoiceRow(
                    titleRes = R.string.contact_preferences_channel,
                    options = listOf(
                        "SMS" to stringResource(R.string.channel_sms),
                        "WHATSAPP" to stringResource(R.string.channel_whatsapp),
                        "EMAIL" to stringResource(R.string.channel_email),
                    ),
                    selected = channel,
                    onSelect = { channel = it },
                )
                ChoiceRow(
                    titleRes = R.string.contact_preferences_formality,
                    options = listOf(
                        "CASUAL" to stringResource(R.string.formality_casual),
                        "SEMI_FORMAL" to stringResource(R.string.formality_semi_formal),
                        "FORMAL" to stringResource(R.string.formality_formal),
                    ),
                    selected = formality,
                    onSelect = { formality = it },
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
                    onSelect = { style = it },
                )
                ChoiceRow(
                    titleRes = R.string.contact_preferences_automation_mode,
                    options = listOf(
                        "DEFAULT" to stringResource(R.string.automation_mode_default),
                        "SMART_APPROVE" to stringResource(R.string.automation_mode_smart_approve_default),
                        "VIP_APPROVE" to stringResource(R.string.automation_mode_vip_approve),
                        "FULLY_AUTO" to stringResource(R.string.automation_mode_fully_auto),
                        "ALWAYS_ASK" to stringResource(R.string.automation_mode_always_ask),
                    ),
                    selected = automationMode,
                    onSelect = { automationMode = it },
                )
                PreferenceField(R.string.contact_preferences_send_time, sendTime) { sendTime = it }
                PreferenceField(R.string.contact_preferences_gift_budget, giftBudget) { giftBudget = it.filter(Char::isDigit) }
                PreferenceField(R.string.contact_preferences_annual_budget, annualBudget) { annualBudget = it.filter(Char::isDigit) }
                PreferenceField(R.string.contact_preferences_interests, interests) { interests = it }
                PreferenceField(R.string.contact_preferences_sensitive_topics, sensitiveTopics) { sensitiveTopics = it }
                PreferenceField(R.string.contact_preferences_life_phase, lifePhase) { lifePhase = it }
                PreferenceField(R.string.contact_preferences_notes, notes, minLines = 2) { notes = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.contact_preferences_skip_auto_wish),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = skipAutoWish, onCheckedChange = { skipAutoWish = it })
                }
                localError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PreferenceField(
    labelRes: Int,
    value: String,
    minLines: Int = 1,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        minLines = minLines,
        maxLines = if (minLines > 1) 4 else 1,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoiceRow(
    titleRes: Int,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodySmall,
            color = RelateOnSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    label = label,
                    isSelected = selected.equals(value, ignoreCase = true),
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun ContactEntity.toPreferenceRequest(): UpdateContactPreferencesUseCase.Request =
    UpdateContactPreferencesUseCase.Request(
        contactId = id,
        nickname = nickname.orEmpty(),
        relationshipType = relationshipType,
        preferredLanguage = preferredLanguage,
        preferredChannel = preferredChannel,
        formalityLevel = formalityLevel,
        communicationStyle = communicationStyle,
        automationMode = automationMode,
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

private fun String.toCsvList(): String {
    return try {
        val array = org.json.JSONArray(this)
        List(array.length()) { array.getString(it) }.joinToString(", ")
    } catch (_: Exception) {
        ""
    }
}

private fun String.hasJsonArrayContent(): Boolean {
    return try {
        org.json.JSONArray(this).length() > 0
    } catch (_: Exception) {
        trim().isNotBlank() && trim() != "[]"
    }
}

private fun String.lifePhaseLabel(): String {
    return try {
        org.json.JSONObject(this).optString("phase")
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = RelateOnSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
