# RelateAI Complete User Guide

Last updated: 2026-06-30

RelateAI is a local-first Android relationship assistant. It helps you import contacts, discover birthdays and other important moments, add relationship context, generate personalized wishes, review or approve drafts, schedule delivery, and send through SMS, WhatsApp, or Gmail depending on your setup.

The core promise is simple: RelateAI should help you remember people and act at the right time, while keeping you in control of anything sensitive.

## How to Use This Guide

Use this document in order the first time you set up RelateAI. After setup, use the daily workflow, recipes, troubleshooting table, and quick reference sections as runbooks.

This guide is written for three situations:

1. You installed RelateAI and want to use it safely.
2. You are testing a local build from this repository.
3. You want to automate relationship reminders and messages while understanding every blocker, permission, and fallback.

RelateAI has no demo-user, guest-user, or fake-data path. The real workflow starts with Google sign-in, real contact import or manual contact/event creation, and explicit setup for any external service.

### Choose Your Path

Start with the path that matches your goal. You can switch paths later by changing automation mode, channel setup, and contact-level overrides.

| Goal | Fast path | Stop when |
| --- | --- | --- |
| Try RelateAI safely | Sign in, sync or create one contact, set Automation Mode to Always Ask, generate one draft, review it, and export a backup. | One reviewed message is scheduled or rejected and Activity History explains the result. |
| Use AI writing only | Configure Gemini, train Style Coach, keep delivery channels optional, and keep Automation Mode on Always Ask. | Wish Preview can generate and regenerate useful drafts. |
| Review before every send | Configure contacts/events/channels, set global Always Ask or VIP Approve, and rely on Messages plus notifications. | Needs review is manageable and no contact can send without approval. |
| Streamline normal contacts | Use Smart Approve globally, keep important contacts on VIP Approve or Always Ask, and run AI Doctor weekly. | Low-risk messages can move with minimal intervention while sensitive contacts stay protected. |
| Fully automate eligible sends | Configure at least one delivery channel end to end, set Fully Auto, verify AI Doctor, test one low-risk real send, then monitor Activity History. | The first real unattended send completes and no required AI Doctor blockers remain. |
| Move devices or recover data | Export encrypted backup, restore on the new install, re-enter secrets and permissions, then run AI Doctor. | Restored records appear and external integrations are configured again. |

### Table of Contents

1. [What RelateAI Can Do](#1-what-relateai-can-do)
2. [Before You Start](#2-before-you-start)
3. [First-Time Setup](#3-first-time-setup)
4. [Daily Workflow](#4-daily-workflow)
5. [Contacts: Build Better Personalization](#5-contacts-build-better-personalization)
6. [Memory Vault](#6-memory-vault)
7. [Gift Advisor](#7-gift-advisor)
8. [Events](#8-events)
9. [Messages Inbox](#9-messages-inbox)
10. [Wish Preview](#10-wish-preview)
11. [Automation and Dispatch Rules](#11-automation-and-dispatch-rules)
12. [Workflow Automation Playbook and Advanced Recipes](#12-workflow-automation-playbook-and-advanced-recipes)
13. [Shortcuts, Widgets, and Deep Links](#13-shortcuts-widgets-and-deep-links)
14. [Privacy and Data Handling](#14-privacy-and-data-handling)
15. [Backup and Restore Details](#15-backup-and-restore-details)
16. [Troubleshooting and Prevention](#16-troubleshooting-and-prevention)
17. [Example Scenarios](#17-example-scenarios)
18. [Best Practices](#18-best-practices)
19. [Quick Reference](#19-quick-reference)
20. [When in Doubt](#20-when-in-doubt)

### Document Coverage Checklist

Use this checklist to confirm the guide answers the full operating question, from first setup to reliable workflow automation.

| User need | Covered in this guide | Completion signal |
| --- | --- | --- |
| Initial setup | Before You Start and First-Time Setup. | The user can install or run the app, sign in, import or create contacts/events, configure AI, configure a delivery channel, and run AI Doctor. |
| Execution workflow | Daily Workflow, Messages Inbox, Wish Preview, Automation and Dispatch Rules. | The user can generate, review, approve, schedule, send, retry, reject, or recover one message with an auditable result. |
| Feature variations | Feature Variations, Minimum Viable Setups, Automation Modes, Route Selection, Shortcuts, Widgets, and Deep Links. | The user understands contact sources, event types, AI/fallback drafting, channel routes, automation modes, recovery paths, and entry points. |
| Advanced usage | Workflow Automation Playbook, Advanced Recipes, Analytics, Backup and Restore, Privacy and Data Handling. | The user can run review-first, smart approve, full automation, professional email, WhatsApp, holiday, revival, follow-up, bulk review, and migration workflows. |
| Practical examples | Example Scenarios and recipe sections. | The user can map common real-life scenarios to exact setup, review, and dispatch steps. |
| Problems and blockers | Troubleshooting and Prevention plus Current Limitations. | Every major issue includes a root cause, a solution or workaround, and a preventive improvement. |
| Reliable operation | Best Practices, Maintenance Routines, Quick Reference, When in Doubt. | The user has recurring checks for AI Doctor, Activity History, backups, provider readiness, and post-restore setup. |

## 1. What RelateAI Can Do

RelateAI supports these major workflows:

| Area | What you can do |
| --- | --- |
| Home | See upcoming moments, pending reviews, setup blockers, backup reminders, and the next best action. |
| Contacts | Sync contacts, search, filter, sort, classify relationships, enrich profiles, set preferred channels, and configure contact-level automation. |
| Events | Track birthdays, anniversaries, work anniversaries, custom events, reminders, duplicate events, and conflicting dates. |
| Messages | Review pending drafts, scheduled messages, blocked messages, sent history, failures, bulk approve, bulk reject, retry failed sends, and revoke approvals. |
| Wish Preview | Edit a draft, choose a tone or variant, inspect why the draft was generated, send a test email, approve and schedule, reject, or regenerate with feedback. |
| AI Doctor | Diagnose AI setup, contact sync, generic messages, permissions, channel setup, background reliability, and failed-send recovery. |
| Style Coach | Train RelateAI on your writing samples or recent sent messages so future wishes sound closer to you. |
| Memory Vault | Save facts, preferences, moments, milestones, and topics to avoid for a contact. |
| Gift Advisor | Track gift history, budgets, gift feedback, and AI gift ideas. |
| Analytics | Review relationship health, contact distribution, delivery reliability, response rate, personalization coverage, neglected contacts, and export a CSV report. |
| Activity History | Audit recent sync, AI, dispatch, event, backup, analytics, settings, and error activity. |
| Backup and Restore | Export encrypted backups and restore them later with the same passphrase. |
| Widget and shortcuts | Use launcher shortcuts and a birthday/upcoming-event widget for quick access. |

### Feature Variations at a Glance

| Feature area | Variations supported |
| --- | --- |
| Account | Google sign-in only. No alternate login, guest mode, developer bypass, or fake account. |
| Contact source | Google People contacts, Android device contacts, and manual contact creation through event flows. |
| Event type | Birthday, anniversary, work anniversary, graduation, holiday, revival, follow-up, custom. |
| Event origin | Imported, manual, calendar, AI inferred, merged, conflict, or unknown source. |
| Delivery channel | SMS, WhatsApp, Email, or blocked/no route until setup is fixed. |
| AI behavior | Gemini generation, regeneration from feedback, style analysis, classification, gift suggestions, or fallback/manual editing when AI is disabled or unavailable. |
| Automation mode | Default/global, Fully Auto, Smart Approve, VIP Approve, Always Ask. |
| Message state | Pending, approved, dispatching, sent, rejected, failed, expired, unknown. |
| Entry point | Home, bottom navigation, launcher shortcuts, widget, notifications, or supported deep links. |
| Recovery path | AI Doctor, Activity History, failed-message retry, encrypted backup restore, or sign-out data purge with prior backup. |

### Workflow Inputs and Outputs

RelateAI becomes more useful as you add accurate context. Use this map to understand which inputs power each output.

| Input | Where to add or review it | Used for |
| --- | --- | --- |
| Google or device contacts | Contacts, Settings, AI Doctor | Contact list, event discovery, relationship health, routing, personalization. |
| Manual events | Events | Reminders, generated wishes, scheduled dispatch, analytics. |
| Contact preferences | Contact Detail | Language, tone, channel, automation mode, quiet send time, personalization quality. |
| Memory notes | Memory Vault | Specific AI wording, topics to include, topics to avoid, relationship context. |
| Gift history | Gift Advisor | Gift suggestions, avoiding repeated gifts, event-specific context. |
| Writing samples | Style Coach | Tone, formality, emoji use, greetings, average message length. |
| Gemini and Gmail credentials | Settings, AI Doctor | AI generation, style analysis, gift suggestions, email delivery, test email. |
| Android permissions | On-device permission prompts, AI Doctor | Contacts import, notifications, SMS send, exact reminders, WhatsApp automation. |
| Backups | Backup and Restore | Recovery after device change, sign-out, uninstall, migration, or local data loss. |

Treat Contacts, Events, Messages, and AI Doctor as the source-of-truth surfaces. If another screen looks stale or incomplete, open the source surface and refresh or fix the top blocker there first.

## 2. Before You Start

You need:

1. An Android device running Android API 24 or newer.
2. A Google account. Google sign-in is the only supported app login.
3. Device contacts permission if you want to import contacts stored on the phone.
4. Notification permission if you want reminders and approval prompts.
5. SMS permission if you want RelateAI to send approved SMS messages.
6. Exact alarm access if you want the most precise scheduled sends and reminders.
7. Gemini access configured from the signed-in Google/Firebase account or a saved Gemini key, depending on the build.
8. Gmail sender address and Gmail app password if you want Email delivery or test-send-to-self.
9. WhatsApp or WhatsApp Business plus Accessibility setup if you want WhatsApp automation.
10. A strong backup passphrase if you want encrypted export and restore.

RelateAI stores relationship data locally and encrypts its live database. Exported backups use a separate passphrase that RelateAI does not store.

### Install or Run RelateAI

Choose the path that matches how you received the app.

#### Option A: Installed APK or store build

1. Install RelateAI on an Android device.
2. Open the app.
3. Continue through onboarding.
4. Sign in with Google.
5. Complete the setup checklist in this guide.

#### Option B: Android Studio local build

Use this when testing from the repository.

1. Install JDK 21 and an Android SDK that supports compile SDK 37.
2. Open the repository root in Android Studio.
3. Add the correct Firebase `google-services.json` files for the debug or release application id.
4. Let Gradle sync.
5. Select the `app` run configuration.
6. Run on an emulator or physical device.

Debug builds use the debug application id, so Firebase OAuth clients and SHA-1 fingerprints must match the debug package and signing key.

#### Option C: Command-line debug build

From the repository root:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug --no-configuration-cache
```

Then install `app/build/outputs/apk/debug/app-debug.apk` through Android Studio, `adb`, or your normal device-management flow.

#### Option C1: Local validation before handing the build to users

Use this when you are preparing a tester build or checking that documentation matches the current app behavior.

1. Run unit and interaction tests for changed areas.
2. Run release-readiness checks before production distribution.
3. Confirm Google sign-in, contact sync, AI Doctor, one generated wish, and backup export on a real device or emulator.
4. Confirm there is no guest login, demo account, fake contact seed, or developer bypass in the build.

Common validation commands from the repository root:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --no-configuration-cache
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug --no-configuration-cache
```

The app uses real authentication and real local data. Testers should create or sync their own contacts and events instead of relying on seeded examples.

#### Option D: Production release build

Use this only if you own release signing and policy review.

1. Configure release signing environment variables.
2. Confirm Firebase release config matches the release application id and signing certificate.
3. Run the release checklist in `docs/operations/release-checklist.md`.
4. Build:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleRelease --no-configuration-cache
```

Do not publish a release build until Play policy, Accessibility, SMS, contacts, Data Safety, backup, and privacy checks are complete.

## 3. First-Time Setup

### Step 1: Open RelateAI

When you open the app for the first time, the onboarding flow explains the main workflow:

1. Sync contacts.
2. Configure AI birthday wishes.
3. Enable reminders.
4. Track birthdays, anniversaries, and custom events.
5. Monitor relationship health.
6. Review automation setup.

You can continue through onboarding or open the setup checklist from onboarding.

### Step 2: Sign in with Google

RelateAI uses one real app login path:

1. Tap Sign in with Google.
2. Choose the Google account you want RelateAI to use.
3. Grant the requested account and Contacts access when prompted.
4. Wait for Firebase/Google sign-in to complete.

There is no alternate login, demo login, or fake local account path. If Google sign-in fails, fix the configuration, network, or Firebase issue rather than bypassing authentication.

### Step 3: Import contacts

From Home, Settings, AI Doctor, or Contacts:

1. Tap Sync Contacts.
2. Grant contacts permission when Android asks.
3. Let RelateAI import Google contacts and/or device contacts.
4. Review the Contacts list for missing relationship labels, missing channels, low health, VIPs, and contacts needing more context.

RelateAI uses contacts to discover events, personalize wishes, calculate relationship health, and determine delivery routes.

### Step 4: Review discovered events

Open Events and check:

1. Birthdays.
2. Anniversaries.
3. Work anniversaries.
4. Custom events.
5. Imported, manual, calendar, AI-inferred, merged, or conflict-sourced rows.
6. Verification labels such as verified, needs review, duplicate reminder, or conflicting date.

For duplicates or date conflicts:

1. Tap Merge here if two rows describe the same real event.
2. Tap Keep separate if both reminders should remain active.

To add an event manually:

1. Open Events.
2. Tap Add Event.
3. Choose Existing contact or New contact.
4. Select the event type.
5. Enter day, month, and optional year.
6. Save.

### Step 5: Configure AI

Open Settings, then AI and Automation:

1. Turn on AI Wish Generation if you want AI drafts.
2. Add a Gemini API key if this build requires API-key access.
3. Save the key.
4. Open AI Doctor.
5. Tap Test AI.

If AI is disabled or unavailable, RelateAI can still use fallback template messages where supported, but they may be less personal.

### Step 6: Configure delivery channels

RelateAI can route messages through SMS, WhatsApp, or Email.

| Channel | Setup required | Best for |
| --- | --- | --- |
| SMS | Phone number, SMS permission, SMS-capable device or carrier support. | Simple birthday and reminder messages. |
| WhatsApp | WhatsApp installed, app-level consent, Android Accessibility service enabled. | Contacts who prefer WhatsApp. |
| Email | Contact email address, Gmail sender address, Gmail app password, network access. | Professional contacts, clients, mentors, formal messages. |

You can temporarily block channels from Settings using Disabled channels. A blocked channel will not be used for automatic sends.

#### SMS setup runbook

Use SMS when the phone itself should send the message.

1. Confirm the contact has a valid phone number.
2. Open Settings or AI Doctor.
3. Grant SMS permission when Android asks.
4. Confirm SMS is not listed under Disabled channels.
5. Set the contact's preferred channel to SMS or leave it as Default/Unknown so route selection can choose SMS when it is available.
6. Generate or approve one low-risk message.
7. Check Activity History after dispatch.

Expected result:

1. If Android accepts the SMS handoff, RelateAI records the message as sent or pending delivery.
2. If Android later reports the sent or delivered callback as failed, RelateAI marks sent history, the matching dispatch attempt, and the pending message row as failed so AI Doctor no longer treats that attempt as successful channel proof and Messages can show recovery.
3. If the carrier or device does not return final callbacks, old pending-delivery SMS history can later become Unknown instead of staying stuck forever.

Do not use SMS automation until you have tested it on the actual device and SIM/carrier combination that will send the messages.

#### WhatsApp setup runbook

Use WhatsApp only when you intentionally want optional Accessibility-based automation.

1. Install WhatsApp or WhatsApp Business.
2. Confirm the contact has a phone number that WhatsApp can use.
3. Open AI Doctor.
4. Review the WhatsApp disclosure.
5. Confirm app-level WhatsApp automation consent.
6. Open Android Accessibility settings from AI Doctor.
7. Enable `RelateAI - Auto WhatsApp`.
8. Keep the device unlocked for the first test.
9. Set one low-risk contact to WhatsApp.
10. Review or schedule one test message and inspect Activity History.

Expected result:

1. RelateAI opens WhatsApp only for eligible sends.
2. The Accessibility service looks for compose/send controls and verifies the approved text before tapping send.
3. If the phone number is not usable, WhatsApp UI is unavailable, the device is locked, text verification fails, the Accessibility service disconnects, or the callback times out, the send fails with a provider reason and should be retried only after setup is fixed.

WhatsApp automation is optional and policy-sensitive. Use SMS or Email if the current build or distribution channel does not allow it.

#### Email setup runbook

Use Email when contacts prefer formal or professional messages.

1. Confirm the contact has a valid email address.
2. Open Settings.
3. Enter the Gmail sender address.
4. Enter the Gmail app password.
5. Tap Save email settings.
6. Open AI Doctor.
7. Tap Test Email.
8. Set the contact's preferred channel to Email.
9. Use Wish Preview to send a test to yourself before the first important real email.

Expected result:

1. Test Email sends to the configured sender address.
2. Real dispatch uses Gmail SMTP with bounded connection, read, and write timeouts.
3. Invalid sender or contact email addresses fail as setup/data issues instead of retrying as provider outages.
4. Invalid app passwords, missing network, or SMTP timeouts fail cleanly and move through retry or recovery instead of leaving the worker blocked.

#### Gemini and AI setup runbook

Use AI setup when you want generated drafts, style analysis, classification, or gift suggestions.

1. Sign in with Google.
2. Turn on AI Wish Generation in Settings.
3. Add a Gemini API key if this build requires one.
4. Open AI Doctor.
5. Tap Test AI.
6. Add Style Coach samples and contact context before judging draft quality.

Expected result:

1. AI Doctor reports Gemini ready.
2. Wish Preview can generate and regenerate drafts.
3. If Gemini is unavailable, RelateAI may use fallback copy where supported, but fallback or generic drafts are held for review instead of being promoted to full automation.

#### Notification, exact alarm, and battery setup runbook

Use these settings when scheduled messages and reminders need to run reliably.

1. Grant notification permission so approval prompts, reminders, and recovery notices can appear.
2. Allow exact alarm access where Android exposes that setting.
3. Review battery optimization if AI Doctor reports delayed or missing daily automation.
4. Reopen RelateAI after a device restart, app update, manual clock change, timezone change, or force-stop.
5. Refresh AI Doctor and check Messages for Failed, Blocked, or Needs review items.

Expected result:

1. Exact sends recover after normal reboot/update/time-change events.
2. Review-first messages stay review-first and do not auto-send during recovery.
3. Background work remains auditable through Activity History.

#### Channel testing and verification

Treat channel setup and channel verification as two separate checks.

| Check | What it proves | What it does not prove |
| --- | --- | --- |
| Test AI | Gemini access and AI generation are reachable from the current build/account. | It does not prove message delivery will work. |
| Test Email | Gmail SMTP can send a real email to the configured sender address. | It does not prove every contact email address is valid. |
| SMS permission/readiness | Android allows RelateAI to attempt SMS for selected SMS-routed contacts. | It does not prove the carrier, SIM, or device callbacks will succeed. A later failed SMS callback changes the dispatch attempt to failed. |
| WhatsApp readiness | WhatsApp is selected for at least one eligible contact, consent is granted, the app is installed, and Accessibility is enabled. | It does not prove WhatsApp UI automation will succeed while the device is locked or WhatsApp changes its UI. |
| Channel Verification | Activity History has recent successful dispatch evidence for the selected automatic channel; for Email, a recent successful Test Email from the same sender also counts. | It does not prove every future provider transaction or every contact address/number will work. |

Current test-send behavior is intentionally narrow: the built-in test-send path sends Email to yourself and records that sender as recently verified. SMS and WhatsApp should be proven with one low-risk real message on the actual device, SIM/carrier, WhatsApp installation, and Android version you plan to use.

### Step 7: Configure automation mode

Open Settings, then Automation Mode.

| Mode | Behavior |
| --- | --- |
| Fully Auto | Current default for new or unsupported saved global settings. Eligible messages can send without manual approval after all quality, schedule, channel, and permission checks pass. |
| Smart Approve | Some pending messages can auto-send when due, but review prompts are still used before the scheduled time and where policy requires review. |
| VIP Approve | VIP-like relationships require approval. If the approval window passes, the message can expire instead of sending late. |
| Always Ask | RelateAI requires review before sending. |
| Default | Contact uses the global mode, with relationship-aware adjustments. |

Contact-level automation can override the global mode. If Skip automatic wishes is enabled for a contact, RelateAI treats that contact like Always Ask.

When you select Fully Auto in Settings, RelateAI also prepares existing local data for full automation:

1. It saves the global mode as Fully Auto.
2. It clears explicit contact-level automation overrides back to Default.
3. It clears Skip automatic wishes for contacts that previously blocked automatic wishes.
4. It promotes queued pending messages to Approved only when they already have an available route and the selected draft still passes the full-automation quality gate.
5. It leaves fallback, generic, blank, very short, or otherwise low-confidence drafts in review.
6. It schedules promoted messages for exact send where possible.
7. It reports how many contacts were updated, how many queued messages were scheduled, how many messages still need contact details or channel setup, and how many messages still need review.

Use contact-level Always Ask, VIP Approve, or Skip automatic wishes again after enabling Fully Auto if specific people should remain manual.

### Step 8: Set quiet hours and blackout rules

Open Settings:

1. Set Quiet Hrs Start and Quiet Hrs End using 24-hour values.
2. Save quiet hours.
3. Use disabled channels when you do not want a channel used temporarily.

RelateAI avoids sending during quiet hours and blackout dates where supported. If a message becomes due during a blocked time, dispatch is deferred to the next allowed window.

### Step 9: Train Style Coach

Open Style Coach:

1. Paste message or email samples you wrote.
2. Separate samples with blank lines.
3. Tap Analyze and Update Style Profile.
4. Optionally analyze recent sent messages after you have sent enough messages.

Style Coach learns formality, emoji preference, typical greetings, language accent, average message length, and common expressions.

### Step 10: Create your first backup

Open Backup and Restore:

1. Enter a strong passphrase.
2. Export a backup.
3. Store the backup file somewhere safe outside the device.
4. Store the passphrase somewhere safe.

You need the same passphrase to preview or restore that backup. RelateAI does not store it.

### Step 11: Run AI Doctor

Open AI Doctor and tap Refresh.

Review these groups:

1. Required: Google contacts, Gemini, AI wish generation, full automation mode, automatable events, automatic delivery routes, channel verification, notifications, SMS, exact sends, daily automation, email setup.
2. Quality: Style Coach, personalization data, generic message risk.
3. Reliability: background reliability, recent errors, circuit state.
4. Recovery: failed dispatch and dead-letter records.

Use the recommended fix at the top before trying advanced automation.

The Channel Verification check looks at recent successful dispatch records for the channels selected by event-ready contacts. For Email, it also accepts a recent successful Test Email from the same configured sender address. If only Email is missing proof, AI Doctor offers Test Email. If SMS or WhatsApp is missing proof, AI Doctor opens Messages filtered to that channel, shows a verification assistant, and moves to the first useful tab so you can approve or retry one low-risk real message before relying on unattended automation.

For SMS, channel verification means the device accepted a tracked send attempt recently and has not reported a failed sent or delivered callback for that attempt. SMS `PENDING_DELIVERY` is useful evidence that Android accepted the handoff, but it is not the same as carrier-confirmed delivery. If a later SMS callback reports failure, RelateAI updates the dispatch attempt and message queue state to failed, and AI Doctor can ask for fresh proof.

### First Successful End-to-End Run

After the setup checklist, prove the workflow with one low-risk contact before enabling broader automation:

1. Sync contacts or create one manual contact and event.
2. Add relationship type, preferred channel, preferred language, and at least one memory note.
3. Configure the selected channel.
4. Temporarily set global Automation Mode to Always Ask if you want a review-first proof run.
5. Generate one wish.
6. Open Wish Preview.
7. Edit or regenerate until the draft is acceptable.
8. Approve and schedule.
9. Confirm the message appears in Messages as Scheduled or Sent.
10. Check Activity History for the generation, approval, scheduling, and dispatch records.
11. Export an encrypted backup.

Only rely on Smart Approve or Fully Auto after this run works and AI Doctor shows no required blockers for the channel you plan to use.

### Setup-to-Execution Map

Use this map when a user asks, "What do I do next?"

| Phase | Goal | Action | Success signal |
| --- | --- | --- | --- |
| 1. Access | Establish the real user identity. | Finish onboarding and sign in with Google. | Home opens without guest/demo mode. |
| 2. Data | Bring in real people and events. | Sync contacts or create manual contacts/events. | Contacts and Events show real records. |
| 3. Readiness | Remove blockers before automation. | Run AI Doctor and fix the top recommended issue. | Required checks are clear or explicitly understood. |
| 4. Personalization | Improve draft quality. | Add relationship type, channel, memories, topics to avoid, gifts, and style samples. | Contact quality and Why this draft signals become specific. |
| 5. Draft | Generate a message for one low-risk event. | Use Contact Detail, Events, or Messages to create a draft. | The draft appears in Wish Preview or Messages. |
| 6. Review | Keep control over content and timing. | Edit, regenerate, reject, approve, or schedule. | Messages shows Needs review, Scheduled, Sent, Rejected, or Failed with a clear reason. |
| 7. Delivery | Let policy and channel setup execute. | Wait for due time or retry after fixing setup. | Activity History records dispatch success or a specific failure. |
| 8. Recovery | Protect the data and learn from failures. | Export a backup, inspect failed sends, and retry one message first. | Backup exists and repeat errors have an owner/action. |

### Minimum Viable Setups by Workflow

RelateAI can be useful before every integration is configured. Use this table to avoid unnecessary setup work.

| Workflow | Required setup | Optional setup | What will not work yet |
| --- | --- | --- | --- |
| Manual reminders only | Google sign-in, contacts or manual events, notifications. | Gemini, delivery channels, Style Coach, backup. | AI generation and automatic sends. |
| AI drafts only | Google sign-in, contact/event data, AI Wish Generation, Gemini access. | Style Coach, delivery channels, exact alarms. | Scheduled channel delivery unless a channel is configured. |
| Review-first sending | Contact/event data, one delivery channel, notifications, Always Ask or VIP Approve. | Style Coach, exact alarms, backup. | Hands-off sending without approval. |
| Smart Approve | Contact/event data, one delivery channel, notifications, Smart Approve, AI Doctor checks. | Contact-level VIP overrides, exact alarms, Style Coach. | Strict manual approval for contacts left on Smart Approve. |
| Fully Auto SMS | Contact/event data, phone numbers, SMS permission, Fully Auto, no SMS blackout, AI Doctor clear for SMS. | WhatsApp, Gmail, Style Coach. | WhatsApp and Email fallback unless configured. |
| Fully Auto WhatsApp | Contact/event data, phone numbers, WhatsApp installed, consent, Accessibility enabled, Fully Auto, no WhatsApp blackout. | SMS fallback, Email fallback. | Reliable unattended WhatsApp sends while the device/app/UI state blocks Accessibility automation. |
| Fully Auto Email | Contact/event data, email addresses, Gmail sender email, Gmail app password, network, Fully Auto, no Email blackout. | SMS/WhatsApp fallback, Style Coach. | Email to contacts without addresses or when Gmail credentials fail. |
| Backup and migration | Backup passphrase, export file, restore passphrase on new install. | Gemini/Gmail setup before export. | Restored secrets, OAuth tokens, permissions, and device-bound setup. |

### Feature Variation Matrix

Use this matrix to understand how one workflow can change depending on data quality, policy, and setup.

| Dimension | Variation | Effect |
| --- | --- | --- |
| Contact source | Google contact, device contact, manual contact, restored contact. | Imported contacts may bring phone/email/event data; manual and restored contacts may need route cleanup. |
| Event source | Imported, manual, calendar, AI inferred, merged, conflict, unknown. | Conflict and duplicate rows should be resolved before automation. |
| Draft source | Gemini, regenerated Gemini, fallback template, user-edited draft. | Fully automatic promotion is safest for AI/user-edited drafts that pass quality checks; fallback/generic drafts stay review-first. |
| Automation policy | Fully Auto, Smart Approve, VIP Approve, Always Ask, Default. | Determines whether the draft can dispatch without explicit approval. |
| Channel route | Preferred route, fallback route, blocked route, unavailable route. | Dispatch tries usable routes only and records a setup reason when none are available. |
| Schedule state | Future, due, quiet-hours deferred, blackout deferred, expired. | Future/deferred messages wait; expired approval windows require a fresh review or retry. |
| Recovery state | Startup recovery, boot recovery, app update recovery, time-change recovery, stale dispatch recovery. | Recoverable approved/smart messages are rescheduled; unknown interrupted sends move to failed review to avoid duplicates. |
| External provider state | Ready, permission missing, credentials missing, transient failure, final failure. | Transient failures may retry automatically; setup and final failures require user action. |

### Automation Maturity Levels

Move through these levels in order instead of jumping directly to full automation.

| Level | Who should use it | Configuration | Exit criteria |
| --- | --- | --- | --- |
| Manual setup | New users and testers. | Google sign-in, contact sync/manual event, AI Doctor, backup. | One contact and one event are correct. |
| Draft assist | Users who want writing help only. | AI Wish Generation on, Automation Mode set to Always Ask. | One draft is generated, edited, and rejected or approved correctly. |
| Review-first scheduling | Most users after setup. | Always Ask or VIP Approve for sensitive contacts. | Scheduled messages appear with accurate channel and time. |
| Smart automation | Users with reliable setup and low-risk contacts. | Smart Approve globally, contact overrides for VIPs, quiet hours configured. | AI Doctor has no required blockers and one low-risk send succeeds. |
| Fully automated routing | Users who want RelateAI to handle eligible sends end to end. | Fully Auto globally, with manual overrides only for contacts that should stay protected. | SMS, WhatsApp, or Email delivery has been tested and Activity History is clean. |

## 4. Daily Workflow

Use this routine when you open RelateAI:

1. Open Home.
2. Check Next best action.
3. If setup needs attention, open AI Doctor and fix the highest ranked blocker.
4. If approvals are waiting, open Messages.
5. If an event is upcoming, open the contact and improve personalization before generating a draft.
6. If backup is overdue, export a fresh encrypted backup.
7. Use Analytics to review relationship health and neglected contacts.

Home is designed as the command center. It should tell you what needs attention first rather than making you inspect every screen manually.

## 5. Contacts: Build Better Personalization

Open Contacts to search, filter, and improve your relationship data.

Useful filters include:

1. Family.
2. Friends.
3. Work.
4. Close Friends.
5. Needs details.
6. Missing relationship.
7. Missing channel.
8. Low health.
9. VIP.

Useful sort modes include:

1. Name.
2. Health high.
3. Health low.

Open a contact to edit:

1. Nickname.
2. Relationship type.
3. Preferred language.
4. Preferred channel: SMS, WhatsApp, or Email.
5. Formality: Casual, Semi-formal, or Formal.
6. Style: Warm, Funny, Professional, or Emotional.
7. Automation mode.
8. Custom send time.
9. Gift budget and annual budget.
10. Interests.
11. Sensitive topics to avoid.
12. Current life phase.
13. Personal notes.
14. Skip automatic wishes.

RelateAI shows a personalization quality score. If it is low, future AI wishes may sound generic. Add a nickname, interests, memory notes, and a preferred channel to improve output quality.

## 6. Memory Vault

Use Memory Vault for facts that make AI messages specific.

Good memory examples:

1. "Likes mango lassi."
2. "Recently changed jobs."
3. "Met at college reunion."
4. "Avoid jokes about age."
5. "Prefers short messages."
6. "Daughter started school this year."

Memory categories:

1. General.
2. Preference.
3. Event.
4. Gift.
5. Milestone.

You can pin important notes and delete outdated notes. Keep notes concise and avoid storing secrets.

## 7. Gift Advisor

Use Gift Advisor to avoid repeated gifts and improve future gift suggestions.

To record a gift:

1. Open a contact.
2. Open Gift Advisor.
3. Tap Record gift.
4. Enter gift name, category, occasion, cost, notes, and feedback.
5. Save.

To ask for suggestions:

1. Add interests, memories, and prior gifts first.
2. Open Gift Advisor.
3. Tap Ask AI.
4. Review the suggestions against budget and the contact's preferences.

## 8. Events

Events are moments RelateAI can remind you about or generate messages for.

Supported event types include:

1. Birthday.
2. Anniversary.
3. Work anniversary.
4. Graduation.
5. Holiday.
6. Revival.
7. Follow-up.
8. Custom.

Event rows can have different origins:

1. Imported.
2. Manual.
3. Calendar.
4. AI inferred.
5. Merged.
6. Conflict.
7. Unknown source.

For each event, confirm:

1. The contact is correct.
2. The date is correct.
3. The event type is correct.
4. The reminder should remain active.
5. Duplicate or conflict labels are resolved.

## 9. Messages Inbox

Messages is the control room for draft and delivery states.

Tabs include:

1. Needs review.
2. Scheduled.
3. Blocked.
4. Sent.
5. Failed.

Depending on build state, you may also see Today, Pending, or Approved groupings.

Each message row can show:

1. Contact.
2. Event.
3. Channel.
4. Scheduled time.
5. Approval mode.
6. Readiness reason.
7. Primary action.

Common actions:

| Action | Use when |
| --- | --- |
| Review | You want to inspect or edit the draft before approval. |
| Approve | The draft is ready and should be scheduled or sent according to policy. |
| Reject | The draft should never be sent. |
| Retry Send | A failed send should be attempted again after setup is fixed. |
| Revoke | A scheduled approval should be withdrawn before dispatch. |
| Bulk approve | Several reviewed drafts are safe to approve together. |
| Bulk reject | Several drafts should be discarded together. |
| Bulk retry | Several failed drafts should be retried after fixing setup. |

Blocked messages usually need missing contact, missing phone, missing email, disabled channel, or Gmail setup fixes before approval or retry can work.

## 10. Wish Preview

Wish Preview is where you make the final decision about a generated draft.

Use it to:

1. Review the selected draft.
2. Choose variants such as Short, Standard, Long, Formal, Funny, or Emotional where available.
3. Edit the message directly.
4. Review Why this draft signals: relationship, language, channel, tone, memory notes, gift records, and previous wishes.
5. Review the Approval plan: event, route, schedule, approval, and draft quality.
6. Send a test email to yourself when Gmail sender details are configured.
7. Save feedback such as Too generic, Too formal, Wrong language, Too long, Not warm enough, or Repeated idea.
8. Regenerate the draft using feedback.
9. Approve and schedule.
10. Reject.
11. Review next pending wish.

RelateAI blocks blank approval and asks for a longer message if the draft is too short.

## 11. Automation and Dispatch Rules

RelateAI dispatches only after checks pass.

### Full Automation Quick Start

Use this path when the goal is to let RelateAI generate, approve, schedule, and send every eligible message with the least manual work.

1. Sign in with Google.
2. Sync contacts or create manual contacts and events.
3. Add at least one valid route for each automated contact: phone for SMS or WhatsApp, or email plus Gmail sender setup for Email.
4. Open Settings and confirm Automation Mode is Fully Auto.
5. If you just changed from another mode to Fully Auto, read the confirmation message. It tells you how many contact overrides were cleared, how many queued messages were scheduled, and how many messages still lack a route.
6. Set quiet hours and any temporary disabled channels.
7. Open AI Doctor and refresh diagnostics.
8. Fix required blockers for the channels you actually use.
9. Generate or wait for scheduled message generation.
10. Watch Messages and Activity History for the first few sends.

Successful full automation means:

1. A contact has a real event.
2. The contact has usable delivery details.
3. AI generation or fallback drafting produces a valid message.
4. Quality checks accept the draft for automation.
5. The message is Approved or policy-eligible.
6. The scheduler reaches the send window.
7. Dispatch uses the selected route or a supported fallback route.
8. The final state becomes Sent, Failed, Deferred, Expired, or Blocked with an auditable reason.

### Automation Modes in Detail

| Mode | Pending message behavior | Review notification | Typical use |
| --- | --- | --- | --- |
| Fully Auto | Treated as dispatch-eligible when scheduled time arrives. New generated messages can be stored as Approved when route and quality checks pass. | No review notification for fully automatic drafts. | Users who want end-to-end automatic sending after setup is verified. |
| Smart Approve | Needs review before the scheduled time. If still pending at or after the scheduled time, eligible messages can send. | Yes. | Lower-friction automation with a chance to review. |
| VIP Approve | Requires approval before the approval window closes. If the window passes, the message expires. | Yes. | Family, close relationships, sensitive contacts, or anything that should not send late. |
| Always Ask | Requires manual review and approval. | Yes. | First setup, sensitive messages, professional sends, and recovery after failures. |
| Default | Contact inherits the global mode, then relationship-aware rules apply unless global mode is Fully Auto. | Depends on resolved mode. | Most contacts after global mode is configured. |

Relationship-aware adjustments apply when the contact is Default and the global mode is not Fully Auto:

1. Family and Best Friend resolve to VIP Approve.
2. Close Friend and Relative resolve to Smart Approve unless the global mode is Always Ask.
3. Other contacts use the global mode.
4. Skip automatic wishes always resolves the contact to Always Ask.

When the global mode is Fully Auto, Default contacts stay Fully Auto regardless of relationship type. Use a contact-level override when you want a specific person to remain review-first.

### Execution Lifecycle

Every automated message goes through the same lifecycle:

1. Contact and event data are collected from the local database.
2. Message generation builds context from contact preferences, event type, memories, gift history, style profile, language, channel, and prior feedback.
3. Gemini generates variants, or RelateAI uses fallback/manual content where supported.
4. Quality checks downgrade risky automation when the draft is weak, generic, too short, or missing route setup.
5. RelateAI creates a pending or approved message record.
6. The scheduler waits for the selected send time.
7. Dispatch checks approval mode, quiet hours, blackout dates, message state, duplicate guards, channel availability, permissions, and sender configuration.
8. The sender attempts SMS, WhatsApp, or Email according to route policy.
9. RelateAI records sent, failed, expired, rejected, or retryable state.
10. Activity History, Analytics, Home, and Messages update from the resulting records.

### Automation Trigger Matrix

RelateAI has several ways to create, schedule, or recover work. Each path should still end in the same visible surfaces: Messages, Activity History, AI Doctor, Home, and Analytics.

| Trigger | Starts from | Creates or changes | User-visible result | Main safeguards |
| --- | --- | --- | --- | --- |
| Manual Generate Wish | Contact Detail, Events, or Messages. | A pending or approved draft for one contact/event. | Wish Preview and Messages. | Duplicate occurrence checks, quality gate, route readiness, approval mode. |
| Regenerate | Wish Preview. | Replaces draft text or variants using feedback. | Updated Wish Preview draft and quality summary. | Blank/short approval block, feedback tracking, quality summary. |
| Approve and Schedule | Wish Preview or Messages. | Moves an eligible draft to Approved or scheduled state. | Scheduled tab, Activity History, notification state. | Approval mode, route readiness, schedule policy, quiet hours. |
| Daily generation | Background worker. | Generates upcoming eligible birthday/event drafts. | Messages and notifications where review is needed. | Same domain generation path, duplicate occurrence checks, AI/fallback quality handling. |
| Holiday wish | Background worker where supported. | Creates holiday-related drafts for eligible contacts/events. | Messages and Activity History. | Event eligibility, route readiness, automation policy, quality checks. |
| Revival/check-in | Background worker where supported. | Creates low-frequency relationship revival drafts. | Messages, Home, Analytics, Activity History. | Cadence rules, relationship health, recent contact history, automation policy. |
| Post-event follow-up | Background worker where supported. | Creates follow-up drafts after a relevant event or send. | Messages and Activity History. | Recent send/event state, duplicate prevention, review-first handling where needed. |
| Exact send alarm | Android exact alarm or fallback worker. | Dispatches a due approved or policy-eligible message. | Sent/Failed/Scheduled state and Activity History. | Atomic dispatch claim, route fallback, provider retry policy, quiet hours. |
| Startup/boot/update/time recovery | App open, boot receiver, app update, time or timezone change. | Reschedules recoverable sends and reconciles stale dispatching states. | Messages, Failed recovery rows, Activity History. | Does not send directly from boot receiver, avoids duplicate sends, fails unknown interrupted sends for manual review. |
| Manual retry | Failed tab or recovery card. | Reattempts one failed message or a selected batch. | Failed/Scheduled/Sent state changes. | Setup checks, retry cap, route readiness, one-message-first recommendation. |
| Backup export/restore | Backup and Restore. | Exports encrypted data or replaces local data from backup. | Backup success, restore preview, restored local records. | Passphrase validation, checksum/manifest checks, replace-only warning, secrets excluded. |

After app startup, device restart, app update, manual clock change, or timezone change, RelateAI asks Android to recover automatic send alarms for messages that can still send without more user action. That includes Approved messages and Pending Smart Approve messages. Review-first messages such as Always Ask, VIP Approve, rejected messages, sent messages, and failed messages still require the normal review or retry flow.

If more than one due dispatch job exists for the same message, only the first job that atomically claims the message can send it. Later jobs exit without sending, which prevents duplicate messages after restart recovery, repeated scheduler calls, or delayed background work.

If the app or device stops while a message is already dispatching, RelateAI checks the latest dispatch attempt on the next startup or boot recovery. If the provider had already accepted the message, RelateAI reconciles the draft as sent so it is not sent twice. If the provider outcome is unknown after the stale-dispatch grace period, RelateAI moves the message to failed recovery and records an interrupted-dispatch failure. Review and retry that message manually after checking the channel, because an automatic resend could create a duplicate real-world message.

Fully automated sending requires all of these to be true:

1. The contact has a valid event and route.
2. The selected channel is enabled and configured.
3. Required Android permissions or external credentials are available.
4. The automation mode allows dispatch without review for that contact and event.
5. The message passes quality and personalization checks.
6. Quiet hours and blackout rules allow the send time.
7. The message has not already been handled for the same occurrence.
8. AI Doctor has no required blocker for the selected path.

If any check fails, RelateAI should hold, downgrade to review, defer, expire, or mark the message failed instead of silently sending.

The dispatch policy considers:

1. Message status.
2. Approval mode.
3. Scheduled time.
4. Approval window.
5. Quiet hours.
6. Blackout dates.
7. Channel availability.
8. Contact data.
9. Permissions.
10. Sender configuration.
11. Duplicate and already-handled states.

### Route Selection and Fallback

RelateAI separates route selection during draft creation from route fallback during dispatch.

During draft creation, RelateAI chooses the best available route using:

1. Previously successful delivery history for that contact when available.
2. The contact's preferred channel if it is currently usable.
3. Default availability order: SMS, then WhatsApp, then Email.

During dispatch, RelateAI tries the draft's preferred route first when that route is usable, then falls back by channel:

| Draft or preferred channel | Dispatch route order |
| --- | --- |
| SMS or Unknown | SMS, WhatsApp, Email |
| WhatsApp | WhatsApp, SMS, Email |
| Email | Email, SMS, WhatsApp |

A route is usable only when required details and setup are present:

| Channel | Required before dispatch |
| --- | --- |
| SMS | Contact phone number, SMS permission, no SMS blackout, and a device/carrier path that can send SMS. |
| WhatsApp | Contact phone number, no WhatsApp blackout, WhatsApp or WhatsApp Business installed, app-level consent, Accessibility service enabled, unlocked/usable device state, and detectable compose/send controls. |
| Email | Contact email address, no Email blackout, Gmail sender address, Gmail app password, and network access. |

If all routes are unavailable, the message remains blocked or failed with a route/setup reason. Fix the missing data or setup, then retry one message before using bulk retry.

WhatsApp automation has two timeout layers. The Accessibility service times out if WhatsApp does not open, cannot find the compose field, cannot verify typed text, cannot find the send button, or cannot confirm the send handoff. The sender also has a watchdog timeout for the service callback itself. If that watchdog fires, RelateAI cancels the queued/current WhatsApp automation job and records a `SENDER_CALLBACK_TIMEOUT` failure instead of letting the dispatch worker wait forever. If the Accessibility service disconnects, RelateAI fails the active and queued WhatsApp jobs with a setup failure instead of leaving them waiting for the watchdog. If a phone number has no usable digits, RelateAI fails before opening WhatsApp.

Email delivery uses Gmail SMTP with explicit connection, read, and write timeouts. If Gmail or the network does not respond within that window, RelateAI treats the result as a transient email provider failure and uses the normal retry/recovery path instead of leaving the dispatch worker blocked.

SMS delivery depends on Android and carrier callbacks. If Android accepts the SMS handoff, RelateAI records the message as pending delivery until a sent or delivered callback arrives. For tracked dispatch attempts, those callbacks update sent history, the dispatch-attempt evidence used by AI Doctor, and failed-callback recovery in Messages. A successful sent callback becomes `SENT`, a successful delivery callback becomes `DELIVERED`, and a failed callback becomes `FAILED_FINAL` with the Android callback result code. Some devices or carriers do not provide callbacks reliably. On startup or boot recovery, very old SMS pending-delivery records are marked `UNKNOWN` so history and analytics do not stay stuck in an in-progress state forever.

Transient provider failures are different from setup blockers. If a provider fails before accepting a message, such as a temporary SMS radio failure or transient email provider failure, RelateAI records the failed attempt, moves the draft back into an approved retry state, and schedules another exact-send attempt at the retry time. Automatic retries are bounded; after repeated retryable failures, RelateAI stops retrying and leaves the message in failed recovery so the setup can be inspected. Missing permissions, missing delivery details, disabled channels, invalid Gmail credentials, and unavailable WhatsApp automation still require setup repair before retry.

Message statuses:

| Status | Meaning |
| --- | --- |
| Pending | Draft exists but may still need approval or scheduled eligibility. |
| Approved | User or policy approved it for dispatch. |
| Dispatching | RelateAI is currently trying to send. |
| Sent | Dispatch succeeded or reached a delivered/sent state. |
| Rejected | User rejected it. |
| Failed | Dispatch failed and needs review or retry. |
| Expired | Approval window or timing rules expired. |
| Unknown | RelateAI cannot safely interpret the state. |

Dispatch outcomes:

| Outcome | Meaning |
| --- | --- |
| Send now | The message is due and eligible. |
| Defer until | The message is not due yet or quiet hours/blackout rules apply. |
| Needs approval | Current mode requires user review before sending. |
| Expire | The approval window elapsed. |
| Blocked | State or setup prevents dispatch. |

### Automation Presets by Use Case

Use these presets as starting points. Contact-level settings can override the global setting.

| Use case | Recommended mode | Channel | Extra safeguards |
| --- | --- | --- | --- |
| Family birthdays | VIP Approve or Always Ask | SMS or WhatsApp | Add memories, topics to avoid, and review every important draft. |
| Friends and casual contacts | Smart Approve | SMS or WhatsApp | Keep quiet hours active and review the first few sends. |
| Professional network | Always Ask or Smart Approve | Email | Use Formal style, test email, and avoid fully automatic first sends. |
| High-volume reminders | Smart Approve | SMS or Email | Filter contacts needing details before bulk approval. |
| Sensitive relationships | Always Ask | Any configured channel | Disable automatic wishes at contact level if mistakes would be harmful. |
| Recovery after failures | Always Ask temporarily | The repaired channel only | Retry one message before bulk retry. |

### Operational Safety Rules

1. Never treat a generated draft as final for sensitive relationships until you have read it.
2. Do not enable Fully Auto for a channel that has not passed a real or dry-run setup check.
3. Keep WhatsApp automation optional because it depends on Android Accessibility, WhatsApp UI state, and release policy approval.
4. Keep email automation scoped to contacts with valid email addresses and a tested Gmail sender.
5. Keep SMS automation scoped to contacts with a valid phone number and active SMS permission.
6. Use Activity History as the audit trail for sync, generation, approval, scheduling, dispatch, backup, and errors.
7. If the app cannot explain why a message is ready, keep it in review-first mode.

## 12. Workflow Automation Playbook and Advanced Recipes

Use this section when RelateAI is already installed and you want repeatable workflows that reduce daily effort.

### Workflow A: End-to-end birthday automation

Goal: Generate, schedule, send, and audit birthday wishes with minimal intervention.

1. Sync contacts.
2. Filter Contacts for Missing relationship, Missing channel, and Needs details.
3. Add relationship type, preferred channel, preferred language, and at least one memory for the contacts you want automated.
4. Configure SMS, WhatsApp, or Email using the channel runbooks in First-Time Setup.
5. Set global Automation Mode to Smart Approve or Fully Auto.
6. Reapply Always Ask, VIP Approve, or Skip automatic wishes for sensitive contacts.
7. Run AI Doctor and fix Required issues.
8. Let daily generation create upcoming drafts, or generate one manually from Events.
9. Watch Messages for Scheduled, Failed, and Blocked states.
10. Review Activity History after the first few sends.

Success signals:

1. Event-ready contacts have usable routes.
2. Low-quality drafts stay in review.
3. Approved or policy-eligible drafts dispatch once, not multiple times.
4. Failures show a concrete setup/provider reason.

### Workflow B: Weekly relationship maintenance

Goal: Keep relationship data fresh without reviewing the whole app every day.

1. Open Home and handle the next best action.
2. Open AI Doctor and refresh.
3. Sync contacts.
4. Open Contacts filtered by Needs details.
5. Improve five high-value contacts with relationship type, language, preferred channel, interests, and memory notes.
6. Open Messages, then Failed and Blocked.
7. Fix one setup issue and retry one message.
8. Open Analytics and review neglected contacts.
9. Export a backup if the last backup is stale.

Success signals:

1. Generic-message risk trends down.
2. Failed and blocked queues stay small.
3. Backup age stays within your tolerance.

### Workflow C: Professional network follow-up

Goal: Send polished professional messages without accidental casual or emotional wording.

1. Mark professional contacts with Work or a similar relationship type.
2. Set preferred channel to Email where possible.
3. Set formality to Formal and style to Professional.
4. Add notes about role, company, last conversation, and topics to avoid.
5. Configure Gmail sender details and run Test Email.
6. Keep global mode on Always Ask or set those contacts to Always Ask/VIP Approve.
7. Generate work anniversary, follow-up, or custom-event drafts.
8. Send a test email to yourself for the first important template.
9. Approve only after checking the route, schedule, and message body.

Success signals:

1. Drafts use the right tone and channel.
2. Gmail setup is verified before real dispatch.
3. Professional contacts do not inherit hands-off personal automation by accident.

### Workflow D: Safe bulk operations

Goal: Approve, reject, or retry many drafts without losing control.

1. Open Messages.
2. Select the state tab first: Needs review, Scheduled, Failed, or Blocked.
3. Apply channel or readiness filters where available.
4. Open one representative draft in Wish Preview.
5. Confirm channel, schedule, approval mode, and draft quality.
6. Use Bulk approve only for low-risk drafts with complete route setup.
7. Use Bulk reject for duplicates, stale drafts, wrong-event drafts, or low-quality fallback drafts.
8. Use Bulk retry only after a single repaired retry succeeds.

Success signals:

1. Bulk actions affect the intended tab/filter group.
2. Activity History shows expected action counts.
3. Failed messages do not immediately repeat the same provider/setup error.

### Workflow E: Recovery after a bad provider day

Goal: Prevent repeated failures from spreading across the queue.

1. Open Messages, then Failed.
2. Open Activity History and identify whether the failures are SMS, WhatsApp, Email, AI, or scheduling related.
3. Temporarily disable the failing channel in Settings if many drafts could use it.
4. Fix the provider issue: permission, credential, network, SIM/carrier, WhatsApp Accessibility, or Gmail app password.
5. Run AI Doctor.
6. Retry one low-risk message.
7. Remove the channel blackout only after the retry succeeds.
8. Retry remaining messages in small batches.

Success signals:

1. The failing channel stops receiving new automatic attempts while it is unhealthy.
2. One repaired retry succeeds before bulk retry.
3. Automatic retry limits are not exhausted repeatedly.

### Recipe A: Safe review-first birthdays

Use this when you want help drafting but never want automatic sending without review.

1. Open Settings.
2. Set Automation Mode to Always Ask.
3. Sync contacts.
4. Add relationship type and preferred channel for important contacts.
5. Train Style Coach.
6. Let RelateAI generate pending wishes.
7. Open Messages, then Needs review.
8. Open each Wish Preview.
9. Edit or regenerate.
10. Approve only when ready.

### Recipe B: Smart approve for normal contacts

Use this when you want lower-friction automation with safeguards.

1. Set global Automation Mode to Smart Approve.
2. Keep VIP contacts on VIP Approve or Always Ask.
3. Configure quiet hours.
4. Configure the preferred channel per contact.
5. Run AI Doctor.
6. Review pending drafts when notified.
7. Let eligible low-risk drafts send when due if policy allows.

### Recipe C: Full automation for eligible contacts

Use this when you want RelateAI to send all eligible messages automatically after setup checks pass.

1. Back up current data.
2. Open Settings.
3. Save SMS, WhatsApp, or Email setup for the channels you plan to use.
4. Set quiet hours.
5. Remove disabled channels that should be available.
6. Select Fully Auto.
7. Read the confirmation message.
8. Open AI Doctor.
9. Fix required blockers.
10. Open Messages and check Scheduled, Failed, and Blocked.
11. Let one low-risk send complete.
12. Check Activity History for the dispatch attempt and final state.

| Confirmation item | Meaning |
| --- | --- |
| Contacts updated | Old manual overrides were reset to Default. |
| Queued messages scheduled | Pending routable drafts were promoted to Approved. |
| Messages needing setup | Contact details or channel setup are still missing. |
| Messages needing review | Draft quality is not safe for full automation, such as fallback, generic, blank, or too-short text. |

After enabling Fully Auto, protect special cases explicitly:

1. Open the contact.
2. Set Automation mode to Always Ask or VIP Approve.
3. Enable Skip automatic wishes if that contact should never auto-send.
4. Save preferences.

To pause full automation without losing contacts or drafts:

1. Open Settings.
2. Change Automation Mode to Always Ask or VIP Approve.
3. Disable channels you do not want used.
4. Revoke scheduled approvals in Messages when necessary.

### Recipe D: VIP approval for family and close relationships

Use this when sensitive relationships should always get human review.

1. Mark close contacts as VIP or use relationship labels such as Family, Best Friend, Close Friend, or Relative.
2. Set contact-level mode to VIP Approve or Always Ask.
3. Add richer memories and topics to avoid.
4. Review Wish Preview before approval.

### Recipe E: WhatsApp automation

Use this only if you understand and accept the automation behavior.

1. Install WhatsApp or WhatsApp Business.
2. Open AI Doctor.
3. Review the WhatsApp automation card.
4. Confirm app-level consent.
5. Open Android Accessibility settings.
6. Enable RelateAI - Auto WhatsApp.
7. Set preferred channel to WhatsApp for selected contacts.
8. Review a low-risk pending draft and let one low-risk real WhatsApp send complete before relying on automation. AI Doctor can verify setup readiness, but the current test-send path does not perform a no-send WhatsApp dry run.

RelateAI should only inspect WhatsApp UI controls needed to place the approved message and tap send. It should not store WhatsApp chat contents.

### Recipe F: Email wishes for professional contacts

1. Open Settings.
2. Enter Gmail sender email.
3. Enter Gmail app password.
4. Save email settings.
5. Open AI Doctor.
6. Run Test Email.
7. Set professional contacts to Email.
8. Use Formal or Professional style.
9. Use Wish Preview to send a test to yourself before approving the first real email.

### Recipe G: Manual custom reminder

1. Open Events.
2. Tap Add Event.
3. Choose Existing contact or New contact.
4. Select Custom.
5. Add label, day, month, and optional year.
6. Save.
7. Open the event or contact later to generate a related message.

### Recipe G1: Holiday wishes

Use this for seasonal greetings where one event may apply to many contacts.

1. Confirm the holiday or custom event exists where supported.
2. Filter Contacts for the people who should receive the greeting.
3. Add language, relationship type, and channel details for those contacts.
4. Keep sensitive contacts on Always Ask or VIP Approve.
5. Use Smart Approve or Fully Auto only for low-risk contacts.
6. Review the first few generated holiday drafts for tone and repetition.
7. Bulk reject duplicates or generic drafts.
8. Let eligible scheduled messages dispatch after route and timing checks pass.

### Recipe G2: Revival check-ins

Use this when relationship health or time since last contact suggests a gentle check-in.

1. Open Analytics and review neglected or fading contacts.
2. Open the contact and add recent context before generating a message.
3. Use Memory Vault for life updates, shared history, or topics to avoid.
4. Keep the first revival drafts review-first.
5. Approve only messages that feel natural for the relationship.
6. Use Activity History to confirm follow-up or revival automation is not repeating too frequently.

Revival messages are more context-sensitive than birthday wishes. If the relationship is sensitive, use Always Ask and write or edit the message manually.

### Recipe G3: Post-event follow-up

Use this when a birthday, meeting, anniversary, or custom event deserves a follow-up after the first message.

1. Confirm the original event and message history are correct.
2. Add notes about the event outcome or response.
3. Generate or review the follow-up draft.
4. Check that the message references the event naturally and does not sound automated.
5. Set professional or sensitive follow-ups to Always Ask.
6. Approve and schedule only if the timing is appropriate.

### Recipe H: Improve generic AI messages

1. Open AI Doctor.
2. Check Generic Message Risk and Personalization Data.
3. Open Contacts filtered by Needs details.
4. Add nickname, relationship type, interests, preferred language, channel, memories, and sensitive topics.
5. Open Style Coach and add writing samples.
6. Regenerate the draft with feedback.

### Recipe I: Recover on a new install

1. Install RelateAI.
2. Open Backup and Restore.
3. Select the encrypted backup file.
4. Enter the backup passphrase.
5. Preview the backup.
6. Confirm restore.
7. Re-enter secrets that are intentionally excluded, such as API keys, Gmail app passwords, and account/session credentials.
8. Re-run AI Doctor.

Current restore behavior is replace-only. It does not merge backup data into existing local data.

### Recipe J: Failed-send recovery

1. Open Messages.
2. Open Failed.
3. Read the recovery card.
4. Open AI Doctor.
5. Fix missing channel, permission, Gmail, WhatsApp, phone, email, or background setup.
6. Return to Messages.
7. Retry one message first.
8. Bulk retry only after the single retry works.

### Recipe K: Backup before risky changes

Create an encrypted backup before:

1. Signing out.
2. Changing phones.
3. Uninstalling RelateAI.
4. Clearing app data.
5. Joining a beta or migration build.
6. Restoring a backup that will replace local data.

### Recipe L: Bulk review without losing control

Use this when several low-risk drafts are waiting.

1. Open Messages.
2. Open Needs review.
3. Filter or scan for contacts with complete channel setup.
4. Open at least one representative draft in Wish Preview.
5. Confirm route, schedule, approval mode, and draft quality.
6. Use Bulk approve only for drafts that are clearly safe.
7. Use Bulk reject for obvious duplicates, stale messages, or wrong-event drafts.
8. Check Activity History after bulk actions.

Do not bulk approve sensitive, professional, emotional, or VIP messages unless you have inspected each draft.

### Recipe M: Temporary channel blackout

Use this when a provider is unreliable or you do not want a channel used for a period of time.

1. Open Settings.
2. Add the channel to Disabled channels.
3. Open AI Doctor and refresh readiness.
4. Review Messages for blocked or deferred drafts.
5. Choose another channel on key contacts if needed.
6. Remove the channel from Disabled channels when the blackout ends.
7. Retry one failed message before retrying in bulk.

### Recipe N: Streamline a weekly maintenance workflow

Use this when RelateAI is already set up and you want a repeatable low-effort routine.

1. Once a week, open AI Doctor and refresh.
2. Sync contacts.
3. Review only Required and Recovery issues first.
4. Open Contacts filtered by Needs details.
5. Add details for the next five highest-value contacts.
6. Open Messages, then Failed and Blocked.
7. Retry one repaired message.
8. Export a backup if the last backup is stale.

This keeps automation reliable without reviewing every contact every day.

## 13. Shortcuts, Widgets, and Deep Links

RelateAI includes quick entry points for common workflows.

Launcher shortcuts:

1. Compose opens the message creation/review path where supported.
2. Contacts opens the Contacts surface directly.

Home screen widget:

1. Add the RelateAI widget from the Android launcher widget picker.
2. Use it to see today's birthdays, the next upcoming event, and pending approval counts.
3. Tap the widget to open the app and continue the workflow.

Supported deep links:

| Deep link | Opens |
| --- | --- |
| `relateai://home` | Home. |
| `relateai://contacts` | Contacts. |
| `relateai://messages` | Messages. |
| `relateai://settings` | Settings. |
| `relateai://backup-restore` | Backup and Restore. |
| `relateai://contact/{contactId}` | A specific contact. |
| `relateai://wish/{contactId}/{messageRef}` | A specific Wish Preview. |

Use deep links from notifications, shortcuts, QA scripts, or other trusted Android automation tools. Contact and message identifiers must match records that exist on the device.

## 14. Privacy and Data Handling

RelateAI is local-first. It does not rely on a custom app server in this repository.

Data that may be stored locally includes:

1. Contact names, phone numbers, email addresses, birthdays, and relationship labels.
2. Events and reminders.
3. Message drafts, approved messages, feedback, and sent history.
4. Memories, gift records, interests, notes, and topics to avoid.
5. Activity logs and local diagnostic snapshots.
6. Preferences and encrypted configuration.

External services are explicit:

| Service | Used for |
| --- | --- |
| Google/Firebase auth | Sign-in and authenticated access. |
| Google People API | Google Contacts sync. |
| Gemini | AI generation, classification, style analysis, and suggestions. |
| Gmail SMTP | Email delivery and test email. |
| Android SMS | SMS dispatch. |
| WhatsApp UI via Accessibility | Optional WhatsApp send automation. |

Backups exclude secrets such as API keys, OAuth tokens, SMTP credentials, and device-bound keys. After restore, configure those again.

## 15. Backup and Restore Details

Important rules:

1. The live SQLCipher database key and the backup passphrase are different.
2. The backup passphrase encrypts exported backup files.
3. RelateAI does not store the backup passphrase.
4. Losing the passphrase means the backup cannot be restored.
5. Restore validates the manifest, checksum, passphrase, file format, and version before replacing local data.
6. Wrong passphrase, malformed file, checksum mismatch, unsupported version, and oversized file errors should stop restore before data changes.
7. Sign-out deletes local data, secure preferences, scheduled work, cached database key material, and auth state.

Recommended backup frequency:

1. Immediately after first setup.
2. After large contact imports.
3. After adding important memory or gift data.
4. Before app updates that may include migrations.
5. At least every 30 days if you rely on RelateAI regularly.

## 16. Troubleshooting and Prevention

Use this order before changing many settings at once:

1. Identify the affected workflow: sign-in, contacts, AI, draft quality, approval, scheduling, SMS, WhatsApp, Email, backup, restore, or analytics.
2. Open AI Doctor and refresh.
3. Fix the top Required issue first.
4. Open Activity History and filter by the affected area if the problem repeated.
5. Open the affected contact, event, or message and verify the exact route, schedule, status, and approval mode.
6. Retry one action only.
7. If the retry succeeds, continue with the batch or automation flow.
8. If the retry fails with the same reason, treat it as a setup or provider problem instead of retrying repeatedly.

Root-cause categories:

| Category | Usually means | First place to check |
| --- | --- | --- |
| Missing data | Contact, event, phone, email, language, relationship, or memory context is incomplete. | Contacts, Events, Wish Preview. |
| Permission/setup | Android permission, exact alarm, notification, Accessibility, Gemini, or Gmail setup is missing. | AI Doctor and Settings. |
| Policy mode | Automation mode, contact override, quiet hours, blackout, or approval window prevents dispatch. | Contact Detail, Settings, Messages. |
| Draft quality | Draft is blank, too short, fallback, generic, or not personalized enough for automation. | Wish Preview and AI Doctor quality checks. |
| Provider failure | Gemini, Gmail, carrier SMS, WhatsApp UI, network, or auth failed outside the local database. | Activity History and provider-specific setup. |
| Recovery state | App/device interruption, restart, update, time change, or stale dispatch changed scheduling state. | Messages, Activity History, AI Doctor recovery group. |
| Backup/restore | Passphrase, file format, checksum, version, or replace-only restore limitation. | Backup and Restore. |

| Problem | Root cause | Solution or workaround | Prevention or improvement |
| --- | --- | --- | --- |
| No contacts appear | Contacts were never synced, Google access is missing, or Android contacts permission was denied. | Open Settings or AI Doctor, run Sync Contacts, grant contacts permission, and confirm Google sign-in is still valid. | Complete contact import during onboarding and re-run AI Doctor after permission changes. |
| Google sign-in fails | OAuth/Firebase config, signing SHA-1, network, or user cancellation issue. | Retry on a stable network. If the build is local, verify Firebase OAuth setup and the matching `google-services.json`. | Test sign-in before relying on Google sync. Keep debug and release Firebase config separate. |
| AI Doctor says Gemini is missing | No Gemini API key, missing Firebase/auth access, or AI generation toggle is off. | Add a Gemini API key in Settings or sign in with the required account, then enable AI Wish Generation and tap Test AI. | Run Test AI after setup and after changing accounts or API keys. |
| AI test hits quota or rate limit | Gemini quota is exhausted or calls are too frequent. | Wait a few minutes, then retry. Reduce repeated regeneration attempts. | Avoid bulk regeneration during setup. Monitor recent AI errors in AI Doctor. |
| AI returns invalid or empty output | Provider returned malformed JSON, partial content, or an unexpected error payload. | Regenerate once. If it repeats, use fallback draft or edit manually and check recent errors. | Keep prompts simple, add clean contact context, and avoid storing unusual control text in notes. |
| Wishes sound generic | Contact has weak personalization data or Style Coach has no writing samples. | Add nickname, relationship type, interests, memories, preferred language, preferred channel, and style samples. Regenerate with feedback. | Filter Contacts by Needs details and improve high-value contacts first. |
| Wrong language or tone | Contact preferences or Style Coach profile do not match the desired output. | Set preferred language, formality, and style on the contact. Use feedback such as Wrong language or Too formal, then regenerate. | Review contact preferences before generating wishes for important events. |
| Approval is blocked | Draft is blank, too short, missing contact/event context, or route readiness failed. | Edit the draft, generate again, or fix the missing contact/channel setup. | Review the Approval plan in Wish Preview before tapping Approve. |
| Message did not send at scheduled time | It is before scheduled time, quiet hours or blackout rules apply, exact alarms are disabled, or background work was delayed. | Check Messages and AI Doctor. Enable exact alarm access if needed and review quiet hours. | Keep quiet hours accurate, allow background reliability, and use AI Doctor after OS battery changes. |
| Message remains Needs review | Current automation mode requires approval. | Open Wish Preview and approve, reject, or edit. | Use Smart Approve or Fully Auto only for contacts where you accept lower-friction automation. |
| Fully Auto did not schedule some existing drafts | Those pending messages had no available route, such as missing phone, missing email, disabled channel, or missing Gmail setup. | Open Messages for blocked drafts, add contact details or channel setup, then regenerate, approve, or retry. | Before selecting Fully Auto, run AI Doctor and filter Contacts for missing channel details. |
| A contact changed from manual to automatic after enabling Fully Auto | Enabling Fully Auto clears explicit contact automation overrides back to Default and clears Skip automatic wishes so the global mode can apply. | Reopen the contact and set Always Ask, VIP Approve, or Skip automatic wishes again for protected contacts. | Before enabling Fully Auto, make a short list of people who must remain review-first. |
| A message sent even though it was still Pending in Smart Approve | Smart Approve permits eligible pending messages to send at or after scheduled time if the user did not act earlier. | Use Always Ask or VIP Approve for contacts that must never send without manual approval. | Treat Smart Approve as timed review, not strict manual approval. |
| A Fully Auto message still asks for review | The draft failed quality, route, or safety checks, or the contact has a review-first override. | Improve contact context, fix route setup, inspect the approval plan, and remove unintended contact overrides. | Keep personalization data complete and verify contact preferences before important events. |
| Message used a fallback channel | The preferred route was blocked or unusable at dispatch time, so RelateAI tried the next supported route. | Check Disabled channels, phone/email details, Gmail setup, SMS permission, and WhatsApp readiness. | Set the preferred channel carefully and disable fallback channels you do not want used. |
| VIP message expired | VIP Approve approval window elapsed. | Generate or retry a fresh draft and approve it before the event window closes. | Review VIP pending messages promptly from notifications or Messages. |
| SMS send fails | SMS permission missing, no phone number, no SIM/carrier support, or channel disabled. | Grant SMS permission, add a phone number, unblock SMS, or choose another channel. | Run AI Doctor and check preferred channel readiness before enabling auto-send. |
| SMS looked accepted, then later failed | Android accepted the original handoff, but the sent or delivered callback later reported failure from the device, radio, SIM, or carrier path. | Open Messages, then Failed, and inspect Activity History. Confirm the failure reason, fix SMS permission/SIM/carrier state, then retry one low-risk message. | Do not treat initial SMS handoff as final delivery. Verify the same device and SIM before unattended automation. |
| SMS history stays Pending delivery | Android or the carrier did not return the expected sent/delivered callback. | Reopen RelateAI after the stale window; startup recovery marks very old pending SMS records as Unknown. Check the device messaging app if you need carrier-level confirmation. | Keep SMS as a tested channel, avoid force-stopping around scheduled sends, and treat Unknown SMS status as unconfirmed rather than failed. |
| WhatsApp send fails | Consent missing, Accessibility service disabled or disconnected, WhatsApp not installed, invalid phone number, device locked, compose field not found, send button not verified, or the Accessibility callback timed out. | Confirm consent, enable RelateAI - Auto WhatsApp in Accessibility, verify the contact phone number, unlock device, install/update WhatsApp, then retry one low-risk message or fallback to SMS. | Test with a low-risk contact, keep WhatsApp automation optional, and treat repeated `SENDER_CALLBACK_TIMEOUT` or service-disabled failures as an Accessibility reliability issue. |
| Email send fails | Gmail sender details missing, sender/contact email address invalid, app password invalid, network unavailable, contact email missing, or SMTP connection/read/write timeout. | Add or correct Gmail sender address and app password, update the contact email address, run Test Email, then retry one message after network is stable. | Use Email only for contacts with valid email addresses, keep credentials current, and run Test Email after changing credentials or network conditions. |
| Test Email works but a real email fails | The self-test proves sender credentials, but the contact email address, network state, Gmail policy, or SMTP transaction can still fail later. | Verify the contact email address, retry on a stable network, and inspect Activity History for the provider reason. | Test the sender after credential changes and keep professional contacts' email addresses up to date. |
| AI Doctor says Channel Verification is missing | A selected automatic channel has no recent successful dispatch evidence in Activity History; for Email, there is also no recent successful Test Email from the same sender. | Send one low-risk real SMS/WhatsApp message on the missing channel, or run Test Email for Email, then refresh AI Doctor. | Before using Fully Auto, prove every selected channel on the actual device and provider account. |
| SMS Channel Verification was present but later disappears | A tracked SMS attempt was initially accepted as pending or sent, then a later SMS callback marked the attempt failed, or the successful evidence aged out of the verification window. | Send one new low-risk SMS after confirming SIM, signal, permissions, disabled-channel settings, and the contact phone number. | Treat SMS verification as recent device/provider evidence, not a permanent certificate. Refresh AI Doctor after carrier, SIM, device, or permission changes. |
| SMS or WhatsApp appears as not used in AI Doctor | No event-ready automatic contact currently selects that channel, or the channel is disabled and route selection chose another usable route. | Check contact preferred channels, disabled channels, phone numbers, and event readiness. | Configure preferred channels only for contacts you intend to automate, then refresh AI Doctor. |
| Test send is unavailable for SMS or WhatsApp | The current built-in test-send path is Email-to-self only; SMS and WhatsApp tests would send real messages. | Use one low-risk contact/message to prove SMS or WhatsApp instead of expecting a synthetic test button. | Keep channel verification evidence current and avoid unattended sends on channels without recent proof. |
| A failed send becomes scheduled again | The provider failure was retryable, so RelateAI queued an automatic retry instead of requiring manual action. | Watch Messages and Activity History. If the retry repeats, open AI Doctor and repair the provider or channel setup. | Treat repeated retryable failures as a setup or provider reliability issue, and test one low-risk send after fixing it. |
| Automatic retries stop after repeated transient failures | RelateAI reached its automatic retry limit for the same message and provider failure pattern. | Open AI Doctor and Activity History, fix the channel/provider issue, then retry one message manually. | Avoid leaving unreliable providers enabled for full automation; disable the channel temporarily if failures repeat. |
| Message was stuck as Sending now after an app/device interruption | The app stopped after claiming the message for dispatch but before it could finish writing the final provider result. | Reopen RelateAI. Startup recovery reconciles provider-accepted attempts as sent, requeues known retryable attempts, or moves unknown stale attempts to Failed for manual review. | Avoid force-stopping during scheduled sends, keep battery settings permissive, and retry interrupted unknown outcomes manually to prevent duplicate messages. |
| Notifications do not appear | Notification permission is denied or OS notification settings are disabled. | Grant notification permission in Android settings and refresh AI Doctor. | Enable notifications during setup because approvals and reminders depend on them. |
| Exact sends unavailable | Android exact alarm access is disabled by system settings. | Open system settings from AI Doctor and allow exact alarms if available. | Keep WorkManager fallback enabled, but use exact alarm access for precise scheduled sends. |
| Daily automation is not running | WorkManager chain is unscheduled, battery optimization delayed work, or app was force-stopped. | Open AI Doctor, refresh, and review battery/background settings. Reopen the app after force-stop. | Avoid force-stopping the app and review battery optimization after OS updates. |
| Scheduled automatic sends did not recover after restart, app update, or time change | Android recovery did not run, the app was force-stopped and not reopened, the message is review-first, or the send route became blocked after reboot/update/clock change. | Open RelateAI, run AI Doctor, check Messages for Needs review/Failed/Blocked items, and retry one affected message after fixing the blocker. | Do not force-stop the app before scheduled sends. After OS updates, app updates, phone restarts, manual clock changes, or timezone changes, open RelateAI once and confirm Activity History has no setup errors. |
| Event duplicate or conflict | Multiple import sources or manual entries describe the same contact/event differently. | In Events, choose Merge here or Keep separate. | Review imported events after every large contact sync. |
| Generate wish says already generated | A pending draft already exists for that event occurrence. | Open Messages or Contact Detail to review the existing draft instead of creating another. | Use the Messages Inbox as the source of truth for generated drafts. |
| Contact or event not found | Source record was deleted, restored, merged, or no longer active. | Reopen Contacts or Events, refresh data, and generate a new draft if needed. | Avoid deleting contacts/events that still have pending messages. |
| Backup export fails | Blank or weak passphrase, storage destination write failure, or database/export error. | Use a strong passphrase and choose a writable destination. Try again. | Export after setup and verify the success details. |
| Restore fails with wrong passphrase | The entered passphrase does not match the backup. | Enter the original passphrase. There is no recovery if it is lost. | Store backup files and passphrases in a password manager or secure location. |
| Restore rejects the file | File is malformed, too large, checksum mismatch, or created by a newer unsupported app version. | Use a valid RelateAI backup or update the app if the backup is from a newer version. | Keep multiple dated backups and avoid editing backup files manually. |
| Data disappeared after sign-out | Sign-out intentionally clears local app data and secure preferences. | Restore from an encrypted backup if available. | Always export a backup before signing out, uninstalling, or clearing app data. |
| Biometric lock cannot unlock | Device credential or biometric setup is unavailable or changed. | Set up Android screen lock or biometrics, then retry. | Confirm device credentials work before enabling biometric lock. |
| Channel is unexpectedly skipped | The channel is disabled in Settings or contact preferences select another route. | Review Disabled channels and the contact's preferred channel. | Document temporary channel blackouts and remove them when no longer needed. |
| Analytics export fails | FileProvider/cache write issue or Android share target issue. | Retry export and choose another share destination. | Keep storage healthy and export smaller reports periodically. |
| Activity History shows repeated errors | A setup blocker is recurring, such as AI auth, Gmail, WhatsApp, or permission failure. | Open AI Doctor, fix the top recommended blocker, then retry one action. | Treat repeated Activity History errors as setup issues, not one-off failures. |
| Settings change does not seem to apply | The value was not saved, a related Android permission changed outside the app, or the affected workflow has not refreshed readiness yet. | Save the setting, reopen the affected screen, tap Refresh in AI Doctor where available, and retry one action. | Change one setting at a time and verify the related readiness check before relying on automation. |
| Bulk action affects the wrong set of drafts | Filters, tabs, or draft states were misunderstood before approving, rejecting, or retrying. | Stop after the first unexpected result, inspect Activity History, and handle remaining drafts individually. | Use bulk actions only after filtering carefully and reviewing a representative draft. |
| Manual contact cannot be automated | The contact exists locally but lacks a valid phone, email, preferred channel, or event. | Add contact details, choose a supported channel, create an event, then regenerate or retry. | Complete the message readiness checklist before expecting automation. |
| A restored install cannot send immediately | Secrets, OAuth tokens, app passwords, permissions, and device-bound setup are intentionally not restored. | Sign in again, re-enter Gemini/Gmail setup, grant permissions, enable optional WhatsApp Accessibility, then run AI Doctor. | After every restore, treat the device like a fresh setup for external integrations. |

### Current Limitations to Plan Around

| Limitation | Root cause | Workaround | Improvement or preventive measure |
| --- | --- | --- | --- |
| Cross-device sync is not automatic | RelateAI is local-first and has no custom backend in this repository. | Use encrypted export and restore when changing devices. | Export after meaningful changes and before device migration. |
| Restore is replace-only | Merge restore is outside the current product scope. | Export a backup before restoring, then restore only when you accept replacing local data. | Keep dated backups so you can choose the safest restore point. |
| Secrets do not restore | API keys, SMTP passwords, OAuth tokens, and device-bound keys are intentionally excluded. | Re-enter Gemini, Gmail, auth, WhatsApp, and permission setup after restore. | Store credentials in a password manager outside RelateAI. |
| WhatsApp automation may be restricted in Play distribution | AccessibilityService automation is a high-risk policy area. | Use SMS or Email if WhatsApp automation is unavailable in your build. | Treat WhatsApp as optional and validate policy before release. |
| Exact timing can degrade without exact alarm access | Android may delay background work when exact alarms are disabled or battery policies intervene. | Enable exact alarm access where available and keep the app out of aggressive battery restrictions. | Check AI Doctor after OS updates or battery-mode changes. |
| AI quality depends on context | Generic contact data produces generic prompts and generic output. | Add memories, relationship type, language, preferred channel, topics to avoid, and style samples. | Use Contacts filters and AI Doctor personalization checks weekly. |
| External providers can fail independently | Google, Gemini, Gmail, carrier SMS, and WhatsApp can return quota, auth, network, or UI failures. | Retry after fixing the provider-specific issue and test one message before bulk retry. | Review Activity History and keep provider credentials current. |
| Fully Auto cannot override Android or provider controls | Full automation changes app policy, but SMS, exact alarms, Accessibility, Gmail, network, battery, and WhatsApp UI still control whether a send can run. | Fix the blocker shown in AI Doctor or Activity History, then retry one message. | Verify every delivery channel on the actual device before relying on unattended sends. |
| Sign-out is destructive for local data | Sign-out intentionally clears local stores, credentials, scheduled work, and auth state. | Restore from an encrypted backup if one exists. | Always export a backup before sign-out, uninstall, or clearing app data. |

## 17. Example Scenarios

### Family birthday with approval

Goal: Send a warm birthday message to a family member after review.

1. Mark the contact as Family.
2. Set automation mode to VIP Approve or Always Ask.
3. Add memories and topics to avoid.
4. Confirm birthday in Events.
5. Generate the wish.
6. Open Wish Preview.
7. Choose Emotional or Warm style.
8. Edit the draft.
9. Approve and schedule.

### Client work anniversary email

Goal: Send a professional email to a client.

1. Add the client's email address.
2. Set preferred channel to Email.
3. Set formality to Formal and style to Professional.
4. Configure Gmail sender details.
5. Run Test Email in AI Doctor.
6. Add a work anniversary event.
7. Generate and preview the draft.
8. Send a test to yourself.
9. Approve and schedule.

### Friend WhatsApp birthday automation

Goal: Use WhatsApp for an approved birthday wish.

1. Set the friend's preferred channel to WhatsApp.
2. Add a nickname and interests.
3. Enable WhatsApp consent and Accessibility service.
4. Keep the contact on Smart Approve or Always Ask for the first few sends.
5. Review the draft.
6. Approve and schedule.

### Fully automatic low-risk birthday send

Goal: Let RelateAI send eligible low-risk birthday messages without manual approval.

1. Sync contacts.
2. Choose contacts where automatic birthday wishes are acceptable.
3. Add phone numbers or email addresses.
4. Set preferred channels.
5. Configure SMS permission, WhatsApp automation, or Gmail sender setup.
6. Set global Automation Mode to Fully Auto.
7. Reapply Always Ask or Skip automatic wishes for sensitive contacts.
8. Run AI Doctor.
9. Generate or wait for birthday drafts.
10. Confirm queued messages appear as Scheduled or Sent.
11. Review Activity History after the first automatic dispatch.

### Reconnect with a neglected contact

Goal: Use relationship health to prompt a check-in.

1. Open Analytics.
2. Review Top Neglected Contacts.
3. Open the contact.
4. Add a recent memory or context.
5. Generate a warm check-in or follow-up draft if available.
6. Review, edit, and send through the preferred channel.

### Protect data before changing phones

Goal: Move safely to a new device.

1. Open Backup and Restore on the old device.
2. Export encrypted backup with a strong passphrase.
3. Save the file outside the old phone.
4. Install RelateAI on the new device.
5. Restore the backup.
6. Re-enter Gemini, Gmail, account, and channel setup.
7. Run AI Doctor.

## 18. Best Practices

1. If you are new to the app, temporarily use Always Ask until one end-to-end proof run succeeds.
2. Use Smart Approve or Fully Auto only after AI Doctor shows no required blockers for the channel you plan to use.
3. Keep VIP contacts on VIP Approve or Always Ask.
4. Add context before generating drafts for important people.
5. Review Why this draft for every important message.
6. Use test email before sending through Gmail for the first time.
7. Use WhatsApp automation only for contacts and flows you understand.
8. Keep quiet hours realistic.
9. Export backups before risky changes.
10. Use Activity History when something unexpected happens.
11. Fix one AI Doctor recommendation at a time.
12. Prefer manual review for sensitive, emotional, professional, or high-stakes messages.
13. After enabling Fully Auto, reapply manual contact overrides for anyone who should remain protected.

### Maintenance Routines

Use these routines to keep automation reliable after the first setup.

| Frequency | Routine | Why it matters |
| --- | --- | --- |
| Daily or when notified | Review pending messages, failed sends, and the next best Home action. | Keeps approvals and recovery from piling up. |
| Weekly | Open AI Doctor, sync contacts, review generic-message risk, and check Activity History errors. | Finds provider, permission, and quality issues before important events. |
| Monthly | Export an encrypted backup and review low-health or neglected contacts in Analytics. | Protects local data and keeps relationship context current. |
| Before travel, OS updates, or phone changes | Check quiet hours, battery/background settings, exact alarm access, and backup status. | Prevents delayed sends and data loss during environment changes. |
| After restore or sign-in change | Re-enter secrets, grant permissions, run Test AI/Test Email, and test one low-risk send. | External integrations are intentionally not restored from backup. |

## 19. Quick Reference

### Zero-blocker execution runbook

Use this compact runbook when you want one complete workflow without leaving loose ends.

1. Start on Home and open the top recommended setup or review action.
2. If setup is incomplete, open AI Doctor, refresh, and fix the first Required blocker before changing optional settings.
3. Confirm the contact has an event, relationship type, preferred language, preferred channel, phone or email as needed, and any sensitive topics to avoid.
4. Confirm the selected route is actually usable: SMS permission and device path, WhatsApp consent plus Accessibility, or Gmail sender plus Test Email.
5. Generate or open the draft, then inspect Wish Preview for text quality, route, schedule, approval mode, and why signals.
6. Choose the right action: edit, regenerate, approve and schedule, reject, retry, or keep it in review.
7. After dispatch, inspect Activity History for the final provider outcome and any fallback route used.
8. If anything fails, retry one low-risk message after fixing the root cause. Use bulk actions only after the single-message retry succeeds.
9. Export an encrypted backup after meaningful contact/event/message changes or before sign-out, uninstall, restore, OS updates, or device migration.
10. For full automation, repeat the successful single-message path once per channel you plan to use, then keep AI Doctor and Activity History clean.

### Setup checklist

1. Finish onboarding.
2. Sign in with Google.
3. Sync contacts.
4. Review Events.
5. Add Gemini access.
6. Configure SMS, WhatsApp, or Email.
7. Set automation mode and protect any review-first contacts.
8. Set quiet hours.
9. Train Style Coach.
10. Create encrypted backup.
11. Run AI Doctor.

### Message readiness checklist

1. Contact exists.
2. Event exists.
3. Draft is not blank.
4. Draft is long enough.
5. Preferred channel is valid.
6. Required phone or email exists.
7. Permission is granted.
8. Sender setup is complete.
9. Quiet hours and blackout rules allow the send.
10. Approval mode permits dispatch.

### Full automation readiness checklist

1. Global Automation Mode is Fully Auto.
2. Contacts that should remain manual are set to Always Ask, VIP Approve, or Skip automatic wishes.
3. Every automated contact has a birthday/event and route details.
4. Disabled channels match your current intent.
5. SMS permission is granted if SMS can be used.
6. WhatsApp consent and Accessibility are enabled if WhatsApp can be used.
7. Gmail sender email and app password are saved if Email can be used.
8. Exact alarm access is enabled where available.
9. AI Doctor has no required blocker for the selected channel.
10. A low-risk real send has completed and appears in Activity History.
11. After a device restart or OS update, RelateAI has been opened once and AI Doctor still shows the selected channel as ready.

### Personalization checklist

1. Nickname.
2. Relationship type.
3. Preferred language.
4. Preferred channel.
5. Formality and style.
6. Interests.
7. Memory notes.
8. Gift records.
9. Sensitive topics to avoid.
10. Writing samples in Style Coach.

## 20. When in Doubt

Use this order:

1. Open AI Doctor.
2. Fix the top recommended blocker.
3. Open Activity History if the issue repeated.
4. Open the affected contact and check personalization plus channel setup.
5. Open Messages and review the affected draft state.
6. Retry one action before using bulk actions.
7. Create a backup before destructive changes.
