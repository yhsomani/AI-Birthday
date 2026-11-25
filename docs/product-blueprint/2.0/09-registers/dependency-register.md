# Dependency Register

| ID | Dependency | Enables | Failure policy/owner |
|---|---|---|---|
| DEP-001 | Approved product/commercial/platform scope | all requirements/release | do not infer / Product |
| DEP-002 | Person aggregate + validation | birthday/import/restore/studio | reject/quarantine invalid / Data |
| DEP-003 | Durable occurrence identity/date-zone model | planning/reminders/history/sync | no ambiguous fallback / Data |
| DEP-004 | Draft revision/event model | AI/delivery/history/retention | preserve exact revision / Studio |
| DEP-005 | Server/store entitlement and App Check | AI and purchase UX | fail closed, manual path / Entitlement |
| DEP-006 | Capability/permission state | Nano/reminders/contacts/SMS | unavailable/manual / Mobile |
| DEP-007 | Backup manifest/retention/restore/delete | continuity/privacy | block completion on unverifiable state / Privacy-Data |
| DEP-008 | Identity/account ownership | cloud/entitlement/delete | explicit account-switch decision / Product-Security |
| DEP-009 | Named deployment/device/evidence | production claims | blocked status / Release |
| DEP-010 | Legal/store/platform approvals | public/automation/locales | excluded until approved / Product-Legal |
