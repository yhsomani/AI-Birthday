# Phase 2.4.1 — Legacy data inventory and migration gates

**Status:** Inventory plan only. No local user database was queried and no user records were read, changed, exported or uploaded.

## 1. Purpose and safety boundary

Before adding non-null birthday fields, leap-day preferences, per-cycle occurrence keys or foreign keys, inspect existing local data in a controlled environment.

The inventory must return aggregate counts only. Do not write names, birthday values, phone numbers, emails, notes, facts, draft bodies, IDs or row dumps to CI logs, analytics, tickets or this repository.

Use a restorable copy of an authorized local database. Never run destructive repair against the only copy.

## 2. Required aggregate inventory

| Inventory | Aggregate counts required | Why |
|---|---|---|
| Person birthday data | null month, null day, partial pair, impossible Gregorian pair, valid pair | DEC-023 and NOT NULL readiness |
| Feb 29 preference | Feb 29 Person total; rows with/without preference once field exists | DEC-024 |
| autoPrepare | true, false, missing/default provenance where detectable | DEC-031 |
| Recipient timezone | absent, valid IANA, invalid/non-IANA, empty string | DEC-021/029 |
| Phone | explicit international prefix, local format, invalid, ambiguous country | DEC-028 |
| Birthday rows | total, duplicate (personId, cycleYear), duplicate personId, orphan Person reference | DEC-026/032 |
| Cycle consistency | Person birthday versus stored date/cycle under approved policy | DEC-026/033 |
| Draft linkage | existing/missing Birthday IDs, Person/year mismatch, multiple drafts per occurrence | DEC-022/026/027 |
| Delivery linkage | valid/invalid Birthday and Person references | DEC-022/027 |
| Tombstones | deleted Persons with linked occurrence/draft/delivery/reminder/job rows | DEC-027 |
| Reminder/job state | active, expired, cancelled and orphan schedule/job counts by category | DEC-027/033 |

Report only counts and consistency rates. If a condition cannot be inferred from the stored schema, report unknown rather than guessing.

## 3. Inventory report requirements

The report must include:
- database schema version and app build/commit;
- whether the source DB was copied and restore-tested;
- inventory timestamp and timezone;
- total row counts by table;
- aggregate exception counts from Section 2;
- query/tool version and reviewer;
- no record identifiers or field values.

Reject any report containing PII and remove it from normal engineering artifact paths.

## 4. Migration sequence

### Gate 0 — Approval and backup

Require authorized access to the source database, classification of the environment, encrypted storage, access-limited copy, verified restore test, and named Data/Privacy reviewers.

### Gate 1 — Inventory, no mutations

Run aggregate-only checks for every category in Section 2. Produce exception counts. Do not write data.

**Go:** complete, privacy-reviewed report.  
**No-go:** missing categories, PII leakage or inability to restore the copied source.

### Gate 2 — Decide remediation per exception class

- Missing/invalid birthday: user correction or approved visible exception; no placeholder.
- Missing Feb 29 preference: user chooses; never infer from old default.
- Invalid timezone: follow approved DEC-029 behavior; preserve original value.
- autoPrepare provenance: preserve old booleans unless reliable provenance supports targeted repair.
- Duplicate/orphan occurrences: reconcile authoritative cycle and all dependent links before repair.
- Phone: request country context where ambiguous; never infer.

### Gate 3 — Rehearsed additive migration

Subject to approval:
1. Add nullable per-Person leap-day policy representation.
2. Introduce migration state for unresolved legacy rows.
3. Re-key existing Birthday rows to their cycle-specific identity, if DEC-026 is approved.
4. Update MessageDrafts and DeliveryEvents birthdayId references in the same transaction.
5. Add supporting indexes for (personId, cycleYear).
6. Preserve unrelated profile, draft, delivery and tombstone fields.
7. Compare aggregate counts and links before/after.

Do not assign the final schema version or SQL until physical design is approved.

### Gate 4 — Repair and constraint preflight

Before constraints:
- zero invalid birthday pairs among records required to satisfy NOT NULL, or an approved compatibility model for remaining exceptions;
- zero unresolved duplicate (personId, cycleYear) rows;
- zero orphan references;
- zero missing draft/delivery references caused by re-keying;
- every active Feb 29 Person has a selected policy or is explicitly excluded from occurrence scheduling under approved behavior;
- migration counts reconcile.

### Gate 5 — Constraints and rollout

Only after Gate 4:
- add Person birthday NOT NULL, if all rows are repaired or a staged compatibility model is approved;
- add unique (personId, cycleYear) on Birthday;
- add FK Birthday.personId → Persons.id ON DELETE RESTRICT;
- test upgrade, cold launch, resume, Undo and rollback;
- release only with schema downgrade/forward-compatibility strategy.

## 5. Rollback requirements

- Keep a verified pre-migration snapshot for rehearsal and release.
- Migration must be transactional where supported; non-transactional rebuilds need a proven restore script.
- Abort if foreign IDs cannot be reconciled.
- Preserve old-app compatibility or explicitly block incompatible downgrade.
- Verify data integrity and user-visible behavior after restore.
- Migration success requires aggregate before/after reconciliation and link integrity, not exit code alone.

## 6. Explicit non-claims

- No database inventory was executed in Phase 2.4.1.
- No number of affected records is known.
- No birthday/timezone values were inferred.
- No migration or schema constraint has been applied.
- This plan is not permission to retrieve or publish user personal data.
