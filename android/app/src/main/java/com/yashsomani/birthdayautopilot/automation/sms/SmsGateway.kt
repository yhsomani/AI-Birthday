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
    if (verifiedPlan(payload) == null) {
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
    val pendingIntents = try {
      createPendingIntents(identities)
    } catch (_: RuntimeException) {
      null
    } catch (_: LinkageError) {
      null
    } ?: return SmsSubmissionResult.Refused("SMS_CALLBACK_COLLISION")

    val bootCount = currentBootCount()
      ?: return SmsSubmissionResult.Refused("SMS_BOOT_ANCHOR_UNAVAILABLE")
    val beforeBoundaryElapsed = SystemClock.elapsedRealtime()
    val boundaryWallMillis = System.currentTimeMillis()
    if (
      environment.subscriptionId != payload.subscriptionId ||
      !foregroundTestAuthorized(permit, payload, boundaryWallMillis) ||
      beforeBoundaryElapsed >= permit.deadlineElapsedRealtimeMillis
    ) return SmsSubmissionResult.Refused("SMS_DEADLINE_OR_SIM_CHANGED")
    val boundaryCommitted = ledger.commitApiBoundary(
      permit = permit,
      expectedAttemptRevision = attempt.revision,
      currentElapsedRealtimeMillis = beforeBoundaryElapsed,
      currentBootCount = bootCount,
      apiBoundaryWallMillis = boundaryWallMillis,
      payloadHash = payload.payloadHash,
      subscriptionId = payload.subscriptionId,
    )
    if (!boundaryCommitted) return SmsSubmissionResult.Refused("SMS_API_BARRIER_REJECTED")

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

    return try {
      val platformBoundaryRan = if (permit.purpose == OperationPurpose.TEST) {
        ForegroundActivityRegistry.withCurrentActivity {
          if (!foregroundTestAuthorized(permit, payload, System.currentTimeMillis(), true)) {
            false
          } else {
            submitToPlatform(
              manager = finalEnvironment.manager,
              destination = payload.destinationE164,
              parts = finalPlan.orderedParts,
              sentIntents = pendingIntents.first,
              deliveryIntents = pendingIntents.second,
            )
            true
          }
        } == true
      } else {
        submitToPlatform(
          manager = finalEnvironment.manager,
          destination = payload.destinationE164,
          parts = finalPlan.orderedParts,
          sentIntents = pendingIntents.first,
          deliveryIntents = pendingIntents.second,
        )
        true
      }
      if (!platformBoundaryRan) {
        return SmsSubmissionResult.OutcomeUnknown("SMS_TEST_LEFT_FOREGROUND")
      }
      val persisted = ledger.markSmsManagerAccepted(
        permit = permit,
        expectedAttemptRevision = attempt.revision + 1,
        submittedAtMillis = System.currentTimeMillis(),
      )
      if (persisted) SmsSubmissionResult.Submitted
      else SmsSubmissionResult.OutcomeUnknown("SMS_ACCEPTED_STATE_UNCERTAIN")
    } catch (_: RuntimeException) {
      SmsSubmissionResult.OutcomeUnknown("SMS_PLATFORM_CALL_UNCERTAIN")
    } catch (_: LinkageError) {
      SmsSubmissionResult.OutcomeUnknown("SMS_PLATFORM_CALL_UNCERTAIN")
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
    if (!BuildConfig.RESTRICTED_SMS_CAPABLE) return null
    if (
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) !=
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) !=
      PackageManager.PERMISSION_GRANTED ||
      !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING) ||
      !SubscriptionManager.isValidSubscriptionId(payload.subscriptionId)
    ) return null
    val readiness = readinessProbe.read()
    if (
      readiness.eligibility.kind != EligibilityKind.SUPPORTED ||
      readiness.smsPermissionGranted != true
    ) return null
    return try {
      val subscriptions = appContext.getSystemService(SubscriptionManager::class.java)
      if (!subscriptions.isActiveSubscriptionId(payload.subscriptionId)) return null
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
  ): Pair<ArrayList<PendingIntent>, ArrayList<PendingIntent>>? {
    val sent = ArrayList<PendingIntent>()
    val delivery = ArrayList<PendingIntent>()
    for (identity in identities.sortedWith(compareBy({ it.token.partIndex }, { it.token.kind }))) {
      val existing = PendingIntent.getBroadcast(
        appContext,
        identity.token.callbackRequestCode,
        identity.intent,
        CallbackIdentityFactory.pendingIntentFlags(identity.token.kind, noCreate = true),
      )
      if (existing != null) return null
      val created = PendingIntent.getBroadcast(
        appContext,
        identity.token.callbackRequestCode,
        identity.intent,
        CallbackIdentityFactory.pendingIntentFlags(identity.token.kind),
      )
      when (identity.token.kind) {
        CallbackKind.SENT -> sent.add(created)
        CallbackKind.DELIVERY -> delivery.add(created)
      }
    }
    return if (sent.size == delivery.size && sent.isNotEmpty()) sent to delivery else null
  }

  @Suppress("DEPRECATION")
  private fun boundSmsManager(subscriptionId: Int): SmsManager =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      appContext.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
    } else {
      SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
    }

  @SuppressLint("MissingPermission")
  private fun submitToPlatform(
    manager: SmsManager,
    destination: String,
    parts: List<String>,
    sentIntents: ArrayList<PendingIntent>,
    deliveryIntents: ArrayList<PendingIntent>,
  ) {
    manager.sendMultipartTextMessage(
      destination,
      null,
      ArrayList(parts),
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

  private companion object {
    val E164 = Regex("^\\+[1-9][0-9]{7,14}$")
  }
}
