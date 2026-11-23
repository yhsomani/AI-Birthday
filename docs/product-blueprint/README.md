# AI-Birthday — Product Blueprint

**Blueprint version:** 0.1.0  
**Status:** Discovery baseline — not yet stakeholder-approved or implementation-ready  
**Review date:** 2026-11-23  
**Repository baseline:** `main` at `0116e1e2c6d63ac04351fc6be44f8291cb98d00c`  
**Scope:** Product discovery, product definition, requirements, architecture, UX, governance and planning. No application implementation is authorized by this blueprint.

> This directory is the proposed product-definition source of truth. It deliberately separates intended product behavior from source-code evidence and historical documentation. It does not claim that every product decision has been validated or that every feature has been fully specified.

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

## Authority model

1. **Approved product decisions** in this directory govern intended behavior once explicitly approved.
2. **Current source code** is evidence of what is implemented, not proof that it is correct or intended.
3. **Tests and CI** are evidence only for the assertions and environments actually exercised.
4. **Deployment evidence** is required before describing a backend or public surface as live.
5. **Legacy documents** remain historical evidence until their claims are revalidated and migrated.

## Evidence labels

- **FACT — repository:** directly visible in the inspected repository at the stated baseline.
- **FACT — prior decision:** an explicit user decision retained from prior project work; must be reconciled into this blueprint.
- **OBSERVED:** a test, CI run, deployed environment or device behavior was actually observed.
- **RECOMMENDATION:** evidence-informed proposed direction, not an approved requirement.
- **ASSUMPTION:** temporary premise required to continue analysis.
- **OPEN:** unresolved question requiring evidence or stakeholder decision.
- **NOT VERIFIED:** cannot be concluded from static repository inspection.

## Baseline findings

- The repository is a public Flutter/Dart application with a native Android Kotlin layer, Firebase Functions and a Firebase Hosting package.
- The root includes README, PROJECT, SSOT, ARCHITECTURE and DESIGN documents; `docs/` includes knowledge, audit, archive, UI/UX and purchase-setup material.
- The repository's knowledge portal declares a 50-node feature registry last reviewed 2026-10-14. It is a source-review snapshot, not runtime proof and must be refreshed against this baseline.
- The backend README explicitly says the Birthday Autopilot coordination control plane is not deployed. Hosting also has external release gates and no evidenced production deployment.
- The repository contains conflicting AI entitlement statements. The project decision carried forward here is that an active application subscription is required before **any** AI provider is used, including BYOK and on-device Nano. The current SSOT/router documentation must be reconciled against this decision.
- The existing data architecture describes local-first SQLite and opt-in Firestore backup. The backup must not be described as end-to-end encrypted unless that property is implemented and independently verified.

## Completion policy

This blueprint is not complete merely because the index exists. Each requirement must have an ID, owner, status, rationale, dependencies, acceptance criteria and test mapping. Unverified areas remain visibly open. No legacy file may be deleted until its information has been migrated and checked.

## Change control

Every approved change must update:
- the relevant domain specification;
- requirement and business-rule registers;
- dependency and decision registers;
- traceability and test mapping;
- relevant Mermaid maps;
- this index and revision history.

## Revision history

| Version | Date | Change |
|---|---|---|
| 0.1.0 | 2026-11-23 | Initial discovery baseline created from repository and prior explicit product decisions. Specialist source review, domain validation, competitor research and full requirement decomposition remain in progress. |
