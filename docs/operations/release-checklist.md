# Release Checklist

Last reviewed: 2026-07-03

This checklist is the production gate for RelateAI. A release is not ready until every required item is completed against the exact build submitted to distribution.

## Build and Test Gate

- Run the full debug gate:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:model:test :core:domain:test testDebugUnitTest lintDebug assembleDebug --no-configuration-cache`
- Confirm Gradle is running on a full JDK 21/JBR with `bin/jlink` available. On Windows,
  Android Studio JBR or Temurin JDK 21 is valid; the Antigravity/Red Hat extension JRE is not.
- Run focused release-risk tests when changing permissions, dispatch, backup, auth, localization, or navigation.
- App-side Robolectric tests default to `android.app.Application` through `app/src/test/resources/robolectric.properties`; tests that need production app startup must opt in explicitly.
- Confirm pull requests with dependency changes pass GitHub Dependency Review for moderate-or-higher vulnerabilities and denied licenses.
- Run `git diff --check` before handoff.
- Confirm Room schema exports and migrations are committed when database versions change.
- Confirm no generated, local, signing, or secret files are accidentally staged.
- Confirm provider config changes follow the approved allowlist: only `app/google-services.json` and `app/src/debug/google-services.json` may be tracked as Firebase/Google client config files.
- Produce a signed release build with the production signing configuration before Play upload.

## Visual Regression Gate

- Use Roborazzi for JVM screenshot validation. Screenshot tests must render deterministic fake state and avoid live APIs, keystore-backed preferences, production Room databases, WorkManager schedulers, notification channels, and external services.
- Keep approved baselines under `app/src/test/screenshots/baseline`. Generated review artifacts belong under ignored Roborazzi output/diff directories unless they are visually approved and intentionally committed.
- Verify approved baselines before release:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:verifyRoborazziDebug -Pscreenshot --no-configuration-cache`
- Use focused visual verification for risky screen changes, for example:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:verifyRoborazziDebug -Pscreenshot --tests com.example.ui.screenshots.MessagesScreenshotTest --no-configuration-cache`
- Record baselines locally only after visual review:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:recordRoborazziDebug -Pscreenshot --no-configuration-cache`
- Baseline updates must be committed with the UI/code change that caused them and a release-record note explaining why the drift is expected.
- Pull requests and pushes to `main`/`master` must keep CI Roborazzi verification enabled for `com.example.ui.screenshots.*` and must upload Roborazzi reports/outputs for mismatch review.
- Screenshot coverage must remain paired with interaction or ViewModel tests for behavior; screenshots validate presentation, not business logic.
- Dense form/dialog fixtures that stand in for unstable platform dialogs must include top and scrolled-bottom coverage when content can overflow, plus compact Hindi large-font coverage when localized labels or values are dense.
- Theme-token migrations expected to be visually equivalent must run focused screen screenshots and the full Roborazzi suite before handoff.

## Play Policy Gate

### AccessibilityService API

Source: https://support.google.com/googleplay/android-developer/answer/10964491

Current status: blocked pending release-owner signoff.

RelateAI uses AccessibilityService API for optional WhatsApp automation. The current service is not a disability-assistive accessibility tool; it is a narrow automation channel for approved outgoing WhatsApp messages.

Release requirements:

- Complete the Play Console AccessibilityService declaration for the exact build.
- Do not declare `isAccessibilityTool=true` unless the app is redesigned and reviewed as an accessibility tool for users with disabilities.
- Confirm the automation is narrow, deterministic, and tied to a clearly understood user purpose.
- Confirm AI generation cannot independently initiate, plan, and execute WhatsApp actions without existing dispatch eligibility, approval, schedule, and consent policies.
- Confirm app-level WhatsApp automation consent is required before the dispatcher can call `WhatsAppSender`.
- Confirm Android Accessibility enablement is still required and revocable.
- Confirm the service remains scoped to `com.whatsapp` and `com.whatsapp.w4b`.
- Confirm the service does not read, store, export, or log WhatsApp chat contents.
- Attach review evidence: in-app disclosure, affirmative consent, system enablement, denial path, and no-send dry run.

Signoff record:

| Item | Owner | Date | Status | Notes |
| --- | --- | --- | --- | --- |
| Accessibility declaration accepted | TBD | TBD | Pending | Required before Play release with WhatsApp automation enabled. |
| Prominent disclosure reviewed | TBD | TBD | Pending | Validate whether AI Doctor checkbox placement is sufficient. |
| Data Safety consistency reviewed | TBD | TBD | Pending | Must match privacy policy, store listing, and final build. |
| WhatsApp automation distribution decision | TBD | TBD | Pending | If rejected, document channel-disable or non-Play distribution decision. |

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

- Verify `SEND_SMS` declaration eligibility and core-feature justification.
- Verify SMS sends require existing dispatch eligibility and approval/automation policy.
- Verify the app does not request SMS inbox or call-log permissions.
- Verify denial path falls back to setup guidance or other eligible channels.

### Exact Alarms

- Manifest must not contain `android.permission.USE_EXACT_ALARM`.
- `android.permission.SCHEDULE_EXACT_ALARM` must remain limited to user-visible scheduled sends/reminders.
- Scheduler code must keep `canScheduleExactAlarms()` checks and fallback behavior.
- If exact-alarm product scope changes, document the policy basis before changing manifest permissions.

### Contacts

- Verify contact access is tied to contact sync/event discovery/personalization.
- Verify contacts can be denied without blocking unrelated app use.
- Verify broad contact access remains justified against current Android and Play policy expectations.

## Security Gate

- Network security pins are valid beyond the release support window. CI runs `ProductionReadinessConfigTest`, which fails release readiness when the soonest `network_security_config.xml` pin expiration is within 60 days.
- Network pin rotation task is scheduled no later than 2027-04-01 for the current `2027-06-01` pin-set expiration. Each release after 2027-04-01 must either include refreshed pins with a new expiration date or attach explicit release-owner signoff accepting the remaining pin lifetime.
- Dependency changes pass the CI dependency-review gate, and the final release branch has no unresolved dependency graph or Dependabot security alerts. The guarded CI gate uses `actions/dependency-review-action@v4`, fails on `moderate` or higher vulnerability severity, and denies `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, and `LGPL-3.0`.
- Approved Google/Firebase client config files may be tracked only at `app/google-services.json` and `app/src/debug/google-services.json`. Do not commit service account JSON, private keys, OAuth access or refresh tokens, client secrets, signing material, SMTP credentials, Gemini keys, or local project variants.
- No API keys, OAuth tokens, SMTP passwords, database keys, phone/email fixtures, raw AI responses, or message bodies appear in logs, test output, backups, or analytics exports outside explicit user export flows.
- SQLCipher key strategy and backup recovery limitations are reviewed.
- Fresh-install database keying generates random local key material formatted as SQLCipher raw-key literals; legacy identifier-derived key recovery is treated as migration-only and must be tested before removal.
- Sign-out clears local stores, workers, alarms, notifications, cached database keys, and auth state through one orchestrator.
- Auto backup remains disabled or sensitive stores remain excluded.

## Backup and Sign-Out Gate

- Backup copy must make the risk model clear: exported backups use a user passphrase that RelateAI does not store; losing the passphrase means that backup file cannot be restored.
- Backup copy must not imply that the backup passphrase can unlock the live SQLCipher database. The live database key is separate local key material stored through Keystore-backed encrypted preferences.
- Restore remains replace-only unless a merge-restore design is implemented and tested. Backup preview, manifest/checksum validation, supported-version checks, and passphrase verification must succeed before any local mutation.
- Wrong passphrase, checksum mismatch, malformed file, oversized file, unsupported future backup version, or database constraint failure must stop restore before or during the transaction without partial user-visible recovery claims.
- Local diagnostic snapshots from AI Doctor and HealthMonitor must stay out of backups and be rebuilt after restore.
- Sign-out copy and release notes must treat sign-out as destructive local cleanup: workers, alarms, notifications, Room data, database files, secure preferences, cached database key material, credentials, and auth state are cleared through the auth-layer orchestrator.
- Before risky operations such as sign-out, device change, uninstall/clear-data guidance, beta migration, or replace import, the UI/release notes should direct users to create a restorable encrypted backup when preserving local data matters.

## UX and Accessibility Gate

- Primary workflows pass manual large-font review.
- Hindi and English primary flows are checked for clipping, stale copy, and untranslated user-facing text.
- Critical actions have accessible labels and clear enabled/disabled states.
- Permission-denied, setup-missing, offline, loading, empty, and failure states are visible and recoverable.
- Screenshot or device validation covers Home, Messages, Wish Preview, Contact Detail, Events, AI Doctor, Settings, Backup/Restore, and onboarding/setup.

## Device Release Smoke Test

- Fresh install can start through Google sign-in or local-only manual mode; no demo or fake-account path is exposed.
- Contact sync/import, manual contact creation, event discovery/manual event creation, wish generation, review/edit/regenerate, approval, schedule, send/test, activity history, backup export, restore preview, and AI Doctor all work.
- Permission denial paths are exercised for contacts, notifications, SMS, exact alarms, and Accessibility.
- Reboot recovery restores scheduled work without direct send from boot receiver.
- Backup export/import round trip succeeds and excludes secrets.
- Backup export/import does not restore stale local diagnostic snapshots; AI Doctor/HealthMonitor diagnostics are rebuilt from current state.
- Sign-out clears local state and cancels scheduled work once through `AuthManager.signOut()`.

## Release Notes Requirements

- Mention high-risk permission/API changes, especially Accessibility, SMS, contacts, exact alarms, auth, backup, and notification behavior.
- Mention user-visible setup, consent, denial, or fallback behavior changes.
- Include test commands and device/screenshot evidence links in the release record.
