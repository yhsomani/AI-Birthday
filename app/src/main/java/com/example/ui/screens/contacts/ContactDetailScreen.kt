package com.example.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.memory.MemoryNoteCategoryCount
import com.example.ui.viewmodel.ContactDetailUiState
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
    const val CONTENT_BOTTOM = "contact_detail_content_bottom"
    const val PREFERENCES_DIALOG = "contact_preferences_dialog"
    const val PREFERENCES_FORM_BODY = "contact_preferences_form_body"
    const val PREFERENCES_NOTES_FIELD = "contact_preferences_notes_field"
    const val PREFERENCES_SKIP_AUTO_WISH = "contact_preferences_skip_auto_wish"
    const val PREFERENCES_SAVE = "contact_preferences_save"
    const val PREFERENCES_CANCEL = "contact_preferences_cancel"
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

    ContactDetailContent(
        contactId = contactId,
        state = state,
        onBack = onBack,
        onNavigateToMemoryVault = onNavigateToMemoryVault,
        onNavigateToGiftAdvisor = onNavigateToGiftAdvisor,
        onNavigateToChatHistory = onNavigateToChatHistory,
        onEditPreferences = { showPreferencesEditor = true },
        onGenerateWish = { viewModel.generateWish() },
        onMarkVip = { contact ->
            viewModel.savePreferences(
                contact.toPreferenceRequest().copy(automationMode = ApprovalMode.VIP_APPROVE),
            )
        },
        onSetWhatsApp = { contact ->
            viewModel.savePreferences(
                contact.toPreferenceRequest().copy(preferredChannel = MessageChannel.WHATSAPP),
            )
        },
        onSetSms = { contact ->
            viewModel.savePreferences(
                contact.toPreferenceRequest().copy(preferredChannel = MessageChannel.SMS),
            )
        },
    )

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
internal fun ContactDetailContent(
    contactId: String,
    state: ContactDetailUiState,
    onBack: () -> Unit = {},
    onNavigateToMemoryVault: (String) -> Unit = {},
    onNavigateToGiftAdvisor: (String) -> Unit = {},
    onNavigateToChatHistory: (String) -> Unit = {},
    onEditPreferences: () -> Unit = {},
    onGenerateWish: () -> Unit = {},
    onMarkVip: (ContactDetailProfile) -> Unit = {},
    onSetWhatsApp: (ContactDetailProfile) -> Unit = {},
    onSetSms: (ContactDetailProfile) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RelateSpacing.sm, vertical = RelateSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        if (state.isLoading) {
            ContactDetailLoadingState()
        } else {
            val contact = state.contact

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RelateSpacing.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val displayName = contact?.displayName ?: contactId
                ContactDetailProfileHeader(
                    displayName = displayName,
                    contact = contact,
                )

                contact?.let { currentContact ->
                    Spacer(modifier = Modifier.height(RelateSpacing.xl))
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
                        onEditPreferences = onEditPreferences,
                        onGenerateWish = onGenerateWish,
                        onMarkVip = { onMarkVip(currentContact) },
                        onSetWhatsApp = { onSetWhatsApp(currentContact) },
                        onSetSms = { onSetSms(currentContact) },
                    )
                }

                Spacer(
                    modifier = Modifier
                        .height(RelateSpacing.xl)
                        .testTag(ContactDetailTestTags.CONTENT_BOTTOM),
                )
            }
        }
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
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.xl),
    ) {
        ContactDetailSection(
            titleRes = R.string.contact_detail_section_essentials,
            testTag = ContactDetailTestTags.SECTION_ESSENTIALS,
        ) {
            ContactInfoCard(contact = contact)
            Spacer(modifier = Modifier.height(RelateSpacing.md))
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
            Spacer(modifier = Modifier.height(RelateSpacing.md))
            PersonalizationActionsCard(
                onAddMemory = { onNavigateToMemoryVault(contactId) },
                onAddGift = { onNavigateToGiftAdvisor(contactId) },
                onEditPreferences = onEditPreferences,
            )
            preferenceMessageRes?.let { messageRes ->
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            preferenceErrorRes?.let { errorRes ->
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
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
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        content()
    }
}
