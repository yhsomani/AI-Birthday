package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactListItem
import com.example.ui.screens.contacts.ContactListContent
import com.example.ui.viewmodel.ContactListUiState
import com.example.ui.viewmodel.ContactQualityState
import com.example.ui.viewmodel.ContactQualityStatus
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Category(ScreenshotTests::class)
@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xhdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactListScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contactListPopulated_compactPhone() {
        setContactListContent(state = populatedContactListState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_list_populated_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun contactListPopulated_typicalPhone() {
        setContactListContent(state = populatedContactListState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_list_populated_typical_phone.png")
    }

    @Test
    fun contactListSyncError_compactPhoneLargeFont() {
        setContactListContent(
            state = populatedContactListState(
                syncError = "Google Contacts sync failed. Check permissions and try again.",
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_list_sync_error_compact_phone_large_font.png")
    }

    @Test
    fun contactListLoading_compactPhone() {
        setContactListContent(state = ContactListUiState(isLoading = true))

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_list_loading_compact_phone.png")
    }

    @Test
    fun contactListEmpty_compactPhone() {
        setContactListContent(state = ContactListUiState(isLoading = false))

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_list_empty_compact_phone.png")
    }

    private fun setContactListContent(
        state: ContactListUiState,
        fontScale: Float = DefaultFontScale,
    ) {
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            ContactListContent(
                state = state,
                onContactClick = {},
                onSearchQueryChange = {},
                onClearSearch = {},
                onFilterSelected = {},
                onSortSelected = {},
                onRefresh = {},
                onDismissSyncError = {},
            )
        }
    }

    private fun populatedContactListState(syncError: String? = null): ContactListUiState {
        val contacts = listOf(
            contactListItem(
                id = "asha",
                displayName = "Asha Mehra",
                relationshipType = "FAMILY",
                contactGroup = "Family",
                healthScore = 92,
                automationMode = ApprovalMode.VIP_APPROVE,
                preferredChannel = MessageChannel.WHATSAPP,
                primaryPhone = "+15550100",
                birthdayDay = 12,
                birthdayMonth = 7,
                notesText = "Sister, likes quiet dinners.",
            ),
            contactListItem(
                id = "ben",
                displayName = "Ben Carter",
                relationshipType = "FRIEND",
                contactGroup = "Friends",
                healthScore = 68,
                preferredChannel = MessageChannel.EMAIL,
                primaryEmail = "ben@example.com",
                anniversaryDay = 5,
                anniversaryMonth = 10,
                notesText = "College friend.",
            ),
            contactListItem(
                id = "dev",
                displayName = "Dev Sharma",
                relationshipType = "WORK",
                contactGroup = "Work",
                healthScore = 34,
                preferredChannel = MessageChannel.SMS,
                primaryPhone = "+15550102",
            ),
        )
        return ContactListUiState(
            allContacts = contacts,
            contacts = contacts,
            contactQuality = mapOf(
                "asha" to ContactQualityState(
                    status = ContactQualityStatus.READY,
                    hasKnownEvent = true,
                    hasReachableChannel = true,
                    hasPersonalizationContext = true,
                ),
                "ben" to ContactQualityState(
                    status = ContactQualityStatus.MISSING_CHANNEL,
                    hasKnownEvent = true,
                    hasReachableChannel = false,
                    hasPersonalizationContext = true,
                ),
                "dev" to ContactQualityState(
                    status = ContactQualityStatus.MISSING_CONTEXT,
                    hasKnownEvent = true,
                    hasReachableChannel = true,
                    hasPersonalizationContext = false,
                ),
            ),
            isLoading = false,
            syncError = syncError,
        )
    }

    private fun contactListItem(
        id: String,
        displayName: String,
        relationshipType: String,
        contactGroup: String?,
        healthScore: Int,
        automationMode: ApprovalMode = ApprovalMode.UNKNOWN,
        preferredChannel: MessageChannel = MessageChannel.UNKNOWN,
        primaryPhone: String? = null,
        primaryEmail: String? = null,
        birthdayDay: Int? = null,
        birthdayMonth: Int? = null,
        anniversaryDay: Int? = null,
        anniversaryMonth: Int? = null,
        notesText: String = "",
    ) = ContactListItem(
        id = ContactId(id),
        displayName = displayName,
        nickname = null,
        company = null,
        contactGroup = contactGroup,
        relationshipType = relationshipType,
        healthScore = healthScore,
        automationMode = automationMode,
        preferredChannel = preferredChannel,
        primaryPhone = primaryPhone,
        secondaryPhone = null,
        primaryEmail = primaryEmail,
        birthdayDay = birthdayDay,
        birthdayMonth = birthdayMonth,
        anniversaryDay = anniversaryDay,
        anniversaryMonth = anniversaryMonth,
        workStartDay = null,
        workStartMonth = null,
        notesText = notesText,
        interestsJson = "[]",
        sharedHistoryJson = "[]",
        classificationConfidence = 0.82,
    )
}
