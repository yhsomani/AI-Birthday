# 07 — Remediation status for the 2026-10 repository audit

Scope: the 24 findings (F01–F24) from the audit of revision `1414d417`, checked
against HEAD. Each finding was re-verified in source before any change.

## Verified state at HEAD

| Finding | Status | Evidence |
|---|---|---|
| F01 Play package name | Already fixed at HEAD | `backend/functions/src/services/subscriptionVerification.ts` uses `com.yashsomani.ai_birthday`. A stale compiled `.d.ts` in `backend/functions/lib/` still has the old value. |
| F02 Reboot alarm payload | **Fixed** (source) | One codec (`persistedTriggerToJson` / `parsePersistedTrigger`) in `BirthdayNotificationReceiver.kt`, used by the writer in `MainActivity.kt` and by reboot recovery. Stored records use the `id`/`personId` keys the codec reads, so no migration is needed. |
| F03 Deletion misses cloud backups | **Fixed** (backend and rules) | `controlPlane.ts` purges `accounts/{uid}` and `users/{uid}` and verifies both are absent. `backend/firestore.rules` denies owner backup writes while `coordinationPresence/{uid}` is `DELETING`. Emulator tests cover both; the fence test fails when the rule is removed. |
| F04 Privacy notice | **Fixed** (copy) | `backend/hosting/privacy/index.html` and `public/privacy/index.html` now describe optional cloud backup. Hindi text is a translation that still needs native review. Static test updated. |
| F10 Closeness loss (part) | **Fixed** (closeness only) | One closeness type (close, casual, distant), chosen by the owner. Legacy stored values collapse on read through `RelationshipCloseness.fromStored`, so no DB migration is needed. The form and model share the type, so no value is lost on save. Round-trip and legacy-mapping tests cover every level. Still open: the two Person models, their repositories, and the other field differences. |
| F11 Reminder empty set | **Fixed** | `reminder_settings_controller.dart` keeps a stored empty set instead of falling back to the defaults. Regression test added; verified to fail with the old line. |
| F16 Purchase acknowledgement | Partly fixed at HEAD | `subscription_service.dart` acknowledges only after a verified success (the audit's "acknowledges on failure" is stale). Purchase status is still a private field, not reactive state. |
| F18 Wrong age in AI prompt | Partly fixed at HEAD | Studio and job paths pass `cycleYear` (commit `56d06dd2`). The edit-overwrite guard is still open. |
| F14 Backup with an expired token | Partly fixed | `cloud_sync_service.dart` refreshes the ID token before each backup and restore (`freshIdToken`, wired in `providers.dart`). Test sends the refreshed token and fails with the refresh disabled. Still open: request deadlines, one controlled retry, and disposing the HTTP client. |
| F05 Overwrites and stale devices | **Fixed** (snapshot backup) | Each backup writes a complete immutable generation under `users/{uid}/generations/{id}`, then one commit writes the manifest and moves `backup/current` to it. A failed row write never moves the pointer (test). Rules fence generation writes during deletion (emulator test). |
| F06 Restore all-or-nothing | **Fixed** (manifest and count check) | Restore reads the `backup/current` pointer and its manifest, checks each collection count against the manifest, and only then applies one local transaction. Test: an incomplete download writes nothing. Still open: malformed records are skipped while success is reported. |
| F20 Handoff history label | Partly fixed (verified at HEAD) | History labels a handoff from the recorded delivery event (`history_screen.dart`, `_handoffLabel`), not from status, and the WhatsApp launch records evidence. Still open: the draft stays `ready` after handoff, and the birthday and draft writes are not one transaction. |
| F24 Dependency audit not enforced | **Partly fixed** | CI now runs `npm audit --omit=dev --audit-level=high` on the backend, which passes locally. Dev-only advisories (in the full tree) are still reported and not blocking. The audit:ci script is unchanged. |
| F21 Recipient name in Android log | **Fixed** | `BirthdayNotificationReceiver.kt` no longer logs the notification title. |
| F23 Dart format gate | **Fixed** | `dart format --set-exit-if-changed .` passes. 37 files were reformatted (indentation only, one separate commit recommended). Line endings normalised to LF to match the index. |
| F12 Import and restore skip reminders | **Fixed** | CSV and device imports already re-plan reminders. A successful cloud restore now invalidates the reminder settings controller and re-plans reminders through `CloudSyncJobHandler.onRestored`. Failed restores do not re-plan. Three handler tests cover backup, restore and failed restore. |
| F13 Scheduling loses alarms | **Partly fixed** (native) | `MainActivity.kt` checks exact-alarm permission before touching anything, schedules the new plan first, and cancels only alarms the new plan drops. Reminders are no longer cancelled on reschedule, and posted notifications are no longer cleared. APK builds; not run on a device. Open: quiet-hours policy (suppressed reminders are still discarded) and rolling renewal of future occurrences. |
| F19 CSV round-trip and validation | **Fixed** (import/export core) | Quote-aware record splitting; real month/day validation (April 31 rejected, Feb 29 accepted); formula guard on export, stripped on import. Phone numbers are exempt from the guard. |

## Decisions settled by the project owner

- **Backup model (F05/F06):** snapshot backup. Each backup is an immutable generation with a complete manifest. A pointer is switched atomically after all documents are written. Restore reads one generation, validates it fully, then applies it in one local transaction. Multi-device sync is out of scope for now.
- **Auto-prepare (F17):** implement it. It needs AI availability, consent, quota and failure handling, and actual sending stays user-controlled.
- **Person model (F10):** unify now, with one canonical model, a migration, and round-trip tests for every field and enum value.

Status: none of the three has been started. Snapshot backup is blocked on a Firestore emulator (Java is not installed on this machine), so it cannot be verified end to end yet.

## Not yet addressed

These need either a product decision or a larger change. Nothing here has been started except where noted above.

- F05 remainder — delete superseded generations (storage grows without it).
- F06 remainder — report malformed records as skipped instead of silently ignoring them.
- F07, F08, F09 — account partitioning, immutable birthday occurrences, midnight refresh.
- F10 remainder — collapse the two Person models and repositories into one.
- F13 remainder — quiet-hours policy and rolling renewal (product decisions); on-device verification.
- F15 — purchase binding against Play.
- F14 remainder — request deadlines, a single controlled retry, and disposal of the owned HTTP client.
- F16 — make purchase status reactive and add a durable retry for pending verification.
- F17 — auto-prepare: implement or remove (product decision).
- F20, F22 — delivery attempts and channel history; splitting oversized screens.
- F21 remainder — App Check initialisation and validation of backup documents.
- F23 remainder — pin the Flutter version, add hosting checks to CI, review goldens.
- F24 remainder — triage and upgrade the moderate production advisories and the dev-tree advisories.
- F19 remainder — export of email, language, timezone, closeness and preferences; duplicate detection within an import batch.
- F03 remainder — decide retention for `subscriptionPurchaseOwnership/` (a product and legal decision).
- Privacy — the Gemini paragraph differs between the source and generated pages. One of them is wrong; check the Gemini prompt code before editing.

## Verification run

| Check | Result |
|---|---|
| `backend/functions`: typecheck, lint, unit tests | Pass (75 tests) |
| `backend/functions`: Firestore emulator tests | Pass (20 tests: control plane and rules). Needs the JDK on PATH; it is installed at C:/Program Files/Java/jdk-26.0.2.1 but not on the system PATH. Run `npx firebase emulators:exec --only firestore "npx vitest run emulator"` after adding its bin folder to PATH. |
| `backend/hosting`: `node --test` | Pass (12 tests) |
| `flutter analyze` | No issues (re-run after formatting) |
| `flutter test` (full suite) | Pass (383 tests, after the F10 closeness change) |
| `dart format --set-exit-if-changed .` (CI gate) | Pass |
| `flutter build apk --debug` | Pass (re-run after the F21 logging change) |
| Reboot recovery on a device or emulator (schedule → reboot → notification) | Not run |

Flutter 3.47.1 and the Android SDK are installed locally (`/c/Users/yashs/develop/flutter`).
The Kotlin codec is compile-verified only; the reboot path still needs a device test.
