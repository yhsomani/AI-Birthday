# Phase 2.4.3 — Documentation consistency audit and controlled blueprint execution

**Repository:** yhsomani/AI-Birthday  
**Branch:** main  
**Starting commit:** 6eed3e28cad0f8ef9243454786e82a37e488efe9  
**Starting blueprint:** 0.1.8  
**Review date:** 2026-11-24  
**Change boundary:** Documentation only  
**Status:** PARTIAL — consistency corrections and execution charter prepared; full product-contract expansion, pending approvals and local database inventory remain open.

## 1. Objective

Continue the existing blueprint from Phase 2.4.2 without restarting prior work. Establish an execution-ready operating prompt, identify contradictions in current documentation against approved decisions and source, correct only factual/documentation drift, and record the remaining execution gates.

This phase does not approve new product or architecture recommendations and does not authorize application implementation.

## 2. Repository baseline

The inspected main-branch tree at the starting commit contains:

| Measure | Result |
|---|---:|
| Total tree entries | 667 |
| File blobs | 462 |
| Files under docs/ | 126 |
| Markdown/diagram/structured documentation artifacts under docs/ | 105 |
| Files under docs/product-blueprint/ | 50 |

The repository already contains:
- A 10-document product blueprint.
- Master product and target-architecture diagrams.
- Phase 2, 2.1, 2.2 and 2.3 audits.
- Phase 2.3 People/Birthday contracts and acceptance traceability.
- Phase 2.4 target architecture and migration design.
- Phase 2.4.1 decision closure recommendations.
- Phase 2.4.2 decision approval matrix, repository inventory and aggregate-only SQLite SQL.
- A separate source-linked knowledge portal, older audits and archived historical specifications.

The product blueprint is therefore an existing workstream. Rebuilding it from scratch would discard useful traceability and duplicate canonical material.

## 3. Binding decisions carried forward

The four Phase 2.3.1 decisions remain approved and unchanged:

1. Every newly persisted Person requires valid birthday month/day; birth year remains optional. Existing legacy null-birthday records require separate remediation.
2. autoPrepare defaults false for every new record and intake path; explicit opt-in is required.
3. Each Feb 29 Person has a saved per-Person Feb 28 or Mar 1 non-leap-year choice; leap years remain Feb 29.
4. A valid recipient IANA timezone takes precedence; when it is absent, use device-local timezone. User-profile timezone is not the fallback.

The global AI policy remains entitlement-first: no AI provider, including BYOK or Nano, may be selected or invoked without active application entitlement. Manual drafting remains available when AI is locked or unavailable.

## 4. Confirmed documentation discrepancies

| ID | Location | Discrepancy | Corrective treatment |
|---|---|---|---|
| DOC-001 | SSOT.md §14 | Says recipient timezone falls back to user's timezone | Align with approved recipient → device-local rule; retain invalid non-empty timezone as an open recovery policy |
| DOC-002 | docs/knowledge/features/README.md | Says a personal Gemini key unlocks BYOK without Pro | Correct to require entitlement before either provider |
| DOC-003 | docs/knowledge/architecture/README.md | AI sequence and prose describe BYOK before the Pro gate | Correct sequence and source-evidence statement |
| DOC-004 | docs/knowledge/business-rules/README.md BR-013/014 and diagram | Encodes BYOK bypass and Nano-only entitlement | Correct to entitlement-first for all providers |
| DOC-005 | docs/knowledge/business-rules/README.md BR-008 | Presents Feb 28 as the default with an optional Mar 1 parameter | State the approved per-Person saved choice; distinguish the source's current default implementation from the target contract |
| DOC-006 | lib/features/ai/domain/ai_router.dart comments | Top-level comments describe BYOK without Pro, while executable code checks entitlement first | Record as a source comment defect; do not modify source under this assignment |
| DOC-007 | lib/features/birthdays/domain/birthday_engine.dart | effectiveTimezone currently describes recipient → user → UTC; timezone-free computeNext uses device-local wall-clock fields | Record cross-caller inconsistency as an implementation gap; do not modify source |
| DOC-008 | docs/knowledge/README.md and feature-registry.json | Registry evidence snapshot is dated 2026-10-14 | Retain as historical baseline until a full registry re-audit is completed; do not relabel it as current evidence |

## 5. Source facts that constrain the documentation

- The current source at the reviewed application baseline checks active entitlement before provider selection in AiRouter.generate.
- The source comments in AiRouter still describe the previous BYOK-without-Pro policy and are stale.
- BirthdayEngine contains a Feb 28 default resolver and a recipient → user → UTC effectiveTimezone helper. The timezone-free computeNext path uses the caller's local wall-clock frame.
- The Drift database is schema version 5 and declares six tables: Persons, Birthdays, MessageDrafts, ReminderSettingsEntries, DeliveryEvents and Jobs.
- Persons permits nullable birthday month/day. No persisted per-Person Feb 29 preference column exists.
- Birthdays has no declared unique constraint on (personId, cycleYear) and no declared foreign key to Persons.
- The checked-in migration test is a synthetic v4→v5 fixture that adds Jobs and checks retention of one Birthday row. It is not a representative user database.
- No operational local SQLite database is present in the tracked repository tree.

These facts establish gaps between the approved target and current source. They do not authorize implementation or prove production behavior.

## 6. Work performed in this phase

- Created the execution-ready master prompt at [MASTER-EXECUTION-PROMPT.md](../MASTER-EXECUTION-PROMPT.md).
- Reconciled the canonical SSOT's People/Birthday constraints and timezone policy with approved Phase 2.3.1 decisions.
- Corrected the active knowledge portal's AI entitlement descriptions and routing diagrams.
- Corrected the knowledge business-rule statements for entitlement and per-Person leap-day policy.
- Added an explicit AI entitlement-routing diagram.
- Updated the product blueprint index and revision history.
- Preserved the old source snapshot as historical evidence.
- Made no changes to application source, tests, dependencies, lockfiles, schema, migrations, configuration, infrastructure or user data.

## 7. Work deliberately not completed

The following are not approvals and remain open:

### Product and privacy approvals
- DEC-022: prior-cycle draft and delivery-history retention.
- DEC-023: legacy Persons without birthdays.
- DEC-024: legacy Feb 29 preference collection.
- DEC-027: delete/Undo aggregate scope and permanent purge.
- DEC-028: phone country context and E.164 normalization UX.
- DEC-029: invalid non-empty timezone recovery.
- DEC-030: invalid import candidate presentation.
- DEC-031: treatment of existing autoPrepare values.
- DEC-035: legacy edit/export behavior.

### Architecture, data and platform sign-offs
- DEC-019: Person/Birthday aggregate transaction boundary.
- DEC-020/025: canonical Person repository and write ownership.
- DEC-026: durable per-Person/per-year Birthday identity.
- DEC-032: physical constraints and migration ordering.
- DEC-033: refresh across recipient timezones.
- DEC-034: Birthday occurrence durability and sync boundary.

### Evidence blockers
- Aggregate inventory of an authorized, restore-tested local SQLite copy.
- Reconciliation of duplicate/orphan/draft/delivery links and legacy field classes.
- Migration rehearsal for every observed exception class.
- Full source-to-test refresh beyond the People/Birthday source baseline.
- Physical Android timezone, reminder, import and lifecycle verification.
- Full domain-specific feature contracts and domain mind maps.

## 8. Next execution sequence

1. Obtain aggregate-only inventory output from the user's local schema-v5 database copy. Do not request or upload the raw database.
2. Reconcile counts against DEC-023, DEC-024, DEC-029 and DEC-031 without inferring user intent.
3. Obtain explicit product/privacy and architecture/data approvals for the listed decisions.
4. Update the People/Birthday migration and transaction design only after approvals and inventory evidence.
5. Expand detailed feature contracts for the remaining domains, using existing feature registry and knowledge portal as evidence.
6. Complete domain-specific mind maps, API/integration contracts, operations specifications and traceability.
7. Run documentation consistency, link, ID, diagram and changed-path validation.
8. Publish a final audit with genuine completeness and blocker status.

## 9. Exit criteria

Phase 2.4.3 can be marked complete only when:
- The execution prompt is present and linked.
- All identified active documentation contradictions are corrected or explicitly retained as open.
- The AI routing diagram matches the approved policy.
- No application-level paths changed.
- The revision index and source baselines are accurate.
- The changed-path audit is clean.

This phase does not close the overall product blueprint. The overall blueprint remains PARTIAL until the outstanding approvals, data inventory, domain contracts, diagrams, traceability and verification work are completed.

## 10. Validation and Git record

Validation must report:
- Exact base and resulting commit.
- Complete changed-path list.
- Proof that every changed path is documentation-only.
- Link and diagram validation performed.
- Whether CI ran and what it covered.
- Explicit items not locally executable from the current environment.

No source tests or device tests are claimed by this phase.