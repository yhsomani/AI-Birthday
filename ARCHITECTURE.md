# ARCHITECTURE

## 1. Purpose

This document describes the architecture that is actually wired in the repository. It is not a plan. Runtime behavior and source code are authoritative over older documentation.

## 2. Runtime architecture

```mermaid
flowchart TD
  UI[Flutter Screens / Widgets]
  STATE[Riverpod State + Controllers]
  DOMAIN[Application + Domain Services]
  REPO[Repository Abstractions]
  DB[Drift / SQLite]
  SEC[Flutter Secure Storage]
  AI[AiRouter]
  GEM[Gemini API / Gemini Nano]
  AUTH[Google Sign-In + Firebase Identity]
  CLOUD[Firestore REST Backup]
  BILL[Cloud Function + Google Play Developer API]
  WA[WhatsApp Click-to-Chat]
  NATIVE[Android Method Channels]
  ALARM[AlarmManager + Notifications]

  UI --> STATE
  STATE --> DOMAIN
  DOMAIN --> REPO
  REPO --> DB
  DOMAIN --> SEC
  DOMAIN --> AI
  AI --> GEM
  DOMAIN --> AUTH
  DOMAIN --> CLOUD
  DOMAIN --> WA
  DOMAIN --> NATIVE
  NATIVE --> ALARM
  BILL --> CLOUD
```

## 3. Source-of-truth ownership

| Concern | Runtime owner |
|---|---|
| Application routing | `lib/app/router.dart` |
| Dependency injection | `lib/app/providers.dart` + `lib/core/core_providers.dart` |
| Local persistence | `AppDatabase` / Drift SQLite |
| People persistence | `DriftPeopleRepository` |
| Birthday persistence | `DriftBirthdaysRepository` |
| Draft persistence | `DriftDraftsRepository` |
| Birthday cycle rollover | `BirthdayLifecycleService` |
| Reminder scheduling | Reminder application services + Android bridge |
| AI routing | `AiRouter` |
| Gemini BYOK | `UserGeminiApiProvider` + `SecureCredentialStorage` |
| Gemini Nano | `GeminiNanoProvider` + `MethodChannelGeminiNanoPlatform` |
| Authentication | `LiveGoogleAuthGateway` |
| Cloud backup/restore | `CloudSyncService` + Firestore security rules |
| Subscription entitlement | `SubscriptionNotifier` + `verifyPurchase` Cloud Function |
| Play verification | `GooglePlaySubscriptionClient` |
| WhatsApp handoff | `WhatsAppHandoffBuilder` |
| Native SMS automation | `BirthdaySmsService` + `AndroidNativeSmsGateway` (`BirthdaySmsReceiver`) |
| Logging | `core/loggerProvider` and `AppLogger` |

The `InMemory*Repository` implementations are test doubles. Production Riverpod providers are wired to Drift repositories.

## 4. Critical user flows

### Add and remember a birthday

`PeopleScreen`
→ `PersonFormScreen`
→ `PersonService` / `DriftPeopleRepository`
→ SQLite
→ `BirthdayLifecycleService`
→ birthday stream
→ dashboard/calendar/reminder scheduling.

When the birthday month/day changes, the current cycle status and draft are invalidated so a draft from a different date cannot remain attached to the new occurrence.

### AI message generation

`MessageStudioScreen`
→ `AiRouter`
→ entitlement check
→ user Gemini API key check
→ Gemini Nano when available without a user key
→ otherwise user Gemini API
→ `AiGenerationResult`
→ draft persistence.

AI entitlement is mandatory before either provider is used. AI failure does not invalidate non-AI birthday management.

### WhatsApp delivery

Draft
→ open WhatsApp / WhatsApp Business
→ user reviews and sends
→ user returns to AI-Birthday
→ explicit confirmation
→ birthday/draft status becomes completed.

Opening WhatsApp is not treated as delivery confirmation.

### Birthday cycle rollover

`birthdaysStreamProvider`
→ load current active people
→ `BirthdayLifecycleService.refresh`
→ create missing occurrences or roll stale occurrences into the next calendar year
→ preserve same-cycle completed drafts/status
→ expose the live birthday stream.

## 5. Authentication

Google Sign-In is implemented through `LiveGoogleAuthGateway`. A successful Google identity is exchanged with Firebase Identity Platform when Firebase configuration is present, then the resulting Firebase UID, ID token, and refresh token are stored in secure storage.

The gateway implements automated session refresh via Google's Secure Token API (`https://securetoken.googleapis.com/v1/token`). When a refresh token is expired or revoked (HTTP 400/401), the gateway invalidates stored credentials and triggers a clean signed-out transition.

## 6. Cloud backup

Cloud backup is opt-in and account-scoped.

The mobile client writes:

- `users/{uid}/people`
- `users/{uid}/birthdays`
- `users/{uid}/drafts`
- `users/{uid}/reminderSettings`

Firestore rules permit an authenticated owner to access only these backup collections under their own UID. Entitlement data and coordination state remain server-only.

The backup includes version/timestamp fields and compares `updatedAt` during restore to avoid replacing newer local data with older remote data.

**Important privacy boundary:** this implementation currently stores backup records as Firestore fields. It is not an encrypted zero-PII envelope implementation. The README and UI must not claim otherwise.

## 7. Subscription verification

The client never grants Pro merely because a purchase token is non-empty.

Flow:

Google Play purchase
→ purchase stream
→ pre-purchase callable reachability probe (blocking billing on 401/403/404/5xx)
→ authenticated server request (with optional Firebase App Check token)
→ Cloud Function
→ Google Play Developer API `subscriptionsv2`
→ verify product/package/account binding/state/expiry
→ write entitlement
→ client unlocks AI.

The verification endpoint supports Firebase authentication and Firebase App Check (via `X-Firebase-AppCheck` header and `enforceAppCheckOnPurchase` configuration).

## 8. AI provider routing

`AiRouter` is the only component allowed to select the AI provider.

Rules:

1. User personal Gemini API key exists (BYOK) → route to user Gemini (unlocked without application subscription).
2. No user key + Pro entitlement active + Gemini Nano is usable → route to on-device Nano.
3. No user key + No active Pro entitlement → AI locked.
4. Otherwise → truthful provider-unavailable error.

The user's Gemini key is never stored in the SQLite database or sent to Firestore backup.

## 9. Delivery boundaries
 
 WhatsApp uses a user-controlled deep-link handoff. No unofficial automation or background message sending is implemented.
 
-Android alarms and notifications are handled through platform services. The Flutter UI does not directly own alarm scheduling internals.
+SMS delivery is split by Gradle product flavors:
+- **Play Store flavor (`play`)**: Omits `android.permission.SEND_SMS` entirely for store policy compliance. SMS uses manual composer handoff (`url_launcher`).
+- **Personal sideload flavor (`personal`)**: Declares `android.permission.SEND_SMS` and uses `BirthdaySmsReceiver` with `SmsManager` for native background dispatch at 00:00 midnight in recipient's timezone.
+
+Strict safeguards apply to native automated SMS:
+1. Never auto-sends unreviewed AI drafts (`DraftStatus.approvedForAutoSms` required).
+2. Draft edits immediately invalidate approval and cancel pending alarms.
+3. Exact alarms schedule for midnight with a 12-hour stale alarm guard.
+4. Pending jobs are restored after reboots (`BOOT_COMPLETED`) and updates (`MY_PACKAGE_REPLACED`).
+
+Android alarms and notifications are handled through native platform services. The Flutter UI does not directly own alarm scheduling internals.

## 10. Security invariants

- API keys and session credentials stay in secure storage.
- Application logs redact credential and PII-like parameter keys.
- User backup collections are scoped by Firebase UID.
- Entitlement documents are readable by the owner but writable only by server code.
- Purchase entitlement is denied until the Play token is verified.
- The app never reports WhatsApp delivery without explicit user confirmation.
- Release builds no longer fall back to the debug signing key.

## 11. Known production risks

These are real unresolved items, not hypothetical tasks:

1. Android release signing requires `android/key.properties` or equivalent CI signing configuration; CI must provide it for a Play-ready artifact.
2. Gemini Nano availability depends on the native Android AICore bridge and device/model support.
3. Full Flutter/backend runtime verification must be executed in CI or a development environment; repository inspection alone is not evidence that every integration test passes.

## 12. Architectural rule

Prefer the smallest coherent implementation that preserves the core loop:

`REMEMBER → PREPARE → PERSONALIZE → REVIEW → SEND → CONFIRM → COMPLETE → RETURN`

Do not introduce a new service, state layer, cache, or abstraction unless it removes a real product or reliability problem.
