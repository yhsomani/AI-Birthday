# 08 — Device test log (Android emulator, manual UI pass)

Device: Android emulator `Medium_Phone` (1080x2400), debug build installed with `adb install -r`.
Physical phone: not attached, so not tested. Gemini API key: not entered and not stored in the repo.

## Flows exercised

| Flow | Result |
|---|---|
| First launch and onboarding (3 slides, Next, Get Started) | Pass |
| Dashboard empty state, quick actions | Pass |
| Add birthday form: name, month and day dropdowns, counter | Pass |
| Save birthday, confirmation snackbar, dashboard update | Pass |
| Relationship and Tone section: closeness shows Casual by default | Pass |
| Review & Send opens the message studio for the birthday | Pass |
| Studio: typing autosaves ("Saved", character count) | Pass |
| Studio: Copy Text copies and confirms | Pass |
| Add birthday form: Cancel label (was wrapping) | Fixed and verified |
| Dashboard: person listed under Action Needed and Today's Birthdays | Fixed and verified (now once) |

## Bugs found and fixed

1. **Cancel label wraps and clips** on the Add birthday app bar. Cause: the leading slot is about 56dp wide. Fix: `leadingWidth: 96` in `person_form_screen.dart`.
2. **Duplicate on the dashboard.** A birthday needing action appeared in both Action Needed and Today's Birthdays. Fix: Today's list excludes items already under Action Needed (`dashboard_screen.dart`).

## Observed, not yet fixed

- A contact with no relationship text shows "Other" on the card, while the closeness control shows Casual. The labels disagree.
- The card shows "No phone number" and the studio shows "Pro" notices. Both are correct, but the copy should be checked for tone.

## Not yet tested on the device

- People, Calendar, History and Settings tabs.
- Entering the Gemini key in Settings and generating a draft (AI).
- Handoff to WhatsApp and the confirmation dialog.
- Reminder scheduling and reboot recovery (needs a reboot and an alarm firing).

## Security note

The Gemini key was pasted into the chat. Rotate it after testing, and enter it only in the app's settings.
