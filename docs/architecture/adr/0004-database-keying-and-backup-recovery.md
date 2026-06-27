# ADR 0004: Database Keying and Backup Recovery

Date: 2026-06-27

Status: Accepted

## Context

The app stores relationship data, contact metadata, generated message drafts, sent history, backup data, API keys, SMTP credentials, and preferences. Current protection mechanisms include:

- SQLCipher-backed Room database via `SupportFactory` in `AppDatabase`.
- `DatabaseKeyDerivation`, which now generates random SQLCipher key material for fresh installs, stores the key material in Android Keystore-backed `EncryptedSharedPreferences`, formats random keys as SQLCipher raw-key literals at database open time, and keeps identifier-derived key computation only as a legacy recovery path when database files already exist but cached key material is missing.
- `EncryptedSharedPreferences` protected with an AndroidX Security `MasterKey`.
- A migration path that deletes legacy plaintext DB-key preferences.
- Legacy plaintext DB quarantine before opening the encrypted database.
- Manual encrypted backup/export/import using a user passphrase and AES-GCM in `BackupEncryption`.
- Android auto backup disabled or configured to exclude sensitive database and secure preference files.

Current keying is local-device protection. It is not a user-held recovery secret for the live database, and clearing cached key material on sign-out makes local recovery dependent on backup/export flows.

## Decision

The rebuild will explicitly separate three security concerns:

1. Live database encryption keying.
2. User-held backup encryption.
3. Sign-out and recovery lifecycle.

Live database:

- Continue using SQLCipher for local Room storage.
- Store live DB key material only in secure local storage protected by Android Keystore-backed `EncryptedSharedPreferences`, or a stronger direct Keystore wrapping equivalent.
- Do not derive the live DB key solely from stable device/app identifiers in the target implementation. Implemented 2026-06-27 for fresh installs: missing cached key material generates random 256-bit key material when no database files exist and passes it to SQLCipher as a raw-key literal.
- Existing database files with missing cached key material may still use the legacy identifier-derived key as a migration/recovery path; that path must remain documented as legacy and should be removed only with a tested rekey migration.
- Treat live DB key loss as destructive unless a validated backup restore is available.

Backups:

- Continue using user-entered passphrase encryption for exported backups.
- Backup encryption remains separate from the live SQLCipher key.
- Backup import must preview and validate manifest/checksum before mutating the database.
- Merge restore remains out of scope until a separate product decision approves it.

Sign-out:

- Sign-out must cancel workers, alarms, notifications, and volatile dispatch state before deleting local stores.
- Sign-out must clear cached DB key material, secure preferences, auth state, and local database files through one orchestrator.
- Settings UI and auth code must not duplicate destructive cleanup logic long term.

## Consequences

Positive:

- The threat model becomes explicit and testable.
- Backup passphrases are not confused with live DB keys.
- Sign-out and restore flows can be validated independently.

Costs:

- Key rotation and recovery need careful migration planning.
- Existing key derivation behavior must be migrated without data loss; legacy identifier-derived recovery currently avoids silently bricking existing encrypted databases when cached key prefs are missing.
- Automated tests need device or integration coverage for encrypted open, sign-out, and restore flows.

## Verification

The decision is implemented when:

- Security docs explain local DB keying, backup passphrases, sign-out deletion, and restore limitations.
- Tests verify backup exclusions, backup round trip, wrong-passphrase failure, sign-out cleanup, and encrypted database open.
- Release checks fail if sensitive stores become auto-backed or exported without explicit approval.
- Completed 2026-06-27 for fresh installs: the target live DB key strategy no longer depends only on stable device/app identifiers when no database files exist and no cached key is present; random keys are formatted as SQLCipher raw-key literals instead of arbitrary passphrase bytes.
- Remaining: add a tested SQLCipher rekey migration for legacy identifier-derived databases if product/security review requires eliminating legacy key material for existing installs.
