# RelateAI UI and Integration Validation

Last updated: 2026-06-10

This document records manual and automated UI validation for the feature compliance pass. The attached validation device discovered during planning is `1b87b5db`.

## Validation Legend

- Pass: validated successfully.
- Fail: validated and broken; requires a fix before feature completion.
- Blocked: prerequisite missing, such as account credentials, SIM/device capability, WhatsApp setup, notification permission, or exact-alarm permission.
- Not Run: still pending in this feature pass.

## Automated Baseline

| Check | Status | Evidence |
|---|---|---|
| Unit tests and lint | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug --no-configuration-cache`, 212 tests, 0 failures, 0 errors, 0 skipped. |
| Biometric lock compile/test | Pass | `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests com.example.BiometricLockPolicyTest --no-configuration-cache`. |
| Full release-readiness validation | Not Run | Planned command: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache`. |
| Compose/instrumented UI coverage | Not Run | Existing `app/src/androidTest` only contains package-context smoke test before this pass. |

## Screen Interaction Checklist

| Area | Interactions to Validate | Status | Evidence |
|---|---|---|---|
| Splash | cold start routing to onboarding/auth/home | Not Run | Pending debug install. |
| Onboarding | continue, setup checklist, skip/finish routing | Not Run | Pending debug install. |
| Auth | Google sign-in, guest/developer bypass, auth errors | Not Run | Requires account/device validation. |
| App shell | bottom tabs, permission rationale grant/not-now, back stack | Not Run | Pending debug install. |
| Home | settings, analytics, activity, style, backup, setup links; dashboard cards; sync error retry/dismiss | Not Run | Pending debug install. |
| Contacts | search, clear search, filters, sort, refresh, contact open | Not Run | Pending seeded contacts. |
| Contact Detail | back, memory, gifts, chat, generate wish, edit preferences, quick enrichment, switches/chips/text fields | Not Run | Pending seeded contacts. |
| Events | search, add event, contact picker, new contact, type chips, date fields, save/cancel, filters | Not Run | Pending debug install. |
| Messages | tabs, search, filters, row selection, approve/reject/retry/revoke, bulk actions, preview open | Not Run | Pending generated messages. |
| Wish Preview | variants, edit text, feedback, regenerate, test send, approve, reject, back | Not Run | Pending generated message and SMTP config for live test. |
| Analytics | activity link, export/share action, empty and populated states | Not Run | Pending data and device share sheet. |
| Activity History | search, type/date/status filters, open route | Not Run | Pending activity data. |
| Style Coach | sample text, analyze button, recent-message analysis, back | Not Run | Pending debug install. |
| Memory Vault | add, category chips, pin/unpin, delete, validation | Not Run | Pending contact. |
| Gift Advisor | FAB, add gift dialog, cost validation, feedback buttons, delete, AI suggestions | Not Run | Pending contact and AI config. |
| Backup/Restore | passphrase visibility, export, restore, back, invalid passphrase states | Not Run | Pending document picker/device validation. |
| Settings | AI key, email settings, automation mode, quiet hours, biometric, reminders, channel blackout, sync, sign out | Not Run | Pending debug install. |
| Biometric lock | enable toggle, background app, resume, authenticate, cancel/retry, unsupported-device messaging | Not Run | Policy and compile tests pass; live prompt still pending. |
| Automation Setup | diagnostics, settings/style/contacts/activity links, system-setting handoffs, dry run, email test | Not Run | Pending permissions and credentials. |
| Chat History | empty/populated states and back | Not Run | Pending sent-message data. |

## External Surfaces

| Surface | Status | Evidence |
|---|---|---|
| SMS live send/status | Not Run | Requires SIM/SMS-capable device and safe test recipient. |
| WhatsApp Accessibility live send | Not Run | Requires WhatsApp installed, accessibility service enabled, unlocked device, and safe test recipient. |
| Gmail SMTP live send | Not Run | Requires sender Gmail address and app password. |
| Google OAuth and People API sync | Not Run | Requires configured Google account and Firebase/OAuth client setup. |
| Gemini live generation/classification | Not Run | Requires Gemini API key or authenticated Firebase Vertex path. |
| Notifications/action receivers | Not Run | Requires notification permission and generated pending/reminder data. |
| Exact alarms/boot recovery | Not Run | Requires exact alarm permission and device scheduling window. |
| Widget | Not Run | Requires widget placement on launcher. |
| Dynamic shortcuts | Not Run | Requires launcher shortcut inspection. |
| Deep links | Not Run | Requires `adb shell am start` validation. |
