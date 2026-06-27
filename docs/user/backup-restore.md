# Backup and Restore

Last reviewed: 2026-06-27

RelateAI stores sensitive relationship, message, event, and configuration data locally. The live app database and exported backup files use separate encryption models.

## What the Backup Passphrase Does

- The passphrase encrypts exported backup files.
- The passphrase is required to preview or restore that backup later.
- RelateAI does not store the backup passphrase.
- Losing the passphrase means that backup file cannot be restored.

## What the Live Database Key Does

- The live Room database is encrypted with SQLCipher.
- Fresh installs generate random 256-bit database key material, store it in Keystore-backed encrypted preferences, and pass it to SQLCipher as a raw local key.
- Existing installs that have an encrypted database but no cached key may use the legacy identifier-derived key once as a migration/recovery path.
- The backup passphrase is not the live database key and cannot unlock the live database directly.
- If local database key material is lost, local database recovery is destructive unless the user has a valid encrypted backup.

## Backup-First Recovery Rule

Before risky operations, users should create a backup they can restore:

- Before signing out.
- Before changing devices.
- Before uninstalling or clearing app data.
- Before joining a production beta or migration build.
- Before importing a backup that replaces local data.

## Restore Limitations

- Restore is replace-only in the current product scope.
- Merge restore is not implemented.
- Local diagnostic snapshots from AI Doctor and HealthMonitor are not backup contents; replace restore clears them and the app rebuilds diagnostics from current state.
- Backup preview and manifest/checksum validation must succeed before local data is replaced.
- Future-version backups may be rejected until the app supports that version.
- Wrong-passphrase, checksum mismatch, malformed file, and oversized file errors must stop restore before mutation.

## What Sign-Out Deletes

Sign-out is destructive for local state:

- Scheduled workers and notifications are cancelled.
- Local Room tables and database files are cleared.
- Secure preferences, credentials, cached database key material, and auth state are cleared.
- Google/Firebase sign-out and Google access revocation are attempted after local cleanup.

## Release Validation

Before production release:

- Export, preview, wrong-passphrase failure, checksum mismatch failure, and replace import must be tested.
- Sensitive database files and secure preference files must remain excluded from Android auto backup.
- Logs and analytics exports must not include database keys, backup passphrases, OAuth tokens, API keys, SMTP credentials, raw AI responses, or message bodies outside explicit user export flows.
