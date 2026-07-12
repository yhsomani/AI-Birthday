package com.yashsomani.birthdayautopilot.people

import com.yashsomani.birthdayautopilot.auth.EphemeralToken
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PeopleHttpContractTest {
  private lateinit var server: MockWebServer

  @Before
  fun startServer() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun stopServer() {
    server.shutdown()
  }

  @Test
  fun `production request transport parser and coordinator paginate with exact HTTP shape`() =
    runBlocking {
      server.enqueue(jsonResponse(page("people/one", nextPage = "page/token+2", totalItems = 2)))
      server.enqueue(jsonResponse(page("people/two", nextSync = "sync/final+token", totalItems = 2)))
      val tokens = RecordingTokenProvider()
      val store = RecordingStore()

      val outcome = coordinator(tokens, store).sync(PeopleSyncMode.Full, interactiveAuthorization = true)

      assertEquals(
        PeopleSyncOutcome.Completed(
          changedPeople = 2,
          pages = 2,
          mode = CompletedSyncMode.FULL,
          recoveredExpiredSyncToken = false,
        ),
        outcome,
      )
      val first = requireRequest()
      assertBaseRequest(first, "access-token-1")
      assertEquals(PEOPLE_PERSON_FIELDS, first.requestUrl?.queryParameter("personFields"))
      assertEquals(PEOPLE_CONTACT_SOURCE, first.requestUrl?.queryParameter("sources"))
      assertEquals("1000", first.requestUrl?.queryParameter("pageSize"))
      assertEquals("true", first.requestUrl?.queryParameter("requestSyncToken"))
      assertEquals("LAST_MODIFIED_ASCENDING", first.requestUrl?.queryParameter("sortOrder"))
      assertNull(first.requestUrl?.queryParameter("pageToken"))
      assertNull(first.requestUrl?.queryParameter("syncToken"))
      assertEquals(5, first.requestUrl?.querySize)

      val second = requireRequest()
      assertBaseRequest(second, "access-token-1")
      assertEquals("page/token+2", second.requestUrl?.queryParameter("pageToken"))
      assertEquals(6, second.requestUrl?.querySize)
      assertEquals(listOf(0, 1), store.transactions.single().pageIndexes)
      assertEquals(
        listOf("people/one", "people/two"),
        store.transactions.single().contacts.flatten().map(PeopleContactDelta::resourceName),
      )
      assertTrue(store.transactions.single().committed)
      assertFalse(store.transactions.single().rolledBack)
      assertEquals("sync/final+token", store.transactions.single().completion?.nextSyncToken)
      assertTrue(tokens.issued.all { !it.isPresent() })
    }

  @Test
  fun `incremental HTTP page accepts a contact tombstone and sends the opaque sync token`() =
    runBlocking {
      server.enqueue(jsonResponse(tombstonePage("people/deleted", "sync-next")))
      val tokens = RecordingTokenProvider()
      val store = RecordingStore()
      val fingerprint = PeopleRequestFactory(1_000).parameterFingerprint

      val outcome = coordinator(tokens, store).sync(
        PeopleSyncMode.Incremental("sync/old+token", fingerprint),
        interactiveAuthorization = false,
      )

      assertEquals(
        PeopleSyncOutcome.Completed(1, 1, CompletedSyncMode.INCREMENTAL, false),
        outcome,
      )
      val request = requireRequest()
      assertBaseRequest(request, "access-token-1")
      assertEquals("sync/old+token", request.requestUrl?.queryParameter("syncToken"))
      val contact = store.transactions.single().contacts.single().single()
      assertTrue(contact.deleted)
      assertTrue(contact.names.isEmpty())
      assertTrue(contact.birthdays.isEmpty())
      assertTrue(contact.phoneNumbers.isEmpty())
      assertTrue(store.transactions.single().committed)
    }

  @Test
  fun `one HTTP 401 clears and reacquires once before a successful bounded replay`() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(401))
    server.enqueue(jsonResponse(page("people/one", nextSync = "sync-next")))
    val tokens = RecordingTokenProvider()
    val store = RecordingStore()

    val outcome = coordinator(tokens, store).sync(PeopleSyncMode.Full, true)

    assertTrue(outcome is PeopleSyncOutcome.Completed)
    assertEquals(2, tokens.acquireCalls)
    assertEquals(1, tokens.clearCalls)
    assertBaseRequest(requireRequest(), "access-token-1")
    assertBaseRequest(requireRequest(), "access-token-2")
    assertEquals(2, store.transactions.size)
    assertTrue(store.transactions.first().rolledBack)
    assertTrue(store.transactions.last().committed)
    assertTrue(tokens.issued.all { !it.isPresent() })
  }

  @Test
  fun `repeated HTTP 401 stops after the single allowed token recovery`() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(401))
    server.enqueue(MockResponse().setResponseCode(401))
    server.enqueue(jsonResponse(page("people/unreachable", nextSync = "never")))
    val tokens = RecordingTokenProvider()
    val store = RecordingStore()

    val outcome = coordinator(tokens, store).sync(PeopleSyncMode.Full, true)

    assertEquals(
      PeopleSyncOutcome.AuthorizationRequired(PeopleAuthorizationReason.REPEATED_UNAUTHORIZED),
      outcome,
    )
    assertEquals(2, tokens.acquireCalls)
    assertEquals(1, tokens.clearCalls)
    assertBaseRequest(requireRequest(), "access-token-1")
    assertBaseRequest(requireRequest(), "access-token-2")
    assertEquals(0, server.requestCount - 2)
    assertEquals(2, store.transactions.size)
    assertTrue(store.transactions.all(RecordingTransaction::rolledBack))
    assertTrue(tokens.issued.all { !it.isPresent() })
  }

  @Test
  fun `HTTP 403 and 429 Retry-After remain typed terminal outcomes`() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(403))
    server.enqueue(
      MockResponse()
        .setResponseCode(429)
        .setHeader("Retry-After", "120"),
    )

    val forbiddenStore = RecordingStore()
    assertEquals(
      PeopleSyncOutcome.Forbidden,
      coordinator(RecordingTokenProvider(), forbiddenStore).sync(PeopleSyncMode.Full, false),
    )
    assertTrue(forbiddenStore.transactions.single().rolledBack)

    val limitedStore = RecordingStore()
    assertEquals(
      PeopleSyncOutcome.RateLimited(120),
      coordinator(RecordingTokenProvider(), limitedStore).sync(PeopleSyncMode.Full, false),
    )
    assertTrue(limitedStore.transactions.single().rolledBack)
    assertEquals(2, server.requestCount)
  }

  @Test
  fun `expired incremental sync token restarts as one full HTTP sync`() = runBlocking {
    server.enqueue(
      MockResponse()
        .setResponseCode(400)
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(expiredSyncTokenError()),
    )
    server.enqueue(jsonResponse(page("people/one", nextSync = "sync-recovered")))
    val tokens = RecordingTokenProvider()
    val store = RecordingStore()
    val fingerprint = PeopleRequestFactory(1_000).parameterFingerprint

    val outcome = coordinator(tokens, store).sync(
      PeopleSyncMode.Incremental("expired-sync", fingerprint),
      false,
    )

    assertEquals(
      PeopleSyncOutcome.Completed(1, 1, CompletedSyncMode.FULL, true),
      outcome,
    )
    val incremental = requireRequest()
    assertEquals("expired-sync", incremental.requestUrl?.queryParameter("syncToken"))
    assertBaseRequest(incremental, "access-token-1")
    val full = requireRequest()
    assertNull(full.requestUrl?.queryParameter("syncToken"))
    assertBaseRequest(full, "access-token-2")
    assertEquals(2, tokens.acquireCalls)
    assertEquals(0, tokens.clearCalls)
    assertEquals(2, store.transactions.size)
    assertTrue(store.transactions.first().rolledBack)
    assertTrue(store.transactions.last().committed)
  }

  @Test
  fun `malformed first page and malformed later page fail closed without partial commit`() =
    runBlocking {
      server.enqueue(jsonResponse("{not-json"))
      val malformedStore = RecordingStore()
      assertEquals(
        PeopleSyncOutcome.Malformed(PeopleMalformedReason.INVALID_JSON),
        coordinator(RecordingTokenProvider(), malformedStore).sync(PeopleSyncMode.Full, false),
      )
      assertTrue(malformedStore.transactions.single().rolledBack)

      server.enqueue(jsonResponse(page("people/one", nextPage = "page-2")))
      server.enqueue(jsonResponse("{still-not-json"))
      val partialStore = RecordingStore()
      assertEquals(
        PeopleSyncOutcome.Partial(PeoplePartialCause.MALFORMED_PAGE),
        coordinator(RecordingTokenProvider(), partialStore).sync(PeopleSyncMode.Full, false),
      )
      assertEquals(listOf(0), partialStore.transactions.single().pageIndexes)
      assertTrue(partialStore.transactions.single().rolledBack)
      assertFalse(partialStore.transactions.single().committed)
    }

  @Test(timeout = 10_000L)
  fun `real HTTP read timeout maps to network failure and rolls back`() = runBlocking {
    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
    val store = RecordingStore()

    val outcome = coordinator(
      RecordingTokenProvider(),
      store,
      readTimeoutMillis = 1_000,
    ).sync(PeopleSyncMode.Full, false)

    assertEquals(PeopleSyncOutcome.NetworkFailure, outcome)
    assertTrue(store.transactions.single().rolledBack)
    assertFalse(store.transactions.single().committed)
  }

  @Test(timeout = 10_000L)
  fun `cancellation during a throttled HTTP body propagates after rollback`() = runBlocking {
    server.enqueue(
      jsonResponse(page("people/one", nextSync = "sync-next"))
        .throttleBody(1, 50, TimeUnit.MILLISECONDS),
    )
    val tokens = RecordingTokenProvider()
    val store = RecordingStore()
    var outcome: PeopleSyncOutcome? = null
    val job = launch(Dispatchers.Default) {
      outcome = coordinator(
        tokens,
        store,
        readTimeoutMillis = 5_000,
      ).sync(PeopleSyncMode.Full, false)
    }
    assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
    delay(25)

    job.cancelAndJoin()

    assertTrue(job.isCancelled)
    assertNull(outcome)
    assertTrue(store.transactions.single().rolledBack)
    assertFalse(store.transactions.single().committed)
    assertTrue(tokens.issued.all { !it.isPresent() })
  }

  private fun coordinator(
    tokens: RecordingTokenProvider,
    store: RecordingStore,
    readTimeoutMillis: Int = 2_000,
  ): PeopleSyncCoordinator = PeopleSyncCoordinator(
    tokenProvider = tokens,
    transport = PeopleHttpTransport(
      networkAvailability = NetworkAvailability { true },
      maxPageBytes = 1024 * 1024,
      connectTimeoutMillis = 1_000,
      readTimeoutMillis = readTimeoutMillis,
      connectionFactory = PeopleHttpConnectionFactory { original ->
        val localPath = buildString {
          append(original.rawPath)
          original.rawQuery?.let { query -> append('?').append(query) }
        }
        server.url(localPath).toUrl().openConnection() as HttpURLConnection
      },
    ),
    stagingStore = store,
  )

  private fun assertBaseRequest(request: RecordedRequest, token: String) {
    assertEquals("GET", request.method)
    assertEquals("/v1/people/me/connections", request.requestUrl?.encodedPath)
    assertEquals("application/json", request.getHeader("Accept"))
    assertEquals("Bearer $token", request.getHeader("Authorization"))
    assertNull(request.body.readUtf8().takeIf(String::isNotEmpty))
  }

  private fun requireRequest(): RecordedRequest =
    requireNotNull(server.takeRequest(3, TimeUnit.SECONDS))

  private fun jsonResponse(body: String): MockResponse = MockResponse()
    .setResponseCode(200)
    .setHeader("Content-Type", "application/json; charset=utf-8")
    .setBody(body)

  private fun page(
    resourceName: String,
    nextPage: String? = null,
    nextSync: String? = null,
    totalItems: Int = 1,
  ): String {
    val terminal = buildList {
      nextPage?.let { add("\"nextPageToken\": \"$it\"") }
      nextSync?.let { add("\"nextSyncToken\": \"$it\"") }
      add("\"totalItems\": $totalItems")
    }.joinToString(",")
    return """
      {
        "connections": [{
          "resourceName": "$resourceName",
          "metadata": {
            "sources": [{"type": "CONTACT", "id": "contacts/${resourceName.substringAfter('/')}"}]
          }
        }],
        $terminal
      }
    """.trimIndent()
  }

  private fun tombstonePage(resourceName: String, nextSync: String): String = """
    {
      "connections": [{
        "resourceName": "$resourceName",
        "metadata": {
          "deleted": true,
          "sources": [{"type": "CONTACT", "id": "contacts/deleted"}]
        }
      }],
      "nextSyncToken": "$nextSync",
      "totalItems": 1
    }
  """.trimIndent()

  private fun expiredSyncTokenError(): String = """
    {
      "error": {
        "code": 400,
        "status": "FAILED_PRECONDITION",
        "details": [{
          "@type": "type.googleapis.com/google.rpc.ErrorInfo",
          "reason": "EXPIRED_SYNC_TOKEN"
        }]
      }
    }
  """.trimIndent()

  private class RecordingTokenProvider : PeopleAccessTokenProvider {
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

  private class RecordingStore : PeopleSyncStagingStore {
    val transactions = mutableListOf<RecordingTransaction>()

    override suspend fun begin(mode: PeopleSyncMode): PeopleStagingTransaction =
      RecordingTransaction().also(transactions::add)
  }

  private class RecordingTransaction : PeopleStagingTransaction {
    val pageIndexes = mutableListOf<Int>()
    val contacts = mutableListOf<List<PeopleContactDelta>>()
    var completion: PeopleSyncCompletion? = null
    var committed = false
    var rolledBack = false

    override suspend fun stagePage(
      pageIndex: Int,
      contacts: List<PeopleContactDelta>,
    ): Boolean {
      pageIndexes += pageIndex
      this.contacts += contacts
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
