# Test Coverage and Gaps

## Latest inspected workflow

[GitHub Actions run 45230577466](https://github.com/yhsomani/AI-Birthday/actions/runs/45230577466)

| Job | Result | Details |
|---|---|---|
| Flutter lint/test/build | Failure | Formatting passed; static analysis passed; 459 tests passed, one failed |
| Backend security/Functions | Failure | `npm ci` failed; downstream audit, lint, TypeScript build, Vitest and emulator tests skipped |
| Public Hosting | Success | Dependency installation, typecheck, tests and production build passed |

### Flutter failure

`test/ui/responsive_mobile_test.dart` — “Calendar adapts safely to 320px compact mobile screen without overflow”. Actual error: `A RenderFlex overflowed by 0.667 pixels on the bottom.` This is a deterministic CI failure to isolate and fix in an implementation phase, not a reason to change product scope.

### Android build evidence

Play Store APK build, SEND_SMS manifest verification, personal sideload APK build and artifact upload steps were skipped after the Flutter test failure. There is no successful Android artifact evidence in this run.

## Flutter test files observed in CI logs

The following 71 distinct Dart test files with `_test.dart` names appeared in the Flutter CI log:

- `test/ai/ai_prompt_builder_test.dart`
- `test/ai/ai_router_test.dart`
- `test/ai/user_gemini_api_provider_test.dart`
- `test/app/midnight_refresh_test.dart`
- `test/app/router_not_found_test.dart`
- `test/app/shell_test.dart`
- `test/core/database/app_database_migration_test.dart`
- `test/core/database/app_database_test.dart`
- `test/core/database/drift_repositories_test.dart`
- `test/core/errors/app_failure_test.dart`
- `test/core/logging/app_logger_test.dart`
- `test/delivery/whatsapp_handoff_test.dart`
- `test/domain/birthday_calculation_test.dart`
- `test/e2e/e2e_suite_test.dart`
- `test/e2e/tier1_features_test.dart`
- `test/e2e/tier2_boundary_corner_test.dart`
- `test/e2e/tier3_cross_feature_test.dart`
- `test/e2e/tier4_user_journeys_test.dart`
- `test/features/auth/adversarial_auth_security_test.dart`
- `test/features/auth/application/auth_controller_test.dart`
- `test/features/auth/data/live_google_auth_gateway_stress_test.dart`
- `test/features/auth/data/live_google_auth_gateway_test.dart`
- `test/features/birthdays/application/birthday_lifecycle_service_test.dart`
- `test/features/birthdays/domain/birthday_engine_test.dart`
- `test/features/calendar/presentation/calendar_empty_state_test.dart`
- `test/features/calendar/presentation/calendar_error_state_test.dart`
- `test/features/calendar/presentation/calendar_screen_test.dart`
- `test/features/dashboard/presentation/dashboard_visibility_test.dart`
- `test/features/delivery/birthday_sms_service_test.dart`
- `test/features/history/presentation/history_screen_test.dart`
- `test/features/jobs/application/ai_job_handler_test.dart`
- `test/features/jobs/application/cloud_job_handler_test.dart`
- `test/features/jobs/application/job_worker_test.dart`
- `test/features/jobs/data/jobs_repository_test.dart`
- `test/features/message_studio/presentation/message_studio_durable_test.dart`
- `test/features/message_studio/presentation/message_studio_not_found_test.dart`
- `test/features/message_studio/presentation/message_studio_optimistic_test.dart`
- `test/features/message_studio/presentation/message_studio_visibility_test.dart`
- `test/features/onboarding/presentation/onboarding_screen_test.dart`
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
- `test/features/reminders/application/android_notification_scheduler_gateway_test.dart`
- `test/features/reminders/application/reminder_service_test.dart`
- `test/features/reminders/application/reminder_settings_controller_persistence_test.dart`
- `test/features/reminders/domain/quiet_hours_test.dart`
- `test/features/reminders/domain/reminder_scheduler_test.dart`
- `test/features/settings/presentation/automatic_sms_card_test.dart`
- `test/features/settings/presentation/settings_auth_test.dart`
- `test/features/settings/presentation/settings_cloud_job_test.dart`
- `test/features/settings/presentation/settings_gemini_onboarding_test.dart`
- `test/features/settings/presentation/settings_notification_revoked_test.dart`
- `test/features/settings/presentation/settings_reminders_test.dart`
- `test/features/subscription/stripe_gateway_service_test.dart`
- `test/features/subscription/subscription_service_test.dart`
- `test/features/subscription/subscription_verification_test.dart`
- `test/features/sync/data/cloud_sync_service_test.dart`
- `test/security/credential_storage_test.dart`
- `test/ui/contrast_test.dart`
- `test/ui/countdown_chip_test.dart`
- `test/ui/responsive_mobile_test.dart`
- `test/ui/section_header_test.dart`
- `test/widget_test.dart`

This is a filename inventory extracted from one run's logs, not a statement that all tests in each file passed. The log summary is 459 passed / 1 failed.

## Backend test gap

Backend CI stopped at dependency installation. The manifest requires npm 11.6.0, but the runner supplied npm 10.9.9. The lockfile also contains version mismatches and missing transitive packages. Backend tests and emulator assertions are NOT RUN in this workflow.

## Hosting evidence

Hosting `check` passed, which runs typecheck, node tests and production build per `backend/hosting/package.json`. No Hosting deployment was established.

## Coverage gaps to close

1. Convert the feature registry's 50 nodes into acceptance-criterion-level source/test mappings.
2. Review every mapped test body and record exact test names/assertions.
3. Add direct tests for feature nodes without direct test mappings.
4. Trace all platform and cross-feature journeys through failure/retry/cancel/process-death paths.
5. Unblock backend install, then run lint, typecheck, Vitest and Firestore emulator suites.
6. Fix and rerun the compact Calendar test; then allow Android APK and manifest checks to execute.
7. Add device and deployment evidence separately from unit/widget CI.
