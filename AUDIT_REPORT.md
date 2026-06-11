# RelateAI Codebase & Documentation Audit Report

## 0. Current Implementation Update

* Added root `features.md` as the primary feature source of truth, plus Kiro compliance specs and `docs/UI_VALIDATION.md` for manual UI/integration evidence.
* Completed the biometric app-lock enforcement pass: `MainActivity` now blocks app composition on protected cold start/resume when biometric lock is enabled, uses localized prompt/lock text, and has a focused policy unit test.
* Completed the automation scheduling and event-reminder pass: message generation now applies custom send time, quiet-hour and blackout-date policy, skip-auto-wish prevents automatic generation/auto-send, dispatch workers defer newly blocked sends, and event reminders are scheduled/rescheduled from `notifyDaysBefore`.
* Completed the background contact-sync parity pass: `ContactSyncWorker` now routes through foreground `SyncContactsUseCase` behavior for Google + device merge, relationship normalization, mock cleanup, and event discovery before optional AI classification.
* Completed the Gmail event-aware subject pass: SMTP delivery resolves event metadata during dispatch and builds birthday, anniversary, work-anniversary, custom-event, fallback, and explicit test-send subjects.
* Completed the localization/script portability pass: app and core-data English/Hindi resource keys have parity tests, touched notification/system-alert copy is resource-backed, critical Hindi notification labels were refreshed, and the string-audit helper no longer assumes `/workspace`.
* Added Compose instrumented smoke coverage for first-run onboarding-to-auth and guest-mode bottom navigation. Debug builds now use the side-by-side package `com.aistudio.relateai.qxtjrk.debug`, with a matching debug `google-services.json`, so validation no longer requires removing the existing production-signed install. Connected execution on device `1b87b5db` installed and started the debug package, then stalled at 0/2 tests while another app was foregrounded; live UI validation needs an idle, unlocked device.
* Added Home dashboard Compose/Robolectric interaction coverage for F-005: dashboard cards render from a populated state, settings/readiness/quick-action/planner links dispatch their callbacks, and sync-error retry/dismiss controls are clickable.
* Added Contact List Compose/Robolectric interaction coverage for F-007: search/clear search, filter chips, sort chips, sync-error retry/dismiss refresh controls, and contact row navigation dispatch the expected callbacks.
* Completed full non-device validation with unit tests, lint, debug assemble, and JaCoCo report using JDK 21.
* Kept the existing single Gradle root project and active modules (`:app`, `:core:domain`, `:core:data`, `:core:ui`) instead of collapsing them into one module.
* Migrated the inactive WhatsApp setup idea into the active app as an Automation Setup screen reachable from onboarding and settings.
* Removed the inactive `feature/onboarding` source tree and updated steering docs so future work uses the active `app/src/main/java/com/example/ui` UI structure.
* Added real manual event creation through `SaveManualEventUseCase`, including local manual contacts, validation, next occurrence calculation, and Feb 29 handling.
* Persisted birthday reminder and AI wish-generation settings; disabled AI generation now returns a user-visible `AiDisabled` outcome instead of calling Gemini.
* Made chat history reachable from Contact Detail and added visible feedback for message/event action failures.
* Completed the AI and automation reliability pass: dispatch now keys exact alarms and WorkManager requests by pending-message id, approval/retry flows schedule the correct message, yearly recurring duplicates use `scheduledYear`, wish preview resolves pending ids with legacy event-id fallback, API-key-only Gemini generation works in background workers, AI-disabled settings are respected, six AI variants are parsed defensively, revival suggestions reject AI error JSON, exact-alarm fallback is safer, Analytics handles zero states, Automation Setup has a readiness dashboard, Messages supports bulk approve/reject/retry, and Wish Preview supports AI regeneration.

## 1. Summary of Completed Changes

### Documentation Updates
* **Fixed Inconsistencies in SSOT_CONSOLIDATED.md:** The primary documentation claimed the UI layer was merely a stub (`MainActivity immediately finishes`), which conflicted with the actual codebase and other statements within the same document. I updated `SSOT_CONSOLIDATED.md` to accurately reflect that the UI layer (built with Jetpack Compose) is fully implemented, functional, and integrated.

### Code Implementation and Fixes
* **Resolved Outstanding TODOs:**
  * Fixed `data_extraction_rules.xml` by removing the TODO comment and implementing explicit exclusion rules to prevent `relateai.db` (which is encrypted using SQLCipher) and `encrypted_prefs.xml` from being backed up to the cloud. This ensures security requirements are maintained.
  * Addressed a TODO comment in `core/data/src/main/kotlin/com/example/core/auth/AuthManager.kt` regarding `signOut()`. After verifying that the calling UI (`SettingsScreen.kt` and `RelateNavGraph.kt`) already correctly handles navigation and clears the back stack upon sign-out, I updated the comment to reflect that this is complete.
* **Build Verification:**
  * Ran standard project tests (`./gradlew testDebugUnitTest`) and lint checks to ensure all existing functionalities passed without introducing regressions. All tasks completed successfully.

## 2. List of Assumptions Made
* **The Code is the Source of Truth:** Given the disparity between the top section of `SSOT_CONSOLIDATED.md` ("UI layer is a stub") and the fully realized Compose UI in the codebase, I assumed the codebase was correct. I updated the documentation to reflect the codebase, rather than deleting the UI to match the incorrect documentation.
* **No "Missing Features" Beyond Identified TODOs:** The documentation stated that "All features... are 100% completed, hardened, and verified with the test suite." My audit of the code confirmed a comprehensive implementation. I assumed "implement any missing features" applied only to explicit `TODO` markers remaining in the codebase that were relevant to the core logic.
* **Backups Policy:** I assumed that because the database uses `SQLCipher` and contains highly sensitive personal relationship data (API keys, memory vaults), it should explicitly *not* be backed up automatically by Android's cloud backup mechanism, to prevent secure data from leaving the device unintentionally. I adjusted `data_extraction_rules.xml` based on this security best practice.

## 3. Remaining Issues & Recommended Future Improvements
* **Test Coverage:** The current local unit-test baseline is 241 tests with no failures, and Compose smoke coverage now exists for onboarding/auth and the primary app shell. Further work should focus on live device validation for Android system integrations and deeper Compose screen workflows.
* **CI/CD Integration:** Establish GitHub Actions for automated linting, testing, and potential Google Play deployment as outlined in the SSOT's "Pending Work" section.
* **On-Device LLM Migration:** Plan for the eventual migration from Firebase Vertex AI to Gemini Nano on-device once API models are standardized, as per the long-term vision in the documentation.

## 4. Full Feature Audit Pass

### Feature 1: App Shell, Permissions, Navigation, Theme, Global Accessibility

**Issues found**
* The app requested `SEND_SMS` and `POST_NOTIFICATIONS` immediately on activity launch, before onboarding or user context.
* Bottom navigation labels were hardcoded and icon content descriptions duplicated visible labels for screen readers.
* Shared typography used non-zero letter spacing, including negative display letter spacing, which can hurt readability at larger font scales.
* `core:ui` still declared a manifest `package` attribute even though the module namespace is already configured in Gradle.

**Root cause**
* Permission request code lived directly in `MainActivity.onCreate()` instead of behind an in-app rationale.
* Bottom nav metadata stored raw strings instead of string resources.
* Theme tokens had custom letter-spacing values that were not aligned with the app-wide UI guidance.
* The library manifest carried legacy namespace configuration.

**Fix implemented**
* Replaced launch-time runtime permission prompts with a top-level permission rationale dialog that appears only after the user reaches a bottom-nav app shell route and explicitly taps "Grant permissions".
* Moved bottom-nav labels to string resources and set decorative nav icon content descriptions to `null` so the selected navigation item is announced once.
* Normalized shared typography letter spacing to `0.sp`.
* Removed the obsolete `package` attribute from `core/ui/src/main/AndroidManifest.xml`.

**Files modified**
* `app/src/main/java/com/example/MainActivity.kt`
* `app/src/main/java/com/example/ui/navigation/Screen.kt`
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-hi/strings.xml`
* `core/ui/src/main/kotlin/com/example/core/ui/theme/Type.kt`
* `core/ui/src/main/AndroidManifest.xml`

**Validation performed**
* `./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache -Djavax.net.ssl.trustStore=/private/tmp/relateai-cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed.
* Preflight environment was unblocked with Homebrew OpenJDK 21 and a project-local Java truststore for the Zscaler root CA.
* `app/google-services.json` is present locally and `processDebugGoogleServices` completed.

**Remaining improvements**
* Live-device validation is blocked because `adb devices` currently shows no attached devices.
* Feature-specific permission education can be expanded in the onboarding/settings feature passes, but the app shell no longer shows a system permission dialog without user action.

### Feature 2: Contact Sync Sensitive Logging Hardening

**Problem identified**
* Google Contacts sync logs exposed sensitive operational details including signed-in email addresses, full People API request URLs, sync/page tokens in query strings, and raw HTTP response bodies.

**Root cause**
* `GoogleContactsSync` logged debug/error data directly from OAuth, People API request construction, and network responses without a reusable redaction boundary.

**Fix implemented**
* Added `SensitiveLogRedactor` with reusable email, bearer-token, People API URL, and sensitive query-parameter redaction.
* Replaced Google Contacts token and request logs with safe presence/status summaries.
* Converted raw Google HTTP response-body logging and thrown errors into generic HTTP status summaries.
* Added unit tests covering redaction and safe Google Contacts HTTP error summaries.

**Impact**
* Reduces PII and OAuth-adjacent data exposure in Logcat and user-facing sync errors.
* Keeps enough diagnostic signal to distinguish auth, request, permission, and service-availability failures.

**Files modified**
* `core/data/src/main/kotlin/com/example/core/contacts/GoogleContactsSync.kt`
* `core/data/src/main/kotlin/com/example/core/resilience/SensitiveLogRedactor.kt`
* `core/data/src/test/kotlin/com/example/core/resilience/SensitiveLogRedactorTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.

**Commit message**
* `fix: redact contact sync logging`

### Feature 3: Navigation Route Argument Hardening

**Problem identified**
* Contact/event route arguments were encoded in each `Screen.createRoute()` method and decoded with repeated ad hoc `URLDecoder` blocks in `NavGraph`.
* The chat-history destination decoded a `contactId` local that was never used by the screen, increasing the chance of route drift during future edits.

**Root cause**
* Route argument handling was implemented inline instead of behind a single path-segment codec, and Navigation argument decoding behavior was not covered by unit tests.

**Fix implemented**
* Added `RouteArgumentCodec` for consistent route path-segment encode/decode behavior.
* Updated all contact/event route builders to use the codec.
* Replaced duplicated `NavGraph` decode blocks with the codec and removed the unused chat-history local decode.
* Added unit tests for spaces, slashes, plus signs, percent values, and invalid percent input.

**Impact**
* Reduces navigation bugs for Google contact IDs and user/event identifiers containing path-sensitive characters.
* Makes route argument behavior easier to test and maintain.

**Files modified**
* `app/src/main/java/com/example/ui/navigation/RouteArgumentCodec.kt`
* `app/src/main/java/com/example/ui/navigation/Screen.kt`
* `app/src/main/java/com/example/ui/navigation/NavGraph.kt`
* `app/src/test/java/com/example/ui/navigation/RouteArgumentCodecTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.

**Commit message**
* `fix: harden navigation route arguments`

### Feature 4: Sync Error UI Consolidation

**Problem identified**
* Home and Contacts duplicated the same sync-error card UI, including hardcoded English strings and a Google Cloud Console URL with a fixed project id.
* The error card offered dismissal but no consistent retry action across screens.

**Root cause**
* Sync failure feedback was implemented inline per screen instead of as a shared app-level component with localized text and explicit screen-provided actions.

**Fix implemented**
* Added a reusable `SyncErrorCard` with warning icon, safe message display, dismiss action, and retry action.
* Replaced the duplicated Home and Contacts sync-error blocks with the shared component.
* Wired Home retry to `HomeViewModel.loadMetrics()` and Contacts retry to `ContactListViewModel.refresh()`.
* Removed the hardcoded Google Cloud project URL from user-facing UI.
* Added English and Hindi string resources for the shared sync-error UI.

**Impact**
* Provides consistent recovery affordances for contact sync failures.
* Avoids exposing developer-only Google Cloud project details to end users.
* Reduces duplicated Compose UI and future maintenance risk.

**Files modified**
* `app/src/main/java/com/example/ui/components/SyncErrorCard.kt`
* `app/src/main/java/com/example/ui/screens/home/HomeScreen.kt`
* `app/src/main/java/com/example/ui/screens/contacts/ContactListScreen.kt`
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-hi/strings.xml`
* `AUDIT_REPORT.md`

**Validation performed**
* `./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.

**Commit message**
* `ui: consolidate sync error feedback`

### Feature 5: Google Sign-In Configuration Hardening

**Problem identified**
* Google Sign-In could fail or cancel because app resources still defined `default_web_client_id` as `YOUR_DEFAULT_WEB_CLIENT_ID`, including the Hindi resource set.
* The installed debug APK is signed with SHA-1 `88055BAEE201EB76971DC0CA5B3C70A239A9DA18`, while the current Firebase Android OAuth client in `google-services.json` is registered for a different certificate hash.
* Auth failures were surfaced as generic text, making configuration failures hard to diagnose.

**Root cause**
* Source string resources shadowed the Google Services generated web client ID for localized builds.
* The local debug signing certificate has not been registered in Firebase/Google Cloud for this app package.
* `AuthManager` returned only a boolean sign-in result.

**Fix implemented**
* Removed source `default_web_client_id` placeholders so all locales fall back to the generated Google Services client ID.
* Added a web-client-id guard before launching Google Sign-In.
* Added structured sign-in failure results for developer configuration, network, Firebase auth, and unknown failures.
* Added user-visible, localized auth error strings and tests for placeholder detection and actionable configuration errors.
* Kept the developer bypass visible only for debug builds.

**Impact**
* Prevents placeholder OAuth client IDs from reaching Google Sign-In.
* Makes the remaining Firebase SHA-1 mismatch visible to the user instead of failing generically.
* Gives maintainers the exact next configuration step: add the debug SHA-1 in Firebase and refresh `google-services.json`.

**Files modified**
* `core/data/src/main/kotlin/com/example/core/auth/AuthManager.kt`
* `app/src/main/java/com/example/ui/viewmodel/AuthViewModel.kt`
* `app/src/main/java/com/example/ui/screens/auth/AuthScreen.kt`
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-hi/strings.xml`
* `app/src/test/java/com/example/ui/viewmodel/AuthViewModelTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Verified merged debug resources contain the generated web client ID and no `values-hi` placeholder override.
* Installed rebuilt debug APK on device `1b87b5db`; app launched and stayed alive.

**Commit message**
* `fix: harden google sign-in configuration`

### Feature 6: Data-Safety Production Hardening

**Problem identified**
* Room still used `fallbackToDestructiveMigration()`, so a missing migration could silently wipe relationship data.
* Legacy plaintext `relateai.db` files were deleted before SQLCipher open, preventing support or manual recovery.
* Message-dispatch work requests were built in multiple places with inconsistent constraints.

**Root cause**
* Database open logic optimized for recovery from old plaintext data but treated destructive migration/deletion as acceptable fallback behavior.
* Dispatch retries, notification approvals, and alarm dispatch each constructed `MessageDispatchWorker` requests independently.

**Fix implemented**
* Removed destructive Room migration fallback so unsupported schema paths fail closed.
* Added `LegacyDatabaseQuarantine` to move plaintext `relateai.db`, `relateai.db-wal`, and `relateai.db-shm` into `noBackupFilesDir/legacy-unencrypted-db/<timestamp>/` without modifying file bytes.
* Added a one-time `SecurePrefs` notice flag and surfaced a localized Settings notice under Data & Sync with a dismiss action.
* Centralized `MessageDispatchWorker` request creation with low-storage protection and exponential backoff while preserving immediate approval/alarm timing semantics.
* Expanded migration tests for exported schema paths `4 -> 11`, `5 -> 11`, `6 -> 11`, `9 -> 11`, and `10 -> 11`, including representative contacts, events, pending messages, sent messages, memory notes, and gift history data where supported.

**Impact**
* Prevents silent loss of user relationship data during database open and migration failures.
* Preserves old plaintext database files for support recovery while still starting a fresh encrypted database.
* Reduces message-state write risk when device storage is low and makes dispatch scheduling behavior easier to maintain without adding low-battery delays to user-approved sends.

**Files modified**
* `core/data/src/main/kotlin/com/example/core/db/AppDatabase.kt`
* `core/data/src/main/kotlin/com/example/core/db/LegacyDatabaseQuarantine.kt`
* `core/data/src/main/kotlin/com/example/core/prefs/SecurePrefs.kt`
* `app/src/main/java/com/example/ui/screens/settings/SettingsScreen.kt`
* `app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt`
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-hi/strings.xml`
* `core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorkRequests.kt`
* `core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt`
* `core/data/src/main/kotlin/com/example/core/automation/notifications/ApprovalReceiver.kt`
* `core/data/src/main/kotlin/com/example/core/automation/scheduler/DailyScheduler.kt`
* `core/data/src/test/kotlin/com/example/core/db/MigrationTest.kt`
* `core/data/src/test/kotlin/com/example/core/db/LegacyDatabaseQuarantineTest.kt`
* `core/data/src/test/kotlin/com/example/core/automation/workers/MessageDispatchWorkRequestsTest.kt`
* `app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `./gradlew :core:data:testDebugUnitTest --no-configuration-cache` passed.
* `./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed with Homebrew JDK 21 and the existing TLS truststore.
* `./gradlew assembleRelease --no-configuration-cache` passed with Homebrew JDK 21 and the existing TLS truststore.
* Launched `com.aistudio.relateai.qxtjrk/com.example.MainActivity` on device `1b87b5db`; filtered logcat showed startup and SQLCipher load, with no app-owned `FATAL EXCEPTION`, `AndroidRuntime`, Room, or SQLCipher open failure.
* Debug validation now uses `com.aistudio.relateai.qxtjrk.debug` for side-by-side installation instead of forcing a reinstall over the existing production package, preserving app data. The latest connected test run installed and started the debug package, then stalled at 0/2 tests because another app was foregrounded on the device.

**Commit message**
* `fix: harden database data safety`

### Feature 7: Backup, Build Boundary, and Runtime Cleanup

**Problem identified**
* Backup/restore was driven by a static data-layer object, accepted malformed encrypted payloads late, surfaced raw failures, and restored records without an explicit transaction boundary.
* `core:ui` declared unused dependencies on domain/data modules, module `compileSdk` values drifted, and several dependencies still used inline versions.
* Navigation directly created `SecurePrefs` for onboarding completion, and high-risk runtime paths still had avoidable `!!`, unredacted structured log storage, fire-and-forget parser persistence, and async receivers without `goAsync()`.

**Fix implemented**
* Added injectable `BackupService` / `BackupServiceImpl` with stable export/import result types, transactional restore, malformed/too-short payload validation, and stable failure reasons.
* Reworked Backup UI/ViewModel to use the service contract, localized user-facing messages, and deterministic export/import state.
* Added backup encryption/service/ViewModel tests, including wrong passphrase, malformed Base64, too-short payload, unsupported backup versions, and transaction rollback on restore failure.
* Removed unused `core:ui` domain/data dependencies, aligned module `compileSdk` to 37, consolidated dependency versions into `libs.versions.toml`, and removed duplicate app compile-options configuration.
* Moved onboarding completion persistence into an injected `OnboardingViewModel` and converted Onboarding, Settings, and Backup visible strings to resources; Settings now shows `BuildConfig.VERSION_NAME`.
* Extended sensitive log redaction for API keys, password/passphrase/token assignments, phone-like values, and redacted `StructuredLogger` history/output.
* Removed avoidable null assertions in WhatsApp automation, Gemini client, and contact sync; made approval/reminder receivers use `goAsync()` with `finish()` in `finally`.
* Removed `ResponseParser` DAO/context side effects; message generation now persists fallback state via the inserted entity and treats fallback alerts as non-fatal.
* Added a scoped regression test preventing new visible string literals in the cleaned Backup, Settings, and Onboarding screens.

**Impact**
* Prevents partial backup restores and gives users stable, localized failure feedback.
* Tightens module boundaries and reduces Gradle version drift.
* Reduces PII/secret leakage risk in structured logs and makes background receiver work more lifecycle-safe.
* Keeps the cleanup reviewable while preventing regressions in the screens cleaned during this pass.

**Files modified**
* `core/domain/src/main/kotlin/com/example/domain/service/BackupService.kt`
* `core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt`
* `core/data/src/main/kotlin/com/example/core/backup/BackupEncryption.kt`
* `core/data/src/main/kotlin/com/example/core/resilience/SensitiveLogRedactor.kt`
* `core/data/src/main/kotlin/com/example/core/resilience/StructuredLogger.kt`
* `app/src/main/java/com/example/ui/viewmodel/BackupRestoreViewModel.kt`
* `app/src/main/java/com/example/ui/screens/backup/BackupRestoreScreen.kt`
* `app/src/main/java/com/example/ui/viewmodel/OnboardingViewModel.kt`
* `app/src/main/java/com/example/ui/screens/onboarding/OnboardingScreen.kt`
* `app/src/main/java/com/example/ui/screens/settings/SettingsScreen.kt`
* `gradle/libs.versions.toml`
* `app/build.gradle.kts`
* `core/data/build.gradle.kts`
* `core/domain/build.gradle.kts`
* `core/ui/build.gradle.kts`

**Validation performed**
* `./gradlew :core:data:testDebugUnitTest --tests com.example.core.backup.BackupEncryptionTest --tests com.example.core.backup.BackupServiceImplTest :app:testDebugUnitTest --tests com.example.ui.viewmodel.BackupRestoreViewModelTest --no-configuration-cache` passed after fixing the app test runner.
* `./gradlew testDebugUnitTest lintDebug --no-configuration-cache -Djavax.net.ssl.trustStore=/private/tmp/relateai-cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed.
* `./gradlew assembleDebug assembleRelease --no-configuration-cache -Djavax.net.ssl.trustStore=/private/tmp/relateai-cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed.

**Remaining non-goals**
* Existing hardcoded visible strings in screens outside Backup, Settings, and Onboarding remain as pre-existing i18n debt; the new regression test covers the cleaned screens only.
* Direct `Log.*` calls inside low-level logging/resilience primitives and domain code remain where replacing them would require a larger logging abstraction migration.

**Commit message**
* `refactor: harden backup and cleanup app boundaries`

### Feature 8: Platform Backup, Release Signing, and Secret-Safe Runtime Reporting

**Problem identified**
* Android Auto Backup was still enabled even though the app stores relationship data, OAuth-adjacent state, API keys, and a SQLCipher database locally.
* Legacy backup rule files did not exclude the actual encrypted preference files used by `SecurePrefs` and `DatabaseKeyDerivation`.
* Release builds could fall back to debug signing, which can produce artifacts that look release-like but are not production-signable.
* Exception messages could still flow into retry logs, health snapshots, AI error JSON, or structured log history without a final redaction boundary.

**Fix implemented**
* Disabled platform Auto Backup in `AndroidManifest.xml`; the app's explicit encrypted export/import remains the supported backup path.
* Made both API 31+ data extraction rules and legacy backup rules explicitly exclude `relateai.db`, WAL/SHM files, auth/config secure prefs, and DB-key metadata prefs.
* Removed debug signing fallback from release signing and added an early Gradle task-graph guard requiring `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` for release artifact tasks.
* Sanitized `HealthMonitor`, `Retry`, `GeminiClient`, and `StructuredLogger` so sensitive exception text is redacted before storage, logging, or user-visible AI fallback JSON.
* Added regression tests for backup/signing configuration and secret redaction through health/structured logging.

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleRelease --no-configuration-cache` now fails fast when release signing env vars are absent, with the explicit production signing error.

**Commit message**
* `fix: harden production privacy and release boundaries`

### Feature 9: Style Coach Release Readiness

**Problem identified**
* Style Coach still used hardcoded visible English strings and a lifecycle-unaware Compose state collector.
* The screen only supported pasted manual samples even though the domain layer already had recent sent-message analysis.
* Style Coach lacked ViewModel regression coverage and was not protected by the hardcoded-string guard.

**Root cause**
* Style Coach had not been included in the earlier localized-screen cleanup tranche.
* `StyleAnalysisUseCase.invoke()` returned no signal for whether recent messages were actually analyzed, making a truthful user-facing auto-analysis result hard to show.

**Fix implemented**
* Localized all Style Coach visible copy and content descriptions in English and Hindi resources.
* Switched Style Coach to `collectAsStateWithLifecycle()`.
* Added a user-facing recent sent-message analysis action backed by `StyleAnalysisUseCase.invoke()`.
* Made `StyleAnalysisUseCase.invoke()` return `true` when analysis was performed and `false` when no recent messages were available.
* Added `StyleCoachViewModelTest` coverage for manual training success, auto-analysis success, empty recent-message state, and stable error messaging.
* Added `StyleCoachScreen.kt` to `NoHardcodedStringsRegressionTest`.

**Impact**
* Improves localization, accessibility consistency, and lifecycle behavior for Style Coach.
* Gives users a lower-effort way to train their writing profile from existing sent-message history.
* Prevents raw exception text from being surfaced through Style Coach training failures.

**Files modified**
* `app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachScreen.kt`
* `app/src/main/java/com/example/ui/viewmodel/StyleCoachViewModel.kt`
* `core/domain/src/main/kotlin/com/example/domain/usecase/StyleAnalysisUseCase.kt`
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-hi/strings.xml`
* `app/src/test/java/com/example/ui/viewmodel/StyleCoachViewModelTest.kt`
* `app/src/test/java/com/example/domain/usecase/StyleAnalysisUseCaseTest.kt`
* `app/src/test/java/com/example/ui/NoHardcodedStringsRegressionTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.StyleCoachViewModelTest --tests com.example.domain.usecase.StyleAnalysisUseCaseTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `ui: harden style coach release readiness`

### Feature 10: Automation Setup / AI Doctor Release Readiness

**Problem identified**
* Automation Setup and AI Doctor still used hardcoded visible English strings and lifecycle-unaware Compose state collection.
* The diagnostic ViewModel generated raw English copy, surfaced raw exception text in sync/test failures, and included an unredacted fallback for unexpected AI errors.
* Firebase Auth access in diagnostics could fail in unit-test or degraded runtime contexts instead of treating auth as unavailable.

**Root cause**
* The AI Doctor screen was added after the earlier localized-screen cleanup and kept display strings in both Compose and ViewModel logic.
* Diagnostic status generation mixed product copy, environment probing, and error classification in one path without a redaction boundary.

**Fix implemented**
* Localized Automation Setup screen copy, diagnostic rows, actions, setup cards, summaries, operation messages, and AI failure explanations in English and Hindi resources.
* Switched Automation Setup to `collectAsStateWithLifecycle()`.
* Replaced raw sync/test exception messages with stable localized user-facing messages.
* Redacted unexpected AI diagnostic fallback text with `SensitiveLogRedactor` before display.
* Wrapped Firebase Auth reads so diagnostics degrade gracefully when Firebase is unavailable.
* Added `AutomationSetupViewModelTest` coverage for required/healthy summaries and redacted diagnostic fallback text.
* Added `AutomationSetupScreen.kt` to `NoHardcodedStringsRegressionTest`.

**Impact**
* Improves localization, lifecycle safety, and diagnostic consistency for the app's automation setup workflow.
* Reduces risk of exposing email addresses, bearer tokens, phone numbers, or other sensitive text through unexpected AI error messages.
* Makes the readiness screen more robust in partially configured environments.

**Files modified**
* `app/src/main/java/com/example/ui/screens/setup/AutomationSetupScreen.kt`
* `app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt`
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-hi/strings.xml`
* `app/src/test/java/com/example/ui/viewmodel/AutomationSetupViewModelTest.kt`
* `app/src/test/java/com/example/ui/NoHardcodedStringsRegressionTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.AutomationSetupViewModelTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `ui: harden automation diagnostics`

### Feature 11: Memory Vault Workflow Release Readiness

**Problem identified**
* Memory Vault still used hardcoded visible English strings, hardcoded content descriptions, and lifecycle-unaware Compose state collection.
* The ViewModel surfaced raw exception messages and relied mostly on UI button state for note validation.
* Category chips displayed internal enum-like values directly and the screen was not protected by the hardcoded-string regression guard.

**Root cause**
* Memory Vault had not been included in the earlier localized-screen cleanup tranche.
* Validation and display concerns were split across the screen and ViewModel without stable user-facing error codes.

**Fix implemented**
* Localized all Memory Vault visible copy, category labels, content descriptions, empty states, and error states in English and Hindi resources.
* Switched Memory Vault to `collectAsStateWithLifecycle()`.
* Replaced raw ViewModel error strings with stable resource-backed messages.
* Trimmed note text, enforced a 500-character maximum in the ViewModel, and normalized unknown categories to `GENERAL`.
* Added a localized note length counter and accessible pin/delete labels.
* Added ViewModel regression tests for trimming/default category behavior and maximum-length rejection.
* Added `MemoryVaultScreen.kt` to `NoHardcodedStringsRegressionTest`.

**Impact**
* Improves localization, accessibility, and lifecycle safety for the Memory Vault workflow.
* Prevents raw exception text and invalid category values from leaking into user-facing state or persisted notes.
* Moves note validation to the ViewModel boundary so programmatic calls are protected, not only Compose interactions.

**Files modified**
* `app/src/main/java/com/example/ui/screens/memoryvault/MemoryVaultScreen.kt`
* `app/src/main/java/com/example/ui/viewmodel/MemoryVaultViewModel.kt`
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-hi/strings.xml`
* `app/src/test/java/com/example/ui/viewmodel/MemoryVaultViewModelTest.kt`
* `app/src/test/java/com/example/ui/NoHardcodedStringsRegressionTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.MemoryVaultViewModelTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `ui: harden memory vault workflow`

### Feature 12: Gift Advisor Workflow Release Readiness

**Problem identified**
* Gift Advisor still used hardcoded visible English strings, hardcoded content descriptions, and lifecycle-unaware Compose state collection.
* The screen silently ignored ViewModel errors and closed the add-record dialog even when cost parsing fell back to `0`.
* The ViewModel accepted untrimmed text, invalid categories/occasions, invalid costs, and raw exception messages.
* Gift Advisor was not covered by the hardcoded-string regression guard.

**Root cause**
* Gift Advisor had not been included in the earlier localization and lifecycle cleanup tranche.
* Numeric parsing and validation were handled ad hoc in Compose instead of at the ViewModel boundary.

**Fix implemented**
* Localized Gift Advisor screen copy, dialog labels, feedback labels, content descriptions, empty states, loading state, and error messages in English and Hindi resources.
* Switched Gift Advisor to `collectAsStateWithLifecycle()`.
* Added stable resource-backed ViewModel error messages for load, add, delete, AI suggestions, missing contact, required fields, invalid cost, and length validation.
* Hardened gift record creation by trimming input, parsing formatted numeric costs safely, rejecting invalid costs, limiting core fields to 80 characters, and limiting notes to 500 characters.
* Kept invalid dialog submissions open and visible with localized validation feedback instead of saving `0` or dismissing the form.
* Added a visible error card, localized loading text, AI empty hint, note counter, accessible feedback/delete labels, and category display in gift history rows.
* Added `GiftAdvisorViewModelTest` coverage for budget stats, AI suggestions, trimmed save behavior, invalid cost rejection, overlong note rejection, and missing-contact AI errors.
* Added `GiftAdvisorScreen.kt` to `NoHardcodedStringsRegressionTest`.

**Impact**
* Improves localization, accessibility, lifecycle safety, and user feedback for the gift workflow.
* Prevents malformed gift history records from being persisted through UI or programmatic ViewModel calls.
* Reduces privacy and support risk by avoiding raw exception text in user-facing state.

**Files modified**
* `app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreen.kt`
* `app/src/main/java/com/example/ui/viewmodel/GiftAdvisorViewModel.kt`
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-hi/strings.xml`
* `app/src/test/java/com/example/ui/viewmodel/GiftAdvisorViewModelTest.kt`
* `app/src/test/java/com/example/ui/NoHardcodedStringsRegressionTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.GiftAdvisorViewModelTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `ui: harden gift advisor workflow`

### Feature 13: Relationship Detail Flow Release Readiness

**Problem identified**
* Contact Detail, Wish Preview, and Chat History still used hardcoded visible English strings and lifecycle-unaware Compose state collection.
* Contact Detail and Wish Preview ViewModels surfaced raw English error/status strings, including AI-disabled and message-not-found states.
* Wish Preview created a `SnackbarHostState` for test-send feedback but did not attach a `SnackbarHost`, so the feedback could not render.
* Chat History swallowed repository failures and showed the empty state instead of a real error state.

**Root cause**
* These relationship-detail flows had not been included in the earlier localized-screen cleanup tranche.
* UI copy, ViewModel state, and feedback status text were mixed as raw strings instead of stable message codes/resources.

**Fix implemented**
* Localized Contact Detail, Wish Preview, and Chat History visible copy, actions, content descriptions, empty states, loading/error states, tone labels, and feedback labels in English and Hindi resources.
* Switched Contact Detail, Wish Preview, and Chat History to `collectAsStateWithLifecycle()`.
* Replaced Contact Detail generation errors with stable resource IDs, including a visible no-upcoming-event error.
* Replaced Wish Preview user-facing error, quality, feedback, and fallback status strings with resource-backed IDs and resource-backed formatting.
* Attached a `SnackbarHost` to Wish Preview so test-send confirmation can render.
* Added a Chat History load error state and localized sent-message timestamp formatting.
* Added `ChatHistoryViewModelTest` coverage for loaded history and repository failure.
* Updated Contact Detail and Wish Preview ViewModel tests for resource-backed state.
* Added `ContactDetailScreen.kt`, `WishPreviewScreen.kt`, and `ChatHistoryScreen.kt` to `NoHardcodedStringsRegressionTest`.

**Impact**
* Improves localization, accessibility, lifecycle safety, and error clarity across relationship detail workflows.
* Prevents raw exception/status copy from leaking through ViewModel state.
* Restores a previously invisible snackbar feedback path in Wish Preview.
* Distinguishes a real Chat History load failure from an empty conversation.

**Files modified**
* `app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt`
* `app/src/main/java/com/example/ui/viewmodel/ContactDetailViewModel.kt`
* `app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt`
* `app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt`
* `app/src/main/java/com/example/ui/screens/chat/ChatHistoryScreen.kt`
* `app/src/main/java/com/example/ui/screens/chat/ChatHistoryViewModel.kt`
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-hi/strings.xml`
* `app/src/test/java/com/example/ui/viewmodel/ContactDetailViewModelTest.kt`
* `app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt`
* `app/src/test/java/com/example/ui/screens/chat/ChatHistoryViewModelTest.kt`
* `app/src/test/java/com/example/ui/NoHardcodedStringsRegressionTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.viewmodel.ContactDetailViewModelTest --tests com.example.ui.viewmodel.WishPreviewViewModelTest --tests com.example.ui.screens.chat.ChatHistoryViewModelTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `ui: harden relationship detail flows`

### Feature 14: Navigation and Feature Discoverability

**Problem identified**
* Analytics, Activity History, Style Coach, Backup/Restore, and Automation Setup existed as implemented routes, but Home only exposed Settings and contact drill-in.
* Several operational workflows were discoverable only after knowing to open Settings or a secondary screen.
* Home still used lifecycle-unaware Compose state collection.

**Root cause**
* The navigation graph had accumulated feature routes without a dashboard-level entry point strategy.
* Home focused on metrics and birthdays, leaving implemented operational workflows hidden behind indirect navigation.

**Fix implemented**
* Added Home quick actions for the already-implemented Analytics, Activity History, AI Style Coach, AI Doctor, and Backup/Restore workflows.
* Wired Home callbacks through `NavGraph` to existing routes without adding new product scope or destinations.
* Switched Home to `collectAsStateWithLifecycle()`.
* Reused existing localized string resources and Material icons for the quick actions.

**Impact**
* Makes high-value implemented workflows discoverable from the main dashboard.
* Reduces navigation depth for diagnostics, history review, analytics, backup/restore, and writing-style training.
* Preserves existing Settings and bottom-navigation entry points while adding a dashboard path.

**Files modified**
* `app/src/main/java/com/example/ui/screens/home/HomeScreen.kt`
* `app/src/main/java/com/example/ui/navigation/NavGraph.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.NoHardcodedStringsRegressionTest --tests com.example.ui.navigation.RouteArgumentCodecTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `ui: improve feature discoverability`

### Feature 15: Fallback Logging Redaction

**Problem identified**
* `FallbackOrchestrator` logged raw provider exception messages through `android.util.Log`.
* Provider exception text can include email addresses, API keys, tokens, passphrases, phone numbers, or sensitive Google Contacts query parameters.
* The fallback name was also embedded in the log tag without redaction.

**Root cause**
* Earlier redaction work covered `StructuredLogger`, retry logging, health snapshots, and Gemini fallbacks, but this lower-level fallback primitive still wrote directly to Logcat.

**Fix implemented**
* Redacted the fallback log tag name with `SensitiveLogRedactor`.
* Redacted provider failure messages before writing fallback warning logs.
* Added a Robolectric regression test that captures fallback logs and verifies email, API key, and token values are removed.

**Impact**
* Reduces the chance of secrets or PII appearing in Logcat during fallback failures.
* Extends the existing redaction boundary to a shared resilience primitive used by future providers.

**Files modified**
* `core/data/src/main/kotlin/com/example/core/resilience/Fallback.kt`
* `core/data/src/test/kotlin/com/example/core/resilience/SensitiveLogRedactorTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest --tests com.example.core.resilience.SensitiveLogRedactorTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `fix: redact fallback provider logs`

### Feature 16: Dispatch Worker Failure Recovery

**Problem identified**
* `MessageDispatchWorker` marked a pending message as `DISPATCHING` before invoking `MessageDispatcher`.
* If the dispatcher threw an unexpected runtime/setup/DAO exception, the worker could exit without changing the message out of `DISPATCHING`.
* The worker's double-send guard treats `DISPATCHING` as a terminal protected state, so the message could become stuck and invisible to normal retry paths.

**Root cause**
* Expected channel failures were handled inside `MessageDispatcher`, but the worker had no outer safety boundary for unexpected dispatcher exceptions after setting the idempotency status.

**Fix implemented**
* Wrapped dispatch invocation in an outer `try/catch` after setting `DISPATCHING`.
* On unexpected dispatch failure, logs the failure, marks the pending message `FAILED`, and returns `Result.failure()` to avoid automatic duplicate-send retries.
* Added a regression test proving dispatcher exceptions move the message from `DISPATCHING` to `FAILED`.

**Impact**
* Prevents pending messages from being stranded in `DISPATCHING` after unexpected dispatch crashes.
* Keeps duplicate-send protection intact while leaving failed messages visible for manual/user-controlled recovery.

**Files modified**
* `core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt`
* `app/src/test/java/com/example/core/automation/workers/MessageDispatchWorkerTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.core.automation.workers.MessageDispatchWorkerTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `fix: recover failed dispatch worker status`

### Feature 17: Database Key Cache Validation

**Problem identified**
* `DatabaseKeyDerivation` trusted the cached DB-key hex string from encrypted preferences.
* A corrupted odd-length, wrong-length, or non-hex cache value could throw during decode or produce invalid key bytes, causing database startup failure instead of safe recovery.

**Root cause**
* Cached key decoding assumed the encrypted preference value was always well-formed and only checked byte-array size after decoding.

**Fix implemented**
* Added strict cached-key validation for expected SHA-256 key length, even-length hex, and valid hex characters before decoding.
* Removed invalid cached key values before recomputing and re-caching the deterministic DB key.
* Added unit tests for valid cached-key decoding and malformed cached-key rejection.

**Impact**
* Improves startup resilience when encrypted preference contents are corrupted or partially written.
* Avoids unnecessary database-open failure caused by malformed cache metadata while preserving the existing deterministic key derivation path.

**Files modified**
* `core/data/src/main/kotlin/com/example/core/db/DatabaseKeyDerivation.kt`
* `core/data/src/test/kotlin/com/example/core/db/DatabaseKeyDerivationTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest --tests com.example.core.db.DatabaseKeyDerivationTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `fix: validate cached database key metadata`

### Feature 18: Backup Import Size Guard

**Problem identified**
* Backup import read the entire selected encrypted file into memory with `readText()`.
* A very large or malformed selected file could create avoidable memory pressure before validation and decryption.

**Root cause**
* Import treated the selected URI as a trusted small encrypted backup and had no bounded read guard.

**Fix implemented**
* Added a bounded UTF-8 read helper with a 25 MB encrypted import limit.
* Oversized selected files now return `INVALID_BACKUP_FILE` before decryption or JSON parsing.
* Added focused tests for reads within the limit and rejection of over-limit content.

**Impact**
* Reduces out-of-memory and memory-pressure risk during backup import.
* Fails oversized or malformed backup inputs predictably through existing user-facing invalid-backup handling.

**Files modified**
* `core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt`
* `core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest --tests com.example.core.backup.BackupServiceImplTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `fix: bound backup import memory use`

### Feature 19: Measured Coverage Reporting

**Problem identified**
* CI uploaded unit-test reports, but the project still lacked measured coverage evidence.
* The release checklist depended on test count and broad coverage claims rather than a reproducible coverage report.

**Root cause**
* No aggregate JaCoCo report task existed for the Android debug unit-test suite.
* CI had no coverage-generation or artifact-upload step.

**Fix implemented**
* Added a root `jacocoDebugUnitTestReport` task that aggregates debug unit coverage across `:app`, `:core:data`, `:core:domain`, and `:core:ui`.
* Scoped JaCoCo agent attachment to coverage-report invocations so normal validation does not depend on the coverage agent.
* Filtered generated Android/Hilt/Room/Moshi classes from the report and mapped AGP 9 debug class directories explicitly.
* Updated Android CI to generate and upload `coverage-reports`.
* Extended `ProductionReadinessConfigTest` to protect the CI coverage artifact and root coverage task configuration.

**Impact**
* Produces reproducible XML and HTML coverage evidence at `build/reports/jacoco/jacocoDebugUnitTestReport/`.
* Establishes measurable coverage evidence for release decisions; the follow-up resilience coverage in Feature 20 brings the current aggregate above the documented minimum threshold.

**Files modified**
* `build.gradle.kts`
* `.github/workflows/android.yml`
* `app/src/test/java/com/example/ProductionReadinessConfigTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew jacocoDebugUnitTestReport --no-configuration-cache -Djavax.net.ssl.trustStore=/Users/yashsomani/Desktop/Android\ Project/AI-Birthday/.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `test: add measured coverage reporting`

### Feature 20: Resilience Primitive Coverage

**Problem identified**
* The first measured aggregate coverage report exposed that the project was still below the documented 30% minimum evidence threshold after real app and UI classes were included.
* `Retry` and `CircuitBreaker` were production reliability primitives with limited direct behavioral coverage.

**Root cause**
* Coverage evidence previously depended on broad test count rather than measured line counters.
* Retry exhaustion, non-retryable open-circuit handling, and circuit-breaker recovery/reopen transitions were not directly covered by focused unit tests.

**Fix implemented**
* Added `ResiliencePrimitivesTest` for successful retry, retry exhaustion metadata, open-circuit no-retry behavior, null fallback after exhaustion, half-open recovery, and half-open failure reopening.
* Regenerated aggregate JaCoCo coverage after the new tests.

**Impact**
* Improves confidence in shared retry and circuit-breaker behavior used by integration and worker paths.
* Raises current aggregate line coverage to 30.54% (`3823` covered lines, `8696` missed lines), clearing the documented minimum coverage evidence threshold while leaving UI/device coverage as a remaining risk.

**Files modified**
* `core/data/src/test/kotlin/com/example/core/resilience/ResiliencePrimitivesTest.kt`
* `AUDIT_REPORT.md`

**Validation performed**
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest --tests com.example.core.resilience.ResiliencePrimitivesTest --no-configuration-cache` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew jacocoDebugUnitTestReport --no-configuration-cache -Djavax.net.ssl.trustStore=/Users/yashsomani/Desktop/Android\ Project/AI-Birthday/.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit` passed.
* `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache` passed.
* Manual device validation was not run because `adb devices` shows no attached devices.

**Commit message**
* `test: cover resilience primitives`

### Feature 21: Functionality Gap and UX Feedback Implementation

**Problem identified**
* Device contacts were documented as part of sync but only Google contact sync was implemented.
* Wish Preview had a simulated "send test to myself" path and AI Doctor had no email readiness/test action.
* Several high-impact settings were persisted but not visible in Settings: Gmail sender credentials, quiet hours, channel blackout, and biometric lock.
* Onboarding was informational instead of an actionable setup flow.
* Contact personalization accepted enum-like values through free text and closed the dialog before save success.
* Notification/setup surfaces and Splash still contained raw visible strings outside the hardcoded-string regression guard.

**Root cause**
* Earlier release-readiness passes localized and hardened screens in slices, but setup, sync, and send-readiness workflows still had mixed feedback patterns and hidden configuration.
* Sync merging used one preferred key per contact, so contacts with both phone and email could miss an obvious Google/device duplicate match.

**Fix implemented**
* Added typed domain boundary values for `MessageStatus`, `MessageChannel`, `ApprovalMode`, and `EventType`.
* Added `UiText` and `FeedbackEvent` primitives and applied them to Settings and Wish Preview feedback.
* Implemented device contact import via `ContactsContract`, gated by `READ_CONTACTS` behind explicit Contact List/Settings sync actions.
* Updated contact sync to merge Google and device contacts, preserve Google identity, fill missing device fields, count Google/device imports, recover when Google fails but device import succeeds, and match duplicates on any stable phone/email/name key.
* Replaced fake Wish Preview test-send feedback with `TestSendUseCase` and `TestSendServiceImpl`, sending a real email test to the configured Gmail sender.
* Added AI Doctor email readiness checks and a Test Email action that preserves operation feedback during automatic refresh.
* Exposed Gmail sender/app password, quiet hours, disabled channels, and biometric lock in Settings with localized snackbar feedback.
* Enforced email setup and disabled-channel checks in message dispatch.
* Reworked onboarding into a setup checklist covering sign-in, contacts, AI/email, permissions, Style Coach, and AI Doctor readiness.
* Reworked contact preferences to use typed controls for language, channel, formality, style, and automation mode, added send-time validation, and kept the dialog open until save succeeds.
* Added a per-contact personalization quality checklist using nickname, interests, notes, and preferred channel.
* Moved notification channel/setup strings into core/data resources and expanded the hardcoded-string regression guard to Splash and notification/setup surfaces.

**Impact**
* Users can now import local device contacts, understand import counts, and recover from Google sync failures with device data.
* Test-send, email readiness, and hidden settings are real, visible, and actionable instead of simulated or buried.
* Setup is clearer for first-run users and points directly to readiness diagnostics.
* Contact personalization has lower invalid-input risk and clearer quality feedback before wish generation.
* Notification and setup copy is resource-backed for localization and future regression protection.

**Files modified**
* `core/domain/src/main/kotlin/com/example/domain/model/*.kt`
* `core/domain/src/main/kotlin/com/example/domain/usecase/SyncContactsUseCase.kt`
* `core/domain/src/main/kotlin/com/example/domain/usecase/TestSendUseCase.kt`
* `core/domain/src/main/kotlin/com/example/domain/service/TestSendService.kt`
* `core/domain/src/main/kotlin/com/example/domain/service/ContactSyncService.kt`
* `core/data/src/main/kotlin/com/example/core/contacts/DeviceContactsReader.kt`
* `core/data/src/main/kotlin/com/example/core/contacts/ContactSyncServiceImpl.kt`
* `core/data/src/main/kotlin/com/example/core/automation/sender/TestSendServiceImpl.kt`
* `core/data/src/main/kotlin/com/example/core/automation/sender/MessageDispatcher.kt`
* `core/data/src/main/kotlin/com/example/core/automation/notifications/NotificationHelper.kt`
* `core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt`
* `core/data/src/main/kotlin/com/example/core/automation/workers/MessageGenerationWorker.kt`
* `core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt`
* `core/data/src/main/kotlin/com/example/core/automation/scheduler/DailyScheduler.kt`
* `core/data/src/main/res/values/strings.xml`
* `core/data/src/main/res/values-hi/strings.xml`
* `app/src/main/AndroidManifest.xml`
* `app/src/main/java/com/example/ui/feedback/UiText.kt`
* `app/src/main/java/com/example/ui/screens/onboarding/OnboardingScreen.kt`
* `app/src/main/java/com/example/ui/screens/settings/SettingsScreen.kt`
* `app/src/main/java/com/example/ui/screens/contacts/ContactListScreen.kt`
* `app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt`
* `app/src/main/java/com/example/ui/screens/events/EventsScreen.kt`
* `app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt`
* `app/src/main/java/com/example/ui/screens/setup/AutomationSetupScreen.kt`
* `app/src/main/java/com/example/ui/screens/splash/SplashScreen.kt`
* `app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt`
* `app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt`
* `app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt`
* `app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt`
* `core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt`
* `app/src/main/res/values/strings.xml`
* `app/src/main/res/values-hi/strings.xml`
* `app/src/test/java/com/example/domain/model/DomainValueParsingTest.kt`
* `app/src/test/java/com/example/domain/usecase/TestSendUseCaseTest.kt`
* `app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt`
* `app/src/test/java/com/example/domain/usecase/SyncContactsUseCaseTest.kt`
* `app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt`
* `app/src/test/java/com/example/ui/viewmodel/AutomationSetupViewModelTest.kt`
* `app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt`
* `app/src/test/java/com/example/ui/NoHardcodedStringsRegressionTest.kt`
* `SSOT_CONSOLIDATED.md`
* `AUDIT_REPORT.md`

**Validation performed**
* `./gradlew :app:compileDebugKotlin :core:data:compileDebugKotlin :core:domain:compileDebugKotlin --no-configuration-cache` passed.
* `./gradlew :app:testDebugUnitTest --tests com.example.domain.usecase.SyncContactsUseCaseTest --tests com.example.ui.viewmodel.AutomationSetupViewModelTest --tests com.example.domain.model.DomainValueParsingTest --tests com.example.domain.usecase.TestSendUseCaseTest --tests com.example.ui.viewmodel.WishPreviewViewModelTest --tests com.example.ui.viewmodel.SettingsViewModelTest --tests com.example.ui.NoHardcodedStringsRegressionTest --no-configuration-cache` passed.
* `./gradlew testDebugUnitTest --no-configuration-cache` passed.
* `./gradlew lintDebug assembleDebug --no-configuration-cache` passed.
* Robolectric emitted a non-fatal temp-directory cleanup warning after tests; the Gradle task completed successfully.

**Remaining improvements**
* Full device smoke testing was not run in this pass.
* Remaining ViewModels beyond Settings/Wish Preview/AI Doctor can continue migrating to the shared `FeedbackEvent` pattern.
* Contact List performance with 500+ contacts still needs measured UI profiling and a Paging UI switch if full-list rendering regresses.

**Commit message**
* `feat: close setup and feedback gaps`
