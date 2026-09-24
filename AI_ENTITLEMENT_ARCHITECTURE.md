# AI Entitlement Architecture (v1.0 — 2026-09-24)

**Status:** Authoritative. Supersedes any "Gemini login" or "bring-your-own-key" framing.
**Applies to:** WishWell/Birthday Autopilot today; reusable unchanged for TalentSphere and every future application.

---

## 0. The one-sentence rule

> **The application provides AI functionality through its own subscription and
> entitlement system. External AI subscriptions are not treated as application
> entitlements. AI providers are implementation details behind a provider-agnostic
> AI Gateway. End-user provider authorisation uses OAuth sign-in ("use my AI
> login") where supported — users never paste or manage API keys; otherwise the
> application uses application-controlled provider credentials. On-device AI may
> be used when supported as an execution path / cost-optimisation mechanism.**

## 0.1 What was explicitly rejected

| Rejected concept                                                       | Why                                                                                                                                                                                       | Verdict in this architecture                                                                 |
| ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| "If the user has Gemini Pro/Ultra, use their subscription for our app" | Google's public docs do not establish that a consumer Gemini subscription entitles third-party API usage; Gemini API quota/billing is governed by projects, credentials and billing tiers | ❌ Never encoded. No code path reads external provider subscription state                    |
| Bring-your-own-key (BYOK): pasting provider API keys                   | User requirement (2026-09-24): _"I don't want to use API keys, I want to use login"_ — keys are long-lived secrets users mishandle                                                        | ❌ Removed. `byok-*` provider ids and key-state vocabularies deleted from TS + Kotlin models |
| Tying entitlement directly to Stripe                                   | Not reusable across apps/payment providers                                                                                                                                                | ❌ Replaced with Billing → Entitlement layering (§5)                                         |

---

## 1. Four concepts that must never be conflated

```text
A. IDENTITY              Who is the user?        → Firebase Auth (Google/…)
B. APP SUBSCRIPTION      May they use our AI?    → free | wishwell-plus
C. PROVIDER AUTHORISATION How do we legally call  → application-owned creds
                         the provider?            | OAuth provider sign-in
                                                  | on-device (no credential)
D. AI EXECUTION          Where does inference     → cloud adapter | on-device
                       actually run?
```

```text
USER'S GOOGLE ACCOUNT ≠ YOUR APPLICATION ACCOUNT ≠ YOUR APPLICATION
SUBSCRIPTION ≠ AI PROVIDER ACCOUNT ≠ AI PROVIDER SUBSCRIPTION ≠ AI API BILLING
```

Code anchors:

| Concept                        | Source of truth                                                                 |
| ------------------------------ | ------------------------------------------------------------------------------- |
| B (entitlement decision)       | `src/domain/ai/model.ts` → `decideAiEntitlement()` (pure)                       |
| B/C/D wire contract            | `src/infrastructure/native/featureSchemas.ts` (`ai*Schema`)                     |
| Application boundary           | `src/application/ports/AiEntitlementPort.ts`, `MessagePort.generateSuggestions` |
| Routing policy (Kotlin mirror) | `android/.../ai/AiGatewayPort.kt` → `AiGatewayRoutingPolicy.route()`            |

## 2. The business rule (the gate)

```text
                 USER
                  │
             Login once (app identity)
                  │
                  ▼
          ┌───────────────┐
          │ Subscription? │   ← ONLY this flips AI on/off
          └───────┬───────┘
          ┌───────┴────────┐
         NO               YES
          │                │
          ▼                ▼
   Subscribe screen    Quota check ──exhausted──▶ "Try again tomorrow"
   (ai-subscription-        │
    required)              ▼
                      AI Gateway route()
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
         On-device   Provider sign-in  Application-owned
         (capable)   (user's own login) Gemini cloud
```

Provider sign-in state is a **routing input that sits BEHIND the entitlement
gate**. Connecting your Google AI account can change _which adapter pays the
provider_, never _whether AI is allowed_. This is unit-tested on both sides:

- `src/domain/ai/entitlement.test.ts` — "never consults external provider
  state: a connected sign-in changes nothing"
- `android/.../ai/AiGatewayRoutingPolicyTest.kt` — "entitlement gate blocks
  before any provider routing"

## 3. Provider authorisation without API keys — the recommended approach

### 3.1 Recommended: OAuth 2.0 Authorization Code + PKCE ("Connect your AI account")

For a native mobile app this is the industry-standard, key-free pattern:

1. App launches the provider's authorize endpoint in a **Custom Tab**
   (Android) with `code_verifier` (PKCE, RFC 7636), `state`, and a
   **redirect via App Link / custom scheme** — no client secret anywhere.
2. Provider authenticates the user in Google's own UI (the user's "AI login").
3. Exchange code + verifier for access/refresh tokens **in native code**.
4. Store tokens only in **EncryptedSharedPreferences** (Android keystore
   backed). They never cross the RN bridge to JavaScript; JS sees only the
   `AiConnectionState` enum (`disconnected/connecting/connected/expired/failed`).
5. The gateway attaches the access token to provider calls, refreshing
   transparently; on `expired` prompt silent re-auth.

Already present in the repo and reused as-is: Firebase Auth (Google provider)
establishes _identity_; the new `connectAiProvider()/disconnectAiProvider()`
port methods establish _provider authorisation_. Two different things — see §1.

### 3.2 Important caveat (documented, not assumed)

OAuth sign-in proves **who the user is at the provider**; it does not, by
itself, guarantee a paid API quota for third-party use. Google documents OAuth
for Gemini API access, while API rate limits/billing remain project-scoped.
Therefore:

- If the user's granted OAuth scope carries usable quota → route there
  (their login pays their provider).
- Otherwise fall back to the **application-owned** path — still behind the
  same entitlement + quota gates, so the app's economics stay bounded
  (₹499 ≠ unlimited Gemini).
- The routing order in `AiGatewayRoutingPolicy.route()` encodes exactly this:
  on-device → provider sign-in → application-owned.

### 3.3 Modes shipped in the vocabulary

| `AiAuthorizationMode` | Credential source                                     | User ever sees a key? |
| --------------------- | ----------------------------------------------------- | --------------------- |
| `application-owned`   | App's Firebase/project credentials (production today) | No — invisible        |
| `provider-sign-in`    | User's OAuth tokens from the login flow               | **No — login only**   |
| `on-device`           | None; local inference                                 | No                    |

## 4. Generic gateway surface (reusable across applications)

Applications call capability-shaped entry points (`generateSuggestions` today;
`POST /api/ai/generate {capability, input, context, options}` when a shared
backend gateway lands) — never `/api/gemini`. The gateway pipeline:

```text
Authentication → Entitlement (app subscription) → Usage limits/rate limiting
→ Policy → Provider selection → Model selection → Execution (adapter)
→ Usage accounting → Response normalisation
```

Adapter pattern: `AIProvider { generate(); stream(); embed(); countTokens(); healthCheck(); }`
— `GeminiProvider` (today: `AndroidGeminiSuggestionGateway`), `OnDeviceProvider`
(planned), future `OpenAIProvider`. Adding a provider = one id + one adapter;
callers, schemas and entitlement logic do not change.

## 5. Payment → entitlement chain (never Stripe → AI)

```text
Stripe (or any PSP) → Billing Service → Entitlement Service → AI Gateway
```

Entitlement read model (target shape for the backend callable; device mirrors it):

```json
{
  "enabled": true,
  "plan": "wishwell-plus",
  "monthlyLimit": 300,
  "dailyLimit": 50,
  "remaining": 247
}
```

Payment success → webhook → subscription activated → entitlement activated →
AI access granted. Expiry/lapse reverses step-by-step; the fail-closed parser
(`AiEntitlementSnapshot.parse`) guarantees malformed/missing state means "no AI".

## 6. Cost controls (we pay the provider on the fallback path)

Per plan (`PLAN_QUOTAS` in `src/domain/ai/model.ts`; enforced pre-flight on
device, mirrored server-side when the shared gateway ships):

| Plan          | requests/day    | requests/month |
| ------------- | --------------- | -------------- |
| free          | 0 (AI disabled) | 0              |
| wishwell-plus | 50              | 300            |

Plus: RPM/TPM ceilings at the provider adapter, model restrictions per plan,
abuse protection, and on-device-first routing to cut cloud spend.

## 7. Multi-application reuse

```text
App A (WishWell) ─┐
App B (TalentSphere) ─┤→ Identity Service → Entitlement Service → AI Gateway
App C … ─────────┘                         (per-app application_id,
                                            plan catalogue, policies,
                                            limits, enabled adapters)
```

Each application owns its plan ids and quotas; the four-concept separation,
schemas, port shapes and routing policy are shared unchanged.

## 8. Localization

Blocked states render through exhaustive EN/HI copy:
`live.reason.aiSubscriptionRequired`, `live.reason.aiQuotaExhausted`
(`src/localization/reasonCopy.ts` + `liveResources.ts`). The internal
`ai-usage-period-mismatch` code maps to the generic internal-error copy —
users never see contract jargon.

## 9. Verification

- `npx tsc --noEmit` clean; `npm test` green incl. `src/domain/ai/entitlement.test.ts`
  (10 tests, 100 % coverage of `model.ts`) and `src/domain/shared/reasonCodes.test.ts`.
- Kotlin: `./gradlew :app:testDevDebugUnitTest` runs
  `AiGatewayRoutingPolicyTest` (gate-before-routing, ordering, fail-closed parse,
  key-free vocabulary assertions).
- Grep invariant (must stay empty): `grep -ri "byok\|api.?key" src/domain/ai android/app/src/*/java/com/yashsomani/birthdayautopilot/ai`
