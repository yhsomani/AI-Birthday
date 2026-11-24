# Phase 2.4.2 — Repository and schema inventory report

**Repository:** yhsomani/AI-Birthday  
**Branch:** main  
**Source baseline:** 93e803aee3064b046ac9b9093cfd35e4b1a0ae62 (application source reviewed in Phase 2.3; later commits in this scope are documentation-only)  
**Report date:** 2026-11-24  
**Mode:** Read-only source/tree inspection. No operational database was opened.

## 1. Repository artifact search

The recursive repository tree at the start of Phase 2.4.2 contained 458 blob entries. The search for database-oriented extensions and migration/fixture names found:
- no tracked .db, .sqlite, .sqlite3, .dump or .sql database file;
- one Dart migration test: test/core/database/app_database_migration_test.dart;
- an unrelated backend TypeScript fixture: backend/functions/test/fixtures.ts;
- documentation and audit references, not live database content.

**Conclusion:** There is no repository-resident local user database from which valid legacy-data counts can be calculated.

## 2. Current schema facts

Verified from lib/core/database/app_database.dart:

| Item | Source-backed result |
|---|---|
| Schema version | 5 |
| Drift tables | Persons, Birthdays, MessageDrafts, ReminderSettingsEntries, DeliveryEvents, Jobs (6 total) |
| Person birthday fields | birthdayMonth and birthdayDay are nullable |
| Birth-year field | Nullable |
| Leap-day policy | No persisted per-Person preference column exists |
| autoPrepare | Non-null bool with SQL default false |
| Person identity | Persons primary key is id |
| Person tombstones | Persons has version and deletedAt |
| Birthday identity | Birthdays primary key is id; personId and cycleYear are ordinary columns |
| Birthday uniqueness | No declared unique constraint for (personId, cycleYear) or personId |
| Birthday FK | No declared foreign key from Birthdays.personId to Persons.id |
| Birthday index | beforeOpen creates idx_birthdays_person_id |
| Draft linkage | MessageDrafts stores birthdayId and personId |
| Delivery linkage | DeliveryEvents stores birthdayId |
| Reminder persistence | SQLite stores ReminderSettingsEntries/preferences, not a materialized row per scheduled reminder |
| Jobs | Jobs stores type/status/subjectId and retry metadata; subjectId meaning depends on job type |

autoPrepare=false is the database default, but source constructors/import paths have inconsistent defaults. The database default does not prove an existing true value was explicitly chosen by the user.

## 3. Current cycle/history source facts

- BirthdayLifecycleService.refresh creates IDs using the current birthday-{person.id} pattern and updates the same row when cycleYear/date changes.
- The Birthday row has a draftId; MessageDrafts and DeliveryEvents refer to birthdayId.
- On rollover, the lifecycle clears the active draft link on the Birthday record, but the old draft record is retained by that service.
- Reusing one Birthday identity across years means a historical draft/delivery reference does not independently identify a distinct yearly occurrence. The proposed per-year identity in Phase 2.4.1 is therefore a substantive data-model change, not a cosmetic ID change.
- No data rows were queried. The number of records currently affected is unknown.

## 4. Migration fixture coverage

test/core/database/app_database_migration_test.dart:
- creates a temporary synthetic database file;
- reproduces a v4 schema by dropping Jobs and setting PRAGMA user_version = 4;
- inserts one synthetic Birthday row (personId is a fixture identifier);
- reopens with schema v5;
- verifies Jobs table creation and retention of the Birthday row.

The fixture does not:
- represent a production backup or a sample of user records;
- insert representative Person rows for missing-birthday analysis;
- cover v5-to-future-schema changes;
- exercise a per-year Birthday re-key;
- migrate MessageDrafts/DeliveryEvents foreign identifiers;
- count null birthday, Feb 29, timezone, autoPrepare, duplicate or orphan cases.

Therefore its successful result is evidence only for the v4→v5 Jobs migration scenario, not a legacy-data inventory.

## 5. Inventory conclusion

<badge color="warning">Actual local-data inventory blocked</badge>

| Requested measure | Repository evidence | Actual user-data result |
|---|---|---|
| Persons missing birthday | Schema allows nulls | Unknown |
| Partial/invalid birthday pairs | No constraints enforce pairs | Unknown |
| Feb 29 Persons | No preference column | Unknown |
| Existing autoPrepare true/false | Schema stores bool; provenance not stored | Unknown counts and provenance |
| Invalid recipient timezones | String column nullable; no DB IANA constraint | Unknown |
| Ambiguous phone numbers | Raw optional text field | Unknown |
| Duplicate Birthday rows | No unique constraint in schema | Unknown |
| Orphan Birthday rows | No FK | Unknown |
| Draft/delivery link consistency | Foreign IDs are text and not constrained by schema | Unknown |
| Deleted Persons with related rows | Person tombstone exists; child tables do not share it | Unknown |
| Reminder/job orphan state | Reminder schedules are not persisted as a row per reminder; job subject depends on type | Unknown |

## 6. Next required evidence

An authorized, restore-tested copy of the app's local SQLite database is required. Run the aggregate-only inventory query script against that copy. Do not upload raw DB contents or query output containing row identifiers or personal fields.

**Result: PARTIAL.** Repository/schema evidence is complete for the inspected source. Real data counts cannot be confirmed without the local database.
