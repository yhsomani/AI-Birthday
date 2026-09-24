# WishWell — Generic AI Entitlement Architecture
## AI Gateway Specification (Phase 2+)

**Status:** 🔮 PLANNED (Post-Launch Feature)  
**Scope:** Foundational architecture for multi-application AI platform  
**Timeline:** Phase 2+ (8-12 weeks post-Android launch)  
**Audience:** Platform architects, backend engineers

---

## Executive Summary

WishWell currently implements **native-only Gemini drafting** (Android Firebase SDK, zero backend). This document specifies a **generic, reusable AI entitlement architecture** to support:

1. **Multiple AI providers** (Gemini, OpenAI, Anthropic, local models)
2. **Multi-application reuse** (Birthday, other future apps)
3. **Hybrid execution** (on-device + cloud)
4. **Application-controlled subscriptions** (NOT tied to user's personal AI subscription)
5. **Provider abstraction** (swap providers without application code changes)

### The Critical Distinction

```
YOUR APPLICATION SUBSCRIPTION
        ≠
USER'S GOOGLE ACCOUNT
        ≠
USER'S GEMINI PRO SUBSCRIPTION
        ≠
AI PROVIDER ACCOUNT
        ≠
AI PROVIDER BILLING
```

**This separation is essential.** Your application provides AI features through its own subscription model. External AI subscriptions are **never** treated as application entitlements.

---

# SECTION 1: CURRENT STATE (Native-Only Gemini)

## 1.1 What Exists Today

**Android-Only Gemini Drafting:**
```
User taps "Generate Suggestion"
    ↓
AndroidGeminiSuggestionGateway.kt
    ↓
Firebase Generative AI SDK (native client)
    ↓
Cloud Gemini API (device-initiated)
    ↓
3 Suggestions returned to UI
    ↓
User picks one (local-only)
```

**Key Properties:**
- ✅ Works on Android
- ❌ No backend involvement
- ❌ iOS cannot draft (no iOS app)
- ❌ Cannot control entitlement (all users get suggestions)
- ❌ Cannot track usage (native-side only)
- ❌ Cannot implement subscription limits
- ❌ Cannot swap providers

---

## 1.2 Limitations

| Limitation | Impact | Workaround (Today) |
|-----------|--------|-------------------|
| No entitlement gating | All users get AI (free) | None; by design |
| No usage tracking | Cannot bill or limit | None; by design |
| Android-only | iOS users cannot draft | Build iOS app (separate effort) |
| Native-side only | Backend unaware of AI | None; architectural choice |
| Provider-locked | Cannot swap Gemini for OpenAI | Rewrite Android code |
| No hybrid execution | Cannot use on-device + cloud | None; native SDK decides |
| No quota management | Cannot limit monthly tokens | None; device-unlimited |

---

# SECTION 2: PROPOSED ARCHITECTURE

## 2.1 Architectural Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                        │
│  (Birthday Autopilot, Future App A, Future App B, etc.)    │
└────────────────────────┬────────────────────────────────────┘
                         │
         ┌───────────────┴────────────────┐
         │                                │
         ▼                                ▼
┌──────────────────┐         ┌──────────────────┐
│  Subscription    │         │   UI/Frontend    │
│  & Entitlements  │         │  (Mobile/Web)    │
└────────┬─────────┘         └────────┬─────────┘
         │                            │
         └────────────────┬───────────┘
                          ▼
         ┌────────────────────────────────┐
         │  PLATFORM LAYER (Shared)       │
         │                                │
         │  - AI Gateway                  │
         │  - Provider Abstraction        │
         │  - Entitlement Enforcement     │
         │  - Usage Tracking              │
         │  - Policy Engine               │
         │  - Rate Limiting               │
         │  - Cost Control                │
         └────────────┬───────────────────┘
                      │
        ┌─────────────┼─────────────┬──────────────┐
        │             │             │              │
        ▼             ▼             ▼              ▼
    ┌────────┐   ┌────────┐   ┌────────┐    ┌─────────┐
    │ Gemini │   │ OpenAI │   │ Local  │    │ Future  │
    │Adapter │   │Adapter │   │Adapter │    │Adapters │
    └────────┘   └────────┘   └────────┘    └─────────┘
        │             │             │              │
        ▼             ▼             ▼              ▼
   [Cloud API]  [Cloud API]  [Device]      [TBD]
```

---

## 2.2 Separation of Concerns

### A. Identity
```
"Who is the user?"

Options:
- Google OAuth
- Apple ID
- Email/password
- Microsoft
- GitHub
- etc.
```

**Current:** Google OAuth via Firebase Auth ✅

---

### B. Application Subscription
```
"Is the user allowed to use AI features in our application?"

Options:
- FREE (AI disabled)
- PRO (cloud AI + on-device)
- PREMIUM (all models, unlimited)
- ENTERPRISE (custom limits)
```

**Current:** None (all users get native Gemini) ❌

---

### C. AI Provider Authorization
```
"How does the application obtain authorization to call AI providers?"

Options:
- Application-owned API credentials (secure, controlled)
- User-provided credentials (flexible, but user manages secrets)
- OAuth (secure, provider-specific)
- Enterprise credentials (deployment-controlled)
- On-device model (no authorization needed)
```

**Current:** Native Firebase SDK (implicit, application-owned) ✅

---

### D. AI Execution
```
"Where does inference actually happen?"

Options:
- Cloud Gemini
- Cloud OpenAI
- Other cloud provider
- Gemini Nano (on-device)
- ONNX (on-device)
- Other local model
```

**Current:** Cloud Gemini (native-initiated) ✅

---

## 2.3 User Flow (Post-Architecture Implementation)

```
┌─────────────────────────────────────┐
│         First Visit                 │
│                                     │
│   Continue with Google              │
│                                     │
└──────────────┬──────────────────────┘
               │
               ▼
         ┌──────────────────┐
         │ Establish:       │
         │ - Identity       │
         │ - Account        │
         │ - Basic access   │
         └────────┬─────────┘
                  │
                  ▼
         ┌──────────────────┐
         │ AI Features      │
         │ Disabled by      │
         │ default          │
         └────────┬─────────┘
                  │
                  ▼
         ┌─────────────────────────┐
         │ User sees:              │
         │                         │
         │ "AI drafting requires   │
         │  subscription"          │
         │                         │
         │ [Subscribe to AI PRO]   │
         └──────────┬──────────────┘
                    │
        ┌───────────┴────────────┐
        │                        │
        ▼                        ▼
   [Payment]              [Cancelled]
        │                        │
        ▼                        ▼
   [Success]            [Stay Free Tier]
        │                        │
        ▼                        ▼
   AI enabled          AI remains disabled
        │                        │
        ▼                        ▼
   AI Gateway          Entitlement check
   operational         returns 403
```

---

# SECTION 3: AI GATEWAY SPECIFICATION

## 3.1 Core Responsibilities

The **AI Gateway** is a single backend service that:

1. **Authenticates** — Verifies request is from valid user/application
2. **Authorizes** — Checks AI entitlement (subscription valid?)
3. **Validates Entitlement** — Enforces subscription/quota/plan
4. **Applies Policy** — Safety, prompt engineering, model selection
5. **Routes to Provider** — Selects Gemini/OpenAI/Local based on capability/cost/policy
6. **Executes** — Calls provider adapter
7. **Tracks Usage** — Records tokens, cost, latency
8. **Rate Limits** — Enforces RPM, TPM, monthly quotas
9. **Normalizes Response** — Adapts provider-specific format
10. **Logs & Observes** — Structured logging, metrics, tracing

---

## 3.2 API Contract

### Endpoint: `POST /api/v1/ai/generate`

**Request:**
```json
{
  "capability": "text-generation",
  "input": {
    "text": "Birthday message for John",
    "context": {
      "recipientName": "John",
      "relationship": "colleague",
      "tone": "professional"
    }
  },
  "options": {
    "maxTokens": 100,
    "temperature": 0.7,
    "model": "auto"
  }
}
```

**Response (Success):**
```json
{
  "status": "ok",
  "output": {
    "text": "Wishing you a wonderful birthday...",
    "usage": {
      "inputTokens": 42,
      "outputTokens": 28
    }
  },
  "metadata": {
    "provider": "gemini",
    "model": "gemini-2.0-flash",
    "executionPath": "cloud",
    "latency": 245,
    "timestamp": "2026-09-24T10:15:00Z"
  }
}
```

**Response (No Entitlement):**
```json
{
  "status": "error",
  "error": {
    "code": "AI_SUBSCRIPTION_REQUIRED",
    "message": "AI features require active subscription",
    "action": "subscribe"
  }
}
```

**Response (Quota Exceeded):**
```json
{
  "status": "error",
  "error": {
    "code": "QUOTA_EXCEEDED",
    "message": "Monthly token limit reached",
    "remaining": 0,
    "resetAt": "2026-10-24T00:00:00Z"
  }
}
```

---

## 3.3 Internal Flow

```
POST /api/v1/ai/generate
         │
         ▼
    Authenticate (JWT)
         │
         ▼
    Extract: user_id, app_id
         │
         ▼
    GET /entitlements/{user_id}
         │
    ┌────┴────┐
    │          │
   NO        YES
    │          │
    ▼          ▼
 403       Check plan
           subscription
           status
           │
      ┌────┴────┐
     NO        YES
      │          │
      ▼          ▼
   403      Check usage
            quota
            │
       ┌────┴────┐
      NO        YES
       │          │
       ▼          ▼
    403       Apply policy
              (safety, prompt,
               model selection)
              │
              ▼
            Route to
            provider
            │
        ┌───┴───┬───┬────┐
        │       │   │    │
        ▼       ▼   ▼    ▼
      Gemini OpenAI Local Future
        │       │    │    │
        └───┬───┴────┴────┘
            │
            ▼
        Execute
            │
            ▼
        Track usage
            │
            ▼
        Normalize response
            │
            ▼
        Return to client
```

---

## 3.4 Entitlement Service Contract

The **Entitlement Service** exposes:

### `GET /entitlements/{user_id}`

**Response (Active Subscription):**
```json
{
  "userId": "user123",
  "aiEnabled": true,
  "plan": "pro",
  "status": "active",
  "monthlyLimitTokens": 100000,
  "usedTokensThisMonth": 27340,
  "remaining": 72660,
  "resetDate": "2026-10-24",
  "allowedModels": ["gemini-2.0-flash", "gemini-1.5-pro"],
  "allowedProviders": ["gemini"],
  "allowedCapabilities": ["text-generation", "embedding"],
  "executionPaths": ["cloud", "device"]
}
```

**Response (No Subscription):**
```json
{
  "userId": "user456",
  "aiEnabled": false,
  "plan": null,
  "status": "inactive",
  "reason": "no_subscription"
}
```

**Response (Expired Subscription):**
```json
{
  "userId": "user789",
  "aiEnabled": false,
  "plan": "pro",
  "status": "expired",
  "expirationDate": "2026-09-20"
}
```

---

## 3.5 Provider Adapter Interface

Every provider implements:

```typescript
interface AIProvider {
  // Core execution
  generate(request: GenerationRequest): Promise<GenerationResponse>;
  stream(request: GenerationRequest): AsyncIterable<StreamChunk>;
  embed(request: EmbeddingRequest): Promise<EmbeddingResponse>;

  // Utility
  countTokens(text: string): Promise<number>;
  listModels(): Promise<ModelInfo[]>;
  
  // Health
  healthCheck(): Promise<HealthStatus>;
}

interface GenerationRequest {
  model: string;
  input: {
    text: string;
    context?: Record<string, any>;
  };
  options?: {
    maxTokens?: number;
    temperature?: number;
    topP?: number;
  };
}

interface GenerationResponse {
  text: string;
  usage: {
    inputTokens: number;
    outputTokens: number;
  };
  finishReason: 'stop' | 'length' | 'error';
}
```

---

## 3.6 Policy Engine

The **Policy Engine** applies business rules:

```typescript
interface AIPolicy {
  // Subscription rules
  requireSubscription: boolean;
  allowedPlans: string[]; // ['pro', 'premium']

  // Capability rules
  modelWhitelist?: string[]; // ['gemini-2.0-flash']
  modelBlacklist?: string[];
  capabilityRequirements?: Record<string, string[]>;

  // Quota rules
  monthlyTokenLimit?: number;
  dailyTokenLimit?: number;
  requestsPerMinute?: number;
  tokensPerMinute?: number;

  // Execution rules
  preferredExecutionPath?: 'cloud' | 'device' | 'hybrid';
  allowCloudFallback?: boolean;
  maxLatency?: number;

  // Safety rules
  enableSafetyFilters?: boolean;
  allowedTones?: string[];
  blockedTopics?: string[];

  // Cost rules
  costLimit?: number; // $/month
  stopOnCostLimit?: boolean;

  // Routing rules
  primaryProvider?: string; // 'gemini'
  fallbackProviders?: string[];
  loadBalancing?: 'round-robin' | 'least-cost' | 'fastest';
}
```

---

# SECTION 4: SUBSCRIPTION MODELS

## 4.1 Tier Structure

### FREE
```
AI Features:     Disabled
Cost:            $0
Use Case:        Basic app functionality
```

### AI PRO
```
AI Features:     Enabled
Cloud AI:        Gemini 2.0 Flash
On-Device AI:    Gemini Nano (where available)
Monthly Tokens:  100,000
Cost:            ₹499/month or $5.99/month
Use Case:        Power users
```

### AI PREMIUM
```
AI Features:     Enabled
Cloud AI:        Gemini 2.0 Pro (advanced reasoning)
On-Device AI:    Gemini Nano + others
Monthly Tokens:  500,000
Cost:            ₹999/month or $12.99/month
Use Case:        Professional users, agencies
```

### ENTERPRISE
```
AI Features:     Custom
Cloud AI:        All models available
On-Device AI:    All models
Monthly Tokens:  Custom
Cost:            Custom
Use Case:        Organizations, high-volume
```

---

## 4.2 Subscription Flow

```
                    USER
                     │
                     ▼
              [Free Tier]
                     │
          AI features disabled
                     │
                     ▼
          User taps "Generate Suggestion"
                     │
                     ▼
          AI Gateway checks entitlement
                     │
         ┌───────────┴───────────┐
         │                       │
       NO                       YES
         │                       │
         ▼                       ▼
    403 Response          Execute request
         │                       │
         ▼                       ▼
    Frontend shows:         Return result
    "AI requires               │
     subscription"             ▼
         │              Track usage
         ▼
    [Subscribe]
         │
         ▼
    Stripe payment
         │
    ┌────┴────┐
   NO        YES
    │          │
    ▼          ▼
 Error    Payment success
           │
           ▼
       Webhook:
    "subscription.created"
           │
           ▼
    Entitlement service:
    Set aiEnabled = true
           │
           ▼
    Frontend: "Subscription active"
           │
           ▼
    User retries AI request
           │
           ▼
    SUCCESS ✅
```

---

## 4.3 Usage Billing

**Billing Calculation:**
```
Monthly Invoice = Base Subscription + Token Overage

Base Subscription = ₹499 (AI PRO)

Token Overage = MAX(0, (tokens_used - monthly_limit) * rate)
              = MAX(0, (150000 - 100000) * ₹0.00001)
              = MAX(0, 50000 * ₹0.00001)
              = ₹0.50

Total = ₹499 + ₹0.50 = ₹499.50
```

**Rate Table:**
```
Provider    Model              Rate (₹ per 1K tokens)
─────────────────────────────────────────────────────
Gemini      2.0 Flash          ₹0.01 (input) / ₹0.04 (output)
Gemini      2.0 Pro            ₹0.01 (input) / ₹0.04 (output)
Gemini      Nano (on-device)   Free
OpenAI      GPT-4o             ₹1.00 (input) / ₹4.00 (output)
Anthropic   Claude 3.5 Sonnet  ₹0.80 (input) / ₹2.40 (output)
```

---

# SECTION 5: HYBRID EXECUTION

## 5.1 On-Device + Cloud Strategy

```
                 AI Request
                     │
                     ▼
          Entitlement check (✅ pass)
                     │
                     ▼
          Capability analysis
                     │
         ┌───────────┴───────────────┐
         │                           │
    Simple task             Complex task
    (summarize,            (reasoning,
     classify,             planning,
     simple gen)           analysis)
         │                           │
         ▼                           ▼
    Device capable?            Always cloud
    ┌────┬────┐                     │
   YES   NO   │                     │
    │    │    │                     │
    ▼    ▼    │                     │
  Nano Cloud  │                    Cloud
    │    │    │                     │
    └────┴────┴─────────────────────┘
             │
             ▼
         Result
```

**Decision Tree:**

| Task | Device Available | Device Performance | Choice |
|------|-------------------|-------------------|--------|
| Summarize (500 words) | Yes | Adequate | Nano (device) |
| Summarize (5000 words) | Yes | Slow | Cloud (fallback) |
| Classify (sentiment) | Yes | Good | Nano (device) |
| Complex reasoning | Yes | Unsupported | Cloud (required) |
| Complex reasoning | No | — | Cloud (required) |
| Offline request | Yes | — | Nano (only option) |

---

## 5.2 Cost Optimization via Hybrid Execution

```
Monthly Usage: 150,000 tokens
AI PRO limit: 100,000 tokens

Without Hybrid:
- All 150,000 tokens → Cloud Gemini
- Cost: ₹499 + (50,000 × ₹0.00001) = ₹499.50
- Overage: 50%

With Hybrid (60% device, 40% cloud):
- 60,000 tokens → Nano (device, free)
- 40,000 tokens → Cloud Gemini
- Cost: ₹499 + 0 (within 100K limit) = ₹499
- Overage: 0%

Savings: ₹0.50/month × 12 = ₹6/year (minimal)
But: Reduced cloud costs for WishWell (significant if scaled)
```

---

# SECTION 6: PROVIDER ADAPTERS

## 6.1 Gemini Adapter

```typescript
// backend/functions/src/providers/geminiAdapter.ts

export class GeminiProvider implements AIProvider {
  private client: GoogleGenerativeAI;
  
  constructor(apiKey: string) {
    this.client = new GoogleGenerativeAI(apiKey);
  }

  async generate(req: GenerationRequest): Promise<GenerationResponse> {
    const model = this.client.getGenerativeModel({
      model: req.model || 'gemini-2.0-flash',
    });

    const result = await model.generateContent({
      contents: [
        {
          role: 'user',
          parts: [{ text: req.input.text }],
        },
      ],
      generationConfig: {
        maxOutputTokens: req.options?.maxTokens || 100,
        temperature: req.options?.temperature || 0.7,
      },
    });

    const text = result.response.text();
    const usage = result.response.usageMetadata;

    return {
      text,
      usage: {
        inputTokens: usage?.promptTokenCount || 0,
        outputTokens: usage?.candidatesTokenCount || 0,
      },
      finishReason: result.response.candidates?.[0]?.finishReason || 'stop',
    };
  }

  async countTokens(text: string): Promise<number> {
    const model = this.client.getGenerativeModel({
      model: 'gemini-2.0-flash',
    });
    const result = await model.countTokens(text);
    return result.totalTokens;
  }

  async listModels(): Promise<ModelInfo[]> {
    // Hardcode available Gemini models
    return [
      {
        id: 'gemini-2.0-flash',
        displayName: 'Gemini 2.0 Flash',
        inputCostPer1kTokens: 0.01,
        outputCostPer1kTokens: 0.04,
      },
      {
        id: 'gemini-2.0-pro',
        displayName: 'Gemini 2.0 Pro',
        inputCostPer1kTokens: 0.01,
        outputCostPer1kTokens: 0.04,
      },
    ];
  }

  async healthCheck(): Promise<HealthStatus> {
    try {
      const model = this.client.getGenerativeModel({
        model: 'gemini-2.0-flash',
      });
      await model.countTokens('health check');
      return { status: 'ok', latency: 0 };
    } catch (e) {
      return { status: 'error', error: e.message };
    }
  }
}
```

---

## 6.2 OpenAI Adapter

```typescript
// backend/functions/src/providers/openaiAdapter.ts

export class OpenAIProvider implements AIProvider {
  private client: OpenAI;
  
  constructor(apiKey: string) {
    this.client = new OpenAI({ apiKey });
  }

  async generate(req: GenerationRequest): Promise<GenerationResponse> {
    const response = await this.client.chat.completions.create({
      model: req.model || 'gpt-4o',
      messages: [
        {
          role: 'user',
          content: req.input.text,
        },
      ],
      max_tokens: req.options?.maxTokens || 100,
      temperature: req.options?.temperature || 0.7,
    });

    const text = response.choices[0]?.message?.content || '';
    const usage = response.usage;

    return {
      text,
      usage: {
        inputTokens: usage?.prompt_tokens || 0,
        outputTokens: usage?.completion_tokens || 0,
      },
      finishReason: response.choices[0]?.finish_reason || 'stop',
    };
  }

  async countTokens(text: string): Promise<number> {
    // Use tiktoken for token counting
    const enc = encoding_for_model('gpt-4o');
    return enc.encode(text).length;
  }

  async listModels(): Promise<ModelInfo[]> {
    return [
      {
        id: 'gpt-4o',
        displayName: 'GPT-4o',
        inputCostPer1kTokens: 1.0,
        outputCostPer1kTokens: 4.0,
      },
    ];
  }

  async healthCheck(): Promise<HealthStatus> {
    try {
      const response = await this.client.models.list();
      return { status: 'ok', latency: 0 };
    } catch (e) {
      return { status: 'error', error: e.message };
    }
  }
}
```

---

## 6.3 Local (On-Device) Adapter

```typescript
// android/app/src/main/java/.../ai/LocalAIAdapter.kt

class LocalAIAdapter : AIProvider {
  private lateinit var model: GenerativeModel
  
  override suspend fun generate(request: GenerationRequest): GenerationResponse {
    // Use Gemini Nano on Android
    val model = GenerativeModel(
      modelName = "gemini-1.5-flash",
      client = GenerativeAI()
    )
    
    val result = model.generateContent(request.input.text)
    
    return GenerationResponse(
      text = result.text ?: "",
      usage = UsageData(
        inputTokens = 0, // Not available on-device
        outputTokens = 0
      ),
      finishReason = "stop"
    )
  }

  override suspend fun countTokens(text: String): Int {
    // On-device: estimate or return 0
    return text.split("\\s+".toRegex()).size / 4
  }

  override suspend fun healthCheck(): HealthStatus {
    return try {
      HealthStatus(status = "ok")
    } catch (e: Exception) {
      HealthStatus(status = "error", error = e.message)
    }
  }
}
```

---

# SECTION 7: INTEGRATION WITH WISHWELL

## 7.1 Current Architecture (Android Native-Only)

```
LiveMessageScreen.tsx
    ↓
generateSuggestions() user intent
    ↓
BirthdayNativeAdapter.generateSuggestions()
    ↓
Native: generate-suggestions intent
    ↓
AndroidGeminiSuggestionGateway.kt
    ↓
Firebase SDK → Cloud Gemini
    ↓
Suggestions returned to UI
```

---

## 7.2 Proposed Architecture (Backend Gateway)

```
┌─────────────────────────────────────────┐
│      LiveMessageScreen.tsx              │
│  "Generate Suggestion" button           │
└──────────────────┬──────────────────────┘
                   │
         ┌─────────┴──────────┐
         │                    │
    (User has               (User has
    subscription)          subscription
    & on-device AI         & no device AI)
         │                    │
         ▼                    ▼
   Try device           Call backend:
   (Nano first)         POST /api/v1/ai/generate
         │                    │
    ┌────┴────┐               ▼
   NO        YES         Backend:
    │         │          1. Check entitlement
    ▼         ▼          2. Check quota
 Cloud     Return       3. Route to provider
 Gemini    (fast)       4. Execute
    │                   5. Track usage
    └────────┬──────────┘
             ▼
        Display suggestions
        to user
```

---

## 7.3 Integration Steps (Phase 2)

### Step 1: Add Subscription Service
```typescript
// backend/functions/src/services/subscriptionService.ts
export const checkAIEntitlement = async (uid: string) => {
  const user = await db.collection('users').doc(uid).get();
  const subscription = user.data()?.aiSubscription;
  
  if (!subscription) {
    return { enabled: false, reason: 'no_subscription' };
  }
  
  if (subscription.expiresAt < Date.now()) {
    return { enabled: false, reason: 'expired' };
  }
  
  return {
    enabled: true,
    plan: subscription.plan, // 'pro', 'premium'
    monthlyLimit: subscriptionPlans[subscription.plan].monthlyTokens,
    used: await trackUsage.getMonthlyUsage(uid),
  };
};
```

### Step 2: Add AI Gateway Cloud Function
```typescript
// backend/functions/src/functions/aiGateway.ts
export const generateAI = onCall(
  authRequired,
  async (req: CallableRequest<AIGenerateRequest>) => {
    const uid = req.auth.uid;
    
    // Check entitlement
    const entitlement = await checkAIEntitlement(uid);
    if (!entitlement.enabled) {
      throw new HttpsError('failed-precondition', 'AI_SUBSCRIPTION_REQUIRED');
    }
    
    // Check quota
    const used = entitlement.used;
    const limit = entitlement.monthlyLimit;
    if (used >= limit) {
      throw new HttpsError('resource-exhausted', 'QUOTA_EXCEEDED');
    }
    
    // Route to provider
    const provider = selectProvider(entitlement.plan);
    const response = await provider.generate(req.data);
    
    // Track usage
    await trackUsage.recordRequest(uid, response.usage.outputTokens);
    
    return response;
  }
);
```

### Step 3: Update Frontend (LiveMessageScreen)
```typescript
// src/features/live/LiveMessageScreen.tsx
const generateSuggestions = async () => {
  try {
    // Call backend instead of native
    const response = await callCloudFunction('generateAI', {
      capability: 'text-generation',
      input: {
        text: `Write birthday message for ${recipientName}`,
        context: { tone: selectedTone, relationship },
      },
    });
    
    setSuggestions(response.suggestions);
  } catch (error) {
    if (error.code === 'AI_SUBSCRIPTION_REQUIRED') {
      showSubscriptionRequired();
    } else if (error.code === 'QUOTA_EXCEEDED') {
      showQuotaExceeded();
    }
  }
};
```

### Step 4: Add Subscription UI
```typescript
// src/features/subscription/AISubscriptionScreen.tsx
export const AISubscriptionScreen = () => {
  return (
    <Screen>
      <Text>{'AI Features'}</Text>
      
      {/* Pricing tier */}
      <PricingCard
        title={'AI PRO'}
        price={'₹499/month'}
        features={['Cloud AI', '100K tokens/month']}
        onSubscribe={handleSubscribe}
      />
      
      {/* Usage display (if subscribed) */}
      {subscription?.enabled && (
        <UsageIndicator
          used={usage.used}
          limit={subscription.monthlyLimit}
          resetDate={subscription.resetDate}
        />
      )}
    </Screen>
  );
};
```

### Step 5: Update Stripe Integration
```typescript
// backend/functions/src/services/stripeService.ts
export const handleSubscriptionCreated = async (event) => {
  const { customer_id, subscription_id } = event.data.object;
  const uid = await getUidFromStripeCustomerId(customer_id);
  
  // Activate AI entitlement
  await db.collection('users').doc(uid).update({
    aiSubscription: {
      provider: 'stripe',
      subscriptionId: subscription_id,
      plan: 'pro', // from product metadata
      status: 'active',
      startedAt: Timestamp.now(),
      expiresAt: Timestamp.now() + 30 days,
    },
  });
};
```

---

# SECTION 8: MULTI-APPLICATION ARCHITECTURE

## 8.1 Shared AI Platform

Once architecture is solid, reuse across multiple applications:

```
┌──────────────────────────────────────────────────────┐
│           Multiple Applications                      │
├──────────────────────────────────────────────────────┤
│                                                       │
│  Birthday Autopilot    │    Future App A    │ App B │
│  - AI Drafting         │    - AI Search     │ - AI  │
│  - AI Suggestions      │    - AI Summaries  │   ...  │
│                        │    - ...           │       │
│                                                       │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
        ┌──────────────────────────┐
        │  Shared AI Platform      │
        │                          │
        │  - Identity Service      │
        │  - Entitlements Service  │
        │  - AI Gateway            │
        │  - Provider Adapters     │
        │  - Usage Tracking        │
        │  - Policy Engine         │
        └──────────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
     Gemini      OpenAI       Local
```

---

## 8.2 Per-Application Configuration

Each application has:

```typescript
interface ApplicationAIConfig {
  appId: string;
  enabledProviders: string[];    // ['gemini', 'local']
  enabledModels: string[];       // ['gemini-2.0-flash']
  defaultModel: string;          // 'gemini-2.0-flash'
  executionPath: 'cloud' | 'device' | 'hybrid';
  monthlyTokenLimit: number;
  maxTokensPerRequest: number;
  safetyFilters: boolean;
  allowedTones: string[];
}
```

---

# SECTION 9: MIGRATION PLAN

## 9.1 Phase 1: Current State (v0.1)

**Status:** ✅ Complete  
**Features:**
- Native Gemini drafting (Android)
- No entitlements
- No usage tracking
- No backend involvement

---

## 9.2 Phase 2: Backend Gateway (Weeks 5-12)

**Goal:** Introduce backend AI Gateway with entitlements

**Deliverables:**
1. AI Gateway Cloud Function
2. Subscription Service + Entitlement checks
3. AI PRO tier on Stripe
4. Frontend subscription screen
5. New `generateAI` callable (parallel native → backend)
6. Usage tracking in Firestore

**Testing:**
- Unit tests: Gateway logic, entitlement checks
- Integration tests: End-to-end with each provider
- E2E: Subscription flow, quota limits
- Security: Abuse prevention, rate limiting

**Backwards Compatibility:**
- Keep native gateway for offline/fallback
- Prefer backend for cloud (entitlement-aware)
- Frontend tries backend first, falls back to native

---

## 9.3 Phase 3: Hybrid Execution (Weeks 13+)

**Goal:** Combine on-device + cloud for cost optimization

**Deliverables:**
1. Device capability detection
2. Hybrid execution policy engine
3. Fallback logic (device unavailable → cloud)
4. Cost tracking per execution path
5. Dashboard showing cloud vs. device usage

---

## 9.4 Phase 4: Multi-Application (Weeks 20+)

**Goal:** Generalize platform for multiple applications

**Deliverables:**
1. Extract AI Gateway into shared service
2. Per-application config management
3. Shared entitlements infrastructure
4. Multi-tenant cost tracking
5. Provider management UI

---

# SECTION 10: IMPLEMENTATION TIMELINE

| Week | Component | Tasks | Effort |
|------|-----------|-------|--------|
| **Phase 2** | | | |
| 1-2 | AI Gateway | Design, schema, types | 3 days |
| 2-3 | Subscription Service | Entitlements, quota | 3 days |
| 3-4 | Stripe Integration | Webhooks, billing | 2 days |
| 4-5 | Frontend UI | Subscription screen, gating | 3 days |
| 5-6 | Adapters | Gemini, OpenAI, Local | 4 days |
| 6-7 | Testing | Unit, integration, E2E | 5 days |
| 7-8 | Launch Prep | Docs, monitoring, runbooks | 2 days |
| | **Total** | | **8 weeks** |
| **Phase 3** | | | |
| 1-2 | Device Detection | Capability checks | 2 days |
| 2-3 | Hybrid Policy | Execution routing | 3 days |
| 3-4 | Fallback Logic | Error handling, retries | 2 days |
| 4 | Testing & Launch | | 3 days |
| | **Total** | | **3 weeks** |

---

# SECTION 11: OPEN QUESTIONS

1. **Provider Selection:** Start with Gemini-only or multi-provider (Gemini + OpenAI)?
2. **On-Device Support:** Prioritize Gemini Nano on Android? When?
3. **Pricing Model:** Simple tiers (PRO, PREMIUM) or usage-based?
4. **Free Tier:** Allow free users to access AI with limits?
5. **Provider Switching:** Allow users to pick preferred provider?
6. **Custom Models:** Support fine-tuned models?
7. **Compliance:** GDPR/privacy implications of usage tracking?

---

# SECTION 12: SUCCESS CRITERIA

✅ **Phase 2 Complete When:**
- Backend AI Gateway operational
- Entitlements enforced (subscription required)
- Usage tracked and reported
- Stripe subscription working
- 95%+ test coverage
- Supports 100+ concurrent requests
- Latency <500ms for most requests

✅ **Phase 3 Complete When:**
- On-device execution working on supported devices
- Hybrid routing reduces cloud tokens by 30%+
- Fallback logic handles all error scenarios
- Cost/performance dashboard functional

✅ **Phase 4 Complete When:**
- New application can reuse AI platform
- Per-application configuration working
- Multi-tenant cost tracking accurate
- Supports 3+ applications simultaneously

---

# CONCLUSION

This generic AI entitlement architecture enables:

1. **Reusability** — Multiple applications share infrastructure
2. **Flexibility** — Swap providers without code changes
3. **Cost Control** — Entitlements + quotas + hybrid execution
4. **Scalability** — On-device fallback + cloud flexibility
5. **Sustainability** — Business model supports AI costs

**The critical distinction:** Application subscriptions are **independent** from user's personal AI subscriptions. This keeps WishWell's business model simple and decoupled from external provider policies.

**Start:** Phase 2 (Weeks 5-12 post-launch)  
**Impact:** Enable sustainable AI features with clear monetization  
**Timeline:** 8 weeks → Phase 3/4 as business demand grows

---

**Document Status:** 🔮 PLANNED  
**Next Review:** Post-Android launch (Week 5)  
**Owner:** Platform Architecture  
**Feedback Channel:** Engineering team via GitHub discussions
