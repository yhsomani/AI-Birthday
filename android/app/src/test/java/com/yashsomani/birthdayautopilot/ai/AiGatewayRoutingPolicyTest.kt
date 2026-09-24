package com.yashsomani.birthdayautopilot.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGatewayRoutingPolicyTest {
  private fun plus() = AiEntitlementSnapshot(
    enabled = true,
    plan = AiEntitlementSnapshot.PLAN_WISHWELL_PLUS,
    requestsPerDay = 50,
    requestsPerMonth = 300,
  )

  @Test
  fun `entitlement gate blocks before any provider routing`() {
    val blocked = AiGatewayRoutingPolicy.route(
      entitlement = AiEntitlementSnapshot.disabled(),
      usedToday = 0,
      usedInPeriod = 0,
      connectedSignInProvider = AiProviderId.GEMINI_CLOUD,
      onDeviceCapable = true,
    )
    assertEquals(
      AiRouteDecision.Blocked(AiGatewayRoutingPolicy.REASON_SUBSCRIPTION_REQUIRED),
      blocked,
    )
  }

  @Test
  fun `quota exhaustion blocks even with a connected provider sign-in`() {
    assertEquals(
      AiRouteDecision.Blocked(AiGatewayRoutingPolicy.REASON_QUOTA_EXHAUSTED),
      AiGatewayRoutingPolicy.route(plus(), usedToday = 50, usedInPeriod = 0),
    )
    assertEquals(
      AiRouteDecision.Blocked(AiGatewayRoutingPolicy.REASON_QUOTA_EXHAUSTED),
      AiGatewayRoutingPolicy.route(plus(), usedToday = 0, usedInPeriod = 300),
    )
  }

  @Test
  fun `routing order is on-device then provider sign-in then application-owned`() {
    assertTrue(
      AiGatewayRoutingPolicy.route(
        plus(),
        usedToday = 1,
        usedInPeriod = 1,
        connectedSignInProvider = AiProviderId.GEMINI_CLOUD,
        onDeviceCapable = true,
      ) is AiRouteDecision.OnDevice,
    )
    assertEquals(
      AiRouteDecision.CloudProviderSignIn(AiProviderId.GEMINI_CLOUD),
      AiGatewayRoutingPolicy.route(
        plus(),
        usedToday = 1,
        usedInPeriod = 1,
        connectedSignInProvider = AiProviderId.GEMINI_CLOUD,
        onDeviceCapable = false,
      ),
    )
    assertEquals(
      AiRouteDecision.CloudApplicationOwned(AiProviderId.GEMINI_CLOUD),
      AiGatewayRoutingPolicy.route(plus(), usedToday = 1, usedInPeriod = 1),
    )
  }

  @Test
  fun `provider sign-in uses the user's own login not an api key`() {
    val decision = AiRouteDecision.CloudProviderSignIn(AiProviderId.GEMINI_CLOUD)
    assertEquals(AiAuthorizationMode.PROVIDER_SIGN_IN, decision.authorizationMode)
    // The wire vocabulary contains no API-key modes or key-state values.
    assertFalse(
      AiAuthorizationMode.entries.map { it.name }.any { "KEY" in it },
    )
    assertEquals(
      listOf("disconnected", "connecting", "connected", "expired", "failed"),
      AiConnectionState.entries.map { it.wireValue },
    )
    assertEquals(listOf("gemini-cloud", "on-device"), AiProviderId.entries.map { it.wireValue })
  }

  @Test
  fun `fail-closed parse of malformed entitlement payloads`() {
    assertNull(AiProviderId.fromWire("byok-openai"))
    assertNull(AiConnectionState.fromWire("active"))
    // Missing fields default to disabled / zero quota.
    val parsed = AiEntitlementSnapshot.parse(JSONObject())
    assertFalse(parsed.enabled)
    assertEquals(0, parsed.requestsPerDay)
    assertEquals(0, parsed.requestsPerMonth)
    // A payload claiming enabled=true while still on the free plan stays off.
    val sneaky = AiEntitlementSnapshot.parse(
      JSONObject("""{"enabled":true,"plan":"free","requestsPerDay":99,"requestsPerMonth":99}"""),
    )
    assertFalse(sneaky.enabled)
    // Null parses as fully disabled.
    assertFalse(AiEntitlementSnapshot.parse(null).enabled)
    // A well-formed Plus payload parses through.
    val good = AiEntitlementSnapshot.parse(
      JSONObject(
        """{"enabled":true,"plan":"wishwell-plus","requestsPerDay":50,"requestsPerMonth":300}""",
      ),
    )
    assertTrue(good.enabled)
    assertEquals(50, good.requestsPerDay)
    assertEquals(300, good.requestsPerMonth)
  }
}
