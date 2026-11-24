# 10 — Governance registers

## 1. Decision register

| ID | Decision / question | Current position | Status | Owner / next action |
|---|---|---|---|---|
| DEC-001 | Product is a personal birthday assistant, not a general AI chatbot | Adopt as working definition | Proposed | Product owner approval |
| DEC-002 | Local-first is the default for core personal data | Existing direction | Proposed | Privacy review |
| DEC-003 | User reviews and initiates all WhatsApp messages | Adopt | Existing product boundary | Preserve and test |
| DEC-004 | Active app subscription gates all AI providers, including BYOK and Nano | Explicit product decision; current router behavior, tests and Settings copy agree. SSOT and root architecture text reconciled in revision 0.1.4; stale router source comments remain | Confirmed product rule; source behavior/test-backed, device/provider runtime not independently verified | Product + AI owner: separately authorize stale source-comment correction |
| DEC-005 | Invalid configured key is an error, not missing configuration | Adopt | Proposed | AI owner |
| DEC-006 | Cloud backup is opt-in | Existing direction | Proposed | Product/privacy approval |
| DEC-007 | Backup is not described as end-to-end encrypted without evidence | Required claim boundary | Confirmed by current architecture docs | Security owner |
| DEC-008 | Personal automated SMS is a separate distribution capability | Existing source flavor split | Proposed | Product/legal/store review |
| DEC-009 | Birthday Autopilot is a separate product surface from core WhatsApp handoff | Existing backend docs | Proposed boundary | Stakeholder decision |
| DEC-010 | Public Hosting is not production until external gates are evidenced | Backend/hosting READMEs | Current evidence | Release owner |
| DEC-011 | Supported platform scope | Android evidenced; broader scope not approved | Open | Product owner |
| DEC-012 | Leap-day occurrence policy | Approved: each Feb 29 Person has a user-selected policy of Feb 28 or Mar 1 in non-leap years; leap years remain Feb 29 | Approved product decision; preference field, UX and legacy backfill remain design work | Product / Birthday |
| DEC-013 | Analytics/telemetry policy | No user-data collection assumed | Open | Privacy owner |
| DEC-014 | Subscription packaging, price and trial | Purchase code exists; commercial strategy unvalidated | Open | Business owner |
| DEC-015 | Backup semantics: one-way backup/restore vs multi-device sync | Existing docs describe snapshot backup | Open | Product/data owner |
| DEC-016 | Person birthday optionality across create/import | Approved: every newly persisted Person must have valid birthday month/day across manual, CSV and device intake; source still permits legacy no-birthday rows | Approved product rule; legacy remediation and read compatibility remain open | Product / Data |
| DEC-017 | Canonical autoPrepare default | Approved: false for every new Person and intake path; explicit user opt-in may set true | Approved product rule; alignment and treatment of existing stored values remain open | Product / Data |
| DEC-018 | Person phone normalization | Form validates shape; save path does not guarantee E.164 canonicalization | Open: define country context and normalization standard | Product / Data |
| DEC-019 | Person/Birthday aggregate transaction boundary | Recommend one SQLite UnitOfWork transaction for Person plus the affected per-cycle Birthday record; reminder/job effects occur after commit through durable reconciliation | Technical recommendation documented; Architecture/Data sign-off and implementation authorization pending | Data / Architecture |
| DEC-020 | Canonical Person persistence adapter | Recommend one PersonRepository port, one Drift adapter and one People application write boundary; read and write paths share explicit active/include-deleted semantics | Technical recommendation documented; cutover plan and Architecture/Data sign-off pending | Architecture / Data |
| DEC-021 | Birthday timezone precedence and refresh trigger | Approved: valid recipient IANA timezone when present; device-local timezone when recipient timezone is absent; no user-profile timezone fallback | Approved product rule; refresh triggers and invalid/unavailable timezone recovery remain open | Product / Birthday / Android |
| DEC-022 | Prior-cycle draft retention | Recommend retain prior-cycle drafts and delivery evidence as read-only history linked to the exact Person/year occurrence; never prune solely because the cycle is no longer current | Product/Privacy approval required; retention and permanent-deletion policy remain open | Product / Message Studio / Privacy |
| DEC-023 | Existing Persons without birthdays | Recommend preserve rows, mark Needs birthday, suppress occurrence/reminders, allow viewing/export, and require a valid birthday before a successful profile mutation | Product/Data approval and aggregate-only data inventory required; record counts are unknown | Product / Data |
| DEC-024 | Existing Feb 29 Persons without a saved preference | Recommend one-time per-Person prompt; do not infer choice from an old computed date; suppress new-cycle reminders until selected | Product approval and count of affected records required | Product / Birthday |
| DEC-025 | Canonical Person persistence boundary | Same target as DEC-020; treat as the cutover/compatibility subdecision, not a separate repository model | Merge with DEC-020 for approval; one adapter and one application write owner recommended | Architecture / Data |
| DEC-026 | Birthday occurrence persistence model | Recommend one durable occurrence identity per Person per cycle year, with unique (personId, cycleYear); never overwrite an old cycle's identity when a new year begins | Product/Data approval required because it affects history, schema and migration | Product / Data |
| DEC-027 | Delete/Undo aggregate scope | Recommend transactional soft-delete of Person and visibility of all linked occurrences/drafts/history; Undo restores the same aggregate; permanent purge must follow an approved retention/privacy rule | Product/Privacy approval required; exact child records and purge timing remain open | Product / Data / Privacy |
| DEC-028 | Phone normalization and country context | Recommend explicit country selection for local numbers, parse/validate with a maintained phone-number library, store canonical E.164, and never infer country from device location | Product/UX and dependency/security review required; legacy values stay unchanged until confirmed | Product / Data |
| DEC-029 | Invalid recipient timezone handling | Recommend distinguish absent from invalid: absent uses approved device-local fallback; invalid non-empty value is flagged, not silently rewritten, and reminder scheduling is paused until repaired | Product/Android approval required; device-timezone discovery failure policy remains open | Birthday / Android |
| DEC-030 | Import UX for invalid birthday candidates | Recommend show candidate with field-level error, unselected/disabled for persistence until user supplies a valid birthday; never silently discard the source row | UX/Product approval required | People / UX |
| DEC-031 | Existing autoPrepare values | Recommend preserve existing stored booleans, including true; apply false only to new records and genuinely missing/defaulted values; do not mass-reset user data | Product/Data approval required because provenance of historical true values is unknown | Product / Data |
| DEC-032 | Database constraints and repair ordering | Recommend UNIQUE(personId, cycleYear) plus FK to Persons.id with ON DELETE RESTRICT; add NOT NULL birthday constraints only after legacy repair and zero-violation preflight | Technical recommendation documented; depends on DEC-023/026/027/034 and inventory | Data / Architecture |
| DEC-033 | Refresh across multiple recipient timezones | Recommend recompute on app resume/timezone change and the next relevant recipient-local midnight; reminder alarms remain separately occurrence-based | Technical recommendation documented; Android lifecycle/Doze validation required | Birthday / Android |
| DEC-034 | Birthday occurrence durability | Recommend durable local per-cycle workflow record, not disposable cache; do not independently sync it until backup/sync contract DEC-015 is approved | Data/Sync approval required; local identity/history design is coupled to DEC-026 | Data / Sync |
| DEC-035 | Legacy edit/export behavior | Recommend keep legacy rows visible as Needs birthday; export blank birthday fields with a warning; require correction before re-import or successful profile mutation | Product/UX/Data approval required; must align with DEC-023 | People / Product / Data |


## 2. Assumptions register

| ID | Assumption | Risk if false | Validation |
|---|---|---|---|
| ASM-001 | Primary user is an individual managing personal relationships | Wrong product positioning | Customer interviews |
| ASM-002 | Manual drafting is sufficient fallback when AI unavailable | Reduced product value if false | Usability testing |
| ASM-003 | Local-first is a differentiator users understand and value | Messaging may be unclear | Onboarding comprehension test |
| ASM-004 | Android is the first supported platform | Scope and roadmap rework | Stakeholder approval |
| ASM-005 | Current local SQLite data remains canonical for core workflows | Multi-device expectations may conflict | Product decision on sync |
| ASM-006 | Google Play subscription is the intended entitlement channel for Android | Commercial/platform constraints | Billing and legal review |
| ASM-007 | No analytics containing personal birthday or message content is required | Product measurement limits | Privacy and product review |

## 3. Risk register

| ID | Risk | Likelihood / impact | Mitigation | Owner |
|---|---|---|---|---|
| RSK-001 | Conflicting AI entitlement behavior between user decision, SSOT and implementation | High / High | Reconcile requirement, router, UI, tests and docs as one controlled change | Product/AI |
| RSK-002 | Data integrity issues across parallel repositories, tombstones and undo | Medium / High | Full source trace, aggregate transaction model, property/integration tests | Data |
| RSK-003 | Birthday cycle/reminder mismatch after timezone or restore changes | Medium / High | Central date policy and reconciliation test matrix | Birthday/Reminders |
| RSK-004 | Handoff/history states are inconsistent or misleading | Medium / High | Canonical delivery event model and cross-screen contract tests | Delivery |
| RSK-005 | Cloud backup misunderstood as encrypted or fully synchronized | Medium / High | Explicit disclosure and approved sync semantics | Privacy/Data |
| RSK-006 | Backend control plane assumed deployed or integrated | High / High | Separate product boundary and deployment evidence gate | Platform |
| RSK-007 | Hosting deletion surface launched without legal/support identity gates | Medium / High | Block release until every external gate is evidenced | Release |
| RSK-008 | Device-dependent Nano and exact alarms fail on supported devices | Medium / Medium | Publish support matrix and physical-device tests | Android |
| RSK-009 | Subscription conversion assumptions lack user evidence | Unknown / High | Customer research and pricing validation | Business |
| RSK-010 | Scope expands into unrelated social/messaging features | Medium / Medium | Product boundary and approval gate | Product |
| RSK-011 | Person and Birthday writes can partially commit across create/edit/delete/undo | High / High | One aggregate transaction or durable recovery state; fault-injection tests | Data / Architecture |
| RSK-012 | Person import paths and timezone calculations can persist inconsistent or invalid Birthday inputs | Medium / High | Shared intake validation and one canonical occurrence calculator; timezone boundary tests | People / Birthday |

## 4. Dependency register

| ID | Depends on | Enables | Constraint |
|---|---|---|---|
| DEP-001 | Person model and validation | Birthday occurrence | Stable person ID and valid birthday |
| DEP-002 | Birthday occurrence policy | Dashboard, calendar, reminders, studio, history | One canonical date/timezone policy |
| DEP-003 | Draft persistence/revision | AI generation and delivery | Preserve last valid revision |
| DEP-004 | Subscription verification | All AI providers | Server-authoritative entitlement |
| DEP-005 | Provider capability discovery | AI generation | Entitlement must pass first |
| DEP-006 | Delivery event model | History and dashboard state | Handoff and confirmation distinct |
| DEP-007 | Local schema and versioning | Cloud backup/restore | Conflict and deletion semantics |
| DEP-008 | Auth and UID authorization | Cloud backup and deletion | Owner-scoped access |
| DEP-009 | Consent and flavor config | Automatic SMS | Separate approval and distribution gates |
| DEP-010 | Deployment config and legal approval | Public Hosting | No production deployment before gates |
| DEP-011 | Separate control-plane integration contract | Autopilot mobile capability | No assumed integration from callable existence |
| DEP-012 | Canonical Person persistence boundary and transaction strategy | Reliable Person/Birthday CRUD and Undo | All write paths use the same aggregate semantics |
| DEP-013 | Shared birthday validation and occurrence resolver | Consistent imports, dashboard, calendar and reminders | No independent date helper may disagree with BirthdayEngine |

## 5. Traceability index

| Requirement range | Domain spec | Journey | Rules | Test suite |
|---|---|---|---|---|
| FR-001–005 | DOM-02 | JRN-002 | BR-001, BR-005, BR-006 | TST-PEOPLE |
| FR-006–010 | DOM-03/04/06 | JRN-003 | BR-002–008, BR-034 | TST-BIRTHDAY / TST-REMINDER |
| FR-011–016 | DOM-07/08 | JRN-004/005 | BR-009–016 | TST-DRAFT / TST-AI |
| FR-017–021 | DOM-09/10 | JRN-006 | BR-017–023 | TST-DELIVERY |
| FR-022–023 | DOM-11 | JRN-008 | BR-029 | TST-AUTH / TST-BILLING |
| FR-024–025 | DOM-12 | JRN-007 | BR-024–028 | TST-BACKUP |
| FR-026 | DOM-13 | JRN-005/007 | BR-033 | TST-JOBS |
| FR-027–028 | DOM-15/16/17 | JRN-009 | BR-030–032 | TST-DELETION / TST-CONTROL |
| FR-001–005 | [Phase 2.3 People contract](phase-2.3-product-specification/people-lifecycle-contract.md) | JRN-002 | BR-001, BR-005, BR-006 | PPL-AC-001–015 |
| FR-006–008, FR-010 | [Phase 2.3 Birthday contract](phase-2.3-product-specification/birthday-lifecycle-contract.md) | JRN-002/003 | BR-002–004, BR-007 | BDY-AC-001–018 |

## 6. Coverage status

| Area | Current status | Reason |
|---|---|---|
| Product definition | Baseline drafted | Customer and commercial validation pending |
| Domain decomposition | Baseline drafted | Full code/source mapping pending |
| Feature contracts | Partial; scoped People/Birthday contracts added in Phase 2.3 | Two domain contracts and source/test traceability exist; stakeholder approvals, several P0 acceptance tests and all other domain contracts remain open |
| User journeys | Core journeys drafted | Edge-case and device validation pending |
| Business rules | Phase 2.3.1 approved birthday requiredness, autoPrepare=false, per-Person leap-day choice and recipient-to-device timezone fallback | Phone normalization, legacy-data handling, import-preview behavior, aggregate deletion and occurrence persistence remain open |
| Technical architecture | Phase 2.4 target architecture plus Phase 2.4.1 decision recommendations drafted (documentation only) | Owner sign-off, legacy-data inventory, physical schema, migration, transaction implementation and source verification remain pending |
| Security/privacy | Threat baseline drafted | Full data-flow and deployment review pending |
| Testing | Scoped People/Birthday test mapping added | Existing test names are mapped; aggregate transaction, import validation and device/timezone cases remain open |
| Roadmap | Dependency sequence drafted | Stakeholder priorities pending |
| Mind maps | Master and architecture diagrams created | Domain-level maps remain to be expanded |
| Legacy migration | Not started | Preserve all existing documents until cross-reference review |
| Git verification | Verified for the Phase 2.4.1 documentation revision | Commit comparison and branch head checked; changed paths are confined to product-blueprint documentation |

## 7. Glossary

| Term | Definition |
|---|---|
| Person | A recipient record maintained by the user |
| Birthday occurrence | A specific year's occurrence of a person's birthday |
| Cycle | The occurrence period associated with a birthday year |
| Draft | Editable message content not yet handed off |
| Handoff | User-initiated launch/copy/share action to an external channel |
| User-confirmed sent | User explicitly states that they sent the message; not provider delivery receipt |
| Entitlement | Server-verified application permission to use paid AI capabilities |
| BYOK | Bring Your Own Key; a provider credential supplied by the user |
| Nano | On-device Gemini capability through Android AICore/ML Kit |
| Backup generation | A versioned snapshot of explicitly selected user data |
| Tombstone | Durable deletion marker used to prevent deleted data from reappearing |
| Control plane | Separate backend system that coordinates sender authorization and lifecycle operations |
| Source-reviewed | Code was inspected; runtime behavior not necessarily executed |
| Verified | Specific behavior was tested in a named environment and tied to a commit |

## 8. Change process

1. Propose change with rationale and affected IDs.
2. Review product, UX, data, security and QA impact.
3. Update canonical requirements and rules.
4. Update journey, architecture and diagrams.
5. Add/adjust acceptance criteria and tests.
6. Record approval and revision history.
7. Retire superseded requirements with replacement references.
