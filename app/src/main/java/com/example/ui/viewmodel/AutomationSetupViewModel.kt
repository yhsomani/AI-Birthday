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
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.CircuitState
import com.example.core.resilience.HealthSnapshot
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.LogEntry
import com.example.core.resilience.SensitiveLogRedactor
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.DiagnosticSnapshotId
import com.example.domain.model.contact.ContactAutomationReadinessProfile
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.DiagnosticSnapshotRepository
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.usecase.SyncContactsUseCase
import com.example.domain.usecase.TestSendUseCase
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

enum class ReadinessStatus { OK, WARNING, ACTION_REQUIRED }

enum class ReadinessGroup {
    REQUIRED,
    QUALITY,
    RELIABILITY,
    RECOVERY,
}

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
)

data class AiDoctorRecommendedFix(
    val title: String,
    val detail: String,
    val actionLabel: String,
    val action: AiDoctorAction,
    val status: ReadinessStatus,
    val group: ReadinessGroup,
)

data class AutomationSetupUiState(
    val checks: List<ReadinessCheck> = emptyList(),
    val summary: AiDoctorSummary = AiDoctorSummary(),
    val recommendedFix: AiDoctorRecommendedFix? = null,
    val setupProgress: SetupProgressSummary = SetupProgressSummary(),
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
)

private data class DispatchRecoverySnapshot(
    val persistedRecoveryCount: Int,
    val persistedDeadLetterCount: Int,
    val latestPersistedAttempt: DispatchAttempt?,
)

private data class AiDoctorLiveInputs(
    val contacts: List<ContactAutomationReadinessProfile>,
    val styleProfile: StyleProfileEntity?,
    val persistedRecoveryCount: Int,
    val persistedDeadLetterCount: Int,
)

@HiltViewModel
class AutomationSetupViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val securePrefs: SecurePrefs,
    private val syncContactsUseCase: SyncContactsUseCase,
    private val geminiClient: GeminiClient,
    private val contactRepository: ContactRepository,
    private val styleProfileRepository: StyleProfileRepository,
    private val testSendUseCase: TestSendUseCase,
    private val dispatchAttemptRepository: DispatchAttemptRepository,
    private val diagnosticSnapshotRepository: DiagnosticSnapshotRepository,
) : ViewModel() {
    private companion object {
        const val PERSONALIZATION_CONFIDENCE_THRESHOLD = 0.6
        const val PERSISTED_HEALTH_SNAPSHOT_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val CHANNEL_VERIFICATION_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        val CHANNEL_TOKEN_PATTERN = Regex("\"([A-Za-z_]+)\"")
    }

    private val _uiState = MutableStateFlow(AutomationSetupUiState())
    val uiState: StateFlow<AutomationSetupUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            whatsAppAutomationConsentGranted = securePrefs.isWhatsAppAutomationConsentGranted()
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
                securePrefs.observeChanges().onStart { emit(Unit) },
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
                        whatsAppAutomationConsentGranted = securePrefs.isWhatsAppAutomationConsentGranted(),
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
        val ready = securePrefs.isAiWishGenerationEnabled() &&
            (securePrefs.getGeminiApiKey().isNotBlank() || currentFirebaseUserOrNull() != null)
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
        securePrefs.setWhatsAppAutomationConsentGranted(granted)
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
        val hasGoogleAuth = securePrefs.getGoogleOAuthToken().isNotBlank() || currentUser != null
        val hasGeminiAccess = securePrefs.getGeminiApiKey().isNotBlank() || currentUser != null
        val globalAutomationMode = securePrefs.getGlobalApprovalMode()
        val aiEnabled = securePrefs.isAiWishGenerationEnabled()
        val notificationsAllowed = runCatching { hasNotificationPermission() }.getOrDefault(false)
        val smsAllowed = runCatching { hasSmsPermission() }.getOrDefault(false)
        val whatsAppConsentGranted = securePrefs.isWhatsAppAutomationConsentGranted()
        val whatsAppAutomationEnabled = runCatching { isWhatsAppAutomationServiceEnabled() }.getOrDefault(false)
        val exactSendsAllowed = runCatching {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        }.getOrDefault(false)
        val dispatchRecovery = loadDispatchRecoverySnapshot(
            persistedRecoveryCount = inputs?.persistedRecoveryCount,
            persistedDeadLetterCount = inputs?.persistedDeadLetterCount,
        )
        val persistedHealthSnapshot = loadRecentPersistedHealthSnapshot()
        val senderEmail = securePrefs.getSenderEmail().trim()
        val senderEmailReady = senderEmail.isNotBlank() &&
            securePrefs.getSenderEmailPassword().isNotBlank()
        val blockedChannels = securePrefs.getChannelBlackout().toChannelSet()
        val selectedChannelCounts = selectedAutomaticChannelCounts(
            contacts = contacts,
            senderEmailReady = senderEmailReady,
            blockedChannels = blockedChannels,
        )
        val selectedChannels = selectedChannelCounts.filterValues { it > 0 }.keys
        val channelVerificationSinceMs = System.currentTimeMillis() - CHANNEL_VERIFICATION_WINDOW_MS
        val emailSelfTestVerified = senderEmailReady &&
            securePrefs.getLastSuccessfulEmailTestMs() >= channelVerificationSinceMs &&
            securePrefs.getLastSuccessfulEmailTestSender().equals(senderEmail, ignoreCase = true)
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
            ReadinessCheck(
                text(R.string.automation_setup_check_google_contacts),
                if (hasGoogleAuth) {
                    text(R.string.automation_setup_google_contacts_ok)
                } else {
                    text(R.string.automation_setup_google_contacts_missing)
                },
                if (hasGoogleAuth) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (hasGoogleAuth) null else text(R.string.automation_setup_action_sync_contacts),
                action = if (hasGoogleAuth) AiDoctorAction.NONE else AiDoctorAction.SYNC_CONTACTS,
                group = ReadinessGroup.REQUIRED,
            ),
            ReadinessCheck(
                text(R.string.automation_setup_check_gemini),
                if (securePrefs.getGeminiApiKey().isNotBlank()) {
                    text(R.string.automation_setup_gemini_key_ok)
                } else {
                    text(R.string.automation_setup_gemini_auth_missing)
                },
                if (hasGeminiAccess) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (hasGeminiAccess) text(R.string.automation_setup_action_test_ai) else text(R.string.automation_setup_action_open_settings),
                action = if (hasGeminiAccess) AiDoctorAction.TEST_AI else AiDoctorAction.OPEN_SETTINGS,
                group = ReadinessGroup.REQUIRED,
            ),
            ReadinessCheck(
                text(R.string.automation_setup_check_ai_wish_generation),
                if (aiEnabled) text(R.string.automation_setup_ai_wish_enabled) else text(R.string.automation_setup_ai_wish_disabled),
                if (aiEnabled) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (aiEnabled) null else text(R.string.automation_setup_action_open_settings),
                action = if (aiEnabled) AiDoctorAction.NONE else AiDoctorAction.OPEN_SETTINGS,
                group = ReadinessGroup.REQUIRED,
            ),
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
            ReadinessCheck(
                text(R.string.automation_setup_check_style_coach),
                when {
                    styleSampleCount >= 3 -> text(R.string.automation_setup_style_trained, styleSampleCount)
                    styleSampleCount > 0 -> text(R.string.automation_setup_style_needs_more, 3 - styleSampleCount)
                    else -> text(R.string.automation_setup_style_empty)
                },
                when {
                    styleSampleCount >= 3 -> ReadinessStatus.OK
                    styleSampleCount > 0 -> ReadinessStatus.WARNING
                    else -> ReadinessStatus.ACTION_REQUIRED
                },
                actionLabel = if (styleSampleCount >= 3) null else text(R.string.automation_setup_action_open_style_coach),
                action = if (styleSampleCount >= 3) AiDoctorAction.NONE else AiDoctorAction.OPEN_STYLE_COACH,
                group = ReadinessGroup.QUALITY,
            ),
            personalizationCheck(contacts),
            genericMessagesCheck(contacts),
            ReadinessCheck(
                text(R.string.automation_setup_check_gemini_circuit),
                health.circuitBreakerStates["gemini"]?.let { state ->
                    if (state == CircuitState.CLOSED) {
                        text(R.string.automation_setup_gemini_circuit_ok)
                    } else {
                        text(R.string.automation_setup_gemini_circuit_state, state.name)
                    }
                } ?: text(R.string.automation_setup_gemini_circuit_none),
                when (health.circuitBreakerStates["gemini"]) {
                    null, CircuitState.CLOSED -> ReadinessStatus.OK
                    CircuitState.HALF_OPEN -> ReadinessStatus.WARNING
                    CircuitState.OPEN -> ReadinessStatus.ACTION_REQUIRED
                },
                actionLabel = if (health.circuitBreakerStates["gemini"] == CircuitState.OPEN) text(R.string.automation_setup_action_test_ai) else null,
                action = if (health.circuitBreakerStates["gemini"] == CircuitState.OPEN) AiDoctorAction.TEST_AI else AiDoctorAction.NONE,
                group = ReadinessGroup.RELIABILITY,
            ),
            ReadinessCheck(
                text(R.string.automation_setup_check_notifications),
                if (notificationsAllowed) text(R.string.automation_setup_notifications_ok) else text(R.string.automation_setup_notifications_missing),
                if (notificationsAllowed) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (notificationsAllowed) null else text(R.string.automation_setup_action_app_settings),
                action = if (notificationsAllowed) AiDoctorAction.NONE else AiDoctorAction.OPEN_APP_SETTINGS,
                group = ReadinessGroup.REQUIRED,
            ),
            smsReadinessCheck(
                smsAllowed = smsAllowed,
                selectedSmsContactCount = selectedChannelCounts[MessageChannel.SMS] ?: 0,
                smsDisabled = MessageChannel.SMS in blockedChannels,
            ),
            ReadinessCheck(
                text(R.string.automation_setup_check_email),
                when {
                    senderEmailReady -> text(R.string.automation_setup_email_ok)
                    emailPreferredContacts > 0 -> text(
                        R.string.automation_setup_email_missing_for_contacts,
                        emailPreferredContacts,
                    )
                    else -> text(R.string.automation_setup_email_optional)
                },
                when {
                    senderEmailReady -> ReadinessStatus.OK
                    emailPreferredContacts > 0 -> ReadinessStatus.ACTION_REQUIRED
                    else -> ReadinessStatus.WARNING
                },
                actionLabel = if (senderEmailReady) text(R.string.automation_setup_action_test_email) else text(R.string.automation_setup_action_open_settings),
                action = if (senderEmailReady) AiDoctorAction.TEST_EMAIL else AiDoctorAction.OPEN_SETTINGS,
                group = ReadinessGroup.REQUIRED,
            ),
            whatsAppReadinessCheck(
                consentGranted = whatsAppConsentGranted,
                accessibilityEnabled = whatsAppAutomationEnabled,
                whatsAppInstalled = whatsAppInstalled,
                selectedWhatsAppContactCount = selectedChannelCounts[MessageChannel.WHATSAPP] ?: 0,
                whatsAppDisabled = MessageChannel.WHATSAPP in blockedChannels,
            ),
            ReadinessCheck(
                text(R.string.automation_setup_check_exact_sends),
                if (exactSendsAllowed) text(R.string.automation_setup_exact_sends_ok) else text(R.string.automation_setup_exact_sends_missing),
                if (exactSendsAllowed) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (exactSendsAllowed) null else text(R.string.automation_setup_action_app_settings),
                action = if (exactSendsAllowed) AiDoctorAction.NONE else AiDoctorAction.OPEN_APP_SETTINGS,
                group = ReadinessGroup.RELIABILITY,
            ),
            ReadinessCheck(
                text(R.string.automation_setup_check_daily_automation),
                if (dailyScheduled) text(R.string.automation_setup_daily_ok) else text(R.string.automation_setup_daily_missing),
                if (dailyScheduled) ReadinessStatus.OK else ReadinessStatus.WARNING,
                actionLabel = if (dailyScheduled) null else text(R.string.automation_setup_action_refresh),
                action = if (dailyScheduled) AiDoctorAction.NONE else AiDoctorAction.REFRESH,
                group = ReadinessGroup.RELIABILITY,
            ),
            ReadinessCheck(
                text(R.string.automation_setup_check_recent_errors),
                if (!hasRecentHealthEvidence) {
                    text(R.string.automation_setup_recent_errors_none)
                } else {
                    recentErrors.toRecentErrorDetail(health, persistedHealthSnapshot)
                },
                if (hasRecentHealthEvidence) ReadinessStatus.WARNING else ReadinessStatus.OK,
                actionLabel = if (hasRecentHealthEvidence) text(R.string.automation_setup_action_view_activity) else null,
                action = if (hasRecentHealthEvidence) AiDoctorAction.OPEN_ACTIVITY_HISTORY else AiDoctorAction.NONE,
                group = ReadinessGroup.RECOVERY,
            ),
            ReadinessCheck(
                text(R.string.automation_setup_check_dead_letter),
                dispatchRecovery.toReadinessDetail(),
                if (dispatchRecovery.totalRecoveryCount == 0) ReadinessStatus.OK else ReadinessStatus.WARNING,
                actionLabel = if (dispatchRecovery.totalRecoveryCount == 0) null else text(R.string.automation_setup_action_view_activity),
                action = if (dispatchRecovery.totalRecoveryCount == 0) AiDoctorAction.NONE else AiDoctorAction.OPEN_ACTIVITY_HISTORY,
                group = ReadinessGroup.RECOVERY,
            ),
        )
        return AiDoctorReport(
            summary = checks.toSummary(),
            checks = checks,
            recommendedFix = checks.toRecommendedFix(),
            setupProgress = checks.toSetupProgressSummary(),
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

    private fun fullAutomationModeCheck(
        globalAutomationMode: ApprovalMode,
        contacts: List<ContactAutomationReadinessProfile>,
    ): ReadinessCheck {
        val reviewFirstOverrideCount = contacts.count { it.hasReviewFirstAutomationOverride }
        return when {
            globalAutomationMode != ApprovalMode.FULLY_AUTO -> ReadinessCheck(
                title = text(R.string.automation_setup_check_full_automation),
                detail = text(
                    R.string.automation_setup_full_automation_disabled,
                    globalAutomationMode.label(),
                ),
                status = ReadinessStatus.ACTION_REQUIRED,
                actionLabel = text(R.string.automation_setup_action_open_settings),
                action = AiDoctorAction.OPEN_SETTINGS,
                group = ReadinessGroup.REQUIRED,
            )
            reviewFirstOverrideCount > 0 -> ReadinessCheck(
                title = text(R.string.automation_setup_check_full_automation),
                detail = text(
                    R.string.automation_setup_full_automation_contact_overrides,
                    reviewFirstOverrideCount,
                ),
                status = ReadinessStatus.WARNING,
                actionLabel = text(R.string.automation_setup_action_review_contacts),
                action = AiDoctorAction.OPEN_CONTACTS,
                group = ReadinessGroup.REQUIRED,
            )
            else -> ReadinessCheck(
                title = text(R.string.automation_setup_check_full_automation),
                detail = text(R.string.automation_setup_full_automation_ok),
                status = ReadinessStatus.OK,
                group = ReadinessGroup.REQUIRED,
            )
        }
    }

    private fun automatableEventsCheck(contacts: List<ContactAutomationReadinessProfile>): ReadinessCheck {
        if (contacts.isEmpty()) {
            return ReadinessCheck(
                title = text(R.string.automation_setup_check_automatable_events),
                detail = text(R.string.automation_setup_automatable_events_empty),
                status = ReadinessStatus.WARNING,
                actionLabel = text(R.string.automation_setup_action_sync_contacts),
                action = AiDoctorAction.SYNC_CONTACTS,
                group = ReadinessGroup.REQUIRED,
            )
        }

        val eventReadyCount = contacts.count { it.hasAutomatableOccasion }
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_automatable_events),
            detail = if (eventReadyCount == contacts.size) {
                text(R.string.automation_setup_automatable_events_ok, eventReadyCount)
            } else {
                text(
                    R.string.automation_setup_automatable_events_missing,
                    contacts.size - eventReadyCount,
                    contacts.size,
                )
            },
            status = when {
                eventReadyCount == contacts.size -> ReadinessStatus.OK
                eventReadyCount == 0 -> ReadinessStatus.ACTION_REQUIRED
                else -> ReadinessStatus.WARNING
            },
            actionLabel = if (eventReadyCount == contacts.size) null else text(R.string.automation_setup_action_review_contacts),
            action = if (eventReadyCount == contacts.size) AiDoctorAction.NONE else AiDoctorAction.OPEN_CONTACTS,
            group = ReadinessGroup.REQUIRED,
        )
    }

    private fun automaticDeliveryRoutesCheck(
        contacts: List<ContactAutomationReadinessProfile>,
        senderEmailReady: Boolean,
        blockedChannels: Set<MessageChannel>,
    ): ReadinessCheck {
        val eventContacts = contacts.filter { it.hasAutomatableOccasion }
        if (eventContacts.isEmpty()) {
            return ReadinessCheck(
                title = text(R.string.automation_setup_check_delivery_routes),
                detail = text(R.string.automation_setup_delivery_routes_no_events),
                status = ReadinessStatus.WARNING,
                actionLabel = text(R.string.automation_setup_action_review_contacts),
                action = AiDoctorAction.OPEN_CONTACTS,
                group = ReadinessGroup.REQUIRED,
            )
        }

        val routableCount = eventContacts.count {
            it.hasAutomaticDeliveryRoute(
                senderEmailReady = senderEmailReady,
                blockedChannels = blockedChannels,
            )
        }
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_delivery_routes),
            detail = if (routableCount == eventContacts.size) {
                text(R.string.automation_setup_delivery_routes_ok, routableCount)
            } else {
                text(
                    R.string.automation_setup_delivery_routes_missing,
                    eventContacts.size - routableCount,
                    eventContacts.size,
                )
            },
            status = if (routableCount == eventContacts.size) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
            actionLabel = if (routableCount == eventContacts.size) null else text(R.string.automation_setup_action_review_contacts),
            action = if (routableCount == eventContacts.size) AiDoctorAction.NONE else AiDoctorAction.OPEN_CONTACTS,
            group = ReadinessGroup.REQUIRED,
        )
    }

    private fun channelVerificationCheck(
        selectedChannels: Set<MessageChannel>,
        successfulChannels: Set<MessageChannel>,
    ): ReadinessCheck {
        if (selectedChannels.isEmpty()) {
            return ReadinessCheck(
                title = text(R.string.automation_setup_check_channel_verification),
                detail = text(R.string.automation_setup_channel_verification_no_routes),
                status = ReadinessStatus.WARNING,
                actionLabel = text(R.string.automation_setup_action_review_contacts),
                action = AiDoctorAction.OPEN_CONTACTS,
                group = ReadinessGroup.RELIABILITY,
            )
        }

        val unverifiedChannels = selectedChannels - successfulChannels
        return if (unverifiedChannels.isEmpty()) {
            ReadinessCheck(
                title = text(R.string.automation_setup_check_channel_verification),
                detail = text(
                    R.string.automation_setup_channel_verification_ok,
                    selectedChannels.toChannelLabelList(),
                ),
                status = ReadinessStatus.OK,
                group = ReadinessGroup.RELIABILITY,
            )
        } else {
            val canFixWithEmailTest = unverifiedChannels == setOf(MessageChannel.EMAIL)
            val realMessageProofChannel = when {
                MessageChannel.SMS in unverifiedChannels -> MessageChannel.SMS
                MessageChannel.WHATSAPP in unverifiedChannels -> MessageChannel.WHATSAPP
                else -> null
            }
            ReadinessCheck(
                title = text(R.string.automation_setup_check_channel_verification),
                detail = text(
                    R.string.automation_setup_channel_verification_missing,
                    unverifiedChannels.toChannelLabelList(),
                ),
                status = ReadinessStatus.WARNING,
                actionLabel = if (canFixWithEmailTest) {
                    text(R.string.automation_setup_action_test_email)
                } else if (realMessageProofChannel == MessageChannel.SMS) {
                    text(R.string.automation_setup_action_review_sms_messages)
                } else if (realMessageProofChannel == MessageChannel.WHATSAPP) {
                    text(R.string.automation_setup_action_review_whatsapp_messages)
                } else {
                    text(R.string.automation_setup_action_view_activity)
                },
                action = if (canFixWithEmailTest) {
                    AiDoctorAction.TEST_EMAIL
                } else if (realMessageProofChannel == MessageChannel.SMS) {
                    AiDoctorAction.OPEN_SMS_MESSAGES
                } else if (realMessageProofChannel == MessageChannel.WHATSAPP) {
                    AiDoctorAction.OPEN_WHATSAPP_MESSAGES
                } else {
                    AiDoctorAction.OPEN_ACTIVITY_HISTORY
                },
                group = ReadinessGroup.RELIABILITY,
            )
        }
    }

    private fun smsReadinessCheck(
        smsAllowed: Boolean,
        selectedSmsContactCount: Int,
        smsDisabled: Boolean,
    ): ReadinessCheck {
        return when {
            smsDisabled -> ReadinessCheck(
                title = text(R.string.automation_setup_check_sms),
                detail = text(R.string.automation_setup_sms_disabled),
                status = ReadinessStatus.OK,
                group = ReadinessGroup.REQUIRED,
            )
            selectedSmsContactCount == 0 -> ReadinessCheck(
                title = text(R.string.automation_setup_check_sms),
                detail = text(R.string.automation_setup_sms_not_used),
                status = ReadinessStatus.OK,
                group = ReadinessGroup.REQUIRED,
            )
            smsAllowed -> ReadinessCheck(
                title = text(R.string.automation_setup_check_sms),
                detail = text(R.string.automation_setup_sms_ok_for_contacts, selectedSmsContactCount),
                status = ReadinessStatus.OK,
                group = ReadinessGroup.REQUIRED,
            )
            else -> ReadinessCheck(
                title = text(R.string.automation_setup_check_sms),
                detail = text(R.string.automation_setup_sms_missing_for_contacts, selectedSmsContactCount),
                status = ReadinessStatus.ACTION_REQUIRED,
                actionLabel = text(R.string.automation_setup_action_app_settings),
                action = AiDoctorAction.OPEN_APP_SETTINGS,
                group = ReadinessGroup.REQUIRED,
            )
        }
    }

    private fun whatsAppReadinessCheck(
        consentGranted: Boolean,
        accessibilityEnabled: Boolean,
        whatsAppInstalled: Boolean,
        selectedWhatsAppContactCount: Int,
        whatsAppDisabled: Boolean,
    ): ReadinessCheck {
        return when {
            whatsAppDisabled -> ReadinessCheck(
                title = text(R.string.automation_setup_check_whatsapp),
                detail = text(R.string.automation_setup_whatsapp_disabled),
                status = ReadinessStatus.OK,
                group = ReadinessGroup.RELIABILITY,
            )
            selectedWhatsAppContactCount == 0 -> ReadinessCheck(
                title = text(R.string.automation_setup_check_whatsapp),
                detail = text(R.string.automation_setup_whatsapp_not_used),
                status = ReadinessStatus.OK,
                group = ReadinessGroup.RELIABILITY,
            )
            !consentGranted -> ReadinessCheck(
                title = text(R.string.automation_setup_check_whatsapp),
                detail = text(
                    R.string.automation_setup_whatsapp_consent_needed_for_contacts,
                    selectedWhatsAppContactCount,
                ),
                status = ReadinessStatus.ACTION_REQUIRED,
                actionLabel = text(R.string.automation_setup_action_open_accessibility),
                action = AiDoctorAction.OPEN_ACCESSIBILITY_SETTINGS,
                group = ReadinessGroup.RELIABILITY,
            )
            !whatsAppInstalled -> ReadinessCheck(
                title = text(R.string.automation_setup_check_whatsapp),
                detail = text(
                    R.string.automation_setup_whatsapp_app_missing_for_contacts,
                    selectedWhatsAppContactCount,
                ),
                status = ReadinessStatus.ACTION_REQUIRED,
                actionLabel = text(R.string.automation_setup_action_open_accessibility),
                action = AiDoctorAction.OPEN_ACCESSIBILITY_SETTINGS,
                group = ReadinessGroup.RELIABILITY,
            )
            !accessibilityEnabled -> ReadinessCheck(
                title = text(R.string.automation_setup_check_whatsapp),
                detail = text(
                    R.string.automation_setup_whatsapp_accessibility_missing_for_contacts,
                    selectedWhatsAppContactCount,
                ),
                status = ReadinessStatus.ACTION_REQUIRED,
                actionLabel = text(R.string.automation_setup_action_open_accessibility),
                action = AiDoctorAction.OPEN_ACCESSIBILITY_SETTINGS,
                group = ReadinessGroup.RELIABILITY,
            )
            else -> ReadinessCheck(
                title = text(R.string.automation_setup_check_whatsapp),
                detail = text(R.string.automation_setup_whatsapp_ok_for_contacts, selectedWhatsAppContactCount),
                status = ReadinessStatus.OK,
                group = ReadinessGroup.RELIABILITY,
            )
        }
    }

    private fun personalizationCheck(contacts: List<ContactAutomationReadinessProfile>): ReadinessCheck {
        if (contacts.isEmpty()) {
            return ReadinessCheck(
                title = text(R.string.automation_setup_check_personalization),
                detail = text(R.string.automation_setup_personalization_empty),
                status = ReadinessStatus.WARNING,
                actionLabel = text(R.string.automation_setup_action_sync_contacts),
                action = AiDoctorAction.SYNC_CONTACTS,
                group = ReadinessGroup.QUALITY,
            )
        }

        val enriched = contacts.count { it.hasPersonalizationData }
        val percentage = (enriched * 100) / contacts.size
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_personalization),
            detail = if (percentage >= 50) {
                text(R.string.automation_setup_personalization_ok, enriched, contacts.size)
            } else {
                text(R.string.automation_setup_personalization_low, enriched, contacts.size)
            },
            status = if (percentage >= 50) ReadinessStatus.OK else ReadinessStatus.WARNING,
            actionLabel = if (percentage >= 50) null else text(R.string.automation_setup_action_review_contacts),
            action = if (percentage >= 50) AiDoctorAction.NONE else AiDoctorAction.OPEN_CONTACTS,
            group = ReadinessGroup.QUALITY,
        )
    }

    private fun genericMessagesCheck(contacts: List<ContactAutomationReadinessProfile>): ReadinessCheck {
        if (contacts.isEmpty()) {
            return ReadinessCheck(
                title = text(R.string.automation_setup_check_generic_messages),
                detail = text(R.string.automation_setup_generic_messages_empty),
                status = ReadinessStatus.WARNING,
                actionLabel = text(R.string.automation_setup_action_sync_contacts),
                action = AiDoctorAction.SYNC_CONTACTS,
                group = ReadinessGroup.QUALITY,
            )
        }

        val genericRiskCount = contacts.count {
            !it.hasPersonalizationContextForAi(PERSONALIZATION_CONFIDENCE_THRESHOLD)
        }
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_generic_messages),
            detail = if (genericRiskCount == 0) {
                text(R.string.automation_setup_generic_messages_ok)
            } else {
                text(R.string.automation_setup_generic_messages_low, genericRiskCount, contacts.size)
            },
            status = if (genericRiskCount == 0) ReadinessStatus.OK else ReadinessStatus.WARNING,
            actionLabel = if (genericRiskCount == 0) null else text(R.string.automation_setup_action_review_contacts),
            action = if (genericRiskCount == 0) AiDoctorAction.NONE else AiDoctorAction.OPEN_CONTACTS,
            group = ReadinessGroup.QUALITY,
        )
    }

    private fun List<ReadinessCheck>.toSummary(): AiDoctorSummary {
        val blockers = count { it.status == ReadinessStatus.ACTION_REQUIRED }
        val warnings = count { it.status == ReadinessStatus.WARNING }
        val firstProblem = firstOrNull { it.status == ReadinessStatus.ACTION_REQUIRED }
            ?: firstOrNull { it.status == ReadinessStatus.WARNING }

        return when {
            blockers > 0 -> AiDoctorSummary(
                title = text(R.string.automation_setup_summary_blockers, blockers),
                detail = firstProblem?.let {
                    text(R.string.automation_setup_summary_start_with, it.title, it.detail)
                } ?: text(R.string.automation_setup_summary_required),
                status = ReadinessStatus.ACTION_REQUIRED,
            )
            warnings > 0 -> AiDoctorSummary(
                title = text(R.string.automation_setup_summary_warnings),
                detail = firstProblem?.let {
                    text(R.string.automation_setup_summary_problem, it.title, it.detail)
                } ?: text(R.string.automation_setup_summary_review_warnings),
                status = ReadinessStatus.WARNING,
            )
            else -> AiDoctorSummary(
                title = text(R.string.automation_setup_summary_ok),
                detail = text(R.string.automation_setup_summary_ok_detail),
                status = ReadinessStatus.OK,
            )
        }
    }

    private fun List<ReadinessCheck>.toRecommendedFix(): AiDoctorRecommendedFix? {
        return withIndex()
            .filter { (_, check) ->
                check.status != ReadinessStatus.OK &&
                    check.action != AiDoctorAction.NONE &&
                    !check.actionLabel.isNullOrBlank()
            }
            .minWithOrNull(
                compareBy<IndexedValue<ReadinessCheck>> { it.value.status.recommendedFixRank() }
                    .thenBy { it.value.group.recommendedFixRank() }
                    .thenBy { it.index },
            )
            ?.value
            ?.let { check ->
                AiDoctorRecommendedFix(
                    title = check.title,
                    detail = check.detail,
                    actionLabel = check.actionLabel.orEmpty(),
                    action = check.action,
                    status = check.status,
                    group = check.group,
                )
            }
    }

    private fun ReadinessStatus.recommendedFixRank(): Int = when (this) {
        ReadinessStatus.ACTION_REQUIRED -> 0
        ReadinessStatus.WARNING -> 1
        ReadinessStatus.OK -> 2
    }

    private fun ReadinessGroup.recommendedFixRank(): Int = when (this) {
        ReadinessGroup.REQUIRED -> 0
        ReadinessGroup.RELIABILITY -> 1
        ReadinessGroup.QUALITY -> 2
        ReadinessGroup.RECOVERY -> 3
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

    private fun selectedAutomaticChannelCounts(
        contacts: List<ContactAutomationReadinessProfile>,
        senderEmailReady: Boolean,
        blockedChannels: Set<MessageChannel>,
    ): Map<MessageChannel, Int> {
        return contacts.asSequence()
            .filter { it.hasAutomatableOccasion }
            .mapNotNull {
                it.selectedAutomaticChannel(
                    senderEmailReady = senderEmailReady,
                    blockedChannels = blockedChannels,
                )
            }
            .groupingBy { it }
            .eachCount()
    }

    private fun ContactAutomationReadinessProfile.selectedAutomaticChannel(
        senderEmailReady: Boolean,
        blockedChannels: Set<MessageChannel>,
    ): MessageChannel? {
        val availableChannels = DEFAULT_ROUTE_ORDER
            .filterNot { it in blockedChannels }
            .filter { channel ->
                when (channel) {
                    MessageChannel.SMS,
                    MessageChannel.WHATSAPP -> hasPrimaryPhone
                    MessageChannel.EMAIL -> hasPrimaryEmail && senderEmailReady
                    MessageChannel.UNKNOWN -> false
                }
            }
        if (availableChannels.isEmpty()) return null
        return preferredChannel.takeIf { it in availableChannels }
            ?: availableChannels.first()
    }

    private fun ContactAutomationReadinessProfile.hasAutomaticDeliveryRoute(
        senderEmailReady: Boolean,
        blockedChannels: Set<MessageChannel>,
    ): Boolean {
        return DEFAULT_ROUTE_ORDER.any { channel ->
            channel !in blockedChannels && when (channel) {
                MessageChannel.SMS,
                MessageChannel.WHATSAPP -> hasPrimaryPhone
                MessageChannel.EMAIL -> hasPrimaryEmail && senderEmailReady
                MessageChannel.UNKNOWN -> false
            }
        }
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
