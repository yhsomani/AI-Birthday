# RelateAI Single Source of Truth

Last updated: 2026-06-25

This is the canonical product and technical source of truth for this repository. It consolidates the former `features.md`, `SSOT_CONSOLIDATED.md`, `AUDIT_REPORT.md`, `CHANGELOG.md`, `docs/BRANCHING.md`, `docs/UI_VALIDATION.md`, `.kiro/steering/*.md`, `.kiro/specs/**/*.md`, and `.Jules/*.md` content.

Companion execution documents:

- [PLAN.md](PLAN.md): current audit, stabilization plan, architecture decisions, and debt registry.
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md): refined product model, user journeys, operating principles, and release definition.
- [IMPLEMENTATION_TASKS.md](IMPLEMENTATION_TASKS.md): micro-task backlog for building the major product step by step.
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md): completed incremental changes, UX rationale, and validation evidence.

Code remains the final authority. If this document conflicts with source, update this document after inspecting the source.

## 1. Product Definition

RelateAI is a local-first Android relationship assistant. It imports Google and device contacts, discovers birthdays and relationship events, learns relationship context and writing style, generates personalized wishes with Gemini, routes messages through approval modes, schedules delivery, and dispatches through SMS, WhatsApp, or Gmail SMTP without a custom backend.

Primary goals:

- Reduce forgotten birthdays, anniversaries, work anniversaries, and custom relationship moments.
- Generate relationship-aware wishes that match contact context, event type, channel, language, and user writing style.
- Support approval-first automation through global and per-contact modes.
- Keep relationship data on device except for explicit external integrations.
- Provide encrypted local storage and explicit encrypted backup/restore.

Target users:

- Android users on API 24+ who manage many family, friend, colleague, client, or professional relationships.
- Users who want different automation levels for normal contacts, VIP contacts, and manual-review contacts.
- Users who value privacy, local storage, and controllable AI-assisted messaging.

There is no custom server in this repository. External network surfaces are Google/Firebase/Gemini, Google People API, Gmail SMTP, and platform APIs.

## 2. Current Implementation Snapshot

| Area | Current value |
|---|---|
| Root Gradle project | `RelateAI` |
| Active modules | `:app`, `:core:domain`, `:core:data`, `:core:ui` |
| Application namespace | `com.example` |
| Release applicationId | `com.aistudio.relateai.qxtjrk` |
| Debug applicationId | `com.aistudio.relateai.qxtjrk.debug` |
| Min SDK | 24 |
| Target SDK | 36 |
| Compile SDK | 37 |
| Gradle wrapper | 9.4.1 |
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.2.10 |
| JDK toolchain | 21 |
| JVM bytecode target | 17 |
| Compose BOM | 2024.12.01 |
| Room | 2.7.0 |
| Room schema version | 13 |
| Hilt | 2.59.2 |
| WorkManager | 2.9.0 |
| SQLCipher | 4.5.4 |
| Firebase BOM | 34.12.0 |
| Firebase Vertex AI | 16.5.0 |
| Google AI client | 0.9.0 |
| JaCoCo | 0.8.12 |

Core stack:

- Jetpack Compose and Material 3 for UI.
- Hilt for dependency injection.
- Clean Architecture split into UI, domain, data, and shared UI modules.
- Room with SQLCipher for encrypted local database storage.
- EncryptedSharedPreferences for auth/config/preferences.
- WorkManager and AlarmManager for recurring automation and exact message/reminder scheduling.
- Firebase Auth and Google Sign-In for identity.
- Google People API and Android ContactsProvider for contact import.
- Gemini through Firebase Vertex AI and API-key-backed Google AI client paths.
- JavaMail/Gmail SMTP, Android SMS APIs, WhatsApp Accessibility Service.
- JUnit, Robolectric, Compose UI tests, Android instrumented smoke tests, lint, and aggregate JaCoCo coverage.

## 3. Repository Layout

```text
.
+-- app/                         Android app, Compose screens, ViewModels, manifest, resources
+-- core/
|   +-- domain/                  Domain models, repository/service contracts, use cases, policies
|   +-- data/                    Room, repositories, integrations, workers, senders, prefs, backup
|   +-- ui/                      Shared Compose theme and reusable components
+-- gradle/                      Gradle wrapper support and version catalog
+-- scripts/                     Helper scripts
+-- .github/workflows/           Android CI workflow
+-- SSOT.md                      This document
```

Important source roots:

```text
app/src/main/java/com/example/
app/src/main/java/com/example/ui/navigation/
app/src/main/java/com/example/ui/screens/
app/src/main/java/com/example/ui/viewmodel/
app/src/main/res/
core/domain/src/main/kotlin/com/example/domain/
core/domain/src/main/kotlin/com/example/core/db/entities/
core/data/src/main/kotlin/com/example/core/
core/data/src/main/kotlin/com/example/data/repository/
core/data/src/main/kotlin/com/example/di/
core/ui/src/main/kotlin/com/example/core/ui/
```

Do not add active UI code under any old `feature/` structure. Active feature UI lives under `app/src/main/java/com/example/ui`.

## 4. Architecture

Layering:

```text
Compose UI and ViewModels (:app)
    -> domain use cases and service/repository contracts (:core:domain)
    -> data implementations, Room, external integrations (:core:data)
    -> platform/external services

Shared UI components/theme (:core:ui) are used by :app.
```

Dependency rules:

- `:app` depends on `:core:domain`, `:core:data`, and `:core:ui`.
- `:core:data` exposes `api(project(":core:domain"))`.
- `:core:domain` should not depend on app or data implementation classes, except current Room entity files are physically located under `core/domain/src/main/kotlin/com/example/core/db/entities`.
- `:core:ui` contains shared Compose primitives and must stay independent of domain/data implementation details.

Dependency injection:

- `RelateAIApp` is annotated with `@HiltAndroidApp`.
- `AppModule` provides `AppDatabase`, DAOs, `SecurePrefs`, `AuthManager`, `OkHttpClient`, `GenerativeModel`, and `GeminiClient`.
- `AppModuleBinds` binds repository interfaces to data implementations.
- `ServiceModule` binds domain service interfaces to data implementations.
- Workers use Hilt WorkManager integration and `HiltWorkerFactory`.

Runtime startup:

- `RelateAIApp.onCreate()` checks certificate pin expiry, creates notification channels, warms database key and secure prefs, and schedules recurring workers unless under Robolectric.
- `MainActivity` enables edge-to-edge Compose, gates app access through biometric lock when enabled, shows the app shell, and requests SMS/notification permissions only after an in-app rationale.
- `RelateNavGraph` owns splash, onboarding, auth, home, contacts, events, messages, analytics, settings, activity, wish preview, style coach, backup, memory vault, gift advisor, chat history, and automation setup routes.

## 5. Recreate From Scratch

These steps rebuild the project from an empty directory or verify a fresh clone.

1. Install prerequisites:

```bash
# Required toolchain
JDK 21
Android SDK Platform 37
Android SDK Build Tools compatible with AGP 9.2.1
Android Studio or command-line SDK tools
Git
```

2. Create the root project and Gradle wrapper:

```bash
mkdir RelateAI
cd RelateAI
gradle wrapper --gradle-version 9.4.1
chmod +x gradlew
```

3. Create `settings.gradle.kts` with Google, Maven Central, and Gradle Plugin Portal plugin repositories, `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, root name `RelateAI`, and module includes:

```kotlin
include(":app")
include(":core:domain")
include(":core:data")
include(":core:ui")
```

4. Create `gradle/libs.versions.toml` with the versions listed in section 2 and aliases for AndroidX, Compose, Hilt, Room, WorkManager, Firebase, Google APIs, Retrofit/OkHttp/Moshi, SQLCipher, JavaMail, Robolectric, MockK, and JaCoCo support.

5. Create the root `build.gradle.kts`:

- Apply Android application/library, Kotlin Android, Kotlin Compose, KSP, Hilt, Google Services, secrets plugin aliases with `apply false`.
- Apply `jacoco`.
- Set Kotlin Android `jvmToolchain(21)` and Kotlin compile `JvmTarget.JVM_17`.
- Configure all `Test` tasks to use JDK 21.
- If `.gradle/trust/cacerts-zscaler` exists, pass it as the test JVM truststore.
- Register aggregate `jacocoDebugUnitTestReport` across `:app`, `:core:data`, `:core:domain`, and `:core:ui`.

6. Create module Gradle files:

- `app/build.gradle.kts`: Android application, Kotlin Compose, KSP, Hilt, Google Services, baseline profile, namespace `com.example`, applicationId `com.aistudio.relateai.qxtjrk`, debug suffix `.debug`, compileSdk 37, minSdk 24, targetSdk 36, versionCode 1, versionName `1.0`, Room schema export to `app/schemas`, release minify/shrink, release signing guard, Compose enabled.
- `core/domain/build.gradle.kts`: Android library, namespace `com.example.core.domain`, compileSdk 37, minSdk 24, Java 17, dependencies for coroutines, Room runtime, Paging, javax.inject, tests.
- `core/data/build.gradle.kts`: Android library, KSP, Hilt, namespace `com.example.core.data`, compileSdk 37, minSdk 24, Room schema export to `core/data/schemas`, test assets from schemas, dependencies for domain, Room, Hilt, WorkManager, security crypto, SQLCipher, Google APIs, Firebase, JavaMail, networking, tests.
- `core/ui/build.gradle.kts`: Android library, Kotlin Compose, namespace `com.example.core.ui`, compileSdk 37, minSdk 24, Compose enabled, Material 3, Navigation Compose, lifecycle Compose, Coil.

7. Add app source:

- Application shell: `RelateAIApp.kt`, `MainActivity.kt`, `SecurityChecks.kt`, `BiometricLockPolicy.kt`.
- Navigation: `Screen.kt`, `RouteArgumentCodec.kt`, `NavGraph.kt`.
- Screens: splash, onboarding, auth, home, contacts, events, messages, wish preview, analytics, activity history, settings, setup/AI Doctor, style coach, backup/restore, memory vault, gift advisor, chat history.
- ViewModels matching each screen plus shared `UiText`/feedback primitives.
- Widget provider: `BirthdayWidgetProvider`.

8. Add domain source:

- Models: `AutomationMode`, `ApprovalMode`, `EventType`, `MessageChannel`, `MessageStatus`.
- Automation policy: `AutomationSchedulePolicy`.
- Repository interfaces: contacts, events, messages, style profiles, message feedback, gifts, activity logs, memory notes.
- Service interfaces: AI, contact sync, message dispatcher, scheduler, event reminder scheduler, notification, preferences, backup, analytics report, test send.
- Use cases: sync contacts, discover events, generate/regenerate messages, approve/reject/revoke pending messages, dispatch message, test send, save manual event, update contact preferences, classify contacts, style analysis, dashboard metrics, analytics, health scoring.

9. Add data source:

- Room: `AppDatabase`, DAOs, migrations 2->13, schema JSON exports, SQLCipher support factory, `DatabaseKeyDerivation`, `LegacyDatabaseQuarantine`.
- Repositories: one implementation per domain repository.
- Preferences: `SecurePrefs`, `PreferencesRepositoryImpl`.
- AI: `GeminiClient`, `AiServiceImpl`, prompt builder, response parser, model types, rate limiter.
- Contacts: Google People sync, device contacts reader, merge/dedupe service.
- Automation: workers, daily scheduler, exact send scheduler, event reminder scheduler, receivers, notifications.
- Senders: dispatcher, SMS, WhatsApp, email, test send, SMS status receiver, email subject builder.
- Security/resilience: backup encryption/service, structured logging, sensitive redaction, retry, circuit breaker, fallback, health monitor, dead-letter queue.

10. Add resources and manifests:

- `app/src/main/AndroidManifest.xml` with permissions, main activity, deep links, WhatsApp accessibility service, receivers, FileProvider, widget, custom WorkManager initializer removal.
- `app/src/main/res/values/strings.xml` and `values-hi/strings.xml` with parity.
- `core/data/src/main/res/values/strings.xml` and `values-hi/strings.xml` for notification/system copy.
- XML configs: `network_security_config.xml`, `data_extraction_rules.xml`, `backup_rules.xml`, `accessibility_service_config.xml`, `shortcuts.xml`, `widget_birthday_info.xml`, `analytics_export_paths.xml`.
- Widget layout and launcher drawables/mipmaps.

11. Add Firebase config files:

- `app/google-services.json` for release applicationId `com.aistudio.relateai.qxtjrk`.
- `app/src/debug/google-services.json` for debug applicationId `com.aistudio.relateai.qxtjrk.debug`.
- Do not paste API keys into docs. Keep local config files out of public history when possible.
- Firebase/Google Cloud must enable Auth, Google Sign-In OAuth clients for the correct package/signing SHA-1, People API, and Gemini/Vertex access.

12. Add release signing environment variables only in CI or local shell:

```bash
export KEYSTORE_PATH=/absolute/path/to/release.keystore
export STORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
```

13. Run validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebugAndroidTest --no-configuration-cache
```

14. Optional connected validation with an idle unlocked device:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ui.MainActivityNavigationSmokeTest --no-configuration-cache
```

15. Release build:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleRelease --no-configuration-cache
```

Release artifact tasks must fail fast when release signing variables are missing.

## 6. Feature Hierarchy

```text
RelateAI
+-- Entry, Navigation, and Account
|   +-- Splash routing
|   +-- Onboarding setup checklist
|   +-- Google authentication
|   +-- Guest/developer bypass
|   +-- Settings
|   +-- Sign-out and local data purge
+-- Relationship Data
|   +-- Google contacts sync
|   +-- Device contacts import
|   +-- Deduplication and merge
|   +-- Contact list/search/filter/sort
|   +-- Contact detail personalization
|   +-- Memory notes
|   +-- Gift history
+-- Events
|   +-- Birthday discovery
|   +-- Anniversary discovery
|   +-- Work anniversary discovery
|   +-- Manual/custom event creation
|   +-- Event list/search/filter
|   +-- Event reminder notifications
+-- AI and Personalization
|   +-- Contact classification
|   +-- Message generation
|   +-- Message regeneration from feedback
|   +-- Style Coach
|   +-- Gift suggestions
|   +-- Fallback message generation
+-- Messages and Automation
|   +-- Pending message inventory
|   +-- Wish preview, edit, variant selection
|   +-- Approval, rejection, retry, revoke
|   +-- WorkManager daily chain
|   +-- Exact send alarms
|   +-- Boot rescheduling
|   +-- Automatic delivery route fallback
|   +-- SMS dispatch
|   +-- WhatsApp dispatch
|   +-- Email dispatch
+-- Insights and Operations
|   +-- Home dashboard
|   +-- Analytics
|   +-- Activity history
|   +-- Relationship health scoring
|   +-- Revival suggestions
|   +-- Automation setup / AI Doctor
|   +-- Widget and shortcuts
+-- Security, Data, and Developer Support
    +-- SQLCipher Room database
    +-- Encrypted preferences
    +-- Database key derivation
    +-- Encrypted backup/restore
    +-- Legacy DB quarantine
    +-- Network pinning
    +-- Resilience, logging, health monitor, dead-letter queue
    +-- Gradle build, CI, lint, unit tests, coverage
```

## 7. Feature Inventory

Status definitions:

- Fully Implemented: production path exists with meaningful tests and documented validation.
- Partially Implemented: usable but missing live validation, coverage, or hardening.
- Not Implemented: documented behavior has no active implementation.
- Broken: active implementation exists but fails required workflow.
- Outdated: code or docs describe obsolete behavior.
- Workspace-Specific: depends on a local path, fixed environment, or hardcoded project/device assumption.
- Deprecated: intentionally inactive and superseded.
- Experimental: present but not production path.

| ID | Feature | Status | Current evidence and required follow-up |
|---|---|---|---|
| F-001 | App shell, navigation, routes, permissions | Fully Implemented | Compose smoke coverage exists for permission rationale and bottom navigation; connected run needs idle device. |
| F-002 | Splash and onboarding | Fully Implemented | First-run onboarding-to-auth routing covered; connected run needs idle device. |
| F-003 | Authentication, guest mode, session state | Fully Implemented | Auth ViewModel and smoke action coverage exists; live OAuth requires credentials/device validation. |
| F-004 | Settings and secure configuration | Fully Implemented | Biometric, quiet-hour, reminder, channel blackout, sync, Gmail, AI, and sign-out settings implemented; live handoffs pending. |
| F-005 | Home dashboard and relationship planner | Fully Implemented | `HomeScreenInteractionTest` covers dashboard links, sync-error controls, and setup progress routing. |
| F-006 | Contact sync, import, and deduplication | Fully Implemented | Foreground/background sync share Google + device merge and event discovery. |
| F-007 | Contact list search, filter, sort | Fully Implemented | `ContactListScreenInteractionTest` covers search, filter, sort, retry/dismiss, row navigation. |
| F-008 | Contact detail personalization | Fully Implemented | Custom send time and skip-auto-wish affect generation/scheduling; live seeded validation pending. |
| F-009 | Event discovery | Fully Implemented | Leap-day and deactivation behavior covered by domain/worker tests. |
| F-010 | Manual and custom event creation | Fully Implemented | `SaveManualEventUseCase` creates contacts/events and schedules reminders. |
| F-011 | Messages inbox and bulk actions | Fully Implemented | `MessagesScreenInteractionTest` covers tabs, filters, row actions, and bulk actions. |
| F-012 | Wish preview, editing, feedback, regeneration | Fully Implemented | `WishPreviewScreenInteractionTest` covers variants, edit, why-signals, regenerate, test-send, approve/reject. |
| F-013 | Chat history | Fully Implemented | `ChatHistoryScreenInteractionTest` covers populated, loading, empty, and error states. |
| F-014 | Analytics and CSV export | Fully Implemented | Screen interaction and FileProvider CSV attachment tests exist; live share sheet pending. |
| F-015 | Activity history and audit log | Fully Implemented | Screen/ViewModel/repository coverage exists; live real-data validation pending. |
| F-016 | Style Coach | Fully Implemented | Manual and recent-message analysis paths covered; live AI quality pending. |
| F-017 | Memory Vault | Fully Implemented | Screen and ViewModel tests cover add, validation, pin/unpin, delete, error states. |
| F-018 | Gift Advisor | Fully Implemented | Screen and ViewModel tests cover records, AI suggestions, feedback, validation, errors. |
| F-019 | Encrypted backup and restore | Fully Implemented | UI and selected-document service tests exist; live document picker pending. |
| F-020 | Automation setup / AI Doctor | Fully Implemented | Diagnostics and actions implemented; live system-setting handoffs pending. |
| F-021 | Room database, schema, migrations | Fully Implemented | SQLCipher Room v13, exported schemas, migration/quarantine tests. |
| F-022 | AI contact classification | Fully Implemented | AI-disabled and generated classification outcomes covered; live AI pending. |
| F-023 | AI message generation and fallback | Fully Implemented | Respects skip-auto-wish, custom send time, quiet hours, blackout dates. |
| F-024 | Approval lifecycle | Fully Implemented | Approve/reject/retry/revoke domain and notification actions implemented. |
| F-025 | Exact scheduling and boot recovery | Fully Implemented | Exact send scheduling and boot rescheduling restore approved and pending Smart Approve auto-sends; live alarm validation pending. |
| F-026 | WorkManager automation chain | Fully Implemented | Daily chain covers sync, event discovery, message generation, holiday wishes, follow-ups, reminders, revival, style analysis. |
| F-027 | Dispatch orchestration and automatic delivery routing | Fully Implemented | Dispatch defers during quiet hours or blackout dates, resolves available delivery routes across enabled channels, and recovers failed worker status. |
| F-028 | SMS delivery and status callbacks | Fully Implemented | SMS sender/status receiver implemented; live SMS requires safe test recipient/SIM. |
| F-029 | WhatsApp Accessibility delivery | Fully Implemented | Service and sender implemented; live WhatsApp/accessibility validation pending. |
| F-030 | Gmail SMTP delivery and test send | Fully Implemented | Event-aware subject and test-send paths implemented; live credentials pending. |
| F-031 | Notifications and action receivers | Fully Implemented | Approval, reminder, setup, backup, revival notifications implemented; live permission actions pending. |
| F-032 | Revival suggestions | Fully Implemented | Low-health revival worker and notification path implemented. |
| F-033 | Relationship health scoring | Fully Implemented | Health scoring use case and tests exist. |
| F-034 | Widget, deep links, shortcuts | Fully Implemented | Manifest/widget/shortcut entries exist; live launcher/deep-link validation pending. |
| F-035 | Security, privacy, and local encryption | Fully Implemented | SQLCipher, encrypted prefs, backup exclusion, pinning, redaction tests. |
| F-036 | Sign-out data purge | Fully Implemented | AuthManager and settings flow clear local app data; test only with disposable data. |
| F-037 | Resilience, logging, health, dead-letter queue | Fully Implemented | Retry, circuit breaker, fallback, redaction, health, dead-letter coverage exists. |
| F-038 | External API and service interfaces | Fully Implemented | Domain/data boundaries exist for all integrations. |
| F-039 | Build, CI, release guard, coverage | Fully Implemented | CI runs test/lint/assemble/coverage and release-signing guard. |
| F-040 | Design system and localization | Fully Implemented | Shared theme/components and English/Hindi resource parity tests. |
| F-041 | Developer helper scripts and docs | Fully Implemented | `scripts/extract_strings.sh` resolves repo root dynamically; this SSOT replaces split docs. |
| F-042 | Biometric app lock enforcement | Fully Implemented | Cold start/resume gate and policy test exist; live prompt pending. |
| F-043 | Quiet hours, blackout dates, reminder toggles | Fully Implemented | `AutomationSchedulePolicy` enforces scheduling rules. |
| F-044 | Event reminder scheduling | Fully Implemented | Schedules/cancels/reschedules alarms from `notifyDaysBefore` and reminder toggle. |
| F-045 | Mood logs | Deprecated | Historical migration only. |
| F-046 | Dedicated birthday quick-add FAB/modal | Deprecated | Superseded by manual event creation. |
| F-047 | Legacy Retrofit Gemini model layer | Experimental | Present but not the primary production Gemini path. |

## 8. Feature Dependency Map

| Feature | Depends on | Enables |
|---|---|---|
| Google authentication | Firebase Auth, Google Sign-In, People API scope | Google contacts sync, authenticated Gemini path |
| Guest mode | AuthManager bypass, SecurePrefs guest flag | Local demo flow and mock contacts |
| Contact sync | Auth, People API, ContactsProvider, Room, dedupe logic | Events, classification, messages, analytics |
| Event discovery | Contact repository, event repository, date normalization | Message generation, reminders, dashboard |
| AI classification | Contact sync, Gemini client | Relationship-aware defaults and personalization |
| Message generation | Contact, event, style, memory, gift, AI settings | Pending messages and approval workflow |
| Approval workflow | Pending messages, notifications, scheduler | Dispatch eligibility |
| Exact scheduling | Pending approvals, AlarmManager, WorkManager fallback | On-time automated dispatch |
| Dispatch | Message dispatcher, channel senders, preferences | Sent history, delivery analytics, health scoring |
| Analytics | Contacts, events, sent messages, pending messages | Dashboard, CSV export, insights |
| Backup/restore | Room repositories, encryption, passphrase | Data portability |
| AI Doctor | Auth, prefs, workers, health monitor, dead-letter queue | Operational readiness and troubleshooting |
| Security storage | SecurePrefs, SQLCipher, key derivation | Safe local storage and sign-out purge |
| CI/build | Gradle modules, lint, tests, coverage, release guard | Release confidence |

## 9. Data Model and Database

Primary entities:

- `ContactEntity`: identity, Google id, name, phone/email, relationship classification, personalization settings, automation preferences, health score, budgets, lifecycle flags.
- `EventEntity`: contact id, event type, label, date/year, next occurrence, notification lead time, source, confidence, verification.
- `PendingMessageEntity`: generated variants, selected draft, channel, approval mode, status, scheduled time/year, model metadata, signals, fallback flag.
- `SentMessageEntity`: sent text, channel, sent time, delivery status, AI metadata, reply metadata.
- `StyleProfileEntity` and `StyleProfileHistoryEntity`: current and historical writing style.
- `MemoryNoteEntity`: per-contact notes for preferences, events, gifts, milestones, and general context.
- `GiftHistoryEntity`: per-contact gifts, cost/budget, occasion, feedback.
- `ActivityLogEntity`: activity/audit trail with type, severity, status, route, contact/event/message references.
- `MessageFeedbackEntity`: feedback from wish preview/regeneration.

Database facts:

- Active Room database: `AppDatabase`.
- Active schema version: 13.
- Exported schema location: `core/data/schemas/com.example.core.db.AppDatabase/13.json`; `app/schemas` also contains older app-level exports.
- SQLCipher is used through Room SupportFactory.
- Destructive migration fallback must stay disabled.
- Legacy plaintext `relateai.db`, WAL, and SHM files are quarantined under protected no-backup storage before encrypted open.
- Platform backup and data extraction exclude database and encrypted preference files.
- Mood logs are not active; they are migration history only.

DAOs:

- `ContactDao`
- `EventDao`
- `PendingMessageDao`
- `SentMessageDao`
- `StyleProfileDao`
- `MemoryNoteDao`
- `GiftHistoryDao`
- `ActivityLogDao`
- `MessageFeedbackDao`

Migration rule:

- Every schema change must add a non-destructive Room migration, update exported schemas, and add/adjust migration tests.
- Explicit `@Index(name = "...")` values must match manually created index names.

## 10. Runtime Surfaces

Manifest permissions:

- `SEND_SMS`
- `READ_CONTACTS`
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `SCHEDULE_EXACT_ALARM`
- `USE_EXACT_ALARM`
- `RECEIVE_BOOT_COMPLETED`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `POST_NOTIFICATIONS`
- `WAKE_LOCK`

Optional hardware:

- `android.hardware.telephony` is not required, so non-SMS-capable devices can install.

Deep links:

- `relateai://wish/{contactId}/{messageRef}`
- `relateai://contact/{contactId}`
- `relateai://settings`

Receivers:

- `MessageDispatchReceiver`: AlarmManager dispatch entry point.
- `BootReceiver`: reschedules exact sends, reminders, and periodic workers after reboot.
- `ApprovalReceiver`: handles notification approve/reject/retry actions.
- `EventReminderReceiver`: shows event reminder notifications.
- `SmsStatusReceiver`: updates SMS sent/delivered state.
- `BirthdayWidgetProvider`: app widget updates.

Services/providers:

- `WhatsAppAccessibilityService`: sends queued WhatsApp jobs by observing WhatsApp/WhatsApp Business UI.
- `androidx.core.content.FileProvider`: shares analytics CSV exports from cache.
- WorkManager initializer is removed from AndroidX Startup and replaced by the Hilt-aware configuration provider.

Shortcuts/widget:

- Dynamic shortcuts: compose message and view contacts.
- Widget: birthday/upcoming event widget with hourly update period.

## 11. Preferences and Configuration

Sensitive preferences live in encrypted preference files through `SecurePrefs`.

Important stored values:

- Google OAuth token.
- Firebase UID.
- Gemini API key.
- Gmail sender email and app password.
- Global automation mode.
- Theme mode.
- Quiet hours start/end.
- Channel blackout list.
- Blackout date list.
- Biometric lock toggle.
- Birthday reminder toggle.
- AI wish generation toggle.
- Contact sync token.
- Onboarding completion.
- Guest mode.
- Last sync error.
- Last backup timestamp.
- Legacy unencrypted DB quarantine notice flag.

Defaults:

- Global automation mode: `SMART_APPROVE`.
- Theme mode: `SYSTEM`.
- Quiet hours: 22 to 8.
- Birthday reminders enabled: true.
- AI wish generation enabled: true.
- Biometric lock enabled: false.

Security rule:

- Secure storage initialization must fail securely. Do not fall back to plaintext `Context.MODE_PRIVATE` preferences when encrypted storage fails.

## 12. AI and Message Generation

AI responsibilities:

- Contact relationship classification.
- Personalized message variant generation.
- Regeneration from user feedback.
- Style Coach analysis.
- Gift suggestions.
- Revival suggestions for neglected contacts.
- Fallback message generation when AI is disabled or unavailable.

Primary files:

- `core/data/src/main/kotlin/com/example/core/gemini/AiServiceImpl.kt`
- `GeminiClient.kt`
- `PromptBuilder.kt`
- `ResponseParser.kt`
- `RateLimiter.kt`
- `GeminiModels.kt`
- `core/domain/src/main/kotlin/com/example/domain/service/AiService.kt`
- `GenerateMessageUseCase.kt`
- `RegeneratePendingMessageUseCase.kt`
- `StyleAnalysisUseCase.kt`
- `ClassifyContactUseCase.kt`

Rules:

- If AI wish generation is disabled, use cases must return a visible disabled outcome instead of calling Gemini.
- Prompt building must avoid sending unnecessary PII.
- Response parsing must be defensive against malformed JSON, partial variants, and error JSON.
- AI errors surfaced to users must be stable and redacted.
- Six generated variants are supported where available.
- Wish Preview must show why-signals explaining personalization inputs.

## 13. Automation and Delivery

Workers:

| Worker | Purpose |
|---|---|
| `DailyTriggerWorker` | Starts daily automation and backup reminder checks. |
| `ContactSyncWorker` | Runs background contact sync and optional classification. |
| `EventDiscoveryWorker` | Rebuilds upcoming events from contacts. |
| `MessageGenerationWorker` | Generates pending messages for upcoming events. |
| `MessageDispatchWorker` | Dispatches approved/due messages and recovers unexpected failures. |
| `RevivalWorker` | Suggests reconnect messages for low-health contacts. |
| `StyleAnalysisWorker` | Refreshes writing style from sent history. |

Schedulers:

- `WorkerScheduler.scheduleAll()` registers periodic daily, revival, and style-analysis work.
- `WorkerScheduler.scheduleDailyAutomationChain()` chains contact sync -> event discovery -> message generation.
- `DailyScheduler.scheduleExactSend()` schedules exact send alarms by pending-message id.
- `EventReminderScheduler` schedules reminder alarms from event occurrence and `notifyDaysBefore`.
- Boot recovery reschedules pending exact sends, event reminders, and recurring workers.

Delivery channels:

- SMS uses Android `SmsManager` plus sent/delivered broadcasts.
- WhatsApp uses an Accessibility Service plus WhatsApp deep-link/open-chat flow and falls back to SMS where configured.
- Email uses Gmail SMTP through JavaMail on port 587 with STARTTLS.

Dispatch rules:

- Pending messages must be approved or eligible by automation mode before dispatch.
- Dispatch must defer during quiet hours or blackout dates.
- Disabled channel preferences must be respected.
- A message set to `DISPATCHING` must be moved to `FAILED` if an unexpected dispatcher exception occurs.
- Exact alarm request codes must be based on pending-message id to avoid collisions.

Automation modes:

- Default/global.
- Smart approve.
- VIP approve.
- Fully auto.
- Always ask.

## 14. Security, Privacy, and Reliability

Implemented controls:

- SQLCipher encrypted Room database.
- EncryptedSharedPreferences for auth/config state.
- Deterministic database key derivation with cached-key validation.
- Legacy plaintext DB quarantine instead of deletion.
- Auto Backup disabled and sensitive files excluded in legacy and API 31+ backup rules.
- Explicit encrypted backup/restore with passphrase, bounded import reads, transaction rollback, and stable failure reasons.
- Network pinning for Google/Firebase/Gmail-related hosts with expiration `2027-06-01`.
- Release signing guard requiring production signing variables.
- Sensitive log redaction for emails, tokens, API keys, passphrases, phone-like values, People API URLs, and fallback/provider errors.
- Biometric app lock gate on cold start/resume when enabled.
- Dead-letter queue, health monitor, retry, circuit breaker, and fallback primitives.

Do not:

- Commit secrets, keystores, app passwords, or private API keys.
- Re-enable destructive Room migration fallback.
- Add plaintext preference fallbacks.
- Log raw OAuth tokens, API keys, email passwords, full People API URLs, backup passphrases, or phone numbers.
- Use release builds signed with debug credentials.

## 15. UI, Navigation, and Localization

Primary routes:

- Splash
- Onboarding
- Auth
- Home
- Contacts
- Contact Detail
- Events
- Messages
- Wish Preview
- Analytics
- Activity History
- Settings
- Style Coach
- Backup/Restore
- Automation Setup / AI Doctor
- Memory Vault
- Gift Advisor
- Chat History

Bottom navigation:

- Home
- Contacts
- Events
- Messages
- Analytics

UI conventions:

- Compose + Material 3.
- Shared colors/type/theme live in `core/ui`.
- Visible strings should come from resources.
- English and Hindi resource key parity is tested.
- Search fields should include conditional clear buttons with accessible descriptions.
- `LazyColumn` and `LazyRow` items must use stable keys for dynamic lists.
- Do not add hardcoded visible strings in screens covered by `NoHardcodedStringsRegressionTest`.
- Use `collectAsStateWithLifecycle()` for lifecycle-aware state collection.

## 16. Testing and Validation

Standard full validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache
```

Optional local truststore:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit
```

Instrumentation build:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-configuration-cache
```

Connected smoke:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ui.MainActivityNavigationSmokeTest --no-configuration-cache
```

Fresh validation evidence from 2026-06-11:

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 1m 23s`, 221 actionable tasks, 17 executed, 204 up-to-date.
- Unit test XML aggregate: 76 suites, 277 tests, 0 failures, 0 errors, 0 skipped.
- Lint passed for debug.
- Debug APK assembled successfully.
- Aggregate JaCoCo XML counters: 66,058 covered / 136,016 total instructions (48.6%), 7,895 covered / 14,972 total lines (52.7%), and 3,225 covered / 10,006 total branches (32.2%).
- JaCoCo HTML report: `build/reports/jacoco/jacocoDebugUnitTestReport/html/index.html`.
- Lint reports: `app/build/reports/lint-results-debug.*`, `core/data/build/reports/lint-results-debug.*`, `core/domain/build/reports/lint-results-debug.*`, and `core/ui/build/reports/lint-results-debug.*`.
- Debug APK exists under `app/build/outputs/apk/debug/`.

Focused validation evidence from 2026-06-25:

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.screens.home.HomeScreenInteractionTest --tests com.example.ui.viewmodel.HomeViewModelTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 53s`, 89 actionable tasks, 11 executed, 78 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.SettingsViewModelTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 26s`, 89 actionable tasks, 21 executed, 68 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.MemoryVaultViewModelTest --tests com.example.ui.screens.memoryvault.MemoryVaultScreenInteractionTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 27s`, 89 actionable tasks, 21 executed, 68 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.MessagesViewModelTest --tests com.example.ui.screens.messages.MessagesScreenInteractionTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 12s`, 89 actionable tasks, 4 executed, 85 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.AutomationSetupViewModelTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 20s`, 89 actionable tasks, 11 executed, 78 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.screens.contacts.ContactDetailPersonalizationQualityCardTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 24s`, 89 actionable tasks, 19 executed, 70 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.HomeViewModelTest --tests com.example.ui.screens.home.HomeScreenInteractionTest --tests com.example.ui.viewmodel.AutomationSetupViewModelTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 44s`, 89 actionable tasks, 22 executed, 67 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.domain.usecase.SaveManualEventUseCaseTest --tests com.example.ui.viewmodel.EventsViewModelTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 39s`, 89 actionable tasks, 27 executed, 62 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.screens.settings.SettingsScreenInteractionTest --tests com.example.ui.viewmodel.SettingsViewModelTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 34s`, 89 actionable tasks, 10 executed, 79 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.MessagesViewModelTest --tests com.example.ui.screens.messages.MessagesScreenInteractionTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 1m 2s`, 89 actionable tasks, 9 executed, 80 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.WishPreviewViewModelTest --tests com.example.ui.screens.wish.WishPreviewScreenInteractionTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 37s`, 89 actionable tasks, 21 executed, 68 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.HomeViewModelTest --tests com.example.ui.screens.home.HomeScreenInteractionTest --tests com.example.ui.LocalizationParityTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 43s`, 89 actionable tasks, 11 executed, 78 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest :app:lintDebug --no-configuration-cache` passed.
- Gradle result: `BUILD SUCCESSFUL in 54s`, 161 actionable tasks, 26 executed, 135 up-to-date. Lint report: `app/build/reports/lint-results-debug.html`.

Representative automated coverage:

- Domain use cases for sync, event discovery, generation, approval, rejection, regeneration, dispatch, analytics, health scoring, manual event save, contact preferences, test send.
- Room DAOs, migrations, SQLCipher key derivation, plaintext DB quarantine.
- Backup encryption, export/import, wrong passphrase, malformed input, oversized import, rollback.
- Gemini prompt/response parsing and fallback behavior.
- WorkManager workers and automation pipeline.
- Scheduler and event reminder policy.
- SMS status receiver and email subject builder.
- Resilience primitives and redaction.
- ViewModels for primary screens.
- Compose/Robolectric screen interactions for home, contacts, messages, wish preview, analytics, activity history, style coach, memory vault, gift advisor, backup/restore, chat history.
- Localization parity and hardcoded-string regression.
- Production readiness config and helper script portability.

Live validation still needed:

| Surface | Status | Missing prerequisite |
|---|---|---|
| Connected app shell smoke | Blocked | Idle, unlocked device; previous run on `1b87b5db` stalled while another app was foregrounded. |
| Google OAuth and People API | Blocked | Configured Google account, OAuth clients, contacts permission, idle device. |
| Device ContactsProvider sync | Blocked | Seeded contacts and contact permission on device. |
| Gemini live generation/classification | Blocked | Gemini API key or authenticated Firebase Vertex path, test data. |
| Gmail SMTP live send | Blocked | Gmail sender and app password. |
| SMS live send/status | Blocked | SIM/SMS-capable device and safe test recipient. |
| WhatsApp live automation | Blocked | WhatsApp installed, accessibility service enabled, safe test recipient. |
| Notification actions | Blocked | Notification permission and generated pending/reminder data. |
| Exact alarms/boot recovery | Blocked | Exact alarm permission and scheduling window. |
| Widget | Blocked | Launcher widget placement. |
| Dynamic shortcuts | Blocked | Launcher shortcut inspection. |
| Deep links | Blocked | Installed debug app and `adb shell am start` validation. |
| Biometric lock | Blocked | Device credential/biometric enrollment and live prompt validation. |

## 17. CI and Release

CI workflow: `.github/workflows/android.yml`.

CI triggers:

- Push to `main` or `master`.
- Pull request targeting `main` or `master`.

CI steps:

- Checkout.
- Set up Temurin JDK 21 with Gradle cache.
- `chmod +x gradlew`.
- `./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache`.
- `./gradlew jacocoDebugUnitTestReport --no-configuration-cache`.
- Verify `assembleRelease` fails when signing environment variables are absent.
- Upload lint, unit-test, coverage, and debug APK artifacts.

Release behavior:

- `release` build enables minification and resource shrinking.
- Release signing is configured only when `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` are present and valid.
- Release artifact tasks fail early when signing is missing.
- Baseline profile plugin is enabled with `mergeIntoMain = true`.

Branching strategy:

- `main`: production-ready; do not push directly.
- `phase/N-*`: one branch per phase, merged after checkpoint validation.
- `fix/ID-*`: one branch per specific bug fix.
- `feature/*`: one branch per UI screen or feature.

Merge rules:

- Never commit secrets, `.env`, local properties, keystores, or private signing files.
- Run at least `./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` before merging.
- Feature branches should update this `SSOT.md` when behavior, setup, validation, or architecture changes.
- Pull requests should describe changes, validation, risks, and live-validation gaps.

## 18. Developer Commands

```bash
# Full local verification
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache

# Unit tests only
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest --no-configuration-cache

# Lint
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew lintDebug --no-configuration-cache

# Debug APK
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleDebug --no-configuration-cache

# Release APK, requires release signing env vars
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleRelease --no-configuration-cache

# Coverage
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew jacocoDebugUnitTestReport --no-configuration-cache

# Android test APK
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebugAndroidTest --no-configuration-cache

# Connected smoke
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ui.MainActivityNavigationSmokeTest --no-configuration-cache

# Find hardcoded Compose Text literals
scripts/extract_strings.sh
```

## 19. Historical Change Log

Unreleased changes:

- Added automatic delivery route fallback so AI-generated messages can send through the best available enabled channel when the preferred channel is unavailable.
- Added smart initial channel selection for AI-created pending messages using contact availability, disabled-channel settings, email setup, and past successful delivery history before dispatch fallback runs.
- Smart Approve generated wishes now schedule exact due-time dispatch while still notifying the user for review before send time.
- Daily AI message generation now prepares a 7-day queue of upcoming event drafts so automatic sends are scheduled earlier and Smart Approve has more review time.
- Revival AI reconnect messages now respect contact/global automation modes and schedule automatic send for Fully Auto and Smart Approve.
- Added AI auto-send quality gate so fallback or generic Fully Auto drafts are downgraded to Smart Approve review before automatic due-time dispatch.
- Added post-event AI follow-ups that scan recent unreplied AI wishes, draft a light follow-up, and schedule it with the same automation, quality, quiet-hour, and channel-routing rules.
- Added fixed-date holiday AI wishes that generate personalized New Year, Republic Day, Women's Day, Independence Day, Gandhi Jayanti, and Christmas messages and schedule them automatically through the same approval and dispatch rules.
- Added Failed tab recovery assistant in Messages with direct AI Doctor routing while keeping retry manual.
- Added Settings sign-out confirmation checklist before local data is cleared.
- Added duplicate manual event detection with an explicit Save anyway override.
- Added shared setup progress summary on Home and AI Doctor so setup status is visible before opening detailed diagnostics.
- Added Contact Detail personalization next-step prompts to reduce generic AI wish inputs.
- Grouped AI Doctor readiness checks into Required, Quality, Reliability, and Recovery sections.
- Added Messages readiness labels for review, approved, failed, and channel/setup prerequisite states.
- Added inline Memory Vault validation for whitespace-only notes and a defensive ViewModel guard against empty note saves.
- Added Settings backup freshness so users can see the last encrypted backup status before opening Backup & Restore.
- Routed Home pending-approval readiness and planner items directly to Messages while keeping setup problems routed to AI Doctor.
- Added feature source of truth, compliance specs, UI validation ledger, now consolidated here.
- Enforced biometric app lock at cold start/resume.
- Added automation scheduling policy for custom send time, quiet hours, blackout dates, reminders, and channel blackout.
- Added AlarmManager-backed event reminders and boot/daily rescheduling.
- Added event-aware Gmail SMTP subjects.
- Added localization parity and helper-script portability tests.
- Added Compose/instrumented smoke coverage and side-by-side debug package.
- Added screen interaction coverage for home, contacts, messages, wish preview, chat history, analytics, activity history, style coach, memory vault, gift advisor, backup/restore.
- Added FileProvider CSV sharing for analytics export.
- Added selected-document backup export/import coverage.
- Refreshed Hindi strings for touched features.
- Background contact sync now shares foreground Google/device merge and event-discovery behavior.
- Message generation respects skip-auto-wish, quiet hours, blackout dates, and custom send time.
- Dispatch defers newly blocked sends.
- Backup/restore actions are safer on small screens and disabled during active operations.

Version 1.0.0, 2026-06-08:

- Added SMS delivery tracking and confirmation.
- Hardened SMS/WhatsApp exception handling.
- Built primary Compose screens for dashboard, contacts, events, messages, analytics, settings, style coach, backup, memory, gifts.
- Added initial runtime permission flows.
- Added Room schema v11 migrations.
- Improved Google sync and mock-contact cleanup.
- Expanded automated tests to use cases, workers, migrations, and SMS receiver.

Version 0.1.0, 2026-06-07:

- Added SQLCipher and encrypted preference security.
- Added sign-out cache wiping.
- Added automation engine duplicate guards and leap-year logic.
- Added WhatsApp accessibility validation loop.
- Added Style Coach writing profile configuration.

## 20. Audit History Condensed

Major audit/fix passes that shaped the current project:

- App shell permissions, navigation, route encoding, bottom nav localization, theme/accessibility cleanup.
- Contact sync sensitive logging redaction.
- Shared sync error UI and retry/dismiss behavior.
- Google Sign-In configuration hardening and structured auth failures.
- Database data-safety hardening, no destructive migration fallback, legacy DB quarantine.
- Backup service abstraction, transactional restore, malformed backup handling, bounded import size.
- Platform Auto Backup disabled and sensitive backup exclusions.
- Release signing guard and CI release-signing verification.
- Style Coach localization, lifecycle collection, recent-message analysis.
- AI Doctor localization, diagnostics, redacted failures, Firebase degradation.
- Memory Vault localization, validation, category normalization.
- Gift Advisor localization, validation, dialog behavior, AI suggestions.
- Contact detail preference hardening and typed controls.
- Home discoverability quick actions.
- Fallback/provider log redaction.
- Dispatch worker failure recovery from stuck `DISPATCHING`.
- Cached database key validation.
- Aggregate JaCoCo coverage reporting.
- Resilience primitive test coverage.
- Device contact import, real test-send email, visible email/quiet-hour/channel/biometric settings, onboarding setup checklist.

## 21. Engineering Guardrails

Security:

- Never fall back to plaintext SharedPreferences when encrypted storage fails.
- Redact secrets and PII before Logcat, structured logs, health snapshots, AI fallback JSON, or user-visible diagnostics.
- Preserve legacy plaintext DBs through quarantine, not deletion.
- Keep platform backup disabled for sensitive local data.

Room:

- Add explicit index names in `@Entity(indices = ...)` when migrations create indexes manually.
- Keep migrations non-destructive and schema-exported.
- Add tests for every supported upgrade path touched.

Compose:

- Use stable keys in `LazyColumn` and `LazyRow` for dynamic lists.
- Use conditional clear buttons for search fields and provide accessible descriptions.
- Keep visible strings in resources and maintain English/Hindi parity.
- Prefer lifecycle-aware state collection.

AI/prompting:

- Use `buildString { appendLine(...) }` for large dynamic prompt strings.
- Keep prompts minimal and privacy-conscious.
- Treat malformed or partial model output as expected input, not a crash.

Android compatibility:

- With minSdk 24, use core library desugaring for Java Time or prefer `Calendar` in worker/scheduler logic.
- Keep exact alarm, notification, SMS, contacts, and accessibility behavior behind explicit setup/permission flows.

Docs:

- Update this `SSOT.md` in the same change as any feature, architecture, build, validation, or release behavior change.
- Do not recreate split feature/audit/changelog docs unless explicitly requested.

## 22. Generated and Local Artifacts

Raw logs and generated reports are not maintained documentation:

- `app_logs*.txt`
- `logcat*.txt`
- `lint_baseline_pre_fixes.txt`
- Gradle build directories
- JaCoCo/lint/test output directories

They may be useful evidence, but this SSOT is the maintained documentation.

Patch helper Python files in the root appear to be historical one-off helpers. Do not use them as reconstruction inputs unless a future task explicitly validates and documents them.

## 23. Feature Compliance Requirements and Task Status

Compliance goal:

- Keep the Android app aligned with this SSOT, with each documented feature implemented, accurately classified, tested, documented, and validated on device where possible.

Compliance requirements:

- The repository must use this root `SSOT.md` as the feature, architecture, validation, changelog, and process source of truth.
- The implementation must avoid local workspace assumptions such as `/workspace` paths and stale module names.
- Biometric lock must block app access on protected cold start/resume when enabled and use localized text.
- Message automation must respect contact custom send time, contact skip-auto-wish, global quiet hours, blackout dates, channel blackout, reminder toggles, and event `notifyDaysBefore`.
- Background contact sync must use the same Google + device merge and event-discovery behavior as foreground sync.
- Gmail SMTP subjects must describe the actual event type instead of always using a birthday subject.
- Event reminders must be scheduled, canceled, and rescheduled from active events and user preferences.
- Unit, Robolectric, Compose/instrumented, lint, build, coverage, and manual device validation must be recorded here.
- Deprecated features F-045 and F-046 must remain deprecated unless explicitly re-scoped.
- Experimental feature F-047 must remain experimental unless it enters the active Gemini call path.

Acceptance criteria:

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache` passes.
- Every primary screen and interactive workflow has pass/fail/blocked evidence recorded in this file.
- New or changed behavior has focused tests.
- Feature status in section 7 matches implementation.
- Feature-scoped commits describe implemented changes.

Task status:

| Task | Status |
|---|---|
| F-001-F-003: verify app shell, onboarding, auth, guest mode, and navigation on device. | Blocked by idle unlocked device. |
| F-001-F-003: add Compose UI smoke coverage for onboarding/auth and app-shell bottom navigation. | Done. |
| F-001-F-003: make debug UI validation install side-by-side as `com.aistudio.relateai.qxtjrk.debug`. | Done. |
| F-004/F-042: implement biometric lock enforcement. | Done. |
| F-004/F-042: validate biometric lock prompt and resume behavior on device. | Blocked by live device validation. |
| F-006/F-026: route background contact sync through foreground sync use case. | Done. |
| F-008/F-023/F-025/F-027/F-043: implement automation schedule policy for custom send time, skip-auto-wish, quiet hours, blackout dates, and dispatch deferral. | Done. |
| F-010/F-031/F-044: implement event reminder scheduling from `notifyDaysBefore` and user reminder toggle. | Done. |
| F-008/F-023/F-025/F-027/F-043/F-044: validate scheduling, deferral, and reminders on device. | Blocked by live device validation. |
| F-005: add Home dashboard Compose/Robolectric interaction coverage. | Done. |
| F-007: add Contact List Compose/Robolectric interaction coverage. | Done. |
| F-011: add Messages inbox Compose/Robolectric interaction coverage. | Done. |
| F-012: add Wish Preview Compose/Robolectric interaction coverage. | Done. |
| F-013: add Chat History Compose/Robolectric interaction coverage. | Done. |
| F-014: add Analytics Compose/Robolectric interaction coverage and FileProvider CSV export validation. | Done. |
| F-015: add Activity History Compose/Robolectric interaction coverage and repository-error handling. | Done. |
| F-016: add Style Coach Compose/Robolectric interaction coverage and refresh touched Hindi strings. | Done. |
| F-017: add Memory Vault Compose/Robolectric interaction coverage and refresh touched Hindi strings. | Done. |
| F-018: add Gift Advisor Compose/Robolectric interaction coverage and refresh touched Hindi strings. | Done. |
| F-019: add Backup/Restore Compose/Robolectric interaction coverage and selected-document export/import tests. | Done. |
| F-019: validate Android document picker export/import on an idle, unlocked device. | Blocked by live device validation. |
| F-030: make Gmail SMTP subject event-aware. | Done. |
| F-040: add localization parity tests and resource-back touched notification strings. | Done. |
| F-041: make helper scripts repo-root aware and consolidate stale steering docs into this SSOT. | Done. |
| F-011-F-020/F-028/F-029/F-032-F-034/F-036-F-038: run targeted code inspection, tests, and UI/device validation. | Automated coverage done for many flows; live system integrations still blocked as listed in section 16. |
| F-045-F-047: confirm deprecated/experimental classification remains accurate. | Done. |
| Run full Gradle validation and update documentation. | Done on 2026-06-11; see section 16. |

## 24. Complete Feature Details

This section documents each user-facing and operational feature at implementation level: what starts it, what inputs it accepts, what data it reads, how it works, what it outputs, what it stores, and what failures the user can see. Source code is still the final authority, but this is the intended feature reference for product, QA, and future development.

### 24.1 App Entry, Navigation, Permissions, and Shell

Purpose:

- Provide the app root, bottom navigation, permission rationale, biometric gate, and route ownership.

Main files:

- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/ui/navigation/Screen.kt`
- `app/src/main/java/com/example/ui/navigation/NavGraph.kt`
- `app/src/main/java/com/example/ui/navigation/RouteArgumentCodec.kt`

Inputs:

- App launch intent.
- Deep links: `relateai://wish/{contactId}/{messageRef}`, `relateai://contact/{contactId}`, `relateai://settings`.
- Runtime permission state for SMS and notifications.
- Biometric-lock preference.
- Navigation actions from screens and notification intents.

How it works:

- `MainActivity` creates the Compose shell and Hilt-aware navigation graph.
- If biometric lock is enabled, `BiometricLockPolicy` decides whether the shell is unlocked, locked, unavailable, authenticating, or in error state.
- The bottom bar is shown only on primary routes: Home, Contacts, Events, Messages, Analytics.
- When a bottom-bar route is tapped, navigation uses `launchSingleTop`, state restore, and pop-up-to Home behavior.
- A permission rationale dialog is shown once per composition when the bottom bar is visible and SMS or notification permission is missing.

Outputs:

- A routed Compose app surface.
- Bottom navigation with Home, Contacts, Events, Messages, and Analytics.
- Permission request launcher for `SEND_SMS` and `POST_NOTIFICATIONS` where applicable.
- Biometric lock screen with unlock/retry states when enabled.

Failure and edge behavior:

- If biometric/device credential is unavailable while lock is enabled, the app shows an unavailable state instead of exposing data.
- Permission denial does not block browsing the app, but SMS sends and notifications may fail later and are surfaced by setup diagnostics.

### 24.2 Splash, Onboarding, Authentication, Guest Mode, and Sign-Out

Purpose:

- Decide first screen, complete setup education, authenticate the user, support local guest/demo mode, and securely wipe local data at sign-out.

Main files:

- `SplashViewModel.kt`
- `OnboardingViewModel.kt`
- `AuthViewModel.kt`
- `AuthManager.kt`
- `SettingsViewModel.kt`

Inputs:

- `SecurePrefs.isOnboardingComplete()`.
- Firebase/Google sign-in state.
- Google sign-in result intent and result code.
- Google web client id resource.
- Optional developer bypass action.
- Sign-out action from Settings.

How it works:

- Splash routes to Home if already signed in, Auth if onboarding is complete but not signed in, or Onboarding for a fresh install.
- Onboarding marks `onboarding_complete=true` and can open AI Doctor setup checklist.
- Google sign-in requests email, ID token, and Google Contacts readonly scope.
- AuthManager signs into Firebase with Google credentials and stores profile state.
- Guest bypass sets guest mode and a local developer profile.
- Settings shows a destructive-action checklist before sign-out so the user sees what local data, preferences, credentials, and external access state are affected.
- Sign-out cancels WorkManager jobs, clears notifications, clears Room tables, closes and resets the database, clears encrypted preferences, clears cached DB key material, deletes DB/WAL/SHM files, signs out from Firebase, and revokes Google access.

Outputs:

- Authenticated user profile with display name, email, and optional photo URL.
- Guest user profile for developer/demo mode.
- Navigation to Home after successful auth.
- Full local purge and navigation back to Auth after sign-out.

Failure and edge behavior:

- Invalid OAuth configuration shows a Google config error before launching sign-in.
- Google API status code 7 maps to network error; status code 10 maps to developer configuration error.
- Firebase credential failure is shown as Firebase auth error.
- Sign-out continues to Firebase/Google sign-out even if local purge has partial failures.

### 24.3 Home Dashboard

Purpose:

- Give the user a daily overview of relationship health, message workload, upcoming events, setup readiness, and high-value actions.

Main files:

- `HomeScreen.kt`
- `HomeViewModel.kt`
- `GetDashboardMetricsUseCase.kt`

Dashboard contents:

| Area | What it contains | Source |
|---|---|---|
| Header | Greeting with user name, optional profile photo, Settings button | `AuthManager.userProfile` |
| Sync error card | Last contact-sync error with retry and dismiss actions | `PreferencesRepository.getLastSyncError()` |
| Readiness banner | Setup warning, no-contact prompt, or pending-approval prompt | Derived from sync error, contact count, pending count |
| Setup progress | Shared completed/total readiness summary with blocker or warning count; tap opens AI Doctor | `SetupProgressSummary` from contact/sync, AI, and approval signals |
| Stats row 1 | Wishes Sent, Upcoming | Sent message count, events in next 30 days |
| Stats row 2 | Contacts, Pending, Score | Contact count, pending message count, average contact health |
| Quick actions | Analytics, Activity History, Style Coach, AI Doctor, Backup/Restore | Static navigation actions |
| Relationship Planner | Up to five action items: pending approvals, low-health contacts, and next events | Contacts, pending messages, upcoming events |
| Upcoming Birthdays | Birthday events in the next 30 days with display date | Event repository |
| Bottom navigation | Home, Contacts, Events, Messages, Analytics | App shell |
| Permission rationale | SMS and notification permission request prompt | Runtime permission state |

Inputs:

- Contact list.
- Pending message list.
- Sent message count.
- Upcoming events for the next 30 days.
- User profile.
- Last sync error.
- AI wish generation toggle and Gemini API-key readiness.
- User taps on quick actions, planner items, Settings, retry, and dismiss.

How it works:

- The ViewModel loads dashboard metrics through `GetDashboardMetricsUseCase`.
- If there are zero contacts, it attempts an automatic contact sync once, then reloads metrics.
- Average health score is computed from all contacts.
- Birthday events are filtered from upcoming events and formatted as month/day display values.
- Setup progress is computed from contacts/sync health, AI generation availability, and pending approval workload.
- Relationship Planner adds:
  - a pending-review item when pending count is greater than zero;
  - up to three low-health contacts where health is under 50;
  - up to two upcoming events.
- Readiness state is computed in priority order: sync error, no contacts, pending approvals, otherwise no banner.
- Planner and readiness cards route through typed action targets for Messages, Contact Detail, AI Doctor, and Backup/Restore instead of inferring behavior from nullable contact ids.

Outputs:

- Dashboard cards and navigation actions.
- Setup progress card opens AI Doctor for the full diagnostic checklist.
- Retry action reloads metrics and may trigger contact sync.
- Dismiss action clears `last_sync_error`.
- Planner and readiness actions open the exact target represented by their typed action: Contact Detail, Messages, AI Doctor, or Backup/Restore.

Failure and edge behavior:

- Dashboard loading failures are logged and leave the screen usable.
- Automatic launch sync failures are ignored except for persisted sync error messaging.
- Empty birthday list shows the empty state text.

### 24.4 Contacts: Sync, Import, Deduplication, List, and Detail

Purpose:

- Import relationship data from Google and device contacts, merge it locally, let the user find contacts, and edit personalization controls that drive AI and automation.

Main files:

- `SyncContactsUseCase.kt`
- `ContactSyncServiceImpl.kt`
- `GoogleContactsSync.kt`
- `DeviceContactsReader.kt`
- `ContactListViewModel.kt`
- `ContactDetailViewModel.kt`
- `UpdateContactPreferencesUseCase.kt`

Inputs:

- Google OAuth token or signed-in Firebase user.
- Android contacts permission for device contacts.
- Google People API contacts.
- Device ContactsProvider rows.
- User search query.
- Contact filters: All, Family, Friends, Work, Close Friends, Needs details.
- Contact sorts: Name, Health high, Health low.
- Personalization form fields:
  - nickname;
  - relationship type;
  - preferred language;
  - preferred channel: `SMS`, `WHATSAPP`, `EMAIL`;
  - formality: `CASUAL`, `SEMI_FORMAL`, `FORMAL`;
  - communication style: `WARM`, `FUNNY`, `PROFESSIONAL`, `EMOTIONAL`;
  - automation mode: `DEFAULT`, `FULLY_AUTO`, `SMART_APPROVE`, `VIP_APPROVE`, `ALWAYS_ASK`;
  - custom send time as hour/minute;
  - gift budget and annual budget in INR;
  - skip automatic wishes;
  - interests;
  - sensitive topics;
  - current life phase;
  - notes.

How it works:

- Contact sync clears mock contacts when not in guest mode.
- Google contacts are fetched first; sync errors are stored in preferences.
- Device contacts are fetched separately and can still be imported when Google sync partially fails.
- Contacts are merged by normalized phone, normalized email, then normalized name. Google rows win conflicts; missing fields are filled from device rows.
- Contact groups infer relationship defaults when relationship type is still unknown.
- Guest mode seeds mock contacts if no real contacts are available.
- Event discovery runs immediately after sync so birthdays/anniversaries are available.
- Contact List filters and sorts the current contact stream locally.
- "Needs details" means nickname is blank, notes are blank, interests/shared history are empty, and classification confidence is below 0.6.
- Contact Detail loads the contact and first upcoming event within 365 days, then can generate a wish or save personalization preferences.

Outputs:

- Upserted `ContactEntity` rows.
- Discovered `EventEntity` rows.
- Contact list with search/filter/sort state.
- Contact detail with contact info, health score, engagement score, upcoming event, automation settings, Memory Vault/Gift Advisor/Chat History links, and Generate AI Wish action.
- Saved preference updates on the contact row.

Validation and failure behavior:

- Preferred channel must be SMS, WhatsApp, or Email.
- Automation mode must be one of the supported values.
- Custom send time must set both hour and minute, with hour 0-23 and minute 0-59.
- Budgets cannot be negative.
- Missing contact returns Contact Not Found.
- If Google sync fails and no device contacts exist outside guest mode, sync throws a visible error.

### 24.5 Contact Classification and Relationship Health

Purpose:

- Classify unknown contacts for relationship-aware defaults and compute health scores for analytics, planner, and revival.

Main files:

- `ClassifyContactUseCase.kt`
- `RefreshHealthScoresUseCase.kt`
- `PromptBuilder.kt`
- `AiServiceImpl.kt`

Inputs:

- Contact name, notes, and interaction frequency.
- Gemini access through API key or authenticated Firebase path.
- Existing contact health fields:
  - interaction frequency per month;
  - last interaction timestamp;
  - last wished timestamp;
  - consecutive years wished.

How it works:

- Classification only runs when `relationshipType` is `UNKNOWN`.
- The classifier prompt asks for relationship type, confidence, language, and formality in JSON.
- Parsed classification updates relationship type, subtype, language, formality, style, and confidence.
- Health score starts at 50 and applies:
  - up to +40 from interaction frequency;
  - +20 for contact within 30 days;
  - +10 for contact within 7 days;
  - up to +25 from consecutive wished years;
  - -20 when never wished and last contact is older than 180 days.
- Final health score is clamped to 0-100.

Outputs:

- Contact classification fields.
- Updated health scores.
- Planner/revival/analytics inputs.

Failure and edge behavior:

- Already-classified contacts are skipped.
- Missing contact returns Contact Not Found.
- Gemini failures are handled by the AI client/parser path and should not crash the pipeline.

### 24.6 Events and Event Reminders

Purpose:

- Track birthdays, anniversaries, work anniversaries, and manual custom events; list and filter them; notify before important dates.

Main files:

- `DiscoverEventsUseCase.kt`
- `SaveManualEventUseCase.kt`
- `EventsViewModel.kt`
- `EventReminderScheduler.kt`
- `EventReminderReceiver.kt`

Inputs:

- Contact birthday, anniversary, and work start date fields.
- Manual event form:
  - existing contact id or new contact name;
  - event type;
  - label;
  - month;
  - day;
  - optional year;
  - reminder lead days.
- Event search query.
- Type filter: All, Birthday, Anniversary, Work, Custom.
- Horizon filter: All, next 7 days, next 30 days, next 90 days.
- Birthday reminder toggle.

How it works:

- Event discovery builds stable event ids from contact id plus event type for contact-derived events.
- Next occurrence is computed for the current year or next year when the date has passed.
- Manual event creation can attach to an existing contact or create a manual local contact.
- Manual date validation uses non-lenient `Calendar`.
- Manual reminders clamp `notifyDaysBefore` to 0-30.
- Manual save checks active same-contact, same-type, same-day/month events before persistence and returns a duplicate warning unless the user explicitly chooses Save anyway.
- Events are upserted and their reminder alarms are scheduled.
- Event list combines events and contacts, then filters by query/type/horizon and sorts by next occurrence.
- Event reminder alarms fire through `EventReminderReceiver`, which rechecks the reminder toggle, loads contact/event, and shows an event reminder notification.

Outputs:

- `EventEntity` rows with type, date, next occurrence, source, confidence, verification, and reminder lead time.
- Event list UI.
- Manual event success/error feedback.
- Reminder notifications deep-linking to contact detail.
- Activity log entry when a manual event is saved.

Failure and edge behavior:

- Manual save requires either an existing contact or a new contact name.
- Invalid dates return a visible input error.
- Likely duplicate manual events show a visible warning and require explicit override before a second reminder is saved.
- Missing selected contact returns Contact Not Found.
- Inactive events or disabled reminders cancel reminder alarms.
- If exact alarm permission is unavailable, reminders fall back to inexact while-idle scheduling.

### 24.7 AI Wish Generation

Purpose:

- Generate personalized event wishes using contact, event, style, memory, gift, and previous-message context.

Main files:

- `GenerateMessageUseCase.kt`
- `MessageGenerationWorker.kt`
- `AiServiceImpl.kt`
- `PromptBuilder.kt`
- `ResponseParser.kt`
- `RegeneratePendingMessageUseCase.kt`

Inputs:

- Event id.
- Contact row.
- Event row.
- AI wish generation toggle.
- Global and per-contact automation mode.
- Contact preferred channel and custom send time.
- Quiet hours and blackout dates.
- Style profile.
- Last 10 sent messages for the contact.
- Memory notes for the contact.
- Gift history for the contact.
- Gemini response.

How it works:

- Generation rejects duplicate pending messages for the same contact, event, and scheduled year.
- The prompt context includes:
  - first name and nickname;
  - relationship type;
  - age/event occurrence when known;
  - interests and shared history;
  - days since last contact;
  - preferred language and channel;
  - current life phase;
  - memory notes;
  - gift history;
  - sensitive topics to avoid;
  - writing samples, emoji preference, average length, and common phrases;
  - previous wishes to avoid repetition.
- Gemini is rate-limited before calls.
- Response parsing produces six variants: short, standard, long, formal, funny, emotional, plus a recommended variant.
- If the standard variant is too similar to previous sent messages by word-set overlap above 0.65, generation retries up to two times.
- Approval mode is selected from contact override, global mode, relationship defaults, and skip-auto-wish.
- Scheduled time is computed from event date plus custom/default send time, then moved out of quiet hours and blackout dates.
- A pending message is saved as APPROVED for `FULLY_AUTO`; otherwise it is PENDING.
- Approved messages are scheduled for exact send; pending messages create approval notifications.

Outputs:

- `PendingMessageEntity` with all variants, selected variant, channel, approval mode, status, scheduled time/year, fallback flag, and model metadata.
- Approval notification or exact send alarm.
- Generated pending id returned to Contact Detail or worker logs.

Failure and edge behavior:

- Missing event returns Event Not Found.
- Missing contact returns Contact Not Found.
- Existing pending message returns Already Exists.
- Disabled AI returns AI Disabled.
- Fallback variants can be saved and are surfaced in Wish Preview as template/fallback quality messaging.

### 24.8 Wish Preview, Editing, AI Feedback, Regeneration, and Test Send

Purpose:

- Let users inspect, understand, edit, test, regenerate, approve, or reject a generated wish before delivery.

Main files:

- `WishPreviewViewModel.kt`
- `WishPreviewScreen.kt`
- `MessageFeedbackRepository`
- `TestSendUseCase.kt`

Inputs:

- Message ref, resolved by pending message id first and event id second.
- Variant selection: short, standard, long, formal, funny, emotional.
- Edited draft text.
- Feedback reason:
  - too generic;
  - too formal;
  - wrong language;
  - too long;
  - not warm enough;
  - repetitive.
- Gmail sender email/app password for test-send.
- Approve or reject action.
- Pending review queue, read from pending messages with `PENDING` status.

How it works:

- The screen loads the pending message and sets edited text to the selected variant text.
- Variant selection replaces the edit box content with that variant.
- "Why this draft" signals are built from relationship, language, channel, selected tone/variant, memory note count, gift record count, and previous wish count.
- Feedback records a `MessageFeedbackEntity`, stores the instruction that should be applied next, and writes an AI activity log.
- Regenerate loads contact/event/style/previous messages/memory/gifts and calls the AI regeneration path with the selected feedback instruction if present.
- When regeneration succeeds, pending variants are replaced and any matching latest feedback is marked applied.
- Test-send sends the current edited text to the configured sender email via Gmail SMTP.
- Approve saves the edited text when changed, marks the pending message APPROVED, and schedules exact send.
- Reject marks the pending message REJECTED and cancels scheduled send if needed.
- While loading the draft, the ViewModel computes the next pending review target by scheduled time, excluding the current draft and ignoring non-pending statuses.
- After approval or rejection, the result state can show a Review next action when another pending wish exists; approval still returns automatically when there is no next pending wish.

Outputs:

- Updated visible draft text.
- Feedback saved message and quality message.
- Regenerated pending message variants.
- Test-send success/error event.
- Approved or rejected state.
- Next-review target and remaining pending-review count when available.
- Activity log for AI feedback.

Failure and edge behavior:

- Missing pending message shows message-not-found.
- Missing contact/event context during regeneration shows context-not-found.
- Disabled AI blocks regeneration.
- Blank test message fails validation.
- Missing Gmail setup blocks test-send.
- Gmail send exceptions return test-send failed.
- Review-queue lookup is fail-soft; if it cannot be read, the preview still loads and approval keeps the existing no-queue behavior.

### 24.9 Messages Inbox and Approval Lifecycle

Purpose:

- Manage all generated, scheduled, approved, sent, and failed messages in one operational inbox.

Main files:

- `MessagesViewModel.kt`
- `MessagesScreen.kt`
- `ApprovePendingMessageUseCase.kt`
- `RejectPendingMessageUseCase.kt`
- `RevokeApprovalUseCase.kt`
- `ApprovalReceiver.kt`

Inputs:

- Pending messages stream.
- Sent messages stream.
- Contacts stream.
- Events stream.
- Search query.
- Channel filter: All, SMS, WhatsApp, Email.
- Sort: scheduled oldest, scheduled newest, contact name.
- Row actions: review/edit, approve, reject, revoke, retry.
- Failed recovery action: open AI Doctor for setup checks before retry.
- Bulk selected message ids.
- Notification actions: approve, reject, retry.

How it works:

- Pending rows are grouped into Today, Pending, Approved, and Failed.
- Today contains pending/unknown/dispatching messages due by end of today.
- Pending contains due-within-30-days pending/unknown/dispatching messages after today.
- Approved contains APPROVED messages.
- Failed contains FAILED messages.
- Failed tab includes a recovery assistant summary with failure count, setup-blocker guidance, and an AI Doctor action.
- Rejected, Sent, and Expired pending rows are excluded from active pending lists.
- Sent rows come from `SentMessageEntity`.
- Search matches contact, event type, channel, message text, delivery status, or variant text.
- Approve marks APPROVED and schedules exact send.
- Reject marks REJECTED and cancels send if already approved.
- Revoke changes APPROVED back to PENDING and cancels exact send.
- Retry marks FAILED messages APPROVED, sets schedule to now, and schedules exact send.
- Bulk approve/reject/retry loops over selected ids and writes a summary activity log.
- Notification approval actions cancel the notification, update pending state, and either schedule or enqueue immediate dispatch if due.

Outputs:

- Filtered and sorted tab lists.
- Failed send recovery assistant and AI Doctor route.
- Pending status transitions.
- Exact-send alarms or immediate WorkManager dispatch jobs.
- Activity log entries for message actions.

Failure and edge behavior:

- Missing pending message returns not found in use cases.
- Revoking a non-approved message returns Not Approved.
- Message action failures show stable screen errors.
- Failed retry remains explicit; the recovery assistant does not retry or change messages automatically.
- Notification actions resolve by message id first, then legacy event id.

### 24.10 Scheduling, Automation Modes, and Delivery Channels

Purpose:

- Convert approved messages into actual sends while respecting automation mode, user review windows, quiet hours, blackout dates, disabled channels, permissions, and channel-specific delivery mechanics.

Main files:

- `AutomationSchedulePolicy.kt`
- `DailyScheduler.kt`
- `MessageDispatchWorker.kt`
- `DispatchMessageUseCase.kt`
- `DeliveryChannelResolver.kt`
- `MessageDispatcher.kt`
- `SmsSender.kt`
- `WhatsAppSender.kt`
- `EmailSender.kt`
- `SmsStatusReceiver.kt`

Inputs:

- Pending message id or event id.
- Pending message status and approval mode.
- Scheduled send time.
- Quiet hours start/end.
- Blackout dates.
- Channel blackout list.
- Contact phone/email.
- SMS permission.
- WhatsApp Accessibility Service state.
- Gmail sender credentials.

How it works:

- Send time is event date at custom contact time or default 09:00.
- Candidate times are moved out of blackout dates and quiet hours.
- Exact send scheduling uses AlarmManager `setAlarmClock` when exact alarm access is available.
- If exact alarm access is unavailable, a setup notification is shown and WorkManager dispatch is enqueued.
- Dispatch worker accepts pending-message id first, or legacy event id.
- Message generation prepares upcoming event drafts 7 days ahead and schedules exact dispatch for `FULLY_AUTO` and `SMART_APPROVE`; Smart Approve also shows a review notification before its due-time auto-send.
- Revival/reconnect suggestions use the same contact/global automation modes; Fully Auto and Smart Approve revival messages are scheduled automatically.
- Boot recovery reschedules approved pending messages and pending `SMART_APPROVE` messages that can auto-send at their due time.
- `AiAutoSendQualityGate` scores generated drafts and downgrades fallback or obviously generic `FULLY_AUTO` drafts to `SMART_APPROVE`, preserving due-time automatic dispatch while adding review visibility.
- `AutoSendChannelSelector` chooses the initial pending-message channel from contact availability, channel blackout preferences, Gmail setup, and previous successful delivery history.
- `HolidayWishWorker` runs in the daily automation chain, checks a fixed-date holiday catalog, asks Gemini for relationship-aware holiday wishes, and creates deterministic `HOLIDAY_<holiday>_<contact>_<year>` pending messages to avoid duplicates.
- `PostEventFollowUpWorker` runs in the daily automation chain after message generation, finds recent unreplied AI wishes, asks Gemini for a short low-pressure follow-up, and creates deterministic `FOLLOWUP_<sent_message_id>` pending messages to avoid duplicates.
- Dispatch checks status:
  - APPROVED sends;
  - SENT/DISPATCHING aborts to avoid duplicate sends and shows setup warning;
  - REJECTED skips;
  - PENDING with FULLY_AUTO sends;
  - PENDING with SMART_APPROVE sends after scheduled time;
  - PENDING with VIP_APPROVE expires two hours after scheduled time if not approved;
  - other pending modes wait.
- Before sending, dispatch rechecks quiet hours and blackout dates and reschedules when blocked.
- Dispatch marks the pending row DISPATCHING before calling channel code.
- `DeliveryChannelResolver` builds preferred-first automatic routes from the pending message channel, available contact phone/email, configured Gmail credentials, and disabled-channel preferences.
- Dispatch tries each available delivery route in order and stops at the first successful send.
- SMS inserts a sent row as PENDING_DELIVERY, calls SmsSender, then SMS broadcasts update SENT/DELIVERED/FAILED.
- WhatsApp, SMS, and Email failures move to the next available route when one exists.
- Email routes require sender email/app password and primary contact email.
- Successful dispatch marks pending SENT, inserts sent history if not already inserted, updates contact last wished date, increments consecutive years wished, and adds +5 health score.
- Failed dispatch marks pending FAILED, updates sent row if needed, records HealthMonitor error, and enqueues a dead-letter item.

Outputs:

- Alarm or WorkManager dispatch job.
- Sent SMS, WhatsApp, or email message.
- `SentMessageEntity` history.
- Pending status: SENT, FAILED, EXPIRED, or left waiting.
- Contact health/last-wished updates.
- Setup notifications for missing permissions/configuration.
- Dead-letter entry on full dispatch failure.

Failure and edge behavior:

- Disabled channel blocks that channel and allows fallback to the next enabled route.
- Missing phone/email or Gmail credentials removes that route from automatic dispatch.
- If no automatic route is available, pending dispatch fails with a dead-letter entry.
- SMS permission failure shows a setup notification.
- Unexpected dispatch exception marks pending FAILED so it does not remain stuck DISPATCHING.

### 24.11 Background Automation Workers

Purpose:

- Keep sync, event discovery, generation, reminders, revival, style analysis, and scheduled dispatch running without manual app use.

Main files:

- `WorkerScheduler.kt`
- `DailyTriggerWorker.kt`
- `ContactSyncWorker.kt`
- `EventDiscoveryWorker.kt`
- `MessageGenerationWorker.kt`
- `MessageDispatchWorker.kt`
- `RevivalWorker.kt`
- `StyleAnalysisWorker.kt`
- `BootReceiver`

Inputs:

- WorkManager periodic schedules.
- Network, battery, and storage constraints.
- Contact/event/message/style data.
- Gemini and Gmail configuration.
- Boot completed broadcast.

How it works:

- App startup registers:
  - daily trigger every 24 hours;
  - revival check every 7 days;
  - style analysis every 14 days with network.
- Daily trigger checks for stale backups, schedules the daily chain, and reschedules all event reminders.
- Daily automation chain runs contact sync -> event discovery -> message generation -> holiday wishes -> post-event follow-ups.
- Contact sync worker also classifies unknown contacts when Gemini/auth is available.
- Event discovery worker deactivates generated event types when matching contact date fields disappear.
- Message generation worker scans events due within 7 days and schedules automatic dispatch for Fully Auto and Smart Approve messages.
- Revival worker scans contacts with health below 40 and no recent revival attempt, generates a short reconnect suggestion, resolves approval mode, and schedules Fully Auto or Smart Approve revival messages automatically.
- Holiday wish and post-event follow-up workers create deterministic AI pending messages so repeated daily runs do not duplicate them.
- Style analysis worker analyzes sent messages from the last 30 days.
- Boot receiver reschedules approved exact sends, pending Smart Approve exact sends, active event reminders, and missing periodic workers.

Outputs:

- Refreshed contacts/events/messages/style profiles.
- Backup reminder notifications after 30 days without backup.
- Revival pending messages and notifications.
- Rescheduled alarms after reboot.

Failure and edge behavior:

- Most worker exceptions return retry with exponential backoff.
- Message generation skips when AI wish generation is disabled or no Gemini/auth path exists.
- Existing PENDING/processed messages are skipped; FAILED messages can be regenerated.

### 24.12 Analytics and CSV Export

Purpose:

- Show relationship health and messaging metrics, then export a shareable CSV report.

Main files:

- `AnalyticsViewModel.kt`
- `GetAnalyticsUseCase.kt`
- `AnalyticsReportServiceImpl.kt`
- `AnalyticsScreen.kt`
- `AnalyticsExportShare.kt`

Inputs:

- Count of sent messages.
- Count of pending messages.
- Contact count.
- Relationship type counts.
- Upcoming events in next 30 days.
- Sent messages since start of current year.
- Contact health scores.
- Personalization fields on contacts.
- Export action.

How it works:

- `GetAnalyticsUseCase` combines sent count, pending count, contact count, and relationship breakdown as a live flow.
- ViewModel derives:
  - health buckets: healthy 70+, needs attention 30-69, at risk under 30;
  - monthly sent-message counts for the current year up to current month;
  - delivery reliability as non-failed sent messages divided by sent messages this year;
  - response rate as reply-received sent messages divided by sent messages this year;
  - personalization coverage as contacts with nickname, notes, interests, or shared history divided by all contacts;
  - top neglected contacts from bottom health scores.
- Export builds CSV with summary, health, messages, and relationship-type rows.
- Export records an analytics activity log.

Outputs:

- Analytics screen metrics:
  - total wishes sent;
  - total contacts;
  - pending approvals;
  - upcoming events;
  - monthly wishes;
  - contact distribution;
  - relationship health;
  - delivery reliability;
  - response rate;
  - personalization coverage;
  - top neglected contacts.
- `AnalyticsReport` with filename `relateai-relationship-report-YYYYMMDD-HHmm.csv`, MIME type `text/csv`, and CSV content.
- Share intent through FileProvider.

Failure and edge behavior:

- Percent metrics return 0 when denominator is 0.
- Export failure sets `exportError=true`.
- Monthly chart is hidden when there are no sent messages this year.

### 24.13 Activity History

Purpose:

- Provide an audit/operations timeline for user and automation actions.

Main files:

- `ActivityHistoryViewModel.kt`
- `ActivityHistoryScreen.kt`
- `ActivityLogRepository`

Inputs:

- Last 100 `ActivityLogEntity` rows.
- Type filter: All, Message, Event, Sync, Analytics, Settings, AI.
- Date filter: All, Today, Last 7 days, Last 30 days.
- Status filter: All, Open, Resolved.
- Search query.
- Action route on activity rows.

How it works:

- The ViewModel streams recent entries.
- Filters are applied locally by type, status, date cutoff, and query.
- Query matches title, detail, type, severity, status, and action route.
- Rows sort by newest first.
- Screen can open an activity action route through navigation.

Outputs:

- Filtered activity timeline.
- Empty, loading, and error states.
- Optional navigation from an activity row to its action route.

Failure and edge behavior:

- Repository stream failure shows a localized load error and empty entries.
- Missing action route means the row is informational only.

### 24.14 Style Coach

Purpose:

- Learn the user's writing style locally and feed it into AI message prompts.

Main files:

- `StyleCoachViewModel.kt`
- `StyleAnalysisUseCase.kt`
- `StyleProfileRepository`
- `StyleCoachScreen.kt`

Inputs:

- Manual pasted samples split by the screen before calling ViewModel.
- Recent sent messages from the last 30 days, up to 100.
- Existing style profile and history.

How it works:

- Manual training passes non-empty sample texts to `analyzeAndSave`.
- Auto analysis loads recent sent messages; if none exist, it returns false with an empty-state message.
- Analysis computes:
  - average message length;
  - emoji density and top emojis;
  - Devanagari detection for Hindi language preference;
  - common bigrams/phrases;
  - common greetings and closings;
  - formality from opener patterns;
  - tone descriptors such as casual, friendly, polite, uses_hindi, hinglish_mix, funny, warm, expressive, concise, detailed.
- The current style profile is updated and a JSON snapshot is inserted into history.

Outputs:

- `StyleProfileEntity` with samples, emoji use, average length, phrases, greetings, formality, preferred language, emoji set, tone descriptors, sample count, and update timestamp.
- `StyleProfileHistoryEntity` snapshots.
- Style Coach screen with current profile and history.
- Success, empty, or error status messages.

Failure and edge behavior:

- Empty manual sample list is ignored.
- Analysis exceptions surface as manual or auto analysis failure messages.

### 24.15 Memory Vault

Purpose:

- Store per-contact relationship facts that enrich AI wishes and gift suggestions.

Main files:

- `MemoryVaultViewModel.kt`
- `MemoryVaultScreen.kt`
- `MemoryNoteRepository`

Inputs:

- Contact id route argument.
- Note text.
- Category: `GENERAL`, `PREFERENCE`, `EVENT`, `GIFT`, `MILESTONE`.
- Pin/unpin and delete actions.

How it works:

- Screen loads contact and notes by contact id.
- Notes are sorted with pinned notes first, then newest first.
- Add note trims text, rejects blank input silently, rejects text longer than 500 chars, and normalizes unknown category to GENERAL.
- Pin toggles `isPinned`.
- Delete removes the note.
- Prompt building takes up to six notes, pinned first/newest first, and redacts email/phone-like content before sending to AI.

Outputs:

- `MemoryNoteEntity` rows with id, contact id, text, category, timestamp, and pin state.
- Memory list, empty state, and error states.
- Better AI personalization in wish generation/regeneration.

Failure and edge behavior:

- Load/add/pin/delete failures show specific error messages.
- Notes over 500 characters are blocked.

### 24.16 Gift Advisor

Purpose:

- Track gifts by contact, calculate budget usage, and generate AI gift suggestions that avoid repeats.

Main files:

- `GiftAdvisorViewModel.kt`
- `GiftAdvisorScreen.kt`
- `GiftHistoryRepository`
- `AiServiceImpl.generateGiftSuggestions`
- `PromptBuilder.buildGiftSuggestionsPrompt`

Inputs:

- Contact id route argument.
- Gift form:
  - gift name;
  - category;
  - occasion;
  - cost;
  - liked/disliked/unknown feedback;
  - notes.
- Generate suggestions action.
- Delete gift action.

How it works:

- Screen loads contact and gift history.
- Current-year spending is the sum of gift costs for records whose year equals the current year.
- Remaining budget is contact gift budget minus current-year spending, clamped at zero.
- Gift form validates required fields, cost, text lengths, and note length.
- Saved gifts use the current year.
- AI prompt includes relationship, interests, annual gift budget, and previous gifts.
- Parsed suggestions are filtered to items with estimated cost from 1 through contact gift budget; if all are filtered out, raw suggestions are returned.
- Gift history is also included in wish-generation prompts.

Outputs:

- Gift history list sorted by year descending.
- Budget stats: annual budget, spent, remaining.
- Three AI gift suggestions when available.
- `GiftHistoryEntity` rows.

Validation and failure behavior:

- Name/category/occasion are required.
- Cost must be numeric after comma removal and from 0 to 10,000,000 INR.
- Name/category/occasion must be 80 characters or fewer.
- Notes must be 500 characters or fewer.
- Missing contact blocks AI suggestions.
- AI suggestion failure shows a localized error.

### 24.17 Chat History

Purpose:

- Show messages already sent for a specific contact.

Main files:

- `ChatHistoryScreen.kt`
- `ChatHistoryViewModel.kt`
- `MessageRepository.getSentByContact`

Inputs:

- Contact id route argument.
- Sent message history for the contact.

How it works:

- The screen loads sent messages for the contact.
- Sent rows include message text, channel, sent timestamp, delivery status, variant used, and reply metadata where available.

Outputs:

- Per-contact sent-message timeline.
- Loading, empty, and error states.

Failure and edge behavior:

- Missing or deleted contacts can still have sent rows with deleted-contact handling through `SentMessageEntity.contactId`.

### 24.18 Settings and Secure Configuration

Purpose:

- Let the user control AI, automation, credentials, reminders, biometric lock, channels, sync, backup, and sign-out.

Main files:

- `SettingsViewModel.kt`
- `SettingsScreen.kt`
- `SecurePrefs.kt`
- `PreferencesRepositoryImpl.kt`

Inputs:

- Birthday reminder toggle.
- AI wish generation toggle.
- Gemini API key.
- Gmail sender email and app password.
- Global automation mode.
- Quiet hours start/end.
- Biometric lock toggle.
- Disabled channel toggles for SMS, WhatsApp, Email.
- Sync contacts action.
- Legacy DB notice dismiss action.
- Sign-out action.

How it works:

- Settings loads persisted encrypted preferences at ViewModel init.
- Gemini key is trimmed and stored.
- Gmail settings require both email and app password before saving.
- Quiet hours input is restricted to digits and max two characters, then validated 0-23.
- Channel blackout is stored as a JSON array of disabled channels.
- Sync contacts calls foreground sync with force refresh and reports success/failure.
- Sign-out delegates to AuthManager and clears database key/preferences/database file.

Outputs:

- Updated encrypted preferences.
- Feedback events for saved key, saved email, quiet hours, biometric lock, channel preferences, and sync.
- Sync timestamp text.
- Legacy DB notice state.

Failure and edge behavior:

- Missing email/password blocks save.
- Invalid quiet hours blocks save.
- Sync failures show the exception message or stable fallback text.

### 24.19 Backup and Restore

Purpose:

- Export and import encrypted local data without relying on platform backup.

Main files:

- `BackupRestoreViewModel.kt`
- `BackupServiceImpl.kt`
- `BackupEncryption.kt`
- `BackupRestoreScreen.kt`

Inputs:

- Passphrase.
- Export destination URI, optional.
- Import source URI.

How it works:

- ViewModel calculates password strength:
  - weak for length under 6 or low score;
  - score points for length at least 8, uppercase, digit, and symbol from `!@#$%^&*-_`;
  - 4 points is very strong, 3 strong, 2 fair.
- Export requires a non-blank passphrase and rejects weak passphrases.
- Export reads contacts, events, pending messages, sent messages, style profile, memory notes, and gift history.
- Backup data is JSON serialized, encrypted, and written to `relateai_backup_yyyyMMdd_HHmmss.enc` under app files.
- When an output URI is provided, the encrypted backup is copied to that destination.
- Import reads UTF-8 text with a 25 MB limit.
- Import decrypts with the passphrase, validates backup JSON/version, and restores in one Room transaction.
- Restore upserts contacts/events/style/memory/gifts and inserts pending/sent messages.

Outputs:

- Encrypted `.enc` backup file name and byte size.
- Import success count of restored records.
- Last backup timestamp in encrypted preferences.

Failure and edge behavior:

- Blank passphrase, weak export passphrase, create/write/read failure, invalid file, wrong passphrase, unsupported backup version, and database failure all map to stable user-facing errors.
- AES-GCM authentication failure maps to wrong passphrase.
- Malformed Base64 or JSON maps to invalid backup file.
- Database restore errors roll back the transaction.

### 24.20 AI Doctor / Automation Setup

Purpose:

- Diagnose why AI, sync, personalization, approvals, or delivery automation may fail or sound generic.

Main files:

- `AutomationSetupViewModel.kt`
- `AutomationSetupScreen.kt`
- `HealthMonitor.kt`
- `DeadLetterQueue.kt`
- `StructuredLogger.kt`

Inputs:

- Refresh action.
- Sync contacts action.
- Dry-run generation check.
- Test AI action.
- Test email action.
- Runtime state:
  - Google auth/OAuth token;
  - Gemini key or Firebase user;
  - AI wish toggle;
  - style profile sample count;
  - contacts and personalization fields;
  - Gemini circuit breaker state;
  - notification permission;
  - SMS permission;
  - email credentials and email-preferred contacts;
  - WhatsApp accessibility service enabled state;
  - exact alarm access;
  - daily WorkManager status;
  - recent structured errors and health errors;
  - dead-letter count.

How it works:

- `buildReport()` creates a list of readiness checks with status OK, WARNING, or ACTION_REQUIRED.
- Summary status is based on number of blockers first, then warnings.
- Personalization is OK when at least 50 percent of contacts have nickname, notes, interests, or shared history.
- Style Coach is OK at 3 or more samples, warning with 1-2, action required with zero.
- Email is action required only when email-preferred contacts exist and credentials are missing; otherwise missing email is a warning/optional state.
- Dry run checks whether AI generation can theoretically run and reports first blocker without creating messages.
- Test AI asks Gemini for a fixed JSON shape and diagnoses error responses.
- AI error diagnosis maps quota, auth, network, JSON, circuit breaker, and other redacted recent errors to stable messages.
- Test email sends a fixed test message through the same Gmail test-send path.

Outputs:

- Diagnostic summary.
- Readiness check cards with action labels and destinations.
- Operation message for sync, dry run, AI test, or email test.
- Navigation/action intents to Settings, Style Coach, Contacts, Activity History, Accessibility settings, Battery settings, and App settings.

Failure and edge behavior:

- WorkManager status lookup failures produce an empty work list rather than crashing.
- AI test exceptions show generic AI test failure.
- Sensitive error text is redacted before display.

### 24.21 Notifications, Widget, Shortcuts, and Deep Links

Purpose:

- Surface key work outside the main app screen.

Main files:

- `NotificationHelper.kt`
- `ApprovalReceiver.kt`
- `EventReminderReceiver.kt`
- `BirthdayWidgetProvider.kt`
- `app/src/main/res/xml/shortcuts.xml`
- `app/src/main/AndroidManifest.xml`

Inputs:

- Notification permission.
- Generated pending messages.
- Event reminders.
- Revival suggestions.
- Setup/system alerts.
- Widget update broadcasts.
- Launcher shortcut actions.
- Deep link intents.

How it works:

- App startup creates notification channels:
  - approval required;
  - revival suggestion;
  - event reminders;
  - system alerts;
  - dispatch status.
- Approval notification includes approve, reject, and edit actions.
- Edit opens `relateai://wish/{contactId}/{messageId}`.
- Event reminder opens `relateai://contact/{contactId}`.
- System backup/setup alert opens the relevant app surface.
- Widget reads events, contacts, and pending approvals, then displays today's birthday count, names, next three events, and pending approval count.
- Static shortcuts expose compose/message and contacts entry points.

Outputs:

- Approval, revival, reminder, setup, backup, and dispatch notifications.
- Widget title/subtitle.
- Deep-link navigation into specific app screens.

Failure and edge behavior:

- Notification SecurityException from missing permission is caught.
- Widget update failures are logged and do not crash the app.
- Unknown widget contact names show "Unknown Contact".

### 24.22 Storage, Data Model, Security, and Reliability

Purpose:

- Keep relationship data local, encrypted, recoverable, and observable.

Main files:

- `AppDatabase.kt`
- `DatabaseKeyDerivation.kt`
- `LegacyDatabaseQuarantine.kt`
- `SecurePrefs.kt`
- `BackupEncryption.kt`
- `SensitiveLogRedactor.kt`
- `Retry.kt`
- `CircuitBreaker.kt`
- `Fallback.kt`
- `HealthMonitor.kt`
- `DeadLetterQueue.kt`

Inputs:

- Room entity data.
- Database key derivation inputs.
- Encrypted preference values.
- Backup passphrases.
- Structured log/error events.
- Dispatch failures.

How it works:

- Room stores the active entity model under SQLCipher.
- EncryptedSharedPreferences stores credentials/configuration in separate auth and config files.
- If encrypted prefs initialization fails, files and AndroidKeyStore aliases are cleared and retried; plaintext fallback is forbidden.
- Legacy plaintext DB files are quarantined instead of silently reused.
- Backup encryption uses AES/GCM/NoPadding with a PBKDF2WithHmacSHA256 256-bit key, 65,536 iterations, 16-byte salt, 12-byte IV, and 128-bit tag.
- SensitiveLogRedactor removes secrets and PII-like values from logs and diagnostics.
- HealthMonitor records recent errors and circuit breaker states.
- DeadLetterQueue stores failed dispatch payload metadata for troubleshooting.

Outputs:

- Encrypted local database.
- Encrypted auth/config preferences.
- Quarantine notice for legacy plaintext DB.
- Redacted diagnostics.
- Health and dead-letter inputs for AI Doctor.

Failure and edge behavior:

- Encrypted storage initialization failure throws securely after retry.
- Backup import rejects oversized, malformed, unauthenticated, unsupported, or constraint-breaking payloads.
- Dispatch failures are not silently dropped; they are marked FAILED and visible through diagnostics.

## 25. Product UX Automation Audit and Improvement Backlog

This audit translates the current implementation into product, UX, workflow, and technical improvement work. It prioritizes automation that reduces effort while keeping critical relationship, message, privacy, and delivery decisions visible and reversible.

### 25.1 Current State Analysis

Modules and feature groups:

| Module | Features | Primary users | Main output |
|---|---|---|---|
| Entry and account | Splash, onboarding, auth, guest mode, biometric lock, sign-out | End user, tester | Authenticated or guest app session |
| Dashboard | Home metrics, readiness banner, relationship planner, quick actions | End user | Daily relationship command center |
| Contacts | Google/device sync, dedupe, list filters, contact detail, personalization | End user | Local contact graph with preferences |
| Events | Discovery, manual event creation, event filters, reminders | End user | Upcoming relationship moments |
| AI personalization | Classification, style profile, wish generation, regeneration, gift suggestions, revival | End user | Drafts, suggestions, and context intelligence |
| Messages | Inbox tabs, approve/reject/retry/revoke, preview, test send | End user | Controlled message lifecycle |
| Automation | Daily chain, exact send alarms, boot recovery, dispatch, reminders | App runtime, end user | Reliable low-touch execution |
| Insights | Analytics, CSV export, activity history, health scoring | End user, operator | Performance and audit visibility |
| Data and security | SQLCipher, encrypted prefs, backup/restore, redaction, dead-letter queue | End user, operator | Private durable local data |
| External integrations | Google/Firebase/Gemini, People API, Android Contacts, SMS, WhatsApp, Gmail SMTP | End user, platform | Contact import and message delivery |

Primary user journeys:

| Journey | Current path | Current friction |
|---|---|---|
| First setup | Splash -> Onboarding -> Auth -> Home -> Settings/AI Doctor | Setup work is split between onboarding, Settings, permissions, Style Coach, and AI Doctor. |
| Import contacts | Settings/Home/AI Doctor -> Sync -> Contacts -> Contact Detail | Sync status exists, but next best action after partial sync is not always obvious. |
| Personalize a contact | Contacts -> Contact Detail -> Edit personalization -> Memory/Gift screens | Rich controls create cognitive load; user must know which fields improve AI quality. |
| Generate a wish manually | Contact Detail -> Generate AI Wish -> Wish Preview | Clear, but duplicate/disabled/context errors need stronger recovery routes. |
| Review approvals | Home/Messages/notification -> Wish Preview or inbox action | Previously Home routed approval prompts to AI Doctor; direct Messages routing is the right behavior. |
| Configure automation | Settings -> AI Doctor -> system settings/Style Coach/Contacts | Diagnostic quality is strong, but setup actions are spread across several surfaces. |
| Recover failed delivery | Messages Failed tab -> retry; AI Doctor -> Activity History | Good visibility, but dead-letter details are operational and not task-oriented yet. |
| Backup data | Home/Settings -> Backup/Restore -> passphrase -> export/import | Secure, but reminder and last-backup status are not prominent in dashboard. |
| Understand relationship health | Home/Analytics/Contact Detail | Metrics exist, but explanations and recommended actions are scattered. |

Roles and permissions:

| Role | Capabilities | Limits |
|---|---|---|
| Signed-in user | Google contacts sync, authenticated profile, Firebase/Gemini path where configured | Needs OAuth/Firebase configuration and runtime permissions. |
| Guest/developer user | Local demo flow and mock contacts | No real Google sync; production use should sign in. |
| App runtime | Background sync, event discovery, generation, reminders, dispatch, boot recovery | Must respect user toggles, permissions, quiet hours, blackout dates, approval mode, and disabled channels. |
| Platform services | ContactsProvider, AlarmManager, WorkManager, SMS, Accessibility, notifications, FileProvider | Can be unavailable or permission-gated. |

Manual versus automated processes:

| Process | Manual today | Automated today | Gap |
|---|---|---|---|
| Setup | User signs in, grants permissions, configures Gemini/Gmail, trains Style Coach | AI Doctor detects blockers; Home and AI Doctor share setup progress counts | Needs a guided resume path that opens each remaining fix step. |
| Contact import | User taps sync or foreground refresh | Daily sync chain and auto event discovery | Partial failures need clearer recovery and import summaries. |
| Personalization | User fills rich fields, memories, gifts | Classification and inferred group relationship | Needs smarter prompts for missing details and one-tap enrichment. |
| Event management | User adds manual events and can override duplicate warnings | Contact-derived event discovery, duplicate warning before manual saves, and reminders | Needs richer merge/keep controls for duplicate families beyond the initial warning. |
| Wish writing | User generates/reviews/regenerates | AI generates variants and learns feedback | Needs batch preview and smarter default variant/channel suggestions. |
| Approval | User reviews, approves, rejects, revokes | Notifications and automation modes | Needs more direct task routing and clearer send-risk labels. |
| Delivery | User configures channel credentials/permissions | Exact scheduling, dispatch, fallback, retries | Needs pre-send channel readiness checks in context. |
| Insights | User opens Analytics/Activity | Metrics and CSV generated | Needs action recommendations tied to metrics. |
| Backup | User exports/imports | 30-day reminder | Needs last-backup status and safer recovery guidance. |

Data flow:

```text
Auth/Prefs
  -> Contact sync
  -> ContactEntity
  -> Event discovery/manual events
  -> EventEntity
  -> AI context: contact + event + style + memory + gifts + previous sent
  -> PendingMessageEntity
  -> approval/review/edit/regenerate
  -> scheduler/dispatch
  -> SentMessageEntity + contact health updates + activity logs
  -> dashboard + analytics + activity history + widget
```

Dependencies and integrations:

- Google Sign-In and Firebase Auth enable account identity and Google Contacts scope.
- Google People API and Android ContactsProvider feed contact sync.
- Gemini/Google AI powers classification, wishes, regeneration, gifts, and revival suggestions.
- Room/SQLCipher stores local data.
- WorkManager and AlarmManager drive background jobs, reminders, and exact sends.
- SMS, WhatsApp Accessibility, and Gmail SMTP deliver messages.
- Android notification, exact alarm, contacts, SMS, accessibility, and document picker permissions are operational gates.

### 25.2 UX and Product Audit by Feature

| Feature | Current behavior | UX issue | Accessibility issue | Performance concern | Click/cognitive load | Recommended improvement |
|---|---|---|---|---|---|---|
| Onboarding and setup | Explains value and links to setup checklist; Home and AI Doctor now show shared setup progress | Setup still spans onboarding, Settings, permissions, Style Coach, and AI Doctor | Progress and blocker changes should be announced, not only visually shown | Rechecking setup state can become expensive if every surface recomputes independently | User must infer which setup step to resume | Extend setup progress into a guided resume flow with exact next actions. |
| Auth and guest mode | Google sign-in plus developer bypass | Guest mode can hide missing production setup | Auth error text must remain readable and announceable during provider failures | OAuth retries can feel stalled without progress feedback | User may not know guest limits until a sync/generation action fails | Label guest mode clearly and show upgrade-to-sign-in action on dashboard. |
| Home dashboard | Metrics, readiness, setup progress, quick actions, planner, birthdays | Backup freshness is surfaced in Settings rather than Home | Status cards need non-color status labels and semantic actions | Home can attempt sync on zero contacts; keep bounded to avoid load stalls | Many cards compete for attention on small screens | Consider a Home backup-staleness prompt once stale-backup thresholds are productized. |
| Contacts list | Search, filter, sort, sync error controls | Needs-details filter is useful but not action-oriented | Filter chips and search clear controls need labels and 48 dp targets | Large imports may make local filtering slow without profiling | User must open each contact to learn what is missing | Add personalization score chips and quick complete-details action. |
| Contact detail | Rich personalization, generate wish, memory/gift/chat links | Many fields increase cognitive load; automation values are technical | Dense form controls need grouped headings and clear field labels | Loading contact, events, memories, gifts, and history together can grow costly | User has to understand which fields improve AI quality | Use progressive disclosure: essentials first, advanced automation collapsed, explain quality impact. |
| Event list | Search/filter/horizon and manual event creation with duplicate warning | Existing/new-contact choice can appear before impact is clear | Date inputs and duplicate warnings need clear focus and error announcement | Duplicate checks should stay indexed as event volume grows | Manual event creation has several required decisions | Add smarter contact suggestions and future merge/keep controls for duplicates. |
| Messages inbox | Today/Pending/Approved/Sent/Failed tabs, bulk actions, readiness labels, failed recovery assistant | Many lifecycle statuses still require understanding | Status labels must not rely on color alone; bulk selection needs clear selected-state labels | Large message queues need stable keys and incremental filtering | User can still scan multiple tabs before finding the next task | Improve failure reason depth and direct fix-and-retry paths. |
| Wish preview | Variant tabs, edit, feedback, why signals, approve/reject/test, review-next | High-value screen is dense; feedback only applies after regenerate | Long editable draft and result actions need clear focus order and button labels | Regeneration and queue lookup must stay cancellable/fail-soft | User must decide among edit, test, feedback, regenerate, approve, reject | Group actions by intent and show selected feedback as pending regeneration input. |
| Scheduling/delivery | Honors approvals, quiet hours, blackout dates, disabled channels | Some channel readiness can still be discovered late | Permission and channel blockers need screen-reader-readable reason text | Exact alarms and workers must avoid duplicate scheduling under retries | User may approve before seeing every delivery constraint | Show pre-send readiness on pending rows and Contact Detail before approval. |
| Analytics | Metrics, charts, export | Metrics are not directly actionable | Charts need text alternatives and non-color bucket labels | Large sent histories may require DAO aggregates instead of recomputing flows | User must translate insight into the next relationship task | Attach recommended actions to low health, failed channels, and neglected contacts. |
| Activity history | Filtered log with optional action route | Operational language can be too technical | Log severity and status should be textual, not color-only | Long logs need pagination or indexed filtering over time | User has to interpret logs and decide whether action is required | Convert common entries into user-facing task names and safe resolve/mark-reviewed actions. |
| Style Coach | Manual and auto style analysis | Users may not know how many samples are enough | Sample quality messages need concise labels and not just progress colors | Recent-message analysis can grow expensive with large histories | User may paste text repeatedly without knowing readiness | Show sample count target, quality state, and examples of what AI learned. |
| Memory Vault | Add/pin/delete memory notes with inline blank validation | Categories and memory usefulness may still be unclear | Validation errors should be near the field and announced | Memory lists should keep stable keys as notes grow | User has to invent useful memory content | Add category descriptions and suggested memory prompts. |
| Gift Advisor | Gift history, budget, AI suggestions | Contact-level budget source may not be discoverable | Budget and suggestion cards need readable labels and affordances | AI suggestions should not block local gift history browsing | User may leave to Contact Detail to adjust budget | Add budget edit shortcut and avoid-repeat explanation. |
| Backup/Restore | Secure passphrase export/import with last-backup freshness in Settings | Strong security creates anxiety around passphrase loss | Password visibility, strength, and status messages need clear announcements | Large exports/imports need progress and must stay off the UI thread | User must remember backup freshness and passphrase implications | Add Home stale-backup prompt and restore rehearsal checklist. |
| AI Doctor | Diagnostic checklist grouped by Required, Quality, Reliability, Recovery | Diagnostic terminology can still overwhelm non-technical users | Check status should be textual and grouped for screen-reader navigation | WorkManager/permission checks should stay cancellable and cached where safe | User may still jump between multiple fix screens | Keep grouping, add guided fix sequence, and route each check to one fix target. |
| Widget/shortcuts | Birthday/upcoming/pending summary | Widget is mostly passive | Widget content descriptions and tap targets depend on launcher support | Widget refresh should avoid heavy dashboard queries | User may see pending count but still open app manually to locate review | Add deep-link actions where launcher/widget APIs allow. |
| Security/sign-out | Strong local wipe with destructive-action checklist | External service data and backup implications still require understanding | Destructive confirmation must keep clear focus order and explicit cancel path | Sign-out cleanup can span DB, prefs, WorkManager, notifications, and auth | User must parse several consequences before confirmation | Keep checklist explicit and offer Backup & Restore before final confirmation. |

Cross-cutting accessibility issues and improvements:

- Use explicit content descriptions for actionable icons beyond decorative cases.
- Ensure all error/status cards are announced through semantic live regions where appropriate.
- Avoid color-only status in AI Doctor, messages, analytics health buckets, and password strength.
- Keep tap targets at least 48 dp, especially compact action chips and row icons.
- Maintain resource-backed text and Hindi parity for every new visible string.

Cross-cutting performance concerns:

- Home can trigger sync on zero contacts during dashboard load; keep it bounded and visible to avoid perceived stalls.
- Large contact/message lists need stable keys and incremental filtering; current local filters are acceptable but should be profiled with large imports.
- AI Doctor calls WorkManager `.get()` from IO, which is acceptable, but diagnostics should stay cancellable and avoid UI blocking.
- Analytics currently recomputes derived metrics when source flows emit; large sent histories may need repository-level aggregate queries later.

### 25.3 Automation Opportunities

| Opportunity | Business value | User benefit | Implementation approach | Risks and safeguards |
|---|---|---|---|---|
| Setup progress automation | Improves activation and reduces support issues | User sees exactly what remains before reliable automation | Initial shared `SetupProgressSummary` now powers Home and AI Doctor; next extend it to onboarding and guided fix routing | Do not auto-enable permissions or credentials; every external/system action remains explicit. |
| Direct task routing from dashboard | Faster task completion | One tap from insight to relevant work queue | Typed dashboard action targets now cover Messages, Contact Detail, AI Doctor, and Backup/Restore for readiness/planner cards | Critical actions still open task screens; no message approval, send, sync, or backup runs without user action. |
| Smart personalization prompts | Better AI quality and retention | User adds useful details without opening advanced form | Compute missing fields per contact and suggest one small prompt at a time | User chooses what to save; never infer sensitive notes without confirmation. |
| Batch approval queue | Faster review of many wishes | Review multiple pending drafts in a focused flow | Initial Wish Preview Review next action implemented from the pending queue; future work can add Messages queue context and bulk review mode | No bulk auto-send without explicit user confirmation. |
| Channel readiness precheck | Fewer failed sends | User knows before approval if SMS/email/WhatsApp is not ready | `DeliveryChannelResolver` now powers dispatch routing; next expose the resolved route/readiness in Messages and Wish Preview | Do not block manual approval unless send is impossible; show override/retry paths. |
| Smart default channel | Better deliverability | Preferred channel is suggested based on available phone/email and prior success | Initial automatic fallback is implemented; next add local scoring from sent history and contact fields | User can override per contact; automatic routing must respect disabled channels and available contact destinations. |
| Event duplicate detection | Cleaner reminders | Avoids duplicate birthday/custom events | Initial same-contact, same-type, same-day/month warning is implemented before save | Users can cancel or explicitly Save anyway; future work can add merge/keep options. |
| Backup reminder surfacing | Reduces data loss | User sees stale backup before a problem | Add Home/Settings last-backup card using existing pref | No automatic export; user must choose destination and passphrase. |
| Smart regeneration feedback | Faster better drafts | Feedback immediately suggests likely fix | Persist selected feedback and show "Regenerate with X" primary action | User can edit prompt/draft manually; feedback is stored locally. |
| Recovery assistant for failed sends | Lower support burden | User sees exact fix and retry path | Initial Failed tab assistant links to AI Doctor while row readiness explains blockers; next add deeper Activity History details | Automatic fallback is limited to `DeliveryChannelResolver` routes; user-visible retry remains explicit. |

### 25.3.1 AI-First Automatic Messaging Feature Candidates

All new messaging features should satisfy this product rule: AI creates or improves the message, the scheduler handles send timing, `DeliveryChannelResolver` chooses an enabled available route, and automation modes decide whether the message sends automatically or waits for review.

| Feature candidate | AI behavior | Automatic message behavior | Priority |
|---|---|---|---|
| Festival and holiday wishes | Generate culturally appropriate wishes from contact language, relationship, and user style. | Implemented for fixed-date holidays; pending messages are created automatically and sent by Fully Auto or Smart Approve rules. | P1 |
| Post-event follow-up | Generate a short follow-up after birthdays, anniversaries, or important custom events. | Implemented for recent unreplied AI-sent wishes; follow-ups are scheduled through the same automation modes, quality gate, quiet-hour policy, and dispatch routing. | P1 |
| Relationship revival cadence | Improve reconnect text using memories, interests, last interaction, and health score. | Initial revival auto-scheduling is implemented; next add user-controlled cadence and per-contact limits. | P1 |
| Gift-to-message assistant | Turn selected AI gift suggestions into a warm note or reminder message. | Schedule gift reminder or gift-accompanying message before the event. | P2 |
| Bulk AI wish preparation | Generate messages for upcoming events in a review queue. | Implemented as 7-day AI draft preparation; Smart Approve can auto-send unchanged drafts at due time and Always Ask requires review. | P2 |
| AI quality gate before auto-send | Initial gate scores fallback, blank, too-short, and generic AI drafts. | Implemented for generated wishes and revival messages by downgrading low-quality Fully Auto drafts to Smart Approve review before due-time automatic dispatch. | P0 |
| Smart channel recommendation | Learn which channel works best per contact from delivery history and configured availability. | Implemented for AI-created pending messages via `AutoSendChannelSelector`; dispatcher fallback still handles send-time failures. | P1 |

### 25.4 User Experience Improvement Plan

Information architecture:

- Make Home the command center for the next action: setup blocker, pending approval, failed delivery, stale backup, or upcoming event.
- Keep Messages as the operational work queue for approvals and delivery recovery.
- Keep AI Doctor as diagnostics, not the destination for ordinary review work.
- Keep Contact Detail focused on relationship context; move advanced automation into a collapsed section.

Workflow simplification:

- Pending approval from Home -> Messages or Wish Preview, not AI Doctor.
- Wish Preview approved/rejected result -> Review next when another pending wish exists.
- Contact needing details -> Contact Detail edit section with missing fields pre-highlighted.
- Failed message -> Failed tab -> failure reason -> fix action -> retry.
- Setup -> one progress checklist, each row opens exactly one fix location.

Feedback and status indicators:

- Add "ready to send", "needs permission", "missing phone/email", "blocked by quiet hours", and "waiting for approval" labels in Messages.
- Add last sync and last backup freshness on Home or Settings.
- Add Style Coach quality state: no samples, learning, ready.
- Add contact personalization quality state near Generate AI Wish.

Error prevention and recovery:

- Validate manual event duplicates before save.
- Warn before approving a message with missing channel prerequisites.
- Confirm destructive sign-out with a data-deletion checklist.
- Make backup passphrase expectations persistent and clear before export/import.

Mobile-first improvements:

- Use progressive disclosure for complex settings and personalization forms.
- Prefer short section headers, chips, and inline status over dense paragraphs.
- Keep primary actions sticky where long forms exist, especially Wish Preview and Backup/Restore.

### 25.5 Prioritized Roadmap

Quick Wins, 1-3 days:

| Item | Priority | Impact | Effort | Dependencies | Business value |
|---|---|---|---|---|---|
| Route Home pending approvals directly to Messages | P0 | High | Low | Home ViewModel/UI/Nav tests | Faster approvals and fewer confused setup visits |
| Add setup/progress summary card to Home from AI Doctor status | P0 | High | Medium | AutomationSetup state extraction | Better activation |
| Add last backup freshness in Settings/Home | P1 | Medium | Low | SecurePrefs last backup timestamp | Reduced data-loss risk |
| Add Messages row readiness labels | P1 | High | Medium | Permission/prefs/contact checks | Fewer failed sends |
| Add inline Memory Vault blank-note validation | P2 | Medium | Low | New strings/tests | Clearer data entry |

Short-term, 1-2 weeks:

| Item | Priority | Impact | Effort | Dependencies | Business value |
|---|---|---|---|---|---|
| Guided setup flow with grouped AI Doctor checks | P0 | High | Medium | Existing checks, UI grouping | Higher completion rate |
| Contact personalization score and missing-detail prompts | P0 | High | Medium | Contact quality helper, strings | Better AI output |
| Failed-send recovery assistant in Messages | P1 | High | Medium | Activity/health/dead-letter mapping | Lower support load |
| Duplicate manual event detection | P1 | Medium | Medium | Event repository query | Cleaner reminders |
| Wish Preview "review next" queue flow | P2 | Medium | Medium | Navigation queue context | Initial implementation complete for sequential pending review |

Medium-term, 2-6 weeks:

| Item | Priority | Impact | Effort | Dependencies | Business value |
|---|---|---|---|---|---|
| Smart channel recommendation engine | P1 | High | Medium | Sent history, contact capabilities | Better deliverability |
| Batch approval mode with per-message transparency | P1 | High | High | Messages/Wish Preview state | Power-user efficiency |
| Relationship action recommendations in Analytics | P2 | Medium | Medium | Analytics action model | Converts insights into retention |
| Backup restore rehearsal and export health | P2 | Medium | Medium | Backup metadata | Trust and safety |

Long-term strategic improvements:

| Item | Priority | Impact | Effort | Dependencies | Business value |
|---|---|---|---|---|---|
| Local relationship intelligence engine | P1 | Very high | High | Quality signals, opt-in inference controls | Differentiated personalization |
| End-to-end automation policy center | P1 | Very high | High | Unified rules model | Enterprise-grade control |
| Privacy-preserving explainability layer | P2 | High | High | Prompt/context tracing | User trust and compliance |
| Robust live validation harness for SMS/WhatsApp/alarms | P2 | High | High | Device lab or scripted device state | Release confidence |

### 25.6 Implementation Plan

Technical architecture changes:

- Extract reusable setup/readiness aggregation from `AutomationSetupViewModel` into a domain-facing service or use case so Home, Onboarding, Settings, and AI Doctor share one truth.
- Typed action destinations now cover dashboard planner/readiness items instead of inferring from nullable contact ids; future dashboard cards should reuse this target model.
- Add contact personalization quality helper shared by Contact Detail, Contacts List, AI Doctor, and Analytics.
- Add message readiness helper shared by Messages, Wish Preview, Dispatch, and AI Doctor.

Frontend improvements:

- Home: task-specific routing, setup progress, last backup freshness, and clearer next-best action cards.
- Messages: readiness labels, failure reason cards, fix-and-retry actions, and clearer bulk action confirmation.
- Contact Detail: progressive disclosure for advanced personalization and automation fields.
- Wish Preview: grouped controls for edit, improve, test, and approve, plus review-next.
- AI Doctor: group checks by Required, Quality, Reliability, and Recovery.

Backend/domain/workers improvements:

- Keep automation business rules in use cases and workers, not only screen ViewModels.
- Centralize duplicate-event, readiness, scheduling, and policy checks so background jobs and foreground actions behave consistently.
- Keep worker retry, dead-letter, and failure-recovery state observable through Messages, AI Doctor, and Activity History.
- Keep AI and delivery integrations behind repository/service interfaces so live provider behavior can be swapped or mocked safely.

Data and database changes:

- Short term: no schema change required for routing, setup progress, backup freshness, readiness labels, or duplicate detection.
- Medium term: consider storing per-message delivery failure reason and last readiness check result for better recovery UX.
- Long term: consider storing explicit automation policy entities if rules outgrow encrypted preference JSON.

API and integration enhancements:

- Keep Google/Gemini/Gmail/SMS/WhatsApp actions behind explicit setup and permission flows.
- Add readiness preflight before scheduling or approval to reduce late failures.
- Keep all AI suggestions inspectable and editable before persistence or send.

State management improvements:

- Prefer typed UI state actions over nullable routing hints.
- Keep one source of truth for setup readiness and message readiness.
- Record user-visible operation outcomes as feedback events rather than ad hoc strings where possible.

Performance optimizations:

- Move heavy analytics aggregates toward DAO-level queries if large sent histories become slow.
- Profile contact and message filtering with large datasets.
- Avoid repeated background sync on Home reload after known failures; show explicit retry.

Analytics and tracking recommendations:

- Track local, privacy-safe counters for setup completed, contacts enriched, approvals reviewed, failed sends fixed, backups exported, and AI regenerations accepted.
- Do not track message content, personal notes, phone numbers, emails, or prompt bodies.
- Use Activity History for user-visible audit and separate aggregate counters for product analytics if introduced later.

### 25.7 Improvement Backlog

| ID | Backlog item | Priority | Status |
|---|---|---|---|
| UX-001 | Route Home pending approvals directly to Messages | P0 | Implemented in code after this audit section was added. |
| UX-002 | Add typed dashboard action destinations for all planner/readiness cards | P0 | Implemented typed action targets for Messages, Contact Detail, AI Doctor, and Backup/Restore. |
| UX-003 | Create setup progress summary shared by Home and AI Doctor | P0 | Implemented initial shared summary and Home/AI Doctor UI. |
| UX-004 | Add contact personalization score and missing-detail prompts | P0 | Implemented initial Contact Detail next-step prompts. |
| UX-005 | Add per-message readiness and failure reason labels | P1 | Implemented initial readiness labels for Messages rows. |
| UX-006 | Add last backup freshness to Home or Settings | P1 | Implemented in Settings. |
| UX-007 | Group AI Doctor checks by Required, Quality, Reliability, Recovery | P1 | Implemented. |
| UX-008 | Add duplicate manual event detection | P1 | Implemented initial warning with explicit Save anyway override. |
| UX-009 | Add failed-send recovery assistant | P1 | Implemented initial Failed tab assistant with AI Doctor route. |
| UX-010 | Add Wish Preview review-next queue | P2 | Implemented initial explicit Review next action after approval/rejection. |
| UX-011 | Add Memory Vault inline validation for blank notes | P2 | Implemented. |
| UX-012 | Add sign-out destructive-action checklist | P2 | Implemented Settings confirmation checklist. |

### 25.8 Incremental Implementation Log

| Change | Why it improves UX | User effort reduction | User control preserved | Validation |
|---|---|---|---|---|
| UX-002: Home planner and readiness cards now use a shared typed action target model for Messages, Contact Detail, AI Doctor, and Backup/Restore. | Dashboard insights route to the exact task surface without relying on nullable contact ids or ad hoc fallback logic. | Reduces wrong turns from dashboard prompts and makes future dashboard actions cheaper to add consistently. | Cards only navigate; setup, backup, approval, sync, and send actions still require explicit user action on the destination screen. | Focused Home ViewModel, screen interaction, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-010: Wish Preview now offers an explicit Review next action after approval or rejection when another pending wish exists. | Users can continue a review session from the result state instead of backing out and finding the next pending draft manually. | Reduces repeated navigation through Messages or Contact Detail during sequential approvals. | The app never auto-approves, auto-rejects, or silently advances to the next draft; users choose Review next, and approval still returns automatically only when no next item exists. | Focused Wish Preview ViewModel, screen interaction, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-009: Messages Failed tab now includes a recovery assistant with failed count, setup guidance, retry reminder, and direct AI Doctor route. | Users get a single recovery entry point instead of interpreting failed rows alone. | Reduces diagnostic navigation by pointing failed-send work straight to setup checks. | The assistant does not retry or alter messages; users still review fixes and retry individual or selected rows explicitly. | Focused Messages ViewModel, screen interaction, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-012: Settings now requires a sign-out confirmation dialog with a checklist of local data deletion, cleared preferences/keys, external access behavior, and backup recommendation. | Users see the consequences before a destructive local wipe. | Reduces recovery effort caused by accidental sign-out or misunderstanding what is deleted. | Sign-out only runs after explicit confirmation; cancel keeps all local data untouched. | Focused Settings screen interaction, Settings ViewModel, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-008: Manual event save now detects likely duplicate active events for the same contact, event type, and day/month before persistence. | Users are warned before creating duplicate reminders that can cause repeated notifications or duplicate AI wish generation. | Prevents cleanup work caused by accidental duplicate manual entries. | Users can cancel by editing/dismissing or explicitly choose Save anyway when a separate reminder is intentional. | Focused SaveManualEventUseCase, EventsViewModel, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-003: Home and AI Doctor now share a setup progress summary with completed/total readiness counts, blocker count, warning count, and progress indicator. | Users can understand setup health from Home before opening detailed diagnostics, then inspect the full AI Doctor checklist when needed. | Reduces navigation and scanning effort by surfacing readiness status on the dashboard and summarizing AI Doctor checks before the long list. | The summary is informational and routes to AI Doctor; it does not auto-enable permissions, credentials, channels, or sends. | Focused Home ViewModel, Home screen interaction, Automation Setup ViewModel, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-004: Contact Detail personalization quality card now shows the next missing detail to add, or a ready state when core signals are complete. | Users can see which input will most improve message personalization before generating a wish. | Reduces guesswork around which relationship fields matter for AI quality. | The prompt is advisory; users still choose whether to edit preferences, add memories, or generate immediately. | Focused Contact Detail quality-card, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-004 extension: Memory Vault now offers suggested memory prompts, and Contact Detail quality uses real Memory Vault note counts plus category summaries. | Users can add high-value relationship facts faster, and the personalization score now reflects context that AI generation actually consumes. | Reduces blank-page effort when adding memory notes and makes AI-quality guidance more accurate. | Prompt chips only prefill editable text and category; users still choose what to save, delete, pin, or send. | Focused AI/personalization tests, app debug unit tests, localization parity, hardcoded-string regression, and `git diff --check` passed on 2026-06-25 using Android Studio JBR 21. |
| UX-007: AI Doctor checks are grouped into Required, Quality, Reliability, and Recovery sections. | The diagnostic list is easier to scan and separates setup blockers from quality and recovery concerns. | Reduces cognitive load when deciding what to fix first. | Existing check actions remain explicit; no permission, credential, or send behavior is changed automatically. | Focused Automation Setup ViewModel, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-005: Messages rows show readiness labels for review, approval, sending, failed retry, missing contact, disabled channel, missing phone/email, and Gmail setup gaps. | Users see likely send blockers directly in the work queue instead of discovering them after approval or retry. | Reduces diagnostic navigation and failed retry loops by showing the next problem at row level. | Labels are informational; approve, edit, reject, revoke, and retry actions remain explicit user choices. | Focused Messages ViewModel, screen interaction, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-011: Memory Vault shows inline validation for whitespace-only notes and rejects blank saves defensively. | Users understand why the Add button stays disabled instead of facing a silent no-op. | Avoids repeated taps or confusion when whitespace was entered accidentally. | Users decide what note to save; the app only blocks empty data that would not be useful. | Focused Memory Vault ViewModel, screen interaction, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-006: Settings Backup & Restore row shows last backup freshness from existing secure preferences. | Data-protection status is visible before the user enters the backup flow. | Reduces the need to open Backup & Restore just to check whether a recent backup exists. | The app only surfaces status; export and restore remain explicit user actions. | Focused Settings ViewModel, localization parity, and hardcoded-string regression tests passed on 2026-06-25. |
| UX-001: Home pending-approval readiness and planner items route directly to Messages. | Approval review is a message task, not a setup diagnosis task. | Reduces path from Home alert to review queue to one tap. | User still reviews, edits, approves, rejects, or revokes before critical sends. | Focused Home interaction tests passed on 2026-06-25. |

## 26. SSOT Maintenance Checklist

When changing the project:

- Inspect the source files related to the change.
- Update feature status/evidence in section 7.
- Update architecture, setup, commands, validation, or security sections if affected.
- Record fresh validation commands and outcomes.
- Separate local automated evidence from live device/integration evidence.
- Preserve known live-validation blockers with concrete prerequisites.
- Do not paste secrets, API keys, app passwords, OAuth tokens, or keystore values into this file.
- Keep this file as the canonical source of truth, and keep `PLAN.md`, `PRODUCT_BLUEPRINT.md`, and `IMPLEMENTATION_TASKS.md` aligned when product scope, debt status, or execution order changes.
