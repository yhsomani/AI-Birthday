# RelateAI Full Codebase, Product, UX, Workflow, and Technical Audit

Date: 2026-07-03

Scope: current working tree under `/Users/yashsomani/Desktop/Android Project/AI-Birthday`.

Method: static repository audit of Gradle modules, app navigation, ViewModels, core data/domain code, workers, schedulers, persistence, resources, tests, documentation, ignored artifacts, and current git status. I did not run the full Gradle test suite for this audit.

Important context: this report audits the working tree, not only committed `HEAD`. The repository already contains many modified and untracked files. I did not revert or overwrite existing changes. The existing `CODEBASE_AUDIT_REPORT_2026-07-01.md` is modified locally, so this report is intentionally a new dated file.

## Executive Summary

RelateAI is a local-first relationship operations assistant. The strongest parts of the product are its automation depth, privacy posture, backup/export design, diagnostic testing, screenshot coverage, and the move toward shared domain policies for dispatch, readiness, notification, and setup decisions. The product is no longer a simple birthday reminder app; it is closer to a personal relationship CRM with AI drafting, reminders, channel-aware sending, memory/gift context, analytics, and recovery workflows.

The main risk is not missing raw functionality. The main risk is product comprehension and architectural concentration. Users are asked to understand contacts, events, personalization, setup readiness, permission readiness, AI generation, approval modes, channel fallbacks, exact alarms, quiet hours, backup, and recovery. The code mirrors that complexity: large screens and ViewModels still coordinate too much, domain boundaries are incomplete, and several "ready to send" concepts exist in parallel.

Top findings:

| Priority | Finding | Why it matters | Recommended action |
| --- | --- | --- | --- |
| P1 | Readiness consistency now needs broader journey-level regression coverage. | Home, Messages, Wish Preview, Setup, setup notifications, and system alerts now have contract-level readiness journey coverage; the domain message lifecycle now has generate -> approve -> dispatch -> failed retry -> recovered dispatch coverage; the worker-to-dispatcher attempt-id handoff, dispatcher retry-limit finalization, SMS delivered-failure receiver callback, Messages failed-row retry success path, Messages recovery setup route wiring, and onboarding setup post-auth target are guarded. Full connected UI journeys and physical-device provider flows can still regress between focused tests. | Add UI lifecycle and device/provider callback/finalization end-to-end tests around the canonical readiness contract. |
| Resolved | Domain persistence/platform leakage through Room/Paging/entity/Android contracts. | `core:domain` is now a Kotlin/JVM module; Room entities, DAO projections, Room-backed mappers, raw entity repository APIs, Android `Uri` backup parameters, Android imports, and `org.json` runtime reliance have been removed from domain. | Keep the guard tests green and route future platform/storage work through app/data adapters. |
| Resolved | Windows/IDE Android builds now fail fast when Gradle runs on a JRE without `jlink`. | The pasted `:app:assembleDebug` failure used the Antigravity/Red Hat extension JRE, which lacks the `jlink` executable AGP needs for `JdkImageTransform`. | Keep the root Gradle preflight guard green and configure IDE Gradle JDK/JAVA_HOME to Android Studio JBR or Temurin JDK 21. |
| P1 | Large presentation files concentrate workflow logic. | `AutomationSetupViewModel.kt` is now 214 lines after extracting setup UI contracts, account/provider, automation/channel, email, quality, system/recovery readiness presentation, command execution, readiness report loading, AI failure diagnosis, diagnostic snapshot persistence, overall readiness presentation, and Android capability probing; `AutomationSetupScreen.kt` is now 236 lines after extracting diagnostics and support cards, `AutomationSetupDashboardComponents.kt` is now 172 lines after extracting readiness/detail rendering, and `AutomationSetupReadinessComponents.kt` is now 135 lines after extracting setup action/progress rendering; `BackupRestoreScreen.kt` is now 186 lines after extracting security/passphrase and action/status rendering, and `BackupRestoreActionComponents.kt` is now 196 lines after extracting status/preview rendering; `StyleCoachScreen.kt` is now 186 lines after extracting training/profile/history rendering, and `StyleCoachDisplayComponents.kt` is now 318 lines after extracting history rendering; `MemoryVaultScreen.kt` is now 318 lines after extracting note editor and note-card rendering; `HomeScreen.kt` is now 219 lines after extracting dashboard/action rendering, `HomeDashboardComponents.kt` is now 219 lines after extracting action/readiness rendering, and `HomeActionComponents.kt` is now 262 lines after extracting quick-action rendering; `MessagesQueueComponents.kt` is now 101 lines after extracting action buttons/status and badge/label rendering; `GiftAdvisorScreen.kt` is now 313 lines after extracting suggestions, add-gift dialog, and budget/history rendering; `EventsScreen.kt` is now 366 lines after extracting card and manual-dialog components, and `EventsManualEventDialogComponents.kt` is now 322 lines after extracting contact choice rendering; `ContactListScreen.kt` is now 309 lines after extracting row/quality rendering; `WishPreviewScreen.kt` is now 214 lines after extracting send-summary, review/rationale, and editor/action components, and `WishPreviewEditorComponents.kt` is now 242 lines after extracting action/result rendering; `SettingsScreen.kt` is now 338 lines after extracting account, preference, AI/send-readiness configuration, data/recovery, about, and sign-out components; `SettingsConfigurationComponents.kt` is now 325 lines after extracting automation/delivery controls; `AnalyticsScreen.kt` is now 178 lines after extracting report rendering, and `AnalyticsReportComponents.kt` is now 228 lines after extracting chart/value-row rendering; `ActivityHistoryScreen.kt` is now 219 lines after extracting log-card rendering; `ContactDetailPreferencesComponents.kt` is now 197 lines after extracting preference form rendering. Several other presentation files remain large. This raises regression and review cost. | Continue splitting remaining large screens by feature state reducers, route-level coordinators, and reusable domain use cases. |
| Resolved | AI generated message text logging risk. | `AiServiceImpl` now logs `recommendedVariantName` metadata only, and tests verify generated copy is absent from `StructuredLogger` history and Android logs. | Keep generated body fields omitted or keyed as redacted message-body text in future changes. |
| P2 | Product has too many diagnostics surfaces without a guided fix sequence. | AI Doctor, Settings, Home, Messages, Wish Preview, and setup notifications all expose parts of readiness. | Convert setup and recovery into a ranked "Fix next" flow with one primary action at a time. |
| Resolved | App dependency surface had duplicate or non-user-facing runtime declarations. | Retrofit/converter/logging references are absent in the current tree; the app module now no longer declares data-layer-only runtime dependencies for SQLite KTX, Security Crypto, Biometric, JavaMail, SQLCipher, Moshi, and OkHttp, and unused Firebase Analytics / XML Material Components aliases were removed. | Keep the repository hygiene guard green and add app runtime dependencies only when app source or an implemented user workflow needs them. |
| P2 | Local/generated artifacts remain near source. | Logs, legacy schemas, one-off patch scripts, and generated screenshots create noise and review risk. | Delete after approval or archive outside the repo; keep active schemas and test baselines. |

## Product Assessment

### What The Product Is

RelateAI is best described as a private relationship assistant:

- It imports and enriches contacts.
- It tracks birthdays, anniversaries, holidays, and manual events.
- It generates personalized messages through AI.
- It supports approval workflows before sending.
- It can dispatch through SMS, WhatsApp, and email routes depending on user setup and device capability.
- It records sent messages, activity history, dispatch attempts, health diagnostics, memory notes, gift history, style samples, and backup state.

### Product Owner View

The product has a strong differentiator: it combines local-first data ownership with real automation. Most relationship reminder apps stop at reminders or templates. This codebase attempts end-to-end execution: detect event, generate draft, evaluate readiness, request review, send, recover failures, and explain health.

Business value is highest when the app is trusted to prevent social misses without causing social embarrassment. That means the product's success metric should not only be "messages generated" or "contacts imported"; it should be:

- important occasions not missed
- fewer stale relationships
- fewer generic or inappropriate messages
- high approval confidence
- low blocked automation rate
- recoverable failures with clear fixes
- user belief that private context is controlled

The current implementation supports those goals technically, but the UX still exposes too much system state. The product should increasingly hide machinery behind "what needs my attention now?"

### End User View

The target user wants confidence, not knobs. They need to know:

- Who should I contact?
- What should I say?
- Will it send correctly?
- What personal context is being used?
- Can I stop, edit, or recover it?
- Is my data private?

The app currently answers most of those questions somewhere, but not always in one place. Users may need to move between Home, Messages, Wish Preview, Contact Detail, Memory Vault, Gift Advisor, AI Doctor, and Settings to resolve a single blocked message.

### UX Designer View

The design system has matured. There are shared components, theme tokens, screenshot baselines, large-font and Hindi coverage, and regression tests for labels and strings. The remaining UX issue is not visual polish. It is workflow density.

The app should reduce the number of simultaneous statuses. A user should see a compact action model:

1. Ready
2. Needs review
3. Needs setup
4. Blocked
5. Failed and recoverable

Each state should map to one primary action and one explanation.

### QA View

The repository has unusually strong guardrails for a mobile side project:

- CI runs unit tests, lint, assemble, Roborazzi screenshot verification, production readiness checks, coverage tasks, and dependency review.
- Tests verify release signing policy, backup exclusions, direct Android Log usage, provider config allowlists, repository hygiene, icon content descriptions, hardcoded string regressions, and design token usage.
- Roborazzi screenshot baselines cover many screens and large-font/Hindi cases.

The QA gap is broader journey-level testing. There are many focused tests, the canonical readiness policy now has cross-surface contract coverage for review, channel repair, and setup/provider repair journeys, the domain message lifecycle has generate -> approve -> dispatch -> failed retry -> recovered dispatch coverage, and the worker-to-dispatcher attempt-id bridge is guarded. The highest remaining business risk is UI/device workflow correctness: setup to contact import to event detection to generation to approval to real device/provider dispatch and provider finalization.

### Software Architect View

The module split is directionally correct:

- `:app`
- `:core:model`
- `:core:domain`
- `:core:data`
- `:core:ui`

However, boundaries are not yet fully clean:

- `core/domain` is now a Kotlin/JVM module and its main source is free of Android, AndroidX, Room entity, DAO, and Paging imports.
- Repository interfaces no longer expose Room entities for the audited contact, message, event, dispatch, and pure-record paths; boundary tests now guard this.
- App ViewModels and `MainActivity` now route app-shell and settings preference state through `PreferencesRepository`; `SecurePrefs` remains in data/app infrastructure.
- Android worker/scheduler code sometimes constructs data dependencies directly.

This is manageable but should be addressed before adding larger features.

## Current Architecture And Folder Structure

### Current Modules

| Module | Current role | Assessment |
| --- | --- | --- |
| `app` | Compose UI, navigation, ViewModels, Android app shell, tests. | Works but screen and ViewModel files are large. Feature package boundaries exist but are not consistently separated by domain workflow. |
| `core:model` | JVM model module. | Good direction. More entity-free domain models should move here or be exposed from domain. |
| `core:domain` | Use cases, policies, repository/service interfaces, many business rules. | Strong policy extraction work. Now a Kotlin/JVM module with a persistence/platform-neutral main source. |
| `core:data` | Room, workers, Firebase/Gemini, contacts sync, dispatch senders, backup, preferences. | Functionally rich. Contains Android-specific orchestration that should stay here, but some decisions are better delegated to domain policies. |
| `core:ui` | Shared Compose components/theme. | Good, backed by token tests. Continue moving repeated patterns here carefully. |

### Notable Build Configuration

- Android Gradle Plugin: 9.2.1
- Kotlin: 2.2.10
- Compile SDK: 37
- Target SDK: 36
- Min SDK: 24
- Java toolchain: 21
- Room schema authority: `core/data/schemas`
- Database version: 16
- Release builds require signing environment variables and have minify/shrink enabled.
- Firebase, Vertex AI/Gemini, WorkManager, Hilt, Room, SQLCipher, Security Crypto, Biometric, JavaMail, Coil, Moshi, and Roborazzi are present.

### Current Structure Problems

| Problem | Evidence | Impact |
| --- | --- | --- |
| Domain module boundary must stay guarded. | `core/domain` is now Kotlin/JVM and no longer imports Android, AndroidX, Room entities, DAOs, or Paging. Boundary tests scan for regressions. | Future platform/storage shortcuts can reintroduce coupling if the guards are weakened. |
| App layer still owns too much orchestration. | `AutomationSetupViewModel.kt`, `WishPreviewViewModel.kt`, `MessagesViewModel.kt`, and `HomeViewModel.kt` coordinate many policies and repositories directly. | UI changes risk business behavior regressions. |
| Presentation files are still large in places. | `AutomationSetupScreen.kt` is now decomposed to 236 lines with diagnostics, readiness/detail, and support-card companion components; `AutomationSetupDashboardComponents.kt` is now decomposed to 172 lines after extracting `AutomationSetupReadinessComponents.kt`; `AutomationSetupReadinessComponents.kt` is now decomposed to 135 lines after extracting `AutomationSetupActionComponents.kt`; `BackupRestoreScreen.kt` is now decomposed to 186 lines with security/passphrase and action/status companion components, and `BackupRestoreActionComponents.kt` is now decomposed to 196 lines after extracting `BackupRestoreStatusComponents.kt`; `GiftAdvisorScreen.kt` is now decomposed to 313 lines with suggestion, dialog, and budget/history companion components; `StyleCoachScreen.kt` is now decomposed to 186 lines with training and profile/history companion components, and `StyleCoachDisplayComponents.kt` is now decomposed to 318 lines after extracting `StyleCoachHistoryComponents.kt`; `MemoryVaultScreen.kt` is now decomposed to 318 lines with note editor and note-card companion components; `HomeScreen.kt` is now decomposed to 219 lines with dashboard and action/readiness companion components; `HomeDashboardComponents.kt` is now decomposed to 219 lines after extracting `HomeActionComponents.kt`; `HomeActionComponents.kt` is now decomposed to 262 lines after extracting `HomeQuickActionComponents.kt`; `MessagesQueueComponents.kt` is now decomposed to 101 lines with action/status and badge companion components; `WishPreviewScreen.kt` is now decomposed to 214 lines with send-summary, review/rationale, and editor/action companion components; `WishPreviewEditorComponents.kt` is now decomposed to 242 lines after extracting `WishPreviewActionComponents.kt`; `SettingsScreen.kt` is now decomposed to 338 lines with configuration, automation/delivery, and data/sign-out companion components; `SettingsConfigurationComponents.kt` is now decomposed to 325 lines after extracting `SettingsAutomationComponents.kt`; `AnalyticsScreen.kt` is now decomposed to 178 lines after extracting `AnalyticsReportComponents.kt`; `AnalyticsReportComponents.kt` is now decomposed to 228 lines after extracting `AnalyticsChartComponents.kt`; `ActivityHistoryScreen.kt` is now decomposed to 219 lines after extracting `ActivityHistoryLogComponents.kt`; `EventsManualEventDialogComponents.kt` is now decomposed to 322 lines after extracting `EventsManualEventContactComponents.kt`; `ContactListScreen.kt` is now decomposed to 309 lines after extracting `ContactListRowComponents.kt`; `ContactDetailPreferencesComponents.kt` is now decomposed to 197 lines after extracting `ContactDetailPreferenceFormComponents.kt`. Remaining large screens/components include `EventsScreen.kt`, `SettingsScreen.kt`, `MessagesListComponents.kt`, and `ContactDetailScreen.kt`. | Harder accessibility, layout, localization, and state review. |
| Some repository APIs exposed persistence details unnecessarily. | `ContactRepository` exposed DAO relationship-count projections and raw health-ranking entity methods even though pure analytics methods already existed. These API leaks have been removed and are covered by boundary tests. | Smaller domain API surface and lower risk of persistence details returning. |
| Documentation is abundant but can become stale. | Multiple audit, plan, progress, and SSOT docs overlap. | New contributors need a clear source of truth. |

### Proposed Structure

Target structure without a disruptive rewrite:

```text
app/
  src/main/java/com/example/
    app/                  # MainActivity, app shell, lifecycle gates
    navigation/           # NavGraph, Screen contracts, deep links
    feature/
      home/
      contacts/
      events/
      messages/
      wish/
      setup/
      settings/
      analytics/
      backup/
      memory/
      gifts/
      style/
      activity/
    ui/
      components/         # app-specific components only
      feedback/

core/
  model/                  # pure models and value objects
  domain/
    policy/
    usecase/
    repository/           # pure interfaces returning domain models
    service/              # pure interfaces
  data/
    database/             # Room entities, DAOs, migrations, schemas
    repository/           # maps Room/API to domain models
    worker/
    scheduler/
    preferences/
    contacts/
    ai/
    backup/
    dispatch/
  ui/
    components/
    theme/
```

Recommended migration order:

1. Keep repository interfaces returning pure domain/model records and maintain the boundary guard tests.
2. Keep presentation preference access routed through `PreferencesRepository` or focused use cases.
3. Split the largest ViewModels into route coordinator plus pure reducers/readers.

## Feature-By-Feature Audit

### 1. App Shell, Navigation, Permissions, Biometric Lock

Purpose: provide authenticated navigation, bottom tabs, deep links, permission prompts, app startup, and optional biometric lock.

Business goal: reduce first-session dropoff and keep automation permissions discoverable.

User goal: open the app and immediately understand what needs attention.

Ideal UX: the app opens to a trustworthy command center with one primary next action. Permission requests appear only when they unlock a visible task.

Expected workflow:

1. Splash resolves auth/onboarding/home.
2. User signs in or completes onboarding.
3. Home displays readiness and next action.
4. Bottom navigation exposes Home, Contacts, Events, Messages, Analytics.
5. Settings and contextual tools are reachable from relevant surfaces.

Current implementation:

- `MainActivity.kt` gates biometric lock and app shell.
- `RelateAIApp.kt` initializes WorkManager, diagnostics, SecurePrefs warmup, workers, and notifications.
- `Screen.kt` and `NavGraph.kt` define primary and contextual routes.
- Manifest declares SMS, contacts, network, exact alarm, boot, foreground service, notification, wake lock, telephony feature, WhatsApp package queries, file provider, receivers, accessibility service, and widget.

Gaps:

- SMS and notification permissions are surfaced centrally, but contact permission and account/provider readiness are more spread out.
- Biometric lock protects local app access, but sensitive operation confirmations need equal attention.
- Deep links exist, but journey-level testing should verify every deep link under locked/auth/onboarding states.

UX issues:

- Bottom navigation is clear, but readiness can still be fragmented across Home, Messages, Settings, and AI Doctor.
- Permission rationale should map to a concrete blocked task, not just a capability.

Accessibility issues:

- There are tests for icon content descriptions in cleaned screens. Keep expanding that to every new icon-only action and modal.

Improvements:

- Add a universal route guard model: unauthenticated, locked, setup-blocked, and permission-blocked states.
- Use one `PermissionReadiness` model across Home, Setup, Wish Preview, and Messages.
- Add deep-link smoke tests for locked/unauthenticated paths.

Priority: P2.

Related files:

- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/RelateAIApp.kt`
- `app/src/main/java/com/example/ui/navigation/Screen.kt`
- `app/src/main/java/com/example/ui/navigation/NavGraph.kt`
- `app/src/main/AndroidManifest.xml`

### 2. Onboarding And Authentication

Purpose: bring the user into the app, explain local-first value, and connect Google/Firebase identity plus contact scope.

Business goal: create a reliable identity and sync base for automation.

User goal: start quickly without fear of losing privacy or sending messages accidentally.

Ideal UX: onboarding explains three promises: private by default, review before sending unless changed, and reversible setup. The user can start with a minimal/manual mode if they do not want Google contacts immediately.

Current implementation:

- `SplashViewModel` resolves Auth/Home/Onboarding.
- `AuthViewModel` handles Google sign-in, Firebase token, contacts scope, and config validation.
- `OnboardingViewModel` records onboarding completion through `PreferencesRepository`.

Gaps:

- No obvious local-only or manual-contact mode.
- Contact sync is foundational, but users may want to evaluate the app before granting broad access.
- Auth and contacts are coupled in the mental model even when some features could work manually.

Missing functionality:

- Guest/manual trial mode.
- "Import later" path with demo or manual event/contact creation.
- Clear data deletion and backup prompt before destructive sign-out.

UX issues:

- The product's strongest trust claims should be explicit before OAuth.
- Users need to understand whether AI context leaves the device and when.

Accessibility issues:

- Ensure sign-in failure and config errors are exposed through accessible text, not only transient snackbars.

Improvements:

- Add "Start with manual contacts" as a low-permission mode.
- Show a compact privacy summary before Google sign-in.
- Add one screen explaining "Nothing sends until these rules say it can."

Priority: P2.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/AuthViewModel.kt`
- `app/src/main/java/com/example/ui/viewmodel/SplashViewModel.kt`
- `app/src/main/java/com/example/ui/viewmodel/OnboardingViewModel.kt`
- `app/src/main/java/com/example/ui/screens/auth/AuthScreen.kt`
- `app/src/main/java/com/example/ui/screens/onboarding/OnboardingScreen.kt`

### 3. Home Command Center

Purpose: give the user a daily relationship dashboard and next best action.

Business goal: make RelateAI feel useful every day, not only on birthdays.

User goal: know what to do now.

Ideal UX: Home shows one primary action, a short explanation, upcoming important moments, and health only when it affects actionability.

Current implementation:

- `HomeViewModel` combines dashboard metrics, upcoming events, setup progress, backup freshness, relationship health, and next actions.
- `HomeScreen.kt` owns route-level orchestration and high-level list flow.
- `HomeDashboardComponents.kt` renders the stats grid, relationship planner, and upcoming birthday section.
- `HomeQuickActionComponents.kt` renders quick action navigation.
- `HomeActionComponents.kt` renders next-action cards and setup progress.
- It can attempt initial contact sync when contact count is zero.
- It uses domain policy work such as home next action logic.

Gaps:

- The "next action" competes with metrics and readiness summaries.
- Silent initial sync can be useful but may confuse users if permission or account state blocks it.
- Home should be the canonical product command center, but AI Doctor and Messages also compete for that role.

Missing functionality:

- A durable "relationship inbox" abstraction that unifies upcoming occasions, stale contacts, failed sends, missing context, and backup/security tasks.
- Snooze/defer for Home actions.
- Explanations tied to business outcomes, for example "prevent missed birthday" rather than "notification permission missing."

UX issues:

- Potentially too many cards.
- Setup and backup prompts may feel administrative unless ranked by urgency.

Accessibility issues:

- Dashboard cards must maintain semantic headings and avoid purely color-coded health.

Improvements:

- Make Home render one `NextActionCard` from a canonical readiness/action use case.
- Move secondary metrics below the fold.
- Add "why this is first" copy for ranked actions.

Priority: P1.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt`
- `app/src/main/java/com/example/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/example/ui/screens/home/HomeDashboardComponents.kt`
- `app/src/main/java/com/example/ui/screens/home/HomeActionComponents.kt`
- `app/src/main/java/com/example/ui/screens/home/HomeQuickActionComponents.kt`
- `core/domain/src/main/kotlin/com/example/domain/home/`

### 4. Contacts List

Purpose: manage relationships and identify contacts needing enrichment or attention.

Business goal: increase personalization coverage and relationship retention.

User goal: find a person, understand relationship quality, and fix missing context.

Ideal UX: contacts are searchable, filterable by action need, and each card exposes the next useful enrichment action.

Current implementation:

- `ContactListViewModel` supports search, filters, sorting, relationship groups, quality signals, missing relationship, missing channel, missing personalization, low health, and VIP filters.
- `ContactListScreen` displays search/filter/sort/list states; `ContactListRowComponents.kt` renders rows, health dots, and quality badges.

Gaps:

- The product has rich contact quality logic but limited bulk repair workflows.
- It is not clear whether contact import conflicts and duplicate cleanup have a user-facing resolution path.

Missing functionality:

- Bulk add relationship/channel for selected contacts.
- Smart suggestions for "top 10 contacts to enrich."
- Duplicate/merge workflow if Google contacts or manual contacts collide.

UX issues:

- Too many filters can become task discovery friction.
- Contact health needs a plain-language explanation.

Accessibility issues:

- Filter chips should expose selected state and count to TalkBack.

Improvements:

- Add a "Needs setup" saved view with batch actions.
- Promote relationship/channel/context completeness as a checklist per contact.
- Add contact list empty/error states tied to contact permission/account readiness.

Priority: P2.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/ContactListViewModel.kt`
- `app/src/main/java/com/example/ui/screens/contacts/ContactListScreen.kt`
- `app/src/main/java/com/example/ui/screens/contacts/ContactListRowComponents.kt`
- `core/data/src/main/kotlin/com/example/data/repository/ContactRepositoryImpl.kt`
- `core/domain/src/main/kotlin/com/example/domain/repository/ContactRepository.kt`

### 5. Contact Detail And Preferences

Purpose: show a relationship profile and configure personalization, channels, reminders, and contextual tools.

Business goal: deepen personalization and increase successful sends.

User goal: understand and improve what the app knows about one person.

Ideal UX: a contact profile should answer "what matters for this person?" and "what will RelateAI do next?"

Current implementation:

- `ContactDetailViewModel` and contact detail components split essentials, personalization, preferences, header, and actions.
- Contact detail links to Wish Preview, Memory Vault, Gift Advisor, and Chat History.

Gaps:

- Contact-specific readiness is not clearly the same readiness model used by Home/Messages/Wish Preview.
- Preference choices can become configuration-heavy.

Missing functionality:

- Contact-level "automation preview" showing next event, draft status, channel, and blocker.
- Recent relationship changes timeline.
- Merge/conflict resolution.

UX issues:

- Contextual tools are valuable but can feel like separate destinations rather than one profile.

Accessibility issues:

- Preference choice flows should expose group labels and selected states.

Improvements:

- Add a contact-level command strip: next occasion, draft, channel, setup status.
- Merge Memory Vault, Gift Advisor, Style impact, and Chat History signals into a compact "personalization context" section.

Priority: P2.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/ContactDetailViewModel.kt`
- `app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt`
- `app/src/main/java/com/example/ui/screens/contacts/ContactDetailPreferencesComponents.kt`
- `app/src/main/java/com/example/ui/screens/contacts/ContactDetailPreferenceFormComponents.kt`
- `app/src/main/java/com/example/ui/screens/contacts/ContactDetailPersonalizationComponents.kt`

### 6. Events And Manual Occasion Management

Purpose: manage birthdays, anniversaries, holidays, manual events, duplicate warnings, and conflict resolution.

Business goal: avoid missed occasions and incorrect duplicated reminders.

User goal: see upcoming events and safely add or fix events.

Ideal UX: event creation should be fast, conflict-aware, and explain whether it will create a message automatically.

Current implementation:

- `EventsViewModel` supports event filters, manual event save, duplicate and date conflict warnings, conflict resolution, and activity logging.
- `EventsScreen.kt` is now 366 lines after moving event-card/trust-chip rendering into `EventsEventCardComponents.kt` and manual-event form/dialog rendering into `EventsManualEventDialogComponents.kt`; manual contact choice rendering now lives in `EventsManualEventContactComponents.kt`.

Gaps:

- Manual event creation should preview downstream automation impact.
- The code is now decomposed, but the manual event journey still needs stronger product guidance around downstream automation impact.

Missing functionality:

- Calendar import/export integration.
- Event-level automation timeline.
- Bulk confirmation for imported events.

UX issues:

- Conflict warnings are useful but need clear language: "same person, same date" vs "same event type."

Accessibility issues:

- Date pickers and conflict dialogs need explicit labels and focus behavior.

Improvements:

- Add "After saving, RelateAI will..." summary.
- Split event creation/editing components into smaller files.
- Add journey tests for duplicate event resolution.

Priority: P2.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt`
- `app/src/main/java/com/example/ui/screens/events/EventsScreen.kt`
- `app/src/main/java/com/example/ui/screens/events/EventsEventCardComponents.kt`
- `app/src/main/java/com/example/ui/screens/events/EventsManualEventDialogComponents.kt`
- `app/src/main/java/com/example/ui/screens/events/EventsManualEventContactComponents.kt`
- `core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt`

### 7. Message Queue

Purpose: review, approve, reject, retry, revoke, and inspect messages across states.

Business goal: convert generated drafts into safe successful sends.

User goal: quickly review what needs attention and trust that nothing wrong sends.

Ideal UX: messages are grouped by user action: review, scheduled, blocked, failed, sent.

Current implementation:

- `MessagesViewModel` buckets pending messages into needs review, scheduled, blocked, failed, and sent.
- It uses `MessageOperationalReadinessPolicy`.
- It supports search, channel filter, sorting, bulk approve/reject/retry, individual approve/reject/retry/revoke, and activity logging.
- UI is split into several message components, which is a good trend.

Gaps:

- Readiness is still one of several readiness interpretations in the app.
- `MessagesViewModel` now receives preferences through `PreferencesRepository`, but readiness still competes with other readiness surfaces.

Missing functionality:

- One-click "fix blocker" for every blocked message.
- Batch unblock guidance, for example "3 messages need SMS permission."
- Per-message audit trail collapsed into a concise timeline.

UX issues:

- Status tabs plus filters plus bulk actions can be dense.
- Failed and blocked states need stronger, action-oriented copy.

Accessibility issues:

- Bulk selection and per-card status need accessible selected/status announcements.

Improvements:

- Make each card expose `primaryAction`, `secondaryAction`, and `reason` from the same readiness model used by Wish Preview and Home.
- Move preference reads behind use cases.
- Add end-to-end test from generated pending message to approve to worker dispatch state.

Priority: P1.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt`
- `app/src/main/java/com/example/ui/screens/messages/MessagesScreen.kt`
- `app/src/main/java/com/example/ui/screens/messages/MessagesQueueComponents.kt`
- `app/src/main/java/com/example/ui/screens/messages/MessagesActionButtonComponents.kt`
- `app/src/main/java/com/example/ui/screens/messages/MessagesBadgeComponents.kt`
- `core/domain/src/main/kotlin/com/example/domain/automation/MessageOperationalReadinessPolicy.kt`

### 8. Wish Preview

Purpose: review and edit AI message drafts before sending.

Business goal: increase approval confidence and reduce embarrassing messages.

User goal: inspect the message, understand why it was generated, edit it, test it, approve it, or reject it.

Ideal UX: the preview should make trust obvious: message, context used, readiness, send timing, channel, and alternatives.

Current implementation:

- `WishPreviewViewModel` loads drafts, contact context, memory/gift counts, event type, route/contact preferences, device readiness, review queue, variants, editing state, test send, feedback, regeneration, approve/reject, and review-next.
- It uses `WishDraftReadinessPolicy` and `WishPreviewSendSummaryPolicy`.
- `WishPreviewScreen.kt` is now 214 lines after moving approval-plan summary rendering into `WishPreviewSendSummaryComponents.kt`, review/rationale controls into `WishPreviewReviewComponents.kt`, editor rendering into `WishPreviewEditorComponents.kt`, and action/result rendering into `WishPreviewActionComponents.kt`.

Gaps:

- This screen has the right trust tools, but too many are local to this screen.
- Context transparency is present but should be a product-wide mental model.

Missing functionality:

- Granular "included in this AI prompt" toggles per memory/gift/history item.
- Clear fallback explanation when AI generation used templates.
- Approval comparison between variants.

UX issues:

- The screen can feel like a cockpit for a single message.
- Users need the primary question first: "Is this safe and ready to send?"

Accessibility issues:

- Variant selection and long editable message fields need strong focus and state labels.

Improvements:

- Top section should be "Ready to send / Needs review / Blocked" with one primary action.
- Move context explanation to a structured expandable section.
- Split the screen into smaller components around message editor, readiness summary, context, and actions.

Priority: P1.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt`
- `app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt`
- `app/src/main/java/com/example/ui/screens/wish/WishPreviewEditorComponents.kt`
- `app/src/main/java/com/example/ui/screens/wish/WishPreviewActionComponents.kt`
- `app/src/main/java/com/example/ui/screens/wish/WishPreviewSendSummaryComponents.kt`
- `app/src/main/java/com/example/ui/screens/wish/WishPreviewReviewComponents.kt`
- `core/domain/src/main/kotlin/com/example/domain/message/WishDraftReadinessPolicy.kt`
- `core/domain/src/main/kotlin/com/example/domain/message/WishPreviewSendSummaryPolicy.kt`

### 9. Automation Setup / AI Doctor

Purpose: diagnose and fix account, AI, notification, exact alarm, worker, route, channel, email, style, personalization, and automation readiness.

Business goal: make automation reliable enough to trust.

User goal: know what is broken and fix it quickly.

Ideal UX: a guided repair wizard with a ranked top issue, fix button, verification, and explanation.

Current implementation:

- `AutomationSetupViewModel.kt` is now a 214-line observer/state applicator after focused A-005 splits.
- `AutomationSetupReadinessReportBuilder.kt` owns readiness input loading and policy orchestration for Google contacts, Gemini, AI generation, circuit breaker, notifications, exact send, daily automation, health, dispatch recovery, email, style, personalization, generic messages, full automation, event routes, SMS/WhatsApp, and channel verification.
- Android permission, package, Google Sign-In, Firebase-auth, and accessibility-service checks now live in `AutomationSetupCapabilityProbe.kt`.
- `AutomationSetupScreen.kt` owns route-level state, Android settings/permission actions, and section ordering.
- `AutomationSetupDashboardComponents.kt` renders the AI Doctor summary banner and dashboard card shell.
- `AutomationSetupActionComponents.kt` renders setup progress, recommended fix, and diagnostic action buttons.
- `AutomationSetupReadinessComponents.kt` renders grouped readiness checks, row actions, and shared readiness status helpers.
- `AutomationSetupSupportCards.kt` renders WhatsApp, battery, notifications, and approval support cards.

Gaps:

- The feature is powerful but overwhelming.
- The feature still needs product simplification; its implementation is now decomposed into focused collaborators, but the user journey is still a dense diagnostics checklist.
- Users should not need to understand every subsystem to become ready.

Missing functionality:

- One-step guided fix mode.
- Historical setup result comparison.
- "Run only checks relevant to this blocked message."

UX issues:

- Checklist density is high.
- The name "AI Doctor" may imply a debugging tool, while users need a simple "Setup" or "Fix automation" flow.

Accessibility issues:

- Long diagnostic lists need headings, state descriptions, and predictable focus after rerun/fix.

Improvements:

- Introduce `SetupReadinessUseCase` that returns ranked blockers and fix commands.
- Keep full diagnostics behind an "Advanced details" affordance.
- Add a message-specific setup entry point.

Priority: P1.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt`
- `app/src/main/java/com/example/ui/screens/setup/AutomationSetupScreen.kt`
- `app/src/main/java/com/example/ui/screens/setup/AutomationSetupDashboardComponents.kt`
- `app/src/main/java/com/example/ui/screens/setup/AutomationSetupReadinessComponents.kt`
- `app/src/main/java/com/example/ui/screens/setup/AutomationSetupActionComponents.kt`
- `app/src/main/java/com/example/ui/screens/setup/AutomationSetupSupportCards.kt`
- `core/domain/src/main/kotlin/com/example/domain/automation/Setup*ReadinessPolicy.kt`

### 10. Settings

Purpose: configure AI key, Gmail sender/password, automation mode, quiet hours, blackout dates, biometric lock, channel settings, sync, sign-out, recovery notices, and backup routes.

Business goal: make advanced control possible without harming trust.

User goal: change important preferences and understand consequences.

Ideal UX: settings grouped by outcome: account, privacy, automation behavior, channels, backup, diagnostics.

Current implementation:

- `SettingsViewModel` uses `PreferencesRepository`, `AuthManager`, contact repository, sync, and automation controls.
- `SettingsScreen.kt` is now 338 lines after moving account, preference, and AI/send-readiness configuration sections into `SettingsConfigurationComponents.kt`, automation/delivery controls into `SettingsAutomationComponents.kt`, and data sync, recovery notices, about/version, and sign-out confirmation rendering into `SettingsDataComponents.kt`; shared row/card/toggle primitives remain in the route file.
- SecurePrefs recovery notice is visible.

Gaps:

- Settings owns critical behavior that should be mediated through use cases.
- Destructive sign-out and secret recovery need very clear backup/data-loss language.

Missing functionality:

- Settings search.
- Export-before-signout prompt.
- Per-channel test from settings.

UX issues:

- Dense sensitive settings increase accidental misconfiguration risk.
- AI key, Gmail password, and automation mode need stronger confirmation and validation states.

Accessibility issues:

- Sensitive input fields and toggle descriptions need explicit labels and error states.

Improvements:

- Move preference mutations to domain/data use cases.
- Add "Automation behavior" summary before advanced controls.
- Make sign-out a multi-step confirmation with backup reminder.

Priority: P2.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt`
- `app/src/main/java/com/example/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/com/example/ui/screens/settings/SettingsConfigurationComponents.kt`
- `app/src/main/java/com/example/ui/screens/settings/SettingsAutomationComponents.kt`
- `app/src/main/java/com/example/ui/screens/settings/SettingsDataComponents.kt`
- `core/data/src/main/kotlin/com/example/core/prefs/SecurePrefs.kt`
- `core/data/src/main/kotlin/com/example/core/auth/AuthManager.kt`

### 11. Analytics

Purpose: show relationship health, sent volume, delivery reliability, response, personalization coverage, and neglected contacts.

Business goal: turn activity into retention and improvement loops.

User goal: understand relationship trends and what to improve.

Ideal UX: analytics should generate actions, not only charts.

Current implementation:

- `AnalyticsViewModel` aggregates relationship counts, health buckets, monthly sent, delivery reliability, response, personalization coverage, top neglected contacts, and CSV export.
- `AnalyticsScreen.kt` owns export/share effects, header actions, loading state, and section ordering.
- `AnalyticsReportComponents.kt` renders analytics stats, monthly chart, contact distribution, relationship health, growth metrics, and neglected contacts.
- `AnalyticsChartComponents.kt` renders chart bars, metric denominator labels, distribution rows, and health trend rows.

Gaps:

- Metrics are only partly connected to next actions.
- Denominators and trend explanations need to be obvious to users.

Missing functionality:

- Tap a metric to see the contacts/messages behind it.
- Create action from insight, for example "review these neglected contacts."
- Relationship health goal setting.

UX issues:

- Analytics can feel detached from daily workflows.

Accessibility issues:

- Charts need textual equivalents and not rely on color alone.

Improvements:

- Convert top insights to actionable rows.
- Add drill-down routes into Contacts/Messages filters.
- Show metric definitions inline.

Priority: P2.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/AnalyticsViewModel.kt`
- `app/src/main/java/com/example/ui/screens/analytics/AnalyticsScreen.kt`
- `app/src/main/java/com/example/ui/screens/analytics/AnalyticsReportComponents.kt`
- `app/src/main/java/com/example/ui/screens/analytics/AnalyticsChartComponents.kt`

### 12. Activity History

Purpose: provide an audit trail of sync, generation, dispatch, setup, and message events.

Business goal: increase trust through traceability.

User goal: understand what happened and when.

Ideal UX: a simple timeline with filters and plain-language entries.

Current implementation:

- `ActivityHistoryViewModel` and `ActivityHistoryScreen` expose logged activities.
- `ActivityHistoryLogComponents.kt` renders log cards, type icons, severity/status labels, date formatting, and open-route actions.
- Dispatch and setup paths record structured activity in several places.

Gaps:

- Activity history is useful but should be linked contextually from failed/blocked messages and setup checks.

Missing functionality:

- Entity-specific timeline shortcuts from contact/message/event.
- Export/share diagnostic summary with sensitive redaction.

UX issues:

- If too technical, activity logs can feel like developer output.

Accessibility issues:

- Timeline grouping should use date headings and status text.

Improvements:

- Add "View history" links from message cards, AI Doctor checks, and contact detail.
- Keep developer metadata hidden behind expanders.

Priority: P3.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/ActivityHistoryViewModel.kt`
- `app/src/main/java/com/example/ui/screens/activity/ActivityHistoryScreen.kt`
- `app/src/main/java/com/example/ui/screens/activity/ActivityHistoryLogComponents.kt`
- `core/domain/src/main/kotlin/com/example/domain/repository/ActivityLogRepository.kt`

### 13. Backup And Restore

Purpose: securely export/import user data and recover from device or app state loss.

Business goal: increase trust in local-first storage.

User goal: not lose relationship data, messages, memories, gifts, and settings.

Ideal UX: backup is simple, periodic, encrypted, previewable, and reversible.

Current implementation:

- `BackupRestoreViewModel` supports passphrase strength, export, import preview, and confirm.
- `BackupServiceImpl` enforces max import size, manifest/checksum verification, encrypted export, and preview before replace restore.
- Backup format is versioned.

Gaps:

- Restore is replace-oriented, not merge-oriented.
- Users may not understand what is excluded or overwritten.

Missing functionality:

- Merge restore.
- Scheduled backup reminders with last-backup health.
- Recovery rehearsal/check backup validity.

UX issues:

- Passphrase strength and replace warnings need calm, clear copy.

Accessibility issues:

- File picker and passphrase errors need persistent accessible text.

Improvements:

- Add backup freshness to Home only when stale or before destructive operations.
- Add import diff summary: contacts added, messages restored, settings changed.
- Add merge restore as a later roadmap item.

Priority: P2.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/BackupRestoreViewModel.kt`
- `app/src/main/java/com/example/ui/screens/backup/BackupRestoreScreen.kt`
- `app/src/main/java/com/example/ui/screens/backup/BackupRestoreSecurityComponents.kt`
- `app/src/main/java/com/example/ui/screens/backup/BackupRestoreActionComponents.kt`
- `app/src/main/java/com/example/ui/screens/backup/BackupRestoreStatusComponents.kt`
- `core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt`
- `core/data/src/main/kotlin/com/example/core/backup/BackupDtos.kt`

### 14. Memory Vault

Purpose: store personal notes and memories for a contact, including private notes excluded from AI prompts.

Business goal: improve message personalization and relationship recall.

User goal: capture details and control what AI can use.

Ideal UX: a lightweight memory notebook with visible AI inclusion status.

Current implementation:

- `MemoryVaultViewModel` handles contact header, note search, add, pin, edit, delete, categories, and private notes.
- Private notes are excluded from AI prompt context.
- `MemoryVaultScreen.kt` owns route-level form/edit state, top app bar, search field, loading/empty states, and list ordering.
- `MemoryVaultEditorComponents.kt` renders prompt chips, category chips, add-note form, and inline edit form.
- `MemoryVaultNoteComponents.kt` renders error and memory-note cards.

Gaps:

- Prompt inclusion control should be highly visible.
- Memory notes should show where they influenced a draft.

Missing functionality:

- Per-note "include in AI" toggle distinct from private category.
- Memory suggestions from sent messages or user edits.

UX issues:

- Category systems can be overkill unless tied to use.

Accessibility issues:

- Pin/private/category state should be read as state, not only icon/color.

Improvements:

- Add "Used in next draft" indicator.
- Add contact-level privacy explanation.
- Show private notes in a separate section with a lock label.

Priority: P2.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/MemoryVaultViewModel.kt`
- `app/src/main/java/com/example/ui/screens/memoryvault/MemoryVaultScreen.kt`
- `app/src/main/java/com/example/ui/screens/memoryvault/MemoryVaultEditorComponents.kt`
- `app/src/main/java/com/example/ui/screens/memoryvault/MemoryVaultNoteComponents.kt`

### 15. Gift Advisor

Purpose: manage gift profile, history, and AI-generated gift suggestions.

Business goal: increase relationship value beyond messages.

User goal: pick thoughtful gifts and avoid repeats or bad budgets.

Ideal UX: suggestions should explain fit, budget, duplicates, and next occasion.

Current implementation:

- `GiftAdvisorViewModel` loads contact gift profile, history, suggestions, budget status, duplicate warnings/confidence, add/delete/dismiss suggestion, and generates with AI.
- Suggestions are filtered by budget when possible.
- `GiftAdvisorScreen.kt` is now 313 lines after moving AI suggestion presentation into `GiftAdvisorSuggestionsComponents.kt`, add-gift dialog/form presentation into `GiftAdvisorDialogComponents.kt`, and budget/history rendering into `GiftAdvisorHistoryComponents.kt`.

Gaps:

- Gift planning is contact-specific; there is no cross-contact gift calendar.
- Suggestion rationale should be explicit and editable.

Missing functionality:

- Cross-contact gift planner.
- Purchase status and reminder integration.
- Gift source/link support.

UX issues:

- AI suggestions need provenance: which preferences/memories caused this suggestion.

Accessibility issues:

- Cost, duplicate status, and confidence should be textual, not only badges.

Improvements:

- Add "why this fits" and "record as purchased" flow.
- Link gift suggestions to upcoming events.
- Add gift history warnings into Wish Preview context.

Priority: P3.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/GiftAdvisorViewModel.kt`
- `app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreen.kt`
- `app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorSuggestionsComponents.kt`
- `app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorDialogComponents.kt`
- `app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorHistoryComponents.kt`
- `core/data/src/main/kotlin/com/example/core/gemini/AiServiceImpl.kt`

### 16. Style Coach

Purpose: learn the user's writing style from samples and sent messages.

Business goal: make generated messages feel authentic.

User goal: have AI write like them without manual editing every time.

Ideal UX: show current style profile, what evidence created it, and how it affects drafts.

Current implementation:

- `StyleCoachViewModel` supports profile/history, manual samples, auto analysis from sent messages, and status.
- `StyleCoachScreen.kt` owns route-level state, top app bar, list ordering, and sample parsing.
- `StyleCoachTrainingComponents.kt` renders manual samples, auto analysis, progress, and status feedback.
- `StyleCoachDisplayComponents.kt` renders the learned profile and style preview; `StyleCoachHistoryComponents.kt` renders empty/history snapshot rows.

Gaps:

- It is unclear at draft time exactly how style changed the generated message.

Missing functionality:

- Before/after style influence preview.
- Per-contact style overrides.
- Confidence and sample freshness indicator.

UX issues:

- Style setup is an advanced concept and should be linked from poor draft quality or Wish Preview.

Accessibility issues:

- Sample analysis progress should be announced.

Improvements:

- Add "used style profile" chip in Wish Preview.
- Add style profile freshness and sample count.
- Add an opt-out for style usage per draft.

Priority: P3.

Related files:

- `app/src/main/java/com/example/ui/viewmodel/StyleCoachViewModel.kt`
- `app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachScreen.kt`
- `app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachTrainingComponents.kt`
- `app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachDisplayComponents.kt`
- `app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachHistoryComponents.kt`

### 17. Chat History

Purpose: show recent sent messages for a contact.

Business goal: prevent repetition and improve follow-up context.

User goal: see what was sent before writing again.

Ideal UX: compact searchable history linked from contact and wish review.

Current implementation:

- `ChatHistoryViewModel` maps recent sent messages into UI items and supports search by text/channel.
- `ChatHistoryScreen.kt` renders the list.

Gaps:

- The feature is useful but should be integrated into message generation explanations.

Missing functionality:

- Highlight repeated phrases or duplicate wishes.
- Link from generated draft anti-repeat warning.

UX issues:

- If hidden too deeply, users will not use it when reviewing a draft.

Accessibility issues:

- Message timestamps and channels need clear spoken labels.

Improvements:

- Show last sent snippet in Wish Preview.
- Add duplicate/repetition warning using prior messages.

Priority: P3.

Related files:

- `app/src/main/java/com/example/ui/screens/chat/ChatHistoryViewModel.kt`
- `app/src/main/java/com/example/ui/screens/chat/ChatHistoryScreen.kt`

### 18. Contact Sync And Google Contacts

Purpose: import and refresh relationship data from Google contacts.

Business goal: reduce manual setup and improve contact coverage.

User goal: keep contacts current with minimal effort.

Ideal UX: sync state should be transparent, recoverable, and not block non-sync features unnecessarily.

Current implementation:

- Google contacts sync uses `SecurePrefs` for token access.
- Sync workers and services update contacts and timestamps.
- Sensitive People API URL query params are redacted.

Gaps:

- Initial sync can be attempted implicitly from Home when no contacts exist.
- Permission/account failures should route to a single fix path.

Missing functionality:

- Manual local contact creation/import independent of Google.
- Contact conflict review.
- Sync diff preview for major changes.

UX issues:

- Users may not know whether contacts are missing because of permissions, account scope, network, or sync worker failure.

Accessibility issues:

- Sync error cards need persistent action labels.

Improvements:

- Add one sync readiness model.
- Offer manual contacts when Google sync is blocked.
- Add sync result summary after first import.

Priority: P2.

Related files:

- `core/data/src/main/kotlin/com/example/core/contacts/GoogleContactsSync.kt`
- `core/data/src/main/kotlin/com/example/core/contacts/ContactSyncServiceImpl.kt`
- `core/data/src/main/kotlin/com/example/core/automation/workers/ContactSyncWorker.kt`
- `app/src/main/java/com/example/ui/components/SyncErrorCard.kt`

### 19. AI Generation, Parsing, And Fallbacks

Purpose: generate message variants, classify contacts, suggest gifts, parse model output, and fall back safely.

Business goal: produce high-quality personalized content without breaking workflows when AI fails.

User goal: get a useful draft and understand when it is generic or fallback-generated.

Ideal UX: AI output should include quality confidence, fallback status, and context transparency.

Current implementation:

- `AiServiceImpl` calls Gemini through `GeminiClient`.
- `PromptBuilder` builds message, regeneration, classification, and gift prompts.
- `ResponseParser` parses model output and provides fallback templates.
- `GenerateMessageUseCase` combines contact, style, history, memory, gift, route, approval, and quality gates.

Gaps:

- Prompt context controls need more visibility.
- AI fallback should be surfaced consistently in Wish Preview and Messages.
- A logging issue exists: generated snippet is logged under key `recommended`, which is not a known redacted message-body key.

Missing functionality:

- Full prompt-context preview and per-context exclusion.
- Draft quality reason display.
- Safer audit metrics that never log content.

UX issues:

- Users may not distinguish "personalized AI draft" from "fallback template."

Accessibility issues:

- Regeneration/loading states need clear text and focus stability.

Improvements:

- Remove AI text snippets from logs.
- Add fallback/source indicators on draft cards.
- Add "context used" panel with memory, gift, history, and style sections.

Priority: P1 for logging cleanup, P2 for UX transparency.

Related files:

- `core/data/src/main/kotlin/com/example/core/gemini/AiServiceImpl.kt`
- `core/data/src/main/kotlin/com/example/core/gemini/PromptBuilder.kt`
- `core/data/src/main/kotlin/com/example/core/gemini/ResponseParser.kt`
- `core/data/src/main/kotlin/com/example/core/resilience/SensitiveLogRedactor.kt`
- `core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt`

### 20. Scheduling, Dispatch, Delivery, Notifications, And Recovery

Purpose: send approved/eligible messages at the correct time and recover from failures, reboot, timezone changes, and provider limitations.

Business goal: make "set and forget" safe.

User goal: messages send when intended, and failures are explained.

Ideal UX: users see scheduled sends, blockers, failures, and recovery actions in one place.

Current implementation:

- `DispatchEligibilityPolicy` evaluates approved, pending, fully auto, smart approve, VIP approve, rejected, expired, failed, unsupported, quiet hours, and blackout dates.
- `DispatchMessageUseCase` records dispatch attempts and activity.
- `MessageDispatchWorker` claims dispatch, evaluates eligibility, dispatches via route, records attempts, marks failures, and shows setup notifications for expiry/double-send guard.
- `DailyScheduler` schedules exact send, uses WorkManager fallback, shows setup notification when exact alarm permission is missing, and recovers on boot/time changes.
- Setup/system alert notification tap targets now derive from canonical readiness actions, including `relateai://automation-setup` for setup/AI issues and Messages for expired/double-send warnings.
- Recovery paths include exact send recovery, SMS delivery status recovery, recurring work reconciliation, and event reminder scheduling.

Gaps:

- Dispatch logic is split between use case and worker. This is expected for manual vs background paths, but shared orchestration should be maximized.
- Failure recovery UX should start from the user's message, not the subsystem.

Missing functionality:

- Unified dispatch timeline in UI.
- Dry-run "would this send?" check for every pending message.
- User-facing route fallback explanation before send.

UX issues:

- "Exact alarm", "WorkManager fallback", and "delivery status" are internal concepts. User copy should say "Android may delay this unless exact reminders are allowed."

Accessibility issues:

- Notifications must have clear actions and accessible labels. Existing notification mapper and readiness-route tests help; keep extending them.

Improvements:

- Add a `DispatchReadinessSummary` returned by one domain use case.
- Show delivery/recovery timeline from message card.
- Keep Android-specific scheduling in data, but keep send eligibility pure in domain.

Priority: P1.

Related files:

- `core/domain/src/main/kotlin/com/example/domain/automation/DispatchEligibilityPolicy.kt`
- `core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt`
- `core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt`
- `core/data/src/main/kotlin/com/example/core/automation/scheduler/DailyScheduler.kt`
- `core/data/src/main/kotlin/com/example/core/automation/scheduler/ExactSendRecovery.kt`
- `core/data/src/main/kotlin/com/example/core/automation/sender/MessageDispatcher.kt`

### 21. Widget, Shortcuts, Localization, And Resources

Purpose: provide quick access and localized user-facing text.

Business goal: increase daily engagement and non-English usability.

User goal: see important relationship tasks outside the app and use the app in their preferred language.

Current implementation:

- Manifest includes widget-related components.
- `values` and `values-hi` resources exist.
- Localization parity tests are present.
- No-hardcoded-string regression tests are present for cleaned surfaces.

Gaps:

- Widget behavior was not deeply audited in this pass.
- Localization coverage likely lags behind rapidly changing setup/readiness copy.

Missing functionality:

- Widget-level action triage.
- Pseudo-localization checks.

UX issues:

- Highly technical readiness strings are harder to localize well.

Accessibility issues:

- Widget content descriptions and resize states need dedicated checks.

Improvements:

- Add widget to journey-level QA matrix.
- Add pseudo-localization or long-string screenshot tests for setup/message screens.

Priority: P3.

Related files:

- `app/src/main/java/com/example/widget/`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-hi/strings.xml`
- `app/src/test/java/com/example/ui/LocalizationParityTest.kt`
- `app/src/test/java/com/example/NoHardcodedStringsRegressionTest.kt`

## Workflow Analysis

| Workflow | Ideal user expectation | Current behavior | Main gap | Recommended redesign | Priority |
| --- | --- | --- | --- | --- | --- |
| First launch | Understand value, privacy, and whether messages can send. | Splash/onboarding/auth paths exist; setup later handles many requirements. | Permissions and setup are fragmented. | Add first-run readiness summary with optional manual mode. | P2 |
| Google contact import | Import contacts safely and see what changed. | Sync workers/services and Home initial sync exist. | Failure reasons can be spread across Home, Setup, Settings. | Unified sync readiness and first-import summary. | P2 |
| Daily check-in | See who needs attention now. | Home aggregates metrics, setup, upcoming events, health, backup. | Too many simultaneous concerns. | One ranked next action plus secondary backlog. | P1 |
| Add/fix event | Add event, resolve conflicts, know downstream automation. | Manual event save and duplicate/date conflict warnings exist. | Automation impact preview is not central. | Save summary: message generation, review mode, reminder/send schedule. | P2 |
| Generate draft | Get a personal, safe message. | `GenerateMessageUseCase`, AI service, parser, fallback, quality gate. | Context transparency and fallback explanation are not consistently surfaced. | Draft source/context panel and fallback indicator. | P2 |
| Review and approve | Know if message is safe, ready, and where it will send. | Wish Preview and Messages both expose readiness/actions. | Multiple readiness models. | One canonical readiness summary reused everywhere. | P1 |
| Fully automated send | Trust that it sends only under allowed conditions. | Dispatch policy, exact alarm, fallback, worker, route logic, attempts. | User-facing explanation of route/timing/fallback needs simplification. | Pre-send summary plus dispatch timeline. | P1 |
| Failed send recovery | See what failed and fix it. | Message buckets, setup notifications, attempts, AI Doctor checks. | Recovery starts from subsystem rather than message. | Failed card with one fix action and history. | P1 |
| Backup/restore | Protect local data and restore safely. | Encrypted export, preview, replace restore. | No merge restore and limited rehearsal. | Backup freshness, import diff, merge roadmap. | P2 |
| Privacy management | Know what data is stored, sent to AI, logged, backed up. | Local-first architecture, SQLCipher, SecurePrefs, backup controls. | AI prompt context and logging policy need clearer UI/code guarantees. | Prompt context panel plus content-free logging policy. | P1 |

## Missing Or Weak Product Capabilities

| Area | Missing or weak capability | Impact | Priority |
| --- | --- | --- | --- |
| Manual adoption | Local-only/manual mode without Google sign-in/contact sync. | Users who are privacy cautious may abandon before experiencing value. | P2 |
| Unified action model | One canonical next action across Home, Messages, Wish Preview, Setup. | Cognitive load and inconsistent readiness language. | P1 |
| Prompt context control | User can see and exclude exact context used in AI generation. | Trust and privacy gap. | P2 |
| Journey tests | End-to-end setup/generate/approve/send/recover tests. | Regressions can slip between isolated tests. | P1 |
| Backup merge | Merge restore and import diff. | Replace restore is riskier for real users. | P2 |
| Bulk contact repair | Batch update relationship/channel/context. | Large contact books are expensive to clean manually. | P2 |
| Cross-contact gift planning | Gift calendar and purchase tracking. | Gift Advisor remains a per-contact utility. | P3 |
| Analytics actions | Drill down from metrics to actionable contact/message lists. | Analytics may not change behavior. | P2 |
| Widget QA | Full widget behavior, localization, and accessibility checks. | External surface can regress unnoticed. | P3 |

## Technical Debt And Code Quality

### Resolved: Domain Persistence Boundary Leakage

Evidence:

- `core/domain/build.gradle.kts` no longer depends on Room runtime.
- `core/domain/build.gradle.kts` now applies the Kotlin/JVM plugin instead of the Android library plugin.
- Room entity classes now live under `core/data/src/main/kotlin/com/example/core/db/entities`.
- The DAO projection `RelationshipTypeCount` now lives next to `ContactDao` under `core:data`.
- `core/domain/src/main/kotlin` no longer contains `com/example/core/db` source files.
- `core/domain/src/main/kotlin` no longer imports Android, AndroidX, Room entities, DAOs, or Paging.
- `core/domain/src/main/kotlin/com/example/domain/repository/ContactRepository.kt` now exposes pure contact models and commands; its legacy raw `ContactEntity` methods were removed.
- Removed ContactRepository's unused DAO projection `countByRelationshipType()`, raw health-ranking entity methods, unused raw `getAll()` stream, unused raw `delete(ContactEntity)` command, and remaining legacy raw entity methods; pure `RelationshipAnalyticsCount` and `ContactAnalyticsSummary` methods remain.
- Classification flows now use pure `ContactClassificationProfile` and unclassified `ContactId` lookups instead of fetching full `ContactEntity` records through use-case and worker paths.
- Full-automation enablement now resets per-contact automation overrides through pure `ContactAutomationReadinessProfile` data and a narrow `updateAutomationOverride()` command instead of mutating copied `ContactEntity` records.
- Message generation and regeneration now use pure `ContactMessageGenerationProfile` context instead of loading full `ContactEntity` records for prompt, route, header, automation, and send-time fields.
- Pending-message generation, regeneration, and full-automation promotion now use pure `PendingMessageRecord` data through `MessageRepository`; `PendingMessageEntity` conversion happens in `core:data`.
- Sent-message repository APIs now use pure `SentMessageRecord` data through `MessageRepository`; `SentMessageEntity` conversion happens in `core:data`.
- Manual event saving now uses `ContactHeader` plus narrow manual-contact create/event-date update commands instead of fetching and upserting full `ContactEntity` records.
- Contact sync now merges `ContactSyncRecord` directly and persists through data-layer `upsertSyncedContact()` mapping; the domain `ContactSyncMappers` entity mapper was removed.
- Activity log, diagnostic snapshot, gift history, memory note, message feedback, and style profile persistence mappers now live in `core:data` instead of `core/domain`.
- Dispatch-attempt persistence mappers now live in `core:data` instead of `core/domain`.
- Event entity mappers for `Occasion`, `EventListItem`, and upcoming-event previews now live in `core:data`; domain event mappers only convert between pure event/list models.
- Contact entity mappers for automation, readiness, analytics, prompt, routing, header, list, picker, and dispatch recipient projections now live in `core:data`.
- `BackupService` now accepts pure `BackupDocumentReference` values instead of `android.net.Uri`; app/data layers do Android URI conversion at their boundaries.
- Domain JSON string handling now uses a small Kotlin helper instead of Android's bundled `org.json` runtime.
- `RepositoryBoundaryContractTest` now guards against recreating the removed mapper files in `core/domain`, against reintroducing Room entity imports into domain event mappers, and against Android/AndroidX/Room/DAO imports in domain main source.

Impact:

- The previously identified Room/Paging/entity contract leakage is addressed in current code.
- Future storage changes are less likely to require domain-interface changes.
- Domain tests now run as pure JVM tests through `:core:domain:test`.

Status:

- Resolved in source and covered by regression tests.
- Keep as an architectural invariant.

### P1: Readiness Model Duplication

Evidence:

- Home uses setup progress, banners, and next action policies.
- Messages uses `MessageOperationalReadinessPolicy`.
- Wish Preview uses `WishDraftReadinessPolicy` and `WishPreviewSendSummaryPolicy`.
- Automation Setup uses many `Setup*ReadinessPolicy` classes.
- Dispatch uses `DispatchEligibilityPolicy`.

Impact:

- Users may see inconsistent language or action priority.
- Developers must update many surfaces for one readiness rule.

Fix:

- A canonical readiness contract now exists:

```kotlin
data class RelationshipActionReadiness(
    val state: RelationshipReadinessState,
    val primaryReason: RelationshipReadinessReason,
    val blockers: List<RelationshipReadinessBlocker>,
    val primaryAction: RelationshipReadinessAction,
    val secondaryActions: List<RelationshipReadinessAction>,
    val confidence: RelationshipReadinessConfidence,
    val relatedMessageId: String?,
    val relatedContactId: String?,
    val relatedEventId: String?
)
```

- `RelationshipActionReadinessPolicy` maps existing message operational readiness, wish draft readiness, and setup readiness candidates into the canonical contract.
- Home next actions and readiness banner now carry `RelationshipActionReadiness` for contact sync, contact import, AI setup, pending review, backup, and low-health relationship actions.
- Automation Setup now exposes `RelationshipActionReadiness` for checks, recommended fixes, and the overall setup summary; the top setup banner uses canonical readiness state for severity.
- Messages now carries `RelationshipActionReadiness` on each pending item and uses canonical action-required state/action metadata for queue grouping, failed-send recovery setup counts, and readiness badge labels/severity.
- Messages blocked-card CTAs now route from canonical primary action/reason: contact-detail blockers open Contact Detail preferences, and setup/provider blockers open Automation Setup.
- Wish Preview now exposes canonical draft action readiness and uses it for blank/short draft approval blocking and readiness copy while preserving the existing localized labels.
- Wish Preview send summary now exposes canonical readiness for route blockers, device setup blockers, and dispatch timing/review state; the summary card severity follows canonical readiness state while preserving existing row copy.
- Setup and system-alert notifications now map typed notification requests into `RelationshipActionReadiness` and use canonical primary actions for click-through routing. This also fixes AI fallback alerts opening the stale-backup destination.
- A-003 named-surface adoption is now covered for Home, Messages, Wish Preview, Setup, and setup/system notifications.
- Contract-level readiness journey regression now covers review, channel-repair, and setup/provider-repair paths across those adapters. Remaining risk is UI and recovery journey coverage around the same readiness contract.
- Domain-level message lifecycle regression now runs generation, approval, dispatch, failed retry, and recovered dispatch together against one in-memory pending-message store.
- Worker dispatch tests now assert the `DispatchAttemptEntity.id` created by `MessageDispatchWorker` is the same `dispatchAttemptId` sent to `MessageDispatcher`, protecting provider finalization from being detached from the queued attempt.
- Dispatcher tests now cover retryable SMS provider failures reaching the automatic retry limit, proving the real dispatcher path marks the pending message failed, writes a final failed attempt outcome, avoids another exact-send retry, and records the dead-letter side effect.
- SMS receiver tests now cover delivered-callback provider failure through the Android callback adapter, proving the sent-message row is marked failed, the dispatch attempt is finalized with delivery-callback failure metadata and a dead-letter timestamp, and the pending message is marked failed.
- Messages ViewModel tests now cover successful manual retry for a failed row, proving the UI recovery command queues `RetryFailedMessageUseCase`, records retry activity, clears the retry in-flight marker, and leaves no retry error.
- Navigation contract tests now guard that both default and filtered Messages routes wire failed-send recovery setup actions to `Screen.AutomationSetup.route`. Remaining risk is full UI journey execution plus physical-device provider callback coverage.
- Current-tree verification passed for `:app:testDebugUnitTest`, `:core:domain:test`, `:core:data:testDebugUnitTest`, and `:app:assembleDebug` using Android Studio JBR with the temporary local trust store.
- First-run setup coverage now includes a compile-verified `MainActivity` instrumentation smoke for the onboarding setup checklist action reaching Auth, plus a JVM NavGraph contract proving the same action stores `Screen.AutomationSetup.route` as the post-auth destination. Connected-device execution remains unverified in this shell because `adb` is not on PATH.

### Resolved: Sensitive Message Logging

Evidence:

- `AiServiceImpl` logs the recommended variant name only through `recommendedVariantName`; it does not log generated message text.
- `SensitiveLogRedactor` redacts generated body keys such as `messageText`, `messageBody`, `draftText`, `selectedVariantText`, `recommendedVariantText`, `recommendedText`, variant text fields, `userEditedText`, and `payload`.
- `AiServiceImplTest.generateMessage logs metadata without generated message text` verifies generated AI copy is absent from both `StructuredLogger` history and Android log output.
- `SensitiveLogRedactorTest` verifies future `recommendedVariantText` extras are redacted while `recommendedVariantName` metadata is preserved.

Impact:

- The previously identified generated-message logging risk is addressed in current code.
- The remaining requirement is to keep future log keys explicit: names/labels are metadata; generated text must use body-key redaction or be omitted.

Status:

- Resolved in code and covered by regression tests.
- Keep as a privacy invariant, not an active P1 backlog item.

### P1: Large ViewModels And Screens

Evidence:

- `AutomationSetupViewModel.kt` is now 214 lines after extracting setup UI contracts, account/provider, automation/channel, email, quality, system/recovery readiness presentation, command execution, readiness report loading, AI failure diagnosis, diagnostic snapshot persistence, overall readiness presentation reduction, and Android capability probes.
- `AutomationSetupUiState.kt` owns the AI Doctor setup state/action contracts.
- `AutomationSetupReadinessPresenter.kt` owns summary, recommended-fix, progress, and setup-action-readiness projection, with focused unit coverage.
- `AutomationSetupAccountProviderCheckPresenter.kt` owns Google Contacts, Gemini access, AI wish generation, and Gemini circuit check presentation, with focused unit coverage.
- `AutomationSetupAutomationChannelCheckPresenter.kt` owns full automation, automatable events, delivery routes, channel verification, SMS, and WhatsApp setup check presentation, with focused unit coverage.
- `AutomationSetupEmailCheckPresenter.kt` owns Gmail sender setup check presentation, with focused unit coverage.
- `AutomationSetupQualityCheckPresenter.kt` owns Style Coach, personalization, and generic-message-risk check presentation, with focused unit coverage.
- `AutomationSetupSystemRecoveryCheckPresenter.kt` owns notification, exact-send, daily-automation, recent-health, and dispatch-recovery check presentation, with focused unit coverage.
- `AutomationSetupCommandRunner.kt` owns contact sync, dry-run generation checks, AI test, email test, and WhatsApp consent command messages, with focused unit coverage.
- `AutomationSetupReadinessReportBuilder.kt` owns readiness input loading, policy orchestration, check assembly, dispatch recovery detail loading, channel parsing, and AI Doctor snapshot recording; existing ViewModel readiness tests exercise this path.
- `AutomationSetupAiFailureDiagnoser.kt` owns provider failure classification and redacted AI failure copy, with focused unit coverage.
- `AutomationSetupDiagnosticSnapshotStore.kt` owns persisted HealthMonitor snapshot lookup and redacted AI Doctor snapshot creation, with focused unit coverage.
- `AutomationSetupCapabilityProbe.kt` owns SMS/notification permissions, WhatsApp installation/accessibility checks, Google Contacts access, and Firebase-auth probing, with focused unit coverage.
- `WishPreviewScreen.kt` is now 214 lines after moving send-summary presentation into `WishPreviewSendSummaryComponents.kt`, review/rationale controls into `WishPreviewReviewComponents.kt`, editor rendering into `WishPreviewEditorComponents.kt`, and action/result rendering into `WishPreviewActionComponents.kt`; focused interaction coverage still validates summary labels, readiness copy, rationale panel, feedback/tone actions, confirmation dialogs, approved/rejected states, and review-next.
- `GiftAdvisorScreen.kt` is now 313 lines after moving AI suggestion cards, evidence labels, progress/empty states, and record/dismiss controls into `GiftAdvisorSuggestionsComponents.kt`, add-gift dialog/form rendering into `GiftAdvisorDialogComponents.kt`, and budget/history rendering into `GiftAdvisorHistoryComponents.kt`; focused interaction coverage still validates suggestion actions, dialog validation, record feedback, budget adjustment, and delete actions.
- `EventsScreen.kt` is now 366 lines after moving event-card source, verification, conflict, and resolution-action rendering into `EventsEventCardComponents.kt`, and manual-event dialog/form rendering into companion files; `EventsManualEventDialogComponents.kt` is now 322 lines after moving existing/new contact choice rendering into `EventsManualEventContactComponents.kt`; focused interaction coverage still validates trust labels, merge/keep actions, manual event type options, and existing/new contact mode switching.
- `SettingsScreen.kt` is now 338 lines after moving account/profile, preference toggles, AI key, and email sender rendering into `SettingsConfigurationComponents.kt`, automation mode, quiet hours, and channel blackout rendering into `SettingsAutomationComponents.kt`, and data sync, recovery notices, about/version, and sign-out confirmation rendering into `SettingsDataComponents.kt`; focused interaction coverage still validates navigation, save, sync, recovery notice, backup/history, and sign-out actions.
- `HomeScreen.kt` is now 219 lines after moving dashboard rendering into companion files; `HomeDashboardComponents.kt` is now 219 lines after moving actions/readiness into `HomeActionComponents.kt`; `HomeActionComponents.kt` is now 262 lines after moving quick actions into `HomeQuickActionComponents.kt`; focused interaction coverage still validates Home action navigation.
- `AutomationSetupScreen.kt` is now 236 lines after moving dashboard rendering into companion files; `AutomationSetupDashboardComponents.kt` is now 172 lines after moving setup readiness rendering into companion files; `AutomationSetupReadinessComponents.kt` is now 135 lines after moving setup progress, recommended fix, and diagnostic action buttons into `AutomationSetupActionComponents.kt`; focused interaction coverage still validates setup action buttons and readiness-row actions.
- `StyleCoachScreen.kt` is now 186 lines after moving training input/status rendering into `StyleCoachTrainingComponents.kt` and profile/preview/history rendering into companion files; `StyleCoachDisplayComponents.kt` is now 318 lines after moving history rows into `StyleCoachHistoryComponents.kt`; focused interaction coverage still validates manual/auto analysis controls, status rendering, profile cards, preview, and history rows.
- `MemoryVaultScreen.kt` is now 318 lines after moving prompt/category add and edit forms into `MemoryVaultEditorComponents.kt`, and error/note-card rendering into `MemoryVaultNoteComponents.kt`; focused interaction coverage still validates adding, prompt prefill, categories, search, edit, pin/unpin, delete, empty, error, and validation states.
- `MessagesQueueComponents.kt` is now 101 lines after moving approve/reject/retry/revoke/edit/setup action buttons and approval-mode status into `MessagesActionButtonComponents.kt`, and event/channel/readiness/draft-source badge rendering into `MessagesBadgeComponents.kt`; focused interaction coverage still validates the Messages workflow.
- `BackupRestoreScreen.kt` is now 186 lines after moving the security warning, passphrase input, strength indicator, export/import cards, status card, and confirm-import controls into companion files; `BackupRestoreActionComponents.kt` is now 196 lines after moving status/preview rendering into `BackupRestoreStatusComponents.kt`; focused interaction coverage and the split-file design-token guard still validate the Backup Restore workflow.
- `ContactDetailPreferencesComponents.kt` is now 197 lines after moving preference dialog form body, choice rows, field rendering, skip-auto-wish row, and form helpers into `ContactDetailPreferenceFormComponents.kt`; focused dialog coverage still validates saving, validation, and disabled states.
- `ContactListScreen.kt` is now 309 lines after moving row avatars, health dots, relationship/group text, and quality badge rendering into `ContactListRowComponents.kt`; focused interaction coverage still validates search, filters, sort, row navigation, sync-error actions, and quality badges.
- Several other presentation files remain large, led by `EventsScreen.kt`, `SettingsScreen.kt`, `MessagesListComponents.kt`, and `ContactDetailScreen.kt`.

Impact:

- Harder code review.
- More fragile recomposition/state changes.
- More difficult accessibility and localization review.

Fix:

- Extract pure reducers for UI state transitions.
- Extract readers/builders for screen summaries.
- Split large screens into feature components with stable contracts.
- Keep Android capability checks in data/app adapters, not inside giant ViewModels.

Progress:

- The A-005 slices moved Automation Setup UI state models, account/provider check presentation, automation/channel check presentation, email check presentation, quality check presentation, system/recovery check presentation, command execution, readiness report loading, AI failure diagnosis, diagnostic snapshot persistence, overall readiness presentation reduction, and Android capability probes out of the ViewModel. The ViewModel now mainly observes inputs, applies reports to state, toggles in-flight flags, and exposes test delegates.
- The Wish Preview send-summary card, readiness title coloring, metadata row renderer, and route/device/dispatch label helpers now live in `WishPreviewSendSummaryComponents.kt`; draft readiness copy, review-next result panel, feedback/tone chips, why-this-message panel, quality-message helper, and approve/reject confirmation dialogs now live in `WishPreviewReviewComponents.kt`; tone options, editable message controls, and approve/reject dialog state now live in `WishPreviewEditorComponents.kt`; regenerate/test-send actions and review result states now live in `WishPreviewActionComponents.kt`. `WishPreviewScreen.kt` dropped from 1086 to 214 lines across the splits. `WishPreviewScreenInteractionTest`, static UI guards, `:app:compileDebugKotlin`, and `:app:assembleDebug` passed across the split checks.
- Gift Advisor AI suggestion presentation now lives in `GiftAdvisorSuggestionsComponents.kt`, add-gift dialog/form presentation now lives in `GiftAdvisorDialogComponents.kt`, and budget/history rendering now lives in `GiftAdvisorHistoryComponents.kt`; `GiftAdvisorScreen.kt` dropped from 1066 to 313 lines across the splits. `GiftAdvisorScreenInteractionTest`, static UI guards, `:app:compileDebugKotlin`, and `:app:assembleDebug` passed across the split checks.
- Events event-card presentation now lives in `EventsEventCardComponents.kt`, and manual-event dialog/form presentation now lives in companion files; `EventsScreen.kt` dropped from 978 to 366 lines across the splits. `EventsScreenInteractionTest`, `:app:assembleDebug`, and `:app:compileDebugKotlin` passed across the split checks.
- Events manual contact-choice presentation now lives in `EventsManualEventContactComponents.kt`; `EventsManualEventDialogComponents.kt` is now 322 lines and focused on dialog state, event details, date input, validation, and duplicate/conflict warnings.
- Settings account/profile, preference toggles, AI key, and email sender rendering now live in `SettingsConfigurationComponents.kt`; automation mode, quiet hours, and channel blackout rendering now live in `SettingsAutomationComponents.kt`; data sync, recovery notices, about/version, sign-out trigger, sign-out confirmation dialog, and sign-out checklist rendering now live in `SettingsDataComponents.kt`. `SettingsScreen.kt` dropped from 936 to 338 lines and `SettingsConfigurationComponents.kt` dropped from 487 to 325 lines across the splits. `SettingsScreenInteractionTest`, static UI guards, `:app:compileDebugKotlin`, and `:app:assembleDebug` passed after the split.
- Home stats, relationship planner, and birthday rendering now live in `HomeDashboardComponents.kt`; quick action navigation now lives in `HomeQuickActionComponents.kt`; next-action cards, setup progress card, readiness color mapping, and action title/detail resource mapping now live in `HomeActionComponents.kt`. `HomeScreen.kt` dropped from 767 to 219 lines, `HomeDashboardComponents.kt` dropped from 550 to 219 lines, and `HomeActionComponents.kt` dropped to 262 lines across the splits. The unused `ReadinessBanner` composable was removed because no Home workflow referenced it. `HomeScreenInteractionTest`, static UI guards, `:app:compileDebugKotlin`, and `:app:assembleDebug` passed after the split.
- Analytics stats, report sections, and neglected-contact drill-in now live in `AnalyticsReportComponents.kt`; chart bars, metric denominator labels, distribution rows, and health trend rows now live in `AnalyticsChartComponents.kt`. `AnalyticsScreen.kt` dropped from 456 to 178 lines and `AnalyticsReportComponents.kt` dropped to 228 lines across the splits.
- Activity History route-level state, search, filter rows, and loading/empty/error/list state now live in `ActivityHistoryScreen.kt`; log cards, type icons, severity/status labels, date formatting, and open-route actions now live in `ActivityHistoryLogComponents.kt`. `ActivityHistoryScreen.kt` dropped to 219 lines after the split.
- Automation Setup dashboard shell now lives in `AutomationSetupDashboardComponents.kt`; setup progress, recommended fix, and diagnostic action buttons now live in `AutomationSetupActionComponents.kt`; grouped readiness checks, readiness status helpers, and row actions now live in `AutomationSetupReadinessComponents.kt`; WhatsApp/battery/notification/approval setup cards now live in `AutomationSetupSupportCards.kt`. `AutomationSetupScreen.kt` dropped from 830 to 236 lines, `AutomationSetupDashboardComponents.kt` dropped from 490 to 172 lines, and `AutomationSetupReadinessComponents.kt` dropped to 135 lines across the splits. `AutomationSetupScreenInteractionTest`, static UI guards, `:app:compileDebugKotlin`, and `:app:assembleDebug` passed after the split.
- Style Coach training controls now live in `StyleCoachTrainingComponents.kt`, and learned profile, style preview, and history rendering now live in `StyleCoachDisplayComponents.kt`; `StyleCoachScreen.kt` dropped from 723 to 186 lines and now keeps route-level state collection, top app bar, list ordering, and sample parsing. `StyleCoachScreenInteractionTest`, `:app:compileDebugKotlin`, and `:app:assembleDebug` passed after the split.
- Style Coach history rendering now lives in `StyleCoachHistoryComponents.kt`; `StyleCoachDisplayComponents.kt` is now 318 lines and focused on learned profile metrics plus style preview.
- Memory Vault prompt/category add and edit form rendering now lives in `MemoryVaultEditorComponents.kt`, and error/note-card rendering now lives in `MemoryVaultNoteComponents.kt`; `MemoryVaultScreen.kt` dropped from 678 to 318 lines and now keeps route-level form/edit state, top app bar, search field, loading/empty states, and list ordering. `MemoryVaultScreenInteractionTest`, `:app:compileDebugKotlin`, and `:app:assembleDebug` passed after the split.

### Resolved: Direct SecurePrefs In Presentation

Evidence:

- Source scans show `MainActivity` and app ViewModels no longer import or construct `SecurePrefs`.
- `MainActivity` now injects `PreferencesRepository` for local-only route gating and biometric-lock preference reads.
- `SplashViewModel`, `OnboardingViewModel`, `AutomationSetupViewModel`, `SettingsViewModel`, and `MessagesViewModel` use `PreferencesRepository`.
- `RelateAIApp` still performs `SecurePrefs` warmup as app infrastructure; data-layer workers, backup, auth, Gemini, contacts sync, and dispatch components still use `SecurePrefs` where storage/provider infrastructure belongs.

Impact:

- Presentation code is less coupled to encrypted storage implementation details.
- App-shell route gating remains testable through source contracts while storage details stay behind the repository/data layer.

Fix:

- Routed `MainActivity` local-only and biometric-lock preference reads through injected `PreferencesRepository`.
- Updated the deep-link route-gate contract to require `PreferencesRepository` and reject a direct `SecurePrefs` import in `MainActivity`.
- Keep `SecurePrefs` in data/app infrastructure only.

### Resolved: Dependency And API Surface Cleanup

Evidence:

- `retrofit`, `converter-moshi`, and `logging-interceptor` are absent from `app/build.gradle.kts`, `core/data/build.gradle.kts`, `core/domain/build.gradle.kts`, `gradle/libs.versions.toml`, and `app/proguard-rules.pro`.
- App source does not import OkHttp, Moshi, SQLCipher, Security Crypto, Biometric, JavaMail, Firebase Analytics, or legacy XML Material Components directly.
- `core:data` still owns the data-layer dependencies that support active business/user capabilities: encrypted Room storage, secure preferences, biometric lock support, People API OkHttp sync, Moshi backup/AI parsing, and email sending.
- App unit tests still need JavaMail exception classes for provider retry-policy assertions, so JavaMail remains `testImplementation` in `app`.

Impact:

- Smaller app-level dependency surface.
- Clearer module ownership: app keeps app-shell/UI dependencies, while data-provider integrations stay in `core:data`.
- Lower privacy and binary surface from removing unimplemented Firebase Analytics and legacy XML Material Components from the app runtime.

Fix:

- Removed the duplicate app runtime declarations for SQLite KTX, Security Crypto, Biometric, JavaMail, SQLCipher, Moshi, and OkHttp.
- Removed unused app runtime declarations for Firebase Analytics and Google Material Components.
- Removed the now-unused Firebase Analytics and Material Components version-catalog aliases.
- Added `RepositoryHygieneTest.appBuild_doesNotDeclareDataLayerOnlyRuntimeDependencies()` to prevent regression.
- Validated with `:app:compileDebugKotlin`, focused app unit tests, and `:app:assembleDebug`.

### P2: Worker/Use Case Dispatch Duplication

Evidence:

- `DispatchMessageUseCase` and `MessageDispatchWorker` both evaluate eligibility and record dispatch attempts.
- Shared policy exists, which is good, but orchestration still has parallel paths.

Impact:

- Manual and background dispatch can drift.

Fix:

- Extract shared dispatch orchestration service that both manual and worker paths call.
- Keep WorkManager-specific claim/retry mechanics in the worker.

### P2: Documentation Overlap

Evidence:

- `SSOT.md`, `PLAN.md`, `IMPLEMENTATION_PROGRESS.md`, `PRODUCT_UX_WORKFLOW_TECHNICAL_ANALYSIS.md`, and multiple audit reports overlap.

Impact:

- New contributors may not know which document is authoritative.

Fix:

- Keep `SSOT.md` as architecture/product truth.
- Move historical audit reports under `docs/audits/`.
- Add a short `docs/README.md` explaining document authority.

### P3: Historical Schema/Comment Cleanup

Evidence:

- `AppDatabase.kt` still has commented `// abstract fun moodLogDao(): MoodLogDao`.
- Migrations create/drop `mood_logs` historically.

Impact:

- Comment is noise.
- Migration code should not be deleted because old users may still migrate.

Fix:

- Remove the commented DAO line only.
- Keep historical migration SQL.

## Dead Code, Unused Files, And Safe-To-Delete Candidates

Do not delete files automatically without approval because the working tree is dirty and some files may be local evidence.

### Safe To Delete Or Move After Approval

| Path/pattern | Why it appears safe | Action |
| --- | --- | --- |
| `app_logs.txt`, `app_logs_more.txt`, `app_logs_more_utf8.txt`, `app_logs_utf8.txt` | Local log artifacts; ignored by `.gitignore`; no build dependency found. | Delete or move to external archive. |
| `logcat_cold.txt`, `logcat_cold_more.txt`, `logcat_dump.txt` | Local diagnostic captures; ignored. | Delete or archive. |
| `logs/*.log` | Local runtime/tool logs; ignored. | Delete or archive. |
| `lint_baseline_pre_fixes.txt` | Historical local lint artifact; ignored. | Delete after confirming not needed for comparison. |
| `scripts/patch_app_build2.py`, `scripts/patch_app_dep.py`, `scripts/patch_settings.py`, `scripts/patch_ui.py` | One-off patch helpers; only documentation references found. | Delete after owner approval. |
| `app/src/test/screenshots/greeting.png` | Ignored stray generated screenshot; not a Roborazzi baseline path. | Delete after visual owner confirms. |
| `.codepulse/`, `.intelligence/`, `.gradle-user-home/`, `.gradle/`, `.kotlin/`, `.idea/`, build dirs | Local/generated/tool state. | Do not track; delete locally if space/noise matters. |
| `.github/modernize/` | Ignored local modernization tool state. | Delete if not actively used. |

### Safe To Delete From Active Source After Verification

| Path/pattern | Why | Required verification |
| --- | --- | --- |
| `app/schemas/com.example.core.db.AppDatabase/4.json`, `5.json`, `6.json`, `.gitkeep` | Active Room schema export is `core/data/schemas`. Docs already mark `app/schemas` as legacy/local generated. | Run migration tests and compile after deletion. |
| Additional app-level runtime dependencies | App now keeps data-provider implementations in `core:data` and app-only declarations only where app source or tests need them. | Keep `RepositoryHygieneTest.appBuild_doesNotDeclareDataLayerOnlyRuntimeDependencies()` green. |

### Keep

| Path/pattern | Reason |
| --- | --- |
| `core/data/schemas/com.example.core.db.AppDatabase/*.json` | Active Room schema history for migrations. |
| `app/src/test/screenshots/baseline/**` | Roborazzi screenshot baselines. |
| `app/google-services.json` and debug provider configs if intentionally allowlisted | CI/hygiene tests allow configured provider files while guarding server secrets. |
| `scripts/extract_strings.sh` | Referenced by helper script tests. |
| Historical migration SQL in `AppDatabase.kt` | Required for users upgrading from old versions. |
| `docs/startup-idea/**` | Archived reference docs; not active product contract but tests/documentation recognize them as archived. |

## Performance Review

Strengths:

- WorkManager is used for background work.
- Exact alarm fallback exists.
- Boot/timezone recovery exists.
- SQLCipher and Room provide structured local persistence.
- Contact list loading no longer exposes Paging through the domain boundary.

Risks:

- Large Compose screens can be hard to optimize and reason about.
- ViewModels combining many flows can cause recomposition churn if UI state is too broad.
- AI generation, sync, backup, and dispatch all touch user-critical data and need careful dispatcher usage.
- Contact list may need real paging if users have large address books, but the current paging API is not visibly consumed.

Recommendations:

- Add performance tests or traces for Home, Messages, Wish Preview, Contact List, and AI Doctor.
- Ensure expensive readiness checks are cached or scoped by screen.
- Either use contact paging in UI or remove it until needed.
- Add dispatch worker latency and retry metrics without logging content.

## Security And Privacy Review

Strengths:

- Local-first design.
- `allowBackup=false`.
- SQLCipher and encrypted preferences.
- Biometric lock support.
- Backup encryption, checksum/manifest, import size guard, preview before restore.
- Redaction for emails, phones, tokens, API keys, Google People API query params, and known message body keys.
- Release signing guardrails.

Risks:

- AI generated message snippet logging under unredacted key.
- Keep `SecurePrefs` isolated to data/app infrastructure so secret-handling details do not spread back into presentation.
- Gmail sender/password handling is sensitive and should have clear setup/testing/revocation UX.
- Sign-out clears WorkManager, Room tables, prefs, DB files, and revokes accounts; this is thorough but highly destructive.
- Prompt context sent to AI includes sensitive relationship data. The user needs stronger visibility and control.

Recommendations:

- Adopt a strict "no message content in logs" rule.
- Add tests for message content redaction using realistic generated messages.
- Keep SecurePrefs access behind repository/use-case APIs in presentation code.
- Add a prompt context preview and opt-out controls.
- Add export-before-destructive-signout UX.

## Accessibility Review

Strengths:

- Accessibility label regression tests exist.
- Design system token tests exist.
- Large-font and Hindi screenshot baselines exist.
- No-hardcoded-string tests exist for cleaned surfaces.

Risks:

- Dense diagnostic, settings, message, and wish preview surfaces can overwhelm screen reader users.
- Charts need textual equivalents.
- Icon-only actions in newly added components need continuous coverage.
- Bulk selection needs clear selected state announcements.
- Dialog focus after check/fix actions must be stable.

Recommendations:

- Add journey-level TalkBack test scripts for: first setup, approve message, recover failed send, backup restore.
- Ensure all badges have text equivalents.
- Add long-string/pseudo-localization screenshots for the largest screens.
- Keep using semantic headings and stable focus after async actions.

## Scalability Review

Product scalability:

- The current feature set is broad enough that new features should be added only if they reduce missed relationship actions or increase trust.
- More analytics/gift/style features should feed the same action model, not create more independent dashboards.

Technical scalability:

- Domain boundary cleanup is the most important scalability task.
- Worker and readiness orchestration should be centralized before more channels/providers are added.
- Backup schema and migrations need continued discipline as data model grows.
- Repository interfaces should be model-based, not database-entity-based.

Operational scalability:

- No backend reduces server complexity, but device-local automation is more fragile across Android versions, OEM policies, exact alarm changes, and permission models.
- The AI Doctor is a necessary product feature, but it should become user-friendly setup repair rather than broad diagnostics.

## QA And Test Strategy Gaps

Existing strengths:

- Unit tests across ViewModels, domain policies, workers, repository, backup, and dispatch paths.
- Roborazzi visual baselines.
- CI guardrails for production readiness, hygiene, design tokens, localization, and accessibility labels.

Recommended additions:

| Test area | Suggested coverage | Priority |
| --- | --- | --- |
| First-run journey | Onboarding/auth/contact permission/setup/home readiness. | P1 |
| Message lifecycle | Event -> generate -> review -> approve -> schedule -> dispatch -> sent history. | P1 |
| Failed dispatch recovery | Provider failure -> message failed -> user fix -> retry -> sent. | P1 |
| Privacy logging | Generated message text never appears in StructuredLogger history/log output. | P1 |
| Backup restore | Export, preview, wrong passphrase, replace restore, post-restore navigation. | P2 |
| Deep links | Locked, logged out, onboarded, and setup-blocked states. | P2 |
| Large-font diagnostics | AI Doctor and Wish Preview at accessibility font sizes. | P2 |
| Widget | Widget render, click actions, empty/error states. | P3 |

## Refactoring Plan

### Quick Wins

1. Remove generated message snippet logging from `AiServiceImpl`.
2. Add a regression test proving message content is not logged.
3. Delete or archive local logs and one-off patch scripts after approval.
4. Remove the commented `moodLogDao` line.
5. Move old audit reports into `docs/audits/` after confirming document policy.
6. Add `docs/README.md` that declares `SSOT.md` as the current source of truth.

### High-Impact Refactors

1. Create canonical readiness/action model.
2. Reuse readiness model in Home, Messages, Wish Preview, Setup, and notifications.
3. Keep `core:domain` as a JVM module and prevent platform/storage imports from returning.
4. Keep the split `AutomationSetupViewModel` collaborators stable and covered while moving new setup behavior into focused collaborators.
5. Keep Wish Preview split components stable while moving new editor/action behavior into focused companion files.
6. Split remaining large screens or components such as `EventsScreen`, `SettingsScreen`, `MessagesListComponents`, and `ContactDetailScreen`, and continue decomposing large component/helper files as needed.
7. Extract shared dispatch orchestration used by manual dispatch and worker dispatch.

### Long-Term Architecture Roadmap

Phase 1: Privacy and cleanup

- Remove message snippet logging.
- Clean local/generated artifacts.
- Verify dependency removals.
- Add logging tests.

Phase 2: Readiness unification

- Define canonical readiness state.
- Map existing policies into it.
- Update Home, Messages, Wish Preview, and Setup to consume it.
- Add journey tests.

Phase 3: Domain purity

- Keep Room entities and DAO projections in data/database.
- Keep repository boundaries on pure domain/model records.
- Keep `core:domain` as a JVM module.
- Keep mapping and boundary tests.

Phase 4: UX simplification

- Home becomes action-first.
- AI Doctor becomes guided repair-first.
- Wish Preview becomes readiness-first.
- Settings becomes outcome-grouped.

Phase 5: Advanced product improvements

- Manual/local-only mode.
- Backup merge restore.
- Cross-contact gift planner.
- Analytics drill-down actions.
- Prompt context controls.

## Proposed Product Roadmap

### 0-2 Weeks

- Fix AI logging privacy issue.
- Clean safe local artifacts.
- Add journey test skeletons.
- Add canonical readiness design document.
- Remove or verify unused dependencies.

### 2-6 Weeks

- Implement canonical readiness model.
- Refactor Home/Messages/Wish Preview to use it.
- Convert AI Doctor to ranked fix flow.
- Add message lifecycle end-to-end test.
- Keep domain JVM boundary guards green.

### 6-12 Weeks

- Complete domain module build cleanup.
- Add manual/local-only onboarding path.
- Add prompt context controls.
- Add dispatch timeline UI.
- Add backup import diff.

### 3-6 Months

- Add merge restore.
- Add cross-contact gift planner.
- Add analytics-to-action workflows.
- Add widget QA coverage.
- Add full accessibility journey tests.

## Priority Backlog

| ID | Task | Priority | Owner area |
| --- | --- | --- | --- |
| A-003 | Expand journey-level regression coverage beyond the current contract-level review/channel/setup readiness paths into UI/use-case flows across Home, Messages, Wish Preview, Setup, and notifications. | P1 | App/QA |
| A-004 | Keep `core:domain` JVM/persistence boundary guards green while adding future domain policies. | P2 | Architecture |
| A-005 | Primary `AutomationSetupViewModel` split is complete; keep the new collaborators covered and continue large-presentation cleanup across remaining oversized screens. | P2 | App/Architecture |
| A-006 | Expand message lifecycle journey coverage from the current domain, worker-attempt bridge, dispatcher retry-limit, SMS receiver callback, Messages retry-command, Messages recovery route-contract, and onboarding setup post-auth route regressions into connected UI journey execution and physical-device provider finalization paths. | P1 | QA |
| A-007 | Expand failed dispatch recovery coverage from the current domain retry, worker-attempt bridge, dispatcher retry-limit finalization, delivered-callback receiver, Messages retry-command, and Messages recovery route-contract regressions into physical-device provider callback and full UI recovery flows. | P1 | QA |
| A-008 | Clean local logs, patch scripts, and legacy app schemas after approval. | P2 | Repo Hygiene |
| A-009 | Keep dependency-surface hygiene guarded after removing app-level data-layer/runtime-unused declarations. | P2 | Build |
| A-010 | Add manual/local-only onboarding mode. | P2 | Product/App |
| A-011 | Add prompt context preview and controls. | P2 | Product/AI |
| A-012 | Add backup import diff and export-before-signout prompt. | P2 | Security/Product |
| A-013 | Add analytics drill-down actions. | P2 | Product/App |
| A-014 | Add widget accessibility and journey tests. | P3 | QA |

## Final Assessment

RelateAI is technically ambitious and significantly more complete than a basic reminder app. Its strongest engineering assets are the policy extraction work, local-first security, WorkManager/scheduler/recovery infrastructure, backup safeguards, and regression testing discipline. Its biggest product opportunity is to compress that capability into fewer user-facing concepts.

The next stage should not be more features by default. It should be trust consolidation:

- one readiness model
- one next-action model
- no private content in logs
- pure domain boundaries
- guided setup and recovery
- journey-level tests

If those are addressed, the existing feature set can become a coherent product rather than a collection of powerful screens.
