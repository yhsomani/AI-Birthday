# RelateAI Single Source of Truth

Last updated: 2026-07-09

This document records the historical Kotlin/Android repository state. The project is now being migrated to React Native. For the active replacement app, use:

- `README.md` for React Native run instructions.
- `src/App.tsx` for the React Native entrypoint.
- `docs/feature-fssot.md` for ideal feature behavior.
- `docs/feature-roadmap-analysis.md` for product prioritization.
- `docs/react-native-migration-status.md` for current React Native parity status.

The Android/Gradle sections below are retained as migration reference until the React Native app reaches verified feature parity and the legacy tree can be archived or removed intentionally.

## 1. Project Identity

RelateAI is a local-first Android relationship assistant. It syncs Google and device contacts, discovers relationship occasions, stores relationship context, drafts AI-assisted messages, routes messages through approval modes, schedules delivery, sends through SMS, WhatsApp, or Gmail SMTP, records activity, reports relationship health, and supports encrypted backup and restore.

Verified implementation facts:

| Area | Current value |
| --- | --- |
| Root Gradle project | `RelateAI` |
| Android application id | `com.aistudio.relateai.qxtjrk` |
| App namespace | `com.example` |
| Active modules | `:app`, `:core:model`, `:core:domain`, `:core:data`, `:core:ui` |
| Minimum SDK | 24 |
| Target SDK | 36 |
| Compile SDK | 37 |
| App version | `versionCode = 1`, `versionName = "1.0"` |
| Gradle wrapper | 9.4.1 |
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.2.10 |
| JDK toolchain | 21 |
| JVM bytecode target | 17 |
| Room schema version | 16 |
| Backup format version | 3 |
| Production theme mode | Dark-only validated theme |

There is no custom backend in this repository. External services are provider integrations used directly by the Android app: Firebase Auth, Google Sign-In, Google People API, Firebase Vertex AI or Google AI Gemini client, Gmail SMTP, Android SMS APIs, and Android Accessibility for optional WhatsApp automation.

## 2. Evidence Map

Primary evidence used:

| Area | Evidence paths |
| --- | --- |
| Build graph and toolchain | `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, module `build.gradle.kts` files |
| App runtime | `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/example/RelateAIApp.kt`, `app/src/main/java/com/example/MainActivity.kt` |
| Navigation | `app/src/main/java/com/example/ui/navigation/Screen.kt`, `NavGraph.kt`, `core/domain/src/main/kotlin/com/example/domain/navigation/RelateDeepLinks.kt` |
| UI and state | `app/src/main/java/com/example/ui/screens`, `app/src/main/java/com/example/ui/viewmodel` |
| Domain | `core/domain/src/main/kotlin/com/example/domain`, `core/model/src/main/kotlin/com/example/domain/model` |
| Data and integrations | `core/data/src/main/kotlin/com/example/core`, `core/data/src/main/kotlin/com/example/data/repository`, `core/data/src/main/kotlin/com/example/di` |
| Database | `core/data/src/main/kotlin/com/example/core/db/AppDatabase.kt`, `core/data/schemas/com.example.core.db.AppDatabase/16.json` |
| Android resources | `app/src/main/res`, `core/data/src/main/res` |
| Tests | `app/src/test`, `app/src/androidTest`, `core/model/src/test`, `core/domain/src/test`, `core/data/src/test` |
| CI and release gates | `.github/workflows/android.yml`, `app/build.gradle.kts`, `app/src/test/java/com/example/ProductionReadinessConfigTest.kt` |
| Existing docs | Root Markdown files and `docs/**`, validated against source where possible |

Generated build output, Gradle caches, and local diagnostics are not treated as product documentation unless a checked-in source or test references them as an intentional artifact.

## 3. Repository Layout

```text
.
+-- app/                         Android application, Compose screens, ViewModels, manifest, app resources, app tests
+-- core/
|   +-- model/                   Pure Kotlin model/value module
|   +-- domain/                  Use cases, policies, repositories, service contracts, mappers; also currently Room entities
|   +-- data/                    Room database, DAOs, repositories, workers, integrations, senders, prefs, backup
|   +-- ui/                      Shared Compose theme, tokens, and components
+-- docs/                        Historical and supporting documentation now consolidated by this SSOT
+-- gradle/                      Wrapper and version catalog
+-- scripts/                     Helper scripts
+-- .github/workflows/           Android CI
+-- SSOT.md                      This document
```

Important source roots:

| Root | Purpose |
| --- | --- |
| `app/src/main/java/com/example/ui/screens` | Compose screen implementations |
| `app/src/main/java/com/example/ui/viewmodel` | Hilt ViewModels and UI state |
| `app/src/main/java/com/example/ui/navigation` | App routes and navigation graph |
| `core/model/src/main/kotlin/com/example/domain/model` | Pure domain data models and enums |
| `core/domain/src/main/kotlin/com/example/domain` | Domain policies and use cases |
| `core/data/src/main/kotlin/com/example/core/db/entities` | Current Room entities and persistence schema models |
| `core/data/src/main/kotlin/com/example/core/db` | Room database, SQLCipher open path, migrations |
| `core/data/src/main/kotlin/com/example/core/automation` | WorkManager, alarms, notifications, dispatch and senders |
| `core/data/src/main/kotlin/com/example/core/gemini` | AI prompt, parser, rate limit, Gemini client |
| `core/data/src/main/kotlin/com/example/core/backup` | Backup DTOs, encryption, import/export service |
| `core/ui/src/main/kotlin/com/example/core/ui` | Design system |

## 4. Architecture Summary

The intended dependency direction is:

```text
Compose UI and ViewModels (:app)
    -> domain use cases, repository/service contracts, policies (:core:domain)
    -> pure shared models (:core:model)
    -> data implementations, Room, external integrations (:core:data)
```

Actual Gradle dependencies:

| Module | Current purpose | Key dependencies |
| --- | --- | --- |
| `:app` | Android app, Compose UI, navigation, ViewModels, widgets, tests | `:core:domain`, `:core:data`, `:core:ui`, Hilt, Compose, Firebase, WorkManager, SQLCipher, JavaMail, Room runtime |
| `:core:model` | JVM-only shared model/value types | Kotlin JVM, JUnit tests |
| `:core:domain` | JVM-only use cases, policies, repository/service contracts, pure mappers | Kotlin JVM, `api(:core:model)`, coroutines, javax.inject |
| `:core:data` | Room, repositories, workers, contacts, AI, backup, prefs, auth, senders | `api(:core:domain)`, Hilt, Room/KSP, WorkManager, Firebase, Google auth, OkHttp/Retrofit/Moshi, SQLCipher, JavaMail |
| `:core:ui` | Shared Compose theme and UI primitives | Compose Material 3, Navigation Compose, Lifecycle Compose, Coil |

Architectural caveat: `:core:domain` is now a Kotlin/JVM module. Its main source is free of Android, AndroidX, Room entity, DAO, and Paging imports, and Room entities/DAO projections live in `:core:data`.

Dependency injection:

- `RelateAIApp` is annotated with `@HiltAndroidApp`.
- `AppModuleBinds` binds repositories such as `ContactRepository`, `EventRepository`, `MessageRepository`, `StyleProfileRepository`, `MemoryNoteRepository`, `GiftHistoryRepository`, `ActivityLogRepository`, `MessageFeedbackRepository`, `DispatchAttemptRepository`, and `DiagnosticSnapshotRepository`.
- `AppModule` provides `AppDatabase`, DAOs, `SecurePrefs`, `OkHttpClient`, `AuthManager`, Firebase Vertex `GenerativeModel`, and `GeminiClient`.
- `ServiceModule` binds `AiService`, `PreferencesRepository`, contact sync, dispatcher, test-send, scheduler, event reminder scheduler, notification, backup, and analytics report services.
- WorkManager uses Hilt worker integration through `HiltWorkerFactory`.

## 5. Technology Stack

| Category | Technologies |
| --- | --- |
| Language/build | Kotlin 2.2.10, Gradle Kotlin DSL, JDK toolchain 21, JVM target 17 |
| Android | AGP 9.2.1, compileSdk 37, minSdk 24, targetSdk 36 |
| UI | Jetpack Compose, Material 3, Navigation Compose, lifecycle Compose, Coil |
| DI | Hilt 2.59.2, Hilt WorkManager |
| Persistence | Room 2.7.0, SQLCipher 4.5.4, AndroidX SQLite, schema export in `core/data/schemas` |
| Preferences/security | AndroidX Security Crypto, EncryptedSharedPreferences, Android Keystore-backed MasterKey |
| Auth/cloud | Firebase Auth, Google Sign-In, Firebase Analytics, Firebase Vertex AI |
| AI | Firebase Vertex AI `gemini-1.5-flash`; optional user API key path via Google AI client |
| Contacts | Google People API via OkHttp, Android ContactsProvider |
| Scheduling | WorkManager, AlarmManager exact alarms with WorkManager fallback, boot/time recovery receivers |
| Delivery | Android SMS, Gmail SMTP through JavaMail, WhatsApp AccessibilityService |
| Serialization/network | Moshi, OkHttp, Retrofit dependency present |
| Testing | JUnit, Robolectric, MockK, coroutines test, Room testing, Compose UI tests, Roborazzi, Android instrumented smoke tests |
| Coverage | JaCoCo aggregate `jacocoDebugUnitTestReport` |

## 6. Runtime Surfaces

Primary bottom navigation:

- Home
- Contacts
- Events
- Messages
- Analytics

Secondary routes:

- Splash
- Onboarding
- Auth
- Contact Detail
- Wish Preview
- Settings
- Activity History
- Style Coach
- Backup/Restore
- Automation Setup / AI Doctor
- Chat History
- Memory Vault
- Gift Advisor

Deep links:

| URI shape | Destination |
| --- | --- |
| `relateai://home` | Home |
| `relateai://contacts` | Contacts |
| `relateai://contact/{contactId}` | Contact Detail |
| `relateai://messages` | Messages |
| `relateai://wish/{contactId}/{messageRef}` | Wish Preview |
| `relateai://settings` | Settings |
| `relateai://backup-restore` | Backup/Restore |

Static shortcuts:

- `compose_message` -> `relateai://messages`
- `view_contacts` -> `relateai://contacts`

Widget:

- `BirthdayWidgetProvider` shows today birthday count, names, next events, and pending approval count.
- Widget tap routes to Messages when pending approvals exist; otherwise it routes to Home.

## 7. Data Model

Current Room database:

| Table | Purpose |
| --- | --- |
| `contacts` | Contact aggregate with Google/device identity, communication methods, relationship metadata, preferences, gift budgets, health, personalization JSON fields, lifecycle flags |
| `events` | Current occasion/event table for birthdays, anniversaries, work anniversaries, custom/manual events, synthetic holiday/revival/follow-up events |
| `pending_messages` | Generated drafts and approval/schedule state; unique `(contactId, eventId, scheduledYear)` |
| `sent_messages` | Sent history, delivery status, AI metadata, reply marker |
| `style_profiles` | Current learned user writing style |
| `style_profile_history` | Snapshots of learned style |
| `memory_notes` | Contact memory notes, categories, pinned state |
| `gift_history` | Contact gift records |
| `activity_logs` | User-visible operational activity history |
| `message_feedback` | Regeneration feedback and instructions |
| `dispatch_attempts` | Durable send attempt, eligibility, retry/dead-letter, and provider result history |
| `diagnostic_snapshots` | Local redacted AI Doctor and health diagnostics; excluded from backup |

Current migration chain:

- `AppDatabase` is at version 16.
- Migrations exist from 2 through 16.
- SQLCipher open path uses `SupportFactory`.
- `core/data/schemas/com.example.core.db.AppDatabase/16.json` is the active schema export.
- Legacy app-level schema exports under `app/schemas` are ignored local/generated artifacts, not the active schema authority.

Important current relationships:

- `pending_messages.contactId` cascades on contact delete.
- `pending_messages.eventId` participates in uniqueness but is not a Room foreign key in version 16.
- `sent_messages.contactId` sets null on contact delete.
- `dispatch_attempts` links to pending message ids and uses nullable contact/occasion references.
- `diagnostic_snapshots` are local diagnostics and are not exported/imported by backup.

Target but not implemented:

- First-class `occasions` and `message_drafts` tables are design targets in architecture docs.
- Room entities should move out of `:core:domain` or into a dedicated database/data boundary.

## 8. State Management

The app uses Hilt ViewModels plus Kotlin `StateFlow`/`MutableStateFlow` for screen state. Compose screens observe ViewModel state and route callbacks through `NavGraph`.

Key examples:

- `HomeViewModel` combines dashboard metrics, setup readiness inputs, domain-derived setup progress, domain-derived backup freshness, and domain-ranked next actions.
- `ContactListViewModel` owns search, filters, sort, and contact quality labels.
- `EventsViewModel` owns event filters, manual event saves, duplicate/conflict warnings, and conflict resolution.
- `MessagesViewModel` owns tabs, channel filter, sort, selection, approve/reject/retry/revoke, and domain-derived route/status/dispatch-window readiness labels.
- `WishPreviewViewModel` owns selected variant, edited text, test send, feedback, regeneration, approve/reject, review-next queue, and why signals; draft text readiness and send-summary route choice/route setup/device setup/quiet-hours and blackout dispatch projection are domain-derived.
- `AutomationSetupViewModel` computes AI Doctor readiness check details and diagnostic snapshots; account/provider checks, quality checks, system readiness checks, summary classification, setup progress, and recommended-fix ranking use shared domain policies.
- `BackupRestoreViewModel` owns passphrase strength, export, import preview, and replace restore confirmation.

Known state-management debt:

- Operational readiness logic still exists across remaining dispatch/recovery timing surfaces outside Messages scheduled readiness, exact-send scheduling, exact-send stale-dispatch recovery, SMS stale delivery-status recovery, event-reminder scheduling, foreground/worker dispatch exception failure classification, SMS callback delivery/attempt outcome mapping, and the Wish Preview review summary, plus remaining notification surfaces outside typed approval/setup/system-alert/revival/event-reminder requests. `DeliveryRouteReadinessPolicy`, `AutoSendChannelSelector`, `DispatchEligibilityPolicy`, `ExactSendSchedulePolicy`, `ExactSendRecoveryPolicy`, `SmsDeliveryStatusRecoveryPolicy`, `EventReminderSchedulePolicy`, `DispatchExceptionFailurePolicy`, `SmsCallbackOutcomePolicy`, `MessageOperationalReadinessPolicy`, `WishDraftReadinessPolicy`, `WishPreviewSendSummaryPolicy`, `ApprovalNotificationActionPolicy`, `SetupNotificationRequest`, `SystemAlertNotificationRequest`, `RevivalNotificationRequest`, `SetupAccountProviderReadinessPolicy`, `SetupAutomationReadinessPolicy`, `SetupChannelReadinessPolicy`, `SetupEmailReadinessPolicy`, `SetupQualityReadinessPolicy`, `SetupSystemReadinessPolicy`, `SetupReadinessSummaryPolicy`, `SetupReadinessProgressPolicy`, `SetupReadinessRecommendationPolicy`, and `HomeNextActionPolicy` improved sharing for route prerequisites, history-aware route selection and final dispatch fallback ordering, exact-send scheduling, exact-send stale-dispatch recovery classification, SMS stale delivery-status recovery cutoff/status decisions, event-reminder cancel/exact/inexact scheduling decisions, foreground/worker dispatch exception final-failure classification, SMS callback delivery/attempt outcome mapping, already-handled `SENT`/`DISPATCHING` dispatch blockers, Messages route/status/scheduled-time/allowed-window readiness, Wish Preview draft validation/send summary/route choice/route setup/device setup/quiet-hours and blackout dispatch context, approval notification actions, dispatch/AI-provider/revival-AI/exact-alarm setup notification request reasons and Android helper payloads, AI-fallback/stale-backup system alert reasons and Android helper payloads, revival notification request payloads, AI Doctor account/provider readiness, AI Doctor automation prerequisites, SMS/WhatsApp setup channel readiness, channel verification routing, AI Doctor email setup readiness, AI Doctor quality readiness, AI Doctor system readiness, setup summary/progress, AI Doctor fix ranking, Home readiness banner classification, and Home next-action ranking, but there is not yet one complete readiness use case for all surfaces.
- Approval notification display copy and domain-to-data message-variant mapping now use `ApprovalNotificationAdapters` and `ApprovalNotificationCopy` after workers or the notification service build `ApprovalNotificationRequest`; remaining notification debt excludes this covered helper path.
- Revival notification display copy now uses `RevivalNotificationAdapters` and `RevivalNotificationCopy` after `RevivalWorker` builds `RevivalNotificationRequest`; remaining notification debt excludes this covered helper path.
- Event-reminder notification display copy now uses `EventReminderNotificationAdapters` and `EventReminderNotificationCopy` after `EventReminderReceiver` builds `EventReminderNotificationRequest`; remaining notification debt excludes this covered helper path.
- Initial recurring automation scheduling and boot recovery now share `BootRecoveryRecurringWorkCommand` and `enqueueRecurringAutomationWork` for daily trigger, revival, and style-analysis periodic work definitions.
- Several ViewModels are large and own mixed workflow/presentation logic.

## 9. Authentication and Authorization

Implemented:

- Google Sign-In and Firebase Auth through `AuthManager` and `AuthViewModel`.
- Splash routes to onboarding, auth, or Home based on onboarding/auth state.
- Authenticated routes use `RequireSignedIn` in `NavGraph`.
- No guest/demo/local-only mode is implemented.
- Sign-out cancels WorkManager work, cancels notifications, clears Room tables, closes/resets the database, clears secure prefs, clears cached database key material, deletes database files, signs out Firebase, and attempts Google access revocation.

Runtime lock:

- `MainActivity` gates UI with biometric lock when the preference is enabled.
- Biometric availability and session state are resolved by `BiometricLockPolicy`.

Limitations:

- Google sign-in is required before authenticated app use.
- Local-only use without Google/Firebase is not implemented.
- Sign-out table cleanup uses `database.clearAllTables()` and the source comment now describes wiping all Room tables without a stale table count.

## 10. Configuration

Repository configuration:

| Config | Purpose |
| --- | --- |
| `.env.example` | Documents `GEMINI_API_KEY` for local AI key configuration |
| `app/google-services.json` | Approved Firebase/Google client config intentionally tracked for the checked-in app id |
| `app/src/debug/google-services.json` | Approved debug Firebase/Google client config intentionally tracked for the checked-in app id |
| `app/src/main/res/xml/network_security_config.xml` | Pins Google/Firebase/Gmail domains until `2027-06-01` |
| `app/src/main/res/xml/data_extraction_rules.xml` | Excludes sensitive stores from cloud backup and device transfer |
| `app/src/main/res/xml/backup_rules.xml` | Legacy backup exclusions for sensitive stores |
| `app/src/main/res/xml/accessibility_service_config.xml` | WhatsApp AccessibilityService scope and event configuration |
| `app/src/main/res/xml/shortcuts.xml` | Static launcher shortcuts |
| `app/src/main/res/xml/widget_birthday_info.xml` | Birthday widget metadata |
| `app/src/main/res/xml/analytics_export_paths.xml` | FileProvider paths for analytics export |

Environment variables:

| Variable | Required for | Source |
| --- | --- | --- |
| `GEMINI_API_KEY` | Optional local Google AI SDK path through secrets/env setup | `.env.example`, `SecurePrefs` stores user-entered key |
| `KEYSTORE_PATH` | Release signing | `app/build.gradle.kts` |
| `STORE_PASSWORD` | Release signing | `app/build.gradle.kts` |
| `KEY_ALIAS` | Release signing | `app/build.gradle.kts` |
| `KEY_PASSWORD` | Release signing | `app/build.gradle.kts` |

Provider config policy: `.gitignore` ignores generic `google-services.json` files and explicitly allowlists only `app/google-services.json` and `app/src/debug/google-services.json`. Service account JSON, private keys, OAuth access or refresh tokens, client secrets, signing material, SMTP credentials, Gemini keys, and local Firebase variants must stay untracked.

Secure runtime preferences include OAuth token, Gemini API key, Gmail sender address/password, last successful email test, global automation mode, theme mode, blackout dates, quiet hours, channel blackout, WhatsApp automation consent, biometric lock, reminders, AI generation, sync token, onboarding state, backup timestamps, sync errors, and secure-pref recovery notices.

Default automation mode:

- `SecurePrefs` and `GlobalAutomationModePrefsMapper` default global automation to `ALWAYS_ASK`.
- `BackupPreferencesDto.defaults()` also defaults `globalAutomationMode` to `ALWAYS_ASK` in the current working tree, so backup preference fallback aligns with the review-first default.

## 11. Build, Test, and Release

Android Gradle tasks require a full JDK 21/JBR whose `bin` directory contains `jlink`.
On Windows, configure the IDE Gradle JDK or `JAVA_HOME` to Android Studio JBR or
Temurin JDK 21. Do not run Android builds through the Antigravity/Red Hat extension
JRE path, because AGP's `JdkImageTransform` needs `jlink`.

Local build commands:

```bash
# Full debug gate used by docs/release checklist
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:model:test :core:domain:test testDebugUnitTest lintDebug assembleDebug --no-configuration-cache

# Unit tests
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:test testDebugUnitTest --no-configuration-cache

# Screenshot verification
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:verifyRoborazziDebug -Pscreenshot --tests 'com.example.ui.screenshots.*' --no-configuration-cache

# Coverage
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew jacocoDebugUnitTestReport --no-configuration-cache

# Debug APK
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleDebug --no-configuration-cache

# Release build, requires signing environment variables
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleRelease --no-configuration-cache
```

Release signing:

- Release artifact tasks validate `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
- Missing/invalid signing variables fail release artifact requests with a clear Gradle exception.
- Release builds enable R8 minification and resource shrinking.

CI:

- GitHub Actions workflow `Android CI` runs on pushes and pull requests to `main` and `master`.
- Pull requests run Dependency Review, failing on moderate-or-higher vulnerabilities and GPL/LGPL/AGPL denied licenses.
- CI sets up JDK 21, runs `:core:domain:test testDebugUnitTest lintDebug assembleDebug`, verifies Roborazzi screenshots, verifies `ProductionReadinessConfigTest`, generates coverage, verifies release signing guard failure, and uploads reports/APK artifacts.

Test inventory verified by file count:

- 194 Kotlin test files under app, Android test, core model, core domain, and core data test roots.
- 1145 `@Test` annotations across those Kotlin test files.
- 177 approved Roborazzi baseline PNGs under `app/src/test/screenshots/baseline`.

This document update itself was not a code behavior change. Run the above gates before release or before deleting migrated docs.

## 12. Security, Privacy, and Reliability

Implemented controls:

- SQLCipher-backed Room database.
- Fresh installs generate random 256-bit database key material and store it in Keystore-backed encrypted preferences.
- Existing database files with missing cached key material may use legacy identifier-derived recovery.
- EncryptedSharedPreferences stores secrets and configuration.
- Legacy plaintext database files are quarantined before opening the encrypted database.
- Android auto backup is disabled in the manifest and sensitive stores are also excluded in backup rules.
- Exported backups use AES-GCM with PBKDF2-HMAC-SHA256 passphrase derivation.
- Backup import validates decrypt/authentication, JSON, version, size limit, and manifest checksum before restore.
- Logs route through `StructuredLogger`, which redacts messages/extras, key-aware generated message body fields, and inline body assignments while keeping an in-memory bounded history.
- `ProductionReadinessConfigTest` blocks direct Android `Log.*` usage outside `StructuredLogger`.
- Network security config pins Google/Firebase/Gmail-related domains until `2027-06-01`; release guard warns/fails inside the 60-day support gate.
- WhatsApp automation requires app-level consent and the Android AccessibilityService enablement path.
- Boot/time/package replacement receivers reschedule work rather than sending directly.

Privacy-sensitive data handled:

- Contacts, phone numbers, email addresses, birthdays/events, relationship notes, memory notes, gift history, generated drafts, sent message history, preferences, diagnostics, and backup payloads.
- AI prompts may include relationship context, event context, memory notes except private notes, gift history, style profile, and previous wishes.
- OAuth tokens, API keys, Gmail app passwords, SQLCipher key material, and backup passphrases must not appear in logs, exports, analytics reports, or documentation.

Release blockers and risks:

- WhatsApp Accessibility automation requires Play policy review and release-owner signoff.
- Data Safety and privacy policy text are not present in this repository as final public release artifacts.
- Approved app/debug `google-services.json` client configs are explicitly allowlisted; other local provider variants and server-side secrets must remain untracked.
- The current local diagnostics are not remote monitoring. There is no backend observability pipeline.

## 13. Feature Catalog

Status definitions:

- Fully Implemented: source, UI or service path, tests or direct wiring exist.
- Partially Implemented: source exists but known important behavior or target design is incomplete.
- Planned: documented target but not implemented in current source.
- Documentation Only: present only in docs, not current product source.
- Broken or Incomplete: source or docs show a current conflict or release blocker.

### 13.1 App Shell, Navigation, Lock, Deep Links

Status: Fully Implemented.

Purpose: Provide app entry, biometric gate, bottom navigation, permission rationale, authenticated routes, deep links, shortcuts, and widget click-through.

How it works:

- `RelateAIApp.onCreate()` checks pin expiry, creates notification channels, starts health diagnostics, warms DB key and secure prefs, and schedules recurring workers outside Robolectric.
- `MainActivity` owns edge-to-edge Compose, biometric gate, SMS/notification permission rationale, and bottom navigation.
- `NavGraph` declares all routes and wraps authenticated routes in `RequireSignedIn`.
- `DeepLinkContractTest` verifies external deep-linked signed-in routes use the auth gate and `MainActivity` renders the nav graph only from the unlocked biometric branch.
- `RelateDeepLinks` defines internal deep-link URI patterns.

Dependencies: Firebase Auth for route gating, `SecurePrefs` for lock preference, Biometric APIs, Navigation Compose.

Related files: `RelateAIApp.kt`, `MainActivity.kt`, `Screen.kt`, `NavGraph.kt`, `RelateDeepLinks.kt`, `shortcuts.xml`, `BirthdayWidgetProvider.kt`.

Limitations: Contacts permission is not requested by the core permission rationale; it is handled through sync/setup flows.

### 13.2 Splash, Onboarding, Authentication, Sign-Out

Status: Fully Implemented, with local-only mode Not Implemented.

Purpose: Route first launch, mark onboarding completion, sign in through Google/Firebase, and securely clear local data on sign-out.

User flow: Splash -> Onboarding when not complete -> Auth -> Home or Automation Setup based on onboarding action. Existing signed-in users go to Home.

Dependencies: Firebase Auth, Google Sign-In, `SecurePrefs`, `AuthManager`, `AppDatabase`.

Limitations: No guest/local-only mode. Google/Firebase configuration correctness cannot be proven from static source alone.

### 13.3 Home Dashboard

Status: Fully Implemented.

Purpose: Daily command center for upcoming events, pending approvals, setup progress, backup freshness, low-health relationships, and next actions.

How it works: `HomeViewModel` combines dashboard metrics, setup readiness inputs, backup timestamp, upcoming previews, pending counts, and contact health into planner items. `SetupReadinessProgressPolicy` computes setup-progress counts; `HomeNextActionPolicy` computes readiness banner classification, backup freshness, and ranked primary/supporting actions.

Dependencies: `GetDashboardMetricsUseCase`, contact/event/message repositories, preferences, setup summary helpers.

Limitations: Home next-action ranking is now domain-derived, but it is not yet a shared cross-feature action service used by Messages, AI Doctor, notifications, or Activity History.

### 13.4 Contacts and Contact Sync

Status: Fully Implemented.

Purpose: Import, merge, search, filter, and enrich contacts for personalization and delivery.

How it works:

- `GoogleContactsSync` fetches People API connections through the injected shared `OkHttpClient` and `PeopleConnectionsRequestFactory`, using cached/refreshed OAuth tokens, page tokens, and sync tokens.
- `DeviceContactsReader` reads ContactsProvider rows when `READ_CONTACTS` is granted.
- `SyncContactsUseCase` merges Google and device contacts by phone/email/name, gives Google records precedence, fills missing fields, infers relationship from contact group, upserts local contacts, then runs event discovery.
- Contact list supports search/filter/sort and contact quality labels.
- Contact Detail owns essentials, personalization, automation preferences, and links to wish generation, Memory Vault, Gift Advisor, and Chat History.

Dependencies: Google Sign-In OAuth scope, People API, ContactsProvider permission, Room repositories.

Limitations: Calendar provider sync is not implemented. Source identity normalization into separate contact source/method tables is planned, not implemented.

### 13.5 Contact Classification and Relationship Health

Status: Fully Implemented.

Purpose: Improve relationship metadata and compute health/analytics state.

How it works:

- `ClassifyContactUseCase` uses `AiService.classifyContact` and persists relationship type, subtype, language, formality, communication style, and confidence.
- `RefreshHealthScoresUseCase` computes a 0-100 score from interaction frequency, recency, consecutive wishes, and stale/no-wish penalty.

Dependencies: Gemini, contact repository, model mappers.

Limitations: Health scoring is deterministic but basic. There is no single product-wide personalization quality score yet.

### 13.6 Events and Reminders

Status: Fully Implemented for contact-derived birthdays, anniversaries, work anniversaries, and manual/custom events. Target `occasions` schema is Planned.

Purpose: Discover relationship occasions, let users add manual events, expose duplicate/conflict states, and schedule reminders.

How it works:

- `DiscoverEventsUseCase` builds events from contact birthday, anniversary, and work-start dates.
- `SaveManualEventUseCase` validates date/type/contact, creates manual contacts when needed, detects duplicates/conflicts, upserts occasions into the current `events` table, and schedules reminders.
- `EventResolutionPolicy` marks duplicate/date-conflict groups and supports keep-separate source suffixes.
- `EventReminderScheduler` and `EventReminderReceiver` handle reminders.

Dependencies: contact/event repositories, `EventDatePolicy`, `EventIdentityPolicy`, `EventReminderSchedulerService`.

Limitations: Current storage table is still named `events`. Target first-class `occasions` persistence is not implemented.

### 13.7 AI Message Generation and Regeneration

Status: Fully Implemented.

Purpose: Generate personalized drafts using contact, event, style, memory, gift, and previous-message context.

How it works:

- `GenerateMessageUseCase` prevents duplicate pending rows per event occurrence, loads contact/event/style/history/memory/gift context, calls `AiService.generateMessage`, retries up to two times when the standard variant is too similar to prior wishes, applies AI fallback alerting, resolves approval mode, runs the AI auto-send quality gate, selects an available channel, schedules automatic dispatch when eligible, and sends review notifications when needed.
- `RegeneratePendingMessageUseCase` regenerates with optional feedback and fallback handling.
- `PromptBuilder`, `ResponseParser`, `RateLimiter`, `GeminiClient`, and `AiServiceImpl` own provider prompting/parsing.

Dependencies: Gemini, Firebase Vertex AI, optional Google AI API key, repositories, preferences, scheduler, notification service.

Limitations: AI quality scoring is heuristic. Runtime provider access and quotas are not verifiable from source.

### 13.8 Wish Preview

Status: Fully Implemented.

Purpose: Review one generated draft, choose variants, edit text, submit feedback, regenerate, test-send, approve, reject, and move to the next review item.

How it works: `WishPreviewViewModel` observes `WishPreviewDraft`, tracks selected variant and edited text, computes draft text readiness through `WishDraftReadinessPolicy`, projects event/channel/route choice/route setup/device setup/schedule/approval/fallback plus quiet-hours and blackout dispatch context through `WishPreviewSendSummaryPolicy`, builds why signals, and invokes test-send, regeneration, approval, and rejection use cases.

Dependencies: message repository, event repository, feedback repository, preferences repository, `WishPreviewDeviceReadinessReader`, test send, regenerate, approve, reject use cases.

Limitations: Draft text readiness, preferred-vs-fallback route choice, route setup, SMS/WhatsApp device setup, quiet-hours/blackout dispatch context, approval/setup/system-alert/revival/event-reminder notification reasons and payloads, and send-summary projection are shared, but quality, remaining notification surfaces outside those typed requests, and end-to-end dispatch readiness are not yet one shared review/dispatch readiness model.

### 13.9 Messages Queue and Approval Lifecycle

Status: Fully Implemented.

Purpose: Operational queue for pending, scheduled/approved, blocked, failed, and sent messages.

How it works:

- `MessagesViewModel` observes pending/sent read models, channel filter, sort, search, selection, domain-derived readiness, and bulk actions.
- Users can approve, reject, retry failed messages, revoke approval, bulk approve, bulk reject, and bulk retry.
- Readiness labels use `MessageOperationalReadinessPolicy`, which combines route blockers from `DeliveryRouteReadinessPolicy` with pending-message status.

Dependencies: message repository, contact repository, preferences, approve/reject/retry/revoke use cases, activity log repository.

Limitations: Readiness and recovery state are partly duplicated with AI Doctor and dispatch policies.

### 13.10 Scheduling, Workers, and Automation

Status: Fully Implemented for current scheduling and worker chain; automatic retry execution beyond scheduled retry metadata is Partially Implemented.

Purpose: Keep contact sync, event discovery, message generation, dispatch, style analysis, revival, holiday wishes, and follow-up work running safely.

How it works:

- `WorkerScheduler.scheduleAll()` schedules daily trigger, daily automation chain, revival worker, style analysis worker, exact-send recovery, SMS status recovery, and event reminders; daily trigger, revival, and style-analysis periodic work reuse the same `BootRecoveryRecurringWorkCommand` definitions as boot recovery.
- Daily automation chain runs contact sync -> event discovery -> message generation -> holiday wishes -> post-event follow-up.
- `DailyScheduler` schedules exact alarm sends when available or WorkManager delayed fallback when exact alarms are unavailable.
- `BootReceiver` recovers exact sends, SMS delivery status, event reminders, and recurring work after boot/package/time changes.
- `DispatchEligibilityPolicy` blocks early sends, defers quiet hours/blackout windows, requires approvals, expires VIP approvals after window, and blocks terminal states.

Dependencies: WorkManager, AlarmManager, BootReceiver, `SecurePrefs`, Room DAOs, domain policies.

Limitations: ADR 0003 says automatic retry execution remains pending. Manual retry paths and retry metadata exist.

### 13.11 Delivery Channels

Status: Fully Implemented, with policy/runtime risks.

Purpose: Send approved messages by SMS, WhatsApp, or email.

How it works:

- `AutoSendChannelSelector` chooses SMS, WhatsApp, or email from available routes, contact preference, and historical successful routes; final dispatch route ordering also uses the same history-aware ordered route list.
- `MessageDispatcher` builds a route plan, attempts delivery routes, records lifecycle logs, persists finalization, and schedules retry when finalization requires it.
- `SmsSender` uses multipart SMS and sent/delivered broadcasts.
- `WhatsAppSender` uses `WhatsAppAccessibilityService` with explicit service callback result and timeout.
- `EmailSender` uses Gmail SMTP with sender/recipient address validation and JavaMail.

Dependencies: SMS permission, WhatsApp install and AccessibilityService, WhatsApp consent, Gmail app password, sender/recipient email, channel blackout settings.

Limitations: WhatsApp Accessibility is a high-risk Play policy area. SMS/email/WhatsApp sends require real device/provider validation.

### 13.12 Notifications, Widget, Shortcuts

Status: Fully Implemented.

Purpose: Surface approvals, fallback alerts, event reminders, setup/recovery diagnostics, widget summaries, and launcher shortcuts.

How it works:

- `NotificationHelper` creates channels and displays notifications; `SetupNotificationRequest` maps typed setup/remediation reasons and helper payloads for SMS permission, expired messages, duplicate-send prevention, missing AI provider setup, missing revival AI provider setup, and exact-alarm permission setup; `SystemAlertNotificationRequest` maps typed AI fallback and stale-backup alert reasons and helper payloads.
- Approval and event reminder receivers route notification actions.
- `BirthdayWidgetProvider` queries local DB and shows today birthdays, next events, and pending approval count.
- Static shortcuts open Messages and Contacts.

Dependencies: notification permission on API 33+, AppWidgetManager, deep links, local database.

Limitations: Widget validation is mostly unit/test evidence; connected launcher behavior depends on device/launcher.

### 13.13 Analytics and Activity History

Status: Fully Implemented.

Purpose: Show relationship metrics, health buckets, neglected contacts, delivery/response/personalization percentages, CSV export, and operational logs.

How it works:

- `GetAnalyticsUseCase` and repositories build reactive analytics snapshots.
- `AnalyticsViewModel` computes delivery reliability, response rate, personalization coverage, monthly sent counts, health buckets, and neglected contacts.
- `AnalyticsReportServiceImpl` emits a CSV relationship report and records an analytics activity log entry.
- `ActivityHistoryViewModel` supports type/date/status filters and text search.

Dependencies: contact/event/message/activity repositories, FileProvider export share.

Limitations: Analytics report is a compact CSV, not a full external BI export. Large activity histories may need paging/indexing later.

### 13.14 Style Coach

Status: Fully Implemented.

Purpose: Learn user writing style from manual samples or recent sent messages.

How it works:

- `StyleAnalysisUseCase` computes average length, emoji density, preferred language, common phrases/greetings/closings, tone descriptors, sample count, and profile history.
- `StyleCoachViewModel` supports manual training and recent sent-message analysis.

Dependencies: sent message history and style profile repository.

Limitations: Style analysis is heuristic and local. There is no model training beyond profile extraction.

### 13.15 Memory Vault

Status: Fully Implemented.

Purpose: Store contact-specific notes that can improve personalization or remain private.

How it works:

- `MemoryVaultViewModel` loads notes, filters by search query, adds notes, updates notes, pins/unpins, and deletes.
- `MemoryNotePromptPolicy` excludes category `PRIVATE` from AI prompt context.

Dependencies: memory note repository, contact route.

Limitations: There is no separate per-note "use in AI" toggle; prompt inclusion is category-based.

### 13.16 Gift Advisor

Status: Fully Implemented.

Purpose: Track gift history, budget usage, AI suggestions, duplicate warnings, and shortcut budget adjustment.

How it works:

- `GiftAdvisorViewModel` loads contact profile and gift records, validates add/delete actions, calculates current-year spend, generates AI suggestions, enriches suggestions with budget fit, confidence, and potential duplicate evidence, supports dismissing local suggestions, and can record a suggestion as a gift.
- `GiftAdvisorScreen` exposes an Adjust budget action that routes to `ContactDetail` with `openPreferences=true`; Contact Detail remains the preference save owner.

Dependencies: gift history repository, contact repository, AI service, Contact Detail preferences route.

Limitations: Suggestion dismissals are local UI state. There is not yet a persisted "use in message" or suggestion feedback workflow.

### 13.17 Chat History

Status: Fully Implemented.

Purpose: Show prior sent messages for a contact with text/channel search.

How it works: `ChatHistoryViewModel` observes sent history as `ChatHistoryMessageItem`, not raw `SentMessageEntity`, and filters by message text or channel.

Dependencies: message repository, contact id route.

Limitations: It is read-only; replies are tracked as a flag/timestamp, not full conversation sync.

### 13.18 Settings and AI Doctor

Status: Fully Implemented, with readiness unification Partially Implemented.

Purpose: Configure AI, Gmail SMTP, automation mode, quiet hours, blackouts, biometric lock, sync, WhatsApp consent, backup state, sign-out, and setup diagnostics.

How it works:

- `SettingsViewModel` reads/writes encrypted preferences, validates and saves Gemini/email settings, toggles features, updates automation mode, quiet hours, channel blackout, biometric lock, runs sync, handles secure-pref recovery notice, and signs out.
- `AutomationSetupViewModel` builds grouped readiness checks, test AI generation, safe generation checks, test email send, WhatsApp consent, persisted diagnostic snapshots, recovery diagnostics, domain-derived SMS/WhatsApp setup-channel and channel-verification checks, a domain-classified setup summary/progress state, and a domain-ranked recommended fix.

Dependencies: preferences, contact/message/dispatch/diagnostic repositories, AI/test-send services, permissions, package manager, Firebase user, WorkManager diagnostics.

Limitations: AI Doctor can still be technical. The readiness model is not one shared domain API across every feature.

### 13.19 Backup and Restore

Status: Fully Implemented for encrypted export, preview, and replace import. Merge restore is Not Implemented.

Purpose: Let users explicitly export and restore relationship data without relying on Android auto backup.

How it works:

- `BackupServiceImpl` exports contacts, events, pending messages, sent messages, style profile, memory notes, gift history, activity logs, message feedback, dispatch attempts, and non-secret preferences.
- Export encrypts payload with passphrase-derived AES-GCM and writes a `.enc` file or selected URI.
- Preview decrypts and validates version/checksum/counts before mutation.
- Import replaces restorable local data transactionally and restores non-secret preferences.
- Diagnostic snapshots are cleared/rebuilt and are not backup contents.

Dependencies: Room transaction, Moshi, `BackupEncryption`, `SecurePrefs`, user passphrase.

Limitations: Merge restore is not implemented. Backup passphrases are not stored and cannot recover the live SQLCipher key directly.

### 13.20 Documentation-Only or Planned Features

| Item | Status | Evidence |
| --- | --- | --- |
| LeadRescue AI missed-call/business CRM product | Removed from active repo | No tracked `docs/startup-idea/*` product contract remains |
| Custom backend/server-side product | Not Implemented | No server module or backend API source exists |
| Multi-device sync | Not Implemented | No backend or sync service beyond Google/device contact import |
| Calendar provider sync | Not Implemented | No calendar provider integration source found |
| Local-only/guest mode | Not Implemented | Authenticated routes require Firebase user |
| Merge restore | Not Implemented | Backup docs and service implement replace mode |
| Light/dynamic theme | Planned | Design docs say production is dark-only |
| Pure domain without Room/Paging | Implemented | `:core:model` exists, Room entities and DAO projections live in `:core:data`, and `:core:domain` is a Kotlin/JVM module with no Android/Room/Paging imports |
| Target `occasions` and `message_drafts` tables | Planned | Architecture docs target them; Room v16 still uses `events` and `pending_messages` |

## 14. User Workflows

### First Setup

1. User opens app.
2. Splash routes to onboarding, auth, or Home.
3. User completes onboarding and signs in with Google.
4. User grants contacts permissions or Google contacts scope when using sync.
5. Sync imports contacts and runs event discovery.
6. User configures AI key/provider path as needed, Gmail sender credentials, WhatsApp consent/accessibility, SMS/notification/exact-alarm permissions, automation mode, quiet hours, blackouts, style, and backup.
7. AI Doctor shows readiness checks and recommended fix.

### Daily Relationship Work

1. Home shows upcoming events, pending reviews, setup progress, backup freshness, and ranked actions.
2. User opens a contact, event, message, setup fix, backup, or analytics insight.
3. Activity logs and analytics update from completed work.

### Contact Sync and Enrichment

1. User triggers sync from Settings, Home, AI Doctor, or Contacts.
2. Google and/or device contacts are fetched.
3. Contacts are merged and upserted.
4. Events are discovered and reminders scheduled.
5. User enriches contact details, memories, gift budget/history, and style context.

### Event to Message

1. Event discovery or manual event creates an active event.
2. User or worker invokes generation.
3. Generation builds prompt context from contact, event, style, memory, gift, and history data.
4. Gemini returns variants or parser fallback variants are used.
5. Quality gate and route readiness decide whether the draft can remain automatic or must be reviewed.
6. Pending message is stored, scheduled, and/or notification is shown.

### Review and Dispatch

1. User opens Messages or Wish Preview.
2. User edits/selects/regenerates/tests/approves/rejects.
3. Approved or eligible automatic messages are scheduled.
4. Dispatch policy checks status, approval mode, schedule, quiet hours, and blackout dates.
5. Dispatcher tries eligible routes and persists attempts/results.
6. Sent history, activity logs, analytics, and contact health are updated.

### Backup and Recovery

1. User enters passphrase and exports an encrypted backup.
2. Import preview validates passphrase, manifest, checksum, version, and counts.
3. Confirmed import replaces local restorable data transactionally.
4. Secrets such as OAuth token, Gmail password, Gemini key, and live DB key are not backup contents.

## 15. Internal APIs and Contracts

There are no public HTTP APIs implemented by this repository. Internal contracts are Kotlin interfaces:

| Contract | Purpose |
| --- | --- |
| `AiService` | Generate/regenerate messages, classify contacts, generate gift suggestions |
| `ContactSyncService` | Fetch Google and device contact records |
| `MessageDispatcherService` | Dispatch a prepared `MessageDispatchRequest` |
| `TestSendService` | Send test email to self |
| `PreferencesRepository` | Read/write secure configuration and observe preference changes |
| `SchedulerService` | Schedule/cancel exact sends |
| `EventReminderSchedulerService` | Schedule/cancel/reschedule event reminders |
| `NotificationService` | Show approval and AI fallback notifications |
| `BackupService` | Export, preview, and import encrypted backups |
| `AnalyticsReportService` | Build CSV relationship report |

Primary domain use cases include contact sync, event discovery/manual save/conflict resolution, message generation/regeneration/approval/rejection/retry/revoke/dispatch/test send, contact classification/preferences, health scoring, dashboard metrics, analytics, style analysis, and full automation enablement.

## 16. Quality, Testing, and CI Strategy

Test coverage by layer:

- Pure model and enum parsing tests in `core:model`.
- Domain policy/use-case tests in `core:domain` and app unit tests.
- Data-layer tests for repositories, Room migrations, backup, senders, scheduler adapters, contacts URL building, preferences, diagnostics, and resilience in `core:data`.
- ViewModel and Compose interaction tests in `app/src/test`.
- Roborazzi screenshot tests for primary and secondary screens, large font, Hindi variants, loading/empty/error/populated states.
- Android instrumented smoke tests for package and navigation.
- Release-readiness tests for manifest, backup exclusions, network pins, signing guard, CI workflow, structured logging, and auth copy.

CI gates:

- Dependency review on PRs.
- Unit tests, lint, assemble debug.
- Roborazzi screenshot verification.
- Production readiness guardrails.
- Coverage report generation.
- Release signing guard failure check.

Known test gaps:

- Final native-language Hindi copy review remains a human/release task.
- Runtime provider checks for SMS, Gmail, WhatsApp Accessibility, exact alarms, and Google/Firebase setup require device or release-owner validation.
- Automatic retry execution remains a gap beyond persisted retry metadata/manual retry scheduling.

## 17. Monitoring, Logging, and Diagnostics

Implemented:

- `StructuredLogger` provides redacted in-memory log history and routes Android logging through one allowed file.
- `SensitiveLogRedactor` redacts sensitive values.
- `HealthMonitor` tracks circuit-breaker/provider health.
- `HealthMonitorDiagnosticRecorder` persists warning snapshots to `diagnostic_snapshots`.
- AI Doctor persists redacted reports and reads recent health warnings after process restart.
- Activity logs provide user-visible operational history with type, severity, status, action route, and metadata.
- Dispatch attempts persist durable send/retry/dead-letter evidence.

Not implemented:

- Remote observability backend.
- Crash reporting configuration evidence.
- Production analytics dashboards beyond Firebase Analytics dependency and local app analytics/reporting.

## 18. Performance Considerations

Implemented performance/reliability measures:

- WorkManager constraints require battery/storage not low and network where needed.
- Exact alarms are used for user-visible scheduled sends when permission allows; WorkManager delayed fallback exists.
- Boot/time recovery reconciles alarms, reminders, SMS delivery status, and workers.
- SQLCipher key and secure prefs are warmed asynchronously on app startup.
- `RateLimiter`, retry, and circuit breaker wrap Gemini generation.
- Lists use stable models and screen-local components in many high-risk screens.
- Roborazzi baselines guard large-font and narrow-width layout behavior.

Risks:

- Large local lists may need paging or indexed filtering in Contacts, Messages, Events, and Activity History.
- `AppDatabase.kt`, `AutomationSetupViewModel`, and some screen/ViewModel files remain large.
- AI, contacts sync, backup import/export, and screenshot tests are expensive and should stay off the main thread or be constrained.

## 19. Known Issues, Debt, and Risks

| ID | Area | Status | Evidence | Impact | Recommended action |
| --- | --- | --- | --- | --- | --- |
| D-001 | Domain purity | Resolved for current architecture | Room entities and DAO projections live under `core/data`; `core/domain/build.gradle.kts` is Kotlin/JVM and no longer depends on Room/Paging; boundary tests guard Android/AndroidX/Room/DAO imports | Domain is persistence-independent at the source/API/build level | Keep boundary tests green and continue feature-level reducer/read-model cleanup |
| D-002 | Readiness model | Partially Implemented | Messages uses `MessageOperationalReadinessPolicy` plus `DispatchEligibilityPolicy` for route/status/scheduled-time/allowed-window readiness; `DispatchEligibilityPolicy` blocks already-handled `SENT` and `DISPATCHING` states; exact-send scheduling uses `ExactSendSchedulePolicy`; exact-send stale-dispatch recovery classification uses `ExactSendRecoveryPolicy`; SMS stale pending-delivery recovery cutoff and recovered status use `SmsDeliveryStatusRecoveryPolicy`; event-reminder cancel/exact/inexact scheduling decisions use `EventReminderSchedulePolicy`; foreground and worker dispatcher exception outcomes use `DispatchExceptionFailurePolicy`; SMS sent/delivered callback delivery status, dispatch-attempt result, pending failure marking, and failure metadata use `SmsCallbackOutcomePolicy`; Wish Preview draft text uses `WishDraftReadinessPolicy`; Wish Preview send summary, route choice context, route setup context, device setup context, and quiet-hours/blackout dispatch context use `WishPreviewSendSummaryPolicy`; approval notification actions use `ApprovalNotificationActionPolicy`; dispatch, AI-provider, revival-AI-provider, and exact-alarm setup notification request reasons/helper rendering use `SetupNotificationRequest`; AI fallback and stale-backup alert reasons/helper rendering use `SystemAlertNotificationRequest`; revival review notifications use `RevivalNotificationRequest`; AI Doctor account/provider readiness uses `SetupAccountProviderReadinessPolicy`; AI Doctor full-automation, event, route, and selected-channel count decisions use `SetupAutomationReadinessPolicy`; SMS/WhatsApp setup readiness and channel verification routing use `SetupChannelReadinessPolicy`; AI Doctor email setup readiness uses `SetupEmailReadinessPolicy`; AI Doctor Style Coach, personalization, and generic-message risk checks use `SetupQualityReadinessPolicy`; AI Doctor notification permission, exact-send permission, daily scheduler, recent-health, and dispatch-recovery checks use `SetupSystemReadinessPolicy`; setup summary/progress uses `SetupReadinessSummaryPolicy` and `SetupReadinessProgressPolicy`; AI Doctor recommended-fix ranking uses `SetupReadinessRecommendationPolicy`; route selector and final dispatch fallback ordering use `DeliveryRouteReadinessPolicy` and `AutoSendChannelSelector`; Home readiness banner and next actions use `HomeNextActionPolicy`; remaining notification surfaces outside typed approval/setup/system-alert/revival/event-reminder requests and dispatch/recovery timing surfaces outside Messages/Wish Preview/exact-send scheduling/exact-send recovery/SMS delivery-status recovery/event-reminder scheduling/dispatch-exception failure/SMS callback outcomes still compute related states separately | Users can still see fragmented setup/recovery reasoning outside the shared route/status/scheduled-window/exact-send-scheduling/exact-send-recovery/SMS-delivery-status-recovery/event-reminder-scheduling/dispatch-exception-failure/SMS-callback-outcome/draft-text/send-summary/route-choice/route-setup/device-setup/dispatch-context/approval-notification/setup-notification/system-alert/revival-notification/account-provider/automation-prerequisite/channel-setup/channel-verification/email-readiness/quality-readiness/system-readiness/setup-summary/setup-progress/fix-ranking/Home slices | Create one operational readiness use case/read model |
| D-003 | Backup automation default mismatch | Resolved in current working tree | `GlobalAutomationModePrefsMapper.DEFAULT_GLOBAL_AUTOMATION_MODE = ALWAYS_ASK`; `BackupPreferencesDto.defaults()` uses `ALWAYS_ASK` | No current user impact; keep regression coverage | Keep backup defaults aligned with review-first automation |
| D-004 | Provider config policy | Resolved in current working tree | `.gitignore` allowlists approved app/debug client config files and release/security docs define forbidden secrets | Keep Firebase project/OAuth/SHA verification in release evidence |
| D-005 | WhatsApp release policy | Open Release Blocker | Security/release docs and manifest AccessibilityService | Play distribution risk | Complete Accessibility declaration/signoff or disable channel for Play |
| D-006 | Target data model | Planned | ADRs target `occasions` and `message_drafts`; Room v16 uses `events` and `pending_messages` | Naming/schema mismatch with target model | Plan migration after mapper and backup compatibility tests |
| D-007 | Local-only mode | Not Implemented | Authenticated routes require Firebase auth | Privacy-conscious/manual-only users cannot use app signed out | Product decision needed |
| D-008 | Merge restore | Not Implemented | Backup service replace mode only | Users cannot merge backup into current data | Keep documented or design merge restore |
| D-009 | Automatic retry execution | Partially Implemented | Retry/dead-letter metadata and manual retry exist; ADR says automatic execution pending | Some provider failures need user/manual retry flow | Implement scheduled retry worker loop if product requires |
| D-010 | Stale comments/docs | Partially Resolved | AuthManager's stale sign-out table-count comment is fixed; some older docs still claim SSOT/plan authority | Maintainer confusion from remaining older docs | Continue consolidating docs and fix stale comments opportunistically |
| D-011 | Release evidence | Unverified | Release checklist has TBD signoffs | Cannot claim Play-ready | Attach privacy, Data Safety, Accessibility, device smoke evidence per release |

Current working-tree note: exact-send enqueue-now/exact-alarm/WorkManager-fallback scheduling now uses `ExactSendSchedulePolicy`; initial recurring automation scheduling and boot recovery now share `BootRecoveryRecurringWorkCommand`; approval, revival, and event-reminder notification copy/helper rendering now use `ApprovalNotificationAdapters`, `RevivalNotificationAdapters`, `EventReminderNotificationAdapters`, and typed notification requests. Remaining D-002 dispatch/notification debt excludes those helper paths.

## 20. Troubleshooting and Runbooks

### Gradle cannot run

- Verify JDK 21 is installed and `JAVA_HOME` points to it.
- Verify the active Gradle runtime has `bin/jlink` (`bin/jlink.exe` on Windows).
- If `:app:assembleDebug` fails with `JdkImageTransform` or `jlink executable ... does not exist`,
  configure the IDE Gradle JDK to Android Studio JBR or Temurin JDK 21.
- On Windows, avoid the Antigravity/Red Hat extension JRE path for Gradle builds.
- Run `./gradlew --version`.
- Use `--no-configuration-cache` for current documented validation commands.

### Release build fails signing

- Set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
- `KEYSTORE_PATH` must point to an existing keystore file.
- Missing values are expected to fail release artifact tasks.

### Google contacts sync fails

- Confirm user is signed in with Google.
- Confirm the contacts readonly scope is granted.
- If sync token is rejected, code clears it and performs full sync.
- Device contacts require Android `READ_CONTACTS`; denial is a distinct outcome.

### Gemini generation fails or returns fallback

- Confirm Firebase/Google AI setup or user Gemini API key.
- Check AI Doctor.
- Fallback output is allowed for review but should downgrade automatic modes through `AiAutoSendQualityGate`.

### Email send fails

- Confirm sender email and Gmail app password are configured.
- Confirm last successful email test state.
- Sender and recipient addresses are validated before SMTP send.

### WhatsApp send fails

- Confirm app-level WhatsApp automation consent is granted.
- Confirm WhatsApp or WhatsApp Business is installed.
- Confirm Android AccessibilityService is enabled for RelateAI.
- Provider failure should be redacted and recorded in dispatch attempts.

### Exact alarm denied

- Scheduler checks `canScheduleExactAlarms()`.
- If exact alarms are unavailable, app shows setup notification and enqueues WorkManager delayed fallback.

### Backup restore fails

- Check passphrase.
- Check file is a RelateAI encrypted backup and below size limit.
- Wrong passphrase, malformed file, checksum mismatch, unsupported future version, or database constraint errors stop restore before or during transaction.

## 21. Documentation Consolidation Report

This section is the cleanup report. Items marked removed have already been taken out of the active repository; remaining candidates should be removed only after the SSOT is reviewed and accepted.

| File | Purpose found | Migration status | Removal recommendation | Reason |
| --- | --- | --- | --- | --- |
| `SSOT.md` | Canonical project document | Replaced by this consolidated version | Keep | Authoritative single source |
| `docs/feature-fssot.md` | Feature single source of truth | Dedicated ideal behavior and UX specification created from product feature surfaces | Keep | Separates final feature expectations from implementation/current-state reporting |
| `PLAN.md` | Removed historical rebuild plan and debt registry | Useful current state and debt migrated into this SSOT, retained ADRs/design docs, and release checklist | Keep outside active docs unless task-history evidence is required | Duplicated current-state, target architecture, testing, release, and progress sections |
| `PRODUCT_BLUEPRINT.md` | Removed product model and journeys doc | Current product intent migrated | Keep outside active docs unless product evidence is required | Duplicated product/UX sections |
| `IMPLEMENTATION_TASKS.md` | Removed historical backlog | Open debt migrated at higher level | Keep outside active docs unless task-history evidence is required | It was a task backlog, not current truth; many entries were complete/stale |
| `IMPLEMENTATION_PROGRESS.md` | Removed incremental change log | Key implemented features migrated, including Gift Advisor budget shortcut | Keep outside active docs unless a release audit needs it | Historical detail was useful but fragmented |
| `CODEBASE_AUDIT_REPORT_2026-07-01.md` | Removed audit report | Findings migrated and validated where current | Keep outside active docs unless a release audit needs it | Contains stale/internal contradictions and duplicates SSOT |
| `CODEBASE_AUDIT_REPORT_2026-07-03.md` | Removed audit report | Findings migrated into this SSOT, retained debt themes, retained design docs, and release checklist where current | Keep outside active docs unless a release audit needs it | Dated snapshot duplicated current product, UX, architecture, and cleanup sections |
| `PRODUCT_UX_WORKFLOW_TECHNICAL_ANALYSIS.md` | Removed UX/product audit | Useful recommendations migrated as gaps/debt | Keep outside active docs unless a release audit needs it | Older than current implementation and duplicates feature catalog |
| `docs/architecture/target-room-schema.md` | Removed target schema design | Current schema facts and target directions migrated into this SSOT and ADRs; detailed future migration shape belongs with the migration implementation | Keep outside active docs unless migration-design evidence is required | It was a target design, not current implementation truth |
| `docs/architecture/adr/0001-domain-purity-and-module-boundaries.md` | Accepted ADR | Decision summary also migrated into SSOT | Keep as compact ADR archive | Current guardrail for the implemented pure-domain module boundary |
| `docs/architecture/adr/0002-occasion-model.md` | Accepted ADR | Target occasion direction also migrated into SSOT | Keep as compact ADR archive | Current guardrail for future `events` to `occasions` migration work |
| `docs/architecture/adr/0003-durable-dispatch-attempts.md` | Accepted ADR | Dispatch attempt state also migrated into SSOT | Keep as compact ADR archive | Current guardrail for durable attempt/retry semantics |
| `docs/architecture/adr/0004-database-keying-and-backup-recovery.md` | Accepted ADR | Security/keying decisions also migrated into SSOT | Keep as compact ADR archive | Current guardrail for DB keying, backup, and destructive sign-out semantics |
| `docs/security/privacy-and-permissions.md` | Privacy/permission release notes | Migrated into security and release risks | Keep until privacy policy/release owner signoff exists, then archive | High-risk release evidence still needs owner review |
| `docs/security/dependency-review.md` | Removed dependency review policy | Migrated into CI/release checklist | Keep outside active docs | CI workflow and `ProductionReadinessConfigTest` are the enforceable sources |
| `docs/operations/release-checklist.md` | Release checklist | Migrated into build/release/security sections | Keep until next release record exists, then archive or generate from SSOT | Contains required signoff checklist |
| `docs/testing/test-strategy.md` | Removed test strategy | Migrated into this SSOT and release checklist | Keep outside active docs | Duplicated current validation gates |
| `docs/testing/screenshot-strategy.md` | Removed Roborazzi strategy | Durable rules moved into release checklist; baselines remain source artifacts | Keep outside active docs | Duplicated screenshot coverage narrative and baseline inventory |
| `docs/design/design-system.md` | Design system | Core tokens/theme policy migrated at summary level | Keep only if design token details must remain expanded, otherwise move token details into SSOT | Current SSOT does not repeat every token to avoid duplication |
| `docs/design/ux-audit-checklist.md` | Removed UX audit checklist | Screen ownership and UX gaps migrated into this SSOT; durable UI rules live in the design system and release checklist | Keep outside active docs unless UI task-history evidence is required | Historical checklist duplicated implementation progress |
| `docs/user/complete-user-guide.md` | Removed user guide | Workflows/runbooks migrated | Keep outside active docs unless user-facing guide generation is required | Very large and duplicated product behavior |
| `docs/user/backup-restore.md` | Removed user backup guide | Migrated into backup, security, troubleshooting, and release-checklist sections | Keep outside active docs unless user-facing guide generation is required | Duplicated SSOT backup section |
| Root `app_logs*.txt`, `logcat*.txt`, `lint_baseline_pre_fixes.txt` | Removed local diagnostic/log snapshots | Not migrated as authoritative docs | Keep ignored | Generated/local diagnostics, not current source of truth |
| `metadata.json` | App/tool metadata | Not documentation | Keep if required by tooling | Mentions server-side Gemini capability, but source has no custom server |
| `app/src/main/baseline-prof.txt` | Baseline profile source artifact | Not documentation | Keep | Build/runtime optimization artifact |

## 22. Documentation Gap Analysis

| Gap | Impact | Evidence | Recommended action |
| --- | --- | --- | --- |
| Final privacy policy and Data Safety text absent | Cannot claim production Play readiness | Release/security docs have requirements and TBD signoffs | Create release-owned privacy/Data Safety artifacts from SSOT |
| WhatsApp Accessibility declaration evidence absent | Play distribution risk | Release checklist status blocked | Attach signoff/evidence or disable WhatsApp automation in Play build |
| Provider config policy | Resolved in current working tree | `.gitignore` allowlists approved app/debug client config files and `RepositoryHygieneTest` checks for server-side secret markers | Keep release-owner OAuth/SHA verification external |
| Runtime smoke evidence not current in SSOT | Static analysis cannot prove provider/device behavior | Tests exist, but this doc pass did not run device checks | Attach release records per build |
| Native-language Hindi review incomplete | Localization quality risk | Release checklist requires Hindi/English primary-flow review before release | Schedule human language review |
| Automatic retry execution unclear | Recovery behavior may be overestimated | ADR says pending; code has manual retry metadata/scheduling | Document product decision and implement if required |
| Design token detail split | Full token table is currently in design doc | This SSOT summarizes rather than duplicates every token | Either migrate token table into SSOT or explicitly keep design doc as generated/reference |

## 23. Inconsistency Report

| Inconsistency | Current evidence | Correct source of truth | Resolution |
| --- | --- | --- | --- |
| Multiple docs claimed to be single source of truth | `PLAN.md` and older audit/progress docs were removed from active docs | This `SSOT.md` | Keep remaining supporting docs labeled as reference/history |
| ADR 0001 module count drift | ADR 0001 now records the five-module graph matching `settings.gradle.kts` | Build files and ADR 0001 | Resolved; keep ADR 0001 as the domain-boundary guardrail |
| Older SSOT recreate notes mentioned debug suffix `.debug` | `app/build.gradle.kts` has no `applicationIdSuffix` in debug | Build file | Do not repeat debug suffix claim |
| Audit report says older SSOT references schema v13 | Current DB is v16 and this SSOT records v16 | `AppDatabase.kt` and schema export | Treat audit statement as historical |
| Backup fallback automation default conflict | Current working tree shows both normal and backup fallback defaults as `ALWAYS_ASK` | `GlobalAutomationModePrefsMapper` and `BackupPreferencesDto.defaults()` | Treat older reports claiming `FULLY_AUTO` backup fallback as historical |
| Sign-out comment used a stale fixed table count | `database.clearAllTables()` clears all current Room tables | `AuthManager.kt` and `AppDatabase.kt` | Resolved: source comment now says all Room tables |
| Provider config allowlist needed | Approved app/debug `google-services.json` files are tracked; other local variants should remain ignored | `.gitignore`, release/security docs, `RepositoryHygieneTest` | Resolved: exact approved paths are explicitly allowlisted and checked for server-side secret markers |
| Metadata says server-side Gemini capability | No backend/server module exists | Source tree and Gradle modules | Treat `metadata.json` as tool metadata, not architecture |
| LeadRescue docs describe another product | Startup docs were removed from the active repo | Current source and product docs | Keep LeadRescue requirements out of this Android app |

## 24. Unverified Information Report

These items are not claimed as verified implementation truth:

| Item | Why unverified | How to verify |
| --- | --- | --- |
| Firebase project correctness, OAuth SHA-1, People API enablement | Config files not inspected for secrets and cloud state is external | Firebase/Google Cloud console and release smoke |
| Gemini runtime availability/quota | External provider state not in repo | Device/integration test with configured provider |
| Gmail SMTP app password validity | User secret not in repo | AI Doctor email test |
| SMS deliverability | Carrier/SIM/device dependent | Device test with safe recipient |
| WhatsApp Accessibility Play approval | Requires policy review outside source | Release-owner signoff and Play Console declaration |
| Public privacy policy/Data Safety form | Not present as final release artifact | Store/release evidence |
| Current CI pass/fail status | Static source review only in this pass | Run GitHub Actions or local Gradle gates |
| External market claims in startup docs | Archived docs cite external sources not checked here | External source review if those docs are retained |
| Production deployment | No published release evidence in repo | Release record and signed artifact |

## 25. Feature Status Matrix

| Feature/module/workflow | Status | Primary evidence | Notes |
| --- | --- | --- | --- |
| App shell, navigation, permission rationale | Fully Implemented | `MainActivity.kt`, `NavGraph.kt`, `Screen.kt` | Bottom nav plus secondary routes |
| Biometric lock | Fully Implemented | `MainActivity.kt`, `BiometricLockPolicy.kt`, `DeepLinkContractTest.kt`, tests | Preference-gated session lock covers deep-linked app entry before nav graph rendering |
| Splash/onboarding/auth | Fully Implemented | Splash/Onboarding/Auth ViewModels and screens | No guest mode |
| Secure sign-out | Fully Implemented | `AuthManager.kt` | Comment now matches all-table cleanup behavior |
| Google/device contact sync | Fully Implemented | `SyncContactsUseCase`, `GoogleContactsSync`, `PeopleConnectionsRequestFactory`, `DeviceContactsReader` | Uses injected shared People API request/client seam; requires permissions/scopes |
| Contact list/detail/preferences | Fully Implemented | Contact screens/ViewModels, `UpdateContactPreferencesUseCase` | Budget adjustment stays in Contact Detail |
| Contact classification | Fully Implemented | `ClassifyContactUseCase`, `AiServiceImpl` | AI/provider dependent |
| Event discovery | Fully Implemented | `DiscoverEventsUseCase` | Birthday, anniversary, work anniversary |
| Manual/custom events | Fully Implemented | `SaveManualEventUseCase`, `EventsViewModel` | Duplicate/conflict handling exists |
| Event reminders | Fully Implemented | `EventReminderScheduler`, receiver | Exact scheduling/recovery exists |
| AI wish generation | Fully Implemented | `GenerateMessageUseCase`, Gemini package | Quality gate downgrades weak automatic drafts |
| Regeneration with feedback | Fully Implemented | `RegeneratePendingMessageUseCase`, `WishPreviewViewModel` | Uses feedback records |
| Wish Preview review | Fully Implemented | `WishPreviewViewModel`, `WishPreviewScreen` | Includes test-send path |
| Messages queue and bulk actions | Fully Implemented | `MessagesViewModel`, messages screen components | Tabs/filter/sort/search/selection |
| Dispatch eligibility policy | Fully Implemented | `DispatchEligibilityPolicy` tests | Covers approval, schedule, quiet hours, blackouts |
| Exact alarm scheduling/fallback | Fully Implemented | `DailyScheduler`, scheduler tests | WorkManager fallback exists |
| Background automation workers | Fully Implemented | Worker package and tests | Daily, sync, discovery, generation, holiday, follow-up, revival, style |
| Durable dispatch attempts | Fully Implemented | `dispatch_attempts`, DAO, mappers, worker/dispatcher | Automatic retry execution still pending |
| SMS sending | Fully Implemented | `SmsSender`, `SmsStatusReceiver` | Device/SIM/permission dependent |
| Email sending | Fully Implemented | `EmailSender`, test-send service | Gmail app password needed |
| WhatsApp sending | Fully Implemented with release risk | `WhatsAppSender`, Accessibility service | Play policy blocker |
| Notifications | Fully Implemented | `NotificationHelper`, approval/event receivers | Notification permission on API 33+ |
| Widget/shortcuts/deep links | Fully Implemented | Widget provider, shortcut XML, deep-link tests | Launcher/device behavior may vary |
| Analytics and CSV export | Fully Implemented | `AnalyticsViewModel`, `AnalyticsReportServiceImpl` | Local/reporting only |
| Activity History | Fully Implemented | `ActivityHistoryViewModel` | May need paging later |
| Style Coach | Fully Implemented | `StyleAnalysisUseCase`, style screen/ViewModel | Heuristic extraction |
| Memory Vault | Fully Implemented | `MemoryVaultViewModel`, `MemoryNotePromptPolicy` | Private category excludes AI prompts |
| Gift Advisor | Fully Implemented | `GiftAdvisorViewModel`, `GiftAdvisorScreen` | Budget shortcut routes to Contact Detail preferences |
| Backup export/preview/import | Fully Implemented | `BackupServiceImpl`, `BackupEncryption` | Replace-only restore |
| SQLCipher and encrypted prefs | Fully Implemented | `AppDatabase`, `DatabaseKeyDerivation`, `SecurePrefs` | Legacy recovery path remains |
| Dark design system | Fully Implemented | `core/ui`, design tests | Light/dynamic theme planned only |
| CI build/test/release guard | Fully Implemented | `.github/workflows/android.yml`, readiness tests | Runtime release signoffs remain external |
| Pure domain architecture | Fully Implemented for current module boundary | `:core:model` exists; `:core:domain` is Kotlin/JVM; Room entities live in `:core:data` | Future feature extraction remains separate |
| Target occasions/message drafts schema | Planned | ADRs and SSOT target data model | Room v16 still uses `events` and `pending_messages`; target schema note was removed |
| Merge restore | Not Implemented | Backup service/docs | Replace-only |
| Local-only mode | Not Implemented | Auth gate | Product decision needed |
| LeadRescue AI product | Removed from active repo | No tracked `docs/startup-idea/*` product contract remains | Unrelated product idea |

## 26. Product and UX Assessment

Product assessment:

| Perspective | Current assessment | Primary gap | Recommended direction |
| --- | --- | --- | --- |
| Product Owner | The product is a relationship operations assistant, not only a birthday reminder. It has contact import, occasions, AI writing, approval, delivery, analytics, backup, and diagnostics. | Activation still depends on users understanding many setup and readiness concepts. | Make the top-level product promise "what needs attention now" and route every insight to one next action. |
| End User | Users can sync contacts, enrich people, generate/review wishes, automate sends, inspect failures, and back up data. | The same user problem can appear in Home, Messages, AI Doctor, Settings, and Activity History with different language. | Use one shared operational state and plain-language status copy across screens. |
| UX Designer | Main screens have loading/empty/error/populated states and screenshot coverage. Secondary surfaces are dense but feature-complete. | Settings, AI Doctor, Events, Wish Preview, and Gift Advisor carry too many decisions per screen. | Split routine actions from advanced diagnostics and progressively reveal risk details. |
| QA Engineer | The repo has broad unit, interaction, screenshot, migration, policy, and readiness tests. | External provider behavior, Play policy signoff, device delivery, and release evidence remain outside static tests. | Keep CI gates and add release-run evidence records for provider/device smoke. |
| Software Architect | Modular Gradle graph exists with app/model/domain/data/ui layers and extensive policies/use cases. Domain is now a Kotlin/JVM module free of Room/Paging/entity and Android imports. | Readiness logic still needs journey-level regression coverage and presentation files remain large. | Add cross-surface readiness journey tests and split the largest route coordinators/screens. |

User expectation summary:

| User type | Expected behavior | Current alignment | Gap |
| --- | --- | --- | --- |
| First-time user | Sign in, import or create a contact, understand required setup, generate one safe message, and know whether it will send. | Onboarding/Auth/Home/AI Doctor provide the path, but setup spans screens. | Guided setup should resume at the next blocker until one end-to-end message is achieved. |
| Daily power user | Open the app and immediately see pending reviews, failed sends, upcoming events, stale backup, and low-health contacts. | Home and Messages support this, with analytics and activity history as supporting surfaces. | Ranking and cross-screen state should become one shared command model. |
| Business stakeholder | Trustworthy automation, lower support burden, measurable engagement and delivery reliability. | Approval modes, dispatch attempts, activity logs, analytics, and backup exist. | Release signoffs, provider policy evidence, and outcome dashboards are not final. |
| Privacy-sensitive user | Know what data stays local, what is sent to providers, and how to recover or delete it. | SQLCipher, encrypted prefs, backup, biometric lock, private memory category, and sign-out purge exist. | Public privacy/Data Safety docs and prompt-inclusion controls need release-owner completion. |

## 27. Feature Expectation Gap Matrix

This matrix extends the feature catalog with ideal behavior and improvement priority.

| Feature | Business goal | User goal | Ideal user experience | Current implementation summary | User expectation gap | Suggested redesign | Priority | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| App shell and lock | Secure entry without blocking work | Open, unlock, navigate | Deep links, shortcuts, and notifications respect auth and lock while landing on exact task | Biometric gate, bottom nav, auth guard, deep links, shortcuts, widget | Contacts permission is not part of the central permission rationale | Add global readiness/status affordance and route-specific blocked-state copy | Medium | `MainActivity.kt`, `NavGraph.kt`, manifest |
| Onboarding/Auth | Activate real users safely | Finish setup fast | Guided first-run checklist ends in a verified first message | Onboarding, Google/Firebase auth, no guest mode | Manual/local-only trial is impossible | Decide local-only mode or make Google-required onboarding explicit through all setup states | High | `OnboardingScreen.kt`, `AuthManager.kt`, `RequireSignedIn` |
| Home | Daily command center | Know what to do now | One primary ranked action plus supporting context | Dashboard metrics, setup state, backup freshness, next actions | Next action is Home-local, not product-wide | Create shared next-best-action use case | High | `HomeViewModel.kt` |
| Contacts | Relationship data quality engine | Find and improve contacts | Search/filter by missing dates, channel, health, personalization gaps | Sync, search/filter/sort, quality labels, detail links | Missing-data remediation still requires manual inspection | Add "needs attention" filter presets and contact quality explanations | Medium | `ContactListViewModel.kt`, `ContactDetailViewModel.kt` |
| Events | Prevent missed occasions | Track accurate moments | Event source/confidence/duplicate state is obvious and mergeable | Contact-derived and manual events, conflict policy, reminders | Current `events` table is a legacy shape and source trust is still nuanced | Move toward occasion model and expose source/merge controls in user copy | Medium | `SaveManualEventUseCase.kt`, `EventResolutionPolicy.kt` |
| AI generation | Produce safe personalized drafts | Get good wishes quickly | Drafts explain why they are personal, what context was used, and whether fallback was used | Gemini, parser, fallback, quality gate, duplicate prevention | Quality is heuristic and provider runtime is external | Show prompt-quality contributors and fallback reason in Wish Preview/Messages | High | `GenerateMessageUseCase.kt`, `AiAutoSendQualityGate.kt` |
| Wish Preview | Safe review and editing | Approve, reject, edit, regenerate | Event, route, schedule, quality, variants, and next review are all visible | Variants, edit, feedback, regenerate, test send, approve/reject, review next | Readiness is screen-local and can diverge from AI Doctor | Use shared readiness model and stronger "why this can/cannot send" panel | High | `WishPreviewViewModel.kt` |
| Messages | Operational control room | Process queues efficiently | Needs review, scheduled, blocked, failed, sent, and recovery are task-first | Tabs/filter/sort/search/selection/bulk actions and domain-derived readiness labels | Recovery state still splits across Messages, AI Doctor, Activity History | Make failed rows link to exact fix and retry after fix | High | `MessagesViewModel.kt`, `MessageOperationalReadinessPolicy.kt`, message components |
| Automation setup/AI Doctor | Reduce setup failures | Fix blockers one at a time | Ranked fix wizard with success confirmation after each fix | Grouped checks and recommended fix | Diagnostic details can overwhelm non-technical users | Convert checks into guided repair sequence | High | `AutomationSetupViewModel.kt` |
| Delivery channels | Reliable sends | Configure and trust SMS/WhatsApp/email | Channel readiness is explicit before approval and route fallback is predictable | SMS, WhatsApp Accessibility, Gmail SMTP, route selection, dispatcher attempts | WhatsApp release policy and real-device provider behavior need external validation | Add provider-specific setup wizard and release policy switch | High | sender package, manifest |
| Analytics/Activity | Turn data into action | Know who needs attention and why | Every metric row has a clear action or explanation | Local metrics, CSV export, activity filters | Metrics still need more product-level recommendations | Add actionable insight cards backed by shared next-action model | Medium | `AnalyticsViewModel.kt`, `ActivityHistoryViewModel.kt` |
| Style/Memory/Gifts | Improve personalization | Teach the app useful context | Users see which context improves the next draft and can exclude sensitive details | Style Coach, private memory category, gift history and AI suggestions | Memory AI inclusion is category-based; gift suggestions have local-only dismissal | Add per-note AI-use toggle and persisted gift suggestion feedback | Medium | `StyleAnalysisUseCase.kt`, `MemoryNotePromptPolicy.kt`, `GiftAdvisorViewModel.kt` |
| Backup/Restore | Protect local data | Export and recover safely | Users see freshness, know passphrase risk, preview restore, and rehearse recovery | Encrypted export, preview, replace import, Home/Settings freshness | Merge restore is absent and release runbooks are not final evidence | Add restore rehearsal and merge design only if product requires it | Medium | `BackupServiceImpl.kt` |

## 28. Workflow Analysis and Effort Reduction

| Workflow | Current user journey | Ideal user journey | Pain points | Recommended improvement | Estimated effort reduction |
| --- | --- | --- | --- | --- | --- |
| First setup to first safe message | Onboarding -> Auth -> Home/Settings/AI Doctor -> sync/manual data -> configure AI/channel -> generate -> review | Guided setup resumes at next blocker and ends at one verified draft or send | Too many surfaces and permissions to interpret | One first-run checklist powered by setup/readiness state | 30-50 percent fewer decisions |
| Daily review | Home or Messages -> open queue -> inspect status -> open Wish Preview -> approve/reject -> maybe review next | Home primary action opens exact queue item or review-next sequence | Users may scan multiple tabs | Shared next action and deep links into filtered queue | 20-40 percent fewer taps |
| Failed-send recovery | Messages failed tab or Activity History -> read reason -> AI Doctor/Settings -> fix -> retry | Failed row explains root cause and opens exact fix, then returns to retry | Recovery language is technical and split across screens | Failure taxonomy mapped to fix routes and retry readiness | 40-60 percent less searching |
| Contact enrichment | Contacts -> search/filter -> Contact Detail -> memory/gift/style/preferences | Contacts show missing context and why it matters, then open focused editor | User has to infer what improves AI | Contact quality model with focused edit routes | 25-45 percent faster enrichment |
| Manual event creation | Events -> form -> duplicate/conflict decision -> save -> reminders | Event form validates source/conflict early and explains merge/keep separate | Duplicate/conflict concepts require care | Source/confidence copy plus suggested merge | 20-35 percent fewer corrections |
| Automation enablement | Settings/Home -> AI Doctor -> grouped checks -> external settings -> return | Guided fix sequence checks one blocker at a time and confirms ready state | External permissions and provider setup are high-friction | Checklist with return-to-app verification and provider test actions | 30-50 percent less setup uncertainty |
| Backup and restore | Settings -> Backup Restore -> passphrase -> export/preview/import | Home/Settings freshness prompt, restore rehearsal, clear post-restore setup | Passphrase and external secrets are easy to misunderstand | Backup readiness card plus post-restore checklist | Lower support risk more than tap reduction |

## 29. Technical Audit Report

| ID | Description | Root cause | User impact | Business impact | Severity | Recommended fix | Files involved |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T-001 | Readiness state is not a single source | Messages uses shared route/status/scheduled-time/allowed-window readiness policy, exact-send scheduling uses a shared scheduling policy, exact-send stale-dispatch recovery uses a shared recovery decision policy, SMS stale delivery-status recovery uses a shared recovery policy, event reminders use a shared cancel/exact/inexact scheduling decision policy, foreground and worker dispatch exception outcomes use a shared final-failure policy, SMS callback delivery/attempt outcomes use a shared callback outcome policy, already-handled `SENT`/`DISPATCHING` dispatch states use a shared blocker decision, Wish Preview draft text uses a shared draft-readiness policy, Wish Preview send summary, route choice context, route setup context, device setup context, and quiet-hours/blackout dispatch context use a shared projection policy, approval notification actions plus approval/setup/system-alert/revival/event-reminder notification request payloads use shared domain/model contracts or Android adapters, route selection and final dispatch fallback ordering use shared history-aware route policy, AI Doctor account/provider readiness and automation prerequisites use shared domain policies, SMS/WhatsApp setup channel checks and channel verification routing use a shared domain policy, AI Doctor email setup, quality, and system checks use shared domain policies, setup summary/progress uses shared domain policies, AI Doctor recommended-fix ranking uses a shared domain policy, and Home readiness banner/next actions use a shared domain policy; remaining notification surfaces outside typed approval/setup/system-alert/revival/event-reminder requests and dispatch/recovery timing surfaces outside Messages/Wish Preview/exact-send scheduling/exact-send recovery/SMS delivery-status recovery/event-reminder scheduling/dispatch-exception failure/SMS callback outcomes still compute related states separately | Conflicting or confusing "ready/blocked" explanations remain possible outside the shared slices | Trust risk for automation | High | Continue toward one operational readiness use case/read model and migrate remaining surfaces | `MessageOperationalReadinessPolicy.kt`, `ExactSendSchedulePolicy.kt`, `ExactSendRecoveryPolicy.kt`, `SmsDeliveryStatusRecoveryPolicy.kt`, `EventReminderSchedulePolicy.kt`, `DispatchExceptionFailurePolicy.kt`, `SmsCallbackOutcomePolicy.kt`, `ExactSendRecovery.kt`, `SmsDeliveryStatusRecovery.kt`, `EventReminderScheduler.kt`, `SmsStatusReceiver.kt`, `DispatchMessageUseCase.kt`, `WorkerMessageDispatchAdapters.kt`, `WishDraftReadinessPolicy.kt`, `WishPreviewSendSummaryPolicy.kt`, `ApprovalNotificationActionPolicy.kt`, `SetupNotificationRequest.kt`, `SystemAlertNotificationRequest.kt`, `ApprovalNotificationRequest.kt`, `NotificationMappers.kt`, `NotificationHelper.kt`, `RevivalWorker.kt`, `SetupAccountProviderReadinessPolicy.kt`, `SetupAutomationReadinessPolicy.kt`, `SetupChannelReadinessPolicy.kt`, `SetupEmailReadinessPolicy.kt`, `SetupQualityReadinessPolicy.kt`, `SetupSystemReadinessPolicy.kt`, `SetupReadinessSummaryPolicy.kt`, `SetupReadinessProgressPolicy.kt`, `SetupReadinessRecommendationPolicy.kt`, `HomeNextActionPolicy.kt`, `AutomationSetupViewModel.kt`, `HomeViewModel.kt`, `WishPreviewViewModel.kt`, `WishPreviewDeviceReadinessReader.kt`, `DispatchEligibilityPolicy.kt`, `DeliveryRouteReadinessPolicy.kt`, `AutoSendChannelSelector.kt` |
| T-002 | Domain module Android/persistence leakage | Domain source and build now avoid Android, AndroidX, Room, DAO, and Paging dependencies | Resolved for current architecture | Lower build overhead and clearer module intent | Resolved | Keep boundary tests | `core/domain/build.gradle.kts`, `RepositoryBoundaryContractTest.kt` |
| T-003 | Large UI/ViewModel files concentrate behavior | Complex screens grew organically | Higher regression risk and harder review | Slower delivery velocity | Medium | Split by sub-feature and extract reducers/state calculators | `AutomationSetupViewModel.kt`, `GiftAdvisorScreen.kt`, `EventsScreen.kt`, `WishPreviewScreen.kt`, `SettingsScreen.kt` |
| T-004 | Provider config policy | `google-services.json` was tracked while ignored by `.gitignore` | Resolved in current working tree with explicit allowlist, release/security policy, and repository hygiene coverage | Remaining Firebase project/OAuth/SHA correctness is external release evidence | High | Implemented: approved app/debug configs are allowlisted; local variants and server-side secrets remain forbidden | `.gitignore`, `RepositoryHygieneTest.kt`, release/security docs |
| T-005 | Release evidence not complete | Static source has controls but not store artifacts/signoffs | Users cannot rely on Play-ready claims | Release blocker | High | Add privacy policy, Data Safety, Accessibility declaration, and device smoke records | `docs/security/*`, `docs/operations/release-checklist.md` |
| T-006 | WhatsApp automation is high-risk for distribution | AccessibilityService automates a third-party app | Users need explicit consent and clear fallback | Play policy risk | High | Keep opt-in, add policy signoff, or disable per release flavor | manifest, `WhatsAppAccessibilityService.kt`, `WhatsAppSender.kt` |
| T-007 | Activity/log history may need paging as data grows | Current local query limits and lists are sufficient for early use but not proven at scale | Older logs may become harder to scan | Support and performance risk | Medium | Add paging/indexed filters when volume warrants | `ActivityLogDao.kt`, `ActivityHistoryViewModel.kt` |
| T-008 | Stale sign-out comment used a fixed table count | Comment was not updated after schema growth | Resolved in current working tree; no runtime behavior changed | Low direct impact | Low | Implemented: comment now says "all Room tables" | `AuthManager.kt` |
| T-009 | Runtime provider behavior is not statically verifiable | SMS, Gmail, WhatsApp, Google/Firebase, Gemini depend on external state | Sends/sync/generation can fail despite green unit tests | Support risk | Medium | Keep AI Doctor and add release smoke records per provider | sender, contacts, gemini, auth packages |

Current working-tree note: T-001 now also covers exact-send scheduling through `ExactSendSchedulePolicy.kt` and `ExactSendSchedulePolicyTest.kt`, already-handled `SENT`/`DISPATCHING` dispatch blockers through `DispatchEligibilityPolicyTest.kt`, recurring automation scheduling through `BootRecoveryWorkCommands.kt` and `WorkerSchedulerTest.kt`, plus approval, revival, and event-reminder notification helper rendering through `ApprovalNotificationAdapters.kt`, `ApprovalNotificationAdaptersTest.kt`, `RevivalNotificationAdapters.kt`, `RevivalNotificationAdaptersTest.kt`, `EventReminderNotificationAdapters.kt`, and `EventReminderNotificationAdaptersTest.kt`.

## 30. Dead Code and Unused Resource Report

No production Kotlin source file was classified as safe to remove solely from static analysis. The verified cleanup candidates are documentation, local diagnostics, tool state, or one-off helper artifacts.

| Path | Purpose | Usage references | Dependency impact | Risk | Safe to remove | Recommended action |
| --- | --- | --- | --- | --- | --- | --- |
| `docs/startup-idea/*` | Removed LeadRescue AI business idea | No app/runtime references found; unrelated product content | None for RelateAI app | Low | Already removed from active product repo | Keep outside this repo unless a future product decision adopts it |
| `docs/feature-fssot.md` | Feature single source of truth | Intentionally retained product specification; excludes implementation/current-state details | None for build | Low | Keep in active docs | Use as the definitive ideal feature behavior reference |
| `PRODUCT_UX_WORKFLOW_TECHNICAL_ANALYSIS.md` | Removed older UX/technical analysis | Superseded by SSOT/current audit | None if SSOT accepted | Low | Removed from active product repo | Keep outside this repo unless release evidence requires it |
| `CODEBASE_AUDIT_REPORT_2026-07-01.md`, `CODEBASE_AUDIT_REPORT_2026-07-03.md` | Removed historical audits | Useful findings migrated and updated | None if SSOT accepted | Medium | Removed from active product repo | Keep as dated artifacts outside active docs if audit trail matters |
| `PLAN.md` | Removed historical rebuild/debt plan | Duplicated by current SSOT sections and retained supporting docs | None if SSOT accepted | Medium | Removed from active product repo | Keep outside this repo unless task-history evidence is required |
| `PRODUCT_BLUEPRINT.md`, `IMPLEMENTATION_TASKS.md` | Removed planning/backlog/product docs | Duplicated by current SSOT sections | None if SSOT accepted | Medium | Removed from active product repo | Keep outside this repo unless historical evidence is required |
| `IMPLEMENTATION_PROGRESS.md` | Removed detailed changelog | Historical evidence only | None for build | Medium | Removed from active product repo | Keep outside this repo unless changelog evidence is required |
| `docs/user/complete-user-guide.md` | Removed broad user guide | Workflows/runbooks migrated into SSOT/current docs | None for build | Low | Removed from active product repo | Regenerate external user docs from SSOT if needed |
| `docs/design/ux-audit-checklist.md` | Removed UX audit checklist | Screen ownership and UI validation rules migrated into SSOT/design-system/release-checklist | None for build | Low | Removed from active product repo | Keep outside this repo unless UI task-history evidence is required |
| `docs/architecture/target-room-schema.md` | Removed target Room schema note | Current Room facts and target naming/migration direction migrated into SSOT/ADRs | None for build | Medium | Removed from active product repo | Recreate detailed schema design only inside the future migration change |
| `app_logs*.txt`, `logcat*.txt`, `logs/*.log`, `lint_baseline_pre_fixes.txt` | Removed local diagnostic snapshots | Ignored by `.gitignore`; no build refs found | None | Low | Removed from local tree | Keep out of git; regenerate only when needed |
| `.codepulse/`, `.intelligence/`, `.gradle-user-home/` | Removed tool/local diagnostics/cache | Ignored by `.gitignore`; no app refs found | None | Low | Removed from local tree | Keep out of git; regenerate only when needed |
| `app/schemas/com.example.core.db.AppDatabase/4-6.json` | Legacy app-level schema exports | Active schemas are in `core/data/schemas`; `app/schemas` ignored | None for current Room export authority | Medium | Removed from local tree | Keep app-level schemas out of the repo; `RepositoryHygieneTest` guards this |
| `scripts/patch_app_dep.py`, `scripts/patch_settings.py`, `scripts/patch_app_build2.py`, `scripts/patch_ui.py` | Removed one-off patch helpers | No references found by repository scan | None for build/tests | Low | Removed from local tree | Keep `scripts/` limited to maintained helpers |
| `scripts/extract_strings.sh` | String extraction helper | Referenced by `HelperScriptsTest` | Test would fail if removed | Medium | No | Keep |
| `metadata.json` | Tool metadata, says server-side Gemini capability | No Gradle/app references found | Unknown tool impact | Medium | No, unless tool owner confirms | Keep as tool metadata, not architecture evidence |
| `app/src/test/screenshots/greeting.png` | Removed stray generated screenshot named in `.gitignore` | No code reference found in scans | None likely | Low | Removed from local tree | Keep approved baselines under `app/src/test/screenshots/baseline/` |
| `mood_logs` migration table | Dropped legacy schema table | Mentioned only in migrations/tests | Must remain in migration history | High | No | Keep migration SQL; do not recreate feature |
| `DeadLetterQueue` | Legacy in-memory diagnostic supplement | Referenced by tests and failure side-effect code | Still part of diagnostics/tests | High | No | Do not remove until durable diagnostics fully replace it |

## 31. Folder Structure Review

Current problems:

| Problem | Evidence | Risk |
| --- | --- | --- |
| Feature UI and ViewModels are split into separate screen/viewmodel package roots | `app/src/main/java/com/example/ui/screens`, `app/src/main/java/com/example/ui/viewmodel` | Feature ownership requires cross-folder navigation |
| Journey-level readiness coverage remains | Canonical readiness is adopted on named surfaces, but cross-surface workflows still need end-to-end regression checks | Users may see inconsistent recovery if a flow regresses between focused tests |
| Data package contains many infrastructure subdomains under one module | `core/data/src/main/kotlin/com/example/core/...` | Module is large but still coherent for current project size |
| Docs are fragmented | Root docs and `docs/**` contain overlapping authority claims | Maintainers may use stale docs |
| Local/tool artifacts can accumulate near source | ignored logs and tool diagnostics | Review noise if accidentally reintroduced |

Proposed target structure:

```text
app/src/main/java/com/example/
  app/
  navigation/
  feature/
    home/{ui,viewmodel}
    contacts/{ui,viewmodel}
    events/{ui,viewmodel}
    messages/{ui,viewmodel}
    wish/{ui,viewmodel}
    setup/{ui,viewmodel}
    settings/{ui,viewmodel}
    analytics/{ui,viewmodel}
    backup/{ui,viewmodel}
    memory/{ui,viewmodel}
    gifts/{ui,viewmodel}
    style/{ui,viewmodel}
  widget/

core/model/
  contact/
  occasion/
  message/
  automation/
  backup/
  activity/

core/domain/
  contact/
  occasion/
  message/
  automation/
  analytics/
  backup/
  style/
  memory/
  gift/

core/data/
  db/{entities,dao,migrations}
  repository/
  contacts/
  ai/
  dispatch/
  backup/
  prefs/
  workers/
  notifications/

core/ui/
  theme/
  component/
```

Migration plan:

1. Freeze new authoritative docs into this SSOT and archive redundant docs only after review.
2. Extract shared readiness/read-model contracts before reorganizing UI packages.
3. Move feature screen and ViewModel files together one feature at a time, with package-level tests unchanged in behavior.
4. Keep `:core:domain` as a JVM module and update CI/coverage commands whenever test task names change.
5. Move from `events`/`pending_messages` naming toward `occasions`/`message_drafts` only through explicit Room migrations and backup compatibility tests.

Benefits: clearer ownership, easier onboarding, cleaner domain tests, smaller review scope, and less documentation drift.

Risks: package moves can create noisy diffs; Room/entity moves require careful migration/test sequencing; feature moves should not happen in the same change as behavior changes.

## 32. New Feature Discovery and Innovation Analysis

| Category | Feature | Problem it solves | Target users | Technical complexity | Dependencies | Risks | Priority | Expected impact | Implementation recommendation |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Quick Win | Shared blocked-state copy library | Users see different language for similar blockers | All users | Low | Readiness policy strings | Copy churn | High | Higher trust and lower support | Centralize blocker labels and actions |
| Quick Win | Contact quality filter presets | Users do not know which contacts need enrichment | Daily users | Low/Medium | Contact read models | More UI states | Medium | Faster personalization cleanup | Add filters for missing event, no channel, low health, weak context |
| Quick Win | Backup freshness action card | Backup can be forgotten until recovery is needed | All users | Low | SecurePrefs timestamps | Notification fatigue | High | Lower data-loss risk | Show stale/never-backed-up card with direct export route |
| High Impact | Unified operational readiness use case | Readiness is fragmented across screens | Automation users, QA, support | Medium/High | Preferences, permissions, route policy, diagnostics | Refactor risk | Critical | Trustworthy automation | Define one read model and migrate Home/Messages/AI Doctor/Wish Preview |
| High Impact | Guided setup/fix wizard | AI Doctor is diagnostic-heavy | First-time users | Medium | Readiness use case, navigation | External settings returns | High | Higher activation | Step through required blockers with post-fix verification |
| High Impact | Failed-send recovery assistant | Users must interpret failures | Automation users | Medium | Dispatch attempts, provider taxonomy | Overpromising auto-fix | High | Lower support burden | Map failure reason -> fix route -> retry readiness |
| High Impact | Prompt context preview | Users cannot see why AI is generic | Reviewers, privacy-sensitive users | Medium | Message prompt context, memory/gift/style summaries | Exposing sensitive content | High | Better AI trust | Show summarized included context, not raw secrets |
| Strategic | Local-only/manual mode | Google sign-in blocks privacy-conscious/manual users | Privacy-sensitive users | High | Auth/nav/data ownership decisions | Product/support complexity | Medium | Broader adoption | Decide product scope before implementation |
| Strategic | Occasion/message-draft schema migration | Current names do not match target domain | Architects/devs | High | Room migrations, backup versioning | Data migration risk | Medium | Cleaner architecture | Stage after entity-boundary cleanup |
| Strategic | Per-note AI inclusion control | Private category is coarse | Privacy-sensitive users | Medium | Memory note schema/UI | More user decisions | Medium | Stronger privacy trust | Add explicit use-for-AI flag with conservative default |
| Strategic | Persisted gift suggestion feedback | Dismissals are local only | Gift Advisor users | Medium | New table/fields | Data bloat | Medium | Better suggestions over time | Store accepted/dismissed reasons and use in prompts |
| Roadmap | Release evidence center | Store/release readiness is outside repo | Product/release owners | Medium | CI artifacts, manual signoffs | Process overhead | High before release | Auditability | Generate release record from SSOT checklist |
| Innovation | Relationship maintenance planner | Turns analytics into a weekly plan | Power users | Medium/High | Analytics, health, next actions | Recommendation quality | Medium | Product differentiation | Rank relationship actions with explainable reasons |
| Innovation | Safe automation simulator | Users can preview what would send and why | Automation-first users | High | Scheduler, dispatch, readiness, fake clock | Complexity | Medium | Trust before full automation | Dry-run next 7/30 days without sending |

## 33. Architecture and Dependency Summary

Dependency summary:

| Layer | Owns | Must not own | Current caveat |
| --- | --- | --- | --- |
| `:app` | UI, navigation, ViewModels, Android app shell, widget | Room schema, provider internals, business policy | ViewModels still call some storage/config primitives directly |
| `:core:ui` | Design tokens, theme, reusable Compose components | Feature state/business decisions | Healthy boundary |
| `:core:model` | Pure models/enums/read models | Android, Room, Hilt, network | Healthy boundary and growing |
| `:core:domain` | Use cases, policies, repository/service contracts, pure mappers | Android UI, Room entities, data implementation | Healthy JVM boundary; keep guard tests green |
| `:core:data` | Room, repositories, workers, senders, integrations, prefs, backup | UI rendering | Large but coherent integration module |

External dependency risk summary:

| Dependency | Used for | Current mitigation | Residual risk |
| --- | --- | --- | --- |
| Firebase Auth/Google Sign-In | Required authentication | Auth failures typed; route guard | Cloud config and SHA-1 not verifiable locally |
| Google People API/ContactsProvider | Contact import | Sync outcomes and token handling | Permission/scope/provider failures need device validation |
| Gemini/Firebase Vertex AI | Message generation, classification, gifts | Parser fallback, quality gate, rate limiter, AI Doctor | Quota/runtime quality external |
| WorkManager/AlarmManager | Scheduling and recovery | Exact alarm fallback and boot/time recovery | OEM/background behavior needs device testing |
| SMS/Gmail/WhatsApp | Delivery | Readiness checks, route selection, dispatch attempts | Real provider/device and Play policy risks |
| SQLCipher/EncryptedSharedPreferences | Local privacy | Random key, quarantine, backup encryption | Recovery and key migration remain sensitive |

## 34. Maintenance Rules

1. Update this file in the same change that alters architecture, features, permissions, persistence, release gates, or user workflows.
2. Do not add new authoritative Markdown documents. Add sections here or generate external docs from this file.
3. If a supporting document must remain, mark it as historical/reference and point back to this SSOT.
4. Keep claims evidence-based. Mark external, runtime, or release-owner-only facts as unverified until validated.
5. Do not store secrets, API keys, tokens, message bodies, or personal contact fixtures in documentation.
6. When removing migrated documents, record the migration in this cleanup report and keep hygiene guards for superseded active-repo artifacts.
