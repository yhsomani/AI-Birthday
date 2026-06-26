package com.example.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.db.entities.ContactEntity
import com.example.domain.model.MessageChannel
import com.example.domain.repository.ContactRepository
import com.example.domain.usecase.SyncContactsUseCase
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        every { mockPreferencesRepository.getLastSyncError() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init collects contacts and clears loading`() = runTest(testDispatcher) {
        val contacts = listOf(
            ContactEntity(id = "c_1", name = "Alice"),
            ContactEntity(id = "c_2", name = "Bob")
        )
        every { contactRepository.getAll() } returns flowOf(contacts)

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.contacts.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `refresh flips isRefreshing and settles back to false`() = runTest(testDispatcher) {
        every { contactRepository.getAll() } returns flowOf(listOf(ContactEntity(id = "c_1", name = "Alice")))

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(1, viewModel.uiState.value.contacts.size)
    }

    @Test
    fun `search filter and sort are applied in viewmodel`() = runTest(testDispatcher) {
        every { contactRepository.getAll() } returns flowOf(
            listOf(
                ContactEntity(id = "c_1", name = "Alice", relationshipType = "FAMILY", healthScore = 90),
                ContactEntity(id = "c_2", name = "Bob", relationshipType = "WORK", healthScore = 30),
                ContactEntity(id = "c_3", name = "Cara", contactGroup = "Friends", healthScore = 55),
            )
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.selectFilter(ContactFilter.FRIENDS)
        assertEquals(listOf("Cara"), viewModel.uiState.value.contacts.map { it.name })

        viewModel.selectFilter(ContactFilter.ALL)
        viewModel.updateSearchQuery("bo")
        assertEquals(listOf("Bob"), viewModel.uiState.value.contacts.map { it.name })

        viewModel.updateSearchQuery("")
        viewModel.selectSort(ContactSort.HEALTH_ASC)
        assertEquals(listOf("Bob", "Cara", "Alice"), viewModel.uiState.value.contacts.map { it.name })
    }

    @Test
    fun `action filters target missing relationship channel low health and vip contacts`() = runTest(testDispatcher) {
        every { contactRepository.getAll() } returns flowOf(
            listOf(
                ContactEntity(
                    id = "missing_relationship",
                    name = "Unknown Riley",
                    relationshipType = "UNKNOWN",
                    birthdayDay = 1,
                    birthdayMonth = 1,
                    primaryPhone = "+15550001",
                    notesText = "Met at a conference",
                    healthScore = 80,
                ),
                ContactEntity(
                    id = "missing_channel",
                    name = "Email Missing",
                    relationshipType = "FRIEND",
                    birthdayDay = 2,
                    birthdayMonth = 2,
                    preferredChannel = MessageChannel.EMAIL.raw,
                    notesText = "Prefers email",
                    healthScore = 75,
                ),
                ContactEntity(
                    id = "low_health",
                    name = "Low Health",
                    relationshipType = "WORK",
                    birthdayDay = 3,
                    birthdayMonth = 3,
                    primaryPhone = "+15550003",
                    notesText = "Former teammate",
                    healthScore = 32,
                ),
                ContactEntity(
                    id = "vip",
                    name = "Vip Maya",
                    relationshipType = "FAMILY",
                    birthdayDay = 4,
                    birthdayMonth = 4,
                    primaryPhone = "+15550004",
                    notesText = "Close family",
                    automationMode = "VIP_APPROVE",
                    healthScore = 92,
                ),
            ),
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.selectFilter(ContactFilter.MISSING_RELATIONSHIP)
        assertEquals(listOf("Unknown Riley"), viewModel.uiState.value.contacts.map { it.name })

        viewModel.selectFilter(ContactFilter.MISSING_CHANNEL)
        assertEquals(listOf("Email Missing"), viewModel.uiState.value.contacts.map { it.name })

        viewModel.selectFilter(ContactFilter.LOW_HEALTH)
        assertEquals(listOf("Low Health"), viewModel.uiState.value.contacts.map { it.name })

        viewModel.selectFilter(ContactFilter.VIP)
        assertEquals(listOf("Vip Maya"), viewModel.uiState.value.contacts.map { it.name })
    }

    @Test
    fun `contact quality state captures missing event channel context and ready contacts`() = runTest(testDispatcher) {
        every { contactRepository.getAll() } returns flowOf(
            listOf(
                ContactEntity(
                    id = "ready",
                    name = "Ready Contact",
                    birthdayDay = 10,
                    birthdayMonth = 4,
                    primaryPhone = "+15550001",
                    notesText = "College friend",
                ),
                ContactEntity(
                    id = "missing_event",
                    name = "Missing Event",
                    primaryPhone = "+15550002",
                    notesText = "Works together",
                ),
                ContactEntity(
                    id = "missing_channel",
                    name = "Missing Channel",
                    birthdayDay = 12,
                    birthdayMonth = 5,
                    preferredChannel = MessageChannel.EMAIL.raw,
                    notesText = "Prefers email",
                ),
                ContactEntity(
                    id = "missing_context",
                    name = "Missing Context",
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

    private fun newViewModel(): ContactListViewModel {
        return ContactListViewModel(
            appContext = context,
            contactRepository = contactRepository,
            preferencesRepository = mockPreferencesRepository,
            syncContactsUseCase = syncContactsUseCase,
        )
    }
}
