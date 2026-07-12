package com.yashsomani.birthdayautopilot.automation.orchestration

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.yashsomani.birthdayautopilot.approvals.ApprovedSegmentPlan
import com.yashsomani.birthdayautopilot.auth.ForegroundActivityRegistry
import com.yashsomani.birthdayautopilot.messages.AndroidSmsManagerPlanSource
import com.yashsomani.birthdayautopilot.messages.NativeSmsPlanResult
import com.yashsomani.birthdayautopilot.readiness.AndroidReadinessProbe
import com.yashsomani.birthdayautopilot.readiness.EligibilityKind
import com.yashsomani.birthdayautopilot.storage.database.FinalExternalGateSnapshot
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import com.yashsomani.birthdayautopilot.storage.database.SafetyLedgerDao
import com.yashsomani.birthdayautopilot.storage.database.SyncFreshness
import java.time.ZoneId

internal interface AutomationTimeSource {
  fun wallMillis(): Long
  fun elapsedRealtimeMillis(): Long
  fun bootCount(): Int?
}

internal data class GatePermitReference(
  val purpose: OperationPurpose,
  val operationId: String,
  val payloadHash: String,
)

internal class AndroidAutomationTimeSource(context: Context) : AutomationTimeSource {
  private val appContext = context.applicationContext

  override fun wallMillis(): Long = System.currentTimeMillis()

  override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

  override fun bootCount(): Int? = try {
    Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT)
      .takeIf { it >= 0 }
  } catch (_: Exception) {
    null
  }
}

internal class AndroidFinalExternalGateSource(
  context: Context,
  private val ledger: SafetyLedgerDao,
  private val orchestrationDao: AutomationOrchestrationDao,
  private val readinessProbe: AndroidReadinessProbe,
  private val accountSessionMatches: suspend (String) -> Boolean,
  private val timeSource: AutomationTimeSource,
) {
  private val appContext = context.applicationContext
  private val planSource = AndroidSmsManagerPlanSource(appContext)

  suspend fun snapshot(
    permit: GatePermitReference,
    foregroundConfirmationNonceHash: String? = null,
  ): FinalExternalGateSnapshot {
    val readiness = readinessProbe.read()
    val account = orchestrationDao.activeAccount()
    val sync = account?.let { orchestrationDao.contactSyncState(it.accountId) }
    val material = loadMaterial(permit)
    val installation = orchestrationDao.localInstallation()
    val callbackGenerationAligned = installation != null &&
      orchestrationDao.callbackCounterGeneration() == installation.callbackGeneration
    val timeZoneAligned = permit.purpose != OperationPurpose.BIRTHDAY ||
      ledger.getBirthdayOccurrence(permit.operationId)?.timeZoneId == ZoneId.systemDefault().id
    val activeSubscription = material?.let { isActiveSubscription(it.subscriptionId) } == true
    val currentPlanHash = material?.let(::currentOrderedPartsHash)
    val backgroundAllowed = readiness.signals.let { signals ->
      signals.backgroundRestricted == false &&
        signals.dozeAllowlisted == true &&
        signals.unusedAppRestrictionsDisabled == true &&
        signals.dataSaverAllowsBackground == true &&
        signals.lowPowerStandbySafe == true
    }
    val confirmationValid = permit.purpose == OperationPurpose.TEST &&
      ForegroundTestConfirmationPolicy.isValid(
        expectedNonceHash = material?.foregroundConfirmationNonceHash,
        suppliedNonceHash = foregroundConfirmationNonceHash,
        foregroundConfirmedAtMillis = material?.foregroundConfirmedAtMillis,
        wallNowMillis = timeSource.wallMillis(),
        resumedActivityPresent = ForegroundActivityRegistry.current() != null,
      )
    return FinalExternalGateSnapshot(
      distributionEligible = readiness.eligibility.kind == EligibilityKind.SUPPORTED,
      accountSessionValid = account != null && accountSessionMatches(account.accountId),
      contactsAuthorizationValid = sync?.freshness in setOf(
        SyncFreshness.FRESH,
        SyncFreshness.STALE_WARNING,
      ),
      networkValidated = readiness.signals.networkValidated == true,
      backgroundAllowed = backgroundAllowed && timeZoneAligned,
      smsPermissionGranted = readiness.smsPermissionGranted == true,
      simReady = readiness.signals.simReady == true && activeSubscription && callbackGenerationAligned,
      currentSubscriptionId = material?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
      payloadHash = material?.payloadHash.orEmpty(),
      orderedPartsHash = currentPlanHash.orEmpty(),
      foregroundConfirmationValid = confirmationValid,
      foregroundConfirmationNonceHash = foregroundConfirmationNonceHash,
      observedAtElapsedRealtimeMillis = timeSource.elapsedRealtimeMillis(),
      bootCount = timeSource.bootCount() ?: -1,
    )
  }

  private suspend fun loadMaterial(permit: GatePermitReference): GateMaterial? = when (permit.purpose) {
    OperationPurpose.BIRTHDAY -> {
      val occurrence = ledger.getBirthdayOccurrence(permit.operationId) ?: return null
      val approval = ledger.getApproval(occurrence.approvalId) ?: return null
      GateMaterial(
        payloadHash = approval.contentHash,
        exactText = approval.exactMessage,
        subscriptionId = approval.resolvedSubscriptionId,
        approvedPartCount = approval.segmentCount,
        approvedEncoding = approval.messageEncoding,
        approvedPartsHash = approval.orderedPartsHash,
        foregroundConfirmationNonceHash = null,
        foregroundConfirmedAtMillis = null,
      )
    }
    OperationPurpose.TEST -> {
      val test = ledger.getTestJob(permit.operationId) ?: return null
      GateMaterial(
        payloadHash = test.payloadHash,
        exactText = test.exactMessage,
        subscriptionId = test.resolvedSubscriptionId,
        approvedPartCount = test.segmentCount,
        approvedEncoding = test.messageEncoding,
        approvedPartsHash = test.orderedPartsHash,
        foregroundConfirmationNonceHash = test.foregroundConfirmationNonceHash,
        foregroundConfirmedAtMillis = test.foregroundConfirmedAtMillis,
      )
    }
  }.takeIf { it.payloadHash == permit.payloadHash }

  private fun currentOrderedPartsHash(material: GateMaterial): String? {
    val plan = when (val result = planSource.plan(material.exactText, material.subscriptionId)) {
      is NativeSmsPlanResult.Planned -> result.plan
      is NativeSmsPlanResult.Rejected -> return null
    }
    if (
      plan.segmentCount != material.approvedPartCount ||
      plan.encoding.name != material.approvedEncoding
    ) return null
    val binding = ApprovedSegmentPlan.bind(
      exactText = material.exactText,
      encoding = plan.encoding,
      orderedParts = plan.orderedParts,
      approvedSegmentCap = 2,
    ) ?: return null
    return binding.orderedPartsHash.takeIf { it == material.approvedPartsHash }
  }

  @SuppressLint("MissingPermission")
  private fun isActiveSubscription(subscriptionId: Int): Boolean {
    if (
      !SubscriptionManager.isValidSubscriptionId(subscriptionId) ||
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) !=
      PackageManager.PERMISSION_GRANTED
    ) return false
    return try {
      appContext.getSystemService(SubscriptionManager::class.java)
        .isActiveSubscriptionId(subscriptionId)
    } catch (_: Exception) {
      false
    } catch (_: LinkageError) {
      false
    }
  }

  private data class GateMaterial(
    val payloadHash: String,
    val exactText: String,
    val subscriptionId: Int,
    val approvedPartCount: Int,
    val approvedEncoding: String,
    val approvedPartsHash: String,
    val foregroundConfirmationNonceHash: String?,
    val foregroundConfirmedAtMillis: Long?,
  ) {
    override fun toString(): String = "GateMaterial(<redacted>)"
  }
}
