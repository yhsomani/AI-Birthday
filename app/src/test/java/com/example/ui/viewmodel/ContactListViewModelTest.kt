package com.example.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactListItem
import com.example.domain.repository.ContactRepository
import com.example.domain.usecase.SyncContactsUseCase
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ContactListViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var contactRepository: ContactRepository

    @RelaxedMockK
    private lateinit var mockPreferencesRepository: com.example.domain.service.PreferencesRepository

    @RelaxedMockK
    private lateinit var syncContactsUseCase: SyncContactsUseCase

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var preferenceChanges: MutableSharedFlow<Unit>
    private var lastSyncError: String? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        preferenceChanges = MutableSharedFlow(extraBufferCapacity = 1)
        lastSyncError = null
        every { mockPreferencesRepository.observeChanges() } returns preferenceChanges
        every { mockPreferencesRepository.getLastSyncError() } answers { lastSyncError }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init collects contacts and clears loading`() = runTest(testDispatcher) {
        val contacts = listOf(
            contactListItem(id = "c_1", displayName = "Alice"),
            contactListItem(id = "c_2", displayName = "Bob")
        )
        every { contactRepository.getContactListItems() } returns flowOf(contacts)

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.contacts.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `preference changes immediately update sync error state`() = runTest(testDispatcher) {
        val contacts = MutableStateFlow(
            listOf(contactListItem(id = "c_1", displayName = "Alice")),
        )
        every { contactRepository.getContactListItems() } returns contacts

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.syncError)

        lastSyncError = "Contacts permission was denied."
        preferenceChanges.tryEmit(Unit)
        advanceUntilIdle()

        assertEquals("Contacts permission was denied.", viewModel.uiState.value.syncError)
        assertEquals(listOf("Alice"), viewModel.uiState.value.contacts.map { it.displayName })
    }

    @Test
    fun `refresh flips isRefreshing and settles back to false`() = runTest(testDispatcher) {
        every { contactRepository.getContactListItems() } returns flowOf(
            listOf(contactListItem(id = "c_1", displayName = "Alice")),
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(1, viewModel.uiState.value.contacts.size)
    }

    @Test
    fun `search filter and sort are applied in viewmodel`() = runTest(testDispatcher) {
        every { contactRepository.getContactListItems() } returns flowOf(
            listOf(
                contactListItem(id = "c_1", displayName = "Alice", relationshipType = "FAMILY", healthScore = 90),
                contactListItem(id = "c_2", displayName = "Bob", relationshipType = "WORK", healthScore = 30),
                contactListItem(id = "c_3", displayName = "Cara", contactGroup = "Friends", healthScore = 55),
            )
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.selectFilter(ContactFilter.FRIENDS)
        assertEquals(listOf("Cara"), viewModel.uiState.value.contacts.map { it.displayName })

        viewModel.selectFilter(ContactFilter.ALL)
        viewModel.updateSearchQuery("bo")
        assertEquals(listOf("Bob"), viewModel.uiState.value.contacts.map { it.displayName })

        viewModel.updateSearchQuery("")
        viewModel.selectSort(ContactSort.HEALTH_ASC)
        assertEquals(listOf("Bob", "Cara", "Alice"), viewModel.uiState.value.contacts.map { it.displayName })
    }

    @Test
    fun `action filters target missing relationship channel low health and vip contacts`() = runTest(testDispatcher) {
        every { contactRepository.getContactListItems() } returns flowOf(
            listOf(
                contactListItem(
                    id = "missing_relationship",
                    displayName = "Unknown Riley",
                    relationshipType = "UNKNOWN",
                    birthdayDay = 1,
                    birthdayMonth = 1,
                    primaryPhone = "+15550001",
                    notesText = "Met at a conference",
                    healthScore = 80,
                ),
                contactListItem(
                    id = "missing_channel",
                    displayName = "Email Missing",
                    relationshipType = "FRIEND",
                    birthdayDay = 2,
                    birthdayMonth = 2,
                    preferredChannel = MessageChannel.EMAIL,
                    notesText = "Prefers email",
                    healthScore = 75,
                ),
                contactListItem(
                    id = "low_health",
                    displayName = "Low Health",
                    relationshipType = "WORK",
                    birthdayDay = 3,
                    birthdayMonth = 3,
                    primaryPhone = "+15550003",
                    notesText = "Former teammate",
                    healthScore = 32,
                ),
                contactListItem(
                    id = "vip",
                    displayName = "Vip Maya",
                    relationshipType = "FAMILY",
                    birthdayDay = 4,
                    birthdayMonth = 4,
                    primaryPhone = "+15550004",
                    notesText = "Close family",
                    automationMode = ApprovalMode.VIP_APPROVE,
                    healthScore = 92,
                ),
            ),
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.selectFilter(ContactFilter.MISSING_RELATIONSHIP)
        assertEquals(listOf("Unknown Riley"), viewModel.uiState.value.contacts.map { it.displayName })

        viewModel.selectFilter(ContactFilter.MISSING_CHANNEL)
        assertEquals(listOf("Email Missing"), viewModel.uiState.value.contacts.map { it.displayName })

        viewModel.selectFilter(ContactFilter.LOW_HEALTH)
        assertEquals(listOf("Low Health"), viewModel.uiState.value.contacts.map { it.displayName })

        viewModel.selectFilter(ContactFilter.VIP)
        assertEquals(listOf("Vip Maya"), viewModel.uiState.value.contacts.map { it.displayName })
    }

    @Test
    fun `contact quality state captures missing event channel context and ready contacts`() = runTest(testDispatcher) {
        every { contactRepository.getContactListItems() } returns flowOf(
            listOf(
                contactListItem(
                    id = "ready",
                    displayName = "Ready Contact",
                    birthdayDay = 10,
                    birthdayMonth = 4,
                    primaryPhone = "+15550001",
                    notesText = "College friend",
                ),
                contactListItem(
                    id = "missing_event",
                    displayName = "Missing Event",
                    primaryPhone = "+15550002",
                    notesText = "Works together",
                ),
                contactListItem(
                    id = "missing_channel",
                    displayName = "Missing Channel",
                    birthdayDay = 12,
                    birthdayMonth = 5,
                    preferredChannel = MessageChannel.EMAIL,
                    notesText = "Prefers email",
                ),
                contactListItem(
                    id = "missing_context",
                    displayName = "Missing Context",
                    birthdayDay = 14,
                    birthdayMonth = 6,
                    primaryPhone = "+15550004",
                ),
            ),
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        val quality = viewModel.uiState.value.contactQuality
        assertEquals(ContactQualityStatus.READY, quality.getValue("ready").status)
        assertTrue(quality.getValue("ready").hasKnownEvent)
        assertTrue(quality.getValue("ready").hasReachableChannel)
        assertTrue(quality.getValue("ready").hasPersonalizationContext)

        assertEquals(ContactQualityStatus.MISSING_EVENT, quality.getValue("missing_event").status)
        assertFalse(quality.getValue("missing_event").hasKnownEvent)

        assertEquals(ContactQualityStatus.MISSING_CHANNEL, quality.getValue("missing_channel").status)
        assertFalse(quality.getValue("missing_channel").hasReachableChannel)

        assertEquals(ContactQualityStatus.MISSING_CONTEXT, quality.getValue("missing_context").status)
        assertFalse(quality.getValue("missing_context").hasPersonalizationContext)
    }

    private fun contactListItem(
        id: String,
        displayName: String,
        nickname: String? = null,
        company: String? = null,
        contactGroup: String? = null,
        relationshipType: String = "UNKNOWN",
        healthScore: Int = 80,
        automationMode: ApprovalMode = ApprovalMode.UNKNOWN,
        preferredChannel: MessageChannel = MessageChannel.UNKNOWN,
        primaryPhone: String? = null,
        secondaryPhone: String? = null,
        primaryEmail: String? = null,
        birthdayDay: Int? = null,
        birthdayMonth: Int? = null,
        anniversaryDay: Int? = null,
        anniversaryMonth: Int? = null,
        workStartDay: Int? = null,
        workStartMonth: Int? = null,
        notesText: String = "",
        interestsJson: String = "[]",
        sharedHistoryJson: String = "[]",
        classificationConfidence: Double = 0.0,
    ) = ContactListItem(
        id = ContactId(id),
        displayName = displayName,
        nickname = nickname,
        company = company,
        contactGroup = contactGroup,
        relationshipType = relationshipType,
        healthScore = healthScore,
        automationMode = automationMode,
        preferredChannel = preferredChannel,
        primaryPhone = primaryPhone,
        secondaryPhone = secondaryPhone,
        primaryEmail = primaryEmail,
        birthdayDay = birthdayDay,
        birthdayMonth = birthdayMonth,
        anniversaryDay = anniversaryDay,
        anniversaryMonth = anniversaryMonth,
        workStartDay = workStartDay,
        workStartMonth = workStartMonth,
        notesText = notesText,
        interestsJson = interestsJson,
        sharedHistoryJson = sharedHistoryJson,
        classificationConfidence = classificationConfidence,
    )

    private fun newViewModel(): ContactListViewModel {
        return ContactListViewModel(
            appContext = context,
            contactRepository = contactRepository,
            preferencesRepository = mockPreferencesRepository,
            syncContactsUseCase = syncContactsUseCase,
        )
    }
}
