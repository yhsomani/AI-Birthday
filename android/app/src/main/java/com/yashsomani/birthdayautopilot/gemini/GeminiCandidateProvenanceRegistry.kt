package com.yashsomani.birthdayautopilot.gemini

import android.os.SystemClock
import java.security.MessageDigest

internal data class GeminiProvenanceDraft(
  val language: String,
  val tone: String,
  val placeholderMode: String,
  val requestedSegmentCap: Int,
  val text: String,
)

internal data class GeminiCandidateProvenance(
  val source: String,
  val candidateDigest: String,
  val language: String,
  val tone: String,
  val placeholderMode: String,
  val requestedSegmentCap: Int,
  val modelIdentifier: String,
  val promptPolicyVersion: String,
  val validatorVersion: String,
)

/**
 * Native-only, purpose-separated account keys. Raw Firebase UIDs and local account generations are
 * accepted only at this boundary and are never returned to JavaScript or persisted by Gemini.
 */
internal object GeminiAccountScope {
  fun accountSessionKey(firebaseUid: String, accountGeneration: String): String? {
    if (
      firebaseUid.isBlank() || firebaseUid.length > MAXIMUM_INPUT_CHARACTERS ||
      accountGeneration.isBlank() || accountGeneration.length > MAXIMUM_INPUT_CHARACTERS
    ) return null
    return GeminiCandidateProvenanceRegistry.digest(
      ACCOUNT_SESSION_DOMAIN,
      firebaseUid + "\u0000" + accountGeneration,
    )
  }

  fun rateScopeKey(accountSessionKey: String): String? {
    if (!SHA_256_HEX.matches(accountSessionKey)) return null
    return GeminiCandidateProvenanceRegistry.digest(RATE_SCOPE_DOMAIN, accountSessionKey)
  }

  private const val ACCOUNT_SESSION_DOMAIN = "BirthdayAutopilot.GeminiAccountSession.v1"
  private const val RATE_SCOPE_DOMAIN = "BirthdayAutopilot.GeminiRateScope.v1"
  private const val MAXIMUM_INPUT_CHARACTERS = 512
  private val SHA_256_HEX = Regex("^[0-9a-f]{64}$")
}

/**
 * Bounded, process-memory-only provenance for the candidates currently visible in the authoring
 * UI. It stores a domain-separated exact-text digest, never the provider response or raw text.
 */
internal class GeminiCandidateProvenanceRegistry(
  private val elapsedClockMillis: () -> Long = SystemClock::elapsedRealtime,
  private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
  private var accountSessionKey: String? = null
  private var entries: List<Entry> = emptyList()

  init {
    require(ttlMillis in 1_000..MAXIMUM_TTL_MILLIS)
  }

  @Synchronized
  fun replace(
    accountSessionKey: String,
    request: GeminiSuggestionRequest,
    candidates: List<String>,
  ) {
    if (accountSessionKey.isBlank() || candidates.isEmpty() || candidates.size > MAXIMUM_ENTRIES) {
      clearLocked()
      return
    }
    val now = elapsedClockMillis()
    if (now < 0 || now > Long.MAX_VALUE - ttlMillis) {
      clearLocked()
      return
    }
    this.accountSessionKey = accountSessionKey
    entries = candidates.distinct().take(MAXIMUM_ENTRIES).map { candidate ->
      Entry(
        provenance = GeminiCandidateProvenance(
          source = "GEMINI",
          candidateDigest = candidateDigest(candidate),
          language = request.language,
          tone = request.tone,
          placeholderMode = request.placeholderMode,
          requestedSegmentCap = request.requestedSegmentCap,
          modelIdentifier = GeminiSuggestionPolicy.MODEL_IDENTIFIER,
          promptPolicyVersion = GeminiSuggestionPolicy.PROMPT_POLICY_VERSION,
          validatorVersion = GeminiSuggestionPolicy.VALIDATOR_VERSION,
        ),
        expiresAtElapsedMillis = now + ttlMillis,
      )
    }
  }

  @Synchronized
  fun peek(
    accountSessionKey: String,
    draft: GeminiProvenanceDraft,
  ): GeminiCandidateProvenance? = match(accountSessionKey, draft, consume = false)

  @Synchronized
  fun consume(
    accountSessionKey: String,
    draft: GeminiProvenanceDraft,
  ): GeminiCandidateProvenance? = match(accountSessionKey, draft, consume = true)

  @Synchronized
  fun clear() = clearLocked()

  private fun match(
    accountSessionKey: String,
    draft: GeminiProvenanceDraft,
    consume: Boolean,
  ): GeminiCandidateProvenance? {
    val now = elapsedClockMillis()
    if (this.accountSessionKey != accountSessionKey || now < 0) {
      clearLocked()
      return null
    }
    entries = entries.filter { it.expiresAtElapsedMillis > now }
    if (entries.isEmpty()) {
      clearLocked()
      return null
    }
    val digest = candidateDigest(draft.text)
    val index = entries.indexOfFirst { entry ->
      val value = entry.provenance
      MessageDigest.isEqual(
        value.candidateDigest.toByteArray(Charsets.US_ASCII),
        digest.toByteArray(Charsets.US_ASCII),
      ) &&
        value.language == draft.language &&
        value.tone == draft.tone &&
        value.placeholderMode == draft.placeholderMode &&
        value.requestedSegmentCap == draft.requestedSegmentCap
    }
    if (index < 0) return null
    val result = entries[index].provenance
    if (consume) {
      entries = entries.filterIndexed { entryIndex, _ -> entryIndex != index }
      if (entries.isEmpty()) clearLocked()
    }
    return result
  }

  private fun clearLocked() {
    accountSessionKey = null
    entries = emptyList()
  }

  private data class Entry(
    val provenance: GeminiCandidateProvenance,
    val expiresAtElapsedMillis: Long,
  )

  companion object {
    internal fun digest(domain: String, value: String): String {
      val bytes = MessageDigest.getInstance("SHA-256").digest(
        (domain + "\u0000" + value).toByteArray(Charsets.UTF_8),
      )
      return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun candidateDigest(value: String): String = digest(
      "BirthdayAutopilot.GeminiCandidateExactText.v1",
      value,
    )

    private const val MAXIMUM_ENTRIES = 3
    private const val DEFAULT_TTL_MILLIS = 15 * 60 * 1_000L
    private const val MAXIMUM_TTL_MILLIS = 60 * 60 * 1_000L
  }
}
