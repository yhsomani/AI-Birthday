# Feature Register

The authoritative detailed feature fields are F-001–F-109 in [definitive/02 — Feature Specifications](../../definitive/02-feature-specifications.md). This 2.0 register adds current evidence/lifecycle disposition.

| Range | Domain | Current evidence | Product disposition |
|---|---|---|---|
| F-001–003 | entry | implemented | retain/redesign recovery correctness |
| F-010–019 | People/import/export/delete | implemented/partial; split writes, soft-delete only | retain with unified aggregate/import; complete deletion/export |
| F-020–024 | Birthday | implemented; current stable-ID/DateTime constraints conflict with target | redesign identity/date/constraints, migration-gated |
| F-030–038 | planning/reminders | implemented | retain; close timezone/reconciliation/device evidence |
| F-040–056 | Studio/AI | implemented; prompt/job/privacy gaps | retain; harden privacy/injection/revision path |
| F-060–064 | delivery/history | manual handoffs; immutable history gaps | retain/redesign event/revision model |
| F-070–082 | account/entitlement | implemented, deployment/store evidence pending | retain; close contracts/evidence |
| F-090–095 | backup/privacy | implemented partial; retention/integrity/delete gaps | redesign before expanded production claim |
| F-096–098 | jobs/automation | durable jobs; no full fences; SMS/auto-prep disabled | retain manual jobs; block product automation |
| F-100–109 | operations/public/control | server/site/control-plane exist; not proven deployed/integrated | separate product/release-gate |

Current removals recommended: README product claim for active automatic personal SMS (`DR`); any BYOK bypass/old routing copies (`DR`); public promises of unsupported support/retention SLAs (`DR`); Stripe client billing (`DR`, already absent).
