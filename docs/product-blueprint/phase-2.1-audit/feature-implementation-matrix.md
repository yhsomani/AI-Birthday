# Feature Implementation Matrix

**Baseline:** `main` @ `4380220014d88a6b05f61466043bc7e319c8dbef`  
**Registry source snapshot:** `75b9d91aadcc1c4136fdcb2c06340d3744bc50a4` (2026-10-14)  
**Interpretation:** The status below is the *legacy registry label*, not a newly certified implementation status. This table preserves its mappings and exposes gaps for re-review.

| ID | Capability | Legacy label | Source mapping | Test mapping | Audit note |
|---|---|---|---|---|---|
| APP | AI-Birthday Android application | implemented (legacy registry label) | `lib/main.dart` | `test/widget_test.dart` | Mapped |
| ONB | Onboarding | implemented (legacy registry label) | `lib/features/onboarding/presentation/onboarding_screen.dart` | `test/features/onboarding/presentation/onboarding_screen_test.dart` | Mapped |
| ONB-FLOW | Intro, skip and start flows | implemented (legacy registry label) | `lib/features/onboarding/presentation/onboarding_screen.dart` | — | No direct test mapped |
| ONB-GATE | First-run persistence and routing gate | implemented (legacy registry label) | `lib/app/router.dart` | `test/app/shell_test.dart` | Mapped |
| NAV | Navigation shell | implemented (legacy registry label) | `lib/app/router.dart` | `test/app/router_not_found_test.dart` | Mapped |
| DASH | Today, upcoming and action dashboard | implemented (legacy registry label) | `lib/features/dashboard/presentation/dashboard_screen.dart` | `test/features/dashboard/presentation/dashboard_visibility_test.dart` | Mapped |
| PEOPLE | People management | implemented (legacy registry label) | `lib/features/people/presentation/people_screen.dart` | — | No direct test mapped |
| PEOPLE-CRUD | Create, edit, soft-delete, undo | implemented (legacy registry label) | `lib/features/people/application/person_service.dart` | `test/features/people/application/person_service_test.dart` | Mapped |
| PEOPLE-VALIDATE | Form validation and normalized fields | implemented (legacy registry label) | `lib/features/people/domain/person_input_validator.dart` | `test/features/people/domain/person_input_validator_test.dart` | Mapped |
| PEOPLE-IMPORT | Import review and selection | implemented (legacy registry label) | `lib/features/people/presentation/people_screen.dart` | — | No direct test mapped |
| PEOPLE-CSV | CSV parse, diagnostics and duplicate hints | implemented (legacy registry label) | `lib/features/people/data/contact_csv_service.dart` | `test/features/people/data/contact_csv_service_test.dart` | Mapped |
| PEOPLE-DEVICE | Permission-gated Android contacts import | implemented (legacy registry label) | `lib/features/people/data/device_contacts_service.dart` | `test/features/people/data/device_contacts_service_test.dart` | Mapped |
| PEOPLE-EXPORT | CSV export and share sheet | implemented (legacy registry label) | `lib/features/people/data/contact_csv_service.dart` | `test/features/people/data/contact_csv_service_test.dart` | Mapped |
| BIRTH | Birthday recurrence and lifecycle | implemented (legacy registry label) | `lib/features/birthdays/domain/birthday_engine.dart` | — | No direct test mapped |
| BIRTH-CALC | Next birthday, timezone, leap-day policy | implemented (legacy registry label) | `lib/features/birthdays/domain/birthday_engine.dart` | `test/features/birthdays/domain/birthday_engine_test.dart` | Mapped |
| BIRTH-CYCLE | Lifecycle refresh and occurrence rollover | implemented (legacy registry label) | `lib/features/birthdays/application/birthday_lifecycle_service.dart` | `test/features/birthdays/application/birthday_lifecycle_service_test.dart` | Mapped |
| CAL | Interactive calendar and day detail | implemented (legacy registry label) | `lib/features/calendar/presentation/calendar_screen.dart` | `test/features/calendar/presentation/calendar_screen_test.dart` | Mapped |
| REM | Local birthday reminders | implemented (legacy registry label) | `lib/features/reminders/application/reminder_service.dart` | — | No direct test mapped |
| REM-RULES | Offset and quiet-hour schedule planning | implemented (legacy registry label) | `lib/features/reminders/domain/reminder_schedule.dart` | `test/features/reminders/domain/reminder_scheduler_test.dart` | Mapped |
| REM-PREFS | Persisted reminder and privacy settings | implemented (legacy registry label) | `lib/features/reminders/application/reminder_settings_controller.dart` | `test/features/reminders/application/reminder_settings_controller_persistence_test.dart` | Mapped |
| REM-NATIVE | Android alarm and notification adapter | implemented (legacy registry label) | `android/app/src/main/kotlin/com/yashsomani/ai_birthday/BirthdayNotificationReceiver.kt` | `test/features/reminders/application/android_notification_scheduler_gateway_test.dart` | Mapped |
| STUDIO | Message Studio | implemented (legacy registry label) | `lib/features/message_studio/presentation/message_studio_screen.dart` | — | No direct test mapped |
| STUDIO-DRAFT | Manual editing and autosave | implemented (legacy registry label) | `lib/features/message_studio/presentation/message_studio_screen.dart` | `test/features/message_studio/presentation/message_studio_durable_test.dart` | Mapped |
| STUDIO-AI | AI generation and durable request | implemented (legacy registry label) | `lib/features/jobs/application/ai_job_handler.dart` | `test/features/jobs/application/ai_job_handler_test.dart` | Mapped |
| STUDIO-EDIT | Rewrite, translate and variations | implemented (legacy registry label) | `lib/features/message_studio/presentation/message_studio_screen.dart` | `test/features/message_studio/presentation/message_studio_optimistic_test.dart` | Mapped |
| AI | AI providers and routing | implemented (legacy registry label) | `lib/features/ai/domain/ai_router.dart` | `test/ai/ai_router_test.dart` | Mapped |
| AI-BYOK | User Gemini API credential and provider | implemented (legacy registry label) | `lib/features/ai/data/user_gemini_api_provider.dart` | `test/ai/user_gemini_api_provider_test.dart` | Mapped |
| AI-NANO | Native Gemini Nano adapter | implemented (legacy registry label) | `lib/features/ai/data/gemini_nano_provider.dart` | — | No direct test mapped |
| AI-PROMPT | User-fact-bounded prompt assembly | implemented (legacy registry label) | `lib/features/ai/domain/ai_prompt_builder.dart` | `test/ai/ai_prompt_builder_test.dart` | Mapped |
| SEND | User-controlled delivery handoffs | implemented (legacy registry label) | `lib/features/delivery/data/whatsapp_handoff_builder.dart` | — | No direct test mapped |
| SEND-WA | WhatsApp click-to-chat URL | implemented (legacy registry label) | `lib/features/delivery/data/whatsapp_handoff_builder.dart` | `test/delivery/whatsapp_handoff_test.dart` | Mapped |
| SEND-SMS | SMS delivery handoff | implemented (legacy registry label) | `lib/features/delivery/data/sms_delivery_service.dart` | — | No direct test mapped |
| SEND-SHARE | System share and copy | implemented (legacy registry label) | `lib/features/delivery/data/native_share_service.dart` | — | No direct test mapped |
| SEND-TRUTH | Handoff evidence and explicit sent confirmation | implemented (legacy registry label) | `lib/features/message_studio/presentation/message_studio_screen.dart` | `test/core/database/drift_repositories_test.dart` | Mapped |
| HISTORY | Draft and delivery history | implemented (legacy registry label) | `lib/features/history/presentation/history_screen.dart` | `test/features/history/presentation/history_screen_test.dart` | Mapped |
| AUTH | Google authentication and session renewal | implemented (legacy registry label) | `lib/features/auth/data/live_google_auth_gateway.dart` | `test/features/auth/data/live_google_auth_gateway_test.dart` | Mapped |
| BILL | Google Play subscription and entitlement | implemented (legacy registry label) | `lib/features/subscription/application/subscription_service.dart` | `test/features/subscription/subscription_service_test.dart` | Mapped |
| CLOUD | Opt-in account-scoped cloud backup | implemented (legacy registry label) | `lib/features/sync/data/cloud_sync_service.dart` | `test/features/sync/data/cloud_sync_service_test.dart` | Mapped |
| CLOUD-UP | Immutable snapshot-generation upload | implemented (legacy registry label) | `lib/features/sync/data/cloud_sync_service.dart` | — | No direct test mapped |
| CLOUD-DOWN | Manifest-validated restore | implemented (legacy registry label) | `lib/features/sync/data/cloud_sync_service.dart` | — | No direct test mapped |
| JOBS | Durable background operation queue | implemented (legacy registry label) | `lib/features/jobs/application/job_worker.dart` | `test/features/jobs/application/job_worker_test.dart` | Mapped |
| SETTINGS | Preferences, account, backup and AI settings | implemented (legacy registry label) | `lib/features/settings/presentation/settings_screen.dart` | `test/features/settings/presentation/settings_reminders_test.dart` | Mapped |
| THEME | UI theme and shared design components | implemented (legacy registry label) | `lib/app/theme/app_theme.dart` | `test/ui/contrast_test.dart` | Mapped |
| AUTO-PREP | Consent-aware scheduled AI draft preparation | partially-implemented (legacy registry label) | `lib/features/people/presentation/person_form_screen.dart` | — | No direct test mapped |
| WEB | Public support, legal and deletion site | implemented (legacy registry label) | `backend/hosting/src/app.ts` | `backend/hosting/test/static-contract.test.mjs` | Mapped |
| WEB-DELETE | Deletion reauthentication and receipt contract | implemented (legacy registry label) | `backend/hosting/src/deletion-contract.ts` | `backend/hosting/test/deletion-contract.test.mjs` | Mapped |
| CTRL | Isolated Firebase coordination control plane | implemented (legacy registry label) | `backend/functions/src/functions/index.ts` | `backend/functions/test/controlPlane.emulator.test.ts` | Mapped |
| CTRL-CLAIM | Sender lease, claim, arm and retry fencing | implemented (legacy registry label) | `backend/functions/src/services/controlPlane.ts` | `backend/functions/test/decisions.test.ts` | Mapped |
| CTRL-DELETE | Deletion and repair operations | implemented (legacy registry label) | `backend/functions/src/services/deletionOrchestrator.ts` | `backend/functions/test/deletionReceipt.test.ts` | Mapped |
| CTRL-PURCHASE | Server purchase verification | implemented (legacy registry label) | `backend/functions/src/services/subscriptionVerification.ts` | `backend/functions/test/subscriptionVerification.test.ts` | Mapped |

## Cross-cutting current source checks

| Area | Directly inspected source | Verified facts | Remaining work |
|---|---|---|---|
| App composition | `lib/main.dart`, `lib/app/router.dart`, `lib/app/providers.dart` | Flutter + Riverpod composition; onboarding redirect; five primary shell branches; root providers include subscription/auth/jobs/draft maintenance | Trace every route and provider to feature-level lifecycle and tests |
| People | `lib/features/people/application/person_service.dart` | Create/update validation, soft-delete, restore, version update and change callback are implemented in this service | Inspect UI forms, repositories, duplicate/import/export flows and every test assertion |
| Birthday | `lib/features/birthdays/domain/birthday_engine.dart` | Leap-day policy, date validity, timezone-aware next occurrence logic | Trace lifecycle service, recurrence rollover and all UI consumers |
| Local database | `lib/core/database/app_database.dart` | Six tables, schema version 5, migrations and indexes; per-table sync metadata differs | Generated schema parity, every repository, transaction semantics and restore behavior |
| AI | `lib/features/ai/domain/ai_router.dart` | Entitlement check occurs before provider routing; Nano and BYOK provider branches exist | Inspect prompt/provider failure modes and complete job path |
| Cloud sync | `lib/features/sync/data/cloud_sync_service.dart` | Firestore REST commit path, authenticated UID/token handling, 500-write commit limit, retry classification | Complete upload/download/manifest/conflict/restore trace and emulator evidence |
| Android | `MainActivity.kt` | Five MethodChannels: notifications, Nano, share, contacts, SMS automation | Map every Dart caller, permissions, receiver, manifest flavor and device behavior |
| Backend | `backend/functions/src/functions/index.ts` | 18 callable exports and two scheduled exports are declared | Read all handlers, transport schemas, services, persistence and tests |
| Firestore rules | `backend/firestore.rules` | Owner-scoped entitlement/backup access and deletion write fence are declared | Emulator tests did not run in latest CI; deployment not verified |

## Status vocabulary for the next audit pass

- **Source verified:** behavior was traced in current implementation.
- **Test mapped:** a test contains a relevant assertion; record exact test name and assertion.
- **CI observed:** the mapped test ran in the identified CI run.
- **Device verified:** observed on a physical device and OS version.
- **Deployed verified:** deployed target and environment were inspected.
- **Open:** evidence missing, conflicting or not yet inspected.

Do not convert legacy `implemented` to “complete” without those dimensions.
