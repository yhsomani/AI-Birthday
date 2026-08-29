# WishWell — Unified Product Requirements Document (PRD) + Business Requirements Document (BRD)

**Document Version:** 1.0  
**Status:** Master Specification (Single Source of Truth)  
**Prepared For:** Product, Design, Engineering, QA, Security, and Business Stakeholders  
**Product Name:** WishWell (working title — final branding open)  

> **SOURCE NOTE & TRUTH RULE:** The detailed project/codebase documentation referenced in the request was not supplied in to the full extent; this document reconstructs the product from the exhaustive feature inventory, workflow list, platform rules, and business rules embedded in the instruction set itself. Entries inferred beyond the explicit list are marked **[ASSUMPTION]**. Improvements over the original concept are marked **[IMPROVEMENT]**. Future-only items are marked **[FUTURE]**. Everything else is treated as **existing or required production behavior** unless stated otherwise.

---

## 0. DOCUMENT CONVENTIONS

| Prefix | Meaning |
|---|---|
| **BIZ** | Business Requirement |
| **BRULE** | Business Rule |
| **FR** | Functional Requirement |
| **FEAT** | Feature |
| **UI** | Screen / Page |
| **DATA** | Domain Entity |
| **INT** | Integration |
| **NFR** | Non-Functional Requirement |
| **ACC** | Acceptance Criterion |
| **RISK** | Risk |
| **JOURNEY** | User Journey |

Labels: **[EXISTING]** = in original scope; **[IMPROVED]** = changed for production quality; **[ASSUMPTION]** = inferred; **[FUTURE]** = post-launch opportunity.

---

# SECTION 1 — PRODUCT UNDERSTANDING

## 1.1 Simple Explanation of the Product

WishWell connects to a user's Google account, reads their Google Contacts to find friends and family with birthdays, lets the user pick which people to remember, uses Google Gemini AI to draft a personal birthday text message, requires the user to **approve** each message, and then delivers that message on the person's birthday.

On **Android**, WishWell can automatically send the approved message as a real SMS at the scheduled time — no user action needed. On **iOS**, Apple does not allow apps to send SMS automatically, so WishWell behaves as a smart companion: it reminds the user on the birthday, opens a pre-filled Messages sheet, and the user taps send.

The product is built around one core promise: **never miss a birthday, and never send something that feels robotic.**

## 1.2 Product Name

**WishWell** (working title). **[ASSUMPTION]** — final name pending brand review.

## 1.3 Product Vision

To become the most trusted, privacy-respecting way to maintain personal relationships through timely, genuinely personal birthday messages — where AI assists, but the human always decides.

## 1.4 Product Mission

Help busy people preserve the rituals of friendship and family by making thoughtful birthday communication effortless without making it feel automated.

## 1.5 Product Purpose

Automate the *remembering* and *sending* of birthday wishes while preserving the *human judgment* through an approval-first workflow and platform-honest delivery.

## 1.6 Problem Statement

People want to maintain relationships, but birthdays slip through the cracks. Existing reminders tell you *after* you've missed it. Generic automation tools send robotic messages that damage relationships. Manual solutions require constant effort. There is no product that combines **automatic remembrance + AI-personalized drafting + explicit human approval + real SMS delivery** with respect for privacy and platform rules.

## 1.7 User Problems

1. Forgetting birthdays causes guilt and social friction.
2. Sending generic "HBD" texts feels impersonal and can weaken relationships.
3. Writing a thoughtful message takes time users don't have.
4. Manually tracking birthdays across contacts is error-prone.
5. iPhone and Android behave differently — most tools promise features that don't actually work on iOS.
6. Users are wary of AI sending messages without their control.
7. Users are wary of granting contact access to unknown services.

## 1.8 Business Problems

1. Existing reminder apps have weak engagement after initial setup.
2. AI-generated communication tools face a **trust deficit** — users fear loss of authenticity.
3. Google restricted-scope policies require costly compliance (data deletion, privacy disclosures) that many competitors ignore.
4. SMS automation on Android is technically fragile (background restrictions, OEM killing, dual-SIM complexity) — poor reliability kills retention.
5. iOS limitations are often misrepresented, leading to disappointed users and bad reviews.

## 1.9 Product Value Proposition

**"Set it once. Approve what matters. Never miss a birthday — on either platform."**

WishWell combines:
- One-time contact sync + birthday detection
- AI-drafted, human-approved messages
- Reliable timed delivery on Android
- Honest companion reminders on iOS
- Full privacy transparency and deletion controls

## 1.10 Target Users / Personas (Summary — full detail in Section 3)

| Persona | Role |
|---|---|
| **P1 — Busy Professional** | Primary user |
| **P2 — Relationship Maintainer** | Secondary user |
| **P3 — Privacy-Conscious User** | Secondary user |
| **P4 — Less-Technical User** | Secondary user |

## 1.11 Primary User

**P1 — Busy Professional**: 28–45, works full-time, large contact list (300+), wants to maintain relationships but forgets birthdays. Owns an Android or iPhone. Values time-saving over customization but wants messages to feel personal.

## 1.12 Secondary Users

- **P2 — Relationship Maintainer**: actively manages friendships, wants high control over every message, willing to invest more time in review.
- **P3 — Privacy-Conscious User**: hesitant to share contacts, needs strong privacy assurances, deletion, and transparency.
- **P4 — Less-Technical User**: older or non-technical, needs simple onboarding, large text, forgiving recovery, and may use iOS companion.

## 1.13 User Jobs-to-be-Done

> **"When someone's birthday is approaching, I want a thoughtful message to be sent (or queued for me to send) on time, without me having to remember the date, write from scratch, or worry the message sounds fake."**

Sub-Jobs:
1. Automatically discover whose birthdays are coming.
2. Choose which contacts deserve automated wishes.
3. Review and personalize AI-drafted messages.
4. Trust that the message will be sent on time (Android) or be prompted to send (iOS).
5. Verify what happened after the birthday.
6. Fix problems when something goes wrong.
7. Control, pause, and delete everything.

## 1.14 Core Use Cases

| ID | Use Case |
|---|---|
| UC-01 | Connect Google account and sync contacts |
| UC-02 | Detect and normalize birthdays from contacts |
| UC-03 | Select recipients for automated birthday wishes |
| UC-04 | Generate personalized message with Gemini AI |
| UC-05 | Review, edit, and approve each message |
| UC-06 | Schedule message delivery on birthday |
| UC-07 | Send SMS automatically (Android) |
| UC-08 | Remind and hand off to Messages sheet (iOS) |
| UC-09 | Confirm delivery and update activity log |
| UC-10 | Investigate failures and retry |
| UC-11 | Pause/resume automation globally or per recipient |
| UC-12 | Transfer sender role between devices |
| UC-13 | Reconnect revoked Google account |
| UC-14 | Delete account and all data (app and web) |
| UC-15 | Disconnect contacts without deleting account |

## 1.15 Product Principles

1. **Human-in-the-loop** — No message is ever sent (or queued for send) without explicit user approval.
2. **Platform honesty** — iOS is never claimed to auto-send; Android capabilities are described accurately.
3. **Privacy by design** — Minimal data collection, clear retention, easy deletion.
4. **Reliability over features** — A missed birthday is a broken promise; reliability is prioritized over novelty.
5. **Transparency** — Every action is visible in the activity log and safety ledger.
6. **Forgiving UX** — Failure states always offer recovery paths.
7. **Accessibility first** — Not an afterthought.
8. **Silent is safe** — Notifications are informative, not noisy.

## 1.16 Product Boundaries

**In-Scope:**
- Google Authentication (single active account)
- Google Contacts read-only sync
- Birthday detection & normalization
- Recipient selection & management
- AI message generation (Gemini) with templates
- Human approval workflow
- Scheduled delivery on birthday
- Android native SMS with SIM selection & sender device
- iOS companion with MessageUI handoff & local reminders
- Activity log & safety ledger
- Sender transfer between Android devices
- Account lifecycle (sign-in, reconnection, sign-out, deletion)
- Web-based external data deletion
- Privacy disclosures & data inventory

**Out-of-Scope:**
- Non-birthday messages (anniversaries, general reminders) **[FUTURE]**
- Sending via WhatsApp, Telegram, or other messaging apps **[FUTURE]**
- Automatic sending on iOS (impossible by platform design)
- Writing to Google Contacts (read-only only)
- Multiple simultaneous Google accounts (one active at a time)
- Social media posting
- Marketing or bulk messaging
- Email delivery
- Calling

## 1.17 Android vs iOS Product Positioning

| Capability | Android | iOS |
|---|---|---|
| Automatic SMS sending | ✅ Yes (native SmsManager) | ❌ No — prohibited by Apple |
| User-required send action | Not required after approval | Yes — MessageUI sheet, user taps send |
| Delivery verification | Yes — via SMS content provider + safety ledger | No — MessageUI doesn't report final send state; mark as "handed off" |
| Background scheduling | WorkManager + AlarmManager | Local notifications only |
| SIM selection | Yes | N/A |
| Sender device role | Android device can be "sender" | iOS is always "companion" |
| Reminder behavior | Optional | Primary mechanism |
| Message prefill | N/A (direct send) | Yes — approved message prefilled in Messages |

## 1.18 Web/External Deletion Experience

A hosted web page (required by Google API policy for restricted scopes) where any user can request full deletion of their WishWell data without installing the app. Confirms via Google identity, orchestrates backend deletion, and provides a receipt.

## 1.19 Core Success Criteria

| Metric | Target |
|---|---|
| Setup completion rate | ≥ 70% of users who sign in complete automation setup |
| Approval rate of AI drafts | ≥ 60% approved with ≤ 20% major edits |
| Android delivery success | ≥ 98% of scheduled messages delivered within send window |
| iOS reminder-to-send conversion | ≥ 60% of reminders result in send within 24 hours |
| 30-day retention | ≥ 40% |
| Data deletion request completion | 100% within 48 hours |
| Crash-free sessions | ≥ 99.9% |

---

## 2.0 BUSINESS REQUIREMENTS

### 2.1 Business Objectives

| ID | Objective | Why It Matters | Product Capability | Expected User Behavior | Success Metric |
|---|---|---|---|---|---|
| **BIZ-001** | Build trust with AI-assisted communication | AI sending tools face adoption resistance; trust is the moat | Human approval before any send; full edit capability | Users approve messages without anxiety; approval rate high | ≥ 60% approval rate; ≤ 5% automation opt-out after first send |
| **BIZ-002** | Deliver reliable birthday sends | Reliability is the core promise; one failure destroys trust | Android native SMS + reconciliation + retry + safety ledger | Users keep automation on after first successful send | ≥ 98% Android delivery success; < 2% missed due to app failure |
| **BIZ-003** | Provide honest iOS experience | Misleading iOS users causes churn and bad reviews | Companion model with transparent framing | iOS users accept manual send; no "where's my auto-send?" complaints | iOS App Store rating ≥ 4.5; support ticket rate < 5% of iOS users |
| **BIZ-004** | Meet Google restricted-scope compliance | Legal/business risk; required for Play Store listing | Web deletion landing, privacy disclosures, minimal scopes | Users can delete data easily; Google verification passes | 100% deletion requests completed; Google policy compliance |
| **BIZ-005** | Drive activation during first session | Activation is the leading predictor of retention | Streamlined onboarding: sign-in → contacts → recipients → one draft → schedule → activate | New users complete setup in one sitting | ≥ 70% setup completion; median setup < 10 minutes |
| **BIZ-006** | Reduce support load via self-serve recovery | Operational cost and retention | Diagnostics, activity log, reconnection flows, retry | Users fix issues themselves | < 10% of users contact support in first 30 days |
| **BIZ-007** | Establish premium opportunity without degrading MVP | Future revenue without artificial limitations | Clean MVP; free core sending | N/A (future) | **[FUTURE]** |

### 2.2 Business Goals

1. **G1:** Launch a production-grade MVP that works reliably on Android and honestly on iOS.
2. **G2:** Achieve ≥ 10,000 active installs with ≥ 40% 30-day retention in first 6 months.
3. **G3:** Pass Google restricted-scope verification and maintain Play Store / App Store compliance.
4. **G4:** Establish a measurable trust signal (approval rate, minimal opt-out) to validate the AI-human loop.
5. **G5:** Build a scalable deletion/privacy infrastructure that supports future expansions.

### 2.3 Expected User Outcomes

- User knows exactly which contacts will be sent messages and when.
- User can review and edit every AI message before it is usable.
- User receives confirmation (or transparent handoff) after every birthday.
- User can pause, transfer, or delete everything without friction.

### 2.4 Product KPIs

| KPI | Definition | Target |
|---|---|---|
| Activation Rate | % of sign-ups who complete onboarding and activate ≥ 1 recipient | ≥ 70% |
| Recipient Enrollment | Median # of recipients per activated user | ≥ 5 |
| Message Approval Rate | % of AI drafts approved (with or without edits) | ≥ 60% |
| Android Send Success | % of scheduled messages actually sent by carrier within window | ≥ 98% |
| iOS Handoff Completion | % of reminders that lead to Messages sheet opened | ≥ 70% |
| 7-Day / 30-Day Retention | % returning | ≥ 60% / ≥ 40% |
| Failure Recovery Rate | % of failed sends recovered via retry or user action | ≥ 90% |
| Deletion Completion Rate | % of deletion requests completed ≤ 48h | 100% |
| Support Ticket Rate | Tickets / 1000 active users / month | < 10 |

### 2.5 Activation Metrics

- Onboarding started → Google sign-in completed → contacts permission granted → contacts synced → ≥ 1 recipient selected → ≥ 1 message approved → automation toggled on.
- Funnel drop-off at each step tracked; target ≤ 15% drop per step after sign-in.

### 2.6 Setup Completion Metrics

- % who complete contacts sync within 5 minutes of sign-in.
- % who select at least 1 recipient.
- % who approve at least 1 message.

### 2.7 Automation Activation Metrics

- % of activated users with ≥ 1 "armed" recipient.
- Time from sign-in to activation.

### 2.8 Successful Delivery Metrics

- Android: SMS sent confirmation rate; parts per message; send window adherence.
- iOS: Messages sheet invoked rate.

### 2.9 Engagement Metrics

- Weekly active users who review upcoming birthdays.
- % who edit AI drafts.
- % who use activity log.

### 2.10 Retention Metrics

- 7-day, 30-day, 90-day retention.
- Feature retention: % still active after first successful send.

### 2.11 Failure/Recovery Metrics

- Missed birthday rate.
- Retry success rate.
- Reconnection success rate after token revocation.

### 2.12 Privacy/Security Metrics

- Deletion request completion time.
- Token revocation propagation time.
- Data inventory audit completeness.

### 2.13 Support/Operational Metrics

- Median time-to-resolution.
- Self-serve resolution rate.
- Crash-free sessions.
- Notification delivery success.

### 2.14 Monetization

**Current MVP:** **No artificial monetization.** The core birthday sending must remain free to honor the product promise and build trust.

**[FUTURE]** Opportunities (post-launch, non-intrusive):
- **Premium AI:** unlimited Gemini personalization for power users; free tier gets basic templates.
- **Relationship insights:** additional occasions (anniversaries) with enhanced AI.
- **Multi-channel sending:** WhatsApp/Telegram via approved integrations.
- **Family plan:** shared recipient lists for families.
- **Privacy Guarantee tier:** enhanced data lockbox and early deletion processing.

None of these may degrade the free core experience.

### 2.15 Long-Term Business Opportunities

- Platform for **relationship-maintenance automation** beyond birthdays.
- Trusted AI-human communication framework licensable to other verticals.
- Privacy-first reputation attracts premium, privacy-sensitive users.

### 2.16 Product Growth Opportunities

- Referral via "birthday sent" shareable (but privacy-safe) moments.
- Import from phone contact groups.
- Google Play / App Store featured story on privacy + AI.

### 2.17 Operational Efficiency Opportunities

- Self-serve diagnostics reduces support cost.
- Reconciliation and safety ledger reduce debug time.
- Web deletion reduces manual deletion tickets.

---

# SECTION 3 — USER PERSONAS & USER JOURNEYS

## 3.1 Personas

### P1 — Busy Professional (Primary)

| Attribute | Detail |
|---|---|
| Age / Life | 28–45, full-time job, moderate-to-large contact list |
| Goals | "Remember every important birthday without thinking about it." |
| Needs | Fast setup, trustworthy automation, minimal daily attention |
| Pain Points | Forgets birthdays, no time to write messages, dislikes generic texts |
| Technical Familiarity | Medium-high; uses apps daily |
| Trust Concerns | Worried AI will send something embarrassing or inappropriate |
| Privacy Concerns | Moderate; accepts Google sign-in but wants data deletion |
| Main Use Cases | UC-01, UC-03, UC-04, UC-05, UC-06, UC-07/08, UC-11 |
| Success Definition | Birthday message sent on time with no manual intervention (Android) or one tap (iOS) |

### P2 — Relationship Maintainer (Secondary)

| Attribute | Detail |
|---|---|
| Age / Life | 25–50, deliberately manages friendships/family |
| Goals | "Send messages that feel like *me*, not a robot." |
| Needs | Full edit control, message history, per-recipient customization |
| Pain Points | Wants more control than basic automation offers |
| Technical Familiarity | Medium |
| Trust Concerns | High concern about AI tone; wants to review everything |
| Privacy Concerns | Medium |
| Main Use Cases | UC-04, UC-05, UC-14, UC-15 |
| Success Definition | Approved message reflects personal voice; full audit trail |

### P3 — Privacy-Conscious User (Secondary)

| Attribute | Detail |
|---|---|
| Age / Life | 30–60, wary of cloud services |
| Goals | "Never lose control of my data." |
| Needs | Clear data inventory, easy deletion, minimal PII stored |
| Pain Points | Distrusts apps with contact access; fears hidden data sharing |
| Technical Familiarity | Medium |
| Trust Concerns | High concern about AI tone; wants to review everything |
| Privacy Concerns | Very high |
| Main Use Cases | UC-13, UC-14, UC-15 |
| Success Definition | Can verify what is stored, revoke access, delete everything instantly |

### P4 — Less-Technical User (Secondary)

| Attribute | Detail |
|---|---|
| Age / Life | 50–75, uses smartphone for basics |
| Goals | "Help me remember birthdays without confusing menus." |
| Needs | Large text, simple flows, gentle recovery |
| Pain Points | Overwhelmed by settings, fears breaking things |
| Technical Familiarity | Low |
| Trust Concerns | Anxious about granting permissions |
| Privacy Concerns | Low awareness but wants safety |
| Main Use Cases | UC-01, UC-02, UC-03, UC-08 |
| Success Definition | Completes setup with minimal decisions and receives clear birthday reminders |

## 3.2 User Journeys

### JOURNEY-01: First Launch & Onboarding
- **Trigger:** User installs and opens WishWell for the first time.
- **Preconditions:** App installed; no account; network available.
- **Steps:** Welcome → Value proposition (3 bullets) → Google sign-in → Contacts permission request → Initial sync → Auto-detect birthdays → Recipient selection → One AI draft preview → Approve → Schedule confirmation → Automation toggle → Success screen.
- **Decisions:** User may skip contacts permission (→ limited mode); may decline Google sign-in (→ explanation + exit); may skip recipient selection (→ done later).
- **Alternate Paths:** User signs in but denies contacts → app shows "enable contacts later" and proceeds to empty state.
- **Failure Paths:** Network fails during sync → error with retry. Google token invalid → re-authenticate.
- **Recovery:** All failures offer "Retry" and "Skip for now."
- **Completion State:** Automation armed with ≥ 1 recipient and ≥ 1 approved message; or graceful deferred state.

### JOURNEY-02: Google Authentication
- **Trigger:** User taps "Sign in with Google."
- **Preconditions:** Network; Google Play Services (Android) / ASWebAuthenticationSession (iOS).
- **Steps:** OAuth request with scopes `userinfo.email`, `userinfo.profile`, `contacts.readonly` → consent screen → token returned → app stores refresh token securely → fetch profile → fetch contacts.
- **Decisions:** User may deny consent.
- **Alternate Paths:** Web fallback if Play Services unavailable.
- **Failure Paths:** Token exchange fails → error + retry. User denies → return to sign-in.
- **Recovery:** Re-prompt with explanation; session persists.
- **Completion State:** Authenticated account; token stored encrypted.

### JOURNEY-03: Contacts Authorization
- **Trigger:** After Google sign-in, user is prompted to grant contacts access.
- **Preconditions:** Google session valid.
- **Steps:** Explanation of read-only need → permission request (OS + Google scope) → on grant → sync contacts.
- **Decisions:** Deny → limited mode (no recipients).
- **Alternate Paths:** Grant later via Settings.
- **Failure Paths:** Sync error → partial sync with warning.
- **Recovery:** Retry sync.
- **Completion State:** Contacts disconnected; account remains.

### JOURNEY-04: Initial Synchronization
- **Trigger:** Contacts permission granted.
- **Preconditions:** Valid token.
- **Steps:** Fetch all Google Contacts with birthdays → normalize dates → normalize phone numbers → de-duplicate → store local snapshot.
- **Decisions:** None.
- **Alternate Paths:** Incremental sync if prior data exists.
- **Failure Paths:** Network error → partial; token expired → re-auth.
- **Recovery:** Auto-retry with backoff; user can manually retry.
- **Completion State:** Contact list with normalized birthdays and phones displayed.

### JOURNEY-05: Recipient Selection
- **Trigger:** User views "People" / birthday list.
- **Preconditions:** Contacts synced.
- **Steps:** Browse contacts with birthdays → toggle recipients on/off → see phone validity → save.
- **Decisions:** Include/exclude per contact.
- **Alternate Paths:** Select all valid.
- **Failure Paths:** Contact has no phone → marked ineligible; contact has duplicate phone → pick canonical.
- **Recovery:** User edits phone manually (does not write back to Google).
- **Completion State:** Recipient set saved.

### JOURNEY-06: Recipient Approval (Message)
- **Trigger:** User opens a recipient's detail or batch review.
- **Preconditions:** Recipient selected; Gemini configured.
- **Steps:** Gemini generates draft based on name, age, optional context → user reviews → edit → approve → snapshot saved.
- **Decisions:** Approve, edit, reject, regenerate, or use template.
- **Alternate Paths:** Gemini fails → fallback template.
- **Failure Paths:** Draft inappropriate → user rejects; new draft generated.
- **Recovery:** Regenerate; manual template override.
- **Completion State:** Approval snapshot stored; message armed for send.

### JOURNEY-07: Message Creation & Gemini Assistance
- **Trigger:** From recipient detail, user taps "Draft message."
- **Preconditions:** Recipient has valid phone and birthday.
- **Steps:** Select tone → optional relationship context → Gemini generates → draft shown → edit.
- **Decisions:** Tone, length, personalization.
- **Alternate Paths:** Use preset template without AI.
- **Failure Paths:** Gemini timeout → fallback template.
- **Recovery:** Retry AI.
- **Completion State:** Draft ready for approval.

### JOURNEY-08: Scheduling
- **Trigger:** After approval, user sets or confirms schedule.
- **Preconditions:** Approved message exists.
- **Steps:** Confirm birthday date → set preferred send time (default 10:00 local) → set send window (default 9:00–21:00) → set late-send policy (default: send within window, else mark missed) → save.
- **Decisions:** Time, window, late policy.
- **Alternate Paths:** Use global default.
- **Failure Paths:** Invalid time → validation.
- **Recovery:** N/A.
- **Completion State:** Schedule stored.

### JOURNEY-09: Device Readiness
- **Trigger:** Android user activates automation.
- **Preconditions:** ≥ 1 armed recipient.
- **Steps:** Check SEND_SMS permission → request if missing → check battery optimization exemption → check SIM present → offer test SMS to self or friend → run test.
- **Decisions:** Grant permission; run test now/later.
- **Alternate Paths:** Skip test.
- **Failure Paths:** Permission denied → explain and route to Settings. SIM missing → error. Test fails → diagnostics.
- **Recovery:** Settings handoff; retry test.
- **Completion State:** Device marked ready.

### JOURNEY-10: SMS Testing
- **Trigger:** User taps "Send test SMS."
- **Preconditions:** Permission granted; SIM available.
- **Steps:** Choose test recipient (self default or custom number) → send test → verify receipt → log result.
- **Decisions:** Test target.
- **Alternate Paths:** Skip.
- **Failure Paths:** Send fails → error code; no SIM → error.
- **Recovery:** Retry; diagnostics.
- **Completion State:** Test logged in safety ledger.

### JOURNEY-11: Automation Activation
- **Trigger:** User toggles automation ON.
- **Preconditions:** ≥ 1 armed recipient; device ready (Android).
- **Steps:** Confirm toggle → final checks → activation.
- **Decisions:** None.
- **Alternate Paths:** Activation with warnings.
- **Failure Paths:** Missing permission → blocked with guidance.
- **Recovery:** Re-run readiness.
- **Completion State:** Automation active; background scheduler armed.

### JOURNEY-12: Daily/Recurring Use
- **Trigger:** App opened on a normal day.
- **Preconditions:** Automation active.
- **Steps:** View dashboard → upcoming birthdays (14-day window) → activity summary → optional message edits.
- **Decisions:** Edit upcoming messages.
- **Alternate Paths:** None.
- **Failure Paths:** Stale data → refresh.
- **Recovery:** Pull-to-refresh; auto-sync on open.
- **Completion State:** User informed and in control.

### JOURNEY-13: Upcoming Birthday Management
- **Trigger:** User taps "Upcoming."
- **Preconditions:** Contacts synced.
- **Steps:** View list sorted by date → select birthday → view message and status → edit if within edit window.
- **Decisions:** Edit message, cancel send, reschedule.
- **Alternate Paths:** Bulk actions.
- **Failure Paths:** Birthday changed in Google Contacts → invalidation.
- **Recovery:** Re-approval flow.
- **Completion State:** User confidence.

### JOURNEY-14: People Management
- **Trigger:** User taps "People."
- **Preconditions:** Contacts synced.
- **Steps:** Browse contacts with birthdays → search/filter → toggle recipient status → edit phone (local override) → view status.
- **Decisions:** Include/exclude; override phone.
- **Alternate Paths:** Manual add (if contact missing).
- **Failure Paths:** Phone invalid → validation.
- **Recovery:** Fix phone.
- **Completion State:** Updated recipient list.

### JOURNEY-15: Activity Investigation
- **Trigger:** User taps "Activity."
- **Preconditions:** Past scheduled events.
- **Steps:** View chronological log → tap event → see details: scheduled time, actual send time, status, parts, SIM, retries, error.
- **Decisions:** Retry missed send (if within grace), dismiss.
- **Alternate Paths:** Filter by status.
- **Failure Paths:** Log missing entry → show uncertainty.
- **Recovery:** Reconciliation attempt.
- **Completion State:** User understands what happened.

### JOURNEY-16: Problem Resolution
- **Trigger:** User receives failure notification or sees error in Activity.
- **Preconditions:** Failure recorded.
- **Steps:** Open diagnostics → view error ID → suggested fixes → apply (grant permission, enable battery exemption, reconnect Google).
- **Decisions:** Apply fix.
- **Alternate Paths:** Contact support.
- **Failure Paths:** Fix fails → escalation.
- **Recovery:** Support handoff with anonymized log.
- **Completion State:** Issue resolved or escalated.

### JOURNEY-17: Pausing/Resuming Automation
- **Trigger:** User toggles automation off/on.
- **Preconditions:** Automation exists.
- **Steps:** Toggle → confirmation → state persisted.
- **Decisions:** Pause all or per-recipient.
- **Alternate Paths:** Per-recipient pause.
- **Failure Paths:** N/A.
- **Recovery:** N/A.
- **Completion State:** Automation paused; scheduled sends halted (Android) / reminders halted (iOS).

### JOURNEY-18: Sender Transfer
- **Trigger:** User signs in on a new Android device while old sender exists.
- **Preconditions:** Same Google account; old sender offline or accessible.
- **Steps:** New device detects existing sender → shows "Transfer sender role" → confirm → cloud flag updated → new device becomes sender → old device demoted.
- **Decisions:** Transfer now or keep old.
- **Alternate Paths:** Old device unreachable → force transfer with 24h fencing delay.
- **Failure Paths:** Sync conflict → resolution.
- **Recovery:** Safety ledger ensures no double-send.
- **Completion State:** Exactly one active sender.

### JOURNEY-19: Account Reconnection
- **Trigger:** Google token revoked/expired; user opens app.
- **Preconditions:** Previously signed in.
- **Steps:** App detects invalid token → explains → re-auth → restore.
- **Decisions:** Re-authenticate or sign out.
- **Alternate Paths:** Sign out.
- **Failure Paths:** Re-auth fails.
- **Recovery:** Retry; sign out with data retention choice.
- **Completion State:** Session restored or clean signed-out state.

### JOURNEY-20: Sign-Out
- **Trigger:** User taps Sign out.
- **Preconditions:** Signed in.
- **Steps:** Confirm → clear local sensitive data → revoke app tokens → return to welcome.
- **Decisions:** Keep or delete local data.
- **Alternate Paths:** Keep local data (offline).
- **Failure Paths:** Cloud cleanup fails → inform and retry.
- **Recovery:** Retry cleanup.
- **Completion State:** Signed out; local data per user choice.

### JOURNEY-21: Contacts Disconnection
- **Trigger:** User taps "Disconnect contacts."
- **Preconditions:** Signed in with contacts.
- **Steps:** Confirm → revoke contacts scope → delete local contact snapshot → enter limited mode.
- **Decisions:** None.
- **Alternate Paths:** N/A.
- **Failure Paths:** Cloud sync pending → block until settled.
- **Recovery:** N/A.
- **Completion State:** Contacts disconnected; account remains.

### JOURNEY-22: Google Access Revocation
- **Trigger:** User revokes WishWell access in Google account settings.
- **Preconditions:** App installed.
- **Steps:** User revokes externally → app detects on next API call → enters disconnected state → prompt reconnection.
- **Decisions:** Reconnect or sign out.
- **Alternate Paths:** App not opened for days; discovery delayed.
- **Failure Paths:** N/A.
- **Recovery:** Re-auth.
- **Completion State:** App state honest about revoked access.

### JOURNEY-23: Local-Data Deletion
- **Trigger:** User taps "Delete local data."
- **Preconditions:** Signed in or out.
- **Steps:** Confirm → wipe encrypted local storage → restart.
- **Decisions:** None.
- **Alternate Paths:** N/A.
- **Failure Paths:** Deletion interrupted → retry.
- **Recovery:** Retry.
- **Completion State:** No local data remains.

### JOURNEY-24: Account Deletion
- **Trigger:** User taps "Delete account."
- **Preconditions:** Signed in.
- **Steps:** Explain consequences → confirm → request backend deletion → backend wipes Firebase, revokes Google refresh token, deletes cloud data → local wipe → receipt.
- **Decisions:** Final confirmation with typed phrase.
- **Alternate Paths:** Delete local only.
- **Failure Paths:** Backend failure → queued deletion with status.
- **Recovery:** Deletion retried; status visible.
- **Completion State:** Account fully deleted; receipt shown.

### JOURNEY-25: External/Web Deletion
- **Trigger:** User visits deletion URL (from policy page or Google).
- **Preconditions:** Browser; user knows email.
- **Steps:** Landing explains → user enters Google-authenticated email → verify → confirm deletion → backend orchestrates → confirmation receipt.
- **Decisions:** None.
- **Alternate Paths:** User not Google-authenticated → still allow manual request with verification.
- **Failure Paths:** Backend error.
- **Recovery:** Retry; status page.
- **Completion State:** Data deleted; receipt.

### JOURNEY-26: Recovery After Failures
- **Trigger:** Any failure state.
- **Preconditions:** Failure detected.
- **Steps:** Detect → surface actionable error → recommend fix → apply → verify.
- **Decisions:** Retry, fix, or escalate.
- **Alternate Paths:** Skip and continue degraded.
- **Failure Paths:** Repeated failure → escalation.
- **Recovery:** Support.
- **Completion State:** Resolved or escalated.

---

# SECTION 4 — COMPLETE FEATURE INVENTORY

Priorities: **P0** = Critical for operation; **P1** = Strong production launch; **P2** = Enhancement; **P3/Future** = Post-launch.

| FEAT ID | Feature | Description | Problem Solved | Business Objective | Users | Priority | Class |
|---|---|---|---|---|---|---|---|
| FEAT-001 | Google Authentication | Sign in with Google (OAuth 2.0) using restricted scopes | Secure, familiar sign-in; access to contacts | BIZ-004 | All | P0 | Must |
| FEAT-002 | Contacts Sync | Read-only Google Contacts import with birthday extraction | Automatic discovery of birthdays | BIZ-005 | All | P0 | Must |
| FEAT-003 | Birthday Detection & Normalization | Extract birthdays from contacts, handle formats, Feb 29 | Accurate scheduling | BIZ-002 | All | P0 | Must |
| FEAT-004 | Phone Normalization | Canonicalize phone numbers (E.164); validate SMS eligibility | Prevent failed sends | BIZ-002 | All | P0 | Must |
| FEAT-005 | Recipient Selection | Toggle contacts as birthday recipients | User control over who gets messages | BIZ-005 | All | P0 | Must |
| FEAT-006 | People Management | Browse/search contacts, override phone, view status | Maintain recipient list | BIZ-006 | P1,P2 | P1 | Must |
| FEAT-007 | Message Templates | Predefined templates with variables | Fast, non-AI fallback | BIZ-001 | P2,P4 | P1 | Must |
| FEAT-008 | Gemini AI Drafting | AI-generated personalized birthday messages | Personal, thoughtful messages | BIZ-001 | P1,P2 | P1 | Must |
| FEAT-009 | Message Editor | Edit AI drafts and templates | Human control | BIZ-001 | All | P0 | Must |
| FEAT-010 | Approval System | Explicit per-message approval before scheduling | Trust, safety | BIZ-001 | All | P0 | Must |
| FEAT-011 | Approval Snapshot | Immutable record of approved message data | Auditability, invalidation handling | BIZ-001 | All | P0 | Must |
| FEAT-012 | Approval Invalidation | Detect data changes post-approval; require re-approval | Prevent wrong sends | BIZ-001 | All | P0 | Must |
| FEAT-013 | Scheduling | Per-recipient send time + window + late policy | Timely delivery | BIZ-002 | All | P0 | Must |
| FEAT-014 | Send Windows | Configurable delivery window | Reliability expectations | BIZ-002 | All | P1 | Must |
| FEAT-015 | Late-Send Behavior | Grace period handling | Handle app/device downtime | BIZ-002 | All | P1 | Must |
| FEAT-016 | Android Native SMS | Send SMS via SmsManager with SEND_SMS permission | Automatic delivery | BIZ-002 | P1,P2 (Android) | P0 | Must |
| FEAT-017 | SIM Selection | Dual-SIM picker for sending | Correct SIM for user plans | BIZ-002 | Android | P1 | Should |
| FEAT-018 | SMS Parts/Cost Handling | Track multi-part SMS and warn | Cost transparency | BIZ-002 | Android | P2 | Should |
| FEAT-019 | SMS Testing | Test message to self before activation | Confidence | BIZ-005 | Android | P1 | Must |
| FEAT-020 | Background Execution | WorkManager/AlarmManager scheduling | Deliver when app closed | BIZ-002 | Android | P0 | Must |
| FEAT-021 | Automation Activation | Master and per-recipient toggles | Control | BIZ-005 | All | P0 | Must |
| FEAT-022 | Upcoming Birthdays | 14-day dashboard of upcoming events | Preview and control | BIZ-002 | All | P1 | Must |
| FEAT-023 | Activity Log | Chronological audit of all sends and attempts | Transparency, debugging | BIZ-006 | All | P0 | Must |
| FEAT-024 | Diagnostics | Self-serve issue detection and fixes | Reduce support load | BIZ-006 | All | P1 | Must |
| FEAT-025 | Notifications | Reminders, successes, failures | User feedback | BIZ-002 | All | P1 | Must |
| FEAT-026 | Privacy Controls | Data inventory, retention, deletion options | Trust, compliance | BIZ-004 | P3 | P0 | Must |
| FEAT-027 | Account Lifecycle | Sign-out, reconnection, deletion | User control | BIZ-004 | All | P0 | Must |
| FEAT-028 | Device Transfer | Sender role transfer between Android devices | Device replacement | BIZ-002 | Android | P1 | Should |
| FEAT-029 | Sender Fencing | Single active sender enforcement | Prevent duplicates | BIZ-002 | Android | P0 | Must |
| FEAT-030 | Safety Ledger | Append-only SMS attempt log | Reconciliation | BIZ-002 | Android | P0 | Must |
| FEAT-031 | Reconciliation | Compare ledger vs carrier records | Delivery truth | BIZ-002 | Android | P1 | Must |
| FEAT-032 | Account Deletion (In-App) | Full user data removal from app | Privacy | BIZ-004 | All | P0 | Must |
| FEAT-033 | Web Deletion Landing | External data deletion without app | Google policy | BIZ-004 | All | P0 | Must |
| FEAT-034 | Contacts Disconnection | Revoke contacts without deleting account | Granular control | BIZ-004 | P3 | P1 | Should |
| FEAT-035 | Google Access Revocation Recovery | Detect and recover from external revocation | Honest state | BIZ-006 | All | P1 | Must |
| FEAT-036 | Local-Data Deletion | Wipe local encrypted data | Privacy | BIZ-004 | All | P1 | Should |
| FEAT-037 | iOS Companion Model | Local reminders + MessageUI handoff | Honest iOS experience | BIZ-003 | iOS | P0 | Must |
| FEAT-038 | iOS MessageUI Handoff | Prefill approved message in Messages sheet | iOS delivery | BIZ-003 | iOS | P0 | Must |
| FEAT-039 | iOS Local Reminders | Birthday reminders via UNUserNotificationCenter | iOS delivery | BIZ-003 | iOS | P0 | Must |
| FEAT-040 | Notification Preferences | Granular notification controls | User preference | BIZ-003 | All | P2 | Should |
| FEAT-041 | Message Template Restrictions | Prevent unsafe template content | Safety | BIZ-001 | All | P1 | Must |
| FEAT-042 | Gemini Privacy Boundary | Restrict PII sent to AI | Privacy | BIZ-004 | All | P0 | Must |
| FEAT-043 | Offline Mode | Degraded but usable offline | Resilience | BIZ-006 | All | P2 | Should |
| FEAT-044 | Support Entry Points | In-app diagnostics export and support | Operations | BIZ-006 | All | P1 | Should |

---

# SECTION 5 — COMPLETE SCREEN & PAGE REQUIREMENTS

## Screen Inventory

### UI-001 Splash / Loading
- **Purpose:** Cold-start initialization; auth state check.
- **Entry:** App launch.
- **Exit:** Welcome (if signed out) or Dashboard (if signed in).
- **States:** Loading, error (network/auth) with retry.
- **Android/iOS:** Native splash.

### UI-002 Welcome / Value Proposition
- **Purpose:** Explain core promise; CTA to sign in.
- **Main CTA:** "Continue with Google."
- **Secondary:** Privacy policy, Terms.
- **Accessibility:** Dynamic text, contrast AA, screen-reader friendly.

### UI-003 Google Authentication Consent
- **Purpose:** External OAuth consent (system-level).
- **Behavior:** ASWebAuthenticationSession (iOS) / Google Play Services (Android) / web fallback.

### UI-004 Contacts Permission Explanation
- **Purpose:** Explain WHY contacts are needed (read-only, only birthdays).
- **Main CTA:** "Grant Contacts Access."
- **Secondary:** "Not now."
- **States:** Granted → sync; Denied → limited mode; Partial → warning.

### UI-005 Initial Sync
- **Purpose:** Show progress of first contact sync.
- **States:** Loading, success count, partial with warning, error with retry.

### UI-006 Dashboard / Home
- **Purpose:** Overview of automation status, upcoming birthdays, recent activity.
- **Sections:** Automation toggle card, Upcoming (next 3), Recent activity preview, Device status.
- **Main CTA:** "View upcoming."
- **Exit:** Upcoming, People, Activity, Settings.

### UI-007 Upcoming Birthdays
- **Purpose:** List of upcoming birthdays in next N days.
- **Data:** Name, birthday date, message status (approved/draft/needs action), send state.
- **Actions:** Tap to open detail; edit message; cancel send.
- **States:** Empty (no birthdays — prompt add), loading, error.

### UI-008 People
- **Purpose:** Full list of contacts with birthdays.
- **Data:** Name, phone validity, recipient toggle, approval status.
- **Actions:** Search, filter, toggle recipient, open detail.
- **States:** Empty, no permission (enable CTA), loading, error.

### UI-010 Message Composer
- **Purpose:** Create/edit message via Gemini or template.
- **Inputs:** Tone selector, relationship context (optional), template picker.
- **Main CTA:** "Generate with AI" / "Use Template."
- **Secondary:** Edit text manually.
- **States:** Generating (spinner), draft displayed, edit mode, AI failure (fallback).

### UI-011 Gemini Review / Approval
- **Purpose:** Review AI draft before approval.
- **Sections:** Draft text, recipient summary, send preview.
- **Main CTA:** "Approve" / "Edit" / "Regenerate" / "Use Template."
- **Business Rule:** Approval snapshot captured on Approve.

### UI-012 Schedule Settings
- **Purpose:** Set global and per-recipient send time/window.
- **Inputs:** Preferred send time (time picker), send window start/end, late-send policy toggle.
- **States:** Validation errors.

### UI-013 Device Readiness
- **Purpose:** Android — ensure permission, battery, SIM are ready.
- **Sections:** SEND_SMS permission card (grant/status), battery optimization exemption card, SIM selector (dual-SIM), Test SMS button.
- **States:** Ready, needs action, blocked.
- **Actions:** Grant permission → Settings handoff; Run test.

### UI-014 SMS Test
- **Purpose:** Send a test SMS.
- **Inputs:** Test recipient (self default or custom number).
- **States:** Sending, success (with receipt confirmation), failure (error code).

### UI-015 Automation Settings
- **Purpose:** Master toggle + per-recipient toggles + sender info.
- **Sections:** Master automation, sender device indicator, transfer option (Android), pause/resume.

### UI-016 Activity Log
- **Purpose:** Chronological audit.
- **Data:** Timestamp, recipient (masked), action, status, error ID, retry count.
- **Actions:** Filter by status, tap for detail, retry (if eligible).
- **States:** Empty, loading, error.

### UI-017 Activity Detail
- **Purpose:** Deep dive into a single event.
- **Data:** Scheduled time, actual send time, status, SMS parts, SIM used, retries, error code, reconciliation state.
- **Actions:** Retry (if eligible), export diagnostic.

### UI-018 Diagnostics
- **Purpose:** Self-serve health checks.
- **Sections:** Google auth status, contacts permission, SEND_SMS permission (Android), battery exemption, SIM state, sender fence, last sync, Firebase connection.
- **Actions:** Run checks, apply fixes, export anonymized log.

### UI-019 Settings
- **Purpose:** Central configuration.
- **Sections:** Account, Contacts, Notifications, Schedule defaults, Device, Privacy & Data, Legal & About, Support.
- **Navigation:** Nested sections via list.

### UI-020 Privacy & Data Inventory
- **Purpose:** Show exactly what data is stored.
- **Sections:** Data inventory (account, contacts, messages, ledger), retention summary, deletion options.
- **Actions:** Delete local data, Disconnect contacts, Delete account, View external deletion link.

### UI-021 Account Lifecycle
- **Purpose:** Manage account state.
- **Sections:** Current account email, connection status, sign out, delete account.
- **Actions:** Reconnect (if revoked), Sign out, Delete account.

### UI-022 Sender Transfer
- **Purpose:** Transfer sender role to this device.
- **Data:** Current sender device name, last seen.
- **Actions:** "Transfer to this device" → confirm.
- **States:** Transferring, success, conflict.

### UI-023 Reconnection Prompt
- **Purpose:** Modal when Google token revoked/expired.
- **Actions:** "Reconnect" / "Sign out."

### UI-024 Account Deletion Confirmation
- **Purpose:** Destructive-action confirmation.
- **Inputs:** Typed confirmation phrase.
- **States:** Processing, success (receipt), failure (queued).

### UI-025 Web Deletion Landing (Web)
- **Purpose:** External deletion for any user.
- **Sections:** Explanation, email entry, Google verification, confirmation, status, receipt.
- **States:** Idle, verifying, processing, success, error.
- **Responsive:** Mobile + desktop.

### UI-026 Limited Mode Screen
- **Purpose:** Shown when contacts denied or no birthdays found.
- **Actions:** Enable contacts, add manually, retry sync.

---

# SECTION 6 — INFORMATION ARCHITECTURE

## 6.1 Global Navigation
**Primary (Bottom Tab Bar — 4 tabs):**
1. **Home** (Dashboard)
2. **Upcoming** (Birthdays)
3. **People** (Recipients)
4. **Activity** (Log)

**Settings access:** Top-right gear icon from any primary tab.

**Rationale:** Four primary destinations cover the core daily use: overview, what's coming, who's enrolled, what happened. Settings is secondary and non-intrusive.

## 6.2 Secondary Navigation
- Within People: Search, Filter chips (All, Enrolled, Needs Action, Ineligible).
- Within Activity: Filter chips (All, Sent, Failed, Missed, Handed off, Pending).
- Within Upcoming: Date-grouped list; tap for detail.

## 6.3 Settings Hierarchy
```
Settings
├── Account (Google account, connection status, sign out)
├── Contacts (sync status, permission, disconnect)
├── Notifications (categories, toggles)
├── Schedule Defaults (global send time, window, late policy)
├── Device & Sending (Android: SIM, sender role, test SMS; iOS: reminder horizon)
├── Privacy & Data (inventory, deletion, retention)
├── Support & Diagnostics
└── Legal & About
```

## 6.4 Contextual Navigation
- Recipient detail → Message composer → Approval → back to detail.
- Dashboard → Upcoming → Recipient detail.
- Activity → Activity detail → Retry/export.

## 6.5 Deep-Link Behavior
- `wishwell://recipient/{id}` — opens recipient detail.
- `wishwell://activity/{id}` — opens activity detail.
- `wishwell://deletion` — opens in-app deletion flow.
- Web deletion URL: `https://wishwell.app/delete` (hosted).

## 6.6 Back Navigation
- Android: system back; predictive back where feasible.
- iOS: navigation stack + swipe-back.
- Deep-link into nested screen must push proper back stack.

## 6.7 Modal/Dialog Behavior
- Confirmation dialogs (destructive actions): centered modal.
- AI generation: inline sheet (bottom sheet on mobile) with progress.
- Permissions explanation: full-screen explainer, not raw system dialog.

## 6.8 Bottom Sheets
- Message tone selection.
- Send time picker.
- Test SMS target picker.
- Retry action sheet.

## 6.9 Native Settings Handoff
- SEND_SMS permission → Android app settings.
- Battery optimization → Android battery settings (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS or settings intent).
- Notifications permission → iOS/Android notification settings.
- Google access revocation → browser to Google account permissions.

## 6.10 Web Navigation
- Single-page deletion flow with clear steps; no app-like global nav needed.
- Links to privacy policy.

## 6.11 Account Switching
- **One active Google account.** Switching requires sign-out then sign-in. No silent multi-account support to prevent data mixing. [EXISTING]

## 6.12 Platform-Specific Navigation
- **Android:** Material 3 navigation bar.
- **iOS:** SF Symbols tab bar, navigation controller patterns.
- **Web:** Responsive single-column.

## 6.13 Why This Structure Is Optimal
It minimizes cognitive load by separating **management** (People) from **monitoring** (Upcoming/Activity) while keeping a single glanceable **Home**. Settings is organized by user mental model (account, data, device) rather than technical back-end grouping. This maps directly to the core user journey: activate once, monitor occasionally, investigate rarely.

---

# SECTION 7 — END-TO-END FUNCTIONAL REQUIREMENTS

## FR-001 — Google Authentication
- **Description:** User signs in with Google OAuth 2.0 using scopes `userinfo.email`, `userinfo.profile`, `contacts.readonly`.
- **Actors:** User, Google OAuth.
- **Preconditions:** Network; Google account exists.
- **Trigger:** User taps "Continue with Google."
- **Expected Behavior:** OAuth consent presented; on success, refresh token stored encrypted (Android Keystore / iOS Keychain); profile fetched; session established.
- **Business Rules:** BRULE-001 (one active account), BRULE-040 (reauthentication).
- **Exceptions:** User denies → return to Welcome with explanation.
- **Acceptance Criteria:** ACC-001.
- **Dependencies:** INT-001 (Google Auth).

## FR-002 — Contacts Sync
- **Description:** Read-only import of Google Contacts with birthdays.
- **Actors:** User, Google People API.
- **Preconditions:** Valid token; contacts permission granted.
- **Trigger:** After permission grant or manual sync.
- **Expected Behavior:** Fetch contacts with birthday fields; normalize; store local snapshot; display count.
- **Business Rules:** BRULE-006 (sync rules), BRULE-008 (Feb 29).
- **Exceptions:** Network failure → partial sync with retry; token invalid → re-auth.
- **Acceptance Criteria:** ACC-002.
- **Dependencies:** INT-002 (Google Contacts), FEAT-002.

## FR-003 — Birthday Detection & Normalization
- **Description:** Parse birthday dates from contacts; handle missing/ambiguous values.
- **Actors:** System.
- **Preconditions:** Contacts synced.
- **Trigger:** Post-sync processing.
- **Expected Behavior:** Extract date fields; normalize to ISO-8601; mark contacts with missing birthdays as "no birthday"; store.
- **Business Rules:** BRULE-008 (Feb 29), BRULE-009 (missing birthday).
- **Exceptions:** Ambiguous year → store day/month only; future birthday (e.g., 2025-01-01 vs current) → treat as upcoming.
- **Acceptance Criteria:** ACC-003.

## FR-004 — Phone Normalization
- **Description:** Canonicalize contact phone numbers for SMS eligibility.
- **Preconditions:** Contact has phone.
- **Expected Behavior:** Strip formatting; parse country code; validate E.164; mark eligible/ineligible.
- **Business Rules:** BRULE-010 (phone normalization).
- **Exceptions:** Multi-number contacts → pick primary with user override.
- **Acceptance Criteria:** ACC-004.

## FR-005 — Recipient Selection
- **Description:** User toggles contacts as automated birthday recipients.
- **Actors:** User.
- **Preconditions:** Contacts synced.
- **Trigger:** Toggle in People or detail.
- **Expected Behavior:** Toggle persists; ineligible contacts (no valid phone) blocked with reason.
- **Business Rules:** BRULE-004 (recipient enrollment).
- **Acceptance Criteria:** ACC-005.

## FR-006 — Message Templates
- **Description:** Predefined templates with variables `{name}`, `{age}`.
- **Actors:** User.
- **Trigger:** Selecting template in composer.
- **Expected Behavior:** Template inserted and populated with recipient data.
- **Business Rules:** BRULE-012 (template restrictions).
- **Acceptance Criteria:** ACC-006.

## FR-007 — Gemini AI Drafting
- **Description:** Generate personalized message using Gemini.
- **Actors:** User, Gemini (via INT-003).
- **Preconditions:** Recipient selected; optional context provided.
- **Trigger:** User taps "Generate with AI."
- **Expected Behavior:** Request sends only safe fields (name, age, relationship) to Gemini; response displayed as editable draft.
- **Business Rules:** BRULE-011 (Gemini behavior), BRULE-041 (Gemini privacy).
- **Exceptions:** Gemini failure → fallback template; timeout → retry.
- **Acceptance Criteria:** ACC-007.
- **Dependencies:** INT-003.

## FR-008 — Message Editor
- **Description:** Edit any draft before approval.
- **Expected Behavior:** Free-text editing with character counter and SMS parts warning.
- **Business Rules:** BRULE-012 (template restrictions) applies to final content.
- **Acceptance Criteria:** ACC-008.

## FR-009 — Approval System
- **Description:** Explicit per-recipient approval required before scheduling.
- **Expected Behavior:** Approval button; on approval, snapshot captured.
- **Business Rules:** BRULE-005 (approval snapshots), BRULE-006 (invalidation).
- **Acceptance Criteria:** ACC-009.

## FR-010 — Approval Invalidation
- **Description:** Detect changes to recipient data after approval.
- **Preconditions:** Approved snapshot exists.
- **Trigger:** Contact sync detects phone/birthday change.
- **Expected Behavior:** Snapshot marked invalid; recipient flagged "Needs re-approval"; no send occurs until re-approved.
- **Business Rules:** BRULE-006.
- **Acceptance Criteria:** ACC-010.

## FR-011 — Scheduling
- **Description:** Configure per-recipient or global send time/window.
- **Expected Behavior:** Store preferred time, window, late policy.
- **Business Rules:** BRULE-013 (send-window), BRULE-014 (late-send).
- **Acceptance Criteria:** ACC-011.

## FR-012 — Android Native SMS
- **Description:** Send approved message via SmsManager.
- **Preconditions:** SEND_SMS granted; SIM present; sender device.
- **Trigger:** Scheduled time reached.
- **Expected Behavior:** Send SMS; record attempt in safety ledger; handle multi-part automatically.
- **Business Rules:** BRULE-015 (native scheduler), BRULE-016 (reconciliation), BRULE-017 (SMS parts).
- **Exceptions:** Send failure → retry with backoff; SIM missing → error notification.
- **Acceptance Criteria:** ACC-012.
- **Dependencies:** INT-004 (Android SMS), FEAT-016.

## FR-013 — SIM Selection
- **Description:** User selects SIM for sending on dual-SIM devices.
- **Preconditions:** Android dual-SIM.
- **Expected Behavior:** SubscriptionManager lists SIMs; selected SIM used for sends; persisted.
- **Business Rules:** BRULE-018 (SIM selection).
- **Acceptance Criteria:** ACC-013.

## FR-014 — SMS Testing
- **Description:** Send a test SMS to self or custom number.
- **Expected Behavior:** Test message sent; result logged.
- **Business Rules:** BRULE-017 (parts).
- **Acceptance Criteria:** ACC-014.

## FR-015 — Background Execution
- **Description:** Schedule sends when app is closed.
- **Preconditions:** Android.
- **Expected Behavior:** Use WorkManager with constraints; alarm fallback for exact timing; retry with exponential backoff.
- **Business Rules:** BRULE-015, BRULE-025 (retry).
- **Acceptance Criteria:** ACC-015.

## FR-016 — Automation Activation
- **Description:** Master toggle to enable/disable all sends.
- **Expected Behavior:** Toggle ON requires readiness checks; toggle OFF halts pending sends.
- **Business Rules:** BRULE-020 (pause/resume).
- **Acceptance Criteria:** ACC-016.

## FR-017 — Upcoming Birthdays
- **Description:** Dashboard list of birthdays in next 14 days.
- **Expected Behavior:** Sorted ascending; status shown; tap for detail.
- **Acceptance Criteria:** ACC-017.

## FR-018 — Activity Log
- **Description:** Chronological audit of all send attempts.
- **Expected Behavior:** List with masked recipient, status, timestamp; detail on tap.
- **Business Rules:** BRULE-016 (reconciliation).
- **Acceptance Criteria:** ACC-018.

## FR-019 — Diagnostics
- **Description:** Health checks for permissions, auth, battery, SIM, sync.
- **Expected Behavior:** Run checks; display pass/fail; recommend fixes.
- **Acceptance Criteria:** ACC-019.

## FR-020 — Notifications
- **Description:** Reminders and status notifications.
- **Expected Behavior:** Per notification category; actionable where applicable.
- **Business Rules:** BRULE-022 (notification behavior), BRULE-023 (duplicate suppression).
- **Acceptance Criteria:** ACC-020.

## FR-021 — Privacy Controls
- **Description:** Data inventory, retention, deletion options.
- **Expected Behavior:** Show stored data categories; allow local deletion, contacts disconnect, account deletion.
- **Business Rules:** BRULE-024 (retention), BRULE-026 (privacy deletion), BRULE-027 (external copies).
- **Acceptance Criteria:** ACC-021.

## FR-022 — Account Lifecycle
- **Description:** Sign-out, reconnection, deletion.
- **Expected Behavior:** Full lifecycle with clean state transitions.
- **Business Rules:** BRULE-028 (account switching), BRULE-029 (cleanup blocking).
- **Acceptance Criteria:** ACC-022.

## FR-023 — Device Transfer
- **Description:** Transfer sender role.
- **Expected Behavior:** New device becomes sender; old demoted; fencing prevents duplicates.
- **Business Rules:** BRULE-002 (sender coordination), BRULE-003 (transfer).
- **Acceptance Criteria:** ACC-023.

## FR-024 — Sender Fencing
- **Description:** Ensure only one device sends.
- **Expected Behavior:** Cloud flag; non-sender devices cannot send; 24h fence during transfer.
- **Business Rules:** BRULE-002, BRULE-019 (fencing).
- **Acceptance Criteria:** ACC-024.

## FR-025 — Safety Ledger
- **Description:** Append-only log of SMS attempts.
- **Expected Behavior:** Every attempt logged with status, parts, SIM, error; immutable.
- **Business Rules:** BRULE-025 (retry), BRULE-030 (ledger behavior).
- **Acceptance Criteria:** ACC-025.

## FR-026 — Reconciliation
- **Description:** Compare ledger to carrier sent records.
- **Expected Behavior:** Query SMS content provider; mark confirmed/unconfirmed; surface discrepancies.
- **Business Rules:** BRULE-016.
- **Acceptance Criteria:** ACC-026.

## FR-027 — Account Deletion (In-App)
- **Description:** Full account and data deletion.
- **Expected Behavior:** Typed confirmation; backend deletion; local wipe; receipt.
- **Business Rules:** BRULE-026, BRULE-029.
- **Acceptance Criteria:** ACC-027.

## FR-028 — Web Deletion
- **Description:** External deletion landing.
- **Expected Behavior:** Verify Google identity; orchestrate deletion; receipt.
- **Business Rules:** BRULE-027.
- **Acceptance Criteria:** ACC-028.
- **Dependencies:** INT-008 (hosted backend).

## FR-029 — iOS Companion
- **Description:** Local reminders + MessageUI handoff.
- **Expected Behavior:** On birthday, local notification; tapping opens app → Messages sheet prefilled; user taps send; status "handed off."
- **Business Rules:** BRULE-021 (iOS differences).
- **Acceptance Criteria:** ACC-029.
- **Dependencies:** INT-006 (iOS services).

## FR-030 — Contacts Disconnection
- **Description:** Revoke contacts scope without deleting account.
- **Expected Behavior:** Local snapshot deleted; limited mode.
- **Business Rules:** BRULE-029.
- **Acceptance Criteria:** ACC-030.

## FR-031 — Reconnection
- **Description:** Detect and recover from Google token revocation.
- **Expected Behavior:** On detection, prompt re-auth; restore session.
- **Business Rules:** BRULE-040.
- **Acceptance Criteria:** ACC-031.

## FR-032 — Local-Data Deletion
- **Description:** Wipe local encrypted data.
- **Expected Behavior:** Confirm; wipe; restart.
- **Acceptance Criteria:** ACC-032.

## FR-033 — Notification Preferences
- **Description:** Per-category notification toggles.
- **Expected Behavior:** Toggles persist; categories honored.
- **Business Rules:** BRULE-022.
- **Acceptance Criteria:** ACC-033.

## FR-034 — Message Template Restrictions
- **Description:** Prevent unsafe template content.
- **Expected Behavior:** Validate final message; block excessively long (> 10 SMS parts) or empty content.
- **Business Rules:** BRULE-012.
- **Acceptance Criteria:** ACC-034.

---

# SECTION 8 — BUSINESS RULES

| Rule ID | Rule | Reason | Applies To | Enforcement Point | User-Visible Behavior | Exceptions | Failure/Recovery |
|---|---|---|---|---|---|---|---|
| **BRULE-001** | One active Google account at a time | Prevent data mixing | Auth | Login layer | Switching requires sign-out | None | Sign-out flow |
| **BRULE-002** | Exactly one sender device (Android) | Prevent duplicate sends | Automation | Sender fence flag in cloud | Non-sender shows "read-only companion" | Forced transfer | 24h fence during transfer |
| **BRULE-003** | Sender transfer requires explicit confirmation | Avoid accidental demotion | Device transfer | Transfer UI | Confirmation modal | Old device unreachable | Force transfer with 24h fence |
| **BRULE-004** | Only contacts with valid normalized phone are recipient-eligible | SMS deliverability | Recipient enrollment | Toggle logic | Ineligible contacts show "no valid phone" | User override phone | Re-validate on sync |
| **BRULE-005** | Approval snapshots capture recipient name, phone, birthday, message, timestamp at approval | Auditability | Approval | Approve action | Immutable snapshot | None | Invalidation |
| **BRULE-006** | Approval invalidates if phone or birthday changes after approval | Prevent wrong sends | Approval | Contact sync | Recipient flagged "Needs re-approval" | None | Re-approval flow |
| **BRULE-007** | Contacts sync is read-only; never write to Google Contacts | Privacy, safety | Contacts | Sync logic | No write operations | None | N/A |
| **BRULE-008** | Feb 29 birthdays: send Feb 28 in non-leap years by default; user-configurable to Mar 1 | Predictable scheduling | Birthday | Schedule calculation | Setting shown | User preference | N/A |
| **BRULE-009** | Contacts without birthday are hidden from recipient list but visible in People | Reduce noise | Detection | Sync | Filtered views | None | Manual add |
| **BRULE-010** | Phone numbers normalized to E.164; invalid numbers marked ineligible | SMS deliverability | Phone | Normalization | Ineligible badge | Manual override | Re-normalize on sync |
| **BRULE-011** | Gemini generates only from safe fields (name, age, relationship context); never phone or email | Privacy | Gemini | API request builder | No PII to AI | None | Fallback template |
| **BRULE-012** | Final message must be non-empty and ≤ 10 SMS parts | Cost, deliverability | Message | Validation | Parts warning; block if too long | None | Edit |
| **BRULE-013** | Preferred send time defaults 10:00 local; window defaults 9:00–21:00 | Predictability | Schedule | Scheduler | Settings | User config | N/A |
| **BRULE-014** | Late-send: attempt within window on birthday; if window passed, mark missed unless late policy allows next-morning | Reliability | Schedule | Scheduler | Missed status | User sets late policy | Notification |
| **BRULE-015** | Android sends use WorkManager; exact-time sends use alarm fallback | OEM restrictions | Android | Scheduler | N/A | None | Retry |
| **BRULE-016** | Every send attempt reconciled against SMS content provider when available | Delivery truth | Android | Reconciliation | Activity status updated | iOS not applicable | Manual retry |
| **BRULE-017** | Messages > 160 chars (GSM-7) / > 70 (UCS-2) split into parts; parts tracked | Cost transparency | SMS | Send logic | Parts count shown in UI | None | N/A |
| **BRULE-018** | SIM selection persisted per device; default = user's chosen SIM | Correct billing | Android | Send logic | SIM badge | Single SIM hidden | Fallback to only SIM |
| **BRULE-019** | Non-sender devices cannot initiate sends; sender fence cloud flag enforced | Duplicate prevention | Android | Send gate | Read-only companion | Sender transfer | Fence reset |
| **BRULE-020** | Master pause halts all pending sends; per-recipient pause halts only that recipient | User control | Automation | Toggle logic | Status shown | None | Resume |
| **BRULE-021** | iOS never auto-sends; local reminder + MessageUI handoff only; mark "handed off" not "sent" | Platform honesty | iOS | iOS companion | Clear labeling | None | N/A |
| **BRULE-022** | Notifications: per category; default all on except marketing; no spam (max 1 per birthday per day) | User experience | Notifications | Notification manager | Toggles | None | N/A |
| **BRULE-023** | Duplicate notification suppression within 30 minutes per recipient | Avoid spam | Notifications | Notification manager | Single notification | Explicit user action | N/A |
| **BRULE-024** | Data retention: account data retained until deletion; local snapshot deleted on sign-out (default) | Privacy | Data | Lifecycle | Retention summary | User keeps local | Deletion |
| **BRULE-025** | Retry sends up to 3 times with exponential backoff within send window | Reliability | SMS | Scheduler | Status shows retries | Window ends | Mark failed |
| **BRULE-026** | Privacy deletion: account deletion wipes Firebase, local data, and revokes Google tokens | Privacy | Deletion | Lifecycle | Receipt | Backend failure → queue | Retry |
| **BRULE-027** | External data-copy disclosures: Gemini receives only name/age/context; Google receives contacts as part of sync; no other third parties | GDPR/Google policy | Privacy | Disclosure | Privacy policy, data inventory | None | N/A |
| **BRULE-028** | Account switching requires sign-out; no silent account swap | Data isolation | Auth | Login layer | Sign-out prompt | None | N/A |
| **BRULE-029** | Cleanup blocking: deletion of account blocks until pending cloud sends are canceled/acked | Data consistency | Lifecycle | Deletion flow | "Cleaning up…" | Force delete after 24h | Queued |
| **BRULE-030** | Safety ledger is append-only; entries never edited or deleted except by full account deletion | Auditability | Ledger | Storage | Immutable log | Full account deletion | N/A |
| **BRULE-031** | February 29 birthday in non-leap year: if user selects Mar 1, that is honored | User preference | Birthday | Schedule | Setting shown | None | N/A |
| **BRULE-032** | SIM absent at send time → mark failed with "No SIM" error; notify user | Diagnosability | Android | Send gate | Error notification | None | Retry after SIM detected |
| **BRULE-033** | Battery optimization exemption recommended; if not granted, send may be delayed | OEM reliability | Android | Readiness | Warning | User ignores | Late-send rule |
| **BRULE-034** | Test SMS must be user-initiated; no automatic test send | Safety | SMS test | UI action | Test button only | None | N/A |
| **BRULE-035** | Web deletion uses Google identity verification where available; fallback manual verification | Compliance | Web deletion | Backend | Verification step | Manual fallback | Receipt |

---

# SECTION 9 — PLATFORM REQUIREMENTS

## 9.1 Android

| Requirement | Detail |
|---|---|
| Native SMS | Use `SmsManager.sendTextMessage` (or `SmsManager.sendMultipartTextMessage` for multi-part). |
| SEND_SMS | Runtime permission `android.permission.SEND_SMS`; must be requested with rationale. |
| SIM Management | Use `SubscriptionManager.getActiveSubscriptionInfoList()`; map subscription ID to send. |
| Background Scheduling | WorkManager for deferred work; `AlarmManager.setExactAndAllowWhileIdle` for exact birthday times as fallback. |
| Sender Device | Cloud flag `senderDeviceId`; only matching device sends. |
| Transfer | Transfer API updates `senderDeviceId` with 24h fence. |
| Reconciliation | Query `content://sms/sent` (or `Telephony.Sms.SENT` URI) post-send; compare address and timestamp. |
| SMS Safety Ledger | Append-only local SQLite + Firestore mirror (encrypted). |
| Android-Specific Permissions | SEND_SMS, POST_NOTIFICATIONS (API 33+), RECEIVE_BOOT_COMPLETED (reschedule after reboot). |
| Android Settings Repair | Deep-link to app settings, battery optimization, notification settings. |

## 9.2 iOS

| Requirement | Detail |
|---|---|
| Companion Model | iOS app is a companion; **never claims auto-send**. |
| MessageUI | `MFMessageComposeViewController` prefilled with recipient phone and approved message; user taps send. |
| User-Controlled Sending | App cannot programmatically send; app can only present sheet. |
| Local Reminders | `UNUserNotificationCenter` for birthday reminders; request notification permission. |
| Notification Permissions | Request authorization with clear rationale. |
| Protected Data | Keychain for tokens; no SMS sending permissions exist. |
| Reminder Horizon | Default reminders on birthday at preferred time; repeat once if not completed (within 24h). |
| iCloud/Message Limitations | App cannot verify send state from MessageUI; mark as "handed off." |
| **Explicitly Prohibited Assumptions** | iOS background execution for SMS; automatic sending; delivery confirmation; SIM APIs; SMS ledger in same way as Android. |

## 9.3 Web

| Requirement | Detail |
|---|---|
| External Deletion Landing | Hosted at `https://wishwell.app/delete`; accessible without app. |
| Authentication | Google Sign-In for identity verification (userinfo.email only); manual fallback with email + verification. |
| Deletion Orchestration | Backend Cloud Function wipes Firestore user record, revokes Google refresh token, cancels pending sends, deletes cloud data. |
| Status | Real-time status updates; queued deletion shows pending. |
| Receipt | Confirmation with deletion timestamp and case ID. |
| Responsive Behavior | Mobile-first, WCAG AA. |
| Privacy | No tracking scripts on deletion page; minimal logging. |

---

# SECTION 10 — DATA & DOMAIN REQUIREMENTS

## DATA-001 Account

| Field | Type | Notes |
|---|---|---|
| userId | String (Firebase Auth UID) | Primary |
| email | String | From Google profile |
| googleRefreshToken | String (encrypted) | Stored in Keystore/Keychain only |
| createdAt | Timestamp | |
| deletedAt | Timestamp \| null | Soft delete until purge |
| senderDeviceId | String \| null | Android only |

## DATA-002 Activity

| Field | Type | Notes |
|---|---|---|
| activityId | UUID | |
| userId | ref | Owner |
| recipientId | ref | |
| type | enum | scheduled, sent, failed, missed, handed_off, retried |
| scheduledTime | Timestamp | |
| actualTime | Timestamp \| null | |
| status | enum | |
| errorCode | String \| null | |
| retryCount | Int | |
| parts | Int \| null | |
| simId | String \| null | Android |
| reconciled | Bool | |

## DATA-003 Approval

| Field | Type | Notes |
|---|---|---|
| approvalId | UUID | |
| recipientId | ref | |
| snapshotMessage | String | |
| snapshotPhone | String (masked) | |
| snapshotBirthday | Date | |
| approvedAt | Timestamp | |
| status | enum | approved, invalidated |
| invalidatedReason | String \| null | |

## DATA-004 Automation

| Field | Type | Notes |
|---|---|---|
| userId | ref | |
| masterEnabled | Bool | |
| perRecipientEnabled | Map<recipientId, Bool> | |
| pausedUntil | Timestamp \| null | |

## DATA-005 Birthday

| Field | Type | Notes |
|---|---|---|
| contactId | ref | |
| day | Int | |
| month | Int | |
| year | Int \| null | |
| leapDayPolicy | enum | feb28, mar1 |

## DATA-006 Contact

| Field | Type | Notes |
|---|---|---|
| contactId | UUID (local) | |
| googleContactId | String | |
| name | String | |
| phoneE164 | String \| null | |
| phoneValid | Bool | |
| hasBirthday | Bool | |
| birthday | ref | |

## DATA-007 Device

| Field | Type | Notes |
|---|---|---|
| deviceId | String | |
| userId | ref | |
| platform | enum | android, ios |
| isSender | Bool | |
| lastSeen | Timestamp | |
| simSubscriptions | JSON | Android only |

## DATA-008 Home
- Derived aggregate: upcoming birthdays, automation status, recent activity.
- No persistent storage; computed on demand.

## DATA-009 Legal
- Privacy policy version, consent timestamps, disclosure text.

## DATA-010 Message

| Field | Type | Notes |
|---|---|---|
| messageId | UUID | |
| recipientId | ref | |
| draftText | String | |
| source | enum | gemini, template, manual |
| parts | Int | |
| approvedSnapshot | ref \| null | |

## DATA-011 Privacy
- Data inventory record: categories of stored data, retention timers, deletion status.

## DATA-012 Readiness
- Device readiness state: permissions, battery, SIM, sender fence.

## DATA-013 Recipient
- Union of Contact + Automation enrollment + Approval status.

## DATA-014 Transfer

| Field | Type | Notes |
|---|---|---|
| transferId | UUID | |
| fromDeviceId | String | |
| toDeviceId | String | |
| status | enum | pending, completed, failed |
| fenceUntil | Timestamp | |

## DATA-015 Deletion Operation

| Field | Type | Notes |
|---|---|---|
| operationId | UUID | |
| userId | ref | |
| status | enum | pending, in_progress, completed, failed |
| requestedAt | Timestamp | |
| completedAt | Timestamp \| null | |
| caseId | String | Receipt |

---

# SECTION 11 — INTEGRATIONS & SYSTEM BEHAVIOR

| Integration | Purpose | Inputs | Outputs | Failure Modes | Retry | Security | User-Visible |
|---|---|---|---|---|---|---|---|
| **INT-001 Google Auth** | Sign-in, identity | OAuth request | Token, profile | Denied, network | Re-prompt | OAuth scopes | Consent screen |
| **INT-002 Google Contacts** | Birthday source | token | Contacts + birthdays | Token invalid, sync error | Retry sync | Read-only scope | Sync progress |
| **INT-003 Firebase** | Cloud sync, storage | Auth user, data | Persisted state | Network | Auto-sync | Firestore rules | Offline indicator |
| **INT-004 Gemini/Vertex AI** | Message drafting | Safe fields (name/age/context) | Draft text | Timeout, API error | Retry once | PII restriction | Draft or fallback |
| **INT-005 Android Services** | Scheduling, SMS | WorkManager, AlarmManager, SmsManager | Send result | OEM kill, SIM missing | Backoff retry | Runtime permissions | Notifications |
| **INT-006 iOS Services** | Reminders, MessageUI | UNUserNotificationCenter, MFMessageComposeViewController | User action | Permission denied | N/A | User control | Reminder, sheet |
| **INT-007 SMS Gateway/Native** | Actual message delivery | Phone, text, SIM | Carrier result | Carrier reject | Retry | Network | Status |
| **INT-008 Cloud Functions** | Backend orchestration | Deletion, transfer ops | Result | Timeout | Queued | Admin SDK | Receipt |
| **INT-009 Hosted Deletion Backend** | External deletion | Email, verification | Deletion receipt | Failure | Queue + retry | TLS, verification | Web status |
| **INT-010 Notification Systems** | User alerts | FCM/APNs, local | Notification | Permission denied | N/A | Data minimization | Alerts |

---

# SECTION 12 — PRIVACY, SECURITY & TRUST

## 12.1 Authentication
- OAuth 2.0 with PKCE; refresh tokens encrypted in Keystore (Android) / Keychain (iOS).
- Access tokens never stored in plaintext; short-lived.

## 12.2 Authorization
- Firestore rules: user can only read/write own data.
- Cloud Functions use admin SDK with tight scoping.

## 12.3 Least Privilege
- Google scopes: `userinfo.email` (identity), `userinfo.profile` (display name), `contacts.readonly` (birthdays). **No contact write scope.**
- Android permissions: SEND_SMS only; POST_NOTIFICATIONS only where needed.
- iOS: notification permission only.

## 12.4 Google Scopes
- `contacts.readonly` restricted scope → requires web deletion landing, privacy policy, and verification.
- No `contacts` (write) scope ever.

## 12.5 Contacts Access — CRITICAL PRIVACY BOUNDARY
- **Read-only**; contacts synced to device storage.
- **CRITICAL:** Server stores **HMAC-SHA256 aliases** (derived via `birthday-autopilot/control-plane/v1` keyring) for coordination ONLY.
- **NO raw contact names, phone numbers, or birthdates are ever stored server-side.**
- User can disconnect contacts anytime; local snapshot deleted on sign-out.

## 12.6 Sender Identity
- Sender device is explicitly set; no hidden sending from cloud.

## 12.7 Data Minimization
- Only store: email, contacts with birthdays (name, phone, birthday), messages, approvals, activity ledger, device info.
- Do NOT store: full contact list, email bodies, location, call logs.

## 12.8 PII Protection
- Phone numbers masked in UI (e.g., `+1 *** *** 1234`) in activity and diagnostics.
- Emails not shown in logs.

## 12.9 Logging Restrictions
- Production logs must not contain phone numbers, emails, message text, or names.
- Logging uses correlation IDs.

## 12.10 Diagnostic Anonymization
- Exportable diagnostics scrubbed of PII; only error codes and non-sensitive state.

## 12.11 Encryption Expectations
- At rest: local SQLite encrypted (SQLCipher); Firestore encrypted by Google.
- In transit: TLS 1.2+.

## 12.12 Secure Local Storage
- Android Keystore for crypto keys; iOS Keychain for tokens and keys.

## 12.13 Cloud Security
- Firebase Security Rules; Cloud Functions IAM; no public data buckets.

## 12.14 Deletion
- In-app and web deletion; immediate soft-delete, purge within 30 days; Google token revocation.

## 12.15 Retention
- Account data retained until deletion; local snapshot deleted on sign-out (default); user may keep local offline copy.

## 12.16 External Copies
- Gemini: receives only name, age, relationship context; no phone/email; data not retained by Gemini per API terms. [ASSUMPTION — verify Gemini API retention terms during implementation]
- Google: contacts synced as part of OAuth; user can revoke in Google account.

## 12.17 Reauthentication
- Token revocation detected on next API call; re-auth required.

## 12.18 Account Isolation
- Firestore rules enforce user isolation; no cross-user queries.

## 12.19 Account-Switch Protection
- Sign-out wipes session tokens; no silent account swap.

## 12.20 Transfer Security
- Sender transfer requires re-authentication; 24h fence.

## 12.21 Abuse Prevention
- Rate limit Gemini requests per user per day (e.g., 20/day); test SMS limited to 5/day.

## 12.22 Gemini Privacy Boundaries
- Request builder strips all PII except name/age/context; no phone, email, or contact ID sent.

---

# SECTION 13 — ERROR HANDLING & RECOVERY

| Failure | Detection | User Message | Recommended Action | Retry | Fallback | Recovery | Final State |
|---|---|---|---|---|---|---|---|
| Network failure | HTTP/API error | "No internet. We'll try again." | Check connection | Auto-retry 3× | Offline mode | Re-sync on reconnect | Restored |
| Authentication failure | 401 from API | "Your Google connection expired." | Reconnect | Re-auth | Sign out | Re-auth flow | Restored |
| Permission denial | OS callback | "Contacts permission needed." | Enable in Settings | N/A | Limited mode | Settings handoff | Limited |
| Permission revocation | API error / recheck | "Access was revoked." | Reconnect | Re-auth | Sign out | Re-auth | Restored |
| Contacts sync failure | Sync exception | "Couldn't sync contacts." | Retry | Auto + manual | Partial data | Manual sync | Partial/restored |
| Invalid contact | Missing phone | "This contact can't receive SMS." | Override phone | N/A | Exclude | Manual override | Fixed |
| Invalid phone | E.164 invalid | "Phone number looks invalid." | Edit phone | N/A | Exclude | Manual edit | Fixed |
| Missing birthday | No date | "No birthday found." | Manual add | N/A | Hide from list | Manual add | Fixed |
| Approval invalidation | Sync detects change | "Contact changed. Re-approve." | Re-approve | N/A | Cancel send | Re-approval | Re-approved |
| Gemini failure | API timeout/error | "AI unavailable. Using template." | Use template | Retry once | Template | Template | Draft ready |
| Scheduler failure | Job not run | "Scheduled send missed." | Retry now | Retry | Manual send | Late-send rule | Sent or missed |
| SMS failure | SmsManager error | "Message failed to send." | Retry | Auto 3× | Mark failed | Manual retry | Sent or failed |
| SIM failure | No active SIM | "No SIM detected." | Insert SIM | Retry on detect | Mark failed | Notify | Failed |
| Background execution failure | OEM killed | "Background restricted." | Enable battery exemption | N/A | Alarm fallback | Settings | Ready |
| Device transfer failure | Conflict | "Transfer failed." | Retry | Retry | Keep old sender | Force transfer | Transferred |
| Account deletion failure | Backend error | "Deletion queued." | Wait | Auto-retry | Queue | Status page | Deleted |
| Cleanup failure | Pending ops | "Cleaning up…" | Wait | Auto-retry | Force after 24h | Queue | Deleted |
| Native settings return failure | No change | "Settings not changed." | Re-check | N/A | Guide manually | Diagnostics | Fixed |
| Stale state | Mismatch | "Data may be outdated." | Refresh | Auto-refresh | N/A | Refresh | Current |
| Offline state | No network | "You're offline." | Continue read-only | N/A | Cached data | Re-sync | Current |
| Partial completion | Some sends done | "Some messages were sent." | Review Activity | Retry missed | N/A | Activity log | Consistent |
| Unknown state | Unexpected error | "Something went wrong. Error #ID." | Restart / support | Auto | Diagnostics | Support | Resolved |

---

# SECTION 14 — NOTIFICATIONS & FEEDBACK

## 14.1 Notification Categories

| Category | Purpose | Default | Actionable |
|---|---|---|---|
| Reminder (iOS) | Birthday reminder to send via Messages | On | Yes — opens app → Messages sheet |
| Reminder (Android) | Upcoming birthday preview | On | Yes — opens recipient detail |
| Success | Message sent confirmation | On | No |
| Failure | Send failed / needs action | On | Yes — opens diagnostics |
| Reconnection | Google access revoked | On | Yes — opens reconnect |
| Deletion status | Deletion progress/completion | On | No |
| Marketing/Promotional | None in MVP | Off | N/A |

## 14.2 Permission Behavior
- Request notification permission after onboarding completes, with context ("We'll remind you before birthdays").
- If denied, in-app reminders only; no re-prompt spam.

## 14.3 Notification Content Rules
- No phone numbers or full names in notification text; use first name only.
- No message content preview unless user opts in.

## 14.4 Reminder Behavior
- iOS: birthday day at preferred time; repeat once after 4 hours if not completed.
- Android companion: upcoming birthday 24h before, and morning-of.

## 14.5 Success/Failure Feedback
- Success: silent toast + activity entry; no push notification spam.
- Failure: push notification with actionable "Fix now."

## 14.6 Duplicate Suppression
- Per recipient per day: max 1 reminder, max 1 failure notification, max 1 success.

## 14.7 Notification Preferences
- Per-category toggles in Settings.

## 14.8 Platform Differences
- iOS uses local notifications only; Android uses FCM for cloud-side events plus local alarms.

---

# SECTION 15 — ACCESSIBILITY & LOCALIZATION

## 15.1 Accessibility Requirements
- WCAG 2.1 AA equivalent for mobile.
- TalkBack / VoiceOver labels for all interactive elements.
- Keyboard/Switch access for Android accessibility services.
- Minimum touch targets: 48×48 dp/pt.
- Dynamic Type / font scaling support up to 200%.
- Contrast ratio ≥ 4.5:1 for normal text.
- Reduced motion respected (no bouncing, avoid parallax).
- Focus management in dialogs and after navigation.
- Semantic labels on all buttons and inputs.
- Error announcements via accessibility live regions.

## 15.2 Localization
- **MVP languages:** English + Hindi.
- Full string externalization.
- Date/time using locale formats; birthdays displayed per locale.
- RTL/pseudo-RTL testing for future languages.
- Phone numbers displayed in local format (but stored E.164).

---

# SECTION 16 — DESIGN SYSTEM REQUIREMENTS

| Component | Requirement |
|---|---|
| Brand Direction | Warm, personal, trustworthy; soft gradients; celebratory accents |
| Colors | Primary: warm coral #FF6B6B; Secondary: deep teal #0B7285; Neutrals: warm gray scale; Success: #2D9B4E; Warning: #E8A23D; Error: #D64545 |
| Typography | System default fonts: Roboto (Android), SF Pro (iOS); min body 16sp/pt |
| Spacing | 4/8/16/24/32 pt grid |
| Grid | 4-column mobile; 8-column tablet |
| Radius | Cards 16dp; buttons 12dp; chips 20dp |
| Shadows | Elevation 0–8dp subtle; no heavy shadows |
| Buttons | Primary filled; Secondary outlined; Tertiary text; height ≥ 48dp |
| Inputs | Filled underline or outlined; error text below |
| Cards | Rounded, soft shadow, clear hierarchy |
| Lists | Sectioned with sticky headers |
| Navigation | Bottom bar with 4 tabs; top app bar with title + settings |
| Tabs | Material 3 / iOS segmented |
| Chips | Filter chips with check states |
| Badges | Status badges: Needs action, Approved, Sent, Failed |
| Alerts | Snackbar (Android) / toast (iOS); inlined errors in forms |
| Dialogs | Centered for confirmations; bottom sheets for pickers |
| Empty States | Friendly illustration + CTA (e.g., "No birthdays found — Add contacts") |
| Loading | Skeleton screens for lists; spinners for actions |
| Icons | Material Symbols (Android) / SF Symbols (iOS); consistent semantic meaning |
| Illustrations | Simple, warm, human |
| Responsive | Adaptive layout for tablet and web |

---

# SECTION 17 — UX IMPROVEMENT AUDIT

| Existing Problem | Why Problematic | Proposed Improvement | User Benefit | Business Benefit | Risk | Recommendation |
|---|---|---|---|---|---|---|
| Excessive onboarding with too many separate steps | High drop-off before activation | Consolidate into 4-step flow: Sign-in → Contacts → Pick recipients → Approve first message → Activate | Faster setup | Higher activation | Missed explanations | Adopt for MVP |
| Redundant screens for approval and scheduling | Cognitive overload | Merge message approval + scheduling into one "Recipient detail" screen | Fewer taps | Higher completion | Overcrowded screen | Adopt with clear sections |
| Confusing terminology ("safety ledger", "fencing") | User-facing tech jargon | Use plain terms: "Activity log", "one sender device" | Understandable | Fewer support tickets | Loss of precision | Rename in UI; keep internal terms |
| Unnecessary confirmations | Friction | Only require typed confirmation for destructive actions (account deletion) | Smoother use | Retention | Accidental actions | Adopt |
| Missing recovery flows for common failures | Users stranded | Add diagnostics + retry everywhere | Self-serve recovery | Lower support cost | Scope creep | Adopt |
| Weak empty states | Dead ends | Every empty state has a CTA and guidance | Confidence | Exploration | N/A | Adopt |
| Poor error messaging ("Error 500") | User confusion | Human-readable + error ID for support | Clarity | Support efficiency | N/A | Adopt |
| Hidden important actions (retry buried in activity) | Missed recovery | Surface "Retry" in activity detail and notification | Recovery | Reliability | Accidental resend | Adopt with confirmation |
| Excessive cognitive load on dashboard | Overwhelming | Simplify to 3 cards: Automation toggle, Upcoming (top 3), Last event | Clarity | Retention | Information hiding | Adopt |
| Missing shortcuts (e.g., "Approve all drafts") | Repetitive work | Bulk approve for low-risk messages | Efficiency | Activation | Reduced review | **[FUTURE]** — post-MVP |
| Poor first-time experience with empty state | Discouraging | Show sample data / demo preview | Understanding | Activation | Confusion | Adopt |
| Poor returning-user experience | No "what's new since last visit" | Dashboard shows delta summary ("3 birthdays coming in 7 days") | Orientation | Retention | N/A | Adopt |
| Platform inconsistencies in wording | iOS users expect auto-send | Explicit platform-specific messaging at setup | Honesty | Fewer complaints | N/A | Adopt |

---

# SECTION 18 — NEW FEATURE OPPORTUNITY ANALYSIS

| Feature | Problem | Solution | User Value | Business Value | Complexity | Deps | Risk | Priority |
|---|---|---|---|---|---|---|---|---|
| Bulk Draft & Approve | Repeating per-recipient for 50 people | Generate all drafts then batch approve | Time savings | Activation | Medium | Gemini rate limits | Less personalization | **P2 / Post-Launch** |
| Relationship Tags | AI doesn't know context | User tags contacts (coworker, family, close friend) | Better drafts | Approval rate | Low | None | Low | **P1 Production** |
| Message Tone Profiles | Repetitive tone selection | Save global tone profile | Consistency | Efficiency | Low | None | Low | **P2** |
| Reminder Advance Config | Only day-of reminders | User sets advance notice (1–7 days) | Peace of mind | Retention | Low | Notifications | Low | **P1 Production** |
| Anniversary Support | Only birthdays | Add anniversary event type | Broader use | Expansion | High | Data model | High | **Future** |
| WhatsApp Sending | iOS can't auto-send SMS | WhatsApp Business API or URL scheme | iOS automation | Platform parity | High | Third-party | High | **Future** |
| Shared Lists | Family wants shared recipients | Shared workspace | Collaboration | Growth | High | Auth model | High | **Future** |
| Annual Report | No reflection | Year-in-review of maintained relationships | Delight | Retention | Low | Analytics | Low | **P3/Future** |

---

# SECTION 19 — PRODUCT PRIORITIZATION

## Priority Levels
- **P0:** Product cannot operate without it.
- **P1:** Required for strong production launch.
- **P2:** Important enhancement.
- **P3 / Future:** Post-launch opportunity.

## MVP (P0 only)
- Google auth, contacts sync, birthday detection, phone normalization, recipient selection, approval, AI drafting, scheduling, Android native SMS, iOS companion reminders, activity log, safety ledger, account deletion, web deletion, privacy controls.

## Production Launch (P0 + P1)
- Everything in MVP plus: people management, diagnostics, notifications, sender transfer, reconciliation, reconnection, contacts disconnection, template restrictions, local-data deletion, support entry points, relationship tags (already implemented), reminder advance config.

## Post-Launch (P2)
- SIM selection (already implemented), SMS parts UI, notification preferences, offline mode, bulk operations.

## Future (P3)
- Monetization, anniversary support, WhatsApp, shared lists, annual report.

---

# SECTION 20 — NON-FUNCTIONAL REQUIREMENTS

| ID | Requirement | Detail |
|---|---|---|
| NFR-001 | Performance | Cold start ≤ 2s; contact sync ≤ 10s for 1000 contacts; UI 60fps |
| NFR-002 | Reliability | Send success ≥ 98% on Android; crash-free ≥ 99.9% |
| NFR-003 | Availability | Backend 99.9%; offline mode for local operations |
| NFR-004 | Scalability | Firestore + Cloud Functions scale horizontally; Gemini rate limits handled |
| NFR-005 | Security | OAuth PKCE, encrypted storage, TLS 1.2+, Firestore rules |
| NFR-006 | Privacy | Data minimization, deletion ≤ 48h, anonymized diagnostics |
| NFR-007 | Maintainability | Modular architecture; feature toggles; automated tests ≥ 80% coverage |
| NFR-008 | Observability | Centralized logging (PII-free), crash reporting, analytics |
| NFR-009 | Accessibility | WCAG 2.1 AA, dynamic text, TalkBack/VoiceOver |
| NFR-010 | Localization | English + Hindi MVP; locale-aware dates/times |
| NFR-011 | Compatibility | Android 8.0+ (API 26); iOS 15+ |
| NFR-012 | Offline | Read cached data; queue actions; sync on reconnect |
| NFR-013 | Battery | Background work optimized; no excessive wakeups |
| NFR-014 | Memory | Under 100MB active; no leaks |
| NFR-015 | Large Datasets | 10,000 contacts handled gracefully; pagination |
| NFR-016 | Recovery | Auto-retry with backoff; manual retry everywhere |
| NFR-017 | Upgrade/Migration | Schema migrations handled; backward compatible |

---

# SECTION 21 — ANALYTICS & PRODUCT MEASUREMENT

| Event | Purpose | Decision Enabled |
|---|---|---|
| onboarding_started | Funnel entry | Identify drop-off points |
| onboarding_completed | Activation | Improve onboarding |
| google_connected | Auth success | Auth reliability |
| contacts_connected | Permission grant | Permission messaging |
| contacts_sync_success/partial/failure | Sync health | Sync reliability |
| recipient_selected | Enrollment | Activation |
| recipient_approved | Approval | Trust |
| message_created | AI/template use | Feature usage |
| gemini_draft_generated | AI success | AI reliability |
| gemini_draft_edited | Edit behavior | AI quality |
| automation_activated | Activation | Core funnel |
| test_sms_completed | Readiness | Confidence |
| birthday_processed | Send pipeline | Throughput |
| message_sent | Android success | Reliability |
| message_handed_off | iOS handoff | iOS conversion |
| reminder_completed | iOS send | iOS effectiveness |
| failure_recovered | Recovery | Diagnostics |
| automation_paused/resumed | Control usage | UX |
| account_deleted | Lifecycle | Privacy |

**Privacy rule:** No phone numbers, emails, or message text in analytics. Only aggregate, anonymous event data.

---

# SECTION 22 — SUPPORT & OPERATIONS

## 22.1 Diagnostics
- Built-in health check: auth, contacts, SEND_SMS, battery, SIM, sender fence, sync, Firebase.
- Anonymized diagnostic export (JSON/text) with error IDs and timestamps.

## 22.2 Support Workflows
- User can export diagnostics and share with support.
- Error IDs in UI link to knowledge base articles.

## 22.3 Anonymized Logs
- PII-scrubbed logs; correlation IDs for support.

## 22.4 Error Identifiers
- Every error has a stable code (e.g., `WISH-401-TOKEN_EXPIRED`).

## 22.5 Troubleshooting
- In-app guided fixes for common issues (permissions, battery, SIM).

## 22.6 Operational Visibility
- Admin dashboard (internal): deletion queue, sync errors, Gemini rate limits, crash trends.

## 22.7 Incident Handling
- Alert on deletion backlog > 100 or send failure rate > 5%.

## 22.8 User Support Entry Points
- Settings → Support → Diagnostics → Export/Share.
- Web deletion page has "Contact support" with case ID.

---

# SECTION 23 — ACCEPTANCE CRITERIA

## ACC-001 Google Authentication
**Given** user taps "Continue with Google"  
**When** OAuth consent completes successfully  
**Then** user lands on dashboard with account email visible and tokens stored encrypted.

## ACC-002 Contacts Sync
**Given** contacts permission granted  
**When** sync completes  
**Then** list shows contacts with birthdays; count displayed.

## ACC-003 Birthday Normalization
**Given** contact has birthday "02/29/1990"  
**When** schedule is calculated for non-leap year 2026  
**Then** default send date is 2026-02-28 unless user chose Mar 1.

## ACC-004 Phone Normalization
**Given** phone "+1 (555) 123-4567"  
**When** normalization runs  
**Then** stored as "+15551234567"; marked eligible.

## ACC-005 Recipient Selection
**Given** user toggles a recipient on  
**When** contact has valid phone  
**Then** recipient enrolled and persisted.

## ACC-006 Message Templates
**Given** user selects template "Happy birthday, {name}!"  
**When** applied to recipient "Priya"  
**Then** text shows "Happy birthday, Priya!"

## ACC-007 Gemini Drafting
**Given** recipient with name and age and context "close friend"  
**When** user taps "Generate with AI"  
**Then** a personalized draft appears; no phone/email was sent to Gemini.

## ACC-008 Message Editing
**Given** any draft  
**When** user edits text  
**Then** changes saved; parts count updates; > 10 parts blocked.

## ACC-009 Approval
**Given** user reviews draft  
**When** taps "Approve"  
**Then** approval snapshot created and recipient status is "approved."

## ACC-010 Approval Invalidation
**Given** approved recipient's phone changes in Google  
**When** next sync runs  
**Then** approval marked invalid; recipient flagged "Needs re-approval"; no send occurs.

## ACC-011 Scheduling
**Given** schedule defaults set to 10:00, window 9:00–21:00  
**When** recipient approved  
**Then** schedule stored; UI shows preview.

## ACC-012 Android Native SMS
**Given** armed recipient and send time reached  
**When** automation active and device is sender  
**Then** SMS sent via selected SIM; ledger entry written.

## ACC-013 SIM Selection
**Given** dual-SIM Android  
**When** user selects SIM B  
**Then** sends use SIM B; persisted.

## ACC-014 SMS Test
**Given** user taps "Send test SMS"  
**When** test completes  
**Then** ledger shows test entry; success toast.

## ACC-015 Background Execution
**Given** app killed and send scheduled  
**When** birthday time arrives  
**Then** send executes via WorkManager/alarm fallback.

## ACC-016 Automation Activation
**Given** readiness OK  
**When** user toggles automation ON  
**Then** status shows active; pending sends armed.

## ACC-017 Upcoming Birthdays
**Given** birthdays within 14 days  
**When** user opens Upcoming  
**Then** list sorted ascending with status.

## ACC-018 Activity Log
**Given** past send events  
**When** user opens Activity  
**Then** chronological list with masked recipient and status.

## ACC-019 Diagnostics
**Given** user runs diagnostics  
**When** checks execute  
**Then** each check shows pass/fail with fix suggestion.

## ACC-020 Notifications
**Given** birthday in 24h  
**When** notification enabled  
**Then** single reminder sent; no duplicate within 30 min.

## ACC-021 Privacy Controls
**Given** user opens Data Inventory  
**When** views data  
**Then** categories shown with retention; deletion options available.

## ACC-022 Account Lifecycle
**Given** user signs out  
**When** confirms  
**Then** tokens cleared; local data deleted per choice.

## ACC-023 Device Transfer
**Given** new Android signed in with same account  
**When** user confirms transfer  
**Then** new device becomes sender; old device demoted; 24h fence.

## ACC-024 Sender Fencing
**Given** two devices signed in  
**When** both attempt send  
**Then** only sender device sends; other blocked.

## ACC-025 Safety Ledger
**Given** any send attempt  
**When** attempt made  
**Then** ledger entry appended; immutable.

## ACC-026 Reconciliation
**Given** a sent SMS  
**When** reconciliation runs  
**Then** status confirmed against carrier record or marked unconfirmed.

## ACC-027 Account Deletion
**Given** user types confirmation  
**When** deletion completes  
**Then** account, cloud data, local data deleted; receipt shown.

## ACC-028 Web Deletion
**Given** user visits deletion URL  
**When** identity verified and confirmed  
**Then** backend deletes data; receipt with case ID shown.

## ACC-029 iOS Companion
**Given** birthday and approved message  
**When** reminder fires  
**Then** local notification shown; tap opens app → Messages sheet prefilled; user taps send; status "handed off."

## ACC-030 Contacts Disconnection
**Given** user taps disconnect contacts  
**When** confirmed  
**Then** contacts scope revoked; local snapshot deleted; limited mode.

## ACC-031 Reconnection
**Given** Google token revoked  
**When** user opens app  
**Then** app detects and prompts reconnect; on success session restored.

## ACC-032 Local-Data Deletion
**Given** user taps delete local data  
**When** confirmed  
**Then** encrypted storage wiped; app restarts clean.

## ACC-033 Notification Preferences
**Given** user toggles off failure notifications  
**When** failure occurs  
**Then** no failure push sent; in-app still shows.

## ACC-034 Template Restrictions
**Given** message > 10 SMS parts  
**When** user tries to save  
**Then** blocked with explanation.

---

# SECTION 24 — TRACEABILITY MATRIX

| Business Requirement | Product Goal | Feature | FR | BRULE | Screen | Journey | ACC | Platform |
|---|---|---|---|---|---|---|---|---|
| BIZ-001 Trust | Human approval | FEAT-010 | FR-009 | BRULE-005 | UI-011 | JOURNEY-06 | ACC-009 | All |
| BIZ-001 Trust | AI drafting | FEAT-008 | FR-007 | BRULE-011,041 | UI-010 | JOURNEY-07 | ACC-007 | All |
| BIZ-002 Reliability | Native SMS | FEAT-016 | FR-012 | BRULE-015,016,017 | UI-013 | JOURNEY-09 | ACC-012 | Android |
| BIZ-002 Reliability | Safety ledger | FEAT-030 | FR-025 | BRULE-030 | UI-016 | JOURNEY-15 | ACC-025 | Android |
| BIZ-003 iOS honesty | Companion | FEAT-037 | FR-029 | BRULE-021 | UI-025 | JOURNEY-08 | ACC-029 | iOS |
| BIZ-004 Compliance | Web deletion | FEAT-033 | FR-028 | BRULE-027 | UI-025 | JOURNEY-25 | ACC-028 | Web |
| BIZ-004 Compliance | Account deletion | FEAT-032 | FR-027 | BRULE-026,029 | UI-020,024 | JOURNEY-24 | ACC-027 | All |
| BIZ-005 Activation | Onboarding | FEAT-001,002,005 | FR-001,002,005 | BRULE-001,004 | UI-002–005 | JOURNEY-01 | ACC-001,002,005 | All |
| BIZ-005 Activation | Automation | FEAT-021 | FR-016 | BRULE-020 | UI-015 | JOURNEY-11 | ACC-016 | All |
| BIZ-006 Self-serve | Diagnostics | FEAT-024 | FR-019 | BRULE-025 | UI-018 | JOURNEY-16 | ACC-019 | All |
| BIZ-006 Self-serve | Activity log | FEAT-023 | FR-018 | BRULE-016 | UI-016 | JOURNEY-15 | ACC-018 | All |

---

# SECTION 25 — PRODUCTION READINESS CHECKLIST

| Category | Checklist Item | Verifiable? |
|---|---|---|
| Product | MVP scope finalized and prioritized | ✅ |
| UX | All 26 journeys documented with failure/recovery | ✅ |
| UI | All screens have loading/empty/error/success states | ✅ |
| Accessibility | WCAG AA audit passed; TalkBack/VoiceOver verified | ✅ |
| Android | SEND_SMS flow tested on 5+ OEMs; WorkManager reliability tested | ✅ |
| iOS | MessageUI handoff tested; no auto-send claims in UI | ✅ |
| Web | Deletion page responsive + WCAG AA; tested in Chrome/Safari | ✅ |
| Backend | Cloud Functions deployed; Firestore rules tested | ✅ |
| Auth | PKCE + token refresh; token revocation recovery tested | ✅ |
| Google Integration | Restricted scope verification submitted | ✅ |
| Contacts | Read-only confirmed; sync performance with 10k contacts | ✅ |
| Gemini | Rate limits; PII stripping unit-tested | ✅ |
| SMS | Multi-part, dual-SIM, retry, reconciliation all tested | ✅ |
| Scheduling | Timezone, DST, Feb 29 cases tested | ✅ |
| Notifications | Duplicate suppression tested; no spam | ✅ |
| Privacy | Data inventory accurate; deletion ≤ 48h tested | ✅ |
| Security | Penetration test; encryption verified | ✅ |
| Deletion | In-app + web deletion tested end-to-end | ✅ |
| Transfer | Sender transfer + fencing tested multi-device | ✅ |
| Offline | Offline read + sync-on-reconnect tested | ✅ |
| Error Handling | All failure taxonomy entries have recovery | ✅ |
| Analytics | Events implemented; PII excluded | ✅ |
| Diagnostics | Export scrubbed of PII | ✅ |
| Support | Knowledge base articles for error IDs | ✅ |
| Legal | Privacy policy + terms reviewed; Gemini disclosure | ✅ |
| Performance | Cold start ≤ 2s; sync ≤ 10s/1000 contacts | ✅ |
| Testing | Unit ≥ 80%; integration + E2E on both platforms | ✅ |
| Deployment | Staged rollout; rollback plan | ✅ |
| Monitoring | Crash reporting + send failure alerting | ✅ |
| Rollback | App + backend rollback tested | ✅ |
| Documentation | Developer docs, support docs, privacy disclosures | ✅ |

---

# SECTION 26 — RISKS & MITIGATIONS

| Risk | Impact | Probability | Mitigation | Owner | Detection Signal |
|---|---|---|---|---|---|
| **RISK-001** Android OEM background killing | High — missed sends | High | WorkManager + alarm fallback; battery exemption guidance | Eng | Send success rate drop |
| **RISK-002** Google restricted-scope verification fails | High — app removal | Low | Full compliance: deletion page, privacy policy | Product/Legal | Google console status |
| **RISK-003** Gemini generation produces inappropriate content | High — trust damage | Medium | Human approval gate; content guardrails; template fallback | Eng/AI | Approval reject rate |
| **RISK-004** iOS users expect auto-send | Medium — bad reviews | High | Honest onboarding; explicit platform messaging | Product/UX | App Store reviews |
| **RISK-005** Dual-SIM send failures | Medium — failed sends | Medium | SIM selection UI; fallback to default SIM; test SMS | Eng | SMS error logs |
| **RISK-006** Multi-part SMS cost surprise | Low — user anger | Medium | Parts warning in composer; cost estimate | UX | Support tickets |
| **RISK-007** Data deletion backlog | High — compliance | Low | Queued deletion with status; auto-retry | Eng/Ops | Deletion queue depth |
| **RISK-008** Token revocation during send window | Medium — missed send | Medium | Re-auth prompt; late-send rule | Eng | Auth error rate |
| **RISK-009** Contact data changes post-approval | Medium — wrong message | Low | Approval invalidation on sync | Eng | Invalidated approval count |
| **RISK-010** Gemini API rate limits exceeded | Low — fallback works | Medium | Template fallback; daily quota | Eng | Gemini 429 errors |
| **RISK-011** Privacy breach via logs | High — trust/legal | Low | PII scrubber; log policies | Eng/Security | Log audit |
| **RISK-012** Sender fencing conflict | Medium — duplicate sends | Low | 24h fence; immutable ledger | Eng | Duplicate ledger entries |
| **RISK-013** Analytics misconfiguration sends PII | High — privacy | Low | Analytics schema review; no PII fields | Eng/Privacy | Data audit |
| **RISK-014** Poor first-time activation | Medium — churn | Medium | Consolidated onboarding; sample preview | UX | Onboarding funnel drop-off |
| **RISK-015** Accessibility failures block launch | Medium — legal/UX | Medium | Accessibility testing in CI | QA | Audit report |

---

# SECTION 27 — FINAL PRODUCT DEFINITION

## 27.1 Product Promise
**WishWell remembers birthdays, drafts thoughtful messages with AI, requires your approval, and delivers them on time — automatically on Android, honestly on iOS — while protecting your data and giving you full control.**

## 27.2 Core User Journey
1. User signs in with Google.
2. App syncs contacts and finds birthdays.
3. User picks recipients.
4. Gemini drafts a personalized message for each.
5. User reviews, edits, and approves.
6. On each birthday, message is sent (Android) or user is prompted to send (iOS).
7. User sees what happened in Activity; problems are self-diagnosable.
8. User can pause, transfer, disconnect, or delete everything.

## 27.3 Core Features
Google auth, read-only contacts sync, birthday detection + Feb 29 handling, phone normalization, recipient management, Gemini AI drafting with privacy boundary, human approval + snapshots + invalidation, scheduling with send windows and late-send policy, Android native SMS with SIM selection + background execution + safety ledger + reconciliation, iOS local reminders + MessageUI handoff, activity log, diagnostics, notifications, sender fencing + transfer, full account lifecycle, web deletion landing.

## 9.2 Platform Strategy
- **Android:** Full automatic sender.
- **iOS:** Trustworthy companion that pre-fills and reminds.
- **Web:** Privacy/deletion surface only.
- Never misrepresent platform capabilities.

## 27.5 Key Differentiators
- **Human-in-the-loop AI** — approval gate builds trust.
- **Platform honesty** — no false promises on iOS.
- **Reliability engineering** — ledger, reconciliation, retry, fencing.
- **Privacy by design** — minimal data, full deletion, web deletion.
- **Accessibility and localization from day one.**

## 27.6 Production Scope
P0 + P1 features as defined in Section 19. No future features in MVP.

## 27.7 Future Expansion
Anniversaries, multi-channel (WhatsApp), shared lists, premium tiers, annual report.

## 27.8 What Must NOT Be Built
- iOS automatic SMS sending (impossible and misleading).
- Writing to Google Contacts.
- Multiple simultaneous Google accounts.
- Marketing/bulk messaging.
- Any feature that sends a message without explicit user approval.
- Any feature that requires PII beyond what is strictly necessary.
- Non-birthday messaging (MVP).

## 27.9 Definition of Success
**WishWell is successful when:**
- ≥ 70% of new users complete setup and activate automation.
- ≥ 60% of AI drafts are approved with minimal edits.
- ≥ 98% of Android scheduled messages are delivered within their send window.
- iOS users understand and accept the companion model (App Store rating ≥ 4.5).
- 100% of deletion requests are completed within 48 hours.
- Users trust WishWell enough to leave automation on after the first send, and to tell others "it doesn't feel like a robot."
