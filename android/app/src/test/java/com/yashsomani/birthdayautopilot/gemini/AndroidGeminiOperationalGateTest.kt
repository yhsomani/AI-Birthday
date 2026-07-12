package com.yashsomani.birthdayautopilot.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidGeminiOperationalGateTest {
  @Test
  fun `policy accepts only the exact remotely activated boolean`() {
    assertFalse(AndroidGeminiOperationalPolicy.IN_APP_DEFAULT)
    assertEquals("gemini_suggestions_enabled", AndroidGeminiOperationalPolicy.PARAMETER_KEY)
    assertEquals(3_600L, AndroidGeminiOperationalPolicy.MINIMUM_FETCH_INTERVAL_SECONDS)
    assertEquals(8L, AndroidGeminiOperationalPolicy.FIREBASE_FETCH_TIMEOUT_SECONDS)
    assertEquals(10_000L, AndroidGeminiOperationalPolicy.LOCAL_COMPLETION_TIMEOUT_MILLIS)
    assertTrue(
      AndroidGeminiOperationalPolicy.acceptsActivatedValue(
        sourceIsRemote = true,
        canonicalString = "true",
        boolValue = true,
      ),
    )
    listOf(
      Triple(false, "true", true),
      Triple(true, "TRUE", true),
      Triple(true, "1", true),
      Triple(true, "true", false),
      Triple(false, "", false),
    ).forEach { value ->
      assertFalse(
        AndroidGeminiOperationalPolicy.acceptsActivatedValue(
          sourceIsRemote = value.first,
          canonicalString = value.second,
          boolValue = value.third,
        ),
      )
    }
  }

  @Test
  fun `missing tier Firebase configuration remains off and can retry`() {
    var availableClient: FakeRemoteConfigClient? = null
    val scheduler = FakeTimeoutScheduler()
    val gate = AndroidGeminiOperationalGate(
      clientFactory = GeminiRemoteConfigClientFactory { availableClient },
      timeoutScheduler = scheduler,
    )

    gate.configureAfterFirebaseLaunch()
    assertFalse(gate.foregroundSuggestionsEnabled())

    availableClient = FakeRemoteConfigClient(remoteEnabledValue())
    gate.refreshInBackground()
    assertEquals(1, availableClient!!.prepareCalls)
    availableClient!!.finishPrepare(succeeded = true)
    assertTrue(gate.foregroundSuggestionsEnabled())
    assertEquals(1, availableClient!!.fetchCalls)
  }

  @Test
  fun `cached activated value gates foreground and successful refresh updates it`() {
    val client = FakeRemoteConfigClient(remoteEnabledValue())
    val gate = AndroidGeminiOperationalGate(
      clientFactory = GeminiRemoteConfigClientFactory { client },
      timeoutScheduler = FakeTimeoutScheduler(),
    )

    gate.configureAfterFirebaseLaunch()
    assertFalse(gate.foregroundSuggestionsEnabled())
    client.finishPrepare(succeeded = true)
    assertTrue(gate.foregroundSuggestionsEnabled())
    assertEquals(1, client.fetchCalls)

    client.value = remoteDisabledValue()
    client.finishFetch(succeeded = true)
    assertFalse(gate.foregroundSuggestionsEnabled())
  }

  @Test
  fun `failed refresh keeps a valid activated cache while timeout rejects late completion`() {
    val scheduler = FakeTimeoutScheduler()
    val client = FakeRemoteConfigClient(remoteEnabledValue())
    val gate = AndroidGeminiOperationalGate(
      clientFactory = GeminiRemoteConfigClientFactory { client },
      timeoutScheduler = scheduler,
    )
    gate.configureAfterFirebaseLaunch()
    client.finishPrepare(succeeded = true)
    assertTrue(gate.foregroundSuggestionsEnabled())

    client.finishFetch(succeeded = false)
    assertTrue(gate.foregroundSuggestionsEnabled())
    gate.refreshInBackground()
    assertEquals(2, client.fetchCalls)
    client.value = remoteDisabledValue()
    scheduler.runLast()
    client.finishFetch(succeeded = true)
    assertTrue(gate.foregroundSuggestionsEnabled())
  }

  @Test
  fun `default static and malformed values never enable foreground suggestions`() {
    listOf(
      GeminiRemoteConfigActivatedValue(false, "true", true),
      GeminiRemoteConfigActivatedValue(true, "TRUE", true),
      GeminiRemoteConfigActivatedValue(true, "true", false),
    ).forEach { value ->
      val client = FakeRemoteConfigClient(value)
      val gate = AndroidGeminiOperationalGate(
        clientFactory = GeminiRemoteConfigClientFactory { client },
        timeoutScheduler = FakeTimeoutScheduler(),
      )
      gate.configureAfterFirebaseLaunch()
      client.finishPrepare(succeeded = true)
      assertFalse(gate.foregroundSuggestionsEnabled())
    }
  }

  private class FakeRemoteConfigClient(
    var value: GeminiRemoteConfigActivatedValue,
  ) : GeminiRemoteConfigClient {
    var prepareCalls = 0
    var fetchCalls = 0
    private var prepareCompletion: ((Boolean) -> Unit)? = null
    private var fetchCompletion: ((Boolean) -> Unit)? = null

    override fun prepare(completion: (Boolean) -> Unit) {
      prepareCalls++
      prepareCompletion = completion
    }

    override fun activatedValue(): GeminiRemoteConfigActivatedValue = value

    override fun fetchAndActivate(completion: (Boolean) -> Unit) {
      fetchCalls++
      fetchCompletion = completion
    }

    fun finishPrepare(succeeded: Boolean) {
      val completion = checkNotNull(prepareCompletion)
      prepareCompletion = null
      completion(succeeded)
    }

    fun finishFetch(succeeded: Boolean) {
      val completion = checkNotNull(fetchCompletion)
      fetchCompletion = null
      completion(succeeded)
    }
  }

  private class FakeTimeoutScheduler : GeminiOperationalTimeoutScheduler {
    private val actions = mutableListOf<() -> Unit>()

    override fun schedule(delayMillis: Long, action: () -> Unit) {
      assertEquals(AndroidGeminiOperationalPolicy.LOCAL_COMPLETION_TIMEOUT_MILLIS, delayMillis)
      actions += action
    }

    fun runLast() {
      actions.removeAt(actions.lastIndex).invoke()
    }
  }

  private companion object {
    fun remoteEnabledValue() = GeminiRemoteConfigActivatedValue(true, "true", true)

    fun remoteDisabledValue() = GeminiRemoteConfigActivatedValue(true, "false", false)
  }
}
