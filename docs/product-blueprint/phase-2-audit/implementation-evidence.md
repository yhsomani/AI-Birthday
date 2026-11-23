# Phase 2 — Implementation Evidence Map

**Reviewed revision:** `ea475e0135a99d5dbae9891340c4a554f2e5fe15`  
**Classification:** source-present unless an explicit CI/test result is stated. Source presence does not prove live behavior.

## 1. Application composition

| Concern | Source evidence | Test evidence present in repository |
|---|---|---|
| Flutter entry and root composition | `lib/main.dart`, `lib/app/app.dart` | `test/widget_test.dart`, app tests |
| Navigation | `lib/app/router.dart`, `app_scaffold.dart` | `test/app/router_not_found_test.dart`, shell tests |
| State/dependency composition | `lib/app/providers.dart`, `lib/core/core_providers.dart` | Provider overrides used by feature tests |
| Theme and design system | `lib/app/theme/app_theme.dart`, `lib/shared/design_system/` | Contrast, responsive and golden tests |

At root startup, the app watches entitlement, auth, durable job worker and draft-prune providers. This is source-observed wiring, not proof of successful remote account restoration or background work on a physical device.

## 2. Product modules

| Module | Source anchors | Test anchors | Evidence status |
|---|---|---|---|
| Onboarding | `features/onboarding/presentation/onboarding_screen.dart` | onboarding widget test | Source and test locations present |
| People | `features/people/application/person_service.dart`, repositories, validators, import services and screens | people service, repository, CSV, contacts, validation and screen tests | Source and test locations present |
| Birthday lifecycle | `features/birthdays/domain/birthday_engine.dart`, lifecycle service | engine and lifecycle tests | Source and test locations present |
| Dashboard/calendar | `features/dashboard/`, `features/calendar/` | dashboard visibility and calendar tests | Source and test locations present; calendar responsive CI failure observed |
| Reminders | `features/reminders/application/`, `domain/`, Android notification gateway | scheduler, quiet-hours, persistence and gateway tests | Source and test locations present; exact alarm/reboot behavior still needs device validation |
| Message Studio | `features/message_studio/` | durable, optimistic, visibility and not-found tests | Source and test locations present |
| AI | `features/ai/domain/ai_router.dart`, prompt builder, BYOK and Nano providers | `test/ai/` | Source and test locations present; entitlement policy discrepancy recorded |
| Delivery | `features/delivery/` | WhatsApp handoff and SMS service tests | Source and test locations present; external send/delivery not established by a deep link |
| History | `features/history/presentation/history_screen.dart`, delivery event repository | history and repository tests | Source and test locations present |
| Auth | `features/auth/` | auth controller, adversarial and gateway tests | Source and test locations present; real provider/device validation not established |
| Subscription | `features/subscription/` | service, verification and gateway tests | Source and test locations present; backend CI did not reach its tests |
| Cloud backup | `features/sync/data/cloud_sync_service.dart` | cloud sync test suite | Source and test locations present; live Firestore behavior not independently verified |
| Durable jobs | `features/jobs/` | worker and handler tests | Source and test locations present |
| Settings | `features/settings/` | settings and reminder tests | Source and test locations present |

## 3. Local persistence

The current `AppDatabase` declares schema version **5** and six tables:

| Table | Main fields/responsibility | Important observed distinction |
|---|---|---|
| `Persons` | Recipient identity, birthday fields, contact details, relationship preferences, facts, delivery preferences | Has `version` and nullable `deletedAt` |
| `Birthdays` | Person ID, cycle year, occurrence date, status, optional draft ID | No `version` or `deletedAt` columns in current table definition |
| `MessageDrafts` | Birthday/person IDs, body, tone, length, status, provider, variation index | No `version` or `deletedAt` columns in current table definition |
| `ReminderSettingsEntries` | Enabled flag, reminder kinds and quiet-hours minutes | Keyed settings row |
| `DeliveryEvents` | Birthday ID, channel and handoff timestamp | Evidence of handoff, not confirmed external delivery |
| `Jobs` | Type/status/subject, payload, attempts, retry/error/result timestamps | Durable local job queue; payload is documented as non-secret |

The schema declares indexes for delivery-event birthday lookup, birthday/person lookup, draft/birthday lookup and job status. The code comment explicitly notes that only `Persons` currently has the full sync envelope.

### Data-model caveats

- Table relationships shown in diagrams are conceptual unless actual SQLite foreign-key constraints are verified.
- The repository contains two `Person` model definitions in different paths. Canonical ownership, field parity and round-trip compatibility require a separate trace.
- Cloud backup writes user-scoped Firestore records. The existing documentation itself states this is not an end-to-end encrypted, zero-PII envelope.
- Full restore, tombstone, conflict and malformed-record semantics need complete source/test tracing; this document does not claim all edge cases pass.

## 4. AI routing and entitlement

The current `AiRouter.generate` sequence is:

1. Read whether a user Gemini key exists.
2. Reject the request with `lockedAi` when `entitlement.canUseAi` is false.
3. With entitlement, use Nano when forced or when no user key exists and Nano is available.
4. Otherwise use the user's Gemini API provider when a key exists.
5. If no provider is available, return a typed credential-missing failure.

The ordering in the executable method means a configured BYOK key does **not** bypass the entitlement gate. `test/ai/ai_router_test.dart` contains a free-entitlement denial case and a Pro + user-key route case.

The source comment at the top of `ai_router.dart` and root `SSOT.md` disagree with this executable logic. See [discrepancy register](discrepancy-register.md).

## 5. Backend and Firebase surfaces

### Cloud Functions

The inspected Functions entry point exports callable functions for:

- Android installation registration and sender lease renewal
- Account mode changes
- Birthday/test claims and arm attempts
- Arm status and test outcome reporting
- Safe retry authorization
- Sender transfer
- Account deletion and deletion receipts
- Contact-derived state reset and Android sender release
- Coordination lifecycle and companion status
- Google Play purchase verification

It also exports scheduled deletion-drain and coordination-operation sweep functions. The source defines region `asia-south1`, App Check enforcement on common options and authenticated request checks. These are source contracts; deployment and IAM configuration are not proven by this inspection.

### Firestore rules

The inspected rules provide owner-scoped reads for entitlement, owner-scoped backup access, and deny client writes to entitlement documents. Backup writes are fenced during deletion. Coordination state and unknown collections fall through to server-only deny rules.

The presence of rule source does not prove deployed rules match this commit. The latest backend CI run did not reach emulator tests.

### Hosting

Firebase config points Hosting to `backend/hosting/public`, with a predeploy build. The hosting package contains public, privacy, terms, support and account-deletion pages and a separate test suite. The latest CI Hosting job passed, but production deployment is not established.

## 6. Native Android

`MainActivity.kt` registers MethodChannels for notifications, Nano, share, contacts and SMS automation. The repository also contains notification and SMS broadcast receivers and separate `play` / `personal` Android manifest flavors.

The existence of these paths is not proof of exact-alarm delivery, reboot recovery, Nano model availability, SMS permissions in a built artifact, or Play Store compliance on a published release. The latest CI skipped both APK builds.

## 7. External integrations

| Integration | Source evidence | What remains unverified |
|---|---|---|
| Google sign-in / Firebase identity | `features/auth/`, Firebase config | Live OAuth client config, signing fingerprints, session behavior |
| Google Play Billing | `features/subscription/`, backend verifier | Real sandbox purchase/restore/refund/renewal behavior |
| Gemini API | `UserGeminiApiProvider` | Live model availability, key quota/billing and response behavior |
| Gemini Nano | Kotlin bridge and Flutter platform adapter | Supported-device/model download and actual inference |
| Firestore backup | `CloudSyncService`, rules | Deployed rules, live write/restore, account deletion races |
| WhatsApp/SMS/share | Delivery builders/adapters and Android channels | External application behavior and actual user send/confirmation |
| Android notifications | Reminder scheduler and Kotlin receivers | Device permission, exact alarm, reboot and timezone behavior |

## 8. Evidence status

- **Source present:** confirmed for the paths listed.
- **Test files present:** confirmed for the paths listed.
- **Latest Flutter CI:** 459 passed, one responsive calendar failure.
- **Latest backend CI:** dependency install failure; backend tests not executed.
- **Latest Hosting CI:** passed.
- **Device/deployment verification:** not established by this audit.
