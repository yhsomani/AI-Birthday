# Release Checklist

Last reviewed: 2026-07-10

This checklist is the production gate for RelateAI. A release is not ready until every required item is completed against the exact build submitted to distribution.

## Build and Test Gate

- Use the pinned Node 24.18.0/npm 11.6.x toolchain (`nvm use`) and install from the lockfile with `npm ci`.
- Prove the clean-checkout order before evidence generation:

  ```bash
  test ! -e reports/react-native-release-evidence.json
  npm run typecheck
  npm test
  ```

- Generate the release record with `npm run release:evidence -- --fail-on-blockers`. The generator executes typecheck, test, audit, Expo dependency compatibility, a production web export, and diff checks itself; no environment variable or manual report edit can mark a check passed through the supported workflow.
- Keep the React Native `npm test` script on the full source-contract suite without per-file process isolation unless CI has proven child `node`/`esbuild` process isolation is reliable for the exact release environment.
- Attach the CI-produced `reports/react-native-release-evidence.json` and its GitHub provenance attestation to the release record. Confirm it names the exact release commit, reports `dirty: false`, matches the submitted lockfile hash and Node/npm versions, and contains exit-code/timing/output-hash proof for every required command. Resolve every `blockers` entry. Pending signed-build, device-smoke, or store-submission warnings require attached evidence, not a locally edited status.
- The report is an ignored operational artifact. It must never be imported by `src/App.tsx`, required by typecheck/test/bundle, or displayed as attached runtime evidence. Setup Check must safely report it as unattached.
- Confirm the large dataset workflow contract remains green after changing Contacts, Messages, Events, Analytics, widgets, persistence, or export behavior.
- Confirm normalized persistence and storage health inspection tests remain green after changing local storage, migrations, backup/restore, local-data clearing, Setup Check storage readiness, or large-state workflows.
- Confirm account access tests remain green after changing account mode, provider disconnect, sign-out copy, backup recommendations, local data clearing, or destructive-action confirmations.
- Confirm the React Native localization contract remains green after changing translation keys, locale metadata, pluralized counts, settings language selection, primary navigation, Onboarding controls/goal/status labels, primary screen headings, Events/Event card/Add Event controls/event type/time/month/status labels, Messages controls/status/channel/sort/bulk labels, Contacts/Contact Detail controls/group/quality/sort/tone/channel/timeline labels, Home widget summary/tile/accessibility labels, Home and Contact Detail check-in reminder title/detail/action/summary labels, Contact Detail contact-language/check-in/relationship-health/group/cadence/automation/timeline-entry metadata/empty-state labels, Memory Vault category/AI-use labels, Gift Advisor category/feedback/confidence/budget labels, Manual Composer controls/reason labels, Chat History controls/channel metadata, Wish Preview controls/status/channel/quality/tone/variant labels/confirmations, Template Library reason/tone labels, More Account/Privacy/Calendar/Reminder/Import/Template/Persistence/Setup Wizard summaries/details/actions/Setup Check diagnostics/Style Coach/AI Provider/Analytics/Backup/Activity History controls/titles/details/Settings controls, native feedback for manual handoff/contact import/reminder scheduling/calendar sync/event file import, lock screen, home dashboard, or localized system feedback.
- Confirm the React Native accessibility contract remains green after changing touch targets, form inputs, tabs, checklists, or primary navigation.
- Confirm the React Native primary interaction contract remains green after changing tab routing, screen rendering, Home/Events/Messages/Contacts/More actions, review flows, or destructive confirmations.
- Confirm event file import tests remain green after changing CSV/vCard parsing, native document picking, calendar import candidates, or imported-event review behavior.
- Confirm calendar sync tests remain green after changing device-calendar export notes, idempotent export reconciliation, stale mirrored-event cleanup, or import candidate handling.
- Confirm Event Preparation tests remain green after changing checklist step relevance, legacy checklist id compatibility, derived completion from memories/drafts/reminders/channels, event-card next-step guidance, or reminder planning actions.
- Confirm AI provider governance tests remain green after changing provider endpoint readiness, request filtering, local rate limits, response safety/language checks, fallback behavior, or redacted observations.
- Confirm Manual Composer tests remain green after changing writing reasons, template selection, context privacy summaries, minimum-body validation, AI readiness/fallback guidance, or non-event draft creation.
- Confirm Message Template Library tests remain green after changing local template catalog browsing, contact/occasion/tone selection, tone fallback explanations, template body rendering, private-note exclusion, or review-first draft creation.
- Confirm email delivery tests remain green after changing sender setup, provider endpoint readiness, provider request validation, approval-window checks, provider send success, stale-success handling, or failed-message recovery.
- Confirm message body policy tests remain green after changing minimum message length, channel character caps, SMS multipart guidance, approval gating, route tests, manual handoff, or provider delivery validation.
- Confirm contact essentials tests remain green after changing contact profile fields, phone/email validation, language options, gift budget handling, stale-message review guardrails, DND and route-aware approval blocking, or profile save behavior.
- Confirm guided contact enrichment tests remain green after changing personalization scoring, missing-signal summaries, core prompts, enrichment-answer validation, saved memory categories, or redacted activity copy.
- Confirm relationship check-in tests remain green after changing cadence calculations, Home check-in queue behavior, snooze semantics, mark-contacted actions, last-contact history, or Contact Detail check-in controls.
- Confirm Memory Vault tests remain green after changing memory note validation, search, pinning, editing, deletion confirmation, private-note AI exclusion, or Contact Detail note controls.
- Confirm Gift Advisor tests remain green after changing gift validation, budget summaries, suggestions, duplicate warnings, private-note exclusion, gift recording, deletion confirmation, or missing-record recovery.
- Confirm analytics tests remain green after changing dashboard metrics, shareable summaries, CSV export, insight routing, redaction, or analytics activity logging.
- Confirm activity history tests remain green after changing activity row metadata, recovery target validation, stale-target fallback, filters, redaction, or recovery navigation.
- Confirm contact group preference tests remain green after changing relationship groups, group defaults, contact tone/channel/cadence/automation overrides, AI drafting context, templates, analytics, or setup readiness.
- Confirm recipient-specific tone controls tests remain green after changing contact tone choices, inherited tone sources, Wish Preview tone impact explanations, template fallback explanations, or tone-adjustment routing.
- Confirm settings/channel/schedule guardrail tests remain green after changing SMS, WhatsApp, email, notification enablement/permission state, reminder plans, quiet hours, blackouts, automation mode, AI enablement, account-mode, or queued-message consequence behavior.
- Confirm notification readiness and scheduling reconciliation tests remain green after changing reminder notification copy, notification permission fallbacks, route payload data, stale route handling, lock-screen privacy, stale/changed reminder cleanup, unrelated scheduled-notification preservation, or Setup Check reminder diagnostics.
- Confirm Setup Check tests remain green after changing diagnostic grouping, provider endpoint readiness checks, recommended fixes, dry-run snapshots, setup actions, redaction, or activity logging.
- Confirm manual handoff and native bridge tests remain green after changing approval, approval expiry, route readiness, channel deep links, Linking/share behavior, approved-text-only copy/share fallback, share-sheet dismissal handling, destination-app prompts, explicit mark-sent confirmation, or sent-message recording.
- Confirm message test-send route tests remain green after changing Wish Preview testing, channel readiness checks, channel body validation, SMS multipart guidance, route setup guidance, or message lifecycle transitions.
- Confirm Wish Preview regeneration feedback tests remain green after changing feedback chips, custom feedback limits, regeneration request payloads, local fallback regeneration, saved feedback metadata, or feedback logging behavior.
- Confirm Wish Preview review-next tests remain green after changing approval, rejection, selected message routing, queue ordering, or handled-message navigation.
- Confirm `app.json` contains stable RN native identifiers, `runtimeVersion.policy = appVersion`, iOS `buildNumber`, Android `versionCode`, and no direct SMS, SMS inbox, call-log, phone-number, exact-alarm, or AccessibilityService permissions. The `react native release configuration contract` test protects this.
- Confirm the Android home widget plugin and app-shell contracts remain green after changing widget summary payloads, widget routes, Expo config plugins, Android manifest receivers, generated widget resources, JS-to-native widget sync, or external-surface privacy copy.
- Confirm `eas.json` contains development, preview, and production profiles; production Android must build an app bundle and production iOS must not target simulator builds.
- Produce signed EAS production builds for Android and iOS with release credentials before store upload.
- Confirm release evidence records `activeReleaseSurface.legacyKotlinGradleArtifactPaths` as an empty array. Treat any reintroduced legacy Android/Gradle path as release evidence drift that must be resolved before signing.
- Run focused release-risk tests when changing permissions, dispatch, backup, auth, localization, or navigation.
- Confirm pull requests with dependency changes pass GitHub Dependency Review for moderate-or-higher vulnerabilities and denied licenses.
- Run `git diff --check` before handoff.
- Confirm no generated, local, signing, or secret files are accidentally staged.
- Confirm provider config changes do not add credentials, backend secrets, signing material, or platform service keys to the client repository.

## React Native Visual and Device UX Gate

- Keep source-level React Native accessibility, localization, and primary interaction contracts green for every UI change.
- Before release, attach screenshots or video from the exact signed EAS candidate on Android and iOS for Home, Events, Messages, Contacts, More, Wish Preview, Settings, Backup/Restore, and any changed feature surface.
- Include compact-phone, typical-phone, large-font, and Hindi/Hinglish spot checks when text density or localization changes.
- Verify widget, shortcut, notification, calendar, contact import, backup file, biometric lock, and channel handoff flows on physical devices or approved release-device simulators where platform policy allows.
- Generated Expo/native artifacts, reports, and local visual review files must remain unstaged. Upload release evidence through the CI/release record instead of committing it or coupling it to runtime source.

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

| Item | Owner | Date | Status | Notes |
| --- | --- | --- | --- | --- |
| RN build excludes AccessibilityService automation | TBD | TBD | Pending | Source config and release evidence block `android.permission.BIND_ACCESSIBILITY_SERVICE`; signed-build manifest review is still required before Play release. |
| Manual WhatsApp handoff reviewed | TBD | TBD | Pending | Verify approved-text-only handoff and explicit mark-sent confirmation. |
| Data Safety consistency reviewed | TBD | TBD | Pending | Must match privacy policy, store listing, and final build. |
| Future WhatsApp automation decision | TBD | TBD | Pending | Required only if unattended automation is reintroduced. |

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
- Release provider endpoints use HTTPS without embedded credentials and do not target localhost, `.local`, loopback, or private-network hosts. Local provider endpoints are allowed only for explicit development testing and must not be present in production build configuration.
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

## UX and Accessibility Gate

- Primary workflows pass manual large-font review.
- Hindi and English primary flows are checked for clipping, stale copy, and untranslated user-facing text.
- Critical actions have accessible labels and clear enabled/disabled states.
- Permission-denied, setup-missing, offline, loading, empty, and failure states are visible and recoverable.
- Screenshot or device validation covers Home, Messages, Wish Preview, Contact Detail, Events, Setup Check, Settings, Backup/Restore, and onboarding/setup.

## Device Release Smoke Test

- Fresh install starts in an empty local-only onboarding state with no sample relationships, fake-account path, sample-contact import, or demo reset. If provider sign-in is enabled in a future release, add a separate signed-device gate for the complete token/sync/delete lifecycle.
- Contact sync/import, manual contact creation, event discovery/manual event creation, wish generation, review/edit/regenerate, channel body validation and SMS multipart guidance, DND and route-unavailable approval blocking, approval expiry/re-approval, approval, schedule, send/test, activity history, backup export, restore preview, Android launcher shortcuts, Android home widget navigation, and Setup Check all work.
- Permission denial paths are exercised for contacts, notifications, calendar, biometric lock, and manual handoff fallbacks.
- Reboot or app-reopen recovery restores scheduled review reminders without direct message sending.
- Backup export/import round trip succeeds and excludes secrets.
- Backup export/import does not restore stale local diagnostic snapshots; Setup Check diagnostics are rebuilt from current state.
- The active React Native clear/sign-out use case clears durable local state and native scheduled artifacts exactly once, verifies completion, and returns to empty first-run state.

## Release Notes Requirements

- Mention high-risk permission/API changes, especially Accessibility, SMS, contacts, exact alarms, auth, backup, and notification behavior.
- Mention user-visible setup, consent, denial, or fallback behavior changes.
- Include test commands and device/screenshot evidence links in the release record.
