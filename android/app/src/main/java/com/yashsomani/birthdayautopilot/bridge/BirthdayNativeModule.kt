package com.yashsomani.birthdayautopilot.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.yashsomani.birthdayautopilot.AppGraph
import com.yashsomani.birthdayautopilot.BuildConfig
import com.yashsomani.birthdayautopilot.R
import com.yashsomani.birthdayautopilot.auth.IdentityFailure
import com.yashsomani.birthdayautopilot.auth.IdentityOutcome
import com.yashsomani.birthdayautopilot.auth.ForegroundActivityRegistry
import com.yashsomani.birthdayautopilot.auth.TelephonyPermissionResult
import com.yashsomani.birthdayautopilot.auth.TelephonyPermissionRemediationPolicy
import com.yashsomani.birthdayautopilot.auth.NotificationPermissionActivityResultOwner
import com.yashsomani.birthdayautopilot.auth.NotificationPermissionResult
import com.yashsomani.birthdayautopilot.auth.GoogleAccessRevocationOutcome
import com.yashsomani.birthdayautopilot.attention.AndroidAttentionRouteStore
import com.yashsomani.birthdayautopilot.attention.AndroidNativeRouteEvents
import com.yashsomani.birthdayautopilot.bridge.codegen.NativeBirthdaySpec
import com.yashsomani.birthdayautopilot.configuration.AndroidConfigurationController
import com.yashsomani.birthdayautopilot.configuration.AccountModeCoordinationMaterial
import com.yashsomani.birthdayautopilot.configuration.AccountModePreparationOutcome
import com.yashsomani.birthdayautopilot.configuration.ConfigurationOutcome
import com.yashsomani.birthdayautopilot.configuration.TestStartOutcome
import com.yashsomani.birthdayautopilot.coordination.CoordinationSessionStatus
import com.yashsomani.birthdayautopilot.coordination.AccountModeAction
import com.yashsomani.birthdayautopilot.coordination.AccountModeOutcome
import com.yashsomani.birthdayautopilot.coordination.DistributionChannel
import com.yashsomani.birthdayautopilot.coordination.ServerAccountMode
import com.yashsomani.birthdayautopilot.coordination.CoordinationCompletion
import com.yashsomani.birthdayautopilot.coordination.CoordinationLifecycleStatusOutcome
import com.yashsomani.birthdayautopilot.coordination.CoordinationOperationOutcome
import com.yashsomani.birthdayautopilot.coordination.FirebaseCoordinationRuntime
import com.yashsomani.birthdayautopilot.coordination.NativeAccountBindingPredicate
import com.yashsomani.birthdayautopilot.automation.orchestration.AccountModeSpec
import com.yashsomani.birthdayautopilot.automation.orchestration.CoordinationBindingSpec
import com.yashsomani.birthdayautopilot.automation.orchestration.OrchestrationCall
import com.yashsomani.birthdayautopilot.automation.orchestration.FirebaseAutomationCoordinationPort
import com.yashsomani.birthdayautopilot.automation.orchestration.SenderTransferSpec
import com.yashsomani.birthdayautopilot.automation.orchestration.SenderReleaseSpec
import com.yashsomani.birthdayautopilot.automation.orchestration.TrustedTimeEstimator
import com.yashsomani.birthdayautopilot.automation.sms.SmsCallbackCleanup
import com.yashsomani.birthdayautopilot.automation.sms.SmsCallbackCleanupResult
import com.yashsomani.birthdayautopilot.automation.workers.AutomationScheduler
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.lifecycle.AndroidLifecycleController
import com.yashsomani.birthdayautopilot.lifecycle.DeletionReceiptLookup
import com.yashsomani.birthdayautopilot.lifecycle.DeletionReceiptAccountPolicy
import com.yashsomani.birthdayautopilot.lifecycle.DeletionReceiptRecoveryPolicy
import com.yashsomani.birthdayautopilot.lifecycle.DurableDeletionReceipt
import com.yashsomani.birthdayautopilot.lifecycle.PrivacyConfirmationOutcome
import com.yashsomani.birthdayautopilot.lifecycle.PrivacyActionPlan
import com.yashsomani.birthdayautopilot.lifecycle.SenderTransferConfirmationOutcome
import com.yashsomani.birthdayautopilot.lifecycle.SenderTransferPlan
import com.yashsomani.birthdayautopilot.lifecycle.SenderTransferRecoveryPolicy
import com.yashsomani.birthdayautopilot.people.PeopleSyncOutcome
import com.yashsomani.birthdayautopilot.readiness.AndroidReadinessSnapshot
import com.yashsomani.birthdayautopilot.readiness.EligibilityDecision
import com.yashsomani.birthdayautopilot.readiness.EligibilityKind
import com.yashsomani.birthdayautopilot.readiness.EligibilityReason
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.yashsomani.birthdayautopilot.storage.database.ApprovalRecordState
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotState
import com.yashsomani.birthdayautopilot.storage.database.PhoneRecordState
import com.yashsomani.birthdayautopilot.storage.database.PeopleDataFreshnessBand
import com.yashsomani.birthdayautopilot.storage.database.PeopleDataFreshnessPolicy
import com.yashsomani.birthdayautopilot.storage.database.RecipientEnrollmentState
import com.yashsomani.birthdayautopilot.storage.database.SyncFreshness
import java.time.Instant
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import android.os.SystemClock
import android.provider.Settings
import com.yashsomani.birthdayautopilot.coordination.SenderTransferOutcome
import com.yashsomani.birthdayautopilot.coordination.AccountDeletionAcceptance
import com.yashsomani.birthdayautopilot.coordination.AccountDeletionReceiptOutcome
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity
import com.yashsomani.birthdayautopilot.storage.database.CoordinationStateEntity
import kotlinx.coroutines.runBlocking
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject

private data class LifecyclePauseAttempt(
  val localPrepared: Boolean,
  val serverPaused: Boolean,
)

class BirthdayNativeModule(
  reactContext: ReactApplicationContext,
) : NativeBirthdaySpec(reactContext) {
  private val appGraph = AppGraph.get(reactContext)
  private val configurationController = AndroidConfigurationController(
    reactContext,
    appGraph.database,
    appGraph.recurrencePlanner,
    nativeLocaleProvider = appGraph.nativeLocaleProvider,
    geminiGateway = appGraph.geminiSuggestionGateway,
    accountSessionMatches = appGraph::identitySessionMatches,
    subscriptionChangePending = {
      appGraph.subscriptionChangeSignalStore.pendingGeneration() != null
    },
    trustedNowProvider = { account -> trustedNowMillis(account.accountId) },
  )
  private val lifecycleController = AndroidLifecycleController(
    reactContext,
    appGraph.database,
    accountSessionMatches = appGraph::identitySessionMatches,
    submissionGate = appGraph.submissionGate,
  )
  private val deletionRecoveryCoordinationPort by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    FirebaseAutomationCoordinationPort(
      FirebaseCoordinationRuntime.resolve(
        context = reactApplicationContext,
        environment = BuildConfig.APP_ENV,
        accountBindingPredicate = NativeAccountBindingPredicate { binding ->
          lifecycleController.matchesDeletionRecoveryBinding(binding)
        },
      ),
    )
  }
  private val senderReleaseRecoveryCoordinationPort by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    FirebaseAutomationCoordinationPort(
      FirebaseCoordinationRuntime.resolve(
        context = reactApplicationContext,
        environment = BuildConfig.APP_ENV,
        accountBindingPredicate = NativeAccountBindingPredicate { binding ->
          lifecycleController.matchesSenderReleaseRecoveryBinding(binding)
        },
      ),
    )
  }
  private val listeners = AtomicInteger(0)
  private val routeStore = AndroidAttentionRouteStore(reactContext)
  private val unsubscribeRouteEvents = AndroidNativeRouteEvents.subscribe(::emitRouteAvailable)
  private val executor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "BirthdayNativeBridge").apply { isDaemon = true }
  }

  override fun getProjection(area: String, requestJson: String, promise: Promise) {
    executor.execute {
      try {
        val request = parseObject(requestJson)
        if (area !in SUPPORTED_PROJECTION_AREAS || request == null) {
          promise.resolve(errorResponse("NATIVE_REQUEST_INVALID"))
          return@execute
        }

        val readinessSnapshot by lazy { appGraph.androidReadinessProbe.read() }
        val payload = when (area) {
          "bootstrap" -> bootstrapPayload(readinessSnapshot)
          "setup" -> setupPayload(readinessSnapshot)
          "home" -> homePayload(readinessSnapshot)
          "eligibility" -> eligibilityPayload(readinessSnapshot.eligibility)
          "readiness" -> readinessPayload(readinessSnapshot)
          "account" -> accountPayload()
          "contacts" -> contactsPayload(request)
          "messages" -> messagesPayload(request)
          "automation" -> automationProjectionPayload(request)
          "activity" -> activityProjectionPayload(request, readinessSnapshot)
          "privacy" -> privacyProjectionPayload(request)
          "route" -> routeProjectionPayload(request)
          "notifications" -> notificationProjectionPayload(request)
          else -> null
        }

        promise.resolve(
          if (payload == null) errorResponse("NATIVE_NOT_CONFIGURED")
          else successResponse(payload),
        )
      } catch (_: Exception) {
        promise.resolve(errorResponse("NATIVE_PROJECTION_FAILURE"))
      } catch (_: LinkageError) {
        promise.resolve(errorResponse("NATIVE_PROJECTION_FAILURE"))
      }
    }
  }

  override fun executeUserIntent(
    intent: String,
    expectedRevision: String?,
    payloadJson: String,
    promise: Promise,
  ) {
    executor.execute {
      try {
        val request = parseObject(payloadJson)
        if (intent !in USER_INTENTS ||
          !isValidRevision(expectedRevision) ||
          request == null
        ) {
          promise.resolve(errorResponse("NATIVE_REQUEST_INVALID"))
          return@execute
        }
        if (
          intent in DELETION_RECOVERY_IDENTITY_DEPENDENT_INTENTS &&
          appGraph.deletionRecoveryIdentitySessionBoundaryRequired() &&
          !(intent == "repair-lifecycle-state" && lifecycleRepairIdentitySessionReady()) &&
          !appGraph.ensureDeletionRecoveryIdentitySessionCleared()
        ) {
          promise.resolve(conflictResponse("coordination-unavailable"))
          return@execute
        }
        if (
          lifecycleController.lifecycleJournalUnreadable() &&
          intent !in JOURNAL_UNREADABLE_ALLOWED_INTENTS
        ) {
          promise.resolve(conflictResponse("coordination-unavailable"))
          return@execute
        }
        val deletionReceiptBlocker = DeletionReceiptAccountPolicy.blockerCode(
          lifecycleController.deletionReceiptLookup(),
        )
        if (
          deletionReceiptBlocker != null &&
          intent !in DELETION_RECEIPT_ALLOWED_INTENTS
        ) {
          promise.resolve(conflictResponse(deletionReceiptBlocker))
          return@execute
        }
        val pendingLifecycleOperation = lifecycleController.latestOperation()?.takeIf {
          it.state !in setOf("complete", "failed")
        }
        val deletionLocalWipeReviewIntent =
          lifecycleController.deletionLocalWipeReviewAllowed() &&
            intent in setOf("prepare-privacy-action", "confirm-privacy-action")
        if (
          pendingLifecycleOperation != null &&
          intent !in LIFECYCLE_OPERATION_ALLOWED_INTENTS &&
          !deletionLocalWipeReviewIntent
        ) {
          promise.resolve(conflictResponse("policy-suspended"))
          return@execute
        }

        when (intent) {
        "refresh-compatibility" -> promise.resolve(
          successResponse(eligibilityPayload(appGraph.androidReadinessProbe.read().eligibility)),
        )
        "continue-with-google" -> promise.resolve(handleGoogleIdentityIntent())
        "authorize-contacts",
        "sync-contacts",
        -> promise.resolve(
          handlePeopleSyncIntent(
            disclosureAcknowledged = intent == "authorize-contacts",
          ),
        )
        "choose-phone" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.choosePhone(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "choose-birthday" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.chooseBirthday(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "prepare-enrollment-review" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.prepareEnrollment(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "confirm-enrollment" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.confirmEnrollment(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "pause-recipient",
        "exclude-recipient",
        "restore-recipient",
        -> {
          val mutation = when (intent) {
            "pause-recipient" -> "pause"
            "exclude-recipient" -> "exclude"
            else -> "restore"
          }
          promise.resolve(
            handleConfigurationOutcome(
              expectedRevision.configurationRevisionOrNull()?.let {
                runBlocking { configurationController.mutateRecipient(mutation, request, it) }
              } ?: ConfigurationOutcome.InvalidRequest,
            ),
          )
        }
        "block-recipient-destination",
        "unblock-recipient-destination",
        -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking {
                configurationController.mutateSelectedDestinationBlock(
                  blocked = intent == "block-recipient-destination",
                  request = request,
                  expectedRevision = it,
                )
              }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "preview-message" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.previewMessage(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "save-message" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.saveMessage(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "preview-policy" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.previewPolicy(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "save-policy" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.savePolicy(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
            automationProjection = true,
          ),
        )
        "prepare-approvals" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.prepareApprovals(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "confirm-approvals" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.confirmApprovals(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
            automationProjection = true,
          ),
        )
        "prepare-test" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { configurationController.prepareTest(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "start-test" -> handleStartTestIntent(request, expectedRevision, promise)
        "prepare-activation" -> promise.resolve(handlePrepareActivation(resume = false))
        "prepare-resume" -> promise.resolve(handlePrepareActivation(resume = true))
        "activate" -> promise.resolve(
          handleActivationIntent(request, expectedRevision, resume = false),
        )
        "resume" -> promise.resolve(
          handleActivationIntent(request, expectedRevision, resume = true),
        )
        "pause-all" -> promise.resolve(handlePauseAll(request, expectedRevision))
        "generate-suggestions" -> promise.resolve(
          successResponse(runBlocking { appGraph.geminiSuggestionGateway.generate(request) }),
        )
        "prepare-today-occurrence" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking { lifecycleController.prepareTodayOccurrence(request, it) }
            } ?: ConfigurationOutcome.InvalidRequest,
          ),
        )
        "confirm-today-occurrence" -> promise.resolve(
          handleConfigurationOutcome(
            expectedRevision.configurationRevisionOrNull()?.let {
              runBlocking {
                appGraph.submissionGate.withExclusiveBoundary {
                  lifecycleController.confirmTodayOccurrence(request, it)
                }
              }
            } ?: ConfigurationOutcome.InvalidRequest,
            automationProjection = true,
          ).also {
            AutomationScheduler.enqueueImmediateLocal(reactApplicationContext, "FOREGROUND")
          },
        )
        "preview-diagnostics" -> promise.resolve(handlePreviewDiagnostics(request))
        "share-diagnostics" -> promise.resolve(handleShareDiagnostics(request, expectedRevision))
        "perform-native-action" -> promise.resolve(handleNativeAction(request, expectedRevision))
        "prepare-privacy-action" -> promise.resolve(
          handlePreparePrivacyAction(request, expectedRevision),
        )
        "confirm-privacy-action" -> promise.resolve(
          handleConfirmPrivacyAction(request, expectedRevision),
        )
        "prepare-sender-transfer" -> promise.resolve(
          handlePrepareSenderTransfer(request, expectedRevision),
        )
        "begin-sender-transfer" -> promise.resolve(
          handleBeginSenderTransfer(request, expectedRevision),
        )
        "complete-sender-transfer" -> promise.resolve(
          handleCompleteSenderTransfer(request),
        )
        "resume-lifecycle-operation" -> promise.resolve(
          handleResumeLifecycleOperation(request),
        )
        "repair-lifecycle-state" -> promise.resolve(
          handleRepairLifecycleState(request),
        )
        "check-account-deletion-status" -> promise.resolve(
          handleCheckAccountDeletionStatus(request),
        )
        "request-notification-permission" -> promise.resolve(
          handleRequestNotificationPermission(request),
        )
        "open-notification-settings" -> promise.resolve(
          handleOpenNotificationSettings(request),
        )
          else -> promise.resolve(errorResponse("NATIVE_NOT_CONFIGURED"))
        }
      } catch (_: Exception) {
        promise.resolve(errorResponse("NATIVE_INTENT_FAILURE"))
      } catch (_: LinkageError) {
        promise.resolve(errorResponse("NATIVE_INTENT_FAILURE"))
      }
    }
  }

  override fun addListener(eventType: String) {
    when (eventType) {
      INVALIDATION_EVENT, ROUTE_EVENT -> listeners.incrementAndGet()
    }
  }

  override fun removeListeners(count: Double) {
    if (!count.isFinite() || count < 0 || count % 1.0 != 0.0) return
    listeners.updateAndGet { current -> (current - count.toInt()).coerceAtLeast(0) }
  }

  override fun invalidate() {
    listeners.set(0)
    unsubscribeRouteEvents()
    executor.shutdownNow()
    super.invalidate()
  }

  private fun successResponse(payload: Any) = response("ok", payload)

  private fun errorResponse(supportCode: String) = response(
    "error",
    JSONObject()
      .put("kind", "internal")
      .put("supportCode", supportCode),
  )

  private fun response(kind: String, payload: Any) = Arguments.createMap().apply {
    putInt("contractVersion", CONTRACT_VERSION)
    putString("revision", currentRevision())
    putString("generatedAt", Instant.now().toString())
    putString("kind", kind)
    putString("payloadJson", payload.toString())
  }

  private fun currentRevision(): String = try {
    kotlinx.coroutines.runBlocking {
      appGraph.database.birthdayDao().getControl()?.revision?.toString() ?: "0"
    }
  } catch (_: Exception) {
    "0"
  }

  private fun bootstrapPayload(snapshot: AndroidReadinessSnapshot) = JSONObject()
    .put("capability", capabilityPayload())
    .put("eligibility", eligibilityPayload(snapshot.eligibility))
    .put("account", accountPayload())
    .put("setupStep", setupStep(snapshot))

  private fun setupPayload(snapshot: AndroidReadinessSnapshot) = JSONObject()
    .put("step", setupStep(snapshot))
    .put(
      "initialActivationCompleted",
      runBlocking { configurationController.initialActivationCompleted() },
    )
    .put("eligibility", eligibilityPayload(snapshot.eligibility))
    .put("account", accountPayload())
    .put("contacts", contactsSyncPayload())
    .put("readiness", readinessPayload(snapshot))
    .put("automation", automationPayload(snapshot))

  private fun homePayload(snapshot: AndroidReadinessSnapshot): JSONObject = runBlocking {
    configurationController.homePayload(
      automationPayload(snapshot),
      contactsSyncPayload(),
    )
  }

  private fun capabilityPayload() = JSONObject()
    .put("platform", "android")
    .put("deliveryMode", "unattended-device-sms")
    .put("minimumApiLevel", 29)
    .put("unattendedSms", "release-gated")
    .put("userComposer", "available-as-explicit-alternative")

  private fun eligibilityPayload(decision: EligibilityDecision): JSONObject {
    val payload = JSONObject()
      .put("kind", decision.kind.name.lowercase())
      .put("capability", capabilityPayload())
    if (decision.kind == EligibilityKind.SUPPORTED) {
      return payload
        .put("channelLabel", BuildConfig.APPROVED_DISTRIBUTION_CHANNEL)
        .put("chargeDisclosureVersion", CHARGE_DISCLOSURE_VERSION)
    }
    return payload
      .put("primaryIssue", eligibilityIssuePayload(decision.primaryReason!!))
      .put(
        "otherIssues",
        org.json.JSONArray().apply {
          decision.otherReasons.forEach { put(eligibilityIssuePayload(it)) }
        },
      )
  }

  private fun readinessPayload(snapshot: AndroidReadinessSnapshot): JSONObject {
    val durable = runBlocking { configurationController.durableReadiness() }
    val coordinationSessionReady = runBlocking {
      appGraph.coordinationRuntime.sessionStatus() == CoordinationSessionStatus.SESSION_READY
    }
    val decision = appGraph.readinessEvaluator.evaluate(
      snapshot.readinessInputs(
        accountMode = durable.accountMode,
        contactsFresh = durable.contactsFresh,
        approvalsReady = durable.approvalsReady,
        passingTestReceipt = durable.passingTestReceipt,
        coordinationAvailable = coordinationSessionReady,
        clockTrusted = durable.clockTrusted,
        resetSafetyClear = durable.resetSafetyClear,
      ),
    )
    val iosComposerReserved = runBlocking {
      appGraph.automationOrchestrationDao.activeAccount()?.let { account ->
        appGraph.automationOrchestrationDao.coordinationState(account.accountId)?.lastSafeCode
      }
    } == "IOS_COMPOSER_RESERVED"
    fun withComposerReservation(blockers: List<String>): List<String> =
      if (iosComposerReserved) blockers + "IOS_COMPOSER_RESERVED" else blockers
    val permanentPermissionIssue = snapshot.permanentPermissionDenial.readinessWireCode()
    return JSONObject()
      .put("platform", "android")
      .put(
        "test",
        gatePayload(
          "test",
          withComposerReservation(decision.testBlockers.map { it.name }),
          permanentPermissionIssue,
        ),
      )
      .put(
        "activation",
        gatePayload(
          "activation",
          withComposerReservation(decision.activationBlockers.map { it.name }),
          permanentPermissionIssue,
        ),
      )
      .put(
        "birthday",
        gatePayload(
          "birthday",
          withComposerReservation(decision.birthdayBlockers.map { it.name }),
          permanentPermissionIssue,
        ),
      )
      .put("lastCheckedAt", Instant.now().toString())
  }

  private fun gatePayload(
    gate: String,
    blockers: List<String>,
    permanentPermissionIssue: String?,
  ): JSONObject =
    if (blockers.isEmpty()) {
    JSONObject().put("kind", "allowed")
  } else {
    JSONObject()
      .put("kind", "blocked")
      .put(
        "issues",
        org.json.JSONArray().apply {
          blockers.distinct().forEach { blocker ->
            put(readinessIssuePayload(blocker, gate, permanentPermissionIssue))
          }
        },
      )
  }

  private fun eligibilityIssuePayload(reason: EligibilityReason) = JSONObject()
    .put("id", "eligibility-${reason.wireCode}")
    .put("code", reason.wireCode)
    .put("severity", "blocking")
    .put("blocks", blockedGates(reason))

  private fun blockedGates(reason: EligibilityReason): org.json.JSONArray {
    val gates = when (reason) {
      EligibilityReason.BACKGROUND_RESTRICTED,
      EligibilityReason.DOZE_EXEMPTION_MISSING,
      EligibilityReason.UNUSED_APP_RESTRICTIONS_UNSAFE,
      EligibilityReason.DATA_SAVER_RESTRICTED,
      EligibilityReason.LOW_POWER_STANDBY_UNSAFE,
      -> listOf("activation", "birthday")
      else -> listOf("test", "activation", "birthday")
    }
    return org.json.JSONArray(gates)
  }

  private fun readinessIssuePayload(
    internalCode: String,
    gate: String,
    permanentPermissionIssue: String?,
  ): JSONObject {
    val wireCode = if (internalCode == "SMS_PERMISSION_MISSING") {
      permanentPermissionIssue ?: READINESS_WIRE_CODES.getValue(internalCode)
    } else {
      READINESS_WIRE_CODES[internalCode] ?: "unknown-native-value"
    }
    val actionCode = TelephonyPermissionRemediationPolicy.actionCode(wireCode)
    return JSONObject()
      .put("id", "readiness-${wireCode}")
      .put("code", wireCode)
      .put("severity", "blocking")
      .put("blocks", org.json.JSONArray(listOf(gate)))
      .apply {
        lifecycleController.actionPayload(actionCode, currentRevision().toLongOrNull() ?: 0L)
          ?.let { put("action", it) }
      }
  }

  private fun accountPayload(): JSONObject = runBlocking {
    if (lifecycleController.lifecycleJournalUnreadable()) {
      return@runBlocking JSONObject()
        .put("kind", "cleanup-pending")
        .put("operation", "repair")
        .put(
          "issue",
          JSONObject()
            .put("id", "account-lifecycle-state-unavailable")
            .put("code", "coordination-unavailable")
            .put("severity", "blocking")
            .put("blocks", JSONArray(listOf("test", "activation", "birthday"))),
        )
    }
    val deletionReceiptBlocker = DeletionReceiptAccountPolicy.blockerCode(
      lifecycleController.deletionReceiptLookup(),
    )
    if (deletionReceiptBlocker != null) {
      return@runBlocking JSONObject()
        .put("kind", "cleanup-pending")
        .put("operation", "delete")
        .put(
          "issue",
          JSONObject()
            .put("id", "account-$deletionReceiptBlocker")
            .put("code", deletionReceiptBlocker)
            .put("severity", "blocking")
            .put("blocks", JSONArray(listOf("test", "activation", "birthday"))),
        )
    }
    val pendingLifecycle = lifecycleController.latestOperation()?.takeIf {
      it.action in setOf(
        "delete-account",
        "revoke-google-access",
        "disconnect-contacts",
        "sign-out-retain",
        "sign-out-wipe",
        "wipe-local-data",
      ) && it.state !in setOf("complete", "failed")
    }
    if (pendingLifecycle != null) {
      val operation = when (pendingLifecycle.action) {
        "delete-account" -> "delete"
        "revoke-google-access" -> "revoke"
        "disconnect-contacts" -> "disconnect"
        else -> "sign-out"
      }
      val code = if (pendingLifecycle.action == "delete-account") {
        "firebase-account-deleting"
      } else {
        "coordination-unavailable"
      }
      return@runBlocking JSONObject()
        .put("kind", "cleanup-pending")
        .put("operation", operation)
        .put(
          "issue",
          JSONObject()
            .put("id", "account-$code")
            .put("code", code)
            .put("severity", "blocking")
            .put("blocks", JSONArray(listOf("test", "activation", "birthday"))),
        )
    }
    val account = appGraph.peopleSyncDao.activeAccount()
      ?: return@runBlocking JSONObject()
        .put("kind", "signed-out")
        .put("retainedSetup", "none")
    if (account.state != AccountRecordState.ACTIVE || account.activeSlot != 1) {
      return@runBlocking JSONObject()
        .put("kind", "signed-out")
        .put("retainedSetup", "same-account-only")
    }
    if (!appGraph.identitySessionMatches(account)) {
      return@runBlocking reconnectRequiredAccountPayload()
    }
    val email = account.displayEmail
    if (email.isNullOrBlank()) {
      return@runBlocking reconnectRequiredAccountPayload()
    }
    JSONObject()
      .put("kind", "connected")
      .put("displayEmail", email)
      .put("sender", senderPayload())
  }

  private fun contactsAuthorizationRequiredPayload() = JSONObject()
    .put("kind", "authorization-required")
    .put("reason", "contacts-authorization-required")

  private fun automationPayload(snapshot: AndroidReadinessSnapshot): JSONObject {
    val readiness = readinessPayload(snapshot)
    val state = runBlocking {
      val account = appGraph.database.configurationDao().activeAccount()
      val control = appGraph.database.configurationDao().control()
      val configured = account?.let {
        appGraph.database.configurationDao().activeTemplate(it.accountId) != null &&
          appGraph.database.configurationDao().activeAutomationPolicy(it.accountId) != null &&
          appGraph.database.configurationDao().configuredRecipientCount(it.accountId) > 0
      } == true
      val mode = control?.accountMode?.let { runCatching { AccountMode.valueOf(it) }.getOrNull() }
      Triple(control?.automationDesired == true, configured, mode)
    }
    val effective = if (!state.second) {
      "not-configured"
    } else {
      when (state.third) {
        AccountMode.TEST_ONLY -> "test-only"
        AccountMode.PAUSED_REPAIR -> "paused-repair"
        AccountMode.AUTOMATION_ACTIVE -> if (
          readiness.getJSONObject("birthday").optString("kind") == "allowed"
        ) "active" else "action-required"
        AccountMode.STANDBY -> "standby"
        AccountMode.TRANSFER_PENDING -> "transfer-pending"
        AccountMode.DELETING -> "deleting"
        null -> "not-configured"
      }
    }
    return JSONObject()
      .put("platform", "android")
      .put("desired", if (state.first) "on" else "paused")
      .put("effective", effective)
      .put("readiness", readiness)
  }

  private fun contactsPayload(request: JSONObject): JSONObject? = when (request.optString("kind")) {
    "list" -> peopleListPayload(request)
    "detail" -> runBlocking {
      configurationController.contactDetail(request.optString("contactId"))
    }
    else -> null
  }

  private fun messagesPayload(request: JSONObject): JSONObject? = when (request.optString("kind")) {
    "editor" -> runBlocking { configurationController.messageEditor() }
    "next-composer-proposal" -> JSONObject().put("kind", "none")
    else -> null
  }

  private fun automationProjectionPayload(request: JSONObject): JSONObject? = when (
    request.optString("kind")
  ) {
    "policy-editor" -> if (request.keyNames() == setOf("kind")) {
      runBlocking { configurationController.policyEditor() }
    } else {
      null
    }
    "approval" -> runBlocking {
      configurationController.approvalProjection(request.optString("contactId"))
    }
    "birthday-job" -> runBlocking { lifecycleController.birthdayJobPayload(request) }
    "latest-test" -> runBlocking { configurationController.latestTestProjection() }
    "sender-transfer-operation" -> lifecycleController.senderTransferOperationProjectionPayload()
    else -> null
  }

  private fun activityProjectionPayload(
    request: JSONObject,
    snapshot: AndroidReadinessSnapshot,
  ): Any? = when (request.optString("kind")) {
    "list" -> runBlocking { lifecycleController.activityPayload(request) }?.also {
      attachActivityRecoveries(it, snapshot)
    }
    "issues" -> if (request.keyNames() == setOf("kind")) {
      activityIssuesPayload(snapshot)
    } else {
      null
    }
    else -> null
  }

  private fun activityIssueBlocksByCode(
    snapshot: AndroidReadinessSnapshot,
  ): LinkedHashMap<String, LinkedHashSet<String>> {
    val readiness = readinessPayload(snapshot)
    val blocksByCode = linkedMapOf<String, LinkedHashSet<String>>()
    listOf("test", "activation", "birthday").forEach { gate ->
      val issues = readiness.optJSONObject(gate)?.optJSONArray("issues") ?: return@forEach
      for (index in 0 until issues.length()) {
        val code = issues.optJSONObject(index)?.optString("code")
          ?.takeIf { it in SAFE_REASON_CODES }
          ?: continue
        blocksByCode.getOrPut(code) { linkedSetOf() }.add(gate)
      }
    }
    return blocksByCode
  }

  /**
   * Activity is historical, while recovery is a live capability. Project a
   * route only when the current native snapshot still exposes that repair.
   */
  private fun attachActivityRecoveries(
    page: JSONObject,
    snapshot: AndroidReadinessSnapshot,
  ) {
    val issueCodes = activityIssueBlocksByCode(snapshot).keys
    val automation = automationPayload(snapshot)
    val effective = automation.optString("effective")
    val items = page.optJSONArray("items") ?: return
    for (index in 0 until items.length()) {
      val item = items.optJSONObject(index) ?: continue
      val kind = item.optString("kind")
      val reason = item.optString("reason").takeIf { it.isNotBlank() }
      val route = AndroidActivityRecoveryPolicy.route(
        kind = kind,
        reason = reason,
        currentIssueCodes = issueCodes,
        automationEffective = effective,
      )
      route?.let {
        item.put("recovery", JSONObject().put("route", it))
      }
    }
  }

  private fun activityIssuesPayload(snapshot: AndroidReadinessSnapshot): JSONArray {
    val blocksByCode = activityIssueBlocksByCode(snapshot)
    val revision = currentRevision().toLongOrNull() ?: 0L
    return JSONArray().apply {
      blocksByCode.forEach { (code, blocks) ->
        put(
          JSONObject()
            .put("id", "readiness-$code")
            .put("code", code)
            .put("severity", "blocking")
            .put("blocks", JSONArray(blocks.toList()))
            .apply {
              lifecycleController.actionPayload(code, revision)?.let { put("action", it) }
            },
        )
      }
    }
  }

  private fun privacyProjectionPayload(request: JSONObject): JSONObject? = when (
    request.optString("kind")
  ) {
    "inventory" -> if (request.keyNames() == setOf("kind")) {
      runBlocking { lifecycleController.inventoryPayload() }
    } else {
      null
    }
    "public-resources" -> if (request.keyNames() == setOf("kind")) {
      lifecycleController.publicResourcesPayload()
    } else {
      null
    }
    "operation" -> if (request.keyNames() == setOf("kind", "operationId")) {
      lifecycleController.operationPayload(request.optString("operationId"))
    } else {
      null
    }
    "current-operation" -> if (request.keyNames() == setOf("kind")) {
      lifecycleController.currentOperationPayload()
    } else {
      null
    }
    "latest-deletion-receipt" -> if (request.keyNames() == setOf("kind")) {
      lifecycleController.latestDeletionReceiptPayload()
    } else {
      null
    }
    else -> null
  }

  private fun routeProjectionPayload(request: JSONObject): JSONObject? {
    if (request.keyNames().isNotEmpty()) return null
    val routeId = routeStore.consumeRouteId() ?: return JSONObject().put("kind", "none")
    return JSONObject()
      .put("kind", "attention")
      .put("routeId", routeId)
      .put("source", "attention")
  }

  private fun notificationProjectionPayload(request: JSONObject): JSONObject? {
    if (request.keyNames().isNotEmpty()) return null
    return JSONObject().put(
      "kind",
      NotificationPermissionActivityResultOwner.status(reactApplicationContext),
    )
  }

  private fun handleRequestNotificationPermission(request: JSONObject): Any {
    if (request.keyNames().isNotEmpty()) return errorResponse("NATIVE_REQUEST_INVALID")
    val result = runBlocking {
      ForegroundActivityRegistry.currentNotificationPermissionLauncher()?.request()
        ?: NotificationPermissionResult.UNAVAILABLE
    }
    return successResponse(
      JSONObject().put(
        "kind",
        when (result) {
          NotificationPermissionResult.GRANTED -> "granted"
          NotificationPermissionResult.DENIED -> "denied"
          NotificationPermissionResult.SETTINGS_REQUIRED -> "settings-required"
          NotificationPermissionResult.UNAVAILABLE -> "cancelled"
        },
      ),
    )
  }

  private fun handleOpenNotificationSettings(request: JSONObject): Any {
    if (request.keyNames().isNotEmpty()) return errorResponse("NATIVE_REQUEST_INVALID")
    val activity = ForegroundActivityRegistry.current()
      ?: return successResponse(JSONObject().put("kind", "cancelled"))
    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
      .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, reactApplicationContext.packageName)
    val opened = runCatching {
      activity.startActivity(intent)
      true
    }.getOrDefault(false)
    return successResponse(JSONObject().put("kind", if (opened) "opened" else "cancelled"))
  }

  private fun handlePreviewDiagnostics(request: JSONObject): Any {
    if (request.keyNames().isNotEmpty()) return errorResponse("NATIVE_REQUEST_INVALID")
    return successResponse(runBlocking { lifecycleController.diagnosticsPayload() })
  }

  private fun handleShareDiagnostics(request: JSONObject, expectedRevision: String?): Any {
    val revision = expectedRevision.configurationRevisionOrNull()
      ?: return errorResponse("NATIVE_REQUEST_INVALID")
    if (
      request.keyNames() != setOf("expectedRevision") ||
      request.optString("expectedRevision") != revision.toString()
    ) return errorResponse("NATIVE_REQUEST_INVALID")
    val result = runBlocking { lifecycleController.shareDiagnostics(revision) }
      ?: return staleRevisionResponse()
    return successResponse(result)
  }

  private fun handleNativeAction(request: JSONObject, expectedRevision: String?): Any {
    val revision = expectedRevision.configurationRevisionOrNull()
      ?: return errorResponse("NATIVE_REQUEST_INVALID")
    if (
      request.keyNames() != setOf("handle", "expectedRevision") ||
      request.optString("expectedRevision") != revision.toString()
    ) return errorResponse("NATIVE_REQUEST_INVALID")
    val result = runBlocking {
      lifecycleController.performNativeAction(request.optString("handle"), revision)
    } ?: return staleRevisionResponse()
    return successResponse(result)
  }

  private fun handlePreparePrivacyAction(
    request: JSONObject,
    expectedRevision: String?,
  ): Any {
    val revision = expectedRevision.configurationRevisionOrNull()
      ?: return errorResponse("NATIVE_REQUEST_INVALID")
    val unresolved = runBlocking {
      appGraph.automationOrchestrationDao.unresolvedPermitCount()
    }
    return handleConfigurationOutcome(
      runBlocking {
        lifecycleController.preparePrivacyAction(
          request,
          revision,
          preissuedPermitMayFinish = unresolved > 0,
        )
      },
    )
  }

  private fun handlePrepareSenderTransfer(
    request: JSONObject,
    expectedRevision: String?,
  ): Any {
    val revision = expectedRevision.configurationRevisionOrNull()
      ?: return errorResponse("NATIVE_REQUEST_INVALID")
    val unresolved = runBlocking { appGraph.automationOrchestrationDao.unresolvedPermitCount() }
    return handleConfigurationOutcome(
      runBlocking {
        lifecycleController.prepareSenderTransfer(
          request,
          revision,
          preissuedPermitMayFinish = unresolved > 0,
        )
      },
    )
  }

  private fun handleBeginSenderTransfer(
    request: JSONObject,
    expectedRevision: String?,
  ): Any {
    val revision = expectedRevision.configurationRevisionOrNull()
      ?: return errorResponse("NATIVE_REQUEST_INVALID")
    val confirmation = runBlocking { lifecycleController.beginSenderTransfer(request, revision) }
    val plan = when (confirmation) {
      is SenderTransferConfirmationOutcome.Rejected -> {
        return handleConfigurationOutcome(confirmation.outcome)
      }
      is SenderTransferConfirmationOutcome.Ready -> confirmation.plan
    }
    return executeBeginSenderTransfer(plan)
  }

  private fun executeBeginSenderTransfer(plan: SenderTransferPlan): Any {
    val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
      ?.takeIf { it.accountId == plan.accountId }
      ?: return transferPending(plan, "account-reconnect-required")
    if (!recentExactGoogleReauthentication(account)) {
      return transferPending(plan, "account-reconnect-required")
    }
    val binding = transferBinding(plan)
    val paused = runBlocking {
      appGraph.automationCoordinationPort.changeAccountMode(
        com.yashsomani.birthdayautopilot.automation.orchestration.AccountModeSpec(
          binding = binding,
          action = AccountModeAction.PAUSE_FOR_REPAIR,
        ),
      )
    }
    if (
      paused !is OrchestrationCall.Authoritative ||
      (paused.value as? AccountModeOutcome.Changed)?.mode != ServerAccountMode.PAUSED_REPAIR
    ) return transferPending(plan, "coordination-unavailable")

    return when (val result = runBlocking {
      appGraph.automationCoordinationPort.beginSenderTransfer(
        SenderTransferSpec(binding, plan.targetInstallationId),
      )
    }) {
      is OrchestrationCall.Authoritative -> when (val outcome = result.value) {
        is SenderTransferOutcome.Started -> applyTransferStarted(plan, outcome.binding)
        is SenderTransferOutcome.Completed -> transferPending(plan, "internal-contract-invalid")
        is SenderTransferOutcome.Refused -> when (outcome.reason) {
          com.yashsomani.birthdayautopilot.coordination.CoordinationServerReason.DELETION_SUPPRESSED ->
            transferPending(plan, "firebase-account-deleting")
          else -> transferPending(plan, "transfer-pending")
        }
      }
      is OrchestrationCall.Unavailable -> reconcileAmbiguousTransferBegin(plan)
    }
  }

  private fun reconcileAmbiguousTransferBegin(plan: SenderTransferPlan): Any {
    val refreshed = runBlocking {
      appGraph.automationOrchestrator.refreshRegistrationForLifecycle()
    } ?: return transferPending(plan, "coordination-unavailable")
    val local = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
    val coordination = runBlocking {
      appGraph.automationOrchestrationDao.coordinationState(plan.accountId)
    }
    return if (
      local?.installationId == plan.targetInstallationId &&
      local.state == InstallationRecordState.STANDBY &&
      local.accountMode == AccountMode.TRANSFER_PENDING &&
      coordination?.mode == AccountMode.TRANSFER_PENDING &&
      coordination.activeInstallationId == plan.activeInstallationId &&
      coordination.senderEpoch == plan.senderEpoch &&
      coordination.resetGeneration == plan.resetGeneration &&
      coordination.transferDrainUntilMillis != null &&
      coordination.lastSuccessfulCoordinationMillis != null
    ) {
      val operation = lifecycleController.markSenderTransferDraining(
        plan,
        coordination.transferDrainUntilMillis,
        coordination.lastSuccessfulCoordinationMillis,
        SystemClock.elapsedRealtime(),
        currentBootCount(),
      )
      emitInvalidation(listOf("account", "automation", "home", "activity", "privacy"))
      successResponse(lifecycleOperationPayload(operation))
    } else if (
      refreshed.installationId == plan.targetInstallationId &&
      transferSourceMatches(plan, local, coordination) &&
      coordination?.mode in setOf(
        AccountMode.TEST_ONLY,
        AccountMode.PAUSED_REPAIR,
        AccountMode.AUTOMATION_ACTIVE,
      )
    ) {
      // The authoritative registration proves that begin did not establish TRANSFER_PENDING.
      // A later explicit resume may reuse the same immutable operation; this call never retries.
      transferPending(plan, "transfer-pending")
    } else {
      transferFailed(plan.operationId)
    }
  }

  private fun applyTransferStarted(
    plan: SenderTransferPlan,
    binding: com.yashsomani.birthdayautopilot.coordination.ServerBinding,
  ): Any {
    val applied = runBlocking {
      appGraph.submissionGate.withExclusiveBoundary {
        appGraph.automationOrchestrationDao.applyRemoteLifecycleMode(
          accountId = plan.accountId,
          localInstallationId = plan.targetInstallationId,
          mode = AccountMode.TRANSFER_PENDING,
          activeInstallationId = binding.activeInstallationId,
          senderEpoch = binding.senderEpoch,
          resetGeneration = binding.resetGeneration,
          ownerLeaseUntilMillis = binding.ownerLeaseUntilMillis,
          nextArmNotBeforeMillis = binding.nextArmNotBeforeMillis,
          latestIssuedSubmitNotAfterMillis = binding.latestIssuedSubmitNotAfterMillis,
          birthdayAutomationNotBeforeMillis = binding.birthdayAutomationNotBeforeMillis,
          transferTargetInstallationId = binding.transferTargetInstallationId,
          transferDrainUntilMillis = binding.transferDrainUntilMillis,
          deletionDrainUntilMillis = binding.deletionDrainUntilMillis,
          serverObservedAtMillis = binding.serverObservedAtMillis,
          deviceWallMillis = System.currentTimeMillis(),
        )
      }
    }
    val drain = binding.transferDrainUntilMillis
    if (!applied || drain == null) return transferPending(plan, "internal-contract-invalid")
    val operation = lifecycleController.markSenderTransferDraining(
      plan,
      drain,
      binding.serverObservedAtMillis,
      SystemClock.elapsedRealtime(),
      currentBootCount(),
    )
    emitInvalidation(listOf("account", "automation", "home", "activity", "privacy"))
    AutomationScheduler.scheduleNetworkAttempt(
      reactApplicationContext,
      null,
      drain + 1,
    )
    return successResponse(lifecycleOperationPayload(operation))
  }

  private fun handleCompleteSenderTransfer(request: JSONObject): Any {
    if (request.keyNames() != setOf("operationId")) {
      return errorResponse("NATIVE_REQUEST_INVALID")
    }
    val operationId = request.optString("operationId")
    val current = lifecycleController.latestOperation()
      ?.takeIf { it.id == operationId && it.action == "sender-transfer" }
      ?: return errorResponse("NATIVE_REQUEST_INVALID")
    if (current.state in setOf("complete", "failed")) {
      return successResponse(lifecycleOperationPayload(current))
    }
    val identityPlan = runBlocking {
      lifecycleController.senderTransferPlanIdentity(operationId)
    } ?: return transferFailed(operationId)
    var local = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
    var coordination = runBlocking {
      appGraph.automationOrchestrationDao.activeAccount()?.let {
        appGraph.automationOrchestrationDao.coordinationState(it.accountId)
      }
    }
    if (transferCompletionObserved(identityPlan, local, coordination)) {
      val completed = lifecycleController.completeSenderTransferOperation(operationId)
        ?: return errorResponse("NATIVE_INTENT_FAILURE")
      return successResponse(lifecycleOperationPayload(completed))
    }

    if (SenderTransferRecoveryPolicy.requiresAuthoritativeRegistration(current.state)) {
      val refreshed = runBlocking {
        appGraph.automationOrchestrator.refreshRegistrationForLifecycle()
      } ?: return successResponse(lifecycleOperationPayload(current))
      local = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
      coordination = runBlocking {
        appGraph.automationOrchestrationDao.coordinationState(identityPlan.accountId)
      }
      if (transferCompletionObserved(identityPlan, local, coordination)) {
        val completed = lifecycleController.completeSenderTransferOperation(operationId)
          ?: return errorResponse("NATIVE_INTENT_FAILURE")
        return successResponse(lifecycleOperationPayload(completed))
      }
      if (
        refreshed.installationId != identityPlan.targetInstallationId ||
        !transferPendingMatches(identityPlan, local, coordination)
      ) return successResponse(lifecycleOperationPayload(current))
      lifecycleController.markSenderTransferDraining(
        identityPlan,
        checkNotNull(coordination?.transferDrainUntilMillis),
        checkNotNull(coordination?.lastSuccessfulCoordinationMillis),
        SystemClock.elapsedRealtime(),
        currentBootCount(),
      )
    }
    if (lifecycleController.latestOperation()?.state != "remote-draining") {
      return successResponse(
        lifecycleOperationPayload(
          lifecycleController.latestOperation() ?: return errorResponse("NATIVE_INTENT_FAILURE"),
        ),
      )
    }
    val plan = runBlocking { lifecycleController.recoverSenderTransferPlan() }
      ?: return transferFailed(operationId)
    val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
      ?.takeIf { it.accountId == plan.accountId }
      ?: return transferPending(plan, "account-reconnect-required")
    if (!recentExactGoogleReauthentication(account)) {
      return transferPending(plan, "account-reconnect-required")
    }
    // Write-ahead ambiguity marker: a kill at any point after this durable transition must query
    // registration before another completion mutation can be dispatched.
    lifecycleController.markSenderTransferPending(plan, "coordination-unavailable")
    return when (val result = runBlocking {
      appGraph.automationCoordinationPort.completeSenderTransfer(
        SenderTransferSpec(transferBinding(plan), plan.targetInstallationId),
      )
    }) {
      is OrchestrationCall.Unavailable -> {
        reconcileAmbiguousTransferCompletion(plan)
      }
      is OrchestrationCall.Authoritative -> when (val outcome = result.value) {
        is SenderTransferOutcome.Completed -> completeAuthoritativeTransfer(plan, outcome)
        is SenderTransferOutcome.Started -> transferPending(plan, "internal-contract-invalid")
        is SenderTransferOutcome.Refused -> when (outcome.reason) {
          com.yashsomani.birthdayautopilot.coordination.CoordinationServerReason.DRAIN_NOT_COMPLETE -> {
            val draining = lifecycleController.markSenderTransferDraining(
              plan,
              coordination?.transferDrainUntilMillis
                ?: return transferPending(plan, "internal-contract-invalid"),
              coordination.lastSuccessfulCoordinationMillis
                ?: return transferPending(plan, "internal-contract-invalid"),
              SystemClock.elapsedRealtime(),
              currentBootCount(),
            )
            successResponse(lifecycleOperationPayload(draining))
          }
          com.yashsomani.birthdayautopilot.coordination.CoordinationServerReason.DELETION_SUPPRESSED ->
            transferPending(plan, "firebase-account-deleting")
          else -> transferPending(plan, "transfer-pending")
        }
      }
    }
  }

  private fun reconcileAmbiguousTransferCompletion(plan: SenderTransferPlan): Any {
    val refreshed = runBlocking {
      appGraph.automationOrchestrator.refreshRegistrationForLifecycle()
    } ?: return successResponse(
      lifecycleOperationPayload(
        lifecycleController.latestOperation() ?: return errorResponse("NATIVE_INTENT_FAILURE"),
      ),
    )
    val local = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
    val coordination = runBlocking {
      appGraph.automationOrchestrationDao.coordinationState(plan.accountId)
    }
    return if (transferCompletionObserved(plan, local, coordination)) {
      val completed = lifecycleController.completeSenderTransferOperation(plan.operationId)
        ?: return errorResponse("NATIVE_INTENT_FAILURE")
      emitInvalidation(listOf("account", "automation", "home", "activity", "privacy"))
      successResponse(lifecycleOperationPayload(completed))
    } else if (
      refreshed.installationId == plan.targetInstallationId &&
      transferPendingMatches(plan, local, coordination)
    ) {
      val draining = lifecycleController.markSenderTransferDraining(
        plan,
        checkNotNull(coordination?.transferDrainUntilMillis),
        checkNotNull(coordination?.lastSuccessfulCoordinationMillis),
        SystemClock.elapsedRealtime(),
        currentBootCount(),
      )
      successResponse(lifecycleOperationPayload(draining))
    } else {
      transferFailed(plan.operationId)
    }
  }

  private fun completeAuthoritativeTransfer(
    plan: SenderTransferPlan,
    outcome: SenderTransferOutcome.Completed,
  ): Any {
    if (!runBlocking { appGraph.automationOrchestrator.applyCompletedSenderTransfer(outcome) }) {
      return transferPending(plan, "internal-contract-invalid")
    }
    val completed = lifecycleController.completeSenderTransferOperation(plan.operationId)
      ?: return errorResponse("NATIVE_INTENT_FAILURE")
    emitInvalidation(listOf("account", "automation", "home", "activity", "privacy"))
    AutomationScheduler.enqueueImmediateLocal(reactApplicationContext, "FOREGROUND")
    return successResponse(lifecycleOperationPayload(completed))
  }

  private fun handleResumeLifecycleOperation(request: JSONObject): Any {
    if (request.keyNames() != setOf("operationId")) {
      return errorResponse("NATIVE_REQUEST_INVALID")
    }
    val operationId = request.optString("operationId")
    val current = lifecycleController.latestOperation()
      ?.takeIf { it.id == operationId }
      ?: return errorResponse("NATIVE_REQUEST_INVALID")
    if (current.state in setOf("complete", "failed")) {
      return successResponse(lifecycleOperationPayload(current))
    }
    if (current.action == "sender-transfer") {
      val plan = runBlocking {
        lifecycleController.senderTransferPlanIdentity(operationId)
      } ?: return transferFailed(operationId)
      val refreshed = runBlocking {
        appGraph.automationOrchestrator.refreshRegistrationForLifecycle()
      } ?: return successResponse(lifecycleOperationPayload(current))
      val local = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
      val account = runBlocking { appGraph.automationOrchestrationDao.activeAccount() }
      val coordination = account?.let {
        runBlocking { appGraph.automationOrchestrationDao.coordinationState(it.accountId) }
      }
      if (transferCompletionObserved(plan, local, coordination)) {
        val completed = lifecycleController.completeSenderTransferOperation(operationId)
          ?: return errorResponse("NATIVE_INTENT_FAILURE")
        return successResponse(lifecycleOperationPayload(completed))
      }
      if (
        refreshed.installationId != plan.targetInstallationId ||
        !transferSourceMatches(plan, local, coordination)
      ) return transferFailed(operationId)
      if (
        transferPendingMatches(plan, local, coordination)
      ) {
        if (current.state != "remote-draining") {
          val draining = lifecycleController.markSenderTransferDraining(
            plan,
            checkNotNull(coordination?.transferDrainUntilMillis),
            checkNotNull(coordination?.lastSuccessfulCoordinationMillis),
            SystemClock.elapsedRealtime(),
            currentBootCount(),
          )
          return successResponse(lifecycleOperationPayload(draining))
        }
        return handleCompleteSenderTransfer(JSONObject().put("operationId", operationId))
      }
      // Registration authoritatively showed that the ambiguous begin did not establish this
      // target. Reusing the same persisted plan is a reconciled retry, not a blind mutation.
      return if (
        current.remoteDrainUntilMillis == null &&
        coordination?.mode in setOf(
          AccountMode.TEST_ONLY,
          AccountMode.PAUSED_REPAIR,
          AccountMode.AUTOMATION_ACTIVE,
        )
      ) {
        executeBeginSenderTransfer(plan)
      } else {
        transferFailed(operationId)
      }
    }
    val plan = lifecycleController.privacyPlanForOperation(operationId)
      ?: return successResponse(lifecycleOperationPayload(current))
    if (plan.deletionLocalWipeFallback && !current.localDataErased) {
      return completeDeletionFailureLocalWipe(plan)
    }
    if (current.localWipeStarted) {
      val installation = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
        ?: return successResponse(lifecycleOperationPayload(current))
      return if (plan.action == "delete-account") {
        completeAcceptedAccountDeletionLocalWipe(plan, installation)
      } else {
        completeMarkedDestructiveWipe(plan, installation)
      }
    }
    if (current.authoritativeRecoveryKind == "contact-reset") {
      val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
        ?: return privacyPending(plan, "account-reconnect-required")
      val revision = runBlocking { appGraph.automationOrchestrationDao.control()?.revision }
        ?: return successResponse(
          lifecycleController.operationPayload(
            lifecycleController.markLocalCleanupPending(plan, "internal-contract-invalid"),
          ),
        )
      if (prepareLifecyclePauseLocally(revision) == null) {
        return successResponse(
          lifecycleController.operationPayload(
            lifecycleController.markLocalCleanupPending(plan, "internal-contract-invalid"),
          ),
        )
      }
      return completeContactDerivedReset(plan, account.accountId)
    }
    if (current.authoritativeRecoveryKind == "sender-release") {
      val installation = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
        ?: return privacyPending(plan, "account-reconnect-required")
      return completeAuthoritativeSenderReleaseLocalWipe(plan, installation)
    }
    if (
      plan.action == "sign-out-retain" &&
      current.state in setOf("verifying", "local-wiping")
    ) {
      val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
        ?: return privacyPending(plan, "account-reconnect-required")
      if (account.state == AccountRecordState.RETAINED_SIGNED_OUT) {
        val completed = runBlocking {
          lifecycleController.retainSignedOutAccount(plan, account.accountId)
        }
        return successResponse(lifecycleController.operationPayload(completed))
      }
      return if (runBlocking {
          appGraph.googleIdentityCoordinator.completeSignOutAfterSafetyShutdown()
        }
      ) {
        val completed = runBlocking {
          lifecycleController.retainSignedOutAccount(plan, account.accountId)
        }
        successResponse(lifecycleController.operationPayload(completed))
      } else {
        successResponse(lifecycleController.operationPayload(current))
      }
    }
    if (!plan.requiresPause) {
      val operation = runBlocking { lifecycleController.executeLocalPrivacyAction(plan) }
      emitInvalidation(listOf("activity", "privacy", "home"))
      return successResponse(lifecycleOperationPayload(operation))
    }
    if (plan.action == "clear-gemini-templates") {
      val revision = runBlocking { appGraph.automationOrchestrationDao.control()?.revision }
        ?: return privacyPending(plan, "internal-contract-invalid")
      val pause = pauseLifecycleAccount(revision)
      if (!pause.localPrepared) return privacyPending(plan, "internal-contract-invalid")
      appGraph.geminiSuggestionGateway.clearProvenance()
      val operation = runBlocking { lifecycleController.executeLocalPrivacyAction(plan) }
      return successResponse(lifecycleController.operationPayload(operation))
    }
    if (plan.action in setOf("disconnect-contacts", "revoke-google-access")) {
      val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
        ?: return privacyPending(plan, "account-reconnect-required")
      if (!current.localDataErased) {
        val revision = runBlocking { appGraph.automationOrchestrationDao.control()?.revision }
          ?: return privacyPending(plan, "internal-contract-invalid")
        if (prepareLifecyclePauseLocally(revision) == null) {
          return privacyPending(plan, "internal-contract-invalid")
        }
        val local = runBlocking {
          lifecycleController.purgeContactDerivedState(plan, account.accountId)
        }
        if (!local.localDataErased) {
          return successResponse(lifecycleController.operationPayload(local))
        }
      }
      val afterLocal = lifecycleController.latestOperation()?.takeIf { it.id == plan.operationId }
        ?: return privacyPending(plan, "internal-contract-invalid")
      if (afterLocal.state != "verifying") {
        val revision = runBlocking { appGraph.automationOrchestrationDao.control()?.revision }
          ?: return privacyPending(plan, "internal-contract-invalid")
        val pause = pauseLifecycleAccount(revision)
        if (!pause.localPrepared) return privacyPending(plan, "internal-contract-invalid")
        if (!pause.serverPaused) return privacyPending(plan, "coordination-unavailable")
      }
      return startOrReplayContactDerivedReset(plan, account.accountId)
    }
    if (plan.action in setOf("sign-out-wipe", "wipe-local-data")) {
      if (current.localDataErased) {
        return successResponse(lifecycleController.operationPayload(current))
      }
      val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
        ?: return privacyPending(plan, "account-reconnect-required")
      val installation = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
        ?: return privacyPending(plan, "account-reconnect-required")
      return completeLocalFirstDestructiveWipe(plan, account, installation)
    }
    if (plan.action == "sign-out-retain") {
      val revision = runBlocking { appGraph.automationOrchestrationDao.control()?.revision }
        ?: return privacyPending(plan, "internal-contract-invalid")
      val pause = pauseLifecycleAccount(revision)
      if (!pause.localPrepared || !pause.serverPaused) {
        return privacyPending(plan, "coordination-unavailable")
      }
      val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
        ?: return privacyPending(plan, "account-reconnect-required")
      val installation = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
        ?: return privacyPending(plan, "account-reconnect-required")
      return if (retireLocalCallbacks(installation)) {
        completeRetainedSignOut(plan, account.accountId)
      } else {
        privacyPending(plan, "coordination-unavailable")
      }
    }
    if (plan.action != "delete-account") {
      return privacyPending(plan, "internal-contract-invalid")
    }
    if (current.state == "remote-draining") {
      val installation = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
        ?: return successResponse(lifecycleOperationPayload(current))
      return continueAcceptedAccountDeletion(plan, installation, current)
    }
    val refreshed = runBlocking {
      appGraph.automationOrchestrator.refreshRegistrationForLifecycle()
    } ?: return privacyPending(plan, "coordination-unavailable")
    val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
      ?: return privacyPending(plan, "account-reconnect-required")
    var installation = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
      ?: return privacyPending(plan, "account-reconnect-required")
    val coordination = runBlocking {
      appGraph.automationOrchestrationDao.coordinationState(account.accountId)
    } ?: return privacyPending(plan, "coordination-unavailable")
    if (refreshed.installationId != installation.installationId) {
      return privacyPending(plan, "internal-contract-invalid")
    }
    if (coordination.mode != AccountMode.DELETING) {
      val revision = runBlocking { appGraph.automationOrchestrationDao.control()?.revision }
        ?: return privacyPending(plan, "internal-contract-invalid")
      val pause = pauseLifecycleAccount(revision)
      if (!pause.localPrepared) return privacyPending(plan, "internal-contract-invalid")
      if (!pause.serverPaused) return privacyPending(plan, "coordination-unavailable")
      installation = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
        ?: return privacyPending(plan, "account-reconnect-required")
    }
    return startOrReplayAccountDeletion(plan, account.accountId, installation)
  }

  private fun transferPending(plan: SenderTransferPlan, reason: String): Any {
    val operation = lifecycleController.markSenderTransferPending(plan, reason)
    return successResponse(lifecycleOperationPayload(operation))
  }

  private fun transferFailed(operationId: String): Any {
    val operation = lifecycleController.failSenderTransferOperation(operationId)
      ?: return errorResponse("NATIVE_INTENT_FAILURE")
    emitInvalidation(listOf("account", "automation", "home", "activity", "privacy"))
    return successResponse(lifecycleOperationPayload(operation))
  }

  private fun transferSourceMatches(
    plan: SenderTransferPlan,
    local: InstallationBindingEntity?,
    coordination: CoordinationStateEntity?,
  ): Boolean =
    local?.installationId == plan.targetInstallationId &&
      local.state == InstallationRecordState.STANDBY &&
      coordination?.activeInstallationId == plan.activeInstallationId &&
      coordination.senderEpoch == plan.senderEpoch &&
      coordination.resetGeneration == plan.resetGeneration

  private fun transferPendingMatches(
    plan: SenderTransferPlan,
    local: InstallationBindingEntity?,
    coordination: CoordinationStateEntity?,
  ): Boolean =
    transferSourceMatches(plan, local, coordination) &&
      local?.accountMode == AccountMode.TRANSFER_PENDING &&
      coordination?.mode == AccountMode.TRANSFER_PENDING &&
      coordination.transferDrainUntilMillis != null &&
      coordination.lastSuccessfulCoordinationMillis != null

  private fun transferCompletionObserved(
    plan: SenderTransferPlan,
    local: InstallationBindingEntity?,
    coordination: CoordinationStateEntity?,
  ): Boolean =
    plan.senderEpoch < Long.MAX_VALUE &&
      local?.installationId == plan.targetInstallationId &&
      local.state == InstallationRecordState.ACTIVE &&
      local.accountMode == AccountMode.TEST_ONLY &&
      local.senderEpoch == plan.senderEpoch + 1 &&
      local.resetGeneration == plan.resetGeneration &&
      coordination?.mode == AccountMode.TEST_ONLY &&
      coordination.activeInstallationId == plan.targetInstallationId &&
      coordination.senderEpoch == local.senderEpoch &&
      coordination.resetGeneration == plan.resetGeneration

  private fun transferBinding(plan: SenderTransferPlan) = CoordinationBindingSpec(
    ledgerGeneration = LEDGER_GENERATION,
    installationId = plan.activeInstallationId,
    senderEpoch = plan.senderEpoch,
    resetGeneration = plan.resetGeneration,
    appBuildNumber = BuildConfig.VERSION_CODE,
    policyVersion = COORDINATION_POLICY_VERSION,
    distributionChannel = currentDistributionChannel(),
  )

  private fun recentExactGoogleReauthentication(
    account: com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity,
  ): Boolean = when (runBlocking { appGraph.googleIdentityCoordinator.reauthenticateExactAccount() }) {
    is IdentityOutcome.SignedIn -> appGraph.identitySessionMatches(account)
    is IdentityOutcome.Failed -> false
  }

  private fun lifecycleOperationPayload(
    operation: com.yashsomani.birthdayautopilot.lifecycle.DurablePrivacyOperation,
  ): JSONObject = if (operation.action == "sender-transfer") {
    lifecycleController.senderTransferOperationPayload(operation)
  } else {
    lifecycleController.operationPayload(operation)
  }

  private fun currentBootCount(): Int = runCatching {
    Settings.Global.getInt(reactApplicationContext.contentResolver, Settings.Global.BOOT_COUNT)
  }.getOrDefault(0).coerceAtLeast(0)

  private fun handleRepairLifecycleState(request: JSONObject): Any {
    val result = handleRepairLifecycleStateInternal(request)
    if (
      lifecycleController.lifecycleJournalUnreadable() &&
      !appGraph.clearLifecycleRepairIdentitySession()
    ) return conflictResponse("account-reconnect-required")
    return result
  }

  private fun handleRepairLifecycleStateInternal(request: JSONObject): Any {
    if (
      request.keyNames() != setOf("kind") ||
      !lifecycleController.lifecycleJournalUnreadable()
    ) return errorResponse("NATIVE_REQUEST_INVALID")
    val action = request.optString("kind")
    if (action !in setOf(
        "disconnect-contacts",
        "revoke-google-access",
        "sign-out-wipe",
        "wipe-local-data",
      )) return errorResponse("NATIVE_REQUEST_INVALID")
    val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
      ?.takeIf(appGraph::consumeLifecycleRepairIdentitySession)
      ?: return conflictResponse("account-reconnect-required")
    val installation = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
      ?.takeIf { it.accountId == account.accountId }
      ?: return conflictResponse("coordination-unavailable")
    val status = when (val call = runBlocking {
      appGraph.automationCoordinationPort.coordinationLifecycleStatus()
    }) {
      is OrchestrationCall.Unavailable -> return conflictResponse("coordination-unavailable")
      is OrchestrationCall.Authoritative -> call.value
    }
    val repaired = when (status) {
      is CoordinationLifecycleStatusOutcome.AndroidState -> {
        val completion = status.latestCompletion as? CoordinationCompletion.ContactDerivedReset
          ?: return conflictResponse("coordination-unavailable")
        val state = status.state
        if (
          action !in setOf("disconnect-contacts", "revoke-google-access") ||
          !completion.androidStateExisted ||
          state.mode != ServerAccountMode.PAUSED_REPAIR ||
          state.activeInstallationId != installation.installationId ||
          completion.senderEpochAfter != state.senderEpoch ||
          completion.resetGenerationAfter != state.resetGeneration ||
          completion.birthdayAutomationNotBeforeMillis !=
            state.birthdayAutomationNotBeforeMillis
        ) return conflictResponse("coordination-unavailable")
        runBlocking {
          lifecycleController.repairUnreadableAfterAuthoritativeReset(action)
        }
      }
      is CoordinationLifecycleStatusOutcome.NoAndroidState -> {
        val completion = status.latestCompletion as? CoordinationCompletion.SenderRelease
          ?: return conflictResponse("coordination-unavailable")
        val senderEpoch = installation.senderEpoch
          ?: return conflictResponse("coordination-unavailable")
        if (
          action !in setOf("sign-out-wipe", "wipe-local-data") ||
          installation.state != InstallationRecordState.ACTIVE ||
          senderEpoch == Long.MAX_VALUE ||
          completion.senderEpochAfter != senderEpoch + 1 ||
          completion.resetGenerationAfter != installation.resetGeneration
        ) return conflictResponse("coordination-unavailable")
        runBlocking {
          lifecycleController.repairUnreadableAfterAuthoritativeRelease(
            action,
            installation.installationId,
            senderEpoch,
            installation.resetGeneration,
          )
        }
      }
      is CoordinationLifecycleStatusOutcome.AccountDeletionInProgress ->
        return conflictResponse("firebase-account-deleting")
      is CoordinationLifecycleStatusOutcome.OperationInProgress,
      is CoordinationLifecycleStatusOutcome.SafetyStatusUnavailable,
      -> return conflictResponse("coordination-unavailable")
    } ?: return conflictResponse("coordination-unavailable")
    emitInvalidation(PROJECTION_AREAS.toList())
    return handleResumeLifecycleOperation(JSONObject().put("operationId", repaired.id))
  }

  private fun lifecycleRepairIdentitySessionReady(): Boolean {
    if (!lifecycleController.lifecycleJournalUnreadable()) return false
    val account = runCatching { runBlocking { appGraph.peopleSyncDao.activeAccount() } }.getOrNull()
      ?: return false
    return appGraph.lifecycleRepairIdentitySessionMatches(account)
  }

  private fun handleCheckAccountDeletionStatus(request: JSONObject): Any {
    if (request.keyNames().isNotEmpty()) return errorResponse("NATIVE_REQUEST_INVALID")
    val receiptId = when (val lookup = lifecycleController.deletionReceiptLookup()) {
      DeletionReceiptLookup.None -> {
        val interruptedId = DeletionReceiptRecoveryPolicy.interruptedOperationId(
          lookup,
          lifecycleController.latestOperation(),
        ) ?: return successResponse(lifecycleController.latestDeletionReceiptPayload())
        handleResumeLifecycleOperation(JSONObject().put("operationId", interruptedId))
        return when (lifecycleController.deletionReceiptLookup()) {
          DeletionReceiptLookup.None -> successResponse(
            JSONObject()
              .put("kind", "unavailable")
              .put("reason", "coordination-unavailable"),
          )
          else -> successResponse(lifecycleController.latestDeletionReceiptPayload())
        }
      }
      DeletionReceiptLookup.Unavailable -> return successResponse(
        JSONObject()
          .put("kind", "unavailable")
          .put("reason", "coordination-unavailable"),
      )
      is DeletionReceiptLookup.Present -> when (lookup.receipt.state) {
        DurableDeletionReceipt.State.COMPLETED ->
          return successResponse(lifecycleController.latestDeletionReceiptPayload())
        DurableDeletionReceipt.State.PENDING -> lookup.receipt.receiptId
      }
    }
    return when (val call = runBlocking {
      appGraph.automationCoordinationPort.accountDeletionReceipt(receiptId)
    }) {
      is OrchestrationCall.Unavailable -> {
        val recovery = lifecycleController.setDeletionRecoveryStatus(
          retryAllowed = true,
          inProgressObserved = false,
        )
        if (recovery != null) {
          successResponse(lifecycleController.latestDeletionReceiptPayload())
        } else {
          successResponse(
            JSONObject()
              .put("kind", "unavailable")
              .put("reason", "coordination-unavailable"),
          )
        }
      }
      is OrchestrationCall.Authoritative -> when (val outcome = call.value) {
        AccountDeletionReceiptOutcome.NotFound -> {
          val recovery = lifecycleController.setDeletionRecoveryStatus(
            retryAllowed = true,
            inProgressObserved = false,
          )
          if (recovery != null) {
            successResponse(lifecycleController.latestDeletionReceiptPayload())
          } else {
            successResponse(
              JSONObject()
                .put("kind", "unavailable")
                .put("reason", "coordination-unavailable"),
            )
          }
        }
        is AccountDeletionReceiptOutcome.InProgress -> {
          val recovery = lifecycleController.setDeletionRecoveryStatus(
            retryAllowed = false,
            inProgressObserved = true,
          )
          if (recovery != null) {
            successResponse(lifecycleController.latestDeletionReceiptPayload())
          } else {
            successResponse(
              JSONObject()
                .put("kind", "unavailable")
                .put("reason", "coordination-unavailable"),
            )
          }
        }
        is AccountDeletionReceiptOutcome.Completed -> {
          if (lifecycleController.completeAccountDeletionReceipt(
            receiptId,
            outcome.completedAtMillis,
          ) == null) return successResponse(
            JSONObject()
              .put("kind", "unavailable")
              .put("reason", "coordination-unavailable"),
          )
          emitInvalidation(listOf("bootstrap", "setup", "account", "privacy"))
          successResponse(lifecycleController.latestDeletionReceiptPayload())
        }
      }
    }
  }

  private fun handleConfirmPrivacyAction(
    request: JSONObject,
    expectedRevision: String?,
  ): Any {
    val revision = expectedRevision.configurationRevisionOrNull()
      ?: return errorResponse("NATIVE_REQUEST_INVALID")
    val started = runBlocking { lifecycleController.beginPrivacyAction(request, revision) }
    val plan = when (started) {
      is PrivacyConfirmationOutcome.Rejected -> return handleConfigurationOutcome(started.outcome)
      is PrivacyConfirmationOutcome.Ready -> started.plan
    }
    if (plan.deletionLocalWipeFallback) {
      return completeDeletionFailureLocalWipe(plan, revision)
    }
    if (!plan.requiresPause) {
      val operation = runBlocking { lifecycleController.executeLocalPrivacyAction(plan) }
      emitInvalidation(listOf("activity", "privacy", "home"))
      return successResponse(lifecycleController.operationPayload(operation))
    }

    val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
      ?: return successResponse(
        lifecycleController.operationPayload(
          lifecycleController.failExternalPrivacyAction(plan, "account-reconnect-required"),
        ),
      )
    val installation = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
      ?: return successResponse(
        lifecycleController.operationPayload(
          lifecycleController.failExternalPrivacyAction(plan, "account-reconnect-required"),
        ),
      )
    val pauseMaterial = prepareLifecyclePauseLocally(revision)
    if (pauseMaterial == null) {
      return successResponse(
        lifecycleController.operationPayload(
          lifecycleController.failExternalPrivacyAction(plan, "internal-contract-invalid"),
        ),
      )
    }

    // Clearing a Gemini-authored template is already locally fail-closed once approvals and the
    // Test receipt are invalidated; server pause convergence can continue independently.
    if (plan.action == "clear-gemini-templates") {
      appGraph.geminiSuggestionGateway.clearProvenance()
      val operation = runBlocking { lifecycleController.executeLocalPrivacyAction(plan) }
      convergeLifecycleServerPause(pauseMaterial)
      return successResponse(lifecycleController.operationPayload(operation))
    }
    if (plan.action in setOf("revoke-google-access", "disconnect-contacts")) {
      val local = runBlocking {
        lifecycleController.purgeContactDerivedState(plan, account.accountId)
      }
      if (!local.localDataErased) {
        return successResponse(lifecycleController.operationPayload(local))
      }
      if (!convergeLifecycleServerPause(pauseMaterial)) {
        return privacyPending(plan, "coordination-unavailable")
      }
      return startOrReplayContactDerivedReset(plan, account.accountId)
    }
    if (plan.action in setOf("sign-out-wipe", "wipe-local-data")) {
      return completeLocalFirstDestructiveWipe(plan, account, installation)
    }
    if (!convergeLifecycleServerPause(pauseMaterial)) {
      return privacyPending(plan, "coordination-unavailable")
    }
    if (plan.action == "delete-account") {
      return startOrReplayAccountDeletion(plan, account.accountId, installation)
    }
    return when (plan.action) {
      "sign-out-retain" -> if (retireLocalCallbacks(installation)) {
        completeRetainedSignOut(plan, account.accountId)
      } else {
        privacyPending(plan, "coordination-unavailable")
      }
      else -> privacyPending(plan, "internal-contract-invalid")
    }
  }

  private fun completeRetainedSignOut(plan: PrivacyActionPlan, accountId: String): Any {
    lifecycleController.markVerifying(plan)
    appGraph.geminiSuggestionGateway.clearProvenance()
    if (!runBlocking { appGraph.googleIdentityCoordinator.completeSignOutAfterSafetyShutdown() }) {
      return successResponse(
        lifecycleController.operationPayload(
          lifecycleController.failExternalPrivacyAction(plan, "account-reconnect-required"),
        ),
      )
    }
    val operation = runBlocking { lifecycleController.retainSignedOutAccount(plan, accountId) }
    emitInvalidation(listOf("bootstrap", "setup", "account", "home", "automation", "privacy"))
    return successResponse(lifecycleController.operationPayload(operation))
  }

  private fun completeLocalFirstDestructiveWipe(
    plan: PrivacyActionPlan,
    account: com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity,
    installation: com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity,
  ): Any {
    val senderEpoch = installation.senderEpoch
      ?: return privacyPending(plan, "coordination-unavailable")
    if (
      lifecycleController.persistReleaseRequestBinding(
        plan,
        account,
        installation.installationId,
        senderEpoch,
        installation.resetGeneration,
      ) == null
    ) return privacyPending(plan, "internal-contract-invalid")
    lifecycleController.markLocalWiping(plan)
    if (!cancelAllLocalWork()) {
      return successResponse(
        lifecycleController.operationPayload(
          lifecycleController.markLocalCleanupPending(plan, "internal-contract-invalid"),
        ),
      )
    }
    val callbacksRetired = runBlocking {
      appGraph.submissionGate.withExclusiveBoundary {
        retireLocalCallbacks(installation, requireNoUnresolvedPermits = false)
      }
    }
    if (!callbacksRetired) {
      return successResponse(
        lifecycleController.operationPayload(
          lifecycleController.markLocalCleanupPending(plan, "coordination-unavailable"),
        ),
      )
    }
    val wipeMarker = lifecycleController.markLocalWipeStarted(
      plan,
      installation.installationId,
      installation.callbackGeneration,
    ) ?: return successResponse(
      lifecycleController.operationPayload(
        lifecycleController.markLocalCleanupPending(plan, "internal-contract-invalid"),
      ),
    )
    return finishMarkedDestructiveWipe(plan, installation, wipeMarker)
  }

  private fun completeAuthoritativeSenderReleaseLocalWipe(
    plan: PrivacyActionPlan,
    installation: com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity,
  ): Any {
    if (!cancelAllLocalWork()) return privacyPending(plan, "internal-contract-invalid")
    val callbacksRetired = runBlocking {
      appGraph.submissionGate.withExclusiveBoundary {
        retireLocalCallbacks(installation, requireNoUnresolvedPermits = false)
      }
    }
    if (!callbacksRetired) return privacyPending(plan, "coordination-unavailable")
    val marker = lifecycleController.markLocalWipeStarted(
      plan,
      installation.installationId,
      installation.callbackGeneration,
    ) ?: return privacyPending(plan, "internal-contract-invalid")
    return finishMarkedDestructiveWipe(plan, installation, marker)
  }

  private fun completeMarkedDestructiveWipe(
    plan: PrivacyActionPlan,
    installation: com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity,
  ): Any {
    val marker = lifecycleController.latestOperation()?.takeIf {
      it.id == plan.operationId && it.localWipeStarted
    } ?: return privacyPending(plan, "internal-contract-invalid")
    return finishMarkedDestructiveWipe(plan, installation, marker)
  }

  private fun finishMarkedDestructiveWipe(
    plan: PrivacyActionPlan,
    installation: com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity,
    wipeMarker: com.yashsomani.birthdayautopilot.lifecycle.DurablePrivacyOperation,
  ): Any {
    appGraph.geminiSuggestionGateway.clearProvenance()
    if (!runBlocking { appGraph.googleIdentityCoordinator.completeSignOutAfterSafetyShutdown() }) {
      return successResponse(lifecycleController.operationPayload(wipeMarker))
    }
    val erased = try {
      appGraph.eraseProtectedDatabaseAfterTeardown(
        installation.installationId,
        installation.callbackGeneration,
      )
    } catch (_: RuntimeException) {
      false
    }
    val operation = if (erased) {
      runCatching {
        if (wipeMarker.authoritativeRecoveryKind == "sender-release") {
          lifecycleController.completeAuthoritativeSenderReleaseLocalErase(plan)
        } else {
          lifecycleController.markDestructiveLocalDataErased(plan)
        }
      }.getOrNull() ?: wipeMarker
    } else {
      wipeMarker
    }
    emitInvalidation(PROJECTION_AREAS.toList())
    scheduleProcessExitAfterDestructiveWipe()
    return successResponse(lifecycleController.operationPayload(operation))
  }

  private fun cancelAllLocalWork(): Boolean = runCatching {
    WorkManager.getInstance(reactApplicationContext)
      .cancelAllWork()
      .result
      .get(WORK_CANCELLATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    true
  }.getOrDefault(false)

  private fun retireLocalCallbacks(
    installation: com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity,
    requireNoUnresolvedPermits: Boolean = true,
  ): Boolean {
    if (
      requireNoUnresolvedPermits &&
      runBlocking { appGraph.automationOrchestrationDao.unresolvedPermitCount() } != 0
    ) {
      AutomationScheduler.enqueueImmediateLocal(reactApplicationContext, "FOREGROUND")
      return false
    }
    return runBlocking {
      SmsCallbackCleanup(reactApplicationContext, appGraph.database).cancelAndRetireGeneration(
        installation.installationId,
        installation.callbackGeneration,
        System.currentTimeMillis(),
      )
    } is SmsCallbackCleanupResult.Completed
  }

  private fun privacyPending(plan: PrivacyActionPlan, reason: String): Any = successResponse(
    lifecycleController.operationPayload(lifecycleController.markRemotePending(plan, reason)),
  )

  private fun startOrReplayContactDerivedReset(
    plan: PrivacyActionPlan,
    accountId: String,
  ): Any {
    val current = lifecycleController.latestOperation()?.takeIf { it.id == plan.operationId }
      ?: return errorResponse("NATIVE_INTENT_FAILURE")
    if (!current.localDataErased) return privacyPending(plan, "internal-contract-invalid")
    val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
      ?.takeIf { it.accountId == accountId }
      ?: return privacyPending(plan, "account-reconnect-required")
    if (plan.action == "revoke-google-access" && current.state == "verifying") {
      if (!current.remoteAccessRevoked && !recentExactGoogleReauthentication(account)) {
        val pending = lifecycleController.markGoogleAccessRevocationPending(
          plan,
          "account-reconnect-required",
        ) ?: return privacyPending(plan, "internal-contract-invalid")
        return successResponse(lifecycleController.operationPayload(pending))
      }
      return completeContactDerivedReset(plan, accountId)
    }
    val requestId = lifecycleController.coordinatedRequestId(plan)
      ?: return privacyPending(plan, "internal-contract-invalid")
    if (!recentExactGoogleReauthentication(account)) {
      return privacyPending(plan, "account-reconnect-required")
    }
    return when (val call = runBlocking {
      appGraph.automationCoordinationPort.resetContactDerivedState(requestId)
    }) {
      is OrchestrationCall.Unavailable -> privacyPending(plan, "coordination-unavailable")
      is OrchestrationCall.Authoritative -> when (val outcome = call.value) {
        is CoordinationOperationOutcome.InProgress -> {
          val progress = outcome.progress
          val operation = lifecycleController.markCoordinatedOperationInProgress(
            plan,
            progress.drainUntilMillis,
            System.currentTimeMillis().coerceAtLeast(0),
            SystemClock.elapsedRealtime(),
            currentBootCount(),
          )
          progress.drainUntilMillis?.let {
            AutomationScheduler.scheduleNetworkAttempt(
              reactApplicationContext,
              "contact-reset-${plan.operationId}",
              it,
            )
          }
          successResponse(lifecycleController.operationPayload(operation))
        }
        is CoordinationOperationOutcome.Refused ->
          privacyPending(plan, outcome.reason.toLifecycleReason())
        is CoordinationOperationOutcome.Completed -> {
          if (outcome.completion !is CoordinationCompletion.ContactDerivedReset) {
            return privacyPending(plan, "internal-contract-invalid")
          }
          if (lifecycleController.markContactResetRemoteCompleted(plan) == null) {
            return privacyPending(plan, "internal-contract-invalid")
          }
          completeContactDerivedReset(plan, accountId)
        }
      }
    }
  }

  private fun completeContactDerivedReset(
    plan: PrivacyActionPlan,
    accountId: String,
  ): Any {
    var current = lifecycleController.latestOperation()?.takeIf { it.id == plan.operationId }
      ?: return errorResponse("NATIVE_INTENT_FAILURE")
    if (!current.localDataErased) {
      current = runBlocking {
        lifecycleController.purgeContactDerivedState(plan, accountId)
      }
      if (!current.localDataErased) {
        return successResponse(lifecycleController.operationPayload(current))
      }
    }
    if (current.state !in setOf("verifying", "complete")) {
      current = lifecycleController.markContactResetRemoteCompleted(plan)
        ?: return privacyPending(plan, "internal-contract-invalid")
    }
    if (plan.action == "disconnect-contacts") {
      emitInvalidation(listOf("contacts", "messages", "automation", "home", "activity", "privacy"))
      return successResponse(lifecycleController.operationPayload(current))
    }
    if (plan.action != "revoke-google-access") {
      return privacyPending(plan, "internal-contract-invalid")
    }
    if (current.remoteAccessRevoked) {
      if (!runBlocking { appGraph.googleIdentityCoordinator.completeSignOutAfterSafetyShutdown() }) {
        return successResponse(lifecycleController.operationPayload(current))
      }
      val completed = runBlocking { lifecycleController.retainSignedOutAccount(plan, accountId) }
      emitInvalidation(PROJECTION_AREAS.toList())
      return successResponse(lifecycleController.operationPayload(completed))
    }
    return when (runBlocking {
      appGraph.googleIdentityCoordinator.revokeAllGoogleAccessAfterSafetyShutdown()
    }) {
      GoogleAccessRevocationOutcome.REVOKED -> {
        if (runBlocking { lifecycleController.markGoogleAccessRevoked(plan) } == null) {
          return privacyPending(plan, "internal-contract-invalid")
        }
        val completed = runBlocking {
          lifecycleController.retainSignedOutAccount(plan, accountId)
        }
        emitInvalidation(PROJECTION_AREAS.toList())
        successResponse(lifecycleController.operationPayload(completed))
      }
      GoogleAccessRevocationOutcome.SESSION_CLEANUP_PENDING -> {
        val pending = runBlocking { lifecycleController.markGoogleAccessRevoked(plan) }
          ?: return privacyPending(plan, "internal-contract-invalid")
        successResponse(lifecycleController.operationPayload(pending))
      }
      GoogleAccessRevocationOutcome.ACCOUNT_CHANGED -> {
        val pending = lifecycleController.markGoogleAccessRevocationPending(
          plan,
          "account-reconnect-required",
        ) ?: return privacyPending(plan, "internal-contract-invalid")
        successResponse(lifecycleController.operationPayload(pending))
      }
      GoogleAccessRevocationOutcome.AMBIGUOUS -> {
        val pending = lifecycleController.markGoogleAccessRevocationPending(
          plan,
          "coordination-unavailable",
        ) ?: return privacyPending(plan, "internal-contract-invalid")
        successResponse(lifecycleController.operationPayload(pending))
      }
    }
  }

  private fun startOrReplayAccountDeletion(
    plan: PrivacyActionPlan,
    accountId: String,
    installation: com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity,
  ): Any {
    val account = runBlocking { appGraph.peopleSyncDao.activeAccount() }
      ?.takeIf { it.accountId == accountId }
      ?: return privacyPending(plan, "account-reconnect-required")
    val requestId = lifecycleController.deletionRequestId(plan)
      ?: return privacyPending(plan, "internal-contract-invalid")
    if (!recentExactGoogleReauthentication(account)) {
      return privacyPending(plan, "account-reconnect-required")
    }
    return when (val call = runBlocking {
      appGraph.automationCoordinationPort.requestAccountDeletion(requestId)
    }) {
      is OrchestrationCall.Unavailable -> privacyPending(plan, "coordination-unavailable")
      is OrchestrationCall.Authoritative -> acceptAccountDeletion(
        plan,
        installation,
        call.value,
      )
    }
  }

  private fun acceptAccountDeletion(
    plan: PrivacyActionPlan,
    installation: com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity,
    acceptance: AccountDeletionAcceptance,
  ): Any {
    val fence = acceptance.deletingFence
    if (fence != null) {
      val applied = runBlocking {
        appGraph.submissionGate.withExclusiveBoundary {
          appGraph.automationOrchestrationDao.applyRemoteDeletionFence(
            accountId = installation.accountId,
            localInstallationId = installation.installationId,
            senderEpoch = fence.senderEpoch,
            resetGeneration = fence.resetGeneration,
            deletionDrainUntilMillis = fence.deletionDrainUntilMillis,
            serverObservedAtMillis = acceptance.serverObservedAtMillis,
            deviceWallMillis = System.currentTimeMillis(),
          )
        }
      }
      if (!applied) return privacyPending(plan, "internal-contract-invalid")
    }
    val operation = lifecycleController.markRemoteDraining(
      plan = plan,
      requestId = acceptance.requestId,
      drainUntilMillis = acceptance.drainUntilMillis,
      serverObservedAtMillis = acceptance.serverObservedAtMillis,
      acceptedAtElapsedMillis = SystemClock.elapsedRealtime(),
      acceptedBootCount = currentBootCount(),
    )
    emitInvalidation(listOf("account", "automation", "home", "activity", "privacy"))
    return continueAcceptedAccountDeletion(plan, installation, operation)
  }

  private fun continueAcceptedAccountDeletion(
    plan: PrivacyActionPlan,
    installation: com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity,
    operation: com.yashsomani.birthdayautopilot.lifecycle.DurablePrivacyOperation,
  ): Any {
    if (operation.localDataErased) {
      return successResponse(lifecycleController.operationPayload(operation))
    }
    if (runBlocking { appGraph.automationOrchestrationDao.unresolvedPermitCount() } != 0) {
      AutomationScheduler.enqueueImmediateLocal(reactApplicationContext, "FOREGROUND")
      return successResponse(lifecycleController.operationPayload(operation))
    }
    val cleanup = runBlocking {
      SmsCallbackCleanup(reactApplicationContext, appGraph.database).cancelAndRetireGeneration(
        installation.installationId,
        installation.callbackGeneration,
        System.currentTimeMillis(),
      )
    }
    if (cleanup !is SmsCallbackCleanupResult.Completed) {
      return successResponse(lifecycleController.operationPayload(operation))
    }
    return completeAcceptedAccountDeletionLocalWipe(plan, installation)
  }

  private fun completeAcceptedAccountDeletionLocalWipe(
    plan: PrivacyActionPlan,
    installation: com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity,
  ): Any {
    appGraph.geminiSuggestionGateway.clearProvenance()
    val workCancelled = runCatching {
      WorkManager.getInstance(reactApplicationContext)
        .cancelAllWork()
        .result
        .get(WORK_CANCELLATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      true
    }.getOrDefault(false)
    if (!workCancelled) {
      val operation = lifecycleController.latestOperation()
        ?.takeIf { it.id == plan.operationId }
        ?: lifecycleController.markRemotePending(plan, "internal-contract-invalid")
      return successResponse(lifecycleController.operationPayload(operation))
    }
    val wipeMarker = lifecycleController.markLocalWipeStarted(
      plan,
      installation.installationId,
      installation.callbackGeneration,
    ) ?: return successResponse(
      lifecycleController.operationPayload(
        lifecycleController.latestOperation()
          ?.takeIf { it.id == plan.operationId }
          ?: lifecycleController.markRemotePending(plan, "internal-contract-invalid"),
      ),
    )
    if (!runBlocking { appGraph.googleIdentityCoordinator.completeSignOutAfterSafetyShutdown() }) {
      return successResponse(lifecycleController.operationPayload(wipeMarker))
    }
    val erased = try {
      appGraph.eraseProtectedDatabaseAfterTeardown(
        installation.installationId,
        installation.callbackGeneration,
      )
    } catch (_: RuntimeException) {
      false
    }
    val receipt = if (erased) runCatching {
      lifecycleController.markDeletionLocalDataErased(plan)
    }.getOrDefault(wipeMarker) else wipeMarker
    emitInvalidation(PROJECTION_AREAS.toList())
    scheduleProcessExitAfterDestructiveWipe()
    return successResponse(lifecycleController.operationPayload(receipt))
  }

  private fun completeDeletionFailureLocalWipe(
    plan: PrivacyActionPlan,
    expectedRevision: Long? = null,
  ): Any {
    val current = lifecycleController.latestOperation()
      ?.takeIf {
        it.id == plan.operationId &&
          it.action == "delete-account" &&
          it.deletionLocalWipeFallback &&
          !it.localDataErased
      }
      ?: return errorResponse("NATIVE_INTENT_FAILURE")
    val installation = runBlocking { appGraph.automationOrchestrationDao.localInstallation() }
      ?: return privacyPending(plan, "internal-contract-invalid")
    if (!current.localWipeStarted) {
      val revision = expectedRevision
        ?: runBlocking { appGraph.automationOrchestrationDao.control()?.revision }
        ?: return privacyPending(plan, "internal-contract-invalid")
      val locallyPaused = runBlocking {
        appGraph.submissionGate.withExclusiveBoundary {
          configurationController.pauseAll(
            JSONObject().put("expectedRevision", revision.toString()),
            revision,
          ) is AccountModePreparationOutcome.Ready
        }
      }
      if (!locallyPaused) return privacyPending(plan, "internal-contract-invalid")

      val workCancelled = runCatching {
        WorkManager.getInstance(reactApplicationContext)
          .cancelAllWork()
          .result
          .get(WORK_CANCELLATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        true
      }.getOrDefault(false)
      if (!workCancelled) return privacyPending(plan, "internal-contract-invalid")

      val callbacksRetired = runBlocking {
        appGraph.submissionGate.withExclusiveBoundary {
          SmsCallbackCleanup(reactApplicationContext, appGraph.database).cancelAndRetireGeneration(
            installation.installationId,
            installation.callbackGeneration,
            System.currentTimeMillis().coerceAtLeast(0),
          ) is SmsCallbackCleanupResult.Completed
        }
      }
      if (!callbacksRetired) return privacyPending(plan, "coordination-unavailable")
      if (
        lifecycleController.markLocalWipeStarted(
          plan,
          installation.installationId,
          installation.callbackGeneration,
        ) == null
      ) return privacyPending(plan, "internal-contract-invalid")
    }

    appGraph.geminiSuggestionGateway.clearProvenance()
    if (!runBlocking { appGraph.googleIdentityCoordinator.completeSignOutAfterSafetyShutdown() }) {
      return successResponse(
        lifecycleController.operationPayload(
          lifecycleController.latestOperation() ?: return errorResponse("NATIVE_INTENT_FAILURE"),
        ),
      )
    }
    val erased = try {
      appGraph.eraseProtectedDatabaseAfterTeardown(
        installation.installationId,
        installation.callbackGeneration,
      )
    } catch (_: RuntimeException) {
      false
    }
    val receipt = if (erased) {
      runCatching { lifecycleController.markDeletionLocalDataErased(plan) }
        .getOrElse {
          lifecycleController.latestOperation() ?: return errorResponse("NATIVE_INTENT_FAILURE")
        }
    } else {
      lifecycleController.latestOperation() ?: return errorResponse("NATIVE_INTENT_FAILURE")
    }
    emitInvalidation(PROJECTION_AREAS.toList())
    scheduleProcessExitAfterDestructiveWipe()
    return successResponse(lifecycleController.operationPayload(receipt))
  }

  private fun scheduleProcessExitAfterDestructiveWipe() {
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
      {
        android.os.Process.killProcess(android.os.Process.myPid())
      },
      PROCESS_EXIT_AFTER_WIPE_MILLIS,
    )
  }

  private fun handleStartTestIntent(
    request: JSONObject,
    expectedRevision: String?,
    promise: Promise,
  ) {
    val revision = expectedRevision.configurationRevisionOrNull()
    if (revision == null) {
      promise.resolve(errorResponse("NATIVE_REQUEST_INVALID"))
      return
    }
    val preflight = runBlocking { configurationController.preflightTestStart(request, revision) }
    if (preflight !is ConfigurationOutcome.Success) {
      promise.resolve(handleConfigurationOutcome(preflight))
      return
    }
    val eligibility = appGraph.androidReadinessProbe.read().eligibility
    if (!eligibility.allowsForegroundTest()) {
      promise.resolve(unsupportedResponse("distribution-channel-unapproved"))
      return
    }
    val permissionResult = runBlocking {
      ForegroundActivityRegistry.currentTelephonyPermissionLauncher()?.request()
        ?: TelephonyPermissionResult.UNAVAILABLE
    }
    when (permissionResult) {
      TelephonyPermissionResult.GRANTED -> Unit
      TelephonyPermissionResult.PHONE_STATE_DENIED -> {
        promise.resolve(conflictResponse("phone-state-permission-denied"))
        return
      }
      TelephonyPermissionResult.PHONE_STATE_PERMANENTLY_DENIED -> {
        promise.resolve(conflictResponse("phone-state-permission-permanently-denied"))
        return
      }
      TelephonyPermissionResult.SMS_DENIED -> {
        promise.resolve(conflictResponse("sms-permission-denied"))
        return
      }
      TelephonyPermissionResult.SMS_PERMANENTLY_DENIED -> {
        promise.resolve(conflictResponse("sms-permission-permanently-denied"))
        return
      }
      TelephonyPermissionResult.UNAVAILABLE -> {
        promise.resolve(temporaryResponse("permission-denied"))
        return
      }
    }
    val outcome = runBlocking { configurationController.startTest(request, revision) }
    when (outcome) {
      is TestStartOutcome.Rejected -> promise.resolve(handleConfigurationOutcome(outcome.outcome))
      is TestStartOutcome.Ready -> {
        val orchestration = runBlocking {
          appGraph.automationOrchestrator.submitForegroundTest(
            outcome.testJobId,
            outcome.foregroundConfirmationNonceHash,
          )
        }
        val projection = runBlocking {
          configurationController.testProjection(outcome.testJobId, orchestration.safeCode)
        }
        if (projection == null) {
          promise.resolve(errorResponse("TEST_PROJECTION_UNAVAILABLE"))
          return
        }
        emitInvalidation(listOf("automation", "readiness", "home", "activity"))
        promise.resolve(successResponse(projection))
      }
    }
  }

  private fun handlePrepareActivation(resume: Boolean): Any {
    if (!activationReadinessAllowed()) return conflictResponse(readinessBlockingCode("activation"))
    return handleConfigurationOutcome(
      runBlocking { configurationController.prepareActivation(resume) },
    )
  }

  private fun handleActivationIntent(
    request: JSONObject,
    expectedRevision: String?,
    resume: Boolean,
  ): Any {
    val revision = expectedRevision.configurationRevisionOrNull()
      ?: return errorResponse("NATIVE_REQUEST_INVALID")
    if (!activationReadinessAllowed()) return conflictResponse(readinessBlockingCode("activation"))
    val prepared = runBlocking {
      configurationController.activationCoordinationMaterial(request, revision, resume)
    }
    val material = when (prepared) {
      is AccountModePreparationOutcome.Rejected -> return handleConfigurationOutcome(prepared.outcome)
      is AccountModePreparationOutcome.Ready -> prepared.material
    }
    val server = runBlocking {
      appGraph.automationCoordinationPort.changeAccountMode(
        accountModeSpec(material, AccountModeAction.ACTIVATE_AUTOMATION),
      )
    }
    when (server) {
      is OrchestrationCall.Unavailable -> {
        AutomationScheduler.enqueueImmediateLocal(reactApplicationContext, "NEXT_WINDOW")
        return temporaryResponse("coordination-unavailable")
      }
      is OrchestrationCall.Authoritative -> when (val outcome = server.value) {
        is AccountModeOutcome.Refused -> return conflictResponse(
          outcome.reason.toLifecycleReason(),
        )
        is AccountModeOutcome.Changed -> if (outcome.mode != ServerAccountMode.AUTOMATION_ACTIVE) {
          return conflictResponse("coordination-unavailable")
        }
      }
    }
    // The network response is not a readiness permit. Re-read every local/platform gate before
    // committing the local active mode under the same SubmissionGate used by final SMS barriers.
    if (!activationReadinessAllowed()) {
      AutomationScheduler.enqueueImmediateLocal(reactApplicationContext, "FOREGROUND")
      return conflictResponse(readinessBlockingCode("activation"))
    }
    val committed = runBlocking {
      appGraph.submissionGate.withExclusiveBoundary {
        configurationController.commitActivation(
          handle = checkNotNull(material.reviewHandle),
          expectedRevision = revision,
          resume = resume,
        )
      }
    }
    if (committed !is ConfigurationOutcome.Success) return handleConfigurationOutcome(committed)
    emitInvalidation(listOf("automation", "readiness", "home", "activity", "account"))
    AutomationScheduler.enqueueImmediateLocal(reactApplicationContext, "FOREGROUND")
    return successResponse(automationPayload(appGraph.androidReadinessProbe.read()))
  }

  private fun handlePauseAll(request: JSONObject, expectedRevision: String?): Any {
    val revision = expectedRevision.configurationRevisionOrNull()
      ?: return errorResponse("NATIVE_REQUEST_INVALID")
    val paused = runBlocking {
      appGraph.submissionGate.withExclusiveBoundary {
        configurationController.pauseAll(request, revision)
      }
    }
    val material = when (paused) {
      is AccountModePreparationOutcome.Rejected -> return handleConfigurationOutcome(paused.outcome)
      is AccountModePreparationOutcome.Ready -> paused.material
    }
    // Local pause has already won and bumped blockerRevision. Network convergence is best effort;
    // ambiguity cannot restore automationDesired or authorize a send.
    runBlocking {
      appGraph.automationCoordinationPort.changeAccountMode(
        accountModeSpec(material, AccountModeAction.PAUSE_FOR_REPAIR),
      )
    }
    emitInvalidation(listOf("automation", "readiness", "home", "activity", "account"))
    AutomationScheduler.enqueueImmediateLocal(reactApplicationContext, "FOREGROUND")
    return successResponse(automationPayload(appGraph.androidReadinessProbe.read()))
  }

  private fun activationReadinessAllowed(): Boolean = try {
    readinessPayload(appGraph.androidReadinessProbe.read())
      .getJSONObject("activation")
      .optString("kind") == "allowed"
  } catch (_: Exception) {
    false
  }

  private fun readinessBlockingCode(gate: String): String = try {
    readinessPayload(appGraph.androidReadinessProbe.read())
      .getJSONObject(gate)
      .optJSONArray("issues")
      ?.optJSONObject(0)
      ?.optString("code")
      ?.takeIf { it in SAFE_REASON_CODES }
      ?: "internal-contract-invalid"
  } catch (_: Exception) {
    "internal-contract-invalid"
  }

  private fun accountModeSpec(
    material: AccountModeCoordinationMaterial,
    action: AccountModeAction,
  ): AccountModeSpec = AccountModeSpec(
    binding = CoordinationBindingSpec(
      ledgerGeneration = LEDGER_GENERATION,
      installationId = material.installationId,
      senderEpoch = material.senderEpoch,
      resetGeneration = material.resetGeneration,
      appBuildNumber = BuildConfig.VERSION_CODE,
      policyVersion = COORDINATION_POLICY_VERSION,
      distributionChannel = currentDistributionChannel(),
    ),
    action = action,
    testClaimId = material.testClaimId,
    boundTestReceiptPrehash = material.boundTestReceiptPrehash,
    readinessContractVersion = if (action == AccountModeAction.ACTIVATE_AUTOMATION) {
      READINESS_CONTRACT_VERSION
    } else {
      null
    },
  )

  private fun pauseLifecycleAccount(revision: Long): LifecyclePauseAttempt {
    val material = prepareLifecyclePauseLocally(revision)
      ?: return LifecyclePauseAttempt(localPrepared = false, serverPaused = false)
    return LifecyclePauseAttempt(
      localPrepared = true,
      serverPaused = convergeLifecycleServerPause(material),
    )
  }

  private fun prepareLifecyclePauseLocally(
    revision: Long,
  ): AccountModeCoordinationMaterial? {
    val prepared = runBlocking {
      appGraph.submissionGate.withExclusiveBoundary {
        configurationController.pauseAll(
          JSONObject().put("expectedRevision", revision.toString()),
          revision,
        )
      }
    }
    return (prepared as? AccountModePreparationOutcome.Ready)?.material
  }

  private fun convergeLifecycleServerPause(
    material: AccountModeCoordinationMaterial,
  ): Boolean {
    val serverPaused = when (val server = runBlocking {
      appGraph.automationCoordinationPort.changeAccountMode(
        accountModeSpec(material, AccountModeAction.PAUSE_FOR_REPAIR),
      )
    }) {
      is OrchestrationCall.Unavailable -> false
      is OrchestrationCall.Authoritative ->
        (server.value as? AccountModeOutcome.Changed)?.mode == ServerAccountMode.PAUSED_REPAIR
    }
    emitInvalidation(listOf("automation", "readiness", "home", "activity", "account", "privacy"))
    AutomationScheduler.enqueueImmediateLocal(reactApplicationContext, "FOREGROUND")
    return serverPaused
  }

  private fun currentDistributionChannel(): DistributionChannel = when (BuildConfig.APP_ENV) {
    "dev" -> DistributionChannel.DEV
    "staging" -> DistributionChannel.STAGING
    "lab" -> DistributionChannel.RESTRICTED_LAB
    "prod" -> if (BuildConfig.APPROVED_DISTRIBUTION_CHANNEL == "google-play") {
      DistributionChannel.PLAY
    } else {
      DistributionChannel.DIRECT_MANAGED
    }
    else -> DistributionChannel.DEV
  }

  private fun com.yashsomani.birthdayautopilot.coordination.CoordinationServerReason
    .toLifecycleReason(): String = when (this) {
    com.yashsomani.birthdayautopilot.coordination.CoordinationServerReason.BOUND_TEST_RECEIPT_REQUIRED ->
      "test-receipt-invalid"
    com.yashsomani.birthdayautopilot.coordination.CoordinationServerReason.CONTINUITY_UNAVAILABLE ->
      "coordination-unavailable"
    com.yashsomani.birthdayautopilot.coordination.CoordinationServerReason.DELETION_SUPPRESSED ->
      "firebase-account-deleting"
    com.yashsomani.birthdayautopilot.coordination.CoordinationServerReason.IOS_COMPOSER_RESERVED ->
      "ios-composer-reserved"
    com.yashsomani.birthdayautopilot.coordination.CoordinationServerReason.MODE_BLOCKED ->
      "policy-suspended"
    else -> "coordination-unavailable"
  }

  private fun handleGoogleIdentityIntent(): Any {
    if (lifecycleController.lifecycleJournalUnreadable()) {
      return handleLifecycleRepairIdentityIntent()
    }
    val deletionReceiptLookup = lifecycleController.deletionReceiptLookup()
    DeletionReceiptAccountPolicy.blockerCode(deletionReceiptLookup)?.let { blocker ->
      if (
        deletionReceiptLookup is DeletionReceiptLookup.Present &&
        deletionReceiptLookup.receipt.state == DurableDeletionReceipt.State.PENDING &&
        lifecycleController.deletionRecoveryReauthenticationAllowed()
      ) return handleDeletionRecoveryIdentityIntent()
      return conflictResponse(blocker)
    }
    if (lifecycleController.senderReleaseRecoveryReauthenticationAllowed()) {
      return handleSenderReleaseRecoveryIdentityIntent()
    }
    lifecycleController.latestOperation()?.takeIf {
      it.action in setOf(
        "delete-account",
        "revoke-google-access",
        "disconnect-contacts",
        "sign-out-wipe",
        "wipe-local-data",
      ) &&
        it.state !in setOf("complete", "failed") &&
        (it.localWipeStarted ||
          (it.action == "delete-account" && it.state == "remote-draining"))
    }?.let { operation ->
      return conflictResponse(
        if (operation.action == "delete-account") {
          "firebase-account-deleting"
        } else {
          "policy-suspended"
        },
      )
    }
    if (!lifecycleController.prepareForOrdinaryAccountIdentity()) {
      return conflictResponse("coordination-unavailable")
    }
    appGraph.geminiSuggestionGateway.clearProvenance()
    val outcome = try {
      runBlocking { appGraph.googleIdentityCoordinator.continueWithGoogle() }
    } catch (_: Exception) {
      return errorResponse("IDENTITY_NATIVE_FAILURE")
    }
    return when (outcome) {
      is IdentityOutcome.SignedIn -> {
        AutomationScheduler.enqueueAccountChange(reactApplicationContext)
        emitInvalidation(listOf("bootstrap", "setup", "account", "home", "readiness"))
        successResponse(accountPayload())
      }
      is IdentityOutcome.Failed -> when (outcome.reason) {
        IdentityFailure.USER_CANCELLED -> cancelledResponse("user")
        IdentityFailure.NETWORK_UNAVAILABLE -> temporaryResponse("network-offline")
        IdentityFailure.ACCOUNT_MISMATCH -> conflictResponse("account-mismatch")
        IdentityFailure.PLAY_SERVICES_UNAVAILABLE,
        IdentityFailure.CREDENTIAL_PROVIDER_UNAVAILABLE,
        -> unsupportedResponse("google-play-services-missing")
        IdentityFailure.FIREBASE_USER_DISABLED -> conflictResponse("account-disabled")
        IdentityFailure.SECURITY_REAUTHENTICATION_REQUIRED ->
          conflictResponse("account-reconnect-required")
        IdentityFailure.ACTIVITY_UNAVAILABLE -> temporaryResponse("account-reconnect-required")
        IdentityFailure.TIER_CONFIGURATION_MISSING,
        IdentityFailure.FIREBASE_UNAVAILABLE,
        IdentityFailure.APP_CHECK_UNAVAILABLE,
        IdentityFailure.GOOGLE_ACCOUNT_UNAVAILABLE,
        IdentityFailure.INVALID_GOOGLE_CREDENTIAL,
        IdentityFailure.INTERNAL_FAILURE,
        -> errorResponse("IDENTITY_CONFIGURATION_UNAVAILABLE")
      }
    }
  }

  private fun handleLifecycleRepairIdentityIntent(): Any {
    val account = appGraph.lifecycleRepairAccount()
      ?: return conflictResponse("coordination-unavailable")
    val outcome = try {
      runBlocking {
        appGraph.googleIdentityCoordinator.continueWithGoogleForLifecycleRepair(
          matchesPreexistingGoogleSubject = { googleSubject ->
            appGraph.matchesLifecycleRepairGoogleSubject(account, googleSubject)
          },
          matchesPreexistingBinding = { binding ->
            appGraph.matchesLifecycleRepairBinding(account, binding)
          },
        )
      }
    } catch (_: Exception) {
      return errorResponse("IDENTITY_NATIVE_FAILURE")
    }
    if (outcome is IdentityOutcome.Failed) {
      return when (outcome.reason) {
        IdentityFailure.USER_CANCELLED -> cancelledResponse("user")
        IdentityFailure.NETWORK_UNAVAILABLE -> temporaryResponse("network-offline")
        IdentityFailure.ACCOUNT_MISMATCH -> conflictResponse("account-mismatch")
        IdentityFailure.FIREBASE_USER_DISABLED -> conflictResponse("account-disabled")
        IdentityFailure.PLAY_SERVICES_UNAVAILABLE,
        IdentityFailure.CREDENTIAL_PROVIDER_UNAVAILABLE,
        -> unsupportedResponse("google-play-services-missing")
        IdentityFailure.ACTIVITY_UNAVAILABLE,
        IdentityFailure.SECURITY_REAUTHENTICATION_REQUIRED,
        -> temporaryResponse("account-reconnect-required")
        else -> errorResponse("IDENTITY_CONFIGURATION_UNAVAILABLE")
      }
    }
    if (!appGraph.authorizeLifecycleRepairIdentitySession(account)) {
      clearDeletionRecoveryIdentitySession()
      return conflictResponse("coordination-unavailable")
    }
    emitInvalidation(listOf("bootstrap", "setup", "account", "home", "readiness"))
    return successResponse(accountPayload())
  }

  private fun handleDeletionRecoveryIdentityIntent(): Any {
    val outcome = try {
      runBlocking {
        appGraph.googleIdentityCoordinator.continueWithGoogleForDeletionRecovery(
          lifecycleController::matchesDeletionRecoveryGoogleSubject,
          lifecycleController::matchesDeletionRecoveryBinding,
        )
      }
    } catch (_: Exception) {
      return errorResponse("IDENTITY_NATIVE_FAILURE")
    }
    if (outcome is IdentityOutcome.Failed) {
      return when (outcome.reason) {
        IdentityFailure.USER_CANCELLED -> cancelledResponse("user")
        IdentityFailure.NETWORK_UNAVAILABLE -> temporaryResponse("network-offline")
        IdentityFailure.ACCOUNT_MISMATCH -> conflictResponse("account-mismatch")
        IdentityFailure.FIREBASE_USER_DISABLED -> conflictResponse("account-disabled")
        IdentityFailure.PLAY_SERVICES_UNAVAILABLE,
        IdentityFailure.CREDENTIAL_PROVIDER_UNAVAILABLE,
        -> unsupportedResponse("google-play-services-missing")
        IdentityFailure.ACTIVITY_UNAVAILABLE,
        IdentityFailure.SECURITY_REAUTHENTICATION_REQUIRED,
        -> temporaryResponse("account-reconnect-required")
        else -> errorResponse("IDENTITY_CONFIGURATION_UNAVAILABLE")
      }
    }

    val operation = lifecycleController.latestOperation()?.takeIf {
      it.deletionLocalWipeFallback &&
        it.localDataErased &&
        it.deletionRetryAllowed &&
        it.requestId != null
    }
    if (operation == null) {
      if (!clearDeletionRecoveryIdentitySession()) {
        return conflictResponse("account-reconnect-required")
      }
      return conflictResponse("coordination-unavailable")
    }
    val replay = try {
      runBlocking {
        deletionRecoveryCoordinationPort.requestAccountDeletion(checkNotNull(operation.requestId))
      }
    } catch (_: Exception) {
      return if (clearDeletionRecoveryIdentitySession()) {
        errorResponse("IDENTITY_NATIVE_FAILURE")
      } else {
        conflictResponse("account-reconnect-required")
      }
    }
    val signedOut = clearDeletionRecoveryIdentitySession()
    if (!signedOut) return conflictResponse("account-reconnect-required")
    val accepted = when (replay) {
      is OrchestrationCall.Unavailable -> false
      is OrchestrationCall.Authoritative -> lifecycleController.markDeletionRecoveryAccepted(
        requestId = replay.value.requestId,
        drainUntilMillis = replay.value.drainUntilMillis,
        serverObservedAtMillis = replay.value.serverObservedAtMillis,
        acceptedAtElapsedMillis = SystemClock.elapsedRealtime(),
        acceptedBootCount = currentBootCount(),
      ) != null
    }
    emitInvalidation(listOf("bootstrap", "setup", "account", "privacy"))
    if (replay is OrchestrationCall.Authoritative && !accepted) {
      return conflictResponse("coordination-unavailable")
    }
    return successResponse(accountPayload())
  }

  private fun handleSenderReleaseRecoveryIdentityIntent(): Any {
    val outcome = try {
      runBlocking {
        appGraph.googleIdentityCoordinator.continueWithGoogleForLifecycleRepair(
          lifecycleController::matchesSenderReleaseRecoveryGoogleSubject,
          lifecycleController::matchesSenderReleaseRecoveryBinding,
        )
      }
    } catch (_: Exception) {
      return errorResponse("IDENTITY_NATIVE_FAILURE")
    }
    if (outcome is IdentityOutcome.Failed) {
      return when (outcome.reason) {
        IdentityFailure.USER_CANCELLED -> cancelledResponse("user")
        IdentityFailure.NETWORK_UNAVAILABLE -> temporaryResponse("network-offline")
        IdentityFailure.ACCOUNT_MISMATCH -> conflictResponse("account-mismatch")
        IdentityFailure.FIREBASE_USER_DISABLED -> conflictResponse("account-disabled")
        IdentityFailure.PLAY_SERVICES_UNAVAILABLE,
        IdentityFailure.CREDENTIAL_PROVIDER_UNAVAILABLE,
        -> unsupportedResponse("google-play-services-missing")
        IdentityFailure.ACTIVITY_UNAVAILABLE,
        IdentityFailure.SECURITY_REAUTHENTICATION_REQUIRED,
        -> temporaryResponse("account-reconnect-required")
        else -> errorResponse("IDENTITY_CONFIGURATION_UNAVAILABLE")
      }
    }

    val operation = lifecycleController.latestOperation()?.takeIf {
      it.action in setOf("sign-out-wipe", "wipe-local-data") &&
        it.localDataErased &&
        it.state in setOf("remote-pending", "remote-draining")
    }
    val plan = operation?.let { lifecycleController.privacyPlanForOperation(it.id) }
    val requestId = operation?.requestId
    val installationId = operation?.remoteRequestInstallationId
    val senderEpoch = operation?.remoteRequestSenderEpoch
    val resetGeneration = operation?.remoteRequestResetGeneration
    if (
      plan == null ||
      requestId == null ||
      installationId == null ||
      senderEpoch == null ||
      resetGeneration == null
    ) {
      clearDeletionRecoveryIdentitySession()
      return conflictResponse("coordination-unavailable")
    }
    val replay = try {
      runBlocking {
        senderReleaseRecoveryCoordinationPort.releaseAndroidSender(
          SenderReleaseSpec(requestId, installationId, senderEpoch, resetGeneration),
        )
      }
    } catch (_: Exception) {
      lifecycleController.markRemotePending(plan, "coordination-unavailable")
      return if (clearDeletionRecoveryIdentitySession()) {
        errorResponse("IDENTITY_NATIVE_FAILURE")
      } else {
        conflictResponse("account-reconnect-required")
      }
    }

    var remoteCompletionObserved = false
    when (replay) {
      is OrchestrationCall.Unavailable ->
        lifecycleController.markRemotePending(plan, "coordination-unavailable")
      is OrchestrationCall.Authoritative -> when (val remote = replay.value) {
        is CoordinationOperationOutcome.InProgress -> lifecycleController
          .markCoordinatedOperationInProgress(
            plan,
            remote.progress.drainUntilMillis,
            System.currentTimeMillis().coerceAtLeast(0),
            SystemClock.elapsedRealtime(),
            currentBootCount(),
          )
        is CoordinationOperationOutcome.Refused ->
          lifecycleController.markRemotePending(plan, remote.reason.toLifecycleReason())
        is CoordinationOperationOutcome.Completed -> {
          if (remote.completion !is CoordinationCompletion.SenderRelease) {
            lifecycleController.markRemotePending(plan, "internal-contract-invalid")
            clearDeletionRecoveryIdentitySession()
            return conflictResponse("coordination-unavailable")
          }
          remoteCompletionObserved = true
        }
      }
    }
    if (!clearDeletionRecoveryIdentitySession()) {
      return conflictResponse("account-reconnect-required")
    }
    if (
      remoteCompletionObserved &&
      lifecycleController.completeSenderReleaseRemoteCleanup(plan) == null
    ) return conflictResponse("coordination-unavailable")
    emitInvalidation(listOf("bootstrap", "setup", "account", "privacy", "automation"))
    return successResponse(accountPayload())
  }

  private fun clearDeletionRecoveryIdentitySession(): Boolean = try {
    runBlocking { appGraph.googleIdentityCoordinator.completeSignOutAfterSafetyShutdown() }
  } catch (_: Exception) {
    false
  }

  private fun handlePeopleSyncIntent(disclosureAcknowledged: Boolean): Any {
    val outcome = try {
      runBlocking {
        appGraph.peopleSyncService.sync(
          interactiveAuthorization = true,
          disclosureAcknowledged = disclosureAcknowledged,
        )
      }
    } catch (_: Exception) {
      return errorResponse("CONTACTS_SYNC_NATIVE_FAILURE")
    }
    if (
      outcome !is PeopleSyncOutcome.Cancelled &&
      outcome !is PeopleSyncOutcome.OwnershipBlocked
    ) {
      emitInvalidation(
        listOf("bootstrap", "setup", "home", "contacts", "readiness", "automation"),
      )
    }
    return when (outcome) {
      is PeopleSyncOutcome.Completed -> {
        AutomationScheduler.enqueueContactChange(reactApplicationContext)
        successResponse(contactsSyncPayload())
      }
      is PeopleSyncOutcome.AuthorizationRequired,
      PeopleSyncOutcome.Forbidden,
      -> successResponse(contactsAuthorizationRequiredPayload())
      PeopleSyncOutcome.Cancelled -> cancelledResponse("user")
      is PeopleSyncOutcome.OwnershipBlocked -> conflictResponse(outcome.reason.wireCode)
      PeopleSyncOutcome.Offline -> temporaryResponse("network-offline")
      is PeopleSyncOutcome.RateLimited -> temporaryResponse(
        code = "contacts-stale",
        retryAfterSeconds = outcome.retryAfterSeconds,
      )
      is PeopleSyncOutcome.Partial,
      is PeopleSyncOutcome.Malformed,
      is PeopleSyncOutcome.BoundExceeded,
      PeopleSyncOutcome.NetworkFailure,
      is PeopleSyncOutcome.ServerFailure,
      PeopleSyncOutcome.StorageFailure,
      -> successResponse(contactsSyncPayload())
    }
  }

  private fun handleConfigurationOutcome(
    outcome: ConfigurationOutcome,
    automationProjection: Boolean = false,
  ): Any = when (outcome) {
    ConfigurationOutcome.InvalidRequest -> errorResponse("NATIVE_REQUEST_INVALID")
    is ConfigurationOutcome.Problem -> problemResponse(outcome.payload)
    is ConfigurationOutcome.Success -> {
      if (outcome.invalidatedAreas.isNotEmpty()) {
        emitInvalidation(outcome.invalidatedAreas.toList())
      }
      val payload = if (automationProjection) {
        automationPayload(appGraph.androidReadinessProbe.read())
      } else {
        outcome.payload
      }
      successResponse(payload)
    }
  }

  private fun contactsSyncPayload(): JSONObject = runBlocking {
    val account = appGraph.peopleSyncDao.activeAccount()
      ?: return@runBlocking contactsAuthorizationRequiredPayload()
    val state = appGraph.peopleSyncDao.contactSyncState(account.accountId)
      ?: return@runBlocking JSONObject().put("kind", "never-synced")
    state.stagingGeneration?.let { generationId ->
      val mode = appGraph.peopleSyncDao.generation(generationId)?.mode
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it == "full" || it == "incremental" }
        ?: "full"
      return@runBlocking JSONObject()
        .put("kind", "syncing")
        .put("mode", mode)
        .put("retainedGeneration", state.activeGeneration != null)
    }
    if (state.freshness == SyncFreshness.AUTH_ACTION_REQUIRED) {
      return@runBlocking contactsAuthorizationRequiredPayload()
    }
    val lastSuccess = listOfNotNull(
      state.lastFullSuccessMillis,
      state.lastIncrementalSuccessMillis,
    ).maxOrNull()
    if (state.freshness == SyncFreshness.NEVER_SYNCED && lastSuccess == null) {
      return@runBlocking if (state.lastErrorCode == null) {
        JSONObject().put("kind", "never-synced")
      } else {
        failedRetainedSyncPayload(null, state.lastErrorCode.toSafeSyncReason())
      }
    }
    val assessment = PeopleDataFreshnessPolicy.assess(
      state,
      trustedNowMillis(account.accountId),
    )
    when (assessment.band) {
      PeopleDataFreshnessBand.NORMAL -> if (lastSuccess != null && state.lastErrorCode == null) {
        JSONObject()
          .put("kind", "fresh")
          .put("completedAt", Instant.ofEpochMilli(lastSuccess).toString())
          .put("contactCount", appGraph.peopleSyncDao.activeContactCount(account.accountId))
      } else {
        failedRetainedSyncPayload(lastSuccess, state.lastErrorCode.toSafeSyncReason())
      }
      PeopleDataFreshnessBand.STALE_WARNING,
      PeopleDataFreshnessBand.SAFETY_PAUSED,
      PeopleDataFreshnessBand.UNTRUSTED,
      -> if (lastSuccess != null && lastSuccess >= 0) {
        JSONObject()
          .put("kind", "stale")
          .put("lastSuccessAt", Instant.ofEpochMilli(lastSuccess).toString())
          .put("reason", state.lastErrorCode.toSafeSyncReason())
      } else {
        failedRetainedSyncPayload(null, state.lastErrorCode.toSafeSyncReason())
      }
    }
  }

  private suspend fun trustedNowMillis(accountId: String): Long? = TrustedTimeEstimator.estimate(
    appGraph.automationOrchestrationDao.clockTrust(accountId),
    SystemClock.elapsedRealtime(),
    trustedBootCount(),
  )

  private fun trustedBootCount(): Int? = runCatching {
    Settings.Global.getInt(reactApplicationContext.contentResolver, Settings.Global.BOOT_COUNT)
  }.getOrNull()?.takeIf { it >= 0 }

  private fun peopleListPayload(request: JSONObject): JSONObject? = runBlocking {
    val account = appGraph.peopleSyncDao.activeAccount()
      ?: return@runBlocking JSONObject().put("items", JSONArray()).put("totalCount", 0)
    val query = request.optJSONObject("query") ?: return@runBlocking null
    val filter = query.optString("filter")
    if (filter !in PEOPLE_FILTERS) return@runBlocking null
    val pageSize = query.optInt("pageSize", -1)
    if (pageSize !in 1..MAX_CONTACT_PAGE_SIZE) return@runBlocking null
    val search = if (query.has("search")) query.optString("search") else ""
    if (search.length > MAX_CONTACT_SEARCH_LENGTH || search.any(::unsafeUiCharacter)) {
      return@runBlocking null
    }
    val cursor = if (query.has("cursor")) query.optString("cursor") else null
    val offset = parseCursor(cursor) ?: return@runBlocking null
    val pattern = if (search.isBlank()) "%" else "%${escapeLike(search.trim())}%"
    val contacts = appGraph.peopleSyncDao.contactPage(
      account.accountId,
      filter,
      pattern,
      pageSize,
      offset,
    )
    val total = appGraph.peopleSyncDao.contactCount(account.accountId, filter, pattern)
    val items = JSONArray()
    contacts.forEach { contact ->
      val summary = configurationController.contactDetail(contact.contactId)
        ?.optJSONObject("summary")
        ?: contactSummaryPayload(contact)
      items.put(summary)
    }
    JSONObject()
      .put("items", items)
      .put("totalCount", total)
      .apply {
        if (offset + contacts.size < total) put("nextCursor", "offset:${offset + contacts.size}")
      }
  }

  private suspend fun contactSummaryPayload(
    contact: ContactSnapshotEntity,
  ): JSONObject {
    val policy = appGraph.peopleSyncDao.recipientPolicy(contact.contactId)
    val phones = appGraph.peopleSyncDao.contactPhones(contact.contactId)
    val readyPhones = phones.filter { it.state == PhoneRecordState.READY }
    val selectedPhone = policy?.chosenPhoneId?.let { selected ->
      readyPhones.singleOrNull { it.phoneId == selected }
    } ?: readyPhones.singleOrNull()
    val reasons = buildList {
      if (contact.state != ContactSnapshotState.ACTIVE) add("source-contact-deleted")
      if (contact.birthdayMonth == null || contact.birthdayDay == null) add("birthday-missing")
      if (readyPhones.isEmpty()) add("phone-missing")
      if (readyPhones.size > 1 && selectedPhone == null) add("phone-choice-required")
      if (contact.safeGivenName == null) add("safe-given-name-missing")
    }.distinct()
    val readiness = when {
      contact.state != ContactSnapshotState.ACTIVE -> JSONObject()
        .put("kind", "unavailable")
        .put("reasons", JSONArray(reasons.ifEmpty { listOf("source-contact-deleted") }))
      reasons.isNotEmpty() -> JSONObject()
        .put("kind", "needs-attention")
        .put("reasons", JSONArray(reasons))
      else -> JSONObject().put("kind", "ready")
    }
    return JSONObject()
      .put("id", contact.contactId)
      .put("displayName", contact.displayName)
      .put("readiness", readiness)
      .put("enrollment", enrollmentPayload(contact, policy))
      .apply {
        birthdayLabel(contact)?.let { put("birthdayLabel", it) }
        selectedPhone?.maskedDisplay?.takeIf(::safeMaskedPhone)?.let { put("maskedPhone", it) }
      }
  }

  private suspend fun enrollmentPayload(
    contact: ContactSnapshotEntity,
    policy: com.yashsomani.birthdayautopilot.storage.database.RecipientPolicyEntity?,
  ): JSONObject {
    if (policy == null || policy.state == RecipientEnrollmentState.OFF) {
      return JSONObject().put("kind", "off")
    }
    if (policy.state == RecipientEnrollmentState.EXCLUDED) {
      return JSONObject().put("kind", "excluded")
    }
    val approval = policy.approvalId?.let { appGraph.peopleSyncDao.approval(it) }
    val approvalPayload = when {
      approval == null -> JSONObject().put("kind", "missing")
      approval.state == ApprovalRecordState.ACTIVE &&
        approval.contactMaterialRevision == contact.materialRevision -> JSONObject()
        .put("kind", "valid")
        .put("approvedAt", Instant.ofEpochMilli(approval.approvedAtMillis).toString())
      else -> JSONObject()
        .put("kind", "invalidated")
        .put("reasons", JSONArray(approval.invalidationReason.toApprovalReasons()))
    }
    return if (
      policy.state == RecipientEnrollmentState.ENABLED &&
      approvalPayload.optString("kind") == "valid"
    ) {
      JSONObject().put("kind", "enabled").put("approval", approvalPayload)
    } else {
      JSONObject()
        .put("kind", "paused")
        .put("reason", "approval-invalid")
        .put("approval", approvalPayload)
    }
  }

  private fun senderPayload(): JSONObject {
    val mode = try {
      runBlocking {
        appGraph.database.birthdayDao().getControl()?.accountMode
          ?.let { runCatching { AccountMode.valueOf(it) }.getOrNull() }
      }
    } catch (_: Exception) {
      null
    } ?: AccountMode.PAUSED_REPAIR
    val localDeviceLabel = reactApplicationContext.getString(R.string.sender_local_device)
    return when (mode) {
      AccountMode.TEST_ONLY -> JSONObject()
        .put("platform", "android").put("kind", "test-only").put("epochLabel", localDeviceLabel)
      AccountMode.PAUSED_REPAIR -> JSONObject()
        .put("platform", "android").put("kind", "paused-repair").put("epochLabel", localDeviceLabel)
      AccountMode.AUTOMATION_ACTIVE -> JSONObject()
        .put("platform", "android").put("kind", "automation-active").put("epochLabel", localDeviceLabel)
      AccountMode.STANDBY -> JSONObject()
        .put("platform", "android").put("kind", "standby")
        .put(
          "activeOtherDeviceLabel",
          reactApplicationContext.getString(R.string.sender_other_verified_android_device),
        )
      AccountMode.TRANSFER_PENDING -> JSONObject()
        .put("platform", "android").put("kind", "transfer-pending")
        .put("preissuedPermitMayFinish", true)
        .apply {
          runBlocking {
            appGraph.automationOrchestrationDao.activeAccount()?.let { account ->
              appGraph.automationOrchestrationDao.coordinationState(account.accountId)
                ?.transferDrainUntilMillis
            }
          }?.let { put("drainUntil", Instant.ofEpochMilli(it).toString()) }
        }
      AccountMode.DELETING -> JSONObject()
        .put("platform", "android").put("kind", "deleting")
        .put("preissuedPermitMayFinish", true)
        .apply {
          runBlocking {
            appGraph.automationOrchestrationDao.activeAccount()?.let { account ->
              appGraph.automationOrchestrationDao.coordinationState(account.accountId)
                ?.deletionDrainUntilMillis
            }
          }?.let { put("drainUntil", Instant.ofEpochMilli(it).toString()) }
        }
    }
  }

  /**
   * Setup progress is derived from durable native state rather than an in-memory phase counter.
   * Recipient/message/test configuration remains available in the main application after the
   * identity and first contact import safety boundary has completed.
   */
  private fun setupStep(snapshot: AndroidReadinessSnapshot): String {
    // LIMITED means this installation can safely plan, preview, and run a foreground test, but
    // still has reliability settings to repair before activation. Keeping LIMITED installs on the
    // compatibility step creates a dead end because those settings are intentionally repaired in
    // the final reliability step. Only a genuinely unsupported installation is blocked here.
    if (snapshot.eligibility.kind == EligibilityKind.UNSUPPORTED) return "compatibility"
    return try {
      runBlocking {
        val account = appGraph.peopleSyncDao.activeAccount()
        val identityReady = account != null &&
          account.state == AccountRecordState.ACTIVE &&
          account.activeSlot == 1 &&
          appGraph.identitySessionMatches(account)
        val state = account?.let { appGraph.peopleSyncDao.contactSyncState(it.accountId) }
        AndroidSetupStepResolver.resolve(
          eligibilitySupported = true,
          identityReady = identityReady,
          syncState = state,
        )
      }
    } catch (_: Exception) {
      "google-account"
    }
  }

  private fun emitInvalidation(areas: List<String>) {
    if (listeners.get() <= 0 || areas.isEmpty()) return
    val event = Arguments.createMap().apply {
      putString("revision", currentRevision())
      putArray(
        "areas",
        Arguments.createArray().apply { areas.distinct().forEach(::pushString) },
      )
    }
    reactApplicationContext.runOnUiQueueThread {
      if (listeners.get() <= 0) return@runOnUiQueueThread
      runCatching {
        reactApplicationContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit(INVALIDATION_EVENT, event)
      }
    }
  }

  private fun emitRouteAvailable() {
    if (listeners.get() <= 0) return
    val event = Arguments.createMap().apply { putString("kind", "available") }
    reactApplicationContext.runOnUiQueueThread {
      if (listeners.get() <= 0) return@runOnUiQueueThread
      runCatching {
        reactApplicationContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit(ROUTE_EVENT, event)
      }
    }
  }

  private fun reconnectRequiredAccountPayload() = JSONObject()
    .put("kind", "reconnect-required")
    .put(
      "issue",
      JSONObject()
        .put("id", "account-reconnect-required")
        .put("code", "account-reconnect-required")
        .put("severity", "blocking")
        .put("blocks", JSONArray(listOf("test", "activation", "birthday"))),
    )

  private fun failedRetainedSyncPayload(lastSuccessMillis: Long?, reason: String) = JSONObject()
    .put("kind", "failed-retained")
    .put("reason", reason)
    .apply { lastSuccessMillis?.let { put("lastSuccessAt", Instant.ofEpochMilli(it).toString()) } }

  private fun cancelledResponse(source: String) = problemResponse(
    JSONObject().put("kind", "cancelled").put("source", source),
  )

  private fun temporaryResponse(code: String, retryAfterSeconds: Long? = null) = problemResponse(
    JSONObject().put("kind", "temporarily-unavailable").put("code", code).apply {
      retryAfterSeconds?.takeIf { it in 0..86_400 }?.let { put("retryAfterSeconds", it) }
    },
  )

  private fun conflictResponse(code: String) = problemResponse(
    JSONObject().put("kind", "conflict").put("code", code),
  )

  private fun staleRevisionResponse() = problemResponse(
    JSONObject().put("kind", "stale-revision").put("latestRevision", currentRevision()),
  )

  private fun unsupportedResponse(code: String) = problemResponse(
    JSONObject().put("kind", "unsupported").put("code", code),
  )

  private fun problemResponse(problem: JSONObject) = response("error", problem)

  private fun String?.toSafeSyncReason(): String = when (this) {
    "NETWORK_OFFLINE", "CONTACTS_NETWORK_FAILURE", "CONTACTS_SERVER_FAILURE" -> "network-offline"
    "CONTACTS_PARTIAL_SYNC" -> "contacts-partial-sync"
    "CONTACTS_AUTHORIZATION_REQUIRED", "CONTACTS_PERMISSION_DENIED" ->
      "contacts-authorization-required"
    else -> "contacts-stale"
  }

  private fun String?.toApprovalReasons(): List<String> = buildList {
    val value = this@toApprovalReasons.orEmpty()
    if ("NAME" in value || "SOURCE" in value || value.isBlank()) add("name-changed")
    if ("BIRTHDAY" in value || "SOURCE" in value || value.isBlank()) add("birthday-changed")
    if ("PHONE" in value || "SOURCE" in value || value.isBlank()) add("phone-changed")
    if (isEmpty()) add("name-changed")
  }.distinct()

  private fun birthdayLabel(contact: ContactSnapshotEntity): String? {
    val month = contact.birthdayMonth ?: return null
    val day = contact.birthdayDay ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    val locale = appGraph.nativeLocaleProvider.current().presentationLocale
    return "${Month.of(month).getDisplayName(TextStyle.SHORT, locale)} $day"
  }

  private fun parseCursor(raw: String?): Int? {
    if (raw.isNullOrEmpty()) return 0
    if (!CURSOR_PATTERN.matches(raw)) return null
    return raw.substringAfter(':').toIntOrNull()?.takeIf { it in 0..MAX_CONTACT_OFFSET }
  }

  private fun escapeLike(value: String): String = value
    .replace("\\", "\\\\")
    .replace("%", "\\%")
    .replace("_", "\\_")

  private fun safeMaskedPhone(value: String): Boolean {
    val visibleDigits = value.count(Char::isDigit)
    return value.length in 1..64 && visibleDigits in 1..4 && '+' !in value && value.none(::unsafeUiCharacter)
  }

  private fun unsafeUiCharacter(char: Char): Boolean =
    char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt()

  private fun parseObject(raw: String): JSONObject? {
    if (raw.length > MAX_REQUEST_CHARS) return null
    return try {
      JSONObject(raw)
    } catch (_: Exception) {
      null
    }
  }

  private fun JSONObject.keyNames(): Set<String> = buildSet {
    val iterator = keys()
    while (iterator.hasNext()) add(iterator.next())
  }

  private fun isValidRevision(revision: String?): Boolean =
    revision == null || REVISION_PATTERN.matches(revision)

  private fun String?.configurationRevisionOrNull(): Long? =
    this?.takeIf(REVISION_PATTERN::matches)?.toLongOrNull()

  private companion object {
    const val CONTRACT_VERSION = 1
    const val INVALIDATION_EVENT = "BirthdayNativeInvalidated"
    const val ROUTE_EVENT = "BirthdayNativeRouteAvailable"
    const val CHARGE_DISCLOSURE_VERSION = "sms-carrier-charges-v1"
    const val MAX_REQUEST_CHARS = 65_536
    const val MAX_CONTACT_PAGE_SIZE = 50
    const val MAX_CONTACT_SEARCH_LENGTH = 256
    const val MAX_CONTACT_OFFSET = 100_000
    const val LEDGER_GENERATION = "birthday-ledger-v1"
    const val COORDINATION_POLICY_VERSION = 1
    const val READINESS_CONTRACT_VERSION = 1
    const val WORK_CANCELLATION_TIMEOUT_SECONDS = 15L
    const val PROCESS_EXIT_AFTER_WIPE_MILLIS = 2_000L
    val CURSOR_PATTERN = Regex("^offset:(0|[1-9][0-9]{0,5})$")
    val PEOPLE_FILTERS = setOf("all", "enabled", "ready", "needs-attention", "excluded")
    val REVISION_PATTERN = Regex("^(0|[1-9][0-9]*)$")
    val READINESS_WIRE_CODES = mapOf(
      "DISTRIBUTION_UNVERIFIED" to "distribution-channel-unapproved",
      "DEVICE_UNSUPPORTED" to "platform-unsupported",
      "ACCOUNT_REQUIRED" to "account-reconnect-required",
      "CONTACTS_AUTHORIZATION_REQUIRED" to "contacts-authorization-required",
      "CONTACTS_STALE" to "contacts-stale",
      "APPROVAL_REQUIRED" to "approval-missing",
      "TEST_REQUIRED" to "test-receipt-invalid",
      "AUTOMATION_PAUSED" to "policy-suspended",
      "NETWORK_UNAVAILABLE" to "network-offline",
      "COORDINATION_UNAVAILABLE" to "coordination-unavailable",
      "SCHEDULER_UNAVAILABLE" to "scheduler-delayed",
      "SMS_PERMISSION_MISSING" to "permission-denied",
      "SIM_UNAVAILABLE" to "no-active-sim",
      "BACKGROUND_RESTRICTED" to "background-restricted",
      "DOZE_NOT_ALLOWLISTED" to "doze-exemption-missing",
      "UNUSED_APP_RESTRICTION" to "unused-app-restrictions-unsafe",
      "DATA_SAVER_RESTRICTED" to "data-saver-restricted",
      "LOW_POWER_STANDBY_UNSAFE" to "low-power-standby-unsafe",
      "CLOCK_UNTRUSTED" to "clock-untrusted",
      "RESET_SAFETY_BLOCKED" to "reset-safety-blocked",
      "STORAGE_UNAVAILABLE" to "internal-contract-invalid",
      "IOS_USER_CONFIRMATION_REQUIRED" to "platform-composer-only",
      "IOS_COMPOSER_RESERVED" to "ios-composer-reserved",
      "UPDATE_REQUIRED" to "platform-unsupported",
    )
    val SAFE_REASON_CODES = READINESS_WIRE_CODES.values.toSet() + setOf(
      "internal-contract-invalid",
      "test-receipt-invalid",
      "transfer-pending",
      "firebase-account-deleting",
      "permission-permanently-denied",
      "phone-state-permission-permanently-denied",
      "sms-permission-permanently-denied",
    )
    val PROJECTION_AREAS = setOf(
      "bootstrap",
      "setup",
      "home",
      "eligibility",
      "readiness",
      "account",
      "contacts",
      "messages",
      "automation",
      "activity",
      "privacy",
    )
    val SUPPORTED_PROJECTION_AREAS = PROJECTION_AREAS + setOf("route", "notifications")
    val USER_INTENTS = setOf(
      "activate",
      "authorize-contacts",
      "block-recipient-destination",
      "choose-birthday",
      "choose-phone",
      "confirm-approvals",
      "confirm-enrollment",
      "confirm-privacy-action",
      "confirm-today-occurrence",
      "continue-with-google",
      "exclude-recipient",
      "generate-suggestions",
      "pause-all",
      "pause-recipient",
      "perform-native-action",
      "prepare-activation",
      "prepare-approvals",
      "prepare-enrollment-review",
      "prepare-privacy-action",
      "prepare-resume",
      "prepare-test",
      "prepare-today-occurrence",
      "preview-diagnostics",
      "preview-message",
      "preview-policy",
      "refresh-compatibility",
      "restore-recipient",
      "resume",
      "save-message",
      "save-policy",
      "share-diagnostics",
      "start-test",
      "sync-contacts",
      "unblock-recipient-destination",
      "request-notification-permission",
      "open-notification-settings",
      "prepare-sender-transfer",
      "begin-sender-transfer",
      "complete-sender-transfer",
      "resume-lifecycle-operation",
      "repair-lifecycle-state",
      "check-account-deletion-status",
    )
    val JOURNAL_UNREADABLE_ALLOWED_INTENTS = setOf(
      "continue-with-google",
      "pause-all",
      "perform-native-action",
      "preview-diagnostics",
      "refresh-compatibility",
      "request-notification-permission",
      "open-notification-settings",
      "share-diagnostics",
      "repair-lifecycle-state",
      "check-account-deletion-status",
    )
    val LIFECYCLE_OPERATION_ALLOWED_INTENTS = setOf(
      "complete-sender-transfer",
      "continue-with-google",
      "pause-all",
      "perform-native-action",
      "preview-diagnostics",
      "refresh-compatibility",
      "request-notification-permission",
      "open-notification-settings",
      "resume-lifecycle-operation",
      "repair-lifecycle-state",
      "check-account-deletion-status",
      "share-diagnostics",
    )
    val DELETION_RECEIPT_ALLOWED_INTENTS = setOf(
      "continue-with-google",
      "pause-all",
      "perform-native-action",
      "preview-diagnostics",
      "refresh-compatibility",
      "request-notification-permission",
      "open-notification-settings",
      "resume-lifecycle-operation",
      "repair-lifecycle-state",
      "check-account-deletion-status",
      "share-diagnostics",
    )
    val DELETION_RECOVERY_IDENTITY_DEPENDENT_INTENTS = setOf(
      "continue-with-google",
      "pause-all",
      "resume-lifecycle-operation",
      "repair-lifecycle-state",
      "check-account-deletion-status",
    )
  }
}
