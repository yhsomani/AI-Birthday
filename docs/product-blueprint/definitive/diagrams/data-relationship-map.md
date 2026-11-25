# Data Relationship Map

**Diagram type:** Mermaid · **Status:** target blueprint view · **Authority:** supporting diagram; the linked specification remains authoritative.

```mermaid
erDiagram
  ACCOUNT ||--o{ ENTITLEMENT_SNAPSHOT : owns
  ACCOUNT ||--o{ BACKUP_GENERATION : owns
  ACCOUNT ||--o{ DELETION_REQUEST : requests
  PERSON ||--o{ BIRTHDAY_OCCURRENCE : has
  BIRTHDAY_OCCURRENCE ||--o{ REMINDER_SCHEDULE : derives
  BIRTHDAY_OCCURRENCE ||--o{ DRAFT_REVISION : prepares
  DRAFT_REVISION ||--o{ DELIVERY_EVENT : references
  BIRTHDAY_OCCURRENCE ||--o{ AI_JOB : requests
  BACKUP_GENERATION ||--o{ BACKUP_MANIFEST_PART : contains
  PERSON {
    uuid id PK
    string display_name
    int birthday_month
    int birthday_day
    int birth_year
    string recipient_timezone
    string leap_day_policy
    bool auto_prepare
    timestamp version_time
    timestamp deleted_at
  }
  BIRTHDAY_OCCURRENCE {
    uuid id PK
    uuid person_id FK
    int cycle_year
    date resolved_date
    string local_timezone
    string status
  }
  DRAFT_REVISION {
    uuid id PK
    uuid occurrence_id FK
    int revision
    text body
    string review_state
    timestamp created_at
  }
  DELIVERY_EVENT {
    uuid id PK
    uuid draft_revision_id FK
    string channel
    string handoff_result
    string user_confirmation
    timestamp attempted_at
  }
  REMINDER_SCHEDULE {
    uuid id PK
    uuid occurrence_id FK
    timestamp due_at
    string schedule_key
    string os_state
  }
```

See [data and API contracts](../04-data-and-api-contracts.md) for entity ownership and field constraints.
