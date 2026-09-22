/**
 * AI Provider Port - Abstract interface for AI provider adapters
 * 
 * This port defines the contract that all AI provider implementations must follow.
 * It enables provider-agnostic AI operations while allowing each provider to
 * implement their specific authentication, authorization, and generation logic.
 * 
 * @module application/ports
 */

/**
 * Authentication result from an AI provider
 */
export interface AIAuthResult {
  /** Whether authentication succeeded */
  readonly isAuthenticated: boolean;
  /** Provider-specific session identifier (opaque to application) */
  readonly sessionId?: string;
  /** Token expiry timestamp in milliseconds since epoch */
  readonly expiresAt?: number;
  /** Error reason if authentication failed */
  readonly error?: string;
}

/**
 * Authorization result from an AI provider
 */
export interface AIAuthorizationResult {
  /** Whether authorization succeeded */
  readonly isAuthorized: boolean;
  /** Granted scopes */
  readonly grantedScopes: readonly string[];
  /** Authorization expiry timestamp in milliseconds since epoch */
  readonly expiresAt?: number;
  /** Error reason if authorization failed */
  readonly error?: string;
}

/**
 * Request for AI content generation
 */
export interface AIGenerationRequest {
  /** Unique request identifier for tracking */
  readonly requestId: string;
  /** System instruction/prompt */
  readonly systemInstruction: string;
  /** User prompt/content */
  readonly prompt: string;
  /** Temperature setting (0.0 to 1.0) */
  readonly temperature?: number;
  /** Maximum output tokens */
  readonly maxOutputTokens?: number;
  /** Response format hint (e.g., 'text', 'application/json') */
  readonly responseFormat?: string;
  /** Optional schema for structured output */
  readonly outputSchema?: Record<string, unknown>;
}

/**
 * Response from AI content generation
 */
export interface AIGenerationResponse {
  /** Request identifier matching the input request */
  readonly requestId: string;
  /** Generated text content */
  readonly text: string;
  /** Number of tokens used in input */
  readonly inputTokenCount: number;
  /** Number of tokens used in output */
  readonly outputTokenCount: number;
  /** Provider identifier */
  readonly provider: string;
  /** Model identifier used */
  readonly model: string;
  /** Generation timestamp in milliseconds since epoch */
  readonly generatedAt: number;
}

/**
 * Streaming chunk for incremental generation
 */
export interface AIGenerationChunk {
  /** Request identifier */
  readonly requestId: string;
  /** Chunk of generated text */
  readonly textChunk: string;
  /** Whether this is the final chunk */
  readonly isFinal: boolean;
  /** Cumulative token count so far */
  readonly cumulativeTokenCount: number;
}

/**
 * Usage quota information
 */
export interface AIUsageQuota {
  /** Total quota units available */
  readonly totalQuota: number;
  /** Quota units used in current period */
  readonly usedQuota: number;
  /** Quota units remaining */
  readonly remainingQuota: number;
  /** Period start timestamp in milliseconds since epoch */
  readonly periodStart: number;
  /** Period end timestamp in milliseconds since epoch */
  readonly periodEnd: number;
  /** Quota unit type (e.g., 'requests', 'tokens', 'USD') */
  readonly unitType: string;
}

/**
 * Async iterator for streaming responses
 */
export type AIStream<T> = AsyncIterable<T>;

/**
 * AI Provider Adapter Interface
 * 
 * All AI provider implementations (Google AI, Vertex AI, Azure OpenAI, Anthropic, etc.)
 * must implement this interface. The AIGateway uses this interface to delegate
 * provider-specific operations while maintaining a consistent application-level API.
 * 
 * @interface
 */
export interface AIProviderAdapter {
  /**
   * Authenticate the user with the AI provider
   * 
   * This method handles provider-specific authentication mechanisms such as:
   * - API key validation
   * - OAuth token exchange
   * - Service account credential verification
   * 
   * @param userToken - User's authentication token (e.g., Firebase ID token)
   * @returns Promise resolving to authentication result
   */
  authenticate(userToken: string): Promise<AIAuthResult>;

  /**
   * Authorize specific scopes/permissions with the AI provider
   * 
   * This method handles provider-specific authorization flows such as:
   * - OAuth consent screen presentation
   * - Scope negotiation
   * - Token refresh for extended permissions
   * 
   * @param scopes - List of permission scopes to request
   * @returns Promise resolving to authorization result
   */
  authorize(scopes: readonly string[]): Promise<AIAuthorizationResult>;

  /**
   * Generate content using the AI provider
   * 
   * This is the primary method for AI content generation. Implementations
   * must handle:
   * - Request validation
   * - Provider API communication
   * - Response parsing and normalization
   * - Error handling and retry logic
   * 
   * @param request - Generation request parameters
   * @returns Promise resolving to generation response
   */
  generate(request: AIGenerationRequest): Promise<AIGenerationResponse>;

  /**
   * Stream content generation incrementally
   * 
   * For providers that support streaming responses, this method returns
   * an async iterable that yields chunks as they become available.
   * 
   * @param request - Generation request parameters
   * @returns Async stream of generation chunks
   */
  stream(request: AIGenerationRequest): AIStream<AIGenerationChunk>;

  /**
   * Get current usage quota information
   * 
   * Returns quota/usage statistics for the current billing period.
   * Implementation depends on provider's quota tracking mechanism.
   * 
   * @returns Promise resolving to usage quota information
   */
  getUsage(): Promise<AIUsageQuota>;

  /**
   * Revoke access and clear credentials
   * 
   * Called when user logs out or revokes AI provider access.
   * Implementations must securely clear any cached credentials/tokens.
   * 
   * @returns Promise that resolves when revocation is complete
   */
  revoke(): Promise<void>;

  /**
   * Get the provider identifier
   * 
   * @returns Provider identifier string (e.g., 'google-ai', 'vertex-ai', 'azure-openai')
   */
  getProviderId(): string;

  /**
   * Check if the provider is currently available
   * 
   * @returns Promise resolving to availability status
   */
  isAvailable(): Promise<boolean>;
}
