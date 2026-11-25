# AI-Birthday — complete project mind map

[Knowledge home](README.md) · [Machine-readable interconnected graph](master-project-graph.json) · [Cross-dependency graph](cross-dependency-graph.md) · [Feature registry](feature-registry.json)

**Evidence snapshot:** `20a598e9789a19e2a35a2b693e63c098473cc68d` (2026-11-25 checkout). The machine graph is the complete, stable-ID node and edge source: it imports all 82 feature-registry nodes, defines 187 additional project nodes, and records 236 explicit cross-system edges plus 81 feature-parent edges. This Mermaid view is the readable map of the same ecosystem; inventories linked below carry the complete field-level detail. It describes source presence, not a production deployment claim.

## Master ecosystem map

```mermaid
flowchart LR
  ROOT(("COMPLETE PROJECT ECOSYSTEM<br/>AI-Birthday"))

  ROOT --> A01["01 Project overview"]
  ROOT --> A02["02 Personas and roles"]
  ROOT --> A03["03 User journeys"]
  ROOT --> A04["04 Feature hierarchy"]
  ROOT --> A05["05 Screens and UI"]
  ROOT --> A06["06 Actions and interactions"]
  ROOT --> A07["07 Navigation"]
  ROOT --> A08["08 Business rules"]
  ROOT --> A09["09 Frontend architecture"]
  ROOT --> A10["10 Backend architecture"]
  ROOT --> A11["11 API ecosystem"]
  ROOT --> A12["12 Data architecture"]
  ROOT --> A13["13 Data flows"]
  ROOT --> A14["14 States and lifecycle"]
  ROOT --> A15["15 External integrations"]
  ROOT --> A16["16 Security and permissions"]
  ROOT --> A17["17 Errors and recovery"]
  ROOT --> A18["18 Testing and quality"]
  ROOT --> A19["19 Infrastructure and deployment"]
  ROOT --> A20["20 Gaps and technical debt"]

  A01 --> P["Flutter local-first Android app<br/>+ separate Hosting + Firebase backend"]
  A02 --> R1["ROLE-001 local app user"]
  A02 --> R2["ROLE-002 authenticated account owner"]
  A02 --> R3["ROLE-003 public-site visitor"]
  A02 --> R4["ROLE-004 backend service identity"]
  A03 --> J1["J-01 first launch"]
  A03 --> J2["J-02 manage people"]
  A03 --> J3["J-03 birthday to reminder"]
  A03 --> J4["J-04 compose and hand off"]
  A03 --> J5["J-05 backup and restore"]
  A03 --> J6["J-06 purchase and entitlement"]
  A03 --> J7["J-07 public deletion"]
  A04 --> FEATURES["APP → 82 recursively identified features<br/>parented in feature-registry.json"]
  A05 --> SCREENS["SCR-001..009 mobile<br/>SCR-010..014 public Hosting"]
  A06 --> ACTIONS["ACT-001..030<br/>trigger → validation → effect → state"]
  A07 --> ROUTES["10 declared Flutter route patterns<br/>4 shell destinations; Settings is a pushed route"]
  A08 --> RULES["BR-001..025<br/>validation, recurrence, entitlement, retry, privacy"]
  A09 --> FRONT["Flutter / Riverpod / GoRouter<br/>Drift repositories, providers and platform adapters"]
  A10 --> BACK["Functions handlers → services → persistence<br/>Hosting client → deletion orchestration"]
  A11 --> APIS["API-001..020<br/>18 callables + 2 scheduled exports"]
  A12 --> DATA["6 local SQLite tables<br/>+ 28 Firestore path templates"]
  A13 --> FLOWS["FLOW-001..008<br/>startup, people, reminder, AI, delivery, cloud, billing, deletion"]
  A14 --> STATES["STATE-001..007<br/>startup, onboarding, birthday, draft/job, reminder, cloud, sender"]
  A15 --> INTEGRATIONS["EXT-001..013<br/>Android, AI, Firebase, Play, handoff"]
  A16 --> SECURITY["Auth / App Check / ownership rules<br/>SMS and preparation default-disabled"]
  A17 --> ERRORS["ERR-001..008<br/>startup, validation, AI, delivery, cloud, SMS, Hosting"]
  A18 --> TESTS["72 Flutter + 14 Functions + 3 Hosting test files<br/>presence is not pass evidence"]
  A19 --> OPS["CI configuration; local Android + Hosting surfaces<br/>deployment and release remain unverified"]
  A20 --> GAPS["ISSUE-001..007<br/>integration, product-copy conflict, release and model gaps"]

  J1 --> A07
  J2 --> A05
  J3 --> A08
  J4 --> A09
  J5 --> A12
  J6 --> A11
  J7 --> A10
  ACTIONS --> FRONT
  FRONT --> APIS
  FRONT --> DATA
  APIS --> BACK
  BACK --> DATA
  DATA --> FLOWS
  FLOWS --> STATES
  STATES --> ERRORS
  TESTS -. evidence for .-> FEATURES
  SECURITY -. constrains .-> APIS
  OPS -. release boundary .-> GAPS
  BACK -. no Flutter sender caller found .-> GAPS

  classDef branch fill:#e8f4ff,stroke:#2476b9,color:#12324a;
  classDef journey fill:#e1efff,stroke:#3478c5,color:#173b63;
  classDef implemented fill:#e7f6ec,stroke:#318553,color:#153c27;
  classDef partial fill:#fff3cd,stroke:#be8b00,color:#4d3b00;
  classDef gap fill:#fbe4e6,stroke:#b3261e,color:#5a1511;
  classDef backend fill:#efe6ff,stroke:#7652a8,color:#34224d;
  classDef api fill:#fff0dd,stroke:#c16a16,color:#4f2c08;
  classDef data fill:#ddf4f1,stroke:#198a80,color:#10443f;
  classDef infra fill:#eceff1,stroke:#66727a,color:#263238;
  class ROOT branch;
  class A01,A02,A03,A04,A05,A06,A07,A08,A09,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19,A20 branch;
  class J1,J2,J3,J4,J5,J6,J7,R1,R2,R3 journey;
  class P,ROUTES,FRONT,ERRORS implemented;
  class FEATURES,SCREENS,ACTIONS,INTEGRATIONS,SECURITY,RULES partial;
  class BACK,APIS backend;
  class DATA,FLOWS,STATES data;
  class GAPS gap;
  class OPS,TESTS infra;
  class APIS api;
```

`R4` is a service identity, not a human persona. Areas are linked to their major subsystems above; the machine graph additionally contains all source-mapped features, screens, actions, APIs, services, tables, integrations, flows, states, failure paths, tests and issues with their specific evidence and cross-edge types.

## Visual legend

| Encoding | Meaning |
|---|---|
| Blue | Product branches, user journeys and personas |
| Green | Source-implemented capability or application surface |
| Yellow | Partial implementation or runtime/approval limitation |
| Red | Missing, conflicting or incomplete project capability |
| Purple | Backend and business execution |
| Orange | API boundary |
| Teal | Data, state and flow |
| Grey | Infrastructure and release boundary |
| Solid arrow | Containment or source-level dependency/sequence |
| Dotted arrow | Constraint, evidence relationship or known boundary |

## Companion views

- [Complete feature inventory](feature-registry.json) — 82 stable feature IDs, parent IDs, implementation status, source/test references and evidence limits.
- [Journey inventory](journeys/traceability.md) and [journey-to-test matrix](traceability-matrix.csv).
- [Screen and interaction inventory](screen-interaction-inventory.md) — 14 screens/pages and 30 grouped interaction patterns.
- [API and backend map](api-backend-map.md) — callable exports, scheduled functions, callers, security, services and data effects.
- [Database and data lineage](data-model/README.md) and [end-to-end flow map](data-flow-map.md).
- [Cross-dependency graph](cross-dependency-graph.md), [implementation gaps](gaps/README.md), and [evidence register](evidence-register.md).
- [Coverage and verification report](project-coverage-report.md) — inventory counts, runtime observations and limitations.
