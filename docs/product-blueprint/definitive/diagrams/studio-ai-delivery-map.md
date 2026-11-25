# Studio, AI, and Delivery Domain Map

**Diagram type:** Mermaid · **Status:** target blueprint view · **Authority:** supporting diagram; the linked specification remains authoritative.

```mermaid
mindmap
  root((Studio AI and Delivery domain))
    Studio
      Manual editor F-040
        Autosave
        Process restart
        Unsaved protection
      Draft revision F-041
        Base revision
        Stale result rejection
        Exact history
      Review readiness F-042
        Recipient
        Destination
        Body
        Language tone size
        Edits revoke review
    AI gating F-050
      Entitlement active
      Zero provider calls when locked
      Expiry and account switch
    Provider resolution F-051
      Valid user Gemini key
      Ready Nano
      Invalid key is error
      No app-owned cloud fallback
    AI operations F-052
      Generate
      Regenerate
      Shorten expand
      Tone translation variations
      Apply as new revision
    Prompt safety F-053
      Facts allow list
      Notes as data
      No invented facts
      Adversarial injection tests
    Nano F-054
      Available
      Downloadable downloading
      Busy unavailable error
      Unsupported device
    Credential F-055
      Secure storage
      Masked test replace remove
      Never log sync export
    Recovery F-056
      Cancel
      Timeout quota
      Preserve previous draft
      Stale discard
    Delivery
      WhatsApp F-060
        Valid E164
        Official Click-to-Chat
        Handoff only
      SMS copy share F-061
        Capability check
        Truthful action record
      Confirmation F-062
        Explicit yes no
        Unknown persists
        No resume inference
      History F-063
        Exact draft revision
        Append-only events
        Search filter
      Personal SMS F-064
        Conditional only
        Recipient revision approval
        Idempotency stale cancellation
    Rules
      BR-009 to BR-023
      BR-038 BR-039
      BR-042 to BR-045
    Tests
      Provider call count
      Prompt injection
      Stale result
      Kill before return
      App launch rejection
      No false sent
    Detail refs
      02 feature specifications
      03 architecture
      05 security
      07 quality
```
