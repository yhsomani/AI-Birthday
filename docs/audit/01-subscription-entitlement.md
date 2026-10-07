# Audit 01 — Subscription / Entitlement Lifecycle

- Date: 2026-10-07
- Scope: `lib/features/subscription/**`, consumers (`message_studio`, `settings`, `ai_router`, `app/providers.dart`), backend `verifyPurchase`, E2E harness + tests, docs claims.
- Method: read-only. Evidence labels: **FACT** (code), **INFERENCE** (derivation), **UNKNOWN**.
- Evidence hierarchy: code > tests > docs/comments. Docs are frequently wrong here.

---

## 1. Current state model (states + transitions)

### 1.1 Domain state

`EntitlementStatus` enum — `lib/features/subscription/domain/entitlement.dart:5-10` (**FACT**):
`active | trial | grace | expired | none`.
- `isEntitled`: active/trial/grace → true — `entitlement.dart:13-18`.
- `canUseAi`: `isEntitled && (expiry == null || expiry.isAfter(now))` — `entitlement.dart:43-47`.
- `displayName`: active→"Pro (Active)", trial→"Free Trial", grace→"Grace Period", expired→"Expired", none→"Free Tier" — `entitlement.dart:20-26`.

**States actually reachable in production** (**FACT**, from `_verifyPurchase` mapping `subscription_service.dart:224-242` and server `subscriptionVerification.ts:99-103`, `schemas.ts:232`):

| State | Reachable in prod? | Evidence |
|---|---|---|
| `none` | Yes — success (server 200, no active), verification failure, auth missing, all HTTP errors | `subscription_service.dart:130-137,174,203,250` |
| `active` | Yes — only when server `status=='active' && expiry > now` | `subscription_service.dart:224-232` |
| `expired` | Yes — server `status=='expired'` | `subscription_service.dart:235-239` |
| `trial` | **No** — server never returns it; only sandbox hook | `schemas.ts:232`; `subscription_service.dart:370-372` |
| `grace` | **No** — server **maps** grace period to `status:'active'`; `EntitlementStatus.grace` only referenced in a preserve-branch | `subscriptionVerification.ts:20-24,99-103`; `subscription_service.dart:133-134` |

Server treats `SUBSCRIPTION_STATE_ACTIVE | IN_GRACE_PERIOD | CANCELED` as entitled (`subscriptionVerification.ts:20-24`) and collapses all three to `status:'active'` (**FACT**). Grace-period and canceled-but-unexpired users therefore display "Pro (Active)" (`entitlement.dart:21`).

### 1.2 Purchase-flow state

`PurchaseStatus { idle, purchasing, verifying, success, cancelled, error }` — `subscription_service.dart:14` (**FACT**). Held in `_purchaseStatus` (private getter, line 67-68), transitions at 116, 129, 137-142, 157, 265-308. **Never read by any UI** (**FACT** — grep `purchaseStatus` in `lib/` returns only `subscription_service.dart` itself; consumers use the `UserEntitlement` snapshot). The Settings "Verifying Purchase..." label is driven by transient local `_isPurchasing` (`settings_screen.dart:400-417`), not by `PurchaseStatus`. Modeled-but-invisible state: dead modeling.

### 1.3 Lifecycle transitions (**FACT**)

```
[cold start] userEntitlement = Free (constructor default, providers.dart:54; no persistence)
  └─ SubscriptionNotifier created lazily (only when Message Studio/Settings first opened —
     nothing at app root watches it: app.dart, app_scaffold.dart, router.dart, main.dart)
       └─ constructor → _initIapAndRestore() (subscription_service.dart:54, 74-103)
            ├─ IAP unavailable → return (no change)
            ├─ authToken null/empty   → return (no restore)          :91-94
            └─ token present → _restorePurchases()
                 → Play restore → purchaseStream → _onPurchasesUpdated (105-158)
                      ├─ productID mismatch → completePurchase, skip  :107-112
                      ├─ status purchased|restored → verifying → _verifyPurchase (116-117)
                      │     ├─ verified != null → state = active|expired|none (120-128)
                      │     └─ verified == null → state = Free unless already active/grace (130-137)
                      ├─ status error → purchaseStatus=error (139-140)
                      ├─ status canceled → purchaseStatus=cancelled (141-142)
                      └─ always: completePurchase if pending (147-149)

[upgrade] Settings "Upgrade to Pro" →
  purchaseProMonthly() (263-309): requires auth+binding (270-276), IAP available (278-282),
  product details non-empty (284-288), buyNonConsumable with applicationUserName=binding (291-296)
  → returns bool = "Play sheet launched", NOT entitlement.
  Entitlement change arrives asynchronously via purchaseStream → verification → _onPurchasesUpdated.

[restore] Settings "Restore Purchases" → restorePurchases() (312-351):
  requires auth+binding (322-327); completer completes with state.canUseAi per event (151-154);
  10s timeout returns stale state.canUseAi (336-339).
```

### 1.4 Failure-path handling (**FACT** unless noted)

| Failure | Code | Result |
|---|---|---|
| Auth/session missing at purchase/restore | `subscription_service.dart:270-276, 322-327` | `purchaseProMonthly`→false + snackbar "Purchase could not be started" (`settings_screen.dart:409-417`); restore →false + "No verified active subscription was found." (426-434) |
| IAP unavailable / no product | 278-288 | false, state untouched (Free) |
| Server 4xx/5xx / network error at verify | 197-203 / 243-251 | `_verifyPurchase`→null → state→Free (unless preserved stale) **and purchase still acknowledged** (147-149) |
| Verification hangs | 187-195 — **no `.timeout()`** on `_http.post` | `_onPurchasesUpdated` blocks; `completePurchase` deferred; Play auto-revokes unacknowledged purchase after ~3 days (INFERENCE, platform rule) |
| Restore with no purchases / slow verify | 332-339 | 10s timeout → returns stale `state.canUseAi` |
| `completePurchase` throws | 147-149 — not wrapped in try/catch | uncaught in stream handler → zone error (debug crash risk) |
| Mid-session expiry | `entitlement.dart:46` (`expiry.isAfter(now)`) | AI blocks on next read; status label stays "Pro (Active)" — see C5 |
| Refund/chargeback after grant | none | never propagated until a *successful* future verify (grant is one-shot) |

---

## 2. Authoritative-source analysis

- **Grant authority: the one-time HTTPS `verifyPurchase` response.** State is written only from `_verifyPurchase` (`subscription_service.dart:117-128`); comment 32-34 states intent. The AI router re-checks `entitlement.canUseAi` before every generation (`ai_router.dart:43-48`) and Message Studio pre-gates UI (`message_studio_screen.dart:793-796, 831-838, 948-966`). A user Gemini key cannot bypass entitlement (`ai_router.dart:41-42,50`). **FACT**
- **Device state authority: in-memory `StateNotifier` only.** `SecureStoreDriver` (`_store`) is used for the purchase *binding* (`readBindingFromStore`, 375-387) and one dead delete (`360`); entitlement is never persisted — grep `user_subscription_entitlement` matches only the delete. **FACT**. Every cold start = `UserEntitlement.free` until a successful restore+verify.
- **Firestore entitlement document (`users/{uid}/entitlement/status`) is write-only.** Server persists it (`subscriptionVerification.ts:105-120, 176-187`); the Flutter client never reads Firestore for entitlement — no Firestore usage in `lib/` for it. **FACT**
- **Offline:** no cache exists (see C2). Cold start offline → Free (safe, no false grant). In-session offline → last verified in-memory entitlement; expiry only enforced on read via `canUseAi`. Provider-unavailable: purchase/restore refuse safely (returns false, state untouched).
- **Dev sandbox:** `setDevSandboxEntitlement` / `updateEntitlement` / `resetToFreeTier` are **public methods on the production notifier** (`subscription_service.dart:355-372`), used by tests (`tier1:247`, `tier2:130`, `tier3:68`, `tier4:191`, `message_studio_visibility_test.dart:57`); no `assert(kDebugMode)` or other runtime guard. Not invoked by production code (**FACT**).
- **Answer to "does the app EVER claim premium when unverified/stale?"**
  - Unverified grant in prod path: **No** — grant requires a verified server response (FACT).
  - Stale grant: **Yes** — verification-failure branch preserves prior active/grace state (P1-3); restore timeout reports verified from stale state (P3-1); server maps grace/canceled to "Pro (Active)" label (P2-2).

---

## 3. Contradiction register

| ID | Conflict | Evidence | Authoritative | Decision |
|---|---|---|---|---|
| C1 | Docs claim R2 subscription verification "**VERIFIED (100%)**"; the real verify path is untested and the wire contract is broken (P0-1/P0-2) | `docs/archive/TEST_READY.md:20` vs `tier1_features_test.dart:241-257` (uses `setDevSandboxEntitlement`), `tier2:143-188` (fake-server-in-isolation), harness never wires http client or purchase stream (`test_harness.dart:84-89`) | Code + tests | Re-test honestly; "100%" claim withdrawn until an integration test proves unlock-from-verification |
| C2 | Docs: "Offline cache treated strictly as fallback, not authority" — **no offline cache exists** (entitlement never persisted, only deleted) | `TEST_READY.md:20` vs `subscription_service.dart:360`; grep `user_subscription_entitlement` | Code | Remove claim or implement cache; current cold-start behavior is Free-on-launch, which is safe but not what docs describe |
| C3 | SSOT §11: "Provide purchase, restore, expired, **grace**, and error states"; PROJECT.md M2: client publishes `freeTrial, gracePeriod` — code can only produce active/expired/none | `SSOT.md:315`; `PROJECT.md:63` vs `schemas.ts:232`, `subscriptionVerification.ts:99-103`, `subscription_service.dart:235-242` | Code | Collapse enum to `active|expired|none` or extend server response; grace/canceled currently masquerade as `active` |
| C4 | PROJECT.md M2: client "listens to Firestore stream `/users/{uid}/entitlement/status`" — **no Firestore read exists in the client** | `PROJECT.md:61-63` vs grep (no Firestore in `lib/features/subscription/**`) | Code | Delete the doc claim or implement read-only display sync; server verify stays the grant authority |
| C5 | Settings row can show **"Pro (Active)"** + **"FREE TIER"** badge at the same time (status.active with elapsed expiry in-session) | `settings_screen.dart:564` (`status.displayName`) vs `:573` (`canUseAi ? 'UNLOCKED' : 'FREE TIER'`) | `canUseAi` | Drive badge text from the same predicate; add a re-verify/refresh trigger |
| C6 | Restore success message claims "Verified Pro subscription restored." even when nothing was re-verified this run (timeout returns stale `state.canUseAi`) | `subscription_service.dart:336-339` vs `settings_screen.dart:430-431` | Fresh verification | Return false on timeout; only a completed verified event may report success |
| C7 | "Test-only hooks" are public, unguarded methods on the production notifier; `resetToFreeTier` deletes a key no code ever writes | `subscription_service.dart:355-372,360` | Code | `assert(kDebugMode)` guard or move to test-only subclass; delete dead key |
| C8 | PROJECT.md M2 documents a **flat** response JSON (`{ "status": ... }`); client parses `data` envelope; deployed backend is `onCall` (`result` envelope) — three mutually incompatible shapes | `PROJECT.md:57`; `subscription_service.dart:206-209`; `functions/index.ts:313` | Runtime contract (backend) | Single source of truth + integration test (P0-1) |

---

## 4. Findings (prioritized)

### P0-1 — Purchase verification can never unlock in production: client/server wire contract mismatch
- **Symptom:** Paying (or restoring) users never receive Pro; every verification resolves to "failure" → state stays Free, yet the purchase is acknowledged; AI stays locked forever despite the charge.
- **Root cause:**
  - Immediate: response-envelope mismatch. Client decodes `decoded['data']` (`subscription_service.dart:206-209`) from a plain `http.post` (188-195). The backend `verifyPurchase` is a Firebase **callable** (`onCall`, `functions/index.ts:313`) whose success body is `{"result": {...}}` (error body `{"error": {...,"message":...}}` with non-200). `decoded['data']` is never a Map → `_verifyPurchase` returns null (207-221) → entitlement forced to Free (130-137) even on success. The documented flat contract (`PROJECT.md:57`) matches neither side (C8).
  - Architectural: the client request/parse shape and the server response shape were authored independently, and the fake double mirrors the client's wrong shape (`fake_subscription_server.dart:84-105` returns a flat map with an in-body `statusCode`), so no test catches it. There is **no test anywhere** that drives `_verifyPurchase` (no fake `http.Client` wired, no `PurchaseDetails` emitted through `purchaseStream` — `test_harness.dart:21-57`; grep whole `test/`).
- **Evidence:** `subscription_service.dart:44-45, 188-221, 130-137, 147-149`; `backend/functions/src/functions/index.ts:313`; `backend/functions/src/transport/schemas.ts:231-237`; `PROJECT.md:54-57`; `docs/archive/TEST_READY.md:20`.
- **Required change:** (1) parse the callable envelope — read `decoded['result']`, handle `decoded['error']` as failure; or switch endpoint to an `onRequest`/REST function returning `{data: ...}` — but make client, backend, and fake all agree; (2) add an integration test wiring a fake `http.Client` (success `{"result":...}` → unlock + `completePurchase`; error/500 → stay Free, **no** acknowledgement) and a fake purchase stream emitting `PurchaseDetails`; (3) re-run R2 gating tests without the sandbox hook.

### P0-2 — The R2 "server-verified" truthfulness claim is untested (vacuous tests)
- **Symptom:** Suite claims "Client entitlement unlocks Pro features strictly upon server verification" as VERIFIED; the claim is proven only against the sandbox hook.
- **Root cause:** `tier1 R2.3` calls `setDevSandboxEntitlement(UserEntitlement.proActive)` (`tier1:241-257`); `R2.B3` asserts Free while never invoking purchase/verify flow (`tier2:162-188`); `R2.1/R2.2` call the fake's handler directly (not through the notifier). Nothing asserts "no unlock without verified server response" through the real code path.
- **Evidence:** `tier1_features_test.dart:182-258`, `tier2_boundary_corner_test.dart:162-188`, `test_harness.dart:84-89`, `TEST_READY.md:20`.
- **Required change:** the P0-1 integration test **is** this test (verification-success grants, failure withholds) — make the absence of the sandbox hook in the verification group a review rule.

### P1-1 — Charged-but-locked: failed verification still acknowledges the purchase; no retry, no surfaced error
- **Symptom:** Transient server/network failure at purchase time → user is charged on the Play account, app acknowledges (`completePurchase`), entitlement drops to Free; only a manual Settings → Restore re-verifies. `PurchaseStatus.error` exists but no UI shows it.
- **Root cause:** acknowledgement is unconditional after verification resolution (`subscription_service.dart:147-149`), including the failure branch (130-137); there is no auto-retry and no "verification failed — retry" state surfaced (settings snackbar `settings_screen.dart:426-434` only for manual restore).
- **Evidence:** `subscription_service.dart:116-149`; `purchaseStatus` unused by UI (grep).
- **Required change:** gate `completePurchase` on verified success (or re-verify before acknowledging — per platform rules a successful verification must precede acknowledgement); surface an explicit retryable error state/action on the paywall; keep a nonce to allow retry.

### P1-2 — Entitlement is lazy + in-memory only: every launch starts Free; restore happens only when Message Studio/Settings first open — never on app start or sign-in
- **Symptom:** After app restart a subscriber sees locked AI / FREE TIER until they happen to open a screen that reads `entitlementProvider`; a moment of lock flash before restore+verify lands; if the notifier materializes before sign-in completes, that session never restores (sign-in never triggers restore).
- **Root cause:** `subscriptionNotifierProvider` is a lazy `StateNotifierProvider` (`providers.dart:46-75`) with no eager watcher in `app.dart`/`app_scaffold.dart`/`router.dart`/`main.dart`; `_initIapAndRestore` runs once in the constructor with an authToken guard (`subscription_service.dart:91-94`) and is never re-triggered; no persistence (C2).
- **Evidence:** `main.dart:7-10`, `app.dart:16-28`, `app_scaffold.dart:45-95`; `DIAGRAM` grep — `entitlementProvider`/`subscriptionNotifierProvider` read only in `message_studio_screen.dart` & `settings_screen.dart`; `auth_controller.dart:40-54` (sign-in) never touches subscription.
- **Required change:** eagerly instantiate the notifier at app start (e.g. root watch) and re-run restore on sign-in completion; show a non-blocking "checking subscription…" state instead of hard-locking during the check; persist last verified entitlement as display-only fallback so an offline cold start does not mislabel a subscriber (grant still requires fresh verification).

### P1-3 — Stale entitlement is preserved on failed re-verification: app keeps granting Pro after verification failure
- **Symptom:** In-session re-verification (manual restore) fails (offline/server down) → the app keeps `active` (AI stays unlocked, Settings shows UNLOCKED) while the restore snackbar simultaneously says "No verified active subscription was found." No retry, no TTL, no de-grade.
- **Root cause:** preserve-branch (`subscription_service.dart:131-136`) only resets to Free when the current status is not active/grace, with no time bound or re-verify timer; verify-failure is indistinguishable from "server says none."
- **Evidence:** `subscription_service.dart:128-137`; `restorePurchases` timeout→`state.canUseAi` (`336-339`); snackbar `settings_screen.dart:426-434`.
- **Required change:** bounded TTL on preserved entitlement (or de-grade to a visible "verification unavailable" state that never claims verified); only a **successful** verify may set `active`; never pair a "verified restored" message with unverified state (C5/C6).

### P2-1 — Firestore entitlement document is write-only dead data; PROJECT.md documents a client Firestore stream that doesn't exist
- **Evidence:** `subscriptionVerification.ts:105-120` (server write); no client Firestore read (grep); `PROJECT.md:61-63`.
- **Required change:** implement (read-only, display sync) or delete the claim; keep server verification as the grant authority — never gate AI on a locally-read Firestore doc.

### P2-2 — grace/trial enum states are dead; server collapses grace/canceled to "active"
- **Evidence:** `schemas.ts:232`; `subscriptionVerification.ts:20-24,99-103`; `entitlement.dart:7-8` (reachable only via sandbox); SSOT §11 requires grace state (`SSOT.md:315`).
- **Required change:** extend the server status contract to emit `grace` (+ optionally `trial`) and map on the client, or remove the states; either way fix the "Pro (Active)" label for grace/canceled users (accuracy, not security — users in these states are correctly entitled until expiry).

### P2-3 — "Pro (Active)" + "FREE TIER" shown simultaneously in Settings
- **Evidence:** `settings_screen.dart:564 vs 573`; entitlement expiry only checked at read (`entitlement.dart:46`).
- **Required change:** derive label + badge from one predicate; add refresh.

### P3-1 — Restore timeout reports "Verified Pro subscription restored." from stale state
- **Evidence:** `subscription_service.dart:336-339` (`onTimeout: () => state.canUseAi`); `settings_screen.dart:430-431`.
- **Required change:** return `false` on timeout unless a purchase event completed the completer with verified canUseAi.

### P3-2 — Unguarded public test hooks on production class; dead store key
- **Evidence:** `subscription_service.dart:355-372`; `resetToFreeTier` deletes key never written (`:360`).
- **Required change:** `assert(kDebugMode)` / test-only subclass; remove dead delete.

### P3-3 — No timeout on verify HTTP POST; `completePurchase` outside try/catch
- **Evidence:** `subscription_service.dart:187-195` (no `.timeout()`), `147-149`; consequence: hung verification stalls the handler, defers acknowledgement (Play revokes unacknowledged purchases after ~3 days), unhandled zone error if `completePurchase` throws.
- **Required change:** `.timeout(...)` on the POST with a retryable error; wrap acknowledgement in try/catch.

---

## 5. Acceptance criteria (top fixes)

**AC-1 (P0-1) — verification contract, end-to-end:**
- Given a `purchaseStream` event with `PurchaseStatus.purchased` and a valid token, when a fake `http.Client` returns `{"result": {"status":"active","productId":"ai_birthday_pro_monthly","expiryDateMs":<future>,"isAutoRenewing":true}}`, then `entitlement.status == active`, `canUseAi == true`, and `completePurchase` was called.
- Given the same event, when the fake client returns HTTP 500 / `{"error": {...}}` / a malformed body, then `canUseAi == false`, state is `none` (or preserved per AC-4), and `completePurchase` was **not** called.
- Given a raw body of `{"result": {...}}`, the parse succeeds; given `{"error": {...}}`, the parse yields failure (no crash).

**AC-2 (P1-1) — no charge-without-unlock:**
- Given a purchase event and a verification that fails once, when the user taps Retry (or an automatic retry fires), then a subsequent successful verification unlocks AI; the paywall shows a distinct "verification failed — retry" state rather than silently dropping to Free.

**AC-3 (P1-2) — cold start / sign-in:**
- Given a returning subscriber launching the app, when the root widget builds, then a restore+verify is initiated automatically (independent of which screen loads) and `entitlementProvider` resolves to `active` before the message studio renders an unlocked state; no subscriber is shown locked at first paint.
- Given a signed-out user completing sign-in, when `signIn()` succeeds, then `restorePurchases()` is triggered if not already run this session.

**AC-4 (P1-3) — no stale propagation:**
- Given `state.status == active` from a prior verified grant, when a new verification fails (timeout/5xx), then either (a) entitlements de-grades to a "verification unavailable" state that does not grant AI and does not claim verified, or (b) the preserved grant carries a bounded TTL and a visible re-verify indicator; in no case does UI show "Verified Pro subscription restored" alongside unverified state.

**AC-5 (P0-2) — truthful test suite:**
- Given the R2 group, when the suite runs, then every "unlocks only on server verification" assertion exercises `_onPurchasesUpdated → _verifyPurchase` with a wired fake http client (no `setDevSandboxEntitlement`), and passes.

---

## 6. Evidence index (key lines)

- `lib/features/subscription/domain/entitlement.dart:5-27,43-47,50-57`
- `lib/features/subscription/application/subscription_service.dart:14,44-45,54,74-103,105-158,160-252,263-309,312-351,355-372,375-387`
- `lib/app/providers.dart:45-80`
- `lib/features/ai/domain/ai_router.dart:35-48`
- `lib/features/message_studio/presentation/message_studio_screen.dart:167,435,502,793-796,831-838,948-966`
- `lib/features/settings/presentation/settings_screen.dart:400-434,441,544-628`
- `lib/features/auth/application/auth_controller.dart:40-60`
- `backend/functions/src/functions/index.ts:47-76,307-328`
- `backend/functions/src/services/subscriptionVerification.ts:20-24,40-130,171-196`
- `backend/functions/src/transport/schemas.ts:215-237`
- `test/e2e/harness/test_harness.dart:21-57,84-89`; `harness/fake_subscription_server.dart:25-106`
- `test/e2e/tier1_features_test.dart:182-258`; `tier2_boundary_corner_test.dart:120-188`; `tier3_cross_feature_test.dart:60-80`; `tier4_user_journeys_test.dart:185-200`
- `test/features/subscription/subscription_service_test.dart:59-96`
- Docs: `SSOT.md:295-315,113-124`; `PROJECT.md:53-63`; `docs/archive/TEST_READY.md:20,88-91`; `ARCHITECTURE.md:58`