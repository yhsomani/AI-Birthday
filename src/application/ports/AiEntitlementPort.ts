import type {
  AiConnectionState,
  AiEntitlementProjection,
  AiSettingsProjection,
  AiSignInProvider,
  AiSignInResult,
  AiUsageSnapshot,
} from '../../domain/ai/model';
import type { NativeRevision } from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';

/**
 * AI entitlement & provider-access port.
 *
 * This port is deliberately provider-agnostic: nothing in its surface names a
 * specific vendor's auth or billing system. Providers (Gemini cloud, on-device,
 * future OpenAI-compatible adapters) are implementation details behind the
 * native AI gateway.
 *
 * Business rule enforced by every implementation:
 *   AI availability is decided ONLY by the application's own subscription /
 *   entitlement state — never by a user's external provider subscription
 *   (e.g. a consumer Google AI plan). See AI_ENTITLEMENT_ARCHITECTURE.md.
 */
export interface AiEntitlementPort {
  getAiSettings(): Promise<NativeResult<AiSettingsProjection>>;
  getAiEntitlement(): Promise<NativeResult<AiEntitlementProjection>>;
  getAiUsage(): Promise<NativeResult<AiUsageSnapshot | null>>;
  /**
   * Provider sign-in ("bring your own AI — via login, not keys"): run an
   * OAuth 2.0 authorization-code + PKCE flow against the provider in a
   * Custom Tab. The resulting tokens live ONLY in device secure storage
   * (EncryptedSharedPreferences / Android keystore); they never cross back
   * over the bridge to JavaScript, and signing in does NOT bypass the
   * entitlement gate above. Users never paste or manage API keys.
   */
  connectAiProvider(input: {
    provider: AiSignInProvider;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<{ result: AiSignInResult; connectionState: AiConnectionState }>>;
  disconnectAiProvider(input: {
    provider: AiSignInProvider;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<{ connectionState: 'disconnected' }>>;
}
