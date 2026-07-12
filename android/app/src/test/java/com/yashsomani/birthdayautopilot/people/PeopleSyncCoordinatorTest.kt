package com.yashsomani.birthdayautopilot.people

import com.yashsomani.birthdayautopilot.auth.EphemeralToken
import java.util.ArrayDeque
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleSyncCoordinatorTest {
  @Test
  fun `successful and malformed raw response buffers are wiped after parsing`() = runTest {
    val successfulBody = page("people/1", nextSync = "sync")
    val successful = coordinator(
      FakeTokenProvider(),
      FakeTransport(PeopleTransportResult.Success(successfulBody)),
      FakeStagingStore(),
    ).sync(PeopleSyncMode.Full, true)
    assertTrue(successful is PeopleSyncOutcome.Completed)
    assertTrue(successfulBody.all { it == 0.toByte() })

    val malformedBody = "{private-contact-json".toByteArray()
    val malformed = coordinator(
      FakeTokenProvider(),
      FakeTransport(PeopleTransportResult.Success(malformedBody)),
      FakeStagingStore(),
    ).sync(PeopleSyncMode.Full, true)
    assertTrue(malformed is PeopleSyncOutcome.Malformed)
    assertTrue(malformedBody.all { it == 0.toByte() })
  }

  @Test
  fun `coroutine cancellation propagates after rollback and token clearing`() = runTest {
    val tokens = FakeTokenProvider()
    val store = FakeStagingStore()
    var calls = 0
    val transport = PeopleTransport { _, _ ->
      if (calls++ == 0) {
        PeopleTransportResult.Success(page("people/1", nextPage = "page-2"))
      } else {
        awaitCancellation()
      }
    }
    val job = launch {
      coordinator(tokens, transport, store).sync(PeopleSyncMode.Full, true)
    }
    runCurrent()
    assertEquals(listOf(0), store.transactions.single().stagedPageIndexes)

    job.cancelAndJoin()

    assertTrue(job.isCancelled)
    assertTrue(store.transactions.single().rolledBack)
    assertFalse(store.transactions.single().committed)
    assertTrue(tokens.issued.all { !it.isPresent() })
  }

  @Test
  fun `all pages stage and become visible only after terminal sync token commit`() = runTest {
    val tokens = FakeTokenProvider()
    val transport = FakeTransport(
      PeopleTransportResult.Success(page("people/1", nextPage = "page-2", totalItems = 2)),
      PeopleTransportResult.Success(page("people/2", nextSync = "sync-final", totalItems = 2)),
    )
    val store = FakeStagingStore()
    val result = coordinator(tokens, transport, store).sync(PeopleSyncMode.Full, true)

    val completed = result as PeopleSyncOutcome.Completed
    assertEquals(2, completed.changedPeople)
    assertEquals(2, completed.pages)
    assertEquals(CompletedSyncMode.FULL, completed.mode)
    assertFalse(completed.recoveredExpiredSyncToken)
    assertEquals(1, store.transactions.size)
    assertEquals(listOf(0, 1), store.transactions.single().stagedPageIndexes)
    assertTrue(store.transactions.single().committed)
    assertFalse(store.transactions.single().rolledBack)
    assertEquals("sync-final", store.transactions.single().completion?.nextSyncToken)
    assertTrue(tokens.issued.all { !it.isPresent() })
  }

  @Test
  fun `malformed later page rolls back every earlier staged page`() = runTest {
    val tokens = FakeTokenProvider()
    val store = FakeStagingStore()
    val result = coordinator(
      tokens,
      FakeTransport(
        PeopleTransportResult.Success(page("people/1", nextPage = "page-2")),
        PeopleTransportResult.Success("{not-json".toByteArray()),
      ),
      store,
    ).sync(PeopleSyncMode.Full, true)

    assertEquals(PeopleSyncOutcome.Partial(PeoplePartialCause.MALFORMED_PAGE), result)
    assertTrue(store.transactions.single().rolledBack)
    assertFalse(store.transactions.single().committed)
    assertTrue(tokens.issued.all { !it.isPresent() })
  }

  @Test
  fun `one 401 clears token rolls back and reauthorizes exactly once`() = runTest {
    val tokens = FakeTokenProvider()
    val store = FakeStagingStore()
    val transport = FakeTransport(
      PeopleTransportResult.Success(page("people/first-attempt", nextPage = "page-2", totalItems = 2)),
      PeopleTransportResult.Unauthorized,
      PeopleTransportResult.Success(page("people/1", nextSync = "sync-final")),
    )
    val result = coordinator(tokens, transport, store).sync(PeopleSyncMode.Full, true)

    assertTrue(result is PeopleSyncOutcome.Completed)
    assertEquals(2, tokens.acquireCalls)
    assertEquals(1, tokens.clearCalls)
    assertEquals(3, transport.requests.size)
    assertEquals(2, store.transactions.size)
    assertTrue(store.transactions.first().rolledBack)
    assertFalse(store.transactions.first().committed)
    assertTrue(store.transactions.last().committed)
    assertTrue(tokens.issued.all { !it.isPresent() })
  }

  @Test
  fun `repeated 401 stops without a second clear or unbounded retry`() = runTest {
    val tokens = FakeTokenProvider()
    val store = FakeStagingStore()
    val transport = FakeTransport(
      PeopleTransportResult.Unauthorized,
      PeopleTransportResult.Unauthorized,
      PeopleTransportResult.Success(page("people/unreachable", nextSync = "never")),
    )
    val result = coordinator(tokens, transport, store).sync(PeopleSyncMode.Full, true)

    assertEquals(
      PeopleSyncOutcome.AuthorizationRequired(PeopleAuthorizationReason.REPEATED_UNAUTHORIZED),
      result,
    )
    assertEquals(2, tokens.acquireCalls)
    assertEquals(1, tokens.clearCalls)
    assertEquals(2, transport.requests.size)
    assertEquals(2, store.transactions.size)
    assertTrue(store.transactions.all(FakeTransaction::rolledBack))
    assertTrue(tokens.issued.all { !it.isPresent() })
  }

  @Test
  fun `expired incremental token rolls back and restarts one full sync`() = runTest {
    val tokens = FakeTokenProvider()
    val store = FakeStagingStore()
    val transport = FakeTransport(
      PeopleTransportResult.ExpiredSyncToken,
      PeopleTransportResult.Success(page("people/1", nextSync = "new-sync")),
    )
    val fingerprint = PeopleRequestFactory(1_000).parameterFingerprint
    val result = coordinator(tokens, transport, store).sync(
      PeopleSyncMode.Incremental("expired-sync", fingerprint),
      true,
    )

    val completed = result as PeopleSyncOutcome.Completed
    assertEquals(CompletedSyncMode.FULL, completed.mode)
    assertTrue(completed.recoveredExpiredSyncToken)
    assertEquals(2, tokens.acquireCalls)
    assertEquals(0, tokens.clearCalls)
    assertEquals(2, store.transactions.size)
    assertTrue(store.transactions.first().rolledBack)
    assertTrue(store.transactions.last().committed)
    assertTrue(transport.requests.first().uri.rawQuery.contains("syncToken="))
    assertFalse(transport.requests.last().uri.rawQuery.contains("syncToken="))
    assertTrue(tokens.issued.all { !it.isPresent() })
  }

  @Test
  fun `offline forbidden and rate limited failures are typed and never commit`() = runTest {
    val cases = listOf(
      PeopleTransportResult.Offline to PeopleSyncOutcome.Offline,
      PeopleTransportResult.Forbidden to PeopleSyncOutcome.Forbidden,
      PeopleTransportResult.RateLimited(120) to PeopleSyncOutcome.RateLimited(120),
    )
    cases.forEach { (transportResult, expected) ->
      val store = FakeStagingStore()
      val tokens = FakeTokenProvider()
      val result = coordinator(tokens, FakeTransport(transportResult), store)
        .sync(PeopleSyncMode.Full, interactiveAuthorization = false)
      assertEquals(expected, result)
      assertTrue(store.transactions.single().rolledBack)
      assertFalse(store.transactions.single().committed)
      assertTrue(tokens.issued.all { !it.isPresent() })
    }
  }

  @Test
  fun `authorization cancellation and offline failures stop before staging or network`() = runTest {
    val cases = listOf(
      PeopleAuthorizationReason.USER_CANCELLED to PeopleSyncOutcome.Cancelled,
      PeopleAuthorizationReason.OFFLINE to PeopleSyncOutcome.Offline,
    )
    cases.forEach { (reason, expected) ->
      val provider = object : PeopleAccessTokenProvider {
        override suspend fun acquire(interactive: Boolean): PeopleTokenAcquisition =
          PeopleTokenAcquisition.Failure(reason)

        override suspend fun clear(token: EphemeralToken): Boolean = error("must not clear")
      }
      val transport = FakeTransport()
      val store = FakeStagingStore()
      val result = PeopleSyncCoordinator(provider, transport, store).sync(PeopleSyncMode.Full, true)

      assertEquals(expected, result)
      assertTrue(transport.requests.isEmpty())
      assertTrue(store.transactions.isEmpty())
    }
  }

  @Test
  fun `failure after a complete page is explicitly partial and rolls back`() = runTest {
    val store = FakeStagingStore()
    val result = coordinator(
      FakeTokenProvider(),
      FakeTransport(
        PeopleTransportResult.Success(page("people/1", nextPage = "page-2")),
        PeopleTransportResult.Offline,
      ),
      store,
    ).sync(PeopleSyncMode.Full, true)

    assertEquals(PeopleSyncOutcome.Partial(PeoplePartialCause.OFFLINE), result)
    assertTrue(store.transactions.single().rolledBack)
    assertFalse(store.transactions.single().committed)
  }

  @Test
  fun `duration person and storage bounds fail closed`() = runTest {
    val durationStore = FakeStagingStore()
    var now = -2_000L
    val durationCoordinator = PeopleSyncCoordinator(
      tokenProvider = FakeTokenProvider(),
      transport = FakeTransport(PeopleTransportResult.Success(page("people/1", nextSync = "sync"))),
      stagingStore = durationStore,
      limits = PeopleSyncLimits(maxDurationMillis = 1_000),
      clock = MonotonicClock { now += 2_000L; now },
    )
    assertEquals(
      PeopleSyncOutcome.BoundExceeded(PeopleBound.DURATION),
      durationCoordinator.sync(PeopleSyncMode.Full, true),
    )
    assertTrue(durationStore.transactions.single().rolledBack)

    val regressingStore = FakeStagingStore()
    var clockReads = 0
    val regressingClock = MonotonicClock {
      if (clockReads++ == 0) 100L else 99L
    }
    val regressingCoordinator = PeopleSyncCoordinator(
      tokenProvider = FakeTokenProvider(),
      transport = FakeTransport(PeopleTransportResult.Success(page("people/1", nextSync = "sync"))),
      stagingStore = regressingStore,
      clock = regressingClock,
    )
    assertEquals(
      PeopleSyncOutcome.BoundExceeded(PeopleBound.DURATION),
      regressingCoordinator.sync(PeopleSyncMode.Full, true),
    )
    assertTrue(regressingStore.transactions.single().rolledBack)

    val peopleStore = FakeStagingStore()
    val peopleCoordinator = PeopleSyncCoordinator(
      tokenProvider = FakeTokenProvider(),
      transport = FakeTransport(
        PeopleTransportResult.Success(page("people/1", nextSync = "sync", totalItems = 3)),
      ),
      stagingStore = peopleStore,
      limits = PeopleSyncLimits(pageSize = 2, maxPeople = 2),
    )
    assertEquals(
      PeopleSyncOutcome.BoundExceeded(PeopleBound.PERSON_COUNT),
      peopleCoordinator.sync(PeopleSyncMode.Full, true),
    )
    assertTrue(peopleStore.transactions.single().rolledBack)

    val inconsistentCountStore = FakeStagingStore()
    val inconsistentCountResult = coordinator(
      FakeTokenProvider(),
      FakeTransport(
        PeopleTransportResult.Success(page("people/1", nextSync = "sync", totalItems = 2)),
      ),
      inconsistentCountStore,
    ).sync(PeopleSyncMode.Full, true)
    assertEquals(
      PeopleSyncOutcome.Malformed(PeopleMalformedReason.INVALID_PAGE),
      inconsistentCountResult,
    )
    assertTrue(inconsistentCountStore.transactions.single().rolledBack)

    val failingStore = FakeStagingStore(failStage = true)
    val storageResult = coordinator(
      FakeTokenProvider(),
      FakeTransport(PeopleTransportResult.Success(page("people/1", nextSync = "sync"))),
      failingStore,
    ).sync(PeopleSyncMode.Full, true)
    assertEquals(PeopleSyncOutcome.StorageFailure, storageResult)
    assertTrue(failingStore.transactions.single().rolledBack)
  }

  private fun coordinator(
    tokens: FakeTokenProvider,
    transport: PeopleTransport,
    store: FakeStagingStore,
  ) = PeopleSyncCoordinator(tokens, transport, store)

  private fun page(
    resourceName: String,
    nextPage: String? = null,
    nextSync: String? = null,
    totalItems: Int = 1,
  ): ByteArray {
    val terminal = buildList {
      nextPage?.let { add("\"nextPageToken\": \"$it\"") }
      nextSync?.let { add("\"nextSyncToken\": \"$it\"") }
      add("\"totalItems\": $totalItems")
    }.joinToString(",")
    return """
      {
        "connections": [{
          "resourceName": "$resourceName",
          "metadata": {"sources": [{"type": "CONTACT", "id": "contacts/${resourceName.substringAfter('/')}"}]}
        }],
        $terminal
      }
    """.trimIndent().toByteArray()
  }

  private class FakeTokenProvider : PeopleAccessTokenProvider {
    var acquireCalls = 0
    var clearCalls = 0
    val issued = mutableListOf<EphemeralToken>()

    override suspend fun acquire(interactive: Boolean): PeopleTokenAcquisition {
      acquireCalls += 1
      return PeopleTokenAcquisition.Success(
        EphemeralToken.from("access-token-$acquireCalls")!!.also(issued::add),
      )
    }

    override suspend fun clear(token: EphemeralToken): Boolean {
      assertTrue(token.isPresent())
      clearCalls += 1
      return true
    }
  }

  private class FakeTransport(vararg results: PeopleTransportResult) : PeopleTransport {
    private val scripted = ArrayDeque(results.toList())
    val requests = mutableListOf<PeopleRequest>()

    override suspend fun execute(
      request: PeopleRequest,
      accessToken: EphemeralToken,
    ): PeopleTransportResult {
      assertTrue(accessToken.isPresent())
      requests += request
      return scripted.removeFirst()
    }
  }

  private class FakeStagingStore(
    private val failStage: Boolean = false,
  ) : PeopleSyncStagingStore {
    val transactions = mutableListOf<FakeTransaction>()

    override suspend fun begin(mode: PeopleSyncMode): PeopleStagingTransaction =
      FakeTransaction(failStage).also(transactions::add)
  }

  private class FakeTransaction(
    private val failStage: Boolean,
  ) : PeopleStagingTransaction {
    val stagedPageIndexes = mutableListOf<Int>()
    var completion: PeopleSyncCompletion? = null
    var committed = false
    var rolledBack = false

    override suspend fun stagePage(
      pageIndex: Int,
      contacts: List<PeopleContactDelta>,
    ): Boolean {
      if (failStage) return false
      stagedPageIndexes += pageIndex
      return true
    }

    override suspend fun commit(completion: PeopleSyncCompletion): Boolean {
      this.completion = completion
      committed = true
      return true
    }

    override suspend fun rollback() {
      rolledBack = true
    }
  }
}
