# Report 06 — Architecture Review

**Date**: 2026-06-01
**Author**: Senior Android Architect + System Architect (SSOT Audit)
**Scope**: Multi-module boundaries, code duplication, performance bottlenecks, security posture, refactoring opportunities, scalability concerns.

---

## 1. Module Structure Analysis

### 1.1 Current Module Graph

```
:app (com.android.application)
├── :core:ui (com.android.library)
├── :feature:splash
├── :feature:login
├── :feature:dashboard
├── :feature:contacts
├── :feature:events
├── :feature:analytics
├── :feature:onboarding
└── :feature:settings

:core:ui
└── (depends on :core:data transitively via :core:domain? — verify)

:feature:* (all)
├── :core:ui
├── :core:data
└── :core:domain

:core:data
├── :core:domain
└── (Room, SQLCipher, Hilt, Moshi, OkHttp, Gemini SDK)

:core:domain
└── (pure Kotlin — repository interfaces, use cases)
```

**Verdict**: Clean Architecture + multi-module is correctly applied. Feature modules depend only on `:core:*`, never on other features. No circular dependencies detected. **ADR-004 is properly executed.**

### 1.2 Module Size Distribution

| Module | Files (est.) | Lines (est.) | Status |
|---|---|---|---|
| `:app` | 5 | ~600 | OK |
| `:core:domain` | 5 | ~150 | Tiny, fine |
| `:core:data` | 35 | ~3500 | **Large — split candidate** |
| `:core:ui` | 5 | ~600 | OK |
| `:feature:splash` | 1 | ~150 | OK |
| `:feature:login` | 1 | ~200 | OK |
| `:feature:dashboard` | 4 | ~500 | OK |
| `:feature:contacts` | 4 | ~1500 | **Largest feature** |
| `:feature:events` | 2 | ~600 | OK |
| `:feature:analytics` | 2 | ~400 | OK |
| `:feature:onboarding` | 1 | ~1500 | **Monolithic** |
| `:feature:settings` | 2 | ~700 | OK (but `StyleCoachScreen.kt` is in `:core:ui`, not this module — see §2) |

**Issues**:
- `:core:data` is a god-module containing Auth, Backup, DB, Gemini, Contacts, Accessibility, Automation, Prefs, and DI. Should be split.
- `:feature:onboarding` has a single ~1500-line file (`OnboardingScreen.kt`).

---

## 2. Code Duplication

### 2.1 Composables Defined in Multiple Files — **HIGH**
Identified in Report 05:
- `GiftAdvisorView` in `feature/contacts/GiftAdvisorView.kt` AND `feature/contacts/ContactDetailScreen.kt:219`
- `MemoryVaultView` in `feature/contacts/MemoryVaultView.kt` AND `feature/contacts/ContactDetailScreen.kt` (used without contactId)

**Risk**: Compile-order-dependent behavior. Visual or behavioral drift between the two versions is inevitable.
**Fix**: Delete the inline copies; import the canonical versions.

### 2.2 StyleCoachScreen Defined in Two Modules — **HIGH**
- `feature/settings/src/main/kotlin/com/example/feature/settings/StyleCoachScreen.kt` (per SSOT §14.4)
- `core/ui/src/main/kotlin/com/example/ui/settings/StyleCoachScreen.kt` (per glob result)

**Risk**: One of these is dead code; whichever is "first" in import order wins.
**Fix**: Pick one location. Per SSOT §14.4, `:feature:settings` is the correct home. The `:core:ui` version is dead and should be deleted.

### 2.3 `AppContent.kt` and `MainAppScreen.kt` Mixing Concerns — **MEDIUM**
- `MainAppScreen` handles tab state, contact detail overlay, and responsive layout (phone vs tablet).
- `AppContent` handles per-tab composable routing.
- These could be split: `MainAppScreen` for layout/state, `AppContent` for routing only. Currently OK, but as more tabs are added, this will become hard to maintain.

### 2.4 Manual JSON Parsing in `MainActivity.kt:101-110` — **MEDIUM**
```kotlin
val arr = JSONArray(existing.sampleMessagesJson)
for (i in 0 until arr.length()) samples.add(arr.getString(i))
```
Uses `org.json.JSONArray` directly when Moshi is available.
- §9.2 SEC-02 claims "All API keys and OAuth tokens MUST be stored in EncryptedSharedPreferences" — fine.
- §33 Do's and Don'ts says "**Don't use `printStackTrace()`**" — fine.
- But §33 doesn't ban `org.json.JSONArray` explicitly. Still, mixing `org.json` and Moshi is inconsistent.
**Fix**: Use Moshi `JsonAdapter<List<String>>` or kotlinx-serialization.

### 2.5 Date/Time Formatting Duplicated — **LOW**
`SimpleDateFormat("MMM d, yyyy", Locale.getDefault())` and `String.format("%02d:%02d", ...)` appear in multiple screens. Should be centralized in a `core/ui` `DateFormatters` object.

---

## 3. Performance Concerns

### 3.1 MasterKey Derivation on Main Thread — **HIGH (CRIT-01)**
Per §29.3 and §9.1 NFR-PERF-01: MasterKey init takes ~200ms on main thread, causing ANR risk on cold start.
**Fix**: Provide `SecurePrefs` and `DatabaseKeyDerivation` via Hilt `@Provides` with `Dispatchers.IO` execution.

### 3.2 No Paging 3 for Contact List — **MEDIUM (MED-01)**
For users with 500+ contacts, the `LazyColumn` will render all items (with `key` set per item). With Coil image loading + classification, scroll may drop below 60fps.
**Fix**: Add `PagingSource<Int, ContactEntity>` to `ContactDao`, use `LazyPagingItems` in `ContactsContent`.

### 3.3 No Work Batching — **LOW (HIGH-04)**
`EventDiscoveryWorker`, `ContactSyncWorker`, and `RevivalWorker` may all fire close to each other (daily cycle). No `setExpedited()` or batch constraints.
**Fix**: Add `Constraints` to each, or chain them via `WorkContinuation`.

### 3.4 No DB Indices Beyond Primary Keys — **LOW**
§18.5 already calls this out. Queries like `events(nextOccurrenceMs)` and `sent_messages(contactId, sentAtMs DESC)` will table-scan as data grows.
**Fix**: Add `@Index(value = ["nextOccurrenceMs"])` to `EventEntity`, etc.

### 3.5 String.format() in Composable — **LOW**
`String.format(java.util.Locale.getDefault(), "%02d:%02d", contact.customSendTimeHour, contact.customSendTimeMinute)` runs on every recomposition.
**Fix**: `remember` the formatted string.

### 3.6 Coil Image Loading — **GOOD**
P3-05 enables Coil with disk + memory caching. Good.
**Improvement**: Add `placeholder` and `error` drawables for graceful degradation.

### 3.7 `whileSubscribed(5000)` on StateFlows — **GOOD**
All 4 StateFlows in `MainViewModel.kt:29-36` use `WhileSubscribed(5000)`. Correct pattern.

---

## 4. Security Concerns

### 4.1 Backup JSON Not Encrypted — **HIGH (CRIT-03)**
§22.8 acknowledges this. A user who backs up to SD card and loses the device has their entire contact list + message history in plaintext JSON.
**Fix**: Encrypt with user-provided passphrase using AES-256-GCM before write.

### 4.2 No Certificate Pinning — **MEDIUM (CRIT-02)**
§22.4 acknowledges. MITM risk if user installs a rogue CA.
**Fix**: Pin `generativelanguage.googleapis.com` and `people.googleapis.com` certificates in `network_security_config.xml`.

### 4.3 ANDROID_ID-Based Key Derivation — **MEDIUM**
`DatabaseKeyDerivation.kt` uses `Settings.Secure.ANDROID_ID`. ANDROID_ID is per-app-per-device since Android 8, so this is **acceptable** but:
- ANDROID_ID can be reset by factory reset → user loses DB key → must restore from backup.
- For Android 10+, ANDROID_ID is per-app-signing-key + per-user, so the same key won't be available after app reinstall on some devices.
**Fix**: Document limitation; consider Tink-based key wrapping with KeyStore master key for v2.

### 4.4 Field Injection in `MainActivity` — **LOW (NFR-MAINT-02)**
```kotlin
@Inject lateinit var styleProfileDao: StyleProfileDao
@Inject lateinit var eventDao: EventDao
```
Violates Hilt's preferred constructor injection. Also violates §33 Do's and Don'ts implied patterns.
**Fix**: Use `@AndroidEntryPoint` (already present) + `by viewModels()` pattern, or extract to a helper.

### 4.5 Accessibility Service Has Broad Power — **MEDIUM**
`WhatsAppAccessibilityService` has full window-content access for `com.whatsapp` and `com.whatsapp.w4b`. Could be exploited if a malicious WhatsApp overlay is used.
**Mitigation**: 
- Scoped to specific package names ✅
- Document for Play Store review ⚠️
- Audit logging not implemented (planned v2)

### 4.6 SecurePrefs Fallback to Plaintext — **LOW**
`TD-08` fix made this "explicit retry + clear-and-restart" instead of silent fallback. Good. But on devices with broken keystore, the app may not be usable.

### 4.7 No Rate Limiting on Sign-Out / Data Deletion — **N/A**
N/A.

### 4.8 PII in Crashlytics (v2) — **N/A for v1**
§28.4 plans opt-in Crashlytics. Must enforce no PII in stack traces (e.g., avoid logging message content).

---

## 5. Scalability Concerns

### 5.1 12 Modules, 7,000+ Lines — **OK for MVP**
71 production .kt files, 12 modules. Build times ~3-5 min for clean build (estimated). Multi-module helps parallel compilation.

### 5.2 Single-Activity Architecture — **GOOD**
`MainActivity` + Compose Navigation is correct. Avoids activity-lifecycle complexity.

### 5.3 Repository Pattern Without UseCase Layer — **OK for v1**
Repositories abstract data sources. UseCases would add another layer (planned P4-02). Not needed unless business logic grows complex.

### 5.4 JSON Blob Fields for Enrichment — **OK for v1**
§33 / ADR-017 acknowledges this is v1 simplicity. Migration to typed entities in v2.

### 5.5 No Cloud Sync — **OK for v1, Risk for Retention**
Local-first is by design (§3.4). Users who lose devices and forget to back up will lose everything. Backup-to-Google-Drive is planned v2.

### 5.6 No Multi-User Support — **OK for v1**
Single-user per device. Family plan is v2.

---

## 6. Build System

### 6.1 Version Catalog — **GOOD**
All dependencies in `gradle/libs.versions.toml`. No `hardcoded versions` in module `.kts` files.

### 6.2 Secrets Gradle Plugin — **GOOD**
Loads `.env` to `BuildConfig`. Standard pattern.

### 6.3 R8/ProGuard — **GOOD**
Enabled in release. `proguard-rules.pro` exists. Spot-check needed for keep rules.

### 6.4 Multi-Module Build — **GOOD**
12 modules. P4-01 in progress. Note: §1.2 says 7 modules (stale, see Report 01).

### 6.5 Missing CI Config — **MEDIUM**
SSOT §23.5 describes a GitHub Actions workflow, but **no `.github/workflows/android.yml` file exists** in the repo. Either create the file or update SSOT to reflect "manual CI only."

### 6.6 No Detekt / ktlint — **MEDIUM**
§26 Coding Standards mentions "Kotlin Official" style but no Detekt or ktlint config in repo. Either add it or update SSOT.

### 6.7 No Pre-commit Hooks — **LOW**
Optional; would catch format violations before push.

---

## 7. Test Architecture

### 7.1 Unit Test Coverage — **POOR**
41 tests across 8 files (~15% line coverage estimated). See Report 04 §15 for breakdown.

### 7.2 No Integration Tests — **HIGH GAP**
- No Room migration tests
- No Worker tests (all 5 workers untested)
- No Sender tests (all 3 untested)
- No ViewModel tests (only MainViewModel, 2 tests)
- No Composable UI tests

### 7.3 Screenshot Test Exists — **GOOD**
`GreetingScreenshotTest.kt` (Roborazzi) shows the test infra is in place. Expand to all screens.

### 7.4 No Test for `MainViewModel` Constructor Change — **PROVEN**
The 4-arg vs 3-arg mismatch was caught only at compile time, not by tests. Adding a `@HiltAndroidTest` or constructor-mirror test would have caught this.

---

## 8. Refactoring Opportunities (Prioritized)

### 8.1 Split `:core:data` into Sub-Modules — **HIGH IMPACT**
Proposed split:
- `:core:database` (Room entities, DAOs, AppDatabase, DatabaseKeyDerivation)
- `:core:network` (GeminiClient, GeminiModels, ResponseParser, PromptBuilder, RateLimiter, OkHttp)
- `:core:contacts` (GoogleContactsSync, DeviceContactsReader, ContactMerger)
- `:core:automation` (workers, senders, scheduler, notifications)
- `:core:prefs` (SecurePrefs)
- `:core:auth` (BiometricAuthManager)
- `:core:backup` (BackupManager)
- `:core:di` (AppModule, Hilt @Provides for above)

**Benefits**: Parallel compilation, clearer dependencies, easier to test in isolation, prevents `:core:data` from being a god-module.
**Effort**: 1-2 weeks. Defer to Q3 2026 (already in §31.1).

### 8.2 Extract Onboarding Composables — **MEDIUM IMPACT**
`OnboardingScreen.kt` (1500+ lines) has 10 screens. Split into per-screen files:
- `WelcomeScreen.kt`
- `GoogleSignInScreen.kt`
- `GeminiSetupScreen.kt`
- `ContactsPermScreen.kt`
- `SmsPermScreen.kt`
- `WhatsAppSetupScreen.kt`
- `BatteryOptScreen.kt`
- `WritingStyleScreen.kt`
- `AutomationPrefsScreen.kt`
- `ImportProgressScreen.kt`
- `OnboardingWrapper.kt` (the shared layout)

**Benefits**: Easier to maintain, easier to add steps, smaller diffs.
**Effort**: 1-2 days (TD-01, MED-05).

### 8.3 Move `:core:ui` Settings into `:feature:settings` — **MEDIUM**
`StyleCoachScreen.kt` exists in both `:core:ui/settings/` and `:feature:settings/`. Pick one (`:feature:settings`) and delete the other.

### 8.4 Introduce a `DateFormatters` Object — **LOW**
Centralize `SimpleDateFormat` and `String.format` patterns in `:core:ui/utils/`.

### 8.5 Introduce a `PermissionHelpers` Object — **LOW**
The `rememberLauncherForActivityResult` pattern is repeated for every permission. Extract a helper.

### 8.6 Replace `org.json` with Moshi in `MainActivity` — **LOW**
See §2.4.

### 8.7 Add `ContactDetailViewModel` — **MEDIUM**
`ContactDetailScreen` currently has no ViewModel; it operates on a passed `ContactEntity` and writes go through `MainActivity` callbacks. A dedicated ViewModel with `StateFlow<ContactDetailUiState>` would be cleaner.

### 8.8 Extract AI Failure UI — **LOW**
When Gemini returns no variants or errors, the app currently may show a "Regenerate" button (per §13.3). Verify this is a polished state, not just an exception catch.

---

## 9. Summary

| Area | Verdict | Top Action |
|---|---|---|
| Module structure | GOOD | Split `:core:data` (Q3) |
| Code duplication | POOR | Resolve GiftAdvisor, MemoryVault, StyleCoachScreen duplicates (1 day) |
| Performance | FAIR | Fix MasterKey on main thread (CRIT-01) |
| Security | FAIR | Encrypt backup JSON (CRIT-03) |
| Scalability | GOOD | Add Paging 3 for 500+ contacts (MED-01) |
| Build system | FAIR | Create actual CI workflow, add Detekt |
| Test coverage | POOR | Add 60% line coverage before Play Store |
| Refactoring backlog | 8 items | Defer to Q3-Q4 2026 |

**Production-readiness verdict for architecture**: NOT ready. P0 items (CRIT-01, CRIT-02, CRIT-03) are blockers. P1 items (duplicates, MainActivity Hilt, NFR-I18N-01) are strongly recommended.
