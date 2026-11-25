# Dependency-Aware Product Roadmap

No dates, budget, staffing or delivery estimate is asserted.

| Phase | Objective | Scope/deliverables | Dependencies/exit evidence |
|---|---|---|---|
| PH-01 Discovery/validation | validate users, commercial model, platform/locales | research plan, evidence, revised assumptions | GAP/ASM owners; product thesis validated or revised |
| PH-02 Definition approval | approve identity, boundaries, privacy/ownership | signed DEC-036–046 | evidence and owner/date; conflicts resolved/excluded |
| PH-03 Contracts | finalize Person/occurrence/draft/event/backup rules | ADRs, schemas, APIs, BR/AC | DEC-041/042/039/040 |
| PH-04 UX foundation | IA, roles, design/a11y/content | prototypes, accessibility/design system contracts | approved journeys/language/platform |
| PH-05 Architecture/security/data | finalize logical model, threat model, migration | approved ADRs, PIA, migration fixtures | product contracts; raw DB excluded |
| PH-06 Infrastructure prep | isolated environments, CI, secrets, obs, release/signing | operational configuration evidence | architecture/owner approvals |
| PH-07 Core platform | local init, Person aggregate, occurrence, navigation | atomic/recoverable core | PH-03/04/05 |
| PH-08 Primary value | planning/reminders/Studio/manual handoff/history | E2E critical journeys | core and platform gates |
| PH-09 Entitled AI | validated route/disclosure/provider safety | server/store + provider contract evidence | entitlement/commercial decisions |
| PH-10 Continuity | approved backup/export/restore/delete | lifecycle and retention evidence | privacy/data decisions |
| PH-11 Conditional expansion | Nano/locales/Autopilot/public/auto-SMS only if approved | separately gated releases | capability-specific evidence |
| PH-12 Integration/quality | cross-domain/race/device/a11y/security/load | traceable results | PH-07–11 stable candidates |
| PH-13 Operational/release | signing/store/deploy/runbook/smoke | named-environment release evidence | all affected gates |
| PH-14 Operate/improve | incidents, outcomes, deletion/restore drills | reviewed metrics and feedback | release monitoring/privacy policy |

Detailed dependencies: [dependency plan](dependency-plan.md); sequence diagram: [roadmap map](../10-diagrams/roadmap-map.md).
