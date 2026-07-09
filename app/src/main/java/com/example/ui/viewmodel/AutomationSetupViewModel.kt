package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.gemini.GeminiClient
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.DiagnosticSnapshotRepository
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.service.PreferencesRepository
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val _uiState = MutableStateFlow(AutomationSetupUiState())
    private val readinessPresenter = AutomationSetupReadinessPresenter(appContext)
    private val accountProviderCheckPresenter = AutomationSetupAccountProviderCheckPresenter(appContext)
    private val automationChannelCheckPresenter = AutomationSetupAutomationChannelCheckPresenter(appContext)
    private val qualityCheckPresenter = AutomationSetupQualityCheckPresenter(appContext)
    private val emailCheckPresenter = AutomationSetupEmailCheckPresenter(appContext)
    private val systemRecoveryCheckPresenter = AutomationSetupSystemRecoveryCheckPresenter(appContext)
    private val aiFailureDiagnoser = AutomationSetupAiFailureDiagnoser(appContext)
    private val diagnosticSnapshotStore = AutomationSetupDiagnosticSnapshotStore(diagnosticSnapshotRepository)
    private val capabilityProbe = AutomationSetupCapabilityProbe(appContext)
    private val commandRunner = AutomationSetupCommandRunner(
        context = appContext,
        preferencesRepository = preferencesRepository,
        syncContactsUseCase = syncContactsUseCase,
        geminiClient = geminiClient,
        testSendUseCase = testSendUseCase,
        aiFailureDiagnoser = aiFailureDiagnoser,
        hasFirebaseAuth = { capabilityProbe.currentFirebaseUserOrNull() != null },
    )
    private val reportBuilder = AutomationSetupReadinessReportBuilder(
        context = appContext,
        preferencesRepository = preferencesRepository,
        contactRepository = contactRepository,
        styleProfileRepository = styleProfileRepository,
        dispatchAttemptRepository = dispatchAttemptRepository,
        readinessPresenter = readinessPresenter,
        accountProviderCheckPresenter = accountProviderCheckPresenter,
        automationChannelCheckPresenter = automationChannelCheckPresenter,
        qualityCheckPresenter = qualityCheckPresenter,
        emailCheckPresenter = emailCheckPresenter,
        systemRecoveryCheckPresenter = systemRecoveryCheckPresenter,
        aiFailureDiagnoser = aiFailureDiagnoser,
        diagnosticSnapshotStore = diagnosticSnapshotStore,
        capabilityProbe = capabilityProbe,
    )
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
            ) { contacts, styleProfile, _, persistedRecoveryCount ->
                AutomationSetupReadinessInputs(
                    contacts = contacts,
                    styleProfile = styleProfile,
                    persistedRecoveryCount = persistedRecoveryCount,
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
            val message = commandRunner.syncContactsMessage()
            _uiState.value = _uiState.value.copy(
                isSyncingContacts = false,
                operationMessage = message,
            )
            refreshChecks(clearOperationMessage = false)
        }
    }

    fun runSafeGenerationCheck() {
        _uiState.value = _uiState.value.copy(
            operationMessage = commandRunner.safeGenerationMessage(_uiState.value),
        )
    }

    fun testAiGeneration() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingAi = true, operationMessage = null)
            val message = commandRunner.testAiGenerationMessage()
            _uiState.value = _uiState.value.copy(
                isTestingAi = false,
                operationMessage = message,
            )
            refreshChecks(clearOperationMessage = false)
        }
    }

    fun testEmailSend() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingEmail = true, operationMessage = null)
            val message = commandRunner.testEmailSendMessage()
            _uiState.value = _uiState.value.copy(
                isTestingEmail = false,
                operationMessage = message,
            )
            refreshChecks(clearOperationMessage = false)
        }
    }

    fun setWhatsAppAutomationConsent(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            whatsAppAutomationConsentGranted = granted,
            operationMessage = commandRunner.saveWhatsAppAutomationConsentMessage(granted),
        )
        refreshChecks(clearOperationMessage = false)
    }

    private suspend fun buildReport(inputs: AutomationSetupReadinessInputs? = null): AiDoctorReport =
        reportBuilder.build(inputs)
}
