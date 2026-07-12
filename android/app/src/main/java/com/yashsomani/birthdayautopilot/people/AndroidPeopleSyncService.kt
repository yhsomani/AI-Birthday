package com.yashsomani.birthdayautopilot.people

import com.yashsomani.birthdayautopilot.auth.AndroidContactsAuthorizationGateway
import com.yashsomani.birthdayautopilot.storage.database.PeopleSyncDao
import kotlinx.coroutines.CancellationException

internal class AndroidPeopleSyncService(
  private val dao: PeopleSyncDao,
  private val authorizationGateway: AndroidContactsAuthorizationGateway,
  private val transport: PeopleTransport,
  private val limits: PeopleSyncLimits = PeopleSyncLimits(),
) {
  private val requestFactory = PeopleRequestFactory(limits.pageSize)

  suspend fun sync(interactiveAuthorization: Boolean): PeopleSyncOutcome {
    val account = dao.activeAccount()
      ?: return PeopleSyncOutcome.AuthorizationRequired(PeopleAuthorizationReason.FIREBASE_SESSION)
    val state = dao.contactSyncState(account.accountId)
      ?: return PeopleSyncOutcome.StorageFailure
    val mode = if (
      state.activeGeneration != null &&
      !state.syncToken.isNullOrBlank() &&
      state.parametersHash == requestFactory.parameterFingerprint
    ) {
      PeopleSyncMode.Incremental(state.syncToken, state.parametersHash)
    } else {
      PeopleSyncMode.Full
    }
    val store = RoomPeopleSyncStagingStore(
      dao = dao,
      accountId = account.accountId,
      accountLocaleTag = account.localeTag,
      parameterFingerprint = requestFactory.parameterFingerprint,
    )
    val outcome = PeopleSyncCoordinator(
      tokenProvider = AndroidPeopleAccessTokenProvider(authorizationGateway),
      transport = transport,
      stagingStore = store,
      limits = limits,
    ).sync(mode, interactiveAuthorization)
    if (outcome !is PeopleSyncOutcome.Completed && outcome !is PeopleSyncOutcome.Cancelled) {
      try {
        dao.recordSyncFailure(
          accountId = account.accountId,
          safeErrorCode = outcome.safeStorageCode(),
          authorizationRequired = outcome is PeopleSyncOutcome.AuthorizationRequired ||
            outcome is PeopleSyncOutcome.Forbidden,
        )
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: RuntimeException) {
        // The typed sync result remains authoritative; a diagnostic write must not replace it.
      }
    }
    return outcome
  }

  private fun PeopleSyncOutcome.safeStorageCode(): String = when (this) {
    is PeopleSyncOutcome.AuthorizationRequired -> "CONTACTS_AUTHORIZATION_REQUIRED"
    PeopleSyncOutcome.Forbidden -> "CONTACTS_PERMISSION_DENIED"
    is PeopleSyncOutcome.RateLimited -> "CONTACTS_RATE_LIMITED"
    PeopleSyncOutcome.Offline -> "NETWORK_OFFLINE"
    is PeopleSyncOutcome.Partial -> "CONTACTS_PARTIAL_SYNC"
    is PeopleSyncOutcome.Malformed -> "CONTACTS_PROVIDER_MALFORMED"
    is PeopleSyncOutcome.BoundExceeded -> "CONTACTS_BOUND_EXCEEDED"
    PeopleSyncOutcome.NetworkFailure -> "CONTACTS_NETWORK_FAILURE"
    is PeopleSyncOutcome.ServerFailure -> "CONTACTS_SERVER_FAILURE"
    PeopleSyncOutcome.StorageFailure -> "CONTACTS_STORAGE_FAILURE"
    PeopleSyncOutcome.Cancelled -> "CONTACTS_CANCELLED"
    is PeopleSyncOutcome.Completed -> "CONTACTS_SYNC_COMPLETE"
  }
}
