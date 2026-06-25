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
import com.example.core.db.entities.ContactEntity
import com.example.core.gemini.GeminiClient
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.CircuitState
import com.example.core.resilience.DeadLetterQueue
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.SensitiveLogRedactor
import com.example.core.resilience.StructuredLogger
import com.example.domain.repository.ContactRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

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

data class AutomationSetupUiState(
    val checks: List<ReadinessCheck> = emptyList(),
    val summary: AiDoctorSummary = AiDoctorSummary(),
    val setupProgress: SetupProgressSummary = SetupProgressSummary(),
    val isRefreshing: Boolean = false,
    val isSyncingContacts: Boolean = false,
    val isTestingAi: Boolean = false,
    val isTestingEmail: Boolean = false,
    val operationMessage: String? = null,
)

private data class AiDoctorReport(
    val summary: AiDoctorSummary,
    val checks: List<ReadinessCheck>,
    val setupProgress: SetupProgressSummary,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationSetupUiState())
    val uiState: StateFlow<AutomationSetupUiState> = _uiState.asStateFlow()

    init {
        refreshChecks()
    }

    fun refreshChecks() {
        refreshChecks(clearOperationMessage = true)
    }

    private fun refreshChecks(clearOperationMessage: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRefreshing = true,
                operationMessage = if (clearOperationMessage) null else _uiState.value.operationMessage,
            )
            val report = withContext(Dispatchers.IO) { buildReport() }
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                checks = report.checks,
                summary = report.summary,
                setupProgress = report.setupProgress,
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
        val firstBlocker = _uiState.value.checks.firstOrNull { it.status == ReadinessStatus.ACTION_REQUIRED }
        _uiState.value = _uiState.value.copy(
            operationMessage = if (ready) {
                firstBlocker?.let {
                    text(R.string.automation_setup_dry_run_blocker, it.title, it.detail)
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

    private suspend fun buildReport(): AiDoctorReport {
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
        val contacts = runCatching { contactRepository.getAllSync() }.getOrDefault(emptyList())
        val styleProfile = runCatching { styleProfileRepository.getProfileOnce() }.getOrNull()
        val styleSampleCount = maxOf(
            styleProfile?.sampleCount ?: 0,
            countJsonArrayItems(styleProfile?.sampleMessagesJson),
        )
        val hasGoogleAuth = securePrefs.getGoogleOAuthToken().isNotBlank() || currentUser != null
        val hasGeminiAccess = securePrefs.getGeminiApiKey().isNotBlank() || currentUser != null
        val aiEnabled = securePrefs.isAiWishGenerationEnabled()
        val notificationsAllowed = runCatching { hasNotificationPermission() }.getOrDefault(false)
        val smsAllowed = runCatching { hasSmsPermission() }.getOrDefault(false)
        val whatsAppAutomationEnabled = runCatching { isWhatsAppAutomationServiceEnabled() }.getOrDefault(false)
        val exactSendsAllowed = runCatching {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        }.getOrDefault(false)
        val deadLetterCount = DeadLetterQueue.count()
        val senderEmailReady = securePrefs.getSenderEmail().isNotBlank() &&
            securePrefs.getSenderEmailPassword().isNotBlank()
        val emailPreferredContacts = contacts.count {
            it.preferredChannel.equals("EMAIL", ignoreCase = true)
        }

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
            ReadinessCheck(
                text(R.string.automation_setup_check_sms),
                if (smsAllowed) text(R.string.automation_setup_sms_ok) else text(R.string.automation_setup_sms_missing),
                if (smsAllowed) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (smsAllowed) null else text(R.string.automation_setup_action_app_settings),
                action = if (smsAllowed) AiDoctorAction.NONE else AiDoctorAction.OPEN_APP_SETTINGS,
                group = ReadinessGroup.REQUIRED,
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
            ReadinessCheck(
                text(R.string.automation_setup_check_whatsapp),
                if (whatsAppAutomationEnabled) text(R.string.automation_setup_whatsapp_ok) else text(R.string.automation_setup_whatsapp_missing),
                if (whatsAppAutomationEnabled) ReadinessStatus.OK else ReadinessStatus.WARNING,
                actionLabel = if (whatsAppAutomationEnabled) null else text(R.string.automation_setup_action_open_accessibility),
                action = if (whatsAppAutomationEnabled) AiDoctorAction.NONE else AiDoctorAction.OPEN_ACCESSIBILITY_SETTINGS,
                group = ReadinessGroup.RELIABILITY,
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
                if (recentErrors.isEmpty() && health.recentErrors.isEmpty()) {
                    text(R.string.automation_setup_recent_errors_none)
                } else {
                    diagnoseAiFailure(recentErrors.lastOrNull()?.message ?: health.recentErrors.last())
                },
                if (recentErrors.isEmpty() && health.recentErrors.isEmpty()) ReadinessStatus.OK else ReadinessStatus.WARNING,
                actionLabel = if (recentErrors.isEmpty() && health.recentErrors.isEmpty()) null else text(R.string.automation_setup_action_view_activity),
                action = if (recentErrors.isEmpty() && health.recentErrors.isEmpty()) AiDoctorAction.NONE else AiDoctorAction.OPEN_ACTIVITY_HISTORY,
                group = ReadinessGroup.RECOVERY,
            ),
            ReadinessCheck(
                text(R.string.automation_setup_check_dead_letter),
                text(R.string.automation_setup_dead_letter_count, deadLetterCount),
                if (deadLetterCount == 0) ReadinessStatus.OK else ReadinessStatus.WARNING,
                actionLabel = if (deadLetterCount == 0) null else text(R.string.automation_setup_action_view_activity),
                action = if (deadLetterCount == 0) AiDoctorAction.NONE else AiDoctorAction.OPEN_ACTIVITY_HISTORY,
                group = ReadinessGroup.RECOVERY,
            ),
        )
        return AiDoctorReport(
            summary = checks.toSummary(),
            checks = checks,
            setupProgress = checks.toSetupProgressSummary(),
        )
    }

    private fun personalizationCheck(contacts: List<ContactEntity>): ReadinessCheck {
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

        val enriched = contacts.count { it.hasPersonalizationData() }
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

    private fun ContactEntity.hasPersonalizationData(): Boolean {
        return !nickname.isNullOrBlank() ||
            notesText.isNotBlank() ||
            hasJsonArrayContent(interestsJson) ||
            hasJsonArrayContent(sharedHistoryJson)
    }

    private fun hasJsonArrayContent(raw: String): Boolean {
        return raw.trim().let { it.isNotBlank() && it != "[]" }
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

    private fun currentFirebaseUserOrNull() = runCatching {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    }.getOrNull()
}
