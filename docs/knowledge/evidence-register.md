# Evidence register and decision ledger

[Knowledge home](README.md) · [Master mind map](master-project-mindmap.md) · [Feature registry](feature-registry.json) · [Coverage report](project-coverage-report.md)

**Snapshot:** repository `HEAD` `20a598e9789a19e2a35a2b693e63c098473cc68d`, reviewed 2026-11-25. The inspected working tree was already dirty before this documentation phase. Findings describe the checked-in source plus explicitly noted runtime observations; they do not establish production deployment or user demand.

## Evidence and decision records

| ID | Classification | Finding | Evidence / source location | Confidence | Owner | Approval status | Related IDs |
|---|---|---|---|---|---|---|---|
| EV-001 | VF | Repository identity is `yhsomani/AI-Birthday`; review snapshot is HEAD `20a598e9789a19e2a35a2b693e63c098473cc68d`. There were 568 tracked files in the snapshot; local working-tree edits are separate from that commit. | Git repository metadata and tracked tree; [coverage report](project-coverage-report.md) | High | Repository maintainer (individual not identified) | Observed fact; no approval implied | ECOSYS-ROOT |
| EV-002 | IR | Flutter routes include onboarding, four shell branches, Settings, person form, and Studio patterns; Settings is not a fifth shell destination. | [router.dart](../../lib/app/router.dart), [app scaffold](../../lib/app/app_scaffold.dart), [navigation catalog](navigation/README.md) | High | App maintainers (individual not identified) | Source fact; no product approval implied | NAV; SCR-001..009; ACT-021..023 |
| EV-003 | IR | Drift declares six local tables at schema version 6. | [app_database.dart](../../lib/core/database/app_database.dart), [data model](data-model/README.md) | High | Data maintainers (individual not identified) | Source fact; migration coverage is not implied | DB-001..006 |
| EV-004 | IR | Functions source exports 18 callables and two scheduled functions; Hosting calls deletion start/receipt and Flutter calls purchase verification. No sender registration/claim/arm client path was found. | [Functions index](../../backend/functions/src/functions/index.ts), [API map](api-backend-map.md), Flutter providers and subscription source | High | Backend and app maintainers (individuals not identified) | Source fact; deployment/integration approval not evidenced | API-001..020; SVC-010; ISSUE-002 |
| EV-005 | IR | The feature registry has 82 stable feature nodes: 76 source-implemented, 3 partial and 3 not implemented. Those labels do not imply runtime success, deployment or completeness. | [feature registry](feature-registry.json), [validator](tools/validate_knowledge.py) | High | Documentation maintainer (individual not identified) | Inventory classification; no stakeholder approval implied | APP and all registry feature IDs |
| EV-006 | IR | Available checked-in test files comprise 72 Flutter `*_test.dart`, 14 Functions `*.test.ts`, and 3 Hosting `*.test.mjs` files. Presence alone does not establish passing or per-requirement coverage. | `test/`, `backend/functions/test/`, `backend/hosting/test/`; [verification](verification/README.md) | High | QA owner not identified | Suite execution not evidenced by file presence | TEST-001..005 |
| EV-007 | IR | Public Hosting preview rendered home/legal/support/deletion pages with missing release configuration; deletion actions remained disabled. This was a local preview, not production Hosting. | [Hosting implementation](../../backend/hosting/src/app.ts), [coverage report](project-coverage-report.md) | High for local preview | Hosting maintainer (individual not identified) | No legal-content or release approval evidenced | SCR-010..014; ACT-018..020; ISSUE-005 |
| EV-008 | IR | Android emulator evidence showed the app installed and onboarding displayed. Further shell-injected interactions were blocked by emulator permissions; later journeys were not validated through that route. | Prior local emulator observation recorded in [coverage report](project-coverage-report.md) | Medium | App/QA owner not identified | No device-test sign-off evidenced | SCR-001; J-01 |
| EV-009 | CF | Public Hosting copy describes Android automation and an iOS companion, while mobile automatic-SMS gates default off, the Play manifest excludes `SEND_SMS`, no tracked Flutter iOS target exists, and deployment is not evidenced. | [Hosting source](../../backend/hosting/src/app.ts), [capability gate](../../lib/core/config/product_capabilities.dart), [backend README](../../backend/README.md), [gap register](gaps/README.md) | High for source conflict | Product/legal and release owners not identified | Requires owner review; no copy or policy approval evidenced | ISSUE-001; ISSUE-004; ISSUE-005; ISSUE-006 |
| EV-010 | EPD | The current product baseline favors local-first birthday data, user-initiated cloud backup, entitlement-first AI routing and separate Android distribution variants. | [SSOT](../../SSOT.md), current application source, [product blueprint](../../docs/product-blueprint/definitive/README.md) | High for documented/source decision | Product owner not identified | Existing project decision; current-turn reapproval not inferred | PEOPLE; CLOUD; AI; BILL |
| EV-011 | DA | Whether automatic SMS is policy-eligible for any distribution variant, and whether it may be enabled, requires explicit policy, safety, platform and release-owner approval. | [SMS capability gate](../../lib/core/config/product_capabilities.dart), Android manifests, [gap register](gaps/README.md) | High that decision is unresolved | Product, legal/policy and release owners not identified | Awaiting approval; no activation implied | ACT-017; SEND-SMS; ISSUE-004 |
| EV-012 | DA | Backend deployment, production Firebase configuration, Play release, signed production artifact and live operational ownership are not established by this repository review. | [backend README](../../backend/README.md), [Hosting README](../../backend/hosting/README.md), [coverage report](project-coverage-report.md) | High that evidence is absent from reviewed material | Release/operator owner not identified | Awaiting independent operator evidence and authorization | API-001..020; ISSUE-005 |
| EV-013 | UA | Business model, monetization beyond source-defined entitlement, customer demand, supported locales, compliance scope, retention duration, support ownership, SLO/RPO/RTO, budget, staffing and delivery dates are not established by the available evidence. | No authoritative source found in the inspected repository material; see [coverage limitations](project-coverage-report.md) | High that the evidence gap exists; no claim about external reality | Product/business/legal/operations owners not identified | Stakeholder validation required | Product scope and release decisions |
| EV-014 | ER | Preserve local-first data boundaries and explicit user-controlled handoffs while resolving incomplete data ownership and release gates; do not infer server-side messaging from the coordination APIs. | Current source architecture, caller map, and [cross-dependency graph](cross-dependency-graph.md) | High architectural confidence | Product and architecture owners not identified | Recommendation, not a new approved product requirement | FLOW-004..008; ISSUE-002 |

## Evidence classification guide

| Code | Meaning in this package |
|---|---|
| `VF` | Verified fact supported by repository metadata or direct inspection. |
| `EPD` | Existing project decision in source or authoritative project documentation. |
| `IR` | Behavior or structure verified in implementation; runtime limitations are stated separately. |
| `ER` | Evidence-based recommendation, not an approved requirement. |
| `PC` | Proposed capability for stakeholder consideration. |
| `UA` | Unresolved assumption or evidence gap; not treated as fact. |
| `DA` | Consequential decision awaiting an identified approver. |
| `DR` | Deprecated or rejected capability/decision with rationale. |
| `CF` | Conflicting evidence that cannot currently be reconciled. |

## Status and confidence semantics

- The machine-readable feature registry uses `implemented`, `partially-implemented`, and `not-implemented` for source status. The graph additionally uses `broken`, `mocked`, `unreachable`, `deprecated`, and `unknown` where evidence warrants them.
- `HIGH` means direct source or local execution evidence supports the specific claim; it is not confidence in production readiness.
- `MEDIUM` means source mapped but runtime/deployment coverage is incomplete.
- `LOW` means indirect or incomplete evidence. No low-confidence capability is promoted to a verified feature.
- A test path in an inventory is evidence of a test file, not a passing test result. Current-turn documentation validation is reported separately.
- Owners are recorded as role-level gaps where the repository does not name a responsible person. No person, legal obligation, service target or stakeholder approval is inferred.

## Unresolved stakeholder questions

1. Who owns approval of public legal/support language and the Android/iOS capability claims?
2. Is an iOS companion an approved product scope, or should the public copy remain restricted to implemented targets?
3. Which app distribution variants, if any, are permitted to request and use direct-SMS capability?
4. Who owns Firebase/Hosting operations, deletion support, data retention and incident response?
5. Which jurisdictions, privacy requirements and retention rules apply?
6. What business model, entitlement limits, localization set and success outcomes are approved?

No external market, competitor, regulatory or customer research was performed for this source-reconstruction package. These require explicit sourcing and stakeholder validation before being represented as findings.
