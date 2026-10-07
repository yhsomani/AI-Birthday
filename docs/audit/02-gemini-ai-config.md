# Audit 02 — Gemini / AI Configuration Lifecycle

**Agent:** Gemini/AI Configuration Audit Agent · **Date:** 2026-10-07 · **Scope:** read-only
**Evidence hierarchy used:** code/control flow > tests > docs/comments. Claims are labeled **FACT** / **INFERENCE** / **UNKNOWN**.

Product line refs: `lib/features/ai/*`, `lib/core/security/*`, `lib/core/platform/gemini_nano_platform.dart`, `lib/features/settings/presentation/settings_screen.dart`, `lib/features/message_studio/presentation/message_studio_screen.dart`, `lib/features/subscription/*`, `android/.../MainActivity.kt`.

---

## 1. AI lifecycle state model (facts + transitions)

### States (all are AI-scoped; see §2 for isolation)

| State | Definition | Evidence |
|---|---|---|
| `Entitled` | `entitlement.canUseAi` = status in {active,trial,grace} **and** expiry (if any) after local now | `entitlement.dart:43-47`; status map `:13-18` |
| `KeyPresent` | `SecureCredentialStorage.hasGeminiApiKey()` — key exists (any validity) | `credential_storage.dart:58-61`, key const `:33` |
| `KeyValid` | Last web ping OK — **transient, in-memory only**: `_connectionResult` | `settings_screen.dart:40,287-293` |
| `KeyNeverValidated` | Key stored via **unvalidated** "Save Key" path — legal state, badge shows "Configured" (green) | `settings_screen.dart:327-365,727-730,1155-1170`; test asserts it: `test/features/settings/presentation/settings_gemini_onboarding_test.dart:233-254` |
| `NanoAvailable` | MethodChannel `currentState` → `ready/available` | `gemini_nano_platform.dart:87-106` ↔ `MainActivity.kt:154-162,601-609` |
| `NanoUnavailable` | Any other Nano state, non-Android, or platform error | `gemini_nano_platform.dart:88-105`; `DefaultGeminiNanoPlatform` `:96-110` |

### Request-time routing (`AiRouter.generate`, `ai_router.dart:36-82`) — FACT

```
entitlement.canUseAi?  NO ─────────────────────────────▶ throw lockedAi          (:43-48)
hasGeminiApiKey()?
  ├─ NO (or forceNano) → nanoStatusChecker()            (:53-56)
  │     Nano available & provider? YES → gemini_nano    (:58-61)
  │     forceNano && not available        → nanoUnavailable (:63-67)
  ├─ YES → userGemini provider (regardless of validity) (:71-73)
  └─ neither → throw credentialMissing                  (:78-81)
```

### Provider error mapping (`user_gemini_api_provider.dart`) — FACT

- No key at generate time → `credentialMissing` (`:96-100`)
- `generateMessage`: Socket → `networkUnavailable`; 400/401/403 → `credentialInvalid`; 429 → `quotaExceeded`; ≥500 → `providerError`; 200 → parse (`:136-159,268-298`)
- `testApiKey`: **live endpoint ping** (no local heuristic): 200→connected, 400/401/403→invalidKey, 429→quotaExceeded, Socket/Http→networkUnavailable, else error; exception strings are scrubbed before any UI copy (`:165-223`, esp. `:220-221`)

### Persistence — FACT

- **Persisted:** key bytes (secure storage), boolean `hasGeminiApiKey`, entitlement is *not* cached (fail-closed at startup — see §5).
- **NOT persisted:** last validation result/status/timestamp. `GeminiConnectionResult` lives only in `_SettingsScreenState` (`settings_screen.dart:40`). There is no `lastValidatedAt` in `CredentialStorage` (`credential_storage.dart:18-25`). Readiness is therefore **derived from transient state + key presence**, not persisted validation.

### Missing/absent transitions — FACT

- No `valid → revoked` detection: remote revocation is only discovered at the next failed generate.
- **No `invalid-key → Nano fallback`:** any stored key takes priority over Nano (`ai_router.dart:50-74`); `forceNano` exists (`:39,53,63`) but **no caller passes true** (repo grep: definition only, plus `providers.dart:145-161` wiring default) — an invalid key permanently starves Nano.
- `AppFailureCode.aiTimeout`/`busy` exist (`app_failure.dart:25-27,128,142`) but no code path raises them for the API provider (no timeout on the HTTP sender beyond 15 s connect — `user_gemini_api_provider.dart:307`; FUSE: `AppFailure.timeout` is unreachable in the user-Gemini path).

---

## 2. Isolation analysis — does AI unavailability ever touch non-AI features?

**Verdict: NO (clean isolation), with one behavioral caveat (entitlement fail-closed at cold start).**

Repo-wide grep for `entitlementProvider`, `aiRouterProvider`, `canUseAi`, `hasGeminiApiKey`, `getGeminiApiKey` — only **two** UI consumers:

| Surface | AI touch | Non-AI impact | Evidence |
|---|---|---|---|
| `MessageStudioScreen` | `aiLocked = !entitlement.canUseAi` hides tone/length/rewrite chips + swaps secondary button; manual editor, autosave, copy, WhatsApp/SMS/share, delivery status updates are **always** available | none (data, delivery, drafts unaffected) | `message_studio_screen.dart:793-796,832-838,917,948-966,750-776` |
| `SettingsScreen` | subscription card + provider section (+ unvalidated Save Key) | none | `settings_screen.dart:441,545-847` |
| `AiRouter` / providers | request-time gate | none (pure request path) | `ai_router.dart:43`, `user_gemini_api_provider.dart:92` |

Birthdays lifecycle, people/CRUD + CSV/contacts import, calendar, history, reminder scheduling, cloud sync — **zero** entitlement/key dependencies (grep of `lib/features/*` for `aiRouter|entitlementProvider|geminiApi|GeminiNano` shows matches only in `ai/`, `message_studio/`, `settings/`, and one non-gating `providerType` DB field in `sync/`). Router has no AI redirects (`router.dart:28-36` only onboarding guard).

**Test-proven:** free tier keeps "Your message" field + WhatsApp + Share (audit H) — `test/features/message_studio/presentation/message_studio_visibility_test.dart:114-130`.

**Caveat (INFERENCE, AI-only):** entitlement starts `Free` and is restored asynchronously (`subscription_service.dart:74-103`); restore hops auth + IAP with a 10 s timeout (`:336-339`). An entitled user who is offline/signed-out at cold start sees AI locked until restore succeeds — **AI only**, per the documented fail-closed rule (SSOT.md:300). No false "AI ready" claim: with no key, the button still says "Generate with AI" (`message_studio_screen.dart:964`) and only errors on tap.

**False "ready" claims:** the only one is the transient "Connected"/"Configured" badge (see C2).

---

## 3. Contradiction register

| ID | Conflict | Evidence | Decision |
|---|---|---|---|
| C1 | Guide step 4: *"We will verify access before saving it"* vs. an unvalidated **Save Key** button that saves immediately | `settings_screen.dart:1483` vs `:727-730,327-365` | Unvalidated save is intentionally tested (`settings_gemini_onboarding_test.dart:233-254`) → keep quick save but reword guide copy, or require a test pass before save |
| C2 | "Connected" (green) is in-memory-only; a never-validated key shows green **"Configured"**; key validity never re-checked on reopen | `settings_screen.dart:1099-1121` vs `:1155-1170`; no persisted result (`credential_storage.dart:18-25`) | Persist last-validation (status+ts) or relabel "Configured" neutral; re-ping on settings open |
| C3 | "Never included in sync" (app-level) claim is accurate for app sync (FACT), but **no Android backup exclusion** — OS Auto Backup can copy the encrypted prefs file | `settings_screen.dart:757`; `AndroidManifest.xml:9-12` (no `allowBackup`/`fullBackupContent`) | Add backup exclusion rules (P3) |
| C4 | SSOT §5: *"explicit option to use Nano when available"* vs. `forceNano` wired to nothing | `SSOT.md:136`; `ai_router.dart:39,53,63`; grep = definition only | Surface a Nano toggle/fallback in Studio or delete the capability |

---

## 4. P0–P3 findings

### P1-1 — Invalid/revoked key starves Gemini Nano fallback
- **Symptom:** Pro user with a dead/revoked key gets error on every generation even when Nano is downloaded and ready.
- **Root cause:** immediate — router prefers *any* stored key over Nano (`ai_router.dart:50-74`); architectural — routing treats "key exists" as "provider ready" and never re-classifies after a `credentialInvalid`, and `forceNano` is unused by every caller.
- **Evidence:** `ai_router.dart:50-74`; `user_gemini_api_provider.dart:275-282` (400/401/403 → `credentialInvalid`); no `forceNano` caller (grep).
- **Required change:** on `AppFailureCode.aiCredentialInvalid`, surface a "Use on-device AI (Gemini Nano)" action in the Studio error banner (`message_studio_screen.dart:841-914`) that retries with `forceNano: true`; optionally auto-fall back when Nano is available.

### P1-2 — Validation results are transient; "Connected" not durable; revocation silent
- **Symptom:** "Connected" reverts to "Configured" on relaunch; a key revoked server-side is only discovered on a failed generate; UI *looks* ready the whole time.
- **Root cause:** immediate — `_connectionResult` is widget state (`settings_screen.dart:40`); architectural — readiness is derived from key presence, not persisted validation; no `lastValidatedAt` in `CredentialStorage` (`credential_storage.dart:18-25`).
- **Evidence:** `settings_screen.dart:64-78,265-309,1098-1188`.
- **Required change:** extend `CredentialStorage` with a `lastGeminiValidation` record (write on `testApiKey` success), read it in `_checkStoredKey`, show stale-state (e.g. "Configured · last verified <date>") and re-verify opportunistically.

### P2-1 — Unvalidated keys enter the "ready-looking" flow
- **Symptom:** garbage key saved via Save Key → green "Configured" → guaranteed generation failure later.
- **Root cause:** `_saveKey` has no validation gate (`settings_screen.dart:343-345`); badge equates presence with readiness (`:1155-1170`).
- **Evidence:** `settings_gemini_onboarding_test.dart:233-254` (behavior is pinned by test).
- **Required change:** validate on save (reuse `testApiKey`) or demote the unvalidated badge and warn on first use.

### P2-2 — Entitlement is not cached; offline cold start locks AI
- **Symptom:** entitled user offline → AI locked until sign-in + IAP restore complete (10 s timeout).
- **Root cause:** architectural — `SubscriptionNotifier` races `_initIapAndRestore` against a hard `Free` start (`subscription_service.dart:46-55,74-103,336-339`); no persisted verified entitlement (`:360` only ever deletes).
- **Evidence:** `subscription_service.dart:74-103,316-351`; SSOT.md:300 (fail-closed is policy).
- **Required change:** persist last-verified entitlement + expiry and hydrate synchronously, re-verifying in background (keep server as source of truth; refresh before use when online).

### P2-3 — Placement/product logic: setup is Settings-only, dead Nano option
- **Finding:** Gemini setup lives solely in Settings (`settings_screen.dart:635-795` + guide sheet `:1367+`); onboarding never mentions key entry; no post-purchase or first-AI-use nudge. This **matches** SSOT "no second AI login; provider setting, not a login" (SSOT.md:326) — but SSOT §5's explicit Nano option (SSOT.md:136, C4) is not surfaced.
- **Required change (optional):** keep Settings placement; add a first-AI-use sheet item explaining the two provider choices.

### P3 — Hardening
- Android manifest lacks `allowBackup="false"`/`fullBackupContent` → OS Auto Backup may copy the encrypted prefs file (`AndroidManifest.xml:9-12`). Add exclusion rules. (C3)
- Key is pre-filled into a `TextField` with a show/hide toggle (`settings_screen.dart:72,693-702`) — visible on an unlocked device; acceptable for the user's own key but note it.
- Dead code: `SecretStore`/`SecureStorageSecretStore` (`secret_store.dart`) is unused (repo grep — only `credential_storage.dart`/`flutter_secure_storage_driver.dart` are wired); wire or delete.
- `app_failure.dart` declares `aiTimeout`/`aiBusy` but the API path can never raise them (`:128,142`; no sender timeout besides 15 s connect `user_gemini_api_provider.dart:307`); the Studio error banner can't distinguish quota-exceeded from generic error copy in one place (`message_studio_screen.dart:223-243` — fine, but no code-based recovery action).
- `MethodChannelGeminiNanoPlatform.generate` maps `PlatformException` to `providerError` with the raw platform message (`gemini_nano_platform.dart:145-149`) — contradicts the no-internal-strings rule of `user_gemini_api_provider.dart:220`; normalize codes client-side.

---

## 5. Acceptance criteria (top fixes)

**AC P1-1 (Nano fallback after credential failure)**
- Given: entitled Pro user, Nano `available`, stored key that returns 401;
- When: tap "Generate with AI";
- Then: generation succeeds via `gemini_nano` without any settings change, or the error banner offers "Use on-device AI (Gemini Nano)" which succeeds on tap.

**AC P1-2 (durable validation state)**
- Given: key validated "Connected" on day 1;
- When: user force-kills and reopens the app, opens Settings;
- Then: UI shows the last-verified state with a timestamp (never a bare green "Connected" claim), and re-verifies before generation.

**AC P2-1 (no unvalidated "Configured")**
- Given: no key in storage;
- When: user pastes a syntactically present but untested key and taps Save Key;
- Then: the key is either tested before save, or the badge reads as *unverified* and the first generation attempt explains the verification step.

**AC P2-2 (offline entitlement restore)**
- Given: an entitled user whose token expired while offline;
- When: app cold-starts with no connectivity;
- Then: AI remains usable from a locally persisted verified entitlement bounded by server-issued expiry, and re-verifies when connectivity returns.

---

## 6. Wallet-card summary

- **Isolation: PASS.** No path gates birthdays/people/calendar/history/reminders/delivery on AI state; test-proven for the free tier (audit H).
- **Validation:** live API ping only (no local heuristic) — good; but results are ephemeral (P1-2) and a bypass exists via unvalidated Save Key (P2-1).
- **Storage:** secure storage, never logged (logger redaction `app_logger.dart:43-54`), never app-synced (verified in `cloud_sync_service.dart` upload fields) — OS backup exclusion missing (P3).
- **Revoke:** exists (Remove Key → delete); but invalid key auto-locks AI incl. Nano (P1-1) and no revoked-key auto-detection.
- **Entitlement:** server-verified, fail-closed — correct policy, un-cached (P2-2).
- **Docs (SSOT) vs code:** one active mismatch — SSOT §5 Nano option unimplemented in UI (C4).