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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.memory.MemoryNoteCategoryCount
import com.example.ui.viewmodel.ContactDetailUiState

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
