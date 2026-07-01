# RelateAI Codebase, Product, UX, Workflow, and Architecture Audit

Date: 2026-07-01
Audited repository: `/Users/yashsomani/Desktop/Android Project/AI-Birthday`
Audit method: static source review of Gradle modules, app navigation, ViewModels, domain use cases, data services, workers, tests, documentation, and tracked repository artifacts.
Status: Detailed audit report. This supersedes the older product/UX report dated 2026-06-26 for current-state findings.

## 1. Executive Summary

RelateAI is no longer just a birthday reminder. The current codebase implements a local-first relationship automation app with Google/device contact sync, relationship events, AI message generation, wish review, automatic scheduling, SMS/WhatsApp/email delivery, activity logs, analytics, memory/gift/style personalization, AI setup diagnostics, encrypted backup/restore, SQLCipher storage, biometric lock, and release-readiness tests.

The strongest product direction is clear: this should become a trusted relationship operations center. The user should open the app and immediately know which relationship action needs attention, which messages are safe to send, which setup item is blocking automation, and whether recovery/backup/privacy are healthy.

The codebase is broadly functional and has a serious test culture, but the audit found several high-impact gaps:

| Priority | Finding | Impact | Primary evidence |
| --- | --- | --- | --- |
| P0 | Fully automatic AI quality gate does not downgrade fallback/generic/low-quality nonblank messages. | Fallback or generic content can be approved for full automation, undermining trust in the core automation promise. | `core/domain/src/main/kotlin/com/example/domain/automation/AiAutoSendQualityGate.kt:48` |
| P1 | Foreground/manual dispatch path does not pass quiet hours or blackout dates into shared dispatch eligibility. | A manual/domain dispatch caller can send during a blocked window even though worker dispatch respects it. | `DispatchMessageUseCase.kt:49`, `DispatchEligibilityPolicy.kt:118`, `MessageDispatchWorker.kt:71` |
| P1 | The operational readiness model is duplicated across Messages, AI Doctor, channel selection, and dispatch policy. | Users can see one screen say "ready" while another path defers, blocks, or requires setup. | `MessagesViewModel.kt:556`, `AutomationSetupViewModel.kt`, `AutoSendChannelSelector`, `DispatchEligibilityPolicy` |
| P1 | Repository contains tracked local/generated artifacts and an over-broad `.gitignore` that ignores valid app assets. | Builds and reviews are noisier; real assets appear as ignored/tracked; private diagnostic logs are in git history. | `.gitignore`, `git ls-files -i -c --exclude-standard` |
| P1 | Documentation is materially stale. | Product and architecture docs describe older modules and Room schema v13 while code is at five modules and schema v16. | `settings.gradle.kts`, `AppDatabase.kt`, `SSOT.md` |
| P2 | Domain module contains Room entities and some UI paths consume data entities directly. | Module boundaries are not clean; future feature modules and testing get harder. | `core/domain/src/main/kotlin/com/example/core/db/entities/*`, `ChatHistoryViewModel.kt:7` |
| P2 | Large screens/ViewModels concentrate too much workflow logic. | Change risk is high for automation setup, events, settings, wish review, and gift advisor. | `AutomationSetupViewModel.kt` 1262 lines; `EventsScreen.kt` 978 lines; `WishPreviewScreen.kt` 933 lines |

Implementation update on 2026-07-01:

- Fixed the P0 AI auto-send quality gate so fallback/generic/blank low-quality messages in automatic modes downgrade to manual review.
- Aligned the foreground/manual dispatch path with worker dispatch by passing quiet hours and blackout dates into `DispatchEligibilityPolicy`.
- Aligned Messages email readiness with shared domain sender-email validation so invalid configured sender addresses stay blocked.
- Narrowed `.gitignore` media rules so valid Android assets are no longer hidden by global image-extension ignores.
- Untracked verified local/generated artifacts, including local Gradle cache files, tool diagnostics, app/logcat dumps, stale lint snapshots, the stray screenshot, and legacy app-level schema exports.
- Updated `SSOT.md` to include the active `:core:model` module and Room schema version 16.
- Improved Gift Advisor suggestions with UI-state confidence, budget-fit evidence, duplicate-history warnings, and a record-suggestion shortcut that pre-fills the gift form.
- Made automation defaults review-first: missing/unsupported global automation preferences, unknown resolver modes, and transient Settings UI state now fall back to `ALWAYS_ASK` instead of `FULLY_AUTO`.
- Hardened manual event saving so unsupported event type input returns a localized validation error instead of persisting `OccasionType.UNKNOWN`.
- Removed the Chat History UI-state dependency on `SentMessageEntity` by mapping sent records into a domain `ChatHistoryMessageItem` before rendering.
- Added a local dismiss/ignore action for Gift Advisor AI suggestions so irrelevant ideas can be cleared from the review list.
- Added a Style Coach message-style preview so users can see the learned opening, tone, length, and emoji rules that will influence generated messages.
- Added Chat History search/filter across message text and channel so users can find prior sent messages without scanning the whole history.
- Remaining open work: decide repository policy for tracked Google/Firebase service config files, and consolidate readiness state across feature surfaces.

## 2. Product Assessment

### 2.1 Product Positioning

RelateAI is best described as a relationship operations assistant:

- It imports contacts from Google and the device.
- It discovers birthdays, anniversaries, work anniversaries, and manual events.
- It stores relationship context, memory notes, style signals, gift history, and contact preferences.
- It generates AI messages and variants.
- It supports review, editing, regeneration, feedback, and approval.
- It schedules and sends through SMS, WhatsApp, and email.
- It provides diagnostics, analytics, activity history, recovery, and encrypted backups.

The app has enough surface area to support a premium automation product. The remaining product challenge is trust. Every automated action must be explainable, reversible, and consistent across screens.

### 2.2 Business Goals

| Goal | Current support | Gap |
| --- | --- | --- |
| Reduce missed relationship moments | Strong event discovery, reminders, scheduled message generation, and Home planning exist. | The next-best action model is still distributed across Home, Messages, AI Doctor, Analytics, and Contact Detail. |
| Increase confidence in automated sends | Approval modes, wish preview, exact alarm scheduling, route checks, and diagnostics exist. | The quality gate flaw allows fully automatic low-quality fallback content. |
| Improve personalization quality over time | Memory vault, style coach, gift advisor, previous wishes, and feedback exist. | There is no single personalization quality score or clear "what to add next" per contact. |
| Make setup self-healing | AI Doctor checks many dependencies and recommends fixes. | Fix flows are still diagnostic-heavy; readiness is duplicated elsewhere. |
| Protect private relationship data | SQLCipher, encrypted preferences, backup encryption, biometric lock, and redaction are present. | Prompt inclusion controls and logged contact-name redaction need more explicit governance. |
| Support release readiness | CI, lint, Roborazzi, localization parity, security tests, and production config tests exist. | Tracked local artifacts, stale docs, and module-boundary drift reduce maintainability. |

### 2.3 End User Assessment

The app can serve three user modes:

| User mode | What works | Main pain point |
| --- | --- | --- |
| Careful reviewer | Messages queue, Wish Preview, edit/regenerate, approve/reject, review-next. | Needs clearer reasons for blocked/scheduled/ready states before acting in bulk. |
| Automation-first user | Full automation mode, exact scheduling, workers, channel selection, AI Doctor. | Must trust that only high-quality, route-ready, time-safe messages can auto-send. That trust is currently weakened by the quality gate. |
| Relationship builder | Contact health, memory notes, gift advisor, style coach, analytics. | Insights are not actionable enough; the app should route directly from insight to the next relationship task. |

## 3. Architecture and Module Review

### 3.1 Current Module Structure

Actual modules from `settings.gradle.kts`:

| Module | Purpose observed |
| --- | --- |
| `:app` | Compose UI, navigation, ViewModels, MainActivity, app shell, widgets, tests. |
| `:core:model` | Shared model module now included in the build. |
| `:core:domain` | Use cases, policies, repositories, domain services, and currently also Room entities. |
| `:core:data` | Room database/DAOs, repositories, auth, sync, AI, backup, workers, dispatch, notifications. |
| `:core:ui` | Shared UI/theme components. |

The older documentation says active modules exclude `:core:model`; that is no longer true.

### 3.2 Boundary Problems

| Problem | Evidence | Why it matters | Recommendation |
| --- | --- | --- | --- |
| Room entities live inside `:core:domain`. | `core/domain/src/main/kotlin/com/example/core/db/entities/*` imports `androidx.room.*`. | Domain is not persistence-independent. This makes domain tests and future data replacement harder. | Move Room entities to `:core:data` or a dedicated `:core:database` module. Map entities to domain models at repository boundaries. |
| UI consumes persistence entities directly. | `ChatHistoryViewModel.kt` imports `com.example.core.db.entities.SentMessageEntity`. | UI state leaks database schema. Schema changes can break UI directly. | Return a domain/UI list model such as `ChatHistoryMessageItem`. |
| App ViewModels inject `SecurePrefs` directly. | `MessagesViewModel`, `SettingsViewModel`, `AutomationSetupViewModel`, `SplashViewModel`, `OnboardingViewModel`. | UI makes policy decisions from storage primitives instead of a domain readiness/preferences contract. | Use `PreferencesRepository` and a shared readiness use case for feature ViewModels. |
| Readiness logic is duplicated. | Messages readiness, AI Doctor checks, channel selector, dispatch policy. | Product copy and behavior can diverge. | Introduce a `DispatchReadinessUseCase` or `MessageOperationalStatePolicy`. |
| Documentation and build metadata drift. | Room schema version is 16; `SSOT.md` references 13. Build includes `:core:model`; older docs do not. | New contributors and release audits will make wrong assumptions. | Update `SSOT.md`, product blueprint, and older analysis files after priority fixes. |

### 3.3 Proposed Folder and Module Structure

Target structure after cleanup and incremental moves:

```text
app/
  src/main/java/com/example/app/
  src/main/java/com/example/navigation/
  src/main/java/com/example/feature/
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
  src/main/java/com/example/widget/

core/model/
  contact/
  event/
  message/
  automation/
  analytics/
  backup/
  activity/

core/domain/
  contact/{model,usecase,policy,repository}
  event/{model,usecase,policy,repository}
  message/{model,usecase,policy,repository}
  automation/{policy,usecase}
  backup/{model,usecase}
  analytics/{model,usecase}
  style/{model,usecase}
  memory/{model,usecase}
  gift/{model,usecase}

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

docs/
  architecture/
  product/
  operations/
  testing/
  security/

tools/
  scripts/
```

Migration sequence:

1. Add shared domain/UI models for message readiness and chat history.
2. Move Room entities out of domain after repository mapping is in place.
3. Consolidate feature packages so each feature owns its screen and ViewModel together.
4. Move stale product/architecture docs into `docs/` and update the single source of truth.
5. Remove tracked local artifacts and correct `.gitignore` so valid launcher assets are not ignored.

## 4. Feature-by-Feature Analysis

### 4.1 App Shell, Navigation, Locking, and Permissions

| Field | Assessment |
| --- | --- |
| Purpose | Provide authenticated app entry, bottom navigation, deep links, biometric lock, and runtime permission prompts. |
| Business goal | Keep the app secure while making the main relationship workflows reachable. |
| User goal | Open the app, unlock if needed, and reach Home, Contacts, Events, Messages, or Analytics quickly. |
| Current implementation | `MainActivity` gates UI with biometric settings, shows bottom navigation for Home/Contacts/Events/Messages/Analytics, and requests SMS/notification permissions from primary routes. `NavGraph` protects authenticated routes with Firebase auth. |
| UX issues | Contacts permission is not requested in the same central shell path; users may discover it through sync failures or setup. Settings and recovery surfaces are deeper routes even though they are operationally important. |
| Accessibility | Bottom nav labels are present; permission rationale is explicit. Continue checking all icon buttons in secondary screens. |
| Missing functionality | A single system-readiness entry point reachable from every blocked message state. |
| Suggested redesign | Add a compact global status affordance in the app bar or Home header that routes to AI Doctor/Setup when automation is blocked. |
| Priority | P2 |
| Related files | `app/src/main/java/com/example/MainActivity.kt`, `app/src/main/java/com/example/navigation/NavGraph.kt`, `app/src/main/java/com/example/navigation/Screen.kt` |

### 4.2 Onboarding and Authentication

| Field | Assessment |
| --- | --- |
| Purpose | Introduce the product and authenticate users with Google/Firebase. |
| Business goal | Secure contact sync and cloud AI-backed workflows. |
| User goal | Start quickly and understand what setup is still needed. |
| Current implementation | Splash routes to onboarding, auth, or Home. Onboarding is a static checklist; Auth uses Google sign-in and validates web client configuration. |
| UX issues | No local-only/guest mode exists. Users who want manual contacts must still sign in. Setup tasks do not execute during onboarding; they are deferred. |
| Missing functionality | Permission preflight, local-only mode, and a first-run setup progress path that ends in a working first message. |
| Suggested redesign | Turn onboarding into a task-based setup flow: sign in, contacts source, first event import, AI/channel readiness, backup reminder. |
| Priority | P2 |
| Related files | `SplashViewModel`, `OnboardingViewModel`, `AuthViewModel`, `NavGraph` |

### 4.3 Home

| Field | Assessment |
| --- | --- |
| Purpose | Daily command center for relationship work. |
| Business goal | Drive retention by presenting the next valuable action. |
| User goal | Know what to do today without scanning the whole app. |
| Current implementation | Home builds pending/upcoming/contact/sent counts, setup progress, backup prompt, planner items, and ranked primary/supporting actions. The ranking includes sync fixes, pending review, AI setup, backup freshness, and reconnect actions. |
| What improved since older docs | Home now has a ranked next-action model and backup freshness prompt; the older report's main Home recommendation is partly implemented. |
| UX issues | The ranking remains local to Home and not shared with Analytics, Messages, or AI Doctor. Planner items mix operational setup and relationship tasks. |
| Missing functionality | A unified "next best relationship action" service that every feature can emit into. |
| Suggested redesign | Introduce a `NextBestActionUseCase` with typed actions, reason, urgency, destination, and completion state. |
| Priority | P2 |
| Related files | `HomeViewModel`, `HomeScreen`, `AnalyticsViewModel`, `AutomationSetupViewModel` |

### 4.4 Contacts List

| Field | Assessment |
| --- | --- |
| Purpose | Manage imported/manual relationship records and their readiness. |
| Business goal | Increase personalization coverage and message deliverability. |
| User goal | Find contacts that need missing information fixed. |
| Current implementation | Contacts support filters for relationship groups, low health, VIP, missing relationship/channel/personalization, search, sort, and sync refresh. |
| UX issues | Relationship filters rely on raw string categories such as family/friends/work. Bulk fixing is limited. |
| Missing functionality | Bulk enrich/fix workflows, source trust labels in the list, and contact-level "why quality is low" actions. |
| Suggested redesign | Add a quality triage view: missing event, missing route, missing relationship, no memory, low health. Each row should have one clear fix action. |
| Priority | P2 |
| Related files | `ContactListViewModel`, contact repositories, contact sync use cases |

### 4.5 Contact Detail and Preferences

| Field | Assessment |
| --- | --- |
| Purpose | Show relationship context and allow per-contact preferences and message generation. |
| Business goal | Personalize automation and increase trust. |
| User goal | Understand what the app knows about a person and control how it contacts them. |
| Current implementation | Contact Detail loads profile, memory counts, category summaries, next upcoming event, preferences, and can generate a wish for the next event. |
| UX issues | Message generation is tied to the next upcoming event; there is no event picker for users who want a different occasion. |
| Missing functionality | Inline explanation of missing AI/channel readiness when generation is blocked. |
| Suggested redesign | Add a contact readiness panel with event picker, route status, personalization score, and "improve this contact" actions. |
| Priority | P2 |
| Related files | `ContactDetailViewModel`, `GenerateMessageUseCase`, `WishPreviewViewModel` |

### 4.6 Events

| Field | Assessment |
| --- | --- |
| Purpose | Discover, review, create, and manage relationship occasions. |
| Business goal | Ensure the app has reliable triggers for message generation. |
| User goal | Know which events are trusted and fix duplicates/conflicts. |
| Current implementation | Events supports filters, manual creation, existing/new contact creation, duplicate/date conflict warnings, event trust state, and rejects unsupported manual event types before persistence. |
| UX issues | The screen is large and likely doing too much in one composable. Resolved 2026-07-01: manual event type normalization no longer persists `UNKNOWN` for unsupported domain input. |
| Missing functionality | Bulk event confirmation, clear source provenance, and an event-to-message preview path for each event. |
| Suggested redesign | Split Events into calendar/list, conflict review, and manual create components. Use typed event kinds end to end. |
| Priority | P2 |
| Related files | `EventsViewModel`, `EventsScreen`, `SaveManualEventUseCase`, `DiscoverEventsUseCase`, `EventResolutionPolicy` |

### 4.7 AI Message Generation

| Field | Assessment |
| --- | --- |
| Purpose | Generate personalized message variants from contacts, events, style, memory, gifts, and previous wishes. |
| Business goal | Deliver differentiated AI value. |
| User goal | Get a message that feels personal and safe to send. |
| Current implementation | `GenerateMessageUseCase` prevents duplicate pending messages, loads personalization context, retries if generated text is too similar to previous wishes, shows fallback alerts, selects a route, schedules exact sends, and inserts pending drafts. |
| Critical issue | `AiAutoSendQualityGate` scores fallback/generic/short text down but keeps `FULLY_AUTO` when the message is merely nonblank. A fallback message can become approved and scheduled automatically. |
| Missing functionality | Hard contract that fallback, generic, very short, or low-score messages always require review in automatic modes. |
| Suggested redesign | Make quality gate return a typed `requiresReview` decision with reasons. Add tests for FULLY_AUTO fallback, generic phrase, too short, blank, edited-by-user, and route unavailable cases. |
| Priority | P0 |
| Related files | `AiAutoSendQualityGate.kt`, `GenerateMessageUseCase.kt`, `RegeneratePendingMessageUseCase.kt`, `EnableFullAutomationUseCase.kt` |

### 4.8 Messages Queue

| Field | Assessment |
| --- | --- |
| Purpose | Operational queue for messages needing review, scheduled messages, blocked messages, sent messages, and failed messages. |
| Business goal | Convert generated drafts into safe sends and recover failures. |
| User goal | See what needs review, what is scheduled, what is blocked, and what failed. |
| Current implementation | Messages supports channel filters, sorting, search, scheduled/blocked/sent/failed buckets, bulk approve/reject/retry, and readiness labels. |
| UX issues | Readiness checks only cover contact missing, channel disabled, phone/email missing, email setup missing, approved, dispatching, and failed. They do not fully model scheduled time, quiet hours, blackout dates, exact alarm state, notification permission, WhatsApp consent/accessibility, or SMS permission. |
| Missing functionality | Shared operational readiness contract with the dispatch worker and AI Doctor. |
| Suggested redesign | Replace screen-local readiness with `MessageOperationalState` from domain: `needsReview`, `scheduled`, `blocked`, `deferred`, `sendableNow`, `failedRecoverable`, `failedTerminal`, with action route and reason. |
| Priority | P1 |
| Related files | `MessagesViewModel.kt:556`, `DispatchEligibilityPolicy`, `AutoSendChannelSelector`, `AutomationSetupViewModel` |

### 4.9 Wish Preview

| Field | Assessment |
| --- | --- |
| Purpose | Review, explain, edit, test, regenerate, approve, or reject a generated draft. |
| Business goal | Make AI output trustworthy before delivery. |
| User goal | Understand why the draft was generated and decide quickly. |
| Current implementation | Wish Preview loads variants, contact/event context, memory/gift/previous-wish signals, quality/fallback state, review-next support, test send, regeneration, feedback, approve, and reject. |
| UX issues | The screen is high-value but dense. Activity log strings in feedback paths are partly hardcoded. |
| Missing functionality | Better traceability from feedback to future prompt changes and a clearer "what changed after regenerate" comparison. |
| Suggested redesign | Split into message editor, quality/why panel, delivery plan, and review actions. Add diff after regeneration. |
| Priority | P2 |
| Related files | `WishPreviewViewModel`, `WishPreviewScreen`, `RegeneratePendingMessageUseCase`, `MessageFeedbackRepository` |

### 4.10 Automation Setup / AI Doctor

| Field | Assessment |
| --- | --- |
| Purpose | Diagnose whether contacts, AI, channels, permissions, scheduling, workers, and recovery are ready. |
| Business goal | Help users reach reliable automation without support. |
| User goal | Fix the next blocker and know when automation is safe. |
| Current implementation | AI Doctor has a very broad readiness model, grouped statuses, ranked recommended fixes, diagnostic snapshots, tests, channel checks, recovery checks, and deep actions. |
| UX issues | The ViewModel is a 1262-line hotspot. Diagnostics are strong, but users need a guided fix sequence rather than a long report. |
| Missing functionality | Shared readiness output used by Home, Messages, Settings, and notifications. |
| Suggested redesign | Keep diagnostics as the engine, but expose a simpler UI: one blocking fix, then warnings, then optional details. |
| Priority | P1 |
| Related files | `AutomationSetupViewModel`, `AutomationSetupScreen`, `HealthMonitorDiagnosticRecorder`, `DiagnosticSnapshotRepository` |

### 4.11 Dispatch, Scheduling, and Delivery

| Field | Assessment |
| --- | --- |
| Purpose | Send approved or fully automatic messages at the right time through available channels. |
| Business goal | Reliably deliver relationship messages without unsafe automation. |
| User goal | Trust that messages are sent only when allowed. |
| Current implementation | Worker dispatch uses `DispatchEligibilityPolicy` with quiet hours and blackout dates. `DailyScheduler` adjusts scheduled times and uses exact alarms or WorkManager fallback. Route selection considers SMS, WhatsApp, email setup, channel blackout, contact availability, and history. |
| Critical issue | `DispatchMessageUseCase` calls `DispatchEligibilityPolicy.evaluate(draft = pending.draft)` without quiet hours or blackout dates, so policy defaults can return `SendNow`. |
| Missing functionality | A single dispatch entry point that always receives current preferences and route readiness. |
| Suggested redesign | Inject `PreferencesRepository` into `DispatchMessageUseCase` or route all sends through a dispatch coordinator that supplies current preferences. Add regression tests that manual/foreground dispatch defers during quiet hours and blackout dates. |
| Priority | P1 |
| Related files | `DispatchMessageUseCase.kt:49`, `DispatchEligibilityPolicy.kt:118`, `MessageDispatchWorker.kt:71`, `DailyScheduler` |

### 4.12 Activity History

| Field | Assessment |
| --- | --- |
| Purpose | Provide operational traceability for messages, dispatch, setup, backup, and automation. |
| Business goal | Build confidence and support recoverability. |
| User goal | Understand what happened and reopen the relevant item. |
| Current implementation | Loads recent logs, filters by type/date/status/search, and can route through action route strings. |
| UX issues | Recent history is capped to 100 and filtered in memory. Action routes are stringly typed. |
| Missing functionality | Paging, typed destinations, export/debug bundle, and stronger grouping by incident/message. |
| Suggested redesign | Use Paging and typed activity actions. Group related dispatch attempts under a message incident. |
| Priority | P2 |
| Related files | `ActivityHistoryViewModel`, `ActivityLogRepository`, dispatch attempt repositories |

### 4.13 Analytics

| Field | Assessment |
| --- | --- |
| Purpose | Show relationship health, personalization coverage, reliability, response rate, and neglected contacts. |
| Business goal | Turn automation data into habit-forming insight. |
| User goal | Know which relationship needs attention next. |
| Current implementation | Builds totals, relationship counts, health counts, monthly trends, reliability/response/personalization percentages, top neglected contacts, and export report. |
| UX issues | Insights are mostly descriptive and not directly actionable. Top neglected contacts are strings rather than clickable models. |
| Missing functionality | Insight-to-action routing and explanation of metric denominators. |
| Suggested redesign | Convert analytics cards into typed insights with target route, severity, and recommended action. |
| Priority | P2 |
| Related files | `AnalyticsViewModel`, analytics repositories/use cases |

### 4.14 Backup and Restore

| Field | Assessment |
| --- | --- |
| Purpose | Protect local relationship data with encrypted export/import. |
| Business goal | Reduce churn and support device changes. |
| User goal | Know data is recoverable before a device loss. |
| Current implementation | Backup uses encrypted v3 payloads, passphrase strength gating for export, preview before import, transactional replace restore, size limit, and excludes OAuth/API/email/password/sync secrets. Home and Settings surface backup freshness. |
| UX issues | Backup strength rules are modest. Users do not get a durable "last backup contains X records" summary. |
| Missing functionality | Scheduled backup reminders, recovery rehearsal, backup age warning severity, and passphrase recovery education. |
| Suggested redesign | Add backup health as a first-class Home/Settings status with record counts, age, and restore-tested state. |
| Priority | P2 |
| Related files | `BackupRestoreViewModel`, `BackupRestoreScreen`, `BackupServiceImpl`, `SecurePrefs` |

### 4.15 Memory Vault

| Field | Assessment |
| --- | --- |
| Purpose | Store relationship details that improve personalization. |
| Business goal | Increase AI quality and user lock-in through private context. |
| User goal | Capture preferences, milestones, gifts, and private notes. |
| Current implementation | Supports categories, notes, pin/unpin, delete, and contact header context. |
| UX issues | No edit flow, no search, no sensitivity labels, and no per-note prompt eligibility. |
| Missing functionality | Privacy controls for AI prompt inclusion. |
| Suggested redesign | Add note edit/search and a sensitivity toggle such as "use for AI suggestions" vs "private reference only." |
| Priority | P2 |
| Related files | `MemoryVaultViewModel`, memory repositories, prompt context builders |

### 4.16 Gift Advisor

| Field | Assessment |
| --- | --- |
| Purpose | Track gift history and suggest gifts from relationship context. |
| Business goal | Expand product value beyond messages. |
| User goal | Remember past gifts and find appropriate new ideas. |
| Current implementation | Shows profile, history, spending, budget, add/delete gift record, AI suggestions, budget-fit evidence, confidence, duplicate-history warnings, dismiss/ignore, and a record-suggestion shortcut that pre-fills the gift form. |
| UX issues | Large screen file. Suggestions are now more explainable, but the screen still mixes budget, suggestion review, history, and record-entry workflows in one file. |
| Missing functionality | Adjust-budget action, use-in-message action, and persisted feedback about whether a suggestion was useful. |
| Suggested redesign | Treat suggestions as draft gift records with actions: save, dismiss, adjust budget, use in message, and explain why a suggestion appeared. |
| Priority | P2 |
| Related files | `GiftAdvisorViewModel`, `GiftAdvisorScreen`, `PromptBuilder`, gift repositories |

### 4.17 Style Coach

| Field | Assessment |
| --- | --- |
| Purpose | Learn user writing style and apply it to generated messages. |
| Business goal | Make AI output feel personal to the sender. |
| User goal | Train the app to write more like them. |
| Current implementation | Supports manual sample training, auto analysis from sent messages, style profile history, heuristic analysis, cumulative sample count, visible confidence level, and a message-style preview derived from the learned profile. |
| UX issues | The analysis is heuristic, not AI-backed. Resolved 2026-07-01: `sampleCount` now accumulates analyzed evidence instead of reflecting only the current batch size. |
| Dead-code candidate | Resolved 2026-07-01: unused `findCommonPhrases` in `StyleAnalysisUseCase` was removed after reference verification. |
| Missing functionality | Before/after generated-message comparison using the learned profile. |
| Suggested redesign | Expand the preview into a live compare flow that shows a generic draft beside the personalized version. |
| Priority | P3 |
| Related files | `StyleAnalysisUseCase.kt`, `StyleCoachViewModel`, `StyleCoachScreen` |

### 4.18 Chat History

| Field | Assessment |
| --- | --- |
| Purpose | Show sent message history for one contact. |
| Business goal | Help avoid repetition and preserve context. |
| User goal | See what was sent before. |
| Current implementation | Loads up to 100 sent messages for a contact from `MessageRepository`, maps them into `ChatHistoryMessageItem`, and supports in-screen search by message text or channel. |
| Architecture issue | Resolved 2026-07-01 for this screen: `ChatHistoryUiState` no longer exposes `SentMessageEntity`. Broader repository/entity leakage remains open in domain/data boundaries. |
| Missing functionality | Paging, resend/regenerate-from-history, and a dedicated domain use case returning `ChatHistoryMessageItem`. |
| Suggested redesign | Return `ChatHistoryMessageItem` from a use case and use Paging if history grows. |
| Priority | P2 |
| Related files | `ChatHistoryViewModel.kt:7` |

### 4.19 Settings

| Field | Assessment |
| --- | --- |
| Purpose | Configure account, AI, email, automation, quiet hours, biometric lock, channel blackout, sync, and sign-out. |
| Business goal | Centralize high-risk configuration. |
| User goal | Safely control automation and delivery channels. |
| Current implementation | Settings reads/writes encrypted prefs, supports full automation enablement, email settings, quiet hours, blackout, biometric, sync, backup timestamps, and sign-out. Resolved 2026-07-01: transient Settings UI state and missing/unsupported global automation preferences now default to `ALWAYS_ASK` instead of `FULLY_AUTO`. |
| UX issues | It is a dense, high-risk screen. The riskiest default-display problem is resolved, but enabling full automation is still a high-stakes setting that would benefit from a guided preflight path. |
| Missing functionality | Safer progressive automation setup and clearer "test before enabling" path. |
| Suggested redesign | Split Settings into Account, Automation, Channels, Privacy/Security, Backup. Make automation escalation a guided flow through AI Doctor. |
| Priority | P1/P2 |
| Related files | `SettingsViewModel.kt:43`, `SettingsScreen`, `EnableFullAutomationUseCase`, `SecurePrefs` |

## 5. Workflow Analysis

### 5.1 First Setup

| Step | Current flow | Pain point | Ideal flow | Expected effort reduction |
| --- | --- | --- | --- | --- |
| Start | Splash -> Onboarding -> Auth. | Onboarding explains but does not complete setup tasks. | A guided checklist that finishes with one imported/created contact and one previewable event. | Medium |
| Contact source | Google auth and later sync/device import. | Device contacts permission is not as centrally staged as SMS/notifications. | Choose Google, device, manual, or both; show what each unlocks. | Medium |
| AI setup | Settings/API key/Firebase checks and AI Doctor. | User must know where to go when AI is blocked. | AI Doctor drives one recommended fix at a time. | High |
| Delivery setup | SMS/WhatsApp/email permissions and credentials. | Channel readiness is scattered. | Unified channel setup with test send and route status. | High |
| Backup | Backup appears in Settings/Home freshness. | Backup is easy to postpone. | First-run backup reminder after first meaningful data import. | Medium |

### 5.2 Daily Relationship Command Center

Current flow: Home shows counts, planner, setup progress, backup prompt, and primary/supporting actions.

Ideal flow: Home should be generated from a shared action engine:

1. Relationship action due today.
2. Message needing review.
3. Blocker preventing automation.
4. Failed send needing recovery.
5. Backup/privacy/security warning.

Pain point: Home's ranking is useful but local. Messages, AI Doctor, and Analytics should contribute typed actions into the same queue.

### 5.3 Contact Sync and Enrichment

Current flow: Google/device sync merges contact records, derives events, and surfaces quality filters in Contacts.

Gaps:

- Multi-source sync errors can under-explain which source failed.
- Users need stronger guided enrichment for missing relationship, missing route, missing event, and missing personalization.
- Contact source trust and conflict history should be clearer in the list.

Ideal flow: A contact triage queue with grouped issues and bulk fixes.

### 5.4 Event-to-Message

Current flow:

1. Event exists or is created manually.
2. Message generation loads context.
3. AI variants are parsed or fallback text is generated.
4. Route and approval mode are selected.
5. Pending message is inserted and scheduled/notified.
6. User reviews in Wish Preview or Messages.

Critical gap: Low-quality fallback content can remain `FULLY_AUTO`. This breaks the ideal rule: "automation may schedule only messages that are high-quality, route-ready, and time-safe."

### 5.5 Review and Approval

Current flow: Messages and Wish Preview support review, edit, regenerate, approve, reject, revoke, bulk approve, bulk reject, retry, and review-next.

Gaps:

- Messages readiness is less complete than dispatch readiness.
- Bulk approval should surface a preflight summary of blocked/deferred/route-risk messages.
- Feedback should clearly influence future suggestions.

Ideal flow: Every approve action should show the same typed decision used by dispatch: sendable now, scheduled for later, blocked, needs setup, or requires review.

### 5.6 Dispatch and Recovery

Current flow: Workers respect quiet hours and blackout dates; `DailyScheduler` adjusts exact send times; failures are recorded and can be retried.

Gaps:

- Foreground/domain dispatch path misses quiet hours/blackout.
- Activity history is capped and not paged.
- Failure recovery should deep-link to the exact missing permission/channel/credential.

Ideal flow: One dispatch coordinator supplies current preferences and route state to every send path.

### 5.7 Backup and Restore

Current flow: User exports/imports encrypted backups with passphrase, import preview, and transactional restore.

Gaps:

- Backup health could be more prominent.
- Users need clearer passphrase permanence education.
- Restore preview could show more user-friendly record summaries.

Ideal flow: Home/Settings show backup age, record counts, and "last restore preview/tested" state.

## 6. Broken or Risky Technical Findings

### P0. Fully Automatic Quality Gate Does Not Downgrade Bad AI Output

Evidence:

- `AiAutoSendQualityGate.evaluate` scores fallback text to 35 and generic phrases to 55.
- Its final mode logic returns `FULLY_AUTO` for any nonblank message when requested mode is `FULLY_AUTO`.
- `GenerateMessageUseCase` sets pending status to `APPROVED` when the chosen approval mode is `FULLY_AUTO`.
- `EnableFullAutomationUseCase` promotes pending messages by reusing the same flawed gate.

Impact:

- AI fallback, generic, or very short content can be automatically approved and scheduled.
- This contradicts the product's trust model.

Recommendation:

- Change quality gate logic so any `score < FULLY_AUTO_MIN_SCORE` or `isUsingFallback` downgrades automatic modes to `ALWAYS_ASK`.
- Keep an explicit exception only for user-edited or user-approved messages, not raw AI fallback.
- Add unit tests covering FULLY_AUTO fallback, generic phrase, short text, blank text, SMART_APPROVE downgrade, and high-quality pass.

### P1. Manual/Foreground Dispatch Does Not Enforce Quiet Hours or Blackout

Evidence:

- `DispatchMessageUseCase` calls `DispatchEligibilityPolicy.evaluate(draft = pending.draft)` without current preferences.
- `DispatchEligibilityPolicy` returns `SendNow` when quiet hours or blackout JSON are null.
- `MessageDispatchWorker` correctly passes `preferencesRepository.getQuietHoursStart()`, `getQuietHoursEnd()`, and `getBlackoutDates()`.

Impact:

- Worker sends are time-safe; other dispatch callers can bypass user quiet hours/blackout settings.

Recommendation:

- Inject `PreferencesRepository` into `DispatchMessageUseCase`.
- Supply quiet hours and blackout dates in every call to `DispatchEligibilityPolicy`.
- Add tests for both approved and FULLY_AUTO pending messages during blocked windows.

### P1. Readiness State Is Not Single-Source

Status on 2026-07-01: partially addressed. Messages now uses `EmailAddressSyntaxPolicy.isConfiguredSender(...)` for email sender readiness, matching route selection/generation for that rule. A complete shared readiness output across Home, Messages, Wish Preview, AI Doctor, notifications, and dispatch is still open.

Evidence:

- Messages readiness is screen-local and only checks route basics.
- AI Doctor has a broader setup model.
- Dispatch policy owns schedule/time eligibility.
- Channel selector owns route availability.

Impact:

- A user can see inconsistent "ready", "blocked", "scheduled", or "needs setup" states across screens.

Recommendation:

- Create one domain readiness output that includes route, permission, schedule, approval, quality, and recovery decisions.
- Use it in Home, Messages, Wish Preview, AI Doctor, notifications, and dispatch.

### P1. Repository Hygiene and Ignore Rules Are Unsafe

Status on 2026-07-01: addressed for verified generated/local artifacts. The global media ignore rules were narrowed so app assets are trackable, and local Gradle/tool diagnostics, app/logcat dumps, stale lint snapshots, the stray screenshot, and legacy app-level schema exports were removed from git tracking. `app/google-services.json` and `app/src/debug/google-services.json` remain tracked pending an explicit public/private repository policy decision.

Evidence:

- Before the 2026-07-01 cleanup, tracked ignored files included local diagnostics, Gradle cache files, `.codepulse`, `.intelligence`, logcat dumps, app logs, lint baseline snapshots, Firebase config files, and launcher icons.
- Before the 2026-07-01 cleanup, `.gitignore` globally ignored `*.png`, `*.jpg`, `*.svg`, `*.webp`, etc. This caused valid app assets such as launcher icons to be ignored.

Impact:

- Review noise and accidental local artifact commits.
- Legitimate asset changes can be hidden by ignore rules.
- Diagnostic logs may contain private local state.

Recommendation:

- Keep media ignores scoped to generated report/output folders only.
- Keep launcher icons and approved screenshot baselines trackable.
- Keep verified local/generated artifacts out of git.
- Decide whether checked-in Google/Firebase service config is acceptable for this repository. If public, rotate and move config generation to secrets/local setup.

### P1. Documentation Drift

Evidence:

- `settings.gradle.kts` includes `:core:model`.
- `AppDatabase` is version 16.
- `SSOT.md` references older module state and schema version 13.
- Existing June 26 analysis includes recommendations that are now partly implemented.

Impact:

- Architecture reviews and release checks can be based on stale assumptions.

Recommendation:

- Update `SSOT.md`, `PRODUCT_BLUEPRINT.md`, and existing progress docs after P0/P1 fixes.
- Move historical planning content under `docs/archive/` if it is no longer current.

### P2. Domain Contains Database Entities

Evidence:

- `core/domain/src/main/kotlin/com/example/core/db/entities/*` uses Room annotations.

Impact:

- Domain module is coupled to Android Room.
- Persistence schema changes leak across layers.

Recommendation:

- Move entities to data/database module.
- Keep domain interfaces and domain models clean.
- Add mapper tests during migration.

### P2. Large Files Increase Regression Risk

Hotspots:

| File | Lines |
| --- | ---: |
| `AutomationSetupViewModel.kt` | 1262 |
| `EventsScreen.kt` | 978 |
| `WishPreviewScreen.kt` | 933 |
| `GiftAdvisorScreen.kt` | 1012 |
| `SettingsScreen.kt` | 889 |
| `HomeScreen.kt` | 765 |
| `MessagesViewModel.kt` | 620 |
| `WishPreviewViewModel.kt` | 561 |
| `AppDatabase.kt` | 699 |

Recommendation:

- Extract UI sections into stable components.
- Extract readiness/action building into domain or feature services.
- Add focused tests before splitting behavior-heavy ViewModels.

### P2. Privacy Controls for Personalization Are Incomplete

Evidence:

- Memory notes and gifts can improve prompts.
- Redaction exists for logs, but contact names and personal relationship context can still appear in operational metadata or prompts.

Impact:

- Users may want to store private notes without allowing AI prompt use.

Recommendation:

- Add per-note/per-category prompt eligibility.
- Add a privacy review screen that explains what data can enter AI prompts.
- Expand structured logger redaction to names where practical or avoid logging names entirely.

### P3. Unused Helper in Style Analysis

Status on 2026-07-01: resolved. The unused private helper was removed and `StyleAnalysisUseCaseTest` still passes.

Evidence:

- Before cleanup, `StyleAnalysisUseCase.findCommonPhrases` had only its definition as a reference.

Recommendation:

- Keep the active bigram-based phrase extraction path covered by tests; do not add a second phrase extractor unless the product needs a distinct signal.

## 7. Dead, Unused, or Cleanup Candidate Files

This section separates safe cleanup from files that only look suspicious.

### 7.1 Safe Cleanup Candidates

| Path/pattern | Why it exists | References found | Safe to remove from git? | Notes |
| --- | --- | --- | --- | --- |
| `.gradle-user-home/**` | Local Gradle cache/locks/native binaries. | No app/runtime dependency. | Removed from tracking | Should never be tracked. |
| `.codepulse/workflow/events.json` | Local/tool workflow metadata. | No app/runtime dependency found. | Removed from tracking | Keep only if the team intentionally version-controls this tool state. |
| `.intelligence/enterprise-diagnostics/*.json` | Local diagnostics output. | No app/runtime dependency found. | Removed from tracking | Diagnostic output should be regenerated, not tracked. |
| `app_logs*.txt` | Local app log captures. | No app/runtime dependency found. | Removed from tracking | Archive externally if needed for incident evidence. |
| `logcat_*.txt` | Local logcat captures. | No app/runtime dependency found. | Removed from tracking | Same as above. |
| `lint_baseline_pre_fixes.txt` | Historical lint snapshot. | No build dependency found. | Removed from tracking | Keep only in docs if it explains a migration. |
| `app/src/test/screenshots/greeting.png` | Stray screenshot image outside approved baseline folder. | No code reference found. | Removed from tracking | Approved screenshot baselines live under `app/src/test/screenshots/baseline/`. |
| `app/schemas/com.example.core.db.AppDatabase/4-6.json` and `.gitkeep` | Older app-level Room schemas. | Active schema export is in `core/data/schemas`. | Removed from tracking | `app/schemas/` is ignored as legacy/local generated output. |

### 7.2 Do Not Remove Without Replacement

| Path/pattern | Reason |
| --- | --- |
| `app/src/main/res/mipmap-*/ic_launcher*.webp` | Valid Android launcher assets. The issue is `.gitignore`, not the assets. |
| `app/google-services.json`, `app/src/debug/google-services.json` | Used by Google/Firebase configuration. If this repository is public, rotate/remove and provide secret-backed config. If private, document policy. |
| `scripts/extract_strings.sh` | Referenced by docs/tests; keep. |
| `PRODUCT_BLUEPRINT.md`, `SSOT.md`, `PRODUCT_UX_WORKFLOW_TECHNICAL_ANALYSIS.md` | Stale but not dead. Update or archive; do not delete blindly. |

### 7.3 `.gitignore` Fixes

Status on 2026-07-01: completed for the global media rules. `.gitignore` now scopes generated media ignores to report/output/screenshot-diff folders and leaves app assets trackable.

Previous `.gitignore` globally ignored common app asset extensions:

```text
*.png
*.jpg
*.jpeg
*.gif
*.svg
*.webp
```

Recommendation:

- Keep global media ignores removed.
- Ignore generated media only by folder, for example `/reports/**`, `/tmp/**`, `/exports/**`.
- Keep explicit exceptions for approved screenshot baselines if needed.

## 8. Testing and QA Assessment

### 8.1 Strengths

- CI runs unit tests, lint, debug assemble, Roborazzi screenshot verification, production readiness checks, and coverage.
- Security/release tests check backup exclusions, pin expiry, signing guardrails, exact alarm permission choice, debug signing fallback, and auth release copy.
- Localization parity and screenshot tests exist, including Hindi and large font scenarios.
- Several domain policies and use cases have focused tests.

### 8.2 Gaps

| Gap | Risk | Recommendation |
| --- | --- | --- |
| No regression appears to cover FULLY_AUTO fallback downgrade. | Critical trust bug survived. | Add `AiAutoSendQualityGateTest` and use-case tests for generation/promotion. |
| Messages readiness tests cannot validate full dispatch readiness because readiness is screen-local. | UI can claim wrong operational state. | Test shared readiness use case once introduced. |
| Accessibility regression checks selected source files, not every action icon/screen. | Secondary features may regress. | Expand a11y checks to feature folders or add Compose UI tests for primary workflows. |
| Hardcoded string checks cover a curated list. | Hardcoded user-facing/activity strings remain possible elsewhere. | Expand reviewed source list or use a stricter lint/custom detector. |
| Activity/history scaling is not tested with large data sets. | In-memory filtering and 100-row caps may hide problems. | Add performance tests or migrate to Paging. |

### 8.3 QA Workflow Coverage Needed

Add end-to-end or integration tests for:

1. Full automation enabled + AI fallback -> draft requires review, no exact send scheduled.
2. Full automation enabled + generic phrase -> draft requires review.
3. Manual/foreground dispatch during quiet hours -> deferred, not sent.
4. Manual/foreground dispatch during blackout date -> deferred, not sent.
5. Messages blocked state matches AI Doctor setup state for missing SMS, email, WhatsApp accessibility, and channel blackout.
6. Enable full automation should not promote fallback/generic/low-quality pending messages.
7. Backup export/import excludes secrets and preserves non-secret automation preferences.

## 9. Security and Privacy Review

### 9.1 Strengths

- `allowBackup=false` in manifest.
- SQLCipher encrypted database with random key for fresh installs.
- Encrypted preferences for API keys, OAuth token, email app password, and sync token.
- Backup excludes sensitive secrets.
- Biometric lock support.
- Certificate pinning with expiry check.
- Release signing guardrails.
- Structured logging and diagnostic redaction exist.

### 9.2 Risks and Recommendations

| Risk | Priority | Recommendation |
| --- | --- | --- |
| Checked-in Firebase/Google service config may be inappropriate for a public repo. | P1 if public, P3 if private | Decide policy. If public, rotate and move config to secret/local generation. |
| Direct `Log` usage and operational metadata may include contact names/context. | P2 | Prefer structured logger everywhere and avoid logging names. |
| Memory notes have no prompt-eligibility privacy control. | P2 | Add "use for AI" controls and prompt data preview. |
| Gmail app password storage is encrypted but high-risk. | P2 | Make setup copy explicit, require test send, and explain revocation. |
| Certificate pins expire on 2027-06-01. | P3 now, P1 near expiry | Add calendar/release task before 2027-04-01. |
| Encrypted prefs corruption handling can clear setup state. | P3 | Add user-facing recovery message when secure prefs are rebuilt. |

## 10. Accessibility Review

### Strengths

- Bottom navigation labels and many explicit content descriptions exist.
- Roborazzi large-font and Hindi screenshot coverage exists.
- Accessibility label regression tests exist for selected source files.

### Gaps

| Area | Issue | Recommendation |
| --- | --- | --- |
| Dense screens | Settings, Events, Wish Preview, Gift Advisor, and AI Doctor are information-heavy. | Verify large font, TalkBack order, and scroll affordances per screen. |
| Charts/analytics | Visual metrics need text equivalents. | Ensure every chart has textual summary and action. |
| Icon actions | Regression scan is curated, not global. | Expand test scope and add UI tests for secondary feature action buttons. |
| Loading/error states | Some screens have complex async states. | Use clear semantics, live regions where appropriate, and retry actions. |

## 11. Performance and Scalability Review

| Area | Current state | Risk | Recommendation |
| --- | --- | --- | --- |
| Lists | Contacts/messages/events/activity often filter/sort in memory. | Large contact/message histories can lag. | Adopt Paging for high-growth lists. |
| Activity history | Loads recent 100 and filters locally. | Older incidents disappear from user search. | Use paged queries with DB filtering. |
| Compose screens | Several screens exceed 800-900 lines. | Recomposition and maintenance risk. | Extract components and stable state models. |
| AI rate limit | In-memory limiter. | Process restarts/workers can bypass global quota assumptions. | Persist quota window if quota cost matters. |
| Backup import | 25MB limit and transactional replace. | Good safety baseline. | Add progress for large backups if needed. |
| Scheduler | Exact alarm with WorkManager fallback and boot/time recovery. | Strong implementation. | Ensure fallback copy never implies exact delivery. |

## 12. Product Roadmap

### Immediate Fixes (P0/P1)

1. Fix `AiAutoSendQualityGate` so fallback/generic/short/low-score content cannot remain fully automatic.
2. Add tests for quality-gate downgrade and `EnableFullAutomationUseCase` promotion behavior.
3. Make `DispatchMessageUseCase` pass quiet hours and blackout dates.
4. Add dispatch tests for manual/foreground blocked windows.
5. Make missing/unsupported global automation defaults review-first instead of fully automatic.
6. Define a shared readiness model and start by replacing Messages readiness.
7. Clean tracked local artifacts and narrow `.gitignore`.
8. Update `SSOT.md` for `:core:model` and Room schema v16.

### Short Term (Next 2-4 Iterations)

1. Convert AI Doctor into a guided fix flow while retaining diagnostics.
2. Add `NextBestActionUseCase` for Home, Messages, Analytics, and AI Doctor.
3. Split Settings into safer sections and route full automation enablement through preflight.
4. Add event picker in Contact Detail message generation.
5. Add personalization quality score and contact triage actions.
6. Add memory prompt-eligibility/privacy controls.
7. Convert analytics insights into typed actions with destination routes.

### Medium Term

1. Move Room entities out of domain.
2. Replace direct UI database entity usage with domain/UI models.
3. Introduce Paging for activity, chat history, messages, and large contact lists.
4. Split large Compose screens into components.
5. Add typed navigation/action routes for activity logs and insights.
6. Persist AI quota/rate state if cost controls matter.

### Long Term

1. Feature-module extraction after domain/data boundaries are clean.
2. Full privacy review screen showing what data enters AI prompts.
3. Advanced recovery center with incident grouping and support export.
4. Multi-channel verification dashboard.
5. Backup rehearsal and migration assistant.

## 13. Recommended First Pull Requests

### PR 1: Automation Trust Fix

Scope:

- Fix `AiAutoSendQualityGate`.
- Add quality gate tests.
- Add `GenerateMessageUseCase` and `EnableFullAutomationUseCase` tests for fallback/generic behavior.

Acceptance criteria:

- `FULLY_AUTO + fallback` becomes `ALWAYS_ASK`.
- `FULLY_AUTO + generic` becomes `ALWAYS_ASK`.
- `SMART_APPROVE + low score` remains downgraded.
- High-quality nonfallback content can remain automatic.

### PR 2: Dispatch Policy Consistency

Scope:

- Inject preferences into `DispatchMessageUseCase`.
- Pass quiet hours and blackout dates to `DispatchEligibilityPolicy`.
- Add tests for quiet hours and blackout.

Acceptance criteria:

- Foreground/manual dispatch defers during blocked windows.
- Worker dispatch behavior remains unchanged.

### PR 3: Repository Hygiene

Scope:

- Remove tracked local Gradle caches, local diagnostics, app/logcat dumps, stale lint snapshot, and stray screenshot.
- Fix `.gitignore` global media patterns.
- Decide Firebase config policy.

Acceptance criteria:

- `git ls-files -i -c --exclude-standard` contains only intentionally tracked exceptions.
- Launcher icon asset changes are visible to git.
- CI still passes.

### PR 4: Current-State Documentation

Scope:

- Update `SSOT.md` for actual modules, Room v16, active schema path, and current Home/Messages/AI Doctor state.
- Mark the June 26 analysis as historical or update it to link here.

Acceptance criteria:

- Docs match `settings.gradle.kts`, `AppDatabase.kt`, and current navigation/features.

### PR 5: Shared Message Readiness

Scope:

- Add a domain readiness model.
- Replace `MessagesViewModel.readinessFor`.
- Begin using the same model in Wish Preview and AI Doctor.

Acceptance criteria:

- Missing route, missing permission, channel blackout, quiet hours, exact alarm, approval, quality, and failed states have typed reasons and action routes.

## 14. Final Assessment

RelateAI has a strong feature foundation and a serious amount of production-readiness work already in place. The biggest risk is not missing functionality; it is consistency and trust across a wide automation surface.

The top technical priority is to make automatic sending impossible unless the message is high-quality, route-ready, approval-safe, and schedule-safe. The top product priority is to convert scattered readiness, analytics, recovery, and setup signals into one clear action system.

After the P0/P1 fixes, the next major engineering investment should be boundary cleanup: move Room entities out of domain, stop exposing database entities to UI, and consolidate readiness/action policies into shared domain use cases. That will reduce regression risk and make the product easier to evolve into a trustworthy daily relationship command center.
