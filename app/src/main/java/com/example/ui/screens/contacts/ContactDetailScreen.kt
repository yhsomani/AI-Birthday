package com.example.ui.screens.contacts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
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
    openPreferencesOnStart: Boolean = false,
    onBack: () -> Unit = {},
    onNavigateToWish: (String) -> Unit = {},
    onNavigateToMemoryVault: (String) -> Unit = {},
    onNavigateToGiftAdvisor: (String) -> Unit = {},
    onNavigateToChatHistory: (String) -> Unit = {},
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPreferencesEditor by remember(contactId, openPreferencesOnStart) {
        mutableStateOf(openPreferencesOnStart)
    }

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
