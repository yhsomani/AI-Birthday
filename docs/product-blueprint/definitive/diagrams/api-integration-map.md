# API and Integration Map

**Diagram type:** Mermaid · **Status:** target blueprint view · **Authority:** supporting diagram; the linked specification remains authoritative.

```mermaid
flowchart LR
  APP[Client]
  subgraph account_apis["Account APIs"]
    AUTH[Identity sign-in refresh revoke]
    VERIFY[Purchase verification]
    ENT[Server-authoritative entitlement]
    STORE[App store]
  end
  subgraph continuity_apis["Continuity APIs"]
    BACKUP[Backup part upload and publish]
    RESTORE[Manifest and generation restore]
    DELETE[Deletion receipt and status]
  end
  subgraph provider_apis["Provider APIs"]
    GEM[User Gemini API]
    NANO[Native on-device bridge]
  end
  subgraph device_integrations["Device integrations"]
    OS[Contacts permissions alarms intents]
    WA[WhatsApp Click-to-Chat]
    SMS[SMS composer]
    SHARE[Share or clipboard]
  end
  subgraph conditional["Conditional"]
    WEB[Public Hosting]
    CTRL[Autopilot callables]
  end
  APP --> AUTH
  APP --> VERIFY --> STORE
  VERIFY --> ENT
  ENT --> APP
  APP --> BACKUP
  APP --> RESTORE
  APP --> DELETE
  APP --> GEM
  APP --> NANO
  APP --> OS
  APP --> WA
  APP --> SMS
  APP --> SHARE
  WEB --> DELETE
  APP -. not integrated or approved .-> CTRL
  VERIFY -. exact versioned transport envelope .-> APP
  BACKUP -. owner derived from token .-> AUTH
  DELETE -. fences concurrent writes .-> BACKUP
  GEM -. entitlement first + secure key .-> ENT
  click VERIFY "../04-data-and-api-contracts.md" "API contracts"
  click CTRL "../05-security-operations.md" "Control boundaries"
```
