# RelateAI Codebase & Documentation Audit Report

## 0. Current Implementation Update

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
* **Test Coverage:** The documentation notes that test coverage targets are not yet met (~38 tests total). A future effort should focus on expanding unit and UI tests, particularly for the Jetpack Compose screens and complex `ViewModel` logic.
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
* Fresh debug reinstall on the same device was not forced because `adb install -r` reported `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; no uninstall was performed in order to preserve app data. Relaunching the installed package still showed no app-owned crash/runtime/database errors in filtered logcat.

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
