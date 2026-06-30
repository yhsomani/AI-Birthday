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
- Current pilot baselines cover Splash, Auth, Onboarding, Home, Contact List, Messages, Wish Preview, Contact Detail, Events, AI Doctor, Backup/Restore, Settings, Analytics, Activity History, Chat History, Memory Vault, Gift Advisor, and Style Coach at compact phone width, with large-font startup/Home including sync-error/Contact List sync-error/Messages/Wish Preview/Contact Detail/Events/AI Doctor/Backup/Restore/Settings/Analytics/Activity History/Chat History/Memory Vault/Gift Advisor/Style Coach coverage plus Home/Contact List/Messages/Wish Preview/Contact Detail/Events/AI Doctor/Backup/Restore/Analytics/Activity History/Chat History/Memory Vault/Gift Advisor/Style Coach loading, empty, error, refreshing, or progress coverage. Gift Advisor covers the shared add-gift form top and scrolled-bottom states at compact-phone large font, compact-phone Hindi large font, and typical-phone width; Contact Detail covers the shared Contact Preferences form top and scrolled-bottom states at compact-phone large font, compact-phone Hindi large font, and typical-phone width; and Events covers the shared manual-entry form for existing-contact, new-contact, and scrolled-bottom warning states at compact-phone large font, compact-phone Hindi large font, and typical-phone width. Hindi large-font baselines now cover Onboarding actions, Messages needs review/failed recovery/reject dialog, Wish Preview blocked review, AI Doctor setup cards, Backup/Restore import preview, Settings data tools, Contact Detail personalization and shared preferences form, Events conflict and shared manual-entry form, Analytics reporting, Activity History action cards/empty/error, Chat History long sent messages/empty/error, Memory Vault pinned notes/error-empty, Gift Advisor suggestions/error-empty/shared add-gift form, and Style Coach learned profile/auto-error-empty; typical-phone baselines now cover Splash default, Auth default, Onboarding default/actions, Home populated/loading, Contact List populated, Messages needs-review/reject-dialog/failed-recovery/loading, Wish Preview editor/approved/loading, AI Doctor blockers/healthy/refreshing, Backup/Restore passphrase/actions/import-preview/exporting/error, Settings overview/AI configuration/data tools/sign-out dialog, Analytics populated/empty/loading, Activity History populated/empty/error/loading, Contact Detail profile/automation-history/shared preferences form, Events schedule/conflict/empty/loading/shared manual-entry form, Chat History populated/empty/error/loading, Memory Vault notes/error-empty/loading, Gift Advisor suggestions/history/error-empty/generating/loading/shared add-gift form, and Style Coach training/profile/history/manual-progress/auto-error-empty. Home baselines guard the tokenized sync-error card at large font and the production theme-backed color-role migration; Contact Detail baselines guard profile, personalization, automation/history, shared preferences form, loading, and the production theme-backed color-role migration; Contact List baselines now guard populated contacts, sync-error, loading, empty states, and the production theme-backed color-role migration; Splash, Auth, Onboarding, AI Doctor, Settings, Wish Preview, Analytics, Activity History, Backup/Restore, Events, Chat History, Memory Vault, Gift Advisor, and Style Coach baselines also guard production-screen theme-backed color-role migrations with no expected visual drift; Messages baselines guard top-aligned populated pager rhythm, needs-review/failed-recovery/reject-dialog/loading states, Hindi variants, and the production theme-backed color-role migration, while Gift Advisor baselines guard a persistent bottom record action that does not overlay history content. Remaining gaps are final Hindi/native-language review, newly discovered high-risk Hindi edge states, newly discovered scrolled/dialog variants, and future light/dynamic mode evidence.

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

- Roborazzi is wired with Splash, Auth, Onboarding, Home, Contact List, Messages, Wish Preview, Contact Detail, Events, AI Doctor, Backup/Restore, Settings, Analytics, Activity History, Chat History, Memory Vault, Gift Advisor, and Style Coach compact-phone pilot baselines, including large-font startup/Home including sync-error/Contact List sync-error/Messages/Wish Preview/Contact Detail/Events/AI Doctor/Backup/Restore/Settings/Analytics/Activity History/Chat History/Memory Vault/Gift Advisor/Style Coach coverage plus Home/Contact List/Messages/Wish Preview/Contact Detail/Events/AI Doctor/Backup/Restore/Analytics/Activity History/Chat History/Memory Vault/Gift Advisor/Style Coach loading, empty, error, refreshing, or progress coverage. Gift Advisor has deterministic shared add-gift form top and scrolled-bottom baselines at compact-phone large font, compact-phone Hindi large font, and typical-phone width; Contact Detail has deterministic shared Contact Preferences form top and scrolled-bottom baselines at compact-phone large font, compact-phone Hindi large font, and typical-phone width; and Events has deterministic shared manual-entry form existing-contact, new-contact, and scrolled-bottom warning baselines at compact-phone large font, compact-phone Hindi large font, and typical-phone width. Hindi large-font baselines now cover Onboarding actions, Messages needs review/failed recovery/reject dialog, Wish Preview blocked review, AI Doctor setup cards, Backup/Restore import preview, Settings data tools, Contact Detail personalization/shared preferences form, Events conflict/shared manual-entry form, Analytics reporting, Activity History action cards/empty/error, Chat History long sent messages/empty/error, Memory Vault pinned notes/error-empty, Gift Advisor suggestions/error-empty/shared add-gift form, and Style Coach learned profile/auto-error-empty; typical-phone baselines now cover Splash default, Auth default, Onboarding default/actions, Home populated/loading, Contact List populated, Messages needs-review/reject-dialog/failed-recovery/loading, Wish Preview editor/approved/loading, AI Doctor blockers/healthy/refreshing, Backup/Restore passphrase/actions/import-preview/exporting/error, Settings overview/AI configuration/data tools/sign-out dialog, Analytics populated/empty/loading, Activity History populated/empty/error/loading, Contact Detail profile/automation-history/shared preferences form, Events schedule/conflict/empty/loading/shared manual-entry form, Chat History populated/empty/error/loading, Memory Vault notes/error-empty/loading, Gift Advisor suggestions/history/error-empty/generating/loading/shared add-gift form, and Style Coach training/profile/history/manual-progress/auto-error-empty. Home baselines protect the tokenized warning card at large font and the production theme-backed color-role migration; AI Doctor baselines protect setup blockers, healthy, refreshing, and large-font setup-card states for the production theme-backed color-role migration; Settings baselines protect account/preferences, AI configuration, data tools, and sign-out dialog states for the production theme-backed color-role migration; Analytics baselines protect reporting, empty, and loading states for the production theme-backed color-role migration; Wish Preview baselines protect editor, blocked, approved, and loading states for the production theme-backed color-role migration; Contact Detail baselines protect profile, personalization, automation/history, shared preferences form, loading, and the production theme-backed color-role migration; Contact List baselines now protect populated, sync-error, loading, and empty contact-list states; Messages queue baselines protect top-aligned populated pager rhythm, needs-review/failed-recovery/reject-dialog/loading states, Hindi variants, and the production theme-backed color-role migration, while Gift Advisor baselines protect the non-overlapping persistent record action and the production theme-backed color-role migration. Remaining gaps include final Hindi/native-language review, newly discovered high-risk Hindi edge states, newly discovered scrolled/dialog variants, and future light/dynamic mode evidence.
- `core/ui` has compile coverage through app usage, but not dedicated component tests for every primitive.
- `RelateAITheme` is intentionally dark-only for the current production redesign and is guarded by `RelateThemeContractTest`; semantic colors now back shared card, feedback, status, shimmer, avatar, text-field, chip, sync-error primitives, screenshot fixture wrappers, and Splash/Auth/Onboarding/AI Doctor/Home/Contact List/Contact Detail/Messages/Settings/Wish Preview/Analytics/Activity History/Backup-Restore/Events/Chat History/Memory Vault/Gift Advisor/Style Coach production color roles, but light/dynamic support should not ship until alternate theme schemes are implemented, contrast-reviewed, and covered by screenshot evidence.
- Screen-level hard-coded `.dp`, `.sp`, raw `Color(0x...)`, gray/white, and ad hoc fractional values are cleared for the reviewed scan patterns; future slices should keep the scan clean and document any intentional exceptions.
- Screenshot fixture storage and baseline-update policy are documented; keep `ProductionReadinessConfigTest` green so CI cannot silently drop visual verification.
