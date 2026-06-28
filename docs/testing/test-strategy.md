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
- CI runs `:app:verifyRoborazziDebug -Pscreenshot --tests 'com.example.ui.screenshots.*'` on pushes and pull requests, then uploads Roborazzi reports/outputs for mismatch review.
- Required before closing D-019 or the Phase 4 UI redesign.
- Target coverage: Home, Messages, Wish Preview, Contact Detail, Events, AI Doctor, Settings, Backup/Restore, Analytics, Activity History, onboarding/auth, and contextual tools.
- Required variants: compact phone width, typical phone width, large font scale, English, Hindi, loading, empty, error, and populated states where applicable.
- Use one repeatable path: Roborazzi for JVM screenshots first. Instrumented screenshots may be added later only for device-only concerns and must be documented separately.
- Current pilot baselines cover Splash, Auth, Onboarding, Home, Messages, Wish Preview, Contact Detail, Events, AI Doctor, Backup/Restore, Settings, Analytics, Activity History, Chat History, Memory Vault, Gift Advisor, and Style Coach at compact phone width, with large-font startup/Home including sync-error/Messages/Wish Preview/Contact Detail/Events/AI Doctor/Backup/Restore/Settings/Analytics/Activity History/Chat History/Memory Vault/Gift Advisor/Style Coach coverage plus Home/Messages/Wish Preview/Contact Detail/Events/AI Doctor/Backup/Restore/Analytics/Activity History/Chat History/Memory Vault/Gift Advisor/Style Coach loading, empty, error, refreshing, or progress coverage. Gift Advisor also covers the shared add-gift form top and scrolled-bottom states at compact-phone large font, Contact Detail covers the shared Contact Preferences form top and scrolled-bottom states at compact-phone large font, and Events covers the shared manual-entry form for existing-contact, new-contact, and scrolled-bottom warning states at compact-phone large font. Hindi large-font baselines now cover Onboarding actions, Messages needs review/failed recovery/reject dialog, Wish Preview blocked review, AI Doctor setup cards, Backup/Restore import preview, Settings data tools, Contact Detail personalization, Events conflict, Analytics reporting, Activity History action cards/empty/error, Chat History long sent messages/empty/error, Memory Vault pinned notes/error-empty, Gift Advisor suggestions/error-empty, and Style Coach learned profile/auto-error-empty; typical-phone baselines now cover Splash default, Auth default, Onboarding default/actions, Home populated/loading, Messages needs-review/reject-dialog/failed-recovery/loading, Wish Preview editor/approved/loading, AI Doctor blockers/healthy/refreshing, Backup/Restore passphrase/actions/import-preview/exporting/error, Settings overview/AI configuration/data tools/sign-out dialog, Analytics populated/empty/loading, Activity History populated/empty/error/loading, Contact Detail profile/automation-history, Events schedule/conflict/empty/loading, Chat History populated/empty/error/loading, Memory Vault notes/error-empty/loading, Gift Advisor suggestions/history/error-empty/generating/loading, and Style Coach training/profile/history/manual-progress/auto-error-empty. Home baselines guard the tokenized sync-error card at large font; Splash, Auth, Events, and Chat History baselines now also guard production-screen theme-backed color-role migrations with no expected visual drift; Messages baselines guard that populated queue pages top-align under filters instead of vertically centering short lists, and Gift Advisor baselines guard a persistent bottom record action that does not overlay history content. Remaining typical-phone gaps are narrower form-dialog/scrolled edge variants, and final Hindi/native-language review plus any remaining high-risk Hindi edge-state coverage remain open.

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

- Roborazzi is wired with Splash, Auth, Onboarding, Home, Messages, Wish Preview, Contact Detail, Events, AI Doctor, Backup/Restore, Settings, Analytics, Activity History, Chat History, Memory Vault, Gift Advisor, and Style Coach compact-phone pilot baselines, including large-font startup/Home including sync-error/Messages/Wish Preview/Contact Detail/Events/AI Doctor/Backup/Restore/Settings/Analytics/Activity History/Chat History/Memory Vault/Gift Advisor/Style Coach coverage plus Home/Messages/Wish Preview/Contact Detail/Events/AI Doctor/Backup/Restore/Analytics/Activity History/Chat History/Memory Vault/Gift Advisor/Style Coach loading, empty, error, refreshing, or progress coverage. Gift Advisor has deterministic shared add-gift form top and scrolled-bottom large-font baselines, Contact Detail has deterministic shared Contact Preferences form top and scrolled-bottom large-font baselines, and Events has deterministic shared manual-entry form existing-contact, new-contact, and scrolled-bottom warning large-font baselines. Hindi large-font baselines now cover Onboarding actions, Messages needs review/failed recovery/reject dialog, Wish Preview blocked review, AI Doctor setup cards, Backup/Restore import preview, Settings data tools, Contact Detail personalization, Events conflict, Analytics reporting, Activity History action cards/empty/error, Chat History long sent messages/empty/error, Memory Vault pinned notes/error-empty, Gift Advisor suggestions/error-empty, and Style Coach learned profile/auto-error-empty; typical-phone baselines now cover Splash default, Auth default, Onboarding default/actions, Home populated/loading, Messages needs-review/reject-dialog/failed-recovery/loading, Wish Preview editor/approved/loading, AI Doctor blockers/healthy/refreshing, Backup/Restore passphrase/actions/import-preview/exporting/error, Settings overview/AI configuration/data tools/sign-out dialog, Analytics populated/empty/loading, Activity History populated/empty/error/loading, Contact Detail profile/automation-history, Events schedule/conflict/empty/loading, Chat History populated/empty/error/loading, Memory Vault notes/error-empty/loading, Gift Advisor suggestions/history/error-empty/generating/loading, and Style Coach training/profile/history/manual-progress/auto-error-empty. Home sync-error baselines protect the tokenized warning card at large font; Messages queue baselines now protect top-aligned populated pager rhythm plus needs-review and reject-dialog Hindi large-font variants, while Gift Advisor baselines protect the non-overlapping persistent record action. Remaining gaps include narrower typical-phone scrolled/dialog variants, and final Hindi/native-language review plus any remaining high-risk Hindi edge-state coverage.
- `core/ui` has compile coverage through app usage, but not dedicated component tests for every primitive.
- `RelateAITheme` is intentionally dark-only for the current production redesign and is guarded by `RelateThemeContractTest`; semantic colors now back shared card, feedback, status, shimmer, avatar, text-field, chip, sync-error primitives, and Splash/Auth/Events/Chat History production color roles, but light/dynamic support should not ship until remaining screen-local and fixture dark-specific exported color use is replaced with semantic/theme-backed tokens and screenshot coverage exists for the new mode.
- Screen-level hard-coded `.dp`, `.sp`, raw `Color(0x...)`, gray/white, and ad hoc fractional values are cleared for the reviewed scan patterns; future slices should keep the scan clean and document any intentional exceptions.
- Screenshot fixture storage and baseline-update policy are documented; keep `ProductionReadinessConfigTest` green so CI cannot silently drop visual verification.
