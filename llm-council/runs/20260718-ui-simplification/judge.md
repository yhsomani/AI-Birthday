# UI Simplification Council — Judge Report

## Ranking and scores

Scoring weights: user value 20; removal/consolidation 20; safety and platform truth 20; accessibility/localization 15; feasibility 10; testability 10; rollback risk 5.

| Rank | Plan | Score | Rationale |
|---|---:|---:|---|
| 1 | Plan 1 | 91/100 | Best evidence-based audit, clearest mapping from every removal to a replacement path, strongest Android/iOS truth, and the most complete acceptance and rollback coverage. It identifies concrete production files and distinguishes routine UI from safety-critical state. It loses points because its initial characterization phase is broader than the first UI slice needs, some route extraction is proposed before proving it is necessary, and accessibility/localization are presented too late even though they must ship with each slice. |
| 2 | Plan 2 | 76/100 | The best atomic first slice: Settings/Help consolidation is small, high-confidence, independently reversible, and establishes replacement navigation before larger Home work. It also correctly keeps native contracts and fail-closed behavior intact. The remaining plan is too compressed to be implementation-ready, gives incomplete English/Hindi and accessibility gates, combines UI work with speculative response guards/state sequencing, and proposes putting message editing into Automation without a sufficient user-job rationale. |
| 3 | Plan 3 | 68/100 | Strong plain-language, accessibility, rendered-matrix, and one-primary-action principles. Its first slice combines too much Home and Settings work, most later tasks lack exact ownership and focused acceptance tests, and the proposal to feed production live components from an isolated fixture port conflicts with the requirement that production projections—not fixture substitutions—prove behavior. Virtualization and fixture retirement also broaden the task without evidence that they are needed for simplification. |

## Comparative judgment

Plan 1 is the primary source for the backlog because it most completely preserves the binding MVP while removing duplicated dashboards, internal terminology, competing actions, and misplaced support detail. Plan 2 contributes the first implementation slice because Settings/Help consolidation has the lowest platform and data risk and makes the later Home simplification easier to verify. Plan 3 contributes its visible-heading focus, 200% text, non-color state, and rendered Android/iOS validation requirements.

No plan is accepted wholesale. The final synthesis keeps work presentation-only until a narrowly named interface gap is proven, puts tests/localization/accessibility into the same slice as each UI change, and separates high-risk composer orchestration from low-risk information-architecture cleanup.

## Material disagreements and decisions

- **First slice:** choose Plan 2's Settings/Help consolidation. Reject Plan 1's new-route phase and Plan 3's combined Home/Settings rewrite as the first change; both increase the initial blast radius before replacement paths are locked.
- **Characterization coverage:** retain focused navigation and no-mutation characterization in each slice, not a broad preliminary rewrite of Android/iOS workflow tests. High-risk iOS composer extraction still gets dedicated native-call-order and suppression coverage before movement.
- **Route splitting:** add a Composer Review leaf only because it is a time-sensitive Home/reminder job. Do not create Policy or Guided Setup routes merely for architectural symmetry; reuse an existing leaf when it already supports the user job and Back/deep-link semantics.
- **Technical detail:** blockers stay expanded in plain language; healthy heartbeats, coordination detail, reason codes, and support references move behind Status/Support details or Diagnostics. Unknown or stale state never becomes healthy because it is collapsed.
- **Accessibility and localization:** do not defer these to a final phase. Every changed string, row, review, focus transition, and empty/error state must land with English/Hindi parity and accessibility evidence.
- **Production evidence:** use real production LiveApp projections and native boundaries. Fixtures may supply synthetic visual states only through their existing isolated test boundary; they cannot replace mutable production behavior or prove safety.
- **iOS terminal outcomes:** `ComposerReportedSent` and `ComposerOutcomeUnknown` remain terminal for that occurrence. “Recovery” means truthful explanation/status refinement, never reopening or auto-retrying the same in-app composer action.

## Rejected ideas

- Feeding production live components from a fixture port or treating immutable smoke fixtures as proof of mutable behavior.
- Retiring fixture/prototype screens as part of this UI simplification; that is unrelated cleanup with avoidable rollback risk.
- Adding `useCompanionStatus.ts` or wrong-platform result guards without first proving a current production defect and separately reviewing the native/state contract impact.
- Combining Home, Settings, navigation, shared primitives, and state derivation in one first slice.
- Moving message editing inside Automation as a default information architecture; Message authoring is a shared planning job and must remain independently discoverable.
- Adding virtualization solely as a visual simplification. Preserve current search/list behavior unless measured 10,000-contact performance requires a separate performance task.
- Removing or hiding blockers, transfer states, test/activation review, simulator conflicts, privacy recovery, external-copy truth, or diagnostic export.
- Moving a privacy operation to another screen if that makes it unavailable from Privacy. A contextual entry may be added, but the authoritative privacy/data-control path remains discoverable.
- Letting a raw readiness code, localization key, Claim/Arm/CAS term, or healthy internal metric dominate a routine screen.

## Synthesis decisions

1. Preserve exactly Home, People, Settings; Activity originates from Home, and setup, repair, review, privacy, legal, diagnostics, and destructive controls remain task/detail flows.
2. Ship pruning before large component extraction. Each removed surface names its replacement and is covered by a route-reachability assertion.
3. Keep native methods, ports, revisions, nonces, reservations, approval hashes, duplicate fences, deletion recovery, permissions, and data models unchanged unless a later, separately reviewed task proves an interface gap.
4. Use one contextual primary action per screen/state, with blocking facts visible and healthy support detail progressively disclosed.
5. Preserve exact Android “Sending from this phone” / “Sent from this phone; delivery not confirmed” truth and iOS foreground MessageUI/unknown-final-payload truth.
6. Validate every slice with focused static/component tests plus rendered Android and iOS inspection; unavailable physical, carrier, external-service, or release-gate evidence is recorded as unavailable, never inferred.
