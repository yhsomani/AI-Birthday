# AI-Birthday — Implementation Status

**Updated:** 2026-09-24 · **Authority:** `SSOT.md`

## Overall progress

- **Phase 0 — Foundation:** complete (Flutter baseline, Riverpod, go_router, Material 3, Drift, secure storage abstraction, domain errors, sanitized logging, platform boundary definition, navigation shell).
- **Phase 1 — Core product:** in progress. Birthday engine, People CRUD, Calendar, Reminders (device gateway gated), Home dashboard and the Auth boundary (gateway + controller + Settings wiring, device-gated) are done with tests. Remaining in Phase 1: History (message drafts land with Message Studio).
- **Phases 2–10:** pending (subscription, credential provider, Gemini Nano, AiRouter, Message Studio, delivery, reliability, QA, release verification).

## Feature states

| Feature | Status | Notes |
|---|---|---|
| Flutter baseline (create + deps) | done | Flutter 3.47.1, Dart 3.13.1 |
| Domain error system | done | `core/errors/app_failure.dart` + tests |
| Sanitized logging | done | `core/logging/app_logger.dart` + redaction tests |
| Drift database (Persons, sync envelope) | done | in-memory integration tests pass on host |
| Secure storage abstraction | done (layer) | impl exercised in Phase 3 on device |
| Material 3 theme + shared widgets | done | light/dark from seed, 48dp targets |
| Navigation shell | done | 5 destinations + widget tests |
| Person model + enums | done | `features/people/domain` |
| Birthday engine | done | timezone-aware recurrence, leap-day resolution, tz selection; 16 tests |
| Person validation + repository + service | done | `PersonInputValidator`, `PeopleStore`/`DriftPeopleStore`, `PersonService`, providers |
| Person create/edit/list UI | done | form + list + FAB + routes |
| Person delete/undo UI | done | soft delete via tile menu + confirm dialog + undo snackbar |
| Calendar year view | done | month grid, leap-day resolution, day sheet, injectable clock; 4 widget tests |
| Reminders | done | scheduler (leads 7/2/1/0, quiet hours, tz, dedupe) + service + settings UI; device gateway interface-only; 17 tests incl. settings |
| Home dashboard | done | Today/Upcoming/Action needed/Quick actions over engine + live people; 9 tests |
| Auth boundary (Google sign-in) | done | `GoogleAuthGateway` (abstract) + truthful unavailable adapter + `AuthController` + Settings Account wiring; real Google/Firebase gateway device-gated; 6 tests |
| History | pending | Phase 1; message drafts land with Message Studio |
| Subscription + entitlement | pending | Phase 2 |
| Gemini credential + provider | pending | Phase 3 |
| Gemini Nano Kotlin bridge | pending | Phase 4 |
| Unified AiRouter | pending | Phase 5 |
| Message Studio | pending | Phase 6 |
| Delivery (WhatsApp/Copy/Share/SMS) | pending | Phase 7 |
| Reliability / sync / idempotency | pending | Phase 8 |
| QA + release verification | pending | Phase 9/10 |

## Test status

- `flutter analyze`: 0 issues.
- `flutter test`: 97/97 passing (engine 16, people, calendar 4, reminders 13, settings 4+3 auth UI, auth controller 3, dashboard 9, shell + accessibility, core foundations).

## Known limitations

- Firebase, Google Play Billing, Gemini API and Gemini Nano require runtime/device/Firebase environments not present in the current host loop; those features are implemented later and verified on device/emulator.
- Firebase reference/config restored from git history (`firebase/`, `backend/`), but the Android google-services plugin stays disabled until a client matching `com.yashsomani.ai_birthday` is registered in the Firebase console (the historical client is `com.aistudio.relateai.qxtjrk`).
- iOS is intentionally out of scope for this implementation (Android-first product).
- Gemini Nano credibility requires on-device verification with a real supported device path; no fake Nano will be shipped.
- The legacy codebase (React Native) is available in git history only.