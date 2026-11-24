# 13 — v1 Production Readiness Decision

**ID:** BP-PROD-REL-001 · **Date:** 2026-11-24 · **Owner:** Product owner / technical strategy · **Status:** Production candidate built and tested; store/online deployment remains blocked by external evidence and credentials.

## 1. Product decision

AI-Birthday v1 is an **Android, local-first personal birthday assistant**. The supported value loop is:

`Remember → plan/remind → prepare manually or with entitled AI → review → user-controlled handoff → explicit user confirmation → history`.

### Included
- Local People, birthday, dashboard, calendar and History.
- Reminder configuration and native Android alarms/notifications.
- Manual Message Studio, durable drafts and stale-response protection.
- WhatsApp Click-to-Chat, SMS composer, copy and share-sheet handoff.
- Truthful handoff/history state and explicit “I Sent It” confirmation.
- Google Play purchase verification with Firebase App Check and backend Play verification.
- Optional BYOK Gemini and supported on-device AI after entitlement.
- Opt-in, owner-scoped cloud backup with explicit non-E2E-encryption disclosure.
- Sign-in, export, backup/restore and existing release-gated deletion service.

### Disabled or removed from v1
- Stripe/client-secret billing was removed entirely. Google Play is the sole purchase channel.
- Debug/local entitlement grants were removed from production UI and the test hook is debug/runtime guarded.
- Automatic background preparation is compile-time disabled and hidden from normal UX.
- Direct/background automatic SMS is compile-time and native-layer disabled; manual SMS composer remains.
- Birthday Autopilot control-plane UI is not represented as part of the core product.
- Public Hosting/support/deletion is not represented as deployed; release remains configuration-gated.

## 2. Implemented production corrections

- Purchase verification now requires a fresh limited-use App Check token in the client and unconditional App Check + consumed-token enforcement in the backend.
- CSV, device contacts and legacy JSON default new/missing `autoPrepare` to false.
- Direct-SMS settings/provider/native receiver fail closed and cancel stale jobs when disabled.
- Person delete and Undo now use an aggregate transaction, including birthday restoration and job cancellation.
- Birthday rollover atomically removes the old-cycle draft and clears the active draft reference.
- Handoff evidence/status uses one transaction; confirmation uses one transaction for Birthday + Draft and rejects confirmation without a successful handoff.
- Startup recovers any previous-process `running` job rather than relying only on a fixed age.
- Dashboard ignores orphan birthdays; History is responsive at 320dp with truthful “User Confirmed Sent” wording.
- Cloud backup copy explicitly discloses that it is not end-to-end encrypted and excludes credentials.
- Backend scheduled sweepers emit exactly two aggregate, identifier-free operational log events.
- Hosting transitive `@grpc/grpc-js` advisory is overridden to a patched release; production audit reports zero Hosting vulnerabilities.
- Shell golden baselines are refreshed against the pinned Flutter 3.47.6 renderer and remain a release gate.

## 3. Verification evidence

Executed locally in the release work:
- `flutter analyze`: pass, no issues.
- Full Flutter non-golden suite after obsolete Stripe/deletion changes: 464 passed.
- Shell goldens: 2 passed.
- Focused production regression matrix: 122 passed (capability, data, lifecycle, jobs, handoff, Settings, entitlement, responsive).
- Backend typecheck/lint/unit suite: 81 passed.
- Firestore emulator integration did not execute: the required emulator JAR could not be downloaded from Google Storage after retry.
- Purchase App Check static suite: 2 passed.
- Hosting typecheck, 12 tests and production build: pass.
- Backend production dependency audit: no high/critical findings; five low/moderate transitives remain (`fast-xml-parser`, `glob`/`rimraf` and `minimatch`).
- Hosting `npm audit --omit=dev --audit-level=high`: 0 vulnerabilities after override.
- Play release APK built: `build/app/outputs/flutter-apk/app-play-release.apk` (69.1 MB).
- Play merged manifest verified without `SEND_SMS`.
- Play debug APK built: `build/app/outputs/flutter-apk/app-play-debug.apk`.

## 4. Remaining release blockers

1. **Release signing:** `android/key.properties` and production keystore are not present. `app-play-release.apk` is unsigned and cannot be uploaded to Play.
2. **Connected-device verification:** the previously connected `1b87b5db` device is no longer authorized/visible to `adb`; alarm, reboot, App Check/Play Integrity, Contacts, Nano and WhatsApp handoff physical-device evidence remains incomplete.
3. **Firebase backend deployment:** repository source passes tests, but live `verifyPurchase`, App Check, service-account Play access, Firestore/rules/index deployment and production configuration are not attested in this run.
4. **Google Sign-In release certificate:** debug and release SHA-1 values must be registered in Firebase/Google. The current debug SHA-1 observed locally is `88:05:5B:AE:E2:01:EB:76:97:1D:C0:CA:5B:3C:70:A2:39:A9:DA:18`; production SHA-1 is unavailable without the keystore.
5. **Play Integrity/App Check:** provider is wired but production registration and a Play-origin test are not evidenced.
6. **Play Billing:** subscription product, Play Console service-account access, internal track and real purchase/restore sandbox still require external verification.
7. **Legal/support/Hosting:** public developer identity, effective dates, support workflow, reCAPTCHA/App Check site key, legal/Hindi approvals and release config remain external gates. Hosting remains fail-closed.
8. **Physical compatibility:** supported Android OS/device matrix, notification/exact alarm/reboot, Nano/AICore and external app behavior need device evidence.
9. **Build tool warning:** current Google sign-in, Play Billing and URL launcher Android plugins still apply KGP in a mode future Flutter versions may stop supporting; update/verify before the next Flutter major migration.
10. **Commercial/operational decisions:** final Play price, trial, support owner, privacy/deletion operating process and analytics policy are not inferred.

## 5. Release disposition

- **Code readiness:** high, subject to final CI rerun on the exact commit.
- **Store readiness:** blocked by signing, Play/Firebase/App Check external configuration and real purchase/device evidence.
- **Backend readiness:** source/test ready; deployment evidence missing.
- **Public legal/deletion readiness:** intentionally fail-closed; external legal/configuration gates remain.
- **Product scope:** approved v1 boundaries should not be expanded with auto-prepare, direct SMS, Stripe or control-plane UI without separate approval and tests.

No application source beyond these approved v1 boundaries should be changed before an exact-commit CI run and product/security sign-off.
