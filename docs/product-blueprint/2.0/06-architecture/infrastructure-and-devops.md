# Infrastructure, Deployment and Operations

## Required environment model

Development/test/emulator/staging/production boundaries with separate Firebase projects, secrets, IAM, billing quotas, App Check, release signing, store tracks, Hosting and region/residency. Do not treat a demo/emulator pass as production evidence.

## Delivery controls

- Reproducible dependency restore/build; lint/typecheck; unit/contract/integration/emulator/E2E; accessibility/performance/security; APK/site artifacts and provenance.
- Static/behavior checks prevent Play `SEND_SMS`, Stripe client secret/billing path, entitlement test hooks and blocked capability enablement.
- Environment promotion, feature flags and database migration are separate approvals; rollback must not erase/overwrite newer records.
- Managed secrets, least privilege, dependency scans, signed artifacts, observability, alerts and incident runbooks.
- Backup/restore/PITR/region/TTL decisions and actual drills; no runbook marked effective before execution.
- Production smoke/post-deploy evidence, release/rollback decision owner and post-incident review.

`backend/README.md` and [v1 readiness](../../definitive/13-v1-production-readiness.md) correctly identify missing external deployment/signing/device/store/legal gates. No infrastructure change/deployment was made here.
