package com.yashsomani.birthdayautopilot.gemini

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.yashsomani.birthdayautopilot.BuildConfig
import com.yashsomani.birthdayautopilot.auth.AndroidIdentityConfigurationResolver
import com.yashsomani.birthdayautopilot.auth.FirebaseAppCheckGate
import com.yashsomani.birthdayautopilot.auth.IdentityConfigurationResult
import com.yashsomani.birthdayautopilot.contacts.UnicodeTextSafety
import com.yashsomani.birthdayautopilot.messages.MessageLanguage
import com.yashsomani.birthdayautopilot.messages.MessageTemplate
import com.yashsomani.birthdayautopilot.messages.MessageTemplateValidator
import com.yashsomani.birthdayautopilot.messages.TemplatePlaceholderMode
import com.yashsomani.birthdayautopilot.messages.TemplateSource
import com.yashsomani.birthdayautopilot.people.AndroidNetworkAvailability
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native-only Gemini authoring boundary.
 *
 * Requests are reduced to closed enums before a prompt is built. Provider text is never logged or
 * persisted and is returned only after deterministic local validation. This gateway is authoring
 * convenience only; it is never reachable from an Android send worker.
 */
internal class AndroidGeminiSuggestionGateway internal constructor(
  private val client: GeminiNativeClient,
  private val rateGuard: GeminiUxRateGuard,
  private val operationalGate: GeminiOperationalGate,
  private val provenanceRegistry: GeminiCandidateProvenanceRegistry =
    GeminiCandidateProvenanceRegistry(),
  private val wallClockMillis: () -> Long = System::currentTimeMillis,
  private val elapsedClockMillis: () -> Long = SystemClock::elapsedRealtime,
) {
  private val requestInFlight = AtomicBoolean(false)

  constructor(
    context: Context,
    exactAccountSessionMatches: () -> Boolean,
    operationalGate: GeminiOperationalGate = AndroidGeminiOperationalGate(context),
  ) : this(
    client = AndroidFirebaseGeminiClient(context, exactAccountSessionMatches),
    rateGuard = GeminiUxRateGuard(AndroidGeminiRateStore(context)),
    operationalGate = operationalGate,
  )

  suspend fun generate(requestJson: JSONObject): JSONObject {
    val request = GeminiSuggestionPolicy.parseRequest(requestJson) ?: run {
      provenanceRegistry.clear()
      return failedProjection()
    }
    operationalGate.refreshInBackground()
    if (!operationalGate.foregroundSuggestionsEnabled()) {
      provenanceRegistry.clear()
      return fallbackProjection("policy-suspended")
    }
    if (!requestInFlight.compareAndSet(false, true)) return fallbackProjection("policy-suspended")
    return try {
      val accountSessionKey = client.accountSessionKey()
      if (accountSessionKey == null) {
        provenanceRegistry.clear()
        return fallbackProjection("coordination-unavailable")
      }
      provenanceRegistry.clear()
      if (!client.isOnline()) return fallbackProjection("network-offline")
      if (!client.appCheckReady()) return fallbackProjection("coordination-unavailable")
      if (!rateGuard.tryAcquire(wallClockMillis(), elapsedClockMillis())) {
        return fallbackProjection("policy-suspended")
      }
      var networkDropped = false
      val raw = try {
        withTimeout(MODEL_TIMEOUT_MILLIS) {
          client.generate(
            systemInstruction = GeminiSuggestionPolicy.SYSTEM_INSTRUCTION,
            prompt = GeminiSuggestionPolicy.prompt(request),
          )
        }
      } catch (_: TimeoutCancellationException) {
        null
      } catch (_: Exception) {
        networkDropped = !client.isOnline()
        null
      } catch (_: LinkageError) {
        null
      }
      if (networkDropped) return fallbackProjection("network-offline")
      val candidates = raw?.let { GeminiSuggestionPolicy.validatedCandidates(it, request) }
        .orEmpty()
      if (candidates.isEmpty()) return fallbackProjection("coordination-unavailable")
      if (client.accountSessionKey() != accountSessionKey) {
        provenanceRegistry.clear()
        return fallbackProjection("coordination-unavailable")
      }
      provenanceRegistry.replace(accountSessionKey, request, candidates)
      JSONObject()
        .put("kind", "candidates")
        .put("candidates", JSONArray(candidates))
    } finally {
      requestInFlight.set(false)
    }
  }

  fun peekProvenance(draft: GeminiProvenanceDraft): GeminiCandidateProvenance? =
    provenance(false, draft)

  fun consumeProvenance(draft: GeminiProvenanceDraft): GeminiCandidateProvenance? =
    provenance(true, draft)

  fun clearProvenance() {
    provenanceRegistry.clear()
  }

  private fun provenance(
    consume: Boolean,
    draft: GeminiProvenanceDraft,
  ): GeminiCandidateProvenance? {
    val accountSessionKey = client.accountSessionKey()
    if (accountSessionKey == null) {
      provenanceRegistry.clear()
      return null
    }
    return if (consume) {
      provenanceRegistry.consume(accountSessionKey, draft)
    } else {
      provenanceRegistry.peek(accountSessionKey, draft)
    }
  }

  private fun fallbackProjection(reason: String) = JSONObject()
    .put("kind", "fallback")
    .put("reason", reason)

  private fun failedProjection() = JSONObject()
    .put("kind", "failed")
    .put("reason", "internal-contract-invalid")

  private companion object {
    const val MODEL_TIMEOUT_MILLIS = 15_000L
  }
}

internal interface GeminiNativeClient {
  /** A one-way, process-local binding. No Firebase UID is returned to the gateway or JavaScript. */
  fun accountSessionKey(): String?

  fun isOnline(): Boolean

  suspend fun appCheckReady(): Boolean

  suspend fun generate(systemInstruction: String, prompt: String): String?
}

private class AndroidFirebaseGeminiClient(
  context: Context,
  private val exactAccountSessionMatches: () -> Boolean,
) : GeminiNativeClient {
  private val appContext = context.applicationContext
  private val network = AndroidNetworkAvailability(appContext)

  override fun accountSessionKey(): String? {
    if (!runCatching(exactAccountSessionMatches).getOrDefault(false)) return null
    val app = firebaseApp() ?: return null
    val user = runCatching { FirebaseAuth.getInstance(app).currentUser }.getOrNull() ?: return null
    if (user.isAnonymous || user.uid.isBlank()) return null
    val googleIdentities = user.providerData.filter {
      it.providerId == GoogleAuthProvider.PROVIDER_ID
    }
    if (googleIdentities.size != 1 || googleIdentities.single().uid.isBlank()) return null
    return GeminiCandidateProvenanceRegistry.digest(
      "BirthdayAutopilot.GeminiAccountSession.v1",
      user.uid,
    )
  }

  override fun isOnline(): Boolean = network.isOnline()

  override suspend fun appCheckReady(): Boolean {
    val app = firebaseApp() ?: return false
    return FirebaseAppCheckGate().attest(app)
  }

  override suspend fun generate(systemInstruction: String, prompt: String): String? {
    val app = firebaseApp() ?: return null
    // Authenticated-users mode automatically attaches the current Firebase Auth session. The
    // explicit flag makes the model request consume a limited-use App Check token.
    val ai = FirebaseAI.getInstance(
      app,
      GenerativeBackend.vertexAI(MODEL_LOCATION),
      true,
    )
    val candidateSchema = Schema.obj(
      mapOf(
        "text" to Schema.string(
          description = "A generic birthday greeting template and no explanatory prose.",
        ),
        "language" to Schema.enumeration(
          listOf("en", "hi"),
          description = "The language tag of the greeting.",
        ),
      ),
      description = "One locally validated birthday greeting candidate.",
    )
    val outputSchema = Schema.obj(
      mapOf(
        "candidates" to Schema.array(
          items = candidateSchema,
          description = "One to three distinct generic birthday greeting templates.",
          minItems = 1,
          maxItems = 3,
        ),
      ),
      description = "Birthday greeting suggestions only.",
    )
    val model = ai.generativeModel(
      modelName = MODEL_NAME,
      generationConfig = generationConfig {
        temperature = 0.7f
        maxOutputTokens = 512
        responseMimeType = "application/json"
        responseSchema = outputSchema
      },
      systemInstruction = content(role = "system") { text(systemInstruction) },
      requestOptions = RequestOptions(timeoutInMillis = REQUEST_TIMEOUT_MILLIS),
    )
    return model.generateContent(prompt).text
  }

  private fun firebaseApp(): FirebaseApp? = when (
    val resolved = AndroidIdentityConfigurationResolver(appContext, BuildConfig.APP_ENV).resolve()
  ) {
    is IdentityConfigurationResult.Ready -> resolved.configuration.firebaseApp
    IdentityConfigurationResult.Missing -> null
  }

  private companion object {
    const val MODEL_NAME = "gemini-3.5-flash"
    const val MODEL_LOCATION = "global"
    const val REQUEST_TIMEOUT_MILLIS = 12_000L
  }
}

internal data class GeminiSuggestionRequest(
  val language: String,
  val tone: String,
  val placeholderMode: String,
  val requestedSegmentCap: Int,
)

internal object GeminiSuggestionPolicy {
  const val PROMPT_POLICY_VERSION = "birthday-greeting-prompt-v1"
  const val MODEL_IDENTIFIER = "vertex-ai/global/gemini-3.5-flash"
  const val VALIDATOR_VERSION = MessageTemplateValidator.VALIDATOR_VERSION

  const val SYSTEM_INSTRUCTION =
    "Create generic personal birthday greeting templates only. Never include or request a " +
      "person's name, phone number, birthday, age, gender, relationship, religion, health, " +
      "private history, contact data, secret, URL, hashtag, marketing, promotion, invented " +
      "memory, sensitive attribute, hateful, sexual, self-harm, violent, or deceptive content. " +
      "Follow the requested language, tone, placeholder mode, and segment cap. Return only the " +
      "structured candidates required by the response schema."

  fun parseRequest(value: JSONObject): GeminiSuggestionRequest? {
    if (value.keyNames() != setOf(
        "language",
        "tone",
        "placeholderMode",
        "requestedSegmentCap",
      )
    ) return null
    val language = value.strictString("language")?.takeIf { it in setOf("en", "hi") }
      ?: return null
    val tone = value.strictString("tone")?.takeIf { it in setOf("warm", "simple", "cheerful") }
      ?: return null
    val placeholder = value.opt("placeholderMode") as? JSONObject ?: return null
    if (placeholder.keyNames() != setOf("kind", "requiredCount")) return null
    val placeholderMode = placeholder.strictString("kind")
      ?.takeIf { it in setOf("given-name", "generic") }
      ?: return null
    val requiredCount = placeholder.strictInt("requiredCount") ?: return null
    if (
      (placeholderMode == "given-name" && requiredCount != 1) ||
      (placeholderMode == "generic" && requiredCount != 0)
    ) return null
    val segmentCap = value.strictInt("requestedSegmentCap")?.takeIf { it in 1..2 }
      ?: return null
    return GeminiSuggestionRequest(language, tone, placeholderMode, segmentCap)
  }

  fun prompt(request: GeminiSuggestionRequest): String {
    val language = if (request.language == "hi") "Hindi (hi)" else "English (en)"
    val tone = when (request.tone) {
      "simple" -> "simple"
      "cheerful" -> "cheerful"
      else -> "warm"
    }
    val placeholder = if (request.placeholderMode == "given-name") {
      "exactly one literal {firstName} placeholder"
    } else {
      "no placeholder and no person's name"
    }
    return "policyVersion=$PROMPT_POLICY_VERSION\n" +
      "language=$language\n" +
      "tone=$tone\n" +
      "placeholderMode=$placeholder\n" +
      "requestedSegmentCap=${request.requestedSegmentCap}\n" +
      "Generate 1 to 3 distinct generic birthday greeting templates."
  }

  fun validatedCandidates(raw: String, request: GeminiSuggestionRequest): List<String> {
    if (raw.toByteArray(Charsets.UTF_8).size !in 2..MAX_RESPONSE_BYTES) return emptyList()
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
    if (root.keyNames() != setOf("candidates")) return emptyList()
    val values = root.opt("candidates") as? JSONArray ?: return emptyList()
    if (values.length() !in 1..3) return emptyList()
    val output = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (index in 0 until values.length()) {
      val candidate = values.opt(index) as? JSONObject ?: return emptyList()
      if (candidate.keyNames() != setOf("text", "language")) return emptyList()
      if (candidate.strictString("language") != request.language) continue
      val rawText = candidate.strictString("text") ?: continue
      if (rawText.length > MAX_CANDIDATE_UTF16_UNITS) continue
      val text = Normalizer.normalize(rawText, Normalizer.Form.NFC).trim()
      if (!safeCandidate(text, request)) continue
      val dedupeKey = if (request.language == "en") text.lowercase(Locale.ROOT) else text
      if (seen.add(dedupeKey)) output += text
    }
    return output.take(3)
  }

  private fun safeCandidate(text: String, request: GeminiSuggestionRequest): Boolean {
    if (text.isBlank() || text.length > 1_000) return false
    if (UnicodeTextSafety.containsUnsafeMessageCodePoint(text)) return false
    val language = if (request.language == "hi") MessageLanguage.HINDI else MessageLanguage.ENGLISH
    val placeholder = if (request.placeholderMode == "given-name") {
      TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME
    } else {
      TemplatePlaceholderMode.GENERIC_NO_NAME
    }
    val template = MessageTemplate(
      version = PROMPT_POLICY_VERSION,
      language = language,
      placeholderMode = placeholder,
      source = TemplateSource.GEMINI_SELECTED,
      text = text,
    )
    val representativeName = if (language == MessageLanguage.HINDI) "मित्र" else "Friend"
    return MessageTemplateValidator().validateAndRender(
      template,
      representativeName,
      request.requestedSegmentCap,
    ).valid
  }

  private const val MAX_RESPONSE_BYTES = 16_384
  private const val MAX_CANDIDATE_UTF16_UNITS = 2_000
}

internal data class GeminiRateState(val epochDay: Long, val attempts: Int)

internal interface GeminiRateStore {
  fun read(): GeminiRateState?

  fun write(value: GeminiRateState): Boolean
}

private class AndroidGeminiRateStore(context: Context) : GeminiRateStore {
  private val preferences = context.applicationContext.getSharedPreferences(
    "birthday_gemini_ux_guard_v1",
    Context.MODE_PRIVATE,
  )

  override fun read(): GeminiRateState? {
    if (!preferences.contains(KEY_DAY) || !preferences.contains(KEY_ATTEMPTS)) return null
    val day = preferences.getLong(KEY_DAY, Long.MIN_VALUE)
    val attempts = preferences.getInt(KEY_ATTEMPTS, -1)
    return if (day >= 0 && attempts >= 0) GeminiRateState(day, attempts) else null
  }

  // The KTX edit helper returns Unit; this safety guard must observe the synchronous commit result.
  @SuppressLint("UseKtx")
  override fun write(value: GeminiRateState): Boolean = preferences.edit()
    .putLong(KEY_DAY, value.epochDay)
    .putInt(KEY_ATTEMPTS, value.attempts)
    .commit()

  private companion object {
    const val KEY_DAY = "utc_epoch_day"
    const val KEY_ATTEMPTS = "attempts"
  }
}

/** A bypassable local cost/UX guard; provider quotas remain the actual abuse boundary. */
internal class GeminiUxRateGuard(
  private val store: GeminiRateStore,
  private val dailyLimit: Int = 10,
  private val cooldownMillis: Long = 5_000,
) {
  private var lastAcceptedElapsedMillis: Long? = null

  init {
    require(dailyLimit in 1..100)
    require(cooldownMillis in 0..60_000)
  }

  @Synchronized
  fun tryAcquire(wallClockMillis: Long, elapsedClockMillis: Long): Boolean {
    if (wallClockMillis < 0 || elapsedClockMillis < 0) return false
    val previousElapsed = lastAcceptedElapsedMillis
    if (
      previousElapsed != null && elapsedClockMillis >= previousElapsed &&
      elapsedClockMillis - previousElapsed < cooldownMillis
    ) return false
    val epochDay = Math.floorDiv(wallClockMillis, MILLIS_PER_DAY)
    val current = store.read()?.takeIf { it.epochDay == epochDay }?.attempts ?: 0
    if (current >= dailyLimit) return false
    if (!store.write(GeminiRateState(epochDay, current + 1))) return false
    lastAcceptedElapsedMillis = elapsedClockMillis
    return true
  }

  private companion object {
    const val MILLIS_PER_DAY = 86_400_000L
  }
}

private fun JSONObject.keyNames(): Set<String> {
  val output = linkedSetOf<String>()
  val iterator = keys()
  while (iterator.hasNext()) output += iterator.next()
  return output
}

private fun JSONObject.strictString(key: String): String? = (opt(key) as? String)
  ?.takeIf { it.isNotBlank() }

private fun JSONObject.strictInt(key: String): Int? {
  val number = opt(key) as? Number ?: return null
  val long = number.toLong()
  if (number.toDouble() != long.toDouble() || long !in Int.MIN_VALUE..Int.MAX_VALUE) return null
  return long.toInt()
}
