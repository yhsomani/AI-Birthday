# Database Data Dictionary

**Source:** `lib/core/database/app_database.dart`  
**Schema version:** 5  
**Database:** Drift / SQLite; local database is described in source as operationally primary.

## Tables and columns

### Persons
| Column | Type / nullability | Notes |
|---|---|---|
| id | text, primary key | Person identifier |
| name | text, required | Recipient name |
| birthdayMonth | integer, nullable | Birthday month |
| birthdayDay | integer, nullable | Birthday day |
| birthYear | integer, nullable | Optional birth year |
| phoneNumber | text, nullable | Contact phone |
| email | text, nullable | Contact email |
| relationship | text | Relationship category |
| relationshipCloseness | text | Closeness descriptor |
| preferredLanguage | text | Language preference |
| preferredTone | text | Message tone preference |
| importantFacts | text | Encoded recipient facts |
| notes | text, nullable | Notes |
| preferredDeliveryChannel | text | Preferred channel |
| timezone | text, nullable | Recipient timezone |
| autoPrepare | boolean, default false | Preparation preference |
| autoSendPolicy | text | Delivery policy |
| createdAt / updatedAt | datetime | Timestamps |
| version | integer | Sync version |
| deletedAt | datetime, nullable | Soft-delete tombstone |

### Birthdays
`id` (text primary key), `personId` (text), `cycleYear` (integer), `date` (datetime), `status` (text), `draftId` (text nullable), `createdAt` and `updatedAt` (datetime).

### MessageDrafts
`id` (text primary key), `birthdayId` (text), `personId` (text), `body` (text), `tone` (text), `length` (text), `status` (text), `providerType` (text), `variationIndex` (integer, default 0), `createdAt` and `updatedAt` (datetime).

### ReminderSettingsEntries
`key` (text primary key), `enabled` (boolean, default false), `kinds` (text), `quietHoursStartMinutes` (integer), `quietHoursEndMinutes` (integer).

### DeliveryEvents
`id` (text primary key), `birthdayId` (text), `channel` (text), `handedOffAt` (datetime). Source comments describe this as evidence of successful external-app launch, not proof the recipient received or read a message.

### Jobs
`id` (text primary key), `type` (text), `status` (text), `subjectId` (text), `payload` (text nullable), `attempts` (integer default 0), `maxAttempts` (integer default 1), `errorCode` (text nullable), `errorMessage` (text nullable), `resultRef` (text nullable), `createdAt` (datetime), `startedAt` / `finishedAt` / `nextRetryAt` (datetime nullable).

## Migration and indexes

- Schema version is 5.
- Fresh database creation calls `createAll()`.
- Upgrade below v2 creates birthdays, message drafts and reminder settings.
- Upgrade below v3 alters Persons and runs legacy data cleanup SQL inside a try/catch.
- Upgrade below v4 creates delivery events.
- Upgrade below v5 creates jobs.
- `beforeOpen` creates indexes for delivery event birthday ID, birthday person ID, draft birthday ID and job status.

## Important discrepancy

The source comment explicitly states only `Persons` currently has both `version` and `deletedAt`; Birthdays, MessageDrafts and ReminderSettingsEntries carry timestamps only. Do not describe all entities as having a uniform version/tombstone envelope.

## Still open

- Inspect generated Drift schema and compare it with these declarations.
- Inspect all Drift repositories and codecs for exact serialization and null/default semantics.
- Trace every create/update/delete/restore transaction.
- Verify sync inclusion/exclusion of DeliveryEvents and Jobs.
- Inspect migration fixtures and rollback/error behavior beyond the observed v4→v5 CI assertion.
