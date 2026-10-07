# 04 — Data Integrity & Backup Audit

**Scope:** local Drift/SQLite schema & migrations, the full persistence cycle for people/birthdays/drafts/reminders, delete/restore/undo, cloud backup/restore, and Google sign-in + Firebase wiring.
**Mode:** read-only. No source under `lib/`, `test/`, `android/`, `ios/` was modified. Audit result lives in this file only.
**Claim labels:** `FACT` (verified in source/tests), `INFERENCE` (derived from code behavior), `UNKNOWN` (not verifiable from repo alone).
**Secret policy:** identifiers (API key, SHA-1, server client id, package names) are referenced by kind, not echoed in full.

---

## 1. Schema integrity (v3)

| Table | Envelope fields | Notes |
|---|---|---|
| `Persons` (21 cols) | `id`, `createdAt`, `updatedAt`, `version`, `deletedAt` | Only table with a full sync envelope. No UNIQUE/FK constraints. `version` is a plain integer, `deletedAt` nullable. |
| `Birthdays` | `createdAt`, `updatedAt` only | **No `version`, no `deletedAt`** — no tombstone. Related to a person via `personId` text column, no FK. |
| `MessageDrafts` | `createdAt`, `updatedAt` only | No envelope; keyed by `birthdayId`. |
| `ReminderSettingsEntries` | none | Keyed by text `key`; holds `enabled`, `kinds`, `quietHoursStartMinutes`, `quietHoursEndMinutes`. |

**Header contradiction (see §8, C-1):** `app_database.dart` lines 3–7 state *"Tables carry the shared sync envelope fields (`id`, `createdAt`, `updatedAt`, `version`, `deletedAt`)"* — only `Persons` actually does.

Claims marked: `FACT` (table defs + header comment read directly) · `INFERENCE` (no FK on `personId` means orphans are possible; see findings).

---

## 2. Migration strategy

- `schemaVersion => 3` (`FACT`).
- `onCreate: createAll()`; `onUpgrade from<2` creates the 3 newer tables; `from<3` runs birthday-fixup SQL; `beforeOpen` re-runs the same birthday-fixups on every launch (`FACT`, lines 111–131).
- Drift 2.31.0 executes `onCreate`/`onUpgrade` **before** `beforeOpen` (verified in pub-cache `drift-2.31.0/lib/src/runtime/api/db_base.dart`), so the `beforeOpen` fixups cannot crash during a first migration and are idempotent. Fixup duplication is harmless redundancy, not a data risk.
- No destructive migrations; no `DROP` paths found. `INFERENCE` — no negative-migration test exists to prove a downgrade path, which is fine for a local-first app.

---

## 3. Persistence cycle (people / birthdays / drafts / reminders)

### 3a. People — two parallel stacks write one table
- **Legacy stack**: `domain/person.dart` (`autoSendPolicy`, `deletedAt` on model), `data/person_repository.dart` (`DriftPeopleStore`), wired via `person_providers.dart`. Uses `DeliveryChannel {whatsapp,sms,none}` and `RelationshipCloseness {family,close,goodFriend,…}`.
- **New stack**: `domain/models/person.dart`, `core/database/drift_repositories.dart` (`DriftPeopleRepository`), wired via `app/providers.dart` for people_screen. Uses `DeliveryChannel {whatsapp,sms,clipboard,share}` and `RelationshipCloseness {close,casual,distant}`.
- Both stacks read/write the same `Persons` table. `FACT` — both were read in full.
- **Hardcoded write values (new repo, `drift_repositories.dart` 77–81):** `autoSendPolicy: 'manualOnly'` and `deletedAt: null` on every `savePerson`. Consequences:
  - Soft-deleted (tombstoned) rows are **resurrected** if any save path touches them (`INFERENCE`).
  - `autoSendPolicy` is never persisted from the UI model — always `manualOnly`.
- **Enum value drift on edit (`INFERENCE`, traced end-to-end):** legacy enum names `family`/`goodFriend` parse to fallback values in the new model, and new names `clipboard`/`share`/`casual` fall back on the legacy parse. Saving a person edited in the form can silently rewrite `relationshipCloseness` (`goodFriend`→`other`) or `preferredDeliveryChannel` (`clipboard`→`none`). This is a silent mutation of stored data on the edit path.

### 3b. Birthdays
- Cycle-based rows (`cycleYear`, `status`) created by `birthday_lifecycle_service.dart`; rollover clears `birthday.draftId` (`FACT`, lifecycle service).
- **Hard delete** via `birthdaysRepo.deleteBirthday` from the delete UI (`FACT`, people_screen 504). No tombstone → deleted birthdays are not synchronized as deletions, only as "absent."
- No FK: deleting a person does not cascade; orphaned birthday rows are possible if a path deletes the person but not the birthday (the UI delete path does delete both; other paths may not — `INFERENCE`).

### 3c. Message drafts
- Draft rows are reused across cycles (keyed by `birthdayId`); when the lifecycle clears `draftId` after a birthday, the **same draft row remains**, so next year the old draft body reloads (`INFERENCE`). Not a loss, but a staleness/UX risk.

### 3d. Reminder settings
- Persisted under key `'default'` with comma-joined `kinds`; unknown kind falls back to `birthday` (`FACT`, reminder service read). Reminder plans are recomputed on-device, not persisted (`FACT`).

Test evidence: `app_database_test.dart` (envelope columns round-trip, tombstone query, null-birthday rows), `person_repository_test.dart` (legacy version bumps: save=1, softDelete=2, restore=3).

---

## 4. Delete / restore / undo

**UI delete (`people_screen.dart` 502–508, `FACT`):**
1. `peopleRepo.deletePerson(id)` — new repo **soft-delete without a version bump** (updates `deletedAt`/`updatedAt` only).
2. `birthdaysRepo.deleteBirthday(id)` — **hard delete** of the associated birthday.
3. `peopleService.remove(id)` — legacy soft-delete with `version + 1`.

**Undo (`people_screen.dart` 518–525, `FACT`):** re-saves the person via the new repo (which writes `deletedAt: null` — resurrection) and calls legacy `restore` (version + 1).

Risks:
- The person gets **two competing tombstone mechanisms** (legacy store bump vs new repo no-bump) and the birthday is **irrecoverably hard-deleted**, so Undo restores the person but **cannot restore the birthday row** — the next lifecycle must regenerate it. `INFERENCE` (verified in code paths; no test covers birthday restoration in undo).
- Undo resurrects a row that cloud sync may have already seen as hard-deleted — see §5.
- `people_screen_delete_test.dart` covers the snackbar/Undo restoring the person (`FACT`).

---

## 5. Cloud backup / restore (`CloudSyncService`)

- **Mechanism: hand-rolled Firestore REST**, not the Firebase SDK. `pubspec.yaml` contains `drift`, `google_sign_in` — **no `firebase_core` / `firebase_auth` / `cloud_firestore`** (`FACT`). `grep` over `lib/` finds **no `Firebase.initializeApp`** anywhere — the app never initializes Firebase (`FACT`). All Firebase interaction is authenticated via the Google ID token + REST web API key.
- **Backup (sync):** uploads **all rows including soft-deleted** people (tombstones included, `cloud_sync_service.dart` 255), birthdays, drafts, reminder settings. Timestamp written only on full success (`FACT`; test asserts no timestamp when a write fails).
- **Restore:** merge-only, per-document — writes a doc only if local is absent **or** cloud `updatedAt` is newer (418–420, 485–487). It **never deletes local rows absent from cloud** and **never applies tombstones from cloud** (`FACT`; tombstone fields are read back at 423/464 into `deletedAt` but no delete propagation exists).
- **Resurrection-on-restore (`INFERENCE`):** if a person was hard-deleted (via the UI path, birthday hard-deleted) and a **stale cloud copy with a newer `updatedAt`** exists, restore re-writes it. Conversely a soft-deleted local person that the cloud never saw as deleted gets its `deletedAt` overwritten to null on restore → **deleted contacts reappear after restore**.
- **Deletion is not a first-class sync op:** deletions only propagate as "absent" from cloud, and restore ignores absence. Cloud and local can diverge indefinitely (`INFERENCE`).
- **Reminder settings restore preserves local** (`FACT`, cloud_sync_service_test 196–267): local `enabled/kinds/quietHours` win over cloud — a merge choice, but the inverse direction (cloud wins) has no counterpart, so settings are effectively one-way (local-primary).
- **Uid path fallback** (`cloud_sync_service.dart` 76): `firebaseUid ?? googleSubject`. If the IdP exchange fails, the uid may differ → backup lands under a different path than restore reads (`INFERENCE`).
- `firestore.rules` scope data to `users/{uid}/{people,birthdays,drafts,reminderSettings}` with server-only entitlement/backup paths (`FACT`). Plaintext fields per `ARCHITECTURE.md` §6 — **backup is NOT encrypted and NOT zero-PII** (`FACT`). Archive docs (IMPLEMENTATION_STATUS.md, TEST_READY.md) reference an "encrypted zero-PII envelope" — that describes test/archive-only code, not production (`FACT`).

---

## 6. Google sign-in + Firebase wiring

- **Auth:** `google_sign_in` (serverClientId set) → IdentityToolkit REST `signInWithIdp` → `securetoken` refresh. `reauth` failures surface as a "SHA-1 not registered" message (`FACT`, `live_google_auth_gateway.dart` + `auth_controller.dart`).
- **Gradle:** `android/app/build.gradle.kts` has **no** `com.google.gms.google-services` plugin; no declaration in `settings.gradle.kts` / root `build.gradle.kts`. So `google-services.json` is **not consumed by any Gradle plugin** (`FACT`). This is consistent with hand-rolled REST (no plugin needed), but it also means **no `google-services.json` validation, no automatic Android app-id injection, and no Firebase SDK init**.
- **google-services.json, two files (`FACT`):**
  - Repo root: 1 client (`com.aistudio.relateai.qxtjrk`).
  - `android/app/`: **2 clients** — `com.yashsomani.ai_birthday` and `com.aistudio.relateai.qxtjrk`, **same SHA-1 cert fingerprint** (`413e…8c392`).
- **Keytool verification blocked (`UNKNOWN`):** no `%USERPROFILE%\.android\debug.keystore` exists on this machine and no `android/key.properties` — the registered fingerprint cannot be verified against a local keystore here. README documents the SHA-1 setup step; follow §9 to verify on a machine with the release keystore.
- `initializeApp` question: **not called** — Firebase is used purely as a token-audience + REST datastore. This is intentional-but-fragile: REST auth depends on the web API key and ID-token acceptance; no offline caching layer beyond values already fetched.

---

## 7. Contradiction register

| ID | A says | B says | Evidence | Decision |
|---|---|---|---|---|
| C-1 | "Tables carry the shared sync envelope (`version`,`deletedAt`)" | Only `Persons` has them; `Birthdays`, `MessageDrafts` don't | `app_database.dart` 3–7 vs 51–83 | Doc is stale; envelope is per-table. Do not rely on uniform tombstones. |
| C-2 | Legacy + new stacks are equivalent models | Different enum value sets and fallbacks silently rewrite stored values on edit | `person.dart` vs `models/person.dart`; form save path traces | Known silent-mutation path; needs one shared model or explicit mappings. |
| C-3 | Delete removes data | UI delete hard-deletes birthday while soft-deleting person; cloud restore can re-add either | people_screen 502–508; cloud_sync merge logic | Delete intent is not durable across sync. |
| C-4 | "Encrypted zero-PII envelope" (archive/test docs) | Production backup is plaintext PII per ARCHITECTURE.md §6 | IMPLEMENTATION_STATUS.md / TEST_READY.md vs CLI sync code | Archive-only claim; flag in docs, keep out of threat model. |
| C-5 | SHA-1 registered vs local signing | No debug/release keystore on this machine; 2 clients share 1 fingerprint | keytool check, google-services.json | Cannot confirm locally — verify on signing machine. |

---

## 8. Findings (P0–P3)

### P0
- **None.** No path found that destroys data without a deletion intent or a sync restore.

### P1
- **F-1 Delete is not durable end-to-end.** Birthday hard-delete is irreversible (no tombstone) and Undo restores the person but not the birthday; cloud restore can resurrect a hard-deleted person from a stale copy with a newer `updatedAt`. Violates user expectation that "delete" stays deleted. (`INFERENCE`; code paths verified, behavior untested.)
- **F-2 Restore can resurrect soft-deleted contacts.** Merge-only restore writes `deletedAt: null` set against cloud copy; cloud-absent rows are never removed locally. (`FACT`/`INFERENCE`.)
- **F-3 Silent enum mutation on edit.** New→legacy and legacy→new enum fallbacks rewrite `relationshipCloseness` / `preferredDeliveryChannel` to fallback values (`goodFriend`→`other`, `clipboard`→`none`) with no user notice. (`INFERENCE`, traced; tests only cover one stack at a time.)

### P2
- **F-4 Two tombstone implementations.** Legacy `DriftPeopleStore` bumps `version` on softDelete/restore; new repo `deletePerson` doesn't. Version-based conflict logic is therefore inconsistent per-writer. (`FACT`.)
- **F-5 No FK/constraints.** Orphan birthday rows possible; no DB-level referential integrity. (`FACT` schema, `INFERENCE` effect.)
- **F-6 Deletions don't propagate to cloud as deletes.** Sync uploads tombstones but restore never applies them; deletion sync is "absence", which restore ignores. (`FACT`.)
- **F-7 Backup plaintext PII.** No encryption; restorable by anyone with the account token. Matches ARCHITECTURE.md but contradicts archive docs (C-4). (`FACT`.)

### P3
- **F-8 Draft staleness:** draft row reused across cycles; old body may reload next year after `draftId` cleared. (`INFERENCE`.)
- **F-9 Uid fallback (`firebaseUid ?? googleSubject`) can split backup path if exchange fails.** (`INFERENCE`.)
- **F-10 Two-copy `google-services.json` drift risk; no plugin validates them.** (`FACT`.) Keytool cannot be verified on this machine. (`UNKNOWN`.)
- **F-11 Fixups run at every `beforeOpen`** — harmless duplication, minor startup cost. (`FACT`, verified idempotent.)

---

## 9. Acceptance criteria (Given/When/Then)

1. **Delete durability**
   - Given a person with a birthday, When the user deletes them, Then the person row is tombstoned (visible to sync) **and** the birthday row is deleted **and** no restore path (undo or cloud) re-creates either.
   - Current status: **FAIL** — undo restores the person; cloud restore can re-add both (F-1/F-2).

2. **Enum round-trip fidelity**
   - Given a person stored with legacy values `family`/`goodFriend`/`whatsapp`, When the user opens and saves them via the form, Then the stored `relationshipCloseness`/`preferredDeliveryChannel`/`preferredTone` values are unchanged.
   - Current status: **FAIL** (F-3).

3. **Cloud restore correctness**
   - Given a cloud backup that lacks a contact (deleted locally after last sync), When the user restores, Then that contact does not reappear locally.
   - Given a cloud backup containing a tombstone, When the user restores, Then the local row stays tombstoned.
   - Current status: **FAIL** (F-2, F-6).

4. **Sync metadata consistency**
   - Given a `.db` seeded via the legacy store, When a new-repo operation touches the same row, Then `version` and `autoSendPolicy` reflect a single policy for all writers.
   - Current status: **FAIL** (F-4, hardcoded `manualOnly`).

5. **Firebase wiring**
   - Given a clean checkout, Then the build consumes exactly one `google-services.json` and the SHA-1 registered in Firebase matches the signing keystore of the built artifact (verified via `gradlew signingReport` on the signing machine).
   - Current status: **UNKNOWN** — no GMS plugin consumes the file; registration unverifiable locally.

6. **Non-regression**
   - Given the current schema/migration set, Then `flutter test` passes and `document.dart`-style round-trips (insert→version-bump→softDelete→restore) are covered by tests.
   - Current status: partially covered (app_database_test, person_repository_test, cloud_sync_service_test, people_screen_delete_test exercises the visible undo path).

---

## Appendix — verification steps (run on a machine with the signing keystore)

```bash
# Confirm which keystores exist and their fingerprints
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android
# Gradle signing report (prints all signing config hashes incl. release)
cd android && gradlew signingReport
# Confirm the reported SHA-1s match the single fingerprint registered in Firebase
# for both clients (com.yashsomani.ai_birthday, com.aistudio.relateai.qxtjrk)
```