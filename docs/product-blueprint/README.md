# AI-Birthday — Product Blueprint

**Blueprint version:** 0.1.5  
**Status:** Discovery baseline, Phase 2.1.1 Flutter audit, Phase 2.2 CI/documentation revalidation and Phase 2.3 People/Birthday contracts; product-wide atomic contracts, stakeholder decisions and live/device verification remain open — not stakeholder-approved or implementation-ready  
**Review date:** 2026-11-24  
**Source review baseline:** main at 93e803aee3064b046ac9b9093cfd35e4b1a0ae62; Phase 2.3 inspects application source at that commit and adds documentation only. Earlier audit reports retain their original evidence baselines  
**Scope:** Product discovery, product definition, requirements, architecture, UX, governance, planning and evidence-based implementation audit. No application implementation is authorized by this blueprint.

> This directory separates intended product behavior from source-code evidence and historical documentation. The initial blueprint is not a claim that every product decision, feature, integration or runtime behavior has been validated.

## Start here

| Need | Document |
|---|---|
| Product purpose, users, jobs, boundaries | [01 — Product discovery](01-product-discovery.md) |
| Domains, modules and capability map | [02 — Product scope and domain map](02-product-scope-domain-map.md) |
| Functional and non-functional requirements | [03 — Requirements baseline](03-requirements-baseline.md) |
| User journeys and interaction contracts | [04 — Experience and journeys](04-experience-and-journeys.md) |
| Business rules and lifecycle semantics | [05 — Business rules](05-business-rules.md) |
| Target system and data architecture | [06 — Technical architecture](06-technical-architecture.md) |
| Privacy, security and threat model | [07 — Security and privacy](07-security-and-privacy.md) |
| Test strategy and release gates | [08 — Quality and verification](08-quality-and-verification.md) |
| Dependency-aware delivery roadmap | [09 — Product roadmap](09-product-roadmap.md) |
| Decisions, assumptions, risks and traceability | [10 — Governance registers](10-governance-registers.md) |
| Product ecosystem mind map | [Master product map](diagrams/master-product-map.mmd) |
| Target architecture map | [Architecture map](diagrams/target-architecture.mmd) |
| Phase 2 audit index | [Phase 2 — Evidence-based repository audit](phase-2-audit/README.md) |
| Phase 2.1 source-to-test audit | [Phase 2.1 — Source-to-test audit](phase-2.1-audit/README.md) |
| Phase 2.2 revalidation and contract gaps | [Phase 2.2 — Current CI and documentation revalidation](phase-2.2-audit/README.md) |
| Phase 2.3 People and Birthday product contracts | [Phase 2.3 — People and Birthday contracts](phase-2.3-product-specification/README.md) |
| Phase 2.1.1 Flutter feature audit | [Flutter Feature Audit](phase-2.1-audit/flutter-feature-audit.md) |
| Flutter source inventory | [65 feature source files](phase-2.1-audit/flutter-source-inventory.md) |
| Flutter test inventory | [72 Dart test files](phase-2.1-audit/flutter-test-inventory.md) |

## Authority model

1. Approved product decisions govern intended behavior once explicitly approved.
2. Current source code is evidence of implementation, not proof that behavior is correct or intended.
3. Tests and CI are evidence only for the assertions and environments actually exercised.
4. Deployment evidence is required before describing a backend or public surface as live.
5. Legacy documents remain historical evidence until their claims are revalidated and migrated.

## Evidence labels

- **FACT — repository:** directly visible in the inspected repository at the stated baseline.
- **FACT — prior decision:** an explicit user decision retained from prior project work; must be reconciled into this blueprint.
- **OBSERVED:** a test, CI run, deployed environment or device behavior was actually observed.
- **RECOMMENDATION:** evidence-informed proposed direction, not an approved requirement.
- **ASSUMPTION:** temporary premise required to continue analysis.
- **OPEN:** unresolved question requiring evidence or stakeholder decision.
- **NOT VERIFIED:** cannot be concluded from static repository inspection.

## Baseline findings

- The Phase 2.3 source-review tree contains 447 file blobs and 201 directory entries (648 entries total), including 111 files beneath docs/. Counts are a snapshot at source baseline 93e803a; they are not a completeness metric. It includes Flutter/Dart, Android/Kotlin, Firebase Functions, Hosting, tests, assets and documentation.
- The Flutter application has 96 files under `lib/`; Android has 27 files; backend has 76 files; tests contain 84 Dart files, including 72 files named `*_test.dart`, plus 20 image artifacts.
- The knowledge portal contains a 50-node feature registry last reviewed 2026-10-14 against an older source snapshot. It is a source-review baseline, not runtime proof.
- The earlier Phase 2.1 CI baseline was red (459 Flutter tests passed and one 320px Calendar test failed; backend dependency installation failed). It is superseded by latest main-branch run 45245907678 on 644571f, which passed all three jobs: 460 Flutter tests; backend lint/build, 11 Vitest files and 2 Firestore-emulator files; Hosting typecheck/tests/build; both Android flavor APK builds; and the Play manifest check excluding SEND_SMS. See Phase 2.2 verification.
- The source-level AI router requires active entitlement before provider selection, and tests assert a free user is blocked even with a Gemini key. SSOT.md and root ARCHITECTURE.md were reconciled to that policy in revision 0.1.4. Stale comments inside lib/features/ai/domain/ai_router.dart remain an application-source documentation defect and were not changed.
- The local database declares six Drift tables at schema version 5. Only `Persons` has both `version` and `deletedAt`; other tables have different sync metadata. Documentation that describes a full version/tombstone envelope on all entities is inaccurate.
- Phase 2.3 source review found two People persistence adapters, non-atomic Person/Birthday writes, inconsistent import validation and constructor defaults, a CSV profile export that omits sync-envelope fields, and multiple timezone-aware vs timezone-free Birthday calculation entry points. See the Phase 2.3 contracts and traceability.
- The backend includes purchase verification and a distinct Birthday Autopilot coordination surface. The existence of functions does not prove deployment or mobile integration.
- Cloud backup is owner-scoped Firestore data. Do not describe it as end-to-end encrypted or zero-PII without implementation evidence.

## Completion policy

This blueprint is not complete merely because the index exists. Each requirement must have an ID, owner, status, rationale, dependencies, acceptance criteria and test mapping. Unverified areas remain visibly open. No legacy file may be deleted until its information has been migrated and checked.

## Change control

Every approved change must update the relevant domain specification, requirement and business-rule registers, dependency and decision registers, traceability and test mapping, relevant diagrams, this index and revision history.

## Revision history

| Version | Date | Change |
|---|---|---|
| 0.1.0 | 2026-11-23 | Initial discovery baseline created from repository and prior explicit product decisions. |
| 0.1.1 | 2026-11-23 | Added Phase 2 repository inventory, CI evidence, implementation map, discrepancy register and audit limitations. No application source/configuration changed. |
| 0.1.2 | 2026-11-23 | Added Phase 2.1 evidence baseline, feature/test mapping, database dictionary, API and platform inventories, security evidence and coverage gaps. |
| 0.1.3 | 2026-11-23 | Added Flutter feature audit for all 15 domains, 65 feature source files and 72 Dart test files. Recorded CI failure, fake-backed test limits, AI policy comment conflict and P0 Stripe architecture finding. No application files changed. |
| 0.1.4 | 2026-11-24 | Revalidated latest main CI; corrected SSOT/root architecture AI entitlement contradictions; added current CI evidence and explicit contract-gap register. No application source/configuration changed. |
| 0.1.5 | 2026-11-24 | Added source-backed People and Birthday lifecycle contracts with named source/test traceability, detailed gaps and open decisions. Updated governance and requirement links. No application source/configuration changed. |
