# 12 — Governance, Requirements and Traceability Registers

**ID:** BP-GOV-001 · **Version:** 1.0 · **Status:** Active target registers; statuses are not implementation claims.

## 1. Requirements register

| ID | Requirement | Owner | Priority | Status | Feature / rules / AC-test |
|---|---|---|---:|---|---|
| REQ-001 | Core workspace is usable without cloud/AI after initialization | Product/Client | P0 | proposed | F-001–003, F-030/040; BR-014/BR-037; TST-ENTRY/JRN-001/007 |
| REQ-002 | New People include valid month/day and preserve unrelated fields | People/Data | P0 | approved intent | F-010–019; BR-001–005; TST-PEOPLE |
| REQ-003 | One deterministic annual occurrence exists per Person/cycle | Birthday/Data | P0 | proposed + approved policy inputs | F-020–024; BR-002–004; TST-BIRTHDAY |
| REQ-004 | Dashboard, Calendar and reminders use canonical occurrence | Planning | P0 | proposed | F-030–037; BR-007/035; TST-PLAN |
| REQ-005 | Manual draft survives navigation/restart/failure | Studio | P0 | proposed | F-040–042; TST-STUDIO |
| REQ-006 | AI remains optional/manual-safe and grounded | AI/Privacy | P0 | approved entitlement intent; rest proposed | F-050–056; BR-009–016; TST-AI |
| REQ-007 | No AI provider call occurs without active entitlement | AI/Entitlement | P0 | approved rule | F-050; BR-009–012; provider call-count tests |
| REQ-008 | Handoff and user-confirmed sent/delivered are distinct | Delivery | P0 | approved direction | F-060–064; BR-017–023; TST-DELIVERY |
| REQ-009 | Purchase entitlement is server/store verified and expiring | Entitlement | P0 | proposed for implementation evidence | F-080–082; BR-029; TST-ACCOUNT |
| REQ-010 | Backup is opt-in/owner-scoped and never includes secrets | Continuity | P1 | proposed | F-090–095; BR-024–028; TST-CONTINUITY |
| REQ-011 | Restore is complete/validated and non-resurrecting | Data | P0 when enabled | proposed | F-091; BR-027–028; corrupt/conflict tests |
| REQ-012 | Deletion distinguishes scope/request/accepted/processing/complete | Privacy/Ops | P1 | proposed | F-094; BR-030–031; TST-DELETION |
| REQ-013 | Jobs are idempotent, bounded and crash recoverable | Client/Platform | P1 | proposed | F-096–098; BR-033; race/restart tests |
| REQ-014 | Critical journeys support narrow/large-text/screen reader | UX/Accessibility | P0 | proposed | F-all; UX §8; TST-A11Y |
| REQ-015 | Logs/analytics exclude names, birthday, destination, facts, messages, prompts/tokens | Security/Privacy | P0 | proposed | F-100/103; security §4; redaction tests |
| REQ-016 | Deployment/production claims require named environment evidence | Release | P0 | proposed | F-101–103; QA gate 2/3 |
| REQ-017 | Public site and control plane are conditional isolated surfaces | Product/Platform | P0 boundary | proposed | F-102/109; BR-032; endpoint/caller evidence |

## 2. Feature register

Feature ranges in [02 — Feature specifications](02-feature-specifications.md):

| Range | Owner | P0/P1/P2 | Status |
|---|---|---|---|
| F-001–003 | Entry/navigation | P0 | source evidence; target contract added |
| F-010–019 | People | P0/P1 | partial approvals; migration decisions open |
| F-020–024 | Birthday | P0 | approved input policies; physical schema open |
| F-030–033 | Planning | P0/P1 | target |
| F-034–038 | Reminders | P0 | platform verification open |
| F-040–056 | Studio/AI | P0/P1 | entitlement approved; implementation/live gaps |
| F-060–064 | Delivery/history | P0/P1/P2 conditional | source evidence; target event model |
| F-070–082 | Account/entitlement | P0/P1 | contract/wire/live gaps |
| F-090–095 | Continuity/privacy | P0/P1 | policy/deployment/data gaps |
| F-096–098 | Automation/reliability | P1/P2 conditional | contract added |
| F-100–109 | Operations/public/control | P1/P2 conditional | owner/deployment approval gaps |

## 3. Business rules register

BR-001–BR-037 in the parent `05-business-rules.md` remain the detailed baseline. Definitive additions:

| ID | Description | Owner | Dependencies | Acceptance |
|---|---|---|---|---|
| BR-038 | A feature must not use a cache/snapshot as entitlement or source authority after its approved expiry | Entitlement/Security | REQ-007/009 | expired snapshot provider-call count zero |
| BR-039 | A draft revision handed off is immutable; later edits create a new review/revision | Studio/Delivery | F-041/042/060 | exact handoff revision retained and edit revokes readiness |
| BR-040 | Invalid non-empty recipient timezone is distinct from absent; it is flagged and no new schedule is invented | Birthday | F-024/019 | invalid and absent cases produce different repair/schedule state |
| BR-041 | Deletion fencing blocks backup/control writers but does not authorize false completion | Continuity/Privacy | F-090/094 | concurrent writer rejected; all scopes reconciled before complete |
| BR-042 | A request receipt, purchase token or app-launch result is never treated as business completion | Product/Backend | F-060/080/094/102 | state-machine negative tests |
| BR-043 | No feature’s retry may duplicate an external side effect | Jobs/Delivery | F-064/080/090/096 | crash/replay/concurrency tests |
| BR-044 | AI prompt fields are an allow-list; notes, names and facts are data, never instructions | AI/Security | F-053 | adversarial prompt suite |
| BR-045 | Unknown delivery/provider/platform state remains unknown or unavailable, not success | All owners | F-050–064 | no default-success mapping |
| BR-046 | Local/account data ownership does not silently switch on sign-out/account switch | Account/Data | F-070–095 | account isolation tests |

## 4. Dependency register

| ID | Dependency | Enables | Constraint / failure policy |
|---|---|---|---|
| DEP-001 | Approved Person model/validation | birthday/import/studio | no downstream guessed defaults |
| DEP-002 | Canonical Birthday engine/occurrence | planning/reminders/studio/history | one owner/query/state |
| DEP-003 | Durable draft revision | AI and delivery | preserve exact base/result |
| DEP-004 | Store/server entitlement | AI + subscription UX | fail closed/zero call |
| DEP-005 | Provider capability | AI operation | availability distinct from entitlement |
| DEP-006 | Delivery event/revision | truthful history/confirmation | no status inference from navigation |
| DEP-007 | Aggregate/schema/version model | backup/restore/delete/migration | approved conflict/retention first |
| DEP-008 | Identity and owner rules | cloud/entitlement/delete | server-derived owner |
| DEP-009 | Permission/capability/consent | contacts/reminders/Nano/auto SMS | point-of-use and revocation |
| DEP-010 | Legal/store/platform policy | public/automatic channels | blocked until approval |
| DEP-011 | Ops environment/evidence | release/production claims | repository existence insufficient |
| DEP-012 | Real/fixture migration inventory | destructive migration constraints | no destructive DDL claim without preflight |
| DEP-013 | Supported platform matrix | feature/device assurance | unsupported = truthful fallback |
| DEP-014 | Documentation/traceability update | all changes | no ready status without links/tests |

## 5. Decision register (open and recommended)

The parent blueprint records DEC-001–DEC-035. The following are the definitive-program decisions still requiring explicit approval.

| ID | Decision | Current recommendation | Status | Approver/evidence |
|---|---|---|---|---|
| DEC-036 | Product definition and boundaries | Adopt BP product thesis/non-goals | Pending | Founder/Product |
| DEC-037 | Initial platform/geography/languages | Approve only after evidence; Android is current implementation evidence | Open | Product |
| DEC-038 | Commercial model/subscription price/trial | Research first; entitlement engineering does not prove willingness/packaging | Open | Business/Legal/Product |
| DEC-039 | Cloud feature semantics | One-way backup/restore unless bidirectional sync is separately funded/defined | Open recommendation | Product/Data |
| DEC-040 | Draft/delivery/history retention | Durable revisions/events with approved purge/retention; never disposable occurrence cache | Open recommendation | Product/Privacy |
| DEC-041 | Delete scope | Soft visibility, active-effect cancellation, tombstone/deletion propagation; permanent purge by policy | Open recommendation | Product/Privacy/Data |
| DEC-042 | Offline entitlement | Expiring verified snapshot with no extension on verify failure; manual remains | Open recommendation | Product/Entitlement/Security |
| DEC-043 | Invalid BYOK key vs Nano | Show explicit repair and “use Nano” when ready; never silently label invalid as absent | Open recommendation | Product/AI |
| DEC-044 | Quiet-hours semantics | Current wording suppresses/skips; approve explicitly versus deferred delivery | Open | Product/Android |
| DEC-045 | Phone normalization | User-selected country for local numbers; canonical E.164; no inferred geolocation | Open recommendation | Product/Data |
| DEC-046 | Local DB encryption/Android backup | Threat/model trade-off and key recovery before adopting/claiming | Open | Security/Product |
| DEC-047 | Auto-prepare | Exclude until consent, eligibility, quota, retry, retention and UX approved | Blocked | Product/Privacy/AI |
| DEC-048 | Automatic SMS | Approved Android scope with per-person opt-in and exact-occurrence approval; manual handoff in Play if policy disallows automation; fail-closed until evidence | Scope approved; activation gated | Product approved; Legal/Platform/Release pending |
| DEC-049 | Public Hosting/deletion | Conditional on legal/support/identity/anti-abuse/deployment evidence | Blocked | Release/Legal/Ops |
| DEC-050 | Birthday Autopilot | Include in AI-Birthday; integrate the server control plane without contact/phone/message content; Hosting identity and production deployment remain separate approvals | Scope approved; integration/deployment pending | Product approved; Platform/Legal/Release pending |
| DEC-051 | Telemetry/analytics | No private-content telemetry default; approve event/consent/retention plan | Open | Privacy/Product |
| DEC-052 | Support/deletion operating model | Name owner, hours/SLA, access and evidence retention before launch | Open | Operations |
| DEC-053 | Performance/availability budgets | Establish baselines on supported device/data/provider matrix, then approve | Open | Engineering/QA/Product |

## 6. Assumption register

| ID | Assumption | If false | Validation / owner |
|---|---|---|---|
| ASM-001 | Individual organizer is primary user | positioning/IA changes | user research / Product |
| ASM-002 | Manual drafting is acceptable fallback | AI/UX slice value changes | usability / Product |
| ASM-003 | Local-first is understood/valued | onboarding/privacy changes | research / Privacy/UX |
| ASM-004 | Android is launch platform | release/device/API scope expands | approval / Product |
| ASM-005 | English launch locale initially sufficient | l10n/content/QA expands | discovery / Product |
| ASM-006 | One local account context at a time is core | multi-profile data isolation needed | approval / Product/Data |
| ASM-007 | Optional provider/store/backend dependencies can meet supportable reliability | fallback/commercial plan changes | provider/device/staging / Engineering |
| ASM-008 | Users accept explicit confirmation after external app | journey friction or alternative evidence design | usability / UX |

## 7. Risk register

| ID | Risk | L/I | Mitigation | Trigger / owner |
|---|---|---:|---|---|
| RSK-001 | Entitlement wire/ack/cache grants wrong AI state | H/H | exact envelope integration, expiring evidence, call-count tests | purchase failure/charged-locked / AI |
| RSK-002 | Person/Birthday/delete/import data corruption | H/H | canonical model, transaction, migration preflight, property/fault tests | duplicate/orphan/resurrection / Data |
| RSK-003 | Date/timezone/quiet-hour disagreement | M/H | one resolver, table/device tests | reminders off by date / Birthday |
| RSK-004 | WhatsApp/SMS launch represented as sent | M/H | separate immutable events/user confirmation | history/dashboard claim / Delivery |
| RSK-005 | Private data in provider/backup/log/diagnostic | M/H | allow-list/denylist, disclosure, redaction, scans | data flow incident / Security |
| RSK-006 | Cross-account restore/delete breach | M/H | server owner, rules, fencing, integration tests | foreign UID access / Security |
| RSK-007 | Automatic capability sends unexpectedly | M/Critical | exclusion/flags/approval/revision/idempotency/stop | any pending duplicate/stale send / Product/Ops |
| RSK-008 | Conditional backend/hosting treated as live | H/H | separate release evidence and claim checks | docs/UI says deployed / Release |
| RSK-009 | CI substituted for physical/device/live proof | H/H | named integration/device gates | release gate missing / QA |
| RSK-010 | Scope/monetization built without user/market evidence | H/H | Phase 0/1 and decision ownership | unsupported claims / Product |
| RSK-011 | Migration locks/destructively alters real user data | M/H | preflight, additive migration, backup/rollback, synthetic+real inventory | nonzero violations / Data |
| RSK-012 | Support/deletion cannot meet rights expectations | M/H | scoped runbook/owner/SLA/receipt evidence | aged requests / Ops/Privacy |
| RSK-013 | Split docs/models drift | H/M | canonical registers and generated projections only | contradictory ID/rule / Documentation |

## 8. Traceability matrix

| Journey | Features | Requirements | Rules | Acceptance/test |
|---|---|---|---|---|
| JRN-001 | F-001–003 | REQ-001/014/016 | BR-037/045 | initialization, first value, blocked migration, a11y |
| JRN-002 | F-010–013, F-019–024 | REQ-002/003 | BR-001–006/036/037/040 | TST-PEOPLE/BIRTHDAY |
| JRN-003 | F-014–016 | REQ-002/014/015 | BR-001/002/036 | malformed/duplicate/permission/privacy corpus |
| JRN-004/005 | F-030–033 | REQ-003/004/014 | BR-007/035 | cross-screen deterministic snapshot |
| JRN-006 | F-034–038 | REQ-004/014 | BR-007/008/034/040 | timezone/quiet/reboot/permission |
| JRN-007 | F-040–042 | REQ-005/014 | BR-039/045 | revision/process-death/stale edit |
| JRN-008 | F-050–056, F-080–082 | REQ-006/007/009/015 | BR-009–016/038/044 | provider zero calls, tamper/timeout/adversarial |
| JRN-009 | F-060–064 | REQ-008 | BR-017–023/039/042/043 | launch/kill/yes/no/unknown/revision |
| JRN-010 | F-070–082 | REQ-007/009 | BR-029/038/042/046 | auth/purchase/restore/account switch |
| JRN-011 | F-090–093 | REQ-010/011/015 | BR-024–028/041 | opt-in/corrupt/conflict/foreign/account |
| JRN-012 | F-071/F-092–095 | REQ-012/015 | BR-030/031/041/046 | sign-out/export/deletion receipt/completion |
| JRN-013 | F-100–104 | REQ-015/016 | security §2–10 | redaction/support/incident |

## 9. Status and change control

Allowed statuses: `proposed`, `approved`, `implementation-ready`, `implemented-evidence`, `verified`, `blocked`, `retired`. `approved` means product intent, not code. `verified` requires named commit/build/environment/result. No status changes without owner and evidence link.

Change process: rationale → impact IDs → product/UX/data/security/QA review → update feature/requirement/rule/journey/architecture/test/diagram/index → approval/evidence → revision history. Superseded IDs remain with replacement; they are never reused.
