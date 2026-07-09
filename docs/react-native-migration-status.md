# React Native Migration Status

Last updated: 2026-07-09

This document tracks the migration from the legacy Kotlin Android app to the React Native app.

## Active React Native App

The React Native app entrypoint is:

- `src/App.tsx`

Supporting files:

- `src/domain/types.ts`
- `src/data/seed.ts`
- `src/state/relateReducer.ts`
- `src/ui/theme.ts`
- `package.json`
- `app.json`
- `index.js`
- `tsconfig.json`

## Implemented in React Native

The current RN app provides a local-first functional shell for the most important user-facing workflows from `feature-fssot.md` and `feature-roadmap-analysis.md`:

- Home next-best-action dashboard.
- Recommended primary navigation: Home, Events, Messages, Contacts, More.
- Events list with preparation checklists, type/time-range filters, and a monthly calendar view.
- Manual event creation for existing or new local contacts, including date validation, duplicate/conflict warnings, and explicit keep-separate confirmation.
- Calendar import/export through Expo Calendar, plus pure rules that export review-safe event notes and import candidates as unverified review-needed events.
- Contact list search.
- Native device contact import through Expo Contacts, plus pure dedupe/merge rules by phone, email, and name.
- Contact detail with guided enrichment prompts, personalization quality signals, tone controls, preferred channel, check-in snooze, Memory Vault, Gift Advisor, filterable relationship timeline, and read-only Chat History entry.
- Manual message composer for non-event messages, with editable local templates by occasion/tone and a separate AI draft path.
- Draft generation using a configurable secure AI endpoint, privacy-filtered contact/style/event/memory context, and review-first local template fallback.
- Message Template Library for offline/non-AI drafting, including contact-personalized template rendering, editable template text, review-first template drafts, and duplicate warnings for repeated manual drafts.
- AI provider status and test action under More, including the last privacy summary and actionable provider errors.
- Wish Preview with variants, editable text, regeneration, duplicate warning, AI context preview, approval, rejection, and manual handoff.
- Safe duplicate-send guardrail that detects same-event, scheduled, sent, and similar manual-message risks, blocks approval until explicit acknowledgement, and records the acknowledgement.
- Manual handoff uses channel-specific deep links for SMS, WhatsApp, and email when possible, with React Native platform share fallback before marking a message sent.
- Configurable provider email delivery through a secure endpoint for approved Email messages, with sender email setup, provider error classification, and manual `mailto:` recovery.
- Messages queue with review, scheduled, blocked, failed, and sent states.
- Contact Chat History for sent RelateAI messages, including text/channel search, channel filters, newest-first ordering, sent timestamps, empty states, no-result states, and deleted-contact history handling.
- Post-send follow-up scheduling from sent messages, with tomorrow/next-week choices that create reviewable follow-up events and reminder plans without sending anything automatically.
- Deep-link routing for safe entry points: Home, Events, Add Event, Messages, Contacts, Contact Detail, Chat History, Wish Preview, More, Setup, and Backup. Stale links recover to the closest useful screen and never trigger sending.
- Retained Android static launcher shortcut definitions for Review messages and Add event, backed by the same safe deep-link routes and an Expo prebuild config plugin.
- Relationship check-in reminder behavior.
- Event reminder planning and Expo Notifications scheduling; notification payloads route users back to safe review surfaces and never send messages directly.
- Goal-based Setup Wizard under More for Reminders only, AI drafts, Manual sends, and Automation, with focused readiness steps, recommended next action, provider tests, reminder planning, and safe navigation actions.
- Setup Check under More, using a redacted dry-run report across required setup, quality, reliability, and recovery checks with one recommended next fix and optional grouped details.
- Style Coach sample analysis from manual writing samples or opt-in recent sent messages, including confidence, language, emoji-use, average-length, validation, and privacy-preserving activity logs.
- Analytics summary under More instead of bottom navigation.
- File-backed encrypted backup export and restore using user-held passphrases, preview-before-restore metadata, checksum/integrity checks, and atomic restore behavior.
- Persistent app state using Expo SecureStore when available, with fallback storage for unsupported platforms, versioned migration, and corrupt-state quarantine/recovery.
- Biometric lock using Expo Local Authentication, with an unavailable-hardware recovery path.
- Settings toggles for AI, notifications, SMS, manual WhatsApp handoff, and biometric lock.
- Locale files for English, Hindi, and Hinglish, user-controlled language preference, localized primary navigation/status labels, and locale-aware date/currency formatting.
- Activity History with search, type/severity/date filters, locale-aware timestamps, open-issue labels, empty/no-result states, and safe recovery navigation actions.
- Pure feature contract tests for AI provider request filtering, provider response validation, provider fallback, message template library rendering/draft creation, guided contact enrichment, relationship timeline filtering, Setup Check diagnostics/redaction, encrypted backup export/preview/restore/tamper rejection, deep-link routing, safe launcher shortcuts, notification tap route payloads, biometric lock policy, event filtering/month views, manual event validation/conflict handling, post-send follow-up scheduling, chat history search/filter states, duplicate-send guardrails, goal-based setup wizard planning, Style Coach sample analysis/privacy, activity history filtering/recovery routes, calendar import/export, contact import/deduplication, email delivery validation/provider client/state transitions, reminder planning, channel handoff, versioned state persistence, storage migration, corrupt-state quarantine, localization completeness/fallback/formatting, review-first drafts, private-note exclusion, duplicate warnings, approval validation, manual handoff, event checklists, backups, and settings.

## Still Required for Full Replacement

The project is not yet a complete React Native replacement for every production capability. Remaining work includes:

- Production-scale encrypted relational/local storage and large-dataset performance validation.
- Device-level notification delivery verification across Android/iOS.
- Device-level calendar import/export verification across Android/iOS calendar providers.
- Device-level SMS, WhatsApp, and email handoff verification across installed provider apps.
- Production AI backend deployment, credentials, rate limits, content safety, and provider observability.
- Device-level Android launcher shortcut verification and home widget implementation/verification if the widget is retained.
- Full string localization coverage beyond the core shell and full accessibility QA on device.
- Primary screen interaction tests and device-level native integration tests.
- Native Android/iOS release configuration for the RN app.
- Final removal or archival of legacy Kotlin/Gradle code after parity is proven.

## Validation Run

Completed after adding the RN app:

```bash
npm install
npm run typecheck
npm test
npm audit --audit-level=moderate
```

Results:

- TypeScript passed.
- Feature contract tests passed, including message template library rendering/draft creation, guided contact enrichment, relationship timeline filtering, Setup Check diagnostics/redaction, duplicate-send guardrails, goal-based setup wizard planning, Style Coach sample analysis/privacy, activity history filtering/recovery routes, event filtering/month views, manual event creation, post-send follow-up scheduling, chat history search/filter states, and launcher shortcut routing.
- npm audit passed with 0 vulnerabilities after a targeted `uuid` override.

## Migration Rule

Do not delete the legacy Android source tree until the React Native app has verified feature parity against:

- `docs/feature-fssot.md`
- `docs/feature-roadmap-analysis.md`
- `docs/README.md`
