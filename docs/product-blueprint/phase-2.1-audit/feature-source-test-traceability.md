# Feature Source-to-Test Traceability

**CI run:** [45230577466](https://github.com/yhsomani/AI-Birthday/actions/runs/45230577466)  
**Branch baseline:** `main` @ `4380220014d88a6b05f61466043bc7e319c8dbef`

## Registry mappings

The table below carries forward all 50 legacy registry feature mappings. “Mapped” means the registry names a test file, not that the current test suite proves the whole capability. “No direct test mapped” is a gap signal, not proof that no indirect test exists.

| Feature ID | Source | Test | Traceability |
|---|---|---|---|
| APP | `lib/main.dart` | `test/widget_test.dart` | Mapped; assertion review pending |
| ONB | `lib/features/onboarding/presentation/onboarding_screen.dart` | `test/features/onboarding/presentation/onboarding_screen_test.dart` | Mapped; assertion review pending |
| ONB-FLOW | `lib/features/onboarding/presentation/onboarding_screen.dart` | — | No direct test mapped; inspect indirect coverage |
| ONB-GATE | `lib/app/router.dart` | `test/app/shell_test.dart` | Mapped; assertion review pending |
| NAV | `lib/app/router.dart` | `test/app/router_not_found_test.dart` | Mapped; assertion review pending |
| DASH | `lib/features/dashboard/presentation/dashboard_screen.dart` | `test/features/dashboard/presentation/dashboard_visibility_test.dart` | Mapped; assertion review pending |
| PEOPLE | `lib/features/people/presentation/people_screen.dart` | — | No direct test mapped; inspect indirect coverage |
| PEOPLE-CRUD | `lib/features/people/application/person_service.dart` | `test/features/people/application/person_service_test.dart` | Mapped; assertion review pending |
| PEOPLE-VALIDATE | `lib/features/people/domain/person_input_validator.dart` | `test/features/people/domain/person_input_validator_test.dart` | Mapped; assertion review pending |
| PEOPLE-IMPORT | `lib/features/people/presentation/people_screen.dart` | — | No direct test mapped; inspect indirect coverage |
| PEOPLE-CSV | `lib/features/people/data/contact_csv_service.dart` | `test/features/people/data/contact_csv_service_test.dart` | Mapped; assertion review pending |
| PEOPLE-DEVICE | `lib/features/people/data/device_contacts_service.dart` | `test/features/people/data/device_contacts_service_test.dart` | Mapped; assertion review pending |
| PEOPLE-EXPORT | `lib/features/people/data/contact_csv_service.dart` | `test/features/people/data/contact_csv_service_test.dart` | Mapped; assertion review pending |
| BIRTH | `lib/features/birthdays/domain/birthday_engine.dart` | — | No direct test mapped; inspect indirect coverage |
| BIRTH-CALC | `lib/features/birthdays/domain/birthday_engine.dart` | `test/features/birthdays/domain/birthday_engine_test.dart` | Mapped; assertion review pending |
| BIRTH-CYCLE | `lib/features/birthdays/application/birthday_lifecycle_service.dart` | `test/features/birthdays/application/birthday_lifecycle_service_test.dart` | Mapped; assertion review pending |
| CAL | `lib/features/calendar/presentation/calendar_screen.dart` | `test/features/calendar/presentation/calendar_screen_test.dart` | Mapped; assertion review pending |
| REM | `lib/features/reminders/application/reminder_service.dart` | — | No direct test mapped; inspect indirect coverage |
| REM-RULES | `lib/features/reminders/domain/reminder_schedule.dart` | `test/features/reminders/domain/reminder_scheduler_test.dart` | Mapped; assertion review pending |
| REM-PREFS | `lib/features/reminders/application/reminder_settings_controller.dart` | `test/features/reminders/application/reminder_settings_controller_persistence_test.dart` | Mapped; assertion review pending |
| REM-NATIVE | `android/app/src/main/kotlin/com/yashsomani/ai_birthday/BirthdayNotificationReceiver.kt` | `test/features/reminders/application/android_notification_scheduler_gateway_test.dart` | Mapped; assertion review pending |
| STUDIO | `lib/features/message_studio/presentation/message_studio_screen.dart` | — | No direct test mapped; inspect indirect coverage |
| STUDIO-DRAFT | `lib/features/message_studio/presentation/message_studio_screen.dart` | `test/features/message_studio/presentation/message_studio_durable_test.dart` | Mapped; assertion review pending |
| STUDIO-AI | `lib/features/jobs/application/ai_job_handler.dart` | `test/features/jobs/application/ai_job_handler_test.dart` | Mapped; assertion review pending |
| STUDIO-EDIT | `lib/features/message_studio/presentation/message_studio_screen.dart` | `test/features/message_studio/presentation/message_studio_optimistic_test.dart` | Mapped; assertion review pending |
| AI | `lib/features/ai/domain/ai_router.dart` | `test/ai/ai_router_test.dart` | Mapped; assertion review pending |
| AI-BYOK | `lib/features/ai/data/user_gemini_api_provider.dart` | `test/ai/user_gemini_api_provider_test.dart` | Mapped; assertion review pending |
| AI-NANO | `lib/features/ai/data/gemini_nano_provider.dart` | — | No direct test mapped; inspect indirect coverage |
| AI-PROMPT | `lib/features/ai/domain/ai_prompt_builder.dart` | `test/ai/ai_prompt_builder_test.dart` | Mapped; assertion review pending |
| SEND | `lib/features/delivery/data/whatsapp_handoff_builder.dart` | — | No direct test mapped; inspect indirect coverage |
| SEND-WA | `lib/features/delivery/data/whatsapp_handoff_builder.dart` | `test/delivery/whatsapp_handoff_test.dart` | Mapped; assertion review pending |
| SEND-SMS | `lib/features/delivery/data/sms_delivery_service.dart` | — | No direct test mapped; inspect indirect coverage |
| SEND-SHARE | `lib/features/delivery/data/native_share_service.dart` | — | No direct test mapped; inspect indirect coverage |
| SEND-TRUTH | `lib/features/message_studio/presentation/message_studio_screen.dart` | `test/core/database/drift_repositories_test.dart` | Mapped; assertion review pending |
| HISTORY | `lib/features/history/presentation/history_screen.dart` | `test/features/history/presentation/history_screen_test.dart` | Mapped; assertion review pending |
| AUTH | `lib/features/auth/data/live_google_auth_gateway.dart` | `test/features/auth/data/live_google_auth_gateway_test.dart` | Mapped; assertion review pending |
| BILL | `lib/features/subscription/application/subscription_service.dart` | `test/features/subscription/subscription_service_test.dart` | Mapped; assertion review pending |
| CLOUD | `lib/features/sync/data/cloud_sync_service.dart` | `test/features/sync/data/cloud_sync_service_test.dart` | Mapped; assertion review pending |
| CLOUD-UP | `lib/features/sync/data/cloud_sync_service.dart` | — | No direct test mapped; inspect indirect coverage |
| CLOUD-DOWN | `lib/features/sync/data/cloud_sync_service.dart` | — | No direct test mapped; inspect indirect coverage |
| JOBS | `lib/features/jobs/application/job_worker.dart` | `test/features/jobs/application/job_worker_test.dart` | Mapped; assertion review pending |
| SETTINGS | `lib/features/settings/presentation/settings_screen.dart` | `test/features/settings/presentation/settings_reminders_test.dart` | Mapped; assertion review pending |
| THEME | `lib/app/theme/app_theme.dart` | `test/ui/contrast_test.dart` | Mapped; assertion review pending |
| AUTO-PREP | `lib/features/people/presentation/person_form_screen.dart` | — | No direct test mapped; inspect indirect coverage |
| WEB | `backend/hosting/src/app.ts` | `backend/hosting/test/static-contract.test.mjs` | Mapped; assertion review pending |
| WEB-DELETE | `backend/hosting/src/deletion-contract.ts` | `backend/hosting/test/deletion-contract.test.mjs` | Mapped; assertion review pending |
| CTRL | `backend/functions/src/functions/index.ts` | `backend/functions/test/controlPlane.emulator.test.ts` | Mapped; assertion review pending |
| CTRL-CLAIM | `backend/functions/src/services/controlPlane.ts` | `backend/functions/test/decisions.test.ts` | Mapped; assertion review pending |
| CTRL-DELETE | `backend/functions/src/services/deletionOrchestrator.ts` | `backend/functions/test/deletionReceipt.test.ts` | Mapped; assertion review pending |
| CTRL-PURCHASE | `backend/functions/src/services/subscriptionVerification.ts` | `backend/functions/test/subscriptionVerification.test.ts` | Mapped; assertion review pending |

## Current CI-observed Flutter test files

The following test files appeared in the Flutter CI log for run 45230577466. This is execution evidence for that run only; it does not assert that every test case passed.

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

## Critical assertion-level trace verified in this pass

| Behavior | Implementation | Test | Assertion observed | Result in CI |
|---|---|---|---|---|
| Free entitlement blocks AI even with personal key | `lib/features/ai/domain/ai_router.dart` | `test/ai/ai_router_test.dart` | Saves a user key, calls router with `UserEntitlement.free`, expects `AppFailureCode.aiLocked`; then Pro + key routes to `user_gemini` | Test file ran in Flutter suite; suite has one unrelated responsive Calendar failure |
| No-key Nano path | Same router | Same test | Pro + no key + Nano available expects `gemini_nano` | Test file ran |
| Missing provider | Same router | Same test | Pro + no key + Nano unavailable expects credential-missing | Test file ran |
| Provider timeout | Same router | Same test | Hanging provider maps to `aiTimeout` | Test file ran |
| Person lifecycle service | `lib/features/people/application/person_service.dart` | `test/features/people/application/person_service_test.dart` | Registry maps service test; assertion-level review not completed in this pass | File appeared in CI log |
| Birthday calculation | `lib/features/birthdays/domain/birthday_engine.dart` | `test/features/birthdays/domain/birthday_engine_test.dart` | Registry maps engine test; assertion-level review not completed in this pass | File appeared in CI log |
| Database migration | `lib/core/database/app_database.dart` | `test/core/database/app_database_migration_test.dart` | CI output explicitly reports v4→v5 creates jobs table and preserves existing data | Passed in CI |
| Calendar compact layout | Calendar screen | `test/ui/responsive_mobile_test.dart` | 320px compact-screen no-overflow assertion | **Failed:** bottom RenderFlex overflow 0.667px |

## Unmapped or weakly mapped capabilities

The legacy registry has no direct test mapping for these feature nodes: `ONB-FLOW`, `PEOPLE`, `PEOPLE-IMPORT`, `BIRTH`, `REM`, `STUDIO`, `AI-NANO`, `SEND`, `SEND-SMS`, `SEND-SHARE`, `CLOUD-UP`, `CLOUD-DOWN`, `AUTO-PREP`.

Priority for the next pass:
1. Add or identify direct assertions for critical user-visible behavior currently lacking a direct mapping.
2. Inspect test bodies, not just filenames; split broad test files into behavior-level assertions.
3. Record source-to-test line or test-name references for every acceptance criterion.
4. Add negative, retry, cancellation, permission-denial, process-death and account-switch cases where relevant.
5. Keep backend test status as NOT RUN until dependency installation succeeds and CI executes the backend suite.


## Phase 2.1.1 — Flutter domain-to-test mapping

The complete file list is in [Flutter Test Inventory](flutter-test-inventory.md); source paths are in [Flutter Source Inventory](flutter-source-inventory.md). The following table records direct feature mapping and meaningful coverage limits.

| Domain | Direct tests | Behavior assertions sampled | Uncovered / weakly covered behavior |
|---|---|---|---|
| AI | `test/ai/ai_prompt_builder_test.dart`, `ai_router_test.dart`, `user_gemini_api_provider_test.dart` | Prompt constraints/untrusted user facts; free entitlement blocks even with key; Pro routes to Gemini/Nano; provider timeout and HTTP response parsing | Nano provider has no dedicated unit test; fake AICore only; stale router comments contradict code |
| Auth | `test/features/auth/application/auth_controller_test.dart`, `data/live_google_auth_gateway_test.dart`, `data/live_google_auth_gateway_stress_test.dart`, `adversarial_auth_security_test.dart` | Session restore, sign-in/out, refresh-token behavior, malformed/partial session rejection, PII redaction | Native Google account chooser, real Firebase project and physical secure storage |
| Birthdays | `test/features/birthdays/domain/birthday_engine_test.dart`, `application/birthday_lifecycle_service_test.dart`, `test/domain/birthday_calculation_test.dart` | Leap day, timezone calendar frames, next occurrence, cycle rollover, duplicate records and batched reads | Device timezone changes and live notification consequences |
| Calendar | `calendar_empty_state_test.dart`, `calendar_error_state_test.dart`, `calendar_screen_test.dart`, `test/ui/responsive_mobile_test.dart` | Empty/error retry, date markers, day sheet, month navigation, compact layout | 320px responsive test fails by 0.667px |
| Dashboard | `dashboard_visibility_test.dart` | Completed vs handed-off vs draft-needed states, missing phone hints | Full matrix of combinations and live data refresh |
| Delivery | `birthday_sms_service_test.dart`, `test/delivery/whatsapp_handoff_test.dart`; delivery repository in `drift_repositories_test.dart` | SMS opt-in/approved-draft eligibility, midnight scheduling, Play flavor unsupported behavior, WhatsApp number/URL validation, atomic handoff event | Direct tests absent for NativeShareService, NativeSmsGateway and SmsDeliveryService; actual Android scheduling/send |
| History | `history_screen_test.dart` | Persisted handoff displays “Opened in WhatsApp”; only explicit confirmedSent displays “Sent”; no record remains neutral | Additional channels, duplicate events and larger history pagination |
| Jobs | `jobs_repository_test.dart`, `job_worker_test.dart`, `ai_job_handler_test.dart`, `cloud_job_handler_test.dart` | Deduplication, due ordering, retries, permanent failures, cancellation, stale recovery, stale draft guard, account ownership | OS process kill and real background execution |
| Message Studio | `message_studio_durable_test.dart`, `not_found_test.dart`, `optimistic_test.dart`, `visibility_test.dart` | Draft survives route leave, autosave/generate converge, stale result cannot overwrite edits, handoff/confirmation visibility, paywall/manual path | Live Gemini provider, Android handoff and process-death on device |
| Onboarding | `onboarding_screen_test.dart` | Page flow, skip, manual add and contacts-import navigation | Real device contacts and restart persistence |
| People | 11 files under `test/features/people/` | Validation, birthday rules, CSV round-trip/formula escaping/duplicates, repository tombstones, delete undo, search and visibility | OS contact permissions, large import performance and full data migration |
| Reminders | `reminder_scheduler_test.dart`, `quiet_hours_test.dart`, `reminder_service_test.dart`, `reminder_settings_controller_persistence_test.dart`, `android_notification_scheduler_gateway_test.dart` | Lead planning, timezone, quiet hours, deferral, persistence, optimistic rollback and non-live no-op | Actual AlarmManager delivery, reboot, exact-alarm grant/revocation and OEM restrictions |
| Settings | Six files under `test/features/settings/presentation/` | Auth sheet with fake gateway, Gemini key setup, cloud job status, notification recovery, reminders accordion, SMS flavor UI | CloudBackupCard-specific widget coverage and live billing/backup integrations |
| Subscription | `subscription_service_test.dart`, `subscription_verification_test.dart`, `stripe_gateway_service_test.dart` | Fake Play store events, backend response contract, purchase acknowledgement/retry, stale grant TTL; Stripe MockClient request/response | Real Play Store sandbox, Stripe webhook/server verification; client-side Stripe secret architecture is unsafe |
| Sync | `cloud_sync_service_test.dart` | 500-write chunking, complete generation, no pointer after failed row, malformed restore abort, tombstone-safe restore | Real Firestore authentication/rules and deployed project behavior |

## Fake-backed test classification

- `test/e2e/tier1_features_test.dart` uses `FakeFirebaseAuthClient`, test storage and fake AICore. Its R1.1 case simulates an Identity Platform response and writes the test session; it does not invoke the production Google sign-in gateway.
- Tier 2 uses fake subscription server and fake platform clients.
- Tier 3/4 use the E2E harness and fake AI Core/store components.
- Live Google Auth gateway tests inject HTTP and platform doubles.
- Subscription verification tests inject fake store and HTTP responses.
- Cloud sync tests inject HTTP senders.
- Stripe tests use `MockClient`.

These tests are still useful and should be retained. Their evidence label must be “fake-backed integration/contract test”, not “live external integration verified”.
