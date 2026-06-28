package com.example.ui.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateCard
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.contact.ContactPickerItem
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.OccasionType
import com.example.ui.screens.events.EventsContent
import com.example.ui.screens.events.EventsTestTags
import com.example.ui.screens.events.ManualEventDialogBody
import com.example.ui.viewmodel.EventsUiState
import com.example.ui.viewmodel.ManualEventDuplicateWarning
import com.example.ui.viewmodel.ManualEventWarningKind
import com.example.ui.viewmodel.buildEventTrustStates
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Before
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
class EventsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var previousLocale: Locale
    private lateinit var previousTimeZone: TimeZone

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        previousTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
        TimeZone.setDefault(previousTimeZone)
    }

    @Test
    fun eventsSchedule_compactPhone() {
        setEventsContent(state = eventsState(events = scheduleEvents()))

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_schedule_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun eventsSchedule_typicalPhone() {
        setEventsContent(state = eventsState(events = scheduleEvents()))

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_schedule_typical_phone.png")
    }

    @Test
    fun eventsConflict_compactPhoneLargeFont() {
        setEventsContent(
            state = eventsState(events = conflictEvents()),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_conflict_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun eventsConflict_typicalPhone() {
        setEventsContent(state = eventsState(events = conflictEvents()))

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_conflict_typical_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun eventsConflict_compactPhoneHindiLargeFont() {
        Locale.setDefault(Locale.forLanguageTag("hi-IN"))
        setEventsContent(
            state = eventsState(events = conflictEventsHindi()),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_conflict_compact_phone_hindi_large_font.png")
    }

    @Test
    fun eventsEmpty_compactPhone() {
        setEventsContent(state = EventsUiState(isLoading = false))

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_empty_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun eventsEmpty_typicalPhone() {
        setEventsContent(state = EventsUiState(isLoading = false))

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_empty_typical_phone.png")
    }

    @Test
    fun eventsLoading_compactPhone() {
        setEventsContent(
            state = EventsUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_loading_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun eventsLoading_typicalPhone() {
        setEventsContent(
            state = EventsUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_loading_typical_phone.png")
    }

    @Test
    fun eventsManualEventForm_compactPhoneLargeFont() {
        setManualEventFormContent(
            useExistingContact = true,
            selectedContactId = "contact-asha",
            newContactName = "",
            eventType = OccasionType.BIRTHDAY.raw,
            label = "Birthday dinner",
            monthText = "04",
            dayText = "10",
            yearText = "",
            duplicateWarning = null,
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(EventsTestTags.MANUAL_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/events_manual_event_form_compact_phone_large_font.png")
    }

    @Test
    fun eventsManualEventFormNewContact_compactPhoneLargeFont() {
        setManualEventFormContent(
            useExistingContact = false,
            selectedContactId = null,
            newContactName = "Rohan Patel",
            eventType = OccasionType.CUSTOM.raw,
            label = "Housewarming",
            monthText = "08",
            dayText = "18",
            yearText = "2026",
            duplicateWarning = null,
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(EventsTestTags.MANUAL_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/events_manual_event_form_new_contact_compact_phone_large_font.png")
    }

    @Test
    fun eventsManualEventFormBottom_compactPhoneLargeFont() {
        setManualEventFormContent(
            useExistingContact = true,
            selectedContactId = "contact-asha",
            newContactName = "",
            eventType = OccasionType.BIRTHDAY.raw,
            label = "Birthday dinner",
            monthText = "04",
            dayText = "10",
            yearText = "2026",
            duplicateWarning = ManualEventDuplicateWarning(
                contactName = "Asha Mehta",
                eventType = OccasionType.BIRTHDAY.raw,
                month = 4,
                dayOfMonth = 10,
                kind = ManualEventWarningKind.DATE_CONFLICT,
                requestedMonth = 4,
                requestedDayOfMonth = 12,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(EventsTestTags.MANUAL_WARNING)
            .performScrollTo()
        composeRule.onNodeWithTag(EventsTestTags.MANUAL_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/events_manual_event_form_bottom_compact_phone_large_font.png")
    }

    private fun setEventsContent(
        state: EventsUiState,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            EventsContent(
                state = state,
                currentTimeMillis = FixedNowMs,
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun setManualEventFormContent(
        useExistingContact: Boolean,
        selectedContactId: String?,
        newContactName: String,
        eventType: String,
        label: String,
        monthText: String,
        dayText: String,
        yearText: String,
        duplicateWarning: ManualEventDuplicateWarning?,
        fontScale: Float = DefaultFontScale,
    ) {
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            ManualEventFormSnapshot(
                useExistingContact = useExistingContact,
                selectedContactId = selectedContactId,
                newContactName = newContactName,
                eventType = eventType,
                label = label,
                monthText = monthText,
                dayText = dayText,
                yearText = yearText,
                duplicateWarning = duplicateWarning,
            )
        }
    }

    @Composable
    private fun ManualEventFormSnapshot(
        useExistingContact: Boolean,
        selectedContactId: String?,
        newContactName: String,
        eventType: String,
        label: String,
        monthText: String,
        dayText: String,
        yearText: String,
        duplicateWarning: ManualEventDuplicateWarning?,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RelateDarkBackground)
                .padding(RelateSpacing.screenHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EventsTestTags.MANUAL_DIALOG),
                colors = CardDefaults.cardColors(
                    containerColor = RelateCard,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(RelateSpacing.cardContent),
                    verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
                ) {
                    Text(
                        text = stringResource(R.string.events_add_event),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ManualEventDialogBody(
                        contacts = manualContacts(),
                        useExistingContact = useExistingContact,
                        onUseExistingContactChange = {},
                        selectedContactId = selectedContactId,
                        onSelectedContactIdChange = {},
                        newContactName = newContactName,
                        onNewContactNameChange = {},
                        eventType = eventType,
                        onEventTypeChange = {},
                        label = label,
                        onLabelChange = {},
                        monthText = monthText,
                        onMonthTextChange = {},
                        dayText = dayText,
                        onDayTextChange = {},
                        yearText = yearText,
                        onYearTextChange = {},
                        localError = null,
                        duplicateWarning = duplicateWarning,
                        onInputChanged = {},
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {},
                            modifier = Modifier.testTag(EventsTestTags.MANUAL_CANCEL),
                        ) {
                            Text(text = stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(RelateSpacing.xs))
                        TextButton(
                            onClick = {},
                            modifier = Modifier.testTag(EventsTestTags.MANUAL_SAVE),
                        ) {
                            Text(
                                text = stringResource(
                                    if (duplicateWarning == null) {
                                        R.string.save
                                    } else {
                                        R.string.events_duplicate_save_anyway
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun eventsState(events: List<EventListItem>): EventsUiState {
        return EventsUiState(
            allEvents = events,
            events = events,
            eventTrust = buildEventTrustStates(events),
            isLoading = false,
        )
    }

    private fun manualContacts(): List<ContactPickerItem> {
        return listOf(
            ContactPickerItem(
                id = ContactId("contact-asha"),
                displayName = "Asha Mehta",
            ),
            ContactPickerItem(
                id = ContactId("contact-dev"),
                displayName = "Dev Shah",
            ),
        )
    }

    private fun scheduleEvents(): List<EventListItem> {
        return listOf(
            event(
                id = "asha-birthday",
                contactId = "contact-asha",
                type = OccasionType.BIRTHDAY,
                label = "Asha birthday",
                daysUntil = 4,
                source = "CONTACTS",
                confidenceScore = 92,
                isVerified = false,
            ),
            event(
                id = "dev-work",
                contactId = "contact-dev",
                type = OccasionType.WORK_ANNIVERSARY,
                label = "Dev work anniversary",
                daysUntil = 18,
                source = "MANUAL",
                confidenceScore = 100,
                isVerified = true,
            ),
            event(
                id = "mira-anniversary",
                contactId = "contact-mira",
                type = OccasionType.ANNIVERSARY,
                label = "Mira anniversary",
                daysUntil = 42,
                source = "CALENDAR",
                confidenceScore = 100,
                isVerified = true,
            ),
        )
    }

    private fun conflictEvents(): List<EventListItem> {
        return listOf(
            event(
                id = "tara-imported",
                contactId = "contact-tara",
                type = OccasionType.BIRTHDAY,
                label = "Tara birthday",
                daysUntil = 7,
                source = "CONTACTS",
                confidenceScore = 88,
                isVerified = true,
            ),
            event(
                id = "tara-manual",
                contactId = "contact-tara",
                type = OccasionType.BIRTHDAY,
                label = "Tara birthday",
                daysUntil = 11,
                source = "MANUAL",
                confidenceScore = 100,
                isVerified = true,
            ),
        )
    }

    private fun conflictEventsHindi(): List<EventListItem> {
        return listOf(
            event(
                id = "tara-imported-hi",
                contactId = "contact-tara",
                type = OccasionType.BIRTHDAY,
                label = "तारा जन्मदिन",
                daysUntil = 7,
                source = "CONTACTS",
                confidenceScore = 88,
                isVerified = true,
            ),
            event(
                id = "tara-manual-hi",
                contactId = "contact-tara",
                type = OccasionType.BIRTHDAY,
                label = "तारा जन्मदिन",
                daysUntil = 11,
                source = "MANUAL",
                confidenceScore = 100,
                isVerified = true,
            ),
        )
    }

    private fun event(
        id: String,
        contactId: String,
        type: OccasionType,
        label: String,
        daysUntil: Int,
        source: String,
        confidenceScore: Int,
        isVerified: Boolean,
    ): EventListItem {
        val nextOccurrenceMs = FixedNowMs + daysUntil * DayMs + OneHourMs
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            timeInMillis = nextOccurrenceMs
        }
        return EventListItem(
            id = OccasionId(id),
            contactId = ContactId(contactId),
            type = type,
            label = label,
            dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
            month = calendar.get(Calendar.MONTH) + 1,
            year = null,
            nextOccurrenceMs = nextOccurrenceMs,
            isActive = true,
            notifyDaysBefore = 1,
            source = source,
            confidenceScore = confidenceScore,
            isVerified = isVerified,
        )
    }

    private companion object {
        const val ProgressAnimationFrameMillis = 750L
        const val FixedNowMs = 1_767_614_400_000L
        const val DayMs = 86_400_000L
        const val OneHourMs = 3_600_000L
    }
}
