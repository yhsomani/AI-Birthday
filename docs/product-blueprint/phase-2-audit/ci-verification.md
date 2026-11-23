# Phase 2 — CI Verification Evidence

**Workflow:** CI / Release Pipeline  
**Run:** [45228379958](https://github.com/yhsomani/AI-Birthday/actions/runs/45228379958)  
**Commit:** `ea475e0135a99d5dbae9891340c4a554f2e5fe15`  
**Event:** push  
**Run interval:** 2026-11-23 17:30:19Z–17:33:08Z  
**Overall conclusion:** failure

This section reports the exact latest GitHub Actions run inspected. It does not substitute for local execution or device verification.

## Job summary

| Job | Result | Evidence |
|---|---|---|
| Flutter Lint, Test & Build | **Failure** | Formatting and analysis passed; Flutter test suite failed |
| Backend Security & Cloud Functions | **Failure** | `npm ci` failed before audit, lint, compile, Vitest or emulator tests |
| Public Site & Account Deletion Hosting | **Success** | Dependency installation and `npm run check` passed |

## Flutter job

Observed steps:

- Flutter dependencies installed.
- `dart format --output=none --set-exit-if-changed .` passed.
- `flutter analyze` passed.
- `flutter test --coverage --exclude-tags golden` failed.

The test summary in the job log is **459 tests passed, 1 failed**.

Failed test:

```text
test/ui/responsive_mobile_test.dart
Multi-Device Responsive Mobile Tests
Calendar adapts safely to 320px compact mobile screen without overflow
```

Observed assertion:

```text
Expected: null
Actual: FlutterError:
A RenderFlex overflowed by 0.667 pixels on the bottom.
```

The test mounts `CalendarScreen` at a 320×568 logical test viewport with devicePixelRatio 1.0, supplies one person, pumps the widget and asserts that `tester.takeException()` is null. The assertion fails due to the reported vertical overflow.

**Root cause status:** the failing screen and test scenario are known. The exact widget/layout constraint causing the overflow has not yet been isolated. Do not change layout based only on the 0.667-pixel symptom.

Because the Flutter test step failed, these later steps were skipped: Play Store APK build, Play manifest SEND_SMS check, Personal APK build and artifact uploads. Their status is **not run**, not pass.

## Backend job

The job uses Node.js 22. The log reports:

```text
package requires: node >=22 <25, npm 11.6.0
runner provides:  node v22.23.5, npm 10.9.9
```

The npm engine mismatch is emitted as `EBADENGINE`. The hard failure is `npm ci` rejecting the committed lockfile as inconsistent with `package.json`.

Representative errors from the log:

- `@firebase/util`: lockfile 1.15.1 does not satisfy required 1.15.3.
- `superstatic`: lockfile 9.2.0 does not satisfy required 10.0.0.
- `tar`: lockfile 6.2.1 does not satisfy required 7.5.23.
- Missing lock entries include `@google-cloud/pubsub@4.11.0`, `uuid@8.3.2`, `yallist@5.0.0`, `node-fetch@3.3.2` and other transitive dependencies.

The failure occurs at the workflow's dependency-install step. Therefore the following backend checks did not execute in this run:

- Production dependency audit
- ESLint
- TypeScript build
- Vitest suite
- Firestore emulator tests

The package manifest declares `"packageManager": "npm@11.6.0"`, while the workflow configures Node 22 without explicitly installing npm 11.6.0. Treat this as a reproducibility/toolchain mismatch to resolve alongside the lockfile drift; the log's fatal error is the lockfile inconsistency.

## Hosting job

The Hosting job completed successfully. It ran:

- `npm ci`
- `npm run check` (typecheck, tests and production build, as defined by the hosting package)

This is CI evidence for the repository's hosting package at this commit. It is not evidence that the Hosting site is deployed or that its production environment is configured correctly.

## Required remediation order (recommendations only)

1. Reproduce the calendar test and isolate the exact RenderFlex constraint; fix only after the owning code change is authorized.
2. Reconcile backend `package.json`, `package-lock.json` and the CI npm version. Regenerate the lockfile using the declared package manager/version, then validate with clean `npm ci`.
3. Rerun the complete workflow and capture new commit-specific evidence.
4. Do not mark backend verification or Android release builds as passed until the relevant steps execute successfully.

No source/configuration change was made during this audit.
