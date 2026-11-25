# Requirements Register

| ID | Requirement | Class | Owner | Priority/phase | Source/map/status |
|---|---|---|---|---|---|
| FR-001 | initialized local workspace works without cloud/AI | IR/EPD | Client/Product | P0/Core | UJ-001, TC normal/offline |
| FR-002 | valid Person birthday and leap resolution on new writes | IR/EPD | People/Data | P0/Core | UJ-002, TC-001/002 |
| FR-003 | one occurrence truth across views/schedules | ER | Birthday | P0/Core | TC-003 |
| FR-004 | durable manual draft across failure/navigation | IR/ER | Studio | P0/Core | TC-005 |
| FR-005 | entitlement before AI selection/call | IR/EPD | AI/Entitlement | P0/Core | TC-004 |
| FR-006 | handoff/confirmation/transport states distinct | EPD/IR | Delivery | P0/Core | TC-006 |
| FR-007 | opt-in owner backup excludes secrets and discloses scope | IR/ER | Continuity/Privacy | P0 if enabled | TC-007 |
| FR-008 | restore/delete validate scope and reconcile linked state | ER/DA | Data/Privacy | P0/P1 | TC-007/009 |
| FR-009 | approved a11y/responsive matrix | ER | UX | P0 | TC-011 |
| FR-010 | durable bounded crash-recoverable jobs | IR/ER | Platform | P1 | TC-010 |
| NFR-001 | no secret/recipient/message/prompt normal logging | ER | Security/Ops | P0 | adversarial/log tests |
| NFR-002 | typed unknown/error/unavailable states, never false success | ER | All domains | P0 | mapping/failure tests |
| NFR-003 | measurable, approved performance/reliability targets | DA | Product/Ops | before release | workload baseline missing |
| NFR-004 | privacy/security/restore/rollback operational readiness | ER | Security/Ops | release | release criteria |

Detailed requirements and source mapping are retained in [definitive governance](../../definitive/12-governance-registers.md) and the phase traceability audits.
