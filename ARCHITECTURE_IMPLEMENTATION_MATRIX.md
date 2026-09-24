# AI Architecture Analysis & Implementation Matrix

## 1. Executive Summary

This document establishes the authoritative implementation analysis and current-to-target state mapping for **AI-Birthday (WishWell)**.

### Core Architectural Invariant
> **The application's subscription controls whether the user is allowed to use ANY AI feature.**
> External AI capabilities (Gemini, local AI, on-device AI) determine **how** the request is executed, never **whether** the user is permitted to use AI.
> 
> `AI_ACCESS_ALLOWED = authenticated AND application_ai_entitlement_active`

---

## 2. Current-State vs Target-State Analysis

### 2.1 The Two Divergent Execution Paths Found in Repo

1. **Native Client Path (Currently wired in Android UI):**
   ```text
   LiveMessageScreen.tsx
         ↓
   port.generateSuggestions() (MessagePort)
         ↓
   BirthdayNativeAdapter.generateSuggestions()
         ↓ React Native bridge ("generate-suggestions")
   BirthdayNativeModule.kt
         ↓
   AndroidGeminiSuggestionGateway.kt
         ↓
   Firebase Generative AI SDK (Device-initiated)
         ↓
   Google Gemini API
   ```
   *Critical Gap:* Bypasses all application subscription checks and quota limits. Only checked a Firebase Remote Config boolean (`gemini_suggestions_enabled`).

2. **Backend AI Gateway Path (Added in recent commits but disconnected from UI):**
   ```text
   backend/functions/src/functions/index.ts (generateBirthdayDraft)
         ↓
   AiGatewayService.generate()
         ↓
   Entitlement Check (aiEntitlementPath) + Quota Reservation (aiUsageSummary)
         ↓
   AiProviderAdapter (GeminiRestAdapter / StubProviderAdapter)
   ```
   *Critical Gap:* Not called by mobile app UI. Also, provider routing did not support local/on-device AI or user-authorized Gemini abstraction.

---

## 3. Implementation Matrix

| Component / Feature | Status | Location | Intended Architecture | Resolution / Verification |
|---|---|---|---|---|
| **App Subscription Invariant** | ✅ COMPLETE | `backend/.../aiModel.ts`, `src/domain/ai/model.ts`, `aiProviders.ts` | Inactive subscription blocks ALL AI (cloud & local) | Enforced across backend `AIExecutionRouter`, `AiGatewayService`, `BirthdayNativeModule.kt`, and `LiveMessageScreen.tsx`. Tested in `aiExecutionRouter.test.ts` (Scenarios 1 & 2). |
| **Duplicate Execution Path** | ✅ RESOLVED | `BirthdayNativeModule.kt` line 373, `AndroidGeminiSuggestionGateway.kt` | Single authoritative AI Gateway pipeline / fail-closed entitlement gate | Added `AiEntitlementSnapshot.parse` check before calling native suggestion generator. Returns `ai-subscription-required` if unsubscribed. |
| **Provider Abstraction** | ✅ COMPLETE | `backend/.../aiProviders.ts`, `src/domain/messages/model.ts`, `AiPort.ts` | Generic `AIProvider`, `AIRequest`, `AIResult` | Generic domain contracts defined; `GeminiRequest` and `GeminiSuggestionsProjection` aliased for 100% backward compatibility. |
| **AI Execution Router** | ✅ COMPLETE | `backend/.../aiProviders.ts`, `backend/.../aiGateway.ts` | Central `AIExecutionRouter` (User Gemini -> Local AI -> Cloud Pooled -> Unavailable) | Implemented `AIExecutionRouter` with 4-tier fallback hierarchy, health checking, and transactional quota/budget reservation. |
| **Google/Gemini Verification** | ✅ COMPLETE (DOC VERIFIED) | Official Google Docs, `UserGeminiProvider` | Consumer Gemini does NOT grant 3rd-party API quota | `UserGeminiProvider` health check honestly marks consumer Gemini unauthorized for third-party API use. Never fakes with developer keys. |
| **Local / On-Device AI** | ✅ COMPLETE | `backend/.../aiProviders.ts` (`LocalAIProvider`, `OnDeviceAIProvider`) | Local AI execution adapter gated by application subscription | Implemented `LocalAIProvider` with bilingual EN/HI personalization and milestone synthesis. Subscription required; zero cloud cost recorded. |
| **Offline Behavior & Caching** | ✅ COMPLETE | `backend/.../aiModel.ts`, `src/domain/ai/model.ts` | Secure cached entitlement with finite grace period (max 72h) | Implemented `CachedAiEntitlement` and `isCachedEntitlementValid` with 72-hour strict maximum grace period ceiling. |
| **Subscription Ingestion** | ✅ COMPLETE | `backend/.../subscriptionIngestion.ts`, `functions/index.ts` | Authoritative ingestion for Google Play RTDN & Stripe | Implemented `onPlayBillingEvent` and `onStripeBillingEvent` Firestore triggers with last-write-wins reduction. |
| **Error Contracts** | ✅ COMPLETE | `aiModel.ts`, `reasonCodes.ts`, `liveResources.ts` | Standard machine-readable AI error codes (`AI_SUBSCRIPTION_REQUIRED`, etc.) | Standardized 10 `AIErrorCode` enum values across backend, client, and UI localization. |
| **Multi-App Reusability** | ✅ COMPLETE | `aiGateway.ts`, `functions/index.ts` | Parameterized `applicationId` (e.g. `ai-birthday`) | Implemented generic `generateAi` callable with `applicationId` and options parameterization. |

---

## 4. Verification Evidence

- **Backend Test Suite:** 17 test suites, 171 tests passing (`npm test` in `backend/functions`).
  - `test/aiExecutionRouter.test.ts` (19 tests) verifying Scenarios 1–7 end-to-end.
  - `test/aiGateway.test.ts` (17 tests) verifying quota reservation, compensation, and budget boundaries.
  - `test/subscriptionIngestion.test.ts` (24 tests) verifying Play RTDN and Stripe reduction.
  - `test/aiModel.test.ts` (22 tests) verifying entitlement decisions, caching, and error codes.
- **Frontend Test Suite:** 33 test suites, 400 tests passing (`npm test` in root).
  - All architecture, contract, screen, and localization tests passing 100%.
- **Zero BYOK Invariant Maintained:** No user-entered API keys exist in the architecture. Provider authentication is OAuth sign-in only.
- **Strict Subscription Gate Maintained:** Under no circumstances may local or cloud AI run for an unsubscribed account.

