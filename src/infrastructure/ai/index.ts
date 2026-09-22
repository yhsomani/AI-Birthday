/**
 * AI Gateway Module Exports
 * 
 * This module provides the centralized AI orchestration layer for the application.
 * It exports the gateway, provider interface, and reference implementations.
 */

// Core gateway
export { AIGateway } from './AIGateway';
export type {
  AISessionState,
  AIAuthorizationFlow,
  AIUsageMetrics,
  RetryConfig,
} from './AIGateway';
export { AuthorizationStatus, AIGatewayErrorType, AIGatewayError } from './AIGateway';

// Provider port (interface)
export type {
  AIProviderAdapter,
  AIAuthResult,
  AIAuthorizationResult,
  AIGenerationRequest,
  AIGenerationResponse,
  AIGenerationChunk,
  AIStream,
  AIUsageQuota,
} from '../../application/ports/AIProviderPort';

// Provider implementations
export { GoogleAIProviderAdapter } from './providers/GoogleAIProviderAdapter';
export type { GoogleAIProviderConfig } from './providers/GoogleAIProviderAdapter';
