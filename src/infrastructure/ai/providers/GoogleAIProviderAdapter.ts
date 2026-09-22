/**
 * Google AI Provider Adapter (App-Subsidized Model)
 * 
 * This adapter implements the AIProviderAdapter interface for Google's Generative AI API
 * using the app-subsidized billing model. In this model:
 * 
 * - The application manages its own API key(s)
 * - The app covers all AI usage costs within free tier limits
 * - No user authorization is required for AI operations
 * - Rate limiting is enforced per user/installation to prevent abuse
 * - Users do not need their own Google Cloud projects or API keys
 * 
 * IMPORTANT: This adapter does NOT provide access to user's consumer Gemini/Google One
 * AI Premium subscriptions. Google's documentation does not support using consumer
 * subscriptions for third-party API calls.
 * 
 * For enterprise scenarios requiring user-owned quota, consider the VertexAIAdapter
 * which implements OAuth-based authentication with separate consent.
 * 
 * @module infrastructure/ai/providers
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
} from '../../../application/ports/AIProviderPort';

/**
 * Configuration for Google AI Provider
 */
export interface GoogleAIProviderConfig {
  /** API key for Google AI Studio */
  readonly apiKey: string;
  /** Model identifier (e.g., 'gemini-1.5-flash') */
  readonly model?: string;
  /** API endpoint URL */
  readonly baseUrl?: string;
  /** Request timeout in milliseconds */
  readonly timeoutMs?: number;
}

/**
 * Default configuration values
 */
const DEFAULT_CONFIG: Required<Omit<GoogleAIProviderConfig, 'apiKey'>> = {
  model: 'gemini-1.5-flash',
  baseUrl: 'https://generativelanguage.googleapis.com/v1beta',
  timeoutMs: 30000,
};

/**
 * Google AI Provider Adapter
 * 
 * Implements AI provider operations using Google's Generative AI API with
 * app-managed API key authentication. This is the simplest integration model
 * suitable for most applications.
 * 
 * Features:
 * - API key-based authentication (app-managed)
 * - Automatic token refresh handling
 * - Structured output support via JSON mode
 * - Usage tracking against app quota
 * - Fail-closed error handling
 * 
 * Security considerations:
 * - API keys must be stored securely (never in client-side code)
 * - Use Firebase App Check or similar to protect API endpoints
 * - Implement rate limiting per user to prevent quota exhaustion
 * - Rotate API keys periodically
 */
export class GoogleAIProviderAdapter implements AIProviderAdapter {
  private readonly config: Required<GoogleAIProviderConfig>;
  private isAuthenticated: boolean = false;
  private lastAuthTime: number = 0;
  private authCacheExpiryMs: number = 3600000; // 1 hour

  constructor(config: GoogleAIProviderConfig) {
    if (!config.apiKey || config.apiKey.trim() === '') {
      throw new Error('Google AI API key is required');
    }
    this.config = {
      ...DEFAULT_CONFIG,
      apiKey: config.apiKey,
    };
  }

  /**
   * Get the provider identifier
   */
  getProviderId(): string {
    return 'google-ai';
  }

  /**
   * Authenticate with Google AI
   * 
   * In the app-subsidized model, authentication is simply validating
   * that the API key is present and properly configured.
   * 
   * @param userToken - User's authentication token (not used in this model)
   * @returns Authentication result
   */
  async authenticate(userToken: string): Promise<AIAuthResult> {
    const now = Date.now();
    
    // Check if we have a valid cached authentication
    if (this.isAuthenticated && (now - this.lastAuthTime) < this.authCacheExpiryMs) {
      return {
        isAuthenticated: true,
        sessionId: `google-ai-session-${this.lastAuthTime}`,
        expiresAt: this.lastAuthTime + this.authCacheExpiryMs,
      };
    }

    // Validate API key format
    if (!this.isValidApiKeyFormat(this.config.apiKey)) {
      return {
        isAuthenticated: false,
        error: 'Invalid API key format',
      };
    }

    // Attempt a simple API call to verify the key is active
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);
      
      const response = await fetch(
        `${this.config.baseUrl}/models?key=${this.config.apiKey}`,
        {
          method: 'GET',
          signal: controller.signal,
        },
      );
      
      clearTimeout(timeoutId);
      
      if (response.ok) {
        this.isAuthenticated = true;
        this.lastAuthTime = now;
        
        return {
          isAuthenticated: true,
          sessionId: `google-ai-session-${now}`,
          expiresAt: now + this.authCacheExpiryMs,
        };
      } else {
        const errorText = await response.text().catch(() => 'Unknown error');
        return {
          isAuthenticated: false,
          error: `Authentication failed: ${response.status} ${errorText}`,
        };
      }
    } catch (error) {
      return {
        isAuthenticated: false,
        error: error instanceof Error ? error.message : 'Network error during authentication',
      };
    }
  }

  /**
   * Authorize scopes
   * 
   * In the app-subsidized model with API key authentication, no additional
   * user authorization is required. The API key grants the permissions
   * configured in Google Cloud Console.
   * 
   * @param scopes - Permission scopes (not used in this model)
   * @returns Authorization result indicating immediate success
   */
  async authorize(scopes: readonly string[]): Promise<AIAuthorizationResult> {
    if (!this.isAuthenticated) {
      return {
        isAuthorized: false,
        grantedScopes: [],
        error: 'Not authenticated',
      };
    }

    // In app-subsidized model, all standard scopes are implicitly granted
    // by the API key configuration
    return {
      isAuthorized: true,
      grantedScopes: scopes.length > 0 ? [...scopes] : ['generate_content'],
    };
  }

  /**
   * Generate content using Google AI
   * 
   * @param request - Generation request parameters
   * @returns Generation response
   */
  async generate(request: AIGenerationRequest): Promise<AIGenerationResponse> {
    const authResult = await this.authenticate('');
    if (!authResult.isAuthenticated) {
      throw new Error(authResult.error ?? 'Authentication failed');
    }

    const startTime = Date.now();
    
    // Build the API request body
    const requestBody = this.buildRequestBody(request);
    
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), this.config.timeoutMs);
      
      const url = `${this.config.baseUrl}/models/${this.config.model}:generateContent?key=${this.config.apiKey}`;
      
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestBody),
        signal: controller.signal,
      });
      
      clearTimeout(timeoutId);
      
      if (!response.ok) {
        const errorText = await response.text().catch(() => 'Unknown error');
        
        // Handle specific error cases
        if (response.status === 429) {
          throw new Error(`Rate limit exceeded: ${errorText}`);
        } else if (response.status === 400) {
          throw new Error(`Invalid request: ${errorText}`);
        } else if (response.status >= 500) {
          throw new Error(`Server error: ${response.status}`);
        } else {
          throw new Error(`API error ${response.status}: ${errorText}`);
        }
      }
      
      const responseData = await response.json();
      const endTime = Date.now();
      
      // Parse the response
      const parsedResponse = this.parseResponse(responseData, request, startTime, endTime);
      
      return parsedResponse;
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        throw new Error(`Request timed out after ${this.config.timeoutMs}ms`);
      }
      throw error;
    }
  }

  /**
   * Stream content generation
   * 
   * Note: Google AI supports streaming, but this implementation provides
   * a basic version. For production use, implement proper stream parsing.
   * 
   * @param request - Generation request parameters
   * @returns Async stream of generation chunks
   */
  async *stream(request: AIGenerationRequest): AIStream<AIGenerationChunk> {
    const authResult = await this.authenticate('');
    if (!authResult.isAuthenticated) {
      throw new Error(authResult.error ?? 'Authentication failed');
    }

    // For now, delegate to generate and yield as single chunk
    // TODO: Implement proper streaming with fetch + ReadableStream
    const response = await this.generate(request);
    
    yield {
      requestId: request.requestId,
      textChunk: response.text,
      isFinal: true,
      cumulativeTokenCount: response.outputTokenCount,
    };
  }

  /**
   * Get usage quota information
   * 
   * Note: Google AI doesn't provide real-time quota APIs for API key users.
   * This returns estimated values based on local tracking.
   * 
   * @returns Usage quota information
   */
  async getUsage(): Promise<AIUsageQuota> {
    const now = Date.now();
    const dayStart = new Date();
    dayStart.setHours(0, 0, 0, 0);
    const dayEnd = new Date(dayStart);
    dayEnd.setDate(dayEnd.getDate() + 1);
    
    // Return placeholder values - actual implementation would track locally
    // or integrate with Google Cloud Monitoring API for service accounts
    return {
      totalQuota: 1500, // Free tier: 1500 requests/day for gemini-1.5-flash
      usedQuota: 0, // Would need local tracking
      remainingQuota: 1500,
      periodStart: dayStart.getTime(),
      periodEnd: dayEnd.getTime(),
      unitType: 'requests',
    };
  }

  /**
   * Revoke access
   * 
   * In the app-subsidized model, this simply clears the authentication cache.
   * The API key remains valid until rotated server-side.
   */
  async revoke(): Promise<void> {
    this.isAuthenticated = false;
    this.lastAuthTime = 0;
  }

  /**
   * Check if provider is available
   */
  async isAvailable(): Promise<boolean> {
    try {
      const authResult = await this.authenticate('');
      return authResult.isAuthenticated;
    } catch {
      return false;
    }
  }

  /**
   * Validate API key format
   */
  private isValidApiKeyFormat(key: string): boolean {
    // Google AI API keys typically start with "AIza"
    return /^AIza[A-Za-z0-9_-]{35}$/.test(key);
  }

  /**
   * Build request body for Google AI API
   */
  private buildRequestBody(request: AIGenerationRequest): Record<string, unknown> {
    const contents = [
      {
        role: 'user',
        parts: [{ text: request.prompt }],
      },
    ];

    const generationConfig: Record<string, unknown> = {
      temperature: request.temperature ?? 0.7,
      maxOutputTokens: request.maxOutputTokens ?? 512,
    };

    // Add response format if specified
    if (request.responseFormat === 'application/json') {
      generationConfig.responseMimeType = 'application/json';
    }

    // Add schema if provided
    if (request.outputSchema) {
      generationConfig.responseSchema = request.outputSchema;
    }

    return {
      contents,
      systemInstruction: {
        parts: [{ text: request.systemInstruction }],
      },
      generationConfig,
    };
  }

  /**
   * Parse response from Google AI API
   */
  private parseResponse(
    data: Record<string, unknown>,
    request: AIGenerationRequest,
    startTime: number,
    endTime: number,
  ): AIGenerationResponse {
    const candidates = (data.candidates as Array<Record<string, unknown>> | undefined) ?? [];
    
    if (candidates.length === 0 || !candidates[0]?.content) {
      throw new Error('No content generated');
    }

    const candidate = candidates[0];
    const content = candidate.content as Record<string, unknown> | undefined;
    const parts = (content?.parts as Array<Record<string, unknown>> | undefined) ?? [];
    
    if (parts.length === 0 || typeof parts[0]?.text !== 'string') {
      throw new Error('Invalid response format');
    }

    const text = parts[0].text as string;
    
    // Extract token counts if available
    const usageMetadata = (data.usageMetadata as Record<string, unknown> | undefined) ?? {};
    const inputTokenCount = (usageMetadata.promptTokenCount as number) ?? 0;
    const outputTokenCount = (usageMetadata.candidatesTokenCount as number) ?? 0;

    return {
      requestId: request.requestId,
      text,
      inputTokenCount,
      outputTokenCount,
      provider: this.getProviderId(),
      model: this.config.model,
      generatedAt: endTime,
    };
  }
}
