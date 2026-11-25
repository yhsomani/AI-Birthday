# Account, Continuity, and Privacy Domain Map

**Diagram type:** Mermaid · **Status:** target blueprint view · **Authority:** supporting diagram; the linked specification remains authoritative.

```mermaid
mindmap
  root((Account Continuity and Privacy domain))
    Identity
      Sign-in F-070
        Real provider account
        Token refresh revoke
        Secure session
      Sign-out F-071
        Purge session
        Preserve explained local scope
        Account switch isolation
    Entitlement
      Purchase restore F-080
        Store purchase stream
        Backend verification
        Exact transport contract
        Acknowledge by policy
      Expiring cache F-081
        Last verified evidence
        Checking stale state
        Never extend on failure
      Refresh revoke F-082
        Start sign-in expiry request
        Server store authority
        Mid-session block
    Continuity
      Backup generation F-090
        Explicit opt-in
        Exact category disclosure
        Integrity manifest
        Atomic current publish
      Restore F-091
        Full prevalidation
        Conflict plan
        No partial corruption
        No resurrection
      Export F-092
        Versioned archive
        Secrets excluded
        Legacy warnings
      Privacy consent F-093
        Contacts AI backup diagnostics
        Revocation behavior
      Deletion F-094
        Scope checklist
        Recent authentication
        Accepted processing complete
        Deletion fences
      Account distinction F-095
        Sign-out not delete
        Local cloud secret scopes
    Operations
      Idempotency and rate limit
      Owner derived from validated token
      Deletion receipt evidence
      PII-free telemetry
      Incident and recovery runbooks
    Security
      Cross-account denial
      Secret deny list
      Replay and tamper
      Corrupt restore
      Concurrent deletion writer
    Decisions
      Sync versus backup DEC-039
      Offline entitlement DEC-042
      Retention DEC-040
      Delete scope DEC-041
      DB encryption DEC-046
    Conditional
      Public Hosting
      Support deletion site
      Autopilot separate
    Detail refs
      04 data and API contracts
      05 security operations
      08 roadmap
      12 governance
```
