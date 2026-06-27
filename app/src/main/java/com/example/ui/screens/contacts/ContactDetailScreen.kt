package com.example.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.platform.testTag
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
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.memory.MemoryNoteCategoryCount
import com.example.domain.usecase.UpdateContactPreferencesUseCase
import com.example.ui.viewmodel.ContactDetailViewModel

internal object ContactDetailTestTags {
    const val PERSONALIZATION_ADD_MEMORY = "contact_detail_personalization_add_memory"
    const val SECTION_ESSENTIALS = "contact_detail_section_essentials"
    const val SECTION_PERSONALIZATION = "contact_detail_section_personalization"
    const val SECTION_AUTOMATION = "contact_detail_section_automation"
    const val SECTION_HISTORY = "contact_detail_section_history"
    const val ACTION_ADD_MEMORY = "contact_detail_action_add_memory"
    const val ACTION_ADD_GIFT = "contact_detail_action_add_gift"
    const val ACTION_EDIT_PREFERENCES = "contact_detail_action_edit_preferences"
}

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

                val displayName = contact?.displayName ?: contactId
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
                val group = contact?.contactGroup ?: contact?.relationshipType.orEmpty()
                Text(
                    text = group,
                    style = MaterialTheme.typography.bodyLarge,
                    color = RelateOnSurfaceVariant,
                )

                contact?.let { currentContact ->
                    Spacer(modifier = Modifier.height(24.dp))
                    ContactDetailBodySections(
                        contactId = contactId,
                        contact = currentContact,
                        memoryNoteCount = state.memoryNoteCount,
                        memoryNoteCategorySummary = state.memoryNoteCategorySummary,
                        upcomingEventDaysLeft = state.upcomingEventDaysLeft,
                        isGenerating = state.isGenerating,
                        generationErrorRes = state.generationErrorRes,
                        preferenceMessageRes = state.preferenceMessageRes,
                        preferenceErrorRes = state.preferenceErrorRes,
                        onNavigateToMemoryVault = onNavigateToMemoryVault,
                        onNavigateToGiftAdvisor = onNavigateToGiftAdvisor,
                        onNavigateToChatHistory = onNavigateToChatHistory,
                        onEditPreferences = { showPreferencesEditor = true },
                        onGenerateWish = { viewModel.generateWish() },
                        onMarkVip = {
                            viewModel.savePreferences(
                                currentContact.toPreferenceRequest().copy(automationMode = ApprovalMode.VIP_APPROVE),
                            )
                        },
                        onSetWhatsApp = {
                            viewModel.savePreferences(
                                currentContact.toPreferenceRequest().copy(preferredChannel = MessageChannel.WHATSAPP),
                            )
                        },
                        onSetSms = {
                            viewModel.savePreferences(
                                currentContact.toPreferenceRequest().copy(preferredChannel = MessageChannel.SMS),
                            )
                        },
                    )
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
internal fun ContactDetailBodySections(
    contactId: String,
    contact: ContactDetailProfile,
    memoryNoteCount: Int = 0,
    memoryNoteCategorySummary: List<MemoryNoteCategoryCount> = emptyList(),
    upcomingEventDaysLeft: Int? = null,
    isGenerating: Boolean = false,
    generationErrorRes: Int? = null,
    preferenceMessageRes: Int? = null,
    preferenceErrorRes: Int? = null,
    onNavigateToMemoryVault: (String) -> Unit = {},
    onNavigateToGiftAdvisor: (String) -> Unit = {},
    onNavigateToChatHistory: (String) -> Unit = {},
    onEditPreferences: () -> Unit = {},
    onGenerateWish: () -> Unit = {},
    onMarkVip: () -> Unit = {},
    onSetWhatsApp: () -> Unit = {},
    onSetSms: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ContactDetailSection(
            titleRes = R.string.contact_detail_section_essentials,
            testTag = ContactDetailTestTags.SECTION_ESSENTIALS,
        ) {
            ContactInfoCard(contact = contact)
            Spacer(modifier = Modifier.height(12.dp))
            UpcomingWishCard(
                upcomingEventDaysLeft = upcomingEventDaysLeft,
                isGenerating = isGenerating,
                generationErrorRes = generationErrorRes,
                onGenerateWish = onGenerateWish,
            )
        }

        ContactDetailSection(
            titleRes = R.string.contact_detail_section_personalization,
            testTag = ContactDetailTestTags.SECTION_PERSONALIZATION,
        ) {
            PersonalizationQualityCard(
                contact = contact,
                memoryNoteCount = memoryNoteCount,
                memoryNoteCategorySummary = memoryNoteCategorySummary,
                onAddMemory = { onNavigateToMemoryVault(contactId) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PersonalizationActionsCard(
                onAddMemory = { onNavigateToMemoryVault(contactId) },
                onAddGift = { onNavigateToGiftAdvisor(contactId) },
                onEditPreferences = onEditPreferences,
            )
            preferenceMessageRes?.let { messageRes ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = RelatePrimary,
                )
            }
            preferenceErrorRes?.let { errorRes ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(errorRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        ContactDetailSection(
            titleRes = R.string.contact_detail_section_automation,
            testTag = ContactDetailTestTags.SECTION_AUTOMATION,
        ) {
            AutomationActionsCard(
                contact = contact,
                onMarkVip = onMarkVip,
                onSetWhatsApp = onSetWhatsApp,
                onSetSms = onSetSms,
            )
        }

        ContactDetailSection(
            titleRes = R.string.contact_detail_section_history,
            testTag = ContactDetailTestTags.SECTION_HISTORY,
        ) {
            HistoryActionsCard(
                contactId = contactId,
                onNavigateToMemoryVault = onNavigateToMemoryVault,
                onNavigateToGiftAdvisor = onNavigateToGiftAdvisor,
                onNavigateToChatHistory = onNavigateToChatHistory,
            )
        }
    }
}

@Composable
private fun ContactDetailSection(
    titleRes: Int,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        SectionHeader(title = stringResource(titleRes))
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
internal fun PersonalizationQualityCard(
    contact: ContactDetailProfile,
    memoryNoteCount: Int = 0,
    memoryNoteCategorySummary: List<MemoryNoteCategoryCount> = emptyList(),
    onAddMemory: () -> Unit = {},
) {
    val checklist = listOf(
        PersonalizationQualityItem(
            labelRes = R.string.personalization_quality_nickname,
            promptRes = R.string.personalization_quality_add_nickname,
            isComplete = !contact.nickname.isNullOrBlank(),
        ),
        PersonalizationQualityItem(
            labelRes = R.string.personalization_quality_interests,
            promptRes = R.string.personalization_quality_add_interests,
            isComplete = contact.interestsJson.hasJsonArrayContent(),
        ),
        PersonalizationQualityItem(
            labelRes = R.string.personalization_quality_memory_notes,
            promptRes = R.string.personalization_quality_add_memory_notes,
            isComplete = memoryNoteCount > 0,
        ),
        PersonalizationQualityItem(
            labelRes = R.string.personalization_quality_channel,
            promptRes = R.string.personalization_quality_choose_channel,
            isComplete = contact.preferredChannel != MessageChannel.UNKNOWN,
        ),
    )
    val complete = checklist.count { it.isComplete }
    val score = (complete * 100) / checklist.size
    val nextPromptRes = checklist.firstOrNull { !it.isComplete }?.promptRes
    val impactRes = when {
        nextPromptRes == null -> R.string.personalization_quality_impact_ready
        score < 50 -> R.string.personalization_quality_impact_low
        else -> R.string.personalization_quality_impact_partial
    }

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
            Text(
                text = if (nextPromptRes == null) {
                    stringResource(R.string.personalization_quality_ready)
                } else {
                    stringResource(R.string.personalization_quality_next_step, stringResource(nextPromptRes))
                },
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
            Text(
                text = stringResource(impactRes),
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
            if (memoryNoteCount > 0) {
                Text(
                    text = stringResource(
                        R.string.personalization_quality_memory_summary,
                        memoryNoteCount,
                        memoryNoteCategorySummaryText(memoryNoteCategorySummary),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                )
            } else {
                Button(
                    onClick = onAddMemory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ContactDetailTestTags.PERSONALIZATION_ADD_MEMORY),
                    colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
                ) {
                    Text(
                        text = stringResource(R.string.personalization_quality_add_one_memory),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            checklist.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.isComplete) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (item.isComplete) RelatePrimary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isComplete) MaterialTheme.colorScheme.onSurface else RelateOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun memoryNoteCategorySummaryText(
    summary: List<MemoryNoteCategoryCount>,
): String {
    if (summary.isEmpty()) return stringResource(R.string.memory_category_general)
    val parts = mutableListOf<String>()
    for (item in summary) {
        val label = memoryCategoryLabel(item.category)
        parts += stringResource(R.string.personalization_quality_memory_category_count, label, item.count)
    }
    return parts.joinToString(", ")
}

@Composable
private fun memoryCategoryLabel(category: String): String {
    return when (category) {
        "PREFERENCE" -> stringResource(R.string.memory_category_preference)
        "EVENT" -> stringResource(R.string.memory_category_event)
        "GIFT" -> stringResource(R.string.memory_category_gift)
        "MILESTONE" -> stringResource(R.string.memory_category_milestone)
        else -> stringResource(R.string.memory_category_general)
    }
}

private data class PersonalizationQualityItem(
    val labelRes: Int,
    val promptRes: Int,
    val isComplete: Boolean,
)

@Composable
private fun ContactInfoCard(contact: ContactDetailProfile) {
    RelateGlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            contact.primaryPhone?.let {
                InfoRow(Icons.Filled.Phone, it)
                Spacer(modifier = Modifier.height(12.dp))
            }
            contact.primaryEmail?.let {
                InfoRow(Icons.Filled.Email, it)
                Spacer(modifier = Modifier.height(12.dp))
            }
            val birthdayMonth = contact.birthdayMonth
            val birthdayDay = contact.birthdayDay
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
}

@Composable
private fun UpcomingWishCard(
    upcomingEventDaysLeft: Int?,
    isGenerating: Boolean,
    generationErrorRes: Int?,
    onGenerateWish: () -> Unit,
) {
    RelateGlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = RelatePrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                val daysText = upcomingEventDaysLeft?.let {
                    stringResource(R.string.contact_detail_days_left_format, it)
                } ?: stringResource(R.string.contact_detail_no_upcoming_event)
                Text(
                    text = daysText,
                    style = MaterialTheme.typography.titleMedium,
                    color = RelatePrimary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (isGenerating) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = RelatePrimary)
                }
            } else {
                RelatePrimaryButton(
                    text = stringResource(R.string.contact_detail_generate_ai_wish),
                    onClick = onGenerateWish,
                )
            }
            generationErrorRes?.let { errorRes ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(errorRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonalizationActionsCard(
    onAddMemory: () -> Unit,
    onAddGift: () -> Unit,
    onEditPreferences: () -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.contact_detail_personalization_actions),
                style = MaterialTheme.typography.titleSmall,
                color = RelatePrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAddMemory,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ContactDetailTestTags.ACTION_ADD_MEMORY),
                    colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
                ) {
                    Text(stringResource(R.string.contact_detail_add_memory), color = MaterialTheme.colorScheme.onSurface)
                }
                Button(
                    onClick = onAddGift,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ContactDetailTestTags.ACTION_ADD_GIFT),
                    colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
                ) {
                    Text(stringResource(R.string.contact_detail_add_gift), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Button(
                onClick = onEditPreferences,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ContactDetailTestTags.ACTION_EDIT_PREFERENCES),
                colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
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
        }
    }
}

@Composable
private fun AutomationActionsCard(
    contact: ContactDetailProfile,
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
                text = stringResource(R.string.contact_detail_automation_actions),
                style = MaterialTheme.typography.titleSmall,
                color = RelatePrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onMarkVip,
                    modifier = Modifier.weight(1f),
                    enabled = contact.automationMode != ApprovalMode.VIP_APPROVE,
                    colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
                ) {
                    Text(stringResource(R.string.contact_detail_mark_vip), color = MaterialTheme.colorScheme.onSurface)
                }
                Button(
                    onClick = onSetWhatsApp,
                    modifier = Modifier.weight(1f),
                    enabled = contact.preferredChannel != MessageChannel.WHATSAPP,
                    colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
                ) {
                    Text(stringResource(R.string.contact_detail_set_whatsapp), color = MaterialTheme.colorScheme.onSurface)
                }
                Button(
                    onClick = onSetSms,
                    modifier = Modifier.weight(1f),
                    enabled = contact.preferredChannel != MessageChannel.SMS,
                    colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
                ) {
                    Text(stringResource(R.string.contact_detail_set_sms), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun HistoryActionsCard(
    contactId: String,
    onNavigateToMemoryVault: (String) -> Unit,
    onNavigateToGiftAdvisor: (String) -> Unit,
    onNavigateToChatHistory: (String) -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = { onNavigateToMemoryVault(contactId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
                ) {
                    Text(
                        text = stringResource(R.string.contact_detail_memory_vault),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Button(
                    onClick = { onNavigateToGiftAdvisor(contactId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
                ) {
                    Text(
                        text = stringResource(R.string.contact_detail_gift_advisor),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Button(
                onClick = { onNavigateToChatHistory(contactId) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant),
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
        }
    }
}

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
                        MessageChannel.SMS to stringResource(R.string.channel_sms),
                        MessageChannel.WHATSAPP to stringResource(R.string.channel_whatsapp),
                        MessageChannel.EMAIL to stringResource(R.string.channel_email),
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
                        ApprovalMode.DEFAULT to stringResource(R.string.automation_mode_default),
                        ApprovalMode.SMART_APPROVE to stringResource(R.string.automation_mode_smart_approve_default),
                        ApprovalMode.VIP_APPROVE to stringResource(R.string.automation_mode_vip_approve),
                        ApprovalMode.FULLY_AUTO to stringResource(R.string.automation_mode_fully_auto),
                        ApprovalMode.ALWAYS_ASK to stringResource(R.string.automation_mode_always_ask),
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
private fun ChoiceRow(
    titleRes: Int,
    options: List<Pair<MessageChannel, String>>,
    selected: MessageChannel,
    onSelect: (MessageChannel) -> Unit,
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
                    isSelected = selected == value,
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
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
                    isSelected = selected == value,
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
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

private fun ContactDetailProfile.toPreferenceRequest(): UpdateContactPreferencesUseCase.Request =
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
