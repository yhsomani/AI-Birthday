# Testing and Release Map

```mermaid
flowchart LR
  O[Objective] --> D[Domain]
  D --> F[Feature]
  F --> R[Requirement + rule]
  R --> A[Acceptance criterion]
  A --> T[Test scenario]
  T --> E[Evidence at commit/environment]
  E --> G[Quality gates]
  G --> REL{Release}
  REL -->|pass + approvals| DEPLOY[Named deployment + smoke]
  REL -->|gap/high risk| BLOCK[Blocked / exclude scope]
  DEPLOY --> POST[Observe, restore, incident, improve]
```

Each critical journey requires normal, boundary, negative, permission, timeout, dependency failure, concurrency, recovery, security and accessibility evidence. See [quality gates](../07-quality/quality-gates.md).
