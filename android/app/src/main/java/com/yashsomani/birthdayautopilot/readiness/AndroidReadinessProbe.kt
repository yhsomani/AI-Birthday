package com.yashsomani.birthdayautopilot.readiness

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.yashsomani.birthdayautopilot.BuildConfig
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import java.security.MessageDigest
import java.time.Clock
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AndroidReadinessSnapshot(
  val signals: DistributionSignals,
  val eligibility: EligibilityDecision,
  val smsPermissionGranted: Boolean?,
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
    smsPermissionGranted = smsPermissionGranted,
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

  fun read(): AndroidReadinessSnapshot {
    val telephonyMessagingAvailable = safely {
      appContext.packageManager.hasSystemFeature(TELEPHONY_MESSAGING_FEATURE)
    }
    val signals = DistributionSignals(
      apiCertified = certifiedApi(),
      telephonyMessagingAvailable = telephonyMessagingAvailable,
      restrictedProfile = restrictedProfile(),
      restrictedSmsBuildApproved = BuildConfig.RESTRICTED_SMS_CAPABLE,
      evidenceCurrent = evidenceCurrent(),
      signingCertificateMatches = signingCertificateMatches(),
      installerMatches = installerMatches(),
      playServicesAvailable = playServicesAvailable(),
      simReady = simReady(telephonyMessagingAvailable),
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
      smsPermissionGranted = safely {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) ==
          PackageManager.PERMISSION_GRANTED
      },
    )
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

  private fun simReady(telephonyMessagingAvailable: Boolean?): Boolean? = safely {
    if (telephonyMessagingAvailable != true) return@safely false
    val telephony = appContext.getSystemService(TelephonyManager::class.java)
    telephony.simState == TelephonyManager.SIM_STATE_READY
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
