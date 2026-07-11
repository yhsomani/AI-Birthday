# ADR 0005: React Native Replacement Architecture

Date: 2026-07-10

Status: Accepted

## Context

RelateAI migrated from the legacy Kotlin Android project to a React Native / Expo app. `SSOT.md` is the active product authority. The now-superseded `docs/feature-fssot.md` and `docs/feature-roadmap-analysis.md` explain the broad scope the current implementation attempted; they cannot authorize future work. The active app entrypoint is `src/App.tsx`.

Legacy Android/Gradle artifacts are historical references only. They may still exist in the repository during migration, but they are not the active build, test, architecture, or release surface for the replacement app.

## Decision

The active application architecture is React Native / Expo with TypeScript domain contracts.

- `src/App.tsx` owns the temporary React Native command shell composition and platform lifecycle, deep-link, notification-entry, and Android-back adapters. It does not own feature screens or visual design.
- `src/application/*` owns the current bounded feature commands, operation coordination, recovery workflows, redacted outcomes, and production composition boundary used by the temporary shell. Future Figma UI is not required to retain this command protocol or application facade; the product reset proposes typed, feature-owned use cases instead.
- `src/domain/*` owns pure TypeScript feature behavior, validations, readiness checks, and user-control rules. Domain modules must remain independent of React Native components, Expo modules, network clients, and filesystem APIs unless a test fixture explicitly models an external boundary.
- `src/state/relateReducer.ts` owns app-state transitions. High-risk user actions such as approval, manual handoff completion, destructive local-data clearing, backup restore, and settings changes must pass through explicit reducer actions or equivalent reviewed state transitions.
- `src/state/persistence.ts` owns normalized, versioned, recoverable local persistence. Raw relationship data must not be exposed in diagnostics, storage-health reports, logs, or release evidence.
- `src/native/*` owns Expo and React Native platform adapters for contacts, calendar, notifications, biometrics, backup files, channel handoff, home widgets, secure storage, and provider clients.
- `src/config/*` owns release configuration checks, Expo plugin contracts, and React Native release evidence.
- `src/ui/*` contains only the minimal render-error recovery boundary and native-widget localization helper required by the active runtime. No theme, component library, or feature-screen contract is active during the temporary-UI phase.

Provider credentials, AI keys, email credentials, signing material, service-account files, and platform secrets do not belong in the React Native client repository. The checked-in composition deliberately supplies no provider session, so AI and provider email remain unavailable even when public endpoint variables are present. An integration build may inject a short-lived authenticated `ProviderSessionSource` for user-configured HTTPS endpoints while preserving endpoint readiness checks and redacted diagnostics.

React Native release evidence is the machine-readable candidate record. It must identify React Native / Expo as the active release surface, record validation command status, enforce permission policy, scan for generated native or legacy Android/Gradle artifact drift, and keep signed-build, device-smoke, store-submission, and legacy-artifact status explicit. A blocker-free production record requires structured evidence bound to the exact commit, working-tree fingerprint, app version, and signed artifact hashes; release owners still verify its linked primary evidence before approval.

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
- Future Figma implementation must call the same application commands and preserve confirmations, lock boundaries, privacy redaction, recovery semantics, and user-controlled handoff rules.

## Verification

The decision is implemented when:

- `npm test` runs the full React Native source-contract suite.
- `npm run typecheck` passes.
- Directly invoking `node --import tsx src/config/releaseEvidenceCli.ts` reports React Native / Expo as the active release surface and validates the package alias instead of trusting it as the bootstrap path.
- `reports/react-native-release-evidence.json` has no blockers before release, every production attachment is candidate-bound, and a release owner has verified the linked primary evidence.
- `app.json` and release evidence block direct SMS, SMS inbox, call-log, phone-number, exact-alarm, and AccessibilityService drift unless a future release decision explicitly changes that policy.
- Docs identify legacy Android/Gradle artifacts and prior Android ADRs as historical references, not active architecture.
