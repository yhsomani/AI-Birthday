# Current CI Verification — Main Branch

**Workflow:** CI / Release Pipeline  
**Run:** [45245907678](https://github.com/yhsomani/AI-Birthday/actions/runs/45245907678)  
**Run number:** 642  
**Commit:** 644571f68be024423376749fe8e50e1db02ff018  
**Commit message:** fix(calendar): improve message formatting for empty state  
**Run interval:** 2026-11-23 19:09:14Z–19:18:48Z  
**Overall conclusion:** success

This report supersedes the “latest CI” claims in the older Phase 2 / Phase 2.1 snapshots. Those files are retained as historical evidence. The current main branch passed its latest observed workflow run.

## Job summary

| Job | Result | Verified steps |
|---|---|---|
| Flutter Lint, Test & Build | PASS | Dependency install, format, static analysis, Flutter tests, Play APK build, Play manifest inspection, Personal APK build, artifact uploads |
| Backend Security & Cloud Functions | PASS | Node/npm setup, npm ci, production dependency audit, lint, TypeScript compile, Vitest, Firestore emulator tests |
| Public Site & Account Deletion Hosting | PASS | npm ci, typecheck, Node tests and production build |

## Flutter job

Job ID: 137942470014

Observed results:

- Formatting verification passed.
- Static analysis passed.
- Flutter test suite completed with **460 tests passed**.
- Play flavor release APK built as a 68.5 MB CI artifact.
- The merged Play manifest inspection confirmed android.permission.SEND_SMS was absent.
- Personal flavor release APK built as a 68.5 MB CI artifact.
- Both artifacts uploaded successfully:
  - Play artifact ID: 14853964107.
  - Personal artifact ID: 14854393183.

The latest run no longer reports the earlier 320px Calendar overflow. The latest commit diff itself only reformats the Calendar empty-state message; the immediately preceding Calendar refactor is part of the tested parent history. This CI result proves the current test suite passed in this run, not that the earlier failure's root cause was independently isolated or that all device sizes are covered.

The workflow describes the Play APK as an unsigned CI artifact. Do not represent either uploaded artifact as a signed Play-ready package. No physical Android device test was performed by this job.

## Backend job

Job ID: 137942469373

Observed successful steps:

- Required npm version installation.
- Clean dependency installation.
- Production dependency audit.
- ESLint.
- TypeScript compile.
- Vitest: **11 test files passed**.
- Firestore emulator suite: **2 test files passed**.

Earlier Phase 2.1 logs showed dependency installation failing because the runner's npm version and committed lockfile were inconsistent. The current workflow now installs the declared npm version and completes all backend steps. The prior failure is historical, not current main status.

The emulator tests provide rule/function test evidence in the emulator environment. They do not prove production Firestore rules or Functions have been deployed.

## Hosting job

Job ID: 137942470024

The Hosting job completed successfully:

- Dependency installation.
- Typecheck.
- Node test suite: **12 tests**.
- Production build.

This proves the repository Hosting package passed CI checks. It does not prove Firebase Hosting deployment, production configuration, live deletion flow or external legal review.

## Evidence boundaries

| Claim | Supported? | Reason |
|---|---|---|
| Latest main CI is green | YES | Workflow run conclusion is success; all three jobs succeeded |
| Flutter suite passed in this run | YES | Log summary reports 460 tests passed |
| Backend tests ran and passed | YES | 11 Vitest files and 2 emulator test files passed |
| Both Android flavor artifacts built | YES | CI build steps and artifact uploads succeeded |
| Play manifest lacks SEND_SMS | YES | Merged-manifest check passed |
| APK is signed and store-ready | NO | Workflow identifies Play artifact as unsigned; store review not evidenced |
| Physical Android notifications/SMS/Nano work | NO | No physical-device execution in this workflow |
| Google OAuth, Play Billing, live Stripe, Gemini and live Firestore work | NO | CI uses test doubles/mocks for service contract tests; no live-provider verification established |
| Firebase Functions or Hosting are deployed | NO | Build/test steps are not deployment evidence |
| Product requirements are stakeholder-approved | NO | CI cannot approve product scope, business policy or commercial decisions |

## Required follow-up

1. Preserve this run as the current CI baseline until a newer main-branch run supersedes it.
2. Add a separate device verification record for notification permissions, exact alarms, reboot, timezone changes, contacts, Nano readiness, SMS flavor behavior and messaging handoff.
3. Exercise Google OAuth, Play purchase/restore, Gemini API, Firestore backup/restore and account deletion in controlled live test environments.
4. Resolve the P0 Stripe client-secret architecture issue before enabling or advertising production Stripe billing.
5. Inspect deployment targets and actual release signing separately from repository CI.