# Release Checklist

Last reviewed: 2026-07-11

This checklist is the production gate for RelateAI. A release is not ready until every required item is completed against the exact build submitted to distribution.

## Build and Test Gate

- Use the pinned Node 24.18.0/npm 11.6.0 toolchain (`nvm use`, `corepack enable npm`) and install from the lockfile with `npm ci`. Confirm `npm --version` prints exactly `11.6.0`.
- Prove the clean-checkout order before evidence generation:

  ```bash
  test ! -e reports/react-native-release-evidence.json
  node --import tsx src/config/releaseEvidenceCli.ts --source-only --fail-on-blockers
  ```

- Generate CI/source validation with `node --import tsx src/config/releaseEvidenceCli.ts --source-only --fail-on-blockers`. This mode executes every source/native command gate but is explicitly not production approval; pending signed-build, device-smoke, and store-submission items remain warnings.
- Generate the final release record with `node --import tsx src/config/releaseEvidenceCli.ts --production --device-evidence=/path/to/device-evidence.json --fail-on-blockers`. The external JSON array must contain the five signed Android/iOS build, Android/iOS device-smoke, and store-submission items with their exact ids and `Attached`, `Pending`, or `Failed` status. Every `Attached` item must include a schema-versioned record with a unique evidence id, timestamp, owner, credential-free HTTPS source URL, exact commit, working-tree SHA-256, app version, and applicable signed artifact SHA-256 values. Device-smoke records also identify platform, device, OS, and test run; store evidence identifies both Google Play and App Store Connect records. Production mode blocks free-form `Attached` claims, candidate/artifact mismatches, malformed, duplicated, missing, pending, or failed evidence. Generated-native/legacy status is derived from the checkout and cannot be supplied externally.
- The checked-in CLI executes typecheck, lint, formatting, thresholded coverage, native prebuild/debug compile, audit, Expo dependency compatibility, and diff checks itself; CI invokes it directly once rather than trusting a mutable package alias or duplicating those gates. The CLI validates the exact package alias and every inner gate script before accepting command results. No environment variable or manual report edit can mark a command passed through the supported workflow. Web is excluded because this release has no protected web persistence adapter.
- Keep both the React Native `npm test` and thresholded `npm run test:coverage` scripts on the quoted full source-contract glob without per-file process isolation. Release evidence validates their exact command bodies before accepting command results.
- Attach the CI-produced source-only `reports/react-native-release-evidence.json` and its GitHub provenance attestation to the release record. Confirm it names the exact release commit, reports `dirty: false`, matches the submitted lockfile hash and exact npm 11.6.0 version, and contains exit-code/timing/output-hash proof for every required command. The separate final production assessment must contain no blockers; pending or failed signed-build, device-smoke, or store-submission evidence is a blocker, never production-ready warning. A release owner must open and verify every linked primary evidence record; the JSON validator does not replace that external review.
- The report is an ignored operational artifact. It must never be imported by `src/App.tsx`, required by typecheck/test/bundle, or displayed as attached runtime evidence. Setup Check must safely report it as unattached.
- Confirm the exhaustive command catalog and parser/runtime suites remain green. Every retained feature must be reachable through a strict command, including preview/confirm, cancellation, lock, and failure recovery boundaries.
- Confirm the temporary-UI surface contract remains green: exactly the command shell and render-error boundary are active React components, with no theme, styling, visual assets, icons, animations, or obsolete design contracts.
- Confirm the active localization contract remains green for the functional console, notifications, home widget, locale resolution, pluralization, date/month formatting, and currency formatting. Deleted feature-screen copy is not an active release contract.
- Confirm persistence, encrypted repository, migration, storage-health, backup/restore, interrupted lifecycle recovery, cryptographic erasure, and local-data clear fault-injection tests remain green.
- Confirm contact/event import, identity conflict, lifecycle, recurrence, preparation, check-in, memory, gift, preference, tone, and relationship-health tests remain green.
- Confirm drafting, templates, message review/approval, duplicate prevention, scheduling, provider email idempotency/reconciliation, manual handoff, and activity recovery tests remain green.
- Confirm permission, reminder, notification-route, widget, shortcut, calendar, file-import, biometric, deep-link, Android-back, and native adapter tests remain green.
- Confirm provider endpoint/session, request filtering, streaming response bounds, fallback, redaction, analytics sharing, setup, and product-availability tests remain green.
- Confirm large-dataset and dirty-write repository tests remain green after changing queries, persistence, analytics, widget summaries, or exports.
- Confirm `app.json` contains stable RN native identifiers, `runtimeVersion.policy = appVersion`, iOS `buildNumber`, Android `versionCode`, and no direct SMS, SMS inbox, call-log, phone-number, exact-alarm, or AccessibilityService permissions. The `react native release configuration contract` test protects this.
- Confirm the Android home widget plugin and app-shell contracts remain green after changing widget summary payloads, widget routes, Expo config plugins, Android manifest receivers, generated widget resources, JS-to-native widget sync, or external-surface privacy copy.
- Confirm `eas.json` contains development, preview, and production profiles; production Android must build an app bundle and production iOS must not target simulator builds.
- Produce signed EAS production builds for Android and iOS with release credentials before store upload.
- Confirm release evidence records `activeReleaseSurface.legacyKotlinGradleArtifactPaths` as an empty array. Treat any checked-in `android/` or `ios/` generated native tree, or reintroduced legacy Android/Gradle path, as release evidence drift that must be resolved before signing.
- Run focused release-risk tests when changing permissions, dispatch, backup, auth, localization, or navigation.
- Confirm pull requests with dependency changes pass GitHub Dependency Review for moderate-or-higher vulnerabilities and denied licenses.
- Run `git diff --check` before handoff.
- Confirm no generated, local, signing, or secret files are accidentally staged.
- Confirm provider config changes do not add credentials, backend secrets, signing material, or platform service keys to the client repository.

## Temporary Functional Harness Device Gate

- Treat the command shell only as an execution harness; do not spend release work on visual polish, screenshots for aesthetic approval, themes, icons, animation, or feature-screen layout.
- On the exact signed candidate, verify that the command input, secure secret input, execute action, redacted output, operation list, issue list, and render-error retry remain operable and accessible with English, Hindi, and Hinglish settings.
- Run `system.catalog` and representative read, mutation, preview/confirm, native-adapter, cancellation, lock/unlock, backup, destructive-clear, and `data.recover` commands on Android and iOS.
- Verify widget, shortcut, notification, calendar, contact import, backup file, biometric lock, and channel handoff flows on physical devices or approved release-device simulators where platform policy allows.
- Generated Expo/native artifacts, reports, and device evidence must remain unstaged. Upload release evidence through the CI/release record instead of committing it or coupling it to runtime source.

## Play Policy Gate

### AccessibilityService API

Source: https://support.google.com/googleplay/android-developer/answer/10964491

Current status: not part of the React Native release surface.

The React Native release path uses manual WhatsApp handoff through user-controlled Linking/share flows. It must not ship an Android AccessibilityService for WhatsApp automation unless a future release-owner decision explicitly adds that capability with policy review.

Release requirements:

- Confirm the React Native build does not request AccessibilityService privileges or contain unattended WhatsApp automation.
- Confirm WhatsApp handoff opens only after the user approves message text and chooses the handoff action.
- Confirm opening WhatsApp, SMS, email, or a share sheet never marks a message sent without explicit user confirmation.
- If a future release adds AccessibilityService behavior, complete a separate Play policy review, prominent disclosure, consent, revocation, no-chat-reading, and no-send dry-run evidence before distribution.

Signoff record:

| Item                                              | Owner | Date | Status  | Notes                                                                                                                                                         |
| ------------------------------------------------- | ----- | ---- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| RN build excludes AccessibilityService automation | TBD   | TBD  | Pending | Source config and release evidence block `android.permission.BIND_ACCESSIBILITY_SERVICE`; signed-build manifest review is still required before Play release. |
| Manual WhatsApp handoff reviewed                  | TBD   | TBD  | Pending | Verify approved-text-only handoff and explicit mark-sent confirmation.                                                                                        |
| Data Safety consistency reviewed                  | TBD   | TBD  | Pending | Must match privacy policy, store listing, and final build.                                                                                                    |
| Future WhatsApp automation decision               | TBD   | TBD  | Pending | Required only if unattended automation is reintroduced.                                                                                                       |

### User Data and Data Safety

Sources:

- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/16558241

Release requirements:

- Privacy policy URL is public, non-geofenced, and describes all personal/sensitive data handling.
- Data Safety form matches final app behavior and privacy policy.
- Contacts, SMS, AI prompt/response data, backups, notifications, auth data, dispatch diagnostics, and local diagnostic snapshots are represented accurately.
- In-app disclosures precede sensitive permission or API use when required.
- Denying a non-critical permission keeps a reasonable app path available.
- Account deletion and local sign-out behavior are documented and tested.

### SMS

- Verify the app does not request `SEND_SMS`, SMS inbox, phone-number, or call-log permissions.
- Verify SMS remains a user-initiated handoff of approved text to an available destination app.
- Verify opening the destination never marks the message sent without explicit user confirmation.
- Verify an unavailable SMS app falls back to approved-text copy/share guidance without hidden dispatch.

### Exact Alarms

- The React Native release config must not request `android.permission.USE_EXACT_ALARM` or `android.permission.SCHEDULE_EXACT_ALARM`.
- `app.json`, the release configuration contract, and React Native release evidence block both exact-alarm permissions from the active RN release surface.
- Reminder and notification behavior must remain review-safe through Expo notification scheduling and in-app recovery states.
- If exact-alarm product scope is introduced later, complete a separate Play-policy review, product decision, fallback UX review, and release-owner signoff before changing manifest permissions.

### Contacts

- Verify contact access is tied to contact sync/event discovery/personalization.
- Verify contacts can be denied without blocking unrelated app use.
- Verify broad contact access remains justified against current Android and Play policy expectations.

## Security Gate

- Provider endpoint preflight and source tests pass for HTTPS, embedded-credential rejection, and local/private-network release rejection. Do not claim certificate pinning unless it is implemented and verified in the active Expo build.
- Dependency changes pass the CI dependency-review gate, and the final release branch has no unresolved dependency graph or Dependabot security alerts. The guarded CI gate uses `actions/dependency-review-action@v4`, fails on `moderate` or higher vulnerability severity, and denies `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, and `LGPL-3.0`.
- Do not commit service account JSON, private keys, OAuth access or refresh tokens, client secrets, signing material, SMTP credentials, Gemini keys, platform service configs, or local project variants. Provider credentials belong on backend services, not in the React Native client.
- No API keys, OAuth tokens, SMTP passwords, database keys, phone/email fixtures, raw AI responses, or message bodies appear in logs, test output, backups, or analytics exports outside explicit user export flows.
- Release provider endpoints use HTTPS without embedded credentials and do not target localhost, `.local`, loopback, or private-network hosts. Local provider endpoints require both the compile-time `__DEV__` build flag and an explicit public development flag; that public flag alone cannot enable local traffic in a release build.
- React Native app-state persistence remains versioned, normalized for large collections, bounded for oversized entries, and recoverable after missing or corrupt local storage entries.
- Backup encryption passphrases remain separate from live app unlock state and are never stored, logged, or recoverable by support.
- Sign-out or local-data clearing clears local RN app state, secure settings, pending reminders, widget summaries, provider endpoint preferences, and auth/account state through one explicit user-confirmed path.
- Platform auto backup must not expose unencrypted relationship data; sensitive stores should be excluded unless encrypted by the app.

## Backup and Sign-Out Gate

- Backup copy must make the risk model clear: exported backups use a user passphrase that RelateAI does not store; losing the passphrase means that backup file cannot be restored.
- Backup copy must not imply that the backup passphrase can unlock the live app session. Backup passphrases protect only exported backup files.
- Restore remains replace-only unless a merge-restore design is implemented and tested. Backup preview, manifest/checksum validation, supported-version checks, and passphrase verification must succeed before any local mutation.
- Wrong passphrase, checksum mismatch, malformed file, oversized file, unsupported future backup version, or database constraint failure must stop restore before or during the transaction without partial user-visible recovery claims.
- Local Setup Check diagnostic snapshots must stay out of backups and be rebuilt from current state after restore.
- Sign-out copy and release notes must treat sign-out as destructive local cleanup: contacts, events, messages, memories, gifts, backups in local state, reminder plans, widget summaries, secure settings, provider endpoint preferences, credentials, and auth/account state are cleared through the explicit local-data clearing path.
- Before risky operations such as sign-out, device change, uninstall/clear-data guidance, beta migration, or replace import, the UI/release notes should direct users to create a restorable encrypted backup when preserving local data matters.

## Temporary Harness Accessibility Gate

- Command and secure-secret inputs and the execute/retry actions expose correct accessible labels, roles, and enabled/disabled states.
- Large commands and redacted results remain scrollable and reviewable at the platform's supported large-text settings.
- Runtime loading, locked, failed-storage, permission-denied, provider-unavailable, operation-failed, and interrupted-data-recovery states remain visible as redacted text with executable recovery commands.
- Hindi and Hinglish active harness, notification, and widget strings receive a human language spot check. No deleted feature-screen localization is release scope.

## Device Release Smoke Test

- Fresh install starts in an empty local-only onboarding state with no sample relationships, fake-account path, sample-contact import, or demo reset. If provider sign-in is enabled in a future release, add a separate signed-device gate for the complete token/sync/delete lifecycle.
- Through the strict command harness, validate contact import/manual creation, event import/manual creation, drafting, review/edit/regenerate, channel validation, DND/route blocking, approval expiry/re-approval, scheduling, handoff/test, activity recovery, backup export/restore preview, launcher shortcuts, home widget navigation, and Setup Check.
- Permission denial paths are exercised for contacts, notifications, calendar, biometric lock, and manual handoff fallbacks.
- Reboot or app-reopen recovery restores scheduled review reminders without direct message sending.
- Backup export/import round trip succeeds and excludes secrets.
- Backup export/import does not restore stale local diagnostic snapshots; Setup Check diagnostics are rebuilt from current state.
- The active React Native clear/sign-out use case clears durable local state and native scheduled artifacts exactly once, verifies completion, and returns to empty first-run state.
- Interrupt clear and restore at native reconciliation, confirm the lifecycle blocker survives ordinary state commits, then run `data.recover` and verify the durable journal disappears only after reminders and widget state reconcile.

## Release Notes Requirements

- Mention high-risk permission/API changes, especially Accessibility, SMS, contacts, exact alarms, auth, backup, and notification behavior.
- Mention user-visible setup, consent, denial, or fallback behavior changes.
- Include test commands and functional device-evidence links in the release record.
