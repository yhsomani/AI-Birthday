# Product-Contract and Verification Gap Register

**Baseline:** main at 644571f68be024423376749fe8e50e1db02ff018  
**Status:** Open work register; this is not an approval record.

The blueprint defines product vision, 17 domains, 14 product requirements, 28 functional requirements, 12 non-functional requirements, 35 business rules, nine core journeys and a logical target architecture. It is a useful discovery baseline, not yet the exhaustive implementation-ready contract requested by the product mandate.

## Gap register

| ID | Area | Current evidence | Gap / required deliverable | Priority |
|---|---|---|---|---|
| GAP-001 | Atomic feature specification | Requirement baseline defines FR-001–FR-028 and a 20-field feature-contract template | Complete one contract for every module, submodule and atomic feature; include actor, permissions, pre/postconditions, controls, validation, states, errors, edge cases, dependencies and testable acceptance criteria | P0 |
| GAP-002 | Requirements traceability | Feature registry maps 50 historic nodes to source and some test paths | Map every accepted requirement and atomic feature to exact source symbols, exact test names/assertions, CI run and verification class; keep untested features visibly open | P0 |
| GAP-003 | Persona and market validation | Primary “birthday organizer” persona and jobs-to-be-done proposed | No customer interviews, competitor evidence, demand, willingness-to-pay, market sizing or commercial validation in the reviewed evidence | P1 |
| GAP-004 | Product approval | Vision, scope, pricing, supported platforms and analytics are mostly proposed/open | Record named decision owner, decision, date and approval evidence; do not treat proposed scope as committed | P0 |
| GAP-005 | Birthday date policy | Leap-day and timezone rules appear in source and tests | Explicitly approve non-leap-year treatment, timezone source, travel/timezone change behavior and daylight-saving semantics; use one policy across all surfaces | P0 |
| GAP-006 | Local data integrity | Six Drift tables, version 5, repositories and sync service are documented | Complete transaction, migration, delete/undo, job and restore tracing; specify referential integrity, rollback and conflict matrix per entity | P0 |
| GAP-007 | Backup vs sync semantics | Existing product and source docs describe opt-in snapshot generations | Decide whether the product offers backup/restore only or bidirectional multi-device sync; specify conflict, retention, restore merge and deletion guarantees | P1 |
| GAP-008 | AI entitlement | Router implementation, tests and Settings copy require active entitlement for BYOK and Nano | Reconcile stale comments in lib/features/ai/domain/ai_router.dart in a separately authorized code change; test current server entitlement freshness and revocation on device | P0 |
| GAP-009 | Stripe security | Flutter loads STRIPE_SECRET_KEY with String.fromEnvironment and calls secret-key Stripe APIs; Settings calls a local entitlement setter | Move all secret-key operations and entitlement decisions to a trusted backend, bind customer/session to authenticated UID, verify signed webhooks, remove production local-grant path and rotate any exposed secret | P0 |
| GAP-010 | Purchase verification | Google Play verifier and emulator/unit tests exist | Verify Play Console product/package/account binding, acknowledgement, restore, expiry/revoke and real sandbox purchase in the supported environment | P0 |
| GAP-011 | Native notification delivery | Kotlin alarm receiver and Flutter scheduling gateway exist; logic tests pass | Verify exact-alarm permissions, denial/revocation, reboot, app update, force stop, timezone changes and OEM-specific scheduling on a declared device matrix | P1 |
| GAP-012 | Contacts and SMS | Android contacts and flavor-specific SMS adapters exist | Verify permission denial, imported-data lifecycle, Play/Personal manifests, approved draft invalidation, stale expiry, duplicate-send protection and actual device behavior | P0 |
| GAP-013 | Delivery truth | Tests distinguish handoff from explicit user confirmation | Specify user-declared “not sent” and unknown outcome, duplicate handoff, app crash after external launch, channel-specific status and event retention | P1 |
| GAP-014 | Privacy and deletion | Firestore rules, deletion orchestration and Hosting contracts are present | Validate actual data inventory, backup generations, local-only data, logs, deletion receipts, retention periods and completed deletion in live controlled environment | P0 |
| GAP-015 | API specifications | Export inventory, transport schemas and partial dispatch/guard map exist | For every callable and scheduled function, document exact input/output/error schema, guard, App Check condition, secret, region, Firestore paths, side effects, idempotency, rate limits and caller | P0 |
| GAP-016 | Role model | Individual app user and external actors documented | Confirm if any support/admin/operator UI is in product scope; otherwise keep operational roles outside customer-facing application | P1 |
| GAP-017 | Responsive accessibility | Responsive and contrast tests exist; latest CI passes | Define supported minimum viewport, text scaling, TalkBack, keyboard, reduced motion and OS matrix; test all critical screens rather than a few sampled layouts | P1 |
| GAP-018 | Domain mind maps | Master product and target architecture Mermaid diagrams exist | Add domain mind maps for major domains and verify every diagram links to detailed specifications | P2 |
| GAP-019 | Operational readiness | CI and infrastructure source inspected | Define production environments, deployment ownership, backup/restore runbooks, incident response, monitoring, rollback and support SLA; deployment not confirmed | P0 |
| GAP-020 | Feature completeness | Source and tests exist across 15 app domains; source inventory covers 65 feature files | “Source present” and “test file present” are not completion statuses; validate each acceptance criterion on correct test tier and update state without artificial 100% claims | P0 |

## Critical production blockers

### P0 — Stripe secret and entitlement authority

Current source contains a Stripe secret key read via compile-time Dart environment and used by Flutter to authorize Stripe API calls. A Dart define is part of the built client and is not a secret store. Settings also contains a browser-check flow that invokes a local entitlement setter. This is not an acceptable production authority model for paid entitlement.

Required product/architecture outcome:

1. Flutter calls an authenticated backend endpoint using the signed-in account.
2. Backend creates checkout and portal sessions using a managed secret.
3. The backend binds Stripe customer and subscription state to a stable authenticated account identifier, not an untrusted email-only lookup.
4. Signed Stripe webhooks update server-authoritative entitlement.
5. The client unlocks AI only from a verified entitlement response/state.
6. The production build has no Stripe secret define or client secret operation.
7. Security review verifies secret scanning, customer ownership, replay/idempotency and revoked/expired state handling.

This is a documented requirement, not an implementation authorization. Do not ship or enable the current secret-key client path.

### P1 — stale AI source comments

The executable router and tests require entitlement before either provider, but comments at the top of the router still state BYOK can be used without Pro. Documentation conflicts in SSOT and ARCHITECTURE have been corrected in this blueprint revision. The stale Dart comments remain out of scope under the docs-only boundary and should be changed in a separately authorized code task.

## Current evidence labels

Use these labels consistently:

- **Source-present:** relevant implementation symbol exists.
- **Test-mapped:** a test file or named assertion is mapped.
- **CI-passed:** the mapped test executed and passed at a specific commit/run.
- **Emulator-verified:** behavior executed against a local emulator.
- **Device-verified:** behavior observed on a physical device with OS/device details.
- **Live-integration-verified:** external provider sandbox/live integration exercised.
- **Deployed-verified:** the expected deployed project/site and configuration inspected.
- **Approved:** stakeholder decision recorded with owner and date.

These labels are not interchangeable. A feature may have source and test coverage while remaining unverified on a device or in production.

## Mind-map coverage status

The master product map and target architecture diagram exist. Separate detailed mind maps for every major domain, API/data lifecycle and roadmap are not yet present in the reviewed product-blueprint directory. Keep this open rather than treating the two diagrams as satisfying the full mind-map deliverable.

## Phase 2.3 recommended sequence

1. Close P0 architecture/security defects and approval decisions before production release.
2. Complete domain-by-domain feature contracts starting with core data, birthday lifecycle, reminder, AI, delivery and account/data deletion.
3. Expand requirement-to-acceptance-to-source-to-test traceability.
4. Verify native functionality on physical Android devices and live service sandboxes.
5. Capture deployment evidence and operational runbooks.
6. Produce domain diagrams from accepted specifications, then run final cross-document and link validation.