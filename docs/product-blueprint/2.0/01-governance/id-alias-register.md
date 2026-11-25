# Stable-ID and Alias Register

## Problem

The 2026-11-24 parent blueprint and definitive blueprint both use `DOM-*` and `JRN-*` ranges with different meanings. Silent ID reuse breaks traceability. This is a `CF` with high confidence.

## Governance rule

Freeze historical identifiers in their originating document. Do not infer equivalence by number. New work cites a namespace:

- `BP0-DOM-###`, `BP0-JRN-###`: parent `01–10` baseline.
- `BP1-DOM-###`, `BP1-JRN-###`, `BP1-F-###`: definitive v1.0 baseline.
- `BP2-DOM-###`, `BP2-FEAT-###`: this 2.0 reconciliation where a new identifier is introduced.
- Phase contract IDs remain qualified by document/phase.

For readability, 2.0 docs may use unqualified `DOM-01…14` only when they explicitly mean the definitive target catalog. Cross-document traceability must qualify ambiguous JRN IDs and must never “fix” history by renumbering.

## Priority alias work

| Collision example | Parent meaning | Definitive meaning | Treatment |
|---|---|---|---|
| `DOM-01` | recipient/People-oriented early taxonomy | entry, onboarding/navigation | qualify by namespace |
| `JRN-003` | reminder journey | import journey | BP0-JRN-003 vs BP1-JRN-003; add semantic alias only after validation |

Owner: Documentation governance. Approval: pending automated validator and owner sign-off.
