package com.yashsomani.birthdayautopilot.people

internal data class PeopleBackgroundSyncDecision(
  val safeCode: String,
  val retry: Boolean,
  val reconcileAutomation: Boolean = false,
  val notifyAttention: Boolean = false,
)

/** Closed policy for non-interactive People refresh. It can never launch consent UI. */
internal object PeopleBackgroundSyncPolicy {
  private const val MAX_TRANSIENT_ATTEMPTS = 3

  fun decide(outcome: PeopleSyncOutcome, runAttemptCount: Int): PeopleBackgroundSyncDecision =
    when (outcome) {
      is PeopleSyncOutcome.Completed -> PeopleBackgroundSyncDecision(
        safeCode = "CONTACTS_SYNC_COMPLETE",
        retry = false,
        reconcileAutomation = true,
      )

      is PeopleSyncOutcome.AuthorizationRequired -> PeopleBackgroundSyncDecision(
        safeCode = "CONTACTS_AUTHORIZATION_REQUIRED",
        retry = false,
        // A fresh install with no account is idle, not an unsolicited notification.
        notifyAttention = outcome.reason != PeopleAuthorizationReason.FIREBASE_SESSION,
      )

      PeopleSyncOutcome.Forbidden -> terminalAttention("CONTACTS_PERMISSION_DENIED")
      is PeopleSyncOutcome.RateLimited -> PeopleBackgroundSyncDecision(
        safeCode = "CONTACTS_RATE_LIMITED",
        retry = false,
      )

      PeopleSyncOutcome.Offline -> transient("NETWORK_OFFLINE", runAttemptCount)
      PeopleSyncOutcome.NetworkFailure -> transient(
        "CONTACTS_NETWORK_FAILURE",
        runAttemptCount,
      )

      is PeopleSyncOutcome.ServerFailure -> transient(
        "CONTACTS_SERVER_FAILURE",
        runAttemptCount,
      )

      is PeopleSyncOutcome.Partial -> PeopleBackgroundSyncDecision(
        safeCode = "CONTACTS_PARTIAL_SYNC",
        retry = false,
      )

      is PeopleSyncOutcome.Malformed -> terminalAttention("CONTACTS_PROVIDER_MALFORMED")
      is PeopleSyncOutcome.BoundExceeded -> terminalAttention("CONTACTS_BOUND_EXCEEDED")
      PeopleSyncOutcome.StorageFailure -> terminalAttention("CONTACTS_STORAGE_FAILURE")
      PeopleSyncOutcome.Cancelled -> PeopleBackgroundSyncDecision(
        safeCode = "CONTACTS_CANCELLED",
        retry = false,
      )

      is PeopleSyncOutcome.OwnershipBlocked -> PeopleBackgroundSyncDecision(
        safeCode = "CONTACTS_SYNC_OWNERSHIP_BLOCKED",
        retry = false,
      )
    }

  private fun transient(
    safeCode: String,
    runAttemptCount: Int,
  ): PeopleBackgroundSyncDecision = PeopleBackgroundSyncDecision(
    safeCode = safeCode,
    retry = runAttemptCount + 1 < MAX_TRANSIENT_ATTEMPTS,
  )

  private fun terminalAttention(safeCode: String) = PeopleBackgroundSyncDecision(
    safeCode = safeCode,
    retry = false,
    notifyAttention = true,
  )
}
