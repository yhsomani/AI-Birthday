# Audit 03 — WhatsApp / External-App Truthfulness

**Scope:** Does any UI claim "sent" when WhatsApp was merely opened? Are the
states DRAFT → OPENED → USER_SENDS → USER_CONFIRMS → COMPLETED honestly
represented?
**Method:** read-only code trace. Evidence hierarchy: code/control flow > tests
> docs/comments. Claims are labeled **FACT** (code), **INFERENCE** (derived),
**UNKNOWN** (unverifiable from repo).

---

## 1. Delivery layer as actually implemented

- **No `canLaunchUrl` anywhere.** Only `launchUrl` is used.
  - WhatsApp: `launchUrl(handoff.uri, mode: LaunchMode.externalApplication)`
    — `lib/features/message_studio/presentation/message_studio_screen.dart:307-310` (FACT)
  - SMS: `launchUrl(uri)` with `sms:<phone>?body=<msg>`
    — `lib/features/delivery/data/sms_delivery_service.dart:15-24` (FACT)
  - Share: Android `ACTION_SEND` chooser
    — `lib/features/delivery/data/native_share_service.dart:13-26`,
    `android/app/src/main/kotlin/com/yashsomani/ai_birthday/MainActivity.kt:236-252` (FACT)
- Handoff URI built from official `https://wa.me/<number>?text=...`
  — `lib/features/delivery/data/whatsapp_handoff_builder.dart:56-81` (FACT)
- **Evidence of sending: none.** `launchUrl` returning `true` only proves an
  external app accepted the intent. There is no WhatsApp ack, no delivery
  receipt, no deep-link-back signal. (FACT — no such code exists anywhere in
  lib/ or android/.)
- Android declares WhatsApp in `<queries>` for intent visibility only
  — `android/app/src/main/AndroidManifest.xml:51-71`; no auto-send code. (FACT)

**Dead delivery model (FACT):**
`DeliveryState`/`DeliveryRecord`
— `lib/features/delivery/domain/models/delivery_channel.dart:27-90` — are never
referenced outside that file (grep: zero readers/writers) and have **no Drift
table** (`lib/core/database/app_database.dart:95-97` declares only
Persons/Birthdays/MessageDrafts/ReminderSettingsEntries). The doc comment
`whatsapp_handoff_builder.dart:7` claiming "The app tracks handoff as
[DeliveryState.handedOff]" is false — the app tracks `BirthdayStatus.handedOff`.

---

## 2. Birthday status lifecycle — every transition (FACT unless noted)

Written transitions (authors of `updateBirthdayStatus` / `saveBirthday`):

| To (BirthdayStatus) | Set by | Location |
|---|---|---|
| `upcoming` | person add/edit | `people_screen.dart:366`, `person_form_screen.dart:288,292` |
| `upcoming` / `reminderDue` | lifecycle refresh | `birthday_lifecycle_service.dart:41-42,52,74,85` |
| `messageDrafted` | AI generate (studio) | `message_studio_screen.dart:207-211` |
| `handedOff` | WhatsApp launch OK | `message_studio_screen.dart:325` |
| `handedOff` | SMS launch OK | `message_studio_screen.dart:370` |
| `handedOff` | Share chooser OK | `message_studio_screen.dart:403` |
| `failed` | WhatsApp launch FAILED | `message_studio_screen.dart:329` |
| `completed` | user taps "Yes, Message Sent!" | `message_studio_screen.dart:711-714` |

**Who sets `completed`?** Only the confirmation dialog's affirmative button
(`:707-714`), which also sets `DraftStatus.confirmedSent` (`:715`). There is no
auto-complete, no timers, no lifecycle-observer complete. **User-confirmed
`completed` is a distinct, mandatory step.** (FACT)

**Dead states:** `messageNotPrepared`, `messageReviewed`, `readyForDelivery`,
`created` are never written by any flow (grep: only enum definitions
`birthday.dart:8-47`, lifecycle read of `created` at
`birthday_lifecycle_service.dart:69`, and dashboard labels
`dashboard_screen.dart:440-457`). `DraftStatus.reviewed` and
`DraftStatus.handedOff` are likewise never written
(`message_studio_screen.dart:84,102,198,246,302,360,393,715` are the only
writers, using draft/ready/confirmedSent). (FACT)

**Real collapsed flow** (what actually happens on a send attempt):

```
draft(ready)  ──launchUrl ok──▶  Birthday=handedOff, Draft stays ready
                                  │
                                  ├─ "Not Sent Yet" (:704) ──▶ stays handedOff
                                  └─ "Yes, Message Sent!" (:707) ──▶ completed + confirmedSent
launch fails ──▶ Birthday=failed (WhatsApp only; SMS/share no-op) ──▶ dialog still shown
```

USER_SENDS and USER_CONFIRMS are collapsed into one question asked immediately
after launch ("Did you send the message?", `:695-698`). The user is the only
arbiter; there is no programmatic signal, so this is honest but fused. (FACT/INFERENCE)

---

## 3. Truthfulness table — every user-visible claim vs evidence

| # | Claim (file:line) | Evidence behind it | Verdict |
|---|---|---|---|
| 1 | Button "Send on WhatsApp" (`message_studio_screen.dart:942`) | Action verb; opens wa.me. Launch ≠ send. | **HONEST** (nothing claims sent) |
| 2 | Dialog title "$channelName Handoff" (`:669`) | Accurate. | **HONEST** |
| 3 | "WhatsApp opened with your message. Once you have dispatched it… confirm below" (`:677-678`) | Opened ≠ sent, explicitly says so. | **HONEST** |
| 4 | "Did you send the message?" / Not Sent Yet / Yes, Message Sent! (`:695-698,704,726`) | The one gate to `completed`. | **HONEST** |
| 5 | Snackbar "Celebration confirmed as sent! 🎉" (`:719-723`) | User-confirmed before shown. | **HONEST** |
| 6 | "✓ Celebration marked as sent. Resend anytime below." (`:1050`) | "marked as" qualifier is honest; "Resend … below" = SMS/Share/Copy only (`:975-990`), no WhatsApp. | **LARGELY HONEST** (minor, P3) |
| 7 | History "Sent" for `confirmedSent` (`history_screen.dart:69-72`) | confirmedSent is user-confirmed. | **HONEST** |
| 8 | History "Opened in WhatsApp" for `DraftStatus.handedOff` (`:73-76`) | **Unreachable**: nothing ever writes `DraftStatus.handedOff` (see §2). Branch covers a state the app cannot produce; hardcodes "WhatsApp" though SMS/share hand off too. Test `test/features/history/presentation/history_screen_test.dart:9-52` manufactures the draft by hand. | **DISHONEST/DEAD** (P1) |
| 9 | History "Ready to Send" for a draft whose birthday is `handedOff` (`:77` + writer at `:302,360,393`) | Draft is saved `ready` before launch and stays `ready` until confirm — history shows *draft* state, not the *delivery* state. A message already opened in WhatsApp displays "Ready to Send". | **STALE/MISLEADING** (P1) |
| 10 | Dashboard chip "Handed Off" (`dashboard_screen.dart:370` → `birthday.dart:27`) | Real state. | **HONEST** |
| 11 | Dashboard "Confirm Sent" CTA (`dashboard_screen.dart:455`) | Button navigates to studio (`:405-412`) which for `handedOff` shows **"Send on WhatsApp"** (`:931-947`) — no confirm-only UI; confirming requires re-launching WhatsApp and re-answering the dialog. | **LABEL ≠ ACTION** (P1) |
| 12 | Dashboard "N greetings require review before sending" (`:271`) | Advisory count of action-needed. | **HONEST** |
| 13 | SMS: "Send via SMS" (`:979`), dialog "SMS opened… confirm below" | `sms:` URI hand-off only; same confirm gate. | **HONEST** |
| 14 | Share Sheet (`:984`, `native_share_service.dart:13-26`) | Chooser hand-off; same gate. | **HONEST** |
| 15 | "You can copy the message to your clipboard" when launch fails (`:679`) | No copy button in dialog; selectable box shows the wa.me URL (WhatsApp) or recipient phone (SMS) (`:682-693`), not the message body. | **MISLEADING** (P2) |
| 16 | Reminder notifications ("Birthday is tomorrow!" etc., `reminders/application/android_notification_scheduler_gateway.dart:82`) | No delivery claims. | **HONEST** |

**Where "sent" is only ever claimed:** confirmed by the user in the dialog
(`:707-714`) — the correct place. No reachable path claims sent on open.

---

## 4. OPENED vs SENT discipline

- Correctly guarded: the launch → `handedOff` boundary never prints "sent"
  (§3 rows 1-7); `completed` requires explicit confirmation.
- **Not guarded:** History displays the *draft* status for delivery events
  (row 9), so a real hand-off is shown as "Ready to Send"; the "Opened in
  WhatsApp" badge exists only for a state the app never writes (row 8).
- `DeliveryRecord` (the intended evidence record) is dead code — there is
  **no persisted record of any launch**, only the birthday row's status
  (`lib/core/database/drift_repositories.dart:190-208`).

---

## 5. SMS path

Real hand-off, honestly represented: `sms:` scheme launch
(`sms_delivery_service.dart:10-25`), `handedOff` on success
(`message_studio_screen.dart:370`), same confirm dialog, `completed` only on
confirm. Not auto-sent. (FACT)

---

## 6. Contradiction register

| ID | Conflict | Evidence | Decision |
|---|---|---|---|
| C1 | Doc claims tracking via `DeliveryState.handedOff`; that enum is dead | `whatsapp_handoff_builder.dart:7` vs grep (no writers/readers, no table) | Delete `DeliveryState`/`DeliveryRecord` or wire them into persistence; fix comment |
| C2 | History "Opened in WhatsApp" for a state nothing produces | `history_screen.dart:73-76`; writers = `message_studio_screen.dart:302,360,393,715` | Wire real handoff evidence or remove branch; never hardcode channel |
| C3 | Dashboard "Confirm Sent" opens a screen whose primary action is "Send on WhatsApp" | `dashboard_screen.dart:455` vs `message_studio_screen.dart:931-947` | Studio must expose a confirm-only state for `handedOff`, or dashboard must not promise it |
| C4 | `handedOff` is Action Needed **today** but not 1–7 days out | `dashboard_screen.dart:78-81` (today: any non-completed) vs `:84-89` (window excludes handedOff) | Include `handedOff` in the action-needed window |
| C5 | WhatsApp launch failure → `failed`; SMS/share failure → silent no-op | `message_studio_screen.dart:326-330` vs `:367-371`, `:400-404` | Unify failure semantics |
| C6 | "copy the message to your clipboard" — dialog offers no copy; box shows URL/phone, not message | `message_studio_screen.dart:679,682-693`, `:373-378` | Add Copy action with actual message body, or reword |

---

## 7. Persistence

`BirthdayStatus` and `DraftStatus` are persisted as text columns in Drift
(`birthdays.status`, `messageDrafts.status`
— `app_database.dart:51-81`; written in `drift_repositories.dart:173-208,271-289`)
and survive restart. Delivery events are **not** persisted (no table, no
`DeliveryRecord` writes) — P1.

---

## 8. Findings P0–P3

### P0 — none found
No reachable code path claims "sent" when WhatsApp was merely opened.

### P1-1 — No persisted evidence of any hand-off (root gap)
- Symptom: History cannot truthfully show "opened"; a message opened in
  WhatsApp still reads "Ready to Send"; the "Opened in WhatsApp" badge is
  dead code.
- Root cause: handoff recorded only as `Birthday.status`; `DeliveryRecord`
  model exists but is unpersisted/unused; drafts save `ready` before launch
  and keep it.
- Evidence: §1, §2, table rows 8-9; `app_database.dart:95-97`.
- Required: persist a delivery record (channel, state, timestamp) when a
  launch succeeds; History renders from that record.

### P1-2 — Unconfirmed hand-off vanishes from Action Needed
- Symptom: open WhatsApp for a +1..7-day birthday, do not confirm — no
  "Confirm Sent" CTA anywhere.
- Evidence: `dashboard_screen.dart:84-89`.
- Required: include `handedOff` in the action-needed window.

### P1-3 — "Confirm Sent" leads to a re-send UI
- Symptom: dashboard promises confirmation; studio offers launch again.
- Evidence: C3.
- Required: show a confirm-only path for `handedOff` birthdays in studio.

### P2-1 — Dead `DraftStatus.handedOff` / `reviewed` branches and test
- Evidence: writers list §2; `history_screen_test.dart:9-52` asserts a state
  the app never produces; `message_draft.dart:72-73` `isReviewed` never true.
- Required: wire writers or delete enum branches + test.

### P2-2 — SMS/share failure silently ignored
- Evidence: `message_studio_screen.dart:367-371,400-404` (no `else`→`failed`).
- Required: mirror WhatsApp failure handling (or explicitly leave state, then
  don't show "could not be opened" dialog as-is).

### P2-3 — Failure-dialog copy instruction is unmet
- Evidence: C6.
- Required: real copy button with the message body, or reworded text.

### P2-4 — Doc comment lies
- Evidence: C1, `whatsapp_handoff_builder.dart:7`.

### P3-1 — "Resend anytime below" over-reaches (channels below exclude WhatsApp)
- Evidence: `message_studio_screen.dart:1050` + visibility test Q intent
  (`test/features/message_studio/presentation/message_studio_visibility_test.dart:144-145`).
- Required: wording tweak ("Resend via the options below").

### P3-2 — SMS send path lacks its own phone validation
- Evidence: `_handleSmsSend` accepts null/empty phone
  (`message_studio_screen.dart:348-365`); UI hides the button when phone is
  unusable (`:975`), but the method is not safe standalone.

### P3-3 — `launchUrl == true` can mean browser fallback, not WhatsApp
- Symptom: on a device without WhatsApp, wa.me opens in a browser, dialog
  still says "$channelName opened with your message".
- Evidence: `message_studio_screen.dart:307-337`. Not fixable cheaply; note in
  copy ("opened WhatsApp or your browser") or keep, given confirmation gate.

---

## 9. Acceptance criteria

- **AC1 (P1-1)** Given a user taps "Send on WhatsApp" and `launchUrl` succeeds,
  When the returns to History, Then a row shows "Opened in WhatsApp" (or
  channel-specific "Opened in SMS") sourced from a persisted delivery record,
  and unchanged "Ready to Send" never appears for a launched hand-off.
- **AC2 (P1-2)** Given a birthday in `handedOff` status dated 1–7 days out,
  When the dashboard loads, Then it appears under "Action Needed" with the
  "Confirm Sent" action.
- **AC3 (P1-3)** Given a birthday in `handedOff` status, When the user taps
  "Confirm Sent", Then they reach a confirmation UI (Yes, Message Sent! /
  Not Yet) without launching WhatsApp again, and confirming transitions to
  `completed` only then.
- **AC4 (P2-2)** Given an SMS or Share launch throws, When the flow returns,
  Then the birthday is marked `failed`/needs-action, matching the WhatsApp
  path.
- **AC5 (P2-1)** Given any end-to-end delivery flow, When History renders,
  Then "Opened in WhatsApp" appears only for records whose evidence is a
  succeeded WhatsApp launch (never SMS/share, never an unbacked enum state),
  and the dead `DraftStatus.handedOff`/`reviewed` branches and their test are
  removed or wired.
- **AC6 (P2-3)** Given WhatsApp cannot be opened, When the failure dialog
  shows, Then a "Copy message" action copies the actual message body.

---

## Appendix — key evidence lines

- Launch + state writes: `message_studio_screen.dart:273-346`
  (WhatsApp), `:348-380` (SMS), `:382-413` (share), `:653-732` (confirm dialog)
- Status persistence: `drift_repositories.dart:190-208` (birthdays),
  `:270-289` (drafts)
- Enum-only states: `birthday.dart:8-17`, `message_draft.dart:7-13`,
  `delivery_channel.dart:27-51`
- Guarded visibility (existing audits): studio primary/gating
  `message_studio_screen.dart:797-802,921-947`; dashboard action labels
  `dashboard_screen.dart:438-458`; tests
  `test/features/message_studio/presentation/message_studio_visibility_test.dart`,
  `test/features/dashboard/presentation/dashboard_visibility_test.dart:78-113`