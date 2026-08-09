package com.yashsomani.birthdayautopilot.smoke

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.yashsomani.birthdayautopilot.bridge.codegen.NativeBirthdaySpec
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/**
 * Read-only native boundary for the production-path device smoke lane.
 *
 * The host launches the real `index.js`, providers, adapter, Zod decoders, and
 * navigation shell, but this bridge can only return reviewed synthetic
 * projections. Every user intent returns one identical fail-closed problem;
 * there is no product graph, storage, network, permission, SMS, or scheduler
 * dependency reachable from this source set.
 */
class SmokeBirthdayNativeModule(
  reactContext: ReactApplicationContext,
) : NativeBirthdaySpec(reactContext) {
  private val document: JSONObject? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    loadDocument()
  }

  override fun getProjection(area: String, requestJson: String, promise: Promise) {
    val key = projectionKey(area, parseRequest(requestJson))
    val fixture = document
    val payload = key?.let { fixture?.optJSONObject("platforms")?.optJSONObject("android")?.opt(it) }
    if (fixture == null || payload == null) {
      promise.resolve(errorResponse(UNAVAILABLE_PROBLEM))
      return
    }
    promise.resolve(response("ok", payload, fixture))
  }

  override fun executeUserIntent(
    intent: String,
    expectedRevision: String?,
    payloadJson: String,
    promise: Promise,
  ) {
    // Deliberately do not parse, branch on, dispatch, or persist any supplied
    // value. A malformed or unknown request has the same safe outcome as every
    // production mutation in this lane.
    @Suppress("UNUSED_VARIABLE") val ignoredArguments = Triple(intent, expectedRevision, payloadJson)
    val fixture = document
    val problem = fixture?.optJSONObject("intentProblem") ?: INTENT_PROBLEM
    promise.resolve(errorResponse(problem))
  }

  override fun addListener(eventType: String) = Unit

  override fun removeListeners(count: Double) = Unit

  private fun parseRequest(requestJson: String): JSONObject? {
    if (requestJson.length > MAX_REQUEST_CHARS) return null
    return try {
      JSONObject(requestJson)
    } catch (_: Exception) {
      null
    }
  }

  private fun projectionKey(area: String, request: JSONObject?): String? {
    if (request == null) return null
    return when (area) {
      "bootstrap", "setup", "home", "eligibility", "readiness", "account", "route",
      "notifications" -> area.takeIf { request.length() == 0 }
      "contacts" -> keyedProjection(request, "list", "contacts:list")
      "messages" -> when (request.optString("kind")) {
        "editor" -> "messages:editor"
        "next-composer-proposal" -> "messages:next-composer-proposal"
        else -> null
      }
      "automation" -> when (request.optString("kind")) {
        "approval" -> "automation:approval"
        "latest-test" -> "automation:latest-test"
        "policy-editor" -> "automation:policy-editor"
        "sender-transfer-operation" -> "automation:sender-transfer-operation"
        else -> null
      }
      "activity" -> when (request.optString("kind")) {
        "list" -> "activity:list"
        "issues" -> "activity:issues"
        else -> null
      }
      "privacy" -> when (request.optString("kind")) {
        "inventory" -> "privacy:inventory"
        "public-resources" -> "privacy:public-resources"
        "current-operation" -> "privacy:current-operation"
        else -> null
      }
      else -> null
    }
  }

  private fun keyedProjection(request: JSONObject, kind: String, key: String): String? =
    key.takeIf { request.optString("kind") == kind }

  private fun loadDocument(): JSONObject? = try {
    reactApplicationContext.assets.open(FIXTURE_ASSET).use { stream ->
      val bytes = stream.readBytes()
      if (bytes.isEmpty() || bytes.size > MAX_FIXTURE_BYTES) return null
      JSONObject(String(bytes, StandardCharsets.UTF_8)).takeIf { fixture ->
        fixture.optInt("schemaVersion") == CONTRACT_VERSION &&
          fixture.optString("revision").matches(REVISION_PATTERN) &&
          fixture.optString("generatedAt").isNotBlank() &&
          fixture.optJSONObject("platforms")?.optJSONObject("android") != null &&
          fixture.optJSONObject("intentProblem")?.optString("kind") == "unsupported"
      }
    }
  } catch (_: Exception) {
    null
  }

  private fun errorResponse(problem: JSONObject) = response("error", problem, document)

  private fun response(kind: String, payload: Any, fixture: JSONObject?) =
    Arguments.createMap().apply {
      putInt("contractVersion", CONTRACT_VERSION)
      putString("revision", fixture?.optString("revision") ?: FALLBACK_REVISION)
      putString("generatedAt", fixture?.optString("generatedAt") ?: FALLBACK_GENERATED_AT)
      putString("kind", kind)
      putString("payloadJson", payload.toString())
    }

  private companion object {
    const val CONTRACT_VERSION = 1
    const val FIXTURE_ASSET = "production-smoke-projections.json"
    const val MAX_FIXTURE_BYTES = 256 * 1024
    const val MAX_REQUEST_CHARS = 64 * 1024
    const val FALLBACK_REVISION = "1"
    const val FALLBACK_GENERATED_AT = "2026-07-12T00:00:00.000Z"
    val REVISION_PATTERN = Regex("(?:0|[1-9][0-9]{0,18})")
    val INTENT_PROBLEM = JSONObject()
      .put("kind", "unsupported")
      .put("code", "distribution-channel-unapproved")
    val UNAVAILABLE_PROBLEM = JSONObject()
      .put("kind", "unsupported")
      .put("code", "native-bridge-unavailable")
  }
}

class SmokeBirthdayNativePackage : BaseReactPackage() {
  override fun getModule(
    name: String,
    reactContext: ReactApplicationContext,
  ): NativeModule? = when (name) {
    NativeBirthdaySpec.NAME -> SmokeBirthdayNativeModule(reactContext)
    else -> null
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider {
    mapOf(
      NativeBirthdaySpec.NAME to ReactModuleInfo(
        NativeBirthdaySpec.NAME,
        SmokeBirthdayNativeModule::class.java.name,
        false,
        false,
        false,
        true,
      ),
    )
  }
}
