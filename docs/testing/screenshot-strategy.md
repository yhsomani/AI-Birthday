# Screenshot Strategy

Last reviewed: 2026-06-28

This document records the selected visual-regression path for the RelateAI UI redesign. It complements `PLAN.md`, `docs/testing/test-strategy.md`, and `docs/design/ux-audit-checklist.md`.

## Decision

Use Roborazzi for JVM screenshot validation.

Rationale:

- The repo already declares Roborazzi in `gradle/libs.versions.toml`.
- The current UI test suite is Robolectric-based and already renders Compose screen content with deterministic fake state.
- JVM screenshots are a better first fit than instrumented screenshots for fast redesign feedback.
- Paparazzi would add a second screenshot stack, and instrumented screenshots should be reserved for device-only concerns.

## Initial Scope

Primary coverage:

- Home populated and loading states.
- Messages queue populated, failed, and empty states.
- Wish Preview editable, blocked, and approved states.
- Contact Detail profile and contextual action states.
- Events list and conflict states.
- AI Doctor setup blocker and healthy states.
- Settings account/preferences/data tools state.
- Backup/Restore passphrase, preview, and status states.
- Analytics populated and empty states.
- Activity History populated and empty states.
- Onboarding, Auth, Splash startup surfaces.
- Contextual tools: Memory Vault, Gift Advisor, Style Coach, Chat History.

Variants:

- Compact phone width.
- Typical phone width.
- Large font scale.
- English.
- Hindi for the highest-risk text-heavy screens.
- Loading, empty, error, and populated states where applicable.

## Baseline Storage

Approved baseline location:

- `app/src/test/screenshots/baseline`

Generated review artifacts should stay outside source-controlled baselines unless they are approved as expected images.

Current pilot baselines:

- `app/src/test/screenshots/baseline/auth_default_compact_phone.png`
- `app/src/test/screenshots/baseline/auth_default_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/onboarding_default_compact_phone.png`
- `app/src/test/screenshots/baseline/onboarding_default_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/onboarding_actions_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/splash_default_compact_phone.png`
- `app/src/test/screenshots/baseline/splash_default_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/home_populated_compact_phone.png`
- `app/src/test/screenshots/baseline/home_populated_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/home_actions_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/home_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/messages_needs_review_compact_phone.png`
- `app/src/test/screenshots/baseline/messages_needs_review_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/messages_failed_recovery_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/messages_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/wish_preview_editor_compact_phone.png`
- `app/src/test/screenshots/baseline/wish_preview_editor_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/wish_preview_blocked_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/wish_preview_approved_compact_phone.png`
- `app/src/test/screenshots/baseline/wish_preview_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/contact_detail_profile_compact_phone.png`
- `app/src/test/screenshots/baseline/contact_detail_personalization_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/contact_detail_automation_history_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/contact_detail_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/events_schedule_compact_phone.png`
- `app/src/test/screenshots/baseline/events_conflict_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/events_empty_compact_phone.png`
- `app/src/test/screenshots/baseline/events_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/ai_doctor_blockers_compact_phone.png`
- `app/src/test/screenshots/baseline/ai_doctor_blockers_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/ai_doctor_setup_cards_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/ai_doctor_healthy_compact_phone.png`
- `app/src/test/screenshots/baseline/ai_doctor_refreshing_compact_phone.png`

## Test Rules

- Screenshot tests should render pure content Composables with deterministic fake state.
- Tests must use `android.app.Application` unless a specific production startup behavior is under test.
- Screenshot tests must not hit live APIs, keystore-backed preferences, Room production databases, WorkManager schedulers, or notification channels.
- Golden updates require an accompanying UX checklist note explaining why the visual change is expected.
- Each screenshot test should also have an interaction or ViewModel test covering behavior; screenshots validate presentation, not business logic.
- Screenshot tests use the `com.example.ui.screenshots.ScreenshotTests` JUnit category.
- Normal unit-test commands exclude screenshot tests by default; use `-Pscreenshot` with Roborazzi tasks to include them.

## Commands

Record approved baselines:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:recordRoborazziDebug -Pscreenshot --no-configuration-cache
```

Verify approved baselines:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:verifyRoborazziDebug -Pscreenshot --no-configuration-cache
```

Run a focused pilot verification:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:verifyRoborazziDebug -Pscreenshot --tests com.example.ui.screenshots.AuthScreenshotTest --no-configuration-cache
```

## Remaining Implementation

- Add CI policy for when screenshot tests run and how baseline updates are reviewed.
- Expand large-font screenshot variants beyond the startup, Home, Messages, Wish Preview, Contact Detail, Events, and AI Doctor pilots.
- Add Hindi screenshot variants for text-heavy screens.
- Expand baselines beyond the startup, Home, Messages, Wish Preview, Contact Detail, Events, and AI Doctor pilots to the primary and contextual screens listed above.
