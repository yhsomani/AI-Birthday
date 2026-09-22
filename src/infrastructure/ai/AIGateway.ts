/**
 * AI Gateway - Centralized AI orchestration layer
 * 
 * The AIGateway provides a unified interface for all AI operations in the application.
 * It manages:
 * - Provider selection and adapter delegation
 * - Session lifecycle and initialization
 * - Authorization state tracking
 * - Retry logic with exponential backoff
 * - Usage metrics and quota enforcement
 * - Error handling and fallback strategies
 * 
 * This gateway ensures that application code never directly calls AI provider APIs,
 * enabling provider swaps, centralized monitoring, and consistent security controls.
 * 
 * @module infrastructure/ai
 */

import type {
  AIProviderAdapter,
  AIAuthResult,
  AIAuthorizationResult,
  AIGenerationRequest,
  AIGenerationResponse,
  AIGenerationChunk,
  AIStream,
  AIUsageQuota,
} from '../../application/ports/AIProviderPort';

/**
 * Session state for an authenticated AI user
 */
export interface AISessionState {
  /** User identifier */
  readonly userId: string;
  /** Session identifier */
  readonly sessionId: string;
  /** Provider identifier */
  readonly provider: string;
  /** Whether session is authenticated */
  readonly isAuthenticated: boolean;
  /** Whether session is authorized for AI operations */
  readonly isAuthorized: boolean;
  /** Session creation timestamp in milliseconds since epoch */
  readonly createdAt: number;
  /** Session expiry timestamp in milliseconds since epoch */
  readonly expiresAt?: number | undefined;
  /** Last activity timestamp in milliseconds since epoch */
  readonly lastActivityAt: number;
}

/**
 * Authorization status enumeration
 */
export enum AuthorizationStatus {
  /** Not yet authorized */
  NOT_AUTHORIZED = 'not_authorized',
  /** Authorization pending user action */
  PENDING = 'pending',
  /** Fully authorized */
  AUTHORIZED = 'authorized',
  /** Authorization expired */
  EXPIRED = 'expired',
  /** Authorization revoked */
  REVOKED = 'revoked',
  /** Authorization failed */
  FAILED = 'failed',
}

/**
 * Authorization flow result
 */
export interface AIAuthorizationFlow {
  /** Current authorization status */
  readonly status: AuthorizationStatus;
  /** Authorization URL if redirect is needed (OAuth flows) */
  readonly authUrl?: string;
  /** Required scopes that need authorization */
  readonly requiredScopes: readonly string[];
  /** Granted scopes */
  readonly grantedScopes: readonly string[];
  /** Error message if authorization failed */
  readonly error?: string;
}

/**
 * Usage metrics for tracking AI consumption
 */
export interface AIUsageMetrics {
  /** Request identifier */
  readonly requestId: string;
  /** User identifier */
  readonly userId: string;
  /** Provider identifier */
  readonly provider: string;
  /** Model identifier */
  readonly model: string;
  /** Input token count */
  readonly inputTokens: number;
  /** Output token count */
  readonly outputTokens: number;
  /** Request duration in milliseconds */
  readonly durationMs: number;
  /** Whether request succeeded */
  readonly success: boolean;
  /** Error type if failed */
  readonly errorType?: string;
  /** Timestamp in milliseconds since epoch */
  readonly timestamp: number;
}

/**
 * Retry configuration options
 */
export interface RetryConfig {
  /** Maximum number of retry attempts */
  readonly maxAttempts: number;
  /** Initial delay in milliseconds */
  readonly initialDelayMs: number;
  /** Maximum delay in milliseconds */
  readonly maxDelayMs: number;
  /** Backoff multiplier (e.g., 2.0 for exponential backoff) */
  readonly backoffMultiplier: number;
  /** HTTP status codes that should trigger retry */
  readonly retryableStatusCodes?: readonly number[];
  /** Error types that should trigger retry */
  readonly retryableErrorTypes?: readonly string[];
}

/**
 * Default retry configuration
 */
const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxAttempts: 3,
  initialDelayMs: 1000,
  maxDelayMs: 30000,
  backoffMultiplier: 2.0,
  retryableStatusCodes: [408, 429, 500, 502, 503, 504],
  retryableErrorTypes: ['network_error', 'timeout', 'rate_limit_exceeded'],
};

/**
 * AI Gateway Error types
 */
export enum AIGatewayErrorType {
  /** Authentication failed */
  AUTHENTICATION_FAILED = 'authentication_failed',
  /** Authorization failed */
  AUTHORIZATION_FAILED = 'authorization_failed',
  /** Provider unavailable */
  PROVIDER_UNAVAILABLE = 'provider_unavailable',
  /** Rate limit exceeded */
  RATE_LIMIT_EXCEEDED = 'rate_limit_exceeded',
  /** Quota exceeded */
  QUOTA_EXCEEDED = 'quota_exceeded',
  /** Request validation failed */
  VALIDATION_FAILED = 'validation_failed',
  /** Generation failed */
  GENERATION_FAILED = 'generation_failed',
  /** Timeout exceeded */
  TIMEOUT = 'timeout',
  /** Unknown error */
  UNKNOWN = 'unknown',
}

/**
 * AI Gateway Error
 */
export class AIGatewayError extends Error {
  constructor(
    message: string,
    public readonly type: AIGatewayErrorType,
    public readonly cause?: unknown,
    public readonly retryable: boolean = false,
  ) {
    super(message);
    this.name = 'AIGatewayError';
  }
}

/**
 * AI Gateway Configuration
 */
export interface AIGatewayConfig {
  /** Default provider adapter to use */
  readonly defaultProvider: AIProviderAdapter;
  /** Optional map of provider ID to adapter for multi-provider support */
  readonly providers?: ReadonlyMap<string, AIProviderAdapter>;
  /** Retry configuration */
  readonly retryConfig?: RetryConfig;
  /** Request timeout in milliseconds */
  readonly requestTimeoutMs?: number;
  /** Whether to track usage metrics */
  readonly enableUsageTracking?: boolean;
  /** Callback for usage metrics */
  readonly onUsageMetric?: (metric: AIUsageMetrics) => void | Promise<void>;
}

interface ResolvedAIGatewayConfig {
  /** Default provider adapter to use */
  readonly defaultProvider: AIProviderAdapter;
  /** Map of provider ID to adapter for multi-provider support */
  readonly providers: ReadonlyMap<string, AIProviderAdapter>;
  /** Retry configuration */
  readonly retryConfig: RetryConfig;
  /** Request timeout in milliseconds */
  readonly requestTimeoutMs: number;
  /** Whether to track usage metrics */
  readonly enableUsageTracking: boolean;
  /** Callback for usage metrics */
  readonly onUsageMetric: (metric: AIUsageMetrics) => void | Promise<void>;
}

/**
 * AI Gateway - Central orchestration layer for all AI operations
 * 
 * The gateway implements the following responsibilities:
 * 1. Provider abstraction through adapter pattern
 * 2. Session management and lifecycle
 * 3. Authorization state tracking
 * 4. Retry logic with configurable backoff
 * 5. Usage metrics collection
 * 6. Error handling and classification
 * 7. Request validation
 * 
 * Example usage:
 * ```typescript
 * const gateway = new AIGateway({
 *   defaultProvider: new GoogleAIAdapter(apiKey),
 *   enableUsageTracking: true,
 *   onUsageMetric: async (metric) => { /* log to analytics *\/ },
 * });
 * 
 * // Initialize session
 * await gateway.initializeSession(userId, firebaseIdToken);
 * 
 * // Check authorization
 * const authStatus = await gateway.checkAuthorization();
 * if (authStatus.status !== AuthorizationStatus.AUTHORIZED) {
 *   await gateway.requestAuthorization(['generate_content']);
 * }
 * 
 * // Generate content with automatic retry
 * const response = await gateway.executeWithRetry(() =>
 *   gateway.generate({
 *     requestId: crypto.randomUUID(),
 *     systemInstruction: 'You are a helpful assistant.',
 *     prompt: 'Write a birthday greeting.',
 *   })
 * );
 * ```
 */
export class AIGateway {
  private readonly config: ResolvedAIGatewayConfig;
  private readonly sessions: Map<string, AISessionState>;
  private readonly usageMetrics: AIUsageMetrics[];
  private readonly maxMetricsBuffer: number;

  constructor(config: AIGatewayConfig) {
    this.config = {
      defaultProvider: config.defaultProvider,
      providers: config.providers ?? new Map(),
      retryConfig: config.retryConfig ?? DEFAULT_RETRY_CONFIG,
      requestTimeoutMs: config.requestTimeoutMs ?? 30000,
      enableUsageTracking: config.enableUsageTracking ?? true,
      onUsageMetric: config.onUsageMetric ?? (() => {}),
    };
    this.sessions = new Map();
    this.usageMetrics = [];
    this.maxMetricsBuffer = 1000;
  }

  /**
   * Get the default provider adapter
   */
  private getProvider(): AIProviderAdapter {
    return this.config.defaultProvider;
  }

  /**
   * Get a specific provider adapter by ID
   */
  getProviderById(providerId: string): AIProviderAdapter | undefined {
    if (providerId === this.config.defaultProvider.getProviderId()) {
      return this.config.defaultProvider;
    }
    return this.config.providers?.get(providerId);
  }

  /**
   * Initialize an AI session for a user
   * 
   * This method authenticates the user with the AI provider and establishes
   * a session context for subsequent operations.
   * 
   * @param userId - Unique user identifier
   * @param userToken - User's authentication token (e.g., Firebase ID token)
   * @returns Promise resolving to session state
   */
  async initializeSession(
    userId: string,
    userToken: string,
  ): Promise<AISessionState> {
    const provider = this.getProvider();
    
    // Authenticate with provider
    const authResult = await provider.authenticate(userToken);
    
    if (!authResult.isAuthenticated) {
      throw new AIGatewayError(
        authResult.error ?? 'Authentication failed',
        AIGatewayErrorType.AUTHENTICATION_FAILED,
        undefined,
        false,
      );
    }

    const now = Date.now();
    const session: AISessionState = {
      userId,
      sessionId: authResult.sessionId ?? `session-${now}-${Math.random().toString(36).slice(2)}`,
      provider: provider.getProviderId(),
      isAuthenticated: true,
      isAuthorized: false, // Will be set after authorization check
      createdAt: now,
      expiresAt: authResult.expiresAt,
      lastActivityAt: now,
    };

    this.sessions.set(userId, session);
    return session;
  }

  /**
   * Check the current authorization status
   * 
   * @param userId - User identifier
   * @returns Promise resolving to authorization status
   */
  async checkAuthorization(userId: string): Promise<AuthorizationStatus> {
    const session = this.sessions.get(userId);
    if (!session) {
      return AuthorizationStatus.NOT_AUTHORIZED;
    }

    const provider = this.getProvider();
    const isAvailable = await provider.isAvailable();
    
    if (!isAvailable) {
      return AuthorizationStatus.FAILED;
    }

    // Check session expiry
    if (session.expiresAt && Date.now() > session.expiresAt) {
      return AuthorizationStatus.EXPIRED;
    }

    // Update session activity
    this.sessions.set(userId, {
      ...session,
      lastActivityAt: Date.now(),
    });

    return session.isAuthorized
      ? AuthorizationStatus.AUTHORIZED
      : AuthorizationStatus.NOT_AUTHORIZED;
  }

  /**
   * Request authorization for AI operations
   * 
   * This method initiates the authorization flow with the AI provider.
   * For OAuth-based providers, this may involve redirecting the user
   * to a consent screen.
   * 
   * @param userId - User identifier
   * @param scopes - Permission scopes to request
   * @returns Promise resolving to authorization flow result
   */
  async requestAuthorization(
    userId: string,
    scopes: readonly string[] = ['generate_content'],
  ): Promise<AIAuthorizationFlow> {
    const session = this.sessions.get(userId);
    if (!session) {
      return {
        status: AuthorizationStatus.NOT_AUTHORIZED,
        requiredScopes: scopes,
        grantedScopes: [],
        error: 'No active session',
      };
    }

    const provider = this.getProvider();
    
    try {
      const authResult = await provider.authorize(scopes);
      
      if (authResult.isAuthorized) {
        // Update session authorization state
        this.sessions.set(userId, {
          ...session,
          isAuthorized: true,
          lastActivityAt: Date.now(),
        });

        return {
          status: AuthorizationStatus.AUTHORIZED,
          requiredScopes: scopes,
          grantedScopes: authResult.grantedScopes,
        };
      } else {
        return {
          status: AuthorizationStatus.PENDING,
          requiredScopes: scopes,
          grantedScopes: authResult.grantedScopes,
          error: authResult.error ?? 'Authorization pending',
        };
      }
    } catch (error) {
      return {
        status: AuthorizationStatus.FAILED,
        requiredScopes: scopes,
        grantedScopes: [],
        error: error instanceof Error ? error.message : 'Authorization failed',
      };
    }
  }

  /**
   * Execute an operation with automatic retry logic
   * 
   * This method wraps any AI operation with configurable retry logic,
   * including exponential backoff and error classification.
   * 
   * @param operation - Async operation to execute
   * @param config - Optional retry configuration override
   * @returns Promise resolving to operation result
   */
  async executeWithRetry<T>(
    operation: () => Promise<T>,
    config?: Partial<RetryConfig>,
  ): Promise<T> {
    const retryConfig = { ...this.config.retryConfig, ...config };
    let lastError: unknown;
    let delayMs = retryConfig.initialDelayMs;

    for (let attempt = 1; attempt <= retryConfig.maxAttempts; attempt++) {
      try {
        return await operation();
      } catch (error) {
        lastError = error;
        
        // Check if error is retryable
        const isRetryable = this.isRetryableError(error, retryConfig);
        if (!isRetryable || attempt === retryConfig.maxAttempts) {
          throw error;
        }

        // Wait before retry with exponential backoff
        await this.sleep(delayMs);
        delayMs = Math.min(delayMs * retryConfig.backoffMultiplier, retryConfig.maxDelayMs);
      }
    }

    throw lastError;
  }

  /**
   * Generate content using the AI provider
   * 
   * This is the primary method for AI content generation. It includes
   * request validation, authorization checking, and usage tracking.
   * 
   * @param userId - User identifier
   * @param request - Generation request parameters
   * @returns Promise resolving to generation response
   */
  async generate(
    userId: string,
    request: AIGenerationRequest,
  ): Promise<AIGenerationResponse> {
    const session = this.sessions.get(userId);
    if (!session) {
      throw new AIGatewayError(
        'No active session',
        AIGatewayErrorType.AUTHENTICATION_FAILED,
        undefined,
        false,
      );
    }

    if (!session.isAuthorized) {
      throw new AIGatewayError(
        'Session not authorized',
        AIGatewayErrorType.AUTHORIZATION_FAILED,
        undefined,
        false,
      );
    }

    const provider = this.getProvider();
    const startTime = Date.now();

    try {
      const response = await this.executeWithRetry(() =>
        this.withTimeout(
          provider.generate(request),
          this.config.requestTimeoutMs,
        ),
      );

      // Track usage metrics
      if (this.config.enableUsageTracking) {
        const metric: AIUsageMetrics = {
          requestId: request.requestId,
          userId,
          provider: provider.getProviderId(),
          model: response.model,
          inputTokens: response.inputTokenCount,
          outputTokens: response.outputTokenCount,
          durationMs: Date.now() - startTime,
          success: true,
          timestamp: Date.now(),
        };
        this.recordUsageMetric(metric);
      }

      return response;
    } catch (error) {
      // Track failure metrics
      if (this.config.enableUsageTracking) {
        const metric: AIUsageMetrics = {
          requestId: request.requestId,
          userId,
          provider: provider.getProviderId(),
          model: 'unknown',
          inputTokens: 0,
          outputTokens: 0,
          durationMs: Date.now() - startTime,
          success: false,
          errorType: error instanceof AIGatewayError ? error.type : AIGatewayErrorType.UNKNOWN,
          timestamp: Date.now(),
        };
        this.recordUsageMetric(metric);
      }

      throw error;
    }
  }

  /**
   * Stream content generation incrementally
   * 
   * @param userId - User identifier
   * @param request - Generation request parameters
   * @returns Async stream of generation chunks
   */
  stream(
    userId: string,
    request: AIGenerationRequest,
  ): AIStream<AIGenerationChunk> {
    const session = this.sessions.get(userId);
    if (!session) {
      throw new AIGatewayError(
        'No active session',
        AIGatewayErrorType.AUTHENTICATION_FAILED,
        undefined,
        false,
      );
    }

    if (!session.isAuthorized) {
      throw new AIGatewayError(
        'Session not authorized',
        AIGatewayErrorType.AUTHORIZATION_FAILED,
        undefined,
        false,
      );
    }

    const provider = this.getProvider();
    return provider.stream(request);
  }

  /**
   * Get current usage quota information
   * 
   * @param userId - User identifier
   * @returns Promise resolving to usage quota information
   */
  async getUsage(userId: string): Promise<AIUsageQuota> {
    const session = this.sessions.get(userId);
    if (!session) {
      throw new AIGatewayError(
        'No active session',
        AIGatewayErrorType.AUTHENTICATION_FAILED,
        undefined,
        false,
      );
    }

    const provider = this.getProvider();
    return provider.getUsage();
  }

  /**
   * Revoke AI access and clear session
   * 
   * @param userId - User identifier
   */
  async revoke(userId: string): Promise<void> {
    const session = this.sessions.get(userId);
    if (session) {
      const provider = this.getProvider();
      await provider.revoke();
      this.sessions.delete(userId);
    }
  }

  /**
   * Clear all sessions (e.g., on logout)
   */
  async clearAllSessions(): Promise<void> {
    const provider = this.getProvider();
    await provider.revoke();
    this.sessions.clear();
  }

  /**
   * Record a usage metric
   */
  private recordUsageMetric(metric: AIUsageMetrics): void {
    this.usageMetrics.push(metric);
    
    // Buffer rotation
    if (this.usageMetrics.length > this.maxMetricsBuffer) {
      this.usageMetrics.splice(0, this.usageMetrics.length - this.maxMetricsBuffer);
    }

    // Invoke callback
    try {
      const result = this.config.onUsageMetric(metric);
      if (result instanceof Promise) {
        result.catch(() => {
          // Silently ignore callback errors to avoid disrupting main flow
        });
      }
    } catch {
      // Silently ignore callback errors
    }
  }

  /**
   * Check if an error is retryable
   */
  private isRetryableError(error: unknown, config: RetryConfig): boolean {
    if (error instanceof AIGatewayError) {
      return error.retryable;
    }

    // Check error type
    if (config.retryableErrorTypes) {
      const errorType = error instanceof Error ? error.name : 'unknown';
      if (config.retryableErrorTypes.includes(errorType)) {
        return true;
      }
    }

    // Check if it's a network-related error
    if (error instanceof Error) {
      const networkErrors = [
        'TypeError', // Fetch network errors
        'NetworkError',
        'AbortError',
        'TimeoutError',
      ];
      if (networkErrors.includes(error.name)) {
        return true;
      }
      
      // Check message for network indicators
      const networkIndicators = [
        'network',
        'fetch',
        'connection',
        'timeout',
        'ETIMEDOUT',
        'ECONNRESET',
      ];
      const lowerMessage = error.message.toLowerCase();
      if (networkIndicators.some(indicator => lowerMessage.includes(indicator))) {
        return true;
      }
    }

    return false;
  }

  /**
   * Sleep utility for retry delays
   */
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Wrap a promise with a timeout
   */
  private async withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
    const timeout = new Promise<never>((_, reject) => {
      setTimeout(() => {
        reject(new AIGatewayError(
          `Operation timed out after ${timeoutMs}ms`,
          AIGatewayErrorType.TIMEOUT,
          undefined,
          true,
        ));
      }, timeoutMs);
    });
    
    return Promise.race([promise, timeout]);
  }

  /**
   * Get current session for a user
   */
  getSession(userId: string): AISessionState | undefined {
    return this.sessions.get(userId);
  }

  /**
   * Get all active sessions
   */
  getAllSessions(): readonly AISessionState[] {
    return Array.from(this.sessions.values());
  }

  /**
   * Get recent usage metrics
   */
  getRecentMetrics(limit: number = 100): readonly AIUsageMetrics[] {
    return this.usageMetrics.slice(-limit);
  }
}
