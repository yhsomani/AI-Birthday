# WishWell — AI Gateway Integration with Current SSOT
## How the Generic Architecture Fits Into Existing System

**Purpose:** Show how the proposed AI Gateway architecture integrates with WishWell's current native-only Gemini implementation  
**Audience:** Technical leads deciding on Phase 2 direction

---

## Current State vs. Proposed State

### TODAY (Phase 1)

```
User taps "Generate Suggestion"
    ↓
Android native: AndroidGeminiSuggestionGateway
    ↓
Firebase Generative AI SDK (client-initiated)
    ↓
Cloud Gemini
    ↓
Suggestions returned (no entitlement, no quota, no backend)
```

**Characteristics:**
- ✅ Works great for Android
- ✅ No server latency
- ✅ Device-owned (can work offline if cached)
- ❌ Cannot gate behind subscription
- ❌ Cannot track usage
- ❌ Cannot implement quotas
- ❌ iOS cannot use
- ❌ Cannot swap providers

---

### PROPOSED (Phase 2+)

```
User taps "Generate Suggestion"
    ↓
Check: Does user have AI subscription?
    ↓
YES → Call backend: POST /api/v1/ai/generate
    │       ↓
    │   Backend AI Gateway
    │       ↓
    │   Check entitlement (✓)
    │   Check quota (✓)
    │   Route to provider (Gemini/OpenAI/Local)
    │   Execute
    │   Track usage
    │   Return result
    │
NO → Show "Subscribe to AI PRO"
         [Subscribe] button
```

**Characteristics:**
- ✅ Works on mobile + web
- ✅ Subscription-gated
- ✅ Usage tracked
- ✅ Quotas enforced
- ✅ Multi-provider support
- ✅ On-device fallback option
- ✅ Cost-controlled
- ✅ Extensible to future apps
- ⚠️ Network latency (~200-500ms)

---

## Integration Points

### 1. Frontend (No Breaking Changes)

**Current Code:**
```typescript
// src/features/live/LiveMessageScreen.tsx
const generateSuggestions = async () => {
  const result = await port.generateSuggestions({
    enrollmentId: contactId,
    recipientName,
    tone: selectedTone,
  });
  setSuggestions(result.value?.suggestions || []);
};
```

**Phase 2 Enhancement (Backwards Compatible):**
```typescript
const generateSuggestions = async () => {
  try {
    // Try new backend first (if user has subscription)
    const result = await callCloudFunction('generateAI', {
      capability: 'text-generation',
      input: {
        text: `Write birthday message for ${recipientName}`,
        context: { tone: selectedTone, relationship },
      },
    });
    setSuggestions(result.suggestions);
  } catch (error) {
    if (error.code === 'AI_SUBSCRIPTION_REQUIRED') {
      // Show subscription prompt
      navigation.navigate('SubscribeToAIPro');
    } else {
      // Fallback to native (device capability available)
      const result = await port.generateSuggestions({
        enrollmentId: contactId,
        recipientName,
        tone: selectedTone,
      });
      setSuggestions(result.value?.suggestions || []);
    }
  }
};
```

**No UI changes needed.** Same "Generate Suggestion" button; just now it:
1. Checks entitlement (backend)
2. Falls back to native if entitlement fails
3. Shows subscription prompt as needed

---

### 2. Backend (New Cloud Function)

**New File: `backend/functions/src/functions/aiGateway.ts`**

```typescript
import { onCall, HttpsError, CallableRequest } from 'firebase-functions/v2/https';
import { AIGateway } from '../services/aiGateway';
import { subscriptionService } from '../services/subscriptionService';

interface AIGenerateRequest {
  capability: 'text-generation' | 'embedding' | 'classification';
  input: {
    text: string;
    context?: Record<string, any>;
  };
  options?: {
    maxTokens?: number;
    temperature?: number;
    model?: string;
  };
}

export const generateAI = onCall(
  { ...commonOptions, region: 'asia-south1' },
  async (request: CallableRequest<AIGenerateRequest>) => {
    const uid = requireAuthenticated(request);

    // Step 1: Check AI entitlement
    const entitlement = await subscriptionService.checkAIEntitlement(uid);
    if (!entitlement.enabled) {
      throw new HttpsError('failed-precondition', 'AI_SUBSCRIPTION_REQUIRED', {
        reason: entitlement.reason,
      });
    }

    // Step 2: Check quota
    const usage = await subscriptionService.getMonthlyUsage(uid);
    if (usage.tokens >= entitlement.monthlyTokenLimit) {
      throw new HttpsError('resource-exhausted', 'QUOTA_EXCEEDED', {
        used: usage.tokens,
        limit: entitlement.monthlyTokenLimit,
        resetDate: usage.resetDate,
      });
    }

    // Step 3: Route & execute
    const gateway = new AIGateway(uid, entitlement);
    const response = await gateway.generate(request.data);

    // Step 4: Track usage
    await subscriptionService.recordUsage(uid, {
      capability: request.data.capability,
      inputTokens: response.usage.inputTokens,
      outputTokens: response.usage.outputTokens,
      provider: response.provider,
      model: response.model,
      latency: response.latency,
      timestamp: Date.now(),
    });

    return response;
  }
);
```

**No changes to existing callables.** Just adds `generateAI` callable alongside current implementations.

---

### 3. Subscription System (New)

**New Service: `backend/functions/src/services/subscriptionService.ts`**

```typescript
export const checkAIEntitlement = async (uid: string) => {
  const user = await db.collection('users').doc(uid).get();
  const sub = user.data()?.aiSubscription;

  if (!sub) {
    return { enabled: false, reason: 'no_subscription' };
  }

  if (sub.status === 'expired' || sub.expiresAt < Date.now()) {
    return { enabled: false, reason: 'subscription_expired' };
  }

  if (sub.status === 'cancelled') {
    return { enabled: false, reason: 'subscription_cancelled' };
  }

  return {
    enabled: true,
    reason: 'active',
    plan: sub.plan, // 'pro', 'premium'
    monthlyTokenLimit: SUBSCRIPTION_TIERS[sub.plan].monthlyTokens,
    allowedProviders: SUBSCRIPTION_TIERS[sub.plan].providers,
    allowedModels: SUBSCRIPTION_TIERS[sub.plan].models,
  };
};

export const getMonthlyUsage = async (uid: string) => {
  const now = new Date();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
  const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0);

  const usageRecords = await db
    .collection('users')
    .doc(uid)
    .collection('aiUsage')
    .where('timestamp', '>=', monthStart.getTime())
    .where('timestamp', '<=', monthEnd.getTime())
    .get();

  let totalTokens = 0;
  usageRecords.forEach((doc) => {
    totalTokens += doc.data().outputTokens;
  });

  return {
    tokens: totalTokens,
    resetDate: monthEnd,
  };
};
```

**New Firestore Structure:**
```
users/
  {uid}/
    aiSubscription/
      provider: 'stripe'
      subscriptionId: 'sub_...'
      plan: 'pro'
      status: 'active'
      startedAt: timestamp
      expiresAt: timestamp
      cancelledAt: timestamp (if cancelled)
    
    aiUsage/
      {usage_id}/
        capability: 'text-generation'
        inputTokens: 42
        outputTokens: 28
        provider: 'gemini'
        model: 'gemini-2.0-flash'
        latency: 245
        cost: 0.00028 (₹)
        timestamp: 1695475200000
```

---

### 4. Stripe Integration (Enhanced)

**New Handler: `backend/functions/src/webhooks/stripeWebhooks.ts`**

```typescript
export const handleStripeWebhook = onRequest(async (request, response) => {
  const sig = request.headers['stripe-signature'];
  let event;

  try {
    event = stripe.webhooks.constructEvent(
      request.rawBody,
      sig,
      STRIPE_WEBHOOK_SECRET
    );
  } catch (err) {
    response.status(400).send(`Webhook Error: ${err.message}`);
    return;
  }

  switch (event.type) {
    case 'customer.subscription.created':
    case 'customer.subscription.updated':
      await handleSubscriptionCreated(event);
      break;

    case 'customer.subscription.deleted':
      await handleSubscriptionCancelled(event);
      break;

    case 'invoice.payment_succeeded':
      await handlePaymentSucceeded(event);
      break;

    default:
      console.log(`Unhandled event type ${event.type}`);
  }

  response.json({ received: true });
});

async function handleSubscriptionCreated(event) {
  const { customer, id: subscriptionId, metadata } = event.data.object;
  const uid = metadata?.uid; // Set via Stripe customer metadata

  if (!uid) {
    console.error('No UID in Stripe metadata');
    return;
  }

  // Activate AI entitlement
  await db.collection('users').doc(uid).update({
    aiSubscription: {
      provider: 'stripe',
      subscriptionId,
      plan: metadata?.plan || 'pro',
      status: 'active',
      startedAt: new Date(),
      expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
    },
  });

  console.log(`AI subscription activated for ${uid}`);
}

async function handleSubscriptionCancelled(event) {
  const { customer, metadata } = event.data.object;
  const uid = metadata?.uid;

  if (!uid) return;

  // Mark as cancelled
  await db.collection('users').doc(uid).update({
    'aiSubscription.status': 'cancelled',
    'aiSubscription.cancelledAt': new Date(),
  });

  console.log(`AI subscription cancelled for ${uid}`);
}
```

---

### 5. AI Gateway Service (Core Logic)

**New Service: `backend/functions/src/services/aiGateway.ts`**

```typescript
import { GeminiProvider } from '../providers/geminiAdapter';
import { OpenAIProvider } from '../providers/openaiAdapter';
import { LocalProvider } from '../providers/localAdapter';

export class AIGateway {
  private uid: string;
  private entitlement: AIEntitlement;
  private providers: Map<string, AIProvider>;

  constructor(uid: string, entitlement: AIEntitlement) {
    this.uid = uid;
    this.entitlement = entitlement;
    this.initializeProviders();
  }

  private initializeProviders() {
    this.providers = new Map();

    if (this.entitlement.allowedProviders.includes('gemini')) {
      this.providers.set(
        'gemini',
        new GeminiProvider(process.env.GEMINI_API_KEY!)
      );
    }

    if (this.entitlement.allowedProviders.includes('openai')) {
      this.providers.set(
        'openai',
        new OpenAIProvider(process.env.OPENAI_API_KEY!)
      );
    }

    // Local provider always available
    this.providers.set('local', new LocalProvider());
  }

  async generate(request: AIGenerateRequest): Promise<AIGenerateResponse> {
    // Select provider based on policy
    const selectedProvider = this.selectProvider(request);

    // Execute
    const start = Date.now();
    const response = await selectedProvider.generate(request);
    const latency = Date.now() - start;

    return {
      ...response,
      provider: selectedProvider.name,
      latency,
    };
  }

  private selectProvider(request: AIGenerateRequest): AIProvider {
    // Policy: Prefer Gemini for simple tasks, fallback to OpenAI for complex
    if (
      request.capability === 'text-generation' &&
      request.input.text.length < 500
    ) {
      return this.providers.get('gemini')!;
    }

    return this.providers.get(this.entitlement.plan === 'premium' ? 'openai' : 'gemini')!;
  }
}
```

---

## Migration Path

### Week 1-2: Setup
```
✅ Define AI Gateway API contract
✅ Define Subscription data model
✅ Set up Stripe webhook infrastructure
❌ Code AI Gateway (not yet)
```

### Week 3-4: Implementation
```
✅ Implement subscription service
✅ Implement AI Gateway Cloud Function
✅ Implement provider adapters
✅ Implement Stripe webhook handlers
```

### Week 5-6: Frontend
```
✅ Add subscription screen UI
✅ Add entitlement checks to LiveMessageScreen
✅ Add fallback to native if subscription missing
❌ Remove native implementation (keep as fallback)
```

### Week 7-8: Testing & Launch
```
✅ Unit tests: All services
✅ Integration tests: End-to-end flows
✅ E2E tests: Subscription → AI generation
✅ Load tests: 100+ concurrent requests
✅ Canary launch: 10% of users
✅ Full launch: 100% of users
```

---

## Backwards Compatibility

### For Users WITHOUT AI Subscription

**Current Flow (native only):**
```
Generate button → Native gateway → Suggestions shown
```

**New Flow (with backend gateway):**
```
Generate button → Check subscription → NO → Show "Subscribe to AI PRO"
                                       
User taps Subscribe → Stripe → Activate subscription → Can use AI
```

**Key Point:** Users who never had AI access now get prompted to subscribe. No regression; new monetization.

---

### For Users WITH AI Subscription

**Current Flow (if implemented later):**
```
Generate button → Native gateway → Suggestions shown (free, no limits)
```

**New Flow:**
```
Generate button → Backend gateway → Check subscription → YES → Suggestions shown
                                                                (quota tracked)
```

**Key Point:** Same user experience, but now with:
- Usage tracking
- Quota enforcement
- Fallback to native if backend fails

---

## Firestore Rules Update

Current rules deny all direct writes:
```javascript
match /users/{uid} {
  allow read, write: if false;  // App uses Cloud Functions only
}
```

**New rules (for AI subscription writes):**
```javascript
match /users/{uid} {
  allow read, write: if false;  // Default: deny all direct access
  
  // Allow Cloud Functions to write subscription state
  // (via service account in Cloud Function context)
  match /aiSubscription {
    allow read: if request.auth.uid == uid;  // User can read own subscription
  }
  
  // Allow Cloud Functions to append usage records
  match /aiUsage/{usageId} {
    allow read: if request.auth.uid == uid;  // User can read own usage
  }
}
```

---

## Monitoring & Observability

### New Metrics to Track

```typescript
// 1. Entitlement checks
Cloud Monitoring.counter('ai/entitlement_checks_total', {
  status: 'pass' | 'fail',  // passed/failed checks
  reason: 'no_subscription' | 'expired' | 'active',
});

// 2. API latency
Cloud Monitoring.histogram('ai/generate_latency_ms', latency);

// 3. Token usage
Cloud Monitoring.counter('ai/tokens_used_total', {
  provider: 'gemini' | 'openai',
  type: 'input' | 'output',
  plan: 'pro' | 'premium',
});

// 4. Cost tracking
Cloud Monitoring.gauge('ai/estimated_cost_usd', cost);

// 5. Subscription metrics
Cloud Monitoring.gauge('subscriptions/ai_active', activeCount);
Cloud Monitoring.gauge('subscriptions/ai_revenue_monthly', monthlyRevenue);
```

---

## Cost Estimation

### Infrastructure Costs

```
Cloud Functions (generateAI):
  ~500K requests/month
  ~2GB memory per function
  ~200ms avg duration
  Cost: ~$10-15/month

Firestore:
  ~1M document reads (entitlements)
  ~500K writes (usage records)
  Cost: ~$5-10/month

Total infra: ~$15-25/month
```

### Revenue (Assumptions)

```
Tier: AI PRO @ ₹499/month (~$6 USD)
Target: 100 subscribers by Month 3

Revenue: 100 × $6 = $600/month
Minus AI costs (Gemini API):
  1M output tokens @ ~$0.0001 = $100
  
Net: ~$500/month profit
```

---

## Success Metrics

### Technical

- ✅ AI Gateway handles 100+ concurrent requests
- ✅ Entitlement checks <50ms latency
- ✅ 99.9% uptime
- ✅ <5% error rate

### Product

- ✅ >50% of active users subscribe to AI PRO
- ✅ Average 50K tokens/month per subscriber
- ✅ <2% monthly churn
- ✅ 4+ week payback period

### Business

- ✅ AI features become paid tier (revenue unlock)
- ✅ Sustainable AI costs (covered by subscriptions)
- ✅ Extensible to other applications

---

## Summary

**The AI Gateway architecture:**

1. **Solves current problems** — Entitlements, quotas, cost control
2. **Maintains compatibility** — Native fallback for offline/failures
3. **Enables monetization** — Subscription-gated AI features
4. **Enables scalability** — Multi-provider, multi-application
5. **Follows best practices** — Separation of concerns, abstraction layers

**Integration with current SSOT:**
- ✅ No breaking changes to existing features
- ✅ New optional AI subscription tier
- ✅ Enhanced monitoring & cost control
- ✅ Foundation for Phase 2+ roadmap

**Timeline:** 8 weeks (Weeks 5-12 post-launch)  
**Owner:** Backend platform team  
**Priority:** High (enables AI monetization)

---

**For questions:** See `AI_GATEWAY_ARCHITECTURE.md` for full specification
