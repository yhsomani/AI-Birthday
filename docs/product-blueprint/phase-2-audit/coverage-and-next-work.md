# Phase 2 — Coverage, Limitations and Next Work

**Reviewed revision:** `ea475e0135a99d5dbae9891340c4a554f2e5fe15`

## What is established

| Area | Evidence status | Conclusion |
|---|---|---|
| Repository structure | Tree inspected recursively | 425 files, 198 directories; major product and infrastructure areas identified |
| Flutter app topology | Representative root, provider, domain, data and presentation files inspected | Local-first Flutter application with Riverpod, Drift and Android adapters |
| Local data schema | Current Drift schema inspected | Six tables, schema version 5; per-table sync metadata differs |
| AI entitlement | Router method, provider, entitlement model and tests inspected | Active app entitlement is required before any provider route |
| Backend structure | Functions entry point, core services, schemas, package and rules inspected | Purchase verifier and additional coordination control plane exist in source |
| CI | Latest run and failing job logs inspected | Flutter responsive test and backend dependency install block pipeline; Hosting passed |
| Documentation conflicts | Root docs, knowledge portal, feature registry and audit reports inspected | Several stale snapshots and contradictory AI/data-model claims exist |

## What remains open

### A. Exhaustive feature audit

- Read all feature source files, not only representative anchors.
- For every feature/subfeature, record source path, entry point, state owner, persistence, external effects, errors, permissions, test assertion and current status.
- Distinguish implemented from complete, tested, device-verified and deployed.
- Verify every node in the 50-node feature registry and add missing capabilities discovered in source.
- Resolve the two Person model definitions and field parity without refactoring application code.

### B. API and backend contracts

- Enumerate every callable and scheduled function with request schema, authentication/App Check requirements, region, secrets, return shape and error codes.
- Trace each mobile/backend callsite to its actual function URL or callable invocation.
- Map Firestore collection/document paths to rules, readers, writers, deletion and retention behavior.
- Verify whether the separate coordination control plane is referenced by any shipped app flow or is an isolated backend surface.
- Reconcile package manager version and lockfile before backend test evidence can be collected.

### C. Data and lifecycle

- Inspect all Drift table definitions, generated schema, migrations, repositories and sync codecs.
- Produce entity-field matrices and relationship diagrams that distinguish conceptual references from enforced constraints.
- Trace create/update/delete/restore flows and identify transaction boundaries.
- Verify backup generation publication, partial failures, conflict resolution, tombstone behavior and restore atomicity against tests.
- Inspect job state transitions, retry limits, idempotency and crash recovery.

### D. Native/device and external integrations

- Inspect full Android manifests, Gradle flavors, receivers, permissions and Kotlin channel methods.
- Verify actual package IDs and flavor-specific permissions from built artifacts once builds pass.
- Device-test notification permission changes, exact alarms, reboot, timezone changes, Nano state/download/inference and SMS flavor boundaries.
- Validate live sign-in, Play Billing sandbox, Gemini API, Firestore backup and user-confirmed delivery flows in controlled environments.

### E. Documentation migration and governance

- Review all 89 files under `docs/` plus root documentation.
- Preserve historical content and provenance; do not delete legacy documents in this phase.
- Migrate unique, current facts into domain specifications and mark superseded reports as historical.
- Build requirement → feature → source → test → CI/deployment traceability.
- Run link, anchor, Mermaid, JSON and registry consistency validation.

## Recommended execution order

1. **Unblock evidence collection:** isolate the calendar overflow; reconcile backend package manifest, lockfile and npm toolchain; rerun CI.
2. **Complete source inventory by module:** People/Birthdays/Calendar/Reminders, Message Studio/AI/Jobs, Delivery/History, Auth/Subscription/Sync/Settings, Android, Functions, Hosting.
3. **Complete API and data lineage:** callable contracts, Firestore paths/rules, Drift schema/migrations, cloud sync semantics.
4. **Reconcile authoritative product decisions:** especially AI entitlement, automated SMS flavor/consent boundary, cloud backup guarantees and deployment scope.
5. **Complete traceability and historical migration:** refresh registry only after source/test re-review.
6. **Final audit:** coverage report, open risk register, CI/device/deployment evidence and documentation-only commit.

## Verification policy

A source path is not a test pass. A test file is not a passing test. A passing CI job is not a physical-device test. A function in source is not a deployed function. A successful Hosting build is not a deployed website.

## Change boundary

This phase created documentation only. No Flutter, Dart, Kotlin, TypeScript, Gradle, Firebase configuration, dependency manifest, lockfile, schema or infrastructure source was changed. The CI failures are recorded as findings, not fixed in this documentation-only pass.
