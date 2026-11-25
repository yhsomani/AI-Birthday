# Data and Security Map

```mermaid
flowchart LR
  subgraph Device[Device trust boundary]
    PII[Names dates contact facts drafts] --> SQLite[(Local SQLite)]
    Secrets[Gemini/auth tokens] --> Secure[Secure storage]
    Native[Notification/contacts/SMS state] --> Prefs[App-private native storage]
  end
  subgraph Provider[Provider boundaries]
    AI[AI provider]
    Msg[Messaging app]
  end
  subgraph Cloud[Cloud account boundary]
    Backup[(Owner backup generations)]
    Ent[(Server entitlement)]
    Ctrl[(Opaque coordination)]
  end
  SQLite -->|explicit opt-in, plaintext fields| Backup
  PII -->|only on AI request with disclosure| AI
  PII -->|reviewed handoff| Msg
  Play[Google Play truth] --> Ent
  Ops[Restricted service/operator] --> Ctrl
  Ops --> Ent
  Delete[Deletion fence] --> Backup
  Delete --> Ent
  Delete --> Ctrl
```

Security invariant: secrets never enter SQLite/backup/analytics. Current gap: full deletion/retention is not reconciled across SQLite, native storage, backup generations, jobs, drafts and delivery events. No diagram implies encryption, deployment or compliance without evidence.
