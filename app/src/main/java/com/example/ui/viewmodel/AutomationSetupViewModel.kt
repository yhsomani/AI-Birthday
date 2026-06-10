package com.example.ui.viewmodel

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.core.db.entities.ContactEntity
import com.example.core.gemini.GeminiClient
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.CircuitState
import com.example.core.resilience.DeadLetterQueue
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.StructuredLogger
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.usecase.SyncContactsUseCase
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

enum class AiDoctorAction {
    NONE,
    REFRESH,
    TEST_AI,
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
    val title: String = "AI Doctor is checking your setup",
    val detail: String = "Run a refresh to inspect AI, personalization, and automation health.",
    val status: ReadinessStatus = ReadinessStatus.WARNING,
)

data class ReadinessCheck(
    val title: String,
    val detail: String,
    val status: ReadinessStatus,
    val actionLabel: String? = null,
    val action: AiDoctorAction = AiDoctorAction.NONE,
)

data class AutomationSetupUiState(
    val checks: List<ReadinessCheck> = emptyList(),
    val summary: AiDoctorSummary = AiDoctorSummary(),
    val isRefreshing: Boolean = false,
    val isSyncingContacts: Boolean = false,
    val isTestingAi: Boolean = false,
    val operationMessage: String? = null,
)

private data class AiDoctorReport(
    val summary: AiDoctorSummary,
    val checks: List<ReadinessCheck>,
)

@HiltViewModel
class AutomationSetupViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val securePrefs: SecurePrefs,
    private val syncContactsUseCase: SyncContactsUseCase,
    private val geminiClient: GeminiClient,
    private val contactRepository: ContactRepository,
    private val styleProfileRepository: StyleProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationSetupUiState())
    val uiState: StateFlow<AutomationSetupUiState> = _uiState.asStateFlow()

    init {
        refreshChecks()
    }

    fun refreshChecks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, operationMessage = null)
            val report = withContext(Dispatchers.IO) { buildReport() }
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                checks = report.checks,
                summary = report.summary,
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
                    operationMessage = "Synced ${outcome.googleCount} contacts.",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncingContacts = false,
                    operationMessage = e.localizedMessage ?: "Contact sync failed.",
                )
            }
            refreshChecks()
        }
    }

    fun runSafeGenerationCheck() {
        val ready = securePrefs.isAiWishGenerationEnabled() &&
            (securePrefs.getGeminiApiKey().isNotBlank() || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null)
        val firstBlocker = _uiState.value.checks.firstOrNull { it.status == ReadinessStatus.ACTION_REQUIRED }
        _uiState.value = _uiState.value.copy(
            operationMessage = if (ready) {
                firstBlocker?.let { "Dry run found a setup blocker: ${it.title}. ${it.detail}" }
                    ?: "AI generation is ready. This dry-run did not create or send messages."
            } else {
                "AI generation needs a Gemini API key, signed-in Google account, and enabled AI toggle."
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
                        "AI test completed successfully."
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingAi = false,
                    operationMessage = e.localizedMessage ?: "AI test failed.",
                )
            }
            refreshChecks()
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
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val contacts = runCatching { contactRepository.getAllSync() }.getOrDefault(emptyList())
        val styleProfile = runCatching { styleProfileRepository.getProfileOnce() }.getOrNull()
        val styleSampleCount = maxOf(
            styleProfile?.sampleCount ?: 0,
            countJsonArrayItems(styleProfile?.sampleMessagesJson),
        )

        val checks = listOf(
            ReadinessCheck(
                "Google Contacts",
                if (securePrefs.getGoogleOAuthToken().isNotBlank() || currentUser != null) {
                    "Account/token available for sync."
                } else {
                    "Sign in with Google and grant Contacts access."
                },
                if (securePrefs.getGoogleOAuthToken().isNotBlank() || currentUser != null) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (securePrefs.getGoogleOAuthToken().isBlank() && currentUser == null) "Sync Contacts" else null,
                action = if (securePrefs.getGoogleOAuthToken().isBlank() && currentUser == null) AiDoctorAction.SYNC_CONTACTS else AiDoctorAction.NONE,
            ),
            ReadinessCheck(
                "Gemini",
                if (securePrefs.getGeminiApiKey().isNotBlank()) "API key configured." else "Use Firebase auth or add a Gemini API key.",
                if (securePrefs.getGeminiApiKey().isNotBlank() || currentUser != null) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (securePrefs.getGeminiApiKey().isBlank() && currentUser == null) "Open Settings" else "Test AI",
                action = if (securePrefs.getGeminiApiKey().isBlank() && currentUser == null) AiDoctorAction.OPEN_SETTINGS else AiDoctorAction.TEST_AI,
            ),
            ReadinessCheck(
                "AI Wish Generation",
                if (securePrefs.isAiWishGenerationEnabled()) "Enabled in Settings." else "Disabled in Settings.",
                if (securePrefs.isAiWishGenerationEnabled()) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (securePrefs.isAiWishGenerationEnabled()) null else "Open Settings",
                action = if (securePrefs.isAiWishGenerationEnabled()) AiDoctorAction.NONE else AiDoctorAction.OPEN_SETTINGS,
            ),
            ReadinessCheck(
                "Style Coach",
                when {
                    styleSampleCount >= 3 -> "Trained with $styleSampleCount writing samples."
                    styleSampleCount > 0 -> "Add ${3 - styleSampleCount} more writing sample(s) so AI can match your tone."
                    else -> "No writing samples found. Messages may sound generic."
                },
                when {
                    styleSampleCount >= 3 -> ReadinessStatus.OK
                    styleSampleCount > 0 -> ReadinessStatus.WARNING
                    else -> ReadinessStatus.ACTION_REQUIRED
                },
                actionLabel = if (styleSampleCount >= 3) null else "Open Style Coach",
                action = if (styleSampleCount >= 3) AiDoctorAction.NONE else AiDoctorAction.OPEN_STYLE_COACH,
            ),
            personalizationCheck(contacts),
            ReadinessCheck(
                "Gemini Circuit",
                health.circuitBreakerStates["gemini"]?.let { state ->
                    if (state == CircuitState.CLOSED) "Gemini circuit is healthy." else "Gemini circuit is $state after repeated failures."
                } ?: "Gemini circuit has not reported a state yet.",
                when (health.circuitBreakerStates["gemini"]) {
                    null, CircuitState.CLOSED -> ReadinessStatus.OK
                    CircuitState.HALF_OPEN -> ReadinessStatus.WARNING
                    CircuitState.OPEN -> ReadinessStatus.ACTION_REQUIRED
                },
                actionLabel = if (health.circuitBreakerStates["gemini"] == CircuitState.OPEN) "Test AI" else null,
                action = if (health.circuitBreakerStates["gemini"] == CircuitState.OPEN) AiDoctorAction.TEST_AI else AiDoctorAction.NONE,
            ),
            ReadinessCheck(
                "Notifications",
                if (hasNotificationPermission()) "Approval and reminder notifications can be shown." else "Notification permission is missing.",
                if (hasNotificationPermission()) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (hasNotificationPermission()) null else "App Settings",
                action = if (hasNotificationPermission()) AiDoctorAction.NONE else AiDoctorAction.OPEN_APP_SETTINGS,
            ),
            ReadinessCheck(
                "SMS",
                if (hasSmsPermission()) "SMS dispatch is allowed." else "SMS permission is missing.",
                if (hasSmsPermission()) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (hasSmsPermission()) null else "App Settings",
                action = if (hasSmsPermission()) AiDoctorAction.NONE else AiDoctorAction.OPEN_APP_SETTINGS,
            ),
            ReadinessCheck(
                "WhatsApp Automation",
                if (isWhatsAppAutomationServiceEnabled()) "Accessibility service is enabled." else "Enable only if you want automatic WhatsApp sends.",
                if (isWhatsAppAutomationServiceEnabled()) ReadinessStatus.OK else ReadinessStatus.WARNING,
                actionLabel = if (isWhatsAppAutomationServiceEnabled()) null else "Open Accessibility",
                action = if (isWhatsAppAutomationServiceEnabled()) AiDoctorAction.NONE else AiDoctorAction.OPEN_ACCESSIBILITY_SETTINGS,
            ),
            ReadinessCheck(
                "Exact Sends",
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                    "Exact alarm scheduling is available."
                } else {
                    "Exact alarm access is disabled by the system."
                },
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
                actionLabel = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) null else "App Settings",
                action = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) AiDoctorAction.NONE else AiDoctorAction.OPEN_APP_SETTINGS,
            ),
            ReadinessCheck(
                "Daily Automation",
                if (dailyScheduled) "Daily WorkManager trigger is scheduled." else "Daily automation work is not currently scheduled.",
                if (dailyScheduled) ReadinessStatus.OK else ReadinessStatus.WARNING,
                actionLabel = if (dailyScheduled) null else "Refresh",
                action = if (dailyScheduled) AiDoctorAction.NONE else AiDoctorAction.REFRESH,
            ),
            ReadinessCheck(
                "Recent Errors",
                if (recentErrors.isEmpty() && health.recentErrors.isEmpty()) {
                    "No recent automation errors."
                } else {
                    diagnoseAiFailure(recentErrors.lastOrNull()?.message ?: health.recentErrors.last())
                },
                if (recentErrors.isEmpty() && health.recentErrors.isEmpty()) ReadinessStatus.OK else ReadinessStatus.WARNING,
                actionLabel = if (recentErrors.isEmpty() && health.recentErrors.isEmpty()) null else "View Activity",
                action = if (recentErrors.isEmpty() && health.recentErrors.isEmpty()) AiDoctorAction.NONE else AiDoctorAction.OPEN_ACTIVITY_HISTORY,
            ),
            ReadinessCheck(
                "Dead Letter Queue",
                "${DeadLetterQueue.count()} failed dispatch records in memory.",
                if (DeadLetterQueue.count() == 0) ReadinessStatus.OK else ReadinessStatus.WARNING,
                actionLabel = if (DeadLetterQueue.count() == 0) null else "View Activity",
                action = if (DeadLetterQueue.count() == 0) AiDoctorAction.NONE else AiDoctorAction.OPEN_ACTIVITY_HISTORY,
            ),
        )
        return AiDoctorReport(summary = checks.toSummary(), checks = checks)
    }

    private fun personalizationCheck(contacts: List<ContactEntity>): ReadinessCheck {
        if (contacts.isEmpty()) {
            return ReadinessCheck(
                title = "Personalization Data",
                detail = "No contacts found. Sync contacts before diagnosing generic AI messages.",
                status = ReadinessStatus.WARNING,
                actionLabel = "Sync Contacts",
                action = AiDoctorAction.SYNC_CONTACTS,
            )
        }

        val enriched = contacts.count { it.hasPersonalizationData() }
        val percentage = (enriched * 100) / contacts.size
        return ReadinessCheck(
            title = "Personalization Data",
            detail = if (percentage >= 50) {
                "$enriched of ${contacts.size} contacts have nicknames, interests, memories, or notes."
            } else {
                "Only $enriched of ${contacts.size} contacts have personal details. Generic inputs create generic wishes."
            },
            status = if (percentage >= 50) ReadinessStatus.OK else ReadinessStatus.WARNING,
            actionLabel = if (percentage >= 50) null else "Review Contacts",
            action = if (percentage >= 50) AiDoctorAction.NONE else AiDoctorAction.OPEN_CONTACTS,
        )
    }

    private fun List<ReadinessCheck>.toSummary(): AiDoctorSummary {
        val blockers = count { it.status == ReadinessStatus.ACTION_REQUIRED }
        val warnings = count { it.status == ReadinessStatus.WARNING }
        val firstProblem = firstOrNull { it.status == ReadinessStatus.ACTION_REQUIRED }
            ?: firstOrNull { it.status == ReadinessStatus.WARNING }

        return when {
            blockers > 0 -> AiDoctorSummary(
                title = "AI Doctor found $blockers fix needed",
                detail = firstProblem?.let { "Start with ${it.title}: ${it.detail}" }
                    ?: "Resolve the required setup items below.",
                status = ReadinessStatus.ACTION_REQUIRED,
            )
            warnings > 0 -> AiDoctorSummary(
                title = "AI works, but quality can improve",
                detail = firstProblem?.let { "${it.title}: ${it.detail}" }
                    ?: "Review the warnings below for better results.",
                status = ReadinessStatus.WARNING,
            )
            else -> AiDoctorSummary(
                title = "AI Doctor found no issues",
                detail = "Generation, personalization, and automation checks look healthy.",
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
                "Gemini quota or rate limit was hit. Wait a few minutes, then run Test AI again."
            lower.contains("api key") || lower.contains("apikey") || lower.contains("permission") || lower.contains("403") || lower.contains("unauthenticated") ->
                "Gemini authentication failed. Check the API key, Firebase auth, and project permissions."
            lower.contains("network") || lower.contains("timeout") || lower.contains("unavailable") || lower.contains("unable to resolve") ->
                "Network access failed. Check the connection and retry the AI test."
            lower.contains("json") || lower.contains("parse") || lower.contains("empty response") ->
                "AI returned an invalid or empty response. Regenerate once; if it repeats, check recent errors."
            lower.contains("circuit breaker") ->
                "AI calls are temporarily paused after repeated failures. Wait a minute, then run Test AI."
            else -> "Recent AI issue: ${raw.take(160)}"
        }
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
}
