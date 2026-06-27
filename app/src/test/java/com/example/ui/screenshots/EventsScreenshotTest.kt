package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.OccasionType
import com.example.ui.screens.events.EventsContent
import com.example.ui.viewmodel.EventsUiState
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
    fun eventsConflict_compactPhoneLargeFont() {
        setEventsContent(
            state = eventsState(events = conflictEvents()),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_conflict_compact_phone_large_font.png")
    }

    @Test
    fun eventsEmpty_compactPhone() {
        setEventsContent(state = EventsUiState(isLoading = false))

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/events_empty_compact_phone.png")
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

    private fun eventsState(events: List<EventListItem>): EventsUiState {
        return EventsUiState(
            allEvents = events,
            events = events,
            eventTrust = buildEventTrustStates(events),
            isLoading = false,
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
