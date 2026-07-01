package com.example.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.contact.ContactPickerItem
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.OccasionType
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.ResolveEventConflictUseCase
import com.example.domain.usecase.SaveManualEventUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EventsViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var eventRepository: EventRepository

    @RelaxedMockK
    private lateinit var contactRepository: ContactRepository

    @RelaxedMockK
    private lateinit var saveManualEventUseCase: SaveManualEventUseCase

    @RelaxedMockK
    private lateinit var resolveEventConflictUseCase: ResolveEventConflictUseCase

    @RelaxedMockK
    private lateinit var activityLogRepository: ActivityLogRepository

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        every { eventRepository.getEventListItems() } returns MutableStateFlow(emptyList())
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init collects events and contacts`() = runTest(testDispatcher) {
        every { eventRepository.getEventListItems() } returns MutableStateFlow(
            listOf(eventListItem(id = "e1", contactId = "c1", type = OccasionType.BIRTHDAY, dayOfMonth = 5, month = 6, nextOccurrenceMs = 100L))
        )
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(
            listOf(contactPickerItem(id = "c1", displayName = "Alice"))
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.events.size)
        assertEquals(1, viewModel.uiState.value.contacts.size)
    }

    @Test
    fun `init derives event trust states from source verification and active conflicts`() = runTest(testDispatcher) {
        val events = listOf(
            eventListItem(
                id = "imported",
                contactId = "c1",
                type = OccasionType.BIRTHDAY,
                dayOfMonth = 5,
                month = 6,
                nextOccurrenceMs = 100L,
                source = "CONTACTS",
                isVerified = true,
            ),
            eventListItem(
                id = "manual_conflict",
                contactId = "c1",
                type = OccasionType.BIRTHDAY,
                dayOfMonth = 1,
                month = 7,
                nextOccurrenceMs = 200L,
                source = "MANUAL",
                isVerified = true,
            ),
            eventListItem(
                id = "low_confidence",
                contactId = "c2",
                type = OccasionType.ANNIVERSARY,
                dayOfMonth = 2,
                month = 8,
                nextOccurrenceMs = 300L,
                source = "CONTACTS",
                confidenceScore = 62,
                isVerified = false,
            ),
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(events)
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(
            listOf(
                contactPickerItem(id = "c1", displayName = "Alice"),
                contactPickerItem(id = "c2", displayName = "Bob"),
            )
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        val trust = viewModel.uiState.value.eventTrust
        assertEquals(EventVerificationState.CONFLICT, trust["imported"]?.verification)
        assertEquals(EventTrustConflictState.DATE_CONFLICT, trust["imported"]?.conflict)
        assertEquals(EventVerificationState.CONFLICT, trust["manual_conflict"]?.verification)
        assertEquals(EventTrustConflictState.DATE_CONFLICT, trust["manual_conflict"]?.conflict)
        assertEquals(EventVerificationState.NEEDS_REVIEW, trust["low_confidence"]?.verification)
        assertEquals(EventTrustConflictState.NONE, trust["low_confidence"]?.conflict)
        assertEquals(62, trust["low_confidence"]?.confidenceScore)
    }

    @Test
    fun `saveManualEvent success exposes snackbar message`() = runTest(testDispatcher) {
        val contact = contactHeader(id = "c1", displayName = "Alice")
        val event = eventListItem(id = "e1", contactId = "c1", type = OccasionType.BIRTHDAY, dayOfMonth = 5, month = 6, nextOccurrenceMs = 100L)
        every { eventRepository.getEventListItems() } returns MutableStateFlow(emptyList())
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(listOf(contactPickerItem("c1", "Alice")))
        coEvery { saveManualEventUseCase(any()) } returns SaveManualEventUseCase.Outcome.Saved(contact, event)

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.saveManualEvent("c1", null, "BIRTHDAY", null, 6, 5, null)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveMessage?.contains("Alice") == true)
        assertFalse(viewModel.uiState.value.isSavingManualEvent)
    }

    @Test
    fun `saveManualEvent invalid input exposes error`() = runTest(testDispatcher) {
        every { eventRepository.getEventListItems() } returns MutableStateFlow(emptyList())
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(emptyList())
        coEvery { saveManualEventUseCase(any()) } returns SaveManualEventUseCase.Outcome.InvalidInput(
            SaveManualEventUseCase.InvalidInputReason.INVALID_DATE,
        )

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.saveManualEvent(null, "Bad Date", "BIRTHDAY", null, 2, 30, null)
        advanceUntilIdle()

        assertEquals(context.getString(R.string.events_error_invalid_date), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSavingManualEvent)
    }

    @Test
    fun `saveManualEvent unsupported event type exposes error`() = runTest(testDispatcher) {
        every { eventRepository.getEventListItems() } returns MutableStateFlow(emptyList())
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(emptyList())
        coEvery { saveManualEventUseCase(any()) } returns SaveManualEventUseCase.Outcome.InvalidInput(
            SaveManualEventUseCase.InvalidInputReason.UNSUPPORTED_EVENT_TYPE,
        )

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.saveManualEvent("c1", null, "TEAM_OUTING", null, 6, 5, null)
        advanceUntilIdle()

        assertEquals(context.getString(R.string.events_error_unsupported_event_type), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSavingManualEvent)
    }

    @Test
    fun `saveManualEvent duplicate exposes warning without snackbar error`() = runTest(testDispatcher) {
        val contact = contactHeader(id = "c1", displayName = "Alice")
        val event = eventListItem(
            id = "existing",
            contactId = "c1",
            type = OccasionType.BIRTHDAY,
            dayOfMonth = 5,
            month = 6,
            nextOccurrenceMs = 100L,
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(listOf(eventListItem(id = event.id.value, contactId = event.contactId.value)))
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(listOf(contactPickerItem("c1", "Alice")))
        coEvery { saveManualEventUseCase(any()) } returns SaveManualEventUseCase.Outcome.DuplicateFound(contact, event)

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.saveManualEvent("c1", null, "BIRTHDAY", null, 6, 5, null)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSavingManualEvent)
        assertNull(viewModel.uiState.value.error)
        assertEquals("Alice", viewModel.uiState.value.duplicateWarning?.contactName)
        assertEquals("BIRTHDAY", viewModel.uiState.value.duplicateWarning?.eventType)
        assertEquals(ManualEventWarningKind.DUPLICATE, viewModel.uiState.value.duplicateWarning?.kind)
    }

    @Test
    fun `saveManualEvent conflict exposes date conflict warning`() = runTest(testDispatcher) {
        val contact = contactHeader(id = "c1", displayName = "Alice")
        val event = eventListItem(
            id = "c1_birthday",
            contactId = "c1",
            type = OccasionType.BIRTHDAY,
            dayOfMonth = 12,
            month = 6,
            nextOccurrenceMs = 100L,
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(listOf(eventListItem(id = event.id.value, contactId = event.contactId.value, dayOfMonth = event.dayOfMonth, month = event.month)))
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(listOf(contactPickerItem("c1", "Alice")))
        coEvery { saveManualEventUseCase(any()) } returns SaveManualEventUseCase.Outcome.ConflictFound(
            contact = contact,
            existingEvent = event,
            requestedMonth = 7,
            requestedDayOfMonth = 1,
            requestedYear = null,
        )

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.saveManualEvent("c1", null, "BIRTHDAY", null, 7, 1, null)
        advanceUntilIdle()

        val warning = viewModel.uiState.value.duplicateWarning
        assertFalse(viewModel.uiState.value.isSavingManualEvent)
        assertNull(viewModel.uiState.value.error)
        assertEquals(ManualEventWarningKind.DATE_CONFLICT, warning?.kind)
        assertEquals("Alice", warning?.contactName)
        assertEquals(6, warning?.month)
        assertEquals(12, warning?.dayOfMonth)
        assertEquals(7, warning?.requestedMonth)
        assertEquals(1, warning?.requestedDayOfMonth)
    }

    @Test
    fun `saveManualEvent override passes allowDuplicate to use case`() = runTest(testDispatcher) {
        val contact = contactHeader(id = "c1", displayName = "Alice")
        val event = eventListItem(
            id = "manual",
            contactId = "c1",
            type = OccasionType.BIRTHDAY,
            dayOfMonth = 5,
            month = 6,
            nextOccurrenceMs = 100L,
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(emptyList())
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(listOf(contactPickerItem("c1", "Alice")))
        coEvery { saveManualEventUseCase(any()) } returns SaveManualEventUseCase.Outcome.Saved(contact, event)

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.saveManualEvent("c1", null, "BIRTHDAY", null, 6, 5, null, allowDuplicate = true)
        advanceUntilIdle()

        coVerify {
            saveManualEventUseCase(match { it.allowDuplicate })
        }
        assertTrue(viewModel.uiState.value.saveMessage?.contains("Alice") == true)
    }

    @Test
    fun `resolveEventConflict keep separate delegates to use case and exposes feedback`() = runTest(testDispatcher) {
        val event = eventListItem(
            id = "manual",
            contactId = "c1",
            type = OccasionType.BIRTHDAY,
            dayOfMonth = 5,
            month = 6,
            nextOccurrenceMs = 100L,
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(emptyList())
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(emptyList())
        coEvery { resolveEventConflictUseCase(any()) } returns ResolveEventConflictUseCase.Outcome.Resolved(
            keptEvent = event,
            affectedEventIds = listOf("imported", "manual"),
            action = ResolveEventConflictUseCase.Action.KEEP_SEPARATE,
        )

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.resolveEventConflict(event.id.value, EventResolutionAction.KEEP_SEPARATE)
        advanceUntilIdle()

        coVerify {
            resolveEventConflictUseCase(
                match {
                    it.eventId == event.id.value &&
                        it.action == ResolveEventConflictUseCase.Action.KEEP_SEPARATE
                }
            )
        }
        coVerify {
            activityLogRepository.record(match { it.type == "EVENT" && it.eventId == event.id.value })
        }
        assertNull(viewModel.uiState.value.resolvingEventId)
        assertTrue(viewModel.uiState.value.saveMessage?.contains("separate", ignoreCase = true) == true)
    }

    @Test
    fun `search type and horizon filters are applied in viewmodel`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val contacts = listOf(
            contactPickerItem(id = "c1", displayName = "Alice"),
            contactPickerItem(id = "c2", displayName = "Bob"),
        )
        val events = listOf(
            eventListItem(
                id = "e1",
                contactId = "c1",
                type = OccasionType.BIRTHDAY,
                dayOfMonth = 1,
                month = 1,
                nextOccurrenceMs = now + 2 * 86_400_000L,
            ),
            eventListItem(
                id = "e2",
                contactId = "c2",
                type = OccasionType.ANNIVERSARY,
                dayOfMonth = 2,
                month = 1,
                nextOccurrenceMs = now + 20 * 86_400_000L,
            ),
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(events)
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(contacts)

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.selectHorizonFilter(EventHorizonFilter.NEXT_7_DAYS)
        assertEquals(listOf("e1"), viewModel.uiState.value.events.map { it.id.value })

        viewModel.selectHorizonFilter(EventHorizonFilter.ALL)
        viewModel.selectTypeFilter(EventTypeFilter.ANNIVERSARY)
        assertEquals(listOf("e2"), viewModel.uiState.value.events.map { it.id.value })

        viewModel.selectTypeFilter(EventTypeFilter.ALL)
        viewModel.updateSearchQuery("alice")
        assertEquals(listOf("e1"), viewModel.uiState.value.events.map { it.id.value })
    }

    @Test
    fun `advanced event type filters select matching generated event types`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val events = listOf(
            eventListItem(id = "graduation", type = OccasionType.GRADUATION, nextOccurrenceMs = now + 1),
            eventListItem(id = "holiday", type = OccasionType.HOLIDAY, nextOccurrenceMs = now + 2),
            eventListItem(id = "revival", type = OccasionType.REVIVAL, nextOccurrenceMs = now + 3),
            eventListItem(id = "follow_up", type = OccasionType.FOLLOW_UP, nextOccurrenceMs = now + 4),
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(events)
        every { contactRepository.getContactPickerItems() } returns MutableStateFlow(emptyList())

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.selectTypeFilter(EventTypeFilter.GRADUATION)
        assertEquals(listOf("graduation"), viewModel.uiState.value.events.map { it.id.value })

        viewModel.selectTypeFilter(EventTypeFilter.HOLIDAY)
        assertEquals(listOf("holiday"), viewModel.uiState.value.events.map { it.id.value })

        viewModel.selectTypeFilter(EventTypeFilter.REVIVAL)
        assertEquals(listOf("revival"), viewModel.uiState.value.events.map { it.id.value })

        viewModel.selectTypeFilter(EventTypeFilter.FOLLOW_UP)
        assertEquals(listOf("follow_up"), viewModel.uiState.value.events.map { it.id.value })
    }

    private fun eventListItem(
        id: String,
        contactId: String = "contact_$id",
        type: OccasionType = OccasionType.BIRTHDAY,
        label: String? = null,
        dayOfMonth: Int = 1,
        month: Int = 1,
        year: Int? = null,
        nextOccurrenceMs: Long = 100L,
        isActive: Boolean = true,
        notifyDaysBefore: Int = 1,
        source: String = "CONTACTS",
        confidenceScore: Int = 100,
        isVerified: Boolean = true,
    ) = EventListItem(
        id = OccasionId(id),
        contactId = ContactId(contactId),
        type = type,
        label = label,
        dayOfMonth = dayOfMonth,
        month = month,
        year = year,
        nextOccurrenceMs = nextOccurrenceMs,
        isActive = isActive,
        notifyDaysBefore = notifyDaysBefore,
        source = source,
        confidenceScore = confidenceScore,
        isVerified = isVerified,
    )

    private fun contactPickerItem(
        id: String,
        displayName: String,
    ) = ContactPickerItem(
        id = ContactId(id),
        displayName = displayName,
    )

    private fun contactHeader(
        id: String,
        displayName: String,
    ) = ContactHeader(
        id = ContactId(id),
        displayName = displayName,
    )

    private fun newViewModel(): EventsViewModel {
        return EventsViewModel(
            appContext = context,
            eventRepository = eventRepository,
            contactRepository = contactRepository,
            saveManualEventUseCase = saveManualEventUseCase,
            resolveEventConflictUseCase = resolveEventConflictUseCase,
            activityLogRepository = activityLogRepository,
        )
    }
}
