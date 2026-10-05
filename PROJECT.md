# Project: AI-Birthday Production Overhaul

## Architecture
AI-Birthday is a Flutter mobile application with a Firebase Cloud Functions backend.
- **Frontend Core**: Flutter (Dart) with Riverpod state management, Drift SQLite local database, `flutter_secure_storage` for key material, and `google_sign_in` + Firebase Auth REST for authentication.
- **On-Device AI Engine**: Native Android Kotlin platform channel (`com.yashsomani.ai_birthday/nano`) interfacing with Google Play Services AICore (`com.google.android.aicore`) on Android 14+ devices.
- **Cloud Infrastructure**: Firebase Cloud Functions (Node.js/TypeScript) for backend operations, Firestore for server-authoritative entitlement records (`/users/{uid}/entitlement/status`) and encrypted zero-PII cloud backup envelopes (`/users/{uid}/backup/envelope`).
- **Security & Privacy Boundary**: Client-side AES-256-GCM encryption ensures zero plaintext PII leaves the client device. Server-side rules enforce read-only entitlement access for clients and server-only writes.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---|---|---|---|
| F1 | Official Google Sign-In & Firebase Exchange | Exchange Google OAuth ID token for genuine Firebase Auth tokens via Identity Platform REST; persist session credentials securely. | M1 | ORIGINAL_REQUEST R1 |
| F2 | Pseudo-Auth & Fake OTP Purge | Eliminate in-memory OTP generator, fake dialogs, synthetic phone/email accounts, and obsolete test assertions. | M1 | ORIGINAL_REQUEST R1 |
| F3 | Logging PII Redaction (Auth & Common) | Redact email, UID, phone numbers, and recipient details from logger messages and parameter dictionaries. | M1 | ORIGINAL_REQUEST R1 |
| F4 | Cloud Function verifyPurchase | Implement Firebase Cloud Function validating Google Play purchase tokens and writing entitlement to Firestore. | M2 | ORIGINAL_REQUEST R2 |
| F5 | Firestore Entitlement Security Rules | Allow authenticated user read access to `users/{uid}/entitlement/status` while enforcing server-only writes. | M2 | ORIGINAL_REQUEST R2 |
| F6 | Server-Authoritative SubscriptionService | Client listens to Firestore entitlement stream; treats local storage strictly as offline fallback; removes "Simulate Pro" backdoor. | M2 | ORIGINAL_REQUEST R2 |
| F7 | Drift Database Schema Evolution | Add `version` (int) and `deletedAt` (nullable DateTime) to Birthdays table in `app_database.dart` for monotonic synchronization. | M3 | ORIGINAL_REQUEST R3 |
| F8 | Hardware-Derived AES-256-GCM Crypto Envelope | Client-side encrypt recipient birthday payloads using AES-256-GCM with hardware-secured keys before transmitting to Firestore. | M3 | ORIGINAL_REQUEST R3 |
| F9 | Deterministic Conflict Resolution & Restore | Implement version vector checks to prevent overwriting newer local edits with older backups; decrypt cleanly into Drift SQLite. | M3 | ORIGINAL_REQUEST R3 |
| F10 | Cloud Sync Zero-PII UI & Log Redaction | Update settings UI privacy notice; scrub all recipient names, phones, notes, and UIDs from sync logging pipelines. | M3 | ORIGINAL_REQUEST R3 |
| F11 | Android Manifest AICore Package Visibility | Add `<package android:name="com.google.android.aicore" />` to `<queries>` in `AndroidManifest.xml` for Android 11+ visibility. | M4 | ORIGINAL_REQUEST R4 |
| F12 | Native Kotlin AICore MethodChannel Bridge | Implement state evaluation (`ready`, `downloading_model`, `unsupported_device`, `service_unavailable`) and inference delegation in `MainActivity.kt`. | M4 | ORIGINAL_REQUEST R4 |
| F13 | Flutter NanoState Integration & Canned Text Purge | Update `gemini_nano_platform.dart` to map typed states; purge canned mock birthday greeting from `DefaultGeminiNanoPlatform`. | M4 | ORIGINAL_REQUEST R4 |
| F14 | Message Studio Keyboard Occlusion Fix | Use `SingleChildScrollView`, dynamic `viewInsets.bottom` padding, and `scrollPadding` to keep editor and actions accessible with IME active. | M5 | ORIGINAL_REQUEST R5 |
| F15 | Flexible Wrap Button & Card Layouts | Replace rigid horizontal `Row` button layouts with `Wrap` or flexible responsive containers across dashboard, settings, people, history. | M5 | ORIGINAL_REQUEST R5 |
| F16 | Accessibility (Semantics) & 1.5x Font Scaling | Add descriptive `Semantics` nodes for TalkBack; verify layout integrity and zero RenderFlex overflows down to 360dp width and 1.5x font scale. | M5 | ORIGINAL_REQUEST R5 |
| F17 | Comprehensive Test Suite & Verification | Pass 100% of Flutter tests, 0 `flutter analyze` issues, 100% backend Vitest checks, plus Tier 1-4 E2E requirement test suite. | M-E2E / M-FINAL | ORIGINAL_REQUEST Acceptance Criteria |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| M1 | Production Google Authentication & Pseudo-Auth Purge | F1, F2, F3 (Auth & Gateway fixes, test suite update, log redaction) | none | PLANNED |
| M2 | Server-Side Subscription Verification | F4, F5, F6 (Cloud Function, Firestore security rules, client SubscriptionService) | M1 | PLANNED |
| M3 | End-to-End Encrypted Zero-PII Cloud Backup Envelope | F7, F8, F9, F10 (Drift schema, AES-GCM crypto, version vectors, log redaction) | M1 | PLANNED |
| M4 | Real Android AICore Native Bridge | F11, F12, F13 (Manifest queries, Kotlin MainActivity channel, Flutter NanoState, purge mock strings) | none | PLANNED |
| M5 | Comprehensive UI/UX, Responsive & Accessibility Remediation | F14, F15, F16 (Message Studio IME, Wrap layouts, Semantics, 360dp/1.5x scaling) | none | PLANNED |
| M-E2E | E2E Requirement Testing Track | F17 (Opaque-box test suite across Tiers 1-4, publish TEST_READY.md) | none (parallel) | DONE |
| M-FINAL | Final Milestone: 100% Test Pass & Adversarial Hardening | Phase 1 (100% pass flutter analyze, flutter test, npm test) + Phase 2 (Tier 5 Adversarial Coverage Hardening) | M1, M2, M3, M4, M5, M-E2E | PLANNED |

## Interface Contracts

### M1 Auth Gateway ↔ Presentation & Storage
- `LiveGoogleAuthGateway`:
  - `Future<SignInResult> signIn()` -> returns `SignInSuccess(AuthIdentity identity)`, `SignInUnavailable()`, or `SignInFailed(String message)`.
  - `Future<void> signOut()` -> purges secure storage keys `auth_session_*`.
  - `Future<AuthIdentity?> getStoredIdentity()` -> returns valid session with `firebaseUid` and `idToken`.
- `authControllerProvider` publishes `AsyncValue<AuthState>`:
  - `AuthState.authenticated(AuthIdentity identity)`
  - `AuthState.unauthenticated()`

### M2 Backend Cloud Functions ↔ Client SubscriptionService
- `POST /verifyPurchase`:
  - Request JSON: `{ "contractVersion": 1, "purchaseToken": string, "productId": string, "packageName": "com.yashsomani.ai_birthday" }`
  - Headers: `Authorization: Bearer <firebaseIdToken>`
  - Response JSON: `{ "status": "active" | "expired" | "none", "productId": string, "expiryDateMs": number, "canUseAi": boolean }`
- Firestore Document: `/users/{uid}/entitlement/status`:
  - Fields: `{ "status": string, "productId": string, "expiryDateMs": number, "isAutoRenewing": boolean, "canUseAi": boolean, "verifiedAtMs": number, "updatedAt": timestamp }`
  - Rules: `allow read: if request.auth != null && request.auth.uid == uid; allow write: if false;`
- Client `subscriptionNotifierProvider` (`SubscriptionService`):
  - Listens to Firestore stream `/users/{uid}/entitlement/status` via `StreamSubscription`.
  - Publishes `UserEntitlement` (`proActive`, `freeTrial`, `gracePeriod`, `none`).

### M3 Drift Local Database ↔ Encrypted Cloud Sync Envelope
- Local SQLite (`Birthdays` table):
  - Columns: `id` (Text), `personId` (Text), `cycleYear` (Int), `date` (DateTime), `status` (Text), `draftId` (Text nullable), `createdAt` (DateTime), `updatedAt` (DateTime), `version` (Int, default 1), `deletedAt` (DateTime nullable).
- Cloud Envelope Document: `/users/{uid}/backup/envelope`:
  - Fields: `{ "schemaVersion": 1, "backupVersion": int, "deviceId": string, "iv": string (Base64), "ciphertext": string (Base64), "authTag": string (Base64), "updatedAt": timestamp }`
- Conflict Rule:
  - `cloudVersion > localMaxVersion` -> prompt or merge remote decrypted payload.
  - `localMaxVersion > cloudVersion` -> encrypt local payload and PUT/PATCH remote envelope.

### M4 Native Kotlin MainActivity ↔ Flutter AICore Platform Channel
- MethodChannel Name: `com.yashsomani.ai_birthday/nano`
- Methods:
  - `currentState()` -> returns typed String: `"ready"`, `"downloading_model"`, `"unsupported_device"`, or `"service_unavailable"`.
  - `startDownload()` -> launches model preparation; returns typed String.
  - `generate(prompt: String)` -> if ready, executes inference and returns String; if not ready, throws PlatformException(`NANO_UNAVAILABLE`, ...).

## Code Layout
- `lib/features/auth/`:
  - `application/auth_controller.dart`
  - `data/live_google_auth_gateway.dart`
  - `presentation/auth_bottom_sheet.dart`
- `lib/features/subscription/`:
  - `application/subscription_service.dart`
  - `data/subscription_repository.dart`
- `lib/features/sync/`:
  - `data/cloud_sync_service.dart`
  - `data/backup_crypto_service.dart` (new)
- `lib/core/database/`:
  - `app_database.dart`
  - `drift_repositories.dart`
- `lib/core/platform/`:
  - `gemini_nano_platform.dart`
- `lib/features/ai/`:
  - `data/gemini_nano_provider.dart`
  - `domain/ai_router.dart`
- `android/`:
  - `app/src/main/kotlin/com/yashsomani/ai_birthday/MainActivity.kt`
  - `app/src/main/AndroidManifest.xml`
- `backend/`:
  - `firestore.rules`
  - `functions/src/functions/index.ts`
  - `functions/src/transport/schemas.ts`
  - `functions/src/services/subscriptionVerification.ts`
- `lib/features/message_studio/presentation/message_studio_screen.dart`
- `lib/features/dashboard/presentation/dashboard_screen.dart`
- `lib/features/settings/presentation/settings_screen.dart`
- `lib/features/people/presentation/people_screen.dart`
- `lib/features/people/presentation/person_form_screen.dart`
- `test/`: Unit, widget, and integration tests
