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
| DEC-019 | Person/Birthday aggregate transaction boundary | Form and delete paths write Person and Birthday separately | Open: approve atomicity and recovery behavior | Data / Architecture |
| DEC-020 | Canonical Person persistence adapter | Two adapters write the same Persons table | Open: consolidate or explicitly separate ownership | Architecture / Data |
| DEC-021 | Birthday timezone precedence and refresh trigger | Approved: valid recipient IANA timezone when present; device-local timezone when recipient timezone is absent; no user-profile timezone fallback | Approved product rule; refresh triggers and invalid/unavailable timezone recovery remain open | Product / Birthday / Android |
| DEC-022 | Prior-cycle draft retention | Birthday rollover clears draftId but retains the old draft record | Open: define archive/history policy | Product / Message Studio |
| DEC-023 | Existing Persons without birthdays | Some source/import paths and existing data permit null month/day | Open: migration, remediation UX, visibility and backup/export behavior; never fabricate a date or silently delete | Product / Data |
| DEC-024 | Existing Feb 29 Persons without a saved preference | Source has no per-Person leap-day preference | Open: backfill choice and prompt timing | Product / Birthday |
| DEC-025 | Canonical Person persistence boundary | Two adapters write Persons with different query/tombstone semantics | Open: approve cutover and compatibility plan; one write owner is recommended | Architecture / Data |
| DEC-026 | Birthday occurrence persistence model | Current source stores one mutable Birthday row per Person; history semantics undefined | Open: mutable current row vs immutable per-cycle records | Product / Data |
| DEC-027 | Delete/Undo aggregate scope | Person tombstone, Birthday deletion and draft/history behavior are split | Open: exact linked-data retention/restoration semantics; atomicity is a target invariant | Product / Data |
| DEC-028 | Phone normalization and country context | Save path does not guarantee E.164 normalization | Open: country selection, accepted formats and migration | Product / Data |
| DEC-029 | Invalid recipient timezone handling | Invalid legacy/imported values may exist | Open: reject, quarantine or safely fall back; device timezone unavailable policy | Birthday / Android |
| DEC-030 | Import UX for invalid birthday candidates | Required birthday is approved; exact preview handling is not | Open: show invalid candidate or exclude before preview; persistence remains blocked | People / UX |
| DEC-031 | Existing autoPrepare values | Current stored values may reflect explicit choice or inconsistent defaults | Open: preserve, normalize or prompt; new/missing defaults resolve false | Product / Data |
| DEC-032 | Database constraints and repair ordering | Birthday lacks declared Person FK/unique constraint; duplicate/orphan rows possible | Open: constraints, duplicate repair, FK behavior and ordering | Data / Architecture |
| DEC-033 | Refresh across multiple recipient timezones | Device-local timer alone is insufficient | Open: scheduler strategy and lifecycle ownership | Birthday / Android |
| DEC-034 | Birthday occurrence durability | Persisted Birthday row has no version/tombstone | Open: derived cache vs durable/syncable record | Data / Sync |
| DEC-035 | Legacy edit/export behavior | Old no-birthday/invalid data may be opened or exported | Open: mutation, portability and recovery contract | People / Product |


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
| Technical architecture | Phase 2.4 People/Birthday target architecture drafted (documentation only) | Physical schema, migration, transaction implementation and source verification remain pending |
| Security/privacy | Threat baseline drafted | Full data-flow and deployment review pending |
| Testing | Scoped People/Birthday test mapping added | Existing test names are mapped; aggregate transaction, import validation and device/timezone cases remain open |
| Roadmap | Dependency sequence drafted | Stakeholder priorities pending |
| Mind maps | Master and architecture diagrams created | Domain-level maps remain to be expanded |
| Legacy migration | Not started | Preserve all existing documents until cross-reference review |
| Git verification | Verified for the Phase 2.3 revision | Commit comparison and branch head checked; changed paths are confined to product-blueprint documentation |

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
