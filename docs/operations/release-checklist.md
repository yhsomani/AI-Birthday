# Release Checklist

Last reviewed: 2026-06-27

This checklist is the production gate for RelateAI. A release is not ready until every required item is completed against the exact build submitted to distribution.

## Build and Test Gate

- Run the full debug gate:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:model:test testDebugUnitTest lintDebug assembleDebug --no-configuration-cache`
- Run focused release-risk tests when changing permissions, dispatch, backup, auth, localization, or navigation.
- Run `git diff --check` before handoff.
- Confirm Room schema exports and migrations are committed when database versions change.
- Confirm no generated, local, signing, or secret files are accidentally staged.
- Produce a signed release build with the production signing configuration before Play upload.

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
- Contacts, SMS, AI prompt/response data, backups, notifications, auth data, and dispatch diagnostics are represented accurately.
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

- Network security pins are valid beyond the release support window.
- No API keys, OAuth tokens, SMTP passwords, database keys, phone/email fixtures, raw AI responses, or message bodies appear in logs, test output, backups, or analytics exports outside explicit user export flows.
- SQLCipher key strategy and backup recovery limitations are reviewed.
- Sign-out clears local stores, workers, alarms, notifications, cached database keys, and auth state through one orchestrator.
- Auto backup remains disabled or sensitive stores remain excluded.

## UX and Accessibility Gate

- Primary workflows pass manual large-font review.
- Hindi and English primary flows are checked for clipping, stale copy, and untranslated user-facing text.
- Critical actions have accessible labels and clear enabled/disabled states.
- Permission-denied, setup-missing, offline, loading, empty, and failure states are visible and recoverable.
- Screenshot or device validation covers Home, Messages, Wish Preview, Contact Detail, Events, AI Doctor, Settings, Backup/Restore, and onboarding/setup.

## Device Release Smoke Test

- Fresh install can start in guest mode and signed-in mode.
- Contact sync/import, manual contact creation, event discovery/manual event creation, wish generation, review/edit/regenerate, approval, schedule, send/test, activity history, backup export, restore preview, and AI Doctor all work.
- Permission denial paths are exercised for contacts, notifications, SMS, exact alarms, and Accessibility.
- Reboot recovery restores scheduled work without direct send from boot receiver.
- Backup export/import round trip succeeds and excludes secrets.
- Sign-out clears local state and cancels scheduled work once through `AuthManager.signOut()`.

## Release Notes Requirements

- Mention high-risk permission/API changes, especially Accessibility, SMS, contacts, exact alarms, auth, backup, and notification behavior.
- Mention user-visible setup, consent, denial, or fallback behavior changes.
- Include test commands and device/screenshot evidence links in the release record.
