# Acceptance criteria and source-to-test traceability

**Specification ID:** TRACE-PPL-BDY-001  
**Source review commit:** 93e803aee3064b046ac9b9093cfd35e4b1a0ae62  
**Status:** Source/test mapping completed for the two scoped domains; critical acceptance cases remain uncovered or only partially exercised.

## 1. Evidence vocabulary

| Label | Meaning |
|---|---|
| SOURCE | Implementation symbol was inspected in the stated source file |
| TEST | A named automated test currently asserts some or all of the behavior |
| PARTIAL | Test covers a related path but not the full acceptance boundary |
| GAP | No adequate test found in the inspected test inventory |
| NOT DEVICE VERIFIED | Requires Android runtime/physical-device evidence |
| PROPOSED TEST | Additional obligation; not present as an existing test |

A previous green CI run on source baseline 644571f68be024423376749fe8e50e1db02ff018 executed 460 Flutter tests. It predates the Phase 2.2 documentation commit. This document records existing test mappings; it does not claim tests were re-run during Phase 2.3 or that the new documentation commit has passed CI.

## 2. People acceptance criteria

| ID | Requirement / rule | Acceptance criterion (Given / When / Then) | Source mapping | Existing test mapping | Assessment |
|---|---|---|---|---|---|
| PPL-AC-001 | FR-001, BR-001 | Given valid name and birthday, when user saves, then one Person is created with stable ID, timestamps and version 1 | PersonFormScreen._save; PersonService.create; PersonDraft.toPerson | person_service_test.dart: “create validates, assigns envelope and persists” | TEST |
| PPL-AC-002 | FR-001, BR-002 | Given missing, partial or impossible birthday data in a birthday-required flow, when validation runs, then save is blocked with a field-specific error and no write | PersonInputValidator.validate; BirthdayEngine.isValidMonthDay | person_input_validator_test.dart: “requires month and day”; “rejects impossible calendar dates via the birthday engine” | TEST |
| PPL-AC-003 | FR-001 | Given empty or overlong name, when user saves, then no Person is written and name error is visible | PersonInputValidator.validate; PersonFormScreen._save | person_input_validator_test.dart: “requires a non-empty name”; “caps name length” | TEST |
| PPL-AC-004 | FR-002, BR-005 | Given a saved Person, when one field is edited, then ID/createdAt are preserved, updatedAt changes, version increments once and unedited preferences remain unchanged | PersonDraft.applyTo; PersonService.update; DriftPeopleStore | person_service_test.dart: “update bumps version and preserves createdAt”; drift_repositories_test.dart: full-field roundtrip assertion | PARTIAL: all form fields not jointly asserted |
| PPL-AC-005 | FR-002 | Given a legacy Person without birthday, when a profile mutation is attempted, then the app must not silently perpetuate an invalid saved record; the approved remediation path is required before a successful update | PersonService.update currently allows no-birthday edits based on existing.hasBirthday | person_service_test.dart: “update allows editing a contact that has no birthday (AUD-010)” | CONFLICT: existing source/test behavior violates the approved target; add a migration-aware edit test |
| PPL-AC-006 | FR-002 | Given Person already has birthday, when month/day is cleared, then app either rejects with explanation or follows approved birthday-removal flow | PersonFormScreen._save; PersonService.update | No test for explicit birthday removal | GAP / DECISION |
| PPL-AC-007 | FR-003, BR-006 | Given Person with Birthday row, when delete is confirmed, then both leave active views as one recoverable aggregate; failure must not leave orphan | PeopleScreen._confirmAndDelete; PersonService.remove; DriftPeopleStore.softDelete; DriftBirthdaysRepository.deleteBirthday | people_screen_delete_test.dart: “fails safely and preserves birthday if personService.remove throws”; “undo restores the hard-deleted birthday row too” | PARTIAL: Birthday-delete failure/atomicity not tested |
| PPL-AC-008 | FR-003 | Given successful delete, when Undo is pressed, then Person and prior Birthday are restored exactly with accurate result | PeopleScreen._confirmAndDelete; PersonService.restore | people_screen_delete_test.dart: “delete shows a snackbar whose Undo restores the contact”; “undo restores the hard-deleted birthday row too” | PARTIAL: restore failure is swallowed and untested |
| PPL-AC-009 | FR-004 | Given CSV with valid and invalid rows, when parsed, then candidates and row-level invalid reasons are separated before writes | ContactCsvService.parseCsvWithResult; PeopleScreen import preview | contact_csv_service_test.dart: parser, invalid date, multiline and formula-guard tests | PARTIAL: parser bypasses full validator; missing birthdays rejected |
| PPL-AC-010 | FR-004 | Given potential duplicates, when preview opens, then reasons appear, duplicates start unselected and second-record creation requires explicit selection | ContactCsvService duplicate detection; PeopleScreen review/import | contact_csv_service_test.dart: “detects duplicate candidates by phone number and name”; “detects intra-batch duplicate candidates within the same CSV” | PARTIAL: focused widget test for manual re-selection missing |
| PPL-AC-011 | FR-004 | Given Android contacts permission is denied, when import is attempted, then no contacts are read/written and manual/CSV alternatives remain | DeviceContactsService permission methods; PeopleScreen._syncDeviceContacts | device_contacts_service_test.dart covers date parsing only | GAP / NOT DEVICE VERIFIED |
| PPL-AC-012 | FR-005 | Given People are exported, when CSV generated, then declared fields are escaped/formula-guarded and portable | ContactCsvService.exportToCsv | contact_csv_service_test.dart: escaped export, formula guard and round-trip tests | PARTIAL: export excludes identity/version/tombstone/autoSendPolicy |
| PPL-AC-013 | FR-001–005 | Given any intake source, when a Person persists, then the same required-birthday and field-validation rules apply before writes | PersonInputValidator; CSV and device candidate paths | Validator/CSV/device tests are separate | GAP: imports bypass shared validator; missing birthdays must never persist |
| PPL-AC-014 | FR-002 | Given same Person, when form edit and import/list paths read/write it, then mappings, tombstones and defaults are consistent | DriftPeopleStore; DriftPeopleRepository; PersonService; global providers | person_repository_test.dart; drift_repositories_test.dart | PARTIAL: two adapters need cross-adapter parity tests |
| PPL-AC-015 | FR-004, FR-005 | Given a legacy no-birthday Person is included in a profile export, when that row is imported, then no new Person is created without a valid birthday and the row is reported as needing correction | ContactCsvService.exportToCsv/parseCsvWithResult | contact_csv_service_test.dart: no-birthday export and candidate tests | GAP: current parser skips missing birthday rows; legacy export/remediation UX is open |

## 3. Birthday acceptance criteria

| ID | Requirement / rule | Acceptance criterion | Source mapping | Existing test mapping | Assessment |
|---|---|---|---|---|---|
| BDY-AC-001 | FR-006, BR-002 | Given month/day, when validated, then impossible Gregorian dates are rejected and valid dates accepted | BirthdayEngine.isValidMonthDay; PersonInputValidator | birthday_engine_test.dart: “isValidMonthDay accepts legal and rejects illegal dates”; validator tests | TEST |
| BDY-AC-002 | FR-006, BR-003 | Given a Feb 29 Person with a saved selection, when occurrence is calculated, then leap years use Feb 29 and non-leap years use that Person's Feb 28 or Mar 1 choice | BirthdayEngine.resolveFeb29/computeNext; target preference field not yet implemented | birthday_engine_test.dart: “resolveFeb29 picks Feb 28 or Mar 1”; lifecycle Feb 29 test | GAP: per-Person persistence, validation and selection flow do not exist yet |
| BDY-AC-003 | FR-006 | Given birthday later this year, when next occurrence computed, then current year/date and whole-day countdown are returned | BirthdayEngine.computeNext | birthday_engine_test.dart: “next occurrence inside the same year” | TEST |
| BDY-AC-004 | FR-006 | Given birthday already passed, when next occurrence computed, then next year and correct day count are returned | BirthdayEngine.computeNext | birthday_engine_test.dart: “rolls into the next year after the date passes” | TEST |
| BDY-AC-005 | FR-006 | Given birthday is reference calendar day, when computed, then isToday is true and daysUntil zero | BirthdayEngine.computeNext | birthday_engine_test.dart: “birthday on the reference date is today (daysUntil 0)” | TEST |
| BDY-AC-006 | FR-006, BR-004 | Given recipient timezone, when same UTC instant is converted, then recipient calendar controls occurrence | BirthdayEngine.computeNext named-timezone path | birthday_engine_test.dart: conversion, recipient-today and UTC-different-day tests | TEST |
| BDY-AC-007 | FR-006 | Given a valid recipient timezone, it is used; when absent, device-local timezone is used consistently by every consumer | BirthdayEngine.computeNext; effectiveTimezone; lifecycle; People screen | Local fallback tests exist; cross-consumer consistency test missing | PARTIAL: approved precedence is not yet enforced across all callers |
| BDY-AC-008 | FR-007 | Given Person with birthday and no row, when refresh runs, then one current Birthday row is created | BirthdayLifecycleService.refresh | birthday_lifecycle_service_test.dart: “creates the current birthday cycle when missing” | TEST |
| BDY-AC-009 | FR-007 | Given unchanged Person/cycle, when refresh repeats, then no duplicate or unintended status reset occurs | BirthdayLifecycleService.refresh; DriftBirthdaysRepository.saveBirthday | lifecycle test: “preserves completed state for the same active cycle” | PARTIAL: repeated all-status idempotency not asserted |
| BDY-AC-010 | FR-007 | Given prior cycle is stale, when refresh runs, then cycle/date advance and active draft link clears | BirthdayLifecycleService.refresh | lifecycle test: “rolls a stale cycle into the next year and clears its draft” | TEST |
| BDY-AC-011 | FR-007 | Given multiple Birthday rows for a Person, when refresh runs, then integrity error occurs; no silent selection | BirthdayLifecycleService.refresh; Birthdays schema | lifecycle tests for duplicate personId | TEST |
| BDY-AC-012 | FR-008 | Given upcoming/created occurrence is today, when refreshed, then it becomes reminderDue; other same-cycle workflow states remain unchanged | BirthdayLifecycleService.refresh | lifecycle tests for promotion and preserving completed | PARTIAL: full transition matrix absent |
| BDY-AC-013 | FR-010 | Given form/import writes a birthday, when dashboard/reminders read it, then date/cycle match canonical BirthdayEngine | PersonFormScreen._save; PeopleScreen import; BirthdayLifecycleService | Existing engine/lifecycle tests do not cover intake write paths together | GAP: form/import use timezone-free helper |
| BDY-AC-014 | FR-006, FR-010 | Given device/recipient midnight or timezone change, when consumers refresh, then date labels and reminders agree | birthdaysStreamProvider and local-midnight timer | No physical-device or multi-timezone boundary E2E test identified | GAP / NOT DEVICE VERIFIED |
| BDY-AC-015 | FR-007, FR-008 | Given Birthday or draft write fails during Person create/edit/delete, when operation returns, then aggregate is committed or rolled back/recoverable | Person form orchestration; People delete; repositories | No aggregate fault-injection/transaction test | GAP |
| BDY-AC-016 | FR-007 | Given Person has no valid birthday, when refresh runs, then no Birthday row/reminder is created | BirthdayLifecycleService.refresh skip path | No focused lifecycle test identified | GAP |
| BDY-AC-017 | FR-008 | Given current cycle has draft and new cycle starts, when rollover occurs, then prior draft is archived/retained by an explicit rule and active link clears | BirthdayLifecycleService.refresh; DriftDraftsRepository.pruneOrphanedDrafts | Rollover test checks draftId clearing only | GAP |
| BDY-AC-018 | FR-006 | Given invalid stored timezone from imported/legacy data, when occurrence is computed, then Birthday stream recovers without crash | BirthdayEngine.computeNext; intake paths | No import-to-refresh invalid-timezone test | GAP |

## 4. Existing test inventory

| Test file | Current coverage |
|---|---|
| test/features/people/application/person_service_test.dart | create/update validation, version, no-birthday edit, remove/restore |
| test/features/people/domain/person_input_validator_test.dart | name/date/year/contact/facts/notes/timezone validation |
| test/features/people/data/person_repository_test.dart | Person persistence mapping, query behavior and tombstones |
| test/features/people/presentation/people_screen_delete_test.dart | delete confirmation, Undo and Person-delete failure |
| test/features/people/presentation/person_form_discard_test.dart | dirty discard, keep editing and pristine cancel |
| test/features/people/presentation/person_form_saving_indicator_test.dart | saving progress while form write is pending |
| test/features/people/data/contact_csv_service_test.dart | CSV parse/export/escaping/formula safety/duplicate detection |
| test/features/people/data/device_contacts_service_test.dart | Android contact birthday string parsing |
| test/features/birthdays/domain/birthday_engine_test.dart | Gregorian recurrence, Feb 29, local and named timezone calculations |
| test/features/birthdays/application/birthday_lifecycle_service_test.dart | create/rollover/today status/duplicate detection/batched read |
| test/core/database/drift_repositories_test.dart | Person/Birthday/Draft/Delivery persistence and selected transactions |
| test/core/database/app_database_migration_test.dart | v4→v5 jobs table migration while preserving a Birthday row |

## 5. Priority test gaps before implementation-ready status

**P0 — Data correctness**
- Fault-injection tests around Person + Birthday create/edit/delete/Undo; assert rollback or recoverable aggregate state.
- Shared validation tests proving CSV and device intake reject invalid birth year, timezone, email/phone and preference data.
- Person-without-birthday lifecycle test, including no reminder creation.
- One-current-Birthday-per-Person integrity test at database level, not only in-memory duplicate detection.
- Cross-adapter parity test proving DriftPeopleStore and DriftPeopleRepository round-trip every field and apply identical tombstone semantics.

**P1 — Calendar correctness**
- Table-driven timezone tests for recipient midnight vs device midnight and timezone changes while app is backgrounded.
- Test that form/import writes and lifecycle refresh use the same occurrence calculator.
- Full transition matrix for every BirthdayStatus.
- Prior-cycle draft retention and History behavior tests.

**P1 — Import UX and recovery**
- Widget test for permission denial, cancellation, duplicate deselection/reselection, partial import and import write failure.
- CSV no-birthday export → import contract test after product decision.
- Repeated import idempotency and explicit duplicate create/merge test.

**P2 — Accessibility and performance**
- Search metacharacter, large-font overflow and TalkBack tests across People form/list.
- Large-dataset query-budget test for People streams and Birthday refresh; preserve existing batch-read assertion.

## 6. Traceability status

| Requirement | Domain spec | Business rules | Source | Acceptance mapping |
|---|---|---|---|---|
| FR-001–005 | [People contract](people-lifecycle-contract.md) | BR-001, BR-005, BR-006 | People model/service/form/import/repositories | PPL-AC-001–015 |
| FR-006–008 | [Birthday contract](birthday-lifecycle-contract.md) | BR-002–004 | Birthday engine/model/lifecycle | BDY-AC-001–012, 015–018 |
| FR-009 | Existing reminders domain docs and Phase 2.1 audit | BR-007, BR-008, BR-034 | Reminder scheduler/providers | Out of scope for Phase 2.3 |
| FR-010 | [Birthday contract](birthday-lifecycle-contract.md) | BR-004, BR-007 | BirthdayEngine and stream consumers | BDY-AC-006–014 |

Traceability is complete for the scoped domain map, not for every requirement or atomic feature in the product.

## 7. Approval and implementation gate

The following four product decisions are approved: birthday month/day is required for newly persisted Persons; autoPrepare defaults false; each Feb 29 Person has a user-selected Feb 28/Mar 1 policy for non-leap years; missing recipient timezone falls back to device-local timezone.

Do not begin People/Birthday application changes until:
1. Product/Data resolves legacy no-birthday records, existing Feb 29 preference backfill and existing autoPrepare values.
2. Product/Data resolves phone normalization, delete/Undo aggregate scope and prior-cycle draft/history retention.
3. Architecture approves the canonical Person repository, physical occurrence model, database constraints and atomic transaction plan.
4. Birthday/Android resolves invalid timezone recovery and refresh triggers across multiple recipient timezones.
5. Every P0 gap above has an implementation and test owner.
6. The resulting code change is separately authorized; Phase 2.3/2.4 are documentation-only.
