# RelateAI Feature Source of Truth

Last updated: 2026-06-11

This file is the primary feature source of truth for the Android app in this repository. `SSOT_CONSOLIDATED.md` remains the expanded architecture and evidence companion, but feature status changes must be reflected here first and mirrored into the companion docs during each feature pass.

## Product Target

RelateAI is a local-first Android relationship assistant. It imports contacts, discovers important dates, learns relationship context and user writing style, generates personalized wishes with Gemini, routes approvals, schedules delivery, and sends messages through SMS, WhatsApp, or Gmail SMTP without a custom backend.

The target implementation is the Android app in this repository, not an IDE extension. Workspace compatibility means the repository and runtime must avoid hardcoded local paths, avoid stale project-structure assumptions, and adapt to available Android device capabilities, permissions, contact sources, account state, and integration credentials.

## Required Validation Statuses

- Fully Implemented: production-ready implementation with tests and documented validation.
- Partially Implemented: usable but missing documented behavior, coverage, device validation, or production hardening.
- Not Implemented: documented behavior has no active implementation.
- Broken: active implementation exists but fails the required workflow or validation.
- Outdated: code or docs describe obsolete behavior.
- Workspace-Specific: behavior depends on a local path, fixed environment, or hardcoded project/device assumption.
- Deprecated: intentionally inactive and superseded by another feature.
- Experimental: present but not part of the production path.

## Feature Inventory

| ID | Feature | Status | Required Completion Work |
|---|---|---|---|
| F-001 | App shell, navigation, routes, permissions | Fully Implemented | Compose smoke coverage added for permission rationale and bottom navigation; debug builds install as `com.aistudio.relateai.qxtjrk.debug`; connected device run installed/started but stalled while another app was foregrounded. |
| F-002 | Splash and onboarding | Fully Implemented | Compose smoke coverage added for first-run onboarding-to-auth routing; debug builds install side-by-side; connected device run installed/started but stalled while another app was foregrounded. |
| F-003 | Authentication, guest mode, session state | Fully Implemented | Compose smoke coverage added for auth actions and guest app shell; live OAuth remains blocked by idle-device and credential prerequisites. |
| F-004 | Settings and secure configuration | Fully Implemented | Biometric, quiet-hour, reminder, channel blackout, sync, Gmail, AI, and sign-out settings are implemented; live device handoffs remain tracked in `docs/UI_VALIDATION.md`. |
| F-005 | Home dashboard and relationship planner | Fully Implemented | `HomeScreenInteractionTest` covers dashboard cards, settings/readiness/quick-action/planner links, and sync-error retry/dismiss behavior. |
| F-006 | Contact sync, import, and deduplication | Fully Implemented | Foreground and background sync share Google + device contact merge, relationship normalization, and event discovery. |
| F-007 | Contact list search, filter, sort | Fully Implemented | Add UI smoke evidence for search, chips, sort, clear, and details navigation. |
| F-008 | Contact detail personalization | Fully Implemented | Custom send time and skip-auto-wish now affect generation/scheduling; live UI validation remains tracked in `docs/UI_VALIDATION.md`. |
| F-009 | Event discovery | Fully Implemented | Preserve leap-day and deactivation behavior. |
| F-010 | Manual and custom event creation | Fully Implemented | Add reminder scheduling after manual event save. |
| F-011 | Messages inbox and bulk actions | Fully Implemented | Add UI smoke evidence for tabs, search, selection, and bulk actions. |
| F-012 | Wish preview, editing, feedback, regeneration | Fully Implemented | Add UI smoke evidence for variants, editing, approve/reject/test/regenerate. |
| F-013 | Chat history | Fully Implemented | Add UI smoke evidence for contact history navigation. |
| F-014 | Analytics and CSV export | Fully Implemented | Validate export/share workflow on device. |
| F-015 | Activity history and audit log | Fully Implemented | Validate filters, search, and route opening on device. |
| F-016 | Style Coach | Fully Implemented | Validate manual sample and recent-message analysis UI. |
| F-017 | Memory Vault | Fully Implemented | Validate add, pin, unpin, delete, and validation states. |
| F-018 | Gift Advisor | Fully Implemented | Validate record, delete, feedback, and AI suggestions where credentials allow. |
| F-019 | Encrypted backup and restore | Fully Implemented | Validate document export/import flow on device. |
| F-020 | Automation setup / AI Doctor | Fully Implemented | Validate diagnostics and system-setting handoffs on device. |
| F-021 | Room database, schema, migrations | Fully Implemented | Keep migrations non-destructive and schema tests passing. |
| F-022 | AI contact classification | Fully Implemented | Validate AI-disabled and live-AI paths. |
| F-023 | AI message generation and fallback | Fully Implemented | Respects skip-auto-wish, custom send time, quiet hours, and blackout dates. |
| F-024 | Approval lifecycle | Fully Implemented | Validate notification actions and scheduling after approval. |
| F-025 | Exact scheduling and boot recovery | Fully Implemented | Exact send scheduling applies automation policy and boot recovery reschedules reminders. |
| F-026 | WorkManager automation chain | Fully Implemented | Daily automation uses shared contact sync, event discovery, message generation, reminders, revival, and style analysis workers. |
| F-027 | Dispatch orchestration | Fully Implemented | Dispatch defers during quiet hours or blackout dates before final send. |
| F-028 | SMS delivery and status callbacks | Fully Implemented | Validate live SMS with test recipient when safe. |
| F-029 | WhatsApp Accessibility delivery | Fully Implemented | Validate live WhatsApp test flow when WhatsApp/accessibility are configured. |
| F-030 | Gmail SMTP delivery and test send | Fully Implemented | Email subjects now reflect birthdays, anniversaries, work anniversaries, custom labels, and SMTP test sends; live SMTP credentials remain required for device validation. |
| F-031 | Notifications and action receivers | Fully Implemented | Add event-reminder scheduler evidence and notification action smoke tests. |
| F-032 | Revival suggestions | Fully Implemented | Validate AI-disabled/live-AI notification paths. |
| F-033 | Relationship health scoring | Fully Implemented | Keep scoring tests passing and validate UI surfaces. |
| F-034 | Widget, deep links, shortcuts | Fully Implemented | Validate widget, shortcuts, and deep links on device. |
| F-035 | Security, privacy, and local encryption | Fully Implemented | Keep secure-storage, backup-exclusion, redaction, and pinning tests passing. |
| F-036 | Sign-out data purge | Fully Implemented | Validate destructive cleanup on test data only. |
| F-037 | Resilience, logging, health, dead-letter queue | Fully Implemented | Keep resilience tests passing and validate AI Doctor surfacing. |
| F-038 | External API and service interfaces | Fully Implemented | Keep domain/data boundaries stable while adding reminder scheduler service. |
| F-039 | Build, CI, release guard, coverage | Fully Implemented | Full JDK 21 Gradle validation passes with unit tests, lint, debug assemble, and JaCoCo report. |
| F-040 | Design system and localization | Fully Implemented | Resource-key parity tests cover English/Hindi app and core-data strings; touched notification/system-alert strings are resource-backed. |
| F-041 | Developer helper scripts and docs | Fully Implemented | Helper string-audit script resolves the repository root dynamically; steering build docs reflect the active Gradle/JDK/SDK setup. |
| F-042 | Biometric app lock enforcement | Fully Implemented | App access is gated at cold start/resume when enabled; live device unlock validation remains tracked in `docs/UI_VALIDATION.md`. |
| F-043 | Quiet hours, blackout dates, reminder toggles | Fully Implemented | Reusable automation schedule policy enforces quiet hours, blackout dates, and reminder toggles. |
| F-044 | Event reminder scheduling | Fully Implemented | Schedules, cancels, and reschedules alarms from `notifyDaysBefore` and the reminder toggle. |
| F-045 | Mood logs | Deprecated | Keep deprecated unless explicitly re-scoped. |
| F-046 | Dedicated birthday quick-add FAB/modal | Deprecated | Keep superseded by manual event creation unless explicitly re-scoped. |
| F-047 | Legacy Retrofit Gemini model layer | Experimental | Keep experimental unless active Gemini client is migrated to it. |

## Validation Requirements

Every feature pass must include:

- Code inspection against this file.
- Unit/Robolectric/Compose/instrumented tests where practical.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache`.
- Manual device validation evidence in `docs/UI_VALIDATION.md` for UI, notification, widget, shortcut, deep-link, and live integration workflows.
- Documentation/spec updates and a descriptive feature-scoped commit.

Live SMS, WhatsApp, Gmail SMTP, OAuth, ContactsProvider, notification, exact alarm, and widget validation requires device state and credentials. When those prerequisites are unavailable, the feature must be marked with the exact missing prerequisite and covered by non-live tests.
