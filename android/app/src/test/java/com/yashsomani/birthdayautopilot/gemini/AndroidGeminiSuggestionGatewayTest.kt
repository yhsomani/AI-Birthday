package com.yashsomani.birthdayautopilot.gemini

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidGeminiSuggestionGatewayTest {
  @Test
  fun `request parsing is closed and prompt cannot contain arbitrary user data`() {
    val valid = personalizedRequest()
    val parsed = GeminiSuggestionPolicy.parseRequest(valid)!!
    val prompt = GeminiSuggestionPolicy.prompt(parsed)
    assertEquals("en", parsed.language)
    assertTrue(prompt.contains("policyVersion=birthday-greeting-prompt-v2"))
    assertTrue(GeminiSuggestionPolicy.SYSTEM_INSTRUCTION.startsWith("Create clearly positive"))
    assertFalse(prompt.contains("Alice"))
    assertFalse(prompt.contains("+91"))

    valid.put("contactName", "Alice")
    assertEquals(null, GeminiSuggestionPolicy.parseRequest(valid))
    assertEquals(
      null,
      GeminiSuggestionPolicy.parseRequest(
        personalizedRequest().put(
          "placeholderMode",
          JSONObject().put("kind", "given-name").put("requiredCount", 0),
        ),
      ),
    )
  }

  @Test
  fun `structured candidates normalize deduplicate and use existing validator`() {
    val request = GeminiSuggestionPolicy.parseRequest(personalizedRequest())!!
    val raw = """
      {"candidates":[
        {"text":" Happy birthday, {firstName}! ","language":"en"},
        {"text":"happy birthday, {firstName}!","language":"en"},
        {"text":"Limited offer for {firstName}","language":"en"}
      ]}
    """.trimIndent()
    assertEquals(
      listOf("Happy birthday, {firstName}!"),
      GeminiSuggestionPolicy.validatedCandidates(raw, request),
    )
  }

  @Test
  fun `policy rejects wrong language prose placeholders URLs harm and over-budget output`() {
    val request = GeminiSuggestionPolicy.parseRequest(personalizedRequest())!!
    val cases = listOf(
      "{\"candidates\":[{\"text\":\"जन्मदिन मुबारक {firstName}\",\"language\":\"hi\"}]}",
      "{\"candidates\":[{\"text\":\"Happy birthday!\",\"language\":\"en\"}]}",
      "{\"candidates\":[{\"text\":\"Visit https://example.com {firstName}\",\"language\":\"en\"}]}",
      "{\"candidates\":[{\"text\":\"I hate you {firstName}\",\"language\":\"en\"}]}",
      "{\"candidates\":[{\"text\":\"${"a".repeat(400)} {firstName}\",\"language\":\"en\"}]}",
      "{\"candidates\":[],\"explanation\":\"hello\"}",
    )
    cases.forEach { assertTrue(GeminiSuggestionPolicy.validatedCandidates(it, request).isEmpty()) }
  }

  @Test
  fun `Hindi and generic candidates follow exact language and placeholder modes`() {
    val request = GeminiSuggestionPolicy.parseRequest(
      JSONObject()
        .put("language", "hi")
        .put("tone", "warm")
        .put("placeholderMode", JSONObject().put("kind", "generic").put("requiredCount", 0))
        .put("requestedSegmentCap", 2),
    )!!
    assertEquals(
      listOf("जन्मदिन मुबारक हो! आपका दिन शानदार हो।"),
      GeminiSuggestionPolicy.validatedCandidates(
        "{\"candidates\":[{\"text\":\"जन्मदिन मुबारक हो! आपका दिन शानदार हो।\",\"language\":\"hi\"}]}",
        request,
      ),
    )
    assertTrue(
      GeminiSuggestionPolicy.validatedCandidates(
        "{\"candidates\":[{\"text\":\"जन्मदिन मुबारक {firstName}\",\"language\":\"hi\"}]}",
        request,
      ).isEmpty(),
    )
  }

  @Test
  fun `rate guard scopes cooldown and UTC daily limit to an exact account session`() {
    val store = MemoryRateStore()
    val guard = GeminiUxRateGuard(store, dailyLimit = 2, cooldownMillis = 5_000)
    val firstSession = accountSession("firebase-user-a", "generation-a")
    val secondSession = accountSession("firebase-user-b", "generation-b")
    assertTrue(guard.tryAcquire(firstSession, 0, 10_000))
    assertFalse(guard.tryAcquire(firstSession, 1_000, 9_999))
    assertFalse(guard.tryAcquire(firstSession, 1_000, 11_000))
    assertTrue(guard.tryAcquire(secondSession, 1_000, 11_000))
    assertTrue(guard.tryAcquire(firstSession, 6_000, 16_000))
    assertFalse(guard.tryAcquire(firstSession, 7_000, 22_000))
    assertTrue(guard.tryAcquire(firstSession, 86_400_000, 23_000))
    assertFalse(guard.tryAcquire(firstSession, 0, 24_000))
    assertEquals(2, store.value.size)
    assertTrue(store.value.all { it.scopeKey.length == 64 })
    assertFalse(store.value.toString().contains("firebase-user"))
    assertFalse(store.value.toString().contains("generation"))
  }

  @Test
  fun `account generation rotates provenance scope and rate state is bounded pruned and clearable`() {
    val first = accountSession("same-firebase-user", "generation-one")
    val second = accountSession("same-firebase-user", "generation-two")
    assertNotEquals(first, second)

    val store = MemoryRateStore()
    val guard = GeminiUxRateGuard(store, dailyLimit = 10, cooldownMillis = 0)
    repeat(MAXIMUM_GEMINI_RATE_SCOPES + 2) { index ->
      assertTrue(
        guard.tryAcquire(
          accountSession("firebase-user-$index", "generation-$index"),
          index * 86_400_000L,
          index.toLong(),
        ),
      )
    }
    assertEquals(MAXIMUM_GEMINI_RATE_SCOPES, store.value.size)
    assertTrue(store.value.none { it.epochDay < 2 })
    assertTrue(guard.clearAll())
    assertTrue(store.value.isEmpty())
  }

  @Test
  fun `gateway fails closed for auth App Check offline and malformed output`() = runTest {
    val store = MemoryRateStore()
    val client = FakeClient()
    val gateway = AndroidGeminiSuggestionGateway(
      client = client,
      rateGuard = GeminiUxRateGuard(store, cooldownMillis = 0),
      operationalGate = EnabledOperationalGate,
      wallClockMillis = { 0 },
      elapsedClockMillis = { 0 },
    )
    client.authenticated = false
    assertEquals("coordination-unavailable", gateway.generate(personalizedRequest()).getString("reason"))
    client.authenticated = true
    client.online = false
    assertEquals("network-offline", gateway.generate(personalizedRequest()).getString("reason"))
    client.online = true
    client.appCheck = false
    assertEquals("coordination-unavailable", gateway.generate(personalizedRequest()).getString("reason"))
    client.appCheck = true
    client.response = "not-json"
    assertEquals("coordination-unavailable", gateway.generate(personalizedRequest()).getString("reason"))
  }

  @Test
  fun `gateway is single-flight and never starts a second provider call`() = runTest {
    val release = CompletableDeferred<Unit>()
    val calls = AtomicInteger(0)
    val client = FakeClient().apply {
      generateBlock = {
        calls.incrementAndGet()
        release.await()
        validResponse
      }
    }
    val gateway = AndroidGeminiSuggestionGateway(
      client = client,
      rateGuard = GeminiUxRateGuard(MemoryRateStore(), cooldownMillis = 0),
      operationalGate = EnabledOperationalGate,
      provenanceRegistry = GeminiCandidateProvenanceRegistry(elapsedClockMillis = { 0 }),
      wallClockMillis = { 0 },
      elapsedClockMillis = { 0 },
    )
    val first = async { gateway.generate(personalizedRequest()) }
    testScheduler.runCurrent()
    val second = async { gateway.generate(personalizedRequest()) }
    testScheduler.runCurrent()
    assertEquals("policy-suspended", second.await().getString("reason"))
    release.complete(Unit)
    assertEquals("candidates", first.await().getString("kind"))
    assertEquals(1, calls.get())
  }

  @Test
  fun `provenance is account-bound digest-only exact single-use and short-lived`() = runTest {
    var elapsed = 1_000L
    val registry = GeminiCandidateProvenanceRegistry(
      elapsedClockMillis = { elapsed },
      ttlMillis = 60_000,
    )
    val client = FakeClient()
    val gateway = AndroidGeminiSuggestionGateway(
      client = client,
      rateGuard = GeminiUxRateGuard(MemoryRateStore(), cooldownMillis = 0),
      operationalGate = EnabledOperationalGate,
      provenanceRegistry = registry,
      wallClockMillis = { 0 },
      elapsedClockMillis = { elapsed },
    )
    assertEquals("candidates", gateway.generate(personalizedRequest()).getString("kind"))
    val draft = GeminiProvenanceDraft(
      language = "en",
      tone = "warm",
      placeholderMode = "given-name",
      requestedSegmentCap = 2,
      text = "Happy birthday, {firstName}!",
    )
    val provenance = gateway.peekProvenance(draft)!!
    assertEquals("GEMINI", provenance.source)
    assertEquals(GeminiSuggestionPolicy.MODEL_IDENTIFIER, provenance.modelIdentifier)
    assertEquals(64, provenance.candidateDigest.length)
    assertFalse(provenance.toString().contains(draft.text))
    assertEquals(null, gateway.peekProvenance(draft.copy(text = "${draft.text} ")))
    assertEquals(provenance, gateway.consumeProvenance(draft))
    assertEquals(null, gateway.consumeProvenance(draft))

    assertEquals("candidates", gateway.generate(personalizedRequest()).getString("kind"))
    client.sessionKey = accountSession("different-firebase-user", "different-generation")
    assertEquals(null, gateway.peekProvenance(draft))
    client.sessionKey = accountSession("test-firebase-user", "test-account-generation")
    assertEquals("candidates", gateway.generate(personalizedRequest()).getString("kind"))
    elapsed += 60_001
    assertEquals(null, gateway.peekProvenance(draft))
  }

  @Test
  fun `operational switch blocks before account App Check and provider work`() = runTest {
    val client = FakeClient()
    val gate = FakeOperationalGate(enabled = false)
    val gateway = AndroidGeminiSuggestionGateway(
      client = client,
      rateGuard = GeminiUxRateGuard(MemoryRateStore(), cooldownMillis = 0),
      operationalGate = gate,
      wallClockMillis = { 0 },
      elapsedClockMillis = { 0 },
    )

    assertEquals("policy-suspended", gateway.generate(personalizedRequest()).getString("reason"))
    assertEquals(1, gate.refreshes)
    assertEquals(0, client.sessionReads)
    assertEquals(0, client.appCheckCalls)
    assertEquals(0, client.providerCalls)
  }

  private fun personalizedRequest() = JSONObject()
    .put("language", "en")
    .put("tone", "warm")
    .put("placeholderMode", JSONObject().put("kind", "given-name").put("requiredCount", 1))
    .put("requestedSegmentCap", 2)

  private fun accountSession(firebaseUid: String, generation: String): String =
    checkNotNull(GeminiAccountScope.accountSessionKey(firebaseUid, generation))

  private class MemoryRateStore : GeminiRateStore {
    var value: List<GeminiRateState> = emptyList()
    override fun read(): List<GeminiRateState> = value
    override fun write(value: List<GeminiRateState>): Boolean {
      this.value = value.toList()
      return true
    }
    override fun clearAll(): Boolean {
      value = emptyList()
      return true
    }
  }

  private class FakeClient : GeminiNativeClient {
    var authenticated = true
    var online = true
    var appCheck = true
    var response: String? = validResponse
    var sessionKey: String? = GeminiAccountScope.accountSessionKey(
      "test-firebase-user",
      "test-account-generation",
    )
    var generateBlock: (suspend () -> String?)? = null
    var sessionReads = 0
    var appCheckCalls = 0
    var providerCalls = 0

    override fun accountSessionKey(): String? {
      sessionReads++
      return sessionKey?.takeIf { authenticated }
    }
    override fun isOnline() = online
    override suspend fun appCheckReady(): Boolean {
      appCheckCalls++
      return appCheck
    }
    override suspend fun generate(systemInstruction: String, prompt: String): String? {
      providerCalls++
      return generateBlock?.invoke() ?: response
    }
  }

  private class FakeOperationalGate(private val enabled: Boolean) : GeminiOperationalGate {
    var refreshes = 0

    override fun configureAfterFirebaseLaunch() = Unit

    override fun refreshInBackground() {
      refreshes++
    }

    override fun foregroundSuggestionsEnabled(): Boolean = enabled
  }

  private object EnabledOperationalGate : GeminiOperationalGate {
    override fun configureAfterFirebaseLaunch() = Unit

    override fun refreshInBackground() = Unit

    override fun foregroundSuggestionsEnabled(): Boolean = true
  }

  private companion object {
    const val validResponse =
      "{\"candidates\":[{\"text\":\"Happy birthday, {firstName}!\",\"language\":\"en\"}]}"
  }
}
