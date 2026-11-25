# Capability Dependency Map

```mermaid
flowchart TD
  G[DOM-01 Entry] --> P[DOM-02 People]
  P --> B[DOM-03 Birthday]
  B --> D[DOM-04 Planning]
  B --> R[DOM-05 Reminders]
  B --> S[DOM-06 Studio]
  S --> A[DOM-07 AI]
  E[DOM-09 Entitlement] --> A
  S --> H[DOM-08 Delivery/History]
  P --> C[DOM-10 Continuity/Privacy]
  B --> C
  S --> C
  C --> O[DOM-12 Operations]
  R --> J[DOM-11 Jobs/Reliability]
  A --> J
  C --> J
  J --> O
  PUB[DOM-13 Public] -. separate approval .-> O
  CP[DOM-14 Autopilot Control Plane] -. "in-scope capability, separate deployment" .-> O
```

Hard prerequisites: Person validation → birthday identity → cross-view occurrence → reminders/studio. Entitlement/store truth → AI. Privacy/retention → backup/delete. Supported platform/release evidence → any production claim. Autopilot is optional and its server control plane must remain isolated; local tracking and manual delivery must work when it is unavailable.
