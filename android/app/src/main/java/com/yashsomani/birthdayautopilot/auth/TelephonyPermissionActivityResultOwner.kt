package com.yashsomani.birthdayautopilot.auth

import android.Manifest
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
  DENIED,
  PERMANENTLY_DENIED,
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
  private val permissions = arrayOf(
    Manifest.permission.SEND_SMS,
    Manifest.permission.READ_PHONE_STATE,
  )
  private val launcher = activity.registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { grants ->
    val continuation = pendingContinuation ?: return@registerForActivityResult
    pendingContinuation = null
    if (!gate.finish() || !continuation.isActive) return@registerForActivityResult
    val granted = permissions.all { permission -> grants[permission] == true }
    val permanentlyDenied = !granted && permissions
      .filter { permission -> grants[permission] != true }
      .all { permission -> !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) }
    continuation.resume(
      when {
        granted -> TelephonyPermissionResult.GRANTED
        permanentlyDenied -> TelephonyPermissionResult.PERMANENTLY_DENIED
        else -> TelephonyPermissionResult.DENIED
      },
    )
  }

  override suspend fun request(): TelephonyPermissionResult = withContext(Dispatchers.Main.immediate) {
    if (permissions.all(::isGranted)) return@withContext TelephonyPermissionResult.GRANTED
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
            gate.finish()
          }
        }
      }
      try {
        launcher.launch(permissions)
      } catch (_: RuntimeException) {
        if (pendingContinuation === continuation) {
          pendingContinuation = null
          gate.finish()
          if (continuation.isActive) continuation.resume(TelephonyPermissionResult.UNAVAILABLE)
        }
      }
    }
  }

  fun onDestroy() {
    val continuation = pendingContinuation
    pendingContinuation = null
    gate.destroy()
    if (continuation?.isActive == true) continuation.resume(TelephonyPermissionResult.UNAVAILABLE)
  }

  private fun isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
}
