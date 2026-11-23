# Data Lifecycle and Cloud Sync

## Local-first data path

`lib/core/database/app_database.dart` declares the six local tables documented in [Database Data Dictionary](database-data-dictionary.md). The source comment states that SQLite is operationally primary and Firestore is a synchronization target.

## Recipient lifecycle

`lib/features/people/application/person_service.dart`:
- validates a draft before create/update;
- creates an ID and timestamped Person on create;
- applies updates to an existing Person;
- logs sanitized person identifiers/version;
- soft-deletes via store operation;
- restores by clearing the tombstone through the store;
- invokes an optional change callback after mutations.

Repository-level transaction and sync effects are not fully traced yet.

## Cloud sync transport

`lib/features/sync/data/cloud_sync_service.dart`:
- uses Firestore REST API endpoints;
- obtains/refreshes an ID token through an injected callback;
- derives the authenticated UID from signed-in auth state;
- scopes collection URLs beneath `users/{uid}/{collection}`;
- defines a Firestore commit batch limit of 500 writes;
- classifies failures into auth/config/sync/network categories and exposes retryability.

These are source facts. They do not prove that remote sync succeeds for a live account.

## Security boundary

`backend/firestore.rules` declares owner-scoped reads/writes for backup paths, server-only entitlement writes, and write fencing during account deletion. Rules in source are not deployment proof.

## Data lifecycle gaps

- Enumerate every local repository and map CRUD to each table.
- Verify backup opt-in toggle gates all remote operations.
- Trace immutable generation upload, manifest validation and restore ordering.
- Determine conflict resolution and behavior for partial uploads/downloads.
- Verify account deletion across local, remote backup, server coordination and hosting receipt surfaces.
- Confirm what happens to DeliveryEvents and Jobs during backup, restore, sign-out and account deletion.
- Add emulator coverage after backend dependencies are installable.
