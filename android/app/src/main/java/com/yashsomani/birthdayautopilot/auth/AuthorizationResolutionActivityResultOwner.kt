package com.yashsomani.birthdayautopilot.auth

import android.app.Activity
import android.app.PendingIntent
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** A small synchronized state machine that makes a resolution prompt strictly single-flight. */
internal class SingleResolutionRequestGate {
  private var state = State.IDLE

  @Synchronized fun begin(): Boolean {
    if (state != State.IDLE) return false
    state = State.ACTIVE
    return true
  }

  @Synchronized fun finish(): Boolean {
    if (state != State.ACTIVE) return false
    state = State.IDLE
    return true
  }

  @Synchronized fun destroy(): Boolean {
    val wasActive = state == State.ACTIVE
    state = State.DESTROYED
    return wasActive
  }

  private enum class State { IDLE, ACTIVE, DESTROYED }
}

/**
 * Registered during Activity construction, before STARTED, as required by Activity Result APIs.
 * All continuation access occurs on the main thread; cancellation removes the continuation and a
 * late platform callback is ignored. No credential or token crosses this owner.
 */
internal class AuthorizationResolutionActivityResultOwner(
  private val activity: ComponentActivity,
) : AuthorizationResolutionLauncher {
  private val gate = SingleResolutionRequestGate()
  private var pendingContinuation:
    kotlinx.coroutines.CancellableContinuation<ResolutionLaunchResult>? = null
  private val launcher = activity.registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    val continuation = pendingContinuation ?: return@registerForActivityResult
    pendingContinuation = null
    if (!gate.finish() || !continuation.isActive) return@registerForActivityResult
    val outcome = when {
      result.resultCode == Activity.RESULT_OK && result.data != null ->
        ResolutionLaunchResult.Completed(result.data!!)
      result.resultCode == Activity.RESULT_CANCELED -> ResolutionLaunchResult.Cancelled
      else -> ResolutionLaunchResult.Failed
    }
    continuation.resume(outcome)
  }

  override suspend fun launch(pendingIntent: PendingIntent): ResolutionLaunchResult =
    withContext(Dispatchers.Main.immediate) {
      if (
        activity.isFinishing ||
        activity.isDestroyed ||
        !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
        !gate.begin()
      ) return@withContext ResolutionLaunchResult.Failed
      suspendCancellableCoroutine<ResolutionLaunchResult> { continuation ->
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
          launcher.launch(IntentSenderRequest.Builder(pendingIntent).build())
        } catch (_: RuntimeException) {
          if (pendingContinuation === continuation) {
            pendingContinuation = null
            gate.finish()
            if (continuation.isActive) continuation.resume(ResolutionLaunchResult.Failed)
          }
        }
      }
    }

  fun onDestroy() {
    val continuation = pendingContinuation
    pendingContinuation = null
    gate.destroy()
    if (continuation?.isActive == true) continuation.resume(ResolutionLaunchResult.Failed)
  }
}
