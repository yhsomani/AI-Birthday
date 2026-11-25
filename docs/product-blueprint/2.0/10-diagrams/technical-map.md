# Technical Architecture Map

```mermaid
flowchart TB
  subgraph Client[Flutter local-first client]
    P[Presentation] --> A[Application use cases]
    A --> D[Domain policies]
    A --> DB[(SQLite)]
    A --> S[Secure storage]
    A --> J[Durable jobs]
    A --> N[Android bridge]
  end
  N --> OS[Permissions alarms contacts Nano]
  A --> AI[Gemini BYOK / Nano]
  A --> MSG[External messaging/share]
  A --> API[Authenticated backend]
  API --> AUTH[Firebase Identity]
  API --> PLAY[Google Play]
  API --> FS[(Owner backup / entitlement)]
  CP[In-scope Autopilot control plane] -. "separate service deployment" .-> API
  OPS[Restricted operations] --> API
  OBS[Privacy-safe observability] --> OPS
```

Authoritative architecture details: [system](../06-architecture/system-architecture.md), [security](../06-architecture/security-architecture.md), [API](../06-architecture/api-architecture.md).
