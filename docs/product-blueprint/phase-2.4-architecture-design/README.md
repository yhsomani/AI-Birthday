# Phase 2.4 — People and Birthday target architecture

**Repository:** yhsomani/AI-Birthday  
**Design baseline:** Phase 2.3 source review at 93e803aee3064b046ac9b9093cfd35e4b1a0ae62; documentation/governance head before this design: 6657322ac1679b6a0954c04340ab27b2d29924d0  
**Review date:** 2026-11-24  
**Change boundary:** documentation only  
**Status:** Target architecture proposed against four approved product decisions. Remaining data, migration and lifecycle questions are explicitly open. Not implementation-ready.

## 1. Objective

Translate the Phase 2.3 source-backed People and Birthday contracts and Phase 2.3.1 product approvals into an implementable target design for domain ownership, application orchestration, data integrity, transactions, migrations and verification.

This phase defines intended boundaries and migration obligations. It does not change Flutter/Dart, Kotlin, Firebase Functions, Hosting, package manifests, dependencies, Drift schema, generated code, tests, secrets, deployment or infrastructure.

## 2. Approved product decisions

These four decisions are treated as approved product intent:

| ID | Area | Approved direction | Immediate architectural consequence |
|---|---|---|---|
| DEC-016 | Birthday optionality | No. Every newly persisted Person must have valid birthday month/day, including manual, CSV and device-contact intake | Validate before persistence; no no-birthday create path |
| DEC-017 | autoPrepare | false by default for every new Person and every intake path; explicit user opt-in may set true | One canonical default across draft, model, form, importer and DB |
| DEC-012 | Feb 29 | Per-Person user choice: Feb 28 or Mar 1 in non-leap years; leap years remain Feb 29 | Persist a two-value policy for Feb 29 Persons; no hidden global default |
| DEC-021 | Missing recipient timezone | Valid recipient IANA timezone first; device-local timezone when absent | One resolver shared by every occurrence consumer; no user-profile timezone fallback |

The source currently disagrees with parts of this target. Approved intent does not mean the code already implements it.

## 3. Design package

| Document | Responsibility |
|---|---|
| [People/Birthday application architecture](people-birthday-application-architecture.md) | Domain ownership, application services, repository boundary, canonical date resolver and workflows |
| [Data integrity and migration design](data-integrity-and-migration-design.md) | Aggregate transaction contract, schema constraints, legacy data, migration sequencing, recovery and rollback |
| [Verification and implementation gates](verification-and-implementation-gates.md) | Acceptance/test matrix, release blockers, phased implementation plan and evidence requirements |
| [Phase 2.4.1 decision closure pack](../phase-2.4.1-decision-closure/README.md) | Recommended resolutions, option analysis, legacy inventory template and approval gates |


## 4. Target architecture at a glance

~~~mermaid
flowchart TB
  UI[Flutter forms, lists, import preview] --> UC[People application use cases]
  UC --> VAL[Canonical Person validator]
  UC --> TX[Local Unit of Work / transaction coordinator]
  TX --> PR[Canonical Person repository]
  TX --> BR[Birthday occurrence repository]
  PR --> DB[(Drift / SQLite)]
  BR --> DB
  UC --> OCC[Birthday occurrence service]
  OCC --> TZ[Timezone resolver]
  OCC --> LEAP[Per-Person leap-day resolver]
  TZ --> DEVICE[Device-local timezone adapter]
  OCC --> BR
  UC --> IMPORT[CSV / Android intake adapters]
  IMPORT --> UC
  TX --> OUTBOX[Post-commit reconciliation request]
  OUTBOX --> REM[Reminder / calendar / dashboard refresh]
~~~

The diagram is logical. It does not prescribe a generated Drift API or exact class names.

## 5. Architecture principles

1. One canonical application write boundary for Person data.
2. Every new persisted Person has a valid birthday month/day.
3. The Person's birthday facts are the input; a Birthday occurrence is a derived/current-cycle representation.
4. Every intake path uses the same normalization and validation policy before writes.
5. Person and current Birthday state commit atomically when one user action promises both.
6. Date calculation has one authoritative resolver; presentation and import code do not calculate dates independently.
7. Side effects such as reminder scheduling run only after a successful local commit and are retryable.
8. Local People/Birthday operations remain useful offline.
9. Existing user data is preserved until a migration/remediation policy is approved.
10. No migration may fabricate a birthday, a leap-day preference or a contact country code.

## 6. Scope boundaries

**Included:** Person create/edit/delete/undo, CSV and Android contact intake, Person validation/defaults, birthday recurrence, current occurrence reconciliation, read models consumed by dashboard/calendar/reminders, local database transaction and migration design.

**Not included:** Message Studio draft content semantics, delivery confirmation/history retention, reminder offset product rules, subscription/AI routing, cloud backup/sync semantics, account deletion, Birthday Autopilot, automatic SMS, public Hosting and unrelated domains. These may consume the resulting contracts but do not become part of the People/Birthday aggregate by implication.

## 7. Explicitly unresolved decisions

| ID | Open decision | Why it blocks a final physical design | Owner |
|---|---|---|---|
| DEC-023 | Existing Persons with null birthday: remediation, hidden/read-only state, delete, or user prompt | Required-birthday invariant cannot be imposed on existing data without a migration UX and compatibility rule | Product / Data |
| DEC-024 | Existing Feb 29 Persons without a saved policy | Backfill needs a user choice; a silent default would violate the approved per-Person choice | Product / Birthday |
| DEC-025 | Consolidation details for the two Person persistence adapters | One write owner is recommended, but public API compatibility and staged cutover need source-level design | Architecture |
| DEC-026 | Mutable current Birthday row versus immutable per-cycle occurrence history | Unique key, FK, history, backup and draft linkage depend on the choice | Product / Data |
| DEC-027 | Exact delete/undo scope across Person, occurrence, drafts, delivery history, reminders and jobs | Atomicity is a target invariant; retention and restore semantics are not approved | Product / Data |
| DEC-028 | Phone canonicalization and country-selection UX | E.164 cannot be safely inferred from local digits without country context | Product / Data |
| DEC-029 | Invalid legacy recipient timezone and unavailable device timezone behavior | Must avoid crashing date streams without silently changing calendar meaning | Birthday / Android |
| DEC-030 | How invalid/missing-birthday import candidates appear in preview | Persistence is prohibited, but the exact review experience remains open | People / UX |
| DEC-031 | Whether existing autoPrepare=true values are preserved, normalized or re-confirmed | New default is false; historical values may reflect explicit user intent or inconsistent defaults | Product / Data |
| DEC-032 | Exact DB constraints and orphan/duplicate repair ordering | Constraint choice depends on DEC-026 and DEC-027 and the existing database contents | Data / Architecture |
| DEC-033 | Refresh event policy for multiple recipient timezones | Device-local timer alone is insufficient; a scheduler strategy and lifecycle ownership must be selected | Birthday / Android |
| DEC-034 | Whether birthday occurrences are derived cache or durable/syncable records | Determines versioning, tombstones, backup format and rebuild semantics | Data / Sync |
| DEC-035 | Final invalid date and legacy record behavior on Person edit/export | Read compatibility and mutation rules must be consistent with remediation and portability | People / Product |

No open item is silently resolved by the proposed component diagram.

## 8. Current evidence and design limitations

- Phase 2.3 inspected application source at 93e803a. Phase 2.3 documentation commits changed only files under the product blueprint.
- Source review identified two Person adapters writing one table, non-atomic Person/Birthday operations, inconsistent intake validation/defaults, independent birthday calculation entry points, and missing Birthday uniqueness/FK constraints.
- The local Drift database was schema version 5 at the inspected source baseline. The target migration version is deliberately not assigned here.
- No database rows were queried for this documentation phase. The count and shape of legacy null birthdays, invalid timezones, duplicate/orphan Birthday rows and Feb 29 records are not known.
- Tests listed in Phase 2.3 are existing source mappings; Phase 2.4 has not run tests, migrations or device flows.

## 9. Exit criteria
## 8. Phase 2.4.1 decision closure status

Phase 2.4.1 proposes one coherent target for unresolved branches. Recommendations are not silently promoted to product approval:

- Technical architecture recommendations: DEC-019, DEC-020/025, DEC-026, DEC-032, DEC-033 and DEC-034. Architecture/Data/Android sign-off is pending.
- Product/privacy/data decisions requiring owner approval: DEC-022, DEC-023, DEC-024, DEC-027, DEC-028, DEC-029, DEC-030, DEC-031 and DEC-035.
- DEC-023, DEC-024, DEC-029, DEC-031 and DEC-032 also require aggregate-only local data inventory before migration details can be finalized.
- No source, schema or user database was changed or queried in this phase.

The complete alternatives, trade-offs, impacts and acceptance tests are in the [decision closure register](../phase-2.4.1-decision-closure/decision-register-and-option-analysis.md).

## 9. Exit criteria

- [x] Four Phase 2.3.1 product decisions recorded as approved.
- [x] Target application boundaries and use-case flows documented.
- [x] Atomicity, validation and timezone policies described.
- [x] Migration and rollback obligations documented.
- [x] Remaining decisions retained as open with owners.
- [ ] Product/Data owners decide all migration and retention questions.
- [ ] Architecture approves the physical schema and transaction implementation.
- [ ] P0 tests are implemented and pass against the approved design.
- [ ] Physical Android timezone/import/delete/undo flows are verified.
- [ ] A separate implementation authorization is recorded.

**Result: PARTIAL.** Target architecture and Phase 2.4.1 recommendations are documented. Owner approvals, existing-data inventory, physical schema/migration choices and implementation authorization remain outstanding.
