# ADR 0006: Encrypted Transactional Entity File Repository

Date: 2026-07-10

Status: Accepted under ADR 0005

## Context

The active React Native app normalized `AppState` into many Expo SecureStore values. That format is bounded and recoverable, but SecureStore is a protected key/value service rather than a database: entity count increases keychain reads, dirty changes still replace manifests, and there is no indexed pagination or multi-entity commit boundary.

The repository has no SQLCipher-capable React Native SQLite dependency. Expo SQLite by itself does not provide an encrypted-at-rest guarantee. Adding it without a reviewed SQLCipher/native encryption integration would move private relationship data from protected storage into plaintext database pages, journals, and temporary files.

## Decision

Keep storage behind the platform-independent interfaces in `src/domain/entityRepository.ts`. The selected rollout implementation is the versioned envelope-encrypted file adapter in `src/native/encryptedEntityStoreCore.ts`, composed with Expo filesystem and protected SecureStore adapters by `src/native/encryptedEntityStore.ts`. Production startup loads the verified migration facade from `src/application/createProductionRuntime.ts`; normalized SecureStore is read-only during migration and ceases to be authoritative after the protected commit checkpoint.

- Generate a random 256-bit AES master key and keep it only in verified platform-protected storage under a dedicated key. Never read or write a plaintext fallback.
- Encrypt every entity and singleton independently with AES-256-GCM and purpose-specific associated data. File names are opaque and contain no contact or entity identifiers.
- Encrypt manifests containing record references, indexes, archive state, counts, and checksums. Only fixed checkpoint filenames and generic envelope metadata remain visible at rest.
- Commit through two alternating encrypted checkpoint slots. A new checkpoint becomes authoritative only after dirty entity blobs and the next manifest have been written and verified. A torn slot leaves the other committed generation readable.
- Retain the immediately previous manifest and its referenced records as the rollback generation. Cleanup is post-commit and cannot invalidate a successful transaction.
- Write only declared dirty entity blobs. Reordering, counts, archive flags, and index changes update the encrypted manifest without rewriting unchanged entity ciphertext.
- Provide bounded cursor pages over encrypted-manifest indexes. A query decrypts only its returned records; reconstructing a complete `AppState` remains available for compatibility and backup workflows.
- Archive old activity and terminal message records before excluding them from default queries. Message history remains loadable and backup-visible. Permanent activity deletion is allowed only through an explicitly configured purge age for already archived activity.
- Migrate from current normalized SecureStore with protected phase checkpoints, read-only source access, count and SHA-256 verification, dual-read until commit, and repository-only writes. Do not delete the normalized rollback source as part of automatic migration.

## Tradeoffs

This adapter provides encrypted-at-rest storage, dirty writes, pagination, schema migrations, and crash-safe generation switching without a new native dependency. It is not a relational query engine. Each commit rewrites one encrypted manifest, indexed predicates are evaluated in memory after decrypting that manifest, file count scales with retained entities plus one rollback generation, and cross-process writers are not supported. Device filesystem durability still depends on Expo and OS semantics, which is why alternating checkpoints—not atomic rename assumptions—define the commit boundary.

A future SQLCipher-backed repository may replace this adapter behind the same domain interface if Android and iOS builds can prove encryption for database pages, WAL/journal files, migrations, key rotation, backup exclusion, and device recovery. Plain Expo SQLite is not an acceptable downgrade.

## Failure and Recovery Rules

1. Failure before checkpoint write leaves the previous slot authoritative.
2. A partial checkpoint overwrites only the slot older than the current commit; the other slot remains valid.
3. Authenticated decryption, ciphertext checksum, plaintext checksum, manifest count, and complete-state checksum must all verify before data is accepted.
4. If the newest generation is damaged, reads use the retained previous generation and report rollback recovery.
5. Lost or unavailable master-key protection fails closed. It never creates a new key over an existing unreadable repository.
6. Legacy normalized SecureStore remains untouched until a separate, explicit cleanup policy is approved after rollout evidence.

## Verification

- Interruption tests cover torn checkpoint writes and interrupted schema/source migrations.
- Corruption tests prove rollback preservation and authenticated record failure.
- Migration tests prove dual-read, single-write, protected checkpoints, count/checksum parity, and zero source mutations.
- Production composition tests prove startup migration, exact dirty-set commits, repository-only restore, and journal-resumed destructive clear with rollback pruning.
- Scale tests enforce page-read and one-dirty-entity write budgets on hundreds of records.
- At-rest tests reject private plaintext and master-key material in every file artifact.
