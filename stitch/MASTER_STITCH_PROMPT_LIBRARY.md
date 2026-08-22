# WishWell — Master Stitch Prompt Library

> **Authority:** `PROJECT_ABOUT.md` is the sole product and technical source of truth.
> This file is a structured prompt library for Google Stitch screen generation.

## Purpose

This document is a comprehensive master prompt library covering **all 63 screens** of the WishWell application. Each screen entry provides:

1. **Screen Description** — Purpose, design intent, key UI elements, and interactions
2. **User Flow Context** — Entry points, available actions, and exit points
3. **Business Requirements** — KPIs, compliance constraints, and BA-## requirement codes
4. **Technical Requirements** — Components, APIs, data models, validation, and native modules
5. **Final Stitch Prompt** — A single actionable instruction for Google Stitch screen generation

## Table of Contents

- [Design System Reference](#design-system-reference)
- [Section 1: Global & Setup (G01-G02, S01-S21)](#section-1-global--setup-25-screens)
- [Section 2: Home (H01-H06)](#section-2-home-6-screens)
- [Section 3: People (P01-P04)](#section-3-people-4-screens)
- [Section 4: Activity & Diagnostics (A01-A06)](#section-4-activity--diagnostics-6-screens)
- [Section 5: Settings (T01-T10)](#section-5-settings-10-screens)
- [Section 6: Account, Transfer & Lifecycle (L01-L11)](#section-6-account-transfer--lifecycle-11-screens)
- [Section 7: Hosted Deletion Resource (W01-W03)](#section-7-hosted-deletion-resource-3-screens)
- [Screen-to-Component Cross-Reference](#screen-to-component-cross-reference)
- [BA Requirements Quick Reference](#ba-requirements-quick-reference)

## Design System Reference

| Token | Value |
|-------|-------|
| Font | Inter |
| Light Background | `#F7F7FC` |
| Dark Background | `#11121A` |
| Accent | `#4B52A3` |
| Positive | `#256A45` |
| Warning | `#8A4F08` |
| Critical | `#A53535` |
| Surface | White / Dark surface |
| Corner Radii | 8px – 20px |
| Touch Targets | 48dp minimum |
| Framework | Material 3 (Android), iOS native navigation |
| Tabs | Home, People, Settings (3 permanent) |
| Avatars | Initials only (no contact photos) |
| Phone Display | Masked in lists; full in approval/test contexts |
| Date Format | `Tue, 14 July` (plain civil dates) |
| Status Pattern | Icon + text always (never color-only) |
| Accessibility | TalkBack, VoiceOver, 200% text, high contrast, reduced motion, switch/keyboard, bidi-safe |
| Locales | English, Hindi (layout fixture), pseudo-RTL for testing |

## KPIs & Success Criteria

| Metric | Target |
|--------|--------|
| Setup completion | ≥ 90% in < 5 minutes |
| Automation reliability | ≥ 95% within delivery window |
| Cold start to interactive | < 2.5s |
| 10,000-contact sync | < 5s |
| SMS claim/arm latency (P95) | < 2.5s |
| Duplicate/unintended SMS | Zero tolerance |
| Unapproved recipients | Zero tolerance |
| Background iOS SMS | Zero (forbidden) |

---

# Section 1: Global & Setup (25 Screens)

---

## G01 — Secure startup

### Screen Description
- **Purpose & Design Intent:** Acts as the initial gatekeeper for the app, verifying device eligibility and data health before allowing entry. It ensures critical safety policies, like verifying the safety ledger or handling companion plans on iOS, are intact.
- **Key UI Elements:** Loading indicator, Status messaging (e.g. 'Checking device...', 'Restoring setup'), Error/Recovery actions (e.g. 'Retry', 'Contact Support')
- **Key Interactions:** Automatic progression on success, Manual retry on recoverable failures, Support deep-links for unrecoverable errors

### User Flow Context
- **Entry Points:** App launch (cold or warm)
- **Available Actions:** Wait for checks, Retry failed checks
- **Exit Points:** G02 Main shell (success), S01 Welcome (first launch), App exit (unrecoverable)

### Business Requirements
- **Objectives:** Cold start < 2.5s, Ensure 100% compliance with safety ledgers
- **Constraints:** Must render immediately without waiting for React Native if possible (Splash), Accessible loading states
- **BA Requirements:** BA-01, BA-18

### Technical Requirements
- **Components:** NativeAppBoundary.tsx, LiveSetupScreen.tsx, LiveProjectionState.tsx
- **APIs & Data:** Firestore (server-only safety ledger), Keychain/Data Protection (iOS), Room DB (Android)
- **Validation & Error Handling:** Check missing/corrupt ledger, Recoverable vs unrecoverable failure handling
- **Native Modules:** BirthdayNative (getProjection)

### Final Stitch Prompt
> Build G01 Secure startup to verify device eligibility and safety ledgers. Show a loading state via NativeAppBoundary.tsx and transition to G02 on success or S01 on first launch. Handle Android/iOS specific ledgers and recoverable startup failures with clear accessibility and 200% text scaling.

---

## G02 — Main shell

### Screen Description
- **Purpose & Design Intent:** Provides the core navigation framework for the app with Home, People, and Settings tabs. It adapts its title bar and tab treatments dynamically based on the platform.
- **Key UI Elements:** Bottom navigation bar (Home, People, Settings), Platform-adaptive title bar, Action-needed indicator badges
- **Key Interactions:** Tab switching, Deep-link routing, Offline status banners

### User Flow Context
- **Entry Points:** G01 Secure startup
- **Available Actions:** Navigate between tabs, View offline or active automation status
- **Exit Points:** Home Tab, People Tab, Settings Tab, Deep-linked screen

### Business Requirements
- **Objectives:** Provide seamless navigation, Clear indication of automation/companion status
- **Constraints:** 48dp touch targets for tabs, Support 200% text/Dynamic Type, Hindi localization and pseudo-RTL
- **BA Requirements:** BA-15, BA-16, BA-19

### Technical Requirements
- **Components:** AppRoot.tsx, LiveAppShell.tsx
- **APIs & Data:** LiveNavigationContext, useLiveProjection
- **Validation & Error Handling:** Check offline status, Deep-link resolution
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build G02 Main shell using LiveAppShell.tsx with bottom tabs for Home, People, and Settings. Implement platform-adaptive title bars, offline status banners, and action-needed badges. Ensure strict accessibility compliance including 48dp targets, TalkBack/VoiceOver, and pseudo-RTL support.

---

## S01 — Welcome and compatibility

### Screen Description
- **Purpose & Design Intent:** Introduces the user to the app and performs initial capability checks tailored to the platform. It manages expectation setting by explicitly listing limitations like 'No automatic sending on iPhone'.
- **Key UI Elements:** Welcome graphic/branding, Capability checklist (Checking, Supported, Limited, Unsupported), Continue button
- **Key Interactions:** Review compatibility status, Acknowledge limitations on iOS

### User Flow Context
- **Entry Points:** G01 (First launch)
- **Available Actions:** Proceed to connect account, Exit if unsupported
- **Exit Points:** S02 Connect with Google

### Business Requirements
- **Objectives:** >= 90% setup in < 5min, Transparent limitation disclosure
- **Constraints:** Must clarify iOS MessageUI and notification limitations clearly
- **BA Requirements:** BA-01

### Technical Requirements
- **Components:** LiveSetupScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** BirthdayNative (capability check)
- **Validation & Error Handling:** Telephony, SIM, Play-services (Android), MessageUI, notifications, Google/Firebase availability (iOS)
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S01 Welcome and compatibility to verify and display device capabilities. Show checking/supported/limited states via LiveSetupScreen.tsx, strictly differentiating Android telephony/SIM requirements from iOS companion limits. Highlight 'No automatic sending on iPhone' before allowing the user to proceed.

---

## S02 — Connect with Google

### Screen Description
- **Purpose & Design Intent:** Handles the authentication flow using Google Sign-In. It relies on platform-native credentials managers and properly handles re-authentication or removed account scenarios.
- **Key UI Elements:** 'Sign in with Google' button, Account status indicator (offline/signed-out), Error message area (Workspace block, cancelled)
- **Key Interactions:** Tap to invoke native sign-in, Handle chooser cancellation

### User Flow Context
- **Entry Points:** S01 Welcome
- **Available Actions:** Sign in, Cancel sign in
- **Exit Points:** S03 Active sender gate

### Business Requirements
- **Objectives:** Secure user authentication, Prevent Workspace accounts
- **Constraints:** Use Android Credential Manager or iOS native Google sign-in, Handle offline and Firebase-disabled states gracefully
- **BA Requirements:** BA-02

### Technical Requirements
- **Components:** LiveSetupScreen.tsx
- **APIs & Data:** Firebase Auth
- **Validation & Error Handling:** Block Workspace accounts (403), Handle reauth and account-removed states
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S02 Connect with Google to authenticate users via platform-native Google Sign-In in LiveSetupScreen.tsx. Handle signed-out, offline, reauth, and Workspace block states gracefully using Firebase Auth. Ensure the UI clearly guides users through credential manager cancellation or errors.

---

## S03 — Active sender gate

### Screen Description
- **Purpose & Design Intent:** Determines which device is the active sender to prevent duplicate automated messages. It manages the cloud fencing policy and cooperative transfers between devices.
- **Key UI Elements:** Active device status, Transfer request button (Android), Read-only warning (iOS)
- **Key Interactions:** Request transfer of active sender role, View current active device

### User Flow Context
- **Entry Points:** S02 Connect with Google
- **Available Actions:** Transfer active state (Android), Proceed as companion (iOS)
- **Exit Points:** S04 Contacts disclosure

### Business Requirements
- **Objectives:** Zero duplicate SMS, Strict cloud fencing enforcement
- **Constraints:** iOS must be read-only coexistence, Cannot proceed if server state unknown
- **BA Requirements:** BA-13

### Technical Requirements
- **Components:** LiveSetupScreen.tsx
- **APIs & Data:** Cloud Functions (epoch transfers), Firestore
- **Validation & Error Handling:** Verify active phone binding, Handle transfer pending and server state unknown errors
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S03 Active sender gate to enforce cloud fencing and prevent duplicate SMS. Implement cooperative transfer flows on Android and read-only coexistence on iOS via LiveSetupScreen.tsx. Depend on Cloud Functions for epoch transfers and clearly display lost-phone replacement or standby states.

---

## S04 — Contacts disclosure

### Screen Description
- **Purpose & Design Intent:** Presents the privacy disclosure and requests explicit consent for read-only Google Contacts access. It handles re-prompting if the disclosure version changes.
- **Key UI Elements:** Privacy disclosure text, Grant Access button, Deny/Cancel button
- **Key Interactions:** Accept disclosure, Decline disclosure

### User Flow Context
- **Entry Points:** S03 Active sender gate
- **Available Actions:** Grant consent, Cancel
- **Exit Points:** S05 Contacts authorization return, Exit setup

### Business Requirements
- **Objectives:** Obtain informed consent, Compliance with privacy standards
- **Constraints:** Must show disclosure before system prompt, Track disclosure version
- **BA Requirements:** BA-03

### Technical Requirements
- **Components:** LiveSetupScreen.tsx
- **APIs & Data:** Google People API (OAuth scope)
- **Validation & Error Handling:** Check previously granted state, Detect disclosure version changes
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S04 Contacts disclosure to present privacy terms for Google Contacts read-only access. Provide clear 'Grant' and 'Cancel' actions in LiveSetupScreen.tsx, adapting to first consent, previously granted, or version-changed states. Ensure 48dp targets and high contrast for accessibility.

---

## S05 — Contacts authorization return

### Screen Description
- **Purpose & Design Intent:** Processes the result of the native contacts authorization request. It translates system-level OAuth responses into actionable UI states like partial grants or Workspace blocks.
- **Key UI Elements:** Loading indicator (Checking), Result summary (Granted, Denied, Revoked), Retry or fallback actions
- **Key Interactions:** Auto-proceed on exact scope granted, Manual action on partial grant or 401 reconnect

### User Flow Context
- **Entry Points:** S04 Contacts disclosure
- **Available Actions:** Retry authorization, Acknowledge partial grant
- **Exit Points:** S06 First Contacts sync

### Business Requirements
- **Objectives:** Seamless handling of OAuth callbacks, Clear error resolution
- **Constraints:** Do not expose raw error tokens to users, Handle 403 for Workspace securely
- **BA Requirements:** BA-03

### Technical Requirements
- **Components:** LiveSetupScreen.tsx
- **APIs & Data:** Google People API
- **Validation & Error Handling:** Validate exact scope vs partial grant, Handle bounded 401 reconnect and Workspace 403
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S05 Contacts authorization return to process platform-native OAuth results. Handle Checking, exact scope granted, partial grant, denied, and Workspace 403 states in LiveSetupScreen.tsx. Never show raw error tokens; provide actionable reconnect or retry UI instead.

---

## S06 — First Contacts sync

### Screen Description
- **Purpose & Design Intent:** Displays the progress and outcome of the initial contacts synchronization. It provides a summary of usable birthdays and alerts the user to missing data or network issues.
- **Key UI Elements:** Sync progress bar/spinner, Summary card (Ready, Needs attention, Unavailable), Empty state graphic
- **Key Interactions:** Wait for sync, Retry on offline/429 errors

### User Flow Context
- **Entry Points:** S05 Contacts authorization return
- **Available Actions:** Proceed to choose people, Retry sync
- **Exit Points:** S07 Choose people

### Business Requirements
- **Objectives:** Clear visibility into sync status, Fast perception of data ingestion
- **Constraints:** Handle 429 rate limits, Retain verified data on partial page failures
- **BA Requirements:** BA-03, BA-04

### Technical Requirements
- **Components:** LivePeopleScreen.tsx, LiveSetupScreen.tsx
- **APIs & Data:** Google People API (contacts.readonly)
- **Validation & Error Handling:** Normalize contact/birthday/phone data, Handle no usable birthdays or offline states
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S06 First Contacts sync to visualize data ingestion from Google People API. Display Loading, Ready, Needs attention, and Unavailable summaries in LivePeopleScreen.tsx. Handle 429 errors, expired tokens, and partial page loads gracefully while normalizing birthday data.

---

## S07 — Choose people

### Screen Description
- **Purpose & Design Intent:** Allows the user to explicitly enroll contacts into the birthday automation. It supports bulk selection and handles edge cases like duplicate destinations or stale sources.
- **Key UI Elements:** List of synced contacts, Select all / Deselect all toggles, Search/Filter bar, Continue button with selected count
- **Key Interactions:** Toggle individual selection, Bulk select 'ready' contacts, Search by name

### User Flow Context
- **Entry Points:** S06 First Contacts sync, G02 Main shell (People tab)
- **Available Actions:** Select contacts, Proceed to review
- **Exit Points:** S08 Bulk recipient review, S09 Repair person (if needs attention)

### Business Requirements
- **Objectives:** Easy selection of multiple recipients, Clear indication of ready vs unavailable contacts
- **Constraints:** Handle large lists efficiently (virtualization), Disable selection for sync running or all unavailable
- **BA Requirements:** BA-05

### Technical Requirements
- **Components:** LivePeopleScreen.tsx, LiveSetupScreen.tsx
- **APIs & Data:** Room DB (Android), Keychain (iOS)
- **Validation & Error Handling:** Prevent duplicate destinations, Handle deleted/stale source references
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S07 Choose people to allow explicit recipient enrollment via LivePeopleScreen.tsx. Implement virtualized lists for large contact sets, support individual and 'Select all ready' actions, and handle edge cases like duplicate destinations, stale sources, and 'all unavailable' states.

---

## S08 — Bulk recipient review

### Screen Description
- **Purpose & Design Intent:** Provides a final confirmation step for multiple selected recipients. It flags any conflicts or source changes that occurred since selection.
- **Key UI Elements:** Reviewable count header, List of selected recipients, Conflict warnings (if any), Confirm/Approve button
- **Key Interactions:** Review list, Resolve conflicts, Confirm selection

### User Flow Context
- **Entry Points:** S07 Choose people
- **Available Actions:** Confirm approvals, Cancel confirmation
- **Exit Points:** S10 Approve person (batch mode), S07 Choose people (on cancel)

### Business Requirements
- **Objectives:** Zero unapproved recipients, Ensure user intent before batch approval
- **Constraints:** Must handle zero selected edge case, Invalidate approvals if source changes mid-review
- **BA Requirements:** BA-05, BA-09

### Technical Requirements
- **Components:** LivePeopleScreen.tsx, LiveSetupScreen.tsx
- **APIs & Data:** Room DB, Keychain
- **Validation & Error Handling:** Check for source changed or approval invalidated, Validate reviewable count
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S08 Bulk recipient review to summarize and confirm explicit enrollment. Display reviewable counts and conflict warnings in LivePeopleScreen.tsx. Safely handle source changes, approval invalidation, and zero-selected edge cases before persisting immutable approval snapshots.

---

## S09 — Repair person

### Screen Description
- **Purpose & Design Intent:** Guides the user to fix incomplete or invalid contact data, such as missing names, ambiguous regions, or leap year birthdays.
- **Key UI Elements:** Issue description card, Data entry/correction fields (e.g., phone picker, date picker), Save/Fix button
- **Key Interactions:** Select correct phone number, Confirm birthday logic (Feb 29)

### User Flow Context
- **Entry Points:** S07 Choose people (Needs attention), S15 Recipient review
- **Available Actions:** Provide missing data, Dismiss/Delete contact
- **Exit Points:** S10 Approve person, S07 Choose people (on abort)

### Business Requirements
- **Objectives:** Convert 'Needs attention' contacts to 'Ready', Ensure data freshness and validity
- **Constraints:** Must enforce February 29 policy, Handle blocked numbers and extensions
- **BA Requirements:** BA-04, BA-16

### Technical Requirements
- **Components:** LivePersonDetailScreen.tsx
- **APIs & Data:** libphonenumber
- **Validation & Error Handling:** Validate multiple phones, unsafe given name, duplicate occurrences, Check against contacts-freshness-policy-v1
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S09 Repair person to resolve invalid contact data using LivePersonDetailScreen.tsx. Implement UI for fixing multiple phones, ambiguous regions, unsafe names, and Feb 29 policy rules. Validate inputs strictly against contacts-freshness-policy-v1 using libphonenumber.

---

## S10 — Approve person

### Screen Description
- **Purpose & Design Intent:** Finalizes the recipient's automation setup by creating an immutable approval snapshot on Android or configuring a companion proposal on iOS.
- **Key UI Elements:** Recipient summary, Approval details (phone, birthday, template), Approve/Confirm button
- **Key Interactions:** Review immutable payload, Acknowledge same-day reset suppression

### User Flow Context
- **Entry Points:** S08 Bulk recipient review, S09 Repair person
- **Available Actions:** Approve automation, Edit details
- **Exit Points:** S15 Recipient and message review

### Business Requirements
- **Objectives:** Zero unapproved recipients, Create strict immutable approval snapshot
- **Constraints:** Differentiate Android immutable approval from iOS companion proposal, Enforce Android-managed suppression on iOS
- **BA Requirements:** BA-09

### Technical Requirements
- **Components:** LivePersonDetailScreen.tsx
- **APIs & Data:** Room DB, Keychain
- **Validation & Error Handling:** Check existing Ready, placeholder, segment states, Validate same-day reset suppression (iOS)
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S10 Approve person to finalize recipient enrollment in LivePersonDetailScreen.tsx. On Android, visually confirm the immutable approval snapshot (phone/birthday/template/SIM). On iOS, present the companion proposal, emphasizing editable final recipient lines and same-day reset suppression.

---

## S11 — Template editor

### Screen Description
- **Purpose & Design Intent:** Provides an interface for crafting personalized or generic birthday messages. It supports English/Hindi and validates message length and placeholder usage.
- **Key UI Elements:** Text area with character count, Placeholder insertion buttons, Personalized/Generic toggle, Save/Preview buttons
- **Key Interactions:** Type message, Insert placeholders, Switch languages

### User Flow Context
- **Entry Points:** S10 Approve person, S15 Recipient review
- **Available Actions:** Save template, Invoke Gemini (S12)
- **Exit Points:** S15 Recipient and message review, S12 Gemini suggestions

### Business Requirements
- **Objectives:** Enable highly personalized messages, Ensure semantic safety of templates
- **Constraints:** Reject URLs/promotions, Handle bidirectional (bidi) text correctly for Hindi
- **BA Requirements:** BA-07

### Technical Requirements
- **Components:** LiveMessageScreen.tsx
- **APIs & Data:** birthday-message-semantic-policy-v2.json
- **Validation & Error Handling:** Validate invalid placeholder, too long, URL/promotion rejected, Handle unsaved changes and offline state
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S11 Template editor for crafting built-in or custom messages via LiveMessageScreen.tsx. Support English/Hindi with correct bidi handling, personalized/generic modes, and placeholder validation. Strictly enforce birthday-message-semantic-policy-v2 to reject URLs and excessively long texts.

---

## S12 — Gemini suggestions

### Screen Description
- **Purpose & Design Intent:** Integrates Vertex AI to generate context-aware birthday message suggestions based on chosen tone and relationship.
- **Key UI Elements:** Tone selector (e.g. funny, heartfelt), Generating spinner, List of candidate messages, Refresh/Fallback buttons
- **Key Interactions:** Select tone, Tap candidate to use/edit, Retry generation

### User Flow Context
- **Entry Points:** S11 Template editor
- **Available Actions:** Apply suggestion, Cancel
- **Exit Points:** S11 Template editor (with text applied)

### Business Requirements
- **Objectives:** Provide delightful AI assistance, Maintain safety via App Check and prompt policy
- **Constraints:** Must provide built-in fallback on failure/offline, Enforce strict safety blocks and quota limits
- **BA Requirements:** BA-08

### Technical Requirements
- **Components:** LiveMessageScreen.tsx
- **APIs & Data:** Vertex AI (gemini-3.5-flash), Firebase App Check
- **Validation & Error Handling:** Handle offline, timeout, quota, safety block, malformed output, model unavailable, Auth failure
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S12 Gemini suggestions to offer AI-generated messages in LiveMessageScreen.tsx. Implement tone choices and candidate selection while gracefully handling offline, timeout, quota, safety blocks, and malformed output. Enforce gemini-prompt-policy-v2 and seamlessly route to built-in fallbacks on Auth/App Check failure.

---

## S13 — Delivery window

### Screen Description
- **Purpose & Design Intent:** Configures when the automated message should be sent. It manages Android's strict send windows and iOS's local notification scheduling horizons.
- **Key UI Elements:** Time picker / Window selector, Timezone/Region indicator, Save/Apply button
- **Key Interactions:** Select preferred delivery window, Review civil-date coalescing warnings

### User Flow Context
- **Entry Points:** G02 Settings, S10 Approve person
- **Available Actions:** Update delivery policy
- **Exit Points:** G02 Settings, S15 Recipient review

### Business Requirements
- **Objectives:** Ensure messages are sent at appropriate times, Handle timezone shifts robustly
- **Constraints:** Android: 400-day cap and grace period states, iOS: Bounded scheduled horizon limit (system limit)
- **BA Requirements:** BA-10

### Technical Requirements
- **Components:** LivePolicyEditor.tsx, LiveAutomationScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** Room DB / Keychain
- **Validation & Error Handling:** Handle timezone change, unsaved change, exhausted horizon (iOS)
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S13 Delivery window to configure send timing via LivePolicyEditor.tsx and LiveAutomationScreen.tsx. On Android, expose send window, grace, cap, and 400-day states. On iOS, design for reminder preferences, civil-date coalescing, and exhausted scheduled horizons beyond current system limits.

---

## S14 — SIM policy

### Screen Description
- **Purpose & Design Intent:** Allows Android users to select the default SIM for sending automated SMS. iOS displays this as a noninteractive annotation.
- **Key UI Elements:** SIM selector (Android), Active SIM annotation (iOS)
- **Key Interactions:** Select valid default SIM (Android only)

### User Flow Context
- **Entry Points:** G02 Settings
- **Available Actions:** Change default SIM
- **Exit Points:** G02 Settings

### Business Requirements
- **Objectives:** Route SMS through correct carrier, Prevent silent failures due to invalid SIM
- **Constraints:** Interactive flow is Android-only, Must handle roaming and deactivated states
- **BA Requirements:** BA-12

### Technical Requirements
- **Components:** LivePolicyEditor.tsx, LiveAutomationScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** TelephonyManager (Android)
- **Validation & Error Handling:** Valid default, no default, dual SIM, changed/removed/deactivated SIM, invalid subscription, roaming
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S14 SIM policy for Android interactive SIM selection and iOS noninteractive annotation using LivePolicyEditor.tsx and LiveAndroidDeviceControls.tsx. Handle dual SIM, valid/no default, removed/deactivated SIM, roaming, and gated explicit-SIM variant states safely for reliable SMS routing.

---

## S15 — Recipient and message review

### Screen Description
- **Purpose & Design Intent:** Provides a comprehensive preview of the finalized automated action for a specific recipient, showing exact payload details or iOS reminder configurations.
- **Key UI Elements:** Exact preview card, Cost/Parts estimation, Edit buttons for recipient/message
- **Key Interactions:** Review payload, Tap to edit details

### User Flow Context
- **Entry Points:** S10 Approve person, S11 Template editor, G02 People tab
- **Available Actions:** Proceed to test, Edit template/recipient
- **Exit Points:** S16 Test destination (Android), G02 Main shell

### Business Requirements
- **Objectives:** Build trust through exact preview, Highlight SMS parts/cost
- **Constraints:** Android shows immutable payload, iOS emphasizes MessageUI editability and final-payload invisibility
- **BA Requirements:** BA-09, BA-12

### Technical Requirements
- **Components:** LivePersonDetailScreen.tsx, LiveMessageScreen.tsx
- **APIs & Data:** Room DB, Keychain
- **Validation & Error Handling:** Handle empty, invalidation, duplicate, capacity states
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S15 Recipient and message review to display exact previews in LivePersonDetailScreen.tsx. On Android, render the immutable payload, SIM, parts, and cost. On iOS, show the proposed recipient and reminder cost, explicitly highlighting MessageUI editability and final-payload invisibility.

---

## S16 — Test destination

### Screen Description
- **Purpose & Design Intent:** An Android-only flow that allows users to send a test SMS to themselves or a confirmed number to verify the automation pipeline.
- **Key UI Elements:** Destination selector (Own number / Custom), Send Test button
- **Key Interactions:** Select or input test number, Initiate test SMS

### User Flow Context
- **Entry Points:** S15 Recipient and message review
- **Available Actions:** Send test message
- **Exit Points:** S17 Test review and SMS disclosure

### Business Requirements
- **Objectives:** Verify SMS delivery capability, Enforce test quotas (3 per 24h)
- **Constraints:** No iOS TEST route, Handle no SIM or blocked destinations
- **BA Requirements:** BA-14

### Technical Requirements
- **Components:** LiveAutomationScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** TelephonyManager
- **Validation & Error Handling:** Check invalid/blocked/ambiguous destination, no SIM, three-per-24-hour budget reached
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S16 Test destination (Android-only) to configure test SMS routing in LiveAutomationScreen.tsx. Implement selection for 'own number' or 'another confirmed number'. Validate strictly against no SIM, ambiguous destinations, and the three-per-24-hour budget limit.

---

## S17 — Test review and SMS disclosure

### Screen Description
- **Purpose & Design Intent:** Displays the pre-permission disclosure for sending SMS (Android) or the reminder and composer disclosure (iOS) before activating automation.
- **Key UI Elements:** Disclosure text (SMS or Reminder), Grant Permission/Proceed button
- **Key Interactions:** Review disclosure, Grant SEND_SMS (Android)

### User Flow Context
- **Entry Points:** S16 Test destination (Android), S15 Recipient review (iOS)
- **Available Actions:** Approve disclosure and permission
- **Exit Points:** S18 Test status

### Business Requirements
- **Objectives:** Obtain SEND_SMS permission securely (Android), Set clear iOS delivery expectations
- **Constraints:** Android: Exact text/SIM/parts/cost summary, iOS: Emphasize manual send requirements and possible charges
- **BA Requirements:** BA-14, BA-12

### Technical Requirements
- **Components:** LiveAutomationScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** Android Permissions (SEND_SMS), iOS Notification Permissions
- **Validation & Error Handling:** Check pre-permission, SEND_SMS, restrictions (Android), Notification permission context (iOS)
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S17 Test review and SMS disclosure via LiveAutomationScreen.tsx. On Android, present the pre-permission SEND_SMS disclosure alongside exact text/SIM/cost. On iOS, present the Reminder/composer disclosure, emphasizing foreground review, MessageUI editability, system-controlled transport, and no automatic delivery proof.

---

## S18 — Test status

### Screen Description
- **Purpose & Design Intent:** Monitors and displays the outcome of the test SMS on Android, or the status of reminder setup on iOS.
- **Key UI Elements:** Status spinner/indicator, Result message (Passed, Failed, Partial), Retry/Continue button
- **Key Interactions:** Wait for test result, Proceed on success

### User Flow Context
- **Entry Points:** S17 Test review and SMS disclosure
- **Available Actions:** Acknowledge result, Retry test
- **Exit Points:** S19 Background readiness

### Business Requirements
- **Objectives:** Confirm reliable end-to-end delivery capability, Provide clear diagnostic feedback
- **Constraints:** Android: Track TestReceipt and coordination, iOS: No TestReceipt, verify reminders ready only
- **BA Requirements:** BA-14

### Technical Requirements
- **Components:** LiveAutomationScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** Cloud Functions (SMS claims), UNUserNotificationCenter (iOS)
- **Validation & Error Handling:** Android: safety/coordination/submission/Passed/Failed/Unknown/Partial/expiry/cleanup/budget, iOS: checking capability, ready, denied, bounded horizon partial
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S18 Test status in LiveAutomationScreen.tsx. For Android, visualize the complex lifecycle: safety, coordination, submission, Passed/Failed/Partial, and expiry/cleanup. For iOS, provide reminder setup status only (checking capability, permission denied, bounded horizon), explicitly omitting any SMS TestReceipt claims.

---

## S19 — Background readiness

### Screen Description
- **Purpose & Design Intent:** Verifies that the device is configured correctly to run background tasks (Android) or schedule local notifications (iOS) reliably.
- **Key UI Elements:** Checklist of OS settings (Battery optimization, Data saver, etc.), Action buttons to open OS settings
- **Key Interactions:** Resolve battery/data saver restrictions, Open system settings

### User Flow Context
- **Entry Points:** S18 Test status
- **Available Actions:** Resolve OS restrictions, Continue when clear
- **Exit Points:** S20 Final activation review

### Business Requirements
- **Objectives:** Ensure 100% background execution reliability, Prevent OEM kills
- **Constraints:** Must handle Doze, hibernation, Low Power Standby on Android, Verify protected-store health on iOS
- **BA Requirements:** BA-11, BA-01

### Technical Requirements
- **Components:** LiveAutomationScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** BirthdayNative (OS Settings intents)
- **Validation & Error Handling:** Detect OEM specific restrictions (Android), Detect notification authorization and bounded horizon limits (iOS)
- **Native Modules:** BirthdayNative (executeUserIntent)

### Final Stitch Prompt
> Build S19 Background readiness to diagnose OS-level restrictions in LiveAutomationScreen.tsx. On Android, handle every background/Doze/hibernation/OEM state with actionable intents via executeUserIntent. On iOS, verify notification authorization, protected-store health, and bounded horizon states without claiming exact-timing execution.

---

## S20 — Final activation review

### Screen Description
- **Purpose & Design Intent:** The definitive final step before committing the automation configuration. It summarizes all permissions, capabilities, and settings.
- **Key UI Elements:** Comprehensive summary card, Final 'Turn On' / 'Activate' button, Review Later option (iOS)
- **Key Interactions:** Review all configurations, Commit activation

### User Flow Context
- **Entry Points:** S19 Background readiness
- **Available Actions:** Activate automation, Review later (iOS)
- **Exit Points:** S21 Activation result

### Business Requirements
- **Objectives:** Ensure complete user understanding before activation, Final confirmation of all BA requirements
- **Constraints:** No TEST or automation language on iOS (use reminder/proposal language)
- **BA Requirements:** BA-14

### Technical Requirements
- **Components:** LiveAutomationScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** Room DB, Keychain
- **Validation & Error Handling:** Verify TestReceipt/background/permission/SIM (Android), Verify notification, horizon, Contacts, MessageUI blockers (iOS)
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S20 Final activation review as the definitive commit gate in LiveAutomationScreen.tsx. On Android, verify TestReceipt, background, permission, and SIM state. On iOS, present a final reminder/privacy review checking Contacts, MessageUI, and horizon blockers, including an explicit 'Review message later' option.

---

## S21 — Activation result

### Screen Description
- **Purpose & Design Intent:** Displays the final success or failure state of the automation commitment, providing closure to the setup flow.
- **Key UI Elements:** Success animation/graphic, Status summary, Done/Return button
- **Key Interactions:** Acknowledge success, Return to repair on failure

### User Flow Context
- **Entry Points:** S20 Final activation review
- **Available Actions:** Finish setup, Fix commitment errors
- **Exit Points:** G02 Main shell (Home tab)

### Business Requirements
- **Objectives:** Provide clear closure, Handle edge case commit failures gracefully
- **Constraints:** Highlight in-app planning mode if notifications denied on iOS
- **BA Requirements:** BA-14, BA-11

### Technical Requirements
- **Components:** LiveAutomationScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** Room DB, Keychain
- **Validation & Error Handling:** Activated, not committed, state changed, return to repair
- **Native Modules:** BirthdayNative

### Final Stitch Prompt
> Build S21 Activation result to display the setup commitment outcome in LiveAutomationScreen.tsx. Handle success (Activated/Reminders on) and failure (not committed, state changed, return to repair). On iOS, clearly indicate if bounded horizons are partially scheduled or if in-app planning is on due to denied notifications.

---
 
# Section 2: Home (6 Screens) 
 
---

## H01 — Home

### Screen Description
- **Purpose & Design Intent:** Serves as the central dashboard for the WishWell application. It provides an immediate overview of the system's operational status, upcoming birthday actions, and any urgent attention required by the user.
- **Key UI Elements:** 
  - Status banner (Automation on/Paused/Needs your attention)
  - Upcoming events summary widget
  - Platform-specific status indicators (Android worker/SIM/power vs iOS reminders/notification visibility/horizon)
- **Key Interactions:** 
  - Tap on status to view details or resolve issues
  - Tap on upcoming events to navigate to the Upcoming screen

### User Flow Context
- **Entry Points:** Default landing screen on app launch.
- **Available Actions:** View status, navigate to upcoming birthdays, address attention flags.
- **Exit Points:** Upcoming (H02), Resume readiness (H05), Today decision (H06).

### Business Requirements
- **Objectives:** Ensure users immediately understand their automation status and trust the system's reliability (>= 95% automation reliability).
- **Constraints:** Inter font, Light background #F7F7FC, Dark #11121A, Accent #4B52A3. 48dp touch targets, Material 3 on Android.
- **BA Requirements:** BA-15 (Home readiness/upcoming view)

### Technical Requirements
- **Components:** LiveHomeScreen.tsx
- **APIs & Data:** useLiveProjection hook, AppProjectionPort, src/domain/home/model.ts
- **Validation & Error Handling:** Handle numerous states including empty/stale/offline/deletion. Android: worker/SIM/power/channel/clock/reset errors. iOS: notification visibility limited, bounded horizon issues, companion ledger reset/corrupt, composer outcome Unknown.
- **Native Modules:** BirthdayNative (getProjection)

### Final Stitch Prompt
> Generate a React Native Home screen (LiveHomeScreen.tsx) functioning as the main dashboard using Inter font, Light #F7F7FC/Dark #11121A/Accent #4B52A3 theme, and 48dp touch targets. It must consume useLiveProjection to display automation status with robust error states for Android (worker/SIM/power/clock/channel) and iOS (reminders, notification horizon, ledger status). Include an upcoming events summary and clear calls-to-action for "Needs your attention" states.

---

## H02 — Upcoming

### Screen Description
- **Purpose & Design Intent:** Displays a forward-looking timeline of scheduled birthday messages. It allows users to review and manage upcoming automated actions or reminders for the next seven days.
- **Key UI Elements:** 
  - Timeline list (Today, Next seven days)
  - Empty state graphic
  - Status badges for each scheduled item (e.g., missed, skipped, scheduled)
- **Key Interactions:** 
  - Scroll through upcoming timeline
  - Tap a scheduled item to view Approved message preview (H03)

### User Flow Context
- **Entry Points:** Navigated via Home (H01) or bottom tab.
- **Available Actions:** Review upcoming schedule, inspect specific upcoming messages.
- **Exit Points:** Approved message preview (H03).

### Business Requirements
- **Objectives:** Provide transparency into the planner's upcoming actions to build user trust.
- **Constraints:** Initials avatars (no photos), masked phone numbers, icon+text status.
- **BA Requirements:** BA-15 (Home readiness/upcoming view), BA-06 (Birthday recurrence/occurrence planner)

### Technical Requirements
- **Components:** LiveHomeScreen.tsx (Stack push to Upcoming)
- **APIs & Data:** useLiveProjection hook, AutomationPort, src/domain/automation/model.ts
- **Validation & Error Handling:** Handle empty, invalid proposal/approval, and source-change states. Android: Missed/Skipped/capacity/reset. iOS: reminder scheduled, beyond notification horizon, delayed reminder, composer eligible, terminal reported-Sent/Unknown, same-day reset suppression.
- **Native Modules:** BirthdayNative (getProjection)

### Final Stitch Prompt
> Generate an Upcoming events screen displaying a chronological list (Today/Next 7 days) of scheduled birthday actions using masked phone numbers and initials avatars. Implement distinct status badges for Android (Missed/Skipped/capacity) and iOS (reminder scheduled, beyond horizon, Android-managed, terminal Sent/Unknown) ensuring adherence to BA-15 and BA-06 using useLiveProjection.

---

## H03 — Approved message preview

### Screen Description
- **Purpose & Design Intent:** Shows the immutable snapshot of the approved message payload or draft proposed for a contact. It allows the user to review what exactly will be sent or proposed.
- **Key UI Elements:** 
  - Recipient summary (initials avatar, masked phone number)
  - Message body preview
  - Status/Editability notice
- **Key Interactions:** 
  - Read-only review
  - Acknowledge or dismiss

### User Flow Context
- **Entry Points:** From Upcoming (H02) or Person detail (P02).
- **Available Actions:** Review the locked-in message details.
- **Exit Points:** Back to Upcoming (H02) or Person detail (P02).

### Business Requirements
- **Objectives:** Guarantee the user sees exactly what the system committed to sending/proposing.
- **Constraints:** 48dp touch targets, Inter font, strictly read-only display of approved data.
- **BA Requirements:** BA-09 (Immutable approval snapshot)

### Technical Requirements
- **Components:** LiveMessageScreen.tsx
- **APIs & Data:** MessagePort, src/domain/approvals/model.ts
- **Validation & Error Handling:** Display Android approved-payload states unchanged. For iOS proposed-draft: show reviewed recipient/body/reminder, material invalidation warnings, MessageUI editable-final-content notice, and composer opened/result terminal (no delivery claim).
- **Native Modules:** None specifically required beyond data provided via projection.

### Final Stitch Prompt
> Generate a read-only Approved Message Preview screen (LiveMessageScreen.tsx) showcasing an immutable snapshot of a birthday message (BA-09) with initials avatars and masked phone numbers. Support Android's approved-payload view and iOS's proposed-draft variant, prominently displaying MessageUI editable-final-content notices and material invalidation warnings where applicable.

---

## H04 — Pause automation

### Screen Description
- **Purpose & Design Intent:** Provides a safe mechanism for the user to halt upcoming automated messages or reminders temporarily.
- **Key UI Elements:** 
  - Confirmation prompt
  - Pause status indicator/progress spinner
  - Success/Failure feedback
- **Key Interactions:** 
  - Confirm pause action
  - Cancel and return

### User Flow Context
- **Entry Points:** From Home (H01) settings or status banner.
- **Available Actions:** Execute pause command.
- **Exit Points:** Returns to Home (H01) with updated status.

### Business Requirements
- **Objectives:** Give users ultimate control to stop the system immediately if needed.
- **Constraints:** Must be highly visible, clear typography (Inter), and accessible (48dp targets).
- **BA Requirements:** BA-15 (Home readiness/upcoming view)

### Technical Requirements
- **Components:** LiveAutomationScreen.tsx
- **APIs & Data:** AutomationPort, src/domain/automation/model.ts
- **Validation & Error Handling:** Android: pause flow unchanged. iOS Pause reminders variant: handle ready, protected-store commit, pending notification cancellation, complete, partial cancellation/recheck, and failure states without server pause/permit language.
- **Native Modules:** BirthdayNative (executeUserIntent)

### Final Stitch Prompt
> Generate a Pause Automation confirmation screen (LiveAutomationScreen.tsx) with clear 48dp touch targets and Inter typography. Integrate executeUserIntent to halt operations, handling Android's standard pause flow and iOS's specific local reminder cancellations (protected-store commit, pending cancellation, partial/recheck, failure) without using server permit language.

---

## H05 — Resume readiness

### Screen Description
- **Purpose & Design Intent:** Guides the user through resolving any blockers preventing the automation or reminders from running, allowing them to safely resume the system.
- **Key UI Elements:** 
  - Checklist of readiness criteria
  - Resolve action buttons (e.g., grant permission, fix SIM)
  - Resume automation button
- **Key Interactions:** 
  - Tap to resolve individual issues
  - Tap to resume once all criteria are met

### User Flow Context
- **Entry Points:** From Home (H01) when in a "Paused" or "Needs attention" state.
- **Available Actions:** Fix blockers, resume system.
- **Exit Points:** Home (H01).

### Business Requirements
- **Objectives:** Ensure all necessary permissions and system states are valid before resuming, preventing silent failures.
- **Constraints:** Material 3 on Android, clear error typography.
- **BA Requirements:** BA-14 (Test SMS/activation), BA-15 (Home readiness/upcoming view)

### Technical Requirements
- **Components:** LiveAutomationScreen.tsx
- **APIs & Data:** AppProjectionPort, useLiveProjection
- **Validation & Error Handling:** Android: TestReceipt, permission, SIM, background states. iOS: Contacts/proposal valid, notification granted/denied-nonblocking, horizon rebuilt/partial, MessageUI capable/unavailable, Android-managed/status unavailable, protected-store issue.
- **Native Modules:** BirthdayNative (executeUserIntent, getProjection)

### Final Stitch Prompt
> Generate a Resume Readiness screen (LiveAutomationScreen.tsx) presenting a checklist of system health requirements. Implement dynamic error resolution flows for Android (SIM, background, TestReceipt) and iOS (Contacts validity, notification permissions, horizon rebuild, MessageUI capabilities) using executeUserIntent to restore automation and fulfill BA-14/BA-15.

---

## H06 — Today decision

### Screen Description
- **Purpose & Design Intent:** Facilitates the manual review and execution of a birthday message scheduled for the current day, particularly critical for iOS or guarded Android states.
- **Key UI Elements:** 
  - Contact info (initials, masked phone)
  - Message proposal text
  - "Review/Send" primary action button
  - Cancel/Dismiss secondary button
- **Key Interactions:** 
  - Approve and open composer (iOS) or send (Android)
  - Dismiss/Cancel

### User Flow Context
- **Entry Points:** Notification tap, or from Home (H01) "Today" widget.
- **Available Actions:** Proceed with sending, cancel message for today.
- **Exit Points:** Native composer (iOS), or return to Home (H01) post-action.

### Business Requirements
- **Objectives:** Enable atomic outbox execution and safe manual intervention for same-day birthdays.
- **Constraints:** Strict adherence to suppression rules to avoid duplicate sends.
- **BA Requirements:** BA-12 (Native SMS/SIM gateway), BA-13 (Atomic outbox/cloud fencing)

### Technical Requirements
- **Components:** LiveMessageScreen.tsx
- **APIs & Data:** MessagePort, src/domain/home/model.ts
- **Validation & Error Handling:** Android: open/closed/guard/reset/system-composer alternatives. iOS foreground review: eligible proposal and explicit 'Review message'; notification tap stale/revalidate; MessageUI handoff annotation; cancel; failed; reported-Sent; lost-result Unknown. Apply suppression for terminal repeats (Sent/Unknown), fresh-install/wipe/restore/corrupt-ledger same-day, and any live Android binding/unresolved/unavailable status.
- **Native Modules:** BirthdayNative (executeUserIntent)

### Final Stitch Prompt
> Generate a Today Decision screen (LiveMessageScreen.tsx) to facilitate same-day message execution fulfilling BA-12 and BA-13. Implement strict validation and suppression logic for iOS (stale notifications, MessageUI handoff, Sent/Unknown terminal states, fresh-install suppression, Android-managed suppression) and Android (guard/reset/system-composer states) before allowing the user to trigger executeUserIntent.

---

## P01 — People list

### Screen Description
- **Purpose & Design Intent:** Displays the master list of synced contacts, allowing users to manage who receives automated birthday messages.
- **Key UI Elements:** 
  - Search bar and filter chips (Enabled, Ready, Needs attention, Excluded)
  - Contact list items (Initials avatar, Name, Birthday, Status icon)
  - Fast scroll/Loading indicators
- **Key Interactions:** 
  - Search/Filter list
  - Tap contact to view Person detail (P02)

### User Flow Context
- **Entry Points:** Bottom tab navigation.
- **Available Actions:** Browse contacts, filter by status, select a contact.
- **Exit Points:** Person detail (P02).

### Business Requirements
- **Objectives:** Render large contact lists efficiently (10,000-contact sync < 5s) and facilitate explicit recipient enrollment.
- **Constraints:** Initials avatars (no photos), masked phone numbers. High performance required.
- **BA Requirements:** BA-16 (People/recipient manager), BA-05 (Explicit recipient enrollment)

### Technical Requirements
- **Components:** LivePeopleScreen.tsx
- **APIs & Data:** PeoplePort, src/domain/contacts/model.ts, Google People API integration via backend/projection.
- **Validation & Error Handling:** Handle loading, empty states (no contacts, no usable birthdays, empty filter/search), and partial/stale sync states. Must efficiently handle 10,000-contact list.
- **Native Modules:** BirthdayNative (getProjection)

### Final Stitch Prompt
> Generate a highly performant People List screen (LivePeopleScreen.tsx) capable of rendering 10,000 contacts in under 5 seconds (BA-16). Utilize Inter font, initials avatars, and masked phone numbers, implementing search and status filters (Enabled/Ready/Needs attention/Excluded) alongside robust empty/loading/stale-sync states (BA-05).

---

## P02 — Person detail

### Screen Description
- **Purpose & Design Intent:** Provides deep-dive information and management options for a specific contact's birthday automation settings.
- **Key UI Elements:** 
  - Header (Initials avatar, Name, Masked Phone, Birthday)
  - Automation toggle (Enable/Disable)
  - Status warnings (e.g., duplicate, invalid data)
- **Key Interactions:** 
  - Toggle automation status
  - Review contact data anomalies

### User Flow Context
- **Entry Points:** Tapped from People list (P01) or Upcoming (H02).
- **Available Actions:** Enable/disable automation, view status rationale.
- **Exit Points:** Back to previous screen.

### Business Requirements
- **Objectives:** Allow granular control over individual contact automation and surface data integrity issues clearly.
- **Constraints:** 48dp touch targets, privacy-safe display (masked phone).
- **BA Requirements:** BA-16 (People/recipient manager), BA-09 (Immutable approval snapshot)

### Technical Requirements
- **Components:** LivePersonDetailScreen.tsx
- **APIs & Data:** PeoplePort, src/domain/contacts/model.ts
- **Validation & Error Handling:** Handle states: Enabled, ready Off, paused, excluded, unavailable, duplicate blocked, phone/birthday/name changed, deleted source, approval invalidated, no safe given name.
- **Native Modules:** BirthdayNative (executeUserIntent)

### Final Stitch Prompt
> Generate a Person Detail screen (LivePersonDetailScreen.tsx) displaying a contact's initials avatar and masked phone number. Implement an automation toggle and clear icon+text status banners handling edge cases like duplicate blocked, data changes (phone/birthday/name), deleted source, and approval invalidations (BA-16, BA-09) using executeUserIntent for state changes.

---

## P03 — Excluded people

### Screen Description
- **Purpose & Design Intent:** Displays a specialized view of the People list focused entirely on contacts that have been explicitly blocked or excluded from automation.
- **Key UI Elements:** 
  - List of excluded contacts
  - Re-enable action/button per contact
  - Empty state (no exclusions)
- **Key Interactions:** 
  - Browse excluded contacts
  - Initiate re-enable review flow

### User Flow Context
- **Entry Points:** Filter selected on People list (P01) or dedicated settings link.
- **Available Actions:** Review blocklist, choose to re-enable a contact.
- **Exit Points:** Re-enable review flow, or back to People list (P01).

### Business Requirements
- **Objectives:** Ensure users can easily find and reverse exclusions if desired, enforcing explicit enrollment rules.
- **Constraints:** Inter font, Material 3 on Android, standard accessibility targets.
- **BA Requirements:** BA-05 (Explicit recipient enrollment)

### Technical Requirements
- **Components:** LivePeopleScreen.tsx (Filtered variant)
- **APIs & Data:** PeoplePort, src/domain/contacts/model.ts
- **Validation & Error Handling:** Handle empty state, populated blocklist, source deleted, material source change. Enforce that re-enabling requires a review.
- **Native Modules:** None specific, leverages existing projection.

### Final Stitch Prompt
> Generate an Excluded People view utilizing LivePeopleScreen.tsx to display a blocklist of contacts. Support empty states, source deleted/changed warnings, and implement a re-enable action that strictly routes the user to a review flow to satisfy explicit recipient enrollment (BA-05).

---

## P04 — Approval invalidation

### Screen Description
- **Purpose & Design Intent:** Alerts the user when a previously approved automation setup is invalidated due to underlying data changes (e.g., name, phone number, template changes), forcing a re-review.
- **Key UI Elements:** 
  - Change diff/explanation (e.g., "Phone number changed")
  - Call-to-action to "Review now"
  - Keep paused/disabled option
- **Key Interactions:** 
  - Tap to review and re-approve
  - Acknowledge and keep disabled

### User Flow Context
- **Entry Points:** From Home (H01) attention banner, or Person detail (P02).
- **Available Actions:** Review changes to re-approve, or leave paused.
- **Exit Points:** Approval flow, or back to origin.

### Business Requirements
- **Objectives:** Prevent automated messages from being sent to incorrect numbers or with incorrect data after contact updates.
- **Constraints:** High visibility alerts, clear explanation of what changed.
- **BA Requirements:** BA-09 (Immutable approval snapshot)

### Technical Requirements
- **Components:** LivePersonDetailScreen.tsx (Invalidation variant)
- **APIs & Data:** ApprovalsPort, src/domain/approvals/model.ts
- **Validation & Error Handling:** Shared: name/phone/birthday/template/reminder-or-window/disclosure changes. Android: segment plan/SIM/Test invalidation. iOS: proposed-draft invalidation and MessageUI editability reminder.
- **Native Modules:** BirthdayNative (executeUserIntent)

### Final Stitch Prompt
> Generate an Approval Invalidation view within LivePersonDetailScreen.tsx to handle BA-09 compliance when underlying contact data changes. Display explicit diffs for name/phone/birthday/template changes, integrating Android-specific invalidations (SIM/segment plan) and iOS-specific proposed-draft invalidations with MessageUI editability reminders, offering clear 'Review now' or 'Keep paused' actions.

---
 
# Section 4: Activity & Diagnostics (6 Screens) / Section 5: Settings (10 Screens) 
 
# Section 3: Activity & Settings (A01-A06, T01-T10)

---

## A01 — Activity

### Screen Description
- **Purpose & Design Intent:** Provides a comprehensive ledger of all automated and manual birthday actions, presenting system outcomes and statuses clearly. Serves as the primary history view for users to verify past operations and review pending iOS scheduled items.
- **Key UI Elements:** Scrollable feed list, loading states, empty state graphic, minimized content view, 30-day filter toggle, offline banner, and clear history button.
- **Key Interactions:** Pull to refresh, tap on list item to view details, toggle history filter, tap clear history to wipe local log.

### User Flow Context
- **Entry Points:** Bottom navigation (Activity tab).
- **Available Actions:** View details, filter feed, clear history.
- **Exit Points:** A02 (Activity detail), A06 (Clear activity), Home tab.

### Business Requirements
- **Objectives:** Build user trust through transparent auditing of all actions (success, failures, blocked).
- **Constraints:** Max 400-day safety ledger on Android; iOS bounded terminal-repeat/reset marker. Offline compatibility. Accessible 48dp touch targets.
- **BA Requirements:** BA-17

### Technical Requirements
- **Components:** LiveActivityScreen.tsx
- **APIs & Data:** ActivityPort, src/domain/activity/model.ts
- **Validation & Error Handling:** Handle offline gracefully, display empty state if no data, filter boundaries for 30-day view.
- **Native Modules:** BirthdayNative.getProjection

### Final Stitch Prompt
> Generate the Activity screen (A01) using LiveActivityScreen.tsx with Inter font and light/dark theme (#F7F7FC/#11121A). Display a feed of past and pending actions via ActivityPort, including shared loading, empty, offline, and 30-day filtered states. For Android, show the standard feed. For iOS, include variants for reminder planned/reconciled, visibility unknown, horizon partial/exhausted, Android-managed suppression, proposal invalidated, and Composer outcomes. Include a clear history action. Ensure 48dp touch targets and accessible contrast.

---

## A02 — Activity detail

### Screen Description
- **Purpose & Design Intent:** Shows deep diagnostic details for a single activity event. Allows the user to understand exactly what happened during a specific automation execution or manual send.
- **Key UI Elements:** Event header (status icon, date/time), detailed metadata list, recipient information, payload summary, error stack or reason (if failed).
- **Key Interactions:** Scroll details, tap to copy diagnostic ID, return to feed.

### User Flow Context
- **Entry Points:** Tapping an item in A01 (Activity).
- **Available Actions:** View full payload, review failure reasons.
- **Exit Points:** Back to A01.

### Business Requirements
- **Objectives:** Provide clear troubleshooting information for failed or blocked messages without overwhelming the user.
- **Constraints:** Text must scale up to 200%. No PII in diagnostic IDs.
- **BA Requirements:** BA-17

### Technical Requirements
- **Components:** LiveActivityScreen.tsx
- **APIs & Data:** ActivityPort, src/domain/activity/model.ts
- **Validation & Error Handling:** Graceful fallback if event payload is corrupted.
- **Native Modules:** BirthdayNative.getProjection

### Final Stitch Prompt
> Generate the Activity detail screen (A02) in LiveActivityScreen.tsx with Inter font and theming. Show deep metadata for a single event. Keep Android states standard. On iOS, support variants: reminder planned/reconciled/visibility unknown, Composer opened/cancelled/failed, Messages reported sent/delivery not confirmed, lost-result Unknown, terminal repeat suppression, proposal invalidated, and Android-managed suppression. Explain that final recipient/body/sender and SMS transport details are not visible to the app on iOS.

---

## A03 — Needs your attention

### Screen Description
- **Purpose & Design Intent:** Central hub for blocking issues and system warnings that prevent the app from functioning correctly. Prioritizes critical issues so users can resolve them immediately.
- **Key UI Elements:** Priority list of issues (account, contact, proposal, Gemini), warning banners with #8A4F08 or #A53535 accents, resolution action buttons.
- **Key Interactions:** Tap an issue to navigate to the repair flow, pull to refresh.

### User Flow Context
- **Entry Points:** Notification tap, Home screen alert banner, Settings Home (T01).
- **Available Actions:** Initiate repair workflows for blockers.
- **Exit Points:** A04 (Issue repair), Settings.

### Business Requirements
- **Objectives:** Reduce silent failures by surfacing explicit, actionable blockers.
- **Constraints:** High contrast for warnings. Clear prioritization of destructive vs minor issues.
- **BA Requirements:** BA-15, BA-17

### Technical Requirements
- **Components:** LiveAttentionScreen.tsx
- **APIs & Data:** AppProjectionPort, src/domain/readiness/model.ts
- **Validation & Error Handling:** Re-evaluate blockers upon return from resolution flows.
- **Native Modules:** BirthdayNative.getProjection

### Final Stitch Prompt
> Generate the Needs your attention screen (A03) in LiveAttentionScreen.tsx using #8A4F08 warning and #A53535 critical accents. Display a prioritized list of blockers. Include shared all-clear, account, contact, proposal, and Gemini states. For iOS, add groups for notification denied, horizon partial/exhausted, MessageUI unavailable, protected-store/reset, Android-managed, coexistence unavailable, and Composer outcome Unknown. Add action buttons to trigger repair flows. Ensure 48dp tap targets.

---

## A04 — Issue repair

### Screen Description
- **Purpose & Design Intent:** Step-by-step resolution screen for a specific blocking issue identified in A03. Guides the user through granting permissions, updating settings, or fixing data.
- **Key UI Elements:** Issue explanation text, native permission or settings prompt buttons, recheck status button, success/failure confirmation.
- **Key Interactions:** Tap to open native settings, recheck resolution status, dismiss on success.

### User Flow Context
- **Entry Points:** A03 (Needs your attention).
- **Available Actions:** Fix issue, recheck.
- **Exit Points:** Back to A03, Home.

### Business Requirements
- **Objectives:** Maximize successful issue resolution rates through clear, context-aware instructions.
- **Constraints:** Must handle background-to-foreground transitions when returning from native settings.
- **BA Requirements:** BA-17

### Technical Requirements
- **Components:** LiveAttentionScreen.tsx
- **APIs & Data:** AppProjectionPort, src/domain/readiness/model.ts
- **Validation & Error Handling:** Handle cases where the user denies permission again.
- **Native Modules:** BirthdayNative.executeUserIntent

### Final Stitch Prompt
> Generate the Issue repair screen (A04) in LiveAttentionScreen.tsx. Present a focused UI for resolving a specific blocker with Inter font and standard theming. Include shared account/Contacts/recheck states. For Android, implement standard repair surfaces. For iOS, handle repairs for notification settings, reminder horizon reconciliation, MessageUI capability changes, protected-store/reset explanation, and Android-managed/unavailable coexistence (omitting fake transfer or sender controls). Include a Recheck button using BirthdayNative.executeUserIntent.

---

## A05 — Diagnostics preview

### Screen Description
- **Purpose & Design Intent:** Allows the user to view and export anonymized system logs for support purposes. Promotes transparency and aids in debugging.
- **Key UI Elements:** Scrollable text view of allowlisted logs, generate export button, share sheet trigger, large text support toggle, error states.
- **Key Interactions:** Generate logs, review logs, tap share.

### User Flow Context
- **Entry Points:** Help & Support or Settings.
- **Available Actions:** Preview logs, export to share sheet.
- **Exit Points:** System share sheet, Back.

### Business Requirements
- **Objectives:** Facilitate user support without exposing Personally Identifiable Information (PII).
- **Constraints:** Strict PII filtering. Handle large text buffers without memory crashes.
- **BA Requirements:** BA-17

### Technical Requirements
- **Components:** LiveDiagnosticsScreen.tsx
- **APIs & Data:** ActivityPort
- **Validation & Error Handling:** Handle generation failures gracefully.
- **Native Modules:** BirthdayNative.executeUserIntent (for share handoff)

### Final Stitch Prompt
> Generate the Diagnostics preview screen (A05) in LiveDiagnosticsScreen.tsx. Show an empty state, generation loading, and a scrollable view of allowlisted non-PII diagnostic text. Include support for large text and generation failure states. Add an export button that triggers system share handoff via BirthdayNative.executeUserIntent. Use Inter font, light/dark backgrounds (#F7F7FC/#11121A), and ensure all touch targets are at least 48dp.

---

## A06 — Clear activity

### Screen Description
- **Purpose & Design Intent:** A destructive action confirmation screen for wiping the local activity ledger. Explains the scope of deletion to prevent accidental data loss.
- **Key UI Elements:** Warning icon (#A53535), explanatory text outlining scope, cancel button, destructive confirm button, loading indicator, success/failure toast.
- **Key Interactions:** Confirm deletion, cancel.

### User Flow Context
- **Entry Points:** A01 (Activity).
- **Available Actions:** Confirm clear, cancel.
- **Exit Points:** A01.

### Business Requirements
- **Objectives:** Give users control over their data footprint while enforcing safety ledgers where required.
- **Constraints:** Must clarify that Android retains a 400-day safety ledger, while iOS only retains the bounded minimal marker.
- **BA Requirements:** BA-17

### Technical Requirements
- **Components:** LiveActivityScreen.tsx
- **APIs & Data:** ActivityPort, src/domain/activity/model.ts
- **Validation & Error Handling:** Handle deletion failures.
- **Native Modules:** BirthdayNative.executeUserIntent

### Final Stitch Prompt
> Generate the Clear activity screen (A06) in LiveActivityScreen.tsx. Build a destructive confirmation view using the critical color #A53535. Display states for scope explanation, ready, clearing, complete, and failed. Include platform-specific explanatory text: Android retains the 400-day safety ledger, while iOS retains only the bounded minimal terminal-repeat/reset marker. Provide Cancel and Clear buttons with 48dp touch targets.

---

## T01 — Settings home

### Screen Description
- **Purpose & Design Intent:** The root configuration hub for the application. Organizes all global preferences, privacy controls, and device readiness states into logical groups.
- **Key UI Elements:** User profile header (signed in/out), attention needed banner, grouped navigation list (Automation, Message, Notifications, Privacy, Help).
- **Key Interactions:** Tap list items to navigate to sub-settings.

### User Flow Context
- **Entry Points:** Bottom navigation (Settings tab).
- **Available Actions:** Navigate to child settings, view high-level status.
- **Exit Points:** T02-T10, A03 (Needs attention).

### Business Requirements
- **Objectives:** Provide intuitive access to all configuration options and data controls.
- **Constraints:** Platform-adaptive grouped index.
- **BA Requirements:** BA-18

### Technical Requirements
- **Components:** LiveSettingsScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** AppProjectionPort
- **Validation & Error Handling:** None specific to navigation, surface remote cleanup pending state.
- **Native Modules:** None directly.

### Final Stitch Prompt
> Generate the Settings home screen (T01) in LiveSettingsScreen.tsx using Inter font and #F7F7FC/#11121A backgrounds. Display a user header (signed in/retained signed out), an optional 'Needs attention' banner, and a remote cleanup pending indicator. Create a platform-adaptive grouped index: show Android Automation controls on Android, or iOS Companion reminders/composer controls on iOS. Ensure Material 3 styling on Android and 48dp minimum touch targets.

---

## T02 — Automation policy

### Screen Description
- **Purpose & Design Intent:** Allows users to configure the global rules for message sending (e.g., time of day, approval windows).
- **Key UI Elements:** Time pickers, bounded horizon sliders, save/cancel buttons, warning text for timezone issues.
- **Key Interactions:** Adjust policy parameters, save changes.

### User Flow Context
- **Entry Points:** T01 (Settings home).
- **Available Actions:** Edit policy, save.
- **Exit Points:** T01.

### Business Requirements
- **Objectives:** Give users control over when automated actions occur to prevent inappropriate send times.
- **Constraints:** iOS must show civil-date coalescing and explain that saving invalidates pending proposals.
- **BA Requirements:** BA-10

### Technical Requirements
- **Components:** LivePolicyEditor.tsx, LiveAutomationScreen.tsx
- **APIs & Data:** AutomationPort
- **Validation & Error Handling:** Validate timezone issues, display save-ready or invalid states.
- **Native Modules:** BirthdayNative.executeUserIntent

### Final Stitch Prompt
> Generate the Automation policy screen (T02) using LivePolicyEditor.tsx and LiveAutomationScreen.tsx. Keep Android policy states standard. For iOS, build the Reminder policy variant showing current window, bounded horizon, and civil-date coalescing. Handle save-ready/invalid and timezone issue states. Include explanatory text for iOS that saving invalidates proposals and rebuilds pending requests, omitting cap/spacing/send guarantee language. Use the accent color #4B52A3 for interactive elements.

---

## T03 — Message

### Screen Description
- **Purpose & Design Intent:** Manage built-in, user-edited, and Gemini-derived local message templates used for generating greetings.
- **Key UI Elements:** Template list, edit text inputs, restore default button, clear Gemini templates button, offline warning.
- **Key Interactions:** Edit template, restore built-in, clear AI templates.

### User Flow Context
- **Entry Points:** T01.
- **Available Actions:** Customize templates, reset.
- **Exit Points:** T01.

### Business Requirements
- **Objectives:** Personalize the user experience through customizable and AI-assisted messaging.
- **Constraints:** Fallback to built-in when offline or on validation error.
- **BA Requirements:** BA-07, BA-08

### Technical Requirements
- **Components:** LiveMessageScreen.tsx
- **APIs & Data:** AutomationPort, Vertex AI (backend)
- **Validation & Error Handling:** Show invalid state for empty/oversized templates.
- **Native Modules:** None.

### Final Stitch Prompt
> Generate the Message settings screen (T03) in LiveMessageScreen.tsx. Display a list of built-in, user-edited, and Gemini-derived local templates. Include states for invalid inputs, offline warnings, restoring built-in templates, and clearing Gemini templates. Ensure text inputs scale to 200% for accessibility and use Inter font with #F7F7FC/#11121A backgrounds. Provide clear 48dp touch targets for restore and clear actions.

---

## T04 — SIM and charges

### Screen Description
- **Purpose & Design Intent:** Informs the user about carrier settings, SMS/MMS transport rules, and potential charges.
- **Key UI Elements:** Carrier info text, disclaimer banners.
- **Key Interactions:** Read-only on iOS, interactive SIM selection on Android.

### User Flow Context
- **Entry Points:** T01.
- **Available Actions:** View info, select SIM (Android).
- **Exit Points:** T01.

### Business Requirements
- **Objectives:** Prevent unexpected carrier charges and clarify transport mechanisms.
- **Constraints:** iOS is strictly read-only regarding sender line/transport.
- **BA Requirements:** BA-12

### Technical Requirements
- **Components:** LiveSettingsScreen.tsx, LiveAndroidDeviceControls.tsx
- **APIs & Data:** AppProjectionPort
- **Validation & Error Handling:** None.
- **Native Modules:** BirthdayNative.getProjection

### Final Stitch Prompt
> Generate the SIM and charges screen (T04) in LiveSettingsScreen.tsx and LiveAndroidDeviceControls.tsx. Implement Android-only interactive SIM settings normally. For iOS, create a read-only companion variant displaying warnings about possible carrier charges and clarifying that iOS/Messages controls any available sender line and SMS/MMS/iMessage transport. Explicitly remove app SIM selection controls on iOS. Use standard typography and theming.

---

## T05 — Notifications

### Screen Description
- **Purpose & Design Intent:** Manage system notification preferences, permissions, and schedules.
- **Key UI Elements:** Permission status banner, toggle switches, summary text.
- **Key Interactions:** Toggle summaries, tap to open system settings.

### User Flow Context
- **Entry Points:** T01.
- **Available Actions:** Enable/disable summaries, review status.
- **Exit Points:** Native OS settings, T01.

### Business Requirements
- **Objectives:** Keep users informed without spamming. Clear up iOS specific reminder semantics.
- **Constraints:** Must reflect true OS permission state.
- **BA Requirements:** BA-20

### Technical Requirements
- **Components:** LiveSettingsScreen.tsx
- **APIs & Data:** AppProjectionPort
- **Validation & Error Handling:** Handle pre-permission, granted, denied, and system-disabled states.
- **Native Modules:** BirthdayNative.getProjection

### Final Stitch Prompt
> Generate the Notifications screen (T05) in LiveSettingsScreen.tsx. Include shared pre-permission, granted, denied, nonblocking, system-disabled, and generic lock-screen states. On Android, include a success summary Off/on toggle. On iOS, add specific UI for per-civil-date generic reminders, bounded pending horizon, scheduled vs planned-not-yet-scheduled dates, horizon exhausted/reconcile, and Focus/delivery uncertainty. Ensure 48dp touch targets and accessible contrast.

---

## T06 — Google, Contacts, and sender device

### Screen Description
- **Purpose & Design Intent:** Manages third-party account bindings, contact sync freshness, and device identity status.
- **Key UI Elements:** Connection status cards, sync/reconnect/disconnect buttons, warning for unresolved permits.
- **Key Interactions:** Sync contacts, sign out, reconnect.

### User Flow Context
- **Entry Points:** T01.
- **Available Actions:** Manage account bindings, force sync.
- **Exit Points:** OAuth flow, T01.

### Business Requirements
- **Objectives:** Maintain data freshness and clarify cloud boundaries.
- **Constraints:** Contacts freshness policy max 30-day (automation). iOS shows Companion limitations.
- **BA Requirements:** BA-02, BA-03, BA-13

### Technical Requirements
- **Components:** LivePrivacyScreen.tsx
- **APIs & Data:** PrivacyPort, Google People API, Firebase Auth
- **Validation & Error Handling:** Handle sync failures, cleanup, revoke, and sign-out.
- **Native Modules:** BirthdayNative.executeUserIntent

### Final Stitch Prompt
> Generate the Google, Contacts, and sender device screen (T06) in LivePrivacyScreen.tsx. Support shared states for connected, reconnect, sync, cleanup, disconnect, revoke, and sign-out. Render Android sender/transfer states normally. For iOS, display companion-only identity, omitting sender epoch and transfer actions. Show "Managed by Android" for live bindings, no-binding/composer-eligible states, unresolved permits, and unavailable coexistence. Enforce 48dp touch targets.

---

## T07 — Device readiness and permissions

### Screen Description
- **Purpose & Design Intent:** A checklist view of all required system permissions and readiness factors to ensure automated and manual tasks can succeed.
- **Key UI Elements:** Checklist with status icons (positive #256A45, critical #A53535), recheck button.
- **Key Interactions:** Recheck statuses, tap items to resolve.

### User Flow Context
- **Entry Points:** T01.
- **Available Actions:** Review system readiness.
- **Exit Points:** A04 (Issue repair), T01.

### Business Requirements
- **Objectives:** Proactively inform users of missing permissions before they cause failures.
- **Constraints:** Accuracy of native state reporting.
- **BA Requirements:** BA-01, BA-11

### Technical Requirements
- **Components:** LiveSettingsScreen.tsx
- **APIs & Data:** AppProjectionPort, src/domain/readiness/model.ts
- **Validation & Error Handling:** Accurate reflection of OS states.
- **Native Modules:** BirthdayNative.getProjection

### Final Stitch Prompt
> Generate the Device readiness and permissions screen (T07) in LiveSettingsScreen.tsx. Use status colors (#256A45 for positive, #A53535 for critical). Maintain standard Android readiness UI. For iOS, build the companion readiness checklist: notification authorization, reminder horizon, MessageUI capability, protected store status, Google/Firebase/App Check companion-status availability, and Android-managed suppression. Include a rechecking state and ensure 48dp minimum tap targets.

---

## T08 — Privacy and data

### Screen Description
- **Purpose & Design Intent:** Control center for data deletion and privacy lifecycle management, handling destructive actions safely.
- **Key UI Elements:** Deletion initiation buttons, warning banners, secondary native review prompts.
- **Key Interactions:** Initiate exact-account deletion, review data handling.

### User Flow Context
- **Entry Points:** T01.
- **Available Actions:** Delete data, review privacy.
- **Exit Points:** T01, Web support.

### Business Requirements
- **Objectives:** Comply with data privacy regulations (GDPR, CCPA) with robust deletion safeguards.
- **Constraints:** Second native review required for immediate device erase.
- **BA Requirements:** BA-18

### Technical Requirements
- **Components:** LivePrivacyScreen.tsx
- **APIs & Data:** PrivacyPort, src/domain/privacy/model.ts
- **Validation & Error Handling:** Handle ambiguous deletion acceptance, blocked-Setup repairs.
- **Native Modules:** BirthdayNative.executeUserIntent

### Final Stitch Prompt
> Generate the Privacy and data screen (T08) in LivePrivacyScreen.tsx using Inter font. Include shared states: retained, remote-pending, deletion, and destructive (with a second native review for immediate device erase after failed/ambiguous deletion), neutral remote-unknown, same-account-only replay, blocked-Setup exact-account lifecycle repair, and support/web recovery. For iOS, add pending-notification cancellation, protected-store/Keychain removal, composer handed off, and Messages/iCloud/carrier/recipient external-copy disclosure.

---

## T09 — Data inventory and retention

### Screen Description
- **Purpose & Design Intent:** A transparent, read-only breakdown of exactly what data is stored, where it lives, and its retention boundaries.
- **Key UI Elements:** Categorized list of data stores, storage size text, boundary definitions.
- **Key Interactions:** Scroll to review.

### User Flow Context
- **Entry Points:** T01.
- **Available Actions:** View data footprint.
- **Exit Points:** T01.

### Business Requirements
- **Objectives:** Maximize transparency about cloud vs local data storage.
- **Constraints:** Accurate representation of platform-specific boundaries.
- **BA Requirements:** BA-18

### Technical Requirements
- **Components:** LivePrivacyInventory.tsx
- **APIs & Data:** PrivacyPort
- **Validation & Error Handling:** N/A.
- **Native Modules:** BirthdayNative.getProjection

### Final Stitch Prompt
> Generate the Data inventory and retention screen (T09) in LivePrivacyInventory.tsx. Present a structured view of Contacts/Firebase/Google/Gemini boundaries. On Android, include the Room/outbox/SMS Provider/carrier safety ledger. On iOS, list protected local proposals/reminders/composer-result classes, pending notification requests, MessageUI/Messages/iCloud boundaries, no sender claim/permit data, storage size, and consent/deletion states. Ensure the text is scalable to 200% and uses standard light/dark themes.

---

## T10 — Help, legal, and about

### Screen Description
- **Purpose & Design Intent:** Standard screen for legal links, app version info, and support resources.
- **Key UI Elements:** External links list, version text, distribution channel text.
- **Key Interactions:** Tap to open links.

### User Flow Context
- **Entry Points:** T01.
- **Available Actions:** Open privacy policy, view version.
- **Exit Points:** External browser.

### Business Requirements
- **Objectives:** Provide legally required documentation and version auditing.
- **Constraints:** Offline link failure handling. Highlight unsupported Android builds.
- **BA Requirements:** BA-19

### Technical Requirements
- **Components:** LiveHelpLegalScreen.tsx
- **APIs & Data:** AppProjectionPort
- **Validation & Error Handling:** Offline handling for external links.
- **Native Modules:** None.

### Final Stitch Prompt
> Generate the Help, legal, and about screen (T10) in LiveHelpLegalScreen.tsx. Show privacy/terms/support/deletion links and app/build/platform/distribution channel info. Include offline link failure states. For Android, add an unsupported build/channel warning. For iOS, note Companion limitations, link to App Store privacy/support, and explain MessageUI/reminder truth. Ensure 48dp minimum touch targets for all external links and accessibility compliance for text contrast.

---
 
# Section 6: Account, Transfer & Lifecycle (11 Screens) / Section 7: Hosted Deletion (3 Screens) 
 
---

## L01 — Sender transfer

### Screen Description
- **Purpose & Design Intent:** Initiates the transfer of sender capability to another device. Designed to guide Android users through cooperative, lost-phone, and reauthentication flows securely.
- **Key UI Elements:**
  - Transfer initiation button
  - Authentication prompt (biometrics/password)
  - Device selection list or QR code display
  - Status indicators (pending, lost-phone mode)
- **Key Interactions:**
  - Authenticate to authorize transfer
  - Select target device
  - Cancel transfer request

### User Flow Context
- **Entry Points:** Device settings, Account management
- **Available Actions:** Start transfer, Authenticate, Cancel
- **Exit Points:** L02 (Transfer approval on old phone), L03 (Transfer drain and status), Settings

### Business Requirements
- **Objectives:** Securely migrate active sender state without data loss or duplication.
- **Constraints:** Android-only flow. iOS maps live binding to read-only "Managed by Android". No raw PII in logs; use HMAC-peppered pseudonyms.
- **BA Requirements:** BA-13 (Atomic outbox/cloud fencing)

### Technical Requirements
- **Components:** `LiveAndroidDeviceControls.tsx`
- **APIs & Data:** `DeviceLifecyclePort`, GlobalControl Firestore, AccountFence (TEST_ONLY, AUTOMATION_ACTIVE). Domain: `src/domain/privacy/model.ts`
- **Validation & Error Handling:** Fail closed on network drops (CoordinationUnknown), no blind retries.
- **Native Modules:** `BirthdayNative` (getProjection, executeUserIntent)

### Final Stitch Prompt
> Create the Sender transfer screen for Android (L01) using LiveAndroidDeviceControls, implementing cooperative, lost-phone, and reauth flows via DeviceLifecyclePort and GlobalControl Firestore, ensuring no blind retries and strict iOS read-only handling, adhering to BA-13 and failing closed on network drops.

---

## L02 — Transfer approval on old phone

### Screen Description
- **Purpose & Design Intent:** Allows the user to securely approve a sender transfer request on their original Android device. Prevents unauthorized hijacking of the sender role.
- **Key UI Elements:**
  - Approval prompt with target device details
  - "Approve" button (#256A45 positive)
  - "Decline" button (#A53535 critical)
  - "Pause" transfer button
- **Key Interactions:**
  - Tap Approve to proceed
  - Tap Decline to abort
  - Tap Pause to halt the process

### User Flow Context
- **Entry Points:** Push notification, L01 (Sender transfer)
- **Available Actions:** Approve, Decline, Pause
- **Exit Points:** L03 (Transfer drain and status), Settings

### Business Requirements
- **Objectives:** Ensure explicit user consent before relinquishing sender role.
- **Constraints:** Android-only request/pause/approve/decline/convergence flow. No iOS equivalent. 48dp touch targets.
- **BA Requirements:** BA-13 (Atomic outbox/cloud fencing)

### Technical Requirements
- **Components:** `LiveAndroidDeviceControls.tsx`
- **APIs & Data:** `DeviceLifecyclePort`, pre-issued-permit state handling, AccountFence.
- **Validation & Error Handling:** Reject invalid/expired permits. Prevent simultaneous approvals.
- **Native Modules:** `BirthdayNative` (executeUserIntent)

### Final Stitch Prompt
> Generate the Transfer approval screen (L02) using LiveAndroidDeviceControls for Android only, featuring approve/decline/pause actions for pre-issued-permits via DeviceLifecyclePort, ensuring 48dp targets and strict adherence to BA-13 for atomic cloud fencing.

---

## L03 — Transfer drain and status

### Screen Description
- **Purpose & Design Intent:** Displays the real-time status of a transfer, ensuring the old device's queue is drained and synced before finalizing. Provides visibility into the handover process.
- **Key UI Elements:**
  - Progress bar/spinner
  - Status text (draining, syncing, complete, failed)
  - Error messages (old-phone-rejected, network fail)
- **Key Interactions:**
  - View progress
  - Acknowledge failure or completion

### User Flow Context
- **Entry Points:** L01, L02
- **Available Actions:** Acknowledge status
- **Exit Points:** L10 (Operation receipt), Settings

### Business Requirements
- **Objectives:** Prevent message duplication or loss during transfer.
- **Constraints:** Android-only trusted-server drain/TEST_ONLY/failure flow. iOS shows read-only "Managed by Android" while binding exists.
- **BA Requirements:** BA-13 (Atomic outbox/cloud fencing)

### Technical Requirements
- **Components:** `LiveAndroidDeviceControls.tsx`
- **APIs & Data:** `DeviceLifecyclePort`, epoch transfer coordination via Cloud Functions.
- **Validation & Error Handling:** Handle CoordinationUnknown, network drops. Fail closed.
- **Native Modules:** `BirthdayNative`

### Final Stitch Prompt
> Construct the Transfer drain and status screen (L03) with LiveAndroidDeviceControls to visualize trusted-server drain and epoch transfer coordination via Cloud Functions, handling CoordinationUnknown network failures robustly, complying with BA-13.

---

## L04 — Retained-account reconnect

### Screen Description
- **Purpose & Design Intent:** Helps users re-authenticate and reconnect a previously retained account. Resolves mismatches between local secure storage and the cloud.
- **Key UI Elements:**
  - Reconnect prompt text
  - "Sign In with Google" button
  - Mismatch warning (if different account detected)
- **Key Interactions:**
  - Initiate Google Sign-In
  - Dismiss or resolve mismatch

### User Flow Context
- **Entry Points:** App launch (retained state), Settings
- **Available Actions:** Sign in, Cancel
- **Exit Points:** Home screen, Settings

### Business Requirements
- **Objectives:** Smoothly restore access for returning users while preventing account mix-ups.
- **Constraints:** Android states unchanged. iOS: handles protected-store/Keychain mismatch, rejects different accounts, reminders remain Off until rebuilt, no epoch/TestReceipt language.
- **BA Requirements:** BA-02 (Google account coordinator)

### Technical Requirements
- **Components:** `LiveSetupScreen.tsx`, `LivePrivacyScreen.tsx`
- **APIs & Data:** `PrivacyPort`, Google Auth. Room Keystore (Android) / Data Protection/Keychain (iOS).
- **Validation & Error Handling:** Reject if authenticated account does not match retained subject.
- **Native Modules:** `BirthdayNative`

### Final Stitch Prompt
> Build the Retained-account reconnect screen (L04) using LiveSetupScreen and PrivacyPort to handle Google reauthentication, enforcing strict exact-subject protected setup matching for iOS Keychain and Android Keystore, fulfilling BA-02 requirements.

---

## L05 — Sign out

### Screen Description
- **Purpose & Design Intent:** Provides the user with a choice to simply sign out (retaining data) or wipe local data entirely. Clarifies the consequences of each action.
- **Key UI Elements:**
  - "Retain Data" option
  - "Wipe Data" option
  - Warning text about pending messages
  - "Confirm Sign Out" button
- **Key Interactions:**
  - Select Retain vs Wipe
  - Confirm sign out

### User Flow Context
- **Entry Points:** Account settings
- **Available Actions:** Choose retain/wipe, Confirm, Cancel
- **Exit Points:** L08 (Delete local app data), L11 (Account switch blocker), Welcome screen

### Business Requirements
- **Objectives:** Give users explicit control over their local data footprint upon exit.
- **Constraints:** Android active-permit/remote-pending unchanged. iOS cancels reminders, retains only exact-subject setup or wipes Keychain; handed-off composers cannot be recalled.
- **BA Requirements:** BA-18 (Privacy/account/data controls)

### Technical Requirements
- **Components:** `LivePrivacyScreen.tsx`
- **APIs & Data:** `PrivacyPort`, Google Auth.
- **Validation & Error Handling:** Handle network failures gracefully during remote state sync.
- **Native Modules:** `BirthdayNative`

### Final Stitch Prompt
> Create the Sign out screen (L05) using LivePrivacyScreen to offer a shared Retain/Wipe decision via PrivacyPort, ensuring iOS reminder cancellation and exact-subject protected setup retention, adhering to BA-18 privacy controls.

---

## L06 — Disconnect Contacts

### Screen Description
- **Purpose & Design Intent:** Allows users to revoke the app's access to their contacts, explaining exactly what data will be removed and what will remain.
- **Key UI Elements:**
  - Exact-scope preview of data to be deleted
  - "Disconnect" button (#A53535 critical)
  - Offline warning (if applicable)
- **Key Interactions:**
  - Confirm disconnection
  - Cancel

### User Flow Context
- **Entry Points:** Privacy settings
- **Available Actions:** Disconnect, Cancel
- **Exit Points:** Settings, L10 (Operation receipt)

### Business Requirements
- **Objectives:** Ensure user trust by providing transparent and reversible contact sync controls.
- **Constraints:** Android 24-hour Birthday reset fence unchanged. iOS cancels reminders, removes contact-derived proposals, requires fresh sync/review.
- **BA Requirements:** BA-18 (Privacy/account/data controls), BA-03 (Contacts sync)

### Technical Requirements
- **Components:** `LivePrivacyScreen.tsx`
- **APIs & Data:** `IdentityContactsPort`, `PrivacyPort`.
- **Validation & Error Handling:** Handle offline state (queue cleanup).
- **Native Modules:** `BirthdayNative`

### Final Stitch Prompt
> Generate the Disconnect Contacts screen (L06) with LivePrivacyScreen to execute exact-scope cleanup via IdentityContactsPort, managing Android 24-hour reset fences and iOS reminder cancellations, strictly adhering to BA-18 and BA-03.

---

## L07 — Revoke all Google access

### Screen Description
- **Purpose & Design Intent:** A critical privacy control to sever the app's connection to the user's Google account completely, requiring reauthentication.
- **Key UI Elements:**
  - All-application-scope warning
  - Reauthentication prompt
  - "Revoke Access" button (#A53535 critical)
- **Key Interactions:**
  - Authenticate
  - Confirm revocation

### User Flow Context
- **Entry Points:** Privacy settings
- **Available Actions:** Reauthenticate, Revoke, Cancel
- **Exit Points:** Welcome screen, L10 (Operation receipt)

### Business Requirements
- **Objectives:** Provide a nuclear option for privacy, ensuring all Google tokens are invalidated.
- **Constraints:** Shared warning/reauth/revoke flow. iOS includes native Google revoke, reminder cancellation, protected data cleanup.
- **BA Requirements:** BA-18 (Privacy/account/data controls), BA-02 (Google account coordinator)

### Technical Requirements
- **Components:** `LivePrivacyScreen.tsx`
- **APIs & Data:** `PrivacyPort`, Google Auth APIs.
- **Validation & Error Handling:** Handle revocation failure or offline state.
- **Native Modules:** `BirthdayNative`

### Final Stitch Prompt
> Develop the Revoke all Google access screen (L07) in LivePrivacyScreen providing an all-application-scope warning and reauthentication flow via PrivacyPort, ensuring iOS native Google revoke and protected data cleanup, matching BA-18 and BA-02 constraints.

---

## L08 — Delete local app data

### Screen Description
- **Purpose & Design Intent:** Allows users to completely erase all local application data, caches, and keystores without necessarily deleting their cloud account.
- **Key UI Elements:**
  - Data scope explanation
  - "Delete Local Data" button (#A53535 critical)
  - Confirmation dialog
- **Key Interactions:**
  - Confirm deletion
  - Cancel

### User Flow Context
- **Entry Points:** Sign out flow (Wipe option), Privacy settings
- **Available Actions:** Delete, Cancel
- **Exit Points:** Welcome screen, L10 (Operation receipt)

### Business Requirements
- **Objectives:** Ensure compliance with local data minimization and privacy requests.
- **Constraints:** Android callback/key/release unchanged. iOS disables reminders, deletes Keychain/protected store, verifies absence, suppresses same-day composer resets. Messages/iCloud copies remain.
- **BA Requirements:** BA-18 (Privacy/account/data controls)

### Technical Requirements
- **Components:** `LivePrivacyScreen.tsx`
- **APIs & Data:** `PrivacyPort`. Room Keystore (Android) / Data Protection/Keychain (iOS).
- **Validation & Error Handling:** Verify absence of data post-deletion.
- **Native Modules:** `BirthdayNative` (executeUserIntent)

### Final Stitch Prompt
> Construct the Delete local app data screen (L08) using LivePrivacyScreen to trigger complete protected store and Keychain/Keystore erasure via PrivacyPort, verifying absence and handling iOS reminder disabling, in accordance with BA-18.

---

## L09 — Delete app account

### Screen Description
- **Purpose & Design Intent:** The ultimate deletion control. Permanently deletes the user's account, cloud data, and local data.
- **Key UI Elements:**
  - Reauthentication prompt
  - Recursive cleanup warning
  - "Delete Account" button (#A53535 critical)
  - Retry/Support links for failed states
- **Key Interactions:**
  - Reauthenticate
  - Confirm permanent deletion

### User Flow Context
- **Entry Points:** Privacy settings
- **Available Actions:** Reauthenticate, Delete, Retry, Contact Support
- **Exit Points:** Welcome screen, L10 (Operation receipt)

### Business Requirements
- **Objectives:** Fulfill CCPA/GDPR right-to-be-forgotten mandates safely and transactionally.
- **Constraints:** Shared reauth/recursive cleanup. Immediate local erase. iOS uses deletion tombstone without creating a sender binding, cancels Keychain.
- **BA Requirements:** BA-18 (Privacy/account/data controls)

### Technical Requirements
- **Components:** `LivePrivacyScreen.tsx`, `LiveActivityScreen.tsx`
- **APIs & Data:** `PrivacyPort`, Deletion orchestrator (Cloud Functions), recursive Firestore subcollection cleanup.
- **Validation & Error Handling:** Handle ambiguous acceptance, wrong-account rejection, retry unavailable/allowed. Same-account-only replay.
- **Native Modules:** `BirthdayNative`

### Final Stitch Prompt
> Build the Delete app account screen (L09) utilizing LivePrivacyScreen and PrivacyPort to orchestrate transactional, recursive Firestore cleanup via Cloud Functions, handling reauth, iOS deletion tombstones, and local erase receipts under BA-18.

---

## L10 — Operation receipt

### Screen Description
- **Purpose & Design Intent:** Provides a definitive record of a completed or failed privacy/lifecycle operation.
- **Key UI Elements:**
  - Status icon (Success/Warning/Error)
  - Operation summary text
  - Details on what data remains (e.g., SMS, iCloud)
  - "Done" button
- **Key Interactions:**
  - Read receipt
  - Dismiss

### User Flow Context
- **Entry Points:** L03, L06, L07, L08, L09
- **Available Actions:** Dismiss
- **Exit Points:** Settings, Welcome screen

### Business Requirements
- **Objectives:** Provide clear closure and transparency for security operations.
- **Constraints:** Must clearly state external copies remain (Android SMS, iOS Messages/iCloud/carrier). Complete, partial, remote pending, or failed/retry states.
- **BA Requirements:** BA-18 (Privacy/account/data controls)

### Technical Requirements
- **Components:** `LiveActivityScreen.tsx`
- **APIs & Data:** `PrivacyPort`. Recovery journal with salted equality digests.
- **Validation & Error Handling:** Display accurate state (failed/retry).
- **Native Modules:** None

### Final Stitch Prompt
> Create the Operation receipt screen (L10) using LiveActivityScreen to display complete, partial, or failed states from PrivacyPort, explicitly detailing external copies (SMS/iCloud) that remain, fulfilling BA-18 transparency requirements.

---

## L11 — Account switch blocker

### Screen Description
- **Purpose & Design Intent:** Prevents users from switching accounts while a cleanup or deletion operation is still in progress, avoiding data corruption or state leakage.
- **Key UI Elements:**
  - Blocking overlay/dialog
  - Status message (cleanup in progress, failed)
  - Retry/Cancel buttons
- **Key Interactions:**
  - Retry cleanup
  - Cancel (leaves retained account signed out)

### User Flow Context
- **Entry Points:** Attempting to switch accounts during cleanup
- **Available Actions:** Retry, Cancel
- **Exit Points:** Welcome screen

### Business Requirements
- **Objectives:** Ensure data isolation and prevent corrupted states during account transitions.
- **Constraints:** Chooser remains blocked until cleanup resolves or is explicitly canceled.
- **BA Requirements:** BA-18 (Privacy/account/data controls)

### Technical Requirements
- **Components:** `LiveSetupScreen.tsx`
- **APIs & Data:** `PrivacyPort`, `DeviceLifecyclePort`.
- **Validation & Error Handling:** Handle cleanup failed, retry logic.
- **Native Modules:** `BirthdayNative`

### Final Stitch Prompt
> Generate the Account switch blocker screen (L11) using LiveSetupScreen to prevent account transitions during active cleanup via PrivacyPort, managing retry and cancel states safely to maintain data isolation per BA-18.

---

## W01 — External deletion landing

### Screen Description
- **Purpose & Design Intent:** The web entry point for users requesting account deletion outside the mobile app.
- **Key UI Elements:**
  - Google Sign-In button
  - Support links for lost/disabled accounts
  - Privacy-first no-analytics disclosure
  - Validation error messages
- **Key Interactions:**
  - Authenticate with Google
  - Navigate to support

### User Flow Context
- **Entry Points:** Direct URL (`/delete/`), Support pages
- **Available Actions:** Sign in, Get support
- **Exit Points:** W02 (Deletion request verification and status)

### Business Requirements
- **Objectives:** Provide a compliant, accessible web route for account deletion (GDPR/CCPA).
- **Constraints:** Mobile/desktop responsive. No raw PII in logs. Inter font.
- **BA Requirements:** BA-18 (Privacy/account/data controls)

### Technical Requirements
- **Components:** Web HTML/JS (served via Express/Firebase Hosting)
- **APIs & Data:** Google Web Auth, `backend/hosting/src/app.ts`.
- **Validation & Error Handling:** Validation errors for bad auth.
- **Native Modules:** N/A

### Final Stitch Prompt
> Construct the External deletion landing page (W01) using backend/hosting/src/app.ts with Google Web Auth, ensuring a responsive mobile/desktop design, privacy-first no-analytics disclosure, and strict adherence to BA-18 for web deletions.

---

## W02 — Deletion request verification and status

### Screen Description
- **Purpose & Design Intent:** Displays the real-time status of the deletion request after authentication on the web.
- **Key UI Elements:**
  - Progress indicator
  - Status text (permit drain, cleanup, pending)
  - Retry/Support buttons
  - Acknowledgement of completion policy
- **Key Interactions:**
  - View status
  - Retry failed request

### User Flow Context
- **Entry Points:** W01
- **Available Actions:** Retry, Contact support
- **Exit Points:** W03 (External deletion receipt)

### Business Requirements
- **Objectives:** Keep the user informed during the potentially long-running recursive deletion process.
- **Constraints:** Must accurately reflect Deletion orchestrator states.
- **BA Requirements:** BA-18 (Privacy/account/data controls)

### Technical Requirements
- **Components:** Web HTML/JS
- **APIs & Data:** `backend/hosting/src/deletion-contract.ts`, Deletion orchestrator (Cloud Functions).
- **Validation & Error Handling:** Handle permit drain, pending, and retry/support states.
- **Native Modules:** N/A

### Final Stitch Prompt
> Build the Deletion request verification web page (W02) powered by deletion-contract.ts to visualize Cloud Functions orchestrator status, including permit drain and cleanup progress, providing retry/support actions in line with BA-18.

---

## W03 — External deletion receipt

### Screen Description
- **Purpose & Design Intent:** Provides the final confirmation of deletion on the web, detailing exactly what was removed and what external data may remain.
- **Key UI Elements:**
  - Final status icon (Verified complete, failed, pending stores)
  - Content-free receipt details
  - Disclaimer regarding external copies
- **Key Interactions:**
  - Read receipt
  - Close/Navigate away

### User Flow Context
- **Entry Points:** W02
- **Available Actions:** None (terminal screen)
- **Exit Points:** External sites / Browser close

### Business Requirements
- **Objectives:** Provide undeniable proof of deletion completion and manage expectations regarding out-of-scope data.
- **Constraints:** Must disclose Android SMS Provider/carrier/recipient and iOS Messages/iCloud/carrier/recipient copies remain.
- **BA Requirements:** BA-18 (Privacy/account/data controls)

### Technical Requirements
- **Components:** Web HTML/JS
- **APIs & Data:** `backend/hosting/src/deletion-contract.ts`
- **Validation & Error Handling:** Display accurate final state (failed, pending).
- **Native Modules:** N/A

### Final Stitch Prompt
> Generate the External deletion receipt page (W03) using deletion-contract.ts to display verified complete, failed, or pending states, explicitly disclosing remaining external SMS/iCloud copies in a content-free receipt format meeting BA-18.

---

# Appendix A: Screen-to-Component Cross-Reference

| Screen ID | Screen Title | Primary Component(s) |
|-----------|-------------|---------------------|
| G01 | Secure startup | `NativeAppBoundary.tsx`, `LiveSetupScreen.tsx`, `LiveProjectionState.tsx` |
| G02 | Main shell | `AppRoot.tsx`, `LiveAppShell.tsx` |
| S01 | Welcome and compatibility | `LiveSetupScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S02 | Connect with Google | `LiveSetupScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S03 | Active sender gate | `LiveSetupScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S04 | Contacts disclosure | `LiveSetupScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S05 | Contacts authorization return | `LiveSetupScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S06 | First Contacts sync | `LivePeopleScreen.tsx`, `LiveSetupScreen.tsx` |
| S07 | Choose people | `LivePeopleScreen.tsx`, `LiveSetupScreen.tsx` |
| S08 | Bulk recipient review | `LivePeopleScreen.tsx`, `LiveSetupScreen.tsx` |
| S09 | Repair person | `LivePersonDetailScreen.tsx` |
| S10 | Approve person | `LivePersonDetailScreen.tsx` |
| S11 | Template editor | `LiveMessageScreen.tsx` |
| S12 | Gemini suggestions | `LiveMessageScreen.tsx` |
| S13 | Delivery window | `LivePolicyEditor.tsx`, `LiveAutomationScreen.tsx` |
| S14 | SIM policy | `LiveAutomationScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S15 | Recipient and message review | `LivePersonDetailScreen.tsx`, `LiveMessageScreen.tsx` |
| S16 | Test destination | `LiveAutomationScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S17 | Test review and SMS disclosure | `LiveAutomationScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S18 | Test status | `LiveAutomationScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S19 | Background readiness | `LiveAutomationScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S20 | Final activation review | `LiveAutomationScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| S21 | Activation result | `LiveAutomationScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| H01 | Home | `LiveHomeScreen.tsx` |
| H02 | Upcoming | `LiveHomeScreen.tsx` |
| H03 | Approved message preview | `LiveHomeScreen.tsx`, `LiveMessageScreen.tsx` |
| H04 | Pause automation | `LiveHomeScreen.tsx`, `LiveAutomationScreen.tsx` |
| H05 | Resume readiness | `LiveHomeScreen.tsx`, `LiveAutomationScreen.tsx` |
| H06 | Today decision | `LiveHomeScreen.tsx`, `LiveAutomationScreen.tsx` |
| P01 | People list | `LivePeopleScreen.tsx` |
| P02 | Person detail | `LivePersonDetailScreen.tsx` |
| P03 | Excluded people | `LivePeopleScreen.tsx` |
| P04 | Approval invalidation | `LivePersonDetailScreen.tsx` |
| A01 | Activity | `LiveActivityScreen.tsx` |
| A02 | Activity detail | `LiveActivityScreen.tsx` |
| A03 | Needs your attention | `LiveAttentionScreen.tsx` |
| A04 | Issue repair | `LiveAttentionScreen.tsx` |
| A05 | Diagnostics preview | `LiveDiagnosticsScreen.tsx` |
| A06 | Clear activity | `LiveActivityScreen.tsx` |
| T01 | Settings home | `LiveSettingsScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| T02 | Automation policy | `LivePolicyEditor.tsx`, `LiveAutomationScreen.tsx` |
| T03 | Message | `LiveMessageScreen.tsx` |
| T04 | SIM and charges | `LiveSettingsScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| T05 | Notifications | `LiveSettingsScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| T06 | Google, Contacts, and sender | `LivePrivacyScreen.tsx`, `LivePrivacyInventory.tsx` |
| T07 | Device readiness | `LiveSettingsScreen.tsx`, `LiveAndroidDeviceControls.tsx` |
| T08 | Privacy and data | `LivePrivacyScreen.tsx`, `LivePrivacyInventory.tsx` |
| T09 | Data inventory and retention | `LivePrivacyScreen.tsx`, `LivePrivacyInventory.tsx` |
| T10 | Help, legal, and about | `LiveHelpLegalScreen.tsx` |
| L01 | Sender transfer | `LiveAndroidDeviceControls.tsx` |
| L02 | Transfer approval on old phone | `LiveAndroidDeviceControls.tsx` |
| L03 | Transfer drain and status | `LiveAndroidDeviceControls.tsx` |
| L04 | Retained-account reconnect | `LivePrivacyScreen.tsx`, `LiveSetupScreen.tsx` |
| L05 | Sign out | `LivePrivacyScreen.tsx` |
| L06 | Disconnect Contacts | `LivePrivacyScreen.tsx` |
| L07 | Revoke all Google access | `LivePrivacyScreen.tsx` |
| L08 | Delete local app data | `LivePrivacyScreen.tsx` |
| L09 | Delete app account | `LivePrivacyScreen.tsx` |
| L10 | Operation receipt | `LivePrivacyScreen.tsx`, `LiveActivityScreen.tsx` |
| L11 | Account switch blocker | `LiveSetupScreen.tsx` |
| W01 | External deletion landing | `backend/hosting/src/app.ts` |
| W02 | Deletion request verification | `backend/hosting/src/deletion-contract.ts` |
| W03 | External deletion receipt | `backend/hosting/src/deletion-contract.ts` |

---

# Appendix B: BA Requirements Quick Reference

| Code | Title | Screens |
|------|-------|---------|
| BA-01 | Distribution & eligibility gate | G01, S01, S19, T07 |
| BA-02 | One Google account coordinator | S02, L04, L07, L11, T06 |
| BA-03 | Read-only Google Contacts sync | S04, S05, S06, L06, T06 |
| BA-04 | Contact, birthday & phone normalization | S06, S09 |
| BA-05 | Explicit recipient enrollment | S07, S08, P01, P03 |
| BA-06 | Birthday recurrence & occurrence planner | H02 |
| BA-07 | Built-in message templates | S11, T03 |
| BA-08 | Gemini template assistant | S12, T03 |
| BA-09 | Immutable approval snapshot | S08, S10, S15, H03, P02, P04 |
| BA-10 | Global send window & late-send policy | S13, T02 |
| BA-11 | Native scheduler & reconciliation | S19, S21, T07 |
| BA-12 | Native SMS & SIM gateway | S14, S15, S17, H06, T04 |
| BA-13 | Atomic outbox, cloud fencing & retry | S03, H06, L01, L02, L03, T06 |
| BA-14 | Test SMS & activation review | S16, S17, S18, S20, S21, H05 |
| BA-15 | Home readiness & upcoming view | H01, H02, H04, H05, A03, G02 |
| BA-16 | People & recipient manager | S09, P01, P02, G02 |
| BA-17 | Activity, attention & diagnostics | A01, A02, A03, A04, A05, A06 |
| BA-18 | Privacy, account & data controls | G01, T01, T08, T09, L05–L11, W01–W03 |
| BA-19 | Accessibility, localization & adaptive | G02, T10 |
| BA-20 | Notifications | T05 |
| BA-24 | iOS Companion Edition | All screens with iOS variants |

---

# Appendix C: Application Port Architecture

| Port | Purpose | Screens |
|------|---------|---------|
| `AppProjectionPort` | Bootstrap and global projection state | G01, G02, all screens |
| `AppRoutePort` | Deep-link and native route resolution | G02 |
| `AutomationPort` | Automation/reminder status and controls | H01, H04, H05, S13–S21, T02 |
| `PeoplePort` | Contact list, detail, enrollment | P01–P04, S06–S10 |
| `MessagePort` | Template editing and Gemini suggestions | S11, S12, S15, H03, T03 |
| `ActivityPort` | Activity feed and attention queue | A01–A06 |
| `IdentityContactsPort` | Google auth, contacts sync, sender | S02–S05, T06, L04–L07 |
| `PrivacyPort` | Data controls, deletion, sign-out | T08, T09, L05–L11 |
| `DeviceLifecyclePort` | Transfer, device controls | L01–L03 |
| `NativeActionPort` | Intent execution bridge | All screens via `executeUserIntent` |
| `BirthdayNativePort` | TurboModule interface | All live screens |
| `PublicResourcesPort` | Web-hosted deletion resources | W01–W03 |

---

# Appendix D: Domain Model Index

| Domain Model | Path | Screens |
|-------------|------|---------|
| Account | `src/domain/account/model.ts` | S02, L04–L11 |
| Activity | `src/domain/activity/model.ts` | A01–A06 |
| Approvals | `src/domain/approvals/model.ts` | S10, S15, H03, P02, P04 |
| Automation | `src/domain/automation/model.ts` | H01, H04, H05, S13–S21, T02 |
| Birthdays | `src/domain/birthdays/model.ts` | H02, S06, S09 |
| Contacts | `src/domain/contacts/model.ts` | P01–P04, S06–S10 |
| Device | `src/domain/device/model.ts` | S01, S19, T07 |
| Home | `src/domain/home/model.ts` | H01, H02, H06 |
| Legal | `src/domain/legal/model.ts` | T10 |
| Messages | `src/domain/messages/model.ts` | S11, S12, T03 |
| Navigation | `src/domain/navigation/model.ts` | G02 |
| Privacy | `src/domain/privacy/model.ts` | T08, T09, L05–L11 |
| Readiness | `src/domain/readiness/model.ts` | S19, T07 |
| Setup | `src/domain/setup/model.ts` | S01–S21 |
| Validation | `src/domain/validation/` | S11 (templateDraft), S13 (windowDraft), S09 (ephemeralPhone) |

---

# Appendix E: Contract & Policy Files

| Contract | Path | Purpose | Screens |
|----------|------|---------|---------|
| Message Semantic Policy v2 | `contracts/birthday-message-semantic-policy-v2.json` | AI/message safety policy, rejection categories | S11, S12, T03 |
| Contacts Freshness Policy v1 | `contracts/contacts-freshness-policy-v1.json` | Sync staleness thresholds (7d normal, 30d automation) | S06, P01, A03 |
| Gemini Prompt Policy v2 | `contracts/gemini-prompt-policy-v2.json` | Model, tones, segment caps, privacy instructions | S12 |

---

*Generated from analysis of `PROJECT_ABOUT.md`, `stitch/SCREEN_MANIFEST.md`, `stitch/IMPLEMENTATION_CROSSWALK.json`, and full codebase inspection.*
