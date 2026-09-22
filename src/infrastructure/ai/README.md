# AI Gateway Architecture

## Overview

This document describes the AI Gateway architecture implemented for the WishWell (Birthday Autopilot) application. The architecture follows the principles outlined in the SSOT.md and provides a provider-agnostic, centralized approach to AI operations.

## Architectural Principles

### 1. Single Login Experience ✅

The AI Gateway is designed to work seamlessly with the existing Google authentication flow:

```
User → Continue with Google → [AI Auto-Available] → Application Ready
```

No separate AI authorization step is required when using the app-subsidized billing model.

### 2. Clear Separation of Concerns

```
User Authentication (Google/Firebase)
    ≠
AI API Authorization (Provider-specific)
    ≠
AI Subscription Entitlement (App-managed or User-owned)
    ≠
API Billing (App or User)
```

### 3. Provider Agnosticism

The architecture supports multiple AI providers through a common adapter interface:

```typescript
AIProviderAdapter
├── authenticate()
├── authorize()
├── generate()
├── stream()
├── getUsage()
└── revoke()
```

## Components

### AIGateway (`src/infrastructure/ai/AIGateway.ts`)

The central orchestration layer that manages:

- **Session Lifecycle**: Initialization, validation, expiry tracking
- **Authorization State**: Tracking which users have authorized AI operations
- **Retry Logic**: Configurable exponential backoff for transient failures
- **Usage Metrics**: Collection and reporting of AI consumption data
- **Error Handling**: Classification and appropriate error propagation
- **Request Validation**: Pre-flight checks before delegating to providers

Key features:
- Automatic retry with configurable backoff (default: 3 attempts, 2x multiplier)
- Request timeout protection (default: 30 seconds)
- Usage metrics buffer (last 1000 requests)
- Session management per user

### AIProviderAdapter Interface (`src/application/ports/AIProviderPort.ts`)

The contract that all AI provider implementations must follow:

```typescript
interface AIProviderAdapter {
  authenticate(userToken: string): Promise<AIAuthResult>;
  authorize(scopes: string[]): Promise<AIAuthorizationResult>;
  generate(request: AIGenerationRequest): Promise<AIGenerationResponse>;
  stream(request: AIGenerationRequest): AIStream<AIGenerationChunk>;
  getUsage(): Promise<AIUsageQuota>;
  revoke(): Promise<void>;
  getProviderId(): string;
  isAvailable(): Promise<boolean>;
}
```

### GoogleAIProviderAdapter (`src/infrastructure/ai/providers/GoogleAIProviderAdapter.ts`)

Reference implementation for Google's Generative AI API using the **app-subsidized model**:

**Characteristics:**
- App-managed API key (stored securely on backend)
- No user authorization required
- Free tier limits apply (1500 requests/day for gemini-1.5-flash)
- Rate limiting enforced per user
- Cannot access user's consumer Gemini/Google One AI Premium subscription

**Important Legal Note:**
> Google's documentation does NOT support using a consumer's existing Gemini/Google One AI Premium subscription for third-party API calls. The architecture assumes app-managed billing.

## Billing Models

### Model 1: App-Subsidized (Implemented ✅)

```
Application pays for all AI usage
    ↓
Users get free AI features within limits
    ↓
No user API keys required
    ↓
Simplest UX - AI available immediately after Google login
```

**Pros:**
- Best user experience
- No additional authorization flows
- Predictable costs at small scale

**Cons:**
- App bears all costs
- Must implement rate limiting to prevent abuse
- Free tier limits may constrain heavy users

### Model 2: User-Owned Quota (Future 🔮)

```
User provides their own API credentials
    ↓
User's quota/billing applies
    ↓
Separate OAuth or API key configuration required
    ↓
More complex UX but unlimited potential
```

**Requirements:**
- Separate OAuth consent screen for AI scopes
- Secure credential storage (encrypted)
- Per-user quota tracking
- Fallback to app-subsidized for users without credentials

**Status:** Not implemented. Only pursue if:
1. AI provider explicitly supports consumer subscription sharing
2. Legal review confirms compliance with provider ToS
3. User research indicates demand for this model

## Usage Example

```typescript
import { AIGateway, GoogleAIProviderAdapter, AuthorizationStatus } from '@/infrastructure/ai';

// Initialize gateway with Google AI provider
const gateway = new AIGateway({
  defaultProvider: new GoogleAIProviderAdapter({
    apiKey: process.env.GOOGLE_AI_API_KEY, // From secure backend
    model: 'gemini-1.5-flash',
  }),
  enableUsageTracking: true,
  onUsageMetric: async (metric) => {
    // Log to analytics or backend
    await logAIMetric(metric);
  },
});

// Initialize session after user logs in with Google
await gateway.initializeSession(userId, firebaseIdToken);

// Check authorization (immediately succeeds in app-subsidized model)
const authStatus = await gateway.checkAuthorization(userId);
if (authStatus !== AuthorizationStatus.AUTHORIZED) {
  await gateway.requestAuthorization(userId, ['generate_content']);
}

// Generate content with automatic retry
const response = await gateway.generate(userId, {
  requestId: crypto.randomUUID(),
  systemInstruction: 'Create birthday greetings.',
  prompt: 'Write a warm birthday message in English.',
  temperature: 0.7,
  maxOutputTokens: 256,
});

console.log(response.text); // Generated greeting
console.log(response.outputTokenCount); // Token usage
```

## Integration with Existing Architecture

### Current State (v0.1.0)

The existing Android implementation uses `AndroidGeminiSuggestionGateway.kt` which directly integrates with Firebase AI SDK. This is a client-side only implementation with:

- Operational gate for policy enforcement
- Rate guard (max 8 retained rate scopes)
- Provenance registry for audit trail
- 15-second timeout
- Never reachable from send workers

### Future Integration Path

**Phase 1 (Pre-Launch):** Keep current client-side Firebase AI implementation
- Add AIGateway abstraction for future flexibility
- Document app covers AI costs (free tier limits)
- Implement server-side rate limiting per user/installation
- No changes to user onboarding flow

**Phase 2 (Post-Launch):** Provider flexibility
- Implement AIProviderAdapter for Vertex AI (enterprise customers)
- Add usage tracking and quota management dashboard
- Consider premium tier with higher AI limits

**Phase 3 (Future):** User-owned quota (only if legally supported)
- Research if any AI provider supports consumer subscription sharing
- Implement OAuth flow if/when providers support it
- Add billing integration

## Security Considerations

### API Key Management

⚠️ **Never expose API keys in client-side code**

Correct approach:
```
Client (React Native / Android)
    ↓
Backend Callable Function (Firebase Cloud Functions)
    ↓
AIGateway (with API key from secrets manager)
    ↓
AI Provider API
```

### Rate Limiting

Protect against quota exhaustion:
- Per-user daily limits (e.g., 50 generations/day)
- Per-installation limits
- Global circuit breaker when approaching free tier cap

### Data Privacy

- AI prompts must exclude PII (person names, phone numbers, etc.)
- Provider text never logged or persisted
- Generation unreachable from send workers (per SSOT.md)

## Error Handling

The gateway classifies errors into retryable and non-retryable categories:

**Retryable:**
- Network timeouts
- HTTP 429 (rate limit) with backoff
- HTTP 5xx (server errors)

**Non-Retryable:**
- Authentication failures
- Invalid request format
- Quota exceeded (403)
- Client errors (4xx except 429)

## Testing Strategy

```typescript
// Unit tests for AIGateway
describe('AIGateway', () => {
  it('initializes session with valid credentials');
  it('rejects session with invalid credentials');
  it('retries on transient network errors');
  it('fails fast on authentication errors');
  it('tracks usage metrics correctly');
  it('enforces request timeouts');
});

// Integration tests with mock provider
describe('GoogleAIProviderAdapter', () => {
  it('authenticates with valid API key');
  it('generates content matching schema');
  it('respects rate limits');
  it('handles streaming responses');
});
```

## Migration Guide

### From Direct Provider Calls

Before:
```typescript
const response = await fetch(`https://api.provider.com/generate?key=${apiKey}`, {
  method: 'POST',
  body: JSON.stringify({ prompt }),
});
```

After:
```typescript
const response = await gateway.generate(userId, {
  requestId: crypto.randomUUID(),
  prompt,
  systemInstruction: '...',
});
```

### From Client-Side SDK

Before (Android):
```kotlin
val ai = FirebaseAI.getInstance(app, GenerativeBackend.vertexAI("global"), true)
val model = ai.generativeModel(modelName = "gemini-3.5-flash", ...)
val response = model.generateContent(prompt)
```

After (unified across platforms):
```typescript
const response = await gateway.generate(userId, {
  requestId: crypto.randomUUID(),
  prompt,
  systemInstruction: '...',
});
```

## Compliance Checklist

- [x] AI provider terms reviewed (Google AI Studio ToS)
- [x] Consumer subscription limitations documented
- [x] App-subsidized model selected for launch
- [x] Rate limiting implemented to prevent abuse
- [x] Privacy boundaries maintained (no PII in prompts)
- [ ] Usage monitoring dashboard (future)
- [ ] Cost alerting configured (future)

## References

- [SSOT.md](../SSOT.md) - Single Source of Truth
- [AndroidGeminiSuggestionGateway.kt](../../android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/AndroidGeminiSuggestionGateway.kt) - Current Android implementation
- [gemini-prompt-policy-v2.json](../../contracts/gemini-prompt-policy-v2.json) - Prompt policy contract
- Google AI Studio Documentation: https://ai.google.dev/
- Google AI Pricing: https://ai.google.dev/pricing
