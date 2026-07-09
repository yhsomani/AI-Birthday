package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.StructuredLogger
import com.example.core.gemini.GeminiClient
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DiagnosticSnapshotId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.contact.ContactAutomationReadinessProfile
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.readiness.RelationshipReadinessAction
import com.example.domain.readiness.RelationshipReadinessReason
import com.example.domain.readiness.RelationshipReadinessState
import com.example.domain.model.style.StyleProfileRecord
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.DiagnosticSnapshotRepository
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.service.PreferencesRepository
import com.example.domain.usecase.SyncContactsUseCase
import com.example.domain.usecase.TestSendUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var preferencesRepository: PreferencesRepository

    @RelaxedMockK
    private lateinit var syncContactsUseCase: SyncContactsUseCase

    @RelaxedMockK
    private lateinit var geminiClient: GeminiClient

    @RelaxedMockK
    private lateinit var contactRepository: ContactRepository

    @RelaxedMockK
    private lateinit var styleProfileRepository: StyleProfileRepository

    @RelaxedMockK
    private lateinit var testSendUseCase: TestSendUseCase

    @RelaxedMockK
    private lateinit var dispatchAttemptRepository: DispatchAttemptRepository

    @RelaxedMockK
    private lateinit var diagnosticSnapshotRepository: DiagnosticSnapshotRepository

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var readinessProfiles: MutableStateFlow<List<ContactAutomationReadinessProfile>>
    private lateinit var styleProfile: MutableStateFlow<StyleProfileRecord?>
    private lateinit var preferenceChanges: MutableSharedFlow<Unit>
    private lateinit var failureRecoveryCount: MutableStateFlow<Int>
    private val createdViewModels = mutableListOf<AutomationSetupViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        HealthMonitor.clearForTests()
        StructuredLogger.clearForTests()
        context = ApplicationProvider.getApplicationContext()
        readinessProfiles = MutableStateFlow(emptyList())
        styleProfile = MutableStateFlow(null)
        preferenceChanges = MutableSharedFlow(extraBufferCapacity = 1)
        failureRecoveryCount = MutableStateFlow(0)
        every { preferencesRepository.getGoogleOAuthToken() } returns ""
        every { preferencesRepository.getGeminiApiKey() } returns ""
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        every { preferencesRepository.getLastSuccessfulEmailTestSender() } returns ""
        every { preferencesRepository.getLastSuccessfulEmailTestMs() } returns 0L
        every { preferencesRepository.getGlobalAutomationMode() } returns ApprovalMode.FULLY_AUTO
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        every { preferencesRepository.isWhatsAppAutomationConsentGranted() } returns true
        every { preferencesRepository.observeChanges() } returns preferenceChanges
        coEvery { contactRepository.getAutomationReadinessProfiles() } returns emptyList()
        every { contactRepository.getAutomationReadinessProfilesFlow() } returns readinessProfiles
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        every { styleProfileRepository.getProfile() } returns styleProfile
        coEvery { testSendUseCase(any()) } returns TestSendUseCase.Outcome.MissingEmailSetup
        every { dispatchAttemptRepository.countFailureRecoveryQueue() } returns failureRecoveryCount
        coEvery { dispatchAttemptRepository.getFailureRecoveryQueue(any()) } returns emptyList()
        coEvery { dispatchAttemptRepository.getSuccessfulChannelsSince(any()) } returns emptySet()
        coEvery { diagnosticSnapshotRepository.getLatestBySource(any()) } returns null
        coEvery { diagnosticSnapshotRepository.record(any()) } returns Unit
    }

    @After
    fun tearDown() = runTest(testDispatcher) {
        createdViewModels.forEach { viewModel ->
            viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        }
        createdViewModels.clear()
        advanceUntilIdle()
        HealthMonitor.clearForTests()
        StructuredLogger.clearForTests()
        Dispatchers.resetMain()
    }

    @Test
    fun `summarize returns required summary for blockers`() {
        val presenter = AutomationSetupReadinessPresenter(context)

        val summary = presenter.summarize(
            listOf(
                ReadinessCheck(
                    title = context.getString(R.string.automation_setup_check_gemini),
                    detail = context.getString(R.string.automation_setup_gemini_auth_missing),
                    status = ReadinessStatus.ACTION_REQUIRED,
                ),
            ),
        )

        assertEquals(ReadinessStatus.ACTION_REQUIRED, summary.status)
        assertEquals(context.getString(R.string.automation_setup_summary_blockers, 1), summary.title)
        assertTrue(summary.detail.contains(context.getString(R.string.automation_setup_check_gemini)))
    }

    @Test
    fun `summarize returns healthy summary when all checks pass`() {
        val presenter = AutomationSetupReadinessPresenter(context)

        val summary = presenter.summarize(
            listOf(
                ReadinessCheck(
                    title = context.getString(R.string.automation_setup_check_gemini),
                    detail = context.getString(R.string.automation_setup_gemini_key_ok),
                    status = ReadinessStatus.OK,
                ),
            ),
        )

        assertEquals(ReadinessStatus.OK, summary.status)
        assertEquals(context.getString(R.string.automation_setup_summary_ok), summary.title)
    }

    @Test
    fun `refresh assigns readiness checks to grouped AI Doctor sections`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val groupsByTitle = currentChecks(viewModel).associate {
            it.title to it.group
        }

        assertEquals(
            ReadinessGroup.REQUIRED,
            groupsByTitle[context.getString(R.string.automation_setup_check_full_automation)],
        )
        assertEquals(
            ReadinessGroup.REQUIRED,
            groupsByTitle[context.getString(R.string.automation_setup_check_delivery_routes)],
        )
        assertEquals(
            ReadinessGroup.REQUIRED,
            groupsByTitle[context.getString(R.string.automation_setup_check_sms)],
        )
        assertEquals(
            ReadinessGroup.QUALITY,
            groupsByTitle[context.getString(R.string.automation_setup_check_style_coach)],
        )
        assertEquals(
            ReadinessGroup.RELIABILITY,
            groupsByTitle[context.getString(R.string.automation_setup_check_exact_sends)],
        )
        assertEquals(
            ReadinessGroup.RECOVERY,
            groupsByTitle[context.getString(R.string.automation_setup_check_recent_errors)],
        )
    }

    @Test
    fun `report requires fully auto for unattended message sending`() = runTest(testDispatcher) {
        every { preferencesRepository.getGlobalAutomationMode() } returns ApprovalMode.ALWAYS_ASK
        val viewModel = newViewModel()
        advanceUntilIdle()

        val fullAutomationCheck = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_full_automation) }

        assertEquals(ReadinessStatus.ACTION_REQUIRED, fullAutomationCheck.status)
        assertEquals(AiDoctorAction.OPEN_SETTINGS, fullAutomationCheck.action)
        assertEquals(context.getString(R.string.automation_setup_action_open_settings), fullAutomationCheck.actionLabel)
        assertEquals(
            context.getString(
                R.string.automation_setup_full_automation_disabled,
                context.getString(R.string.automation_mode_always_ask),
            ),
            fullAutomationCheck.detail,
        )
    }

    @Test
    fun `report warns when contacts keep review first overrides under full automation`() =
        runTest(testDispatcher) {
            seedReadinessProfiles(
                readinessProfile(
                    id = "manual",
                    automationMode = ApprovalMode.ALWAYS_ASK,
                ),
                readinessProfile(
                    id = "skip",
                    skipAutoWish = true,
                ),
                readinessProfile(
                    id = "automatic",
                    automationMode = ApprovalMode.DEFAULT,
                ),
            )
            val viewModel = newViewModel()
            advanceUntilIdle()

            val fullAutomationCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_full_automation) }

            assertEquals(ReadinessStatus.WARNING, fullAutomationCheck.status)
            assertEquals(AiDoctorAction.OPEN_CONTACTS, fullAutomationCheck.action)
            assertEquals(
                context.getString(R.string.automation_setup_full_automation_contact_overrides, 2),
                fullAutomationCheck.detail,
            )
        }

    @Test
    fun `report surfaces contacts without automatable events`() = runTest(testDispatcher) {
        seedReadinessProfiles(
            readinessProfile(
                id = "birthday",
                hasAutomatableOccasion = true,
            ),
            readinessProfile(
                id = "no_event",
                hasAutomatableOccasion = false,
            ),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        val eventsCheck = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_automatable_events) }

        assertEquals(ReadinessStatus.WARNING, eventsCheck.status)
        assertEquals(AiDoctorAction.OPEN_CONTACTS, eventsCheck.action)
        assertEquals(
            context.getString(R.string.automation_setup_automatable_events_missing, 1, 2),
            eventsCheck.detail,
        )
    }

    @Test
    fun `report requires real Google Contacts token or scope`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val googleContactsCheck = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_google_contacts) }

        assertEquals(ReadinessStatus.ACTION_REQUIRED, googleContactsCheck.status)
        assertEquals(AiDoctorAction.SYNC_CONTACTS, googleContactsCheck.action)
        assertEquals(context.getString(R.string.automation_setup_action_sync_contacts), googleContactsCheck.actionLabel)
        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, googleContactsCheck.actionReadiness.state)
        assertEquals(RelationshipReadinessReason.SETUP_ACTION_REQUIRED, googleContactsCheck.actionReadiness.primaryReason)
        assertEquals(RelationshipReadinessAction.OPEN_SETUP, googleContactsCheck.actionReadiness.primaryAction)
    }

    @Test
    fun `report exposes canonical setup readiness summary`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        currentChecks(viewModel)

        val readiness = viewModel.uiState.value.setupActionReadiness

        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, readiness.state)
        assertEquals(RelationshipReadinessReason.SETUP_ACTION_REQUIRED, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.OPEN_SETUP, readiness.primaryAction)
    }

    @Test
    fun `report accepts cached People API token for Google Contacts readiness`() = runTest(testDispatcher) {
        every { preferencesRepository.getGoogleOAuthToken() } returns "cached-token"
        val viewModel = newViewModel()
        advanceUntilIdle()

        val googleContactsCheck = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_google_contacts) }

        assertEquals(ReadinessStatus.OK, googleContactsCheck.status)
        assertEquals(AiDoctorAction.NONE, googleContactsCheck.action)
        assertEquals(context.getString(R.string.automation_setup_google_contacts_ok), googleContactsCheck.detail)
    }

    @Test
    fun `report blocks full automation when event contacts lack delivery routes`() =
        runTest(testDispatcher) {
            seedReadinessProfiles(
                readinessProfile(
                    id = "routable_sms",
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                ),
                readinessProfile(
                    id = "missing_route",
                    hasAutomatableOccasion = true,
                ),
                readinessProfile(
                    id = "email_without_sender",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryEmail = true,
                ),
            )
            val viewModel = newViewModel()
            advanceUntilIdle()

            val deliveryRoutesCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_delivery_routes) }

            assertEquals(ReadinessStatus.ACTION_REQUIRED, deliveryRoutesCheck.status)
            assertEquals(AiDoctorAction.OPEN_CONTACTS, deliveryRoutesCheck.action)
            assertEquals(
                context.getString(R.string.automation_setup_delivery_routes_missing, 2, 3),
                deliveryRoutesCheck.detail,
            )
        }

    @Test
    fun `report warns when selected channels have no recent successful dispatch evidence`() =
        runTest(testDispatcher) {
            seedReadinessProfiles(
                readinessProfile(
                    id = "sms",
                    preferredChannel = MessageChannel.SMS,
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                ),
                readinessProfile(
                    id = "email",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryEmail = true,
                ),
            )
            every { preferencesRepository.getSenderEmail() } returns "sender@example.com"
            every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
            coEvery { dispatchAttemptRepository.getSuccessfulChannelsSince(any()) } returns setOf(MessageChannel.SMS)
            val viewModel = newViewModel()
            advanceUntilIdle()

            val verificationCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_channel_verification) }

            assertEquals(ReadinessStatus.WARNING, verificationCheck.status)
            assertEquals(AiDoctorAction.TEST_EMAIL, verificationCheck.action)
            assertEquals(context.getString(R.string.automation_setup_action_test_email), verificationCheck.actionLabel)
            assertEquals(
                context.getString(R.string.automation_setup_channel_verification_missing, context.getString(R.string.channel_email)),
                verificationCheck.detail,
            )
        }

    @Test
    fun `report passes channel verification when selected channels have recent success`() =
        runTest(testDispatcher) {
            seedReadinessProfiles(
                readinessProfile(
                    id = "email",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryEmail = true,
                ),
            )
            every { preferencesRepository.getSenderEmail() } returns "sender@example.com"
            every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
            coEvery { dispatchAttemptRepository.getSuccessfulChannelsSince(any()) } returns setOf(MessageChannel.EMAIL)
            val viewModel = newViewModel()
            advanceUntilIdle()

            val verificationCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_channel_verification) }

            assertEquals(ReadinessStatus.OK, verificationCheck.status)
            assertEquals(AiDoctorAction.NONE, verificationCheck.action)
            assertEquals(
                context.getString(R.string.automation_setup_channel_verification_ok, context.getString(R.string.channel_email)),
                verificationCheck.detail,
            )
        }

    @Test
    fun `report counts recent matching email self-test as email channel verification`() =
        runTest(testDispatcher) {
            seedReadinessProfiles(
                readinessProfile(
                    id = "email",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryEmail = true,
                ),
            )
            every { preferencesRepository.getSenderEmail() } returns "sender@example.com"
            every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
            every { preferencesRepository.getLastSuccessfulEmailTestSender() } returns "sender@example.com"
            every { preferencesRepository.getLastSuccessfulEmailTestMs() } returns Long.MAX_VALUE
            coEvery { dispatchAttemptRepository.getSuccessfulChannelsSince(any()) } returns emptySet()
            val viewModel = newViewModel()
            advanceUntilIdle()

            val verificationCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_channel_verification) }

            assertEquals(ReadinessStatus.OK, verificationCheck.status)
            assertEquals(AiDoctorAction.NONE, verificationCheck.action)
            assertEquals(
                context.getString(R.string.automation_setup_channel_verification_ok, context.getString(R.string.channel_email)),
                verificationCheck.detail,
            )
        }

    @Test
    fun `report ignores email self-test after sender changes`() =
        runTest(testDispatcher) {
            seedReadinessProfiles(
                readinessProfile(
                    id = "email",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryEmail = true,
                ),
            )
            every { preferencesRepository.getSenderEmail() } returns "new-sender@example.com"
            every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
            every { preferencesRepository.getLastSuccessfulEmailTestSender() } returns "old-sender@example.com"
            every { preferencesRepository.getLastSuccessfulEmailTestMs() } returns Long.MAX_VALUE
            coEvery { dispatchAttemptRepository.getSuccessfulChannelsSince(any()) } returns emptySet()
            val viewModel = newViewModel()
            advanceUntilIdle()

            val verificationCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_channel_verification) }

            assertEquals(ReadinessStatus.WARNING, verificationCheck.status)
            assertEquals(AiDoctorAction.TEST_EMAIL, verificationCheck.action)
            assertEquals(context.getString(R.string.automation_setup_action_test_email), verificationCheck.actionLabel)
            assertEquals(
                context.getString(R.string.automation_setup_channel_verification_missing, context.getString(R.string.channel_email)),
                verificationCheck.detail,
            )
        }

    @Test
    fun `report routes SMS or WhatsApp channel verification to messages`() =
        runTest(testDispatcher) {
            seedReadinessProfiles(
                readinessProfile(
                    id = "sms",
                    preferredChannel = MessageChannel.SMS,
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                ),
                readinessProfile(
                    id = "email",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryEmail = true,
                ),
            )
            every { preferencesRepository.getSenderEmail() } returns "sender@example.com"
            every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
            coEvery { dispatchAttemptRepository.getSuccessfulChannelsSince(any()) } returns emptySet()
            val viewModel = newViewModel()
            advanceUntilIdle()

            val verificationCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_channel_verification) }

            assertEquals(ReadinessStatus.WARNING, verificationCheck.status)
            assertEquals(AiDoctorAction.OPEN_SMS_MESSAGES, verificationCheck.action)
            assertEquals(context.getString(R.string.automation_setup_action_review_sms_messages), verificationCheck.actionLabel)
            assertEquals(
                context.getString(
                    R.string.automation_setup_channel_verification_missing,
                    "${context.getString(R.string.channel_sms)}, ${context.getString(R.string.channel_email)}",
                ),
                verificationCheck.detail,
            )
        }

    @Test
    fun `report routes WhatsApp channel verification to filtered messages`() =
        runTest(testDispatcher) {
            seedReadinessProfiles(
                readinessProfile(
                    id = "whatsapp",
                    preferredChannel = MessageChannel.WHATSAPP,
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                ),
            )
            every { preferencesRepository.getChannelBlackout() } returns """["${MessageChannel.SMS.raw}"]"""
            coEvery { dispatchAttemptRepository.getSuccessfulChannelsSince(any()) } returns emptySet()
            val viewModel = newViewModel()
            advanceUntilIdle()

            val verificationCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_channel_verification) }

            assertEquals(ReadinessStatus.WARNING, verificationCheck.status)
            assertEquals(AiDoctorAction.OPEN_WHATSAPP_MESSAGES, verificationCheck.action)
            assertEquals(context.getString(R.string.automation_setup_action_review_whatsapp_messages), verificationCheck.actionLabel)
            assertEquals(
                context.getString(
                    R.string.automation_setup_channel_verification_missing,
                    context.getString(R.string.channel_whatsapp),
                ),
                verificationCheck.detail,
            )
        }

    @Test
    fun `report adds generic message risk diagnostic from personalization context`() = runTest(testDispatcher) {
        seedReadinessProfiles(
            readinessProfile(
                id = "ready",
                notesText = "College friend",
            ),
            readinessProfile(
                id = "generic",
            ),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        val genericRisk = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_generic_messages) }

        assertEquals(ReadinessStatus.WARNING, genericRisk.status)
        assertEquals(AiDoctorAction.OPEN_CONTACTS, genericRisk.action)
        assertEquals(context.getString(R.string.automation_setup_action_review_contacts), genericRisk.actionLabel)
        assertEquals(
            context.getString(R.string.automation_setup_generic_messages_low, 1, 2),
            genericRisk.detail,
        )
    }

    @Test
    fun `report counts email preferred contacts from readiness profiles`() = runTest(testDispatcher) {
        seedReadinessProfiles(
            readinessProfile(
                id = "email",
                preferredChannel = MessageChannel.EMAIL,
            ),
            readinessProfile(
                id = "sms",
                preferredChannel = MessageChannel.SMS,
            ),
            readinessProfile(
                id = "legacy",
                preferredChannel = MessageChannel.UNKNOWN,
            ),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        val emailCheck = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_email) }

        assertEquals(ReadinessStatus.ACTION_REQUIRED, emailCheck.status)
        assertEquals(AiDoctorAction.OPEN_SETTINGS, emailCheck.action)
        assertEquals(
            context.getString(R.string.automation_setup_email_missing_for_contacts, 1),
            emailCheck.detail,
        )
    }

    @Test
    fun `report warns when Gmail sender is configured but unverified`() = runTest(testDispatcher) {
        every { preferencesRepository.getSenderEmail() } returns "sender@example.com"
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
        val viewModel = newViewModel()
        advanceUntilIdle()

        val emailCheck = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_email) }

        assertEquals(ReadinessStatus.WARNING, emailCheck.status)
        assertEquals(AiDoctorAction.TEST_EMAIL, emailCheck.action)
        assertEquals(context.getString(R.string.automation_setup_email_unverified), emailCheck.detail)
    }

    @Test
    fun `report passes email readiness after matching recent self test`() = runTest(testDispatcher) {
        every { preferencesRepository.getSenderEmail() } returns "sender@example.com"
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
        every { preferencesRepository.getLastSuccessfulEmailTestSender() } returns "sender@example.com"
        every { preferencesRepository.getLastSuccessfulEmailTestMs() } returns Long.MAX_VALUE
        val viewModel = newViewModel()
        advanceUntilIdle()

        val emailCheck = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_email) }

        assertEquals(ReadinessStatus.OK, emailCheck.status)
        assertEquals(AiDoctorAction.NONE, emailCheck.action)
        assertEquals(context.getString(R.string.automation_setup_email_ok), emailCheck.detail)
    }

    @Test
    fun `report blocks invalid saved Gmail sender address`() = runTest(testDispatcher) {
        every { preferencesRepository.getSenderEmail() } returns "not-an-email"
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
        val viewModel = newViewModel()
        advanceUntilIdle()

        val emailCheck = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_email) }

        assertEquals(ReadinessStatus.ACTION_REQUIRED, emailCheck.status)
        assertEquals(AiDoctorAction.OPEN_SETTINGS, emailCheck.action)
        assertEquals(context.getString(R.string.automation_setup_email_invalid), emailCheck.detail)
    }

    @Test
    fun `report treats SMS as optional when no event-ready contact selects SMS`() =
        runTest(testDispatcher) {
            seedReadinessProfiles(
                readinessProfile(
                    id = "email",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryEmail = true,
                ),
            )
            every { preferencesRepository.getSenderEmail() } returns "sender@example.com"
            every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
            val viewModel = newViewModel()
            advanceUntilIdle()

            val smsCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_sms) }

            assertEquals(ReadinessStatus.OK, smsCheck.status)
            assertEquals(AiDoctorAction.NONE, smsCheck.action)
            assertEquals(context.getString(R.string.automation_setup_sms_not_used), smsCheck.detail)
        }

    @Test
    fun `report requires SMS permission when SMS is selected for event contacts`() =
        runTest(testDispatcher) {
            seedReadinessProfiles(
                readinessProfile(
                    id = "sms",
                    preferredChannel = MessageChannel.SMS,
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                ),
            )
            val viewModel = newViewModel()
            advanceUntilIdle()

            val smsCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_sms) }

            assertEquals(ReadinessStatus.ACTION_REQUIRED, smsCheck.status)
            assertEquals(AiDoctorAction.OPEN_APP_SETTINGS, smsCheck.action)
            assertEquals(
                context.getString(R.string.automation_setup_sms_missing_for_contacts, 1),
                smsCheck.detail,
            )
        }

    @Test
    fun `report treats disabled SMS as intentionally unused`() =
        runTest(testDispatcher) {
            every { preferencesRepository.getChannelBlackout() } returns """["${MessageChannel.SMS.raw}"]"""
            seedReadinessProfiles(
                readinessProfile(
                    id = "sms",
                    preferredChannel = MessageChannel.SMS,
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                ),
            )
            val viewModel = newViewModel()
            advanceUntilIdle()

            val smsCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_sms) }

            assertEquals(ReadinessStatus.OK, smsCheck.status)
            assertEquals(AiDoctorAction.NONE, smsCheck.action)
            assertEquals(context.getString(R.string.automation_setup_sms_disabled), smsCheck.detail)
        }

    @Test
    fun `report requires WhatsApp consent before automation channel is ready`() = runTest(testDispatcher) {
        every { preferencesRepository.isWhatsAppAutomationConsentGranted() } returns false
        every { preferencesRepository.getChannelBlackout() } returns """["${MessageChannel.SMS.raw}"]"""
        seedReadinessProfiles(
            readinessProfile(
                id = "whatsapp",
                preferredChannel = MessageChannel.WHATSAPP,
                hasAutomatableOccasion = true,
                hasPrimaryPhone = true,
            ),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        val whatsAppCheck = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_whatsapp) }

        assertEquals(ReadinessStatus.ACTION_REQUIRED, whatsAppCheck.status)
        assertEquals(
            context.getString(R.string.automation_setup_whatsapp_consent_needed_for_contacts, 1),
            whatsAppCheck.detail,
        )
    }

    @Test
    fun `setWhatsAppAutomationConsent persists acknowledgement and updates state`() = runTest(testDispatcher) {
        every { preferencesRepository.isWhatsAppAutomationConsentGranted() } returns false
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setWhatsAppAutomationConsent(true)
        advanceUntilIdle()

        verify { preferencesRepository.setWhatsAppAutomationConsentGranted(true) }
        assertTrue(viewModel.uiState.value.whatsAppAutomationConsentGranted)
        assertEquals(
            context.getString(R.string.automation_setup_whatsapp_consent_saved),
            viewModel.uiState.value.operationMessage,
        )
    }

    @Test
    fun `contact readiness changes immediately refresh AI Doctor personalization checks`() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()

            val initialCheck = awaitCheck(
                viewModel = viewModel,
                title = context.getString(R.string.automation_setup_check_personalization),
            ) { it.status == ReadinessStatus.WARNING }
            assertEquals(ReadinessStatus.WARNING, initialCheck.status)

            readinessProfiles.value = listOf(
                readinessProfile(
                    id = "ready",
                    notesText = "College friend with specific context",
                ),
            )

            val updatedCheck = awaitCheck(
                viewModel = viewModel,
                title = context.getString(R.string.automation_setup_check_personalization),
            ) { it.status == ReadinessStatus.OK }
            assertEquals(ReadinessStatus.OK, updatedCheck.status)
            assertEquals(
                context.getString(R.string.automation_setup_personalization_ok, 1, 1),
                updatedCheck.detail,
            )
        }

    @Test
    fun `preference changes immediately refresh AI Doctor Gemini readiness`() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()

            val initialCheck = awaitCheck(
                viewModel = viewModel,
                title = context.getString(R.string.automation_setup_check_gemini),
            ) { it.status == ReadinessStatus.ACTION_REQUIRED }
            assertEquals(ReadinessStatus.ACTION_REQUIRED, initialCheck.status)

            every { preferencesRepository.getGeminiApiKey() } returns "real-gemini-key"
            preferenceChanges.tryEmit(Unit)

            val updatedCheck = awaitCheck(
                viewModel = viewModel,
                title = context.getString(R.string.automation_setup_check_gemini),
            ) { it.status == ReadinessStatus.OK }
            assertEquals(ReadinessStatus.OK, updatedCheck.status)
            assertEquals(
                context.getString(R.string.automation_setup_gemini_key_ok),
                updatedCheck.detail,
            )
        }

    @Test
    fun `report surfaces persisted dispatch recovery rows`() = runTest(testDispatcher) {
        every { dispatchAttemptRepository.countFailureRecoveryQueue() } returns flowOf(2)
        coEvery { dispatchAttemptRepository.getFailureRecoveryQueue(1) } returns listOf(
            DispatchAttempt(
                id = DispatchAttemptId("attempt_1"),
                messageDraftId = MessageDraftId("draft_1"),
                contactId = null,
                occasionId = null,
                channel = MessageChannel.SMS,
                routeRank = 0,
                eligibilityDecision = DispatchEligibilityRecord.SEND_NOW,
                blockOrDeferReason = null,
                requestedAtMs = 1_700_000_000_000,
                attemptedAtMs = 1_700_000_000_100,
                resolvedAtMs = 1_700_000_000_200,
                result = DispatchAttemptResult.FAILED_FINAL,
                deliveryStatus = MessageDeliveryStatus.FAILED,
                providerMessageId = null,
                errorType = "NO_DELIVERY_ROUTE",
                errorCode = null,
                redactedErrorMessage = "All automatic delivery routes failed.",
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = 1_700_000_000_200,
                createdBy = DispatchAttemptCreator.WORKER,
            ),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        val recoveryCheck = currentChecks(viewModel)
            .first { it.title == context.getString(R.string.automation_setup_check_dispatch_recovery) }

        assertEquals(ReadinessStatus.WARNING, recoveryCheck.status)
        assertEquals(AiDoctorAction.OPEN_ACTIVITY_HISTORY, recoveryCheck.action)
        assertEquals(context.getString(R.string.automation_setup_action_view_activity), recoveryCheck.actionLabel)
        assertTrue(recoveryCheck.detail.contains("2 persisted dispatch recovery records"))
        assertTrue(recoveryCheck.detail.contains("SMS FAILED_FINAL"))
        assertTrue(recoveryCheck.detail.contains("draft_1"))
    }

    @Test
    fun `report reports healthy recovery when persisted recovery is empty`() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            advanceUntilIdle()

            val recoveryCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_dispatch_recovery) }

            assertEquals(ReadinessStatus.OK, recoveryCheck.status)
            assertEquals(context.getString(R.string.automation_setup_dispatch_recovery_none), recoveryCheck.detail)
        }

    @Test
    fun `report surfaces recent persisted HealthMonitor warning after process restart`() =
        runTest(testDispatcher) {
            coEvery {
                diagnosticSnapshotRepository.getLatestBySource(DiagnosticSnapshotSource.HEALTH_MONITOR)
            } returns DiagnosticSnapshot(
                id = DiagnosticSnapshotId("health_1"),
                source = DiagnosticSnapshotSource.HEALTH_MONITOR,
                status = DiagnosticSnapshotStatus.WARNING,
                summary = "HealthMonitor: healthy=false; recentErrors=1",
                checksJson = "{}",
                createdAtMs = System.currentTimeMillis(),
            )
            val viewModel = newViewModel()
            advanceUntilIdle()

            val recentErrorsCheck = currentChecks(viewModel)
                .first { it.title == context.getString(R.string.automation_setup_check_recent_errors) }

            assertEquals(ReadinessStatus.WARNING, recentErrorsCheck.status)
            assertEquals(AiDoctorAction.OPEN_ACTIVITY_HISTORY, recentErrorsCheck.action)
            assertEquals(context.getString(R.string.automation_setup_action_view_activity), recentErrorsCheck.actionLabel)
            assertTrue(recentErrorsCheck.detail.contains("HealthMonitor"))
        }

    @Test
    fun `report persists redacted AI Doctor diagnostic snapshot`() =
        runTest(testDispatcher) {
            val recorded = CopyOnWriteArrayList<DiagnosticSnapshot>()
            coEvery {
                diagnosticSnapshotRepository.getLatestBySource(DiagnosticSnapshotSource.HEALTH_MONITOR)
            } returns DiagnosticSnapshot(
                id = DiagnosticSnapshotId("health_secret"),
                source = DiagnosticSnapshotSource.HEALTH_MONITOR,
                status = DiagnosticSnapshotStatus.WARNING,
                summary = "HealthMonitor user=aarav@example.com Authorization=Bearer ya29.secret-token",
                checksJson = "{}",
                createdAtMs = System.currentTimeMillis(),
            )
            coEvery { diagnosticSnapshotRepository.record(capture(recorded)) } returns Unit
            val viewModel = newViewModel()
            advanceUntilIdle()

            currentChecks(viewModel)
            val aiDoctorSnapshot = recorded.last { it.source == DiagnosticSnapshotSource.AI_DOCTOR }

            assertEquals(DiagnosticSnapshotSource.AI_DOCTOR, aiDoctorSnapshot.source)
            assertFalse(aiDoctorSnapshot.summary.contains("aarav@example.com"))
            assertFalse(aiDoctorSnapshot.checksJson.contains("aarav@example.com"))
            assertFalse(aiDoctorSnapshot.summary.contains("ya29.secret-token"))
            assertFalse(aiDoctorSnapshot.checksJson.contains("ya29.secret-token"))
            assertTrue(aiDoctorSnapshot.checksJson.contains(context.getString(R.string.automation_setup_check_recent_errors)))
            assertTrue(aiDoctorSnapshot.checksJson.contains("[REDACTED_EMAIL]"))
            assertTrue(aiDoctorSnapshot.checksJson.contains("Bearer [REDACTED]"))
    }

    @Test
    fun `recommendedFix ranks required blockers before earlier warnings and quality fixes`() {
        val presenter = AutomationSetupReadinessPresenter(context)

        val recommendedFix = presenter.recommendedFix(
            listOf(
                ReadinessCheck(
                    title = "Style Coach",
                    detail = "No writing samples",
                    status = ReadinessStatus.ACTION_REQUIRED,
                    actionLabel = "Open Style Coach",
                    action = AiDoctorAction.OPEN_STYLE_COACH,
                    group = ReadinessGroup.QUALITY,
                ),
                ReadinessCheck(
                    title = "Daily Automation",
                    detail = "Daily work is missing",
                    status = ReadinessStatus.WARNING,
                    actionLabel = "Refresh",
                    action = AiDoctorAction.REFRESH,
                    group = ReadinessGroup.RELIABILITY,
                ),
                ReadinessCheck(
                    title = MessageChannel.SMS.raw,
                    detail = "SMS permission is missing",
                    status = ReadinessStatus.ACTION_REQUIRED,
                    actionLabel = "App Settings",
                    action = AiDoctorAction.OPEN_APP_SETTINGS,
                    group = ReadinessGroup.REQUIRED,
                ),
            ),
        )

        assertEquals(MessageChannel.SMS.raw, recommendedFix?.title)
        assertEquals(AiDoctorAction.OPEN_APP_SETTINGS, recommendedFix?.action)
        assertEquals(ReadinessGroup.REQUIRED, recommendedFix?.group)
        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, recommendedFix?.actionReadiness?.state)
        assertEquals(RelationshipReadinessReason.SETUP_ACTION_REQUIRED, recommendedFix?.actionReadiness?.primaryReason)
        assertEquals(RelationshipReadinessAction.OPEN_SETUP, recommendedFix?.actionReadiness?.primaryAction)
    }

    @Test
    fun `setupProgress counts ok warnings and blockers`() {
        val presenter = AutomationSetupReadinessPresenter(context)

        val progress = presenter.setupProgress(
            listOf(
                ReadinessCheck(
                    title = "Ready",
                    detail = "Ready",
                    status = ReadinessStatus.OK,
                ),
                ReadinessCheck(
                    title = "Warning",
                    detail = "Warning",
                    status = ReadinessStatus.WARNING,
                ),
                ReadinessCheck(
                    title = "Blocker",
                    detail = "Blocker",
                    status = ReadinessStatus.ACTION_REQUIRED,
                ),
            ),
        )

        assertEquals(1, progress.completedSteps)
        assertEquals(3, progress.totalSteps)
        assertEquals(1, progress.warningCount)
        assertEquals(1, progress.actionRequiredCount)
    }

    @Test
    fun `diagnose redacts sensitive fallback text`() {
        val diagnoser = AutomationSetupAiFailureDiagnoser(context)

        val message = diagnoser.diagnose(
            "Unexpected user=aarav@example.com Authorization=Bearer ya29.secret-token phone=+91 98765 43210",
        )

        assertFalse(message.contains("aarav@example.com"))
        assertFalse(message.contains("ya29.secret-token"))
        assertFalse(message.contains("+91 98765 43210"))
        assertTrue(message.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun `testEmailSend reports missing setup`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.testEmailSend()
        advanceUntilIdle()

        assertEquals(context.getString(R.string.automation_setup_email_missing), viewModel.uiState.value.operationMessage)
        assertFalse(viewModel.uiState.value.isTestingEmail)
    }

    private fun newViewModel(): AutomationSetupViewModel {
        return AutomationSetupViewModel(
            appContext = context,
            preferencesRepository = preferencesRepository,
            syncContactsUseCase = syncContactsUseCase,
            geminiClient = geminiClient,
            contactRepository = contactRepository,
            styleProfileRepository = styleProfileRepository,
            testSendUseCase = testSendUseCase,
            dispatchAttemptRepository = dispatchAttemptRepository,
            diagnosticSnapshotRepository = diagnosticSnapshotRepository,
        ).also { createdViewModels += it }
    }

    private fun currentChecks(viewModel: AutomationSetupViewModel): List<ReadinessCheck> {
        val deadline = System.currentTimeMillis() + 3_000
        var latest = emptyList<ReadinessCheck>()
        while (System.currentTimeMillis() < deadline) {
            testDispatcher.scheduler.advanceUntilIdle()
            latest = viewModel.uiState.value.checks
            if (latest.isNotEmpty()) {
                return latest
            }
            Thread.sleep(10)
        }
        return latest
    }

    private fun seedReadinessProfiles(vararg profiles: ContactAutomationReadinessProfile) {
        val profileList = profiles.toList()
        readinessProfiles.value = profileList
        coEvery { contactRepository.getAutomationReadinessProfiles() } returns profileList
    }

    private fun awaitCheck(
        viewModel: AutomationSetupViewModel,
        title: String,
        predicate: (ReadinessCheck) -> Boolean,
    ): ReadinessCheck {
        val deadline = System.currentTimeMillis() + 3_000
        var latest: ReadinessCheck? = null
        while (System.currentTimeMillis() < deadline) {
            testDispatcher.scheduler.advanceUntilIdle()
            latest = viewModel.uiState.value.checks.firstOrNull { it.title == title }
            if (latest != null && predicate(latest)) {
                return latest
            }
            Thread.sleep(10)
        }
        return latest ?: error("No readiness check found for $title")
    }

    private fun readinessProfile(
        id: String,
        preferredChannel: MessageChannel = MessageChannel.SMS,
        automationMode: ApprovalMode = ApprovalMode.DEFAULT,
        skipAutoWish: Boolean = false,
        hasPrimaryPhone: Boolean = false,
        hasPrimaryEmail: Boolean = false,
        hasAutomatableOccasion: Boolean = false,
        nickname: String? = null,
        notesText: String = "",
        interestsJson: String = "[]",
        sharedHistoryJson: String = "[]",
        classificationConfidence: Double = 0.0,
    ): ContactAutomationReadinessProfile {
        return ContactAutomationReadinessProfile(
            id = ContactId(id),
            preferredChannel = preferredChannel,
            automationMode = automationMode,
            skipAutoWish = skipAutoWish,
            hasPrimaryPhone = hasPrimaryPhone,
            hasPrimaryEmail = hasPrimaryEmail,
            hasAutomatableOccasion = hasAutomatableOccasion,
            nickname = nickname,
            notesText = notesText,
            interestsJson = interestsJson,
            sharedHistoryJson = sharedHistoryJson,
            classificationConfidence = classificationConfidence,
        )
    }
}
