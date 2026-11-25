# AI-Birthday Product Blueprint 2.0 — Master Index

**ID:** BP2-MASTER-001 · **Baseline:** `main` commit `593f7800` plus this documentation-only worktree · **Date:** 2026-11-25 · **Status:** DA — proposed approval baseline; not an implementation or release authorization.

## Authority and scope

This 2.0 hierarchy is the authoritative index for this product-discovery exercise. It does **not** silently approve unresolved decisions or overwrite historical evidence. The detailed feature contracts in [`../../definitive`](../../definitive/) remain supporting specification evidence; where current source and those documents conflict, register the conflict here until approval and preserve both baselines.

Evidence statuses: `VF` verified fact, `EPD` existing project decision, `IR` implementation reality, `ER` evidence-based recommendation, `PC` proposed capability, `UA` unresolved assumption, `DA` decision awaiting approval, `DR` deprecated/rejected, `CF` conflicting finding.

Because the parent and definitive blueprints reused some IDs with different meanings, use the [ID alias register](id-alias-register.md) when tracing ambiguous `DOM/JRN` identifiers across baselines.

## Product identified

**AI-Birthday** is an Android, local-first personal birthday assistant. Its intended value loop is: **remember a person → calculate the occurrence → plan and remind → prepare manually or with entitled AI → review → user-controlled handoff → explicit user confirmation → truthful history**. AI and cloud services support the workflow; they are not the product. (`EPD`, high confidence; SSOT, README, definitive product definition.)

## Read in order

1. Governance: [conventions](documentation-conventions.md), [revision history](revision-history.md), [sources](source-register.md)
2. Discovery: [repository assessment](../02-discovery/repository-assessment.md), [implementation assessment](../02-discovery/implementation-assessment.md), [gaps](../02-discovery/gaps-and-questions.md)
3. Product: [vision](../03-product-strategy/vision-and-mission.md), [PRD](../03-product-strategy/product-requirements-document.md), [scope](../03-product-strategy/scope-and-non-goals.md)
4. Experience: [roles](../04-users-and-experience/personas-and-roles.md), [permissions](../04-users-and-experience/permissions-matrix.md), [journeys](../04-users-and-experience/user-journeys.md), [accessibility](../04-users-and-experience/accessibility-requirements.md)
5. Domains/features: [domain catalog](../05-domains/domain-catalog.md), [feature register](../05-domains/feature-register.md)
6. Architecture: [system](../06-architecture/system-architecture.md), [frontend](../06-architecture/frontend-architecture.md), [backend](../06-architecture/backend-architecture.md), [data](../06-architecture/data-architecture.md), [API](../06-architecture/api-architecture.md), [security](../06-architecture/security-architecture.md), [reliability](../06-architecture/performance-and-reliability.md), [operations](../06-architecture/infrastructure-and-devops.md)
7. Quality: [testing](../07-quality/testing-strategy.md), [gates](../07-quality/quality-gates.md)
8. Roadmap: [roadmap](../08-roadmap/product-roadmap.md)
9. Registers: [requirements](../09-registers/requirements-register.md), [features](../09-registers/feature-register.md), [rules](../09-registers/business-rules-register.md), [dependencies](../09-registers/dependency-register.md), [decisions](../09-registers/decision-register.md), [risks](../09-registers/risk-register.md), [assumptions](../09-registers/assumptions-register.md), [traceability](../09-registers/traceability-matrix.md), [completeness](completeness-register.md)
10. Diagrams: [master](../10-diagrams/master-product-map.md), [technical](../10-diagrams/technical-map.md), [roadmap](../10-diagrams/roadmap-map.md)
11. Migration: [legacy map](../11-migration/legacy-documentation-map.md), [preservation](../11-migration/preservation-checklist.md)
12. Audit: [specialist review](../12-audit/specialist-review.md), [final audit](../12-audit/final-audit-report.md)

## Change boundary

Documentation only. No application source, schema, dependency, runtime configuration, infrastructure, or deployment was changed. No files were deleted. Commit/push authorization was not supplied. CI was not rerun because this is a documentation-only workstream; status is separately verified in the final audit.
