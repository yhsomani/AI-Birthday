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
import com.example.core.gemini.GeminiClient
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.DeadLetterQueue
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.StructuredLogger
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

enum class ReadinessStatus { OK, WARNING, ACTION_REQUIRED }

data class ReadinessCheck(
    val title: String,
    val detail: String,
    val status: ReadinessStatus,
)

data class AutomationSetupUiState(
    val checks: List<ReadinessCheck> = emptyList(),
    val isRefreshing: Boolean = false,
    val isSyncingContacts: Boolean = false,
    val isTestingAi: Boolean = false,
    val operationMessage: String? = null,
)

@HiltViewModel
class AutomationSetupViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val securePrefs: SecurePrefs,
    private val syncContactsUseCase: SyncContactsUseCase,
    private val geminiClient: GeminiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationSetupUiState())
    val uiState: StateFlow<AutomationSetupUiState> = _uiState.asStateFlow()

    init {
        refreshChecks()
    }

    fun refreshChecks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, operationMessage = null)
            val checks = withContext(Dispatchers.IO) { buildChecks() }
            _uiState.value = _uiState.value.copy(isRefreshing = false, checks = checks)
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
        _uiState.value = _uiState.value.copy(
            operationMessage = if (ready) {
                "AI generation is ready. This dry-run did not create or send messages."
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
                        "AI test returned an error. Check Gemini/Firebase configuration."
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

    private fun buildChecks(): List<ReadinessCheck> {
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

        return listOf(
            ReadinessCheck(
                "Google Contacts",
                if (securePrefs.getGoogleOAuthToken().isNotBlank() || currentUser != null) {
                    "Account/token available for sync."
                } else {
                    "Sign in with Google and grant Contacts access."
                },
                if (securePrefs.getGoogleOAuthToken().isNotBlank() || currentUser != null) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
            ),
            ReadinessCheck(
                "Gemini",
                if (securePrefs.getGeminiApiKey().isNotBlank()) "API key configured." else "Use Firebase auth or add a Gemini API key.",
                if (securePrefs.getGeminiApiKey().isNotBlank() || currentUser != null) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
            ),
            ReadinessCheck(
                "AI Wish Generation",
                if (securePrefs.isAiWishGenerationEnabled()) "Enabled in Settings." else "Disabled in Settings.",
                if (securePrefs.isAiWishGenerationEnabled()) ReadinessStatus.OK else ReadinessStatus.WARNING,
            ),
            ReadinessCheck(
                "Notifications",
                if (hasNotificationPermission()) "Approval and reminder notifications can be shown." else "Notification permission is missing.",
                if (hasNotificationPermission()) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
            ),
            ReadinessCheck(
                "SMS",
                if (hasSmsPermission()) "SMS dispatch is allowed." else "SMS permission is missing.",
                if (hasSmsPermission()) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
            ),
            ReadinessCheck(
                "WhatsApp Automation",
                if (isWhatsAppAutomationServiceEnabled()) "Accessibility service is enabled." else "Enable only if you want automatic WhatsApp sends.",
                if (isWhatsAppAutomationServiceEnabled()) ReadinessStatus.OK else ReadinessStatus.WARNING,
            ),
            ReadinessCheck(
                "Exact Sends",
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                    "Exact alarm scheduling is available."
                } else {
                    "Exact alarm access is disabled by the system."
                },
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) ReadinessStatus.OK else ReadinessStatus.ACTION_REQUIRED,
            ),
            ReadinessCheck(
                "Daily Automation",
                if (dailyScheduled) "Daily WorkManager trigger is scheduled." else "Daily automation work is not currently scheduled.",
                if (dailyScheduled) ReadinessStatus.OK else ReadinessStatus.WARNING,
            ),
            ReadinessCheck(
                "Recent Errors",
                if (recentErrors.isEmpty() && health.recentErrors.isEmpty()) "No recent automation errors." else (recentErrors.lastOrNull()?.message ?: health.recentErrors.last()),
                if (recentErrors.isEmpty() && health.recentErrors.isEmpty()) ReadinessStatus.OK else ReadinessStatus.WARNING,
            ),
            ReadinessCheck(
                "Dead Letter Queue",
                "${DeadLetterQueue.count()} failed dispatch records in memory.",
                if (DeadLetterQueue.count() == 0) ReadinessStatus.OK else ReadinessStatus.WARNING,
            ),
        )
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
