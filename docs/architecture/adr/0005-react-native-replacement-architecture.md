# ADR 0005: React Native Replacement Architecture

Date: 2026-07-10

Status: Accepted

## Context

RelateAI is migrating from the legacy Kotlin Android project to a React Native / Expo app. The active product reference is `docs/feature-fssot.md`; product prioritization is in `docs/feature-roadmap-analysis.md`; the active app entrypoint is `src/App.tsx`.

Legacy Android/Gradle artifacts are historical references only. They may still exist in the repository during migration, but they are not the active build, test, architecture, or release surface for the replacement app.

## Decision

The active application architecture is React Native / Expo with TypeScript domain contracts.

- `src/App.tsx` owns the React Native shell, navigation state, screen composition, and user-facing workflow wiring.
- `src/domain/*` owns pure TypeScript feature behavior, validations, readiness checks, and user-control rules. Domain modules must remain independent of React Native components, Expo modules, network clients, and filesystem APIs unless a test fixture explicitly models an external boundary.
- `src/state/relateReducer.ts` owns app-state transitions. High-risk user actions such as approval, manual handoff completion, destructive local-data clearing, backup restore, and settings changes must pass through explicit reducer actions or equivalent reviewed state transitions.
- `src/state/persistence.ts` owns normalized, versioned, recoverable local persistence. Raw relationship data must not be exposed in diagnostics, storage-health reports, logs, or release evidence.
- `src/native/*` owns Expo and React Native platform adapters for contacts, calendar, notifications, biometrics, backup files, channel handoff, home widgets, secure storage, and provider clients.
- `src/config/*` owns release configuration checks, Expo plugin contracts, and React Native release evidence.
- `src/ui/*` owns source-level UI contracts and presentation helpers that protect accessibility, localization, primary interaction routing, and widget presentation.

Provider credentials, AI keys, email credentials, signing material, service-account files, and platform secrets do not belong in the React Native client repository. AI and email delivery use user-configured HTTPS backend endpoints with endpoint readiness checks and redacted diagnostics.

React Native release evidence is the release source of truth. It must identify React Native / Expo as the active release surface, record validation command status, enforce permission policy, scan for legacy Android/Gradle artifact drift, and keep signed-build, device-smoke, store-submission, and legacy-artifact status explicit.

## Consequences

Positive:

- Feature behavior is testable through TypeScript contracts without depending on the legacy Android module graph.
- Native platform integrations remain thin adapters around user-controlled flows.
- The release path can prove that the React Native app, not the legacy Kotlin tree, is the active product.
- AI coding agents can use the FSSOT, roadmap, README, and RN tests as the implementation reference.

Costs:

- Legacy Android ADRs are historical context only and should not be used to justify new RN implementation choices.
- Some release completion evidence still requires signed EAS builds, device smoke tests, and store submission checks.
- Native capability changes need matching Expo config, adapter tests, release evidence, privacy docs, and Setup Check behavior.

## Verification

The decision is implemented when:

- `npm test` runs the full React Native source-contract suite.
- `npm run typecheck` passes.
- `npm run release:evidence` reports React Native / Expo as the active release surface.
- `reports/react-native-release-evidence.json` has no blockers before release and lists any remaining external evidence warnings.
- `app.json` and release evidence block direct SMS, SMS inbox, call-log, phone-number, exact-alarm, and AccessibilityService drift unless a future release decision explicitly changes that policy.
- Docs identify legacy Android/Gradle artifacts and prior Android ADRs as historical references, not active architecture.
