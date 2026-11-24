# AI-Birthday — Definitive Product Blueprint

**Blueprint ID:** BP-MASTER-001 · **Version:** 1.0 · **Review date:** 2026-11-24  
**Status:** Consolidated definition/architecture/planning baseline. Stakeholder approvals, research, migration evidence and runtime verification remain explicitly open.

> This is the centralized product blueprint. It defines the intended product independently of the implementation. It does not authorize app, schema, dependency, configuration, infrastructure or deployment changes.

## Read in this order

1. [01 — Product definition and strategy](01-product-definition.md)
2. [02 — Product/module/feature specifications](02-feature-specifications.md)
3. [06 — User experience and journeys](06-user-experience.md)
4. [03 — Technical architecture](03-technical-architecture.md)
5. [04 — Data, database and API contracts](04-data-and-api-contracts.md)
6. [05 — Security, privacy and operations](05-security-operations.md)
7. [07 — Quality and verification](07-quality-testing.md)
8. [08 — Dependency-aware roadmap](08-roadmap.md)
9. [12 — Governance and traceability registers](12-governance-registers.md)
10. [09 — Completeness report](09-completeness-report.md)
11. [11 — Documentation migration and audit](11-documentation-audit.md)
12. [13 — v1 production-readiness decision](13-v1-production-readiness.md)

## Diagrams

- [Master product mind map](diagrams/master-product-map.mmd)
- [Domain ecosystem map](diagrams/domain-ecosystem-map.mmd)
- [People and birthday domain map](diagrams/people-birthday-map.mmd)
- [Studio, AI and delivery domain map](diagrams/studio-ai-delivery-map.mmd)
- [Account, continuity and privacy domain map](diagrams/account-continuity-map.mmd)
- [Experience and journey map](diagrams/ux-journey-map.mmd)
- [Technical architecture and security map](diagrams/technical-system-map.mmd)
- [Data relationship map](diagrams/data-relationship-map.mmd)
- [API/integration map](diagrams/api-integration-map.mmd)
- [Quality/release map](diagrams/quality-release-map.mmd)
- [Roadmap map](diagrams/roadmap-map.mmd)

## Relationship to prior artifacts

The parent [`docs/product-blueprint`](../README.md), root `SSOT.md`, `ARCHITECTURE.md`, `README.md`, `DESIGN.md`, Phase audits and `docs/knowledge/**` remain valuable evidence and historical decision material. The definitive directory consolidates the cross-product definition; it does not silently rewrite those baselines or claim their implementation status.

Authority/evidence labels:
- **APPROVED PRODUCT RULE:** explicit owner-approved intent.
- **RECOMMENDATION:** evidence-based target, not requirement approval.
- **FACT—REPOSITORY:** visible at stated inspected baseline.
- **OBSERVED:** test/deployment/device result in named environment.
- **OPEN/BLOCKED:** requires owner/evidence.
- **NOT VERIFIED:** cannot be concluded from current evidence.

Implementation language such as “current source”, “existing function”, or “CI passed” is evidence only. Deployment requires an environment/release record; feature presence requires tested behavior.

## Stable identifier policy

- `DOM-###`: domain; `F-###`: feature; `JRN-###`: journey.
- `REQ-###`: consolidated requirement; parent `PRD/FR/NFR/SEC/TST` IDs remain valid detailed links.
- `BR-###`: business rule; `DEC-###`: decision; `DEP-###`: dependency.
- `RSK-###`: risk; `ASM-###`: assumption; `TST-*`: test family.
- IDs are never reused. Retired entries name their replacement.

## Governance status

The blueprint is intentionally transparent about incomplete evidence. It is ready for stakeholder approval and decomposition, not a claim that every capability has been implemented, deployed or verified. See [09 — Completeness report](09-completeness-report.md) for genuine gaps.

## Revision history

| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-11-24 | Consolidated complete product, feature, journey, architecture, API/data, security, operations, quality, roadmap, diagrams, migration audit and governance registers. No application/configuration/schema/infrastructure changes or legacy deletions. |
