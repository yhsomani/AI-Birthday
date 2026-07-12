package com.yashsomani.birthdayautopilot.readiness

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.yashsomani.birthdayautopilot.BuildConfig
import com.yashsomani.birthdayautopilot.auth.TelephonyPermissionDenialStore
import com.yashsomani.birthdayautopilot.auth.TelephonyPermanentDenial
import com.yashsomani.birthdayautopilot.automation.workers.SchedulerStartupStateStore
import com.yashsomani.birthdayautopilot.automation.workers.SchedulerStartupStatus
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import java.security.MessageDigest
import java.time.Clock
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AndroidReadinessSnapshot(
  val signals: DistributionSignals,
  val eligibility: EligibilityDecision,
  val smsPermissionGranted: Boolean?,
  val telephonyStatePermissionGranted: Boolean?,
  val permanentPermissionDenial: TelephonyPermanentDenial,
  val schedulerStartupReady: Boolean,
  val evaluatedSubscriptionId: Int?,
  val activeSubscriptionIds: Set<Int>?,
) {
  fun readinessInputs(
    accountMode: AccountMode?,
    contactsFresh: Boolean?,
    approvalsReady: Boolean?,
    passingTestReceipt: Boolean?,
    coordinationAvailable: Boolean?,
    clockTrusted: Boolean?,
    resetSafetyClear: Boolean?,
  ) = ReadinessInputs(
    distributionVerified =
      eligibility.distributionVerified && eligibility.kind != EligibilityKind.UNSUPPORTED,
    accountMode = accountMode,
    contactsFresh = contactsFresh,
    approvalsReady = approvalsReady,
    passingTestReceipt = passingTestReceipt,
    networkAvailable = signals.networkValidated,
    coordinationAvailable = coordinationAvailable,
    schedulerReady = schedulerStartupReady,
    smsPermissionGranted = smsPermissionGranted == true && telephonyStatePermissionGranted == true,
    simReady = signals.simReady,
    backgroundRestricted = signals.backgroundRestricted,
    dozeAllowlisted = signals.dozeAllowlisted,
    unusedAppRestrictionDisabled = signals.unusedAppRestrictionsDisabled,
    dataSaverAllowsBackground = signals.dataSaverAllowsBackground,
    lowPowerStandbySafe = signals.lowPowerStandbySafe,
    clockTrusted = clockTrusted,
    resetSafetyClear = resetSafetyClear,
  )
}

class AndroidReadinessProbe(
  context: Context,
  private val clock: Clock = Clock.systemUTC(),
  private val eligibilityEvaluator: DistributionEligibilityEvaluator =
    DistributionEligibilityEvaluator(),
) {
  private val appContext = context.applicationContext

  fun read(resolvedSubscriptionId: Int? = defaultSmsSubscriptionId()): AndroidReadinessSnapshot {
    val telephonyMessagingAvailable = safely {
      appContext.packageManager.hasSystemFeature(TELEPHONY_MESSAGING_FEATURE)
    }
    val smsPermissionGranted = permissionGranted(Manifest.permission.SEND_SMS)
    val telephonyStatePermissionGranted = permissionGranted(Manifest.permission.READ_PHONE_STATE)
    val permanentPermissionDenial = TelephonyPermissionDenialStore(appContext).reconcile(
      telephonyStatePermissionGranted,
      smsPermissionGranted,
    )
    val activeSubscriptionIds = activeSubscriptionIds(telephonyStatePermissionGranted)
    val evaluatedSubscriptionId = resolvedSubscriptionId
      ?.takeIf(SubscriptionManager::isValidSubscriptionId)
    val signals = DistributionSignals(
      apiCertified = certifiedApi(),
      telephonyMessagingAvailable = telephonyMessagingAvailable,
      restrictedProfile = restrictedProfile(),
      restrictedSmsBuildApproved = BuildConfig.RESTRICTED_SMS_CAPABLE,
      evidenceCurrent = evidenceCurrent(),
      signingCertificateMatches = signingCertificateMatches(),
      installerMatches = installerMatches(),
      playServicesAvailable = playServicesAvailable(),
      simReady = simReady(
        telephonyMessagingAvailable,
        evaluatedSubscriptionId,
        telephonyStatePermissionGranted,
        activeSubscriptionIds,
      ),
      networkValidated = networkValidated(),
      backgroundRestricted = safely {
        appContext.getSystemService(ActivityManager::class.java).isBackgroundRestricted
      },
      dozeAllowlisted = safely {
        appContext.getSystemService(PowerManager::class.java)
          .isIgnoringBatteryOptimizations(appContext.packageName)
      },
      unusedAppRestrictionsDisabled = unusedAppRestrictionsDisabled(),
      dataSaverAllowsBackground = safely {
        appContext.getSystemService(ConnectivityManager::class.java)
          .restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
      },
      lowPowerStandbySafe = lowPowerStandbySafe(),
    )
    return AndroidReadinessSnapshot(
      signals = signals,
      eligibility = eligibilityEvaluator.evaluate(signals),
      smsPermissionGranted = smsPermissionGranted,
      telephonyStatePermissionGranted = telephonyStatePermissionGranted,
      permanentPermissionDenial = permanentPermissionDenial,
      schedulerStartupReady = SchedulerStartupStateStore(appContext).status() ==
        SchedulerStartupStatus.READY,
      evaluatedSubscriptionId = evaluatedSubscriptionId,
      activeSubscriptionIds = activeSubscriptionIds,
    )
  }

  /**
   * Lint cannot infer the permission represented by the already-sampled nullable projection.
   * Keep the suppression on this single guarded read; [safely] also treats a concurrent revoke as
   * unavailable instead of allowing a SecurityException to escape.
   */
  @SuppressLint("MissingPermission")
  private fun activeSubscriptionIds(permissionGranted: Boolean?): Set<Int>? {
    if (permissionGranted != true) return null
    return safely {
      appContext.getSystemService(SubscriptionManager::class.java)
        .activeSubscriptionInfoList
        .orEmpty()
        .mapTo(linkedSetOf()) { it.subscriptionId }
    }
  }

  private fun permissionGranted(permission: String): Boolean? = safely {
    ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
  }

  private fun certifiedApi(): Boolean =
    BuildConfig.MINIMUM_CERTIFIED_API >= Build.VERSION_CODES.Q &&
      BuildConfig.MAXIMUM_CERTIFIED_API >= BuildConfig.MINIMUM_CERTIFIED_API &&
      Build.VERSION.SDK_INT in
      BuildConfig.MINIMUM_CERTIFIED_API..BuildConfig.MAXIMUM_CERTIFIED_API

  private fun evidenceCurrent(): Boolean =
    BuildConfig.DISTRIBUTION_EVIDENCE_EXPIRES_AT_SECONDS > 0 &&
      clock.instant().epochSecond < BuildConfig.DISTRIBUTION_EVIDENCE_EXPIRES_AT_SECONDS

  private fun installerMatches(): Boolean? = safely {
    val expected = BuildConfig.APPROVED_INSTALLER_PACKAGE
    if (expected.isBlank()) return@safely false
    val actual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      appContext.packageManager.getInstallSourceInfo(appContext.packageName)
        .installingPackageName
    } else {
      @Suppress("DEPRECATION")
      appContext.packageManager.getInstallerPackageName(appContext.packageName)
    }
    actual == expected
  }

  private fun signingCertificateMatches(): Boolean? = safely {
    val expected = BuildConfig.APPROVED_SIGNING_CERTIFICATE_SHA256
      .replace(":", "")
      .lowercase()
    if (!expected.matches(Regex("[0-9a-f]{64}"))) return@safely false

    @Suppress("DEPRECATION")
    val packageInfo = appContext.packageManager.getPackageInfo(
      appContext.packageName,
      PackageManager.GET_SIGNING_CERTIFICATES,
    )
    val signers = packageInfo.signingInfo?.apkContentsSigners.orEmpty()
    signers.size == 1 && certificateDigest(signers.single().toByteArray()) == expected
  }

  private fun certificateDigest(encoded: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
      .digest(encoded)
      .joinToString(separator = "") { byte ->
        String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
      }

  private fun playServicesAvailable(): Boolean? = safely {
    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) ==
      ConnectionResult.SUCCESS
  }

  private fun restrictedProfile(): Boolean? = safely {
    appContext.getSystemService(UserManager::class.java)
      .hasUserRestriction(UserManager.DISALLOW_SMS)
  }

  @SuppressLint("MissingPermission")
  private fun simReady(
    telephonyMessagingAvailable: Boolean?,
    subscriptionId: Int?,
    phoneStatePermissionGranted: Boolean?,
    activeSubscriptionIds: Set<Int>?,
  ): Boolean? {
    val simState = if (
      telephonyMessagingAvailable == true &&
      subscriptionId != null &&
      (phoneStatePermissionGranted != true || activeSubscriptionIds?.contains(subscriptionId) == true)
    ) {
      safely {
        appContext.getSystemService(TelephonyManager::class.java)
          .createForSubscriptionId(subscriptionId)
          .simState
      }
    } else {
      null
    }
    return SubscriptionSimReadinessPolicy.evaluate(
      telephonyMessagingAvailable = telephonyMessagingAvailable,
      subscriptionId = subscriptionId,
      phoneStatePermissionGranted = phoneStatePermissionGranted,
      activeSubscriptionIds = activeSubscriptionIds,
      selectedSubscriptionSimState = simState,
    )
  }

  private fun defaultSmsSubscriptionId(): Int? = safely {
    SubscriptionManager.getDefaultSmsSubscriptionId()
      .takeIf(SubscriptionManager::isValidSubscriptionId)
  }

  private fun networkValidated(): Boolean? = safely {
    val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    val activeNetwork = connectivity.activeNetwork ?: return@safely false
    val capabilities = connectivity.getNetworkCapabilities(activeNetwork) ?: return@safely false
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
      capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }

  private fun unusedAppRestrictionsDisabled(): Boolean? = safely {
    val status = PackageManagerCompat.getUnusedAppRestrictionsStatus(appContext)
      .get(UNUSED_APP_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    status == UnusedAppRestrictionsConstants.DISABLED ||
      status == UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE
  }

  private fun lowPowerStandbySafe(): Boolean? = safely {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@safely true
    val power = appContext.getSystemService(PowerManager::class.java)
    if (!power.isLowPowerStandbyEnabled) return@safely true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return@safely power.isExemptFromLowPowerStandby
    }
    false
  }

  private inline fun <Value> safely(block: () -> Value): Value? = try {
    block()
  } catch (_: Exception) {
    null
  } catch (_: LinkageError) {
    null
  }

  private companion object {
    // The literal platform feature name is stable on API 29+, while the SDK constant itself is
    // annotated newer and triggers an incorrect inlined-API warning for the supported min SDK.
    const val TELEPHONY_MESSAGING_FEATURE = "android.hardware.telephony.messaging"
    const val UNUSED_APP_STATUS_TIMEOUT_SECONDS = 3L
  }
}

internal object SubscriptionSimReadinessPolicy {
  fun evaluate(
    telephonyMessagingAvailable: Boolean?,
    subscriptionId: Int?,
    phoneStatePermissionGranted: Boolean?,
    activeSubscriptionIds: Set<Int>?,
    selectedSubscriptionSimState: Int?,
  ): Boolean? {
    if (telephonyMessagingAvailable != true) return false
    val selected = subscriptionId?.takeIf { it >= 0 } ?: return false
    if (phoneStatePermissionGranted == true) {
      val active = activeSubscriptionIds ?: return null
      if (selected !in active) return false
    }
    val state = selectedSubscriptionSimState ?: return null
    return state == TelephonyManager.SIM_STATE_READY
  }
}
