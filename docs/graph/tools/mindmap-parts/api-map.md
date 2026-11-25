Clients, callable exports, services and server data. The sender-coordination callables (API-001…API-011, API-014…API-017) have **no Flutter caller** in the inspected source; only API-012/API-013 (public Hosting) and API-018 (Flutter purchase verification) are called by a client. Details, schemas and error model: [ARCHITECTURE.md §8](ARCHITECTURE.md).

```mermaid
flowchart LR
  FL["Flutter app<br/>SubscriptionService"] -->|"Auth + App Check"| A18["API-018 verifyPurchase"]
  HS["Public Hosting site<br/>backend/hosting/src/app.ts"] -->|"recent Google auth + App Check"| A12["API-012 requestAccountDeletion"]
  HS -->|"signed-out App Check"| A13["API-013 accountDeletionReceipt"]
  NOCALL["No client path found"] -.-> SENDER["API-001..011, 014..017<br/>sender coordination callables"]
  SCHED["Scheduler every minute UTC"] --> A19["API-019 sweepDeletionDrains"]
  SCHED --> A20["API-020 sweepCoordinationOperations"]
  A18 --> SVC9["SubscriptionVerificationService"]
  SVC9 --> PLAY["Google Play subscription API"]
  SVC9 --> ENT[("users/{uid}/entitlement/status<br/>subscriptionPurchaseOwnership/{hash}")]
  A12 --> CP["SVC-010 ControlPlaneService"]
  A13 --> CP
  SENDER --> CP
  CP --> CPD[("accounts/{uid} tree, deletionTombstones,<br/>coordinationPresence, deletionReceipts")]
  A12 --> DEL["SVC-011 DeletionOrchestrator"]
  A19 --> DEL
  DEL --> CPD
  DEL --> ENT
  A20 --> COORD["CoordinationOperationOrchestrator"]
  COORD --> CPD
  FL2["Flutter CloudSyncService<br/>REST with ID token"] --> BK[("users/{uid}/backup, generations/*")]
  RULES["firestore.rules"] -.guards.-> BK
  RULES -.guards.-> ENT
```
