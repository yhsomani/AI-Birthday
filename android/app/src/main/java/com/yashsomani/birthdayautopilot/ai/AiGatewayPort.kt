package com.yashsomani.birthdayautopilot.ai

import org.json.JSONObject

/**
 * Provider-agnostic AI gateway boundary.
 *
 * The application's own entitlement state (never an external provider
 * subscription) gates every request; provider selection is a routing decision
 * made behind this interface. Today the only wired adapter is the native-only
 * Gemini path (`gemini/AndroidGeminiSuggestionGateway.kt`, application-owned
 * Firebase authorisation). Provider sign-in (OAuth "use my AI login" — users
 * never paste API keys) and on-device adapters plug in here without touching
 * callers.
 *
 * See AI_ENTITLEMENT_ARCHITECTURE.md at the repository root for the full model
 * (identity ≠ app subscription ≠ provider authorisation ≠ execution target).
 */

/** Which authorisation supplies provider credentials for one generation. */
enum class AiAuthorizationMode {
  /** Application-owned project credentials (current production path). */
  APPLICATION_OWNED,

  /**
   * The user signs into the AI provider with their own account via OAuth 2.0
   * authorization code + PKCE ("bring your own AI — via login, not keys").
   * No API keys are ever pasted or handled by the app; tokens live only in
   * device secure storage and never cross the bridge to JavaScript.
   */
  PROVIDER_SIGN_IN,

  /** No provider credential at all; inference runs on-device (planned). */
  ON_DEVICE,
}

/** Lifecycle of a user's provider sign-in connection. */
enum class AiConnectionState(val wireValue: String) {
  DISCONNECTED("disconnected"),
  CONNECTING("connecting"),
  CONNECTED("connected"),
  EXPIRED("expired"),
  FAILED("failed");

  companion object {
    fun fromWire(value: String?): AiConnectionState? = entries.firstOrNull {
      it.wireValue == value
    }
  }
}

/** Closed set of routing targets. Adding a provider = adding one entry + one adapter. */
enum class AiProviderId(val wireValue: String) {
  GEMINI_CLOUD("gemini-cloud"),
  ON_DEVICE("on-device");

  companion object {
    fun fromWire(value: String?): AiProviderId? = entries.firstOrNull {
      it.wireValue == value
    }
  }
}

/**
 * Entitlement snapshot consumed by the router. Mirrors the TypeScript domain
 * model in src/domain/ai/model.ts; both sides are validated fail-closed.
 */
internal data class AiEntitlementSnapshot(
  val enabled: Boolean,
  val plan: String,
  val requestsPerDay: Int,
  val requestsPerMonth: Int,
) {
  companion object {
    const val PLAN_FREE = "free"
    const val PLAN_WISHWELL_PLUS = "wishwell-plus"

    /** Fail-closed parse: anything malformed means "no AI", never "default on". */
    fun parse(value: JSONObject?): AiEntitlementSnapshot {
      if (value == null) return disabled()
      val plan = value.optString("plan", PLAN_FREE)
      val enabled = value.optBoolean("enabled", false) && plan != PLAN_FREE
      return AiEntitlementSnapshot(
        enabled = enabled,
        plan = plan,
        requestsPerDay = value.optInt("requestsPerDay", 0).coerceAtLeast(0),
        requestsPerMonth = value.optInt("requestsPerMonth", 0).coerceAtLeast(0),
      )
    }

    fun disabled() = AiEntitlementSnapshot(false, PLAN_FREE, 0, 0)
  }
}

/** Routing outcome for one request. */
internal sealed interface AiRouteDecision {
  val authorizationMode: AiAuthorizationMode

  data class CloudApplicationOwned(
    val provider: AiProviderId,
  ) : AiRouteDecision {
    override val authorizationMode: AiAuthorizationMode =
      AiAuthorizationMode.APPLICATION_OWNED
  }

  /**
   * The user is signed in to the provider with their own account (OAuth);
   * requests run under the user's provider authorisation, not app keys.
   */
  data class CloudProviderSignIn(
    val provider: AiProviderId,
  ) : AiRouteDecision {
    override val authorizationMode: AiAuthorizationMode =
      AiAuthorizationMode.PROVIDER_SIGN_IN
  }

  data object OnDevice : AiRouteDecision {
    override val authorizationMode: AiAuthorizationMode = AiAuthorizationMode.ON_DEVICE
    val provider: AiProviderId = AiProviderId.ON_DEVICE
  }

  data class Blocked(val reason: String) : AiRouteDecision {
    override val authorizationMode: AiAuthorizationMode =
      AiAuthorizationMode.APPLICATION_OWNED
  }
}

/**
 * Pure routing policy: entitlement first, provider second. A user's external
 * provider subscription must never influence [route]; it is not observable
 * from this process by design. Provider sign-in state (an OAuth connection,
 * never an API key) is a routing input that sits BEHIND the entitlement gate.
 */
internal object AiGatewayRoutingPolicy {
  const val REASON_SUBSCRIPTION_REQUIRED = "ai-subscription-required"
  const val REASON_QUOTA_EXHAUSTED = "ai-quota-exhausted"

  fun route(
    entitlement: AiEntitlementSnapshot,
    usedToday: Int,
    usedInPeriod: Int,
    connectedSignInProvider: AiProviderId? = null,
    onDeviceCapable: Boolean = false,
  ): AiRouteDecision {
    if (!entitlement.enabled) {
      return AiRouteDecision.Blocked(REASON_SUBSCRIPTION_REQUIRED)
    }
    if (usedToday >= entitlement.requestsPerDay || usedInPeriod >= entitlement.requestsPerMonth) {
      return AiRouteDecision.Blocked(REASON_QUOTA_EXHAUSTED)
    }
    // Cost optimisation order: on-device where capable, then a connected
    // provider sign-in (user's own AI login pays their provider), then the
    // application-owned cloud path.
    if (onDeviceCapable) return AiRouteDecision.OnDevice
    connectedSignInProvider?.let { return AiRouteDecision.CloudProviderSignIn(it) }
    return AiRouteDecision.CloudApplicationOwned(AiProviderId.GEMINI_CLOUD)
  }
}
