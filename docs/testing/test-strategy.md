# Test Strategy

Last reviewed: 2026-06-28

This document defines the validation gates for production readiness and the UI redesign. It complements `PLAN.md`; when behavior or architecture changes, update both the tests and this strategy.

## Test Layers

Unit and policy tests:

- Pure model/domain policies live in `core:model` and `core:domain` tests.
- Data-layer adapters, migrations, backup, sender taxonomy, scheduler behavior, and security helpers live in `core:data` and app unit tests.
- ViewModels are tested with fake repositories/use cases so UI state remains behavior-preserving during redesign.
- App Robolectric tests default to `android.app.Application` through `app/src/test/resources/robolectric.properties`; tests that need production app startup must opt in explicitly. This keeps unit tests from starting keystore-backed database and secure-pref warm-ups.

Compose interaction tests:

- App-side Robolectric Compose tests live under `app/src/test/java/com/example/ui/screens`.
- These tests cover screen actions, visible states, disabled states, filters, search, dialogs, and navigation callbacks.
- Use this layer for behavior-preserving UI redesign before adding screenshot assertions.

Design-system tests:

- `DesignSystemTokensTest` guards shared spacing, radius, sizing, alpha, elevation, and fraction token contracts.
- Shared `core/ui` components should gain focused tests when they begin owning semantics, state, or interaction beyond visual composition.

Accessibility regression tests:

- `AccessibilityLabelsRegressionTest` scans cleaned screens for icon-only actions missing non-null content descriptions.
- Add screens to that allowlist only after their icon-only actions are intentionally reviewed.
- Compose interaction tests should assert important labels and disabled-state behavior when practical.

Screenshot and large-font validation:

- Tool selected: Roborazzi. See `docs/testing/screenshot-strategy.md`.
- Wired into Gradle through `recordRoborazziDebug` and `verifyRoborazziDebug`; screenshot tests are included only when `-Pscreenshot` is supplied.
- Required before closing D-019 or the Phase 4 UI redesign.
- Target coverage: Home, Messages, Wish Preview, Contact Detail, Events, AI Doctor, Settings, Backup/Restore, Analytics, Activity History, onboarding/auth, and contextual tools.
- Required variants: compact phone width, typical phone width, large font scale, English, Hindi, loading, empty, error, and populated states where applicable.
- Use one repeatable path: Roborazzi for JVM screenshots first. Instrumented screenshots may be added later only for device-only concerns and must be documented separately.
- Current pilot baselines cover Splash, Auth, Onboarding, Home, Messages, Wish Preview, Contact Detail, Events, and AI Doctor at compact phone width, with large-font startup/Home/Messages/Wish Preview/Contact Detail/Events/AI Doctor coverage plus Home/Messages/Wish Preview/Contact Detail/Events/AI Doctor loading or refreshing coverage; broader screen and Hindi variants remain open.

## Standard Commands

Full debug gate:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:model:test testDebugUnitTest lintDebug assembleDebug --no-configuration-cache
```

UI foundation gate:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew -q :core:ui:compileDebugKotlin :app:compileDebugKotlin --no-configuration-cache
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests com.example.ui.DesignSystemTokensTest --no-configuration-cache
```

Static whitespace gate:

```bash
git diff --check
```

Release smoke testing is tracked in `docs/operations/release-checklist.md`.

## UI Redesign Acceptance

A screen redesign is complete only when:

- The screen owner and feature ownership are recorded in `docs/design/ux-audit-checklist.md`.
- The screen uses documented tokens/components where practical.
- Behavior-preserving ViewModel tests still pass.
- Compose interaction tests cover primary commands, filters, setup blockers, loading, empty, and failure states.
- Accessibility labels are reviewed for icon-only actions.
- Large-font and narrow-width validation evidence is attached to the task or release record.
- The screen avoids duplicate ownership of features listed elsewhere in the audit.

## Current Gaps

- Roborazzi is wired with Splash, Auth, Onboarding, Home, Messages, Wish Preview, Contact Detail, Events, and AI Doctor compact-phone pilot baselines, including large-font startup/Home/Messages/Wish Preview/Contact Detail/Events/AI Doctor coverage plus Home/Messages/Wish Preview/Contact Detail/Events/AI Doctor loading or refreshing coverage; broad screenshot coverage is still missing.
- `core/ui` has compile coverage through app usage, but not dedicated component tests for every primitive.
- `RelateAITheme` remains dark-only.
- Screen-level hard-coded `.dp`, `.sp`, raw `Color(0x...)`, gray/white, and ad hoc fractional values are cleared for the reviewed scan patterns; future slices should keep the scan clean and document any intentional exceptions.
- Screenshot fixtures and baselines need a stable storage and update policy.
