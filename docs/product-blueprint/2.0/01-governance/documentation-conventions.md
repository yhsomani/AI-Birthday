# Documentation Conventions

Every material requirement/decision must state: stable ID, classification, evidence/source, confidence, owner, approval, related IDs, and validation. Facts are anchored to a commit or named environment. Implementation is never treated as intent; intent is never treated as deployed behavior.

## Identifier policy

`DOM-###`, `MOD-###`, `FEAT-###`, `FR-###`, `NFR-###`, `BR-###`, `UJ-###`, `AC-###`, `TC-###`, `DEC-###`, `RSK-###`, `ASM-###`, `DEP-###`. IDs are immutable. Use “supersedes” and “superseded by”; never recycle.

## Status vocabulary

- `VF`: directly verified in repository or named system evidence.
- `EPD`: explicit pre-existing project decision.
- `IR`: current executable/operational behavior evidenced by code or tests.
- `ER`: recommendation derived from verified needs/standards/architecture.
- `PC`: optional capability awaiting consideration.
- `UA`: evidence gap.
- `DA`: consequential stakeholder approval required.
- `DR`: recommended retirement.
- `CF`: mutually inconsistent evidence.

## Requirement style

Normative language appears only in approved requirements. Proposed directions use “should” and carry `ER/PC`. A source path is evidence, not proof of deployment. A test file proves that assertion exists; a passing named run proves only that run at that commit/environment.
