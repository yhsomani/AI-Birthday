package com.yashsomani.birthdayautopilot.messages

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.yashsomani.birthdayautopilot.auth.ForegroundActivityRegistry
import com.yashsomani.birthdayautopilot.contacts.UnicodeTextSafety
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Private, freshly revalidated material for a user-controlled system-composer handoff.
 *
 * This value must stay native. It is never a send request, receipt, or delivery observation.
 */
internal data class UserControlledSmsComposerDraft(
  val canonicalRecipient: String,
  val exactApprovedBody: String,
) {
  override fun toString(): String = "UserControlledSmsComposerDraft(values=<redacted>)"
}

internal interface UserControlledSmsComposer {
  /** A best-effort preflight. [open] repeats every check at the foreground launch boundary. */
  fun canOpen(draft: UserControlledSmsComposerDraft): Boolean

  /** Distinguishes a known non-launch from a timeout after the main-thread boundary began. */
  fun open(draft: UserControlledSmsComposerDraft): UserControlledSmsComposerOpenResult
}

internal enum class UserControlledSmsComposerOpenResult {
  OPENED,
  KNOWN_FAILURE,
  UNKNOWN,
}

/**
 * Opens only Android's recipient-scoped messaging handoff. It never calls an unattended-send API
 * and cannot observe whether the user edits, sends, cancels, which SIM is used, or delivery occurs.
 */
internal class AndroidUserControlledSmsComposer(
  private val foregroundBoundary: ((Activity) -> Boolean) -> Boolean? =
    ForegroundActivityRegistry::withCurrentActivity,
) : UserControlledSmsComposer {
  override fun canOpen(draft: UserControlledSmsComposerDraft): Boolean {
    val intent = SystemSmsComposerIntentPolicy.create(draft) ?: return false
    return (onMainThread {
      foregroundBoundary { activity -> intent.hasCapableHandler(activity) } == true
    } as? MainThreadBooleanResult.Completed)?.value == true
  }

  override fun open(
    draft: UserControlledSmsComposerDraft,
  ): UserControlledSmsComposerOpenResult {
    val intent = SystemSmsComposerIntentPolicy.create(draft)
      ?: return UserControlledSmsComposerOpenResult.KNOWN_FAILURE
    return when (val result = onMainThread {
      foregroundBoundary { activity ->
        if (!intent.hasCapableHandler(activity)) return@foregroundBoundary false
        try {
          activity.startActivity(intent)
          true
        } catch (_: RuntimeException) {
          false
        }
      } == true
    }) {
      is MainThreadBooleanResult.Completed -> if (result.value) {
        UserControlledSmsComposerOpenResult.OPENED
      } else {
        UserControlledSmsComposerOpenResult.KNOWN_FAILURE
      }
      MainThreadBooleanResult.NotRun -> UserControlledSmsComposerOpenResult.KNOWN_FAILURE
      MainThreadBooleanResult.Unknown -> UserControlledSmsComposerOpenResult.UNKNOWN
    }
  }

  // The production manifest declares the exact ACTION_SENDTO/smsto query and
  // a repository contract keeps that declaration coupled to this handoff.
  // The isolated E2E manifest deliberately removes every package query and
  // never registers this product-native composer.
  @SuppressLint("QueryPermissionsNeeded")
  private fun Intent.hasCapableHandler(activity: Activity): Boolean =
    resolveActivity(activity.packageManager) != null

  private fun onMainThread(block: () -> Boolean): MainThreadBooleanResult {
    val mainLooper = Looper.getMainLooper()
    if (Looper.myLooper() === mainLooper) {
      return MainThreadBooleanResult.Completed(runCatching(block).getOrDefault(false))
    }
    val handler = Handler(mainLooper)
    val latch = CountDownLatch(1)
    val pending = AtomicBoolean(true)
    val result = AtomicReference(false)
    val runnable = Runnable {
      if (pending.compareAndSet(true, false)) {
        result.set(runCatching(block).getOrDefault(false))
      }
      latch.countDown()
    }
    if (!handler.post(runnable)) return MainThreadBooleanResult.NotRun
    val completed = try {
      latch.await(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
      false
    }
    if (completed) return MainThreadBooleanResult.Completed(result.get())
    if (pending.compareAndSet(true, false)) {
      handler.removeCallbacks(runnable)
      return MainThreadBooleanResult.NotRun
    }
    return MainThreadBooleanResult.Unknown
  }

  private sealed interface MainThreadBooleanResult {
    data class Completed(val value: Boolean) : MainThreadBooleanResult
    data object NotRun : MainThreadBooleanResult
    data object Unknown : MainThreadBooleanResult
  }

  private companion object {
    const val MAIN_THREAD_TIMEOUT_SECONDS = 5L
  }
}

/** Intent construction is kept separate so instrumentation can prove the exact platform contract. */
internal object SystemSmsComposerIntentPolicy {
  private val E164 = Regex("^\\+[1-9][0-9]{1,14}$")

  fun create(draft: UserControlledSmsComposerDraft): Intent? {
    if (!E164.matches(draft.canonicalRecipient) || !safeBody(draft.exactApprovedBody)) return null
    val uri = Uri.fromParts(SMSTO_SCHEME, draft.canonicalRecipient, null)
    return Intent(Intent.ACTION_SENDTO, uri).putExtra(SMS_BODY_EXTRA, draft.exactApprovedBody)
  }

  internal fun validDraft(draft: UserControlledSmsComposerDraft): Boolean =
    E164.matches(draft.canonicalRecipient) && safeBody(draft.exactApprovedBody)

  private fun safeBody(value: String): Boolean =
    value.length in 1..MAX_BODY_CHARS &&
      value.isNotBlank() &&
      !UnicodeTextSafety.containsUnsafeMessageCodePoint(value)

  private const val SMSTO_SCHEME = "smsto"
  private const val SMS_BODY_EXTRA = "sms_body"
  private const val MAX_BODY_CHARS = 1_000
}
