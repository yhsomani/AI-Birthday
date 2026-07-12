package com.yashsomani.birthdayautopilot.people

import com.yashsomani.birthdayautopilot.auth.ContactsAuthorizationFailure
import com.yashsomani.birthdayautopilot.core.model.AccountMode

sealed interface PeopleSyncOutcome {
  data class Completed(
    val changedPeople: Int,
    val pages: Int,
    val mode: CompletedSyncMode,
    val recoveredExpiredSyncToken: Boolean,
  ) : PeopleSyncOutcome

  data class AuthorizationRequired(val reason: PeopleAuthorizationReason) : PeopleSyncOutcome

  data object Forbidden : PeopleSyncOutcome

  data class RateLimited(val retryAfterSeconds: Long?) : PeopleSyncOutcome

  data object Offline : PeopleSyncOutcome

  data class Partial(val cause: PeoplePartialCause) : PeopleSyncOutcome

  data class Malformed(val reason: PeopleMalformedReason) : PeopleSyncOutcome

  data class BoundExceeded(val bound: PeopleBound) : PeopleSyncOutcome

  data object NetworkFailure : PeopleSyncOutcome

  data class ServerFailure(val statusCode: Int?) : PeopleSyncOutcome

  data object StorageFailure : PeopleSyncOutcome

  data object Cancelled : PeopleSyncOutcome

  /** Sender ownership must be resolved before this installation reads raw Contacts. */
  data class OwnershipBlocked(val reason: PeopleSyncOwnershipBlock) : PeopleSyncOutcome
}

enum class PeopleSyncOwnershipBlock(val wireCode: String) {
  ACTIVE_SENDER_OTHER_DEVICE("active-sender-other-device"),
  TRANSFER_PENDING("transfer-pending"),
  ACCOUNT_DELETING("firebase-account-deleting"),
  OWNERSHIP_UNVERIFIED("coordination-unavailable"),
}

internal object PeopleSyncOwnershipPolicy {
  fun blockedReason(mode: AccountMode?): PeopleSyncOwnershipBlock? = when (mode) {
    AccountMode.STANDBY -> PeopleSyncOwnershipBlock.ACTIVE_SENDER_OTHER_DEVICE
    AccountMode.TRANSFER_PENDING -> PeopleSyncOwnershipBlock.TRANSFER_PENDING
    AccountMode.DELETING -> PeopleSyncOwnershipBlock.ACCOUNT_DELETING
    AccountMode.TEST_ONLY,
    AccountMode.PAUSED_REPAIR,
    AccountMode.AUTOMATION_ACTIVE,
    -> null
    null -> PeopleSyncOwnershipBlock.OWNERSHIP_UNVERIFIED
  }
}

enum class CompletedSyncMode {
  FULL,
  INCREMENTAL,
}

enum class PeopleAuthorizationReason {
  CONFIGURATION,
  CONTACTS_DISCLOSURE,
  APP_CHECK,
  FIREBASE_SESSION,
  ACTIVITY,
  PLAY_SERVICES,
  FOREGROUND_RESOLUTION_REQUIRED,
  DENIED_OR_PARTIAL_GRANT,
  ACCOUNT_MISMATCH,
  TOKEN_MISSING,
  USER_CANCELLED,
  OFFLINE,
  REPEATED_UNAUTHORIZED,
  PROVIDER_UNAVAILABLE,
}

enum class PeoplePartialCause {
  UNAUTHORIZED,
  FORBIDDEN,
  RATE_LIMITED,
  OFFLINE,
  NETWORK,
  SERVER,
  MALFORMED_PAGE,
  SOURCE_MERGE,
  STORAGE,
  BOUND_EXCEEDED,
}

enum class PeopleMalformedReason {
  INVALID_JSON,
  INVALID_PAGE,
  MALFORMED_PERSON,
  PARTIAL_SOURCE_MERGE,
  PAGINATION_CYCLE,
  DUPLICATE_PERSON,
  PARAMETER_MISMATCH,
  SYNC_TOKEN_MISSING,
}

enum class PeopleBound {
  PAGE_BYTES,
  TOTAL_BYTES,
  PAGE_COUNT,
  PERSON_COUNT,
  DURATION,
}

internal sealed interface PeopleSyncMode {
  data object Full : PeopleSyncMode

  class Incremental(
    val syncToken: String,
    val parameterFingerprint: String,
  ) : PeopleSyncMode {
    override fun toString(): String = "Incremental(<redacted>)"
  }
}

internal data class PeopleName(
  val displayName: String?,
  val givenName: String?,
) {
  override fun toString(): String = "PeopleName(<redacted>)"
}

internal data class PeopleBirthday(
  val year: Int?,
  val month: Int?,
  val day: Int?,
) {
  override fun toString(): String = "PeopleBirthday(<redacted>)"
}

internal data class PeoplePhone(
  val value: String,
  val type: String?,
) {
  override fun toString(): String = "PeoplePhone(<redacted>)"
}

internal data class PeopleContactDelta(
  val resourceName: String,
  val contactSourceId: String,
  val deleted: Boolean,
  val names: List<PeopleName>,
  val birthdays: List<PeopleBirthday>,
  val phoneNumbers: List<PeoplePhone>,
) {
  override fun toString(): String = "PeopleContactDelta(deleted=$deleted, values=<redacted>)"
}

internal class PeoplePage(
  val contacts: List<PeopleContactDelta>,
  val nextPageToken: String?,
  val nextSyncToken: String?,
  val totalItems: Int?,
  val encodedBytes: Int,
) {
  override fun toString(): String =
    "PeoplePage(count=${contacts.size}, hasNext=${nextPageToken != null}, values=<redacted>)"
}

internal sealed interface PeoplePageParseResult {
  data class Success(val page: PeoplePage) : PeoplePageParseResult

  data class Failure(val reason: PeopleMalformedReason) : PeoplePageParseResult
}

internal data class PeopleSyncLimits(
  val pageSize: Int = 1_000,
  val maxPageBytes: Int = 8 * 1024 * 1024,
  val maxTotalBytes: Long = 64L * 1024 * 1024,
  val maxPages: Int = 100,
  val maxPeople: Int = 100_000,
  val maxDurationMillis: Long = 120_000,
) {
  init {
    require(pageSize in 1..1_000)
    require(maxPageBytes in 1..16 * 1024 * 1024)
    require(maxTotalBytes >= maxPageBytes)
    require(maxPages in 1..1_000)
    require(maxPeople >= pageSize)
    require(maxDurationMillis in 1_000..10 * 60_000)
  }
}

internal data class PeopleSyncCompletion(
  val nextSyncToken: String,
  val parameterFingerprint: String,
  val changedPeople: Int,
  val pages: Int,
) {
  override fun toString(): String = "PeopleSyncCompletion(count=$changedPeople, pages=$pages, token=<redacted>)"
}

/**
 * A storage adapter must make each stagePage call atomic and expose nothing until an atomic commit
 * succeeds. A false/throwing commit must leave the prior active generation unchanged.
 */
internal interface PeopleSyncStagingStore {
  suspend fun begin(mode: PeopleSyncMode): PeopleStagingTransaction?
}

internal interface PeopleStagingTransaction {
  suspend fun stagePage(pageIndex: Int, contacts: List<PeopleContactDelta>): Boolean

  suspend fun commit(completion: PeopleSyncCompletion): Boolean

  suspend fun rollback()
}

internal fun ContactsAuthorizationFailure.toPeopleAuthorizationReason(): PeopleAuthorizationReason = when (this) {
  ContactsAuthorizationFailure.TIER_CONFIGURATION_MISSING,
  ContactsAuthorizationFailure.FIREBASE_UNAVAILABLE,
  -> PeopleAuthorizationReason.CONFIGURATION
  ContactsAuthorizationFailure.APP_CHECK_UNAVAILABLE -> PeopleAuthorizationReason.APP_CHECK
  ContactsAuthorizationFailure.FIREBASE_SESSION_MISSING -> PeopleAuthorizationReason.FIREBASE_SESSION
  ContactsAuthorizationFailure.ACTIVITY_UNAVAILABLE -> PeopleAuthorizationReason.ACTIVITY
  ContactsAuthorizationFailure.PLAY_SERVICES_UNAVAILABLE -> PeopleAuthorizationReason.PLAY_SERVICES
  ContactsAuthorizationFailure.FOREGROUND_RESOLUTION_REQUIRED ->
    PeopleAuthorizationReason.FOREGROUND_RESOLUTION_REQUIRED
  ContactsAuthorizationFailure.PERMISSION_DENIED,
  ContactsAuthorizationFailure.PARTIAL_SCOPE_GRANT,
  ContactsAuthorizationFailure.UNEXPECTED_AUTHORIZATION_CODE,
  -> PeopleAuthorizationReason.DENIED_OR_PARTIAL_GRANT
  ContactsAuthorizationFailure.ACCESS_TOKEN_MISSING -> PeopleAuthorizationReason.TOKEN_MISSING
  ContactsAuthorizationFailure.USER_CANCELLED -> PeopleAuthorizationReason.USER_CANCELLED
  ContactsAuthorizationFailure.NETWORK_UNAVAILABLE -> PeopleAuthorizationReason.OFFLINE
  ContactsAuthorizationFailure.ACCOUNT_MISMATCH -> PeopleAuthorizationReason.ACCOUNT_MISMATCH
  ContactsAuthorizationFailure.PROVIDER_UNAVAILABLE -> PeopleAuthorizationReason.PROVIDER_UNAVAILABLE
}
