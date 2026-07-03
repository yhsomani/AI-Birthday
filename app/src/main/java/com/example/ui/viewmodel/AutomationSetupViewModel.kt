package com.example.ui.viewmodel

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.core.gemini.GeminiClient
import com.example.core.resilience.CircuitState
import com.example.core.resilience.HealthSnapshot
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.LogEntry
import com.example.core.resilience.SensitiveLogRedactor
import com.example.core.resilience.StructuredLogger
import com.example.domain.automation.AiWishGenerationReadiness
import com.example.domain.automation.AiWishGenerationReadinessReason
import com.example.domain.automation.AutomatableEventsReadiness
import com.example.domain.automation.AutomatableEventsReadinessReason
import com.example.domain.automation.AutomaticDeliveryRoutesReadiness
import com.example.domain.automation.AutomaticDeliveryRoutesReadinessReason
import com.example.domain.automation.ChannelVerificationReadiness
import com.example.domain.automation.ChannelVerificationReadinessReason
import com.example.domain.automation.FullAutomationReadiness
import com.example.domain.automation.FullAutomationReadinessReason
import com.example.domain.automation.GeminiAccessReadiness
import com.example.domain.automation.GeminiAccessReadinessReason
import com.example.domain.automation.GeminiCircuitReadiness
import com.example.domain.automation.GeminiCircuitReadinessReason
import com.example.domain.automation.GenericMessageReadiness
import com.example.domain.automation.GenericMessageReadinessReason
import com.example.domain.automation.GoogleContactsReadiness
import com.example.domain.automation.GoogleContactsReadinessReason
import com.example.domain.automation.PersonalizationReadiness
import com.example.domain.automation.PersonalizationReadinessReason
import com.example.domain.automation.SetupAccountProviderReadinessPolicy
import com.example.domain.automation.SetupAutomationReadinessPolicy
import com.example.domain.automation.SetupChannelReadinessPolicy
import com.example.domain.automation.SetupDailyAutomationReadiness
import com.example.domain.automation.SetupDailyAutomationReadinessReason
import com.example.domain.automation.SetupDispatchRecoveryReadiness
import com.example.domain.automation.SetupDispatchRecoveryReadinessReason
import com.example.domain.automation.SetupEmailReadiness
import com.example.domain.automation.SetupEmailReadinessPolicy
import com.example.domain.automation.SetupEmailReadinessReason
import com.example.domain.automation.SetupExactSendReadiness
import com.example.domain.automation.SetupExactSendReadinessReason
import com.example.domain.automation.SetupNotificationReadiness
import com.example.domain.automation.SetupNotificationReadinessReason
import com.example.domain.automation.SetupQualityReadinessPolicy
import com.example.domain.automation.SetupReadinessRecommendationCandidate
import com.example.domain.automation.SetupReadinessRecommendationPolicy
import com.example.domain.automation.SetupReadinessSummaryPolicy
import com.example.domain.automation.SetupRecentHealthReadiness
import com.example.domain.automation.SetupRecentHealthReadinessReason
import com.example.domain.automation.SetupSystemReadinessPolicy
import com.example.domain.automation.SetupProviderCircuitState
import com.example.domain.automation.SmsSetupReadiness
import com.example.domain.automation.SmsSetupReadinessReason
import com.example.domain.automation.StyleCoachReadiness
import com.example.domain.automation.StyleCoachReadinessReason
import com.example.domain.automation.WhatsAppSetupReadiness
import com.example.domain.automation.WhatsAppSetupReadinessReason
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipActionReadinessPolicy
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.DiagnosticSnapshotId
import com.example.domain.model.contact.ContactAutomationReadinessProfile
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.style.StyleProfileRecord
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.DiagnosticSnapshotRepository
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.service.PreferencesRepository
import com.example.domain.usecase.SyncContactsUseCase
import com.example.domain.usecase.TestSendUseCase
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

enum class AiDoctorAction {
    NONE,
    REFRESH,
    TEST_AI,
    TEST_EMAIL,
    SYNC_CONTACTS,
    OPEN_SETTINGS,
    OPEN_STYLE_COACH,
    OPEN_CONTACTS,
    OPEN_MESSAGES,
    OPEN_SMS_MESSAGES,
    OPEN_WHATSAPP_MESSAGES,
    OPEN_ACTIVITY_HISTORY,
    OPEN_ACCESSIBILITY_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_APP_SETTINGS,
}

data class AiDoctorSummary(
    val title: String = "",
    val detail: String = "",
    val status: ReadinessStatus = ReadinessStatus.WARNING,
)

data class ReadinessCheck(
    val title: String,
    val detail: String,
    val status: ReadinessStatus,
    val actionLabel: String? = null,
    val action: AiDoctorAction = AiDoctorAction.NONE,
    val group: ReadinessGroup = ReadinessGroup.REQUIRED,
    val actionReadiness: RelationshipActionReadiness = RelationshipActionReadinessPolicy.fromSetupCandidates(
        listOf(
            SetupReadinessRecommendationCandidate(
                status = status,
                group = group,
                hasAction = action != AiDoctorAction.NONE && !actionLabel.isNullOrBlank(),
            )
        )
    ),
)

data class AiDoctorRecommendedFix(
    val title: String,
    val detail: String,
    val actionLabel: String,
    val action: AiDoctorAction,
    val status: ReadinessStatus,
    val group: ReadinessGroup,
    val actionReadiness: RelationshipActionReadiness = RelationshipActionReadinessPolicy.fromSetupCandidates(
        listOf(
            SetupReadinessRecommendationCandidate(
                status = status,
                group = group,
                hasAction = action != AiDoctorAction.NONE && actionLabel.isNotBlank(),
            )
        )
    ),
)

data class AutomationSetupUiState(
    val checks: List<ReadinessCheck> = emptyList(),
    val summary: AiDoctorSummary = AiDoctorSummary(),
    val recommendedFix: AiDoctorRecommendedFix? = null,
    val setupProgress: SetupProgressSummary = SetupProgressSummary(),
    val setupActionReadiness: RelationshipActionReadiness =
        RelationshipActionReadinessPolicy.fromSetupCandidates(emptyList()),
    val isRefreshing: Boolean = false,
    val isSyncingContacts: Boolean = false,
    val isTestingAi: Boolean = false,
    val isTestingEmail: Boolean = false,
    val whatsAppAutomationConsentGranted: Boolean = false,
    val operationMessage: String? = null,
)

private data class AiDoctorReport(
    val summary: AiDoctorSummary,
    val checks: List<ReadinessCheck>,
    val recommendedFix: AiDoctorRecommendedFix?,
    val setupProgress: SetupProgressSummary,
    val setupActionReadiness: RelationshipActionReadiness,
)

private data class DispatchRecoverySnapshot(
    val persistedRecoveryCount: Int,
    val persistedDeadLetterCount: Int,
    val latestPersistedAttempt: DispatchAttempt?,
)

private data class AiDoctorLiveInputs(
    val contacts: List<ContactAutomationReadinessProfile>,
    val styleProfile: StyleProfileRecord?,
    val persistedRecoveryCount: Int,
    val persistedDeadLetterCount: Int,
)

@HiltViewModel
class AutomationSetupViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val preferencesRepository: PreferencesRepository,
    private val syncContactsUseCase: SyncContactsUseCase,
    private val geminiClient: GeminiClient,
    private val contactRepository: ContactRepository,
    private val styleProfileRepository: StyleProfileRepository,
    private val testSendUseCase: TestSendUseCase,
    private val dispatchAttemptRepository: DispatchAttemptRepository,
    private val diagnosticSnapshotRepository: DiagnosticSnapshotRepository,
) : ViewModel() {
    private companion object {
        const val PERSISTED_HEALTH_SNAPSHOT_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val CHANNEL_VERIFICATION_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        const val GOOGLE_CONTACTS_SCOPE_URI = "https://www.googleapis.com/auth/contacts.readonly"
        val CHANNEL_TOKEN_PATTERN = Regex("\"([A-Za-z_]+)\"")
        val GOOGLE_CONTACTS_SCOPE = Scope(GOOGLE_CONTACTS_SCOPE_URI)
    }

    private val _uiState = MutableStateFlow(AutomationSetupUiState())
    val uiState: StateFlow<AutomationSetupUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            whatsAppAutomationConsentGranted = preferencesRepository.isWhatsAppAutomationConsentGranted()
        )
        observeReadinessInputs()
    }

    fun refreshChecks() {
        refreshChecks(clearOperationMessage = true)
    }

    private fun refreshChecks(clearOperationMessage: Boolean) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isRefreshing = true,
                    operationMessage = if (clearOperationMessage) null else state.operationMessage,
                )
            }
            val report = withContext(Dispatchers.IO) { buildReport() }
            applyReport(report) { state -> state.copy(isRefreshing = false) }
        }
    }

    private fun observeReadinessInputs() {
        viewModelScope.launch {
            combine(
                contactRepository.getAutomationReadinessProfilesFlow(),
                styleProfileRepository.getProfile(),
                preferencesRepository.observeChanges().onStart { emit(Unit) },
                dispatchAttemptRepository.countFailureRecoveryQueue(),
                dispatchAttemptRepository.countDeadLettered(),
            ) { contacts, styleProfile, _, persistedRecoveryCount, persistedDeadLetterCount ->
                AiDoctorLiveInputs(
                    contacts = contacts,
                    styleProfile = styleProfile,
                    persistedRecoveryCount = persistedRecoveryCount,
                    persistedDeadLetterCount = persistedDeadLetterCount,
                )
            }.collectLatest { inputs ->
                val report = withContext(Dispatchers.IO) { buildReport(inputs) }
                applyReport(report) { state ->
                    state.copy(
                        isRefreshing = false,
                        whatsAppAutomationConsentGranted = preferencesRepository.isWhatsAppAutomationConsentGranted(),
                    )
                }
            }
        }
    }

    private fun applyReport(
        report: AiDoctorReport,
        extraState: (AutomationSetupUiState) -> AutomationSetupUiState = { it },
    ) {
        _uiState.update { state ->
            extraState(
                state.copy(
                    checks = report.checks,
                    summary = report.summary,
                    recommendedFix = report.recommendedFix,
                    setupProgress = report.setupProgress,
                    setupActionReadiness = report.setupActionReadiness,
                ),
            )
        }
    }

    fun syncContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncingContacts = true, operationMessage = null)
            try {
                val outcome = syncContactsUseCase(forceRefresh = true)
                _uiState.value = _uiState.value.copy(
                    isSyncingContacts = false,
                    operationMessage = text(R.string.automation_setup_sync_success, outcome.googleCount),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncingContacts = false,
                    operationMessage = text(R.string.automation_setup_sync_failed),
                )
            }
            refreshChecks(clearOperationMessage = false)
        }
    }

    fun runSafeGenerationCheck() {
        val ready = preferencesRepository.isAiWishGenerationEnabled() &&
            (preferencesRepository.getGeminiApiKey().isNotBlank() || currentFirebaseUserOrNull() != null)
        val rankedBlocker = _uiState.value.recommendedFix
            ?.takeIf { it.status == ReadinessStatus.ACTION_REQUIRED }
            ?.let { it.title to it.detail }
        val firstBlocker = rankedBlocker ?: _uiState.value.checks
            .firstOrNull { it.status == ReadinessStatus.ACTION_REQUIRED }
            ?.let { it.title to it.detail }
        _uiState.value = _uiState.value.copy(
            operationMessage = if (ready) {
                firstBlocker?.let { (title, detail) ->
                    text(R.string.automation_setup_dry_run_blocker, title, detail)
                } ?: text(R.string.automation_setup_dry_run_ready)
            } else {
                text(R.string.automation_setup_dry_run_missing_ai)
            }
        )
    }

    fun testAiGeneration() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingAi = true, operationMessage = null)
            try {
                val response = withContext(Dispatchers.IO) {
                    geminiClient.generate(
                        """
                        Return ONLY valid JSON:
                        {"short":"Ready","standard":"RelateAI automation check is ready.","long":"RelateAI automation check is ready.","formal":"RelateAI automation check is ready.","funny":"RelateAI automation check is ready.","emotional":"RelateAI automation check is ready.","recommended":"standard"}
                        """.trimIndent()
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isTestingAi = false,
                    operationMessage = if (response.contains("\"error\"", ignoreCase = true)) {
                        diagnoseAiFailure(response)
                    } else {
                        text(R.string.automation_setup_ai_test_success)
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingAi = false,
                    operationMessage = text(R.string.automation_setup_ai_test_failed),
                )
            }
            refreshChecks(clearOperationMessage = false)
        }
    }

    fun testEmailSend() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingEmail = true, operationMessage = null)
            val message = when (testSendUseCase(text(R.string.automation_setup_email_test_message))) {
                TestSendUseCase.Outcome.Sent -> text(R.string.automation_setup_email_test_success)
                TestSendUseCase.Outcome.MissingEmailSetup -> text(R.string.automation_setup_email_missing)
                TestSendUseCase.Outcome.BlankMessage -> text(R.string.automation_setup_email_test_failed)
                TestSendUseCase.Outcome.SendFailed -> text(R.string.automation_setup_email_test_failed)
            }
            _uiState.value = _uiState.value.copy(
                isTestingEmail = false,
                operationMessage = message,
            )
            refreshChecks(clearOperationMessage = false)
        }
    }

    fun setWhatsAppAutomationConsent(granted: Boolean) {
        preferencesRepository.setWhatsAppAutomationConsentGranted(granted)
        _uiState.value = _uiState.value.copy(
            whatsAppAutomationConsentGranted = granted,
            operationMessage = text(R.string.automation_setup_whatsapp_consent_saved),
        )
        refreshChecks(clearOperationMessage = false)
    }

    private suspend fun buildReport(inputs: AiDoctorLiveInputs? = null): AiDoctorReport {
        val workInfos = try {
            WorkManager.getInstance(appContext).getWorkInfosByTag("daily_trigger").get()
        } catch (e: Exception) {
            emptyList<WorkInfo>()
        }
        val dailyScheduled = workInfos.any {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        }
        val health = HealthMonitor.snapshot()
        val recentErrors = StructuredLogger.getErrors().takeLast(3)
        val currentUser = currentFirebaseUserOrNull()
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val contacts = inputs?.contacts
            ?: runCatching { contactRepository.getAutomationReadinessProfiles() }.getOrDefault(emptyList())
        val styleProfile = inputs?.styleProfile
            ?: runCatching { styleProfileRepository.getProfileOnce() }.getOrNull()
        val styleSampleCount = maxOf(
            styleProfile?.sampleCount ?: 0,
            countJsonArrayItems(styleProfile?.sampleMessagesJson),
        )
        val hasGoogleContactsAccess = hasGoogleContactsAccess()
        val hasGeminiApiKey = preferencesRepository.getGeminiApiKey().isNotBlank()
        val hasFirebaseAuth = currentUser != null
        val hasGeminiAccess = hasGeminiApiKey || hasFirebaseAuth
        val globalAutomationMode = preferencesRepository.getGlobalAutomationMode()
        val aiEnabled = preferencesRepository.isAiWishGenerationEnabled()
        val notificationsAllowed = runCatching { hasNotificationPermission() }.getOrDefault(false)
        val smsAllowed = runCatching { hasSmsPermission() }.getOrDefault(false)
        val whatsAppConsentGranted = preferencesRepository.isWhatsAppAutomationConsentGranted()
        val whatsAppAutomationEnabled = runCatching { isWhatsAppAutomationServiceEnabled() }.getOrDefault(false)
        val exactSendsAllowed = runCatching {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        }.getOrDefault(false)
        val dispatchRecovery = loadDispatchRecoverySnapshot(
            persistedRecoveryCount = inputs?.persistedRecoveryCount,
            persistedDeadLetterCount = inputs?.persistedDeadLetterCount,
        )
        val persistedHealthSnapshot = loadRecentPersistedHealthSnapshot()
        val senderEmail = preferencesRepository.getSenderEmail().trim()
        val senderEmailPassword = preferencesRepository.getSenderEmailPassword().trim()
        val senderEmailReady = SetupEmailReadinessPolicy.isSenderReady(
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        )
        val blockedChannels = preferencesRepository.getChannelBlackout().toChannelSet()
        val selectedChannelCounts = SetupAutomationReadinessPolicy.selectedAutomaticChannelCounts(
            contacts = contacts,
            senderEmailReady = senderEmailReady,
            blockedChannels = blockedChannels,
        )
        val selectedChannels = selectedChannelCounts.filterValues { it > 0 }.keys
        val channelVerificationSinceMs = System.currentTimeMillis() - CHANNEL_VERIFICATION_WINDOW_MS
        val emailSelfTestVerified = senderEmailReady &&
            preferencesRepository.getLastSuccessfulEmailTestMs() >= channelVerificationSinceMs &&
            preferencesRepository.getLastSuccessfulEmailTestSender().equals(senderEmail, ignoreCase = true)
        val successfulChannels = runCatching {
            dispatchAttemptRepository.getSuccessfulChannelsSince(channelVerificationSinceMs)
        }.getOrDefault(emptySet()) + if (emailSelfTestVerified) setOf(MessageChannel.EMAIL) else emptySet()
        val emailPreferredContacts = contacts.count {
            it.preferredChannel == MessageChannel.EMAIL
        }
        val whatsAppInstalled = runCatching { isWhatsAppInstalled() }.getOrDefault(false)
        val hasRecentHealthEvidence = recentErrors.isNotEmpty() ||
            health.recentErrors.isNotEmpty() ||
            persistedHealthSnapshot != null

        val checks = listOf(
            SetupAccountProviderReadinessPolicy.evaluateGoogleContacts(
                hasGoogleContactsAccess = hasGoogleContactsAccess,
            ).toReadinessCheck(),
            SetupAccountProviderReadinessPolicy.evaluateGeminiAccess(
                hasGeminiApiKey = hasGeminiApiKey,
                hasFirebaseAuth = hasFirebaseAuth,
            ).toReadinessCheck(),
            SetupAccountProviderReadinessPolicy.evaluateAiWishGeneration(
                aiWishGenerationEnabled = aiEnabled,
            ).toReadinessCheck(),
            fullAutomationModeCheck(
                globalAutomationMode = globalAutomationMode,
                contacts = contacts,
            ),
            automatableEventsCheck(contacts),
            automaticDeliveryRoutesCheck(
                contacts = contacts,
                senderEmailReady = senderEmailReady,
                blockedChannels = blockedChannels,
            ),
            channelVerificationCheck(
                selectedChannels = selectedChannels,
                successfulChannels = successfulChannels,
            ),
            SetupQualityReadinessPolicy.evaluateStyleCoach(
                styleSampleCount = styleSampleCount,
            ).toReadinessCheck(),
            personalizationCheck(contacts),
            genericMessagesCheck(contacts),
            SetupAccountProviderReadinessPolicy.evaluateGeminiCircuit(
                circuitState = health.circuitBreakerStates["gemini"].toSetupProviderCircuitState(),
            ).toReadinessCheck(),
            SetupSystemReadinessPolicy.evaluateNotificationPermission(
                notificationsAllowed = notificationsAllowed,
            ).toReadinessCheck(),
            smsReadinessCheck(
                smsAllowed = smsAllowed,
                selectedSmsContactCount = selectedChannelCounts[MessageChannel.SMS] ?: 0,
                smsDisabled = MessageChannel.SMS in blockedChannels,
            ),
            SetupEmailReadinessPolicy.evaluate(
                senderEmail = senderEmail,
                senderEmailPassword = senderEmailPassword,
                emailSelfTestVerified = emailSelfTestVerified,
                emailPreferredContactCount = emailPreferredContacts,
            ).toReadinessCheck(),
            whatsAppReadinessCheck(
                consentGranted = whatsAppConsentGranted,
                accessibilityEnabled = whatsAppAutomationEnabled,
                whatsAppInstalled = whatsAppInstalled,
                selectedWhatsAppContactCount = selectedChannelCounts[MessageChannel.WHATSAPP] ?: 0,
                whatsAppDisabled = MessageChannel.WHATSAPP in blockedChannels,
            ),
            SetupSystemReadinessPolicy.evaluateExactSends(
                exactSendsAllowed = exactSendsAllowed,
            ).toReadinessCheck(),
            SetupSystemReadinessPolicy.evaluateDailyAutomation(
                dailyScheduled = dailyScheduled,
            ).toReadinessCheck(),
            SetupSystemReadinessPolicy.evaluateRecentHealth(
                hasRecentHealthEvidence = hasRecentHealthEvidence,
            ).toReadinessCheck(
                recentErrors = recentErrors,
                health = health,
                persistedHealthSnapshot = persistedHealthSnapshot,
            ),
            SetupSystemReadinessPolicy.evaluateDispatchRecovery(
                persistedRecoveryCount = dispatchRecovery.persistedRecoveryCount,
                persistedDeadLetterCount = dispatchRecovery.persistedDeadLetterCount,
            ).toReadinessCheck(dispatchRecovery),
        )
        return AiDoctorReport(
            summary = checks.toSummary(),
            checks = checks,
            recommendedFix = checks.toRecommendedFix(),
            setupProgress = checks.toSetupProgressSummary(),
            setupActionReadiness = checks.toSetupActionReadiness(),
        ).also { report ->
            persistAiDoctorSnapshot(report)
        }
    }

    private fun List<LogEntry>.toRecentErrorDetail(
        health: HealthSnapshot,
        persistedHealthSnapshot: DiagnosticSnapshot?,
    ): String {
        val liveError = lastOrNull()?.message ?: health.recentErrors.lastOrNull()
        return when {
            liveError != null -> diagnoseAiFailure(liveError)
            persistedHealthSnapshot != null -> text(
                R.string.automation_setup_ai_error_recent,
                SensitiveLogRedactor.redact(persistedHealthSnapshot.summary).take(160),
            )
            else -> text(R.string.automation_setup_recent_errors_none)
        }
    }

    private fun GoogleContactsReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_google_contacts),
            detail = when (reason) {
                GoogleContactsReadinessReason.READY ->
                    text(R.string.automation_setup_google_contacts_ok)
                GoogleContactsReadinessReason.ACCESS_MISSING ->
                    text(R.string.automation_setup_google_contacts_missing)
            },
            status = status,
            actionLabel = when (reason) {
                GoogleContactsReadinessReason.READY -> null
                GoogleContactsReadinessReason.ACCESS_MISSING ->
                    text(R.string.automation_setup_action_sync_contacts)
            },
            action = when (reason) {
                GoogleContactsReadinessReason.READY -> AiDoctorAction.NONE
                GoogleContactsReadinessReason.ACCESS_MISSING -> AiDoctorAction.SYNC_CONTACTS
            },
            group = group,
        )
    }

    private fun GeminiAccessReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_gemini),
            detail = when (reason) {
                GeminiAccessReadinessReason.API_KEY_CONFIGURED ->
                    text(R.string.automation_setup_gemini_key_ok)
                GeminiAccessReadinessReason.FIREBASE_AUTH_AVAILABLE ->
                    text(R.string.automation_setup_gemini_auth_ok)
                GeminiAccessReadinessReason.MISSING_ACCESS ->
                    text(R.string.automation_setup_gemini_auth_missing)
            },
            status = status,
            actionLabel = when (reason) {
                GeminiAccessReadinessReason.API_KEY_CONFIGURED,
                GeminiAccessReadinessReason.FIREBASE_AUTH_AVAILABLE ->
                    text(R.string.automation_setup_action_test_ai)
                GeminiAccessReadinessReason.MISSING_ACCESS ->
                    text(R.string.automation_setup_action_open_settings)
            },
            action = when (reason) {
                GeminiAccessReadinessReason.API_KEY_CONFIGURED,
                GeminiAccessReadinessReason.FIREBASE_AUTH_AVAILABLE -> AiDoctorAction.TEST_AI
                GeminiAccessReadinessReason.MISSING_ACCESS -> AiDoctorAction.OPEN_SETTINGS
            },
            group = group,
        )
    }

    private fun AiWishGenerationReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_ai_wish_generation),
            detail = when (reason) {
                AiWishGenerationReadinessReason.ENABLED ->
                    text(R.string.automation_setup_ai_wish_enabled)
                AiWishGenerationReadinessReason.DISABLED ->
                    text(R.string.automation_setup_ai_wish_disabled)
            },
            status = status,
            actionLabel = when (reason) {
                AiWishGenerationReadinessReason.ENABLED -> null
                AiWishGenerationReadinessReason.DISABLED ->
                    text(R.string.automation_setup_action_open_settings)
            },
            action = when (reason) {
                AiWishGenerationReadinessReason.ENABLED -> AiDoctorAction.NONE
                AiWishGenerationReadinessReason.DISABLED -> AiDoctorAction.OPEN_SETTINGS
            },
            group = group,
        )
    }

    private fun GeminiCircuitReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_gemini_circuit),
            detail = when (reason) {
                GeminiCircuitReadinessReason.NO_STATE ->
                    text(R.string.automation_setup_gemini_circuit_none)
                GeminiCircuitReadinessReason.CLOSED ->
                    text(R.string.automation_setup_gemini_circuit_ok)
                GeminiCircuitReadinessReason.HALF_OPEN,
                GeminiCircuitReadinessReason.OPEN -> text(
                    R.string.automation_setup_gemini_circuit_state,
                    circuitState.name,
                )
            },
            status = status,
            actionLabel = when (reason) {
                GeminiCircuitReadinessReason.OPEN ->
                    text(R.string.automation_setup_action_test_ai)
                GeminiCircuitReadinessReason.NO_STATE,
                GeminiCircuitReadinessReason.CLOSED,
                GeminiCircuitReadinessReason.HALF_OPEN -> null
            },
            action = when (reason) {
                GeminiCircuitReadinessReason.OPEN -> AiDoctorAction.TEST_AI
                GeminiCircuitReadinessReason.NO_STATE,
                GeminiCircuitReadinessReason.CLOSED,
                GeminiCircuitReadinessReason.HALF_OPEN -> AiDoctorAction.NONE
            },
            group = group,
        )
    }

    private fun SetupNotificationReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_notifications),
            detail = when (reason) {
                SetupNotificationReadinessReason.READY -> text(R.string.automation_setup_notifications_ok)
                SetupNotificationReadinessReason.PERMISSION_MISSING ->
                    text(R.string.automation_setup_notifications_missing)
            },
            status = status,
            actionLabel = when (reason) {
                SetupNotificationReadinessReason.READY -> null
                SetupNotificationReadinessReason.PERMISSION_MISSING ->
                    text(R.string.automation_setup_action_app_settings)
            },
            action = when (reason) {
                SetupNotificationReadinessReason.READY -> AiDoctorAction.NONE
                SetupNotificationReadinessReason.PERMISSION_MISSING -> AiDoctorAction.OPEN_APP_SETTINGS
            },
            group = group,
        )
    }

    private fun CircuitState?.toSetupProviderCircuitState(): SetupProviderCircuitState {
        return when (this) {
            null -> SetupProviderCircuitState.NONE
            CircuitState.CLOSED -> SetupProviderCircuitState.CLOSED
            CircuitState.HALF_OPEN -> SetupProviderCircuitState.HALF_OPEN
            CircuitState.OPEN -> SetupProviderCircuitState.OPEN
        }
    }

    private fun SetupExactSendReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_exact_sends),
            detail = when (reason) {
                SetupExactSendReadinessReason.READY -> text(R.string.automation_setup_exact_sends_ok)
                SetupExactSendReadinessReason.PERMISSION_MISSING ->
                    text(R.string.automation_setup_exact_sends_missing)
            },
            status = status,
            actionLabel = when (reason) {
                SetupExactSendReadinessReason.READY -> null
                SetupExactSendReadinessReason.PERMISSION_MISSING ->
                    text(R.string.automation_setup_action_app_settings)
            },
            action = when (reason) {
                SetupExactSendReadinessReason.READY -> AiDoctorAction.NONE
                SetupExactSendReadinessReason.PERMISSION_MISSING -> AiDoctorAction.OPEN_APP_SETTINGS
            },
            group = group,
        )
    }

    private fun SetupDailyAutomationReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_daily_automation),
            detail = when (reason) {
                SetupDailyAutomationReadinessReason.SCHEDULED -> text(R.string.automation_setup_daily_ok)
                SetupDailyAutomationReadinessReason.MISSING -> text(R.string.automation_setup_daily_missing)
            },
            status = status,
            actionLabel = when (reason) {
                SetupDailyAutomationReadinessReason.SCHEDULED -> null
                SetupDailyAutomationReadinessReason.MISSING -> text(R.string.automation_setup_action_refresh)
            },
            action = when (reason) {
                SetupDailyAutomationReadinessReason.SCHEDULED -> AiDoctorAction.NONE
                SetupDailyAutomationReadinessReason.MISSING -> AiDoctorAction.REFRESH
            },
            group = group,
        )
    }

    private fun SetupRecentHealthReadiness.toReadinessCheck(
        recentErrors: List<LogEntry>,
        health: HealthSnapshot,
        persistedHealthSnapshot: DiagnosticSnapshot?,
    ): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_recent_errors),
            detail = when (reason) {
                SetupRecentHealthReadinessReason.CLEAR ->
                    text(R.string.automation_setup_recent_errors_none)
                SetupRecentHealthReadinessReason.RECENT_EVIDENCE ->
                    recentErrors.toRecentErrorDetail(health, persistedHealthSnapshot)
            },
            status = status,
            actionLabel = when (reason) {
                SetupRecentHealthReadinessReason.CLEAR -> null
                SetupRecentHealthReadinessReason.RECENT_EVIDENCE ->
                    text(R.string.automation_setup_action_view_activity)
            },
            action = when (reason) {
                SetupRecentHealthReadinessReason.CLEAR -> AiDoctorAction.NONE
                SetupRecentHealthReadinessReason.RECENT_EVIDENCE -> AiDoctorAction.OPEN_ACTIVITY_HISTORY
            },
            group = group,
        )
    }

    private fun SetupDispatchRecoveryReadiness.toReadinessCheck(
        dispatchRecovery: DispatchRecoverySnapshot,
    ): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_dead_letter),
            detail = dispatchRecovery.toReadinessDetail(),
            status = status,
            actionLabel = when (reason) {
                SetupDispatchRecoveryReadinessReason.CLEAR -> null
                SetupDispatchRecoveryReadinessReason.RECOVERY_QUEUE_PRESENT ->
                    text(R.string.automation_setup_action_view_activity)
            },
            action = when (reason) {
                SetupDispatchRecoveryReadinessReason.CLEAR -> AiDoctorAction.NONE
                SetupDispatchRecoveryReadinessReason.RECOVERY_QUEUE_PRESENT ->
                    AiDoctorAction.OPEN_ACTIVITY_HISTORY
            },
            group = group,
        )
    }

    private fun SetupEmailReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_email),
            detail = when (reason) {
                SetupEmailReadinessReason.INVALID_SENDER ->
                    text(R.string.automation_setup_email_invalid)
                SetupEmailReadinessReason.VERIFIED ->
                    text(R.string.automation_setup_email_ok)
                SetupEmailReadinessReason.UNVERIFIED ->
                    text(R.string.automation_setup_email_unverified)
                SetupEmailReadinessReason.MISSING_FOR_CONTACTS -> text(
                    R.string.automation_setup_email_missing_for_contacts,
                    emailPreferredContactCount,
                )
                SetupEmailReadinessReason.OPTIONAL ->
                    text(R.string.automation_setup_email_optional)
            },
            status = status,
            actionLabel = when (reason) {
                SetupEmailReadinessReason.INVALID_SENDER,
                SetupEmailReadinessReason.MISSING_FOR_CONTACTS,
                SetupEmailReadinessReason.OPTIONAL -> text(R.string.automation_setup_action_open_settings)
                SetupEmailReadinessReason.UNVERIFIED -> text(R.string.automation_setup_action_test_email)
                SetupEmailReadinessReason.VERIFIED -> null
            },
            action = when (reason) {
                SetupEmailReadinessReason.INVALID_SENDER,
                SetupEmailReadinessReason.MISSING_FOR_CONTACTS,
                SetupEmailReadinessReason.OPTIONAL -> AiDoctorAction.OPEN_SETTINGS
                SetupEmailReadinessReason.UNVERIFIED -> AiDoctorAction.TEST_EMAIL
                SetupEmailReadinessReason.VERIFIED -> AiDoctorAction.NONE
            },
            group = group,
        )
    }

    private fun StyleCoachReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_style_coach),
            detail = when (reason) {
                StyleCoachReadinessReason.TRAINED ->
                    text(R.string.automation_setup_style_trained, sampleCount)
                StyleCoachReadinessReason.NEEDS_MORE ->
                    text(R.string.automation_setup_style_needs_more, samplesNeeded)
                StyleCoachReadinessReason.EMPTY ->
                    text(R.string.automation_setup_style_empty)
            },
            status = status,
            actionLabel = when (reason) {
                StyleCoachReadinessReason.TRAINED -> null
                StyleCoachReadinessReason.NEEDS_MORE,
                StyleCoachReadinessReason.EMPTY -> text(R.string.automation_setup_action_open_style_coach)
            },
            action = when (reason) {
                StyleCoachReadinessReason.TRAINED -> AiDoctorAction.NONE
                StyleCoachReadinessReason.NEEDS_MORE,
                StyleCoachReadinessReason.EMPTY -> AiDoctorAction.OPEN_STYLE_COACH
            },
            group = group,
        )
    }

    private fun PersonalizationReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_personalization),
            detail = when (reason) {
                PersonalizationReadinessReason.EMPTY ->
                    text(R.string.automation_setup_personalization_empty)
                PersonalizationReadinessReason.READY -> text(
                    R.string.automation_setup_personalization_ok,
                    enrichedContactCount,
                    totalContactCount,
                )
                PersonalizationReadinessReason.LOW -> text(
                    R.string.automation_setup_personalization_low,
                    enrichedContactCount,
                    totalContactCount,
                )
            },
            status = status,
            actionLabel = when (reason) {
                PersonalizationReadinessReason.EMPTY ->
                    text(R.string.automation_setup_action_sync_contacts)
                PersonalizationReadinessReason.READY -> null
                PersonalizationReadinessReason.LOW ->
                    text(R.string.automation_setup_action_review_contacts)
            },
            action = when (reason) {
                PersonalizationReadinessReason.EMPTY -> AiDoctorAction.SYNC_CONTACTS
                PersonalizationReadinessReason.READY -> AiDoctorAction.NONE
                PersonalizationReadinessReason.LOW -> AiDoctorAction.OPEN_CONTACTS
            },
            group = group,
        )
    }

    private fun GenericMessageReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_generic_messages),
            detail = when (reason) {
                GenericMessageReadinessReason.EMPTY ->
                    text(R.string.automation_setup_generic_messages_empty)
                GenericMessageReadinessReason.READY ->
                    text(R.string.automation_setup_generic_messages_ok)
                GenericMessageReadinessReason.RISK -> text(
                    R.string.automation_setup_generic_messages_low,
                    genericRiskCount,
                    totalContactCount,
                )
            },
            status = status,
            actionLabel = when (reason) {
                GenericMessageReadinessReason.EMPTY ->
                    text(R.string.automation_setup_action_sync_contacts)
                GenericMessageReadinessReason.READY -> null
                GenericMessageReadinessReason.RISK ->
                    text(R.string.automation_setup_action_review_contacts)
            },
            action = when (reason) {
                GenericMessageReadinessReason.EMPTY -> AiDoctorAction.SYNC_CONTACTS
                GenericMessageReadinessReason.READY -> AiDoctorAction.NONE
                GenericMessageReadinessReason.RISK -> AiDoctorAction.OPEN_CONTACTS
            },
            group = group,
        )
    }

    private suspend fun loadRecentPersistedHealthSnapshot(): DiagnosticSnapshot? {
        val now = System.currentTimeMillis()
        return runCatching {
            diagnosticSnapshotRepository.getLatestBySource(DiagnosticSnapshotSource.HEALTH_MONITOR)
                ?.takeIf { it.status != DiagnosticSnapshotStatus.OK }
                ?.takeIf { now - it.createdAtMs <= PERSISTED_HEALTH_SNAPSHOT_TTL_MS }
        }.getOrNull()
    }

    private suspend fun persistAiDoctorSnapshot(report: AiDoctorReport) {
        runCatching {
            diagnosticSnapshotRepository.record(report.toDiagnosticSnapshot())
        }
    }

    private fun AiDoctorReport.toDiagnosticSnapshot(): DiagnosticSnapshot {
        val checksJson = JSONArray().also { checksArray ->
            checks.forEach { check ->
                checksArray.put(
                    JSONObject()
                        .put("title", check.title)
                        .put("status", check.status.name)
                        .put("group", check.group.name)
                        .put("action", check.action.name)
                        .put("detail", check.detail),
                )
            }
        }
        val payload = JSONObject()
            .put("source", DiagnosticSnapshotSource.AI_DOCTOR.raw)
            .put("summaryStatus", summary.status.name)
            .put("recommendedAction", recommendedFix?.action?.name)
            .put("completedSteps", setupProgress.completedSteps)
            .put("totalSteps", setupProgress.totalSteps)
            .put("checks", checksJson)
            .toString()
        return DiagnosticSnapshot(
            id = DiagnosticSnapshotId("ai-doctor-${UUID.randomUUID()}"),
            source = DiagnosticSnapshotSource.AI_DOCTOR,
            status = summary.status.toDiagnosticSnapshotStatus(),
            summary = SensitiveLogRedactor.redact("${summary.title}: ${summary.detail}"),
            checksJson = SensitiveLogRedactor.redact(payload),
            createdAtMs = System.currentTimeMillis(),
        )
    }

    private fun ReadinessStatus.toDiagnosticSnapshotStatus(): DiagnosticSnapshotStatus = when (this) {
        ReadinessStatus.OK -> DiagnosticSnapshotStatus.OK
        ReadinessStatus.WARNING -> DiagnosticSnapshotStatus.WARNING
        ReadinessStatus.ACTION_REQUIRED -> DiagnosticSnapshotStatus.ACTION_REQUIRED
    }

    private suspend fun loadDispatchRecoverySnapshot(
        persistedRecoveryCount: Int? = null,
        persistedDeadLetterCount: Int? = null,
    ): DispatchRecoverySnapshot {
        val resolvedPersistedRecoveryCount = persistedRecoveryCount ?: runCatching {
            dispatchAttemptRepository.countFailureRecoveryQueue().first()
        }.getOrDefault(0)
        val resolvedPersistedDeadLetterCount = persistedDeadLetterCount ?: runCatching {
            dispatchAttemptRepository.countDeadLettered().first()
        }.getOrDefault(0)
        val latestPersistedAttempt = runCatching {
            dispatchAttemptRepository.getFailureRecoveryQueue(limit = 1).firstOrNull()
        }.getOrNull()
        return DispatchRecoverySnapshot(
            persistedRecoveryCount = resolvedPersistedRecoveryCount,
            persistedDeadLetterCount = resolvedPersistedDeadLetterCount,
            latestPersistedAttempt = latestPersistedAttempt,
        )
    }

    private val DispatchRecoverySnapshot.totalRecoveryCount: Int
        get() = persistedRecoveryCount

    private fun DispatchRecoverySnapshot.toReadinessDetail(): String {
        val summary = when {
            totalRecoveryCount == 0 -> text(R.string.automation_setup_dead_letter_none)
            else -> text(
                R.string.automation_setup_dead_letter_count,
                persistedRecoveryCount,
                persistedDeadLetterCount,
            )
        }
        val latest = latestPersistedAttempt ?: return summary
        return "$summary " + text(
            R.string.automation_setup_dead_letter_latest,
            latest.channel.raw,
            latest.result.raw,
            latest.messageDraftId.value,
        )
    }

    private fun FullAutomationReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_full_automation),
            detail = when (reason) {
                FullAutomationReadinessReason.MODE_DISABLED -> text(
                    R.string.automation_setup_full_automation_disabled,
                    globalAutomationMode.label(),
                )
                FullAutomationReadinessReason.CONTACT_OVERRIDES -> text(
                    R.string.automation_setup_full_automation_contact_overrides,
                    reviewFirstOverrideCount,
                )
                FullAutomationReadinessReason.READY ->
                    text(R.string.automation_setup_full_automation_ok)
            },
            status = status,
            actionLabel = when (reason) {
                FullAutomationReadinessReason.MODE_DISABLED ->
                    text(R.string.automation_setup_action_open_settings)
                FullAutomationReadinessReason.CONTACT_OVERRIDES ->
                    text(R.string.automation_setup_action_review_contacts)
                FullAutomationReadinessReason.READY -> null
            },
            action = when (reason) {
                FullAutomationReadinessReason.MODE_DISABLED -> AiDoctorAction.OPEN_SETTINGS
                FullAutomationReadinessReason.CONTACT_OVERRIDES -> AiDoctorAction.OPEN_CONTACTS
                FullAutomationReadinessReason.READY -> AiDoctorAction.NONE
            },
            group = group,
        )
    }

    private fun AutomatableEventsReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_automatable_events),
            detail = when (reason) {
                AutomatableEventsReadinessReason.NO_CONTACTS ->
                    text(R.string.automation_setup_automatable_events_empty)
                AutomatableEventsReadinessReason.READY ->
                    text(R.string.automation_setup_automatable_events_ok, eventReadyCount)
                AutomatableEventsReadinessReason.MISSING_EVENTS -> text(
                    R.string.automation_setup_automatable_events_missing,
                    totalContactCount - eventReadyCount,
                    totalContactCount,
                )
            },
            status = status,
            actionLabel = when (reason) {
                AutomatableEventsReadinessReason.NO_CONTACTS ->
                    text(R.string.automation_setup_action_sync_contacts)
                AutomatableEventsReadinessReason.READY -> null
                AutomatableEventsReadinessReason.MISSING_EVENTS ->
                    text(R.string.automation_setup_action_review_contacts)
            },
            action = when (reason) {
                AutomatableEventsReadinessReason.NO_CONTACTS -> AiDoctorAction.SYNC_CONTACTS
                AutomatableEventsReadinessReason.READY -> AiDoctorAction.NONE
                AutomatableEventsReadinessReason.MISSING_EVENTS -> AiDoctorAction.OPEN_CONTACTS
            },
            group = group,
        )
    }

    private fun AutomaticDeliveryRoutesReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_delivery_routes),
            detail = when (reason) {
                AutomaticDeliveryRoutesReadinessReason.NO_EVENT_CONTACTS ->
                    text(R.string.automation_setup_delivery_routes_no_events)
                AutomaticDeliveryRoutesReadinessReason.READY ->
                    text(R.string.automation_setup_delivery_routes_ok, routableContactCount)
                AutomaticDeliveryRoutesReadinessReason.MISSING_ROUTES -> text(
                    R.string.automation_setup_delivery_routes_missing,
                    eventContactCount - routableContactCount,
                    eventContactCount,
                )
            },
            status = status,
            actionLabel = when (reason) {
                AutomaticDeliveryRoutesReadinessReason.NO_EVENT_CONTACTS,
                AutomaticDeliveryRoutesReadinessReason.MISSING_ROUTES ->
                    text(R.string.automation_setup_action_review_contacts)
                AutomaticDeliveryRoutesReadinessReason.READY -> null
            },
            action = when (reason) {
                AutomaticDeliveryRoutesReadinessReason.NO_EVENT_CONTACTS,
                AutomaticDeliveryRoutesReadinessReason.MISSING_ROUTES -> AiDoctorAction.OPEN_CONTACTS
                AutomaticDeliveryRoutesReadinessReason.READY -> AiDoctorAction.NONE
            },
            group = group,
        )
    }

    private fun fullAutomationModeCheck(
        globalAutomationMode: ApprovalMode,
        contacts: List<ContactAutomationReadinessProfile>,
    ): ReadinessCheck {
        return SetupAutomationReadinessPolicy.evaluateFullAutomation(
            globalAutomationMode = globalAutomationMode,
            contacts = contacts,
        ).toReadinessCheck()
    }

    private fun automatableEventsCheck(contacts: List<ContactAutomationReadinessProfile>): ReadinessCheck {
        return SetupAutomationReadinessPolicy.evaluateAutomatableEvents(contacts).toReadinessCheck()
    }

    private fun automaticDeliveryRoutesCheck(
        contacts: List<ContactAutomationReadinessProfile>,
        senderEmailReady: Boolean,
        blockedChannels: Set<MessageChannel>,
    ): ReadinessCheck {
        return SetupAutomationReadinessPolicy.evaluateDeliveryRoutes(
            contacts = contacts,
            senderEmailReady = senderEmailReady,
            blockedChannels = blockedChannels,
        ).toReadinessCheck()
    }

    private fun channelVerificationCheck(
        selectedChannels: Set<MessageChannel>,
        successfulChannels: Set<MessageChannel>,
    ): ReadinessCheck {
        return SetupChannelReadinessPolicy.evaluateChannelVerification(
            selectedChannels = selectedChannels,
            successfulChannels = successfulChannels,
        ).toReadinessCheck()
    }

    private fun ChannelVerificationReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_channel_verification),
            detail = when (reason) {
                ChannelVerificationReadinessReason.NO_ROUTES ->
                    text(R.string.automation_setup_channel_verification_no_routes)
                ChannelVerificationReadinessReason.VERIFIED -> text(
                    R.string.automation_setup_channel_verification_ok,
                    selectedChannels.toChannelLabelList(),
                )
                ChannelVerificationReadinessReason.EMAIL_TEST_REQUIRED,
                ChannelVerificationReadinessReason.REVIEW_SMS_MESSAGES,
                ChannelVerificationReadinessReason.REVIEW_WHATSAPP_MESSAGES,
                ChannelVerificationReadinessReason.VIEW_ACTIVITY -> text(
                    R.string.automation_setup_channel_verification_missing,
                    unverifiedChannels.toChannelLabelList(),
                )
            },
            status = status,
            actionLabel = channelVerificationActionLabel(),
            action = channelVerificationAction(),
            group = group,
        )
    }

    private fun ChannelVerificationReadiness.channelVerificationActionLabel(): String? {
        return when (reason) {
            ChannelVerificationReadinessReason.NO_ROUTES -> text(R.string.automation_setup_action_review_contacts)
            ChannelVerificationReadinessReason.VERIFIED -> null
            ChannelVerificationReadinessReason.EMAIL_TEST_REQUIRED -> text(R.string.automation_setup_action_test_email)
            ChannelVerificationReadinessReason.REVIEW_SMS_MESSAGES ->
                text(R.string.automation_setup_action_review_sms_messages)
            ChannelVerificationReadinessReason.REVIEW_WHATSAPP_MESSAGES ->
                text(R.string.automation_setup_action_review_whatsapp_messages)
            ChannelVerificationReadinessReason.VIEW_ACTIVITY -> text(R.string.automation_setup_action_view_activity)
        }
    }

    private fun ChannelVerificationReadiness.channelVerificationAction(): AiDoctorAction {
        return when (reason) {
            ChannelVerificationReadinessReason.NO_ROUTES -> AiDoctorAction.OPEN_CONTACTS
            ChannelVerificationReadinessReason.VERIFIED -> AiDoctorAction.NONE
            ChannelVerificationReadinessReason.EMAIL_TEST_REQUIRED -> AiDoctorAction.TEST_EMAIL
            ChannelVerificationReadinessReason.REVIEW_SMS_MESSAGES -> AiDoctorAction.OPEN_SMS_MESSAGES
            ChannelVerificationReadinessReason.REVIEW_WHATSAPP_MESSAGES -> AiDoctorAction.OPEN_WHATSAPP_MESSAGES
            ChannelVerificationReadinessReason.VIEW_ACTIVITY -> AiDoctorAction.OPEN_ACTIVITY_HISTORY
        }
    }

    private fun smsReadinessCheck(
        smsAllowed: Boolean,
        selectedSmsContactCount: Int,
        smsDisabled: Boolean,
    ): ReadinessCheck {
        return SetupChannelReadinessPolicy.evaluateSms(
            smsAllowed = smsAllowed,
            selectedSmsContactCount = selectedSmsContactCount,
            smsDisabled = smsDisabled,
        ).toReadinessCheck()
    }

    private fun whatsAppReadinessCheck(
        consentGranted: Boolean,
        accessibilityEnabled: Boolean,
        whatsAppInstalled: Boolean,
        selectedWhatsAppContactCount: Int,
        whatsAppDisabled: Boolean,
    ): ReadinessCheck {
        return SetupChannelReadinessPolicy.evaluateWhatsApp(
            consentGranted = consentGranted,
            accessibilityEnabled = accessibilityEnabled,
            whatsAppInstalled = whatsAppInstalled,
            selectedWhatsAppContactCount = selectedWhatsAppContactCount,
            whatsAppDisabled = whatsAppDisabled,
        ).toReadinessCheck()
    }

    private fun SmsSetupReadiness.toReadinessCheck(): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_sms),
            detail = when (reason) {
                SmsSetupReadinessReason.DISABLED -> text(R.string.automation_setup_sms_disabled)
                SmsSetupReadinessReason.NOT_USED -> text(R.string.automation_setup_sms_not_used)
                SmsSetupReadinessReason.READY -> text(
                    R.string.automation_setup_sms_ok_for_contacts,
                    selectedContactCount,
                )
                SmsSetupReadinessReason.PERMISSION_MISSING -> text(
                    R.string.automation_setup_sms_missing_for_contacts,
                    selectedContactCount,
                )
            },
            status = status,
            actionLabel = if (reason == SmsSetupReadinessReason.PERMISSION_MISSING) {
                text(R.string.automation_setup_action_app_settings)
            } else {
                null
            },
            action = if (reason == SmsSetupReadinessReason.PERMISSION_MISSING) {
                AiDoctorAction.OPEN_APP_SETTINGS
            } else {
                AiDoctorAction.NONE
            },
            group = group,
        )
    }

    private fun WhatsAppSetupReadiness.toReadinessCheck(): ReadinessCheck {
        val requiresFix = status == ReadinessStatus.ACTION_REQUIRED
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_whatsapp),
            detail = when (reason) {
                WhatsAppSetupReadinessReason.DISABLED -> text(R.string.automation_setup_whatsapp_disabled)
                WhatsAppSetupReadinessReason.NOT_USED -> text(R.string.automation_setup_whatsapp_not_used)
                WhatsAppSetupReadinessReason.CONSENT_REQUIRED -> text(
                    R.string.automation_setup_whatsapp_consent_needed_for_contacts,
                    selectedContactCount,
                )
                WhatsAppSetupReadinessReason.APP_MISSING -> text(
                    R.string.automation_setup_whatsapp_app_missing_for_contacts,
                    selectedContactCount,
                )
                WhatsAppSetupReadinessReason.ACCESSIBILITY_MISSING -> text(
                    R.string.automation_setup_whatsapp_accessibility_missing_for_contacts,
                    selectedContactCount,
                )
                WhatsAppSetupReadinessReason.READY -> text(
                    R.string.automation_setup_whatsapp_ok_for_contacts,
                    selectedContactCount,
                )
            },
            status = status,
            actionLabel = if (requiresFix) {
                text(R.string.automation_setup_action_open_accessibility)
            } else {
                null
            },
            action = if (requiresFix) {
                AiDoctorAction.OPEN_ACCESSIBILITY_SETTINGS
            } else {
                AiDoctorAction.NONE
            },
            group = group,
        )
    }

    private fun personalizationCheck(contacts: List<ContactAutomationReadinessProfile>): ReadinessCheck {
        return SetupQualityReadinessPolicy.evaluatePersonalization(contacts).toReadinessCheck()
    }

    private fun genericMessagesCheck(contacts: List<ContactAutomationReadinessProfile>): ReadinessCheck {
        return SetupQualityReadinessPolicy.evaluateGenericMessages(contacts).toReadinessCheck()
    }

    private fun List<ReadinessCheck>.toSummary(): AiDoctorSummary {
        val decision = SetupReadinessSummaryPolicy.summarize(map { it.status })
        val firstProblem = decision.firstProblemIndex?.let(::get)

        return when (decision.status) {
            ReadinessStatus.ACTION_REQUIRED -> AiDoctorSummary(
                title = text(R.string.automation_setup_summary_blockers, decision.blockerCount),
                detail = firstProblem?.let {
                    text(R.string.automation_setup_summary_start_with, it.title, it.detail)
                } ?: text(R.string.automation_setup_summary_required),
                status = ReadinessStatus.ACTION_REQUIRED,
            )
            ReadinessStatus.WARNING -> AiDoctorSummary(
                title = text(R.string.automation_setup_summary_warnings),
                detail = firstProblem?.let {
                    text(R.string.automation_setup_summary_problem, it.title, it.detail)
                } ?: text(R.string.automation_setup_summary_review_warnings),
                status = ReadinessStatus.WARNING,
            )
            ReadinessStatus.OK -> AiDoctorSummary(
                title = text(R.string.automation_setup_summary_ok),
                detail = text(R.string.automation_setup_summary_ok_detail),
                status = ReadinessStatus.OK,
            )
        }
    }

    private fun List<ReadinessCheck>.toRecommendedFix(): AiDoctorRecommendedFix? {
        val index = SetupReadinessRecommendationPolicy.selectRecommendedIndex(
            toRecommendationCandidates(),
        ) ?: return null
        val check = this[index]
        return AiDoctorRecommendedFix(
            title = check.title,
            detail = check.detail,
            actionLabel = check.actionLabel.orEmpty(),
            action = check.action,
            status = check.status,
            group = check.group,
            actionReadiness = check.actionReadiness,
        )
    }

    private fun List<ReadinessCheck>.toSetupActionReadiness(): RelationshipActionReadiness {
        return RelationshipActionReadinessPolicy.fromSetupCandidates(toRecommendationCandidates())
    }

    private fun List<ReadinessCheck>.toRecommendationCandidates(): List<SetupReadinessRecommendationCandidate> {
        return map { check ->
            SetupReadinessRecommendationCandidate(
                status = check.status,
                group = check.group,
                hasAction = check.action != AiDoctorAction.NONE && !check.actionLabel.isNullOrBlank(),
            )
        }
    }

    private fun countJsonArrayItems(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return try {
            JSONArray(raw).length()
        } catch (_: Exception) {
            0
        }
    }

    private fun diagnoseAiFailure(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("429") || lower.contains("quota") || lower.contains("exhausted") ->
                text(R.string.automation_setup_ai_error_quota)
            lower.contains("api key") || lower.contains("apikey") || lower.contains("permission") || lower.contains("403") || lower.contains("unauthenticated") ->
                text(R.string.automation_setup_ai_error_auth)
            lower.contains("network") || lower.contains("timeout") || lower.contains("unavailable") || lower.contains("unable to resolve") ->
                text(R.string.automation_setup_ai_error_network)
            lower.contains("json") || lower.contains("parse") || lower.contains("empty response") ->
                text(R.string.automation_setup_ai_error_json)
            lower.contains("circuit breaker") ->
                text(R.string.automation_setup_ai_error_circuit)
            else -> text(
                R.string.automation_setup_ai_error_recent,
                SensitiveLogRedactor.redact(raw).take(160),
            )
        }
    }

    internal fun diagnoseAiFailureForTesting(raw: String): String = diagnoseAiFailure(raw)

    internal fun summarizeForTesting(checks: List<ReadinessCheck>): AiDoctorSummary = checks.toSummary()

    internal fun setupProgressForTesting(checks: List<ReadinessCheck>): SetupProgressSummary =
        checks.toSetupProgressSummary()

    internal fun recommendedFixForTesting(checks: List<ReadinessCheck>): AiDoctorRecommendedFix? =
        checks.toRecommendedFix()

    internal suspend fun buildChecksForTesting(): List<ReadinessCheck> = buildReport().checks

    internal suspend fun buildSetupActionReadinessForTesting(): RelationshipActionReadiness =
        buildReport().setupActionReadiness

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return appContext.getString(resId, *args)
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun isWhatsAppAutomationServiceEnabled(): Boolean {
        val expectedService = "${appContext.packageName}/com.example.core.accessibility.WhatsAppAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabledServices.split(':').any {
            it.equals(expectedService, ignoreCase = true)
        }
    }

    private fun isWhatsAppInstalled(): Boolean {
        return isPackageInstalled("com.whatsapp") || isPackageInstalled("com.whatsapp.w4b")
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            appContext.packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }

    private fun currentFirebaseUserOrNull() = runCatching {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    }.getOrNull()

    private fun hasGoogleContactsAccess(): Boolean {
        if (preferencesRepository.getGoogleOAuthToken().isNotBlank()) return true
        val account = runCatching { GoogleSignIn.getLastSignedInAccount(appContext) }.getOrNull()
            ?: return false
        return runCatching { GoogleSignIn.hasPermissions(account, GOOGLE_CONTACTS_SCOPE) }
            .getOrDefault(false)
    }

    private fun String.toChannelSet(): Set<MessageChannel> {
        return CHANNEL_TOKEN_PATTERN.findAll(this)
            .map { MessageChannel.fromRaw(it.groupValues[1]) }
            .filter { it != MessageChannel.UNKNOWN }
            .toSet()
    }

    private fun Set<MessageChannel>.toChannelLabelList(): String {
        return sortedBy { DEFAULT_ROUTE_ORDER.indexOf(it).takeIf { index -> index >= 0 } ?: DEFAULT_ROUTE_ORDER.size }
            .joinToString(", ") { it.label() }
    }

    private fun MessageChannel.label(): String {
        return when (this) {
            MessageChannel.SMS -> text(R.string.channel_sms)
            MessageChannel.WHATSAPP -> text(R.string.channel_whatsapp)
            MessageChannel.EMAIL -> text(R.string.channel_email)
            MessageChannel.UNKNOWN -> MessageChannel.UNKNOWN.raw
        }
    }

    private fun ApprovalMode.label(): String {
        return when (this) {
            ApprovalMode.FULLY_AUTO -> text(R.string.automation_mode_fully_auto)
            ApprovalMode.SMART_APPROVE -> text(R.string.automation_mode_smart_approve_default)
            ApprovalMode.VIP_APPROVE -> text(R.string.automation_mode_vip_approve)
            ApprovalMode.ALWAYS_ASK -> text(R.string.automation_mode_always_ask)
            ApprovalMode.DEFAULT,
            ApprovalMode.UNKNOWN -> text(R.string.automation_mode_default)
        }
    }

    private val DEFAULT_ROUTE_ORDER = listOf(
        MessageChannel.SMS,
        MessageChannel.WHATSAPP,
        MessageChannel.EMAIL,
    )
}
