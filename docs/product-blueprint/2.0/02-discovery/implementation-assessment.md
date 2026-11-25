# Current Implementation Assessment

## Verified strengths (`IR`/`VF`)

- Local Flutter workspace with Drift/SQLite schema v6, People/Birthday/Draft/Reminder/DeliveryEvent/Job tables (`lib/core/database/app_database.dart`).
- People create/update with required birthday validation, saved per-Person leap-day resolution and atomic Person/Birthday writes through `PersonService`/`DriftPeopleStore` for that path.
- Birthday recurrence, leap-year handling and recipient/device-local calendar calculation; dashboard, calendar and reminders consume birthday streams.
- Manual Studio with durable job-backed AI, autosave, stale result protection, WhatsApp/SMS/share/copy handoff and explicit confirmation.
- AI entitlement-first route; configured BYOK and Gemini Nano are behind `canUseAi`; provider calls are bounded.
- Google sign-in/Firebase exchange, secure credential storage, Play purchase verification path and server-side entitlement writes.
- Owner-scoped Firestore backup/generations and server-only coordination/endpoints; callable App Check is enabled in source.
- Notifications/native contacts/Nano/hand off Android bridges, reminder quiet hours, durable jobs and substantial unit/widget/E2E/emulator sources.

## Current-state gaps and conflicts (`IR`/`CF`)

1. **Schema baseline:** existing blueprint descriptions of schema v5 and absent leap-day column are stale; current schema v6 includes `leap_day_resolution`.
2. **Automation:** `ProductCapabilities.autoPrepare/automaticSms` default false; Kotlin hard-codes `AUTOMATIC_SMS_ENABLED=false`. Automatic SMS is in approved Android product scope but is not currently available; activation remains gated by safety, policy, and device evidence.
3. **Product boundary:** the user approved Birthday Autopilot as in-scope for AI-Birthday. The existing server control plane is not integrated with Flutter/Android or deployed; the identity and release of the public Hosting surface remain unresolved.
4. **Ownership:** `DriftPeopleStore` and `DriftPeopleRepository` create split write authority. Presentation-driven imports can bypass full Person validation.
5. **History:** Birthday uses stable `birthday-{personId}` and rollover overwrites cycle rather than durable per-cycle history; schema lacks FK and unique `(person_id, cycle_year)`.
6. **Backup:** complete generation semantics, retention, integrity hashes, deep restore validation and all-delete reconciliation are incomplete.
7. **Privacy lifecycle:** whole-device export/wipe and complete account-linked deletion are not implemented; person soft-delete is not a general purge.
8. **Operations:** backend, public site and store configuration have source/test assets but no verified deployment evidence in this worktree.
