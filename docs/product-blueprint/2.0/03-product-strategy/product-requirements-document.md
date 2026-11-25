# Product Requirements Document

**Status:** DA — requirements baseline for stakeholder review. Detailed atomic feature contracts remain in [definitive/02](../../definitive/02-feature-specifications.md).

| Requirement | Product obligation | Evidence/priority | AC/test |
|---|---|---|---|
| FR-001 | The initialized local workspace shall open and support manual birthday work without cloud/AI | EPD, P0 | AC-001; offline journey TC family |
| FR-002 | A newly persisted Person shall have a valid month/day; leap-day recurrence shall have saved preference | IR/EPD, P0 | create/import rejects unresolved; transaction tests |
| FR-003 | Exactly one canonical occurrence definition shall drive dashboard/calendar/reminder/studio | ER, P0 | cross-view consistency and duplicate tests |
| FR-004 | Manual draft edits shall be durable across navigation/restart/failure | IR/ER, P0 | autosave/recovery/stale tests |
| FR-005 | AI shall check active entitlement before provider selection/call; manual path survives lock/failure | IR/EPD, P0 | zero-call negative and timeout tests |
| FR-006 | Handoff, explicit user confirmation and external transport shall remain distinct | EPD, P0 | state-machine negative/security tests |
| FR-007 | Backup shall be opt-in, owner-scoped, explicitly non-E2E-encrypted, and exclude secrets | IR, P0 when enabled | auth/rule/privacy/failure tests |
| FR-008 | Restore/deletion shall validate scope and reconcile local/cloud/linked records before completion | ER, P0/P1 | corruption, tombstone, concurrent-writer tests |
| FR-009 | All critical journeys shall meet approved responsive and accessibility matrix | ER, P0 | narrow/large text/keyboard/screen-reader tests |
| FR-010 | Jobs shall have durable state, bounded retries, cancellation and crash recovery | IR/ER, P1 | process-death/replay/concurrency tests |
| FR-011 | App shall expose truthful capability/availability states; unknown must not become success | ER, P0 | provider/platform mapping tests |
| FR-012 | Store entitlement shall be server/store verified and expiring | IR/EPD, P0 | reject client grants/replay/expiry tests |

Full acceptance/test mappings: [requirements register](../09-registers/requirements-register.md).
