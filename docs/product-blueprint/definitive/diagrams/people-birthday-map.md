# People and Birthday Domain Map

**Diagram type:** Mermaid · **Status:** target blueprint view · **Authority:** supporting diagram; the linked specification remains authoritative.

```mermaid
mindmap
  root((People and Birthday domain))
    People module
      Manual create F-010
        Required name
        Required valid month day
        Optional year and contact
        Atomically create occurrence
      Edit F-011
        Dirty state
        Version conflict
        Preserve unrelated fields
      Delete and undo F-013
        Aggregate scope
        Tombstone and retention
        Stop active reminders jobs
      Imports F-014 F-015
        Device permission on demand
        CSV parse and preview
        Duplicate candidates
        Validate before save
      Relationship context F-018
        User approved facts
        Notes distinct from facts
        No inference
      Legacy repair F-019
        Needs birthday
        Invalid timezone
        Missing leap policy
        No fabricated occurrence
    Birthday module
      Resolver F-020
        Gregorian recurrence
        Recipient timezone
        Device local fallback
        DST and year boundary
      Durable cycle F-021
        Unique person cycle
        Historical identity
        Idempotent refresh
      Lifecycle F-022
        Preparation
        Review
        Handoff
        Confirmation unknown
      Leap-day F-023
        User Feb 28
        User Mar 1
        Never infer
      Timezone reconcile F-024
        Resume
        Timezone change
        Recipient local midnight
    Rules
      BR-001 to BR-006
      BR-036 and BR-037
      BR-040
    Data
      Person
      BirthdayOccurrence
      ReminderSchedule
      DraftRevision
      DeliveryEvent
    Permissions
      Local owner
      Account owner for cloud
      OS contact permission
    Integrations
      Contacts
      CSV export
      Backup restore
      Reminder scheduler
    Tests
      Round trip
      Transaction failure
      Leap DST boundary
      Parallel reconcile
      Legacy migration
      Restore resurrection
    Detail refs
      02 feature specifications
      04 data and API contracts
      06 journeys
      12 traceability
```
