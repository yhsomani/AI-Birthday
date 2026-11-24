# Phase 2.4.2 — Decision approval and data inventory

**Repository:** yhsomani/AI-Birthday  
**Starting commit:** 60d71021f2ca4ea1e6eb7951428fe8db43862edc  
**Scope:** People and Birthday architecture decisions, repository-level schema evidence and preparation for an authorized local data inventory  
**Change boundary:** Documentation and an aggregate-only SQLite query script; no application or user database mutation  
**Status:** Repository-level inventory complete. Decision recommendations are ready for owner approval. Real local-data inventory is blocked because no authorized database copy is available in this conversation/repository.

## 1. Objective

Move Phase 2.4.1 from recommended design into a controlled approval and evidence state:
- record which owner must approve each decision;
- inspect the repository's real schema and migration fixtures;
- establish what can and cannot be inferred from code and fixtures;
- prepare aggregate-only SQL for an authorized copy of the user's local database;
- define the go/no-go requirements before physical schema design.

## 2. Four previously approved product decisions remain unchanged

| Decision | Approved direction |
|---|---|
| Birthday requiredness | Every newly persisted Person requires valid birthday month/day |
| autoPrepare | New records default false; explicit user choice may enable it |
| Feb 29 | Each Feb 29 Person chooses Feb 28 or Mar 1 for non-leap years |
| Missing recipient timezone | A valid recipient timezone takes precedence; missing timezone uses device-local timezone |

No Phase 2.4.2 command is interpreted as approval of the additional recommendations below.

## 3. Work completed

| Workstream | Result |
|---|---|
| Repository artifact scan | No .db, .sqlite, .sqlite3, .dump or .sql database artifact found in the tracked repository tree |
| Schema review | Current Drift schema is version 5 with six tables; no leap-day preference column exists |
| Migration fixture review | Existing automated migration fixture is synthetic v4→v5 and tests adding Jobs while retaining a Birthday row |
| Real user-data counts | Not available; no authorized local DB copy was supplied or queried |
| Approval handling | Recommended directions and required owner sign-offs recorded; none of the new recommendations is marked approved |
| Inventory preparation | Aggregate-only SQLite query script prepared; it emits counts/schema metadata rather than record values |

## 4. Package contents

| Document | Purpose |
|---|---|
| [Approval matrix](decision-approval-matrix.md) | Explicit approvers, recommendations, status and decisions still required |
| [Repository inventory report](repository-inventory-report.md) | Verified schema/migration facts and limitations of repository evidence |
| [Aggregate inventory SQL](aggregate-inventory.sql) | Read-only aggregate queries for an authorized schema-v5 local DB copy |

## 5. Decision approval state

Recommendations are ready for approval, but this phase does not assume the current user has already approved all product/privacy decisions or that Architecture/Data/Android reviewers have signed off.

- Product-owner approvals are needed for user-facing behavior, retention and legacy remediation.
- Architecture/Data approvals are needed for repository ownership, transactions, cycle identity and constraints.
- Android approval is needed for timezone refresh and platform scheduling.
- Privacy review is needed for deletion/purge, retention and diagnostics.
- A decision remains OPEN where its outcome depends on actual record counts or provenance.

See the [approval matrix](decision-approval-matrix.md).

## 6. Data inventory result

<badge color="warning">Real local-data inventory blocked</badge>

The repository does not contain an actual operational database backup. The tracked app_database_migration_test.dart creates a temporary synthetic database to test v4→v5 migration. It is not a representative user dataset and cannot provide valid counts for legacy People records.

The repository-level report is complete. The actual local database inventory requires an owner-authorized copy of the local ai_birthday SQLite database, with encrypted handling and aggregate-only execution. Until then, counts for missing birthdays, Feb 29 rows, invalid timezone values, duplicate/orphan occurrences, and draft/delivery linkage are unknown.

Do not apply migrations, run repair statements or upload a raw user database to GitHub.

## 7. Go/no-go gates

**GO to physical schema design only when:**
1. Product/Privacy approvals are recorded for all data-retention and legacy UX decisions.
2. Architecture/Data approves one Person persistence boundary, one transaction owner and the per-cycle model.
3. An authorized local DB copy is restore-tested and aggregate inventory completed.
4. Duplicate/orphan/linkage counts are reconciled.
5. The proposed migration is rehearsed against schema-v5 fixtures for every discovered exception class.
6. Rollback and old-app compatibility are demonstrated.

**NO-GO currently:** actual data inventory, owner approvals and migration rehearsal are incomplete.

## 8. Explicit non-claims

This phase does not claim:
- any user database was opened or inspected;
- any affected-record count is known;
- any user preference was inferred;
- any product/privacy/architecture approval was granted;
- any application code, database schema, user data, tests, dependencies or infrastructure was changed;
- any schema migration is ready to run.

**Result: PARTIAL.** Repository/schema evidence is documented and the inventory script is prepared; decision sign-off and actual local-data inventory remain pending.
