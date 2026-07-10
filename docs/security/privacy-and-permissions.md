# Privacy and Permissions

Last reviewed: 2026-07-10

This document records the production privacy and permissions contract for RelateAI. It is not legal advice; the Play Console release owner must verify every declaration against the final build, store listing, privacy policy, and current Google Play policy before release.

## Source References

- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play AccessibilityService API policy: https://support.google.com/googleplay/android-developer/answer/10964491
- Google Play Permissions and APIs that Access Sensitive Information: https://support.google.com/googleplay/android-developer/answer/16558241

## Provider Configuration Policy

Repository policy:

- The React Native client must not contain provider secrets, service account JSON, private keys, OAuth access or refresh tokens, client secrets, signing material, SMTP credentials, Gemini keys, or local project variants.
- AI and email provider credentials belong on backend services. The RN app may receive only release-safe endpoint URLs through environment/build configuration.
- Legacy Google/Firebase config files, if still present in archived Android artifacts, are not part of the React Native release surface and must not be used as the source of truth for RN builds.
- If account-backed or provider-backed features are added to the RN release, document the tracked client config allowlist before committing any new platform config file.

## React Native Replacement Manifest Surface

The React Native replacement uses `app.json` as the source for native permissions during Expo prebuild/EAS builds.

| Permission or API | RN replacement use | Data touched | Release requirement |
| --- | --- | --- | --- |
| `READ_CONTACTS` | Device contact import only when the user starts import. | Contact names, phone numbers, email addresses, birthdays/events when available. | Explain before requesting, support denial, and keep imported events review-needed. |
| `READ_CALENDAR`, `WRITE_CALENDAR` | Calendar import/export only when the user starts sync. | Relationship event labels, dates, and review-safe notes. | Imported events remain unverified until review; exported notes must route back to review. |
| `POST_NOTIFICATIONS` | Event reminders and safe review routes. | Minimal reminder text and route metadata. | Denial must leave in-app reminder states available. Notification taps must not send messages. |
| `USE_BIOMETRIC` | Optional biometric lock when the user enables it. | Local unlock state only. | Hardware unavailable or unenrolled states must be recoverable without data loss. |
| Android home widget | Optional launcher widget generated during Expo prebuild. The RN shell syncs a sanitized summary to the generated widget module, which exposes safe counts and navigation-only intents. | Privacy-minimized summary labels, counts, and safe routes for Home, Events, Messages, or More. | Widget payloads must avoid message bodies, phone numbers, email addresses, private notes, send actions, and delete actions; serialized native payloads must strip record identifiers. Widget intents must be immutable and route to in-app review or recovery screens only. |
| Direct SMS, SMS inbox, call log, and phone-number permissions | Not requested by the RN replacement. SMS uses user-controlled deep-link/share handoff instead of direct background sends. | None through these permissions. | `app.json` blocks `SEND_SMS`, SMS inbox, call-log, and phone-number permissions from merged Android manifests. |
| Exact alarm permissions | Not requested by the RN replacement. Reminders use review-safe notification scheduling and in-app recovery states. | None through exact alarm APIs. | `app.json` blocks `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM` from merged Android manifests. A future exact-alarm release would require separate Play-policy review and release-owner signoff. |
| WhatsApp AccessibilityService API | Not part of the RN replacement release config. WhatsApp uses manual deep-link/share handoff. | None through Accessibility APIs. | `app.json` blocks `android.permission.BIND_ACCESSIBILITY_SERVICE`; do not add AccessibilityService automation to the RN Play build without a separate prominent disclosure, policy review, consent gate, and release-owner signoff. |

## Legacy Android Artifact Policy

Legacy Android/Gradle artifacts are not the target React Native release surface, and their manifests, Firebase files, Room schema, workers, or AccessibilityService declarations must not be treated as active RN release requirements.

`npm run release:evidence` must show an empty top-level legacy Android artifact path list for the active RN release. Any reintroduced legacy path is release evidence drift and must be removed or explicitly resolved before signing.

## WhatsApp Manual Handoff Contract

The React Native release path uses manual WhatsApp handoff, not AccessibilityService automation.

Required behavior:

- The user must approve the message text before any WhatsApp handoff option appears.
- Opening WhatsApp or a share sheet must never mark a message sent by itself.
- RelateAI records sent status only after the user explicitly confirms the message was sent.
- Fallback sharing must include only approved message text and must not append route errors, setup diagnostics, credentials, phone numbers, or email addresses.
- Provider failure logs and activity records must remain redacted and must not include message bodies or screen contents.

Policy risk:

- Do not add AccessibilityService automation to the React Native Play build without a separate policy review, prominent disclosure, explicit consent, revocation path, no-chat-reading evidence, and release-owner signoff.

Release evidence required:

- Screenshot or video showing approved-text-only WhatsApp handoff.
- Screenshot or video showing share fallback and explicit mark-sent confirmation.
- Evidence from the release config that no AccessibilityService permission is present in the active RN config, plus signed-build manifest evidence that no AccessibilityService service declaration is present in the submitted build.

## Data Safety Inputs

Data types currently in scope for the Play Data Safety form and privacy policy review:

- Contact info: names, phone numbers, email addresses, birthdays/events, relationship labels, notes, and personalization metadata.
- User-generated content: message drafts, approved messages, feedback, history, backup files, and manual contact/event entries.
- Account/auth data: account identifiers and OAuth/session state only when account-backed features are explicitly enabled; local-only mode stores a local mode preference instead of account identifiers.
- App activity/diagnostics: dispatch attempts, delivery state, setup checks, local redacted diagnostic snapshots, recovery diagnostics, and non-sensitive analytics summaries.
- AI data: prompts and generated responses may include contact/event/message context. They must be disclosed as sent to the AI provider if enabled.
- Email/SMS send data: recipient address/number, subject where applicable, and body.
- WhatsApp manual handoff data: approved outgoing message text passed to WhatsApp or the platform share sheet only after user action. The current contract excludes AccessibilityService automation, transient WhatsApp UI inspection, WhatsApp chat-content collection, and unattended WhatsApp sending.

Sensitive data handling requirements:

- Platform auto backup must not expose unencrypted relationship data; sensitive stores should be excluded unless encrypted by the app.
- User backup/export must be explicit, encrypted where implemented, and documented with restore limitations.
- The production repository path uses independently AES-GCM-encrypted entity files with encrypted indexes/manifests and alternating rollback checkpoints. Its random master key must remain only in verified platform-protected storage. Production startup uses a verified dual-read/single-write migration facade: current normalized SecureStore is read-only during migration, the encrypted repository is authoritative after the protected commit checkpoint, and automatic migration does not delete the rollback source. A user-confirmed local-data clear removes that legacy payload only after the empty repository replacement verifies.
- Expo SQLite must not store private relationship data unless a future adapter proves SQLCipher-equivalent encryption for database pages, journals/WAL files, migrations, and key lifecycle on both platforms.
- Live app state encryption and backup passphrases must remain separate. Backup passphrases only protect exported backup files and must not be stored.
- Local-only mode does not send contacts to account sync providers. Manual contacts, device-imported contacts, events, drafts, and activity remain local unless the user explicitly uses an external provider feature such as AI drafting, provider email delivery, SMS/WhatsApp handoff, calendar export, or encrypted backup export.
- Sign-out or local-data clearing must clear local app state through one explicit user-confirmed path.
- Logs, analytics exports, backup manifests, and provider failure metadata must redact tokens, credentials, raw AI responses, raw screen contents, and message bodies where not explicitly user-exported.
- The default analytics share action contains aggregate metrics only. The secondary CSV report may contain contact names and relationship metrics, so it requires a fresh preview/confirmation, opens only a user-controlled share destination, and deletes its app-owned temporary file after the share flow succeeds or fails.
- Diagnostic snapshots are local troubleshooting evidence. They must stay redacted, must not contain raw message bodies or secrets, and are excluded from user backup export/import.

## Open Release Blockers

- A release owner must confirm the React Native build excludes AccessibilityService automation.
- A release owner must complete Data Safety and privacy policy copy against the final shipped feature set.
- A release owner must attach signed-build, release-device, and store submission evidence for the final RN build.
