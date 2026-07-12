package com.yashsomani.birthdayautopilot.people

import com.yashsomani.birthdayautopilot.auth.AndroidContactsAuthorizationGateway
import com.yashsomani.birthdayautopilot.auth.ContactsAuthorizationResult
import com.yashsomani.birthdayautopilot.auth.EphemeralToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal sealed interface PeopleTokenAcquisition {
  data class Success(val token: EphemeralToken) : PeopleTokenAcquisition

  data class Failure(val reason: PeopleAuthorizationReason) : PeopleTokenAcquisition
}

internal interface PeopleAccessTokenProvider {
  suspend fun acquire(interactive: Boolean): PeopleTokenAcquisition

  suspend fun clear(token: EphemeralToken): Boolean
}

internal class AndroidPeopleAccessTokenProvider(
  private val gateway: AndroidContactsAuthorizationGateway,
) : PeopleAccessTokenProvider {
  override suspend fun acquire(interactive: Boolean): PeopleTokenAcquisition =
    when (val result = gateway.authorize(interactive)) {
      is ContactsAuthorizationResult.Authorized -> PeopleTokenAcquisition.Success(result.accessToken)
      is ContactsAuthorizationResult.Failed -> {
        PeopleTokenAcquisition.Failure(result.reason.toPeopleAuthorizationReason())
      }
    }

  override suspend fun clear(token: EphemeralToken): Boolean = gateway.clear(token)
}

internal fun interface MonotonicClock {
  fun nowMillis(): Long
}

internal class PeopleSyncCoordinator(
  private val tokenProvider: PeopleAccessTokenProvider,
  private val transport: PeopleTransport,
  private val stagingStore: PeopleSyncStagingStore,
  private val limits: PeopleSyncLimits = PeopleSyncLimits(),
  private val clock: MonotonicClock = MonotonicClock { System.nanoTime() / 1_000_000L },
) {
  private val requestFactory = PeopleRequestFactory(limits.pageSize)
  private val parser = PeopleJsonParser(limits.pageSize)

  suspend fun sync(
    requestedMode: PeopleSyncMode,
    interactiveAuthorization: Boolean,
  ): PeopleSyncOutcome {
    var mode = requestedMode
    var unauthorizedRecoveryUsed = false
    var expiredTokenRecoveryUsed = false
    while (true) {
      val acquisition = try {
        tokenProvider.acquire(interactiveAuthorization)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: RuntimeException) {
        return PeopleSyncOutcome.AuthorizationRequired(PeopleAuthorizationReason.PROVIDER_UNAVAILABLE)
      }
      if (acquisition is PeopleTokenAcquisition.Failure) {
        return when (acquisition.reason) {
          PeopleAuthorizationReason.USER_CANCELLED -> PeopleSyncOutcome.Cancelled
          PeopleAuthorizationReason.OFFLINE -> PeopleSyncOutcome.Offline
          else -> PeopleSyncOutcome.AuthorizationRequired(acquisition.reason)
        }
      }
      val token = (acquisition as PeopleTokenAcquisition.Success).token
      try {
        val attempt = try {
          runOnce(mode, token)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: RuntimeException) {
          return PeopleSyncOutcome.StorageFailure
        }
        when (attempt) {
          is AttemptResult.Unauthorized -> {
            if (unauthorizedRecoveryUsed) {
              return PeopleSyncOutcome.AuthorizationRequired(
                PeopleAuthorizationReason.REPEATED_UNAUTHORIZED,
              )
            }
            unauthorizedRecoveryUsed = true
            val cleared = try {
              tokenProvider.clear(token)
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: RuntimeException) {
              false
            }
            if (!cleared) {
              return PeopleSyncOutcome.AuthorizationRequired(PeopleAuthorizationReason.PROVIDER_UNAVAILABLE)
            }
          }
          is AttemptResult.ExpiredSyncToken -> {
            if (mode !is PeopleSyncMode.Incremental || expiredTokenRecoveryUsed) {
              return PeopleSyncOutcome.Malformed(PeopleMalformedReason.PARAMETER_MISMATCH)
            }
            expiredTokenRecoveryUsed = true
            mode = PeopleSyncMode.Full
          }
          is AttemptResult.Terminal -> {
            return attempt.outcome.withExpiredRecovery(expiredTokenRecoveryUsed)
          }
        }
      } finally {
        token.clear()
      }
    }
  }

  private suspend fun runOnce(
    mode: PeopleSyncMode,
    accessToken: EphemeralToken,
  ): AttemptResult {
    val transaction = try {
      stagingStore.begin(mode)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: RuntimeException) {
      null
    } ?: return AttemptResult.Terminal(PeopleSyncOutcome.StorageFailure)
    val startedAt = clock.nowMillis()
    var pages = 0
    var people = 0
    var totalBytes = 0L
    var pageToken: String? = null
    var expectedTotalItems: Int? = null
    val seenPageTokens = mutableSetOf<String>()
    val seenResourceNames = mutableSetOf<String>()

    suspend fun rollbackSafely() {
      withContext(NonCancellable) {
        runCatching { transaction.rollback() }
      }
    }

    suspend fun fail(result: AttemptResult): AttemptResult {
      rollbackSafely()
      return result
    }

    while (true) {
      if (durationExceeded(startedAt)) {
        return fail(bound(PeopleBound.DURATION, pages))
      }
      if (pages >= limits.maxPages) return fail(bound(PeopleBound.PAGE_COUNT, pages))
      val request = when (val built = requestFactory.build(mode, pageToken)) {
        is PeopleRequestBuildResult.Success -> built.request
        is PeopleRequestBuildResult.Failure -> {
          return fail(malformed(built.reason, pages))
        }
      }
      val response = try {
        transport.execute(request, accessToken)
      } catch (cancelled: CancellationException) {
        rollbackSafely()
        throw cancelled
      } catch (_: RuntimeException) {
        return fail(partialOr(PeopleSyncOutcome.NetworkFailure, PeoplePartialCause.NETWORK, pages))
      }
      if (response !is PeopleTransportResult.Success) {
        return fail(mapTransportFailure(response, pages))
      }
      val body = response.body
      val parsedResult = try {
        if (body.size > limits.maxPageBytes) {
          return fail(bound(PeopleBound.PAGE_BYTES, pages))
        }
        totalBytes += body.size
        if (totalBytes > limits.maxTotalBytes) {
          return fail(bound(PeopleBound.TOTAL_BYTES, pages))
        }
        parser.parse(body)
      } finally {
        body.fill(0)
      }
      val page = when (val parsed = parsedResult) {
        is PeoplePageParseResult.Success -> parsed.page
        is PeoplePageParseResult.Failure -> {
          return fail(malformed(parsed.reason, pages))
        }
      }
      if (mode is PeopleSyncMode.Full && page.contacts.any(PeopleContactDelta::deleted)) {
        return fail(malformed(PeopleMalformedReason.INVALID_PAGE, pages))
      }
      val reportedTotal = page.totalItems
      if (reportedTotal != null) {
        if (reportedTotal > limits.maxPeople) return fail(bound(PeopleBound.PERSON_COUNT, pages))
        if (expectedTotalItems != null && expectedTotalItems != reportedTotal) {
          return fail(malformed(PeopleMalformedReason.INVALID_PAGE, pages))
        }
        expectedTotalItems = reportedTotal
      }
      people += page.contacts.size
      if (people > limits.maxPeople) return fail(bound(PeopleBound.PERSON_COUNT, pages))
      if (!seenResourceNames.addAllDistinct(page.contacts.map(PeopleContactDelta::resourceName))) {
        return fail(malformed(PeopleMalformedReason.DUPLICATE_PERSON, pages))
      }
      val nextPageToken = page.nextPageToken
      if (nextPageToken == null && expectedTotalItems != null && expectedTotalItems != people) {
        return fail(malformed(PeopleMalformedReason.INVALID_PAGE, pages))
      }
      val staged = try {
        transaction.stagePage(pages, page.contacts)
      } catch (cancelled: CancellationException) {
        rollbackSafely()
        throw cancelled
      } catch (_: RuntimeException) {
        false
      }
      if (!staged) {
        return fail(partialOr(PeopleSyncOutcome.StorageFailure, PeoplePartialCause.STORAGE, pages))
      }
      pages += 1
      if (durationExceeded(startedAt)) {
        return fail(bound(PeopleBound.DURATION, pages))
      }
      if (nextPageToken != null) {
        if (!seenPageTokens.add(nextPageToken)) {
          return fail(malformed(PeopleMalformedReason.PAGINATION_CYCLE, pages))
        }
        pageToken = nextPageToken
        continue
      }
      val nextSyncToken = page.nextSyncToken
        ?: return fail(malformed(PeopleMalformedReason.SYNC_TOKEN_MISSING, pages))
      val completion = PeopleSyncCompletion(
        nextSyncToken = nextSyncToken,
        parameterFingerprint = requestFactory.parameterFingerprint,
        changedPeople = people,
        pages = pages,
      )
      val committed = try {
        transaction.commit(completion)
      } catch (cancelled: CancellationException) {
        rollbackSafely()
        throw cancelled
      } catch (_: RuntimeException) {
        false
      }
      if (!committed) {
        return fail(partialOr(PeopleSyncOutcome.StorageFailure, PeoplePartialCause.STORAGE, pages))
      }
      return AttemptResult.Terminal(
        PeopleSyncOutcome.Completed(
          changedPeople = people,
          pages = pages,
          mode = if (mode is PeopleSyncMode.Full) CompletedSyncMode.FULL else CompletedSyncMode.INCREMENTAL,
          recoveredExpiredSyncToken = false,
        ),
      )
    }
  }

  private fun mapTransportFailure(
    result: PeopleTransportResult,
    pages: Int,
  ): AttemptResult = when (result) {
    is PeopleTransportResult.Success -> error("success must be handled before failure mapping")
    PeopleTransportResult.Unauthorized -> AttemptResult.Unauthorized(pages)
    PeopleTransportResult.Forbidden -> partialOr(PeopleSyncOutcome.Forbidden, PeoplePartialCause.FORBIDDEN, pages)
    is PeopleTransportResult.RateLimited -> partialOr(
      PeopleSyncOutcome.RateLimited(result.retryAfterSeconds),
      PeoplePartialCause.RATE_LIMITED,
      pages,
    )
    PeopleTransportResult.ExpiredSyncToken -> AttemptResult.ExpiredSyncToken(pages)
    PeopleTransportResult.Offline -> partialOr(PeopleSyncOutcome.Offline, PeoplePartialCause.OFFLINE, pages)
    PeopleTransportResult.Timeout,
    PeopleTransportResult.NetworkFailure,
    -> partialOr(PeopleSyncOutcome.NetworkFailure, PeoplePartialCause.NETWORK, pages)
    PeopleTransportResult.PageTooLarge -> bound(PeopleBound.PAGE_BYTES, pages)
    PeopleTransportResult.UnexpectedContentType -> malformed(PeopleMalformedReason.INVALID_PAGE, pages)
    is PeopleTransportResult.HttpFailure -> partialOr(
      PeopleSyncOutcome.ServerFailure(result.statusCode),
      PeoplePartialCause.SERVER,
      pages,
    )
  }

  private fun malformed(reason: PeopleMalformedReason, pages: Int): AttemptResult =
    if (pages > 0) {
      AttemptResult.Terminal(
        PeopleSyncOutcome.Partial(
          if (reason == PeopleMalformedReason.PARTIAL_SOURCE_MERGE) {
            PeoplePartialCause.SOURCE_MERGE
          } else {
            PeoplePartialCause.MALFORMED_PAGE
          },
        ),
      )
    } else {
      AttemptResult.Terminal(PeopleSyncOutcome.Malformed(reason))
    }

  private fun bound(bound: PeopleBound, pages: Int): AttemptResult =
    if (pages > 0) {
      AttemptResult.Terminal(PeopleSyncOutcome.Partial(PeoplePartialCause.BOUND_EXCEEDED))
    } else {
      AttemptResult.Terminal(PeopleSyncOutcome.BoundExceeded(bound))
    }

  private fun partialOr(
    initial: PeopleSyncOutcome,
    partialCause: PeoplePartialCause,
    pages: Int,
  ): AttemptResult = AttemptResult.Terminal(
    if (pages > 0) PeopleSyncOutcome.Partial(partialCause) else initial,
  )

  private fun PeopleSyncOutcome.withExpiredRecovery(recovered: Boolean): PeopleSyncOutcome =
    if (this is PeopleSyncOutcome.Completed && recovered) copy(recoveredExpiredSyncToken = true) else this

  private fun durationExceeded(startedAt: Long): Boolean {
    val now = clock.nowMillis()
    return now < startedAt || now - startedAt > limits.maxDurationMillis
  }

  private sealed interface AttemptResult {
    data class Unauthorized(val stagedPages: Int) : AttemptResult

    data class ExpiredSyncToken(val stagedPages: Int) : AttemptResult

    data class Terminal(val outcome: PeopleSyncOutcome) : AttemptResult
  }

  private fun MutableSet<String>.addAllDistinct(values: List<String>): Boolean {
    if (values.toSet().size != values.size) return false
    if (values.any(::contains)) return false
    addAll(values)
    return true
  }
}
