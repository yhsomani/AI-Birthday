package com.yashsomani.birthdayautopilot.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * A recovery credential may remain live only long enough to replay the bound deletion request.
 * Every failed/cancelled attempt clears and verifies SDK session state without touching the
 * durable deletion journal.
 */
internal class DeletionRecoverySessionOutcomeGuard(
  private val clearSession: suspend () -> Boolean,
) {
  suspend fun run(attempt: suspend () -> IdentityOutcome): IdentityOutcome {
    val outcome = try {
      attempt()
    } catch (cancelled: CancellationException) {
      withContext(NonCancellable) { clearSessionSafely() }
      throw cancelled
    } catch (_: RuntimeException) {
      IdentityOutcome.Failed(IdentityFailure.INTERNAL_FAILURE)
    }
    if (outcome is IdentityOutcome.SignedIn) return outcome
    return if (clearSessionSafely()) {
      outcome
    } else {
      IdentityOutcome.Failed(IdentityFailure.INTERNAL_FAILURE)
    }
  }

  private suspend fun clearSessionSafely(): Boolean = try {
    clearSession()
  } catch (_: RuntimeException) {
    false
  }
}
