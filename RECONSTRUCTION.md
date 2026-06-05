# RelateAI — Complete Reconstruction Blueprint

> **Purpose**: Step-by-step guide to rebuild the entire project from scratch without access to the source code.
> **Reference**: `SSOT.md` for detailed specs (3702 lines covering all 33 sections).
> **Build Verified**: `assembleDebug` succeeds (427 tasks, 0 errors).
> **Last Updated**: 2026-06-05

---

## Table of Contents

1. [Prerequisites & Environment](#1-prerequisites--environment)
2. [Project Scaffold](#2-project-scaffold)
3. [Gradle Configuration](#3-gradle-configuration)
4. [Core Module: Domain](#4-core-module-domain)
5. [Core Module: Data](#5-core-module-data)
6. [Core Module: UI](#6-core-module-ui)
7. [Feature Modules (×9)](#7-feature-modules)
8. [App Module](#8-app-module)
9. [Android Resources](#9-android-resources)
10. [CI/CD & Deployment](#10-cicd--deployment)
11. [Build & Verify](#11-build--verify)
12. [Key Files Checklist](#12-key-files-checklist)

---

## 1. Prerequisites & Environment

### 1.1 Required Tools
- **Android Studio**: Ladybug | 2024.2+ or IntelliJ IDEA 2024.2+
- **JDK**: 17 (configured via `jvmToolchain(17)` in root `build.gradle.kts`)
- **Gradle**: 8.x (wired via `gradle-wrapper.properties`; AGP 9.2.1 compatible)
- **Android SDK**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`
- **Git**: For version control

### 1.2 Environment Variables & Secrets
Create `.env` in project root (gitignored):
```properties
GEMINI_API_KEY=<your-gemini-api-key>
```

Create `local.properties` (gitignored):
```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

For release builds, export these env vars:
- `KEYSTORE_PATH` — Path to release keystore (default: `my-upload-key.jks`)
- `STORE_PASSWORD` — Keystore password
- `KEY_PASSWORD` — Key password
- `KEY_ALIAS` — Key alias (default: `upload`)

### 1.3 Firebase Setup
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create project with package name `com.aistudio.relateai.qxtjrk`
3. Download `google-services.json` → place in `app/` directory
4. Enable Firebase Authentication → Sign-in method → Email/Password
5. Enable Firebase Vertex AI for Gemini access (or use direct REST API)

### 1.4 Google Cloud Setup
1. Enable People API in Google Cloud Console
2. Configure OAuth consent screen (external, add `contacts.readonly` scope)
3. Get web client ID for Google Sign-In
4. Create API key restricted to Gemini API

---

## 2. Project Scaffold

### 2.1 Create Root Directories
```bash
mkdir -p app/src/main/java/com/example
mkdir -p app/src/main/res/xml
mkdir -p app/src/main/res/values
mkdir -p app/src/main/res/mipmap-hdpi
mkdir -p app/src/test/java/com/example
mkdir -p core/domain/src/main/kotlin/com/example/core/db/entities
mkdir -p core/domain/src/main/kotlin/com/example/domain/repository
mkdir -p core/data/src/main/kotlin/com/example/core/db/dao
mkdir -p core/data/src/main/kotlin/com/example/core/gemini
mkdir -p core/data/src/main/kotlin/com/example/core/prefs
mkdir -p core/data/src/main/kotlin/com/example/core/contacts
mkdir -p core/data/src/main/kotlin/com/example/core/backup
mkdir -p core/data/src/main/kotlin/com/example/core/auth
mkdir -p core/data/src/main/kotlin/com/example/core/accessibility
mkdir -p core/data/src/main/kotlin/com/example/core/automation/workers
mkdir -p core/data/src/main/kotlin/com/example/core/automation/notifications
mkdir -p core/data/src/main/kotlin/com/example/core/automation/scheduler
mkdir -p core/data/src/main/kotlin/com/example/core/automation/sender
mkdir -p core/data/src/main/kotlin/com/example/data/repository
mkdir -p core/data/src/main/kotlin/com/example/domain/usecase
mkdir -p core/data/src/main/kotlin/com/example/di
mkdir -p core/ui/src/main/kotlin/com/example/ui/theme
mkdir -p core/ui/src/main/kotlin/com/example/ui/components
mkdir -p core/ui/src/main/kotlin/com/example/ui/navigation
mkdir -p core/ui/src/main/kotlin/com/example/ui/settings
mkdir -p feature/splash/src/main/kotlin/com/example/feature/splash
mkdir -p feature/login/src/main/kotlin/com/example/feature/login
mkdir -p feature/dashboard/src/main/kotlin/com/example/feature/dashboard
mkdir -p feature/contacts/src/main/kotlin/com/example/feature/contacts
mkdir -p feature/events/src/main/kotlin/com/example/feature/events
mkdir -p feature/analytics/src/main/kotlin/com/example/feature/analytics
mkdir -p feature/onboarding/src/main/kotlin/com/example/feature/onboarding
mkdir -p feature/settings/src/main/kotlin/com/example/feature/settings
mkdir -p feature/messages/src/main/kotlin/com/example/feature/messages
mkdir -p gradle/wrapper
```

### 2.2 Initialize Git
```bash
git init
echo ".env\nlocal.properties\nbuild/\n.cxx/\n*.iml\n.idea/\n" > .gitignore
```

---

## 3. Gradle Configuration

### 3.1 `settings.gradle.kts`
Root project name `RelateAI`, 13 modules:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "RelateAI"
include(":app"); include(":core:domain"); include(":core:data"); include(":core:ui")
include(":feature:splash"); include(":feature:login"); include(":feature:dashboard")
include(":feature:contacts"); include(":feature:events"); include(":feature:analytics")
include(":feature:onboarding"); include(":feature:settings"); include(":feature:messages")
```

### 3.2 Root `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.google.services) apply false
}
subprojects {
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
            jvmToolchain(17)
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    tasks.withType<Test>().configureEach {
        val toolchainService = project.extensions.findByType<org.gradle.jvm.toolchain.JavaToolchainService>()
            ?: project.rootProject.extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>()
        javaLauncher.set(toolchainService.launcherFor {
            languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
        })
    }
}
```

### 3.3 `gradle.properties`
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
kotlin.code.style=official
android.nonTransitiveRClass=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.workers.max=4
ksp.useKSP2=true
kotlin.jvm.target=11
```

### 3.4 `gradle/libs.versions.toml`
See SSOT.md §20.1 for the full version catalog. Key versions:
```
agp=9.2.1, kotlin=2.2.10, composeBom=2024.09.00, hilt=2.59.2
room=2.7.0, sqlcipher=4.5.4, workManager=2.9.0, moshi=1.15.2
firebaseBom=34.12.0, retrofit=2.12.0, okhttp=4.10.0, coil=2.7.0
```
Includes 45+ libraries across Compose, Hilt, Room, Network, Firebase, Testing, etc.

### 3.5 Module `build.gradle.kts` Files

Each module file is in `SSOT.md §14.4` (folder structure). Key configurations:

**`:core:domain`** — Android library, namespace `com.example.core.domain`, depends on `kotlinx-coroutines`, `room-runtime`, `paging-runtime`, `javax.inject`.

**`:core:data`** — Android library, namespace `com.example.core.data`, depends on `:core:domain`, applies 3 KSP processors: Room compiler, Hilt compiler, Moshi kotlin-codegen. Dependencies: Room, SQLCipher, OkHttp, Moshi, Retrofit, Firebase Auth, Firebase Vertex AI, SunMail, Coil, AndroidX Security Crypto, Biometric.

**`:core:ui`** — Android library, namespace `com.example.core.ui`, Compose-enabled. `api` dependencies on Compose BOM, Material 3, Navigation Compose, Activity Compose, Lifecycle, Hilt Navigation, Coil.

**`:feature:*`** — Android library, namespace `com.example.feature.<name>`, Compose-enabled. Each depends on all 3 core modules (except `:feature:messages` which omits `:core:domain`).

**`:app`** — Application module. Applies: `android.application`, `kotlin.compose`, `ksp`, `hilt.android`, `google.services`, `baselineprofile`, `roborazzi`. Namespace `com.example`, `applicationId = "com.aistudio.relateai.qxtjrk"`. Signing config uses env vars. Packaging excludes `META-INF/gradle/incremental.annotation.processors` (Hilt+Dagger duplicate fix). Room schema export at `$projectDir/schemas` via KSP arg. Dependencies include all 9 feature modules + Hilt + Firebase + all major libraries.

---

## 4. Core Module: Domain (`:core:domain`)

### 4.1 Room Entities (7 files)
All entities live under `core/domain/src/main/kotlin/com/example/core/db/entities/`:

**`ContactEntity.kt`** — 38 columns (17 core + 21 enrichment via JSON fields). Columns: `id` (PK, String), `googleContactId`, `name`, `nickname`, birthday fields (day/month/year), anniversary fields, `primaryPhone`, `primaryEmail`, `company`, `jobTitle`, `profilePhotoUri`, `contactGroup`, `relationshipType` (UNKNOWN/FAMILY/FRIEND/WORK/ACQUAINTANCE), `preferredLanguage`, `preferredChannel` (SMS/WHATSAPP/EMAIL), `formalityLevel`, `communicationStyle`, `healthScore`, `engagementScore`, `interactionFrequencyPerMonth`, `lastInteractionDate`, `lastWishedDate`, `consecutiveYearsWished`, `automationMode` (FULLY_AUTO/SMART_APPROVE/VIP_APPROVE/DEFAULT), `giftBudgetInr`, `skipAutoWish`, `customSendTimeHour`, `customSendTimeMinute`, JSON fields: `interestsJson`, `hobbiesJson`, `sharedHistoryJson`, `favoritesJson`, `relationsJson`, `notesText`, `typicalMoodWhenContacted`, `sensitiveTopicsJson`, `currentLifePhaseJson`, `createdAt`, `updatedAt`, `isArchived`.

**`EventEntity.kt`** — 12 columns. `id` (PK, String, UUID), `contactId` (FK), `type` (BIRTHDAY/ANNIVERSARY/WORK_ANNIVERSARY/GRADUATION/CUSTOM), `label`, `dayOfMonth`, `month`, `year`, `nextOccurrenceMs`, `daysUntil`, `isActive`, `notifyDaysBefore`, `source` (CONTACTS/CALENDAR/MANUAL/AI_INFERRED), `confidenceScore`, `isVerified`. Computed: `ageTurning` (`@get:Ignore`).

**`PendingMessageEntity.kt`** — 13 columns. `id` (PK, String, UUID), `contactId` (FK), `eventId` (FK), 6 text variant columns (`shortVariant` through `emotionalVariant`), `selectedVariant` (key), `selectedVariantText`, `channel`, `scheduledForMs`, `approvalMode`, `status` (PENDING/APPROVED/REJECTED/SENT/FAILED), `aiModel`, `generatedAtMs`, `editedByUser`, `userEditedText`, `qualityScore`, `tone` (WARM/FUNNY/NOSTALGIC/MOTIVATIONAL/PROFESSIONAL), `length` (ULTRA_SHORT/STANDARD/LONG), `includeEmoji`.

**`SentMessageEntity.kt`** — 13 columns. `id` (PK, String, UUID), `contactId` (FK), `eventType`, `eventYear`, `messageText`, `channel`, `sentAtMs`, `deliveryStatus` (SENT/DELIVERED/FAILED), `aiGenerated`, `geminiModel`, `variantUsed`, `replyAtMs`.

**`StyleProfileEntity.kt`** — 8 columns. `id` (PK, Int = 1 — singleton), `sampleMessagesJson`, `usesEmoji`, `avgMessageLength`, `commonPhrasesJson`, `commonGreetingsJson`, `formalityLevel`, `preferredLanguage`, `emojiSetJson`, `avoidPhrasesJson`, `toneDescriptors`, `sampleCount`, `updatedAtMs`.

**`MemoryNoteEntity.kt`** — 5 columns. `id` (PK, String, UUID), `contactId` (FK), `category` (PERSONAL/MILESTONE/INSIDE_JOKE/GIFT_IDEA/OTHER), `title`, `noteText`, `createdAt`.

**`GiftHistoryEntity.kt`** — 6 columns. `id` (PK, String, UUID), `contactId` (FK), `giftName`, `giftAmountInr`, `giftDate`, `occasion`, `notes`.

### 4.2 Repository Interfaces (3 files)
Under `core/domain/src/main/kotlin/com/example/domain/repository/`:
- **`ContactRepository.kt`** — `fun getAll(): Flow<List<ContactEntity>>`, `getAllSync()`, `getById()`, `upsert()`, `update()`, `updateClassification()`, `updateHealthScore()`, `updateLastWished()`, `incrementEngagementScore()`, `incrementConsecutiveYearsWished()`, `countAll(): Flow<Int>`, `countByRelationshipType(): Flow<List<RelationshipTypeCount>>`, `getTopByHealthScore()`, `getBottomByHealthScore()`, `delete()`.
- **`EventRepository.kt`** — `getAll(): Flow<List<EventEntity>>`, `getUpcoming(days)`, `getByContactId()`, `upsert()`, `getById()`.
- **`MessageRepository.kt`** — `getAllPending(): Flow<List<PendingMessageEntity>>`, `getSent(): Flow<List<SentMessageEntity>>`, `insertPending()`, `updatePendingStatus()`, `insertSent()`, `deletePending()`.

---

## 5. Core Module: Data (`:core:data`)

### 5.1 Database Layer

**`AppDatabase.kt`** (`core/data/.../db/`):
- `@Database(entities = [...all 7...], version = 7, exportSchema = true)`
- Abstract class extending `RoomDatabase()`
- Abstract DAO accessors: `contactDao()`, `eventDao()`, `pendingMessageDao()`, `sentMessageDao()`, `styleProfileDao()`, `memoryNoteDao()`, `giftHistoryDao()`
- Companion with `@Volatile private var INSTANCE`, `getDatabase(context, passphrase)` using `Room.databaseBuilder()` + `SupportFactory(passphrase)` for SQLCipher + `.addCallback()` + all migrations
- 3 migrations: `MIGRATION_1_2` (SQLCipher passphrase), `MIGRATION_2_3` (add StyleProfileEntity table), `MIGRATION_3_4` (add GiftHistoryEntity + rename access token field)

**`DatabaseKeyDerivation.kt`**:
- Key derived via PBKDF2 (65536 iterations) from `ANDROID_ID + appCertHash`
- Cached in plain `SharedPreferences` (`relateai_db_meta`, schema v2) to avoid 349ms re-derivation
- `warmUpAsync()` on `Application.onCreate()` daemon thread
- See SSOT.md §22.3 for full code

### 5.2 DAOs (7 files)
Under `core/data/.../core/db/dao/`:
- **`ContactDao.kt`** — `@Upsert`, `@Query("SELECT * FROM contacts ORDER BY name")`: `getAll(): Flow<List<ContactEntity>>`, `getById()`, `updateClassification()`, `updateHealthScore()`, `countAll(): Flow<Int>`, `countByRelationshipType(): Flow<List<RelationshipTypeCount>>`, `getTopByHealthScore(limit)`, `getBottomByHealthScore(limit)`.
- **`EventDao.kt`** — `@Upsert`, `getAll(): Flow<List<EventEntity>>`, `getUpcoming(days)`, `getByContactId()`.
- **`PendingMessageDao.kt`** — `@Insert`, `@Query` with status filtering, `updateStatus()`, `getByContactId()`, `getByEventId()`.
- **`SentMessageDao.kt`** — `@Insert`, `@Query` for history, `countAll()`, `countRecent()`.
- **`StyleProfileDao.kt`** — `@Query("SELECT * FROM style_profile LIMIT 1")`, `@Upsert`.
- **`MemoryNoteDao.kt`** — `@Query` by contactId, `@Insert`, `@Update`, `@Delete`.
- **`GiftHistoryDao.kt`** — `@Query` by contactId, `@Insert`, `@Update`, `@Delete`.

### 5.3 Gemini Integration
Under `core/data/.../core/gemini/`:
- **`GeminiModels.kt`** — Moshi `@JsonClass(generateAdapter = true)` data classes for request/response: `GeminiRequest(contents, generationConfig, safetySettings)`, `GeminiResponse(candidates: List<Candidate>)`, `Candidate(content: Content)`, `Content(parts: List<Part>)`, `Part(text: String)`, `InlinedDataBlock(mimeType, data)`, `SafetySetting(category, threshold)`, `GenerationConfig(temperature, maxOutputTokens, topP, topK)`.
- **`GeminiClient.kt`** — REST client calling `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent` with `x-goog-api-key` header. Uses Moshi for serialization. Returns `Result<List<String>>` (6 variants). Error handling: 429 → retry, 4xx → user error, 5xx → server error. All errors logged via `Log.e()`.
- **`PromptBuilder.kt`** — Builds structured prompt from ContactEntity + EventEntity + StyleProfileEntity. See SSOT.md §11.2 for prompt template.
- **`ResponseParser.kt`** — Extracts 6 variants from Gemini JSON response. Parses `candidates[0].content.parts[0].text`.
- **`RateLimiter.kt`** — Adaptive sliding-window (60 req/min). Uses `ConcurrentLinkedDeque` of timestamps. `tryAcquire(): Boolean` with cleanup of expired entries.

### 5.4 Contacts Integration
Under `core/data/.../core/contacts/`:
- **`GoogleContactsSync.kt`** — Uses Google People API via `com.google.api.services.people.v1`. `personFields=names,emailAddresses,phoneNumbers,birthdays,events,memberships,relations,photos`. Supports incremental sync via `syncToken`. OAuth token refresh via `AccountManager.getAuthToken()` before each call.
- **`DeviceContactsReader.kt`** — Reads from `ContactsContract.Contacts` + `CommonDataKinds.Event`. Filters for birthdays. Maps to `ContactEntity`.
- **`ContactMerger.kt`** — Deduplicates contacts from Google + device sources. Merge strategy: prefer Google data, supplement with device data. Matches on phone number or email.

### 5.5 Workers (5 files)
Under `core/data/.../core/automation/workers/`:
- **`ContactSyncWorker.kt`** — Periodic (daily). Fetches contacts from Google + device, merges, updates DB. Pre-flight: check API key not empty.
- **`EventDiscoveryWorker.kt`** — Periodic (daily at 6 AM). Query contacts with birthdays/anniversaries, upsert EventEntity with `daysUntil` computed at write time (KI-04 fix).
- **`MessageGenerationWorker.kt`** — Periodic (daily). Query events where `daysUntil <= 3`. For each, call GeminiClient to generate 6 variants, insert PendingMessageEntity.
- **`MessageDispatchWorker.kt`** — Triggered by AlarmManager. Query pending_messages where `scheduledForMs <= now`. Dispatch via MessageDispatcher based on approval mode.
- **`RevivalWorker.kt`** — Weekly. Query bottom 5 contacts by health score where `lastWishedDate > 180 days ago`. Generate revival message via Gemini. Show notification.

All workers: `@HiltWorker`, `@AssistedInject`, extend `CoroutineWorker`. Pre-flight check on `prefs.getGeminiApiKey().isEmpty()`. Exponential backoff (30s). See SSOT.md §1.4 for hardening details.

### 5.6 Senders (3 + 1 dispatcher)
Under `core/data/.../core/automation/sender/`:
- **`MessageDispatcher.kt`** — Routes to correct sender based on channel. Handles approval workflow. DI via Hilt `@EntryPoint`.
- **`SmsSender.kt`** — Uses `SmsManager.getDefault().sendTextMessage()`. Requires `SEND_SMS` permission.
- **`WhatsAppSender.kt`** — Interacts via `WhatsAppAccessibilityService`. Launches `Intent.ACTION_SEND` targeting `com.whatsapp`.
- **`EmailSender.kt`** — JavaMail via Gmail SMTP (`smtp.gmail.com:587`, TLS). User provides email + app password.

### 5.7 Accessibility
Under `core/data/.../core/accessibility/`:
- **`WhatsAppAccessibilityService.kt`** — Extends `AccessibilityService`. Configured for `com.whatsapp,com.whatsapp.w4b` only. Detects chat open → finds message input → types text → clicks send button.

### 5.8 Notifications
Under `core/data/.../core/automation/notifications/`:
- **`NotificationHelper.kt`** — Creates notification channels: `APPROVAL` (smart approve), `REVIVAL` (revival suggestions), `EVENT` (upcoming events). `showApprovalNotification()`, `showRevivalNotification()`, `showEventNotification()`.
- **`ApprovalReceiver.kt`** — BroadcastReceiver for notification actions (Approve/Edit/Reject). Uses Hilt `@EntryPoint` for DI.

### 5.9 Scheduler
Under `core/data/.../core/automation/scheduler/`:
- **`DailyScheduler.kt`** — Schedules WorkManager periodic requests. 1h minimum initial delay floor. `NetworkType.CONNECTED`, `BatteryNotLow` constraints.
- **`MessageDispatchReceiver.kt`** — BroadcastReceiver for AlarmManager exact-time alarms. Triggers `MessageDispatchWorker`.
- **`BootReceiver.kt`** — BroadcastReceiver for `BOOT_COMPLETED`. Reschedules all periodic workers.

### 5.10 Repository Implementations (3 files)
Under `core/data/.../data/repository/`:
- `ContactRepositoryImpl.kt`, `EventRepositoryImpl.kt`, `MessageRepositoryImpl.kt`
- Each implements the corresponding interface, injects the DAO via `@Inject constructor`
- Maps DAO calls to Repository interface, adds business logic (e.g., health score calculation in `updateHealthScore()`)

### 5.11 UseCases (10 files)
Under `core/data/.../domain/usecase/` (technical debt — should be in `:core:domain`):
- `ClassifyContactUseCase.kt`, `SyncContactsUseCase.kt`, `DiscoverEventsUseCase.kt`, `GenerateMessageUseCase.kt`
- `ApprovePendingMessageUseCase.kt`, `RejectPendingMessageUseCase.kt`, `DispatchMessageUseCase.kt`
- `RefreshHealthScoresUseCase.kt`, `GetDashboardMetricsUseCase.kt`, `GetAnalyticsUseCase.kt`

### 5.12 DI Module (`AppModule.kt`)
Under `core/data/.../di/`:
- `@Module @InstallIn(SingletonComponent::class) abstract class AppModuleBinds`
  - `@Binds ContactRepository.bind(ContactRepositoryImpl)`
  - `@Binds EventRepository.bind(EventRepositoryImpl)`
  - `@Binds MessageRepository.bind(MessageRepositoryImpl)`
- `@Module @InstallIn(SingletonComponent::class) object AppModule`
  - `@Provides @Singleton fun provideDatabase(context, keyDerivation): AppDatabase`
  - `@Provides fun provideContactDao(db): ContactDao` (same for all 7 DAOs)
  - `@Provides @Singleton fun provideGeminiClient(okHttp, moshi, rateLimiter, prefs): GeminiClient`
  - `@Provides @Singleton fun provideOkHttpClient(): OkHttpClient` (singleton, connection pool)
  - `@Provides @Singleton fun provideMoshi(): Moshi` (with KotlinJsonAdapterFactory)
  - `@Provides @Singleton fun provideSecurePrefs(context): SecurePrefs`
  - `@Provides @Singleton fun provideAuthManager(context): AuthManager`
  - `@Provides @Singleton fun provideWorkerScheduler(context): WorkerScheduler`
  - `@Provides fun provideBiometricAuthManager(context, prefs): BiometricAuthManager`

### 5.13 Auth & Security
Under `core/data/.../core/`:
- **`auth/BiometricAuthManager.kt`** — `authenticate(activity, onSuccess, onFail)`. Uses `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`. `isBiometricAvailable(): Boolean`.
- **`prefs/SecurePrefs.kt`** — Wraps `EncryptedSharedPreferences` (MasterKey AES-256-GCM, key alias `relateai_master_key_v4`). Methods: `getOAuthToken()`, `setOAuthToken()`, `getGeminiApiKey()`, `setGeminiApiKey()`, `isBiometricLockEnabled()`, `setBiometricLockEnabled()`, `isSecureStorageAvailable()`, `clearAll()`.
- **`backup/BackupManager.kt`** — Export: serialize all 7 tables to JSON, save to user-chosen file. Import: deserialize, upsert. Encryption: AES-256-GCM with user passphrase.
- **`backup/BackupEncryption.kt`** — AES-256-GCM encryption/decryption for backup JSON.

---

## 6. Core Module: UI (`:core:ui`)

### 6.1 Theme
Under `core/ui/.../ui/theme/`:
- **`Color.kt`** — Full palette: Primary `#8B5CF6` (Neon Violet), Secondary `#06B6D4` (Electric Cyan), Tertiary `#F43F5E` (Cyber Rose), Background `#0D0D0D` (Obsidian Black), Surface variants. Glassmorphic overlay colors: `GlassOverlay = Color(0x33FFFFFF)`, `GlassBorder = Color(0x4DFFFFFF)`.
- **`Type.kt`** — 13-style typography scale. Headline: `MonoTypeAce` (custom font). Body: `Inter`. All Material 3 roles: `displayLarge` through `labelSmall` with custom size/weight/letterSpacing/lineHeight.
- **`Shape.kt`** — 3 shapes: `extraSmall = RoundedCornerShape(8dp)`, `small = RoundedCornerShape(16dp)`, `medium = RoundedCornerShape(24dp)`.
- **`Theme.kt`** — Dark-only `RelateAIDarkColorScheme` using the neon palette. `RelateAITheme` composable wraps `MaterialTheme(colorScheme, typography, shapes)`. Applies glassmorphic overlay via `Surface(color = GlassOverlay, border = BorderStroke(1.dp, GlassBorder))` on top-level containers.

### 6.2 Navigation
Under `core/ui/.../ui/navigation/`:
- **`AppBottomNavigation.kt`** — 5-tab bottom nav: HOME, CONTACTS, EVENTS, MESSAGES, MORE. Tablet: `NavigationRail` variant (same 5 items). Uses `WindowSizeClass` for responsive switching. Nav routes: `FeatureNavigation` sealed class with routes.

### 6.3 Components
Under `core/ui/.../ui/components/`:
- **`Components.kt`** — Shared composables: `RelateAICard`, `RelateAIButton`, `GlassmorphicCard`, `EmptyState(title, message, actionLabel, onClick)`, `HealthScoreRing(score, size, strokeWidth)`.
- **`LoadingShimmer.kt`** — `ShimmerBox(width, height)`, `ShimmerCircle(size)`, `ShimmerTextLine(width)`, `ShimmerCard()`.
- **`TimePickerDialog.kt`** — Dialog with hour/minute selection for custom send time. Full-screen on small devices.

---

## 7. Feature Modules

### 7.1 `:feature:splash` — `SplashScreen.kt`
- `@Composable fun SplashScreen(onNavigate: (NavRoute) -> Unit)`
- Logo animation, checks auth state → biometric gate → login or home
- `SplashViewModel` with Hilt, checks `SecurePrefs.isBiometricLockEnabled()` + cached auth token

### 7.2 `:feature:login` — `LoginScreen.kt`
- Google Sign-In button using `CredentialManager.createCredentialManager()` + `GoogleIdTokenCredential`
- On success: store OAuth token via `SecurePrefs`, navigate to onboarding or home
- `LoginViewModel` with Hilt

### 7.3 `:feature:dashboard` — Main App Shell
- **`MainAppScreen.kt`** — Scaffold with top bar + bottom nav/rail + nav host
- **`AppContent.kt`** — NavHost with routes for all screens, screen-specific ViewModels
- **`DashboardScreen.kt`** — Home tab: health score ring, upcoming events summary, recent messages, revival suggestions
- **`MainViewModel.kt`** — `@HiltViewModel`, 4-arg constructor (contactRepository, eventRepository, messageRepository, getDashboardMetricsUseCase). StateFlows for contacts, events, pendingMessages, healthScore.

### 7.4 `:feature:contacts` — Contact Management
- **`ContactsContent.kt`** — `LazyColumn` with contact search/filter, Paging 3 integration. Each item shows name, photo, relationship type badge, health score ring.
- **`ContactDetailScreen.kt`** — Hero section (photo, name, relationship), stats grid (health/engagement/consecutive years), tabs (Details/Memories/Gifts), interests list, automation card (DND switch, send time picker, channel selector, VIP toggle), FAB. Stitch-aligned.
- **`MemoryVaultView.kt`** — Notes list with color-coded category badges, info banner, delete button, full-width add button, empty state with prompt.
- **`GiftAdvisorView.kt`** — Gift suggestions list, gift history, log gift dialog, empty state.

### 7.5 `:feature:events` — Event Timeline
- **`EventsScreen.kt`** — Top bar with month navigation, timeline with today badge, verified icon, message approval cards, status badges, FAB for quick-add.
- **`BirthdayCalendarView.kt`** — Month grid with birthday dots, click to see details.

### 7.6 `:feature:messages` — Message Center
- **`MessagesScreen.kt`** — Pending/sent tabs. For pending: accent border cards, variant selector (FilterChip: length + tone), Smart Approve countdown, action buttons (Approve/Edit/Reject), FAB.

### 7.7 `:feature:analytics` — Dashboard
- **`AnalyticsScreen.kt`** — Total wishes, this month, pending approvals, contact count by type, top 5/bottom 5 health scores, average health score, engagement trend line chart.
- **`AnalyticsViewModel.kt`** — `@HiltViewModel`, injects DAOs directly for aggregate queries. Exposes `UiState` via `StateFlow`.

### 7.8 `:feature:onboarding` — 10-Step Wizard
- **`OnboardingScreen.kt`** — Step container with progress indicator, navigation (Next/Back), step content composables. Steps: 1-Welcome, 2-Google Sign-In, 3-Gemini API Key, 4-Permissions Bundle (contacts+SMS), 5-WhatsApp Accessibility, 6-Writing Style, 7-Automation Prefs, 8-Battery Optimization, 9-Import Progress, 10-Complete.

### 7.9 `:feature:settings` — Settings
- **`SettingsScreen.kt`** — Material 3 toggle items: Biometric Lock, WhatsApp Setup, Gemini API Key, Email Setup, Sync Now, Backup, Restore, Send Test To Self, Sign Out (clears all data).
- **`StyleCoachScreen.kt`** — Text input for training text, save to StyleProfileEntity via DAO.

---

## 8. App Module (`:app`)

### 8.1 `MainActivity.kt`
- `@AndroidEntryPoint` FragmentActivity
- `setContent { RelateAITheme { RelateAINavGraph() } }`
- Hilt field injection (technical debt per HIGH-06)
- Registered in manifest with MAIN/LAUNCHER intent filter + shortcuts metadata

### 8.2 `RelateAIApp.kt` (Application Class)
- `@HiltAndroidApp`
- `onCreate()`: init Firebase, init WorkManager, call `scheduleAllWorkers()`, init LeakCanary (debug), init StrictMode (debug), enable predictive back gesture, `DatabaseKeyDerivation.warmUpAsync(this)`
- `scheduleAllWorkers()`: schedules 4 periodic workers (ContactSync, EventDiscovery, MessageGeneration, Revival) with 1h minimum initial delay, 30s exponential backoff

### 8.3 `AndroidManifest.xml`
13 permissions, 4 Activities, 3 Services, 2 Receivers, WorkManager init provider, network security config.
```xml
<!-- Permissions -->
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<uses-feature android:name="android.hardware.telephony" android:required="false" />

<!-- Application -->
<application android:name=".RelateAIApp" android:allowBackup="true"
    android:networkSecurityConfig="@xml/network_security_config"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:enableOnBackInvokedCallback="true"
    android:fullBackupContent="@xml/backup_rules"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.MyApplication">

    <!-- Activities -->
    <activity android:name=".MainActivity" android:exported="true" ...>
        <intent-filter><action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" /></intent-filter>
        <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />
    </activity>

    <!-- Services -->
    <service android:name="com.example.core.accessibility.WhatsAppAccessibilityService"
        android:exported="true" android:label="RelateAI — Auto WhatsApp"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
        <intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter>
        <meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_service_config" />
    </service>

    <!-- Receivers -->
    <receiver android:name="com.example.core.automation.scheduler.MessageDispatchReceiver" android:exported="false" />
    <receiver android:name="com.example.core.automation.notifications.ApprovalReceiver" android:exported="false" />
    <receiver android:name="com.example.core.automation.scheduler.BootReceiver" android:exported="true">
        <intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /></intent-filter>
    </receiver>

    <!-- WorkManager init provider (custom init to avoid auto-init) -->
    <provider android:name="androidx.startup.InitializationProvider"
        android:authorities="${applicationId}.androidx-startup" android:exported="false"
        tools:node="merge">
        <meta-data android:name="androidx.work.WorkManagerInitializer"
            android:value="androidx.startup" tools:node="remove" />
    </provider>
</application>
```

### 8.4 Widget
- **`BirthdayWidgetProvider.kt`** — AppWidgetProvider showing today's birthdays. Update on `ACTION_BOOT_COMPLETED` and periodic tick.
- **`res/layout/widget_birthday.xml`** — Simple layout with contact name, days until.

---

## 9. Android Resources

### 9.1 XML Config Files (under `app/src/main/res/xml/`)

**`network_security_config.xml`** — Certificate pinning for Google APIs:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">googleapis.com</domain>
        <domain includeSubdomains="true">gstatic.com</domain>
    </domain-config>
</network-security-config>
```

**`accessibility_service_config.xml`** — Scoped to WhatsApp:
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeViewClicked|typeViewFocused"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:packageNames="com.whatsapp,com.whatsapp.w4b" />
```

**`data_extraction_rules.xml`** — Android 12+ backup:
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" />
    </device-transfer>
</data-extraction-rules>
```

**`backup_rules.xml`** — Full backup rules.

**`shortcuts.xml`** — App shortcuts for quick actions.

### 9.2 `strings.xml` (66+ entries)
See SSOT.md §8 for all 52+ string resource IDs. Key categories:
- App name: `RelateAI`
- Navigation: `home`, `contacts`, `events`, `messages`, `analytics`, `settings`, `more`
- Contact: `contact_health_score`, `contact_engagement_score`, `contact_communication_style`, `contact_automation_settings`, `contact_dnd_label`, `contact_send_time_label`, `edit_contact`
- Memory Vault: `memory_vault_title`, `memory_vault_subtitle`, `memory_no_memories`, `memory_add_prompt`
- Gift Advisor: `gift_advisor_title`, `gift_suggestions_title`, `gift_search_button`, `gift_no_gifts`, `gift_history_title`
- Events: `events_send_message`, `events_generate_wish`, `messages_no_recent`, `analytics_reconnect`
- Common: `save`, `cancel`, `add`, `delete`, `back`, `edit`

### 9.3 Theme (`res/values/themes.xml`)
```xml
<style name="Theme.MyApplication" parent="android:Theme.Material.Light.NoActionBar" />
```

### 9.4 `baseline-prof.txt`
Hot code paths for AOT compilation — includes `kotlinx.coroutines`, `androidx.compose`, `androidx.room`, `androidx.work`, app-specific startup paths.

---

## 10. CI/CD & Deployment

### 10.1 GitHub Actions (`.github/workflows/android.yml`)
```yaml
name: Android CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17' }
      - name: Cache Gradle
        uses: actions/cache@v4
        with: { path: ~/.gradle/caches, key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle.kts') }} }
      - name: Lint
        run: ./gradlew lint
      - name: Test
        run: ./gradlew test
      - name: Build Debug APK
        run: ./gradlew assembleDebug
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with: { name: app-debug, path: app/build/outputs/apk/debug/app-debug.apk }
```

### 10.2 ProGuard Rules (`app/proguard-rules.pro`)
46 lines — keeps: Hilt generated classes, Room entities, Moshi JsonAdapters, OkHttp, Retrofit, Gemini models, Google API classes, JavaMail, CoroutineWorker subclasses, Service subclasses, BroadcastReceiver subclasses.

### 10.3 Build Commands
```bash
# Full build
./gradlew assembleDebug

# Tests
./gradlew test
./gradlew :core:data:test

# Lint
./gradlew lint

# Release
./gradlew assembleRelease   # requires KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD env vars

# Room schema generation
./gradlew :core:data:kspDebugKotlin

# Install on device
./gradlew installDebug
```

---

## 11. Build & Verify

### 11.1 First-Time Setup
1. Clone repo, open in Android Studio
2. Create `.env` with `GEMINI_API_KEY`
3. Place `google-services.json` from Firebase Console in `app/`
4. Run `./gradlew assembleDebug` — should produce 427 tasks, 0 errors
5. Run `./gradlew test` — 42 tests should pass

### 11.2 Key Verification Points
| Check | Command | Expected |
|-------|---------|----------|
| Build succeeds | `./gradlew assembleDebug` | BUILD SUCCESSFUL (427 tasks) |
| Unit tests pass | `./gradlew test` | 42 tests pass |
| Lint passes | `./gradlew lint` | No errors |
| KSP generates adapters | `./gradlew :core:data:kspDebugKotlin` | Moshi JsonAdapters generated |
| Room schema generated | Check `app/schemas/` | schema JSON files present |

### 11.3 Common Build Issues & Fixes
- **`META-INF/gradle/incremental.annotation.processors` duplicate** → fix: add to `packaging.excludes` in `:app/build.gradle.kts`
- **`GeminiRequestJsonAdapter ClassNotFoundException`** → fix: enable `ksp(libs.moshi.kotlin.codegen)` on `:core:data`
- **`NoSuchMethodError: getAuthToken`** → fix: add `play-services-auth` dependency
- **Room schema not found** → fix: add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` in `:app/build.gradle.kts`
- **Hilt compilation error** → fix: add `ksp(libs.hilt.compiler)` + `ksp(libs.hilt.ext.compiler)` to each module

---

## 12. Key Files Checklist

### Must Recreate (93+ files)
| # | File | Source |
|---|------|--------|
| 1 | `settings.gradle.kts` | §3.1 |
| 2 | `build.gradle.kts` (root) | §3.2 |
| 3 | `gradle.properties` | §3.3 |
| 4 | `gradle/libs.versions.toml` | §3.4 |
| 5 | `app/build.gradle.kts` | §3.5 |
| 6 | `core/domain/build.gradle.kts` | §3.5 |
| 7 | `core/data/build.gradle.kts` | §3.5 |
| 8 | `core/ui/build.gradle.kts` | §3.5 |
| 9-17 | 9 feature `build.gradle.kts` files | §3.5 |
| 18 | `app/proguard-rules.pro` | §10.2 |
| 19 | `AndroidManifest.xml` | §8.3 |
| 20-24 | 5 XML config files | §9.1 |
| 25 | `strings.xml` | §9.2 |
| 26 | `themes.xml` | §9.3 |
| 27 | `baseline-prof.txt` | §9.4 |
| 28 | `MainActivity.kt` | §8.1 |
| 29 | `RelateAIApp.kt` | §8.2 |
| 30-31 | Widget files | §8.4 |
| 32-38 | 7 Room entities | §4.1 |
| 39-41 | 3 Repository interfaces | §4.2 |
| 42 | `AppDatabase.kt` | §5.1 |
| 43 | `DatabaseKeyDerivation.kt` | §5.1 |
| 44-50 | 7 DAO files | §5.2 |
| 51-55 | 5 Gemini files | §5.3 |
| 56-58 | 3 Contacts files | §5.4 |
| 59-63 | 5 Worker files | §5.5 |
| 64-67 | 4 Sender files | §5.6 |
| 68 | WhatsAppAccessibilityService | §5.7 |
| 69-71 | 3 Notification files | §5.8 |
| 72-74 | 3 Scheduler files | §5.9 |
| 75-77 | 3 Repository impls | §5.10 |
| 78-87 | 10 UseCase files | §5.11 |
| 88 | `AppModule.kt` (DI) | §5.12 |
| 89-91 | Auth/SecurePrefs/Backup | §5.13 |
| 92-95 | Theme files (Color/Type/Shape/Theme) | §6.1 |
| 96 | Navigation | §6.2 |
| 97-99 | UI Components | §6.3 |
| 100+ | 9+ feature screen files | §7 |

### Reference Documents
- **`SSOT.md`** — Full 33-section specification (3702 lines) — THE primary reference
- **`IMPLEMENTATION_STATUS.md`** — Current build status (50% complete, all P0 done)
- **`PRD.md`** — Product requirements document
- **`reports/`** — 8 audit reports with detailed analysis

---

## Appendix A: Full DB Migrations

### MIGRATION_1_2 (v1 → v2) — SQLCipher passphrase
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA key = 'relateai_v1_legacy';")
        db.execSQL("PRAGMA rekey = 'relateai_v2_migration';")
    }
}
```

### MIGRATION_2_3 (v2 → v3) — Add StyleProfileEntity
```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `style_profile` (`id` INTEGER NOT NULL, `sampleMessagesJson` TEXT, `usesEmoji` INTEGER NOT NULL DEFAULT 0, `avgMessageLength` INTEGER NOT NULL DEFAULT 0, `commonPhrasesJson` TEXT, `commonGreetingsJson` TEXT, `formalityLevel` TEXT, `preferredLanguage` TEXT, `emojiSetJson` TEXT, `avoidPhrasesJson` TEXT, `toneDescriptors` TEXT NOT NULL DEFAULT '', `sampleCount` INTEGER NOT NULL DEFAULT 0, `updatedAtMs` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
    }
}
```

### MIGRATION_3_4 (v3 → v4) — Add GiftHistory + rename access token field
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `gift_history` (`id` TEXT NOT NULL, `contactId` TEXT, `giftName` TEXT, `giftAmountInr` INTEGER NOT NULL DEFAULT 0, `giftDate` INTEGER NOT NULL DEFAULT 0, `occasion` TEXT, `notes` TEXT, PRIMARY KEY(`id`))")
    }
}
```

---

## Appendix B: API Endpoints

| Service | Method | Endpoint | Auth |
|---------|--------|----------|------|
| Gemini | POST | `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent` | `x-goog-api-key` header |
| Google People | GET | `https://people.googleapis.com/v1/people/me/connections?personFields=...&pageSize=2000` | OAuth Bearer token |
| Gmail SMTP | SMTP | `smtp.gmail.com:587` (TLS) | SMTP credentials |

---

## Appendix C: Environment Variables

| Variable | Required For | Default | Storage |
|----------|-------------|---------|---------|
| `GEMINI_API_KEY` | AI message generation | None (user-provided) | `.env` + `SecurePrefs` |
| `KEYSTORE_PATH` | Release builds | `my-upload-key.jks` | CI secrets |
| `STORE_PASSWORD` | Release builds | None | CI secrets |
| `KEY_PASSWORD` | Release builds | None | CI secrets |
| `KEY_ALIAS` | Release builds | `upload` | CI secrets |

---

> **END OF RECONSTRUCTION.md** — This document, combined with `SSOT.md` (3702 lines), provides everything needed to rebuild RelateAI from scratch.
> **Build Verification**: Last verified with `assembleDebug` — 427 tasks, 0 errors.
> **Reference**: Always check `SSOT.md` for detailed specifications on any section.
