# Business Rules Register

BR-001–BR-046 remain detailed in definitive governance/parent rule document. 2.0 authoritative additions and overrides:

| ID | Rule | Rationale | Trigger/result | Affected/test/approval |
|---|---|---|---|---|
| BR-047 | No release may market automatic SMS while compile/native gates are false | prevents false capability claim | release copy/artifact; remove/unsupported | DOM-08/11, capability static test; DEC-045 |
| BR-048 | Public/Autopilot functionality may not be represented as core AI-Birthday until separately approved | avoid identity/security mismatch | docs/build/navigation review; exclude or version product | DOM-13/14; DEC-044 |
| BR-049 | All People writes, including imports/restore, cross one invariant-preserving use case | prevent split validation/ownership | candidate/action → transaction or explicit reject | DOM-02/03; TC-001/002; DEC-041 |
| BR-050 | Occurrence identity must represent durable cycle semantics; rollover may not silently replace history | trace/data correctness | recurrence/rollover → create/reconcile approved record | DOM-03/08/10; TC-003; DEC-042 |
| BR-051 | Backup completion requires verified manifest integrity and approved retention/purge | privacy/recovery | publish/restore → verify hashes/count/schema/owner | DOM-10; TC-007; DEC-039/040 |
| BR-052 | Person/account deletion states exact local/cloud/provider scope and completion evidence | prevent false privacy completion | deletion request → scoped receipt/state; local purge separate | DOM-10/13; TC-007/009; DA |
| BR-053 | Prompt facts/instructions are data with escaping/limits; output cannot become fact without user context | abuse/safety | AI call/output → safe structured input/review | DOM-07; TC-008 |
| BR-054 | Account switch never silently changes local-data owner or uploads all local PII | privacy ownership | sign-in as different UID → explicit decision/export/wipe/continue | DOM-09/10; TC-009; DEC-043 |
| BR-055 | Numeric SLO/support/retention promises require approved owner, method and evidence | truth in operations | public/release copy → remove or prove | DOM-12/13; release gate; DA |
