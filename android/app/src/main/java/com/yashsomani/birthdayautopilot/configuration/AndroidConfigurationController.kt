package com.yashsomani.birthdayautopilot.configuration

import android.content.Context
import androidx.room.withTransaction
import com.yashsomani.birthdayautopilot.BuildConfig
import com.yashsomani.birthdayautopilot.automation.sms.AndroidSmsPolicyBinding
import com.yashsomani.birthdayautopilot.approvals.AndroidSmsApprovalPlanner
import com.yashsomani.birthdayautopilot.approvals.ApprovalBuildResult
import com.yashsomani.birthdayautopilot.approvals.ApprovalMaterial
import com.yashsomani.birthdayautopilot.approvals.ApprovedBirthdayRecurrence
import com.yashsomani.birthdayautopilot.approvals.ApprovedLatePolicy
import com.yashsomani.birthdayautopilot.approvals.ApprovedRenderedMessage
import com.yashsomani.birthdayautopilot.approvals.ApprovedSegmentPlan
import com.yashsomani.birthdayautopilot.approvals.ApprovedSendWindow
import com.yashsomani.birthdayautopilot.approvals.ApprovedSimPolicy
import com.yashsomani.birthdayautopilot.approvals.ApprovedSimPolicyKind
import com.yashsomani.birthdayautopilot.approvals.ImmutableApprovalSnapshotFactory
import com.yashsomani.birthdayautopilot.approvals.SmsApprovalPlanResult
import com.yashsomani.birthdayautopilot.contacts.CanonicalPhoneNumber
import com.yashsomani.birthdayautopilot.contacts.LibPhoneNumberMetadataEngine
import com.yashsomani.birthdayautopilot.contacts.PhoneLabel
import com.yashsomani.birthdayautopilot.contacts.PhoneNormalizer
import com.yashsomani.birthdayautopilot.contacts.RawContactPhone
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.coordination.DistributionChannel
import com.yashsomani.birthdayautopilot.gemini.AndroidGeminiSuggestionGateway
import com.yashsomani.birthdayautopilot.gemini.GeminiCandidateProvenance
import com.yashsomani.birthdayautopilot.gemini.GeminiProvenanceDraft
import com.yashsomani.birthdayautopilot.localization.AndroidNativeLocaleProvider
import com.yashsomani.birthdayautopilot.messages.AndroidSmsManagerPlanSource
import com.yashsomani.birthdayautopilot.messages.BuiltInMessageTemplates
import com.yashsomani.birthdayautopilot.messages.MessageLanguage
import com.yashsomani.birthdayautopilot.messages.MessageTemplate
import com.yashsomani.birthdayautopilot.messages.MessageTemplateValidator
import com.yashsomani.birthdayautopilot.messages.NativeSmsPlanResult
import com.yashsomani.birthdayautopilot.messages.SmsEncoding
import com.yashsomani.birthdayautopilot.messages.SmsPlatformPlanSource
import com.yashsomani.birthdayautopilot.messages.TemplatePlaceholderMode
import com.yashsomani.birthdayautopilot.messages.TemplateSource as DomainTemplateSource
import com.yashsomani.birthdayautopilot.messages.TemplateValidationError
import com.yashsomani.birthdayautopilot.planning.BirthdayRule
import com.yashsomani.birthdayautopilot.planning.LeapDayPolicy
import com.yashsomani.birthdayautopilot.planning.RecurrencePlanner
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.ActivityEntity
import com.yashsomani.birthdayautopilot.storage.database.ApprovalRecordState
import com.yashsomani.birthdayautopilot.storage.database.ApprovalSnapshotEntity
import com.yashsomani.birthdayautopilot.storage.database.AutomationPolicyEntity
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.ClockTrustStatus
import com.yashsomani.birthdayautopilot.storage.database.ConfigurationDao
import com.yashsomani.birthdayautopilot.storage.database.ConfigurationReviewEntity
import com.yashsomani.birthdayautopilot.storage.database.ConfiguredBirthdayRow
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitEntity
import com.yashsomani.birthdayautopilot.storage.database.ConsentDecision
import com.yashsomani.birthdayautopilot.storage.database.ConsentKind
import com.yashsomani.birthdayautopilot.storage.database.ConsentReceiptEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactBirthdayChoiceEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactPhoneEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotState
import com.yashsomani.birthdayautopilot.storage.database.ControlEntity
import com.yashsomani.birthdayautopilot.storage.database.DestinationBlockEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.MessageTemplateEntity
import com.yashsomani.birthdayautopilot.storage.database.PhoneRecordState
import com.yashsomani.birthdayautopilot.storage.database.PolicyRecordState
import com.yashsomani.birthdayautopilot.storage.database.PeopleDataFreshnessPolicy
import com.yashsomani.birthdayautopilot.storage.database.RecipientEnrollmentState
import com.yashsomani.birthdayautopilot.storage.database.RecipientPolicyEntity
import com.yashsomani.birthdayautopilot.storage.database.ReconcileHeartbeatPolicy
import com.yashsomani.birthdayautopilot.storage.database.ResetSafetyStatus
import com.yashsomani.birthdayautopilot.storage.database.TemplateSource
import com.yashsomani.birthdayautopilot.storage.database.TemplateValidationState
import com.yashsomani.birthdayautopilot.storage.database.TestJobEntity
import com.yashsomani.birthdayautopilot.storage.database.TestReceiptEntity
import com.yashsomani.birthdayautopilot.storage.database.SafetyLedgerDao
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.people.StablePrivateId
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class DurableConfigurationReadiness(
  val accountMode: AccountMode?,
  val contactsFresh: Boolean,
  val approvalsReady: Boolean,
  val passingTestReceipt: Boolean,
  val clockTrusted: Boolean,
  val resetSafetyClear: Boolean,
  val lastCoordinationSuccessMillis: Long?,
)

internal sealed interface TestStartOutcome {
  data class Ready(
    val testJobId: String,
    val foregroundConfirmationNonceHash: String,
  ) : TestStartOutcome

  data class Rejected(val outcome: ConfigurationOutcome) : TestStartOutcome
}

internal data class AccountModeCoordinationMaterial(
  val reviewHandle: String?,
  val installationId: String,
  val senderEpoch: Long,
  val resetGeneration: Long,
  val ownerLeaseUntilMillis: Long?,
  val testClaimId: String? = null,
  val boundTestReceiptPrehash: String? = null,
)

internal sealed interface AccountModePreparationOutcome {
  data class Ready(val material: AccountModeCoordinationMaterial) : AccountModePreparationOutcome
  data class Rejected(val outcome: ConfigurationOutcome) : AccountModePreparationOutcome
}

internal data class ConfigurationBuildSignals(
  val versionCode: Long,
  val distributionChannel: DistributionChannel,
  val signingCertificateSha256: String,
)

internal fun interface ConfigurationBuildSignalSource {
  fun read(): ConfigurationBuildSignals
}

internal class AndroidConfigurationController(
  context: Context,
  private val database: BirthdayDatabase,
  private val recurrencePlanner: RecurrencePlanner,
  private val clock: ConfigurationClock = ConfigurationClock(System::currentTimeMillis),
  private val zoneProvider: ConfigurationZoneProvider = ConfigurationZoneProvider(ZoneId::systemDefault),
  private val nativeLocaleProvider: AndroidNativeLocaleProvider = AndroidNativeLocaleProvider(context),
  private val subscriptionResolver: ConfigurationSubscriptionResolver =
    AndroidConfigurationSubscriptionResolver(context),
  smsPlanSource: SmsPlatformPlanSource = AndroidSmsManagerPlanSource(context),
  private val buildSignalSource: ConfigurationBuildSignalSource = ConfigurationBuildSignalSource {
    ConfigurationBuildSignals(
      versionCode = BuildConfig.VERSION_CODE.toLong(),
      distributionChannel = when (BuildConfig.APP_ENV) {
        "dev" -> DistributionChannel.DEV
        "staging" -> DistributionChannel.STAGING
        "lab" -> DistributionChannel.RESTRICTED_LAB
        "prod" -> if (BuildConfig.APPROVED_DISTRIBUTION_CHANNEL == "google-play") {
          DistributionChannel.PLAY
        } else {
          DistributionChannel.DIRECT_MANAGED
        }
        else -> DistributionChannel.DEV
      },
      signingCertificateSha256 = BuildConfig.APPROVED_SIGNING_CERTIFICATE_SHA256,
    )
  },
  private val geminiGateway: AndroidGeminiSuggestionGateway? = null,
  private val accountSessionMatches: (AccountRecordEntity) -> Boolean = { true },
  private val subscriptionChangePending: () -> Boolean = { false },
  private val trustedNowProvider: suspend (AccountRecordEntity) -> Long? = { null },
) {
  private val appContext = context.applicationContext
  private val dao: ConfigurationDao = database.configurationDao()
  private val ledger: SafetyLedgerDao = database.safetyLedgerDao()
  private val templateValidator = MessageTemplateValidator()
  private val approvalPlanner = AndroidSmsApprovalPlanner(smsPlanSource)
  private val testSmsPlanSource = smsPlanSource
  private val testPhoneNormalizer = PhoneNormalizer(LibPhoneNumberMetadataEngine())

  suspend fun durableReadiness(): DurableConfigurationReadiness {
    val account = dao.activeAccount()
      ?: return DurableConfigurationReadiness(null, false, false, false, false, false, null)
    val control = dao.control()
    val enabled = dao.enabledRecipientCount(account.accountId)
    val trustedNowMillis = runCatching { trustedNowProvider(account) }.getOrNull()
    return DurableConfigurationReadiness(
      accountMode = control?.accountMode?.let { runCatching { AccountMode.valueOf(it) }.getOrNull() },
      contactsFresh = PeopleDataFreshnessPolicy.allowsUnattendedAutomation(
        dao.syncState(account.accountId),
        trustedNowMillis,
      ),
      approvalsReady = enabled > 0 && dao.unreadyConfiguredRecipientCount(account.accountId) == 0,
      passingTestReceipt = passingTestReceipt(account),
      clockTrusted = dao.clockTrust(account.accountId)?.status == ClockTrustStatus.TRUSTED,
      resetSafetyClear = dao.resetSafety(account.accountId)?.status == ResetSafetyStatus.CLEAR,
      lastCoordinationSuccessMillis = dao.coordinationState(account.accountId)
        ?.lastSuccessfulCoordinationMillis,
    )
  }

  suspend fun initialActivationCompleted(): Boolean =
    dao.control()?.initialActivationCompleted == true

  suspend fun prepareActivation(resume: Boolean): ConfigurationOutcome {
    if (subscriptionChangePending()) return conflict("sim-changed")
    val account = dao.activeAccount() ?: return conflict("account-reconnect-required")
    val control = dao.control() ?: return internal("CONFIGURATION_CONTROL_MISSING")
    val mode = control.accountMode.let { runCatching { AccountMode.valueOf(it) }.getOrNull() }
      ?: return internal("CONFIGURATION_CONTROL_INVALID")
    ConfigurationOwnershipPolicy.blockedReason(mode)?.let { return conflict(it) }
    if (mode !in setOf(AccountMode.TEST_ONLY, AccountMode.PAUSED_REPAIR)) {
      return conflict(if (mode == AccountMode.TRANSFER_PENDING) "transfer-pending" else "policy-suspended")
    }
    if (resume && mode != AccountMode.PAUSED_REPAIR) return conflict("policy-suspended")
    val binding = currentActivationBinding(account)
      ?: return conflict("test-receipt-invalid")
    val policy = dao.activeAutomationPolicy(account.accountId)
      ?: return validation("window", "invalid-window")
    capacityIssue(account.accountId, policy = policy)?.let { return it }
    val approval = dao.latestActiveApproval(account.accountId)
      ?: return conflict("approval-missing")
    val subscription = subscriptionResolver.resolveDefault() as? SubscriptionResolution.Ready
      ?: return conflict("no-active-sim")
    if (subscription.subscriptionId != policy.resolvedSubscriptionId) {
      return conflict("sim-changed")
    }
    val enabled = dao.enabledRecipientCount(account.accountId)
    val attention = dao.unreadyConfiguredRecipientCount(account.accountId)
    if (enabled <= 0 || attention != 0) return conflict("approval-invalid")
    val kind = if (resume) REVIEW_RESUME else REVIEW_ACTIVATION
    val payload = JSONObject()
      .put("installationId", binding.installation.installationId)
      .put("senderEpoch", binding.installation.senderEpoch)
      .put("resetGeneration", binding.installation.resetGeneration)
      .put("testJobId", binding.test.testJobId)
      .put("testClaimId", binding.permit.opaqueClaimId)
      .put("receiptBindingHash", binding.receipt.bindingHash)
      .put("policyId", policy.policyId)
      .put("policyRevision", policy.revision)
      .put("approvalId", approval.approvalId)
      .put("enabledRecipientCount", enabled)
      .put("attentionCount", attention)
      .put("resolvedSubscriptionId", subscription.subscriptionId)
    val handle = createReview(kind, payload, account, control)
      ?: return internal("CONFIGURATION_REVIEW_CREATE_FAILED")
    return ConfigurationOutcome.Success(
      JSONObject()
        .put("platform", "android")
        .put("handle", handle)
        .put("enabledRecipientCount", enabled)
        .put("attentionCount", attention)
        .put("templatePreview", approval.exactMessage)
        .put("windowLabel", policyWindowLabel(policy))
        .put("simLabel", subscription.label)
        .put("dailyCap", policy.dailyCap)
        .put("limitationsDisclosure", ACTIVATION_LIMITATIONS),
    )
  }

  suspend fun activationCoordinationMaterial(
    request: JSONObject,
    expectedRevision: Long,
    resume: Boolean,
  ): AccountModePreparationOutcome {
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("handle", "expectedRevision")
    ) return AccountModePreparationOutcome.Rejected(ConfigurationOutcome.InvalidRequest)
    val handle = request.optString("handle")
    if (!validHandle(handle, if (resume) "rs" else "ac")) {
      return AccountModePreparationOutcome.Rejected(ConfigurationOutcome.InvalidRequest)
    }
    val kind = if (resume) REVIEW_RESUME else REVIEW_ACTIVATION
    val loaded = loadReview(handle, kind, expectedRevision)
      ?: return AccountModePreparationOutcome.Rejected(reviewProblem(handle, expectedRevision))
    val (review, payload, control) = loaded
    if (payload.keyNames() != ACTIVATION_REVIEW_KEYS) {
      return AccountModePreparationOutcome.Rejected(internal("CONFIGURATION_REVIEW_CORRUPT"))
    }
    val account = dao.activeAccount()
      ?: return AccountModePreparationOutcome.Rejected(conflict("account-reconnect-required"))
    val mode = runCatching { AccountMode.valueOf(control.accountMode) }.getOrNull()
    if (mode !in setOf(AccountMode.TEST_ONLY, AccountMode.PAUSED_REPAIR) ||
      (resume && mode != AccountMode.PAUSED_REPAIR)
    ) return AccountModePreparationOutcome.Rejected(conflict("policy-suspended"))
    val binding = currentActivationBinding(account)
      ?: return AccountModePreparationOutcome.Rejected(conflict("test-receipt-invalid"))
    val policy = dao.activeAutomationPolicy(account.accountId)
      ?: return AccountModePreparationOutcome.Rejected(conflict("approval-invalid"))
    capacityIssue(account.accountId, policy = policy)?.let {
      return AccountModePreparationOutcome.Rejected(it)
    }
    val approval = dao.latestActiveApproval(account.accountId)
      ?: return AccountModePreparationOutcome.Rejected(conflict("approval-missing"))
    val enabled = dao.enabledRecipientCount(account.accountId)
    val attention = dao.unreadyConfiguredRecipientCount(account.accountId)
    val valid = review.accountId == account.accountId &&
      payload.optString("installationId") == binding.installation.installationId &&
      payload.optLong("senderEpoch", Long.MIN_VALUE) == binding.installation.senderEpoch &&
      payload.optLong("resetGeneration", Long.MIN_VALUE) == binding.installation.resetGeneration &&
      payload.optString("testJobId") == binding.test.testJobId &&
      payload.optString("testClaimId") == binding.permit.opaqueClaimId &&
      ConfigurationCanonicalHash.matches(
        payload.optString("receiptBindingHash"),
        binding.receipt.bindingHash,
      ) &&
      payload.optString("policyId") == policy.policyId &&
      payload.optLong("policyRevision", Long.MIN_VALUE) == policy.revision &&
      payload.optString("approvalId") == approval.approvalId &&
      payload.optInt("enabledRecipientCount", -1) == enabled &&
      payload.optInt("attentionCount", -1) == attention &&
      payload.optInt("resolvedSubscriptionId", -1) == policy.resolvedSubscriptionId &&
      enabled > 0 && attention == 0
    if (!valid) return AccountModePreparationOutcome.Rejected(stale(control.revision))
    return AccountModePreparationOutcome.Ready(
      AccountModeCoordinationMaterial(
        reviewHandle = handle,
        installationId = binding.installation.installationId,
        senderEpoch = checkNotNull(binding.installation.senderEpoch),
        resetGeneration = binding.installation.resetGeneration,
        ownerLeaseUntilMillis = binding.installation.ownerLeaseUntilMillis,
        testClaimId = binding.permit.opaqueClaimId,
        boundTestReceiptPrehash = binding.receipt.bindingHash,
      ),
    )
  }

  suspend fun commitActivation(
    handle: String,
    expectedRevision: Long,
    resume: Boolean,
  ): ConfigurationOutcome = database.withTransaction {
    val kind = if (resume) REVIEW_RESUME else REVIEW_ACTIVATION
    val loaded = loadReview(handle, kind, expectedRevision)
      ?: return@withTransaction reviewProblem(handle, expectedRevision)
    val (review, _, control) = loaded
    val account = dao.activeAccount() ?: return@withTransaction conflict("account-reconnect-required")
    val binding = currentActivationBinding(account)
      ?: return@withTransaction conflict("test-receipt-invalid")
    capacityIssue(account.accountId)?.let { return@withTransaction it }
    if (dao.unreadyConfiguredRecipientCount(account.accountId) != 0 ||
      dao.enabledRecipientCount(account.accountId) <= 0
    ) return@withTransaction conflict("approval-invalid")
    val installation = binding.installation
    val epoch = checkNotNull(installation.senderEpoch)
    val at = now()
    if (dao.markReviewConsumed(
        review.reviewId,
        kind,
        review.controlRevision,
        review.blockerRevision,
        at,
      ) != 1
    ) return@withTransaction conflict("approval-invalid")
    check(
      dao.markAutomationActivated(
        control.revision,
        control.blockerRevision,
      ) == 1,
    ) { "activation-control-cas-failed" }
    check(
      dao.updateInstallationMode(
        installation.installationId,
        epoch,
        AccountMode.AUTOMATION_ACTIVE,
        installation.ownerLeaseUntilMillis,
        at,
      ) == 1,
    ) { "activation-installation-cas-failed" }
    check(
      dao.updateCoordinationMode(
        account.accountId,
        installation.installationId,
        epoch,
        AccountMode.AUTOMATION_ACTIVE,
        installation.ownerLeaseUntilMillis,
        null,
        at,
      ) == 1,
    ) { "activation-coordination-cas-failed" }
    database.birthdayDao().insertActivity(
      ActivityEntity(
        activityId = "activity_${UUID.randomUUID().toString().replace("-", "")}",
        category = "SETTINGS_CHANGED",
        safeCode = if (resume) "AUTOMATION_RESUMED" else "AUTOMATION_ACTIVATED",
        recordedAtMillis = at,
        relatedOccurrenceId = null,
      ),
    )
    ConfigurationOutcome.Success(JSONObject(), CONFIG_INVALIDATIONS + "activity")
  }

  suspend fun pauseAll(
    request: JSONObject,
    expectedRevision: Long,
  ): AccountModePreparationOutcome = database.withTransaction {
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("expectedRevision")
    ) return@withTransaction AccountModePreparationOutcome.Rejected(ConfigurationOutcome.InvalidRequest)
    val control = dao.control()
      ?: return@withTransaction AccountModePreparationOutcome.Rejected(
        internal("CONFIGURATION_CONTROL_MISSING"),
      )
    if (control.revision != expectedRevision) {
      return@withTransaction AccountModePreparationOutcome.Rejected(stale(control.revision))
    }
    val account = dao.activeAccount()
      ?: return@withTransaction AccountModePreparationOutcome.Rejected(
        conflict("account-reconnect-required"),
      )
    val installation = dao.activeInstallation(account.accountId)
      ?: return@withTransaction AccountModePreparationOutcome.Rejected(
        conflict("account-reconnect-required"),
      )
    val epoch = installation.senderEpoch
      ?: return@withTransaction AccountModePreparationOutcome.Rejected(
        conflict("account-reconnect-required"),
      )
    val at = now()
    check(
      dao.updateAutomationControl(
        control.revision,
        control.blockerRevision,
        false,
        AccountMode.PAUSED_REPAIR,
      ) == 1,
    ) { "pause-control-cas-failed" }
    check(
      dao.updateInstallationMode(
        installation.installationId,
        epoch,
        AccountMode.PAUSED_REPAIR,
        null,
        at,
      ) == 1,
    ) { "pause-installation-cas-failed" }
    check(
      dao.updateCoordinationMode(
        account.accountId,
        installation.installationId,
        epoch,
        AccountMode.PAUSED_REPAIR,
        null,
        "PAUSE_SERVER_SYNC_PENDING",
        at,
      ) == 1,
    ) { "pause-coordination-cas-failed" }
    database.birthdayDao().insertActivity(
      ActivityEntity(
        activityId = "activity_${UUID.randomUUID().toString().replace("-", "")}",
        category = "PAUSED",
        safeCode = "AUTOMATION_PAUSED",
        recordedAtMillis = at,
        relatedOccurrenceId = null,
      ),
    )
    AccountModePreparationOutcome.Ready(
      AccountModeCoordinationMaterial(
        reviewHandle = null,
        installationId = installation.installationId,
        senderEpoch = epoch,
        resetGeneration = installation.resetGeneration,
        ownerLeaseUntilMillis = installation.ownerLeaseUntilMillis,
      ),
    )
  }

  /**
   * Builds a short-lived, encrypted review for the one explicit foreground Test. The raw input is
   * never returned after normalization and never leaves the device; cloud coordination receives
   * only independently domain-separated prehashes.
   */
  suspend fun prepareTest(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (subscriptionChangePending()) return conflict("sim-changed")
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("destination", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val rawDestination = request.optString("destination")
    val digitCount = rawDestination.count(Char::isDigit)
    if (
      rawDestination.length !in 7..32 ||
      digitCount !in 7..15 ||
      !TEST_PHONE_INPUT.matches(rawDestination)
    ) return validation("phone", "phone-invalid")

    val account = dao.activeAccount() ?: return conflict("account-reconnect-required")
    val control = dao.control() ?: return internal("CONFIGURATION_CONTROL_MISSING")
    if (control.revision != expectedRevision) return stale(control.revision)
    senderOwnershipProblem(control)?.let { return it }
    if (!testModeAllowed(control.accountMode)) return conflict("policy-suspended")
    val template = dao.activeTemplate(account.accountId)
      ?: return validation("template", "template-empty")
    val policy = dao.activeAutomationPolicy(account.accountId)
      ?: return validation("window", "invalid-window")
    val installation = dao.activeInstallation(account.accountId)
      ?: return temporary("coordination-unavailable")
    val senderEpoch = installation.senderEpoch
      ?: return temporary("coordination-unavailable")
    val subscription = when (val resolved = subscriptionResolver.resolveDefault()) {
      is SubscriptionResolution.Ready -> resolved
      is SubscriptionResolution.Rejected -> return validation("sim", resolved.code)
    }
    if (policy.resolvedSubscriptionId != subscription.subscriptionId) {
      return validation("sim", "sim-changed")
    }
    val buildBinding = currentBuildBindingOrNull()
      ?: return unsupported("distribution-channel-unapproved")
    if (!installationMatchesBuild(installation, senderEpoch, buildBinding)) {
      return conflict("account-reconnect-required")
    }

    val normalized = normalizeTestDestination(
      rawDestination,
      nativeLocaleProvider.current().phoneRegion,
    )
      ?: return validation("phone", "phone-invalid")
    val destinationFingerprint = StablePrivateId.hash(
      "Destination.v1",
      account.accountId,
      normalized.canonical.value,
    )
    if (dao.activeDestinationBlockCount(account.accountId, destinationFingerprint) != 0) {
      return validation("phone", "phone-blocked-form")
    }
    val nativePlan = when (
      val result = testSmsPlanSource.plan(TEST_MESSAGE, subscription.subscriptionId)
    ) {
      is NativeSmsPlanResult.Planned -> result.plan
      is NativeSmsPlanResult.Rejected -> return validation("template", "invalid-segment-cap")
    }
    val segmentPlan = ApprovedSegmentPlan.bind(
      exactText = TEST_MESSAGE,
      encoding = nativePlan.encoding,
      orderedParts = nativePlan.orderedParts,
      approvedSegmentCap = 2,
    ) ?: return validation("template", "invalid-segment-cap")
    if (segmentPlan.segmentCount !in 1..2) {
      return validation("template", "invalid-segment-cap")
    }

    val configHash = AndroidTestConfigurationBinding.configurationHash(
      template,
      policy,
      subscription.subscriptionId,
      AndroidSmsPolicyBinding.VERSION,
    )
    val destinationPrehash = StablePrivateId.hash(
      "BirthdayAutopilot.TestDestination.v1",
      account.accountId,
      normalized.canonical.value,
    )
    val payloadHash = testPayloadHash(
      configHash = configHash,
      destinationPrehash = destinationPrehash,
      exactMessage = TEST_MESSAGE,
      resolvedSubscriptionId = subscription.subscriptionId,
      messageEncoding = segmentPlan.encoding.name,
      segmentCount = segmentPlan.segmentCount,
      orderedPartsHash = segmentPlan.orderedPartsHash,
      buildBindingHash = buildBinding.hash,
    )
    val reviewPayload = JSONObject()
      .put("normalizedDestination", normalized.canonical.value)
      .put("maskedDestination", normalized.maskedDisplay)
      .put("destinationFingerprint", destinationFingerprint)
      .put("destinationPrehash", destinationPrehash)
      .put("exactMessage", TEST_MESSAGE)
      .put("resolvedSubscriptionId", subscription.subscriptionId)
      .put("simLabel", subscription.label)
      .put("messageEncoding", segmentPlan.encoding.name)
      .put("segmentCount", segmentPlan.segmentCount)
      .put("orderedParts", JSONArray(nativePlan.orderedParts))
      .put("orderedPartsHash", segmentPlan.orderedPartsHash)
      .put("configHash", configHash)
      .put("payloadHash", payloadHash)
      .put("buildBindingHash", buildBinding.hash)
      .put("installationId", installation.installationId)
      .put("senderEpoch", senderEpoch)
    val handle = createReview(REVIEW_TEST, reviewPayload, account, control)
      ?: return internal("CONFIGURATION_REVIEW_CREATE_FAILED")
    return ConfigurationOutcome.Success(
      JSONObject()
        .put("platform", "android")
        .put("handle", handle)
        .put("maskedDestination", normalized.maskedDisplay)
        .put("exactText", TEST_MESSAGE)
        .put("simLabel", subscription.label)
        .put("segmentCount", segmentPlan.segmentCount)
        .put("chargeDisclosure", TEST_CHARGE_DISCLOSURE),
    )
  }

  /** Verifies the review before showing either dangerous-permission system prompt. */
  suspend fun preflightTestStart(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (subscriptionChangePending()) return conflict("sim-changed")
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("handle", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val handle = request.optString("handle")
    if (!validHandle(handle, "tr")) return ConfigurationOutcome.InvalidRequest
    return if (loadReview(handle, REVIEW_TEST, expectedRevision) == null) {
      reviewProblem(handle, expectedRevision)
    } else {
      ConfigurationOutcome.Success(JSONObject())
    }
  }

  /** Consumes a Test review exactly once and atomically persists its immutable local ledger row. */
  suspend fun startTest(
    request: JSONObject,
    expectedRevision: Long,
  ): TestStartOutcome {
    if (subscriptionChangePending()) {
      return TestStartOutcome.Rejected(conflict("sim-changed"))
    }
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("handle", "expectedRevision")
    ) return TestStartOutcome.Rejected(ConfigurationOutcome.InvalidRequest)
    val handle = request.optString("handle")
    if (!validHandle(handle, "tr")) {
      return TestStartOutcome.Rejected(ConfigurationOutcome.InvalidRequest)
    }
    return database.withTransaction {
      val material = loadReview(handle, REVIEW_TEST, expectedRevision)
        ?: return@withTransaction TestStartOutcome.Rejected(
          reviewProblem(handle, expectedRevision),
        )
      val (review, payload, control) = material
      if (payload.keyNames() != TEST_REVIEW_KEYS) {
        return@withTransaction rejectedInternal("CONFIGURATION_REVIEW_CORRUPT")
      }
      if (!testModeAllowed(control.accountMode)) {
        return@withTransaction rejectedConflict("policy-suspended")
      }
      val template = dao.activeTemplate(review.accountId)
        ?: return@withTransaction rejectedStale(control.revision)
      val policy = dao.activeAutomationPolicy(review.accountId)
        ?: return@withTransaction rejectedStale(control.revision)
      val installation = dao.activeInstallation(review.accountId)
        ?: return@withTransaction rejectedTemporary("coordination-unavailable")
      val senderEpoch = installation.senderEpoch
        ?: return@withTransaction rejectedTemporary("coordination-unavailable")
      val subscription = when (val resolved = subscriptionResolver.resolveDefault()) {
        is SubscriptionResolution.Ready -> resolved
        is SubscriptionResolution.Rejected -> return@withTransaction TestStartOutcome.Rejected(
          validation("sim", resolved.code),
        )
      }
      if (policy.resolvedSubscriptionId != subscription.subscriptionId) {
        return@withTransaction TestStartOutcome.Rejected(validation("sim", "sim-changed"))
      }
      val buildBinding = currentBuildBindingOrNull()
        ?: return@withTransaction TestStartOutcome.Rejected(
          unsupported("distribution-channel-unapproved"),
        )
      if (
        !installationMatchesBuild(installation, senderEpoch, buildBinding) ||
        installation.installationId != payload.optString("installationId") ||
        senderEpoch != payload.optLong("senderEpoch", Long.MIN_VALUE)
      ) return@withTransaction rejectedConflict("account-reconnect-required")

      val normalizedDestination = payload.optString("normalizedDestination")
      val normalized = normalizeTestDestination(normalizedDestination, null)
        ?: return@withTransaction rejectedInternal("CONFIGURATION_REVIEW_CORRUPT")
      if (
        !constantTextEquals(normalized.canonical.value, normalizedDestination) ||
        !constantTextEquals(normalized.maskedDisplay, payload.optString("maskedDestination"))
      ) return@withTransaction rejectedInternal("CONFIGURATION_REVIEW_CORRUPT")
      val destinationFingerprint = StablePrivateId.hash(
        "Destination.v1",
        review.accountId,
        normalizedDestination,
      )
      if (!ConfigurationCanonicalHash.matches(
          destinationFingerprint,
          payload.optString("destinationFingerprint"),
        )) return@withTransaction rejectedInternal("CONFIGURATION_REVIEW_CORRUPT")
      if (dao.activeDestinationBlockCount(review.accountId, destinationFingerprint) != 0) {
        return@withTransaction TestStartOutcome.Rejected(
          validation("phone", "phone-blocked-form"),
        )
      }

      val exactMessage = payload.optString("exactMessage")
      if (!constantTextEquals(TEST_MESSAGE, exactMessage)) {
        return@withTransaction rejectedInternal("CONFIGURATION_REVIEW_CORRUPT")
      }
      val nativePlan = when (
        val result = testSmsPlanSource.plan(exactMessage, subscription.subscriptionId)
      ) {
        is NativeSmsPlanResult.Planned -> result.plan
        is NativeSmsPlanResult.Rejected -> return@withTransaction TestStartOutcome.Rejected(
          validation("template", "invalid-segment-cap"),
        )
      }
      val segmentPlan = ApprovedSegmentPlan.bind(
        exactText = exactMessage,
        encoding = nativePlan.encoding,
        orderedParts = nativePlan.orderedParts,
        approvedSegmentCap = 2,
      ) ?: return@withTransaction TestStartOutcome.Rejected(
        validation("template", "invalid-segment-cap"),
      )
      if (
        segmentPlan.segmentCount != payload.optInt("segmentCount", -1) ||
        segmentPlan.encoding.name != payload.optString("messageEncoding") ||
        !ConfigurationCanonicalHash.matches(
          segmentPlan.orderedPartsHash,
          payload.optString("orderedPartsHash"),
        ) ||
        !jsonStringListEquals(payload.optJSONArray("orderedParts"), nativePlan.orderedParts)
      ) return@withTransaction rejectedConflict("sim-changed")

      val configHash = AndroidTestConfigurationBinding.configurationHash(
        template,
        policy,
        subscription.subscriptionId,
        AndroidSmsPolicyBinding.VERSION,
      )
      val destinationPrehash = StablePrivateId.hash(
        "BirthdayAutopilot.TestDestination.v1",
        review.accountId,
        normalizedDestination,
      )
      val payloadHash = testPayloadHash(
        configHash = configHash,
        destinationPrehash = destinationPrehash,
        exactMessage = exactMessage,
        resolvedSubscriptionId = subscription.subscriptionId,
        messageEncoding = segmentPlan.encoding.name,
        segmentCount = segmentPlan.segmentCount,
        orderedPartsHash = segmentPlan.orderedPartsHash,
        buildBindingHash = buildBinding.hash,
      )
      if (
        !ConfigurationCanonicalHash.matches(configHash, payload.optString("configHash")) ||
        !ConfigurationCanonicalHash.matches(
          destinationPrehash,
          payload.optString("destinationPrehash"),
        ) ||
        !ConfigurationCanonicalHash.matches(payloadHash, payload.optString("payloadHash")) ||
        !ConfigurationCanonicalHash.matches(
          buildBinding.hash,
          payload.optString("buildBindingHash"),
        )
      ) return@withTransaction rejectedStale(control.revision)

      val at = now()
      val retentionUntil = at.plusOrNull(TEST_RETENTION_MILLIS)
        ?: return@withTransaction rejectedInternal("CONFIGURATION_TIME_OVERFLOW")
      val testJobId = "tj_${UUID.randomUUID().toString().replace("-", "")}"
      val testRequestId = UUID.randomUUID().toString()
      val confirmationNonceHash = ConfigurationCanonicalHash.content(
        "BirthdayAutopilot.TestForegroundConfirmation.v1",
        listOf(review.accountId, testJobId, UUID.randomUUID().toString(), at.toString()),
      )
      if (
        dao.markReviewConsumed(
          handle,
          REVIEW_TEST,
          review.controlRevision,
          review.blockerRevision,
          at,
        ) != 1
      ) return@withTransaction rejectedConflict("approval-invalid")
      ledger.insertTestJob(
        TestJobEntity(
          testJobId = testJobId,
          accountId = review.accountId,
          installationId = installation.installationId,
          senderEpoch = senderEpoch,
          testRequestId = testRequestId,
          configHash = configHash,
          destinationPrehash = destinationPrehash,
          normalizedDestination = normalizedDestination,
          maskedDestination = normalized.maskedDisplay,
          exactMessage = exactMessage,
          payloadHash = payloadHash,
          simPolicyKind = policy.simPolicyKind,
          resolvedSubscriptionId = subscription.subscriptionId,
          segmentCount = segmentPlan.segmentCount,
          messageEncoding = segmentPlan.encoding.name,
          orderedPartsHash = segmentPlan.orderedPartsHash,
          buildBindingHash = buildBinding.hash,
          appCheckPolicyVersion = AndroidTestConfigurationBinding.APP_CHECK_POLICY_VERSION,
          state = TestJobState.PREPARED,
          revision = 0,
          foregroundConfirmationNonceHash = confirmationNonceHash,
          foregroundConfirmedAtMillis = at,
          createdAtMillis = at,
          updatedAtMillis = at,
          terminalAtMillis = null,
          invalidationReason = null,
          retentionUntilMillis = retentionUntil,
        ),
      )
      TestStartOutcome.Ready(testJobId, confirmationNonceHash)
    }
  }

  suspend fun latestTestProjection(): JSONObject? {
    val account = dao.activeAccount() ?: return null
    return dao.latestTestJob(account.accountId)?.let(::testProjection)
  }

  suspend fun testProjection(
    testJobId: String,
    orchestrationSafeCode: String? = null,
  ): JSONObject? = dao.testJob(testJobId)?.let { testProjection(it, orchestrationSafeCode) }

  private suspend fun passingTestReceipt(account: AccountRecordEntity): Boolean {
    return currentActivationBinding(account) != null
  }

  private suspend fun currentActivationBinding(
    account: AccountRecordEntity,
  ): ActivationBinding? {
    val installation = dao.activeInstallation(account.accountId) ?: return null
    val receipts = dao.validTestReceipts(account.accountId)
    if (receipts.size != 1) return null
    val receipt = receipts.single()
    val test = dao.testJob(receipt.testJobId) ?: return null
    val permit = dao.testPermit(test.testJobId) ?: return null
    if (
      test.state != TestJobState.PASSED ||
      permit.accountId != account.accountId ||
      permit.installationId != installation.installationId ||
      permit.senderEpoch != installation.senderEpoch ||
      permit.opaqueClaimId.isBlank()
    ) return null
    val template = dao.activeTemplate(account.accountId) ?: return null
    val policy = dao.activeAutomationPolicy(account.accountId) ?: return null
    val subscription = subscriptionResolver.resolveDefault() as? SubscriptionResolution.Ready
      ?: return null
    val buildSignals = buildSignalSource.read()
    val signing = buildSignals.signingCertificateSha256.lowercase(Locale.ROOT)
    if (!signing.matches(Regex("^[0-9a-fA-F]{64}$"))) return null
    if (!AndroidTestConfigurationBinding.matchesCurrent(
      test = test,
      installation = installation,
      receipt = receipt,
      template = template,
      policy = policy,
      currentAppVersionCode = buildSignals.versionCode,
      currentDistributionChannel = buildSignals.distributionChannel.name,
      currentSigningCertificateSha256 = signing,
      currentResolvedSubscriptionId = subscription.subscriptionId,
      currentSmsPolicyVersion = AndroidSmsPolicyBinding.VERSION,
    )) return null
    return ActivationBinding(installation, test, receipt, permit)
  }

  private fun normalizeTestDestination(raw: String, homeRegion: String?):
    com.yashsomani.birthdayautopilot.contacts.NormalizedPhone? {
    return testPhoneNormalizer.resolve(
      phones = listOf(RawContactPhone("test-destination", raw, PhoneLabel.MOBILE)),
      selectedPhoneId = "test-destination",
      homeRegion = homeRegion,
    ).selected
  }

  private fun currentBuildBindingOrNull(): TestBuildBinding? {
    val signals = buildSignalSource.read()
    val signing = signals.signingCertificateSha256.lowercase(Locale.ROOT)
    if (!signing.matches(SHA256)) return null
    if (signals.versionCode <= 0) return null
    val channel = signals.distributionChannel.name
    return TestBuildBinding(
      versionCode = signals.versionCode,
      distributionChannel = channel,
      signingCertificateSha256 = signing,
      hash = AndroidTestConfigurationBinding.buildHash(
        signals.versionCode,
        channel,
        signing,
      ),
    )
  }

  private fun installationMatchesBuild(
    installation: InstallationBindingEntity,
    senderEpoch: Long,
    build: TestBuildBinding,
  ): Boolean =
    installation.localSlot == 1 &&
      installation.state == InstallationRecordState.ACTIVE &&
      installation.senderEpoch == senderEpoch &&
      senderEpoch > 0 &&
      installation.resetGeneration > 0 &&
      installation.appVersionCode == build.versionCode &&
      installation.distributionChannel == build.distributionChannel &&
      constantTextEquals(
        installation.signingCertificateSha256.lowercase(Locale.ROOT),
        build.signingCertificateSha256,
      ) &&
      testModeAllowed(installation.accountMode.name)

  private fun testModeAllowed(rawMode: String): Boolean = rawMode in setOf(
    AccountMode.TEST_ONLY.name,
    AccountMode.PAUSED_REPAIR.name,
  )

  private fun testPayloadHash(
    configHash: String,
    destinationPrehash: String,
    exactMessage: String,
    resolvedSubscriptionId: Int,
    messageEncoding: String,
    segmentCount: Int,
    orderedPartsHash: String,
    buildBindingHash: String,
  ): String = ConfigurationCanonicalHash.content(
    "BirthdayAutopilot.TestPayload.v1",
    listOf(
      configHash,
      destinationPrehash,
      exactMessage,
      resolvedSubscriptionId.toString(),
      messageEncoding,
      segmentCount.toString(),
      orderedPartsHash,
      buildBindingHash,
      AndroidTestConfigurationBinding.APP_CHECK_POLICY_VERSION,
      AndroidSmsPolicyBinding.VERSION,
    ),
  )

  private fun testProjection(
    test: TestJobEntity,
    orchestrationSafeCode: String? = null,
  ): JSONObject = JSONObject()
    .put("platform", "android")
    .put("phase", test.state.toWirePhase())
    .put("updatedAt", Instant.ofEpochMilli(test.updatedAtMillis.coerceAtLeast(1)).toString())
    .apply {
      (test.invalidationReason.toTestSafeReason()
        ?: orchestrationSafeCode.toTestSafeReason())?.let { put("reason", it) }
    }

  private fun TestJobState.toWirePhase(): String = when (this) {
    TestJobState.PREPARED -> "prepared"
    TestJobState.CLOUD_CLAIMED -> "cloud-claimed"
    TestJobState.ARM_RECONCILING -> "arm-reconciling"
    TestJobState.COORDINATION_UNKNOWN -> "coordination-unknown"
    TestJobState.CLOUD_ARMED -> "cloud-armed"
    TestJobState.ARMED_SUPPRESSED -> "armed-suppressed"
    TestJobState.BARRIER_CONSUMED -> "barrier-consumed"
    TestJobState.SUBMITTED -> "submitted"
    TestJobState.SENT_FROM_DEVICE -> "sent-from-device"
    TestJobState.PASSED -> "passed"
    TestJobState.FAILED -> "failed"
    TestJobState.PARTIAL_UNKNOWN -> "partial-unknown"
    TestJobState.UNKNOWN -> "unknown"
    TestJobState.PERMANENT_FAILURE -> "permanent-failure"
    TestJobState.CLEANUP_CANCELLED -> "cleanup-cancelled"
    TestJobState.RECEIPT_INVALIDATED -> "receipt-invalidated"
  }

  private fun String?.toTestSafeReason(): String? {
    val raw = this ?: return null
    return when {
      "BUDGET" in raw -> "test-budget-exhausted"
      "NETWORK" in raw -> "network-offline"
      "CLOCK" in raw -> "clock-untrusted"
      "RESET" in raw -> "reset-safety-blocked"
      "PERMISSION" in raw -> "permission-denied"
      "SIM" in raw -> "sim-changed"
      "ACCOUNT" in raw || "IDENTITY" in raw || "SENDER_REGISTRATION" in raw ->
        "account-reconnect-required"
      "LEASE" in raw || "CLAIM" in raw || "COORDINATION" in raw || "ARM" in raw ->
        "coordination-unavailable"
      "BINDING" in raw || "CONTRACT" in raw || "SUBMISSION_OUTCOME_UNKNOWN" in raw ->
        "internal-contract-invalid"
      else -> null
    }
  }

  suspend fun contactDetail(contactId: String): JSONObject? {
    if (!validOpaque(contactId)) return null
    val account = dao.activeAccount() ?: return null
    val contact = dao.contact(contactId)?.takeIf { it.accountId == account.accountId } ?: return null
    return contactDetail(contact, account)
  }

  suspend fun messageEditor(): JSONObject? {
    val account = dao.activeAccount() ?: return null
    val template = dao.activeTemplate(account.accountId)
      ?: return JSONObject().put("kind", "not-configured")
    val draft = template.toDraftPayload()
      ?: return JSONObject().put("kind", "not-configured")
    return JSONObject().put("kind", "configured").put("draft", draft)
  }

  suspend fun policyEditor(): JSONObject {
    val account = dao.activeAccount()?.takeIf(accountSessionMatches)
      ?: return JSONObject().put("kind", "not-configured")
    val policy = dao.activeAutomationPolicy(account.accountId)
      ?: return JSONObject().put("kind", "not-configured")
    if (
      policy.windowStartMinute !in 0..1439 ||
      policy.windowEndMinute !in 1..1439 ||
      policy.windowStartMinute >= policy.windowEndMinute ||
      policy.dailyCap !in 1..20
    ) return JSONObject().put("kind", "not-configured")
    val latePolicy = when (policy.latePolicy) {
      "SAME_DAY_WINDOW_ONLY" -> if (policy.graceEndMinute == null) {
        JSONObject().put("kind", "none")
      } else {
        return JSONObject().put("kind", "not-configured")
      }
      "SAME_DAY_GRACE" -> policy.graceEndMinute?.takeIf {
        it in (policy.windowEndMinute + 1)..1439
      }?.let {
        JSONObject().put("kind", "same-day-grace").put("graceEnd", formatMinute(it))
      } ?: return JSONObject().put("kind", "not-configured")
      else -> return JSONObject().put("kind", "not-configured")
    }
    return JSONObject()
      .put("kind", "configured")
      .put(
        "draft",
        JSONObject()
          .put("primaryStart", formatMinute(policy.windowStartMinute))
          .put("primaryEnd", formatMinute(policy.windowEndMinute))
          .put("latePolicy", latePolicy)
          .put("dailyCap", policy.dailyCap),
      )
  }

  suspend fun approvalProjection(contactId: String): JSONObject? {
    if (!validOpaque(contactId)) return null
    val account = dao.activeAccount() ?: return null
    val contact = dao.contact(contactId)?.takeIf { it.accountId == account.accountId } ?: return null
    val policy = dao.recipientPolicy(contactId)
    return approvalProjection(contact, policy)
  }

  suspend fun homePayload(
    automation: JSONObject,
    contactsSync: JSONObject,
  ): JSONObject {
    val account = dao.activeAccount()
      ?: return JSONObject()
        .put("automation", automation)
        .put("counts", emptyCounts())
        .put("contactsSync", contactsSync)
    val rows = dao.enabledPlanRows(account.accountId, MAX_PLANNED_CONTACTS)
    val today = currentLocalDate(zoneProvider.zoneId())
    val plans = rows.mapNotNull { row ->
      val leap = row.leapDayPolicy.toLeapPolicyOrNull()
      val date = try {
        recurrencePlanner.nextOccurrence(
          today,
          BirthdayRule(row.birthdayMonth, row.birthdayDay, leap),
        )
      } catch (_: IllegalArgumentException) {
        null
      }
      date?.let { row to it }
    }
    val next = plans.minWithOrNull(compareBy({ it.second }, { it.first.contactId }))
    val reviewableTodayOccurrences = plans
      .filter { it.second == today }
      .associate { (row, date) ->
        row.contactId to dao.reviewableOccurrenceId(row.contactId, date.toString())
      }
    val requiresName = dao.activeTemplate(account.accountId)?.placeholderMode
      ?.let { it == TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME.name } ?: true
    val counts = JSONObject()
      .put("configured", dao.configuredRecipientCount(account.accountId))
      .put("enabled", dao.enabledRecipientCount(account.accountId))
      .put("needsAttention", dao.needsAttentionContactCount(account.accountId, requiresName))
      .put("unavailable", dao.unavailableContactCount(account.accountId))
      .put("today", reviewableTodayOccurrences.values.count { it != null })
      .put("nextSevenDays", plans.count { !it.second.isAfter(today.plusDays(6)) })
    val payload = JSONObject()
      .put("automation", automation)
      .put("counts", counts)
      .put("contactsSync", contactsSync)
    next?.let { (row, date) ->
      val realOccurrenceId = if (date == today) {
        reviewableTodayOccurrences[row.contactId]
      } else {
        dao.reviewableOccurrenceId(row.contactId, date.toString())
      }
      payload.put(
        "next",
        JSONObject()
          .put(
            "occurrenceId",
            realOccurrenceId
              ?: opaqueId("o", "BirthdayAutopilot.HomeOccurrence.v1", row.contactId, date.toString()),
          )
          .put("recipient", row.displayName)
          .put("localDate", date.toString())
          .put("windowLabel", activeWindowLabel(account.accountId))
          .put("maskedPhone", row.maskedDisplay)
          .put("exactText", row.exactMessage),
      )
    }
    ReconcileHeartbeatPolicy.snapshot(ledger.getReadinessState(account.accountId))
      ?.heartbeatAtMillis
      ?.takeIf { it > 0 }
      ?.let { payload.put("schedulerHeartbeatAt", Instant.ofEpochMilli(it).toString()) }
    durableReadiness().lastCoordinationSuccessMillis?.takeIf { it > 0 }?.let {
      payload.put("lastCoordinationSuccessAt", Instant.ofEpochMilli(it).toString())
    }
    return payload
  }

  suspend fun choosePhone(request: JSONObject, expectedRevision: Long): ConfigurationOutcome {
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("contactId", "phoneId", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val contactId = request.optString("contactId")
    val phoneId = request.optString("phoneId")
    if (!validOpaque(contactId) || !validOpaque(phoneId)) return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    val mutation = database.withTransaction {
      val gate = mutationGate(expectedRevision) ?: return@withTransaction staleOrInternal(expectedRevision)
      val (control, account) = gate
      val contact = dao.contact(contactId)?.takeIf { it.accountId == account.accountId }
        ?: return@withTransaction conflict("source-contact-deleted")
      val phone = dao.phones(contactId).singleOrNull { it.phoneId == phoneId }
      if (phone == null || phone.state != PhoneRecordState.READY || phone.normalizedE164 == null) {
        return@withTransaction validation("phone", "phone-invalid")
      }
      val policy = dao.recipientPolicy(contactId)
        ?: return@withTransaction internal("CONFIGURATION_POLICY_MISSING")
      if (policy.chosenPhoneId == phoneId) return@withTransaction ConfigurationOutcome.Success(JSONObject())
      val nextContactRevision = contact.materialRevision.incrementOrNull()
        ?: return@withTransaction internal("CONFIGURATION_REVISION_EXHAUSTED")
      val nextPolicyRevision = policy.revision.incrementOrNull()
        ?: return@withTransaction internal("CONFIGURATION_REVISION_EXHAUSTED")
      val nextState = policy.state.afterMaterialEdit()
      val projectedRows = ConfigurationPolicyValidator.projectConfiguredBirthday(
        dao.configuredBirthdayRowsForCapacity(account.accountId),
        contact.contactId,
        contact.birthdayMonth,
        contact.birthdayDay,
        contact.leapDayPolicy,
        nextState in CAPACITY_CONFIGURED_STATES,
      )
      capacityIssue(account.accountId, projectedRows)?.let { return@withTransaction it }
      val at = now()
      val invalidated = dao.invalidateApprovals(contactId, at, REASON_PHONE_SELECTION)
      check(dao.updateContact(contact.copy(materialRevision = nextContactRevision)) == 1)
      check(
        dao.updateRecipientPolicy(
          policy.copy(
            chosenPhoneId = phoneId,
            state = nextState,
            blockReason = if (policy.state == RecipientEnrollmentState.OFF) null else REASON_PHONE_SELECTION,
            approvalId = null,
            revision = nextPolicyRevision,
            enabledAtMillis = null,
            updatedAtMillis = at,
          ),
        ) == 1,
      )
      dao.invalidateTestReceipts(account.accountId, at, REASON_PHONE_SELECTION)
      check(dao.bumpControlBlocker(control.revision, control.blockerRevision) == 1)
      ConfigurationOutcome.Success(JSONObject().put("invalidated", invalidated))
    }
    if (mutation !is ConfigurationOutcome.Success) return mutation
    return ConfigurationOutcome.Success(
      contactDetail(contactId) ?: return internal("CONFIGURATION_CONTACT_PROJECTION_FAILED"),
      CONFIG_INVALIDATIONS,
    )
  }

  suspend fun chooseBirthday(request: JSONObject, expectedRevision: Long): ConfigurationOutcome {
    val allowed = setOf("contactId", "birthdayId", "expectedRevision", "leapPolicy")
    if (!revisionEchoMatches(request, expectedRevision) || !allowed.containsAll(request.keyNames())) {
      return ConfigurationOutcome.InvalidRequest
    }
    val contactId = request.optString("contactId")
    val birthdayId = request.optString("birthdayId")
    if (!validOpaque(contactId) || !validOpaque(birthdayId)) return ConfigurationOutcome.InvalidRequest
    val leapPolicy = request.optString("leapPolicy").takeIf(String::isNotEmpty)?.toLeapPolicy()
    if (request.has("leapPolicy") && leapPolicy == null) return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    val mutation = database.withTransaction {
      val gate = mutationGate(expectedRevision) ?: return@withTransaction staleOrInternal(expectedRevision)
      val (control, account) = gate
      val contact = dao.contact(contactId)?.takeIf { it.accountId == account.accountId }
        ?: return@withTransaction conflict("source-contact-deleted")
      val choice = dao.birthdays(contactId).singleOrNull { it.birthdayId == birthdayId }
      if (choice == null || !choice.selectable || choice.birthdayMonth == null || choice.birthdayDay == null) {
        return@withTransaction validation("birthday", "birthday-choice-required")
      }
      val requiredLeap = choice.birthdayMonth == 2 && choice.birthdayDay == 29
      if ((requiredLeap && leapPolicy == null) || (!requiredLeap && leapPolicy != null)) {
        return@withTransaction validation("birthday", "leap-policy-required")
      }
      val policy = dao.recipientPolicy(contactId)
        ?: return@withTransaction internal("CONFIGURATION_POLICY_MISSING")
      if (
        policy.chosenBirthdayId == birthdayId &&
        contact.leapDayPolicy == leapPolicy?.name
      ) return@withTransaction ConfigurationOutcome.Success(JSONObject())
      val nextContactRevision = contact.materialRevision.incrementOrNull()
        ?: return@withTransaction internal("CONFIGURATION_REVISION_EXHAUSTED")
      val nextPolicyRevision = policy.revision.incrementOrNull()
        ?: return@withTransaction internal("CONFIGURATION_REVISION_EXHAUSTED")
      val nextState = policy.state.afterMaterialEdit()
      val projectedRows = ConfigurationPolicyValidator.projectConfiguredBirthday(
        dao.configuredBirthdayRowsForCapacity(account.accountId),
        contact.contactId,
        choice.birthdayMonth,
        choice.birthdayDay,
        leapPolicy?.name,
        nextState in CAPACITY_CONFIGURED_STATES,
      )
      capacityIssue(account.accountId, projectedRows)?.let { return@withTransaction it }
      val at = now()
      val invalidated = dao.invalidateApprovals(contactId, at, REASON_BIRTHDAY_SELECTION)
      check(
        dao.updateContact(
          contact.copy(
            birthdayYear = choice.birthdayYear,
            birthdayMonth = choice.birthdayMonth,
            birthdayDay = choice.birthdayDay,
            leapDayPolicy = leapPolicy?.name,
            materialRevision = nextContactRevision,
          ),
        ) == 1,
      )
      check(
        dao.updateRecipientPolicy(
          policy.copy(
            chosenBirthdayId = birthdayId,
            state = nextState,
            blockReason = if (policy.state == RecipientEnrollmentState.OFF) null else REASON_BIRTHDAY_SELECTION,
            approvalId = null,
            revision = nextPolicyRevision,
            enabledAtMillis = null,
            updatedAtMillis = at,
          ),
        ) == 1,
      )
      dao.invalidateTestReceipts(account.accountId, at, REASON_BIRTHDAY_SELECTION)
      check(dao.bumpControlBlocker(control.revision, control.blockerRevision) == 1)
      ConfigurationOutcome.Success(JSONObject().put("invalidated", invalidated))
    }
    if (mutation !is ConfigurationOutcome.Success) return mutation
    return ConfigurationOutcome.Success(
      contactDetail(contactId) ?: return internal("CONFIGURATION_CONTACT_PROJECTION_FAILED"),
      CONFIG_INVALIDATIONS,
    )
  }

  suspend fun prepareEnrollment(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("contactIds", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val contactIds = request.optJSONArray("contactIds").opaqueIdsOrNull() ?: return ConfigurationOutcome.InvalidRequest
    if (contactIds.isEmpty() || contactIds.size > MAX_REVIEW_ITEMS) return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    val account = dao.activeAccount() ?: return conflict("account-reconnect-required")
    val control = dao.control() ?: return internal("CONFIGURATION_CONTROL_MISSING")
    if (control.revision != expectedRevision) return stale(control.revision)
    val contacts = dao.contacts(account.accountId, contactIds).associateBy(ContactSnapshotEntity::contactId)
    if (contacts.size != contactIds.size) return conflict("source-contact-deleted")
    val recipients = JSONArray()
    val readyItems = JSONArray()
    var readyCount = 0
    contactIds.forEach { contactId ->
      val contact = contacts.getValue(contactId)
      val detail = contactDetail(contact, account)
      val summary = detail.getJSONObject("summary")
      val policy = checkNotNull(dao.recipientPolicy(contactId))
      recipients.put(summary)
      if (
        summary.getJSONObject("readiness").getString("kind") == "ready" &&
        policy.state != RecipientEnrollmentState.EXCLUDED
      ) {
        val phone = selectedPhone(contact, policy, dao.phones(contactId)) ?: return@forEach
        readyItems.put(
          JSONObject()
            .put("contactId", contactId)
            .put("contactRevision", contact.materialRevision)
            .put("policyRevision", policy.revision)
            .put("phoneId", phone.phoneId)
            .put("phoneRevision", phone.materialRevision)
            .put("birthdayId", policy.chosenBirthdayId ?: JSONObject.NULL),
        )
        readyCount++
      }
    }
    if (readyCount == 0) return validation("confirmation", "approval-missing")
    val reviewPayload = JSONObject().put("items", readyItems)
    val handle = createReview(REVIEW_ENROLLMENT, reviewPayload, account, control)
      ?: return internal("CONFIGURATION_REVIEW_CREATE_FAILED")
    return ConfigurationOutcome.Success(
      JSONObject()
        .put("handle", handle)
        .put("recipients", recipients)
        .put("readyCount", readyCount)
        .put("attentionCount", contactIds.size - readyCount)
        .put("explicitConfirmationRequired", true),
    )
  }

  suspend fun confirmEnrollment(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("handle", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val handle = request.optString("handle")
    if (!validHandle(handle, "er")) return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    return database.withTransaction {
      val material = loadReview(handle, REVIEW_ENROLLMENT, expectedRevision)
        ?: return@withTransaction reviewProblem(handle, expectedRevision)
      val (review, payload, control) = material
      val items = payload.optJSONArray("items") ?: return@withTransaction internal("CONFIGURATION_REVIEW_CORRUPT")
      val changes = ArrayList<Pair<RecipientPolicyEntity, RecipientPolicyEntity>>()
      var projectedRows = dao.configuredBirthdayRowsForCapacity(review.accountId)
      repeat(items.length()) { index ->
        val item = items.optJSONObject(index) ?: return@withTransaction internal("CONFIGURATION_REVIEW_CORRUPT")
        val contactId = item.optString("contactId")
        val contact = dao.contact(contactId) ?: return@withTransaction stale(control.revision)
        val policy = dao.recipientPolicy(contactId) ?: return@withTransaction stale(control.revision)
        val phone = dao.phones(contactId).singleOrNull { it.phoneId == item.optString("phoneId") }
          ?: return@withTransaction stale(control.revision)
        if (
          contact.accountId != review.accountId ||
          contact.materialRevision != item.optLong("contactRevision", -1) ||
          policy.revision != item.optLong("policyRevision", -1) ||
          phone.materialRevision != item.optLong("phoneRevision", -1) ||
          phone.state != PhoneRecordState.READY ||
          phone.normalizedE164 == null ||
          contact.state != ContactSnapshotState.ACTIVE ||
          contact.birthdayMonth == null || contact.birthdayDay == null ||
          policy.state == RecipientEnrollmentState.EXCLUDED
        ) return@withTransaction stale(control.revision)
        val at = now()
        projectedRows = ConfigurationPolicyValidator.projectConfiguredBirthday(
          projectedRows,
          contact.contactId,
          contact.birthdayMonth,
          contact.birthdayDay,
          contact.leapDayPolicy,
          included = true,
        )
        changes += policy to policy.copy(
          chosenPhoneId = phone.phoneId,
          state = RecipientEnrollmentState.NEEDS_REVIEW,
          explicitEnrollmentEventId = handle,
          blockReason = "APPROVAL_REQUIRED",
          approvalId = null,
          revision = policy.revision.incrementOrNull()
            ?: return@withTransaction internal("CONFIGURATION_REVISION_EXHAUSTED"),
          enabledAtMillis = null,
          updatedAtMillis = at,
        )
      }
      capacityIssue(review.accountId, projectedRows)?.let { return@withTransaction it }
      check(
        dao.markReviewConsumed(
          handle,
          REVIEW_ENROLLMENT,
          review.controlRevision,
          review.blockerRevision,
          now(),
        ) == 1,
      )
      changes.forEach { (_, updated) -> check(dao.updateRecipientPolicy(updated) == 1) }
      dao.invalidateTestReceipts(review.accountId, now(), REASON_ENROLLMENT_CHANGED)
      check(dao.bumpControlBlocker(control.revision, control.blockerRevision) == 1)
      ConfigurationOutcome.Success(
        JSONObject()
          .put("changedContactIds", JSONArray(changes.map { it.first.contactId }))
          .put("invalidatedApprovalCount", 0),
        CONFIG_INVALIDATIONS,
      )
    }
  }

  suspend fun mutateRecipient(
    kind: String,
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (
      kind !in setOf("pause", "exclude", "restore") ||
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("contactId", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val contactId = request.optString("contactId")
    if (!validOpaque(contactId)) return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    return database.withTransaction {
      val gate = mutationGate(expectedRevision) ?: return@withTransaction staleOrInternal(expectedRevision)
      val (control, account) = gate
      val contact = dao.contact(contactId)?.takeIf { it.accountId == account.accountId }
        ?: return@withTransaction conflict("source-contact-deleted")
      val policy = dao.recipientPolicy(contactId)
        ?: return@withTransaction internal("CONFIGURATION_POLICY_MISSING")
      val approval = policy.approvalId?.let { dao.approval(it) }
      val at = now()
      var invalidated = 0
      if (
        (kind == "pause" && policy.state !in setOf(
          RecipientEnrollmentState.ENABLED,
          RecipientEnrollmentState.NEEDS_REVIEW,
          RecipientEnrollmentState.PAUSED,
        )) ||
        (kind == "restore" && policy.state !in setOf(
          RecipientEnrollmentState.PAUSED,
          RecipientEnrollmentState.EXCLUDED,
          RecipientEnrollmentState.NEEDS_REVIEW,
        ))
      ) return@withTransaction conflict("policy-suspended")
      val nextState = when (kind) {
        "pause" -> RecipientEnrollmentState.PAUSED
        "exclude" -> RecipientEnrollmentState.EXCLUDED
        else -> if (approvalIsValid(contact, policy, approval)) {
          RecipientEnrollmentState.ENABLED
        } else {
          RecipientEnrollmentState.NEEDS_REVIEW
        }
      }
      if (kind == "restore") {
        val projectedRows = ConfigurationPolicyValidator.projectConfiguredBirthday(
          dao.configuredBirthdayRowsForCapacity(account.accountId),
          contact.contactId,
          contact.birthdayMonth,
          contact.birthdayDay,
          contact.leapDayPolicy,
          nextState in CAPACITY_CONFIGURED_STATES,
        )
        capacityIssue(account.accountId, projectedRows)?.let { return@withTransaction it }
      }
      if (kind == "exclude") invalidated = dao.revokeApprovals(contactId, at, REASON_EXCLUDED)
      val nextRevision = policy.revision.incrementOrNull()
        ?: return@withTransaction internal("CONFIGURATION_REVISION_EXHAUSTED")
      check(
        dao.updateRecipientPolicy(
          policy.copy(
            state = nextState,
            blockReason = when (nextState) {
              RecipientEnrollmentState.PAUSED -> "POLICY_SUSPENDED"
              RecipientEnrollmentState.EXCLUDED -> "RECIPIENT_EXCLUDED"
              RecipientEnrollmentState.NEEDS_REVIEW -> "APPROVAL_REQUIRED"
              else -> null
            },
            approvalId = if (kind == "exclude") null else policy.approvalId,
            revision = nextRevision,
            enabledAtMillis = if (nextState == RecipientEnrollmentState.ENABLED) at else null,
            updatedAtMillis = at,
          ),
        ) == 1,
      )
      dao.invalidateTestReceipts(account.accountId, at, "RECIPIENT_${kind.uppercase(Locale.ROOT)}")
      check(dao.bumpControlBlocker(control.revision, control.blockerRevision) == 1)
      ConfigurationOutcome.Success(
        JSONObject()
          .put("changedContactIds", JSONArray(listOf(contactId)))
          .put("invalidatedApprovalCount", invalidated),
        CONFIG_INVALIDATIONS,
      )
    }
  }

  /**
   * Applies the account-wide destination block behind the same configuration CAS used by every
   * recipient mutation. React Native supplies only the opaque contact ID; the canonical number
   * never leaves native storage and only its keyed destination fingerprint is persisted here.
   */
  suspend fun mutateSelectedDestinationBlock(
    blocked: Boolean,
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("contactId", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val contactId = request.optString("contactId")
    if (!validOpaque(contactId)) return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    return database.withTransaction {
      val gate = mutationGate(expectedRevision)
        ?: return@withTransaction staleOrInternal(expectedRevision)
      val (control, account) = gate
      val contact = dao.contact(contactId)?.takeIf { it.accountId == account.accountId }
        ?: return@withTransaction conflict("source-contact-deleted")
      val policy = dao.recipientPolicy(contact.contactId)
        ?: return@withTransaction internal("CONFIGURATION_POLICY_MISSING")
      val phone = policy.chosenPhoneId
        ?.let { chosen -> dao.phones(contact.contactId).singleOrNull { it.phoneId == chosen } }
        ?.takeIf { it.state == PhoneRecordState.READY }
        ?: return@withTransaction validation("phone", "phone-choice-required")
      val fingerprint = phone.destinationFingerprint
        ?: return@withTransaction validation("phone", "phone-invalid")
      val current = dao.destinationBlock(account.accountId, fingerprint)
      if (current?.active == blocked) {
        return@withTransaction ConfigurationOutcome.Success(
          JSONObject()
            .put("changedContactIds", JSONArray(listOf(contact.contactId)))
            .put("invalidatedApprovalCount", 0),
          CONFIG_INVALIDATIONS,
        )
      }
      val at = now()
      if (current == null) {
        if (!blocked) {
          return@withTransaction ConfigurationOutcome.Success(
            JSONObject()
              .put("changedContactIds", JSONArray(listOf(contact.contactId)))
              .put("invalidatedApprovalCount", 0),
            CONFIG_INVALIDATIONS,
          )
        }
        dao.insertDestinationBlock(
          DestinationBlockEntity(
            blockId = opaqueId(
              "db",
              "BirthdayAutopilot.DestinationBlock.v1",
              account.accountId,
              fingerprint,
            ),
            accountId = account.accountId,
            destinationFingerprint = fingerprint,
            reason = REASON_DESTINATION_BLOCKED,
            active = true,
            revision = 0,
            createdAtMillis = at,
            updatedAtMillis = at,
          ),
        )
      } else {
        val nextRevision = current.revision.incrementOrNull()
          ?: return@withTransaction internal("CONFIGURATION_REVISION_EXHAUSTED")
        check(
          dao.updateDestinationBlock(
            current.copy(
              reason = REASON_DESTINATION_BLOCKED,
              active = blocked,
              revision = nextRevision,
              updatedAtMillis = at,
            ),
          ) == 1,
        )
      }
      val invalidated = if (blocked) {
        dao.revokeApprovalsForDestination(
          account.accountId,
          fingerprint,
          at,
          REASON_DESTINATION_BLOCKED,
        ).also {
          dao.markEnabledRecipientsForDestinationReview(
            account.accountId,
            fingerprint,
            at,
            REASON_DESTINATION_BLOCKED,
          )
          dao.cancelUnclaimedOccurrencesForDestination(
            account.accountId,
            fingerprint,
            at,
            REASON_DESTINATION_BLOCKED,
          )
        }
      } else {
        dao.clearDestinationBlockReasonForReview(
          account.accountId,
          fingerprint,
          at,
          REASON_DESTINATION_BLOCKED,
        )
        0
      }
      dao.invalidateTestReceipts(
        account.accountId,
        at,
        if (blocked) "DESTINATION_BLOCKED" else "DESTINATION_UNBLOCKED",
      )
      check(dao.bumpControlBlocker(control.revision, control.blockerRevision) == 1)
      ConfigurationOutcome.Success(
        JSONObject()
          .put("changedContactIds", JSONArray(listOf(contact.contactId)))
          .put("invalidatedApprovalCount", invalidated),
        CONFIG_INVALIDATIONS,
      )
    }
  }

  suspend fun previewMessage(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("draft", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val draft = parseMessageDraft(request.optJSONObject("draft"))
      ?: return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    val account = dao.activeAccount() ?: return conflict("account-reconnect-required")
    val control = dao.control() ?: return internal("CONFIGURATION_CONTROL_MISSING")
    if (control.revision != expectedRevision) return stale(control.revision)
    val template = draft.toDomainTemplate()
    val sample = if (draft.language == "hi") "मित्र" else "Friend"
    val base = templateValidator.validateAndRender(template, sample, draft.segmentCap)
    if (!base.valid) {
      return ConfigurationOutcome.Success(
        JSONObject()
          .put("kind", "invalid")
          .put("issues", validationIssues(base.errors))
          .put("affectedRecipientCount", dao.configuredRecipientCount(account.accountId)),
      )
    }
    val affected = dao.configuredRecipientCount(account.accountId)
    val candidates = dao.configuredPreviewContacts(account.accountId, PREVIEW_SCAN_LIMIT)
      .ifEmpty { dao.fallbackPreviewContacts(account.accountId, PREVIEW_SCAN_LIMIT) }
    val examples = JSONArray()
    var maximumSegments = base.preview!!.metrics.segmentCount
    candidates.forEach { contact ->
      val rendered = templateValidator.validateAndRender(
        template,
        contact.safeGivenName,
        draft.segmentCap,
      )
      val preview = rendered.preview?.takeIf { rendered.valid } ?: return@forEach
      maximumSegments = maxOf(maximumSegments, preview.metrics.segmentCount)
      if (examples.length() < MAX_PREVIEW_EXAMPLES) {
        examples.put(
          JSONObject()
            .put("displayName", contact.displayName)
            .put("finalText", preview.exactText)
            .put("characterCount", preview.metrics.characterCount)
            .put("segmentCount", preview.metrics.segmentCount)
            .put("encodingLabel", preview.metrics.encoding.toWire()),
        )
      }
    }
    val provenance = geminiGateway?.peekProvenance(draft.toGeminiProvenanceDraft())
    val handle = createReview(
      REVIEW_MESSAGE,
      messageReviewPayload(draft, provenance),
      account,
      control,
    )
      ?: return internal("CONFIGURATION_REVIEW_CREATE_FAILED")
    return ConfigurationOutcome.Success(
      JSONObject()
        .put("kind", "valid")
        .put("handle", handle)
        .put("examples", examples)
        .put("maximumSegmentCount", maximumSegments)
        .put("affectedRecipientCount", affected),
    )
  }

  suspend fun saveMessage(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("handle", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val handle = request.optString("handle")
    if (!validHandle(handle, "mp")) return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    val preflightMaterial = loadReview(handle, REVIEW_MESSAGE, expectedRevision)
      ?: return reviewProblem(handle, expectedRevision)
    val preflightPayload = parseMessageReviewPayload(preflightMaterial.second)
      ?: return internal("CONFIGURATION_REVIEW_CORRUPT")
    val preflightDraft = preflightPayload.first.toGeminiProvenanceDraft()
    val preflightGemini = preflightPayload.second?.let { bound ->
      geminiGateway?.peekProvenance(preflightDraft)?.takeIf { current ->
        current == bound &&
          current.source == "GEMINI" &&
          current.validatorVersion == MessageTemplateValidator.VALIDATOR_VERSION
      }
    }
    var persistedGeminiDraft: GeminiProvenanceDraft? = null
    val outcome = database.withTransaction {
      val material = loadReview(handle, REVIEW_MESSAGE, expectedRevision)
        ?: return@withTransaction reviewProblem(handle, expectedRevision)
      val (review, payload, control) = material
      val reviewPayload = parseMessageReviewPayload(payload)
        ?: return@withTransaction internal("CONFIGURATION_REVIEW_CORRUPT")
      val draft = reviewPayload.first
      val sample = if (draft.language == "hi") "मित्र" else "Friend"
      if (!templateValidator.validateAndRender(draft.toDomainTemplate(), sample, draft.segmentCap).valid) {
        return@withTransaction internal("CONFIGURATION_REVIEW_CORRUPT")
      }
      val geminiDraft = draft.toGeminiProvenanceDraft()
      val geminiProvenance = reviewPayload.second?.let { bound ->
        preflightGemini?.takeIf { current -> current == bound && geminiDraft == preflightDraft }
      }
      val revision = dao.latestTemplateRevision(review.accountId).incrementOrNull()
        ?: return@withTransaction internal("CONFIGURATION_REVISION_EXHAUSTED")
      val hash = ConfigurationCanonicalHash.content(
        "BirthdayAutopilot.MessageTemplate.v1",
        draft.canonicalValues(),
      )
      val at = now()
      val builtIn = draft.matchingBuiltIn()
      val template = MessageTemplateEntity(
        templateId = opaqueId("t", "BirthdayAutopilot.TemplateId.v1", review.accountId, hash, revision.toString()),
        accountId = review.accountId,
        source = when {
          geminiProvenance != null -> TemplateSource.GEMINI
          builtIn != null -> TemplateSource.BUILT_IN
          else -> TemplateSource.USER
        },
        exactTemplateText = draft.text,
        languageTag = draft.language,
        tone = draft.tone,
        placeholderMode = draft.placeholderMode.name,
        templateVersion = if (geminiProvenance != null) {
          "gemini-template-v1-${hash.take(12)}"
        } else {
          builtIn?.version ?: draft.templateVersion()
        },
        promptPolicyVersion = geminiProvenance?.promptPolicyVersion,
        validatorVersion = geminiProvenance?.validatorVersion
          ?: MessageTemplateValidator.VALIDATOR_VERSION,
        modelIdentifier = geminiProvenance?.modelIdentifier,
        contentHash = hash,
        validationState = TemplateValidationState.VALID,
        revision = revision,
        createdAtMillis = at,
        updatedAtMillis = at,
        requestedSegmentCap = draft.segmentCap,
      )
      val affected = dao.configuredRecipientCount(review.accountId)
      check(
        dao.markReviewConsumed(
          handle,
          REVIEW_MESSAGE,
          review.controlRevision,
          review.blockerRevision,
          at,
        ) == 1,
      )
      dao.supersedeTemplates(review.accountId)
      dao.insertTemplate(template)
      val invalidated = dao.invalidateAllApprovals(review.accountId, at, REASON_TEMPLATE_CHANGED)
      dao.markConfiguredRecipientsForReview(review.accountId, at, REASON_TEMPLATE_CHANGED)
      dao.invalidateTestReceipts(review.accountId, at, REASON_TEMPLATE_CHANGED)
      check(dao.bumpControlBlocker(control.revision, control.blockerRevision) == 1)
      if (geminiProvenance != null) persistedGeminiDraft = geminiDraft
      ConfigurationOutcome.Success(
        JSONObject()
          .put("draft", draft.toJson())
          .put("affectedRecipientCount", affected)
          .put("invalidatedApprovalCount", invalidated),
        setOf("messages", "contacts", "automation", "home", "readiness"),
      )
    }
    persistedGeminiDraft?.let { geminiGateway?.consumeProvenance(it) }
    return outcome
  }

  suspend fun previewPolicy(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (subscriptionChangePending()) return conflict("sim-changed")
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("draft", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val draftJson = request.optJSONObject("draft") ?: return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    val (draft, issues) = ConfigurationPolicyValidator.parse(draftJson)
    if (draft == null) {
      return ConfigurationOutcome.Success(JSONObject().put("kind", "invalid").put("issues", issues))
    }
    val subscription = subscriptionResolver.resolveDefault()
    if (subscription is SubscriptionResolution.Rejected) {
      return ConfigurationOutcome.Success(
        JSONObject()
          .put("kind", "invalid")
          .put("issues", JSONArray().put(fieldIssue("sim", subscription.code))),
      )
    }
    subscription as SubscriptionResolution.Ready
    val account = dao.activeAccount() ?: return conflict("account-reconnect-required")
    val control = dao.control() ?: return internal("CONFIGURATION_CONTROL_MISSING")
    if (control.revision != expectedRevision) return stale(control.revision)
    val zoneId = zoneProvider.zoneId()
    val simulation = ConfigurationPolicyValidator.simulate(
      draft,
      dao.configuredBirthdayRowsForCapacity(account.accountId),
      currentLocalDate(zoneId),
      zoneId,
      recurrencePlanner,
    )
    if (!simulation.isAcceptableFor(draft)) {
      return ConfigurationOutcome.Success(
        JSONObject()
          .put("kind", "invalid")
          .put("issues", JSONArray().put(fieldIssue("window", "window-capacity-conflict")))
          .apply { simulation.firstConflictDate?.let { put("firstConflictDate", it.toString()) } },
      )
    }
    val reviewPayload = JSONObject()
      .put("startMinute", draft.startMinute)
      .put("endMinute", draft.endMinute)
      .put("graceEndMinute", draft.graceEndMinute ?: JSONObject.NULL)
      .put("dailyCap", draft.dailyCap)
      .put("latePolicy", draft.latePolicy)
      .put("timeZoneId", zoneId.id)
      .put("subscriptionId", subscription.subscriptionId)
      .put("simLabel", subscription.label)
    val handle = createReview(REVIEW_POLICY, reviewPayload, account, control)
      ?: return internal("CONFIGURATION_REVIEW_CREATE_FAILED")
    return ConfigurationOutcome.Success(
      JSONObject()
        .put("kind", "valid")
        .put("handle", handle)
        .put("summary", ConfigurationPolicyValidator.summary(draft))
        .put("simulatedDays", ConfigurationPolicyValidator.SIMULATED_DAYS)
        .put("maximumPlannedInLocalDay", simulation.maximumLocalDay)
        .put("maximumPlannedInRolling24Hours", simulation.maximumRolling24Hours),
    )
  }

  suspend fun savePolicy(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (subscriptionChangePending()) return conflict("sim-changed")
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("handle", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val handle = request.optString("handle")
    if (!validHandle(handle, "pr")) return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    return database.withTransaction {
      val material = loadReview(handle, REVIEW_POLICY, expectedRevision)
        ?: return@withTransaction reviewProblem(handle, expectedRevision)
      val (review, payload, control) = material
      val currentSubscription = subscriptionResolver.resolveDefault()
      if (
        currentSubscription !is SubscriptionResolution.Ready ||
        currentSubscription.subscriptionId != payload.optInt("subscriptionId", -1)
      ) return@withTransaction validation("sim", "sim-changed")
      val revision = dao.latestPolicyRevision(review.accountId).incrementOrNull()
        ?: return@withTransaction internal("CONFIGURATION_REVISION_EXHAUSTED")
      val start = payload.optInt("startMinute", -1)
      val end = payload.optInt("endMinute", -1)
      val grace = payload.optNullableInt("graceEndMinute")
      val cap = payload.optInt("dailyCap", -1)
      if (start !in 0..1439 || end !in 1..1440 || start >= end || cap !in 1..20) {
        return@withTransaction internal("CONFIGURATION_REVIEW_CORRUPT")
      }
      val zoneId = runCatching { ZoneId.of(payload.optString("timeZoneId")) }.getOrNull()
        ?: return@withTransaction internal("CONFIGURATION_REVIEW_CORRUPT")
      val capacityDraft = ParsedWindowDraft(start, end, grace, cap)
      capacityIssue(
        capacityDraft,
        dao.configuredBirthdayRowsForCapacity(review.accountId),
        zoneId,
      )?.let { return@withTransaction it }
      val at = now()
      val policy = AutomationPolicyEntity(
        policyId = opaqueId("pol", "BirthdayAutopilot.PolicyId.v1", review.accountId, revision.toString()),
        accountId = review.accountId,
        revision = revision,
        state = PolicyRecordState.ACTIVE,
        timeZoneId = payload.optString("timeZoneId"),
        windowStartMinute = start,
        windowEndMinute = end,
        graceEndMinute = grace,
        latePolicy = payload.optString("latePolicy"),
        dailyCap = cap,
        simPolicyKind = "SYSTEM_DEFAULT",
        resolvedSubscriptionId = currentSubscription.subscriptionId,
        roamingAllowed = false,
        policyVersion = POLICY_VERSION,
        createdAtMillis = at,
        invalidatedAtMillis = null,
        invalidationReason = null,
      )
      check(
        dao.markReviewConsumed(
          handle,
          REVIEW_POLICY,
          review.controlRevision,
          review.blockerRevision,
          at,
        ) == 1,
      )
      dao.supersedePolicies(review.accountId, at, REASON_POLICY_CHANGED)
      dao.insertAutomationPolicy(policy)
      dao.invalidateAllApprovals(review.accountId, at, REASON_POLICY_CHANGED)
      dao.markConfiguredRecipientsForReview(review.accountId, at, REASON_POLICY_CHANGED)
      dao.invalidateTestReceipts(review.accountId, at, REASON_POLICY_CHANGED)
      check(dao.bumpControlBlocker(control.revision, control.blockerRevision) == 1)
      ConfigurationOutcome.Success(JSONObject(), CONFIG_INVALIDATIONS)
    }
  }

  suspend fun prepareApprovals(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (subscriptionChangePending()) return conflict("sim-changed")
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("contactIds", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val contactIds = request.optJSONArray("contactIds").opaqueIdsOrNull()
      ?: return ConfigurationOutcome.InvalidRequest
    if (contactIds.isEmpty() || contactIds.size > MAX_REVIEW_ITEMS) {
      return ConfigurationOutcome.InvalidRequest
    }
    senderOwnershipProblem()?.let { return it }
    val account = dao.activeAccount() ?: return conflict("account-reconnect-required")
    val control = dao.control() ?: return internal("CONFIGURATION_CONTROL_MISSING")
    if (control.revision != expectedRevision) return stale(control.revision)
    val template = dao.activeTemplate(account.accountId)
      ?: return validation("template", "template-empty")
    val domainTemplate = template.toDomainTemplate()
      ?: return internal("CONFIGURATION_TEMPLATE_CORRUPT")
    val automationPolicy = dao.activeAutomationPolicy(account.accountId)
      ?: return validation("window", "invalid-window")
    val subscription = subscriptionResolver.resolveDefault()
    if (
      subscription !is SubscriptionResolution.Ready ||
      subscription.subscriptionId != automationPolicy.resolvedSubscriptionId
    ) return validation("sim", if (subscription is SubscriptionResolution.Rejected) subscription.code else "sim-changed")

    val contacts = dao.contacts(account.accountId, contactIds).associateBy(ContactSnapshotEntity::contactId)
    val recipientPolicies = dao.recipientPolicies(contactIds).associateBy(RecipientPolicyEntity::contactId)
    val phones = dao.phonesForContacts(contactIds).groupBy(ContactPhoneEntity::contactId)
    val candidates = ArrayList<ApprovalCandidate>()
    var blockedCount = 0
    contactIds.forEach { contactId ->
      val contact = contacts[contactId]
      val recipient = recipientPolicies[contactId]
      val phone = if (contact != null && recipient != null) {
        selectedPhone(contact, recipient, phones[contactId].orEmpty())
      } else {
        null
      }
      if (
        contact == null || recipient == null || phone == null ||
        contact.state != ContactSnapshotState.ACTIVE ||
        contact.birthdayMonth == null || contact.birthdayDay == null ||
        recipient.explicitEnrollmentEventId == null ||
        recipient.state in setOf(
          RecipientEnrollmentState.OFF,
          RecipientEnrollmentState.EXCLUDED,
          RecipientEnrollmentState.PAUSED,
        ) ||
        phone.destinationFingerprint == null || phone.normalizedE164 == null
      ) {
        blockedCount++
        return@forEach
      }
      val rendered = templateValidator.validateAndRender(
        domainTemplate,
        contact.safeGivenName,
        template.requestedSegmentCap,
      )
      val approvedMessage = ApprovedRenderedMessage.from(rendered)
      val plan = approvalPlanner.prepareForApproval(
        rendered.preview?.exactText.orEmpty(),
        automationPolicy.resolvedSubscriptionId,
        template.requestedSegmentCap,
      )
      if (approvedMessage == null || plan !is SmsApprovalPlanResult.Prepared) {
        blockedCount++
        return@forEach
      }
      candidates += ApprovalCandidate(contact, recipient, phone, rendered, plan)
    }
    val repeatedDestinations = candidates.groupingBy { it.phone.destinationFingerprint }.eachCount()
      .filterValues { it > 1 }.keys
    val ready = ArrayList<ApprovalCandidate>()
    candidates.forEach { candidate ->
      val fingerprint = checkNotNull(candidate.phone.destinationFingerprint)
      if (
        fingerprint in repeatedDestinations ||
        dao.enabledDuplicateDestinationCount(
          account.accountId,
          candidate.contact.contactId,
          fingerprint,
        ) > 0
      ) {
        blockedCount++
      } else {
        ready += candidate
      }
    }
    if (ready.isEmpty()) return validation("confirmation", "approval-missing")
    val reviewItems = JSONArray()
    val outputItems = JSONArray()
    ready.forEach { candidate ->
      val contact = candidate.contact
      val recipient = candidate.recipient
      val phone = candidate.phone
      val preview = checkNotNull(candidate.rendered.preview)
      val plan = candidate.plan
      reviewItems.put(
        JSONObject()
          .put("contactId", contact.contactId)
          .put("contactRevision", contact.materialRevision)
          .put("recipientPolicyRevision", recipient.revision)
          .put("phoneId", phone.phoneId)
          .put("phoneRevision", phone.materialRevision)
          .put("birthdayId", recipient.chosenBirthdayId ?: JSONObject.NULL)
          .put("templateId", template.templateId)
          .put("templateRevision", template.revision)
          .put("automationPolicyId", automationPolicy.policyId)
          .put("automationPolicyRevision", automationPolicy.revision)
          .put("exactText", preview.exactText)
          .put("segmentCount", plan.segmentPlan.segmentCount)
          .put("messageEncoding", plan.segmentPlan.encoding.name)
          .put("orderedPartsHash", plan.segmentPlan.orderedPartsHash)
          .put("orderedParts", JSONArray(plan.nativePlan.orderedParts)),
      )
      outputItems.put(
        JSONObject()
          .put("platform", "android")
          .put("contactId", contact.contactId)
          .put("recipient", contact.displayName)
          .put("maskedPhone", phone.maskedDisplay)
          .put(
            "birthdayLabel",
            birthdayLabel(contact)
              ?: appContext.getString(com.yashsomani.birthdayautopilot.R.string.birthday_selected),
          )
          .put("exactText", preview.exactText)
          .put("windowLabel", policyWindowLabel(automationPolicy))
          .put("simLabel", subscription.label)
          .put("segmentCount", plan.segmentPlan.segmentCount)
          .put("chargeDisclosure", CHARGE_DISCLOSURE)
          .put("consentDisclosure", CONSENT_DISCLOSURE),
      )
    }
    val handle = createReview(
      REVIEW_APPROVAL,
      JSONObject().put("items", reviewItems),
      account,
      control,
    ) ?: return internal("CONFIGURATION_REVIEW_CREATE_FAILED")
    return ConfigurationOutcome.Success(
      JSONObject()
        .put("handle", handle)
        .put("items", outputItems)
        .put("readyCount", ready.size)
        .put("blockedCount", blockedCount)
        .put("explicitConfirmationRequired", true),
    )
  }

  suspend fun confirmApprovals(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (subscriptionChangePending()) return conflict("sim-changed")
    if (
      !revisionEchoMatches(request, expectedRevision) ||
      request.keyNames() != setOf("handle", "expectedRevision")
    ) return ConfigurationOutcome.InvalidRequest
    val handle = request.optString("handle")
    if (!validHandle(handle, "ar")) return ConfigurationOutcome.InvalidRequest
    senderOwnershipProblem()?.let { return it }
    return database.withTransaction {
      val material = loadReview(handle, REVIEW_APPROVAL, expectedRevision)
        ?: return@withTransaction reviewProblem(handle, expectedRevision)
      val (review, payload, control) = material
      val template = dao.activeTemplate(review.accountId)
        ?: return@withTransaction stale(control.revision)
      val domainTemplate = template.toDomainTemplate()
        ?: return@withTransaction internal("CONFIGURATION_TEMPLATE_CORRUPT")
      val automationPolicy = dao.activeAutomationPolicy(review.accountId)
        ?: return@withTransaction stale(control.revision)
      val subscription = subscriptionResolver.resolveDefault()
      if (
        subscription !is SubscriptionResolution.Ready ||
        subscription.subscriptionId != automationPolicy.resolvedSubscriptionId
      ) return@withTransaction validation("sim", "sim-changed")
      val items = payload.optJSONArray("items")
        ?: return@withTransaction internal("CONFIGURATION_REVIEW_CORRUPT")
      if (items.length() !in 1..MAX_REVIEW_ITEMS) {
        return@withTransaction internal("CONFIGURATION_REVIEW_CORRUPT")
      }
      val at = now()
      val prepared = ArrayList<Pair<RecipientPolicyEntity, ApprovalSnapshotEntity>>()
      repeat(items.length()) { index ->
        val item = items.optJSONObject(index)
          ?: return@withTransaction internal("CONFIGURATION_REVIEW_CORRUPT")
        val contactId = item.optString("contactId")
        val contact = dao.contact(contactId)
          ?: return@withTransaction stale(control.revision)
        val recipient = dao.recipientPolicy(contactId)
          ?: return@withTransaction stale(control.revision)
        val phone = dao.phones(contactId).singleOrNull { it.phoneId == item.optString("phoneId") }
          ?: return@withTransaction stale(control.revision)
        if (
          contact.accountId != review.accountId ||
          contact.state != ContactSnapshotState.ACTIVE ||
          contact.materialRevision != item.optLong("contactRevision", -1) ||
          recipient.revision != item.optLong("recipientPolicyRevision", -1) ||
          recipient.chosenPhoneId != phone.phoneId ||
          recipient.explicitEnrollmentEventId == null ||
          recipient.state in setOf(
            RecipientEnrollmentState.OFF,
            RecipientEnrollmentState.EXCLUDED,
            RecipientEnrollmentState.PAUSED,
          ) ||
          phone.materialRevision != item.optLong("phoneRevision", -1) ||
          phone.state != PhoneRecordState.READY ||
          template.templateId != item.optString("templateId") ||
          template.revision != item.optLong("templateRevision", -1) ||
          automationPolicy.policyId != item.optString("automationPolicyId") ||
          automationPolicy.revision != item.optLong("automationPolicyRevision", -1) ||
          contact.birthdayMonth == null || contact.birthdayDay == null
        ) return@withTransaction stale(control.revision)
        val normalizedPhone = phone.normalizedE164?.let(CanonicalPhoneNumber::parse)
          ?: return@withTransaction validation("phone", "phone-invalid")
        val validation = templateValidator.validateAndRender(
          domainTemplate,
          contact.safeGivenName,
          template.requestedSegmentCap,
        )
        val message = ApprovedRenderedMessage.from(validation)
          ?: return@withTransaction validation("template", "template-control-character")
        if (!constantTextEquals(message.exactText, item.optString("exactText"))) {
          return@withTransaction stale(control.revision)
        }
        val plan = approvalPlanner.prepareForApproval(
          message.exactText,
          automationPolicy.resolvedSubscriptionId,
          template.requestedSegmentCap,
        )
        if (plan !is SmsApprovalPlanResult.Prepared) {
          return@withTransaction validation("template", "invalid-segment-cap")
        }
        if (
          plan.segmentPlan.segmentCount != item.optInt("segmentCount", -1) ||
          plan.segmentPlan.encoding.name != item.optString("messageEncoding") ||
          !ConfigurationCanonicalHash.matches(
            plan.segmentPlan.orderedPartsHash,
            item.optString("orderedPartsHash"),
          ) ||
          !jsonStringListEquals(item.optJSONArray("orderedParts"), plan.nativePlan.orderedParts)
        ) return@withTransaction validation("template", "invalid-segment-cap")
        val leap = contact.leapDayPolicy.toLeapPolicyOrNull()
        val approved = ImmutableApprovalSnapshotFactory.create(
          ApprovalMaterial(
            recipientId = contact.contactId,
            normalizedPhone = normalizedPhone,
            message = message,
            birthday = ApprovedBirthdayRecurrence(
              contact.birthdayMonth,
              contact.birthdayDay,
              leap,
            ),
            sendWindow = ApprovedSendWindow(
              automationPolicy.windowStartMinute,
              automationPolicy.windowEndMinute,
              automationPolicy.graceEndMinute,
              automationPolicy.latePolicy.toApprovedLatePolicy()
                ?: return@withTransaction internal("CONFIGURATION_POLICY_CORRUPT"),
            ),
            simPolicy = ApprovedSimPolicy(
              ApprovedSimPolicyKind.SYSTEM_DEFAULT,
              automationPolicy.resolvedSubscriptionId,
            ),
            segmentPlan = plan.segmentPlan,
            carrierCostDisclosureVersion = CHARGE_DISCLOSURE_VERSION,
            consentDisclosureVersion = CONSENT_DISCLOSURE_VERSION,
          ),
          at,
        )
        val snapshot = (approved as? ApprovalBuildResult.Created)?.snapshot
          ?: return@withTransaction internal("CONFIGURATION_APPROVAL_BUILD_REJECTED")
        prepared += recipient to ApprovalSnapshotEntity(
          approvalId = opaqueId(
            "ap",
            "BirthdayAutopilot.ApprovalId.v1",
            review.accountId,
            contact.contactId,
            snapshot.contentHash,
          ),
          accountId = review.accountId,
          contactId = contact.contactId,
          phoneId = phone.phoneId,
          schemaVersion = snapshot.schemaVersion,
          contactMaterialRevision = contact.materialRevision,
          phoneMaterialRevision = phone.materialRevision,
          policyId = automationPolicy.policyId,
          policyRevision = automationPolicy.revision,
          normalizedPhoneE164 = snapshot.normalizedPhoneE164,
          destinationFingerprint = checkNotNull(phone.destinationFingerprint),
          maskedPhoneDisplay = snapshot.maskedPhoneDisplay,
          exactMessage = snapshot.exactText,
          sourceTemplateId = template.templateId,
          sourceTemplateVersion = snapshot.sourceTemplateVersion,
          placeholderMode = snapshot.placeholderMode.name,
          birthdayMonth = snapshot.birthdayMonth,
          birthdayDay = snapshot.birthdayDay,
          leapDayPolicy = snapshot.leapDayPolicy?.name,
          windowStartMinute = snapshot.windowStartMinuteOfDay,
          windowEndMinute = snapshot.windowEndMinuteOfDay,
          graceEndMinute = snapshot.graceEndMinuteOfDay,
          latePolicy = snapshot.latePolicy.name,
          simPolicyKind = snapshot.simPolicyKind.name,
          resolvedSubscriptionId = snapshot.resolvedSubscriptionId,
          segmentCount = snapshot.segmentCount,
          messageEncoding = snapshot.messageEncoding.name,
          orderedPartsHash = snapshot.orderedPartsHash,
          carrierCostDisclosureVersion = snapshot.carrierCostDisclosureVersion,
          consentDisclosureVersion = snapshot.consentDisclosureVersion,
          contentHash = snapshot.contentHash,
          state = ApprovalRecordState.ACTIVE,
          approvedAtMillis = snapshot.approvedAtEpochMillis,
          invalidatedAtMillis = null,
          invalidationReason = null,
        )
      }
      capacityIssue(review.accountId)?.let { return@withTransaction it }
      check(
        dao.markReviewConsumed(
          handle,
          REVIEW_APPROVAL,
          review.controlRevision,
          review.blockerRevision,
          at,
        ) == 1,
      )
      prepared.forEach { (recipient, approval) ->
        dao.invalidateApprovals(recipient.contactId, at, REASON_REAPPROVED)
        dao.insertApproval(approval)
        check(
          dao.updateRecipientPolicy(
            recipient.copy(
              state = RecipientEnrollmentState.ENABLED,
              blockReason = null,
              approvalId = approval.approvalId,
              revision = recipient.revision.incrementOrNull()
                ?: error("recipient-policy-revision-exhausted"),
              enabledAtMillis = at,
              updatedAtMillis = at,
            ),
          ) == 1,
        )
      }
      val reviewScope = ConfigurationCanonicalHash.content(
        "BirthdayAutopilot.ApprovalConsentScope.v1",
        prepared.map { it.second.contentHash }.sorted(),
      )
      insertConsent(review.accountId, ConsentKind.SMS_STANDING_APPROVAL, reviewScope, at)
      insertConsent(review.accountId, ConsentKind.CARRIER_COST, reviewScope, at)
      dao.invalidateTestReceipts(review.accountId, at, REASON_APPROVAL_CHANGED)
      check(dao.bumpControlBlocker(control.revision, control.blockerRevision) == 1)
      ConfigurationOutcome.Success(JSONObject(), CONFIG_INVALIDATIONS)
    }
  }

  private suspend fun insertConsent(
    accountId: String,
    kind: ConsentKind,
    scopeHash: String,
    atMillis: Long,
  ) {
    val sequence = dao.latestConsentSequence(accountId, kind).incrementOrNull()
      ?: error("consent-sequence-exhausted")
    dao.insertConsentReceipt(
      ConsentReceiptEntity(
        receiptId = opaqueId("cr", "BirthdayAutopilot.Consent.v1", accountId, kind.name, sequence.toString()),
        accountId = accountId,
        kind = kind,
        decision = ConsentDecision.GRANTED,
        disclosureVersion = if (kind == ConsentKind.CARRIER_COST) {
          CHARGE_DISCLOSURE_VERSION
        } else {
          CONSENT_DISCLOSURE_VERSION
        },
        scopeHash = scopeHash,
        sequence = sequence,
        supersedesReceiptId = null,
        recordedAtMillis = atMillis,
      ),
    )
  }

  private suspend fun contactDetail(
    contact: ContactSnapshotEntity,
    account: AccountRecordEntity,
  ): JSONObject {
    val policy = dao.recipientPolicy(contact.contactId)
    val phones = dao.phones(contact.contactId)
    val birthdays = dao.birthdays(contact.contactId)
    val summary = contactSummary(contact, account, policy, phones, birthdays)
    val phoneChoices = JSONArray()
    phones.sortedBy(ContactPhoneEntity::phoneId)
      .takeBoundedChoices(policy?.chosenPhoneId)
      .forEach { phone ->
        phoneChoices.put(
          JSONObject()
            .put("id", phone.phoneId)
            .put("maskedDisplay", phone.maskedDisplay.takeIf(::safeMaskedPhone) ?: "•••• 0000")
            .put("sourceLabel", safeSourceLabel(phone.typeLabel))
            .put("selectable", phone.state == PhoneRecordState.READY)
            .apply { phoneIssue(phone.state)?.let { put("issue", it) } },
        )
      }
    val birthdayChoices = JSONArray()
    birthdays.takeBoundedBirthdayChoices(policy?.chosenBirthdayId).forEach { choice ->
      birthdayChoices.put(
        JSONObject()
          .put("id", choice.birthdayId)
          .put("displayLabel", birthdayChoiceLabel(choice))
          .put("hasYear", choice.birthdayYear != null)
          .put("selectable", choice.selectable)
          .apply { choice.issueCode?.takeIf(::safeContactIssue)?.let { put("issue", it) } },
      )
    }
    return JSONObject()
      .put("summary", summary)
      .put("phoneChoices", phoneChoices)
      .put("birthdayChoices", birthdayChoices)
      .put(
        "selectedDestinationBlocked",
        selectedPhone(contact, policy, phones)?.destinationFingerprint?.let { fingerprint ->
          dao.activeDestinationBlockCount(account.accountId, fingerprint) > 0
        } ?: false,
      )
      .apply {
        policy?.chosenPhoneId?.takeIf { selected -> phones.any { it.phoneId == selected } }
          ?.let { put("selectedPhoneId", it) }
        policy?.chosenBirthdayId?.takeIf { selected -> birthdays.any { it.birthdayId == selected } }
          ?.let { put("selectedBirthdayId", it) }
        nextOccurrenceLabel(contact)?.let { put("nextOccurrenceLabel", it) }
        dao.latestOccurrence(contact.contactId)?.safeOutcomeCode?.toVisibleOutcome()?.let {
          put("lastOutcomeLabel", it)
        }
      }
  }

  private suspend fun contactSummary(
    contact: ContactSnapshotEntity,
    account: AccountRecordEntity,
    policy: RecipientPolicyEntity?,
    phones: List<ContactPhoneEntity>,
    birthdays: List<ContactBirthdayChoiceEntity>,
  ): JSONObject {
    val selectedPhone = selectedPhone(contact, policy, phones)
    val readyPhones = phones.filter { it.state == PhoneRecordState.READY }
    val templateRequiresName = dao.activeTemplate(account.accountId)?.placeholderMode
      ?.let { it == TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME.name } ?: true
    val reasons = linkedSetOf<String>()
    if (contact.state != ContactSnapshotState.ACTIVE) reasons += "source-contact-deleted"
    if (contact.birthdayMonth == null || contact.birthdayDay == null) {
      reasons += if (birthdays.count { it.selectable } > 1) {
        "birthday-choice-required"
      } else {
        "birthday-missing"
      }
    }
    if (readyPhones.isEmpty()) reasons += "phone-missing"
    if (readyPhones.size > 1 && selectedPhone == null) reasons += "phone-choice-required"
    if (templateRequiresName && contact.safeGivenName == null) reasons += "safe-given-name-missing"
    selectedPhone?.destinationFingerprint?.let { fingerprint ->
      if (dao.activeDestinationBlockCount(account.accountId, fingerprint) > 0) {
        reasons += "phone-blocked-form"
      }
      if (dao.enabledDuplicateDestinationCount(account.accountId, contact.contactId, fingerprint) > 0) {
        reasons += "duplicate-destination"
      }
    }
    val enrollment = enrollmentProjection(contact, policy, selectedPhone)
    if (
      enrollment.optString("kind") == "paused" &&
      enrollment.optString("reason") == "approval-invalid"
    ) {
      reasons += "approval-invalid"
    }
    val readiness = when {
      contact.state != ContactSnapshotState.ACTIVE -> JSONObject()
        .put("kind", "unavailable")
        .put("reasons", JSONArray(reasons.ifEmpty { setOf("source-contact-deleted") }))
      reasons.isNotEmpty() -> JSONObject()
        .put("kind", "needs-attention")
        .put("reasons", JSONArray(reasons))
      else -> JSONObject().put("kind", "ready")
    }
    return JSONObject()
      .put("id", contact.contactId)
      .put("displayName", contact.displayName)
      .put("readiness", readiness)
      .put("enrollment", enrollment)
      .apply {
        birthdayLabel(contact)?.let { put("birthdayLabel", it) }
        selectedPhone?.maskedDisplay?.takeIf(::safeMaskedPhone)?.let { put("maskedPhone", it) }
      }
  }

  private suspend fun enrollmentProjection(
    contact: ContactSnapshotEntity,
    policy: RecipientPolicyEntity?,
    phone: ContactPhoneEntity?,
  ): JSONObject {
    if (policy == null || policy.state == RecipientEnrollmentState.OFF) {
      return JSONObject().put("kind", "off")
    }
    if (policy.state == RecipientEnrollmentState.EXCLUDED) {
      return JSONObject().put("kind", "excluded").apply {
        policy.blockReason.toSafeExclusionReason()?.let { put("reason", it) }
      }
    }
    val approval = policy.approvalId?.let { dao.approval(it) }
    val approvalPayload = approvalProjection(contact, policy, phone, approval)
    return if (
      policy.state == RecipientEnrollmentState.ENABLED &&
      approvalPayload.optString("kind") == "valid"
    ) {
      JSONObject().put("kind", "enabled").put("approval", approvalPayload)
    } else {
      JSONObject()
        .put("kind", "paused")
        .put(
          "reason",
          if (policy.state == RecipientEnrollmentState.PAUSED && approvalPayload.optString("kind") == "valid") {
            "policy-suspended"
          } else {
            "approval-invalid"
          },
        )
        .put("approval", approvalPayload)
    }
  }

  private suspend fun approvalProjection(
    contact: ContactSnapshotEntity,
    policy: RecipientPolicyEntity?,
  ): JSONObject {
    val phone = policy?.let { selectedPhone(contact, it, dao.phones(contact.contactId)) }
    val approval = policy?.approvalId?.let { dao.approval(it) }
    return approvalProjection(contact, policy, phone, approval)
  }

  private suspend fun approvalProjection(
    contact: ContactSnapshotEntity,
    policy: RecipientPolicyEntity?,
    phone: ContactPhoneEntity?,
    approval: ApprovalSnapshotEntity?,
  ): JSONObject = when {
    approval == null -> JSONObject().put("kind", "missing")
    approvalIsValid(contact, policy, approval, phone) -> JSONObject()
      .put("kind", "valid")
      .put("approvedAt", Instant.ofEpochMilli(approval.approvedAtMillis).toString())
    else -> JSONObject()
      .put("kind", "invalidated")
      .put("reasons", JSONArray(approval.invalidationReason.toApprovalReasons()))
  }

  private suspend fun approvalIsValid(
    contact: ContactSnapshotEntity,
    policy: RecipientPolicyEntity?,
    approval: ApprovalSnapshotEntity?,
    phone: ContactPhoneEntity? = null,
  ): Boolean {
    if (policy == null || approval == null) return false
    val selectedPhone = phone ?: selectedPhone(contact, policy, dao.phones(contact.contactId))
      ?: return false
    val activeTemplate = dao.activeTemplate(contact.accountId) ?: return false
    val activePolicy = dao.activeAutomationPolicy(contact.accountId) ?: return false
    return approval.state == ApprovalRecordState.ACTIVE &&
      approval.invalidatedAtMillis == null &&
      approval.contactId == contact.contactId &&
      approval.phoneId == selectedPhone.phoneId &&
      approval.contactMaterialRevision == contact.materialRevision &&
      approval.phoneMaterialRevision == selectedPhone.materialRevision &&
      approval.sourceTemplateId == activeTemplate.templateId &&
      approval.sourceTemplateVersion == activeTemplate.templateVersion &&
      approval.policyId == activePolicy.policyId &&
      approval.policyRevision == activePolicy.revision &&
      approval.normalizedPhoneE164 == selectedPhone.normalizedE164 &&
      approval.destinationFingerprint == selectedPhone.destinationFingerprint &&
      approval.birthdayMonth == contact.birthdayMonth &&
      approval.birthdayDay == contact.birthdayDay &&
      approval.leapDayPolicy == contact.leapDayPolicy
  }

  private suspend fun activeWindowLabel(accountId: String): String =
    dao.activeAutomationPolicy(accountId)?.let(::policyWindowLabel)
      ?: appContext.getString(com.yashsomani.birthdayautopilot.R.string.configuration_not_configured)

  private fun policyWindowLabel(policy: AutomationPolicyEntity): String = buildString {
    append(formatMinute(policy.windowStartMinute))
    append('–')
    append(formatMinute(policy.windowEndMinute))
    policy.graceEndMinute?.let {
      append(
        appContext.getString(
          com.yashsomani.birthdayautopilot.R.string.configuration_grace_to,
          formatMinute(it),
        ),
      )
    }
  }

  private suspend fun createReview(
    kind: String,
    payload: JSONObject,
    account: AccountRecordEntity,
    expectedControl: ControlEntity,
  ): String? = database.withTransaction {
    val now = now()
    val control = dao.control() ?: return@withTransaction null
    val currentAccount = dao.activeAccount() ?: return@withTransaction null
    if (
      control.revision != expectedControl.revision ||
      control.blockerRevision != expectedControl.blockerRevision ||
      currentAccount.accountId != account.accountId ||
      !accountSessionMatches(currentAccount)
    ) return@withTransaction null
    val payloadJson = payload.toString()
    if (payloadJson.length !in 2..MAX_REVIEW_PAYLOAD_CHARS) return@withTransaction null
    dao.deleteObsoleteReviews(now, (now - CONSUMED_REVIEW_RETENTION_MILLIS).coerceAtLeast(0))
    val handlePrefix = when (kind) {
      REVIEW_ENROLLMENT -> "er"
      REVIEW_MESSAGE -> "mp"
      REVIEW_POLICY -> "pr"
      REVIEW_APPROVAL -> "ar"
      REVIEW_TEST -> "tr"
      REVIEW_ACTIVATION -> "ac"
      REVIEW_RESUME -> "rs"
      else -> return@withTransaction null
    }
    val handle = "${handlePrefix}_${UUID.randomUUID().toString().replace("-", "")}" 
    val expires = now.plusOrNull(REVIEW_TTL_MILLIS) ?: return@withTransaction null
    val inserted = dao.insertReview(
      ConfigurationReviewEntity(
        reviewId = handle,
        accountId = account.accountId,
        kind = kind,
        payloadJson = payloadJson,
        payloadHash = ConfigurationCanonicalHash.payload(kind, payloadJson),
        controlRevision = control.revision,
        blockerRevision = control.blockerRevision,
        createdAtMillis = now,
        expiresAtMillis = expires,
        consumedAtMillis = null,
      ),
    )
    handle.takeIf { inserted != -1L }
  }

  private suspend fun loadReview(
    handle: String,
    kind: String,
    expectedRevision: Long,
  ): Triple<ConfigurationReviewEntity, JSONObject, ControlEntity>? {
    val now = now()
    val control = dao.control() ?: return null
    val review = dao.review(handle) ?: return null
    val account = dao.activeAccount() ?: return null
    if (
      review.kind != kind ||
      review.controlRevision != expectedRevision ||
      review.controlRevision != control.revision ||
      review.blockerRevision != control.blockerRevision ||
      review.consumedAtMillis != null ||
      review.createdAtMillis < 0 ||
      review.expiresAtMillis <= now ||
      review.createdAtMillis >= review.expiresAtMillis ||
      account.accountId != review.accountId ||
      !accountSessionMatches(account) ||
      !ConfigurationCanonicalHash.matches(
        ConfigurationCanonicalHash.payload(review.kind, review.payloadJson),
        review.payloadHash,
      )
    ) return null
    if (review.payloadJson.length !in 2..MAX_REVIEW_PAYLOAD_CHARS) return null
    val payload = try {
      JSONObject(review.payloadJson)
    } catch (_: Exception) {
      return null
    }
    return Triple(review, payload, control)
  }

  private suspend fun mutationGate(expectedRevision: Long): Pair<ControlEntity, AccountRecordEntity>? {
    val control = dao.control() ?: return null
    val account = dao.activeAccount() ?: return null
    return (control to account).takeIf {
      control.revision == expectedRevision &&
        accountSessionMatches(account) &&
        senderOwnershipProblem(control) == null
    }
  }

  private suspend fun senderOwnershipProblem(): ConfigurationOutcome? =
    senderOwnershipProblem(dao.control())

  private fun senderOwnershipProblem(
    control: ControlEntity?,
  ): ConfigurationOutcome? {
    val current = control ?: return internal("CONFIGURATION_CONTROL_MISSING")
    val mode = runCatching { AccountMode.valueOf(current.accountMode) }.getOrNull()
      ?: return internal("CONFIGURATION_CONTROL_INVALID")
    val reason = ConfigurationOwnershipPolicy.blockedReason(mode) ?: return null
    return conflict(reason)
  }

  private suspend fun staleOrInternal(expectedRevision: Long): ConfigurationOutcome {
    val latest = dao.control()?.revision ?: return internal("CONFIGURATION_CONTROL_MISSING")
    return if (latest != expectedRevision) stale(latest) else conflict("account-reconnect-required")
  }

  private suspend fun reviewProblem(
    handle: String,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    val latest = dao.control()?.revision ?: return internal("CONFIGURATION_CONTROL_MISSING")
    if (latest != expectedRevision) return stale(latest)
    val review = dao.review(handle)
    return if (review != null && review.controlRevision != latest) {
      stale(latest)
    } else {
      conflict("approval-invalid")
    }
  }

  private fun stale(latest: Long) = ConfigurationOutcome.Problem(
    JSONObject().put("kind", "stale-revision").put("latestRevision", latest.toString()),
  )

  private fun validation(field: String, code: String) = ConfigurationOutcome.Problem(
    JSONObject()
      .put("kind", "validation")
      .put("issues", JSONArray().put(fieldIssue(field, code))),
  )

  private fun conflict(code: String) = ConfigurationOutcome.Problem(
    JSONObject().put("kind", "conflict").put("code", code),
  )

  private fun temporary(code: String) = ConfigurationOutcome.Problem(
    JSONObject().put("kind", "temporarily-unavailable").put("code", code),
  )

  private fun unsupported(code: String) = ConfigurationOutcome.Problem(
    JSONObject().put("kind", "unsupported").put("code", code),
  )

  private fun internal(code: String) = ConfigurationOutcome.Problem(
    JSONObject().put("kind", "internal").put("supportCode", code),
  )

  private fun rejectedInternal(code: String) = TestStartOutcome.Rejected(internal(code))

  private fun rejectedConflict(code: String) = TestStartOutcome.Rejected(conflict(code))

  private fun rejectedTemporary(code: String) = TestStartOutcome.Rejected(temporary(code))

  private fun rejectedStale(revision: Long) = TestStartOutcome.Rejected(stale(revision))

  private fun fieldIssue(field: String, code: String) = JSONObject()
    .put("field", field)
    .put("code", code)

  private fun validationIssues(errors: Set<TemplateValidationError>): JSONArray {
    val seen = linkedSetOf<String>()
    return JSONArray().apply {
      errors.forEach { error ->
        val code = when (error) {
          TemplateValidationError.EMPTY -> "template-empty"
          TemplateValidationError.SEGMENT_CAP_INVALID,
          TemplateValidationError.SEGMENT_CAP_EXCEEDED,
          -> "invalid-segment-cap"
          TemplateValidationError.PLACEHOLDER_REQUIRED,
          TemplateValidationError.PLACEHOLDER_COUNT_INVALID,
          -> "template-placeholder-count"
          TemplateValidationError.PLACEHOLDER_NOT_ALLOWED,
          TemplateValidationError.UNRESOLVED_VARIABLE,
          -> "template-unsupported-placeholder"
          TemplateValidationError.BIRTHDAY_INTENT_REQUIRED ->
            "template-birthday-intent-required"
          TemplateValidationError.URL_NOT_ALLOWED -> "template-url-not-allowed"
          TemplateValidationError.TRACKING_OR_HASHTAG_NOT_ALLOWED ->
            "template-tracking-not-allowed"
          TemplateValidationError.PROMOTIONAL_CONTENT_NOT_ALLOWED ->
            "template-promotional-content"
          TemplateValidationError.SENSITIVE_OR_INVENTED_CLAIM_NOT_ALLOWED ->
            "template-sensitive-content"
          TemplateValidationError.LANGUAGE_MISMATCH -> "template-language-mismatch"
          TemplateValidationError.UNSAFE_UNICODE -> "template-control-character"
          else -> "internal-contract-invalid"
        }
        if (seen.add(code)) put(fieldIssue("template", code))
      }
    }
  }

  private fun messageReviewPayload(
    draft: ParsedMessageDraft,
    provenance: GeminiCandidateProvenance?,
  ): JSONObject = JSONObject()
    .put("draft", draft.toJson())
    .apply {
      provenance?.let { value ->
        put(
          "geminiProvenance",
          JSONObject()
            .put("source", value.source)
            .put("candidateDigest", value.candidateDigest)
            .put("language", value.language)
            .put("tone", value.tone)
            .put("placeholderMode", value.placeholderMode)
            .put("requestedSegmentCap", value.requestedSegmentCap)
            .put("modelIdentifier", value.modelIdentifier)
            .put("promptPolicyVersion", value.promptPolicyVersion)
            .put("validatorVersion", value.validatorVersion),
        )
      }
    }

  private fun parseMessageReviewPayload(
    payload: JSONObject,
  ): Pair<ParsedMessageDraft, GeminiCandidateProvenance?>? {
    // Reviews created immediately before an app upgrade remain saveable, but without claiming
    // Gemini authorship because they carry no bound provenance.
    if (payload.keyNames() == MESSAGE_DRAFT_KEYS) {
      return parseMessageDraft(payload)?.let { it to null }
    }
    if (payload.keyNames() !in setOf(setOf("draft"), MESSAGE_REVIEW_KEYS)) return null
    val draft = parseMessageDraft(payload.optJSONObject("draft")) ?: return null
    if (!payload.has("geminiProvenance")) return draft to null
    val value = payload.optJSONObject("geminiProvenance") ?: return null
    if (value.keyNames() != GEMINI_PROVENANCE_KEYS) return null
    val candidateDigest = value.optString("candidateDigest")
      .takeIf { SHA256.matches(it) }
      ?: return null
    val source = value.optString("source").takeIf { it == "GEMINI" } ?: return null
    val language = value.optString("language").takeIf { it in setOf("en", "hi") } ?: return null
    val tone = value.optString("tone").takeIf { it in setOf("warm", "simple", "cheerful") }
      ?: return null
    val placeholder = value.optString("placeholderMode")
      .takeIf { it in setOf("given-name", "generic") }
      ?: return null
    val segmentCap = value.optInt("requestedSegmentCap", -1).takeIf { it in 1..2 }
      ?: return null
    val model = value.optString("modelIdentifier").takeIf(::safeProvenanceLabel) ?: return null
    val prompt = value.optString("promptPolicyVersion").takeIf(::safeProvenanceLabel) ?: return null
    val validator = value.optString("validatorVersion").takeIf(::safeProvenanceLabel) ?: return null
    return draft to GeminiCandidateProvenance(
      source = source,
      candidateDigest = candidateDigest,
      language = language,
      tone = tone,
      placeholderMode = placeholder,
      requestedSegmentCap = segmentCap,
      modelIdentifier = model,
      promptPolicyVersion = prompt,
      validatorVersion = validator,
    )
  }

  private fun safeProvenanceLabel(value: String): Boolean =
    value.length in 1..128 && value.none(::unsafeUiCharacter)

  private data class ParsedMessageDraft(
    val language: String,
    val tone: String,
    val placeholderMode: TemplatePlaceholderMode,
    val text: String,
    val segmentCap: Int,
  ) {
    fun toJson(): JSONObject = JSONObject()
      .put("language", language)
      .put("tone", tone)
      .put(
        "placeholderMode",
        JSONObject()
          .put(
            "kind",
            if (placeholderMode == TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME) {
              "given-name"
            } else {
              "generic"
            },
          )
          .put(
            "requiredCount",
            if (placeholderMode == TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME) 1 else 0,
          ),
      )
      .put("text", text)
      .put("requestedSegmentCap", segmentCap)

    fun toDomainTemplate(): MessageTemplate = MessageTemplate(
      version = templateVersion(),
      language = if (language == "hi") MessageLanguage.HINDI else MessageLanguage.ENGLISH,
      placeholderMode = placeholderMode,
      source = matchingBuiltIn()?.let { DomainTemplateSource.BUILT_IN }
        ?: DomainTemplateSource.USER_EDITED,
      text = text,
    )

    fun templateVersion(): String = matchingBuiltIn()?.version
      ?: "local-template-v1-${ConfigurationCanonicalHash.content(
        "BirthdayAutopilot.MessageTemplateVersion.v1",
        canonicalValues(),
      ).take(12)}"

    fun matchingBuiltIn(): MessageTemplate? {
      val messageLanguage = if (language == "hi") MessageLanguage.HINDI else MessageLanguage.ENGLISH
      val candidate = if (placeholderMode == TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME) {
        BuiltInMessageTemplates.personalized(messageLanguage)
      } else {
        BuiltInMessageTemplates.generic(messageLanguage)
      }
      return candidate.takeIf { it.text == text }
    }

    fun canonicalValues(): List<String> = listOf(
      language,
      tone,
      placeholderMode.name,
      text,
      segmentCap.toString(),
      MessageTemplateValidator.VALIDATOR_VERSION,
    )

    fun toGeminiProvenanceDraft(): GeminiProvenanceDraft = GeminiProvenanceDraft(
      language = language,
      tone = tone,
      placeholderMode = if (
        placeholderMode == TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME
      ) {
        "given-name"
      } else {
        "generic"
      },
      requestedSegmentCap = segmentCap,
      text = text,
    )
  }

  private fun parseMessageDraft(value: JSONObject?): ParsedMessageDraft? {
    if (
      value == null ||
      value.keyNames() != setOf(
        "language",
        "tone",
        "placeholderMode",
        "text",
        "requestedSegmentCap",
      )
    ) return null
    val language = value.optString("language").takeIf { it in setOf("en", "hi") } ?: return null
    val tone = value.optString("tone").takeIf { it in setOf("warm", "simple", "cheerful") }
      ?: return null
    val placeholder = value.optJSONObject("placeholderMode") ?: return null
    if (placeholder.keyNames() != setOf("kind", "requiredCount")) return null
    val placeholderMode = when (placeholder.optString("kind")) {
      "given-name" -> TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME
        .takeIf { placeholder.optInt("requiredCount", -1) == 1 }
      "generic" -> TemplatePlaceholderMode.GENERIC_NO_NAME
        .takeIf { placeholder.optInt("requiredCount", -1) == 0 }
      else -> null
    } ?: return null
    val text = value.optString("text")
    if (text.length !in 1..MAX_MESSAGE_CHARS) return null
    val cap = value.optInt("requestedSegmentCap", -1).takeIf { it in 1..2 } ?: return null
    return ParsedMessageDraft(language, tone, placeholderMode, text, cap)
  }

  private fun MessageTemplateEntity.toDraftPayload(): JSONObject? {
    val mode = runCatching { TemplatePlaceholderMode.valueOf(placeholderMode) }.getOrNull()
      ?: return null
    if (
      languageTag !in setOf("en", "hi") ||
      tone !in setOf("warm", "simple", "cheerful") ||
      requestedSegmentCap !in 1..2 ||
      exactTemplateText.length !in 1..MAX_MESSAGE_CHARS
    ) return null
    return ParsedMessageDraft(
      language = languageTag,
      tone = tone,
      placeholderMode = mode,
      text = exactTemplateText,
      segmentCap = requestedSegmentCap,
    ).toJson()
  }

  private fun MessageTemplateEntity.toDomainTemplate(): MessageTemplate? {
    val language = when (languageTag) {
      "en" -> MessageLanguage.ENGLISH
      "hi" -> MessageLanguage.HINDI
      else -> return null
    }
    val mode = runCatching { TemplatePlaceholderMode.valueOf(placeholderMode) }.getOrNull()
      ?: return null
    if (
      tone !in setOf("warm", "simple", "cheerful") ||
      requestedSegmentCap !in 1..2 ||
      exactTemplateText.length !in 1..MAX_MESSAGE_CHARS
    ) return null
    return MessageTemplate(
      version = templateVersion,
      language = language,
      placeholderMode = mode,
      source = when (source) {
        TemplateSource.BUILT_IN -> DomainTemplateSource.BUILT_IN
        TemplateSource.USER -> DomainTemplateSource.USER_EDITED
        TemplateSource.GEMINI -> DomainTemplateSource.GEMINI_SELECTED
      },
      text = exactTemplateText,
    )
  }

  private fun defaultDraftPayload(): JSONObject = ParsedMessageDraft(
    language = "en",
    tone = "warm",
    placeholderMode = TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME,
    text = BuiltInMessageTemplates.personalized(MessageLanguage.ENGLISH).text,
    segmentCap = 2,
  ).toJson()

  private fun selectedPhone(
    contact: ContactSnapshotEntity,
    policy: RecipientPolicyEntity?,
    phones: List<ContactPhoneEntity>,
  ): ContactPhoneEntity? {
    if (policy == null || contact.state != ContactSnapshotState.ACTIVE) return null
    val ready = phones.filter { it.state == PhoneRecordState.READY }
    return policy.chosenPhoneId?.let { selected -> ready.singleOrNull { it.phoneId == selected } }
      ?: ready.singleOrNull()
  }

  private fun nextOccurrenceLabel(contact: ContactSnapshotEntity): String? {
    val month = contact.birthdayMonth ?: return null
    val day = contact.birthdayDay ?: return null
    val leap = contact.leapDayPolicy.toLeapPolicyOrNull()
    val date = try {
      recurrencePlanner.nextOccurrence(
        currentLocalDate(zoneProvider.zoneId()),
        BirthdayRule(month, day, leap),
      )
    } catch (_: IllegalArgumentException) {
      return null
    }
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(nativeLocaleProvider.current().presentationLocale)
      .format(date)
  }

  private fun birthdayLabel(contact: ContactSnapshotEntity): String? {
    val month = contact.birthdayMonth ?: return null
    val day = contact.birthdayDay ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return buildString {
      append(
        Month.of(month).getDisplayName(
          TextStyle.SHORT,
          nativeLocaleProvider.current().presentationLocale,
        ),
      )
      append(' ')
      append(day)
      contact.birthdayYear?.let { append(", $it") }
    }
  }

  private fun birthdayChoiceLabel(
    choice: ContactBirthdayChoiceEntity,
  ): String {
    val month = choice.birthdayMonth
    val day = choice.birthdayDay
    if (month == null || day == null || month !in 1..12 || day !in 1..31) {
      return appContext.getString(com.yashsomani.birthdayautopilot.R.string.birthday_incomplete)
    }
    return buildString {
      append(
        Month.of(month).getDisplayName(
          TextStyle.SHORT,
          nativeLocaleProvider.current().presentationLocale,
        ),
      )
      append(' ')
      append(day)
      choice.birthdayYear?.let { append(", $it") }
    }
  }

  private fun phoneIssue(state: PhoneRecordState): String? = when (state) {
    PhoneRecordState.READY -> null
    PhoneRecordState.NEEDS_REGION -> "phone-ambiguous-region"
    PhoneRecordState.UNSAFE_DESTINATION -> "phone-blocked-form"
    PhoneRecordState.NON_SMS,
    PhoneRecordState.INVALID,
    PhoneRecordState.DELETED,
    -> "phone-invalid"
  }

  private fun safeSourceLabel(value: String?): String {
    val candidate = value?.trim()?.takeIf { text ->
      text.length in 1..64 && text.none(::unsafeUiCharacter)
    }
    return candidate
      ?: appContext.getString(com.yashsomani.birthdayautopilot.R.string.phone_source_fallback)
  }

  private fun safeMaskedPhone(value: String): Boolean =
    value.length in 1..64 &&
      value.count(Char::isDigit) in 1..4 &&
      '+' !in value &&
      value.none(::unsafeUiCharacter)

  private fun unsafeUiCharacter(char: Char): Boolean =
    char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt()

  private fun safeContactIssue(value: String): Boolean = value in setOf(
    "birthday-missing",
    "birthday-conflict",
    "birthday-choice-required",
    "leap-policy-required",
  )

  private fun String?.toApprovalReasons(): List<String> = buildList {
    val raw = this@toApprovalReasons.orEmpty()
    if ("PHONE" in raw) add("phone-changed")
    if ("BIRTHDAY" in raw) add("birthday-changed")
    if ("NAME" in raw || "SOURCE" in raw) add("name-changed")
    if ("TEMPLATE" in raw) add("template-changed")
    if ("POLICY" in raw || "WINDOW" in raw) add("window-changed")
    if ("SIM" in raw) add("sim-changed")
    if ("SEGMENT" in raw) add("segment-plan-changed")
    if ("DISCLOSURE" in raw) add("disclosure-changed")
    if (isEmpty()) add("approval-invalid".toKnownApprovalFallback())
  }.distinct()

  private fun String.toKnownApprovalFallback(): String = "permission-policy-changed"

  private fun String?.toSafeExclusionReason(): String? = when (this) {
    "RECIPIENT_EXCLUDED" -> "policy-suspended"
    else -> null
  }

  private fun String?.toLeapPolicyOrNull(): LeapDayPolicy? = this?.let {
    runCatching { LeapDayPolicy.valueOf(it) }.getOrNull()
  }

  private fun String.toLeapPolicy(): LeapDayPolicy? = when (this) {
    "feb-28" -> LeapDayPolicy.FEBRUARY_28
    "mar-01" -> LeapDayPolicy.MARCH_1
    "skip" -> LeapDayPolicy.SKIP_NON_LEAP_YEAR
    else -> null
  }

  private fun String.toApprovedLatePolicy(): ApprovedLatePolicy? = when (this) {
    "SAME_DAY_WINDOW_ONLY" -> ApprovedLatePolicy.SAME_DAY_WINDOW_ONLY
    "SAME_DAY_GRACE" -> ApprovedLatePolicy.SAME_DAY_GRACE
    else -> null
  }

  private fun SmsEncoding.toWire(): String = when (this) {
    SmsEncoding.GSM_7 -> "gsm-7"
    SmsEncoding.UNICODE -> "unicode"
  }

  private fun RecipientEnrollmentState.afterMaterialEdit(): RecipientEnrollmentState = when (this) {
    RecipientEnrollmentState.OFF -> RecipientEnrollmentState.OFF
    RecipientEnrollmentState.EXCLUDED -> RecipientEnrollmentState.EXCLUDED
    else -> RecipientEnrollmentState.NEEDS_REVIEW
  }

  private fun String?.toVisibleOutcome(): String? = when (this) {
    "DELIVERED" -> appContext.getString(com.yashsomani.birthdayautopilot.R.string.outcome_delivered)
    "SENT_FROM_DEVICE" ->
      appContext.getString(com.yashsomani.birthdayautopilot.R.string.outcome_sent_from_device)
    "MISSED" -> appContext.getString(com.yashsomani.birthdayautopilot.R.string.outcome_missed)
    "SKIPPED" -> appContext.getString(com.yashsomani.birthdayautopilot.R.string.outcome_skipped)
    "UNKNOWN", "PARTIAL_UNKNOWN" ->
      appContext.getString(com.yashsomani.birthdayautopilot.R.string.outcome_unknown)
    else -> null
  }

  private fun revisionEchoMatches(request: JSONObject, expected: Long): Boolean =
    request.optString("expectedRevision") == expected.toString()

  private fun JSONArray?.opaqueIdsOrNull(): List<String>? {
    if (this == null || length() !in 1..MAX_REVIEW_ITEMS) return null
    val values = ArrayList<String>(length())
    repeat(length()) { index ->
      val value = optString(index)
      if (!validOpaque(value)) return null
      values += value
    }
    return values.takeIf { it.distinct().size == it.size }
  }

  private fun JSONObject.optNullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name, Int.MIN_VALUE)
      .takeUnless { it == Int.MIN_VALUE }

  private fun JSONObject.keyNames(): Set<String> = buildSet {
    val iterator = keys()
    while (iterator.hasNext()) add(iterator.next())
  }

  private fun jsonStringListEquals(value: JSONArray?, expected: List<String>): Boolean {
    if (value == null || value.length() != expected.size) return false
    return expected.indices.all { constantTextEquals(value.optString(it), expected[it]) }
  }

  private fun constantTextEquals(left: String, right: String): Boolean =
    MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))

  private fun validOpaque(value: String): Boolean =
    value.length in 1..128 && OPAQUE.matches(value)

  private fun validHandle(value: String, prefix: String): Boolean =
    value.startsWith("${prefix}_") && validOpaque(value)

  private fun opaqueId(prefix: String, domain: String, vararg values: String): String =
    "${prefix}_${ConfigurationCanonicalHash.content(domain, values.toList())}"

  private fun Long.incrementOrNull(): Long? = if (this in 0 until Long.MAX_VALUE) this + 1 else null

  private fun Long.plusOrNull(other: Long): Long? = try {
    Math.addExact(this, other)
  } catch (_: ArithmeticException) {
    null
  }

  private fun now(): Long = clock.nowMillis().coerceAtLeast(1)

  private suspend fun capacityIssue(
    accountId: String,
    rows: List<ConfiguredBirthdayRow>? = null,
    policy: AutomationPolicyEntity? = null,
  ): ConfigurationOutcome? {
    val currentPolicy = policy ?: dao.activeAutomationPolicy(accountId) ?: return null
    val currentRows = rows ?: dao.configuredBirthdayRowsForCapacity(accountId)
    val draft = currentPolicy.capacityDraftOrNull()
      ?: return internal("CONFIGURATION_POLICY_CORRUPT")
    val zoneId = runCatching { ZoneId.of(currentPolicy.timeZoneId) }.getOrNull()
      ?: return internal("CONFIGURATION_POLICY_CORRUPT")
    return capacityIssue(draft, currentRows, zoneId)
  }

  private fun capacityIssue(
    draft: ParsedWindowDraft,
    rows: List<ConfiguredBirthdayRow>,
    zoneId: ZoneId,
  ): ConfigurationOutcome? {
    val simulation = ConfigurationPolicyValidator.simulate(
      draft,
      rows,
      currentLocalDate(zoneId),
      zoneId,
      recurrencePlanner,
    )
    if (simulation.isAcceptableFor(draft)) return null
    return ConfigurationOutcome.Problem(
      JSONObject()
        .put("kind", "validation")
        .put("issues", JSONArray().put(fieldIssue("window", "window-capacity-conflict")))
        .apply { simulation.firstConflictDate?.let { put("firstConflictDate", it.toString()) } },
    )
  }

  private fun AutomationPolicyEntity.capacityDraftOrNull(): ParsedWindowDraft? {
    if (
      state != PolicyRecordState.ACTIVE ||
      windowStartMinute !in 0..1439 ||
      windowEndMinute !in 1..1440 ||
      windowStartMinute >= windowEndMinute ||
      dailyCap !in 1..20
    ) return null
    val expectedLatePolicy = if (graceEndMinute == null) {
      "SAME_DAY_WINDOW_ONLY"
    } else {
      "SAME_DAY_GRACE"
    }
    if (latePolicy != expectedLatePolicy) return null
    return ParsedWindowDraft(windowStartMinute, windowEndMinute, graceEndMinute, dailyCap)
  }

  private fun currentLocalDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(now()).atZone(zoneId).toLocalDate()

  private fun currentDistributionChannel(): DistributionChannel =
    buildSignalSource.read().distributionChannel

  private fun formatMinute(minute: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minute / 60,
    minute % 60,
  )

  private fun emptyCounts(): JSONObject = JSONObject()
    .put("configured", 0)
    .put("enabled", 0)
    .put("needsAttention", 0)
    .put("unavailable", 0)
    .put("today", 0)
    .put("nextSevenDays", 0)

  private fun List<ContactPhoneEntity>.takeBoundedChoices(
    selectedId: String?,
  ): List<ContactPhoneEntity> {
    val first = take(MAX_DETAIL_CHOICES).toMutableList()
    if (selectedId != null && first.none { it.phoneId == selectedId }) {
      find { it.phoneId == selectedId }?.let {
        if (first.size == MAX_DETAIL_CHOICES) first.removeAt(first.lastIndex)
        first += it
      }
    }
    return first
  }

  private fun List<ContactBirthdayChoiceEntity>.takeBoundedBirthdayChoices(
    selectedId: String?,
  ): List<ContactBirthdayChoiceEntity> {
    val first = take(MAX_DETAIL_CHOICES).toMutableList()
    if (selectedId != null && first.none { it.birthdayId == selectedId }) {
      find { it.birthdayId == selectedId }?.let {
        if (first.size == MAX_DETAIL_CHOICES) first.removeAt(first.lastIndex)
        first += it
      }
    }
    return first
  }

  private data class ApprovalCandidate(
    val contact: ContactSnapshotEntity,
    val recipient: RecipientPolicyEntity,
    val phone: ContactPhoneEntity,
    val rendered: com.yashsomani.birthdayautopilot.messages.TemplateValidationResult,
    val plan: SmsApprovalPlanResult.Prepared,
  )

  private data class TestBuildBinding(
    val versionCode: Long,
    val distributionChannel: String,
    val signingCertificateSha256: String,
    val hash: String,
  )

  private data class ActivationBinding(
    val installation: InstallationBindingEntity,
    val test: TestJobEntity,
    val receipt: TestReceiptEntity,
    val permit: CoordinationPermitEntity,
  )

  private companion object {
    val CAPACITY_CONFIGURED_STATES = setOf(
      RecipientEnrollmentState.ENABLED,
      RecipientEnrollmentState.BLOCKED,
      RecipientEnrollmentState.NEEDS_REVIEW,
    )
    const val REVIEW_ENROLLMENT = "ENROLLMENT"
    const val REVIEW_MESSAGE = "MESSAGE"
    const val REVIEW_POLICY = "POLICY"
    const val REVIEW_APPROVAL = "APPROVAL"
    const val REVIEW_TEST = "TEST"
    const val REVIEW_ACTIVATION = "ACTIVATION"
    const val REVIEW_RESUME = "RESUME"
    const val REVIEW_TTL_MILLIS = 10L * 60 * 1_000
    const val CONSUMED_REVIEW_RETENTION_MILLIS = 24L * 60 * 60 * 1_000
    const val MAX_REVIEW_PAYLOAD_CHARS = 256_000
    const val MAX_REVIEW_ITEMS = 50
    const val MAX_MESSAGE_CHARS = 1_000
    const val MAX_PREVIEW_EXAMPLES = 3
    const val PREVIEW_SCAN_LIMIT = 50
    const val MAX_DETAIL_CHOICES = 20
    const val MAX_PLANNED_CONTACTS = 100_000
    const val CHARGE_DISCLOSURE_VERSION = "sms-carrier-charges-v1"
    const val CONSENT_DISCLOSURE_VERSION = "standing-approval-v1"
    const val POLICY_VERSION = "global-window-v1"
    const val TEST_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000
    const val TEST_MESSAGE =
      "Birthday Autopilot test message. No birthday greeting will be sent."
    const val TEST_CHARGE_DISCLOSURE =
      "This sends a real SMS now. Your carrier may charge for each SMS segment."
    const val ACTIVATION_LIMITATIONS =
      "Android schedules best-effort background work. Phone restrictions, network, SIM, carrier, or force-stop can still delay or prevent a greeting."
    const val CHARGE_DISCLOSURE =
      "Your carrier may charge for every SMS segment. Roaming is not used unless separately approved."
    const val CONSENT_DISCLOSURE =
      "Confirming stores this exact recipient, number, birthday, message, window, SIM and segment plan for future birthday sends."
    const val REASON_PHONE_SELECTION = "PHONE_SELECTION_CHANGED"
    const val REASON_BIRTHDAY_SELECTION = "BIRTHDAY_SELECTION_CHANGED"
    const val REASON_TEMPLATE_CHANGED = "TEMPLATE_CHANGED"
    const val REASON_POLICY_CHANGED = "POLICY_CHANGED"
    const val REASON_EXCLUDED = "RECIPIENT_EXCLUDED"
    const val REASON_DESTINATION_BLOCKED = "USER_DESTINATION_BLOCKED"
    const val REASON_REAPPROVED = "REAPPROVED"
    const val REASON_ENROLLMENT_CHANGED = "ENROLLMENT_CHANGED"
    const val REASON_APPROVAL_CHANGED = "APPROVAL_CHANGED"
    val OPAQUE = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]*$")
    val SHA256 = Regex("^[0-9a-f]{64}$")
    val MESSAGE_DRAFT_KEYS = setOf(
      "language",
      "tone",
      "placeholderMode",
      "text",
      "requestedSegmentCap",
    )
    val MESSAGE_REVIEW_KEYS = setOf("draft", "geminiProvenance")
    val GEMINI_PROVENANCE_KEYS = setOf(
      "source",
      "candidateDigest",
      "language",
      "tone",
      "placeholderMode",
      "requestedSegmentCap",
      "modelIdentifier",
      "promptPolicyVersion",
      "validatorVersion",
    )
    val TEST_PHONE_INPUT = Regex("^[+0-9 ()-]{7,32}$")
    val TEST_REVIEW_KEYS = setOf(
      "normalizedDestination",
      "maskedDestination",
      "destinationFingerprint",
      "destinationPrehash",
      "exactMessage",
      "resolvedSubscriptionId",
      "simLabel",
      "messageEncoding",
      "segmentCount",
      "orderedParts",
      "orderedPartsHash",
      "configHash",
      "payloadHash",
      "buildBindingHash",
      "installationId",
      "senderEpoch",
    )
    val ACTIVATION_REVIEW_KEYS = setOf(
      "installationId",
      "senderEpoch",
      "resetGeneration",
      "testJobId",
      "testClaimId",
      "receiptBindingHash",
      "policyId",
      "policyRevision",
      "approvalId",
      "enabledRecipientCount",
      "attentionCount",
      "resolvedSubscriptionId",
    )
    val CONFIG_INVALIDATIONS = setOf("contacts", "automation", "home", "readiness")
  }
}
