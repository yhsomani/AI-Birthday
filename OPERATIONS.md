# AI-Birthday — Developer and operations handbook

**Purpose.** How to set up, build, test, release, operate and document AI-Birthday: the Flutter Android app, the Firebase Functions backend (`backend/functions`), the Firebase Hosting public pages (`backend/hosting`) and the CI pipeline. It covers commands, configuration *names*, deployment procedure and the operational gaps that are still open.

**Scope boundary.** Product intent lives in [PRODUCT.md](PRODUCT.md), design in [ARCHITECTURE.md](ARCHITECTURE.md), controls and threat analysis in [SECURITY.md](SECURITY.md), the issue register and readiness gates in [IMPLEMENTATION.md](IMPLEMENTATION.md) (§8 = release readiness gates), and the generated map in [PROJECT_MINDMAP.md](PROJECT_MINDMAP.md). This document does not repeat them.

**Status & evidence.** Snapshot: Git HEAD `c4aace16` plus the uncommitted remediation changes in the working tree. Verified in that snapshot: `flutter analyze` clean; Flutter tests 653 pass (excluding the `golden` tag) plus 2 golden shell tests; `backend/functions` `npm run check` 91 tests pass; `backend/hosting` 12 tests pass; Firestore-emulator suites 22/22 pass when run manually under OpenJDK 21. **Not run:** device/emulator UI journeys, `npm audit`, an AAB build, a signed release APK, the CSP in a real browser, and the new CI aapt2 permission guard in a real CI run. Nothing is evidenced as deployed to Firebase, Play or Hosting.

**Rules for this document.** Environment variables, `--dart-define` names and secret names appear by **name and purpose only**; never put values, keystores, keyring JSON, tokens, certificate fingerprints or release-config contents in the repository or in this file. Status tags: `[IMPLEMENTED]` `[PARTIAL]` `[NOT IMPLEMENTED]` `[APPROVED]` `[PROPOSED]` `[OPEN]` `[DEPRECATED]`. Legacy sources are cited in prose only (they were removed from the working tree and are recoverable from Git commit `c4aace16`).

## Table of contents

1. [Prerequisites and toolchain](#1-prerequisites-and-toolchain)
2. [Local development](#2-local-development)
3. [Build commands](#3-build-commands)
4. [Test commands](#4-test-commands)
5. [Database setup and migrations](#5-database-setup-and-migrations)
6. [CI/CD](#6-cicd)
7. [Deployment](#7-deployment)
8. [Environment configuration reference](#8-environment-configuration-reference)
9. [Monitoring, logging and alerting](#9-monitoring-logging-and-alerting)
10. [Backup and recovery](#10-backup-and-recovery)
11. [Troubleshooting](#11-troubleshooting)
12. [Release procedure and checklist](#12-release-procedure-and-checklist)
13. [Play Billing and purchases setup](#13-play-billing-and-purchases-setup)
14. [Documentation maintenance procedure](#14-documentation-maintenance-procedure)
- [Appendix A — Superseded, rejected and historical items](#appendix-a--superseded-rejected-and-historical-items)
- [Appendix B — Open decisions register](#appendix-b--open-decisions-register)

---

## 1. Prerequisites and toolchain

[IMPLEMENTED] Versions below were read from the repository (`ci.yml`, `pubspec.yaml`, `package.json` files, Gradle files). Local machines are not pinned beyond these constraints.

| Component | Version / constraint | Source of truth | Notes |
|---|---|---|---|
| Flutter | **3.47.6** stable | `.github/workflows/ci.yml` (`subosito/flutter-action`) | `pubspec.yaml` requires Flutter `>=3.44.0`; use the CI version to reproduce CI. |
| Dart SDK | `>=3.12.0 <4.0.0` | `pubspec.yaml` | The root README's "Dart >=3.11.0" is stale. |
| App version | `1.0.0+1` | `pubspec.yaml` | |
| Java for Flutter/Gradle | **17** (Zulu in CI) | `ci.yml` | |
| Java for the Firestore emulator | **21** | `ci.yml` (`backend-verification`) | Needed only for `npm run test:emulator`. |
| Android Gradle Plugin / Kotlin / Gradle | 9.1.0 / 2.4.0 / 9.3.1 | `android/` Gradle files | `minSdk = max(flutter.minSdkVersion, 26)`; `compileSdk`/`targetSdk` come from Flutter. |
| Android SDK | Platform + build-tools with `aapt2` | CI guard needs `aapt2` | The README states "API 35"; that exact level is not enforced in the Gradle files (`[OPEN]` minor). |
| Node — Functions | `>=22 <25`; CI uses **22**; `firebase.json` runtime `nodejs22` | `backend/functions/package.json` | |
| Node — Hosting | `>=24.18.0 <25`; CI uses **24** | `backend/hosting/package.json` | Functions and Hosting need different Node majors. |
| npm | **11.6.0** (both packages) | `package.json` `packageManager`/engines; CI runs `npm i -g npm@11.6.0` for Functions | |
| Firebase CLI | `firebase-tools ^15.34.1` (Functions dev dependency); `firebase ^13.1.0` in Hosting | package manifests | Use `npx firebase-tools` from `backend/`. |
| Python 3 | any recent 3.x, standard library only | `docs/graph/tools/*.py` | Documentation validator and graph builders. |

Key library lines: Functions — `firebase-admin ^14.5.0`, `firebase-functions ^7.4.0`, `zod ^4.6.7`, `vitest ^4.1.12`, `eslint 10.7.0`; Hosting — `vite 8.1.4` (build output `backend/hosting/public`, `emptyOutDir: true`); Flutter — Drift/`drift_dev` for the local database and, new in this remediation, a direct `crypto ^3.0.6` dependency (SHA-256 purchase binding).

Optional: `adb` for installing debug builds (see [§3](#3-build-commands)), `keytool` for registering signing-certificate fingerprints ([§2](#2-local-development)), `gcloud` for Firestore TTL policies ([§7](#7-deployment)).

## 2. Local development

### 2.1 Flutter app

```bash
flutter pub get
flutter run --flavor personal      # or --flavor play
```

[IMPLEMENTED] Two Gradle product flavors share `applicationId` `com.yashsomani.ai_birthday`:

| Flavor | Manifest difference | Use |
|---|---|---|
| `play` | no `SEND_SMS` permission | Store-bound build; CI fails if the APK contains an SMS/call-log permission ([§6](#6-cicd)). |
| `personal` | `android/app/src/personal/AndroidManifest.xml` declares `SEND_SMS` | Side-loaded build. Automatic SMS is still hard-disabled in code. |

Capability switches are compile-time defines with default `false`: `ENABLE_AUTO_PREPARE`, `ENABLE_AUTOMATIC_SMS` (`lib/core/config/product_capabilities.dart`). Test-only entitlement hooks must be guarded by `assert(kDebugMode)`; CI greps production code for them ([§6](#6-cicd)).

The Windows helper `build-and-install-android.bat [play|personal] [--clean]` runs `flutter pub get`, builds a **debug** APK for the chosen flavor (default `personal`) and installs it with `adb`.

### 2.2 Firebase client configuration (names only)

[IMPLEMENTED] The app does **not** use the `google-services` Gradle plugin and does not consume any `google-services.json` file. Firebase options come from `lib/core/config/firebase_config.dart`: compile-time `--dart-define` values with committed defaults for the project `relateai-birthday-ysoman-a2372` and region `asia-south1`. The `google-services.json` files in the repository root, `android/app/` and `firebase/` are not read by the build; the two under `firebase/` belong to a different project id and package and are legacy artifacts.

| Dart define | Purpose |
|---|---|
| `FIREBASE_PROJECT_ID` | Firebase project id |
| `FIREBASE_PROJECT_NUMBER` | Project number (sender id) |
| `FIREBASE_ANDROID_APP_ID` | Android app id |
| `FIREBASE_FUNCTIONS_REGION` | Callable region (default `asia-south1`) |
| `FIREBASE_WEB_CLIENT_ID` | OAuth web client for Google Sign-In |
| `FIREBASE_WEB_API_KEY` | Public Firebase web API key; used for Identity Toolkit/Firestore REST calls |

The web API key is a public project identifier, not a secret (it is restricted by Google Cloud API restrictions, not by secrecy). Release and CI builds may override it with `--dart-define=FIREBASE_WEB_API_KEY=<value from your secret store>`. `[OPEN]` In CI the value comes from the `FIREBASE_WEB_API_KEY` repository secret; if that secret is unset, the define is an *empty string* and (by Dart semantics, not exercised) the committed default is not used, so Firebase init would receive an empty key. Decide whether CI should fail when the secret is empty ([Appendix B](#appendix-b--open-decisions-register), OPS-OPEN-07).

### 2.3 Sign-in and App Check prerequisites

- **Google Sign-In** requires the SHA-1 fingerprint of *every* signing keystore (debug **and** release) to be registered for `com.yashsomani.ai_birthday` in the Firebase console. Missing fingerprint symptom: `GoogleSignInException [16] Account reauth failed`. Obtain fingerprints with `keytool -list -v` against the relevant keystore and paste them into the console; never commit them or the keystores. See the README for the keytool invocations.
- **App Check.** In `lib/main.dart`, debug builds (`kDebugMode`) use the App Check *debug* provider and release builds use Play Integrity. A debug build therefore needs its debug token registered in the Firebase console, otherwise callables that enforce App Check fail. Firebase/App Check initialization failures are logged and never block app startup.
- The app talks to Firestore through the **REST API** with the signed-in user's ID token; this path does not send an App Check header (`[OPEN]`, [Appendix B](#appendix-b--open-decisions-register) OPS-OPEN-03).

### 2.4 Backend and Hosting locally

```bash
cd backend/functions && npm ci          # Functions (Node 22)
cd backend/hosting   && npm ci          # Hosting (Node 24)
cd backend/hosting   && npm run build   # development build into hosting/public
```

Emulator ports (from `backend/firebase.json`): Functions 5001, Firestore 8080, Hosting 5000, emulator UI disabled. Use the demo project `demo-birthday-autopilot` for emulator runs so nothing can touch a live project.

## 3. Build commands

[IMPLEMENTED]

```bash
# one-time and after dependency changes
flutter pub get

# Drift code generation — required after any change to the database schema classes
dart run build_runner build --delete-conflicting-outputs

# Debug / release APK per flavor
flutter build apk --flavor play     --debug
flutter build apk --flavor personal --debug
flutter build apk --flavor play     --release [--dart-define=FIREBASE_WEB_API_KEY=...]
flutter build apk --flavor personal --release [--dart-define=FIREBASE_WEB_API_KEY=...]
```

- Outputs land in `build/app/outputs/flutter-apk/` (for example `app-play-release.apk`).
- **Generated code is committed:** `lib/core/database/app_database.g.dart` and the l10n output. Regenerate and commit them together with the source change.
- **Localization:** `l10n.yaml` reads `lib/l10n/app_en.arb` and generates `app_localizations.dart`; `flutter: generate: true` means `flutter pub get`/builds regenerate it. Run `flutter gen-l10n` to regenerate explicitly.
- **Signing.** Release signing reads `android/key.properties` (keys `keyAlias`, `keyPassword`, `storeFile`, `storePassword`; template: `android/key.properties.example`). `key.properties`, `*.jks` and `*.keystore` are git-ignored. If `key.properties` is missing the release signing config is `null` and the APK is **unsigned**; it never falls back to the debug key. `[OPEN]` No production keystore exists yet.
- There is no minification/R8 configuration. Output is **APK only**; no Android App Bundle (`.aab`) is produced anywhere (`[OPEN]`, needed for a Play upload).
- Backend: `cd backend/functions && npm run build` (compiles TypeScript); `cd backend/hosting && npm run build` (Vite) or `npm run build:release` (Vite build then `tools/write-release-config.mjs`; fail-closed, see [§7.3](#73-hosting-release-configuration)).

## 4. Test commands

### 4.1 Flutter

```bash
dart format --output=none --set-exit-if-changed .
flutter analyze                                   # must be clean (0 issues)
flutter test --coverage --exclude-tags golden     # unit + widget + e2e tiers
flutter test test/app/shell_goldens_test.dart     # golden shell test (tag: golden)
```

- Use `flutter analyze`, not `dart analyze`. Analysis excludes `backend/` and `android/`.
- **Goldens.** The single golden test is `test/app/shell_goldens_test.dart`. CI runs it as a separate step on Ubuntu. Update baselines with `flutter test --update-goldens test/app/shell_goldens_test.dart`. Comments in `dart_test.yaml` and the test file call it a "local-only gate" and mention `test/ui/goldens`; both are stale (`[OPEN]` OPS-OPEN-08). Old `test/ui/goldens/design_system_*.png` files and `failures/` PNGs are leftover artifacts.
- **E2E suite** layout: `test/e2e/{harness, tier1…tier4, e2e_suite_test.dart}`. Run `flutter test test/e2e/`, a single tier file, or `e2e_suite_test.dart`. `test/run_all_domain_tests.dart` is a standalone runner.
- **Device journey** (`integration_test/device_journey_test.dart`) needs a physical device and is **not run in CI** `[NOT IMPLEMENTED in CI]`.
- **Timezone matrix.** `test/features/birthdays/application/birthday_lifecycle_timezone_test.dart` must pass under `TZ=UTC`, `TZ=America/New_York` and `TZ=Asia/Kolkata`, for example `TZ=Asia/Kolkata flutter test test/features/birthdays/application/birthday_lifecycle_timezone_test.dart`.
- **Migration tests:** `flutter test test/core/database/app_database_migration_test.dart` (see [§5.4](#54-testing-migrations)).

### 4.2 Functions (`backend/functions`)

```bash
npm ci
npm run check            # = typecheck && lint && test (unit tests; emulator tests excluded)
npm run test             # unit tests only
npm run test:coverage    # thresholds: branches 75, functions 100, lines/statements 80
npm run test:emulator    # Firestore-emulator suites
npm run audit:ci         # npm audit (production deps, high+)
```

`test:emulator` runs `firebase emulators:exec --config ../firebase.json --project demo-birthday-autopilot --only firestore "vitest run test/*.emulator.test.ts"`.

**Emulator prerequisites.**
1. **Java 21** on `PATH` (`JAVA_HOME` set if Java is not the default).
2. **Network access on first use.** The installed `firebase-tools` (15.34.1) expects Firestore emulator jar **v1.22.0**; the local cache may hold only an older jar (during this transformation only `v1.21.0` was cached). Without network the run fails. The jar is cached under `~/.cache/firebase/emulators`. Behind a TLS-intercepting proxy set `NODE_EXTRA_CA_CERTS` to the corporate CA bundle.
3. The demo project id avoids any live Firebase access.

`[PARTIAL]` During this transformation the 22 emulator tests were run manually under OpenJDK 21 and passed; `npm run test:emulator` itself could not be run offline.

### 4.3 Hosting (`backend/hosting`)

```bash
npm ci
npm run check      # = typecheck && test && build   (test = node --test test/*.test.mjs)
```

### 4.4 Static capability checks

Because Play policy and product decisions depend on them, the following are enforced by CI grep/aapt2 gates rather than by unit tests ([§6.2](#62-job-flutter-quality-and-test)): no Stripe/card-payment strings or secrets in `lib`, no calls to `setDevSandboxEntitlement(` outside its definition, and no SMS/call-log permission in the Play APK.

## 5. Database setup and migrations

### 5.1 Local schema (Drift, schema version 7)

[IMPLEMENTED] The local database is SQLite via Drift, opened as `driftDatabase(name: 'ai_birthday')` (in-memory when `FLUTTER_TEST` is set). Tables: `persons`, `birthdays`, `message_drafts`, `reminder_settings_entries`, `delivery_events`, `jobs`. The schema is defined in `lib/core/database/app_database.dart`; there is no `drift_schemas` snapshot directory. The detailed data model is in [ARCHITECTURE.md](ARCHITECTURE.md).

### 5.2 Migration history and v7 notes

Upgrade steps run in order inside `onUpgrade`:

| Step | Change |
|---|---|
| `from < 2` | Create `birthdays`, `message_drafts`, `reminder_settings_entries` |
| `from < 3` | `alterTable(TableMigration(persons))` |
| `from < 4` | Create `delivery_events` |
| `from < 5` | Create `jobs` |
| `from < 6` | Add `persons.leap_day_resolution`, `delivery_events.draft_id` (nullable), `delivery_events.outcome` (default `'launched'`); each guarded by a column-exists check |
| `from < 7` (`_migrateToV7`, idempotent) | Add `message_drafts.revision INTEGER NOT NULL DEFAULT 0`; dedupe `birthdays` to one row per person; convert `birthdays.date` to ISO `TEXT` |

Details of v7 (fixes A1-001, A1-009, A2-004):
- `_dedupeBirthdaysPerPerson` keeps the **newest** row per person, re-points drafts and delivery events at the survivor, and deletes queued jobs of removed rows.
- If `birthdays.date` is not already `TEXT`, the calendar day is **re-derived** from the person's month/day plus `cycle_year`, with 29 February resolving to 1 March or 28 February according to `leap_day_resolution`; only when that data is unavailable does it fall back to the device-local day of the stored instant. The column is then rewritten with `strftime('%Y-%m-%d', date, 'unixepoch')`.
- `beforeOpen` creates indexes `idx_delivery_events_birthday_id`, `idx_birthdays_person_id_unique` (drops the old non-unique `idx_birthdays_person_id`), `idx_message_drafts_birthday_id`, `idx_jobs_status`.
- The reminder bootstrap plan stamp (A1-002/003) is stored as the `plan_state` row of `reminder_settings_entries`; it requires no schema change.

### 5.3 Migration gates and safety rules

**v6 → v7 data-safety note.** The v7 migration (`_migrateToV7`, `_dedupeBirthdaysPerPerson`) keeps the newest birthday row per person, re-points that person's drafts and delivery events to it, deletes the queued jobs of the removed rows, and then creates the UNIQUE `person_id` index. Removed duplicate rows are hard-deleted, no pre-migration database copy is taken, and a surviving row with no draft link is not re-linked to a removed row's draft. Take a copy of the database file before upgrading any installation that may hold duplicate birthday rows.

[PROPOSED → partly IMPLEMENTED] The legacy migration plan defined six gates. Mapping to what actually happened and what to do next time:

| Gate | Requirement | Status / how to apply it |
|---|---|---|
| 0 Approval and backup | Decisions approved; **verified backup**; separate authorization for destructive steps | `[PROPOSED]` process rule. Before any destructive change take a copy of the database with the app closed (checkpoint the WAL), export the contacts CSV (`contact_csv_service.dart`) and make a cloud backup ([§10](#10-backup-and-recovery)). Android OS backup is disabled (`allowBackup="false"`), so there is no automatic recovery after uninstall. |
| 1 Inventory, no mutations | Read-only aggregate queries | `[PROPOSED]` — see the inventory procedure below. |
| 2 Decide remediation per exception class | Per-class decisions recorded | Applied for v6/v7 (see §5.2). |
| 3 Rehearsed additive migration | Run on a copy with fixtures; nullable additions | `[IMPLEMENTED]` as migration tests (§5.4); v6 was additive. |
| 4 Repair and constraint preflight | Zero violations before constraints | `[DEPRECATED]` — superseded: v7 enforces the unique `person_id` index after an *in-migration* dedupe instead of a separate preflight ([Appendix A](#appendix-a--superseded-rejected-and-historical-items)). |
| 5 Constraints and rollout | Enforce constraints; staged rollout | `[DEPRECATED]` for the same reason; staged rollout is `[NOT IMPLEMENTED]` ([§6.3](#63-target-pipeline-versus-current-ci)). |

**Rollback and recovery rules.**
- Migrations are **forward-only**. App downgrade is unsupported: an older build cannot read a v7 database. A rollback must never erase newer user records.
- Migration errors propagate; a partially upgraded database is never treated as safe and a migration is never marked complete before validation `[PARTIAL]` (the legacy requirement "migrate in a transaction where supported, keep a pre-migration copy during rehearsal" is `[PROPOSED]`; v6/v7 follow it for guarded column additions).
- Keep a pre-migration copy of the database for any rehearsal; never run an experiment on the only copy.

**Privacy-safe SQLite inventory procedure (operator checklist).** Used before a destructive change to measure how much data would be affected, without exposing personal data:
1. Close the app and copy the database file (and checkpoint the WAL) to an operator-controlled location; work **only on the copy**.
2. Open the copy with `PRAGMA query_only = ON` and run **aggregate-only** queries; never `SELECT` names, phone numbers, notes or message text, and never paste row-level output into tickets or docs.
3. **Stop if `PRAGMA user_version` differs from the expected value.** The legacy procedure expected `5` (stale); the current expected value is **7** at the working-tree snapshot (6 at HEAD `c4aace16`).
4. The legacy query set is kept verbatim in [docs/operations/aggregate-inventory.sql](docs/operations/aggregate-inventory.sql) (it was written for schema v5: revalidate each query against v7 before use). The set (numbered 0–13) covers: schema/`user_version` check; table row totals; person birthday completeness; `autoPrepare` counts; timezone value shape; phone value shape *buckets*; duplicate-birthday groups; orphan references; draft linkage and draft-id integrity; delivery-event orphans; `ai_generate` job orphans; reminder-setting counts; constraint/index metadata. `[OPEN]` The SQL file itself was a legacy artifact (`docs/knowledge/.../aggregate-inventory.sql`, recoverable from commit `c4aace16`); it targets schema v5 and must be re-validated against v7 before reuse.
5. Record counts and decisions only; delete the copy afterwards.

### 5.4 Testing migrations

`test/core/database/app_database_migration_test.dart` builds a **real older database file** under `.dart_tool/ai_birthday_migration_tests` by pinning `user_version`, then opens it with the current code. Cases: v4→v7, v6→v7 (re-derives dates, dedupes, adds revision), v5→v6 and a legacy-compat case.

```bash
flutter test test/core/database/app_database_migration_test.dart
```

When you add a schema version: bump `schemaVersion`, add a guarded `from < N` step, regenerate code (`dart run build_runner build --delete-conflicting-outputs`), add an old-version fixture test, and update [ARCHITECTURE.md](ARCHITECTURE.md) and this section.

### 5.5 User-data safety rules

- Never log or commit contact data, phone numbers, message drafts or database copies.
- Soft-deleted personal data retention is undecided `[OPEN]` (A1-011; [Appendix B](#appendix-b--open-decisions-register)).
- Destructive operations on a user's device need an export or cloud backup first; there is no OS-level backup.

## 6. CI/CD

Workflow: `.github/workflows/ci.yml`, name **"CI / Release Pipeline"**. Triggers: push to `main` and `feat/**`, and pull requests to `main`. It has no `permissions:` block, no concurrency control and uses version-tag (not SHA-pinned) actions `[OPEN]`. **There is no deploy job**: CI builds and tests but never deploys.

### 6.1 Overview

| Job | Working dir | Runtime | Purpose |
|---|---|---|---|
| `flutter-quality-and-test` | repo root | Java 17 (Zulu), Flutter 3.47.6 | Format, analyze, tests, goldens, APK builds, capability gates, Play-permission guard, artifacts |
| `backend-verification` | `backend/functions` | Node 22, npm 11.6.0, Java 21 | Audit, lint, build, unit and emulator tests |
| `hosting-verification` | `backend/hosting` | Node 24 | `npm ci` and `npm run check` |

### 6.2 Job: `flutter-quality-and-test`

In order: checkout → setup-java (Zulu 17) → Flutter 3.47.6 → `flutter pub get` → `dart format --output=none --set-exit-if-changed .` → `flutter analyze` → `flutter test --coverage --exclude-tags golden` → golden shell test (`flutter test test/app/shell_goldens_test.dart`) → build Play **release** APK (unsigned CI artifact) → capability grep gate → **Play permission guard** → build personal release APK → upload artifacts `app-play-release-apk` and `app-personal-release-apk` (`if-no-files-found: error`).

**Capability grep gate** — the step fails if `lib` contains `STRIPE_SECRET_KEY`, the string `Pay with Card / Stripe`, or `stripeGatewayServiceProvider`, or if any `setDevSandboxEntitlement(` call exists in `lib` other than its definition. This keeps client payment secrets and entitlement test hooks out of release builds.

**Fail-closed Play permission guard** `[IMPLEMENTED]` (new in this remediation; **not yet exercised in a real CI run**). Runs with `set -euo pipefail`:
1. Locates the latest `aapt2` under the build-tools directory of `$ANDROID_HOME` / `ANDROID_SDK_ROOT`.
2. Runs `aapt2 dump permissions build/app/outputs/flutter-apk/app-play-release.apk`.
3. **Fails if** the APK is missing, the SDK root is missing, `aapt2` cannot be found, or the permission list is empty; also fails if `INTERNET` is absent (a parser sanity check — a real build always has it).
4. **Fails if** the APK declares any of: `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`, `READ_CALL_LOG`, `WRITE_CALL_LOG`, `PROCESS_OUTGOING_CALLS`.

The guard replaces an earlier check that could pass silently when the tool was missing. It checks the Play flavor only; the `personal` flavor intentionally declares `SEND_SMS`.

### 6.3 Target pipeline versus current CI

The legacy blueprint proposed a ten-step release pipeline, an eight-stage CI/CD model, five isolated environments and a "Quality Gate 3 — operational release". The table compares them with what exists.

| Proposed element | Current state |
|---|---|
| PR checks (format, analyze, unit tests) | `[IMPLEMENTED]` `flutter-quality-and-test` |
| Static analysis and dependency audit | `[PARTIAL]` Flutter analyze `[IMPLEMENTED]`; `npm audit` for Functions in CI, **not for Hosting** `[OPEN]`; no license/vulnerability scan for Dart dependencies |
| Backend and rules tests | `[IMPLEMENTED]` Functions unit + emulator tests (including Firestore rules suite); CI does not run Functions test typecheck or the coverage thresholds `[OPEN]` (A4-016) |
| Per-flavor build with provenance | `[PARTIAL]` per-flavor unsigned release APKs; no provenance, no SBOM, no signing step `[NOT IMPLEMENTED]` |
| Integration with emulators and sandboxes | `[PARTIAL]` Firestore emulator only; no Play/App Check sandbox |
| Device/OS matrix | `[NOT IMPLEMENTED]` `integration_test` is not run in CI |
| Signed AAB, staged rollout, canary, rollback, release approval record | `[NOT IMPLEMENTED]` |
| Five isolated environments (developer, CI, integration, staging, production) | `[PROPOSED]` — only developer, CI and an emulator demo project exist; a single Firebase project is configured (`.firebaserc` default `relateai-birthday-ysoman-a2372`) |
| Secure SDLC: per-change threat model, license/vulnerability checks | `[PROPOSED]` |
| Quality Gate 3: staged rollout, smoke/health checks, alerts and pause/disable, known-risk sign-off | `[NOT IMPLEMENTED]` |

### 6.4 Job: `backend-verification`

`npm ci` → `npm audit --omit=dev --audit-level=high` → Java 21 setup → `npm run lint` → `npm run build` → `npx vitest run test/purchase-app-check.static.test.ts` → `npm test` → `npm run test:emulator`. The static test asserts that purchase verification keeps App Check enforcement.

### 6.5 Job: `hosting-verification`

Node 24, `npm ci`, `npm run check` (typecheck, tests, build). Production dependencies are not audited `[OPEN]`.

### 6.6 What CI does not cover

Device journeys, AAB builds, Hosting `npm audit`, a browser test of the Hosting CSP, signing, any deployment, and live Firebase/Play verification.

## 7. Deployment

`[OPEN]` **Nothing is evidenced as deployed.** There is no attested deployment record for Firebase Functions, Firestore rules/indexes/TTL, Hosting or a Play track. The last recorded probe of the `verifyPurchase` endpoint returned 404 (undated); the live state cannot be verified from the repository (gap GAP-009). A deployment may happen only after the approvals in [§7.6](#76-required-approvals-and-whats-not-evidenced).

### 7.1 Environments and project

One Firebase project is configured: `.firebaserc` default `relateai-birthday-ysoman-a2372`, region `asia-south1`. Separate dev/test/staging/production projects with least-privilege service accounts are `[PROPOSED]`, not implemented. **Always pass an explicit `--project <id>`** when deploying and confirm it before executing.

### 7.2 Functions and Firestore

Run from `backend/` (`firebase.json` lives there). Predeploy runs `npm --prefix "$RESOURCE_DIR" run build`.

```bash
cd backend
npx firebase-tools deploy --project <explicit-id> --only functions
npx firebase-tools deploy --project <explicit-id> --only functions:verifyPurchase   # single function
npx firebase-tools deploy --project <explicit-id> --only firestore                   # rules + indexes
```

- **Parameters.** `CONTROL_PLANE_SERVICE_ACCOUNT` (string param) and `COORDINATION_HMAC_KEYRING` (JSON secret; bound only to `claimOccurrence` and `claimTest`). Provide secrets through Firebase secret management (`firebase functions:secrets:set COORDINATION_HMAC_KEYRING`) or the git-ignored local `functions/.env*` / `.secret.local` files; never commit them. Keep the *previous* HMAC key in the keyring for at least **400 days** after rotation so earlier authorizations remain verifiable.
- **Callables** (all region `asia-south1`, `enforceAppCheck` and `consumeAppCheckToken` true): `registerAndroidInstallation`, `renewSenderLease`, `changeAccountMode`, `claimOccurrence`, `claimTest`, `armAttempt`, `getArmStatus`, `reportTestOutcome`, `authorizeSafeRetry`, `beginSenderTransfer`, `completeSenderTransfer`, `requestAccountDeletion`, `accountDeletionReceipt`, `resetContactDerivedState`, `releaseAndroidSender`, `coordinationLifecycleStatus`, `companionStatus`, `verifyPurchase`.
- **Schedulers:** `sweepDeletionDrains` and `sweepCoordinationOperations`, every 1 minute (UTC), 300 s timeout, `maxInstances` 1.
- **Do not deploy** the callable surface or the sweepers for a blocked product scope until the corresponding scope approvals are given (legacy DEC-044) `[PROPOSED]` rule; deploy the minimal set (see [§13](#13-play-billing-and-purchases-setup) for the purchase path) rather than "all".
- **Firestore TTL** is enabled manually per collection group, for example `gcloud firestore fields ttls update cleanupAt --collection-group=<group> --enable-ttl`. `backend/ttl-policies.json` lists 12 groups (including `deletionReceipts`), while `firestore.indexes.json` lists 11 (no `deletionReceipts` index; A4-015) `[OPEN]`. Neither `users/*/generations` nor `subscriptionPurchaseOwnership` has TTL.
- **Firestore security rules** (`backend/firestore.rules`) are deployed with the `firestore` target; summary in [SECURITY.md](SECURITY.md).

### 7.3 Hosting release configuration

Hosting is **fail-closed**: a production build requires a complete release configuration and refuses placeholder approvals.

1. Copy `backend/hosting/release-config.example.json` to a file **outside the repository** and fill it from approved sources. Never commit it and never paste its contents into docs or tickets.
2. Export `BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH` to that path.
3. `cd backend/hosting && npm run build:release` runs Vite and then `tools/write-release-config.mjs`, which validates the file and writes `public/runtime-config.json`.

Release-config fields (names only): `schemaVersion` (1), `publicBaseUrl`, `developerDisplayName`, `supportUrl` (must be a different origin), `recaptchaEnterpriseSiteKey`, `privacyEffectiveDate`, `termsEffectiveDate`, `legalApprovalReference`, `privacyApprovalReference`, `hindiCopyApprovalReference`, `adminDeletionRunbookReference`, `verifiedAdminDeletionWorkflowTested` (must be `true`), `productionFirebaseDeletionSagaTested` (must be `true`). Approval references must be at least 8 characters and must not contain `todo`, `tbd`, `placeholder` or `pending`. The generated `runtime-config.json` exposes 8 public fields including `functionsRegion: asia-south1`.

```bash
cd backend
BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH=<out-of-repo-path> npx firebase-tools deploy --project <explicit-id> --only hosting
```

Hosting `firebase.json` predeploys `npm --prefix hosting run build:release`, sets `cleanUrls`/`trailingSlash` and a CSP plus security headers. The CSP `script-src` now includes `https://apis.google.com` (A4-001 fix) so the Google sign-in script can load. `[OPEN]` The CSP has **not** been tested in a browser and `frame-src` may also need the Firebase auth domain. `hosting/public` is a committed build output and `runtime-config.json` is **not** git-ignored `[OPEN]` OPS-OPEN-09 — check `git status` before committing a release build and do not commit production values.

Deletion flow depends on Hosting: users request deletion from the `/delete/` page; there is no in-app entry `[OPEN]` ([Appendix B](#appendix-b--open-decisions-register)).

### 7.4 Android / Play release

`[PARTIAL]` What is documented and implemented: a signed release needs `android/key.properties` + keystore ([§3](#3-build-commands)); CI produces only unsigned APK artifacts; there is no AAB build, no upload automation and no Play track evidence. Play submission, the production keystore and a closed-testing track are `[NOT IMPLEMENTED]`/`[OPEN]`; see [§12](#12-release-procedure-and-checklist).

### 7.5 Release artifact requirements

`[PROPOSED]` Each released artifact should identify: commit, dependency lock state, database schema version, target environment, Firebase region, signing identity and test evidence. Today CI records the commit only implicitly (artifact of a workflow run). Release builds should also be checked statically for the Play SMS-permission gate, client Stripe secrets and entitlement test hooks (implemented by the CI gates in [§6.2](#62-job-flutter-quality-and-test)).

### 7.6 Required approvals and what's not evidenced

| Gate (see IMPLEMENTATION.md §8) | Needed before production | Evidence today |
|---|---|---|
| OPS-GATE-01 Release signing | Production keystore + `key.properties`; signed AAB/APK | None; CI artifact unsigned |
| OPS-GATE-02 Firebase backend deployment attested | Functions, rules, TTL, `verifyPurchase` live | None; last probe 404 |
| OPS-GATE-03 Google Sign-In certificates | Debug and release SHA-1 registered; production SHA-1 unavailable until the keystore exists | Not evidenced |
| OPS-GATE-04 Play Integrity / App Check registration | Production registration; a test from a Play-installed build | Not evidenced |
| OPS-GATE-05 Play Billing production | Product, console service-account access, internal track, real purchase and restore sandbox test | Not evidenced |
| OPS-GATE-06 Legal/support/Hosting | Developer identity, effective dates, support workflow, reCAPTCHA key, Hindi copy approval; Hosting stays fail-closed | Missing |
| OPS-GATE-07 Device QA | Physical-device QA (see §12) | Not done |
| OPS-GATE-08 Operational readiness | Environments, backup/restore, incident response, monitoring, support model | See [§9](#9-monitoring-logging-and-alerting) |

## 8. Environment configuration reference

Names and purpose only. Values never appear in the repository.

| Name | Kind | Where used | Purpose |
|---|---|---|---|
| `FIREBASE_PROJECT_ID`, `FIREBASE_PROJECT_NUMBER`, `FIREBASE_ANDROID_APP_ID`, `FIREBASE_FUNCTIONS_REGION`, `FIREBASE_WEB_CLIENT_ID` | Dart define (committed defaults) | `lib/core/config/firebase_config.dart` | Firebase client identity and callable region |
| `FIREBASE_WEB_API_KEY` | Dart define; CI repository secret of the same name | `firebase_config.dart`, `ci.yml` | Public Firebase web API key override |
| `ENABLE_AUTO_PREPARE`, `ENABLE_AUTOMATIC_SMS` | Dart define (default `false`) | `product_capabilities.dart` | Capability switches |
| `FLUTTER_TEST` | Environment (set by Flutter) | `app_database.dart` | Selects the in-memory database in tests |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | Environment | CI aapt2 guard, Android toolchain | SDK location |
| `JAVA_HOME` | Environment | Local Java 17/21 selection | JDK for Gradle / emulator |
| `TZ` | Environment | timezone test matrix | Timezone for tests |
| `NODE_EXTRA_CA_CERTS` | Environment | Firebase CLI on corporate networks | Extra CA bundle path |
| `BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH` | Environment | `backend/hosting/tools/write-release-config.mjs` | Path to the out-of-repo Hosting release config |
| `CONTROL_PLANE_SERVICE_ACCOUNT` | Functions string param | Functions | Runtime service account used for control-plane access and Play Console access |
| `COORDINATION_HMAC_KEYRING` | Functions JSON secret | `claimOccurrence`, `claimTest` | HMAC keyring for authorization binding; keep the previous key 400 days |
| `key.properties` keys: `keyAlias`, `keyPassword`, `storeFile`, `storePassword` | Git-ignored file | `android/app/build.gradle.kts` | Release signing |
| `GITHUB secret FIREBASE_WEB_API_KEY` | CI secret | `ci.yml` | See above |

Tests that need no secrets use the demo project `demo-birthday-autopilot`.

## 9. Monitoring, logging and alerting

### 9.1 What exists

- **App logging** `[IMPLEMENTED]`: `ConsoleAppLogger` logs at warning level in release and info otherwise, and redacts sensitive keys by pattern. Global error handlers are installed from `lib/core/logging/global_error_handlers.dart`. Logs stay on the device.
- **Functions logging** `[PARTIAL]`: scheduled sweeps log completion (`deletion_sweep_completed`, `coordination_operation_sweep_completed`) with `logger.info` — they are visible in Cloud Logging once deployed. The callable wrapper `safeCall` converts unexpected (non-`HttpsError`) failures to a generic error **without logging them**, and there is no alert hook `[OPEN]` (A4-014).
- No Crashlytics, analytics, Sentry or performance SDK is present in `pubspec.yaml` or `lib`.

### 9.2 Gaps `[OPEN]` / `[NOT IMPLEMENTED]`

| Need | State |
|---|---|
| Operational dashboard and alerting (F-103): low-cardinality redacted metrics for entitlement, provider, backup and reminder health | `[NOT IMPLEMENTED]` |
| Observability and incident rules: redacted metrics (operation, result, retryable flag, latency bucket, release, flavor), incident severity and escalation | `[NOT IMPLEMENTED]` |
| Billing/deployment reconciliation operations (F-104): authorized support workflow by opaque ids with idempotent audit | `[NOT IMPLEMENTED]` |
| Crash reporting | `[NOT IMPLEMENTED]` |
| Alerting on failing sweeps, repair loops, quota or purchase-verification errors | `[NOT IMPLEMENTED]` |
| Support/deletion operating model (DEC-052): named owner, hours/SLA, access and evidence retention | `[OPEN]` decision |
| Performance and availability budgets (DEC-053): baseline on a supported device/data/provider matrix, then approve | `[OPEN]` decision |
| Billing: no reconciliation of Play state versus entitlement beyond `verifyPurchase` and restore | `[NOT IMPLEMENTED]` |

### 9.3 Runbooks `[NOT IMPLEMENTED]`

The legacy blueprint required ten minimum runbooks: purchase-verification outage or wrong grant; backup/restore integrity failure; account-deletion failure; credential/key rotation; provider outage; App Check/Play Integrity failure; Firebase quota/cost incident; Hosting release or configuration incident; data-corruption/migration incident; support escalation. **No runbook exists in the repository** (the legacy note that runbooks are "still valid" is superseded). The only operational procedure text is in this handbook and the package READMEs. Creating them is an open work item, and `adminDeletionRunbookReference` in the Hosting release config requires an approved reference to such a runbook.

## 10. Backup and recovery

### 10.1 Cloud backup generations

**Recovery limit.** After each successful backup the client deletes backup generations older than the previous one (at most 5 per run; orphaned unpublished generations after a 1-hour grace). There is no user-facing way to restore an older generation; the previous generation exists only until the next successful backup. Server-side recovery of older generations would require Firestore point-in-time recovery or export, which is not evidenced as configured (OPS-OPEN-05).

[IMPLEMENTED] `CloudSyncService` uses the Firestore **REST API** with the user's ID token (not the Firestore SDK) and stores backups under `users/{uid}`:

- Every backup writes a **full, immutable generation** `g<micros>` at `users/{uid}/generations/{id}/{people,birthdays,drafts,reminderSettings}`. The manifest is written first, then the pointer `backup/current` is moved **last**, so a restore never observes a partially written generation. Timestamps are UTC.
- **Retention (new, A3-002/A4-006).** After publishing, the service keeps the **current and the previous** published generations; it deletes older published generations and **orphaned unpublished** generations older than 1 hour (`_orphanGracePeriod`). Documents are deleted before the manifest. Pruning is **best-effort**: failures are logged and swallowed, and at most 5 generations are deleted per run. `[OPEN]` There is no server-side garbage collection of orphan generations ([Appendix B](#appendix-b--open-decisions-register)).
- Firestore rules: the owner may read/write `backup`, `generations` and legacy collections; generation documents are create/delete only; writes are denied while an account deletion is in progress; the entitlement document is owner-read and server-write only.

### 10.2 Restore

[IMPLEMENTED] Restore reads the pointer and manifest (the manifest `schemaVersion` is checked), validates **all collections against the manifest before any local write**, then applies everything in **one database transaction**. Conflict rules: a local copy that is equal or newer wins; a local tombstone outranks a live cloud copy. Conflicts are recorded. `[OPEN]` The restore does not carry the draft `revision` column.

Operator checklist for a user-facing restore failure: confirm the user is signed in with the same Google account; confirm the Firestore rules are deployed (owner read access); confirm the pointer and manifest exist for the account (ask the user — operators must not read user data without an approved support process, `[OPEN]` DEC-052); then retry restore. Local data is untouched if validation fails.

### 10.3 Server backups (Firestore)

`backend/README.md` step 7 says to disable managed backups/point-in-time recovery for the "minimal ledger". Firestore backup/PITR is a **per-database** setting and the same database holds user cloud backups, entitlements and the purchase-ownership ledger, so the instruction is ambiguous `[OPEN]` OPS-OPEN-05. Decide the policy explicitly and record it before enabling or disabling anything. There is no automated export of Firestore data, and no restore runbook `[NOT IMPLEMENTED]`.

### 10.4 Account deletion

[IMPLEMENTED] Deletion is a server-side saga driven from the Hosting `/delete/` page (there is no in-app entry `[OPEN]`):
1. `requestAccountDeletion` requires a recent Google sign-in (≤5 minutes) and a consumed App Check token.
2. It creates a deletion tombstone, which **fences** all writes for the account (rules and Functions refuse writes while the tombstone exists or presence is `DELETING`), then drains in-flight work.
3. Recursive purge of `accounts/{uid}` and `users/{uid}`, then purge of `subscriptionPurchaseOwnership` documents owned by the uid (A4-002 fix).
4. Verification that the data is absent, deletion of the Firebase Auth user, then finalization: tombstone deleted and a `COMPLETED` receipt written.
5. Repair sweeps retry with backoff from 1 minute up to 1 hour (attempt counter capped at 30).
6. `accountDeletionReceipt` is a signed-out lookup by receipt id (a domain-separated SHA-256 UUID). A `COMPLETED` receipt expires by TTL after 365 days; `IN_PROGRESS` has no TTL.
7. While a deletion is in progress `verifyPurchase` refuses with `ACCOUNT_DELETION_IN_PROGRESS`.

Known gap: `releaseAndroidSender` still purges `users/{uid}` (A4-003; unreachable in the current flow, `[OPEN]`).

### 10.5 Retention windows (server data)

| Data | Retention |
|---|---|
| Never-armed authorization | Expires 10 min; physical cleanup after 24 h |
| Birthday armed/outcome/guards/aliases | 400 days |
| TEST records | 30 days |
| STANDBY installation | 90 days |
| REVOKED installation | 30 days |
| Operation receipts | 30 days |
| Deletion receipt (`COMPLETED`) | 365 days |
| Cloud backup generations | Current + previous published (client-pruned); no TTL |

These depend on TTL policies being enabled ([§7.2](#72-functions-and-firestore)) `[OPEN]` until deployment is evidenced.

## 11. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `ValueNotifier` / `SubscriptionNotifier` "A … was used after being disposed" or `StateError` on double dispose in a widget test | `SubscriptionNotifier.dispose()` disposes its `_purchaseUi` notifier and `super.dispose()` throws on a second dispose. Riverpod owns and disposes the notifier on unmount | Do **not** dispose the notifier manually in `tearDown`, and do **not** reuse one notifier across several `pumpWidget` calls; create a fresh notifier per pump. Examples: `settings_cloud_job_test.dart`, `settings_notification_revoked_test.dart` |
| `npm run test:emulator` fails to start or tries to download a jar | Installed `firebase-tools` expects Firestore emulator jar v1.22.0; the cache may hold only v1.21.0; needs Java 21 and network | Install JDK 21, ensure network (and `NODE_EXTRA_CA_CERTS` behind a corporate proxy), rerun; offline machines cannot run it |
| `Waiting for another flutter command to release the startup lock` | Another `flutter`/`dart` process holds the SDK lock (observed during this transformation; general Flutter behavior, not repo-specific) | Wait for or stop the other process, then retry; do not delete SDK files while one is running |
| Timezone-dependent test failures on one machine | Tests depend on local TZ and the v7 migration derives calendar days from stored values | Run `birthday_lifecycle_timezone_test.dart` under `TZ=UTC`, `America/New_York`, `Asia/Kolkata` ([§4.1](#41-flutter)) |
| `GoogleSignInException [16] Account reauth failed` | Debug or release SHA-1 not registered for `com.yashsomani.ai_birthday` | Register fingerprints ([§2.3](#23-sign-in-and-app-check-prerequisites)) |
| Callables fail with App Check errors in a debug build | Debug token not registered | Register the debug App Check token in the Firebase console |
| Purchases fail on an adb/side-loaded install | Play Billing requires a Play-installed build | Use an internal-testing install and a license-tester account ([§13](#13-play-billing-and-purchases-setup)) |
| Release APK is unsigned / "not installable" | `android/key.properties` missing | Provide `key.properties` and keystore locally; never commit them |
| CI step "Play permission guard" fails | A forbidden permission appears in the Play APK, `aapt2` or the SDK root is missing, or the permission list could not be parsed | Read the step output; fix the manifest source (often a plugin) or the runner SDK path. The guard fails closed by design |
| CI fails on `FIREBASE_WEB_API_KEY` | Secret unset (empty define) | Set the repository secret ([§2.2](#22-firebase-client-configuration-names-only)) |
| Release build fails resolving Gradle plugin or Kotlin artifacts | A historical failure in the release build was recorded without root cause | Run `flutter clean`, check network/proxy and Gradle caches; if it persists record the exact error in IMPLEMENTATION.md |
| Gradle/Flutter warnings about plugins applying Kotlin Gradle Plugin | Third-party plugins; the Play debug APK still builds (about 25 s in the baseline) | Informational |
| `hosting` build fails with release-config errors | Missing or placeholder approval fields, or `supportUrl` on the same origin | Fix the out-of-repo release config ([§7.3](#73-hosting-release-configuration)) |
| Hosting page blocked scripts for Google sign-in | Missing CSP allowances (fix applied, untested) | Verify in a browser after deploying; adjust `script-src`/`frame-src` |

## 12. Release procedure and checklist

Readiness *criteria* (gates, evidence required, owners) are in [IMPLEMENTATION.md](IMPLEMENTATION.md) §8. This section is the operational procedure.

**Release blockers (any one blocks a release).** An unapproved product variant is present; a destructive migration lacks an inventory and rehearsal; the release environment, signing or region cannot be identified; the Play permission guard fails; the capability gates fail; production claims lack a named deployment, device and evidence.

**Procedure.**
1. Freeze the commit; confirm the working tree is clean and `git rev-parse HEAD` is recorded.
2. Run the full verification set: format, `flutter analyze`, Flutter tests, goldens, TZ matrix, migration tests, Functions `npm run check` and `npm run test:emulator`, Hosting `npm run check` ([§4](#4-test-commands)). Record counts.
3. Check CI is green on the commit (all three jobs).
4. Run `npm audit` for Functions and Hosting and remediate or document high-severity advisories `[OPEN]`.
5. For a database change: complete [§5.3](#53-migration-gates-and-safety-rules) (backup, inventory, rehearsal) and bump the schema version only with a migration test.
6. Build signed release artifacts with the production keystore on a controlled machine `[OPEN]`; produce an AAB for Play `[NOT IMPLEMENTED]`; verify permissions with `aapt2 dump permissions` (Play flavor must not contain SMS/call-log permissions).
7. Deploy backend pieces in order: Firestore rules/indexes/TTL → Functions → Hosting ([§7](#7-deployment)), each with explicit `--project`, after the required approvals; probe `verifyPurchase` ([§13](#13-play-billing-and-purchases-setup)).
8. Register App Check (Play Integrity) and the debug/release SHA-1 certificates.
9. Upload to an internal Play track; run physical-device QA; run a real purchase and restore with a license tester; then promote through closed testing to production with a staged rollout `[NOT IMPLEMENTED]`.
10. Record the evidence (commit, schema version, environment, region, signing identity, test results) with the release.
11. Update documentation per [§14](#14-documentation-maintenance-procedure).

**Checklist.**
- [ ] No unapproved flavor/capability enabled; capability grep gate and Play permission guard green
- [ ] Production signing identity exists and the artifact is signed
- [ ] Backend deployment attested; TTL enabled; App Check production registration done
- [ ] Debug and release SHA-1 registered
- [ ] Play product `ai_birthday_pro_monthly`, console service-account access and internal track exist; real purchase and restore tested
- [ ] Hosting release config approved; legal/support pages approved; deletion workflow tested end-to-end
- [ ] Legal: `LICENSE` file present or the README claim corrected (OPS-OPEN-06)
- [ ] Not yet device-tested items completed: Settings sign-in and purchase; WhatsApp hand-off with its confirmation dialog; reminder scheduling and recovery after reboot (per the device QA audit)
- [ ] Physical-device QA on the supported device matrix (including Gemini Nano–capable devices)
- [ ] Rollback plan: forward-only migrations; the previous APK cannot read a v7 database — never promise downgrade
- [ ] Release evidence and docs updated; validator passes ([§14](#14-documentation-maintenance-procedure))

**Outstanding gates (as of this snapshot).** Production Play keystore and closed-testing track; physical-device QA; deployed and attested backend; Play Integrity/App Check production registration; legal/support/Hosting readiness; npm advisory remediation; AAB; operational runbooks.

## 13. Play Billing and purchases setup

Entitlement for the Pro subscription (`ai_birthday_pro_monthly`) is granted by the `verifyPurchase` callable, which checks the Google Play Developer API server-side. Setup is **required** before any production purchase can work. `[OPEN]` none of it is evidenced as done. The product-level behavior is in [PRODUCT.md](PRODUCT.md); the verification design is in [ARCHITECTURE.md](ARCHITECTURE.md).

### 13.1 Steps

1. **Install and authenticate.** `firebase-tools` available (`npx firebase-tools`), `firebase login` (interactive), `cd backend`.
2. **Set the secret.** `firebase functions:secrets:set COORDINATION_HMAC_KEYRING` (a JSON keyring). `[UNCONFIRMED]` `verifyPurchase` itself uses `commonOptions` and binds no secrets, so this step may not be needed for purchase verification alone; it is required for `claimOccurrence`/`claimTest`. Confirm before relying on it.
3. **Set the parameter.** Provide `CONTROL_PLANE_SERVICE_ACCOUNT` (Functions param; the deploy prompts or reads `.env*`).
4. **Configure App Check (Play Integrity)** for the project. `verifyPurchase` always enforces and consumes App Check; without a registered provider every call is rejected. (The legacy setup guide did not state this prerequisite.)
5. **Deploy:** `cd backend && npx firebase-tools deploy --project <explicit-id> --only functions:verifyPurchase`.
6. **Probe the deployment** (no credentials; an unauthenticated empty call):
   ```bash
   curl -i -X POST -H 'Content-Type: application/json' -d '{"data":{}}' \
     https://asia-south1-relateai-birthday-ysoman-a2372.cloudfunctions.net/verifyPurchase
   ```
   `400`/`401` means the function is deployed (it rejected the empty call); `404` means it is **not** deployed. The last recorded probe returned `404`; deployment status is unverified `[OPEN]`.
7. **Grant the runtime service account Play Console access** for `com.yashsomani.ai_birthday`: invite the service account named by `CONTROL_PLANE_SERVICE_ACCOUNT` in Play Console → Users and permissions, with permission to view subscriptions/orders. The function calls the `androidpublisher.googleapis.com` v3 API using the runtime metadata-server token; **no key file exists in the repository**. `[UNCONFIRMED]` Enabling the Android Publisher API in the Google Cloud project is also expected.
8. **Create the subscription** in Play Console: an auto-renewing subscription with product id `ai_birthday_pro_monthly` for package `com.yashsomani.ai_birthday`. Both identifiers are hard-coded in `backend/functions/src/services/subscriptionVerification.ts` and in the app's `subscription_service.dart`; changing either requires a code change.
9. **Test only from Play.** Upload to an internal-testing track, install from Play with a license-tester account. Side-loaded or `adb`-installed builds cannot buy.

### 13.2 Behavior notes

- The app probes the endpoint with `POST {"data":{}}` before opening billing; each probe consumes a limited-use App Check token and an invocation (W7).
- The purchase token is bound to the account with SHA-256 (A3-001 fix); the ownership ledger `subscriptionPurchaseOwnership` is purged on account deletion, and `verifyPurchase` refuses during deletion.
- Purchase verification requires, beyond Play linkage, a real **acknowledgement**, **restore**, and **expiry/revocation** handling to be validated against Play (`[OPEN]`; not exercised against real Play).

### 13.3 User-visible messages (current `settings_screen.dart`, `_purchaseFailureMessage`)

| Outcome | Message |
|---|---|
| Sign-in required | "Sign in with Google to subscribe to Pro." |
| Store unavailable | "Google Play Billing is not available on this device." |
| Product unavailable | "Pro is not available on this install. Install the Google Play version." |
| Verification unavailable | "Pro purchases are temporarily unavailable. You have not been charged. Try again later." |
| Verification failed | 'We could not verify your purchase yet. Tap "Restore Purchases" to retry; do not buy again.' |
| Purchase error | "Google Play could not complete the purchase. Try again." |
| Cancelled | "Purchase cancelled. You have not been charged." |
| Success | "Pro verified. AI features are unlocked." |

The legacy setup guide's table of messages was partly stale; this table reflects the code.

## 14. Documentation maintenance procedure

### 14.1 What must stay in sync

The canonical set is `README.md`, `PRODUCT.md`, `ARCHITECTURE.md`, `SECURITY.md`, `IMPLEMENTATION.md`, `OPERATIONS.md`, `PROJECT_MINDMAP.md`, plus the package READMEs `backend/README.md` and `backend/hosting/README.md`. Supporting machine-readable graph data under `docs/graph/` includes `feature-registry.json`, `graph-overlay.json`, `issue-register.json`, `master-project-graph.json`/`.dot`, `traceability-matrix.csv`, and `migration-matrix.csv`; the human-facing mind map remains Markdown.

| Change | Update |
|---|---|
| Behavior or scope change | PRODUCT.md; feature registry (status, source, tests); IMPLEMENTATION.md status |
| Architecture, schema, API change | ARCHITECTURE.md; graph overlay; this handbook if commands/versions change |
| Security-relevant change | SECURITY.md and the issue register |
| Build, CI, deployment, config change | OPERATIONS.md (this file) |
| New/closed issue | `issue-register.json` and its entry in IMPLEMENTATION.md |

### 14.2 Rules

- Keep stable IDs through renames; never reuse a retired ID.
- Describe code/documentation mismatches rather than quietly rewriting intent; link the issue.
- Mark a feature implemented only when its code is located; claim "runtime-verified" only with named conditions (device, build, environment). CI success is narrower than production verification.
- Re-check links, Mermaid diagrams and source references after edits.
- **Never copy secrets, recipient data or private content into documentation.**
- Do not link canonical docs to retired paths (`docs/product-blueprint`, `docs/audit`, `docs/archive`, `docs/ui-*`, `docs/knowledge`, `SSOT.md`, `PROJECT.md`, `DESIGN*.md`, `docs/purchases-setup.md`); cite them in prose with the recovery commit.
- Archived plans stay archival.

### 14.3 Validate and regenerate

```bash
python3 docs/graph/tools/validate_docs.py            # exit code 0 = pass; add --format json for machine output
python3 docs/graph/tools/build_graph.py              # regenerate master-project-graph.json/.dot
python3 docs/graph/tools/build_graph.py --check      # fail if committed graph outputs are stale
python3 docs/graph/tools/build_mindmap.py            # regenerate PROJECT_MINDMAP.md from the graph
python3 docs/graph/tools/build_mindmap.py --check
```

The validator (offline, standard library) checks: balanced code fences; relative links and `#anchors` in the canonical Markdown files; Mermaid blocks start with a known diagram type; no links to retired paths; feature-registry IDs, parents, cycles and source/test paths; graph IDs, parent tree, edge endpoints and relationship types, evidence paths, 25 branches, **generated outputs up to date**; traceability-matrix IDs exist; every issue-register ID is documented in `IMPLEMENTATION.md`; every migration-matrix row points at an existing canonical heading and anchor.

**Procedure.** (1) Edit the sources of truth: `feature-registry.json`, `graph-overlay.json`, `issue-register.json`. Never hand-edit `master-project-graph.*` or `PROJECT_MINDMAP.md`, because they are generated. The narrative parts of the mind map are in `docs/graph/tools/mindmap-parts/*.md`. (2) Run `build_graph.py`, then `build_mindmap.py`. (3) Edit canonical Markdown. (4) Run the validator; fix every error. (5) Commit sources and generated outputs together. When you rename or move a heading that the migration matrix points to, update the matrix row in the same change. The validator is **not** wired into CI `[OPEN]` OPS-OPEN-15; run it locally before merging.

---

## Appendix A — Superseded, rejected and historical items

- **Migration Gates 4 and 5** (repair and constraint preflight; constraints and staged rollout): superseded. Schema v7 enforces a unique `birthdays.person_id` index after an in-migration dedupe, so a separate preflight and rollout stage were not used. Legacy source: `docs/product-blueprint/**` migration gates (removed; recoverable from commit `c4aace16`).
- **"Ten minimum runbooks … still valid" claim:** superseded as a statement of fact — no runbooks exist in the repository. The list of ten remains as an open work item in [§9.3](#93-runbooks-not-implemented).
- **README MIT licence claim:** the claim is still *made* but no `LICENSE` file is tracked; this is recorded as an open legal decision (OPS-OPEN-06).
- **ESM test fallback to `node:crypto` webcrypto** (legacy backend-test note): no such code was found in `backend/functions` `src` or `test`; the Hosting package carries a committed `deletion-contract.js` twin with a `node:crypto` fallback (A4-009). Treat the legacy statement as unverified for Functions.
- **Namespace collision DEP-009:** the legacy requirement that a named deployment, device and evidence are needed before production claims (owner: Release) is retained as the release-gate rule in [§12](#12-release-procedure-and-checklist). The ID collided with another dependency namespace in the legacy blueprint; it is not carried forward.
- **Legacy expectation of `user_version` 5** in the inventory procedure: stale; see [§5.3](#53-migration-gates-and-safety-rules).
- **Stale README statements** (Dart >=3.11.0, "local-only" goldens, `runtime-config.json` ignored by Git, Android API 35): corrected in the relevant sections above; see OPERATIONS-notes for the full list.

## Appendix B — Open decisions register

| ID | Decision / gap | Tag |
|---|---|---|
| OPS-OPEN-01 | Public Hosting copy (Android automation, iOS/React Native wording) conflicts with the shipped Flutter app (A4-007); needs product and legal approval | `[OPEN]` |
| OPS-OPEN-02 | No in-app account-deletion or backup-delete action; support/privacy tiles lack handlers (A3-003) | `[OPEN]` |
| OPS-OPEN-03 | App Check is not attached to Firestore REST / Identity Toolkit calls (A3-004, A4-012) | `[OPEN]` |
| OPS-OPEN-04 | Production environments, signing keystore, Play track, deployment ownership and evidence; support operating model (DEC-052); performance/availability budgets (DEC-053) | `[OPEN]` |
| OPS-OPEN-05 | Firestore backup/PITR policy for the shared database vs the README "disable" instruction | `[OPEN]` |
| OPS-OPEN-06 | No `LICENSE` file although README claims MIT | `[OPEN]` |
| OPS-OPEN-07 | CI passes an empty `FIREBASE_WEB_API_KEY` define if the secret is unset | `[OPEN]` |
| OPS-OPEN-08 | Golden test runs on Ubuntu CI but comments say "local-only"; stale golden artifacts tracked | `[OPEN]` |
| OPS-OPEN-09 | `runtime-config.json` is not git-ignored although a committed build output directory exists | `[OPEN]` |
| OPS-OPEN-10 | TTL list drift: `deletionReceipts` in `ttl-policies.json` but not in `firestore.indexes.json` (A4-015) | `[OPEN]` |
| OPS-OPEN-11 | Soft-deleted personal data retention (A1-011) | `[OPEN]` |
| OPS-OPEN-12 | Server-side garbage collection for orphan backup generations | `[OPEN]` |
| OPS-OPEN-13 | `npm audit` for Hosting; remediation of advisories; AAB build | `[OPEN]` |
| OPS-OPEN-14 | CSP browser test; `frame-src` for auth domain | `[OPEN]` |
| OPS-OPEN-15 | Wire the documentation validator and `build_graph.py --check` into CI | `[PROPOSED]` |
| OPS-OPEN-16 | Operational runbooks, dashboards, alerting and crash reporting (§9) | `[NOT IMPLEMENTED]` |
