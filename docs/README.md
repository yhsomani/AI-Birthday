# RelateAI Documentation

## Product authority

- `../SSOT.md` is the only normative business and end-user product scope.
- `product-reset/README.md` indexes the complete zero-based assessment and requested deliverables.
- `product-reset/product-vision-and-roadmap.md` contains the evidence-gated roadmap and build-from-scratch strategy.

The previous `feature-fssot.md` and `feature-roadmap-analysis.md` are retained only as historical scope evidence. They must not be used to authorize new features.

## Current implementation evidence

- `react-native-migration-status.md` records what the current React Native capability laboratory implements and what remains externally unverified.
- ADR 0005 is the active React Native replacement architecture decision. The proposed v2 architecture must supersede it explicitly if the reset proceeds.
- `architecture/adr/` records technical decisions. ADRs cannot expand product scope.
- `operations/release-checklist.md` records release procedures.
- `security/privacy-and-permissions.md` records privacy and permission controls.
- The root `README.md` explains how to run and validate the current implementation.

## Documentation rules

- Product decisions belong in `SSOT.md` with evidence, owner, and date.
- User research belongs in dated research artifacts linked from `product-reset/`.
- Current architectural decisions belong in ADRs.
- Operational procedures belong in runbooks, not product specifications.
- Tests and generated evidence prove implementation behavior, not market demand.
- Superseded feature lists, progress logs, and audits should be archived or deleted after the product owner completes the history-retention review; they must not become competing sources of truth.

The current source tree intentionally has no design-system specification. Figma work should start from the normative journeys and information architecture in `../SSOT.md`, using the product-reset documents as supporting evidence only where consistent, not from the temporary command console.
