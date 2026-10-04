# AI-Birthday — Requirements Traceability Matrix

**Authority:** `SSOT.md` (final). This file maps requirements to implementation and evidence. No requirement is marked complete without evidence.

**Status legend:** `MISSING` (not implemented) · `PARTIAL` (some layers exist) · `IMPLEMENTED` (vertical slice + tests) · `ENV` (blocked on environment/device only)

| ID | Requirement | Source | Existing state | Target | Deps | Test evidence | Status |
|---|---|---|---|---|---|---|---|
| FND-001 | Flutter baseline with Riverpod, go_router, Material 3 | SSOT §3/§24 | Clean-slate repo | `lib/` feature-first structure | — | `test/widget_test.dart` (shell + reduced motion) | IMPLEMENTED |
| FND-002 | Domain error system (typed failures) | ARCHITECTURE §9 | New | `core/errors/app_failure.dart` | — | `test/core/errors/app_failure_test.dart` | IMPLEMENTED |
| FND-003 | Sanitized structured logging, no secrets | SSOT §23, SECURITY §8 | New | `core/logging/app_logger.dart` | FND-002 | `test/core/logging/app_logger_test.dart` | IMPLEMENTED |
| FND-004 | Drift/SQLite operational store with sync envelope | SSOT §13/§19 | New | `core/database/app_database.dart` (Persons) | — | `test/core/database/app_database_test.dart` | IMPLEMENTED |
| FND-005 | Secure secret storage layer | SSOT §3, SECURITY §2 | New | `core/security/secret_store.dart` | — | (integration on device in Phase 3) | PARTIAL |
| FND-006 | Material 3 design foundation + shared widgets | UI/UX §1, §11 | New | `app/theme`, `shared/design_system` | FND-001 | via widget tests | IMPLEMENTED |
| FND-007 | Typed Flutter/Kotlin platform boundary foundation | SSOT §25, ADR-010 | Interface only | `core/platform/gemini_nano_platform.dart` | — | Phase 4 native | PARTIAL |
| FND-008 | Navigation shell (Home/Birthdays/Calendar/History/Settings) | SSOT §15, UI/UX §2 | Shell + empty states | `app/router.dart`, `app/app_scaffold.dart` | FND-001 | `test/widget_test.dart` | IMPLEMENTED |
| FR-002 | People CRUD | TRD FR-002, SSOT §7 | Create/edit/list/delete(undo) vertical slice | `features/people/` full | FND-004 | `person_input_validator_test.dart`, `person_repository_test.dart`, `person_service_test.dart`, `person_flow_test.dart` | IMPLEMENTED |
| FR-003 | Birthday engine (recurrence, tz, leap day) | TRD FR-003, SSOT §14 | Implemented | `features/birthdays/domain/` | — | `test/features/birthdays/domain/birthday_engine_test.dart` (16) | IMPLEMENTED |
| FND-009 | Calendar annual view | SSOT §15 nav, F-SPEC §2 | Month grid + leap-day resolution + day sheet | `features/calendar/presentation/` | FR-003, FND-004 | `test/features/calendar/presentation/calendar_screen_test.dart` (4) | IMPLEMENTED |
| FND-010 | Home dashboard (Today/Upcoming/Action/Quick actions) | SSOT §15 | Engine-driven feed over live people | `features/dashboard/` | FR-003, FND-004 | `home_feed_test.dart`, `home_screen_test.dart` (9) | IMPLEMENTED |
| FR-001 | Google sign-in (single login) | TRD FR-001, SSOT §12 | Gateway boundary + controller + Settings wiring | `features/auth/` | Firebase/device | `test/features/auth/application/auth_controller_test.dart`, `test/features/settings/presentation/settings_auth_test.dart` (6) | IMPLEMENTED |
| FR-004 | Contact import (Android/CSV/vCard) | TRD FR-004, SSOT §18 | Missing | `features/people/` import | — | — | MISSING |
| FR-005 | Reminders (offsets, quiet hours, permission) | TRD FR-005, SSOT §17 | Scheduler + service + settings UI | `features/reminders/` | FR-003, FND-004 | `reminder_scheduler_test.dart`, `quiet_hours_test.dart`, `reminder_service_test.dart`, `settings_reminders_test.dart` | IMPLEMENTED |
| FR-006 | Application entitlement gate | TRD FR-006, SSOT §11 | Missing | `features/subscription/` | phase 2 | — | MISSING |
| FR-007 | User Gemini provider (secure, test/replace/remove) | TRD FR-007, SSOT §5 | Missing | `features/ai/` | FND-005 | phase 3 | MISSING |
| FR-008 | Gemini Nano (real AICore/ML Kit path) | TRD FR-008, SSOT §5/§7 | Interface only | `GeminiNanoBridge.kt` + adapter | FND-007 | phase 4 | MISSING |
| FR-009 | AiRouter (entitlement → credential → nano → unavailable) | TRD FR-009, SSOT §5 | Missing | `features/ai/ai_router.dart` | FR-006/7/8 | phase 5 | MISSING |
| FR-010 | Prompt builder (trusted vs untrusted; no invented facts) | TRD FR-010, SSOT §21 | Missing | `features/ai/prompt_builder.dart` | — | — | MISSING |
| FR-011 | Message Studio (draft/edit/review/version/deliver) | TRD FR-011, SSOT §16 | Missing | `features/message_studio/` | FR-009 | phase 6 | MISSING |
| FR-012 | WhatsApp Click-to-Chat handoff | TRD FR-012, SSOT §9 | Missing | `features/delivery/whatsapp_handoff_service.dart` | — | phase 7 | MISSING |
| FR-013 | SMS adapter with accurate semantics | TRD FR-013, SSOT §10 | Missing | `features/delivery/` | — | phase 7 | MISSING |
| FR-014 | Subscription (Play Billing + backend verify + restore) | TRD FR-014, SSOT §11 | Missing | `features/subscription/` | backend | phase 2 | MISSING |
| FR-015 | Offline-first | TRD FR-015, SSOT §13 | DB is local | — | FND-004 | phase 8 | PARTIAL |
| FR-016 | Sync (versioned, tombstones, no credential sync) | TRD FR-016, SSOT §19 | Envelope only | `core/sync/` | — | phase 8 | PARTIAL |
| NFR-001 | Security controls | SECURITY | Layer foundations | — | — | ongoing | PARTIAL |
| NFR-002 | Performance (non-blocking, local-first) | TRD NFR-002 | Design follows it | — | — | phase 9 | MISSING |
| NFR-003 | Accessibility on all P0 screens | TRD NFR-003 | Shell passes reduced-motion + text-scale | — | — | widget tests | PARTIAL |
| NFR-004 | Idempotency of repeatable ops | TRD NFR-004 | Design follows it | — | — | phase 8 | MISSING |
| NFR-005 | Observability (op id, sanitized) | TRD NFR-005 | Logger implemented | — | FND-003 | implemented | IMPLEMENTED |
| E2E-001..010 | Login/add/import/AI cred/AI nano/AI unavailable/WhatsApp/subscribe/restore/delete | SSOT §26 | Missing | — | — | phase 9 | MISSING |
| SEC-001..006 | Credential leaks / authz / entitlement fraud / replays / duplicate send / prompt injection | SSOT §26 | Foundation tests | — | — | phase 8/9 | PARTIAL |
| ACC-001 | Accessibility audit on P0 screens | TRD NFR-003 | Shell smoke test | — | — | ongoing | PARTIAL |

**Current feature:** Phase 0 complete → Phase 1: Birthday engine + People CRUD + Calendar + Reminders + Home dashboard + Auth boundary (Gateway + controller + Settings wiring) done; next: History (message drafts land with Message Studio). Google/Firebase sign-in wiring and reminder device delivery are device-gated.

**Notes**
- Legacy React Native/Kotlin code was removed from the working tree and exists only in git history. It is migration evidence and is intentionally **not** the implementation baseline.
- Firebase reference material was restored from git history into `firebase/` (google-services configs, reference Kotlin clients) and `backend/` (Firebase Functions + Firestore rules + hosting, project `demo-birthday-autopilot` default). Nothing is wired into the Android build yet: the registered Android client (`com.aistudio.relateai.qxtjrk`) predates the current applicationId (`com.yashsomani.ai_birthday`); a matching client must be registered in the Firebase console before `com.google.gms.google-services` is enabled.
- Notifications are scheduled through the abstract `NotificationSchedulerGateway` (SSOT §17); the device implementation (`flutter_local_notifications` + Android permission flow) lands in a device phase. No fake scheduler is shipped.
- Google sign-in runs through the abstract `GoogleAuthGateway` (SSOT §12, FR-001): the host build truthfully reports `unavailable` until a Firebase client matching `com.yashsomani.ai_birthday` is registered and the real `firebase_auth`/Google Sign-In gateway is wired on device (reference: `firebase/reference/FirebaseIdentityRuntime.kt`). No fake sign-in is shipped.
- `Only facts supplied by the user` and `No invented facts` remain hard constraints for every AI feature (SSOT §7, §21).