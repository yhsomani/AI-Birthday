package com.yashsomani.birthdayautopilot.automation.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.yashsomani.birthdayautopilot.BuildConfig
import com.yashsomani.birthdayautopilot.approvals.ApprovedSegmentPlan
import com.yashsomani.birthdayautopilot.auth.ForegroundActivityRegistry
import com.yashsomani.birthdayautopilot.automation.orchestration.ForegroundTestConfirmationPolicy
import com.yashsomani.birthdayautopilot.automation.workers.SmsOutcomeWorkScheduler
import com.yashsomani.birthdayautopilot.contacts.UnicodeTextSafety
import com.yashsomani.birthdayautopilot.messages.AndroidSmsManagerPlanSource
import com.yashsomani.birthdayautopilot.messages.NativeSmsPlan
import com.yashsomani.birthdayautopilot.messages.NativeSmsPlanResult
import com.yashsomani.birthdayautopilot.readiness.AndroidReadinessProbe
import com.yashsomani.birthdayautopilot.readiness.EligibilityKind
import com.yashsomani.birthdayautopilot.storage.database.ArmedAttemptPermit
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDao
import com.yashsomani.birthdayautopilot.storage.database.CallbackKind
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import com.yashsomani.birthdayautopilot.storage.database.SafetyLedgerDao
import com.yashsomani.birthdayautopilot.storage.database.SendAttemptState
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayList
import java.util.concurrent.TimeUnit

internal sealed interface SmsSubmissionResult {
  data object Submitted : SmsSubmissionResult
  data class Refused(val safeCode: String) : SmsSubmissionResult
  data class OutcomeUnknown(val safeCode: String) : SmsSubmissionResult
}

/** The sole production entry point capable of invoking an Android SMS submission API. */
internal class AndroidSmsGateway(
  context: Context,
  private val ledger: SafetyLedgerDao,
  private val birthdayDao: BirthdayDao,
  private val submissionGate: SubmissionGate = SubmissionGate(context),
  private val readinessProbe: AndroidReadinessProbe = AndroidReadinessProbe(context),
  private val subscriptionChangeSignalStore: SubscriptionChangeSignalStore,
  private val platformSubmitter: SmsPlatformSubmitter = AndroidSmsPlatformSubmitter,
) {
  private val appContext = context.applicationContext
  private val planSource = AndroidSmsManagerPlanSource(appContext)

  suspend fun submit(permit: ArmedAttemptPermit): SmsSubmissionResult {
    val result = submissionGate.withExclusiveBoundary {
      val payload = LocalSendPayloadLoader.load(permit, ledger)
        ?: return@withExclusiveBoundary SmsSubmissionResult.Refused("SMS_PAYLOAD_UNAVAILABLE")
      submitInsideBoundary(permit, payload)
    }
    if (result == SmsSubmissionResult.Submitted) {
      // WorkManager persistence happens after releasing SubmissionGate. The immediate reducer
      // installs the sent watchdog even when no platform callback ever arrives.
      SmsOutcomeWorkScheduler.enqueueEvidenceNow(appContext, permit.sendAttemptId)
    }
    return result
  }

  private suspend fun submitInsideBoundary(
    permit: ArmedAttemptPermit,
    payload: LocalSendPayload,
  ): SmsSubmissionResult {
    val attempt = ledger.getSendAttempt(permit.sendAttemptId)
      ?: return SmsSubmissionResult.Refused("SMS_ATTEMPT_UNAVAILABLE")
    when (attempt.state) {
      SendAttemptState.SUBMITTED,
      SendAttemptState.SENT_FROM_DEVICE,
      -> return SmsSubmissionResult.Submitted
      SendAttemptState.API_CALL_STARTED,
      SendAttemptState.UNKNOWN,
      SendAttemptState.PARTIAL_UNKNOWN,
      -> return SmsSubmissionResult.OutcomeUnknown("SMS_PRIOR_BOUNDARY_UNRESOLVED")
      SendAttemptState.BARRIER_CONSUMED -> Unit
      SendAttemptState.RETRYABLE_ZERO,
      SendAttemptState.PERMANENT_FAILURE,
      SendAttemptState.TERMINAL,
      -> return SmsSubmissionResult.Refused("SMS_ATTEMPT_ALREADY_CLOSED")
    }
    if (attempt.revision < 0 || payload.payloadHash != permit.payloadHash) {
      return SmsSubmissionResult.Refused("SMS_BINDING_INVALID")
    }
    val environment = verifyEnvironment(payload)
      ?: return SmsSubmissionResult.Refused("SMS_ENVIRONMENT_BLOCKED")
    val initialPlan = verifiedPlan(payload)
    if (initialPlan == null) {
      return SmsSubmissionResult.Refused("SMS_SEGMENT_PLAN_CHANGED")
    }
    val nowMillis = System.currentTimeMillis()
    val expiration = callbackExpiration(nowMillis, attempt.retentionUntilMillis)
      ?: return SmsSubmissionResult.Refused("SMS_CALLBACK_WINDOW_INVALID")
    val identities = try {
      createCallbackIdentities(permit, payload, nowMillis, expiration)
    } catch (_: RuntimeException) {
      return SmsSubmissionResult.Refused("SMS_CALLBACK_IDENTITY_FAILED")
    }
    try {
      ledger.registerCallbackTokens(permit, identities.map(CallbackIdentity::token))
    } catch (_: RuntimeException) {
      return SmsSubmissionResult.Refused("SMS_CALLBACK_REGISTRATION_FAILED")
    }
    val pendingIntentCreation = createPendingIntents(identities)
    val pendingIntents = when (pendingIntentCreation) {
      is PendingIntentCreation.Success -> pendingIntentCreation.allocation
      is PendingIntentCreation.Failed -> {
        val tokensRetired = retireUnsubmittedTokens(permit, identities, nowMillis)
        return SmsSubmissionResult.Refused(
          if (pendingIntentCreation.rollbackComplete && tokensRetired) {
            "SMS_CALLBACK_COLLISION"
          } else {
            "SMS_CALLBACK_ROLLBACK_FAILED"
          },
        )
      }
    }

    if (
      SmsPlatformSubmissionPlan.create(
        exactText = payload.exactText,
        orderedParts = initialPlan.orderedParts,
        sentIntents = pendingIntents.sent,
        deliveryIntents = pendingIntents.delivery,
      ) == null
    ) return rollbackAndRefuse(
      permit,
      identities,
      pendingIntents,
      nowMillis,
      "SMS_PLATFORM_PLAN_INVALID",
    )

    val bootCount = currentBootCount()
      ?: return rollbackAndRefuse(
        permit,
        identities,
        pendingIntents,
        nowMillis,
        "SMS_BOOT_ANCHOR_UNAVAILABLE",
      )
    val beforeBoundaryElapsed = SystemClock.elapsedRealtime()
    val boundaryWallMillis = System.currentTimeMillis()
    if (
      environment.subscriptionId != payload.subscriptionId ||
      !foregroundTestAuthorized(permit, payload, boundaryWallMillis) ||
      beforeBoundaryElapsed >= permit.deadlineElapsedRealtimeMillis
    ) return rollbackAndRefuse(
      permit,
      identities,
      pendingIntents,
      nowMillis,
      "SMS_DEADLINE_OR_SIM_CHANGED",
    )
    val boundaryCommitted = ledger.commitApiBoundary(
      permit = permit,
      expectedAttemptRevision = attempt.revision,
      currentElapsedRealtimeMillis = beforeBoundaryElapsed,
      currentBootCount = bootCount,
      apiBoundaryWallMillis = boundaryWallMillis,
      payloadHash = payload.payloadHash,
      subscriptionId = payload.subscriptionId,
    )
    if (!boundaryCommitted) return rollbackAndRefuse(
      permit,
      identities,
      pendingIntents,
      nowMillis,
      "SMS_API_BARRIER_REJECTED",
    )

    // External settings cannot participate in the Room/file lock. Re-read them after the durable
    // barrier and immediately before the platform call. Any change sacrifices this Armed attempt.
    val finalEnvironment = verifyEnvironment(payload)
    val finalPlan = verifiedPlan(payload)
    val finalElapsed = SystemClock.elapsedRealtime()
    if (
      finalEnvironment == null ||
      finalPlan == null ||
      !foregroundTestAuthorized(permit, payload, System.currentTimeMillis()) ||
      currentBootCount() != bootCount ||
      finalElapsed >= permit.deadlineElapsedRealtimeMillis
    ) return SmsSubmissionResult.OutcomeUnknown("SMS_FINAL_GATE_CLOSED")

    val platformResult = if (permit.purpose == OperationPurpose.TEST) {
      ForegroundActivityRegistry.withCurrentActivity {
        SmsPlatformSubmissionBoundary.execute(
          finalGateOpen = {
            subscriptionChangeSignalStore.pendingGeneration() == null &&
              foregroundTestAuthorized(permit, payload, System.currentTimeMillis(), true)
          },
          submit = {
            submitToPlatform(
              manager = finalEnvironment.manager,
              destination = payload.destinationE164,
              exactText = payload.exactText,
              parts = finalPlan.orderedParts,
              sentIntents = pendingIntents.sent,
              deliveryIntents = pendingIntents.delivery,
            )
          },
        )
      } ?: SmsPlatformBoundaryResult.NotCalled
    } else {
      SmsPlatformSubmissionBoundary.execute(
        finalGateOpen = { subscriptionChangeSignalStore.pendingGeneration() == null },
        submit = {
          submitToPlatform(
            manager = finalEnvironment.manager,
            destination = payload.destinationE164,
            exactText = payload.exactText,
            parts = finalPlan.orderedParts,
            sentIntents = pendingIntents.sent,
            deliveryIntents = pendingIntents.delivery,
          )
        },
      )
    }

    return when (platformResult) {
      SmsPlatformBoundaryResult.NotCalled ->
        SmsSubmissionResult.OutcomeUnknown("SMS_FINAL_GATE_CLOSED")
      SmsPlatformBoundaryResult.OutcomeUnknown ->
        SmsSubmissionResult.OutcomeUnknown("SMS_PLATFORM_CALL_UNCERTAIN")
      SmsPlatformBoundaryResult.Accepted -> {
        val persisted = try {
          ledger.markSmsManagerAccepted(
            permit = permit,
            expectedAttemptRevision = attempt.revision + 1,
            submittedAtMillis = System.currentTimeMillis(),
          )
        } catch (_: RuntimeException) {
          false
        } catch (_: LinkageError) {
          false
        }
        if (persisted) SmsSubmissionResult.Submitted
        else SmsSubmissionResult.OutcomeUnknown("SMS_ACCEPTED_STATE_UNCERTAIN")
      }
    }
  }

  private fun verifiedPlan(payload: LocalSendPayload): NativeSmsPlan? {
    if (
      !E164.matches(payload.destinationE164) ||
      payload.exactText.isBlank() ||
      payload.exactText.length > 1_000 ||
      payload.exactText != UnicodeTextSafety.normalizeNfc(payload.exactText) ||
      UnicodeTextSafety.containsUnsafeMessageCodePoint(payload.exactText) ||
      '{' in payload.exactText ||
      '}' in payload.exactText ||
      payload.expectedPartCount !in 1..2
    ) return null
    val plan = when (val result = planSource.plan(payload.exactText, payload.subscriptionId)) {
      is NativeSmsPlanResult.Planned -> result.plan
      is NativeSmsPlanResult.Rejected -> return null
    }
    val binding = ApprovedSegmentPlan.bind(
      exactText = payload.exactText,
      encoding = plan.encoding,
      orderedParts = plan.orderedParts,
      approvedSegmentCap = 2,
    ) ?: return null
    return plan.takeIf {
      it.segmentCount == payload.expectedPartCount &&
        it.encoding.name == payload.messageEncoding &&
        constantTimeEquals(binding.orderedPartsHash, payload.orderedPartsHash)
    }
  }

  @SuppressLint("MissingPermission")
  private fun verifyEnvironment(payload: LocalSendPayload): VerifiedSmsEnvironment? {
    subscriptionChangeSignalStore.observe(SubscriptionChangeFingerprint.read(appContext))
    if (subscriptionChangeSignalStore.pendingGeneration() != null) return null
    if (!BuildConfig.RESTRICTED_SMS_CAPABLE) return null
    if (
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) !=
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) !=
      PackageManager.PERMISSION_GRANTED ||
      !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING) ||
      !SubscriptionManager.isValidSubscriptionId(payload.subscriptionId)
    ) return null
    val readiness = readinessProbe.read(payload.subscriptionId)
    if (
      !(if (payload.purpose == OperationPurpose.TEST) {
        readiness.eligibility.allowsForegroundTest()
      } else {
        readiness.eligibility.kind == EligibilityKind.SUPPORTED
      }) ||
      readiness.smsPermissionGranted != true ||
      !readiness.schedulerStartupReady
    ) return null
    return try {
      val subscriptions = appContext.getSystemService(SubscriptionManager::class.java)
      if (!SubscriptionBindingPolicy.matches(
          policyKind = payload.simPolicyKind,
          approvedSubscriptionId = payload.subscriptionId,
          currentDefaultSubscriptionId = currentDefaultSmsSubscriptionIdOrNull(),
          approvedSubscriptionActive = subscriptions.isActiveSubscriptionId(payload.subscriptionId),
        )) return null
      if (!payload.roamingAllowed && subscriptions.isNetworkRoaming(payload.subscriptionId)) return null
      val telephony = appContext.getSystemService(TelephonyManager::class.java)
        .createForSubscriptionId(payload.subscriptionId)
      if (telephony.simState != TelephonyManager.SIM_STATE_READY) return null
      val manager = boundSmsManager(payload.subscriptionId)
      if (manager.subscriptionId != payload.subscriptionId) return null
      VerifiedSmsEnvironment(payload.subscriptionId, manager)
    } catch (_: SecurityException) {
      null
    } catch (_: RuntimeException) {
      null
    } catch (_: LinkageError) {
      null
    }
  }

  private suspend fun createCallbackIdentities(
    permit: ArmedAttemptPermit,
    payload: LocalSendPayload,
    createdAtMillis: Long,
    expiresAtMillis: Long,
  ): List<CallbackIdentity> = buildList(payload.expectedPartCount * 2) {
    repeat(payload.expectedPartCount) { partIndex ->
      for (kind in CallbackKind.entries) {
        val requestCode = birthdayDao.allocateCallbackId(payload.callbackGeneration)
        add(
          CallbackIdentityFactory.create(
            context = appContext,
            permit = permit,
            payload = payload,
            kind = kind,
            partIndex = partIndex,
            requestCode = requestCode,
            createdAtMillis = createdAtMillis,
            expiresAtMillis = expiresAtMillis,
          ),
        )
      }
    }
  }

  private fun createPendingIntents(
    identities: List<CallbackIdentity>,
  ): PendingIntentCreation {
    val ordered = identities.sortedWith(compareBy({ it.token.partIndex }, { it.token.kind }))
    val expectedKinds = ordered.groupBy { it.token.partIndex }.values.all { part ->
      part.map { it.token.kind }.toSet() == CallbackKind.entries.toSet()
    }
    if (
      ordered.isEmpty() ||
      ordered.map { it.token.callbackRequestCode }.distinct().size != ordered.size ||
      !expectedKinds
    ) return PendingIntentCreation.Failed(rollbackComplete = true)
    return when (
      val result = CallbackAllocationTransaction.allocate(
        items = ordered,
        collisionExists = { identity ->
          PendingIntent.getBroadcast(
            appContext,
            identity.token.callbackRequestCode,
            identity.intent,
            CallbackIdentityFactory.pendingIntentFlags(identity.token.kind, noCreate = true),
          ) != null
        },
        create = { identity ->
          PendingIntent.getBroadcast(
            appContext,
            identity.token.callbackRequestCode,
            identity.intent,
            CallbackIdentityFactory.pendingIntentFlags(identity.token.kind),
          )
        },
        cancel = PendingIntent::cancel,
      )
    ) {
      is CallbackAllocationResult.Failed -> PendingIntentCreation.Failed(result.rollbackComplete)
      is CallbackAllocationResult.Success -> {
        val sent = ArrayList<PendingIntent>()
        val delivery = ArrayList<PendingIntent>()
        ordered.zip(result.allocations).forEach { (identity, pendingIntent) ->
          when (identity.token.kind) {
            CallbackKind.SENT -> sent += pendingIntent
            CallbackKind.DELIVERY -> delivery += pendingIntent
          }
        }
        if (sent.size == delivery.size && sent.isNotEmpty()) {
          PendingIntentCreation.Success(
            PendingIntentAllocation(sent, delivery, result.allocations),
          )
        } else {
          PendingIntentCreation.Failed(
            CallbackAllocationTransaction.rollback(result.allocations, PendingIntent::cancel),
          )
        }
      }
    }
  }

  private suspend fun rollbackAndRefuse(
    permit: ArmedAttemptPermit,
    identities: List<CallbackIdentity>,
    allocation: PendingIntentAllocation,
    retiredAtMillis: Long,
    safeCode: String,
  ): SmsSubmissionResult.Refused {
    val pendingIntentsCancelled = CallbackAllocationTransaction.rollback(
      allocation.all,
      PendingIntent::cancel,
    )
    val tokensRetired = retireUnsubmittedTokens(permit, identities, retiredAtMillis)
    return SmsSubmissionResult.Refused(
      if (pendingIntentsCancelled && tokensRetired) safeCode else "SMS_CALLBACK_ROLLBACK_FAILED",
    )
  }

  private suspend fun retireUnsubmittedTokens(
    permit: ArmedAttemptPermit,
    identities: List<CallbackIdentity>,
    retiredAtMillis: Long,
  ): Boolean {
    val tokenIds = identities.map { it.token.callbackTokenId }
    if (tokenIds.isEmpty()) return false
    return try {
      ledger.retireUnsubmittedCallbackTokens(
        sendAttemptId = permit.sendAttemptId,
        tokenIds = tokenIds,
        retiredAtMillis = retiredAtMillis,
      ) == tokenIds.size
    } catch (_: RuntimeException) {
      false
    } catch (_: LinkageError) {
      false
    }
  }

  @Suppress("DEPRECATION")
  private fun boundSmsManager(subscriptionId: Int): SmsManager =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      appContext.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
    } else {
      SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
    }

  private fun submitToPlatform(
    manager: SmsManager,
    destination: String,
    exactText: String,
    parts: List<String>,
    sentIntents: ArrayList<PendingIntent>,
    deliveryIntents: ArrayList<PendingIntent>,
  ) {
    platformSubmitter.submit(
      manager,
      destination,
      exactText,
      parts,
      sentIntents,
      deliveryIntents,
    )
  }

  private fun currentBootCount(): Int? = try {
    Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT)
      .takeIf { it >= 0 }
  } catch (_: RuntimeException) {
    null
  }

  private fun foregroundTestAuthorized(
    permit: ArmedAttemptPermit,
    payload: LocalSendPayload,
    wallNowMillis: Long,
    resumedActivityPresent: Boolean = ForegroundActivityRegistry.current() != null,
  ): Boolean = permit.purpose != OperationPurpose.TEST ||
    ForegroundTestConfirmationPolicy.isValid(
      expectedNonceHash = payload.foregroundConfirmationNonceHash,
      suppliedNonceHash = permit.foregroundConfirmationNonceHash,
      foregroundConfirmedAtMillis = payload.foregroundConfirmedAtMillis,
      wallNowMillis = wallNowMillis,
      resumedActivityPresent = resumedActivityPresent,
    )

  private fun callbackExpiration(nowMillis: Long, retentionUntilMillis: Long): Long? = try {
    Math.addExact(nowMillis, TimeUnit.DAYS.toMillis(30))
      .coerceAtMost(retentionUntilMillis)
      .takeIf { it > nowMillis }
  } catch (_: ArithmeticException) {
    null
  }

  private fun constantTimeEquals(left: String, right: String): Boolean =
    MessageDigest.isEqual(
      left.toByteArray(StandardCharsets.US_ASCII),
      right.toByteArray(StandardCharsets.US_ASCII),
    )

  private data class VerifiedSmsEnvironment(
    val subscriptionId: Int,
    val manager: SmsManager,
  )

  private data class PendingIntentAllocation(
    val sent: ArrayList<PendingIntent>,
    val delivery: ArrayList<PendingIntent>,
    val all: List<PendingIntent>,
  )

  private sealed interface PendingIntentCreation {
    data class Success(val allocation: PendingIntentAllocation) : PendingIntentCreation
    data class Failed(val rollbackComplete: Boolean) : PendingIntentCreation
  }

  private companion object {
    val E164 = Regex("^\\+[1-9][0-9]{7,14}$")
  }
}
