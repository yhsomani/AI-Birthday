# Flutter Dart Test Inventory

**Source baseline:** `85795e845a945ac3442405ed843b4f12c6983249`  
**Scope:** All 72 files ending in `_test.dart` under `test/`.

The tree contains 72 Dart test files: 47 under `test/features/` and 25 cross-cutting / AI / app / database / E2E / security / UI tests. The presence of a test file does not mean every feature behavior is covered.

## Feature-domain test files — 47

### Auth — 4
- `test/features/auth/adversarial_auth_security_test.dart`
- `test/features/auth/application/auth_controller_test.dart`
- `test/features/auth/data/live_google_auth_gateway_stress_test.dart`
- `test/features/auth/data/live_google_auth_gateway_test.dart`

### Birthdays — 2
- `test/features/birthdays/application/birthday_lifecycle_service_test.dart`
- `test/features/birthdays/domain/birthday_engine_test.dart`

### Calendar — 3
- `test/features/calendar/presentation/calendar_empty_state_test.dart`
- `test/features/calendar/presentation/calendar_error_state_test.dart`
- `test/features/calendar/presentation/calendar_screen_test.dart`

### Dashboard — 1
- `test/features/dashboard/presentation/dashboard_visibility_test.dart`

### Delivery — 1
- `test/features/delivery/birthday_sms_service_test.dart`

### History — 1
- `test/features/history/presentation/history_screen_test.dart`

### Jobs — 4
- `test/features/jobs/application/ai_job_handler_test.dart`
- `test/features/jobs/application/cloud_job_handler_test.dart`
- `test/features/jobs/application/job_worker_test.dart`
- `test/features/jobs/data/jobs_repository_test.dart`

### Message Studio — 4
- `test/features/message_studio/presentation/message_studio_durable_test.dart`
- `test/features/message_studio/presentation/message_studio_not_found_test.dart`
- `test/features/message_studio/presentation/message_studio_optimistic_test.dart`
- `test/features/message_studio/presentation/message_studio_visibility_test.dart`

### Onboarding — 1
- `test/features/onboarding/presentation/onboarding_screen_test.dart`

### People — 11
- `test/features/people/application/person_service_test.dart`
- `test/features/people/data/contact_csv_service_test.dart`
- `test/features/people/data/device_contacts_service_test.dart`
- `test/features/people/data/person_repository_test.dart`
- `test/features/people/domain/person_input_validator_test.dart`
- `test/features/people/domain/relationship_card_label_test.dart`
- `test/features/people/presentation/people_screen_delete_test.dart`
- `test/features/people/presentation/people_screen_search_test.dart`
- `test/features/people/presentation/people_screen_visibility_test.dart`
- `test/features/people/presentation/person_form_discard_test.dart`
- `test/features/people/presentation/person_form_saving_indicator_test.dart`

### Reminders — 5
- `test/features/reminders/application/android_notification_scheduler_gateway_test.dart`
- `test/features/reminders/application/reminder_service_test.dart`
- `test/features/reminders/application/reminder_settings_controller_persistence_test.dart`
- `test/features/reminders/domain/quiet_hours_test.dart`
- `test/features/reminders/domain/reminder_scheduler_test.dart`

### Settings — 6
- `test/features/settings/presentation/automatic_sms_card_test.dart`
- `test/features/settings/presentation/settings_auth_test.dart`
- `test/features/settings/presentation/settings_cloud_job_test.dart`
- `test/features/settings/presentation/settings_gemini_onboarding_test.dart`
- `test/features/settings/presentation/settings_notification_revoked_test.dart`
- `test/features/settings/presentation/settings_reminders_test.dart`

### Subscription — 3
- `test/features/subscription/stripe_gateway_service_test.dart`
- `test/features/subscription/subscription_service_test.dart`
- `test/features/subscription/subscription_verification_test.dart`

### Sync — 1
- `test/features/sync/data/cloud_sync_service_test.dart`

## AI tests — 3
- `test/ai/ai_prompt_builder_test.dart`
- `test/ai/ai_router_test.dart`
- `test/ai/user_gemini_api_provider_test.dart`

## App-shell tests — 4
- `test/app/midnight_refresh_test.dart`
- `test/app/router_not_found_test.dart`
- `test/app/shell_goldens_test.dart`
- `test/app/shell_test.dart`

## Core / database / error / logging tests — 5
- `test/core/database/app_database_migration_test.dart`
- `test/core/database/app_database_test.dart`
- `test/core/database/drift_repositories_test.dart`
- `test/core/errors/app_failure_test.dart`
- `test/core/logging/app_logger_test.dart`

## Cross-feature and domain tests — 2
- `test/delivery/whatsapp_handoff_test.dart`
- `test/domain/birthday_calculation_test.dart`

## E2E-labelled suite — 5
- `test/e2e/e2e_suite_test.dart`
- `test/e2e/tier1_features_test.dart`
- `test/e2e/tier2_boundary_corner_test.dart`
- `test/e2e/tier3_cross_feature_test.dart`
- `test/e2e/tier4_user_journeys_test.dart`

## Security, UI and root widget tests — 6
- `test/security/credential_storage_test.dart`
- `test/ui/contrast_test.dart`
- `test/ui/countdown_chip_test.dart`
- `test/ui/responsive_mobile_test.dart`
- `test/ui/section_header_test.dart`
- `test/widget_test.dart`

## Evidence interpretation

- The 72-file count is derived from the repository tree, not CI output.
- Latest CI run [45231730459](https://github.com/yhsomani/AI-Birthday/actions/runs/45231730459) reports 459 passed and one failed Flutter test.
- The E2E-labelled tests use fake Firebase Auth clients, fake subscription server/store drivers, fake AICore platform and test harnesses. They are useful deterministic contract/journey tests, not proof of live external systems.
- The Google auth gateway tests use mocked HTTP and fake Google Sign-In platform; subscription tests use fake purchase drivers and mocked HTTP; sync tests use an injected HTTP sender; Stripe tests use `MockClient`.
- The native notification gateway test explicitly exercises non-live Android no-op behavior. No dedicated test file directly targets `NativeShareService`, `NativeSmsGateway` or `SmsDeliveryService`.
- A test file may include helper declarations or groups without adding an independent executable test case; file count is not test-case count.
