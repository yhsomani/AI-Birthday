package com.yashsomani.birthdayautopilot.gemini

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidGeminiSuggestionGatewayTest {
  @Test
  fun `request parsing is closed and prompt cannot contain arbitrary user data`() {
    val valid = personalizedRequest()
    val parsed = GeminiSuggestionPolicy.parseRequest(valid)!!
    val prompt = GeminiSuggestionPolicy.prompt(parsed)
    assertEquals("en", parsed.language)
    assertTrue(prompt.contains("policyVersion=birthday-greeting-prompt-v1"))
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
  fun `rate guard enforces cooldown and UTC daily limit without identity storage`() {
    val store = MemoryRateStore()
    val guard = GeminiUxRateGuard(store, dailyLimit = 2, cooldownMillis = 5_000)
    assertTrue(guard.tryAcquire(0, 10_000))
    assertFalse(guard.tryAcquire(1_000, 11_000))
    assertTrue(guard.tryAcquire(6_000, 16_000))
    assertFalse(guard.tryAcquire(7_000, 22_000))
    assertTrue(guard.tryAcquire(86_400_000, 23_000))
    assertEquals(GeminiRateState(1, 1), store.value)
  }

  @Test
  fun `gateway fails closed for auth App Check offline and malformed output`() = runTest {
    val store = MemoryRateStore()
    val client = FakeClient()
    val gateway = AndroidGeminiSuggestionGateway(
      client = client,
      rateGuard = GeminiUxRateGuard(store, cooldownMillis = 0),
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
    client.sessionKey = "different-account-key"
    assertEquals(null, gateway.peekProvenance(draft))
    client.sessionKey = "test-account-key"
    assertEquals("candidates", gateway.generate(personalizedRequest()).getString("kind"))
    elapsed += 60_001
    assertEquals(null, gateway.peekProvenance(draft))
  }

  private fun personalizedRequest() = JSONObject()
    .put("language", "en")
    .put("tone", "warm")
    .put("placeholderMode", JSONObject().put("kind", "given-name").put("requiredCount", 1))
    .put("requestedSegmentCap", 2)

  private class MemoryRateStore : GeminiRateStore {
    var value: GeminiRateState? = null
    override fun read(): GeminiRateState? = value
    override fun write(value: GeminiRateState): Boolean {
      this.value = value
      return true
    }
  }

  private class FakeClient : GeminiNativeClient {
    var authenticated = true
    var online = true
    var appCheck = true
    var response: String? = validResponse
    var sessionKey: String? = "test-account-key"
    var generateBlock: (suspend () -> String?)? = null

    override fun accountSessionKey() = sessionKey?.takeIf { authenticated }
    override fun isOnline() = online
    override suspend fun appCheckReady() = appCheck
    override suspend fun generate(systemInstruction: String, prompt: String): String? =
      generateBlock?.invoke() ?: response
  }

  private companion object {
    const val validResponse =
      "{\"candidates\":[{\"text\":\"Happy birthday, {firstName}!\",\"language\":\"en\"}]}"
  }
}
