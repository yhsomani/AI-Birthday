# RelateAI Consolidated Single Source of Truth

Last updated: 2026-06-10

`features.md` is the primary feature source of truth for the current Android project in this repository. This document is the expanded architecture and evidence companion. It is based on source inspection of `:app`, `:core:domain`, `:core:data`, `:core:ui`, Gradle configuration, Android manifest resources, CI workflow, Room schemas, test result XML, JaCoCo output, and existing documentation.

Code remains the final authority. If this document conflicts with implementation, update this document after verifying the code.

## 1. Project Overview

RelateAI is a local-first Android relationship assistant. It imports contacts, discovers important dates, learns relationship context and user writing style, generates personalized wishes with Gemini, routes approvals, schedules delivery, and sends messages over SMS, WhatsApp, or email without a custom backend.

Primary objectives:

- Reduce forgotten birthdays, anniversaries, work anniversaries, and custom relationship moments.
- Generate messages that fit the contact, event, channel, relationship, language, and user style.
- Automate delivery while keeping user control through approval modes and per-contact preferences.
- Keep data on device except for deliberate external integration calls such as Google sign-in, Google People API, Gemini, Gmail SMTP, SMS, and WhatsApp intents/accessibility.

Target users:

- People with many personal or professional contacts who want relationship maintenance without manual calendar and message work.
- Users who want differentiated approval levels: fully automatic, smart approval, or VIP approval.
- Users who value local-first storage, encrypted local data, and explicit sign-out cleanup.

Main business domains:

- Contact graph and relationship context.
- Event discovery and date normalization.
- AI message generation and style personalization.
- Approval, scheduling, and message delivery.
- Relationship health, analytics, memory, and gifts.
- Local data security, backup, and operational resilience.

## 2. Implementation Snapshot

Repository and modules:

- Root project: `RelateAI`.
- Active Gradle modules: `:app`, `:core:domain`, `:core:data`, `:core:ui`.
- Application namespace: `com.example`.
- Android applicationId: `com.aistudio.relateai.qxtjrk`.
- SDK targets: compileSdk 37, minSdk 24, targetSdk 36.
- Runtime architecture: Jetpack Compose UI, Hilt DI, Room v13, SQLCipher, WorkManager, AlarmManager exact alarms, Firebase Auth, Google Sign-In, Google People API, Firebase Vertex AI or user Gemini API key, JavaMail Gmail SMTP, Android SMS APIs, WhatsApp Accessibility Service.
- No custom server or REST backend exists in this repository.

Important permissions and Android surfaces:

- Permissions: `SEND_SMS`, `READ_CONTACTS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `WAKE_LOCK`.
- Main activity deep links: `relateai://wish`, `relateai://contact`, `relateai://settings`.
- Registered receivers: message dispatch, approval actions, event reminders, boot completed, SMS status callbacks, birthday widget provider.
- Registered service: `WhatsAppAccessibilityService`.
- AppWidget: birthday and upcoming event widget.
- Dynamic shortcuts: compose message and view contacts.

Validation snapshot:

- `./gradlew testDebugUnitTest lintDebug` passed during this documentation update.
- `./gradlew jacocoDebugUnitTestReport` passed during this documentation update.
- Local XML reports under `app/build/test-results/testDebugUnitTest`, `core:data`, and `core:domain`: 241 tests, 0 failures, 0 errors, 0 skipped.
- Latest aggregate JaCoCo report: 30,148 covered and 96,178 missed instructions; 4,083 covered and 10,075 missed lines; 878 covered and 7,589 missed branches. Approximate coverage: 23.9 percent instruction, 28.8 percent line, 10.4 percent branch.

## 3. Feature Hierarchy

```text
RelateAI
+-- Entry, Navigation, and Account
|   +-- Splash routing
|   +-- Onboarding
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
|   +-- Event reminder notification receiver
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

## 4. Feature Dependency Map

| Feature | Depends On | Enables |
|---|---|---|
| Google authentication | Firebase Auth, Google Sign-In, People API scope | Google contacts sync, authenticated Gemini path |
| Guest mode | AuthManager bypass, SecurePrefs guest flag | Local demo flow, mock contacts |
| Contact sync | Auth, People API, ContactsProvider, Room, dedupe logic | Events, classification, messages, analytics |
| Event discovery | Contact repository, event repository, date normalization | Message generation, reminders, dashboard |
| AI classification | Contact sync, Gemini client | Relationship-aware defaults and personalization |
| Message generation | Contact, event, style, memory, gift, AI settings | Pending messages and approval workflow |
| Approval workflow | Pending messages, notifications, scheduler | Dispatch eligibility |
| Exact scheduling | Pending approvals, AlarmManager, WorkManager fallback | On-time automated dispatch |
| Dispatch | Message dispatcher, channel sender, preferences | Sent history, delivery analytics, health scoring |
| Analytics | Contacts, events, sent messages, pending messages | Dashboard, export, user insights |
| Backup/restore | Room repositories, encryption, passphrase | Data portability |
| AI Doctor | Auth, prefs, workers, health monitor, dead-letter queue | Operational readiness and troubleshooting |
| Security storage | SecurePrefs, SQLCipher, key derivation | Safe local storage, sign-out purge |
| CI/build | Gradle modules, lint, tests, coverage, release guard | Release confidence and regression checks |

## 5. Complete Feature Inventory

| ID | Feature | Category | Status | Completion | Test Coverage | Confidence |
|---|---|---|---|---:|---|---:|
| F-001 | App shell, navigation, routes, permissions | UI Feature | Fully Implemented | 95% | Partially Tested | 95% |
| F-002 | Splash and onboarding | UI Feature | Fully Implemented | 95% | Partially Tested | 95% |
| F-003 | Authentication, guest mode, session state | Core Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-004 | Settings and secure configuration | UI/System Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-005 | Home dashboard and relationship planner | UI Feature | Fully Implemented | 95% | Partially Tested | 95% |
| F-006 | Contact sync, import, and deduplication | Core Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-007 | Contact list search, filter, sort | UI Feature | Fully Implemented | 95% | Partially Tested | 95% |
| F-008 | Contact detail personalization | Core Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-009 | Event discovery | Backend Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-010 | Manual and custom event creation | Core Feature | Fully Implemented | 90% | Partially Tested | 90% |
| F-011 | Messages inbox and bulk actions | UI Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-012 | Wish preview, editing, feedback, regeneration | Core Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-013 | Chat history | UI Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-014 | Analytics and CSV export | UI/Backend Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-015 | Activity history and audit log | System Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-016 | Style Coach | AI Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-017 | Memory Vault | Core Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-018 | Gift Advisor | AI/UI Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-019 | Encrypted backup and restore | System Feature | Fully Implemented | 90% | Partially Tested | 90% |
| F-020 | Automation setup / AI Doctor | System Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-021 | Room database, schema, migrations | Backend Feature | Fully Implemented | 95% | Partially Tested | 98% |
| F-022 | AI contact classification | AI Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-023 | AI message generation and fallback | AI Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-024 | Approval lifecycle | Core Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-025 | Exact scheduling and boot recovery | Backend Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-026 | WorkManager automation chain | Backend Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-027 | Dispatch orchestration | Backend Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-028 | SMS delivery and status callbacks | Integration Feature | Fully Implemented | 80% | Partially Tested | 85% |
| F-029 | WhatsApp Accessibility delivery | Integration Feature | Fully Implemented | 75% | Not Tested | 80% |
| F-030 | Gmail SMTP delivery and test send | Integration Feature | Fully Implemented | 80% | Partially Tested | 85% |
| F-031 | Notifications and action receivers | System Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-032 | Revival suggestions | AI/System Feature | Fully Implemented | 80% | Partially Tested | 90% |
| F-033 | Relationship health scoring | Backend Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-034 | Widget, deep links, shortcuts | UI/System Feature | Fully Implemented | 80% | Not Tested | 85% |
| F-035 | Security, privacy, and local encryption | System Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-036 | Sign-out data purge | Security Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-037 | Resilience, logging, health, dead-letter queue | System Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-038 | External API and service interfaces | Integration Feature | Fully Implemented | 85% | Partially Tested | 95% |
| F-039 | Build, CI, release guard, coverage | Developer Feature | Fully Implemented | 90% | Partially Tested | 95% |
| F-040 | Design system and localization | UI/Developer Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-041 | Developer helper scripts and docs | Developer Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-042 | Biometric app lock enforcement | Security Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-043 | Quiet hours, blackout dates, reminder toggles | System Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-044 | Event reminder scheduling | System Feature | Fully Implemented | 85% | Partially Tested | 90% |
| F-045 | Mood logs | Deprecated | Deprecated | 0% | Partially Tested via migration | 98% |
| F-046 | Dedicated birthday quick-add FAB/modal | UI Feature | Deprecated | 0% | Not Tested | 90% |
| F-047 | Legacy Retrofit Gemini model layer | Internal Feature | Experimental | 30% | Not Tested | 75% |

## 6. Detailed Feature Reference

### F-001 App Shell, Navigation, Routes, Permissions

- Category: UI Feature.
- Description: Provides the Android application entry point, Compose app shell, route graph, bottom navigation, deep-link handling, and runtime permission prompts.
- Functionality: Starts `MainActivity`, creates the Compose navigation graph, routes through splash/auth/onboarding/home screens, exposes bottom tabs for Home, Contacts, Events, Messages, and Analytics, encodes route arguments safely, and prompts for SMS/notification permissions when relevant.
- Components involved: `MainActivity`, `RelateAIApp`, `NavGraph`, `Screen`, `RouteArgumentCodec`, screen composables, viewmodels, Android manifest.
- Related files: `app/src/main/java/com/example/MainActivity.kt`, `app/src/main/java/com/example/RelateAIApp.kt`, `app/src/main/java/com/example/ui/navigation/NavGraph.kt`, `app/src/main/java/com/example/ui/navigation/Screen.kt`, `app/src/main/java/com/example/ui/navigation/RouteArgumentCodec.kt`, `app/src/main/AndroidManifest.xml`.
- Dependencies: Jetpack Compose, Navigation Compose, Hilt, Android permission APIs, app resources.
- User workflow: User opens app, splash decides the first route, bottom navigation controls primary sections, detail screens are opened from lists or deep links. Edge cases include encoded contact/message ids and denied runtime permissions.
- Current status: Fully Implemented.
- Completion percentage: 95%.
- Test coverage: Partially Tested. `RouteArgumentCodecTest` covers argument encoding, and `MainActivityNavigationSmokeTest` covers permission-rationale dismissal plus Home/Contacts/Events/Messages/Analytics bottom-nav clicks at the Compose smoke level. Debug builds use `com.aistudio.relateai.qxtjrk.debug` for side-by-side validation. Connected execution on device `1b87b5db` installed and started the debug package, but the smoke stalled at 0/2 tests while another app was foregrounded; live validation requires an idle, unlocked device.
- Confidence score: 95%.

### F-002 Splash and Onboarding

- Category: UI Feature.
- Description: Determines first-run and returning-user routing and captures onboarding completion.
- Functionality: Splash checks signed-in state and onboarding completion, routes to Home, Auth, or Onboarding, and onboarding stores completion in secure preferences.
- Components involved: Splash screen/viewmodel, onboarding screen/viewmodel, secure preferences, navigation graph.
- Related files: `SplashScreen.kt`, `SplashViewModel.kt`, `OnboardingScreen.kt`, `OnboardingViewModel.kt`, `SecurePrefs.kt`, `NavGraph.kt`.
- Dependencies: SecurePrefs, AuthManager, Compose.
- User workflow: New users see onboarding, then authentication. Returning onboarded users go to auth or home depending on session. Edge cases include missing/corrupt secure prefs and guest mode.
- Current status: Fully Implemented.
- Completion percentage: 95%.
- Test coverage: Partially Tested through viewmodel tests, preference behavior, and `MainActivityNavigationSmokeTest` first-run onboarding-to-auth coverage. Debug builds use `com.aistudio.relateai.qxtjrk.debug` for side-by-side validation. Connected execution on device `1b87b5db` installed and started the debug package, but the smoke stalled at 0/2 tests while another app was foregrounded; live validation requires an idle, unlocked device.
- Confidence score: 95%.

### F-003 Authentication, Guest Mode, Session State

- Category: Core Feature.
- Description: Supports Google sign-in with Firebase auth plus a development/guest path for local demo use.
- Functionality: Starts Google sign-in, requests contacts scope, converts Google credential to Firebase auth, tracks Firebase UID, stores guest mode when bypassing sign-in, and exposes sign-out.
- Components involved: Auth screen/viewmodel, AuthManager, SecurePrefs, Firebase Auth, Google Sign-In.
- Related files: `AuthScreen.kt`, `AuthViewModel.kt`, `AuthManager.kt`, `SecurePrefs.kt`, `google-services.json`, `AndroidManifest.xml`.
- Dependencies: Firebase Auth, Play Services Auth, Google People API scope, encrypted prefs.
- User workflow: User signs in with Google or uses guest mode where available. Auth errors surface as structured failure categories. Edge cases include developer console misconfiguration, network errors, Firebase errors, and missing web client id.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by `AuthViewModelTest`, config tests, and `MainActivityNavigationSmokeTest` auth-action coverage. Live OAuth still needs device/integration validation and credentials.
- Confidence score: 95%.

### F-004 Settings and Secure Configuration

- Category: UI/System Feature.
- Description: Lets users configure AI, Gmail, automation mode, channel blackout, quiet hours, biometric toggle, birthday reminder toggle, sync, legacy DB notices, and sign-out.
- Functionality: Loads and saves Gemini API key, Gmail sender credentials, global automation mode, quiet hours, channel blackout JSON, AI generation enabled, biometric setting, birthday reminders, and force sync action. Saved settings are wired into biometric app locking, automation scheduling, event reminders, dispatch channel blackout, contact sync, and SMTP test/send flows.
- Components involved: Settings screen/viewmodel, SecurePrefs, SyncContactsUseCase, AuthManager.
- Related files: `SettingsScreen.kt`, `SettingsViewModel.kt`, `SecurePrefs.kt`, `AuthManager.kt`, `SyncContactsUseCase.kt`, `strings.xml`, `values-hi/strings.xml`.
- Dependencies: Encrypted preferences, contact sync service, auth manager, localization resources.
- User workflow: User opens Settings, edits credentials/toggles, saves settings, triggers sync, or signs out. Edge cases include invalid quiet-hour values, missing Gmail credentials, and sign-out cleanup.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by `SettingsViewModelTest`, `BiometricLockPolicyTest`, automation scheduling/dispatch tests, localization regressions, and no-hardcoded-strings regression. Live device system-setting handoffs remain tracked in `docs/UI_VALIDATION.md`.
- Confidence score: 95%.

### F-005 Home Dashboard and Relationship Planner

- Category: UI Feature.
- Description: Provides a summary landing screen for upcoming events, relationship health, pending work, and setup readiness.
- Functionality: Loads dashboard metrics, profile info, 30-day upcoming birthdays/events, sync errors, readiness summary, pending approvals, at-risk contacts, and suggested next actions.
- Components involved: Home screen/viewmodel, GetDashboardMetricsUseCase, repositories, SyncErrorCard.
- Related files: `HomeScreen.kt`, `HomeViewModel.kt`, `GetDashboardMetricsUseCase.kt`, `ContactRepository.kt`, `EventRepository.kt`, `MessageRepository.kt`, `SyncErrorCard.kt`.
- Dependencies: Contact/event/message data, auth state, sync use case, analytics aggregates.
- User workflow: User sees relationship status after entering Home, can retry or dismiss sync errors, and can navigate to contacts/events/messages. Edge cases include first-run empty data and sync failures.
- Current status: Fully Implemented.
- Completion percentage: 95%.
- Test coverage: Partially Tested by `HomeViewModelTest` and `HomeScreenInteractionTest`; the Compose smoke covers dashboard cards, settings/readiness/quick-action/planner navigation, and sync-error retry/dismiss controls. Live device visual validation remains tracked in `docs/UI_VALIDATION.md`.
- Confidence score: 95%.

### F-006 Contact Sync, Import, and Deduplication

- Category: Core Feature.
- Description: Imports contacts from Google People API and Android ContactsProvider, then merges and persists them locally.
- Functionality: Foreground and background sync use the same `SyncContactsUseCase` path to fetch Google and device contacts, deduplicate by phone/email/name, prefer Google identity, fill gaps from device contacts, normalize semantic contact groups to relationship types, clear mock contacts when not guest, seed mock contacts in guest mode when needed, and run event discovery.
- Components involved: SyncContactsUseCase, GoogleContactsSync, DeviceContactsReader, ContactRepository, EventRepository, ContactSyncWorker.
- Related files: `SyncContactsUseCase.kt`, `GoogleContactsSync.kt`, `DeviceContactsReader.kt`, `ContactSyncServiceImpl.kt`, `ContactSyncWorker.kt`, `ContactEntity.kt`, `ContactDao.kt`.
- Dependencies: Google People API, ContactsProvider, READ_CONTACTS permission, Firebase/Google auth, Room, SecurePrefs sync token.
- User workflow: User signs in and syncs contacts, or uses guest mode with seeded demo contacts. Edge cases include permission denial, Google API failures, no contacts, duplicate identity, and stale mock data.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by `SyncContactsUseCaseTest` and `ContactSyncWorkerTest`; live People API and ContactsProvider require device/integration validation.
- Confidence score: 95%.

### F-007 Contact List Search, Filter, Sort

- Category: UI Feature.
- Description: Lets users inspect and segment their relationship graph.
- Functionality: Lists contacts, supports search, filters such as family/friends/work/close friends/needs personalization, sorts by name or health score, and triggers refresh sync.
- Components involved: ContactListScreen, ContactListViewModel, ContactRepository, ContactDao.
- Related files: `ContactListScreen.kt`, `ContactListViewModel.kt`, `ContactRepositoryImpl.kt`, `ContactDao.kt`, `ContactEntity.kt`.
- Dependencies: Room contact data, paging support, sync use case, Compose.
- User workflow: User opens Contacts, filters/searches/sorts, selects a contact, or refreshes sync. Edge cases include empty contacts and sync error display.
- Current status: Fully Implemented.
- Completion percentage: 95%.
- Test coverage: Partially Tested by `ContactListViewModelTest` and `ContactListScreenInteractionTest`; the Compose/Robolectric smoke covers search input, clear search, filter chips, sort chips, sync-error retry/dismiss refresh controls, and contact row navigation. Live seeded-device visual validation remains tracked in `docs/UI_VALIDATION.md`.
- Confidence score: 95%.

### F-008 Contact Detail Personalization

- Category: Core Feature.
- Description: Stores per-contact personalization used by AI generation, scheduling, and delivery.
- Functionality: Loads contact and upcoming event, edits nickname, relationship type, language, preferred channel, formality, style, automation mode, gift budgets, skip-auto-wish, custom send time, interests, sensitive topics, life phase, and notes.
- Components involved: ContactDetailScreen, ContactDetailViewModel, UpdateContactPreferencesUseCase, contact repository, event repository.
- Related files: `ContactDetailScreen.kt`, `ContactDetailViewModel.kt`, `UpdateContactPreferencesUseCase.kt`, `ContactEntity.kt`, `ContactDao.kt`.
- Dependencies: Contact schema fields, domain value parsers, Compose forms, repositories.
- User workflow: User opens a contact, updates preferences, and generates a wish from the contact. Edge cases include invalid typed values, missing event, and archived/deleted contacts.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by `ContactDetailViewModelTest` and domain value parsing tests.
- Confidence score: 95%.

### F-009 Event Discovery

- Category: Backend Feature.
- Description: Finds and normalizes birthdays, anniversaries, and work anniversaries from contact data.
- Functionality: Creates stable events from contact date fields, calculates next occurrence, computes days until event and age turning where available, handles leap-day behavior, deactivates missing background-discovered events, and distinguishes source/confidence/verification.
- Components involved: DiscoverEventsUseCase, EventDiscoveryWorker, EventRepository, EventDao, EventEntity.
- Related files: `DiscoverEventsUseCase.kt`, `EventDiscoveryWorker.kt`, `EventEntity.kt`, `EventDao.kt`, `EventRepositoryImpl.kt`.
- Dependencies: Contact data, date/time APIs, Room, WorkManager.
- User workflow: User syncs contacts or daily workers run; events appear in Home and Events. Edge cases include Feb 29, missing years, inactive old events, duplicate event ids, and custom/manual events.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by `DiscoverEventsUseCaseTest` and `EventDiscoveryWorkerTest`.
- Confidence score: 95%.

### F-010 Manual and Custom Event Creation

- Category: Core Feature.
- Description: Lets users add events that are missing from imported contacts.
- Functionality: Saves manual events for existing or new contacts, validates date input including Feb 29, supports birthday/anniversary/work/custom-style event types present in the domain model, updates contact date fields for primary event types, and logs activity.
- Components involved: EventsScreen, EventsViewModel, SaveManualEventUseCase, EventRepository, ContactRepository.
- Related files: `EventsScreen.kt`, `EventsViewModel.kt`, `SaveManualEventUseCase.kt`, `EventEntity.kt`, `ContactEntity.kt`, `ActivityLogEntity.kt`.
- Dependencies: Contact/event repositories, date validation, activity log repository.
- User workflow: User opens Events, adds a manual event, selects or creates contact details, saves, and sees it in event lists. Edge cases include invalid dates, duplicated event data, and new-contact fallback.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by `SaveManualEventUseCaseTest` and `EventsViewModelTest`.
- Confidence score: 90%.

### F-011 Messages Inbox and Bulk Actions

- Category: UI Feature.
- Description: Central queue for pending, approved, sent, and failed relationship messages.
- Functionality: Groups messages into today/upcoming/pending/approved/sent/failed buckets, supports search, channel filter, sorting, approve/reject/retry/revoke, bulk approve, bulk reject, bulk retry, activity logging, and navigation to wish preview.
- Components involved: MessagesScreen, MessagesViewModel, message repository, approval/rejection/regeneration use cases.
- Related files: `MessagesScreen.kt`, `MessagesViewModel.kt`, `PendingMessageEntity.kt`, `SentMessageEntity.kt`, `ApprovePendingMessageUseCase.kt`, `RejectPendingMessageUseCase.kt`, `RegeneratePendingMessageUseCase.kt`, `RevokeApprovalUseCase.kt`.
- Dependencies: Pending/sent message DAOs, scheduler service, notification service, activity log repository.
- User workflow: User reviews queued messages, filters, opens preview, approves or rejects one or many. Edge cases include stale pending messages, failed retries, rejected messages, and legacy event-id navigation.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `MessagesViewModelTest` and approval/rejection use-case tests.
- Confidence score: 90%.

### F-012 Wish Preview, Editing, Feedback, Regeneration

- Category: Core Feature.
- Description: Gives users final control over AI-generated message variants before delivery.
- Functionality: Loads pending message by id or legacy event id, displays six variants, supports variant selection, direct text edit, approve, reject, test-send-to-self, regenerate, feedback options, why-signals, fallback disclosure, and message feedback persistence.
- Components involved: WishPreviewScreen, WishPreviewViewModel, pending message repository, feedback repository, approval/reject/regenerate/test-send use cases.
- Related files: `WishPreviewScreen.kt`, `WishPreviewViewModel.kt`, `PendingMessageEntity.kt`, `MessageFeedbackEntity.kt`, `ApprovePendingMessageUseCase.kt`, `RejectPendingMessageUseCase.kt`, `RegeneratePendingMessageUseCase.kt`, `TestSendUseCase.kt`.
- Dependencies: AI service, scheduler service, notification service, message feedback repository, activity logging.
- User workflow: User opens preview from notification, Messages, or deep link; selects/edits a variant; sends a test email if configured; approves or rejects. Edge cases include missing pending message, fallback AI text, wrong language feedback, and already-sent messages.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by `WishPreviewViewModelTest`, regeneration tests, and test-send tests.
- Confidence score: 95%.

### F-013 Chat History

- Category: UI Feature.
- Description: Shows previous sent messages for a contact.
- Functionality: Loads sent messages by contact, presents history/error/empty states, and supports navigation back to contact context.
- Components involved: ChatHistoryScreen, ChatHistoryViewModel, MessageRepository, SentMessageDao.
- Related files: `ChatHistoryScreen.kt`, `ChatHistoryViewModel.kt`, `SentMessageEntity.kt`, `SentMessageDao.kt`, `MessageRepositoryImpl.kt`.
- Dependencies: Sent message persistence and contact id routing.
- User workflow: User opens a contact's chat history to see prior wishes and delivery results. Edge cases include deleted contacts and empty history.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `ChatHistoryViewModelTest`.
- Confidence score: 90%.

### F-014 Analytics and CSV Export

- Category: UI/Backend Feature.
- Description: Reports relationship and delivery performance from local data.
- Functionality: Calculates wishes sent, contact totals, pending approvals, upcoming events, relationship counts, health buckets, monthly counts, delivery reliability, response rate, personalization coverage, neglected contacts, and relationship CSV export.
- Components involved: AnalyticsScreen, AnalyticsViewModel, GetAnalyticsUseCase, AnalyticsReportServiceImpl, DAOs.
- Related files: `AnalyticsScreen.kt`, `AnalyticsViewModel.kt`, `GetAnalyticsUseCase.kt`, `AnalyticsReportService.kt`, `AnalyticsReportServiceImpl.kt`, `ContactDao.kt`, `EventDao.kt`, `SentMessageDao.kt`.
- Dependencies: Room aggregate queries, sent message state, contact health data, local file/share behavior for export.
- User workflow: User opens Analytics to inspect trends or export relationship data. Edge cases include empty data and insufficient sent history.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `GetAnalyticsUseCaseTest`, `AnalyticsViewModelTest`, and `AnalyticsReportServiceImplTest`.
- Confidence score: 90%.

### F-015 Activity History and Audit Log

- Category: System Feature.
- Description: Records and displays important app actions for traceability and troubleshooting.
- Functionality: Persists activity logs, filters by type/date/status, searches recent entries, and supports resolved/open status views.
- Components involved: ActivityHistoryScreen, ActivityHistoryViewModel, ActivityLogRepository, ActivityLogDao.
- Related files: `ActivityHistoryScreen.kt`, `ActivityHistoryViewModel.kt`, `ActivityLogEntity.kt`, `ActivityLogDao.kt`, `ActivityLogRepositoryImpl.kt`.
- Dependencies: Room, repositories, domain activity model.
- User workflow: User or maintainer opens Activity History to inspect sync, message, event, settings, analytics, and AI activity. Edge cases include large logs and missing contact references.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `ActivityHistoryViewModelTest` and `ActivityLogRepositoryImplTest`.
- Confidence score: 90%.

### F-016 Style Coach

- Category: AI Feature.
- Description: Learns the user's writing patterns from pasted samples or recent sent messages.
- Functionality: Accepts manual writing samples, analyzes recent sent messages, calculates average length, emoji density, language cues, bigrams, greetings, closings, tone descriptors, formality, and writes current style plus history.
- Components involved: StyleCoachScreen, StyleCoachViewModel, StyleAnalysisUseCase, StyleAnalysisWorker, StyleProfileRepository.
- Related files: `StyleCoachScreen.kt`, `StyleCoachViewModel.kt`, `StyleAnalysisUseCase.kt`, `StyleAnalysisWorker.kt`, `StyleProfileEntity.kt`, `StyleProfileHistoryEntity.kt`, `StyleProfileDao.kt`.
- Dependencies: Sent messages, AI prompt context, Room, WorkManager.
- User workflow: User trains style manually or lets background analysis run. Generated wishes then use style profile context. Edge cases include too few samples and mixed-language messages.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `StyleCoachViewModelTest`, `StyleAnalysisUseCaseTest`, and `StyleAnalysisWorkerTest`.
- Confidence score: 90%.

### F-017 Memory Vault

- Category: Core Feature.
- Description: Stores personal notes, preferences, milestones, and memories per contact for future personalization.
- Functionality: Creates, lists, pins/unpins, and deletes contact memory notes with categories such as general, preference, event, gift, and milestone. Notes are capped by UI validation.
- Components involved: MemoryVaultScreen, MemoryVaultViewModel, MemoryNoteRepository, MemoryNoteDao.
- Related files: `MemoryVaultScreen.kt`, `MemoryVaultViewModel.kt`, `MemoryNoteEntity.kt`, `MemoryNoteDao.kt`, `MemoryNoteRepositoryImpl.kt`.
- Dependencies: Contact id routing, Room, AI prompt builder context.
- User workflow: User opens Memory Vault from a contact, adds or pins context, and future AI prompts reference memories. Edge cases include empty notes, note length limits, and deleted contacts.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `MemoryVaultViewModelTest`.
- Confidence score: 90%.

### F-018 Gift Advisor

- Category: AI/UI Feature.
- Description: Tracks gift history and requests AI gift ideas constrained by contact context and budget.
- Functionality: Adds and deletes gift records, shows yearly budget stats, calculates remaining budget, calls AI for suggestions, filters suggestions by budget, and validates gift cost input.
- Components involved: GiftAdvisorScreen, GiftAdvisorViewModel, GiftHistoryRepository, AiService.
- Related files: `GiftAdvisorScreen.kt`, `GiftAdvisorViewModel.kt`, `GiftHistoryEntity.kt`, `GiftHistoryDao.kt`, `GiftHistoryRepositoryImpl.kt`, `AiService.kt`, `AiServiceImpl.kt`.
- Dependencies: Contact preferences, gift history, Gemini prompt/response parsing, Room.
- User workflow: User opens Gift Advisor for a contact, records prior gifts, sets price/outcome, requests ideas, and reviews budget stats. Edge cases include invalid cost, no AI key, empty suggestions, and budget overflow.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `GiftAdvisorViewModelTest`; live AI suggestion quality needs integration validation.
- Confidence score: 90%.

### F-019 Encrypted Backup and Restore

- Category: System Feature.
- Description: Exports and imports local relationship data using passphrase-based encrypted backup.
- Functionality: Serializes supported local entities, encrypts backup payload with AES-GCM, validates passphrase strength, imports data, and tracks backup reminder timing.
- Components involved: BackupRestoreScreen, BackupRestoreViewModel, BackupServiceImpl, BackupEncryption, repositories, SecurePrefs.
- Related files: `BackupRestoreScreen.kt`, `BackupRestoreViewModel.kt`, `BackupService.kt`, `BackupServiceImpl.kt`, `BackupEncryption.kt`, `DailyTriggerWorker.kt`, `SecurePrefs.kt`.
- Dependencies: Room repositories, Android file/document APIs, cryptography, passphrase input.
- User workflow: User chooses a passphrase, exports backup, later imports with the same passphrase. Edge cases include wrong passphrase, corrupt file, missing OAuth/API secrets, and large datasets.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by `BackupRestoreViewModelTest`, `BackupServiceImplTest`, and `BackupEncryptionTest`.
- Confidence score: 90%.

### F-020 Automation Setup / AI Doctor

- Category: System Feature.
- Description: Provides an operational readiness checklist for automation and integrations.
- Functionality: Checks Google contacts auth, Gemini readiness, AI generation toggle, style profile, personalization, circuit breaker, notifications, SMS readiness, email readiness, WhatsApp accessibility, exact alarms, daily workers, recent errors, and dead-letter queue. It also exposes actions to settings, contacts, style coach, activity history, accessibility settings, battery/app settings, sync, dry-run generation, AI test, and email test.
- Components involved: AutomationSetupScreen, AutomationSetupViewModel, HealthMonitor, DeadLetterQueue, SecurePrefs, workers/scheduler, TestSendUseCase.
- Related files: `AutomationSetupScreen.kt`, `AutomationSetupViewModel.kt`, `HealthMonitor.kt`, `DeadLetterQueue.kt`, `WorkerScheduler.kt`, `DailyScheduler.kt`, `TestSendUseCase.kt`, `TestSendServiceImpl.kt`.
- Dependencies: System settings, notification permission, exact alarm permission, integrations, local health state.
- User workflow: User opens setup, resolves failed checks, runs diagnostics, and follows actions into system settings or app screens. Edge cases include unavailable system capabilities and disabled integrations.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `AutomationSetupViewModelTest`; system-setting handoffs require device validation.
- Confidence score: 90%.

### F-021 Room Database, Schema, and Migrations

- Category: Backend Feature.
- Description: Stores all local app data in an encrypted Room database with schema migrations through version 13.
- Functionality: Defines entities for contacts, events, pending messages, sent messages, style profiles, style profile history, memory notes, gift history, activity logs, and message feedback. Applies migrations, exports schema JSON, uses SQLCipher SupportFactory, and avoids destructive fallback.
- Components involved: AppDatabase, entity classes, DAOs, repository implementations, SQLCipher key derivation.
- Related files: `AppDatabase.kt`, `ContactEntity.kt`, `EventEntity.kt`, `PendingMessageEntity.kt`, `SentMessageEntity.kt`, `StyleProfileEntity.kt`, `StyleProfileHistoryEntity.kt`, `MemoryNoteEntity.kt`, `GiftHistoryEntity.kt`, `ActivityLogEntity.kt`, `MessageFeedbackEntity.kt`, `core/data/schemas/com.example.core.db.AppDatabase/13.json`.
- Dependencies: Room 2.7.0, SQLCipher 4.5.4, AndroidX SQLite, Moshi for structured JSON fields.
- User workflow: Invisible to users; all features read/write local state through repositories. Edge cases include migration from older versions, plaintext legacy DB quarantine, and deleted contact references.
- Current status: Fully Implemented.
- Completion percentage: 95%.
- Test coverage: Partially Tested by DAO, migration, database key, and quarantine tests.
- Confidence score: 98%.

### F-022 AI Contact Classification

- Category: AI Feature.
- Description: Infers relationship metadata for imported contacts that have unknown classification.
- Functionality: Builds classification prompt, calls Gemini, parses relationship type/subtype, language, formality, and communication style, then updates contact metadata.
- Components involved: ClassifyContactUseCase, AiServiceImpl, GeminiClient, PromptBuilder, ResponseParser, ContactRepository.
- Related files: `ClassifyContactUseCase.kt`, `AiService.kt`, `AiServiceImpl.kt`, `GeminiClient.kt`, `PromptBuilder.kt`, `ResponseParser.kt`, `ContactEntity.kt`.
- Dependencies: Gemini API path, contact context, rate limiter, circuit breaker, SecurePrefs/Firebase user state.
- User workflow: Runs after sync/background classification where enabled; user benefits through better defaults. Edge cases include AI disabled, invalid AI response, quota/rate failures, and already-classified contacts.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `ClassifyContactUseCaseTest`, prompt/parser tests, and resilience tests.
- Confidence score: 90%.

### F-023 AI Message Generation and Fallback

- Category: AI Feature.
- Description: Generates six personalized message variants for a contact event.
- Functionality: Loads contact/event/style/history/memory/gift context, respects AI enabled state, calls Gemini, retries anti-repetition when generated text is too similar to previous wishes, chooses approval mode, persists pending message, schedules send or shows approval notification, and falls back to template variants on AI failure.
- Components involved: GenerateMessageUseCase, MessageGenerationWorker, AiServiceImpl, GeminiClient, PromptBuilder, ResponseParser, MessageRepository.
- Related files: `GenerateMessageUseCase.kt`, `MessageGenerationWorker.kt`, `AiServiceImpl.kt`, `GeminiClient.kt`, `PromptBuilder.kt`, `ResponseParser.kt`, `PendingMessageEntity.kt`, `MessageRepositoryImpl.kt`.
- Dependencies: Gemini, rate limiter, circuit breaker, contact/event/style/memory/gift repositories, SchedulerService, NotificationService.
- User workflow: User requests a wish or automation generates pending wishes before upcoming events. Edge cases include missing contact/event, no AI credentials, fallback template, duplicate scheduled year, and context not found.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by `GenerateMessageUseCaseTest`, `MessageGenerationWorkerTest`, prompt/parser tests.
- Confidence score: 95%.

### F-024 Approval Lifecycle

- Category: Core Feature.
- Description: Controls whether generated messages can be sent automatically.
- Functionality: Supports approve, reject, revoke approval, smart approval, VIP approval, edited text persistence, selected variant persistence, notification actions, and exact-send scheduling after approval.
- Components involved: ApprovePendingMessageUseCase, RejectPendingMessageUseCase, RevokeApprovalUseCase, ApprovalReceiver, Messages/Wish UI.
- Related files: `ApprovePendingMessageUseCase.kt`, `RejectPendingMessageUseCase.kt`, `RevokeApprovalUseCase.kt`, `ApprovalReceiver.kt`, `NotificationHelper.kt`, `PendingMessageEntity.kt`, `MessagesViewModel.kt`, `WishPreviewViewModel.kt`.
- Dependencies: Pending message DAO, scheduler service, notification service, WorkManager dispatch fallback.
- User workflow: User approves from UI or notification, rejects messages, revokes approvals before send, or lets eligible smart-auto messages dispatch. Edge cases include expired VIP approval, already sent/failed messages, and edited text.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by approval/rejection/revoke-related use-case and viewmodel tests; notification action flow needs device validation.
- Confidence score: 95%.

### F-025 Exact Scheduling and Boot Recovery

- Category: Backend Feature.
- Description: Schedules approved messages for exact delivery and recovers schedules after reboot.
- Functionality: Uses AlarmManager exact/alarm-clock scheduling by pending id, enqueues WorkManager fallback when due now or exact alarms are unavailable, cancels scheduled sends, and boot receiver reschedules approved pending messages plus periodic work.
- Components involved: DailyScheduler, MessageDispatchReceiver, BootReceiver, MessageDispatchWorkRequests, WorkerScheduler.
- Related files: `DailyScheduler.kt`, `SchedulerServiceImpl.kt`, `MessageDispatchWorkRequests.kt`, `MessageDispatchWorker.kt`, `WorkerScheduler.kt`, `AndroidManifest.xml`.
- Dependencies: AlarmManager, WorkManager, exact alarm permission, pending message repository, notification setup alerts.
- User workflow: User approves a message; the app schedules it for custom/default send time; reboot restores schedules. Edge cases include exact-alarm denial, due-now sends, legacy event-id cancellation, and device reboot.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by worker/scheduler-related tests; real exact alarms and reboot behavior need device smoke testing.
- Confidence score: 90%.

### F-026 WorkManager Automation Chain

- Category: Backend Feature.
- Description: Runs background automation for sync, event discovery, message generation, revival, and style analysis.
- Functionality: Schedules daily trigger, chains ContactSyncWorker through the shared foreground sync path to EventDiscoveryWorker and MessageGenerationWorker, reschedules event reminders, schedules weekly revival and biweekly style analysis, applies constraints/backoff, and avoids scheduling in Robolectric app startup.
- Components involved: WorkerScheduler, DailyTriggerWorker, ContactSyncWorker, EventDiscoveryWorker, MessageGenerationWorker, RevivalWorker, StyleAnalysisWorker.
- Related files: `WorkerScheduler.kt`, `DailyTriggerWorker.kt`, `ContactSyncWorker.kt`, `EventDiscoveryWorker.kt`, `MessageGenerationWorker.kt`, `RevivalWorker.kt`, `StyleAnalysisWorker.kt`, `RelateAIApp.kt`.
- Dependencies: WorkManager, Hilt Worker injection, auth/prefs, DAOs, AI service.
- User workflow: Mostly invisible; automation updates contacts/events/messages and suggestions. Edge cases include missing auth, AI disabled, no API key/Firebase user, worker failures, and retry backoff.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by worker tests and `AutomationPipelineTest`.
- Confidence score: 95%.

### F-027 Dispatch Orchestration

- Category: Backend Feature.
- Description: Selects the correct channel sender and updates message state atomically around delivery.
- Functionality: Resolves edited or selected message text, enforces approved-only dispatch, handles pending id and legacy event id lookup, uses channel blackout data, delegates SMS/WhatsApp/email, inserts sent message records, updates pending status, records failures to health/dead-letter systems, and updates contact health/consecutive years.
- Components involved: DispatchMessageUseCase, MessageDispatchWorker, MessageDispatcher, MessageDispatcherServiceImpl.
- Related files: `DispatchMessageUseCase.kt`, `MessageDispatchWorker.kt`, `MessageDispatcher.kt`, `MessageDispatcherServiceImpl.kt`, `PendingMessageEntity.kt`, `SentMessageEntity.kt`.
- Dependencies: Message repositories, contact repository, channel senders, SecurePrefs channel blackout, HealthMonitor, DeadLetterQueue.
- User workflow: User approves a pending message; scheduler/worker dispatches when due. Edge cases include rejected pending, failed sender, contact deleted, duplicate sent guard, and channel blackout.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `DispatchMessageUseCaseTest`, `MessageDispatchWorkerTest`, and dispatch request tests.
- Confidence score: 90%.

### F-028 SMS Delivery and Status Callbacks

- Category: Integration Feature.
- Description: Sends text messages using Android SMS APIs and tracks sent/delivered callbacks.
- Functionality: Sends multipart SMS with per-message PendingIntents, records pending delivery, receives `SMS_SENT` and `SMS_DELIVERED`, and updates delivery status.
- Components involved: SmsSender, SmsStatusReceiver, MessageDispatcher, SentMessageDao.
- Related files: `SmsSender.kt`, `SmsStatusReceiver.kt`, `MessageDispatcher.kt`, `SentMessageEntity.kt`, `AndroidManifest.xml`.
- Dependencies: SEND_SMS permission, telephony feature when available, Android SMS Manager, broadcast receivers.
- User workflow: User approves an SMS message; app sends and later updates status. Edge cases include permission denial, no telephony hardware, multipart failure, and delivery callback absence.
- Current status: Fully Implemented.
- Completion percentage: 80%.
- Test coverage: Partially Tested by `SmsStatusReceiverTest`; real carrier delivery requires device validation.
- Confidence score: 85%.

### F-029 WhatsApp Accessibility Delivery

- Category: Integration Feature.
- Description: Automates WhatsApp message sending through an Accessibility Service.
- Functionality: Queues WhatsApp send jobs, opens wa.me link, waits for WhatsApp UI, types exact text, clicks send, verifies send button disappearance, times out after failure, rejects while device is locked, and supports WhatsApp and WhatsApp Business packages.
- Components involved: WhatsAppSender, WhatsAppAccessibilityService, MessageDispatcher.
- Related files: `WhatsAppSender.kt`, `WhatsAppAccessibilityService.kt`, `MessageDispatcher.kt`, `accessibility_service_config.xml`, `AndroidManifest.xml`.
- Dependencies: Accessibility permission, WhatsApp installed/configured, device unlocked, contact phone number, Android UI automation.
- User workflow: User enables accessibility service; approved WhatsApp messages are sent by opening WhatsApp and automating UI. Edge cases include changed WhatsApp UI, locked screen, missing service instance, invalid number, and timeout.
- Current status: Fully Implemented.
- Completion percentage: 75%.
- Test coverage: Not Tested in local unit reports; must be validated manually on device.
- Confidence score: 80%.

### F-030 Gmail SMTP Delivery and Test Send

- Category: Integration Feature.
- Description: Sends email wishes through Gmail SMTP and supports test send to self.
- Functionality: Stores sender email/password, validates configuration, sends SMTP email through `smtp.gmail.com:587`, builds event-aware subjects for birthdays, anniversaries, work anniversaries, custom events, and test sends, exposes a test-send use case, and reports readiness in AI Doctor.
- Components involved: EmailSender, EmailSubjectBuilder, TestSendServiceImpl, TestSendUseCase, Settings, Wish Preview, MessageDispatcher.
- Related files: `EmailSender.kt`, `EmailSubjectBuilder.kt`, `TestSendServiceImpl.kt`, `TestSendUseCase.kt`, `SettingsViewModel.kt`, `WishPreviewViewModel.kt`, `MessageDispatcher.kt`.
- Dependencies: JavaMail Android, Gmail app password or SMTP credentials, internet permission, SecurePrefs.
- User workflow: User enters Gmail settings, sends a test email, and approves email-channel wishes. Edge cases include invalid email, wrong password, network failure, and Gmail security restrictions.
- Current status: Fully Implemented.
- Completion percentage: 80%.
- Test coverage: Partially Tested by `EmailSubjectBuilderTest`, `TestSendUseCaseTest`, settings and wish preview tests. Live SMTP must be validated with real credentials.
- Confidence score: 85%.

### F-031 Notifications and Action Receivers

- Category: System Feature.
- Description: Informs users and exposes actions for approvals, revivals, event reminders, setup/system alerts, and dispatch status.
- Functionality: Creates notification channels, shows approval notifications with approve/reject/edit, revival notifications, system/setup notifications, event reminders, AI fallback alerts, and dispatch-related notifications. Receivers process approval and reminder actions.
- Components involved: NotificationHelper, NotificationServiceImpl, ApprovalReceiver, EventReminderReceiver, RelateAIApp.
- Related files: `NotificationHelper.kt`, `NotificationServiceImpl.kt`, `ApprovalReceiver.kt`, `EventReminderReceiver.kt`, `RelateAIApp.kt`, `AndroidManifest.xml`.
- Dependencies: Notification permission on modern Android, PendingIntents, Hilt entrypoints, message/event/contact repositories.
- User workflow: User receives actionable notification, approves/rejects/edits or opens relevant app screen. Edge cases include notification permission denial, stale pending ids, and missing contact/event.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by notification hardcoded-string regression and related receiver/use-case tests; notification UX needs device validation.
- Confidence score: 90%.

### F-032 Revival Suggestions

- Category: AI/System Feature.
- Description: Suggests reconnection messages for relationships with low health.
- Functionality: Finds contacts with low health and stale revival attempts, asks AI for reconnect text, creates VIP approval pending messages scheduled soon, updates last revival attempt, and shows revival notification.
- Components involved: RevivalWorker, AiService, NotificationHelper, MessageRepository, ContactRepository.
- Related files: `RevivalWorker.kt`, `AiServiceImpl.kt`, `PromptBuilder.kt`, `PendingMessageEntity.kt`, `NotificationHelper.kt`, `ContactEntity.kt`.
- Dependencies: Health score, AI enabled/auth/key state, WorkManager, pending message storage.
- User workflow: User receives a revival suggestion and approves/edits/rejects it like any other pending wish. Edge cases include no AI credentials, AI fallback, recently attempted contacts, and invalid AI response.
- Current status: Fully Implemented.
- Completion percentage: 80%.
- Test coverage: Partially Tested by `RevivalWorkerTest`; real notification and AI behavior require integration validation.
- Confidence score: 90%.

### F-033 Relationship Health Scoring

- Category: Backend Feature.
- Description: Scores relationship freshness to drive dashboards, filters, analytics, and revival suggestions.
- Functionality: Calculates score from base score, message frequency, recency, consecutive years, stale/no-wish penalties, and clamps to 0..100. Dispatch success increases health.
- Components involved: RefreshHealthScoresUseCase, ContactRepository, MessageRepository, analytics/dashboard use cases, MessageDispatcher.
- Related files: `RefreshHealthScoresUseCase.kt`, `ContactEntity.kt`, `ContactRepositoryImpl.kt`, `MessageDispatcher.kt`, `GetDashboardMetricsUseCase.kt`, `GetAnalyticsUseCase.kt`.
- Dependencies: Sent message history, contact last wished dates, consecutive year counters.
- User workflow: User sees health buckets, neglected contacts, needs-personalization filters, and revival suggestions. Edge cases include new contacts and deleted/archived contacts.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `RefreshHealthScoresUseCaseTest`.
- Confidence score: 90%.

### F-034 Widget, Deep Links, Shortcuts

- Category: UI/System Feature.
- Description: Extends app entry points beyond the main navigation shell.
- Functionality: Home-screen widget shows birthdays/upcoming events/pending approvals, deep links route to wish/contact/settings flows, and dynamic shortcuts expose compose-message and contacts entry points.
- Components involved: BirthdayWidgetProvider, Android manifest, shortcuts XML, navigation route decoding.
- Related files: `BirthdayWidgetProvider.kt`, `widget_birthday.xml`, `widget_birthday_info.xml`, `shortcuts.xml`, `AndroidManifest.xml`, `NavGraph.kt`, `RouteArgumentCodec.kt`.
- Dependencies: AppWidgetManager, Room access from receiver, PendingIntents, manifest deep links.
- User workflow: User taps widget or shortcut/deep link to open relevant app context. Edge cases include deleted contact ids, no upcoming events, and app process cold start.
- Current status: Fully Implemented.
- Completion percentage: 80%.
- Test coverage: Not Tested in observed unit reports except route codec; widget and shortcuts need device validation.
- Confidence score: 85%.

### F-035 Security, Privacy, and Local Encryption

- Category: System Feature.
- Description: Protects local data and reduces accidental sensitive leakage.
- Functionality: Uses SQLCipher for Room, derives/caches database key, stores secrets in encrypted preferences, disables Android backup for sensitive local data, quarantines legacy plaintext DB, configures network security/certificate pins, redacts logs, and keeps custom backend out of architecture.
- Components involved: AppDatabase, DatabaseKeyDerivation, LegacyDatabaseQuarantine, SecurePrefs, SecurityChecks, network/backup XML, SensitiveLogRedactor.
- Related files: `AppDatabase.kt`, `DatabaseKeyDerivation.kt`, `LegacyDatabaseQuarantine.kt`, `SecurePrefs.kt`, `SecurityChecks.kt`, `SensitiveLogRedactor.kt`, `network_security_config.xml`, `backup_rules.xml`, `data_extraction_rules.xml`.
- Dependencies: SQLCipher, AndroidX Security Crypto, Android keystore/master key, network security config.
- User workflow: Mostly invisible; users benefit through encrypted local storage, safe sign-out, and backup exclusion. Edge cases include encrypted prefs initialization failure, legacy DB detection, and certificate pin expiry.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by key derivation, quarantine, sensitive redactor, and production-readiness tests.
- Confidence score: 95%.

### F-036 Sign-out Data Purge

- Category: Security Feature.
- Description: Removes local relationship data and credentials when the user signs out.
- Functionality: Cancels all work, cancels notifications, clears Room tables, closes/resets database, clears encrypted preferences, clears DB key cache, deletes database files, signs out Firebase, and revokes Google access.
- Components involved: AuthManager, AppDatabase, SecurePrefs, WorkManager, NotificationManager, Firebase Auth, Google Sign-In client.
- Related files: `AuthManager.kt`, `AppDatabase.kt`, `SecurePrefs.kt`, `RelateAIApp.kt`, `SettingsViewModel.kt`.
- Dependencies: Auth/session services, database lifecycle, Android notification/work services.
- User workflow: User taps sign out in Settings; app clears local state and returns to auth path. Edge cases include partial cleanup failure, DB open/close race, and Google revoke failure.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested through Auth/Settings tests; destructive local file cleanup needs device/instrumented validation.
- Confidence score: 90%.

### F-037 Resilience, Logging, Health, Dead-Letter Queue

- Category: System Feature.
- Description: Provides operational guardrails around external calls and automation failures.
- Functionality: Implements retry policies, circuit breaker, fallback helpers, sliding-window Gemini rate limiter, structured logging, sensitive redaction, health monitor state, and dead-letter queue for failed dispatch/generation work.
- Components involved: Retry, CircuitBreaker, RateLimiter, HealthMonitor, DeadLetterQueue, StructuredLogger, SensitiveLogRedactor, GeminiClient, MessageDispatcher.
- Related files: `Retry.kt`, `CircuitBreaker.kt`, `Fallback.kt`, `HealthMonitor.kt`, `DeadLetterQueue.kt`, `StructuredLogger.kt`, `SensitiveLogRedactor.kt`, `RateLimiter.kt`, `GeminiClient.kt`, `MessageDispatcher.kt`.
- Dependencies: Coroutines, time APIs, AI and dispatch callers, local diagnostics.
- User workflow: Mostly internal; AI Doctor surfaces recent errors/dead letters and system health. Edge cases include quota exhaustion, repeated Gemini failures, and dispatch failure loops.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by resilience primitive and redaction tests.
- Confidence score: 90%.

### F-038 External API and Service Interfaces

- Category: Integration Feature.
- Description: Defines app boundary interfaces for AI, sync, notifications, dispatch, preferences, scheduling, backup, test send, and analytics reports.
- Functionality: Domain service interfaces isolate implementations; external integrations include Google Sign-In, Firebase Auth, Google People API, Android ContactsProvider, Gemini via Firebase Vertex AI or user API key, Gmail SMTP, SMS Manager, WhatsApp Accessibility, WorkManager, AlarmManager, AppWidgetManager, and notification APIs.
- Components involved: Domain service interfaces and data implementations.
- Related files: `AiService.kt`, `ContactSyncService.kt`, `MessageDispatcherService.kt`, `NotificationService.kt`, `SchedulerService.kt`, `PreferencesRepository.kt`, `BackupService.kt`, `TestSendService.kt`, `AnalyticsReportService.kt`, corresponding `*Impl.kt` files in `core/data`.
- Dependencies: Firebase, Google APIs, Android system services, JavaMail, Room, Hilt.
- User workflow: Users do not call interfaces directly; features use them through use cases/viewmodels. Edge cases map to each external integration's permissions, credentials, network, and device state.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by use-case, worker, backup, analytics, and config tests. Live third-party calls need integration/device validation.
- Confidence score: 95%.

### F-039 Build, CI, Release Guard, Coverage

- Category: Developer Feature.
- Description: Supports local and CI verification, release safety, and coverage reporting.
- Functionality: Configures four Gradle modules, Kotlin/Java toolchains, KSP/Room schema export, Compose, Hilt, baseline profile, aggregate JaCoCo report, GitHub Actions for tests/lint/debug assemble/coverage, artifact upload, and release signing guard requiring `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
- Components involved: Gradle root/app/core build files, version catalog, CI workflow.
- Related files: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `core/data/build.gradle.kts`, `core/domain/build.gradle.kts`, `core/ui/build.gradle.kts`, `.github/workflows/android.yml`.
- Dependencies: AGP 9.2.1, Kotlin 2.2.10, JDK 21 toolchain, JaCoCo 0.8.12, GitHub Actions.
- User workflow: Developers run Gradle tasks locally; CI runs on push/PR to main/master. Edge cases include missing release signing env and local enterprise truststore.
- Current status: Fully Implemented.
- Completion percentage: 90%.
- Test coverage: Partially Tested by `ProductionReadinessConfigTest` and CI config, but CI itself was not executed during this document update.
- Confidence score: 95%.

### F-040 Design System and Localization

- Category: UI/Developer Feature.
- Description: Provides shared Compose UI components, theme tokens, feedback components, and English/Hindi strings.
- Functionality: Defines Relate components, feedback components, shimmer loading, colors, type, theme, localized app strings, app/core-data English-Hindi resource-key parity tests, and regression tests for hardcoded UI/notification strings.
- Components involved: `:core:ui`, app screens, string resources, no-hardcoded-strings test, localization parity test.
- Related files: `RelateComponents.kt`, `FeedbackComponents.kt`, `ShimmerLoading.kt`, `Color.kt`, `Theme.kt`, `Type.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-hi/strings.xml`, `core/data/src/main/res/values/strings.xml`, `core/data/src/main/res/values-hi/strings.xml`, `NoHardcodedStringsRegressionTest.kt`, `LocalizationParityTest.kt`.
- Dependencies: Compose Material3, resource system, localization resources.
- User workflow: Users see consistent UI and localized strings where translations exist. Edge cases include translation quality, which still benefits from human review, and future raw strings introduced in code.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `NoHardcodedStringsRegressionTest` and `LocalizationParityTest`; full localization quality requires manual review.
- Confidence score: 90%.

### F-041 Developer Helper Scripts and Docs

- Category: Developer Feature.
- Description: Provides auxiliary documentation and helper automation outside the app runtime.
- Functionality: Branching docs describe workflow expectations; localization helper script scans for hardcoded `Text("...")` calls from the dynamically detected repository root and excludes build outputs.
- Components involved: Docs and scripts.
- Related files: `docs/BRANCHING.md`, `scripts/extract_strings.sh`, `.kiro/steering/tech.md`, existing audit/docs files.
- Dependencies: Shell environment, `rg`, optional Git checkout metadata, developer discipline.
- User workflow: Developers read docs and optionally run helper script from any checkout path. Edge cases include running outside a Git checkout, which falls back to the current directory.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `HelperScriptsTest`.
- Confidence score: 90%.

### F-042 Biometric App Lock Enforcement

- Category: Security Feature.
- Description: Intended to protect app access with biometric/device credential authentication.
- Functionality: A biometric manager exists, Settings persists a biometric-lock toggle, and `MainActivity` gates app composition on cold start/resume when the toggle is enabled. The lock screen blocks access until biometric or device credential authentication succeeds, with an unavailable state when the device has no supported authenticator.
- Components involved: MainActivity, BiometricLockPolicy, BiometricAuthManager, Settings, SecurePrefs.
- Related files: `MainActivity.kt`, `BiometricLockPolicy.kt`, `BiometricAuthManager.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `SecurePrefs.kt`.
- Dependencies: AndroidX Biometric, device biometric/device credential support, app lifecycle integration.
- User workflow: User toggles biometric lock in Settings. On the next protected cold start/resume, RelateAI shows the biometric/device credential prompt before rendering relationship data. Edge cases include prompt cancellation and unsupported device authentication.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `BiometricLockPolicyTest`; live device prompt validation remains required.
- Confidence score: 90%.

### F-043 Quiet Hours, Blackout Dates, Reminder Toggles

- Category: System Feature.
- Description: Stores user preferences meant to suppress or control automation timing and channel usage.
- Functionality: SecurePrefs stores quiet hours, blackout dates, channel blackout JSON, biometric setting, birthday reminders, and AI enabled. Settings validates/saves quiet hours and channel blackout. `AutomationSchedulePolicy` applies default/custom send time, quiet-hour deferral, blackout-date deferral, reminder timing, and channel-block parsing. Generation, exact scheduling, dispatch workers, and event reminders use the policy.
- Components involved: Settings, SecurePrefs, AutomationSchedulePolicy, GenerateMessageUseCase, MessageGenerationWorker, DailyScheduler, MessageDispatchWorker, EventReminderScheduler, AutomationSetup diagnostics.
- Related files: `SettingsScreen.kt`, `SettingsViewModel.kt`, `SecurePrefs.kt`, `AutomationSchedulePolicy.kt`, `GenerateMessageUseCase.kt`, `MessageGenerationWorker.kt`, `DailyScheduler.kt`, `MessageDispatchWorker.kt`, `EventReminderScheduler.kt`, `AutomationSetupViewModel.kt`.
- Dependencies: Preferences, scheduler/dispatcher enforcement, notification settings.
- User workflow: User configures quiet hours/channel blackout/reminders. Automatic sends are scheduled outside quiet hours and blackout dates, dispatch workers defer if settings changed after scheduling, and reminders are disabled when the reminder toggle is off.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by settings tests, `AutomationSchedulePolicyTest`, generation tests, dispatch worker deferral tests, and event reminder scheduler tests.
- Confidence score: 90%.

### F-044 Event Reminder Scheduling

- Category: System Feature.
- Description: Intended to notify users before important events.
- Functionality: EventReminderScheduler schedules AlarmManager reminders from active event `notifyDaysBefore`, cancels reminders when the global reminder toggle is disabled, reschedules all active reminders during daily startup and boot recovery, and EventReminderReceiver guards against stale alarms after reminders are disabled.
- Components involved: EventReminderScheduler, EventReminderSchedulerService, EventReminderReceiver, NotificationHelper, EventEntity, DailyScheduler, WorkerScheduler, DailyTriggerWorker.
- Related files: `EventReminderScheduler.kt`, `EventReminderSchedulerService.kt`, `EventReminderReceiver.kt`, `NotificationHelper.kt`, `EventEntity.kt`, `DailyScheduler.kt`, `WorkerScheduler.kt`, `DailyTriggerWorker.kt`.
- Dependencies: AlarmManager or WorkManager scheduling, event repository, notification permission.
- User workflow: User saves or syncs events and receives reminder notifications before the event date. Edge cases include disabled reminders, stale event ids, notification permission denial, and exact-alarm availability.
- Current status: Fully Implemented.
- Completion percentage: 85%.
- Test coverage: Partially Tested by `AutomationSchedulePolicyTest`, `EventReminderSchedulerTest`, manual-event/discovery tests, and daily worker tests; live notification/alarm validation remains required.
- Confidence score: 90%.

### F-045 Mood Logs

- Category: Deprecated.
- Description: A historical/experimental schema concept that is not an active feature.
- Functionality: `mood_logs` is dropped in older migrations, recreated in migration 7 to 8, dropped again in migration 8 to 9, and the DAO is commented out. No active entity, DAO, repository, screen, or use case exists.
- Components involved: AppDatabase migrations only.
- Related files: `AppDatabase.kt`.
- Dependencies: None active.
- User workflow: None. Users cannot create, view, or use mood logs.
- Current status: Deprecated.
- Completion percentage: 0%.
- Test coverage: Partially Tested indirectly by migration tests.
- Confidence score: 98%.

### F-046 Dedicated Birthday Quick-Add FAB/Modal

- Category: UI Feature.
- Description: Older docs referenced a dedicated birthday quick-add FAB/modal flow.
- Functionality: Current implementation supports manual event creation through Events/manual event flow. A separate dedicated birthday quick-add FAB/modal is not active and should be treated as superseded by manual event creation unless reintroduced intentionally.
- Components involved: Events screen/manual event flow.
- Related files: `EventsScreen.kt`, `EventsViewModel.kt`, `SaveManualEventUseCase.kt`.
- Dependencies: Event/contact repositories.
- User workflow: Users add birthdays through manual events rather than a dedicated quick-add FAB/modal. Edge cases are covered by manual event validation.
- Current status: Deprecated.
- Completion percentage: 0% for the dedicated quick-add concept; manual event creation is covered by F-010.
- Test coverage: Not Tested as a separate feature.
- Confidence score: 90%.

### F-047 Legacy Retrofit Gemini Model Layer

- Category: Internal Feature.
- Description: Retrofit-style Gemini models appear in source but the current Gemini client primarily uses Firebase Vertex AI or Google AI SDK paths.
- Functionality: `GeminiModels.kt` defines model structures but is not clearly part of the active call path observed in `GeminiClient`.
- Components involved: Gemini model classes, Gemini client.
- Related files: `GeminiModels.kt`, `GeminiClient.kt`, `AiServiceImpl.kt`.
- Dependencies: Moshi/Retrofit if used by a future or legacy path.
- User workflow: None direct.
- Current status: Experimental.
- Completion percentage: 30%.
- Test coverage: Not Tested directly.
- Confidence score: 75%.

## 7. Data Model and Database Summary

Primary entities:

- `ContactEntity`: identity, Google id, contact details, relationship classification, personalization settings, automation preferences, health score, gift budgets, memory fields, lifecycle flags.
- `EventEntity`: contact id, event type, label, date/year, next occurrence, notify days, source, confidence, verification, computed age/days fields.
- `PendingMessageEntity`: six generated variants, selected variant/text, channel, approval mode, status, scheduled time/year, model metadata, personalization signals, fallback flag.
- `SentMessageEntity`: message text, channel, sent time, delivery status, AI metadata, reply metadata, contact deletion marker.
- `StyleProfileEntity` and `StyleProfileHistoryEntity`: current and historical user writing style.
- `MemoryNoteEntity`: per-contact notes for preferences, events, gifts, milestones, and general context.
- `GiftHistoryEntity`: per-contact gift records, budget and outcome metadata.
- `ActivityLogEntity`: app action/audit trail.
- `MessageFeedbackEntity`: AI feedback signal from wish preview/regeneration.

Schema status:

- Active Room database version: 13.
- Current schema location: `core/data/schemas/com.example.core.db.AppDatabase/13.json`.
- SQLCipher is used via Room SupportFactory.
- Legacy plaintext DB quarantine exists before encrypted open.
- Mood logs are not active; they are migration history only.

## 8. Internal APIs and Service Interfaces

There are no custom HTTP API endpoints in this project. The API surface is a set of domain service interfaces and external integrations.

| Interface | Purpose | Implementation |
|---|---|---|
| `AiService` | Classification, message generation, regeneration, gifts | `AiServiceImpl` |
| `ContactSyncService` | Google/device contact sync boundary | `ContactSyncServiceImpl`, `GoogleContactsSync`, `DeviceContactsReader` |
| `MessageDispatcherService` | Dispatch abstraction for domain use cases | `MessageDispatcherServiceImpl`, `MessageDispatcher` |
| `NotificationService` | Approval/system/revival notification abstraction | `NotificationServiceImpl`, `NotificationHelper` |
| `SchedulerService` | Exact send scheduling/canceling | `SchedulerServiceImpl`, `DailyScheduler` |
| `PreferencesRepository` | App preference boundary | `PreferencesRepositoryImpl`, `SecurePrefs` |
| `BackupService` | Encrypted export/import | `BackupServiceImpl` |
| `TestSendService` | Send test email to configured sender | `TestSendServiceImpl` |
| `AnalyticsReportService` | Analytics export/report generation | `AnalyticsReportServiceImpl` |

External integrations:

- Google Sign-In and Firebase Auth for identity.
- Google People API for Google contacts.
- Android ContactsProvider for device contacts.
- Firebase Vertex AI and Google AI client for Gemini.
- Gmail SMTP via JavaMail for email delivery.
- Android SMS APIs and broadcasts for SMS delivery.
- WhatsApp and WhatsApp Business through Accessibility Service automation.
- WorkManager, AlarmManager, NotificationManager, AppWidgetManager, and Android settings intents.

## 9. Background Jobs, Receivers, and Workflows

Background workers:

| Worker | Role | Status |
|---|---|---|
| `DailyTriggerWorker` | Kicks daily automation and backup reminders | Fully Implemented |
| `ContactSyncWorker` | Background Google contacts sync/classification | Fully Implemented |
| `EventDiscoveryWorker` | Rebuilds upcoming events from contacts | Fully Implemented |
| `MessageGenerationWorker` | Generates pending wishes for upcoming events | Fully Implemented |
| `MessageDispatchWorker` | Dispatches approved/due messages | Fully Implemented |
| `RevivalWorker` | Suggests reconnect messages for low-health contacts | Fully Implemented |
| `StyleAnalysisWorker` | Updates writing style from sent history | Fully Implemented |

Receivers:

| Receiver | Role | Status |
|---|---|---|
| `MessageDispatchReceiver` | AlarmManager entry point for dispatch | Fully Implemented |
| `BootReceiver` | Reschedules automation after reboot | Fully Implemented |
| `ApprovalReceiver` | Handles notification approve/reject/retry | Fully Implemented |
| `EventReminderReceiver` | Shows event reminder notifications | Partially Implemented due scheduling uncertainty |
| `SmsStatusReceiver` | Updates SMS sent/delivered status | Fully Implemented |
| `BirthdayWidgetProvider` | Updates birthday widget | Fully Implemented |

## 10. Undocumented Features Found in Code

- Guest/developer bypass mode and mock-contact seeding.
- Legacy plaintext database quarantine before SQLCipher open.
- Dead-letter queue surfaced through AI Doctor.
- AI Doctor readiness checks and dry-run/test actions.
- SMS delivery status callbacks with sent/delivered broadcasts.
- WhatsApp Accessibility state machine for UI automation.
- Birthday widget and dynamic shortcuts.
- Deep links for wish, contact, and settings routes.
- Backup reminder logic when the last backup is older than the threshold.
- Channel blackout enforcement inside message dispatch.
- Test-send-to-self flow from Wish Preview and Settings-backed Gmail config.
- Message feedback reasons for regeneration quality control.
- Wish Preview why-signals explaining personalization inputs.
- Legacy event-id fallback when opening wish preview.
- Local Zscaler truststore support for Gradle test JVMs when `.gradle/trust/cacerts-zscaler` exists.

## 11. Partially Implemented Features

- Biometric app lock: manager, toggle, app-wide cold-start/resume gate, and policy test exist; live device prompt validation remains required.
- Live device validation is still required for quiet-hour deferral, blackout-date deferral, and event-reminder notifications.
- Dynamic shortcuts: implemented as entry points, but not deeply route-specific beyond their configured intents.
- UI/device smoke coverage: many viewmodels and use cases are tested, but Compose rendering, notifications, widgets, exact alarms, SMS, WhatsApp, OAuth, SMTP, and ContactsProvider require device validation.

## 12. Dead, Deprecated, or Unused Features

- `mood_logs`: historical migration table only; not an active feature.
- Dedicated birthday quick-add FAB/modal: superseded by manual event creation flow.
- `GeminiModels.kt`: may be legacy or future Retrofit-style model code; not clearly active in the current Gemini client path.
- Older documentation references to feature modules: actual active modules are `:app`, `:core:domain`, `:core:data`, and `:core:ui`.
- Older documentation claims with outdated test counts or blanket "fully implemented" security should be replaced with the current observed 212-test report and the partial biometric/enforcement notes above.

## 13. Validation and Test Coverage Summary

Observed test artifacts:

- `app/build/test-results/testDebugUnitTest`: app, viewmodel, domain-usecase, worker, parser, route, SMS receiver, and config test XML.
- `core/data/build/test-results/testDebugUnitTest`: data-layer migration, backup, analytics, repository, key derivation, quarantine, and resilience test XML.
- Aggregate observed result: 241 tests, 0 failures, 0 errors, 0 skipped.
- No separate local XML result directories were observed for `core/domain/build/test-results/testDebugUnitTest` or `core/ui/build/test-results/testDebugUnitTest` in this snapshot.

Observed coverage:

- Instructions: 30,148 covered / 126,326 total, approximately 23.9 percent.
- Lines: 4,083 covered / 14,158 total, approximately 28.8 percent.
- Branches: 878 covered / 8,467 total, approximately 10.4 percent.
- Methods: 1,013 covered / 2,582 total, approximately 39.2 percent.
- Classes: 297 covered / 554 total, approximately 53.6 percent.

Coverage interpretation:

- Strongest local coverage is around domain use cases, data migrations, parsers, backup/encryption, resilience utilities, and viewmodel behavior.
- Weakest coverage is UI rendering, Android system integrations, live Google/Firebase/Gemini/Gmail/SMS/WhatsApp behavior, exact alarms, widgets, and notification UX.
- Existing unit tests are useful but are not a replacement for device smoke testing.

Validation commands executed for this update:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ui.MainActivityNavigationSmokeTest --no-configuration-cache -Djavax.net.ssl.trustStore=.gradle/trust/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=changeit
```

Connected instrumentation was attempted on `1b87b5db` with `MainActivityNavigationSmokeTest`. Debug builds now use `com.aistudio.relateai.qxtjrk.debug` and a matching debug `google-services.json` so the test target installs side-by-side with the existing production-signed app. The latest connected run installed the debug package and reported `Starting 2 tests`, but progress remained at 0/2 for several minutes while `dumpsys activity top` showed another app foregrounded and the RelateAI debug process backgrounded. The stuck wrapper was stopped; live UI validation requires an idle, unlocked device that is not being actively used.

CI validation:

- `.github/workflows/android.yml` runs `./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache`.
- CI then runs `./gradlew jacocoDebugUnitTestReport --no-configuration-cache`.
- CI verifies that release artifact tasks fail when release signing environment variables are missing.
- CI uploads lint, unit-test, coverage, and debug APK artifacts.

## 14. Configuration, Build, and Deployment

Gradle and tooling:

- AGP: 9.2.1.
- Kotlin: 2.2.10.
- JDK toolchain: 21.
- Kotlin JVM target: 17.
- Compose BOM: 2024.12.01.
- Hilt: 2.59.2.
- Room: 2.7.0.
- WorkManager: 2.9.0.
- SQLCipher: 4.5.4.
- Firebase BOM: 34.12.0.
- Firebase Vertex AI: 16.5.0.
- Google AI client: 0.9.0.
- JaCoCo: 0.8.12.

Release behavior:

- Release build enables minification and resource shrinking.
- Release signing is blocked unless `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` are configured and valid.
- Baseline profile plugin is enabled with merge into main.

Security configuration:

- `network_security_config.xml` pins major Google/Firebase/Gmail-related hosts to trusted roots with a 2027-06-01 expiration.
- `backup_rules.xml` and `data_extraction_rules.xml` exclude sensitive local database and encrypted preference data.
- Android manifest disables regular backup with `android:allowBackup="false"`.

## 15. Acceptance Criteria for Future SSOT Updates

Any future feature documentation update should:

- Name the exact source files inspected.
- Classify each feature as Fully Implemented, Partially Implemented, Experimental, Deprecated, or Not Implemented.
- Include completion percentage, test coverage status, and confidence score for every new or changed feature.
- Separate local unit-test evidence from live device/integration evidence.
- Record contradictions between docs and code in the partial/dead/undocumented sections.
- Avoid claiming custom backend/API behavior unless a backend surface exists in code.
