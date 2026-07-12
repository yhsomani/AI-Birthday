package com.yashsomani.birthdayautopilot.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal enum class TelephonyPermissionResult {
  GRANTED,
  PHONE_STATE_DENIED,
  PHONE_STATE_PERMANENTLY_DENIED,
  SMS_DENIED,
  SMS_PERMANENTLY_DENIED,
  UNAVAILABLE,
}

internal fun interface TelephonyPermissionLauncher {
  suspend fun request(): TelephonyPermissionResult
}

/**
 * Just-in-time permission owner used only after the user confirms the real foreground Test SMS.
 * It is resume-scoped, single-flight, and contains no phone number, message, token, or other PII.
 */
internal class TelephonyPermissionActivityResultOwner(
  private val activity: ComponentActivity,
) : TelephonyPermissionLauncher {
  private val gate = SingleResolutionRequestGate()
  private var pendingContinuation:
    kotlinx.coroutines.CancellableContinuation<TelephonyPermissionResult>? = null
  private var pendingStage: PermissionStage? = null
  private var requestWasPreviouslyAttempted = false
  // Activity-result launchers must be registered during Activity construction,
  // but Android has not attached the ContextWrapper base at that point. Read
  // app storage only when a resumed foreground request actually starts.
  private val requestHistory by lazy(LazyThreadSafetyMode.NONE) {
    activity.getSharedPreferences(
      REQUEST_HISTORY_PREFERENCES,
      Context.MODE_PRIVATE,
    )
  }
  private val denialState by lazy(LazyThreadSafetyMode.NONE) {
    TelephonyPermissionDenialStore(activity.applicationContext)
  }
  private val phoneStateLauncher = activity.registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted -> handleResult(PermissionStage.PHONE_STATE, granted) }
  private val smsLauncher = activity.registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted -> handleResult(PermissionStage.SMS, granted) }

  override suspend fun request(): TelephonyPermissionResult = withContext(Dispatchers.Main.immediate) {
    val phoneStateGranted = isGranted(Manifest.permission.READ_PHONE_STATE)
    val smsGranted = isGranted(Manifest.permission.SEND_SMS)
    val permanentDenial = denialState.reconcile(phoneStateGranted, smsGranted)
    if (phoneStateGranted && smsGranted) {
      return@withContext TelephonyPermissionResult.GRANTED
    }
    if (!phoneStateGranted && permanentDenial.blocksPhoneState()) {
      return@withContext TelephonyPermissionResult.PHONE_STATE_PERMANENTLY_DENIED
    }
    if (!smsGranted && permanentDenial.blocksSms()) {
      return@withContext TelephonyPermissionResult.SMS_PERMANENTLY_DENIED
    }
    if (
      activity.isFinishing ||
      activity.isDestroyed ||
      !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
      !gate.begin()
    ) return@withContext TelephonyPermissionResult.UNAVAILABLE
    suspendCancellableCoroutine { continuation ->
      pendingContinuation = continuation
      continuation.invokeOnCancellation {
        activity.runOnUiThread {
          if (pendingContinuation === continuation) {
            pendingContinuation = null
            pendingStage = null
            gate.finish()
          }
        }
      }
      try {
        launchNextPermission()
      } catch (_: RuntimeException) {
        finish(TelephonyPermissionResult.UNAVAILABLE)
      }
    }
  }

  fun onDestroy() {
    val continuation = pendingContinuation
    pendingContinuation = null
    pendingStage = null
    gate.destroy()
    if (continuation?.isActive == true) continuation.resume(TelephonyPermissionResult.UNAVAILABLE)
  }

  private fun launchNextPermission() {
    when {
      !isGranted(Manifest.permission.READ_PHONE_STATE) -> launch(PermissionStage.PHONE_STATE)
      !isGranted(Manifest.permission.SEND_SMS) -> launch(PermissionStage.SMS)
      else -> finish(TelephonyPermissionResult.GRANTED)
    }
  }

  private fun launch(stage: PermissionStage) {
    val permission = stage.permission
    requestWasPreviouslyAttempted = requestHistory.getBoolean(permission, false)
    // The prior-attempt bit is part of the permanent-denial classifier. Commit it before the
    // platform dialog so a process death between the result and the next launch cannot erase that
    // distinction.
    requestHistory.edit().putBoolean(permission, true).commit()
    pendingStage = stage
    when (stage) {
      PermissionStage.PHONE_STATE -> phoneStateLauncher.launch(permission)
      PermissionStage.SMS -> smsLauncher.launch(permission)
    }
  }

  private fun handleResult(stage: PermissionStage, granted: Boolean) {
    if (pendingContinuation == null || pendingStage != stage) return
    pendingStage = null
    if (granted) {
      denialState.reconcile(
        isGranted(Manifest.permission.READ_PHONE_STATE),
        isGranted(Manifest.permission.SEND_SMS),
      )
      try {
        launchNextPermission()
      } catch (_: RuntimeException) {
        finish(TelephonyPermissionResult.UNAVAILABLE)
      }
      return
    }
    val permanentlyDenied = requestWasPreviouslyAttempted &&
      !ActivityCompat.shouldShowRequestPermissionRationale(activity, stage.permission)
    val result = when (stage) {
      PermissionStage.PHONE_STATE -> if (permanentlyDenied) {
        TelephonyPermissionResult.PHONE_STATE_PERMANENTLY_DENIED
      } else {
        TelephonyPermissionResult.PHONE_STATE_DENIED
      }
      PermissionStage.SMS -> if (permanentlyDenied) {
        TelephonyPermissionResult.SMS_PERMANENTLY_DENIED
      } else {
        TelephonyPermissionResult.SMS_DENIED
      }
    }
    if (result == TelephonyPermissionResult.PHONE_STATE_PERMANENTLY_DENIED) {
      denialState.markPhoneStatePermanent()
    } else if (result == TelephonyPermissionResult.SMS_PERMANENTLY_DENIED) {
      denialState.markSmsPermanent()
    }
    finish(result)
  }

  private fun finish(result: TelephonyPermissionResult) {
    val continuation = pendingContinuation ?: return
    pendingContinuation = null
    pendingStage = null
    if (!gate.finish() || !continuation.isActive) return
    continuation.resume(result)
  }

  private fun isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

  private enum class PermissionStage(val permission: String) {
    PHONE_STATE(Manifest.permission.READ_PHONE_STATE),
    SMS(Manifest.permission.SEND_SMS),
  }

  private companion object {
    const val REQUEST_HISTORY_PREFERENCES = "telephony-permission-request-history-v1"
  }
}
