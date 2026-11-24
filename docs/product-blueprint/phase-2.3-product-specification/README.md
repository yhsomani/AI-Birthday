# Phase 2.3 — People and Birthday product contracts

**Repository:** yhsomani/AI-Birthday  
**Branch reviewed:** main  
**Source baseline:** 93e803aee3064b046ac9b9093cfd35e4b1a0ae62  
**Review date:** 2026-11-24  
**Change boundary:** documentation only  
**Status:** Scoped source-backed contracts and initial acceptance/test traceability drafted; product approval, architecture remediation and test obligations remain open.

## Objective

Convert People and Birthday from broad feature descriptions into actionable domain contracts. This phase inspects the Person model/form, validation, service and persistence adapters, CSV/device imports, Birthday engine and lifecycle, global providers, schema and current tests.

## Deliverables

| Document | Contents |
|---|---|
| [People lifecycle contract](people-lifecycle-contract.md) | Person data dictionary, validation, create/edit/delete/undo, imports, export, repository boundaries, states and privacy |
| [Birthday lifecycle contract](birthday-lifecycle-contract.md) | Gregorian recurrence, leap day, timezone, one active occurrence, status transitions, refresh and integrity |
| [Acceptance criteria and traceability](acceptance-criteria-and-traceability.md) | Given/When/Then criteria mapped to FR/BR, source files, named existing tests and uncovered cases |

## Evidence-based findings

1. **Two People persistence adapters write to the same table.** DriftPeopleStore backs PersonService; DriftPeopleRepository backs global streams and direct imports. They expose different deleted-row query semantics.
2. **Person and Birthday do not commit as one aggregate.** The form writes Person and then Birthday; delete tombstones Person and separately hard-deletes Birthday.
3. **Import validation is inconsistent.** CSV requires a birthday and bypasses the normal form validator; Android contacts can yield a Person without a birthday.
4. **CSV profile export is not full-fidelity backup.** It omits ID, timestamps, sync version, tombstone and autoSendPolicy.
5. **Person defaults disagree.** autoPrepare differs across model, draft, SQL column, form and import candidate.
6. **Phone numbers are not normalized to E.164 by the Person save path.** The validator checks shape; duplicate matching strips non-digits without country-aware normalization.
7. **Occurrence calculations have multiple entry points.** Normal lifecycle uses BirthdayEngine; form/import writes use timezone-free Birthday.nextBirthdayDate.
8. **Timezone handling is partially unified.** A recipient → user → UTC helper exists but is not wired in inspected lifecycle callers; refresh timer uses device-local midnight.
9. **Birthday uniqueness is an application invariant, not a database constraint.** Refresh throws on duplicate personId; table has an index but no unique constraint or declared FK.
10. **Cycle rollover clears the active draft link but retains the old draft row.** Intended History/archive behavior remains open.

These are source-review observations, not proof that every path has failed in production.

## Recommended next actions

1. Decide open product policies in both domain contracts.
2. Agree on one Person persistence abstraction and aggregate transaction strategy.
3. Create one canonical occurrence service for manual create/edit, imports and refresh.
4. Add P0 tests in the traceability register before changing application behavior.
5. Then authorize a separate implementation phase with scoped plan, migration strategy, code changes and test execution.

## Phase 2.3 exit status

- [x] People and Birthday source inventory inspected.
- [x] Data schema and primary repository paths inspected.
- [x] Current lifecycle, import, delete/undo and timezone behavior recorded.
- [x] Current test files and named assertions mapped.
- [x] Proposed contracts and unresolved product choices recorded.
- [ ] Product owner approves open decisions.
- [ ] Data/Architecture approves aggregate transactions and canonical repositories.
- [ ] All P0 acceptance gaps are closed by tests.
- [ ] Real Android device/timezone/import/reminder journeys are verified.
- [ ] Every People/Birthday feature node in global registry is mapped to these contracts.

**Result: PARTIAL.** Scoped contracts and source-to-test map are delivered, but implementation readiness is not approved while product, integrity and verification gaps remain open.
