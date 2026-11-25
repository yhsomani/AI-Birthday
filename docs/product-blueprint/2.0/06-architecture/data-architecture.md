# Data Architecture

## Selection and ownership

SQLite relational storage is an appropriate current local store for transaction/query/migration needs (`ER`); it is not a claim that Drift must remain forever. Firebase/Firestore is acceptable for server-verified entitlement and opt-in owner backup, subject to residency/privacy/retention approval.

## Conceptual model

Person 1→N BirthdayOccurrence (target: per cycle), BirthdayOccurrence 1→N DraftRevision, active revision pointer, DraftRevision 1→N DeliveryEvent, Person 1→N ReminderIntent; Job references subject/operation without secret/raw prompt; Entitlement and BackupGeneration are account/continuity records. Delivery events are append-only.

## Current defects requiring ADR/migration

- Schema v6 has no Person/Birthday FK or unique `(person_id, cycle_year)`.
- Stable birthday ID is overwritten each year; prior-cycle draft/delivery trace can be lost or ambiguously linked.
- `DateTime` lacks explicit zone/local-date components.
- v3 migration swallows failures and deletes birthday children for null-birthday People.
- Backup restores shallowly validate and omit delivery/jobs; generation integrity is count-based and generations unbounded.

Required lifecycle: classified/minimized fields, explicit local/cloud/provider copy, retention, archival/purge, versioned migration, preflight backup, forward recovery, restore verification and lineage. RPO/RTO are `DA`, not invented. Detailed data dictionary/contracts: [definitive/04](../../definitive/04-data-and-api-contracts.md).
