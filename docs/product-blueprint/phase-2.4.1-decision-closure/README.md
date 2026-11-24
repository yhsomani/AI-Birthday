# Phase 2.4.1 — Architecture decision closure pack

**Repository:** yhsomani/AI-Birthday  
**Base commit:** 2b1839f685fa14f922eb21417935cb7dae1e16df  
**Scope:** People and Birthday architecture  
**Change boundary:** Documentation only  
**Status:** Recommendations documented; Phase 2.4.2 approval matrix and repository-level inventory completed. Formal owner approval and actual local-database inventory remain pending.

## 1. Purpose

Close the unresolved architecture branches from Phase 2.3/2.4 by choosing a preferred direction, recording alternatives and trade-offs, identifying migration impact, and assigning tests and owners before implementation.

This pack does not convert recommendations into user-approved policy. The four Phase 2.3.1 decisions remain the only newly approved product decisions in this scope:

1. Birthday month/day is required for every newly persisted Person.
2. New autoPrepare defaults to false.
3. Each Feb 29 Person chooses Feb 28 or Mar 1 for non-leap years.
4. Missing recipient timezone falls back to device-local timezone.

## 2. Decision disposition

| Decision | Recommended direction | Approval/evidence still required |
|---|---|---|
| DEC-019 | One SQLite UnitOfWork transaction for Person plus affected per-cycle Birthday records; side effects after commit | Architecture/Data sign-off |
| DEC-020 + DEC-025 | Merge: one PersonRepository port, one Drift adapter, one application write owner | Architecture/Data sign-off and caller cutover plan |
| DEC-022 | Retain prior-cycle drafts and delivery evidence as read-only history linked to that exact cycle | Product/Privacy retention and purge approval |
| DEC-023 | Preserve legacy no-birthday rows as Needs birthday; require repair before successful mutation; do not schedule | Product/Data approval plus local record-count inventory |
| DEC-024 | Explicit one-time Feb 28/Mar 1 choice for each legacy Feb 29 Person; do not infer it | Product approval plus affected-record count |
| DEC-026 | Prefer a durable Birthday occurrence identity per Person per cycle year | Product/Data approval because history and migration semantics change |
| DEC-027 | Soft-delete Person as aggregate visibility boundary; preserve linked rows hidden while Undo is available | Product/Privacy approval of exact scope and permanent-delete policy |
| DEC-028 | Explicit country selection for local numbers; canonical E.164 after validated parsing | Product/UX and dependency/security review |
| DEC-029 | Treat invalid non-empty timezone as invalid data, not as absent; no silent fallback | Product/Android approval; device timezone failure policy |
| DEC-030 | Show invalid import candidates with errors, disabled until corrected | Product/UX approval |
| DEC-031 | Preserve existing booleans; apply false only to new/missing defaults | Product/Data approval |
| DEC-032 | Recommend unique (personId, cycleYear) and FK RESTRICT; add only after clean preflight | Architecture/Data sign-off and inventory |
| DEC-033 | Reconcile on resume/timezone change and next relevant recipient-local midnight | Android/Architecture sign-off and device tests |
| DEC-034 | Birthday occurrence is durable local workflow state, not disposable cache; do not independently sync until DEC-015 is resolved | Data/Sync approval |
| DEC-035 | Keep legacy rows visible/exportable with warnings; require birthday correction before successful mutation/re-import | Product/UX/Data approval |

## 3. Highest-impact recommendation

**Use one durable Birthday occurrence identity per Person per calendar year.**

The current source uses a stable ID pattern based only on Person and updates the same Birthday row's cycleYear during rollover. Message drafts and delivery records reference birthdayId. Reusing one row for a later year can make a prior-cycle draft appear attached to the new cycle.

Recommended model:
- one unique (personId, cycleYear) occurrence;
- stable identity for that year;
- prior cycle date/status remains unchanged;
- new year creates a new occurrence;
- drafts and delivery evidence remain linked to the exact cycle;
- no automatic orphan-draft pruning solely because a cycle is no longer current.

This aligns occurrence identity with the requirement for one occurrence per Person per cycle and makes History links unambiguous. Product/Data approval remains required.

## 4. Technical directions proposed for closure

- **Transaction boundary:** one local SQLite transaction for Person and associated occurrence writes; no reminder/import/UI success before commit; post-commit scheduling must be retryable.
- **Repository ownership:** one PersonRepository port, one Drift implementation and one People application write service; active and include-deleted behavior is explicit.
- **Constraints:** after repair, enforce Birthday uniqueness per Person/year and a restrictive Person foreign key; no database cascade deletion.
- **Timezone refresh:** reconcile on app resume, device timezone changes and the earliest upcoming midnight across active recipient zones. Background reminders remain scheduled from occurrence data, not a generic midnight timer.
- **Occurrence durability:** per-cycle rows hold status and draft linkage, so they are durable local workflow state. Cloud sync remains governed by DEC-015.

## 5. Decisions that cannot be closed by source inspection alone

A source review cannot establish what real users intended or how many rows are affected. These need aggregate-only local database inventory and owner approval:

- null/partial birthday rows;
- Feb 29 rows without an explicit preference;
- invalid timezone strings;
- existing autoPrepare=true values with unknown provenance;
- duplicate/orphan Birthday rows and linked draft/delivery records.

Do not infer these from code defaults, test fixtures or an old computed Birthday date. No live database was queried in Phase 2.4.1.

## 6. Package contents

| Document | Purpose |
|---|---|
| [Decision register and option analysis](decision-register-and-option-analysis.md) | Per-decision recommendation, alternatives, impacts, acceptance tests and status |
| [Legacy data inventory and migration gates](legacy-data-inventory-and-migration-gates.md) | PII-safe inventory, migration ordering, safeguards and go/no-go checks |
| Phase 2.4.2 approval and inventory | [Decision approval and data inventory](../phase-2.4.2-decision-approval-and-data-inventory/README.md) |

## 7. Entry to Phase 2.5

Implementation planning should begin only after:

1. Architecture/Data approves DEC-019, DEC-020/025, DEC-026, DEC-032, DEC-033 and DEC-034 or records justified alternatives.
2. Product/Privacy owners decide DEC-022, DEC-023, DEC-024, DEC-027, DEC-028, DEC-029, DEC-030, DEC-031 and DEC-035.
3. An authorized, non-production database inventory reports aggregate counts and zero-PII diagnostics.
4. The migration is rehearsed against schema-v5 fixtures for every legacy condition.
5. A separate application-code change authorization is recorded.

**Result: PARTIAL.** Recommendations are carried into Phase 2.4.2. Formal product/technical sign-off and actual-record inventory remain outstanding. No application code, schema, data, tests, dependencies or infrastructure changed.
