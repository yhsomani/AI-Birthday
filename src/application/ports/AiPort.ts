import type {
  AiEntitlementProjection,
  AIRequest,
  AIResult,
  CachedAiEntitlement,
} from '../../domain/ai/model';
import type {
  AIMessageSuggestionRequest,
  AIMessageSuggestionResult,
} from '../../domain/messages/model';
import type { NativeResult } from '../../domain/shared/result';

/**
 * Provider-agnostic AI application port.
 *
 * Core architectural invariant:
 * The application's subscription controls whether the user is allowed
 * to use ANY AI feature. External AI capabilities determine execution
 * target, not access entitlement.
 */
export interface AiPort {
  /**
   * Retrieves the current AI entitlement status, including quota,
   * plan, capabilities, and provider access configuration.
   */
  getAiEntitlementStatus(): Promise<NativeResult<AiEntitlementProjection>>;

  /**
   * Primary entry point for AI generation across any supported capability.
   */
  generateAi(request: AIRequest): Promise<NativeResult<AIResult>>;

  /**
   * Specialized message suggestion drafting endpoint.
   */
  generateSuggestions(
    request: AIMessageSuggestionRequest,
  ): Promise<NativeResult<AIMessageSuggestionResult>>;

  /**
   * Reads the offline cached entitlement snapshot if present.
   */
  getCachedEntitlement?(): Promise<CachedAiEntitlement | null>;

  /**
   * Persists an offline entitlement snapshot to local secure storage.
   */
  saveCachedEntitlement?(cached: CachedAiEntitlement): Promise<void>;
}
