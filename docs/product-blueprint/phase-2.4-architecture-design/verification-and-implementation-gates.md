# People and Birthday verification and implementation gates

**Design ID:** VERIFY-PPL-BDY-001  
**Parent phase:** 2.4  
**Status:** Planned verification; no tests or device journeys were executed in this documentation phase.

## 1. Verification principles

- A documented target is not source behavior.
- Existing tests are evidence only for assertions they actually execute.
- A passing CI run on the old source baseline does not prove Phase 2.4 implementation.
- Every new invariant must have domain, database, import, widget and applicable device tests.
- Data migration requires old-schema fixtures, populated legacy fixtures, integrity assertions and rollback rehearsal.
- No PII is printed in test logs or diagnostic output.

## 2. Acceptance and test mapping

| Area | Required assertion | Verification |
|---|---|---|
| Required birthday | Manual, CSV and Android intake cannot persist missing/partial/impossible month/day | Validator unit tests; form/import widget tests; repository negative tests |
| Legacy no-birthday data | Existing null rows remain readable until explicit remediation; no invented date or silent deletion | Migration fixtures; list/edit/export compatibility tests; owner-approved remediation journey |
| autoPrepare | Every new Person defaults false; explicit true remains true | Model/draft/form/CSV/device/database parity tests |
| Feb 29 selection | Feb 29 Person requires explicit Feb 28 or Mar 1 selection; leap year remains Feb 29 | Validator, model serialization, migration and table-driven Gregorian tests |
| Missing recipient timezone | Valid recipient zone wins; absent recipient zone uses device-local calendar | Injected device clock/timezone tests across People, Birthday, Dashboard, Calendar and Reminders |
| Invalid timezone | Invalid legacy zone does not crash streams or silently misstate date | Typed-error/fallback tests after DEC-029 approval |
| Canonical occurrence | Every caller obtains the same date/cycle for the same Person and instant | Contract tests against one occurrence service; no duplicate helper call sites |
| Repository ownership | All writes and reads agree on active/deleted semantics and field mapping | Cross-adapter parity tests during migration; one canonical-port tests after cutover |
| Aggregate atomicity | Person and occurrence both commit or neither commits | SQLite integration and fault-injection tests for insert/update/delete/restore |
| Idempotency | Retrying the same create/import/reconcile operation does not duplicate data | Property and repository tests with repeated operation IDs |
| Delete/Undo | The selected aggregate is restored exactly; no swallowed errors | Failure injection at every transaction step and post-commit side effect |
| Import | Invalid candidates never write; duplicates are advisory; partial outcomes are truthful | Parser/mapper/preview tests, retry tests and import batch tests |
| Schema migration | Existing data survives upgrade; per-cycle identities and linked foreign IDs are preserved; constraints are added only after repair | v5-to-target fixtures, draft/delivery link migration, corrupt/partial fixtures, rollback and integrity checks |
| Timezone boundaries | Recipient midnight, device midnight, DST and device timezone changes agree across consumers | Fake-clock tests and physical Android time/timezone-change E2E |
| Reminder reconciliation | Alarm changes follow commit and recover after process death/reboot/permission change | Outbox/reconciliation tests plus physical-device tests |

## 3. P0 test obligations

### P0-A — Data contract

1. New Person with no birthday is rejected from manual entry.
2. CSV row with missing birthday is invalid and cannot be selected for persistence.
3. Android contact candidate with missing/unparseable birthday is not persisted.
4. A partial month/day pair and impossible date are rejected on every path.
5. Feb 29 with no preference is rejected for new writes; each selected policy resolves correctly in leap and non-leap years.
6. autoPrepare is false from every constructor/intake/database default; explicit true survives a normal edit.
7. Valid recipient timezone wins; absent recipient timezone resolves in device-local time.
8. New Person + current occurrence is idempotent and remains one logical aggregate.

### P0-B — Transaction and repositories

1. Inject failure after Person insert and prove the transaction rolls back.
2. Inject failure after occurrence insert and prove the transaction rolls back.
3. Inject update failure on either repository and prove version and both records remain unchanged.
4. Inject delete and restore failure at every step; assert no partial success is reported.
5. Test active versus include-deleted reads consistently.
6. Prove CSV/device imports call the same lifecycle application service.
7. Retrying an operation ID does not create duplicates.
8. Assert reminder side effects are requested only after commit.

### P0-C — Migrations

1. Build fixtures for schema version 5 with normal rows, null birthdays, Feb 29 birthdays, invalid timezone strings, duplicates, orphans, tombstones, linked drafts and delivery events.
2. Upgrade without losing unaffected records or private profile fields.
3. Apply approved per-row remediation choices and validate every resulting Person/occurrence.
4. Enforce Person birthday NOT NULL and Birthday UNIQUE(personId, cycleYear)/FK constraints only after approved legacy remediation and a zero-violation preflight.
5. Simulate migration interruption and restore from backup.
6. Verify release rollback compatibility.

### P0-D — Date/time and downstream consumers

1. Table-drive Feb 28 and Mar 1 policies across leap and non-leap years and year boundaries.
2. Test recipient timezone versus device timezone when their calendar dates differ.
3. Test app background/resume across recipient midnight.
4. Test device timezone change while the app is suspended.
5. Test DST transitions and date-only values around UTC midnight.
6. Verify Dashboard, Calendar and Reminders consume the same occurrence result.

## 4. P1/P2 test obligations
### Phase 2.4.1 proposed per-cycle history tests

- A rollover creates a new Birthday identity and leaves the prior cycle ID/date/status unchanged.
- MessageDrafts and DeliveryEvents continue to reference the exact historical cycle after rollover.
- Retrying rollover creates no duplicate (personId, cycleYear).
- Delete hides associated cycle/draft/history data and Undo restores the same links, once DEC-027 is approved.
- Migration from the current stable birthday-per-Person row updates all dependent birthdayId references atomically.


**P1:** full Birthday status transition matrix; previous-cycle draft/history handling after DEC-027; duplicate/orphan repair; import partial failure; stale-edit conflict; reminder exact-alarm/permission/reboot journeys; accessibility and localization for birthday selection and invalid-candidate review.

**P2:** large dataset query budget; large text and screen-reader review; search literal behavior; backup/restore compatibility once the sync model is approved; operational telemetry redaction.

## 5. Implementation sequence after separate authorization

| Order | Work package | Entry criteria | Exit evidence |
|---|---|---|---|
| 0 | Source and data inventory | DEC-023–035 scoped; code-change authorization | Current call-site map, DB fixture inventory and signed decision log |
| 1 | Canonical validators/defaults | Required birthday, autoPrepare, leap-day UX and timezone recovery specified | Cross-path unit/widget tests |
| 2 | Canonical Person repository + UnitOfWork | DEC-025, DEC-027 and DEC-032 approved | Cross-adapter parity, transaction and fault-injection tests |
| 3 | Birthday occurrence service | DEC-024, DEC-026, DEC-029, DEC-033 and DEC-034 approved | One resolver, migration and recurrence test suite |
| 4 | Intake and downstream consumers | Import behavior, deletion/retention and consumer contracts approved | End-to-end import → list → calendar → reminder test |
| 5 | Legacy migration and constraints | Inventory clean; rollback rehearsed; data remediation UX approved | Migration validation report; schema integrity report |
| 6 | Device and release validation | Supported Android matrix and release policy approved | Physical-device evidence and green CI tied to implementation commit |

Implementation order may be adjusted only when dependency and migration risks are explicitly documented.

## 6. Release gates

Do not describe the feature as complete until:
- every P0 test above is implemented and passing;
- existing migration fixtures prove all supported upgrades;
- no invalid birthday, orphan or duplicate current occurrence remains outside an approved exception;
- People and Birthday use the same canonical resolver and write boundary;
- reminder reconciliation survives process death and app/device timezone changes;
- a real Android device has exercised missing birthday import, Feb 29 preference, timezone fallback, delete/undo and permission denial;
- test and CI evidence is tied to the exact implementation commit;
- privacy/security review confirms no PII leakage in logs/diagnostics.

## 7. Explicit non-claims

This document does not claim:
- application code has been changed;
- database schema has been migrated;
- existing user records have been inspected or repaired;
- tests, CI, emulator tests or physical-device journeys have been run;
- reminder scheduling or timezone behavior is fixed in the current app;
- every open DEC-023–035 item has been approved.

**Result: PARTIAL.** Verification obligations and implementation sequence are specified; they have not been executed.
