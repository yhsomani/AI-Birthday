# People and Birthday data-integrity and migration design

**Design ID:** DATA-PPL-BDY-001  
**Parent phase:** 2.4  
**Status:** Target integrity contract; exact physical constraints and legacy backfill remain open.

## 1. Current source baseline

Phase 2.3 inspected source at commit 93e803aee3064b046ac9b9093cfd35e4b1a0ae62. At that baseline:

- Local Drift schema version is 5.
- Persons has a primary key on id plus version and deletedAt fields.
- Birthday has a primary key on id and an index on personId, but no declared foreign key to Persons and no unique personId constraint.
- Two adapters write Persons: DriftPeopleStore for PersonService and DriftPeopleRepository for streams/import paths.
- Person and Birthday writes are split across several create/edit/delete/undo flows.
- Existing rows may have null birthday month/day; no database inventory was executed in Phase 2.4.
- The Birthday table does not have the same version/tombstone envelope as Persons.
- No physical database migration, row repair or constraint change has been performed in this phase.

These are source-level facts at the stated baseline, not counts of actual affected user records.

## 2. Integrity invariants

The target design requires:

1. Every newly persisted Person has a non-null, valid birthday month/day pair.
2. Month/day values form a valid Gregorian date; birth year remains optional.
3. Feb 29 Persons have an explicit per-Person Feb 28/Mar 1 preference before a definitive non-leap-year occurrence is calculated.
4. New Person autoPrepare defaults to false unless explicitly enabled.
5. Recipient IANA timezone, when valid, takes precedence; missing recipient timezone uses device-local context.
6. No active current occurrence may refer to a missing or deleted Person.
7. A Person create/edit/delete/undo operation that changes its occurrence has one atomic local commit or a durable, detectable recovery state.
8. A retry of the same operation must not create duplicate Person or occurrence records.
9. Failed writes must not increment Person version or produce reminder side effects.
10. Existing data is preserved until a migration policy explicitly authorizes remediation.

## 3. Logical aggregate and transaction boundary

Person is the aggregate root for profile identity and birthday facts. The current Birthday occurrence is associated state. The exact physical model for current vs historical occurrences remains open under DEC-026 and DEC-034.

The application layer owns a UnitOfWork/transaction boundary across Person and Birthday repositories. Repositories must share the same SQLite/Drift transaction executor for an aggregate write; they must not independently start or commit nested transactions.

~~~mermaid
sequenceDiagram
  participant UI as Flutter UI
  participant APP as Person lifecycle use case
  participant VAL as Canonical validator
  participant OCC as Birthday occurrence service
  participant TX as UnitOfWork
  participant P as Person repository
  participant B as Birthday repository
  participant R as Reminder reconciler
  UI->>APP: submit Person + operationId
  APP->>VAL: normalize and validate
  VAL-->>APP: valid profile
  APP->>OCC: compute occurrence
  OCC-->>APP: resolved cycle/date
  APP->>TX: begin transaction
  TX->>P: insert/update Person
  TX->>B: upsert approved occurrence state
  TX-->>APP: commit
  APP->>R: post-commit reconcile
  R-->>APP: success or retryable diagnostic
  APP-->>UI: committed result
~~~

A transaction rollback must leave the pre-operation aggregate unchanged. No reminder, alarm, notification, import-success counter or UI success result is emitted before commit.

## 4. Person and Birthday persistence contract

### 4.1 Person

Target logical constraints:

| Field | Target constraint | Notes |
|---|---|---|
| id | Primary key, non-empty, stable | Generated once per create operation |
| name | NOT NULL, trimmed, bounded | Reuse canonical validator |
| birthdayMonth | NOT NULL for new/fully migrated records | 1–12 |
| birthdayDay | NOT NULL for new/fully migrated records | Validate against month and Gregorian rules |
| birthYear | Nullable | Existing approved range remains |
| leapDayPolicy | Nullable except required for Feb 29 | Allowed values Feb28 or Mar1 |
| autoPrepare | NOT NULL, default false | Explicit true is a user preference; legacy interpretation open |
| timezone | Nullable | Must be valid IANA when supplied for new writes |
| version | Positive integer | Exactly one increment per accepted mutation |
| deletedAt | Nullable | Tombstone state |

A normal SQL NOT NULL constraint cannot safely be introduced until legacy null-birthday records are inventoried and DEC-023 has an approved resolution. A staged constraint may be necessary: application-level enforcement first, legacy repair/read compatibility second, physical constraint only after a clean-data assertion.

### 4.2 Birthday occurrence

The occurrence repository must guarantee:
- an occurrence belongs to an existing Person;
- no occurrence is active for a tombstoned/deleted Person;
- occurrence date/cycle is derived through the canonical resolver;
- repeated reconciliation is idempotent;
- duplicates/orphans produce repair diagnostics, not arbitrary selection.

Exact uniqueness key and FK action are intentionally not fixed because DEC-026/027/034 are open. If a mutable current row is selected, a unique Person association may be appropriate. If immutable per-cycle records are selected, a unique (Person, cycleYear) key may be appropriate. Do not apply both interpretations simultaneously.

## 5. Aggregate operation semantics

### 5.1 Create

- Validate Person and leap-day preference before transaction.
- Compute occurrence using one injected clock/timezone context.
- Insert Person and occurrence inside one transaction.
- Reuse operation ID on retry; enforce idempotency at application/repository boundary.
- Roll back all writes if any insert fails.
- Reconcile reminders only after commit.

### 5.2 Edit

- Read active Person and version.
- Validate full candidate, preserving fields not changed.
- Recompute occurrence only for birthday facts, leap-day policy or recipient timezone changes, and whenever a date-boundary refresh requires it.
- Update Person and occurrence atomically.
- Increment Person version exactly once after a successful mutation.
- Do not change version or occurrence if validation/transaction fails.
- Exact optimistic concurrency policy remains open.

### 5.3 Delete and Undo

The target invariant is one transaction for all database state selected by the approved deletion plan. Transaction participants must be known before implementation. The plan must define:

- Person tombstone/visibility;
- current occurrence hide/delete/archive behavior;
- prior occurrence history;
- current and historical drafts;
- delivery events and user-confirmed evidence;
- scheduled reminders/OS alarms;
- queued jobs and retries;
- backup/tombstone propagation;
- Undo eligibility, expiry and completeness.

No irreversible deletion of drafts or delivery evidence is approved by this design. No retention interval is invented. Until DEC-027 is approved, a developer must not infer cascade behavior from the current code.

Post-commit platform work (cancel/rebuild alarms and jobs) cannot be part of the SQLite transaction. It must be represented as a durable reconciliation request/outbox or a startup/app-resume repair scan so process death after commit does not leave an undetectable mismatch.

## 6. Import transaction and batch semantics

- Parse external data outside the database transaction.
- Apply canonical mapping, normalization and validation before preview.
- Persist only valid and user-selected rows.
- Each candidate has a stable source/batch identity and per-row operation ID.
- A Person create plus occurrence is atomic per candidate.
- Batch atomicity remains open: all-or-nothing batch versus per-row commits. The target design should expose per-row outcomes either way.
- Duplicate review is advisory and cannot infer identity from phone/name similarity alone.
- Invalid phone/timezone/birthday values must not bypass the canonical validator.
- CSV export is a profile export, not a full backup. Legacy no-birthday record export/round-trip behavior remains open under DEC-023/035.

## 7. Migration strategy

Migration work is a separate authorized implementation task. The following order is a plan, not an executed migration.

### Stage 0 — Inventory and rehearsal

1. Take a restorable database snapshot in a controlled test environment.
2. Count Persons with null/partial/invalid month/day.
3. Count Feb 29 Persons and identify missing leap-day policy.
4. Count invalid/non-IANA recipient timezones.
5. Count duplicate and orphan Birthday rows.
6. Identify Person/Birthday rows whose date/cycle disagree with the canonical resolver.
7. Inventory autoPrepare values and determine which values are defaults versus explicit user choices where evidence exists.
8. Inventory drafts, reminder jobs and delivery events linked to each affected Person/Birthday.

No user-level data values are to be copied into diagnostic logs.

### Stage 1 — Additive compatibility schema

Subject to approved physical design:
- Add nullable leapDayPolicy field or equivalent versioned preference representation.
- Add migration metadata if needed to distinguish unreviewed legacy rows.
- Keep existing columns and records readable.
- Do not make birthdayMonth/day NOT NULL while unresolved legacy null rows remain.
- Avoid destructive table rebuilds until fixture-based upgrade and rollback tests pass.

The target schema version is not selected in Phase 2.4.

### Stage 2 — Application-level invariant and compatibility layer

After separate code authorization:
- Enforce required birthday for all new writes and imports.
- Enforce Feb 29 choice for newly created/edited Feb 29 Persons.
- Set all new defaults for autoPrepare to false.
- Use one timezone resolver and occurrence service.
- Route all writes through the canonical repository/application boundary.
- Make the migration state of legacy rows explicit in read and edit flows.

### Stage 3 — Data repair and user remediation

DEC-023/024/029/031 govern how each legacy class is repaired:
- Missing birthday: ask user, keep read-only/needs-attention, or another approved route; never fabricate or silently delete.
- Missing leap-day preference: prompt the owner to choose; no implicit preference.
- Invalid timezone: user correction or approved fallback with a visible diagnostic.
- autoPrepare: preserve or reconcile historical value only under the approved policy.
- Duplicate/orphan occurrences: deterministic repair only after record-level conflict rules are approved.

### Stage 4 — Enforce physical constraints

Only after a clean-data report proves readiness:
- Add NOT NULL constraints for birthday month/day.
- Add an appropriate leap-day policy constraint.
- Add the selected unique occurrence key and referential action.
- Add supporting indexes based on measured query plans.
- Verify every upgraded database with integrity checks.

Do not guess the exact constraints until the occurrence persistence and deletion policies are approved.

### Stage 5 — Cleanup and compatibility retirement

- Remove duplicate repository adapters only after all callers are migrated.
- Remove timezone-free date helpers only after call-site search proves there are no active consumers.
- Remove old constructor defaults and parser-specific bypasses after parity tests.
- Retain migration tests and old-schema fixtures permanently.

## 8. Rollback and recovery

- Migration must run in a transaction where SQLite/Drift supports the operation; table rebuilds need explicit backup/restore rehearsal.
- Preserve a pre-migration database copy in test/release rehearsal.
- A failed migration must leave the previous schema/data usable or restore from a verified snapshot.
- Never mark migration complete before data validation and repository integrity checks pass.
- Provide an operation-level recovery path for interrupted aggregate writes if any external side effect is performed outside SQLite.
- Rollback of an application release must remain compatible with the migrated schema, or the rollout must explicitly block downgrade to an incompatible app version.
- Do not log Person-level PII when reporting migration failures.

## 9. Security and privacy

Birthday, contact and preference fields are private personal data. Inventory counts and integrity diagnostics must be aggregate-only. Never log raw names, birthday values, phone/email, notes, facts, CSV content or draft content. Device Contacts permission only permits user-initiated local import; it is not cloud-upload consent. Cloud backup remains opt-in and governed by its separate contract.

## 10. Data design decisions still open

See DEC-023 through DEC-035 in the [Phase 2.4 index](README.md). In particular, a physical schema cannot be finalized until the legacy no-birthday strategy, leap-day backfill, cycle persistence model, deletion scope and referential integrity rules are approved.
