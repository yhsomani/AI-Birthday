# React Native Migration Status

Last updated: 2026-07-10

This document tracks the active React Native replacement. The behavior baseline is
`feature-fssot.md`, modified by the product-priority decisions in
`feature-roadmap-analysis.md`.

## Active Runtime

`src/App.tsx` is the only application entry point. Legacy Android/Gradle artifacts have been removed,
and the former Kotlin product is not an active build or release surface.

The visible React Native surface is intentionally a temporary functionality console:

- one bounded JSON command input;
- redacted runtime, operation, issue, and record-count output;
- a small set of example commands needed to exercise the runtime;
- no feature-screen layout, theme, animation, icon system, or visual design.

The console is not the product UX and must not be used as a design reference. A later
Figma implementation will replace it while retaining the application and domain APIs.
Private record content is never rendered by the console. Successful non-secret command
input is cleared, failed non-secret input remains editable for retry, and secure input is
always cleared. Backgrounding clears the temporary shell, and biometric lock prevents
private state inspection. `system.catalog` exposes every strict command type plus bounded
workflow examples so functionality does not depend on undocumented source knowledge.

## Functional Architecture

The replacement is local-first and organized around these boundaries:

- deterministic domain state transitions for contacts, events, reminders, messages,
  memories, gifts, preferences, privacy, onboarding, setup, and activity;
- a bounded command runtime that makes product behavior executable without depending
  on a screen implementation;
- serialized and cancellable operation scopes with redacted outcomes;
- verified durable commits before state-changing commands report success;
- an encrypted entity-file repository with bounded records, atomic generations,
  rollback recovery, strict schema decoding, and migration from the earlier normalized
  protected-state format;
- resumable clear and restore transactions with native reminder/widget reconciliation;
- user-intent-gated native adapters for contacts, calendars, notifications, manual
  channel handoff, backups, biometric authentication, shortcuts, and the home widget;
- short-lived authenticated provider-session seams for AI and email, with no persisted
  provider token or client-side provider secret;
- UI-independent navigation, deep-link, notification-entry, Android-back, and browser
  history state.

Core behavior includes conflict-aware contact and event import, manual contact/event
lifecycle actions, relationship check-ins, event preparation, reminder planning,
review-first AI or local-template drafting, duplicate-send prevention, message approval
and recovery, explicit manual send handoff, enrichment/memory context, follow-ups,
home next-action planning, permissions/privacy controls, diagnostics, and encrypted
backup/restore. The executable boundary also includes goal-gated onboarding, selected
idempotent device-calendar export, time-zone-safe schedule recovery, confirmed advanced
bulk message actions, current derived relationship-health analytics, redacted summary
sharing, and secondary confirmed CSV report sharing.

Roadmap-deprioritized capabilities do not compete with the core workflow. In
particular, the replacement does not implement unattended WhatsApp sending, inbox
scraping, social scraping, or a public/social/CRM surface. Full automation and provider
email remain advanced guarded behavior; bulk actions, CSV analytics export, detailed
diagnostics, shortcuts, and widget behavior are non-primary validation surfaces.

## Production Boundaries Still External

The repository can validate client behavior and native build integration, but the
following evidence cannot be manufactured by source tests:

- deployed production AI and email backends, authenticated account/session issuance,
  server-side quotas, abuse controls, and provider observability;
- signed Android/iOS credentials and store submission;
- physical-device verification of notifications, calendars, installed handoff apps,
  biometric hardware, shortcuts, and the widget across supported OS versions.

When these integrations are unavailable, the client fails closed or uses the documented
local/manual fallback. It must never silently claim provider readiness or successful
delivery.

## Validation

Use the pinned Node and npm versions and run:

```bash
nvm use
npm ci
npm run typecheck
npm run lint
npm run format:check
npm test
npm run test:coverage
npm run test:native-prebuild
npm audit --audit-level=moderate
npx expo install --check
npx expo export --platform web --output-dir reports/web-export
git diff --check
npm run release:evidence -- --fail-on-blockers
```

`test:native-prebuild` requires JDK 17 and an Android SDK. Release evidence is valid only
when produced from the exact candidate checkout; it records command results and source,
lockfile, and toolchain provenance. Dirty local work, a missing signed build, or missing
device evidence must not be represented as a production-ready store release.

## Migration Rule

Any reintroduced legacy Android/Kotlin/Gradle application path is release drift. Native
files generated by the Expo prebuild validation fixture are temporary build artifacts,
not a second product implementation. Release evidence scans for legacy Android/Gradle artifact drift.
