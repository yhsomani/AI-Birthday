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
- Messages queue populated, failed, reject-confirmation dialog, and empty states, including top-aligned populated pager pages below filters.
- Wish Preview editable, blocked, and approved states.
- Contact Detail profile and contextual action states.
- Events list and conflict states.
- AI Doctor setup blocker and healthy states.
- Settings account/preferences/data tools state.
- Backup/Restore passphrase, preview, and status states.
- Analytics populated, empty, and loading states.
- Activity History populated, routed action, empty, error, and loading states.
- Onboarding, Auth, Splash startup surfaces.
- Chat History populated, long-message large-font, empty, error, and loading states.
- Memory Vault add-note, populated notes, pinned-note large-font, error/empty, and loading states.
- Gift Advisor suggestions, history large-font, error/empty, generating, and loading states.
- Style Coach training, profile large-font, history large-font, manual progress, and auto-error/empty states.

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
- `app/src/test/screenshots/baseline/auth_default_typical_phone.png`
- `app/src/test/screenshots/baseline/auth_default_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/onboarding_default_compact_phone.png`
- `app/src/test/screenshots/baseline/onboarding_default_typical_phone.png`
- `app/src/test/screenshots/baseline/onboarding_default_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/onboarding_actions_typical_phone.png`
- `app/src/test/screenshots/baseline/onboarding_actions_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/onboarding_actions_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/splash_default_compact_phone.png`
- `app/src/test/screenshots/baseline/splash_default_typical_phone.png`
- `app/src/test/screenshots/baseline/splash_default_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/home_populated_compact_phone.png`
- `app/src/test/screenshots/baseline/home_populated_typical_phone.png`
- `app/src/test/screenshots/baseline/home_populated_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/home_actions_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/home_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/home_loading_typical_phone.png`
- `app/src/test/screenshots/baseline/home_sync_error_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/messages_needs_review_compact_phone.png`
- `app/src/test/screenshots/baseline/messages_needs_review_typical_phone.png`
- `app/src/test/screenshots/baseline/messages_needs_review_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/messages_needs_review_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/messages_failed_recovery_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/messages_failed_recovery_typical_phone.png`
- `app/src/test/screenshots/baseline/messages_failed_recovery_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/messages_reject_dialog_compact_phone.png`
- `app/src/test/screenshots/baseline/messages_reject_dialog_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/messages_reject_dialog_typical_phone.png`
- `app/src/test/screenshots/baseline/messages_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/messages_loading_typical_phone.png`
- `app/src/test/screenshots/baseline/wish_preview_editor_compact_phone.png`
- `app/src/test/screenshots/baseline/wish_preview_editor_typical_phone.png`
- `app/src/test/screenshots/baseline/wish_preview_editor_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/wish_preview_blocked_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/wish_preview_blocked_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/wish_preview_approved_compact_phone.png`
- `app/src/test/screenshots/baseline/wish_preview_approved_typical_phone.png`
- `app/src/test/screenshots/baseline/wish_preview_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/wish_preview_loading_typical_phone.png`
- `app/src/test/screenshots/baseline/contact_detail_profile_compact_phone.png`
- `app/src/test/screenshots/baseline/contact_detail_profile_typical_phone.png`
- `app/src/test/screenshots/baseline/contact_detail_personalization_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/contact_detail_personalization_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/contact_detail_automation_history_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/contact_detail_automation_history_typical_phone.png`
- `app/src/test/screenshots/baseline/contact_detail_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/contact_detail_preferences_form_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/contact_detail_preferences_form_bottom_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/events_schedule_compact_phone.png`
- `app/src/test/screenshots/baseline/events_schedule_typical_phone.png`
- `app/src/test/screenshots/baseline/events_conflict_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/events_conflict_typical_phone.png`
- `app/src/test/screenshots/baseline/events_conflict_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/events_empty_compact_phone.png`
- `app/src/test/screenshots/baseline/events_empty_typical_phone.png`
- `app/src/test/screenshots/baseline/events_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/events_loading_typical_phone.png`
- `app/src/test/screenshots/baseline/events_manual_event_form_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/events_manual_event_form_new_contact_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/events_manual_event_form_bottom_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/ai_doctor_blockers_compact_phone.png`
- `app/src/test/screenshots/baseline/ai_doctor_blockers_typical_phone.png`
- `app/src/test/screenshots/baseline/ai_doctor_blockers_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/ai_doctor_setup_cards_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/ai_doctor_setup_cards_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/ai_doctor_healthy_compact_phone.png`
- `app/src/test/screenshots/baseline/ai_doctor_healthy_typical_phone.png`
- `app/src/test/screenshots/baseline/ai_doctor_refreshing_compact_phone.png`
- `app/src/test/screenshots/baseline/ai_doctor_refreshing_typical_phone.png`
- `app/src/test/screenshots/baseline/backup_restore_passphrase_compact_phone.png`
- `app/src/test/screenshots/baseline/backup_restore_passphrase_typical_phone.png`
- `app/src/test/screenshots/baseline/backup_restore_actions_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/backup_restore_actions_typical_phone.png`
- `app/src/test/screenshots/baseline/backup_restore_import_preview_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/backup_restore_import_preview_typical_phone.png`
- `app/src/test/screenshots/baseline/backup_restore_import_preview_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/backup_restore_exporting_compact_phone.png`
- `app/src/test/screenshots/baseline/backup_restore_exporting_typical_phone.png`
- `app/src/test/screenshots/baseline/backup_restore_error_compact_phone.png`
- `app/src/test/screenshots/baseline/backup_restore_error_typical_phone.png`
- `app/src/test/screenshots/baseline/settings_overview_compact_phone.png`
- `app/src/test/screenshots/baseline/settings_overview_typical_phone.png`
- `app/src/test/screenshots/baseline/settings_ai_configuration_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/settings_ai_configuration_typical_phone.png`
- `app/src/test/screenshots/baseline/settings_data_tools_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/settings_data_tools_typical_phone.png`
- `app/src/test/screenshots/baseline/settings_data_tools_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/settings_sign_out_dialog_compact_phone.png`
- `app/src/test/screenshots/baseline/settings_sign_out_dialog_typical_phone.png`
- `app/src/test/screenshots/baseline/analytics_populated_compact_phone.png`
- `app/src/test/screenshots/baseline/analytics_populated_typical_phone.png`
- `app/src/test/screenshots/baseline/analytics_reporting_sections_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/analytics_reporting_sections_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/analytics_empty_compact_phone.png`
- `app/src/test/screenshots/baseline/analytics_empty_typical_phone.png`
- `app/src/test/screenshots/baseline/analytics_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/analytics_loading_typical_phone.png`
- `app/src/test/screenshots/baseline/activity_history_populated_compact_phone.png`
- `app/src/test/screenshots/baseline/activity_history_populated_typical_phone.png`
- `app/src/test/screenshots/baseline/activity_history_action_card_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/activity_history_action_card_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/activity_history_empty_compact_phone.png`
- `app/src/test/screenshots/baseline/activity_history_empty_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/activity_history_empty_typical_phone.png`
- `app/src/test/screenshots/baseline/activity_history_error_compact_phone.png`
- `app/src/test/screenshots/baseline/activity_history_error_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/activity_history_error_typical_phone.png`
- `app/src/test/screenshots/baseline/activity_history_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/activity_history_loading_typical_phone.png`
- `app/src/test/screenshots/baseline/chat_history_populated_compact_phone.png`
- `app/src/test/screenshots/baseline/chat_history_populated_typical_phone.png`
- `app/src/test/screenshots/baseline/chat_history_long_message_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/chat_history_long_message_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/chat_history_empty_compact_phone.png`
- `app/src/test/screenshots/baseline/chat_history_empty_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/chat_history_empty_typical_phone.png`
- `app/src/test/screenshots/baseline/chat_history_error_compact_phone.png`
- `app/src/test/screenshots/baseline/chat_history_error_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/chat_history_error_typical_phone.png`
- `app/src/test/screenshots/baseline/chat_history_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/chat_history_loading_typical_phone.png`
- `app/src/test/screenshots/baseline/memory_vault_add_note_compact_phone.png`
- `app/src/test/screenshots/baseline/memory_vault_notes_compact_phone.png`
- `app/src/test/screenshots/baseline/memory_vault_notes_typical_phone.png`
- `app/src/test/screenshots/baseline/memory_vault_pinned_note_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/memory_vault_pinned_note_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/memory_vault_error_empty_compact_phone.png`
- `app/src/test/screenshots/baseline/memory_vault_error_empty_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/memory_vault_error_empty_typical_phone.png`
- `app/src/test/screenshots/baseline/memory_vault_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/memory_vault_loading_typical_phone.png`
- `app/src/test/screenshots/baseline/gift_advisor_suggestions_compact_phone.png`
- `app/src/test/screenshots/baseline/gift_advisor_suggestions_typical_phone.png`
- `app/src/test/screenshots/baseline/gift_advisor_suggestions_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/gift_advisor_history_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/gift_advisor_history_typical_phone.png`
- `app/src/test/screenshots/baseline/gift_advisor_add_dialog_form_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/gift_advisor_add_dialog_form_bottom_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/gift_advisor_error_empty_compact_phone.png`
- `app/src/test/screenshots/baseline/gift_advisor_error_empty_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/gift_advisor_error_empty_typical_phone.png`
- `app/src/test/screenshots/baseline/gift_advisor_generating_compact_phone.png`
- `app/src/test/screenshots/baseline/gift_advisor_generating_typical_phone.png`
- `app/src/test/screenshots/baseline/gift_advisor_loading_compact_phone.png`
- `app/src/test/screenshots/baseline/gift_advisor_loading_typical_phone.png`
- `app/src/test/screenshots/baseline/style_coach_training_compact_phone.png`
- `app/src/test/screenshots/baseline/style_coach_training_typical_phone.png`
- `app/src/test/screenshots/baseline/style_coach_profile_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/style_coach_profile_typical_phone.png`
- `app/src/test/screenshots/baseline/style_coach_profile_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/style_coach_history_compact_phone_large_font.png`
- `app/src/test/screenshots/baseline/style_coach_history_typical_phone.png`
- `app/src/test/screenshots/baseline/style_coach_manual_progress_compact_phone.png`
- `app/src/test/screenshots/baseline/style_coach_manual_progress_typical_phone.png`
- `app/src/test/screenshots/baseline/style_coach_auto_error_empty_compact_phone.png`
- `app/src/test/screenshots/baseline/style_coach_auto_error_empty_compact_phone_hindi_large_font.png`
- `app/src/test/screenshots/baseline/style_coach_auto_error_empty_typical_phone.png`

## Test Rules

- Screenshot tests should render pure content Composables with deterministic fake state.
- Tests must use `android.app.Application` unless a specific production startup behavior is under test.
- Screenshot tests must not hit live APIs, keystore-backed preferences, Room production databases, WorkManager schedulers, or notification channels.
- Golden updates require an accompanying UX checklist note explaining why the visual change is expected.
- Each screenshot test should also have an interaction or ViewModel test covering behavior; screenshots validate presentation, not business logic.
- Screenshot tests use the `com.example.ui.screenshots.ScreenshotTests` JUnit category.
- Normal unit-test commands exclude screenshot tests by default; use `-Pscreenshot` with Roborazzi tasks to include them.
- Dense text-field form dialogs require their own harness stabilization before Roborazzi coverage. Gift Advisor now covers the shared add-gift form body, Contact Preferences covers the shared preferences form body, and Events covers the shared manual-entry form body through deterministic large-font fixtures because platform `AlertDialog` windows still do not idle reliably under JVM screenshot runs.
- Theme-token migrations that should be visually equivalent must run the focused screen screenshot test plus the full Roborazzi suite; D-219 Events, D-220 Chat History, D-221 Splash, and D-222 Auth use this guardrail for production-screen color-role migrations.

## CI Policy

- Pull requests and pushes to `main`/`master` must run `:app:verifyRoborazziDebug -Pscreenshot --tests 'com.example.ui.screenshots.*'`.
- CI uploads `roborazzi-reports` from `app/build/reports/roborazzi`, `app/build/outputs/roborazzi`, and `app/build/test-results/roborazzi` so reviewers can inspect mismatches.
- Baseline updates are allowed only when paired with the UI/code change that caused them and a matching note in `PLAN.md` or the UX audit checklist.
- Do not record baselines in CI. Developers record locally, visually inspect the output, then commit the approved PNGs under `app/src/test/screenshots/baseline`.

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

- Expand large-font screenshot variants beyond the current startup, Home including sync-error coverage, Messages, Wish Preview, Contact Detail including the shared preferences form top/bottom fixtures, Events including manual-entry existing/new/bottom fixtures, AI Doctor, Backup/Restore, Settings, Analytics, Activity History, Chat History, Memory Vault, Gift Advisor including the shared add-form top/bottom fixtures, and Style Coach pilots.
- Expand Hindi screenshot variants beyond the current Onboarding actions, Messages needs-review/failed-recovery/reject-dialog, Wish Preview blocked-review, AI Doctor setup-card, Backup/Restore import-preview, Settings data-tools, Contact Detail personalization, Events conflict, Analytics reporting, Activity History action-card/empty/error, Chat History long-message/empty/error, Memory Vault pinned-note/error-empty, Gift Advisor suggestions/error-empty, and Style Coach learned-profile/auto-error-empty pilots.
- Expand typical-phone variants beyond the current startup/auth/onboarding defaults/actions, Home populated/loading, Messages needs-review/reject-dialog/failed-recovery/loading, Wish Preview editor/approved/loading, AI Doctor blockers/healthy/refreshing, Backup/Restore passphrase/actions/import-preview/exporting/error, Settings overview/AI configuration/data tools/sign-out dialog, Analytics populated/empty/loading, Activity History populated/empty/error/loading, Contact Detail profile/automation-history plus large-font preferences-form fixtures, Events schedule/conflict/empty/loading plus large-font manual-entry fixtures, Chat History populated/empty/error/loading, Memory Vault notes/error-empty/loading, Gift Advisor suggestions/history/error-empty/generating/loading plus large-font add-form fixtures, and Style Coach training/profile/history/manual-progress/auto-error-empty pilots, especially remaining scrolled edge variants.
