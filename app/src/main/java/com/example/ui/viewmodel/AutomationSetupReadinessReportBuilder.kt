package com.example.ui.viewmodel

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.StructuredLogger
import com.example.domain.automation.SetupAccountProviderReadinessPolicy
import com.example.domain.automation.SetupAutomationReadinessPolicy
import com.example.domain.automation.SetupChannelReadinessPolicy
import com.example.domain.automation.SetupEmailReadinessPolicy
import com.example.domain.automation.SetupQualityReadinessPolicy
import com.example.domain.automation.SetupSystemReadinessPolicy
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageChannelSetCodec
import com.example.domain.model.common.JsonTextCodec
import com.example.domain.model.contact.ContactAutomationReadinessProfile
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.service.PreferencesRepository

internal class AutomationSetupReadinessReportBuilder(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val contactRepository: ContactRepository,
    private val styleProfileRepository: StyleProfileRepository,
    private val dispatchAttemptRepository: DispatchAttemptRepository,
    private val readinessPresenter: AutomationSetupReadinessPresenter,
    private val accountProviderCheckPresenter: AutomationSetupAccountProviderCheckPresenter,
    private val automationChannelCheckPresenter: AutomationSetupAutomationChannelCheckPresenter,
    private val qualityCheckPresenter: AutomationSetupQualityCheckPresenter,
    private val emailCheckPresenter: AutomationSetupEmailCheckPresenter,
    private val systemRecoveryCheckPresenter: AutomationSetupSystemRecoveryCheckPresenter,
    private val aiFailureDiagnoser: AutomationSetupAiFailureDiagnoser,
    private val diagnosticSnapshotStore: AutomationSetupDiagnosticSnapshotStore,
    private val capabilityProbe: AutomationSetupCapabilityProbe,
) {
    suspend fun build(inputs: AutomationSetupReadinessInputs? = null): AiDoctorReport {
        val workInfos = try {
            WorkManager.getInstance(context).getWorkInfosByTag("daily_trigger").get()
        } catch (_: Exception) {
            emptyList<WorkInfo>()
        }
        val dailyScheduled = workInfos.any {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        }
        val health = HealthMonitor.snapshot()
        val recentErrors = StructuredLogger.getErrors().takeLast(3)
        val currentUser = capabilityProbe.currentFirebaseUserOrNull()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val contacts = inputs?.contacts
            ?: runCatching { contactRepository.getAutomationReadinessProfiles() }.getOrDefault(emptyList())
        val styleProfile = inputs?.styleProfile
            ?: runCatching { styleProfileRepository.getProfileOnce() }.getOrNull()
        val styleSampleCount = maxOf(
            styleProfile?.sampleCount ?: 0,
            JsonTextCodec.countStringArrayItems(styleProfile?.sampleMessagesJson),
        )
        val hasGoogleContactsAccess = capabilityProbe.hasGoogleContactsAccess(
            hasCachedGoogleOAuthToken = preferencesRepository.getGoogleOAuthToken().isNotBlank(),
        )
        val hasGeminiApiKey = preferencesRepository.getGeminiApiKey().isNotBlank()
        val hasFirebaseAuth = currentUser != null
        val globalAutomationMode = preferencesRepository.getGlobalAutomationMode()
        val aiEnabled = preferencesRepository.isAiWishGenerationEnabled()
        val notificationsAllowed = runCatching { capabilityProbe.hasNotificationPermission() }.getOrDefault(false)
        val smsAllowed = runCatching { capabilityProbe.hasSmsPermission() }.getOrDefault(false)
        val whatsAppConsentGranted = preferencesRepository.isWhatsAppAutomationConsentGranted()
        val whatsAppAutomationEnabled = runCatching {
            capabilityProbe.isWhatsAppAutomationServiceEnabled()
        }.getOrDefault(false)
        val exactSendsAllowed = runCatching {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        }.getOrDefault(false)
        val dispatchRecovery = loadDispatchRecoverySnapshot(
            persistedRecoveryCount = inputs?.persistedRecoveryCount,
        )
        val persistedHealthSnapshot = diagnosticSnapshotStore.loadRecentPersistedHealthSnapshot()
        val senderEmail = preferencesRepository.getSenderEmail().trim()
        val senderEmailPassword = preferencesRepository.getSenderEmailPassword().trim()
        val senderEmailReady = SetupEmailReadinessPolicy.isSenderReady(
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        )
        val blockedChannels = MessageChannelSetCodec.parse(preferencesRepository.getChannelBlackout())
        val selectedChannelCounts = SetupAutomationReadinessPolicy.selectedAutomaticChannelCounts(
            contacts = contacts,
            senderEmailReady = senderEmailReady,
            blockedChannels = blockedChannels,
        )
        val selectedChannels = selectedChannelCounts.filterValues { it > 0 }.keys
        val channelVerificationSinceMs = System.currentTimeMillis() - AUTOMATION_CHANNEL_VERIFICATION_WINDOW_MS
        val emailSelfTestVerified = senderEmailReady &&
            preferencesRepository.getLastSuccessfulEmailTestMs() >= channelVerificationSinceMs &&
            preferencesRepository.getLastSuccessfulEmailTestSender().equals(senderEmail, ignoreCase = true)
        val successfulChannels = runCatching {
            dispatchAttemptRepository.getSuccessfulChannelsSince(channelVerificationSinceMs)
        }.getOrDefault(emptySet()) + if (emailSelfTestVerified) setOf(MessageChannel.EMAIL) else emptySet()
        val emailPreferredContacts = contacts.count {
            it.preferredChannel == MessageChannel.EMAIL
        }
        val whatsAppInstalled = runCatching { capabilityProbe.isWhatsAppInstalled() }.getOrDefault(false)
        val hasRecentHealthEvidence = recentErrors.isNotEmpty() ||
            health.recentErrors.isNotEmpty() ||
            persistedHealthSnapshot != null

        val checks = listOf(
            accountProviderCheckPresenter.googleContacts(
                SetupAccountProviderReadinessPolicy.evaluateGoogleContacts(
                    hasGoogleContactsAccess = hasGoogleContactsAccess,
                ),
            ),
            accountProviderCheckPresenter.geminiAccess(
                SetupAccountProviderReadinessPolicy.evaluateGeminiAccess(
                    hasGeminiApiKey = hasGeminiApiKey,
                    hasFirebaseAuth = hasFirebaseAuth,
                ),
            ),
            accountProviderCheckPresenter.aiWishGeneration(
                SetupAccountProviderReadinessPolicy.evaluateAiWishGeneration(
                    aiWishGenerationEnabled = aiEnabled,
                ),
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
            qualityCheckPresenter.styleCoach(
                SetupQualityReadinessPolicy.evaluateStyleCoach(
                    styleSampleCount = styleSampleCount,
                ),
            ),
            personalizationCheck(contacts),
            genericMessagesCheck(contacts),
            accountProviderCheckPresenter.geminiCircuit(
                SetupAccountProviderReadinessPolicy.evaluateGeminiCircuit(
                    circuitState = health.circuitBreakerStates["gemini"].toSetupProviderCircuitState(),
                ),
            ),
            systemRecoveryCheckPresenter.notifications(
                SetupSystemReadinessPolicy.evaluateNotificationPermission(
                    notificationsAllowed = notificationsAllowed,
                ),
            ),
            smsReadinessCheck(
                smsAllowed = smsAllowed,
                selectedSmsContactCount = selectedChannelCounts[MessageChannel.SMS] ?: 0,
                smsDisabled = MessageChannel.SMS in blockedChannels,
            ),
            emailCheckPresenter.email(
                SetupEmailReadinessPolicy.evaluate(
                    senderEmail = senderEmail,
                    senderEmailPassword = senderEmailPassword,
                    emailSelfTestVerified = emailSelfTestVerified,
                    emailPreferredContactCount = emailPreferredContacts,
                ),
            ),
            whatsAppReadinessCheck(
                consentGranted = whatsAppConsentGranted,
                accessibilityEnabled = whatsAppAutomationEnabled,
                whatsAppInstalled = whatsAppInstalled,
                selectedWhatsAppContactCount = selectedChannelCounts[MessageChannel.WHATSAPP] ?: 0,
                whatsAppDisabled = MessageChannel.WHATSAPP in blockedChannels,
            ),
            systemRecoveryCheckPresenter.exactSends(
                SetupSystemReadinessPolicy.evaluateExactSends(
                    exactSendsAllowed = exactSendsAllowed,
                ),
            ),
            systemRecoveryCheckPresenter.dailyAutomation(
                SetupSystemReadinessPolicy.evaluateDailyAutomation(
                    dailyScheduled = dailyScheduled,
                ),
            ),
            systemRecoveryCheckPresenter.recentHealth(
                readiness = SetupSystemReadinessPolicy.evaluateRecentHealth(
                    hasRecentHealthEvidence = hasRecentHealthEvidence,
                ),
                recentErrorDetail = recentErrors.toRecentErrorDetail(
                    context = context,
                    health = health,
                    persistedHealthSnapshot = persistedHealthSnapshot,
                    aiFailureDiagnoser = aiFailureDiagnoser,
                ),
            ),
            systemRecoveryCheckPresenter.dispatchRecovery(
                readiness = SetupSystemReadinessPolicy.evaluateDispatchRecovery(
                    persistedRecoveryCount = dispatchRecovery.persistedRecoveryCount,
                ),
                recoveryDetail = dispatchRecovery.toReadinessDetail(context),
            ),
        )
        return AiDoctorReport(
            summary = readinessPresenter.summarize(checks),
            checks = checks,
            recommendedFix = readinessPresenter.recommendedFix(checks),
            setupProgress = readinessPresenter.setupProgress(checks),
            setupActionReadiness = readinessPresenter.setupActionReadiness(checks),
        ).also { report ->
            diagnosticSnapshotStore.recordAiDoctorSnapshot(report)
        }
    }

    private suspend fun loadDispatchRecoverySnapshot(
        persistedRecoveryCount: Int? = null,
    ): DispatchRecoverySnapshot = dispatchAttemptRepository.loadDispatchRecoverySnapshot(persistedRecoveryCount)

    private fun fullAutomationModeCheck(
        globalAutomationMode: ApprovalMode,
        contacts: List<ContactAutomationReadinessProfile>,
    ): ReadinessCheck {
        return automationChannelCheckPresenter.fullAutomation(
            SetupAutomationReadinessPolicy.evaluateFullAutomation(
                globalAutomationMode = globalAutomationMode,
                contacts = contacts,
            ),
        )
    }

    private fun automatableEventsCheck(contacts: List<ContactAutomationReadinessProfile>): ReadinessCheck {
        return automationChannelCheckPresenter.automatableEvents(
            SetupAutomationReadinessPolicy.evaluateAutomatableEvents(contacts),
        )
    }

    private fun automaticDeliveryRoutesCheck(
        contacts: List<ContactAutomationReadinessProfile>,
        senderEmailReady: Boolean,
        blockedChannels: Set<MessageChannel>,
    ): ReadinessCheck {
        return automationChannelCheckPresenter.deliveryRoutes(
            SetupAutomationReadinessPolicy.evaluateDeliveryRoutes(
                contacts = contacts,
                senderEmailReady = senderEmailReady,
                blockedChannels = blockedChannels,
            ),
        )
    }

    private fun channelVerificationCheck(
        selectedChannels: Set<MessageChannel>,
        successfulChannels: Set<MessageChannel>,
    ): ReadinessCheck {
        return automationChannelCheckPresenter.channelVerification(
            SetupChannelReadinessPolicy.evaluateChannelVerification(
                selectedChannels = selectedChannels,
                successfulChannels = successfulChannels,
            ),
        )
    }

    private fun smsReadinessCheck(
        smsAllowed: Boolean,
        selectedSmsContactCount: Int,
        smsDisabled: Boolean,
    ): ReadinessCheck {
        return automationChannelCheckPresenter.sms(
            SetupChannelReadinessPolicy.evaluateSms(
                smsAllowed = smsAllowed,
                selectedSmsContactCount = selectedSmsContactCount,
                smsDisabled = smsDisabled,
            ),
        )
    }

    private fun whatsAppReadinessCheck(
        consentGranted: Boolean,
        accessibilityEnabled: Boolean,
        whatsAppInstalled: Boolean,
        selectedWhatsAppContactCount: Int,
        whatsAppDisabled: Boolean,
    ): ReadinessCheck {
        return automationChannelCheckPresenter.whatsApp(
            SetupChannelReadinessPolicy.evaluateWhatsApp(
                consentGranted = consentGranted,
                accessibilityEnabled = accessibilityEnabled,
                whatsAppInstalled = whatsAppInstalled,
                selectedWhatsAppContactCount = selectedWhatsAppContactCount,
                whatsAppDisabled = whatsAppDisabled,
            ),
        )
    }

    private fun personalizationCheck(contacts: List<ContactAutomationReadinessProfile>): ReadinessCheck {
        return qualityCheckPresenter.personalization(
            SetupQualityReadinessPolicy.evaluatePersonalization(contacts),
        )
    }

    private fun genericMessagesCheck(contacts: List<ContactAutomationReadinessProfile>): ReadinessCheck {
        return qualityCheckPresenter.genericMessages(
            SetupQualityReadinessPolicy.evaluateGenericMessages(contacts),
        )
    }

}
