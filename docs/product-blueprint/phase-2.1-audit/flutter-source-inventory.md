# Flutter Feature Source Inventory

**Source baseline:** `85795e845a945ac3442405ed843b4f12c6983249`  
**Scope:** Every Dart file under `lib/features/` at the baseline.

The recursive Git tree contains **65 Dart feature source files across 15 domains**. Each path below was fetched for this audit.

## AI — 5 files
- `lib/features/ai/data/gemini_nano_provider.dart`
- `lib/features/ai/data/user_gemini_api_provider.dart`
- `lib/features/ai/domain/ai_prompt_builder.dart`
- `lib/features/ai/domain/ai_provider.dart`
- `lib/features/ai/domain/ai_router.dart`

## Auth — 6 files
- `lib/features/auth/application/auth_controller.dart`
- `lib/features/auth/application/google_auth_gateway.dart`
- `lib/features/auth/data/live_google_auth_gateway.dart`
- `lib/features/auth/domain/auth_state.dart`
- `lib/features/auth/domain/google_identity.dart`
- `lib/features/auth/presentation/auth_bottom_sheet.dart`

## Birthdays — 4 files
- `lib/features/birthdays/application/birthday_lifecycle_service.dart`
- `lib/features/birthdays/domain/birthday_engine.dart`
- `lib/features/birthdays/domain/models/birthday.dart`
- `lib/features/birthdays/domain/repositories/birthdays_repository.dart`

## Calendar — 1 file
- `lib/features/calendar/presentation/calendar_screen.dart`

## Dashboard — 1 file
- `lib/features/dashboard/presentation/dashboard_screen.dart`

## Delivery — 8 files
- `lib/features/delivery/application/birthday_sms_service.dart`
- `lib/features/delivery/data/native_share_service.dart`
- `lib/features/delivery/data/native_sms_gateway.dart`
- `lib/features/delivery/data/sms_delivery_service.dart`
- `lib/features/delivery/data/whatsapp_handoff_builder.dart`
- `lib/features/delivery/domain/models/delivery_channel.dart`
- `lib/features/delivery/domain/models/delivery_handoff.dart`
- `lib/features/delivery/domain/repositories/delivery_events_repository.dart`

## History — 1 file
- `lib/features/history/presentation/history_screen.dart`

## Jobs — 5 files
- `lib/features/jobs/application/ai_job_handler.dart`
- `lib/features/jobs/application/cloud_job_handler.dart`
- `lib/features/jobs/application/job_worker.dart`
- `lib/features/jobs/data/jobs_repository.dart`
- `lib/features/jobs/domain/job.dart`

## Message Studio — 3 files
- `lib/features/message_studio/domain/models/message_draft.dart`
- `lib/features/message_studio/domain/repositories/drafts_repository.dart`
- `lib/features/message_studio/presentation/message_studio_screen.dart`

## Onboarding — 1 file
- `lib/features/onboarding/presentation/onboarding_screen.dart`

## People — 15 files
- `lib/features/people/application/person_service.dart`
- `lib/features/people/data/contact_csv_service.dart`
- `lib/features/people/data/device_contacts_service.dart`
- `lib/features/people/data/person_providers.dart`
- `lib/features/people/data/person_repository.dart`
- `lib/features/people/domain/models/person.dart`
- `lib/features/people/domain/models/relationship.dart`
- `lib/features/people/domain/models/tone.dart`
- `lib/features/people/domain/person.dart`
- `lib/features/people/domain/person_enums.dart`
- `lib/features/people/domain/person_input.dart`
- `lib/features/people/domain/person_input_validator.dart`
- `lib/features/people/domain/repositories/people_repository.dart`
- `lib/features/people/presentation/people_screen.dart`
- `lib/features/people/presentation/person_form_screen.dart`

## Reminders — 8 files
- `lib/features/reminders/application/android_notification_scheduler_gateway.dart`
- `lib/features/reminders/application/notification_scheduler_gateway.dart`
- `lib/features/reminders/application/reminder_providers.dart`
- `lib/features/reminders/application/reminder_service.dart`
- `lib/features/reminders/application/reminder_settings_controller.dart`
- `lib/features/reminders/domain/quiet_hours.dart`
- `lib/features/reminders/domain/reminder_kind.dart`
- `lib/features/reminders/domain/reminder_schedule.dart`

## Settings — 3 files
- `lib/features/settings/presentation/settings_screen.dart`
- `lib/features/settings/presentation/widgets/automatic_sms_card.dart`
- `lib/features/settings/presentation/widgets/cloud_backup_card.dart`

## Subscription — 3 files
- `lib/features/subscription/application/stripe_gateway_service.dart`
- `lib/features/subscription/application/subscription_service.dart`
- `lib/features/subscription/domain/entitlement.dart`

## Sync — 1 file
- `lib/features/sync/data/cloud_sync_service.dart`

## Cross-cutting Flutter files also sampled

App composition and persistence anchors inspected alongside the feature tree:
- `lib/main.dart`
- `lib/app/app.dart`
- `lib/app/router.dart`
- `lib/app/providers.dart`
- `lib/core/database/app_database.dart`
- `lib/core/database/drift_repositories.dart`

These six cross-cutting files are not included in the 65 feature-source count. The rest of `lib/core/`, `lib/shared/` and generated localization files require separate cross-cutting review.
